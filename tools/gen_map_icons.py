"""Génère les icônes de la carte (32x32) dans src/main/resources/assets/minenorth_map/textures/map_icons/.
Usage : python tools/gen_map_icons.py    (nécessite Pillow). Les couleurs doivent rester identiques à MapIcons.java."""
import math, os
from PIL import Image, ImageDraw

S = 8            # suréchantillonnage
N = 32 * S
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "minenorth_map", "textures", "map_icons")

WHITE = (255, 255, 255, 255)

def u(v): return v * S

def shade(c, f):
    return tuple(max(0, min(255, int(x * f))) for x in c) + (255,)

class Ctx:
    def __init__(self, d, col):
        self.d, self.col = d, col
        self.cut = shade(col, 0.62)      # couleur des détails « creux » du pictogramme
    def rect(self, x0, y0, x1, y1, fill=WHITE, r=0):
        if r: self.d.rounded_rectangle([u(x0), u(y0), u(x1), u(y1)], radius=u(r), fill=fill)
        else: self.d.rectangle([u(x0), u(y0), u(x1), u(y1)], fill=fill)
    def poly(self, pts, fill=WHITE): self.d.polygon([(u(x), u(y)) for x, y in pts], fill=fill)
    def circ(self, cx, cy, r, fill=WHITE, outline=None, w=0):
        self.d.ellipse([u(cx - r), u(cy - r), u(cx + r), u(cy + r)], fill=fill, outline=outline, width=int(u(w)) if w else 0)
    def line(self, pts, w=1.5, fill=WHITE): self.d.line([(u(x), u(y)) for x, y in pts], fill=fill, width=int(u(w)), joint="curve")

def garage(c):
    c.poly([(7, 15), (16, 7.5), (25, 15)]); c.rect(9.5, 15, 22.5, 24.5); c.rect(12.5, 18, 19.5, 24.5, c.cut)
    for y in (19.8, 21.6, 23.4): c.rect(12.5, y, 19.5, y + 0.5, WHITE)
def concession(c):
    c.rect(7, 16, 25, 21, r=2); c.poly([(10.5, 16), (13, 11.5), (19.5, 11.5), (22, 16)])
    c.poly([(12.2, 15), (13.8, 12.5), (15.6, 12.5), (15.6, 15)], c.cut); c.poly([(16.6, 15), (16.6, 12.5), (19, 12.5), (20.4, 15)], c.cut)
    for x in (11.5, 20.5): c.circ(x, 21.5, 2.7, WHITE); c.circ(x, 21.5, 1.1, c.cut)
def boutique(c):
    c.line([(12.5, 14), (12.5, 11), (14, 8.7), (18, 8.7), (19.5, 11), (19.5, 14)], 1.5)
    c.rect(8.5, 13, 23.5, 25, r=1.5); c.circ(13, 16.5, 1, c.cut); c.circ(19, 16.5, 1, c.cut)
def banque(c):
    c.poly([(6.5, 13), (16, 6.5), (25.5, 13)]); c.rect(8, 22.5, 24, 25)
    for x in (9.5, 14.8, 20.1): c.rect(x, 14, x + 2.4, 22)
    c.circ(16, 11.2, 1.3, c.cut)
def police(c):
    c.poly([(16, 6.5), (24, 9), (24, 16), (20, 21.5), (16, 25), (12, 21.5), (8, 16), (8, 9)])
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5; r = 5.2 if i % 2 == 0 else 2.2
        pts.append((16 + r * math.cos(a), 15.5 + r * math.sin(a)))
    c.poly(pts, c.cut)
def hopital(c):
    c.rect(13.6, 7.5, 18.4, 24.5, r=1); c.rect(7.5, 13.6, 24.5, 18.4, r=1)
def pompier(c):
    c.poly([(16, 6), (19.5, 11), (22.5, 15), (23, 19.5), (20.5, 23.5), (16, 25.5), (11.5, 23.5), (9, 19.5), (9.5, 15), (12.5, 12), (13.5, 15), (15, 12)])
    c.poly([(16, 15), (18.8, 19), (18.5, 22.5), (16, 24), (13.5, 22.5), (13.2, 19)], c.cut)
def restaurant(c):
    c.rect(9, 7.5, 10.4, 14); c.rect(11.8, 7.5, 13.2, 14); c.rect(14.6, 7.5, 16, 14)
    c.rect(9, 12.5, 16, 15, r=1); c.rect(11.8, 14, 13.2, 25, r=0.6)
    c.poly([(20, 7.5), (22.6, 9), (22.6, 16), (20, 16)]); c.rect(20, 14.5, 22, 25, r=0.6)
def bar(c):
    c.poly([(8.5, 8.5), (23.5, 8.5), (16, 17.5)]); c.rect(15, 17, 17, 23); c.rect(11.5, 23, 20.5, 25, r=1)
    c.line([(18, 6), (21, 11.5)], 1.1, c.cut); c.circ(20.5, 10, 1.6, c.cut)
def livraison(c):
    c.poly([(7, 13), (16, 8), (25, 13), (25, 22.5), (16, 25.5), (7, 22.5)])
    c.line([(7, 13), (16, 18), (25, 13)], 1, c.cut); c.line([(16, 18), (16, 25.5)], 1, c.cut)
    c.poly([(11.5, 10.5), (20.5, 15.5), (20.5, 17.5), (11.5, 12.5)], c.cut)
def station(c):
    c.rect(8.5, 7.5, 19, 25, r=1.5); c.rect(10.5, 9.5, 17, 15, c.cut, r=0.6); c.rect(7.5, 23.5, 20, 25.5)
    c.line([(19, 12), (22, 14), (22.5, 20.5)], 1.6); c.circ(22.5, 21.5, 1.6)
def mairie(c):
    c.poly([(7, 13.5), (16, 7), (25, 13.5)]); c.rect(8.5, 14, 23.5, 25)
    c.circ(16, 10.8, 1.5, c.cut); c.rect(14, 18, 18, 25, c.cut); c.rect(10, 16.5, 12, 20, c.cut); c.rect(20, 16.5, 22, 20, c.cut)
def casino(c):
    c.rect(7.5, 7.5, 24.5, 24.5, r=3.2)
    for x, y in ((11.5, 11.5), (20.5, 11.5), (16, 16), (11.5, 20.5), (20.5, 20.5)): c.circ(x, y, 1.7, c.cut)
def sport(c):
    c.rect(10, 14.8, 22, 17.2); c.rect(7.5, 10.5, 11, 21.5, r=1); c.rect(21, 10.5, 24.5, 21.5, r=1)
    c.rect(5, 13, 7.5, 19, r=0.8); c.rect(24.5, 13, 27, 19, r=0.8)
def entreprise(c):
    c.line([(12.5, 13), (12.5, 9.8), (19.5, 9.8), (19.5, 13)], 1.7)
    c.rect(7, 12.5, 25, 24, r=2); c.rect(7, 17, 25, 17.8, c.cut); c.rect(14.6, 15.8, 17.4, 19.6, WHITE, r=0.5); c.rect(15.3, 16.5, 16.7, 19, c.cut)
def atelier(c):
    pts = []
    for i in range(16):
        a = i * math.pi / 8; r = 9.6 if i % 2 == 0 else 7.2
        for da in (-0.17, 0.17): pts.append((16 + r * math.cos(a + da), 16 + r * math.sin(a + da)))
    c.poly(pts); c.circ(16, 16, 3.4, c.cut)
def gang(c):
    c.rect(10.6, 6.5, 12.6, 25.5, r=0.8); c.poly([(12.6, 7.5), (24.5, 11.5), (12.6, 16)])
    c.circ(17.2, 11.6, 1.5, c.cut)
def maison(c):
    c.rect(19.5, 7.5, 22, 13); c.poly([(6, 16), (16, 7), (26, 16)]); c.rect(9.5, 15.5, 22.5, 25)
    c.rect(14, 19, 18, 25, c.cut); c.rect(11, 17.5, 13, 19.5, c.cut)
def aeroport(c):
    c.poly([(16, 5.5), (17.6, 11), (26, 17), (26, 19.4), (17.5, 16.6), (17.2, 22), (20.5, 24.2), (20.5, 25.8), (16, 24.6), (11.5, 25.8), (11.5, 24.2), (14.8, 22), (14.5, 16.6), (6, 19.4), (6, 17), (14.4, 11)])
def port(c):
    c.circ(16, 8.8, 2.3, fill=None, outline=WHITE, w=1.4); c.line([(16, 11), (16, 24)], 1.8); c.line([(12, 14), (20, 14)], 1.6)
    c.d.arc([u(8.5), u(10.5), u(23.5), u(26)], 10, 170, fill=WHITE, width=int(u(1.8)))
    c.poly([(7, 18.5), (10.6, 17.2), (9.6, 21.3)]); c.poly([(25, 18.5), (21.4, 17.2), (22.4, 21.3)])
def transport(c):
    c.rect(7.5, 8, 24.5, 22, r=2.5); c.rect(9.5, 10, 14, 15, c.cut, r=0.6); c.rect(15.2, 10, 19.8, 15, c.cut, r=0.6)
    c.rect(21, 10, 23, 15, c.cut, r=0.6); c.rect(7.5, 17.4, 24.5, 18.2, c.cut)
    for x in (11.5, 20.5): c.circ(x, 23, 2.2); c.circ(x, 23, 0.8, c.cut)
def loisir(c):
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5; r = 9.6 if i % 2 == 0 else 4.2
        pts.append((16 + r * math.cos(a), 16.6 + r * math.sin(a)))
    c.poly(pts)
def recolte(c):
    c.poly([(8, 24), (8.5, 15), (14, 8.5), (24, 7.5), (24, 15), (18, 22), (10.5, 24.2)])
    c.line([(9, 24), (19.5, 12)], 1.2, c.cut)

# id -> (couleur, dessin)
ICONS = {
    "garage": ((96, 125, 139), garage), "concession": ((0, 150, 199), concession), "boutique": ((251, 140, 0), boutique),
    "banque": ((46, 125, 50), banque), "police": ((57, 73, 171), police), "hopital": ((229, 57, 53), hopital),
    "pompier": ((216, 67, 21), pompier), "restaurant": ((255, 112, 67), restaurant), "bar": ((142, 36, 170), bar),
    "livraison": ((255, 170, 51), livraison), "station": ((0, 137, 123), station), "mairie": ((121, 85, 72), mairie),
    "casino": ((198, 40, 90), casino), "sport": ((67, 160, 71), sport), "entreprise": ((30, 136, 229), entreprise),
    "atelier": ((117, 117, 117), atelier), "gang": ((55, 71, 79), gang), "maison": ((141, 110, 99), maison),
    "aeroport": ((3, 155, 229), aeroport), "port": ((2, 119, 189), port), "transport": ((0, 172, 193), transport),
    "loisir": ((236, 64, 122), loisir), "recolte": ((124, 179, 66), recolte),
}

def make(col, fn):
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse([u(0.8), u(0.8), u(31.2), u(31.2)], fill=(10, 10, 16, 255))              # contour sombre
    d.ellipse([u(1.9), u(1.9), u(30.1), u(30.1)], fill=shade(col, 1.0))
    d.ellipse([u(3.2), u(3.2), u(28.8), u(28.8)], outline=shade(col, 1.25), width=int(u(0.7)))   # liseré clair
    fn(Ctx(d, col))
    return im.resize((32, 32), Image.LANCZOS)

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for k, (col, fn) in ICONS.items():
        make(col, fn).save(os.path.join(OUT, k + ".png"))
    sheet = Image.new("RGBA", (6 * 72, 4 * 72), (30, 30, 36, 255))
    for i, (k, (col, fn)) in enumerate(ICONS.items()):
        ic = make(col, fn).resize((64, 64), Image.NEAREST)
        sheet.paste(ic, ((i % 6) * 72 + 4, (i // 6) * 72 + 4), ic)
    sheet.save(os.path.join(os.path.dirname(__file__), "apercu-icones.png"))
    print(" ".join("%s:%02X%02X%02X" % (k, *col) for k, (col, _) in ICONS.items()))
