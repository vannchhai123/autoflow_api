import argparse
import asyncio
import json
import os
import random
import subprocess
import sys
import tempfile

sys.stdout.reconfigure(encoding='utf-8')

try:
    from voice_cloner import synthesize_cloned_voice
except Exception:
    synthesize_cloned_voice = None

try:
    import edge_tts
except ImportError:
    edge_tts = None

try:
    from gtts import gTTS
except ImportError:
    gTTS = None


def parse_args():
    parser = argparse.ArgumentParser(description="AutoClip Video Generation Engine")
    parser.add_argument("--title", required=True, help="Video title to display")
    parser.add_argument("--text", required=True, help="Script text for voiceover")
    parser.add_argument("--aspect_ratio", default="PORTRAIT_9_16", choices=["PORTRAIT_9_16", "LANDSCAPE_16_9", "SQUARE_1_1"], help="Aspect ratio")
    parser.add_argument("--output", required=True, help="Target MP4 output file path")
    parser.add_argument("--voice", default="en-US-ChristopherNeural", help="TTS voice model")
    parser.add_argument("--audio_file", default=None, help="Path to real custom uploaded audio file")
    parser.add_argument("--cloned_profile", default=None, help="Path to cloned voice profile JSON")
    parser.add_argument("--background_image", default=None, help="Path to custom background image")
    return parser.parse_args()


def generate_fallback_audio(output_audio: str, duration_sec: int = 12):
    cmd = [
        "ffmpeg", "-y",
        "-f", "lavfi",
        "-i", f"sine=frequency=220:sample_rate=44100:duration={duration_sec}",
        "-af", "volume=0.08,afade=t=in:st=0:d=1,afade=t=out:st=" + str(max(1, duration_sec - 2)) + ":d=2",
        "-c:a", "libmp3lame",
        "-q:a", "4",
        output_audio
    ]
    subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)


async def generate_voiceover(text: str, voice: str, output_audio: str):
    print("PROGRESS:20:Generating AI voiceover...")
    clean_text = text.strip() if text else ""
    if not clean_text or clean_text.replace("?", "").strip() == "":
        clean_text = "Welcome to AutoClip AI Video Studio."

    if edge_tts is not None:
        try:
            communicate = edge_tts.Communicate(clean_text, voice)
            await communicate.save(output_audio)
            if os.path.exists(output_audio) and os.path.getsize(output_audio) > 256:
                print(f"PROGRESS:40:Voiceover saved ({output_audio})")
                return
        except Exception as e:
            print(f"Edge-TTS primary voice warning ({e}), retrying with fallback voice...", file=sys.stderr)
            try:
                fallback_voice = "en-US-ChristopherNeural" if voice != "en-US-ChristopherNeural" else "en-US-AriaNeural"
                communicate = edge_tts.Communicate(clean_text, fallback_voice)
                await communicate.save(output_audio)
                if os.path.exists(output_audio) and os.path.getsize(output_audio) > 256:
                    print(f"PROGRESS:40:Voiceover saved with fallback ({output_audio})")
                    return
            except Exception as e2:
                print(f"Edge-TTS fallback warning ({e2}), switching to gTTS...", file=sys.stderr)

    if gTTS is not None:
        try:
            tts = gTTS(text=clean_text[:500], lang="en")
            tts.save(output_audio)
            if os.path.exists(output_audio) and os.path.getsize(output_audio) > 256:
                print(f"PROGRESS:40:Voiceover synthesized with gTTS ({output_audio})")
                return
        except Exception as e3:
            print(f"gTTS warning ({e3}), using synthesized studio audio track...", file=sys.stderr)

    words = max(1, len(clean_text.split()))
    est_seconds = max(8, min(45, int(words / 2.5)))
    generate_fallback_audio(output_audio, est_seconds)
    print(f"PROGRESS:40:Fallback studio audio generated ({output_audio})")


def get_audio_duration(audio_file: str) -> float:
    try:
        cmd = [
            "ffprobe", "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            audio_file
        ]
        result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=True)
        val = float(result.stdout.strip())
        return max(3.0, val)
    except Exception:
        return 10.0


def select_background_visual(custom_bg: str = None) -> str:
    if custom_bg and os.path.exists(custom_bg):
        return custom_bg

    templates_dir = os.path.join("storage", "templates")
    if os.path.exists(templates_dir):
        available = [
            os.path.join(templates_dir, f)
            for f in os.listdir(templates_dir)
            if f.lower().endswith((".jpg", ".png", ".jpeg"))
        ]
        if available:
            return random.choice(available)

    return None


def resolve_font_option() -> str:
    candidates = [
        "C:/Windows/Fonts/LeelawUI.ttf",
        "C:/Windows/Fonts/arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            escaped = path.replace("\\", "/").replace(":", "\\:")
            return f"fontfile='{escaped}':"
    return ""


def render_video(audio_file: str, duration: float, title: str, aspect_ratio: str, output_file: str, bg_image: str = None):
    print("PROGRESS:60:Assembling video layers and audio waveform...")

    if aspect_ratio == "LANDSCAPE_16_9":
        w, h = 1280, 720
        card_w, card_h = 960, 260
        wave_w, wave_h = 600, 90
        wave_y = "H-170"
        title_size = 38
        branding_size = 20
    elif aspect_ratio == "SQUARE_1_1":
        w, h = 720, 720
        card_w, card_h = 620, 260
        wave_w, wave_h = 500, 90
        wave_y = "H-190"
        title_size = 38
        branding_size = 20
    else:
        w, h = 720, 1280
        card_w, card_h = 640, 320
        wave_w, wave_h = 540, 100
        wave_y = "H-260"
        title_size = 42
        branding_size = 22

    resolution = f"{w}x{h}"
    safe_title = (
        title.replace("'", "")
        .replace('"', "")
        .replace(":", " -")
        .replace("\\", "/")
        .replace("%", "")
        .replace("\n", " ")
        .strip()[:48]
    )
    if not safe_title:
        safe_title = "AutoClip AI Video"
    branding = "AutoClip Studio"

    font_option = resolve_font_option()
    accent_color = "0x38bdf8"
    waveform_color = "0x38bdf8@0.9"

    if bg_image and os.path.exists(bg_image):
        filter_with_text = (
            f"[0:v]scale={w}:{h}:force_original_aspect_ratio=increase,crop={w}:{h},setsar=1,format=yuv420p[bg];"
            f"[1:a]showwaves=s={wave_w}x{wave_h}:mode=line:colors={waveform_color}:scale=sqrt[wave];"
            f"[bg][wave]overlay=(W-w)/2:{wave_y}[v1];"
            f"[v1]drawbox=x=(w-{card_w})/2:y=(h-{card_h})/2:w={card_w}:h={card_h}:color=black@0.65:t=fill,"
            f"drawtext={font_option}text='{safe_title}':fontcolor=white:fontsize={title_size}:x=(w-text_w)/2:y=(h-text_h)/2-35,"
            f"drawtext={font_option}text='{branding}':fontcolor={accent_color}:fontsize={branding_size}:x=(w-text_w)/2:y=(h-text_h)/2+45,"
            f"drawbox=x=0:y=ih-10:w='iw*(t/{duration})':h=10:color={accent_color}:t=fill[vfinal]"
        )
        cmd_primary = [
            "ffmpeg", "-y",
            "-loop", "1", "-framerate", "24", "-i", bg_image,
            "-i", audio_file,
            "-filter_complex", filter_with_text,
            "-map", "[vfinal]",
            "-map", "1:a",
            "-c:v", "libx264",
            "-preset", "ultrafast",
            "-pix_fmt", "yuv420p",
            "-c:a", "aac",
            "-b:a", "128k",
            "-shortest",
            output_file
        ]
    else:
        filter_with_text = (
            f"[0:v]format=yuv420p[bg];"
            f"[1:a]showwaves=s={wave_w}x{wave_h}:mode=line:colors={waveform_color}:scale=sqrt[wave];"
            f"[bg][wave]overlay=(W-w)/2:{wave_y}[v1];"
            f"[v1]drawbox=x=(w-{card_w})/2:y=(h-{card_h})/2:w={card_w}:h={card_h}:color=black@0.6:t=fill,"
            f"drawtext={font_option}text='{safe_title}':fontcolor=white:fontsize={title_size}:x=(w-text_w)/2:y=(h-text_h)/2-35,"
            f"drawtext={font_option}text='{branding}':fontcolor={accent_color}:fontsize={branding_size}:x=(w-text_w)/2:y=(h-text_h)/2+45,"
            f"drawbox=x=0:y=ih-10:w='iw*(t/{duration})':h=10:color={accent_color}:t=fill[vfinal]"
        )
        cmd_primary = [
            "ffmpeg", "-y",
            "-f", "lavfi", "-i", f"color=c=0x080f1e:s={resolution}:r=24:d={duration}",
            "-i", audio_file,
            "-filter_complex", filter_with_text,
            "-map", "[vfinal]",
            "-map", "1:a",
            "-c:v", "libx264",
            "-preset", "ultrafast",
            "-pix_fmt", "yuv420p",
            "-c:a", "aac",
            "-b:a", "128k",
            "-shortest",
            output_file
        ]

    print("PROGRESS:80:Encoding MP4 video stream...")
    res = subprocess.run(cmd_primary, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if res.returncode != 0:
        print("Primary filter warning, running safe fallback encoder...", file=sys.stderr)
        if bg_image and os.path.exists(bg_image):
            filter_fallback = (
                f"[0:v]scale={w}:{h}:force_original_aspect_ratio=increase,crop={w}:{h},setsar=1,format=yuv420p[bg];"
                f"[1:a]showwaves=s={wave_w}x{wave_h}:mode=line:colors={waveform_color}:scale=sqrt[wave];"
                f"[bg][wave]overlay=(W-w)/2:{wave_y}[vfinal]"
            )
            cmd_fallback = [
                "ffmpeg", "-y",
                "-loop", "1", "-framerate", "24", "-i", bg_image,
                "-i", audio_file,
                "-filter_complex", filter_fallback,
                "-map", "[vfinal]",
                "-map", "1:a",
                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                "-b:a", "128k",
                "-shortest",
                output_file
            ]
        else:
            cmd_fallback = [
                "ffmpeg", "-y",
                "-f", "lavfi", "-i", f"color=c=0x080f1e:s={resolution}:r=24:d={duration}",
                "-i", audio_file,
                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                "-b:a", "128k",
                "-shortest",
                output_file
            ]
        subprocess.run(cmd_fallback, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    try:
        thumb_dir = os.path.join(os.path.dirname(os.path.dirname(output_file)), "thumbnails")
        os.makedirs(thumb_dir, exist_ok=True)
        base_name = os.path.splitext(os.path.basename(output_file))[0]
        thumb_file = os.path.join(thumb_dir, f"{base_name}.jpg")
        thumb_cmd = [
            "ffmpeg", "-y",
            "-ss", "00:00:01",
            "-i", output_file,
            "-vframes", "1",
            "-q:v", "2",
            thumb_file
        ]
        subprocess.run(thumb_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    except Exception as e:
        print(f"Warning: could not generate thumbnail image: {e}")

    print("PROGRESS:100:Video rendering completed successfully!")


async def main():
    args = parse_args()
    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)

    bg_visual = select_background_visual(args.background_image)
    if bg_visual:
        print(f"PROGRESS:15:Selected background visual: {bg_visual}")

    if args.audio_file and os.path.exists(args.audio_file):
        print(f"PROGRESS:30:Using custom audio recording ({args.audio_file})...")
        duration = get_audio_duration(args.audio_file)
        render_video(args.audio_file, duration, args.title, args.aspect_ratio, args.output, bg_visual)
    else:
        with tempfile.TemporaryDirectory() as tmp_dir:
            temp_audio = os.path.join(tmp_dir, "speech.mp3")
            if args.cloned_profile and os.path.exists(args.cloned_profile) and synthesize_cloned_voice is not None:
                print(f"PROGRESS:20:Synthesizing speech with voice profile ({args.cloned_profile})...")
                try:
                    with open(args.cloned_profile, "r", encoding="utf-8") as f:
                        profile = json.load(f)
                    await synthesize_cloned_voice(args.text, profile, temp_audio)
                    print(f"PROGRESS:40:Voiceover synthesized ({temp_audio})")
                except Exception:
                    await generate_voiceover(args.text, args.voice, temp_audio)
            else:
                await generate_voiceover(args.text, args.voice, temp_audio)
            duration = get_audio_duration(temp_audio)
            render_video(temp_audio, duration, args.title, args.aspect_ratio, args.output, bg_visual)


if __name__ == "__main__":
    asyncio.run(main())
