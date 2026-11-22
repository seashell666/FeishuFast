# -*- coding: utf-8 -*-
"""生成红色系飞书鸟模块图标：
- adaptive icon：前景 vector XML（抄飞书 pathData，填色改红），背景纯白
- 兜底 PNG：从飞书 512 logo 做色相重映射（青/蓝 -> 红色区，保留 S/V 梯度）
"""
import colorsys
import io
import os
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(ROOT, "res")
APK = os.path.join(ROOT, "shots", "lark_base.apk")

SRC_COLORS = ["#00D6B9", "#3370FF", "#133C9A"]  # 青(翅膀) / 亮蓝(身体) / 深藏蓝(头部)
RED_HUES = [2, 356, 358]  # 对应新色相（度），保留原 S/V


def remap(hexcolor, hue_deg):
    hexcolor = hexcolor.lstrip("#")
    r = int(hexcolor[0:2], 16) / 255
    g = int(hexcolor[2:4], 16) / 255
    b = int(hexcolor[4:6], 16) / 255
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    nr = colorsys.hls_to_rgb(hue_deg / 360.0, l, s)
    return "#%02X%02X%02X" % tuple(int(round(c * 255)) for c in nr)


NEW_COLORS = [remap(c, h) for c, h in zip(SRC_COLORS, RED_HUES)]
print("颜色映射:", dict(zip(SRC_COLORS, NEW_COLORS)))

PATHS = [
    "M56.9 54.87l0.13-0.12c0.08-0.08 0.17-0.16 0.25-0.25l0.18-0.17 0.52-0.51 0.7-0.7 0.61-0.6 0.57-0.56 0.6-0.59L61 50.83l0.76-0.75 0.45-0.42c0.27-0.25 0.56-0.5 0.85-0.73s0.54-0.42 0.82-0.62 0.8-0.54 1.21-0.79 0.83-0.46 1.26-0.67 0.81-0.37 1.23-0.53l0.7-0.26 0.36-0.12h0A30 30 0 0 0 63 34.56a2.75 2.75 0 0 0-2.13-1H38.94a0.42 0.42 0 0 0-0.25 0.75 63.92 63.92 0 0 1 18.16 20.61z",
    "M48.21 74.16a30.1 30.1 0 0 0 26.31-15.47c0.18-0.33 0.36-0.65 0.52-1h0a11.16 11.16 0 0 1-0.86 1.42c-0.11 0.16-0.22 0.32-0.34 0.47s-0.3 0.38-0.46 0.56L73 60.56c-0.26 0.27-0.54 0.53-0.83 0.78l-0.48 0.39-0.59 0.43-0.39 0.24-0.42 0.25c-0.28 0.15-0.57 0.3-0.86 0.43a7.68 7.68 0 0 1-0.79 0.32 8 8 0 0 1-0.87 0.28 12.3 12.3 0 0 1-1.34 0.29c-0.33 0.05-0.66 0.09-1 0.11a9.32 9.32 0 0 1-1.06 0c-0.39 0-0.79 0-1.19-0.07l-0.88-0.13-0.77-0.16-0.41-0.1L60 63.34l-0.56-0.16-0.83-0.26-0.68-0.21-0.64-0.21-0.44-0.15-0.53-0.19L55.94 62l-0.74-0.27-0.44-0.17-0.57-0.22-0.61-0.24L53.19 61l-0.49-0.2-0.37-0.16-0.39-0.17-0.34-0.15-0.31-0.14L51 60l-0.32-0.15-0.4-0.19-0.43-0.2-0.45-0.22c-0.13-0.06-0.26-0.12-0.38-0.19A63 63 0 0 1 31.07 45.7a0.41 0.41 0 0 0-0.71 0.28v19.33 1.57a2.72 2.72 0 0 0 1.2 2.27 30 30 0 0 0 16.63 5",
    "M74.52 58.69v0l0.1-0.19a1.74 1.74 0 0 1-0.1 0.19zm0.48-0.89h0v0a0.25 0.25 0 0 1 0 0zm6.44-10.89a17.37 17.37 0 0 0-12.29-1.12l-0.51 0.15-0.36 0.12-0.7 0.26c-0.42 0.16-0.83 0.34-1.23 0.53s-0.85 0.43-1.26 0.67-0.82 0.51-1.21 0.79-0.55 0.4-0.82 0.62-0.58 0.48-0.85 0.73l-0.45 0.42L61 50.83l-0.54 0.54-0.6 0.59-0.57 0.56-0.61 0.6-0.7 0.7-0.52 0.51-0.18 0.17c-0.08 0.09-0.17 0.17-0.25 0.25l-0.13 0.12c-0.06 0.07-0.13 0.13-0.19 0.19l-0.22 0.2a30 30 0 0 1-6.23 4.41l0.4 0.19L51 60l0.31 0.14 0.31 0.14 0.34 0.15 0.39 0.17 0.37 0.16 0.49 0.2 0.39 0.16 0.61 0.24 0.57 0.22 0.44 0.17 0.74 0.27 0.38 0.13 0.53 0.19 0.44 0.15 0.64 0.21 0.68 0.21 0.83 0.26 0.56 0.16 1.12 0.32 0.41 0.1 0.77 0.16 0.88 0.13c0.4 0 0.8 0.07 1.19 0.07a9.32 9.32 0 0 0 1.06 0c0.34 0 0.67-0.06 1-0.11a12.3 12.3 0 0 0 1.34-0.29 8 8 0 0 0 0.87-0.28 7.68 7.68 0 0 0 0.79-0.32c0.29-0.13 0.58-0.28 0.86-0.43l0.42-0.25 0.39-0.24 0.59-0.43 0.48-0.39c0.29-0.25 0.57-0.51 0.83-0.78l0.38-0.42c0.16-0.18 0.31-0.37 0.46-0.56s0.23-0.31 0.34-0.47a13.1 13.1 0 0 0 0.86-1.4l0.29-0.59 2.65-5.3h0a16.48 16.48 0 0 1 3.44-4.94z",
]

FOREGROUND_XML = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
  <path android:fillColor="%s" android:pathData="%s"/>
  <path android:fillColor="%s" android:pathData="%s"/>
  <path android:fillColor="%s" android:pathData="%s"/>
</vector>
""" % (NEW_COLORS[0], PATHS[0], NEW_COLORS[1], PATHS[1], NEW_COLORS[2], PATHS[2])

ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
  <background android:drawable="@color/ic_launcher_background"/>
  <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
"""

VALUES_XML = """<?xml version="1.0" encoding="utf-8"?>
<resources>
  <color name="ic_launcher_background">#FFFFFF</color>
</resources>
"""


def hue_swap(im):
    """青/蓝色相区(120..280) -> 红色区，保留 S/L；白/透明不动"""
    rgba = im.convert("RGBA")
    px = rgba.load()
    w, h = rgba.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 8:
                continue
            hh, ll, ss = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            hue = hh * 360
            if 120 <= hue <= 280 and ss > 0.15:
                nr = colorsys.hls_to_rgb(RED_HUES[1] / 360.0 if hue > 180
                                         else RED_HUES[0] / 360.0, ll, ss)
                px[x, y] = (int(nr[0] * 255), int(nr[1] * 255), int(nr[2] * 255), a)
    return rgba


def write(p, content):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print("write", os.path.relpath(p, ROOT))


# 1) 矢量前景 + 自适应图标声明
write(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"), FOREGROUND_XML)
write(os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher.xml"), ADAPTIVE_XML)
write(os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher_round.xml"), ADAPTIVE_XML)
write(os.path.join(RES, "values", "ic_launcher_background.xml"), VALUES_XML)

# 2) 兜底 PNG（从飞书 512 logo 色相替换）
z = zipfile.ZipFile(APK)
src = Image.open(io.BytesIO(z.read("r/pje.webp"))).convert("RGBA")
print("source logo size:", src.size)
red = hue_swap(src)
densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for dpi, size in densities.items():
    out = red.resize((size, size), Image.LANCZOS)
    p = os.path.join(RES, "mipmap-%s" % dpi, "ic_launcher.png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    out.save(p)
    print("write", os.path.relpath(p, ROOT), out.size)

# 3) 预览图（桌面效果：192 主图标 + adaptive 渲染示意）
prev = red.resize((384, 384), Image.LANCZOS)
prev.save(os.path.join(ROOT, "shots", "icons", "red_preview.png"))
print("preview -> shots/icons/red_preview.png")
