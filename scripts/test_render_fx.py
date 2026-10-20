import subprocess
import os

font_path = "C:/Windows/Fonts/LeelawUI.ttf"
font_option = f"fontfile='C\\:/Windows/Fonts/LeelawUI.ttf':" if os.path.exists(font_path) else ""

duration = 4.36
cmd = [
    "ffmpeg", "-y",
    "-loop", "1", "-i", "storage/templates/test_bg.jpg",
    "-i", "storage/videos/test_khmer.mp3",
    "-filter_complex", (
        "[0:v]scale=1200:2134,zoompan=z='min(zoom+0.0015,1.25)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=130:s=1080x1920:fps=30[bg];"
        "[1:a]showwaves=s=760x140:mode=line:colors=0x38bdf8@0.9:scale=sqrt[wave];"
        "[bg][wave]overlay=(W-w)/2:H-380[v1];"
        f"[v1]drawbox=x=(w-940)/2:y=(h-460)/2:w=940:h=460:color=black@0.65:t=fill,"
        f"drawtext={font_option}text='៣ ជំហានចាប់ផ្ដើមថ្ងៃថ្មី':fontcolor=white:fontsize=64:x=(w-text_w)/2:y=(h-text_h)/2-50,"
        f"drawtext={font_option}text='AutoClip Studio • Short Video':fontcolor=0x38bdf8:fontsize=32:x=(w-text_w)/2:y=(h-text_h)/2+60,"
        f"drawbox=x=0:y=ih-14:w='iw*(t/{duration})':h=14:color=0x38bdf8:t=fill[vfinal]"
    ),
    "-map", "[vfinal]",
    "-map", "1:a",
    "-c:v", "libx264",
    "-preset", "ultrafast",
    "-c:a", "aac",
    "-b:a", "192k",
    "-shortest",
    "storage/videos/test_upgraded_preview.mp4"
]

res = subprocess.run(cmd, capture_output=True, text=True)
if res.returncode == 0:
    print("SUCCESS: Rendered upgraded video! Size:", os.path.getsize("storage/videos/test_upgraded_preview.mp4"), "bytes")
else:
    print("FFmpeg Error:", res.stderr[-500:])