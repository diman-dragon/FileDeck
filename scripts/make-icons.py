#!/usr/bin/env python3
"""Генерирует иконки FileDeck в стиле DataLaw: тёмно-синий фон, жёлтая папка со стилизованной буквой D внутри.
Запуск: python3 scripts/make-icons.py   (нужен Pillow и numpy)"""
from PIL import Image, ImageDraw, ImageChops
import numpy as np, glob, os, math

NAVY=(17,27,48,255); MANILA=(240,184,74,255); MANILA_DEEP=(201,143,28,255)
D=Image.open('src/assets/d-mark.png').split()[3]
SS=4
RES='android/app/src/main/res/'

def rrect(d, x0, y0, x1, y1, r, fill):
    d.rounded_rectangle([x0, y0, x1, y1], radius=r, fill=fill)

def composition(px=512, with_bg=True, mono=False):
    """Папка (форма как в приложении) с буквой D на лицевой панели. mono — силуэт для тематической иконки."""
    s=px*SS/512
    im=Image.new('RGBA',(px*SS,px*SS),NAVY if with_bg else (0,0,0,0))
    # геометрия папки в координатах глифа 40×40 приложения: x 4..36, y 7..33
    k=13.4*s                       # масштаб глифа
    ox=256*s-20*k; oy=256*s-20*k   # центр глифа (20,20) — в центр холста
    X=lambda x: ox+x*k
    Y=lambda y: oy+y*k
    r=3*k
    back=Image.new('RGBA',im.size,(0,0,0,0)); bd=ImageDraw.Draw(back)
    white=(255,255,255,255)
    rrect(bd,X(4),Y(10),X(36),Y(33),r,white if mono else MANILA_DEEP)       # задняя стенка
    rrect(bd,X(4),Y(7),X(18.6),Y(15),r,white if mono else MANILA_DEEP)      # язычок
    rrect(bd,X(4),Y(17),X(36),Y(33),r,white if mono else MANILA)            # лицевая панель
    if not mono:
        bd.line([X(4)+r*0.4,Y(17.5),X(36)-r*0.4,Y(17.5)],fill=(255,255,255,70),width=max(1,int(k*0.16)))
    # буква D по центру лицевой панели
    box=D.crop(D.getbbox())
    dh=int((33-17)*k*0.88); dw=int(dh*box.width/box.height)
    dm=box.resize((dw,dh),Image.LANCZOS)
    cx=(X(4)+X(36))/2; cy=(Y(17)+Y(33))/2
    pos=(int(cx-dw/2),int(cy-dh/2))
    if mono:
        cut=Image.new('L',im.size,0); cut.paste(dm,pos)
        back.putalpha(ImageChops.subtract(back.split()[3],cut))
    else:
        back.paste(Image.new('RGBA',(dw,dh),NAVY),pos,dm)
    return Image.alpha_composite(im,back).resize((px,px),Image.LANCZOS)

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
