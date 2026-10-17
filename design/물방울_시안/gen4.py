import re
base=open('gen3.py').read()
css=re.search(r"css='''(.*?)'''",base,re.S).group(1)
css+='''
/* H: 윤곽을 따라 감기는 빛(가로 띠 없음) */
.h{background:radial-gradient(ellipse 115% 105% at 50% 18%,rgba(70,40,175,.86) 0%,rgba(75,105,225,.72) 52%,rgba(120,195,255,.66) 100%);
 box-shadow:0 0 26px 6px rgba(130,190,255,.40),
  inset 0 -16px 18px -8px rgba(205,248,255,.90),
  inset 0 -2px 0 0 rgba(130,255,235,.70),
  inset 0 -5px 0 -1px rgba(255,175,235,.30),
  inset 0 2px 5px 0 rgba(20,10,80,.35)}
.h .b1{position:absolute;left:15%;top:13%;width:24%;height:17%;border-radius:55% 65% 45% 60%/60% 55% 50% 45%;background:#fff;transform:rotate(-16deg);filter:blur(.4px)}
.h .b2{position:absolute;left:41%;top:12%;width:7%;height:9%;border-radius:50%;background:#fff;filter:blur(.3px)}
.h .b3{position:absolute;right:13%;top:30%;width:3.2%;height:16%;border-radius:4px;background:rgba(255,255,255,.85);transform:rotate(14deg);filter:blur(.3px)}
.h .half{position:absolute;left:8%;right:8%;top:4%;height:46%;border-radius:36px 36px 50% 50%/30px 30px 22px 22px;
 background:linear-gradient(180deg,rgba(255,255,255,.26),rgba(255,255,255,0));filter:blur(1.5px)}
.h .cg{position:absolute;left:22%;right:22%;bottom:3px;height:36%;background:radial-gradient(ellipse at 50% 100%,rgba(235,252,255,.55),transparent 72%);filter:blur(2px)}
.h2{background:radial-gradient(ellipse 115% 105% at 50% 18%,rgba(30,55,150,.86) 0%,rgba(45,110,215,.72) 52%,rgba(110,205,255,.66) 100%)}
'''
def orb(kind):
    if kind=='g':
        return '<div class="wrap"><div class="shadow"></div><div class="bounce"></div><div class="orb an jelly"><div class="glow"></div><div class="edge"></div><div class="win"></div><div class="dot"></div><div class="dot2"></div></div><div class="sp"></div></div>'
    extra=' h2' if kind=='h2' else ''
    half='<div class="half"></div>' if kind in('h1','h2') else ''
    return f'<div class="wrap"><div class="shadow"></div><div class="bounce"></div><div class="orb h{extra}">{half}<div class="cg"></div><div class="b1"></div><div class="b2"></div><div class="b3"></div></div><div class="sp"></div></div>'
rows=''
for bg,cap in [('dark','어두운 화면'),('light','흰 화면(글)'),('photo','사진·밝은 색')]:
    rows+='<div class="row">'+''.join(f'<div class="cell {bg}">{orb(k)}<div class="lbl">설정</div><div class="cap">{cap}</div></div>' for k in ['g','h1','h2'])+'</div>'
html=f'<!doctype html><html><head><meta charset="utf-8"><style>{css}</style></head><body><div class="hdr"><div>G(지난 시안)</div><div>H 보라 — 윤곽 따라 감기는 빛</div><div>H′ 파랑 — 같은 구조, 원래 메뉴 색</div></div>{rows}</body></html>'
open('drops4.html','w').write(html)
