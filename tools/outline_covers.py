"""Freeze original SVG title typography to outlines for consistent Vietnamese glyphs."""
from pathlib import Path
import sys,xml.etree.ElementTree as ET,unicodedata
root=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(root/'.tools/pythonlib'))
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
font=instantiateVariableFont(TTFont(root/'src/main/resources/static/fonts/Lora.ttf'),{'wght':600})
glyphs=font.getGlyphSet();cmap=font.getBestCmap();upm=font['head'].unitsPerEm
ns='{http://www.w3.org/2000/svg}'
ET.register_namespace('',ns[1:-1])
for path in (root/'src/main/resources/static/covers').glob('*.svg'):
 svg=ET.parse(path).getroot()
 for text in list(svg.findall(ns+'text')):
  if 'Georgia' not in text.get('font-family',''):continue
  group=ET.Element(ns+'g',{'fill':text.get('fill','#263b31'),'aria-label':''.join(text.itertext())})
  x=float(text.get('x','0'));y=float(text.get('y','0'));size=float(text.get('font-size','27'));scale=size/upm
  spans=list(text) or [text]
  for span in spans:
   x=float(span.get('x',str(x)));y+=float(span.get('dy','0'))
   for ch in unicodedata.normalize('NFC',span.text or ''):
    name=cmap.get(ord(ch));
    if not name:continue
    pen=SVGPathPen(glyphs);glyphs[name].draw(pen);commands=pen.getCommands()
    if commands:ET.SubElement(group,ns+'path',{'d':commands,'transform':f'translate({x:.3f} {y:.3f}) scale({scale:.5f} {-scale:.5f})'})
    x+=glyphs[name].width*scale
  svg.insert(list(svg).index(text),group);svg.remove(text)
 path.write_text(ET.tostring(svg,encoding='unicode'),encoding='utf-8')
css=root/'src/main/resources/static/site.css'
s=css.read_text(encoding='utf-8')
if 'url("/fonts/Lora.ttf")' not in s:s='@font-face{font-family:"Lora";src:url("/fonts/Lora.ttf") format("truetype");font-weight:400 700;font-display:swap}\n'+s
s=s.replace('Georgia, "Times New Roman", serif','"Lora", Georgia, "Times New Roman", serif').replace('font: 25px Georgia','font: 25px "Lora", Georgia').replace('font: 29px Georgia','font: 29px "Lora", Georgia')
css.write_text(s,encoding='utf-8')
