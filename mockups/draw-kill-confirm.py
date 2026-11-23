"""Replace the Kill Confirm card art with a pixel skull and refresh its preview data URI."""
import base64
import re
from pathlib import Path

from PIL import Image, ImageDraw

root = Path(__file__).parent
path = root / "module-scenes" / "kill-confirm.png"
im = Image.new("RGBA", (64, 44))
d = ImageDraw.Draw(im)

# Chunky bone silhouette: flat colors and single-pixel edges survive downscaling.
bone, shade, dark, glow = "#f4ead1", "#bdb8af", "#29323a", "#ef9b77"
d.polygon([(21, 5), (42, 5), (48, 10), (48, 26), (43, 31),
           (40, 31), (40, 37), (24, 37), (24, 31), (20, 31),
           (16, 26), (16, 10)], fill=bone)
d.rectangle((19, 24, 44, 27), fill=shade)
d.rectangle((24, 28, 39, 36), fill=shade)
d.rectangle((23, 16, 29, 22), fill=dark)
d.rectangle((35, 16, 41, 22), fill=dark)
d.polygon([(32, 22), (29, 27), (35, 27)], fill=dark)
for x in (27, 31, 36):
    d.rectangle((x, 31, x + 1, 36), fill=dark)
d.rectangle((18, 11, 20, 13), fill=glow)
d.rectangle((43, 10, 45, 12), fill=glow)

assert im.getbbox() and im.getpixel((0, 0))[3] == 0
path.parent.mkdir(exist_ok=True)
im.save(path)
html = root / "module-desk.html"
text = html.read_text("utf-8")
data = base64.b64encode(path.read_bytes()).decode("ascii")
text, count = re.subn(
    r'("kill-confirm": "data:image/png;base64,)[^"]+',
    lambda match: match.group(1) + data,
    text,
    count=1,
)
assert count == 1
html.write_text(text, "utf-8")
