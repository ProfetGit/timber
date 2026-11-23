from PIL import Image
import sys
Z=8
sets=[("poof",16),("poof_small",8)]
bgs=[(128,128,128),(40,72,40),None]
W=16*16*Z; rows=[]
sheet=Image.new("RGB",(W,len(sets)*len(bgs)*16*Z),(0,0,0))
y=0
for bg in bgs:
  for name,n in sets:
    for f in range(16):
      im=Image.open(f"frames/{name}_{f}.png").convert("RGBA")
      if bg is None:
        a=im.split()[3].point(lambda v:255 if v else 0)
        im=Image.merge("RGBA",(Image.new("L",im.size,0),)*3+(a,)); b=(255,255,255)
      else: b=bg
      tile=Image.new("RGBA",im.size,b+(255,)); tile.alpha_composite(im)
      tile=tile.resize((n*Z,n*Z),Image.NEAREST)
      sheet.paste(tile.convert("RGB"),(f*16*Z+(16-n)*Z//2,y+(16-n)*Z//2))
    y+=16*Z
sheet.save("poof_sheet.png")
