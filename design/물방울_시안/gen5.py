import re
base=open('gen4.py').read()
exec(base.split("def orb(kind):")[0].replace("open('gen3.py')","open('gen3.py')"))  # css 변수 재사용
W,H,R=160,112,40
def rr(dx=0,dy=0,inset=0):
    x,y,w,h,r=dx+inset,dy+inset,W-2*inset,H-2*inset,R-inset
    return f'M{x+r},{y} H{x+w-r} A{r},{r} 0 0 1 {x+w},{y+r} V{y+h-r} A{r},{r} 0 0 1 {x+w-r},{y+h} H{x+r} A{r},{r} 0 0 1 {x},{y+h-r} V{y+r} A{r},{r} 0 0 1 {x+r},{y} Z'
n=[0]
def svg(style):
    n[0]+=1;i=n[0]
    soft = style=='soft'
    body = f'<radialGradient id="bd{i}" cx="58%" cy="52%" r="62%"><stop offset="0" stop-color="#101a6b"/><stop offset=".62" stop-color="#1f3ca8"/><stop offset="1" stop-color="#3f74d8"/></radialGradient>' if soft else ''
    bodyfill=f'url(#bd{i})' if soft else '#1b2a86'
    blur=f'<filter id="bl{i}"><feGaussianBlur stdDeviation="3"/></filter>' if soft else ''
    fb=f' filter="url(#bl{i})"' if soft else ''
    return f'''<svg viewBox="0 0 {W} {H}" style="position:absolute;inset:0;width:100%;height:100%;overflow:visible">
<defs>{body}{blur}
<clipPath id="c{i}"><path d="{rr()}"/></clipPath>
<mask id="rim{i}"><rect x="-10" y="-10" width="180" height="132" fill="black"/><path d="{rr()}" fill="white"/><path d="{rr(5,5)}" fill="black"/></mask>
<linearGradient id="rf{i}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff"/><stop offset=".38" stop-color="#fff"/><stop offset=".52" stop-color="#fff" stop-opacity="0"/></linearGradient>
<mask id="br{i}"><rect x="-10" y="-10" width="180" height="132" fill="black"/><path d="{rr()}" fill="white"/><path d="{rr(-3,-3)}" fill="black"/></mask>
<linearGradient id="bf{i}" x1="1" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#9ff3ff"/><stop offset=".35" stop-color="#9ff3ff"/><stop offset=".5" stop-color="#9ff3ff" stop-opacity="0"/></linearGradient>
</defs>
<g clip-path="url(#c{i})">
<rect width="{W}" height="{H}" fill="{bodyfill}"/>
<ellipse{fb} cx="98" cy="100" rx="92" ry="52" fill="#2f4fc4" opacity="{'.8' if soft else '1'}"/>
<ellipse{fb} cx="112" cy="101" rx="54" ry="25" transform="rotate(-14 112 101)" fill="#6fd2ff" opacity="{'.85' if soft else '1'}"/>
<ellipse{fb} cx="126" cy="99" rx="25" ry="10" transform="rotate(-18 126 99)" fill="#d8faff" opacity="{'.9' if soft else '1'}"/>
</g>
<rect width="{W}" height="{H}" fill="url(#rf{i})" mask="url(#rim{i})"/>
<rect width="{W}" height="{H}" fill="url(#bf{i})" mask="url(#br{i})"/>
<ellipse cx="34" cy="24" rx="7" ry="4.5" transform="rotate(-28 34 24)" fill="#fff"/>
<circle cx="48" cy="17" r="2.6" fill="#fff"/>
</svg>'''
css2=css+'''
.i{width:160px;height:112px;position:relative;z-index:1;filter:drop-shadow(0 0 14px rgba(110,180,255,.45))}
'''
def orb(kind):
    if kind=='h':
        return '<div class="wrap"><div class="shadow"></div><div class="bounce"></div><div class="orb h h2"><div class="half"></div><div class="cg"></div><div class="b1"></div><div class="b2"></div><div class="b3"></div></div><div class="sp"></div></div>'
    return f'<div class="wrap"><div class="shadow"></div><div class="bounce"></div><div class="i">{svg(kind)}</div></div>'
rows=''
for bg,cap in [('dark','어두운 화면'),('light','흰 화면(글)'),('photo','사진·밝은 색')]:
    rows+='<div class="row">'+''.join(f'<div class="cell {bg}">{orb(k)}<div class="lbl">설정</div><div class="cap">{cap}</div></div>' for k in ['h','soft','cel'])+'</div>'
html=f'<!doctype html><html><head><meta charset="utf-8"><style>{css2}</style></head><body><div class="hdr"><div>H′(지난 시안)</div><div>I1 투명 보석 — 애니 塗り①(부드럽게)</div><div>I2 투명 보석 — 애니 塗り②(셀, 경계 또렷)</div></div>{rows}</body></html>'
open('drops5.html','w').write(html)
