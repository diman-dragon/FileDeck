#!/usr/bin/env python3
"""Генерирует иконки FileDeck в стиле DataLaw: тёмно-синий фон, кремовая D, жёлтый значок с папкой.
Запуск: python3 scripts/make-icons.py   (нужен Pillow и numpy)"""
from PIL import Image, ImageDraw, ImageChops
import numpy as np, glob, os, math

NAVY=(17,27,48,255); CREAM=(244,232,206,255); YEL=(255,204,0,255)
D=Image.open('src/assets/d-mark.png').split()[3]
SS=4
RES='android/app/src/main/res/'

def folder_glyph(d, cx, cy, size, color):
    w=size; h=size*0.78
    x0=cx-w/2; y0=cy-h/2+size*0.04; r=size*0.10
    d.rounded_rectangle([x0, y0+h*0.18, x0+w, y0+h], radius=r, fill=color)
    d.rounded_rectangle([x0, y0, x0+w*0.46, y0+h*0.34], radius=r, fill=color)

def composition(px=512, with_bg=True, mono=False):
    s=px*SS/512
    im=Image.new('RGBA',(px*SS,px*SS),NAVY if with_bg else (0,0,0,0))
    layer=Image.new('RGBA',im.size,(0,0,0,0))
    dw,dh=int(287*s),int(318*s)
    dm=D.resize((dw,dh),Image.LANCZOS)
    layer.paste(Image.new('RGBA',(dw,dh),(255,255,255,255) if mono else CREAM),(int(63*s),int(60*s)),dm)
    d=ImageDraw.Draw(layer)
    cx,cy,r=399.5*s,399.5*s,110*s
    if mono:
        d.ellipse([cx-r,cy-r,cx+r,cy+r],fill=(255,255,255,255))
        cut=Image.new('L',im.size,0); folder_glyph(ImageDraw.Draw(cut),cx,cy,r*1.15,255)
        layer.putalpha(ImageChops.subtract(layer.split()[3],cut))
    else:
        d.ellipse([cx-r,cy-r,cx+r,cy+r],fill=YEL)
        folder_glyph(d,cx,cy,r*1.15,NAVY)
    return Image.alpha_composite(im,layer).resize((px,px),Image.LANCZOS)

def enclosing_circle(alpha):
    """Приближённая минимальная окружность вокруг непрозрачных пикселей."""
    a=np.array(alpha)>40
    ys,xs=np.where(a)
    pts=np.stack([xs,ys],1).astype(float)
    c=pts.mean(0)
    for i in range(2000):
        dist=np.hypot(*(pts-c).T); j=dist.argmax()
        c=c+(pts[j]-c)*(1/(i+2))
    return c, np.hypot(*(pts-c).T).max()

def safe_foreground(px=432, radius_dp=31.5, mono=False):
    """Композиция целиком внутри круга радиуса radius_dp (безопасная зона адаптивной иконки — 33 dp)."""
    base=composition(1024,with_bg=False,mono=mono)
    c,r=enclosing_circle(base.split()[3])
    scale=(radius_dp/108*px)/r
    w=int(base.width*scale)
    b=base.resize((w,w),Image.LANCZOS)
    out=Image.new('RGBA',(px,px),(0,0,0,0))
    out.paste(b,(int(px/2-c[0]*scale),int(px/2-c[1]*scale)),b)
    return out

def legacy(size, round_=False):
    S=432
    if round_:
        bg=Image.alpha_composite(Image.new('RGBA',(S,S),NAVY),safe_foreground(S,33))
        c=bg.crop((int(18/108*S),int(18/108*S),int(90/108*S),int(90/108*S))).resize((size,size),Image.LANCZOS)
        mask_shape='circle'
    else:
        c=composition(512).resize((size,size),Image.LANCZOS); mask_shape='rect'
    m=Image.new('L',(size*4,size*4),0); md=ImageDraw.Draw(m)
    if mask_shape=='circle': md.ellipse([0,0,size*4-1,size*4-1],fill=255)
    else: md.rounded_rectangle([0,0,size*4-1,size*4-1],radius=size*4*0.22,fill=255)
    m=m.resize((size,size),Image.LANCZOS)
    out=Image.new('RGBA',(size,size),(0,0,0,0)); out.paste(c,(0,0),m); return out

if __name__=='__main__':
    os.makedirs('branding',exist_ok=True)
    play=composition(512)
    play.convert('RGB').save('branding/play-store-icon-512.png')
    play.convert('RGB').save('branding/icon-512.png')
    os.makedirs(RES+'drawable-nodpi',exist_ok=True)
    safe_foreground().save(RES+'drawable-nodpi/ic_launcher_foreground.png')
    safe_foreground(mono=True).save(RES+'drawable-nodpi/ic_launcher_monochrome.png')
    open(RES+'values/ic_launcher_background.xml','w').write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#111B30</color>\n</resources>\n')
    for dpi,sz in {'mdpi':48,'hdpi':72,'xhdpi':96,'xxhdpi':144,'xxxhdpi':192}.items():
        legacy(sz).save(RES+f'mipmap-{dpi}/ic_launcher.png'); legacy(sz,True).save(RES+f'mipmap-{dpi}/ic_launcher_round.png')
    comp=composition(512,with_bg=False); comp=comp.crop(comp.getbbox())
    for p in glob.glob(RES+'drawable*/splash.png'):
        w,h=Image.open(p).size
        im=Image.new('RGBA',(w,h),(0,0,0,255))
        side=int(min(w,h)*0.46); c=comp.resize((side,int(side*comp.height/comp.width)),Image.LANCZOS)
        im.paste(c,((w-c.width)//2,(h-c.height)//2),c); im.save(p)
    print('Иконки обновлены')
