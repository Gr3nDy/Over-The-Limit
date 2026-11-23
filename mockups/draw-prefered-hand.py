"""Draw one pixel hand with one wrist and update the self-contained menu preview."""
import base64
import re
from pathlib import Path

from PIL import Image, ImageDraw

root = Path(__file__).parent
path = root / "module-scenes" / "left-hand.png"
im = Image.new("RGBA", (64, 44))
d = ImageDraw.Draw(im)
skin, light, shade, cuff = "#d9a37c", "#f2c398", "#a87155", "#648ab3"

# One open palm, five fingers and a single cuff; transparent background.
d.polygon([(15, 29), (22, 20), (24, 12), (27, 11), (29, 19),
           (31, 7), (34, 6), (35, 18), (40, 10), (43, 11),
           (41, 21), (46, 17), (49, 19), (44, 29),
           (34, 36), (23, 37)], fill=skin)
d.polygon([(19, 30), (25, 23), (27, 24), (24, 31), (31, 34),
           (36, 32), (36, 35), (23, 37)], fill=shade)
d.line([(23, 19), (24, 12), (27, 11)], fill=light, width=2)
d.line([(31, 14), (32, 8), (34, 7)], fill=light, width=2)
d.line([(38, 16), (41, 11)], fill=light, width=2)
d.polygon([(13, 32), (21, 35), (25, 40), (20, 43), (9, 36)], fill=cuff)

assert im.getbbox() and im.getpixel((0, 0))[3] == 0
im.save(path)
html = root / "module-desk.html"
text = html.read_text("utf-8")
data = base64.b64encode(path.read_bytes()).decode("ascii")
text, count = re.subn(
    r'("left-hand": "data:image/png;base64,)[^"]+',
    lambda match: match.group(1) + data,
    text,
    count=1,
)
assert count == 1
html.write_text(text, "utf-8")
