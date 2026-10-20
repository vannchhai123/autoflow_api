import argparse
import asyncio
import json
import os
import random
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request

sys.stdout.reconfigure(encoding='utf-8')

from voice_cloner import synthesize_cloned_voice

try:
    import edge_tts
except ImportError:
    print("ERROR: edge-tts is not installed. Run: pip install edge-tts", file=sys.stderr)
    sys.exit(1)

def parse_args():
    parser = argparse.ArgumentParser(description="AutoClip Upgraded Animated Video Generation Engine")
    parser.add_argument("--title", required=True, help="Video title to display")
    parser.add_argument("--text", required=True, help="Script text for voiceover")
    parser.add_argument("--aspect_ratio", default="PORTRAIT_9_16", choices=["PORTRAIT_9_16", "LANDSCAPE_16_9", "SQUARE_1_1"], help="Aspect ratio")
    parser.add_argument("--output", required=True, help="Target MP4 output file path")
    parser.add_argument("--voice", default="km-KH-PisethNeural", help="TTS voice model")
    parser.add_argument("--audio_file", default=None, help="Path to real custom uploaded audio file")
    parser.add_argument("--cloned_profile", default=None, help="Path to cloned voice profile JSON")
    parser.add_argument("--background_image", default=None, help="Path to custom background image")
    return parser.parse_args()

async def generate_voiceover(text: str, voice: str, output_audio: str):
    print("PROGRESS:20:Generating AI voiceover...")
    clean_text = text.strip() if text else ""
    if not clean_text or clean_text.replace("?", "").strip() == "":
        clean_text = "សួស្តី នេះជាវីដេអូរបស់ AutoClip"
    try:
        communicate = edge_tts.Communicate(clean_text, voice)
        await communicate.save(output_audio)
    except Exception as e:
        print(f"Voiceover warning ({e}), retrying with default voice...", file=sys.stderr)
        communicate = edge_tts.Communicate(clean_text, "km-KH-PisethNeural")
        await communicate.save(output_audio)
    print(f"PROGRESS:40:Voiceover saved ({output_audio})")

def get_audio_duration(audio_file: str) -> float:
    cmd = [
        "ffprobe", "-v", "error",
        "-show_entries", "format=duration",
        "-of", "default=noprint_wrappers=1:nokey=1",
        audio_file
    ]
    result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=True)
    return float(result.stdout.strip())

def select_background_visual(custom_bg: str = None) -> str:
    """
    Selects the best visual for the video background:
    1. Custom background image if provided and exists.
    2. Curated glowing aesthetic templates in storage/templates/.
    """
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

def render_video(audio_file: str, duration: float, title: str, aspect_ratio: str, output_file: str, bg_image: str = None):
    print("PROGRESS:60:Assembling dynamic video layers (Ken Burns + Waveform + Progress Bar)...")

    if aspect_ratio == "LANDSCAPE_16_9":
        resolution = "1920x1080"
        scale_w, scale_h = 2134, 1200
        card_w, card_h = 1350, 360
        wave_w, wave_h = 800, 120
        wave_y = "H-240"
        title_size = 52
        branding_size = 28
    elif aspect_ratio == "SQUARE_1_1":
        resolution = "1080x1080"
        scale_w, scale_h = 1200, 1200
        card_w, card_h = 920, 380
        wave_w, wave_h = 700, 130
        wave_y = "H-280"
        title_size = 56
        branding_size = 30
    else:
        resolution = "1080x1920"
        scale_w, scale_h = 1200, 2134
        card_w, card_h = 940, 460
        wave_w, wave_h = 760, 140
        wave_y = "H-380"
        title_size = 64
        branding_size = 32

    safe_title = title.replace("'", "").replace(":", "-").replace("\\", "/")
    branding = "AutoClip Studio • Short Video"

    font_path = "C:/Windows/Fonts/LeelawUI.ttf"
    font_option = f"fontfile='C\\:/Windows/Fonts/LeelawUI.ttf':" if os.path.exists(font_path) else ""
    duration_frames = max(30, int(duration * 30))

    accent_color = "0x38bdf8"
    waveform_color = "0x38bdf8@0.9"

    if bg_image and os.path.exists(bg_image):
        filter_complex = (
            f"[0:v]scale={scale_w}:{scale_h},zoompan=z='min(zoom+0.0015,1.25)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d={duration_frames}:s={resolution}:fps=30[bg];"
            f"[1:a]showwaves=s={wave_w}x{wave_h}:mode=line:colors={waveform_color}:scale=sqrt[wave];"
            f"[bg][wave]overlay=(W-w)/2:{wave_y}[v1];"
            f"[v1]drawbox=x=(w-{card_w})/2:y=(h-{card_h})/2:w={card_w}:h={card_h}:color=black@0.65:t=fill,"
            f"drawtext={font_option}text='{safe_title}':fontcolor=white:fontsize={title_size}:x=(w-text_w)/2:y=(h-text_h)/2-50,"
            f"drawtext={font_option}text='{branding}':fontcolor={accent_color}:fontsize={branding_size}:x=(w-text_w)/2:y=(h-text_h)/2+60,"
            f"drawbox=x=0:y=ih-14:w='iw*(t/{duration})':h=14:color={accent_color}:t=fill[vfinal]"
        )
        cmd = [
            "ffmpeg", "-y",
            "-loop", "1", "-i", bg_image,
            "-i", audio_file,
            "-filter_complex", filter_complex,
            "-map", "[vfinal]",
            "-map", "1:a",
            "-c:v", "libx264",
            "-preset", "ultrafast",
            "-c:a", "aac",
            "-b:a", "192k",
            "-shortest",
            output_file
        ]
    else:
        filter_complex = (
            f"[0:v]format=yuv420p[bg];"
            f"[1:a]showwaves=s={wave_w}x{wave_h}:mode=line:colors={waveform_color}:scale=sqrt[wave];"
            f"[bg][wave]overlay=(W-w)/2:{wave_y}[v1];"
            f"[v1]drawbox=x=(w-{card_w})/2:y=(h-{card_h})/2:w={card_w}:h={card_h}:color=black@0.6:t=fill,"
            f"drawtext={font_option}text='{safe_title}':fontcolor=white:fontsize={title_size}:x=(w-text_w)/2:y=(h-text_h)/2-50,"
            f"drawtext={font_option}text='{branding}':fontcolor={accent_color}:fontsize={branding_size}:x=(w-text_w)/2:y=(h-text_h)/2+60,"
            f"drawbox=x=0:y=ih-14:w='iw*(t/{duration})':h=14:color={accent_color}:t=fill[vfinal]"
        )
        cmd = [
            "ffmpeg", "-y",
            "-f", "lavfi", "-i", f"color=c=0x080f1e:s={resolution}:d={duration}",
            "-i", audio_file,
            "-filter_complex", filter_complex,
            "-map", "[vfinal]",
            "-map", "1:a",
            "-c:v", "libx264",
            "-preset", "ultrafast",
            "-c:a", "aac",
            "-b:a", "192k",
            "-shortest",
            output_file
        ]

    print("PROGRESS:80:Encoding video stream with active motion and visualizer...")
    subprocess.run(cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

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
            if args.cloned_profile and os.path.exists(args.cloned_profile):
                print(f"PROGRESS:20:Synthesizing speech with voice profile ({args.cloned_profile})...")
                with open(args.cloned_profile, "r", encoding="utf-8") as f:
                    profile = json.load(f)
                await synthesize_cloned_voice(args.text, profile, temp_audio)
                print(f"PROGRESS:40:Voiceover synthesized ({temp_audio})")
            else:
                await generate_voiceover(args.text, args.voice, temp_audio)
            duration = get_audio_duration(temp_audio)
            render_video(temp_audio, duration, args.title, args.aspect_ratio, args.output, bg_visual)

if __name__ == "__main__":
    asyncio.run(main())