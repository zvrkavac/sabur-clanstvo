"""Pravi ikone aplikacije iz loga sa sajta (nema binarnih fajlova u repozitoriju).
Pokreće ga GitHub Actions prije builda:  python3 android/tools/make_icons.py
"""
import io, os, sys, urllib.request
from PIL import Image, ImageDraw

BASE = "https://dzemat.sabur.dk/clanstvo/assets/"
RES = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "SaburApp-build"})
    return Image.open(io.BytesIO(urllib.request.urlopen(req, timeout=30).read())).convert("RGBA")

def save(img, rel):
    p = os.path.join(RES, rel); os.makedirs(os.path.dirname(p), exist_ok=True); img.save(p, optimize=True); print("✓", rel)

icon = get(BASE + "icons/icon-512.png")
for d, s in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    save(icon.resize((s, s), Image.LANCZOS), f"mipmap-{d}/ic_launcher.png")

try:
    logo = get(BASE + "logo.png")
except Exception as e:
    print("logo.png nije dostupan, koristim ikonu:", e); logo = icon
c = Image.new("RGBA", (432, 432), (0, 0, 0, 0)); l = logo.resize((280, 280), Image.LANCZOS); c.paste(l, (76, 76), l)
save(c, "drawable-nodpi/splash_icon.png")

# mala ikona za obavijesti: bijelo zvono (Android traži jednobojnu ikonu)
S = 384; im = Image.new("RGBA", (S, S), (0, 0, 0, 0)); g = ImageDraw.Draw(im); w = (255, 255, 255, 255)
g.pieslice([S*.22, S*.14, S*.78, S*.70], 180, 360, fill=w); g.rectangle([S*.22, S*.42, S*.78, S*.70], fill=w)
g.polygon([(S*.22, S*.70), (S*.12, S*.80), (S*.88, S*.80), (S*.78, S*.70)], fill=w)
g.ellipse([S*.40, S*.80, S*.60, S*.94], fill=w); g.ellipse([S*.45, S*.06, S*.55, S*.16], fill=w)
save(im.resize((96, 96), Image.LANCZOS), "drawable-nodpi/ic_notification.png")
