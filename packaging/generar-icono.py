# Genera el icono de PDF Listo: cuadrado redondeado azul, hoja blanca con esquina doblada y un check.
import sys
from PIL import Image, ImageDraw
raiz = sys.argv[1]
def dibujar(n):
    S = 1024
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((40, 40, S-40, S-40), radius=210, fill=(9, 105, 218, 255))
    # hoja
    x0, y0, x1, y1 = 270, 190, 754, 834
    pliegue = 150
    d.polygon([(x0, y0), (x1 - pliegue, y0), (x1, y0 + pliegue), (x1, y1), (x0, y1)], fill=(255, 255, 255, 255))
    d.polygon([(x1 - pliegue, y0), (x1 - pliegue, y0 + pliegue), (x1, y0 + pliegue)], fill=(180, 210, 245, 255))
    # lineas de texto
    for i, ancho in enumerate([300, 260, 300]):
        y = 300 + i * 70
        d.rounded_rectangle((x0 + 70, y, x0 + 70 + ancho - 60, y + 26), radius=13, fill=(175, 190, 210, 255))
    # check verde
    d.ellipse((470, 520, 790, 840), fill=(26, 127, 55, 255), outline=(255, 255, 255, 255), width=28)
    d.line([(548, 683), (612, 747), (722, 617)], fill=(255, 255, 255, 255), width=52, joint="curve")
    return im.resize((n, n), Image.LANCZOS)
tamanos = [16, 24, 32, 48, 64, 128, 256]
for n in tamanos:
    dibujar(n).save(f"{raiz}/src/main/resources/icono/pdflisto-{n}.png")
dibujar(256).save(f"{raiz}/packaging/pdflisto.png")
dibujar(256).save(f"{raiz}/packaging/pdflisto.ico", sizes=[(n, n) for n in tamanos])
