import argparse
import asyncio
import json
import os
import subprocess
import sys
import tempfile
import numpy as np

sys.stdout.reconfigure(encoding='utf-8')

try:
    import edge_tts
except ImportError:
    print("ERROR: edge-tts is not installed.", file=sys.stderr)
    sys.exit(1)

BASE_VOICE_PITCHES = {
    "km-KH-PisethNeural": 140.0,
    "km-KH-SreymomNeural": 220.0,
    "en-US-GuyNeural": 125.0,
    "en-US-JennyNeural": 210.0,
    "en-US-ChristopherNeural": 130.0,
    "en-US-AriaNeural": 215.0,
    "en-US-EricNeural": 110.0,
    "en-GB-RyanNeural": 135.0,
}

def analyze_audio_profile(audio_file: str, base_voice: str = "km-KH-PisethNeural") -> dict:
    """
    Analyzes an uploaded voice recording to extract:
    1. Fundamental frequency (F0 / Pitch in Hz) using autocorrelation.
    2. Pitch delta relative to base TTS voice (e.g. +10Hz, -25Hz).
    3. Multi-band spectral energy profile (bass, low_mid, mid, presence, brilliance).
    4. Equivalent FFmpeg parametric EQ filter string.
    """
    if not os.path.exists(audio_file):
        raise FileNotFoundError(f"Audio file not found: {audio_file}")

    cmd = [
        "ffmpeg", "-y", "-i", audio_file,
        "-f", "f32le", "-ac", "1", "-ar", "16000", "-t", "15", "pipe:1"
    ]
    proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=True)
    data = np.frombuffer(proc.stdout, dtype=np.float32)

    if len(data) < 16000:
        raise ValueError("Audio recording is too short. Please provide at least 1-2 seconds of speech.")

    frame_size = int(16000 * 0.04)
    hop_size = int(16000 * 0.02)
    min_lag = int(16000 / 400)
    max_lag = int(16000 / 70)

    pitches = []
    for i in range(0, len(data) - frame_size, hop_size):
        frame = data[i:i + frame_size]
        rms = np.sqrt(np.mean(frame ** 2))
        if rms < 0.02:
            continue
        corr = np.correlate(frame, frame, mode='full')
        corr = corr[len(frame) - 1:]
        peak_lag = min_lag + np.argmax(corr[min_lag:max_lag])
        if peak_lag > 0:
            pitch = 16000 / peak_lag
            if 70 <= pitch <= 400:
                pitches.append(pitch)

    detected_pitch = float(np.median(pitches)) if pitches else BASE_VOICE_PITCHES.get(base_voice, 140.0)

    ref_pitch = BASE_VOICE_PITCHES.get(base_voice, 140.0)
    pitch_diff = int(round(detected_pitch - ref_pitch))
    pitch_diff = max(-50, min(50, pitch_diff))
    pitch_delta_str = f"{pitch_diff:+d}Hz"

    fft_vals = np.abs(np.fft.rfft(data))
    freqs = np.fft.rfftfreq(len(data), 1.0 / 16000)

    def band_energy(low, high):
        idx = np.where((freqs >= low) & (freqs < high))[0]
        return float(np.mean(fft_vals[idx])) if len(idx) > 0 else 0.0

    bass = band_energy(60, 250)
    mid = band_energy(500, 2000)
    presence = band_energy(2000, 4000)
    high = band_energy(4000, 8000)

    total_energy = bass + mid + presence + high + 1e-6
    bass_ratio = bass / total_energy
    presence_ratio = presence / total_energy

    bass_gain = round(max(-4.0, min(4.0, (bass_ratio - 0.4) * 10.0)), 1)
    presence_gain = round(max(-3.0, min(3.0, (presence_ratio - 0.1) * 15.0)), 1)

    eq_filter = (
        f"equalizer=f=120:width_type=h:width=100:g={bass_gain},"
        f"equalizer=f=3200:width_type=h:width=1000:g={presence_gain}"
    )

    profile = {
        "base_voice": base_voice,
        "detected_pitch_hz": round(detected_pitch, 1),
        "reference_pitch_hz": ref_pitch,
        "pitch_delta": pitch_delta_str,
        "rate_delta": "+0%",
        "bass_gain_db": bass_gain,
        "presence_gain_db": presence_gain,
        "eq_filter": eq_filter
    }
    return profile

async def synthesize_cloned_voice(text: str, profile: dict, output_file: str):
    """
    Synthesizes speech matching the cloned voice profile:
    1. Edge-TTS produces speech with adjusted pitch and rate.
    2. FFmpeg applies spectral EQ to mold the acoustic timbre.
    """
    base_voice = profile.get("base_voice", "km-KH-PisethNeural")
    pitch_delta = profile.get("pitch_delta", "+0Hz")
    rate_delta = profile.get("rate_delta", "+0%")
    eq_filter = profile.get("eq_filter", "")

    os.makedirs(os.path.dirname(os.path.abspath(output_file)), exist_ok=True)

    with tempfile.TemporaryDirectory() as tmp_dir:
        raw_tts_path = os.path.join(tmp_dir, "raw_tts.mp3")
        communicate = edge_tts.Communicate(text, base_voice, pitch=pitch_delta, rate=rate_delta)
        await communicate.save(raw_tts_path)

        if eq_filter:
            cmd = [
                "ffmpeg", "-y",
                "-i", raw_tts_path,
                "-af", eq_filter,
                "-c:a", "libmp3lame",
                "-b:a", "192k",
                output_file
            ]
            subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
        else:
            import shutil
            shutil.copyfile(raw_tts_path, output_file)

def main():
    parser = argparse.ArgumentParser(description="AutoClip Voice Cloner Engine")
    parser.add_argument("--analyze", help="Path to audio file to extract voice profile from")
    parser.add_argument("--base_voice", default="km-KH-PisethNeural", help="Base TTS voice")
    parser.add_argument("--synthesize", action="store_true", help="Synthesize cloned voice speech")
    parser.add_argument("--profile_json", help="Voice profile JSON string or path to JSON file")
    parser.add_argument("--text", help="Text to speak")
    parser.add_argument("--output", help="Output MP3 path")

    args = parser.parse_args()

    if args.analyze:
        profile = analyze_audio_profile(args.analyze, args.base_voice)
        print(json.dumps(profile, indent=2))
        return

    if args.synthesize:
        if not args.profile_json or not args.text or not args.output:
            print("ERROR: --profile_json, --text, and --output are required for synthesis.", file=sys.stderr)
            sys.exit(1)

        if os.path.exists(args.profile_json):
            with open(args.profile_json, "r", encoding="utf-8") as f:
                profile = json.load(f)
        else:
            profile = json.loads(args.profile_json)

        asyncio.run(synthesize_cloned_voice(args.text, profile, args.output))
        print(f"SUCCESS: Synthesized cloned voice speech to {args.output}")

if __name__ == "__main__":
    main()