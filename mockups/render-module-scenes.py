"""Paint six transparent PNG sprites and embed them in the module menu preview."""
import base64
import re
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).parent
folder = root / "module-scenes"
folder.mkdir(exist_ok=True)
html = (root / "module-menu-v6.html").read_text("utf-8")
images = []
ink, cream, gold = "#202431", "#fff0cf", "#ffbe68"
for key in ["team-bar", "kill-feed", "reliable-chat", "map-preview", "face-rig", "player-animation"]:
    im = Image.new("RGBA", (64, 44))
    d = ImageDraw.Draw(im)
    rect, poly, line = d.rectangle, d.polygon, d.line
    # A common three-face block and a tiny offset shadow give the scenes one perspective.
    def block(x, y, w, h, front, top, side):
        rect((x,y,x+w-1,y+h-1), front)
        poly([(x,y-1),(x+5,y-5),(x+w+4,y-5),(x+w-1,y-1)], top)
        poly([(x+w,y),(x+w+4,y-4),(x+w+4,y+h-5),(x+w,y+h-1)], side)

    if key == "team-bar":
        for x, y, a, b, c in [(6,18,"#3476a2","#9ee9fa","#21546e"),(40,13,"#bf5953","#ffb796","#763b40")]:
            block(x,y,13,12,a,b,c)
            rect((x+2,y+5,x+10,y+8),cream)
            rect((x+5,y+5,x+7,y+8),ink)
            block(x-2,y+15,17,3,a,b,c)
        rect((30,18,31,19),gold); rect((30,24,31,25),gold)
    elif key == "kill-feed":
        for i,(x,y,col) in enumerate([(25,6,"#be7163"),(16,17,"#e39870"),(7,28,"#ffba78")]):
            block(x,y,28,7,col,cream,"#82495a")
            rect((x+3,y+2,x+5,y+4),ink)
            rect((x+9,y+3,x+21-i*2,y+3),ink)
            line((x-6,y+3,x-3,y+3),col)
    elif key == "reliable-chat":
        for x,y,col,top,side in [(26,9,"#7768be","#c9c2ff","#4f4584"),(8,21,"#9ec6f0","#deedff","#608aac")]:
            block(x,y,25,12,col,top,side)
            poly([(x+4,y+11),(x+4,y+17),(x+11,y+11)],col)
            for dx in (5,11,17): rect((x+dx,y+5,x+dx+2,y+7),ink)
    elif key == "map-preview":
        for x,y,dy,col in [(6,18,-6,"#d8c197"),(22,12,5,"#fae4af"),(38,17,-6,"#c7b385")]:
            poly([(x,y),(x+15,y+dy),(x+15,y+dy+20),(x,y+20)],col)
        line([(10,31),(20,24),(29,27),(36,21),(43,22)],"#548c88",3)
        rect((43,6,44,21),ink); poly([(45,5),(56,5),(53,8),(56,11),(45,11)],"#f76e56")
    elif key == "face-rig":
        block(16,12,24,24,"#e6ad82","#ae725a","#9b6253")
        rect((16,12,39,16),"#75434a"); rect((16,17,20,20),"#75434a")
        for x,y in [(21,22),(32,20)]:
            rect((x,y,x+4,y+3),cream); rect((x+2,y,x+3,y+3),ink)
        line([(25,29),(27,31),(31,31),(34,28)],"#83454a",2)
        for x,y in [(8,16),(51,23)]:
            line((x-2,y,x+2,y),gold); line((x,y-2,x,y+2),gold)
    else:
        # Deck mid-kickturn with a bent knee and high-top sneaker.
        poly([(9,32),(13,34),(46,27),(54,21),(56,23),(49,31),(14,39),(8,36)],"#49b8b1")
        line([(9,32),(14,35),(47,28),(54,21)],"#acf2cf",2)
        for x,y in [(18,39),(43,33)]: rect((x,y,x+3,y+3),gold)
        poly([(32,3),(42,5),(38,15),(32,17),(28,25),(20,23),(24,12),(29,11)],"#7c94cc")
        poly([(20,22),(28,24),(28,27),(37,27),(38,30),(18,33),(17,30)],cream)
        for x,y in [(6,24),(10,19),(48,15)]: line((x,y,x+5,y-1),"#79a5b8",1)
    path = folder / (key + ".png")
    assert im.getbbox() and im.getpixel((0,0))[3] == 0
    im.save(path)
    data = base64.b64encode(path.read_bytes()).decode()
    images.append(f'<img alt="" width="64" height="44" src="data:image/png;base64,{data}">')

it = iter(images)
html, n = re.subn(r"<svg\b.*?</svg>", lambda _: next(it), html, flags=re.S)
assert n == len(images)
html = html.replace("</style>", ".art img{image-rendering:pixelated}.card{height:96px}.intro{width:auto}</style>")
html = re.sub(r'<p class="intro">.*?</p>', '<p class="intro"><b>OTL Modules</b> · Pixel artwork preview</p>', html)
(root / "module-menu-pixel.html").write_text(html, "utf-8")
