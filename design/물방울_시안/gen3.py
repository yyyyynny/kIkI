css='''
body{margin:0;font-family:'Noto Sans CJK KR',sans-serif}
.hdr{display:flex}.hdr div{width:400px;text-align:center;font:700 17px sans-serif;padding:10px 0;background:#222;color:#fff}
.row{display:flex}
.cell{width:400px;height:300px;position:relative;display:flex;flex-direction:column;align-items:center;justify-content:center;overflow:hidden}
.dark{background:radial-gradient(ellipse at 50% 50%,#0d2244,#030c1a)}
.light{background:#fff}
.light::before{content:'오늘 날씨가 맑고 기온이 조금 올라 산책하기 좋은 하루가 되겠습니다. 미세먼지는 보통 수준이고 오후에는 바람이 조금 불겠습니다.';position:absolute;inset:16px;font-size:16px;line-height:1.75;color:#333}
.photo{background:linear-gradient(135deg,#f6d365,#fda085 45%,#a1c4fd)}
.cap{position:absolute;bottom:10px;font-size:14px;font-weight:700;color:#999}
.lbl{position:relative;z-index:2;margin-top:14px;font-size:20px;font-weight:700;color:#e8f6ff;text-shadow:0 1px 3px #000a}
.light .lbl,.photo .lbl{color:#1d2c4a;text-shadow:0 1px 2px #fff8}
.wrap{position:relative}
.orb{width:160px;height:112px;border-radius:40px;position:relative;overflow:hidden;z-index:1}
.now{background:rgba(20,60,180,.12);border:3px solid rgba(180,240,255,.55);
 box-shadow:0 0 0 3px rgba(180,240,255,.55),0 0 40px 12px rgba(120,200,255,.7),0 0 100px 28px rgba(80,150,255,.4),inset 0 0 44px 12px rgba(127,210,255,.12)}
.now .g{position:absolute;inset:-12% -16%;background:radial-gradient(ellipse 62% 44% at 40% 9%,rgba(255,255,255,.64),rgba(232,247,255,.3) 42%,transparent 72%)}
/* 애니 물방울: 위(광원 쪽)는 짙고 아래는 밝다 — 빛이 통과해 아래에 모인다 */
.an{background:linear-gradient(180deg,rgba(18,42,120,.78) 0%,rgba(40,95,200,.55) 45%,rgba(120,200,255,.55) 85%,rgba(190,235,255,.7) 100%);
 box-shadow:0 0 30px 8px rgba(120,200,255,.45),inset 0 -1px 0 rgba(255,255,255,.35)}
/* 윤곽: 위는 가늘고 아래는 굵게(애니 물방울 윤곽) — 테두리 대신 안쪽 그림자로 */
.an::before{content:'';position:absolute;inset:0;border-radius:inherit;box-shadow:inset 0 1px 0 0 rgba(150,215,255,.55),inset 0 -3px 0 0 rgba(20,50,140,.55)}
/* 위 하이라이트: 창 모양(물방울 윗면을 닮은 둥근 사각, 살짝 기울임) + 작은 점 */
.win{position:absolute;left:13%;top:12%;width:34%;height:22%;border-radius:12px 18px 10px 14px;background:#fff;transform:rotate(-8deg);opacity:.95}
.dot{position:absolute;left:52%;top:13%;width:9px;height:9px;border-radius:50%;background:#fff}
.dot2{position:absolute;left:59%;top:21%;width:4px;height:4px;border-radius:50%;background:#fff}
/* 아래 가장자리 가는 빛(아래 키와의 하이라이트) */
.edge{position:absolute;left:14%;right:14%;bottom:6px;height:10px;border-bottom:2.5px solid rgba(235,250,255,.95);border-radius:0 0 24px 24px;
 -webkit-mask-image:linear-gradient(90deg,transparent,#000 25%,#000 75%,transparent)}
/* 아래 투과광(밝게 모이는 곳) */
.glow{position:absolute;left:25%;right:25%;bottom:4px;height:30%;background:radial-gradient(ellipse at 50% 100%,rgba(230,250,255,.75),transparent 70%)}
/* 떨어진 그림자 + 그림자 속 반사광 */
.shadow{position:absolute;left:10%;right:10%;bottom:-14px;height:18px;border-radius:50%;background:radial-gradient(ellipse at 50% 40%,rgba(10,30,90,.40),transparent 70%);z-index:0}
.bounce{position:absolute;left:30%;right:30%;bottom:-10px;height:8px;border-radius:50%;background:radial-gradient(ellipse,rgba(160,225,255,.55),transparent 70%);z-index:0}
/* 젤리(사탕) 변형: 색을 더 맑게 */
.jelly{background:linear-gradient(180deg,rgba(60,40,170,.80) 0%,rgba(70,120,230,.6) 50%,rgba(110,225,255,.62) 88%,rgba(200,245,255,.8) 100%)}
.jelly .win{border-radius:50% 60% 45% 55%;width:26%;height:20%;left:16%;top:13%;transform:rotate(-14deg)}
.sp{position:absolute;right:-6px;top:-8px;width:22px;height:22px;z-index:3}
.sp::before,.sp::after{content:'';position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);border-radius:50%;background:radial-gradient(closest-side,#fff,rgba(255,255,255,0))}
.sp::before{width:3px;height:22px}.sp::after{width:22px;height:3px}
'''
def orb(kind):
    if kind=='now': return '<div class="orb now"><div class="g"></div></div>'
    cls='orb an'+(' jelly' if kind=='jelly' else '')
    sp='<div class="sp"></div>' if kind=='jelly' else ''
    return f'<div class="wrap"><div class="shadow"></div><div class="bounce"></div><div class="{cls}"><div class="glow"></div><div class="edge"></div><div class="win"></div><div class="dot"></div><div class="dot2"></div></div>{sp}</div>'
rows=''
for bg,cap in [('dark','어두운 화면'),('light','흰 화면(글)'),('photo','사진·밝은 색')]:
    rows+='<div class="row">'+''.join(f'<div class="cell {bg}">{orb(k)}<div class="lbl">설정</div><div class="cap">{cap}</div></div>' for k in ['now','an','jelly'])+'</div>'
html=f'<!doctype html><html><head><meta charset="utf-8"><style>{css}</style></head><body><div class="hdr"><div>지금</div><div>F 애니 물방울(위 짙고 아래 밝음·창 빛)</div><div>G 사탕 젤리(맑은 색·둥근 빛·반짝)</div></div>{rows}</body></html>'
open('drops3.html','w').write(html)
