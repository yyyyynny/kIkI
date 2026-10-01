#!/usr/bin/env python3
"""
kIkI 한영타 판정 보조 표(app/.../util/TypoTables.kt) 생성기.

    tools/typo-model/fetch_corpora.sh      # 학습 말뭉치(약 200MB) → tools/typo-model/data/
    tools/eval-data/fetch.sh               # NSMC(train 만 학습에 씀)·AG News(train.csv — 약어 모델)
    python3 tools/typo-model/build_tables.py

필요: Python 3.9+, pyarrow(KLUE parquet 읽기). 약 5분. 결과 파일은 손으로 고치지 않는다.
만드는 표와 근거는 docs/한영타_검증.md "판정 구조" 절, 판정식은 TypoLanguageModel.judge 와 같다
(이 스크립트의 score() 는 Kotlin 판정의 파이썬 이식 — "판정에 영향을 주는 사전 항목"을 고를 때 쓴다).
⚠️ 판정식이나 매개변수를 바꾸면 이 파일의 score()/P 도 같이 바꿔야 사전 선별이 맞는다.
"""
import bz2, csv, html, math, os, re, sys
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
DATA = os.path.join(HERE, "data")
EVAL = os.path.join(ROOT, "tools", "eval-data", "data")
UTIL = os.path.join(ROOT, "app", "src", "main", "kotlin", "com", "langsense", "app", "util")
OUT = os.path.join(UTIL, "TypoTables.kt")
csv.field_size_limit(10 ** 9)

# ── 매개변수(실측으로 정함 — docs/한영타_검증.md) ────────────────────────────
P = dict(
    ALPHA=200.0, FLOOR_UNI=0.5,        # 위치별 분포를 전체 음절 분포 쪽으로 평활
    EOJ_K=5000, EOJ_LAMBDA=0.5,        # 자주 쓰는 어절 기억
    EOJ_COLLOQUIAL=float(os.environ.get("KIKI_EOJ_COL", "0.3")),                # 어절 기억 = 구어체 30% + 정제된 글 70% 혼합(구어체 말뭉치가 적어 풀어 세면 묻힌다)
    LEX_R=0.2,                         # 증인 사전 소문자 확률 = (소문자 출현 + r × 대문자 증인)
    A=3.0, B=1.0,                      # 의미 없는 Shift 벌점 / 의미 있는 Shift 가산(글자당)
    CAPS_BONUS=3.0, CAPS_PRIOR=-3.0, CAPS_SHORT=-2.0,
    ACR={"lower": -4.0, "upper": -0.5, "mixed": -3.0, "title": -4.0}, P_ACR=0.7,
    CTX=3.0, CTX_STRONG=10.0, SHIFT=0.2620,  # 주변 한글: 첫 글자가 그 가설로 칠 수 있는 모양이면 STRONG, 아니면 CTX
    LEX_NEED=0.3,
    # 주변 한글이 없을 때(2026-10-01): 한국인이 실제로 칠 영어는 흔한 단어라, 문맥 없는 판정은 임계를 0.45 상당으로
    # 낮추고(로짓 차이 = logit(0.70) − logit(0.45)) 대신 흔한 영어 단어(자막 빈도 상위 2만 개 중 낮춘 판정에서
    # PROTECT_NEED 이상인 것)는 문맥이 없으면 판정하지 않는다.
    NOCTX_DELTA=math.log(0.7 / 0.3) - math.log(0.45 / 0.55), PROTECT_TOP=20000, PROTECT_NEED=0.65,                      # 사전 없이 이 신뢰도 이상 나오는 모양이 있는 항목만 담는다
)
SHIFT_KEYS = set("qwertop")
COLLOQUIAL_CORPORA = {"nsmc.train", "unsmile.train", "hate.train", "chatbot.train", "kmhas.train", "persona.train",
                      "multisession.train", "safeconv.train"}

# ── 말뭉치 → 줄(학습 분할만) ─────────────────────────────────────────────
def _lines(gen):
    for l in gen:
        l = l.replace("\n", " ").replace("\r", " ").strip()
        if l: yield l

def nsmc_train():
    with open(os.path.join(EVAL, "ratings_train.txt"), encoding="utf-8") as f:
        next(f)
        for line in f:
            p = line.rstrip("\n").split("\t")
            if len(p) >= 2: yield p[1]

SENT = re.compile(r"(?<=[.?!])\s+")
def news_train():
    with open(os.path.join(DATA, "news_train.csv"), encoding="utf-8") as f:
        for row in csv.DictReader(f):
            yield row["title"]
            yield from SENT.split(row["document"])

def parquet(name):
    import pyarrow.parquet as pq
    return pq.read_table(os.path.join(DATA, name)).to_pydict()

def ynat_train():
    yield from parquet("ynat_train.parquet")["title"]

def mrc_train():
    t = parquet("mrc_train.parquet")
    seen = set()
    for c in t["context"]:
        if c in seen: continue
        seen.add(c)
        yield from SENT.split(c)
    yield from t["question"]

def tsv_col(name, col):
    with open(os.path.join(DATA, name), encoding="utf-8") as f:
        r = csv.reader(f, delimiter="\t")
        next(r)
        for row in r:
            if row: yield row[col]

def chatbot():
    with open(os.path.join(DATA, "chatbot.csv"), encoding="utf-8") as f:
        for row in csv.DictReader(f):
            yield row["Q"]; yield row["A"]

def _strip_nested(s, op, cl):
    out = []; depth = 0; i = 0
    while i < len(s):
        if s.startswith(op, i): depth += 1; i += len(op); continue
        if depth and s.startswith(cl, i): depth -= 1; i += len(cl); continue
        if not depth: out.append(s[i])
        i += 1
    return "".join(out)

R_COMMENT = re.compile(r"<!--.*?-->", re.S)
R_REF = re.compile(r"<ref[^>/]*/>|<ref[^>]*>.*?</ref>", re.S | re.I)
R_TAGBLOCK = re.compile(r"<(math|code|source|syntaxhighlight|pre|gallery|timeline|score|chem)[^>]*>.*?</\1>", re.S | re.I)
R_TAG = re.compile(r"<[^>]+>")
R_FILE = re.compile(r"\[\[(?:파일|File|file|그림|Image|image|분류|Category|category)\s*:[^\[\]]*(?:\[\[[^\]]*\]\][^\[\]]*)*\]\]")
R_LINK = re.compile(r"\[\[(?:[^\[\]|]*\|)?([^\[\]]*)\]\]")
R_EXT = re.compile(r"\[(?:https?|ftp)://\S+\s*([^\]]*)\]")
R_URL = re.compile(r"(?:https?|ftp)://\S+")
R_QUOTE = re.compile(r"'{2,}")
R_INTERWIKI = re.compile(r"^\[\[[a-z\-]+:.*\]\]$")

def _wiki_clean(t):
    t = R_COMMENT.sub(" ", t); t = R_TAGBLOCK.sub(" ", t); t = R_REF.sub(" ", t)
    t = _strip_nested(t, "{{", "}}"); t = _strip_nested(t, "{|", "|}")
    t = R_FILE.sub(" ", t); t = R_LINK.sub(r"\1", t); t = R_EXT.sub(r"\1", t); t = R_URL.sub(" ", t)
    t = R_QUOTE.sub("", t); t = R_TAG.sub(" ", t); t = html.unescape(t)
    for l in t.split("\n"):
        l = l.strip()
        if not l or R_INTERWIKI.match(l): continue
        l = l.lstrip("*#:;=|! ").rstrip("= ")
        if l.startswith("__") or l.lower().startswith("#redirect") or l.startswith("#넘겨주기"): continue
        for s in SENT.split(l):
            if s: yield s

def wiki_train():
    """문서 10개 중 1개(순번 % 10 == 0)는 평가용으로 떼어 둔다."""
    ns = None; pages = 0
    for _, el in ET.iterparse(bz2.open(os.path.join(DATA, "wiki1.xml.bz2")), events=("end",)):
        tag = el.tag.rsplit("}", 1)[-1]
        if tag == "ns": ns = el.text
        elif tag == "text":
            if ns == "0" and el.text and not el.text.lstrip().lower().startswith(("#redirect", "#넘겨주기")):
                pages += 1
                if pages % 10: yield from _wiki_clean(el.text)
            el.clear()
        elif tag == "page":
            el.clear()

def _utterances(cols, name):
    """대화 세션 칸(파이썬 리스트 문자열)을 발화 한 줄씩."""
    import ast
    t = parquet(name)
    for c in cols:
        for v in t[c]:
            try:
                yield from ast.literal_eval(v)
            except (ValueError, SyntaxError):
                continue

CORPORA = [
    ("nsmc.train", nsmc_train), ("news.train", news_train), ("ynat.train", ynat_train), ("mrc.train", mrc_train),
    ("wiki.train", wiki_train), ("unsmile.train", lambda: tsv_col("unsmile_train.tsv", 0)),
    ("hate.train", lambda: tsv_col("hate_train.tsv", 0)), ("chatbot.train", chatbot),
    # 구어체 보강(2026-09-30): 뉴스 댓글 · 페르소나 채팅 · 다중 세션 일상 대화 · 일상 질문(사람 쪽 발화만)
    ("kmhas.train", lambda: parquet("kmhas_train.parquet")["text"]),
    ("persona.train", lambda: _utterances(["session_dialog"], "persona_train.parquet")),
    ("multisession.train", lambda: _utterances(["session1", "session2"], "multisession_train.parquet")),
    ("safeconv.train", lambda: parquet("safeconv_train.parquet")["instruction"]),
]

# ── 집계 ────────────────────────────────────────────────────────────────
RUN = re.compile(r"[가-힣]+")
def is_hangul(c):
    o = ord(c)
    return 0xAC00 <= o <= 0xD7A3 or 0x3130 <= o <= 0x318F

def latin_tokens(line):
    """HangulConverter.analyze 와 같은 토큰화(공백·한글이 경계), 라틴 글자가 있는 조각만."""
    out = []; start = -1; n = len(line)
    for i in range(n + 1):
        c = line[i] if i < n else " "
        if c.isspace() or is_hangul(c):
            if start >= 0:
                tok = line[start:i]
                if any('a' <= ch <= 'z' or 'A' <= ch <= 'Z' for ch in tok): out.append(tok)
                start = -1
        elif start < 0:
            start = i
    return out

def count_all():
    pos = {k: Counter() for k in ["single", "first", "mid", "last", "uni", "lens", "eoj_col", "eoj_for"]}
    latin = Counter()
    for name, gen in CORPORA:
        n = 0
        eoj = pos["eoj_col" if name in COLLOQUIAL_CORPORA else "eoj_for"]
        for line in _lines(gen()):
            n += 1
            for w in line.split():
                for m in RUN.finditer(w):
                    r = m.group(); k = len(r)
                    pos["lens"][min(k, 12)] += 1
                    eoj[r] += 1
                    for ch in r: pos["uni"][ch] += 1
                    if k == 1: pos["single"][r] += 1
                    else:
                        pos["first"][r[0]] += 1; pos["last"][r[-1]] += 1
                        for ch in r[1:-1]: pos["mid"][ch] += 1
            if any(0xAC00 <= ord(c) <= 0xD7A3 for c in line):
                for t in latin_tokens(line): latin[t] += 1
        print(f"  {name}: {n}줄", file=sys.stderr)
    return pos, latin

# ── 표 ─────────────────────────────────────────────────────────────────
def build_tables(pos, latin):
    T = {}
    N = sum(pos["uni"].values()); V = 11172; fu = P["FLOOR_UNI"]; a = P["ALPHA"]
    syl = sorted(pos["uni"])
    uni = lambda s: (pos["uni"].get(s, 0) + fu) / (N + fu * V)
    T["syl"] = "".join(syl)
    for k in ["single", "first", "mid", "last"]:
        tot = sum(pos[k].values())
        T[k] = [math.log((pos[k].get(s, 0) + a * uni(s)) / (tot + a)) for s in syl]
        T[k + "_floor"] = math.log((a * fu / (N + fu * V)) / (tot + a))
    lt = sum(pos["lens"].values())
    T["lens"] = [math.log(pos["lens"].get(i, 1) / lt) for i in range(1, 13)]
    # 자주 쓰는 어절: 구어체·정제된 글 분포를 섞은 확률의 내림차순, 같으면 글자순(재현성)
    k, wc = P["EOJ_K"], P["EOJ_COLLOQUIAL"]
    cc, cf = pos["eoj_col"], pos["eoj_for"]; tc, tf = sum(cc.values()), sum(cf.values())
    keys = {e for e, _ in cc.most_common(k)} | {e for e, _ in cf.most_common(k)}
    mix = {e: wc * cc.get(e, 0) / tc + (1 - wc) * cf.get(e, 0) / tf for e in keys}
    top = sorted(mix.items(), key=lambda kv: (-kv[1], kv[0]))[:k]
    T["eoj"] = [(e, math.log(p)) for e, p in top]
    # Shift 증인: 라틴 연속 구간별 모양 빈도
    forms = defaultdict(Counter)
    for t, n in latin.items():
        for m in re.finditer(r"[A-Za-z]+", t): forms[m.group().lower()][m.group()] += n
    NL = sum(sum(f.values()) for f in forms.values())
    lex = {}
    for w, f in forms.items():
        wit = sum(n for form, n in f.items()
                  if any(ch.isupper() and ch.lower() not in SHIFT_KEYS for ch in form[1:])
                  or (len(form) >= 2 and form.isupper() and any(ch.lower() not in SHIFT_KEYS for ch in form)))
        if wit < 1 or len(w) < 2: continue
        low = sum(n for form, n in f.items() if form == w)
        up = sum(n for form, n in f.items() if form.isupper() and len(form) >= 2)
        lex[w] = (math.log((low + P["LEX_R"] * wit) / NL), math.log(up / NL) if up else None)
    T["lex"] = lex
    # 약어 bigram: AG News train + 한국어 글의 전부 대문자 라틴 구간, 종류당 1회
    types = set()
    with open(os.path.join(EVAL, "train.csv"), encoding="utf-8") as f:
        for line in f:
            for m in re.finditer(r"[A-Za-z]+", line):
                if len(m.group()) >= 2 and m.group().isupper(): types.add(m.group().lower())
    for t in latin:
        for m in re.finditer(r"[A-Za-z]+", t):
            if len(m.group()) >= 2 and m.group().isupper(): types.add(m.group().lower())
    ng = Counter(); ctx = Counter()
    for w in types:
        s = [26] + [ord(c) - 97 for c in w] + [26]
        for i in range(1, len(s)): ng[(s[i - 1], s[i])] += 1; ctx[s[i - 1]] += 1
    T["acr"] = [math.log((ng[(i, j)] + 0.5) / (ctx[i] + 27 * 0.5)) for i in range(27) for j in range(27)]
    return T

# ── 양자화(91단계, TypoLanguageModel.decode 와 같은 문자표) ───────────────────
ENC = [chr(c) for c in range(33, 127) if chr(c) not in '"\\$']
def q(v, lo, hi): return ENC[max(0, min(90, round((v - lo) / (hi - lo) * 90)))]
def dq(ch, lo, hi): return lo + ENC.index(ch) / 90 * (hi - lo)

def quantize(T):
    Q = {"syl": T["syl"], "lens": T["lens"]}
    allpos = T["single"] + T["first"] + T["mid"] + T["last"]
    Q["pos_lo"], Q["pos_hi"] = min(allpos), max(allpos)
    for k in ["single", "first", "mid", "last"]:
        Q[k + "_str"] = "".join(q(v, Q["pos_lo"], Q["pos_hi"]) for v in T[k])
        Q[k] = [dq(c, Q["pos_lo"], Q["pos_hi"]) for c in Q[k + "_str"]]
        Q[k + "_floor"] = T[k + "_floor"]
    ev = [v for _, v in T["eoj"]]; Q["eoj_lo"], Q["eoj_hi"] = min(ev), max(ev)
    Q["eoj_list"] = [(e, q(v, Q["eoj_lo"], Q["eoj_hi"])) for e, v in T["eoj"]]
    Q["eoj"] = {e: dq(c, Q["eoj_lo"], Q["eoj_hi"]) for e, c in Q["eoj_list"]}
    lv = [v for pair in T["lex"].values() for v in pair if v is not None]
    Q["lex_lo"], Q["lex_hi"] = min(lv), max(lv)
    Q["lex_q"] = {w: (q(a, Q["lex_lo"], Q["lex_hi"]), " " if b is None else q(b, Q["lex_lo"], Q["lex_hi"])) for w, (a, b) in T["lex"].items()}
    Q["lex"] = {w: (dq(a, Q["lex_lo"], Q["lex_hi"]), None if b == " " else dq(b, Q["lex_lo"], Q["lex_hi"])) for w, (a, b) in Q["lex_q"].items()}
    Q["acr_lo"], Q["acr_hi"] = min(T["acr"]), max(T["acr"])
    Q["acr_str"] = "".join(q(v, Q["acr_lo"], Q["acr_hi"]) for v in T["acr"])
    Q["acr"] = [dq(c, Q["acr_lo"], Q["acr_hi"]) for c in Q["acr_str"]]
    Q["syl_index"] = {s: i for i, s in enumerate(T["syl"])}
    return Q

# ── Kotlin 판정의 파이썬 이식(사전 선별용) ──────────────────────────────────
_TLM = open(os.path.join(UTIL, "TypoLanguageModel.kt"), encoding="utf-8").read()
_HC = open(os.path.join(UTIL, "HangulConverter.kt"), encoding="utf-8").read()
def _kt_table(name):
    m = re.search(r"private const val %s =\s*((?:\"[^\"]*\"\s*\+?\s*)+)" % name, _TLM)
    return "".join(re.findall(r'"([^"]*)"', m.group(1)))
def _kt_const(name):
    return float(re.search(r"const val %s = (-?[0-9.]+)" % name, _TLM).group(1))
EN_TABLE = _kt_table("EN_TRIGRAM_TABLE"); EN_LO, EN_HI = _kt_const("EN_LO"), _kt_const("EN_HI")
KO_FLOOR = _kt_const("KO_FLOOR")
UNIT_TABLE = _kt_table("UNIT_TRANSITION_TABLE")
# 구어체에서 반복해 쓰는 낱자모(자음 + ㅠㅜㅡ). ㅐ·ㅔ 같은 모음은 늘여 쓴 영어(soooo→내ㅐㅐ)라 제외
COLLOQUIAL_JAMO = set("ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎㅠㅜㅡ")
def unit_tr(row, col):
    """구어체 단위 전이(행: 0 시작·1 음절·2+ 낱자모 / 열: 0 음절·1 끝·2+ 낱자모), -18..0 양자화."""
    return dq(UNIT_TABLE[row * 53 + col], -18.0, 0.0)
STOP = set()
for name in ["ENGLISH_STOPWORDS_BASE", "ENGLISH_TECH_ABBREVIATIONS"]:
    STOP |= set(re.findall(r'"([a-z]+)"', re.search(name + r": Set<String> = setOf\((.*?)\)", _HC, re.S).group(1)))
CENTER = 3.0 - 0.8472978603872034 * 2.0

def en_logprob(lower):
    t = 0.0; s1 = s2 = 26
    for i in range(len(lower) + 1):
        s3 = (ord(lower[i]) - 97) if i < len(lower) and 'a' <= lower[i] <= 'z' else 26
        t += dq(EN_TABLE[s1 * 729 + s2 * 27 + s3], EN_LO, EN_HI); s1, s2 = s2, s3
    return t

ENG = dict(zip("qwertyuiopasdfghjklzxcvbnm", "ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔㅁㄴㅇㄹㅎㅗㅓㅏㅣㅋㅌㅊㅍㅠㅜㅡ"))
ENG_UP = dict(zip("QWERTOP", "ㅃㅉㄸㄲㅆㅒㅖ"))
CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
JONG = "\0ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"
JUNG_C = {("ㅗ", "ㅏ"): "ㅘ", ("ㅗ", "ㅐ"): "ㅙ", ("ㅗ", "ㅣ"): "ㅚ", ("ㅜ", "ㅓ"): "ㅝ", ("ㅜ", "ㅔ"): "ㅞ", ("ㅜ", "ㅣ"): "ㅟ", ("ㅡ", "ㅣ"): "ㅢ"}
JONG_C = {("ㄱ", "ㅅ"): "ㄳ", ("ㄴ", "ㅈ"): "ㄵ", ("ㄴ", "ㅎ"): "ㄶ", ("ㄹ", "ㄱ"): "ㄺ", ("ㄹ", "ㅁ"): "ㄻ", ("ㄹ", "ㅂ"): "ㄼ",
          ("ㄹ", "ㅅ"): "ㄽ", ("ㄹ", "ㅌ"): "ㄾ", ("ㄹ", "ㅍ"): "ㄿ", ("ㄹ", "ㅎ"): "ㅀ", ("ㅂ", "ㅅ"): "ㅄ"}
SPLIT = {v: k for k, v in JONG_C.items()}
for j in JONG[1:]:
    if j not in SPLIT and j in CHO: SPLIT[j] = ("\0", j)

def eng_to_kor(s):
    """HangulConverter.convertEngToKor 와 같은 두벌식 오토마타."""
    out = []; cho = jung = -1; jong = 0
    def flush():
        nonlocal cho, jung, jong
        if cho >= 0 and jung >= 0: out.append(chr(0xAC00 + (cho * 21 + jung) * 28 + jong))
        elif cho >= 0: out.append(CHO[cho])
        elif jung >= 0: out.append(JUNG[jung])
        elif jong > 0: out.append(JONG[jong])
        cho = jung = -1; jong = 0
    for ch in s:
        j = ENG_UP.get(ch) or ENG.get(ch) or ENG.get(ch.lower())
        if j is None: flush(); out.append(ch); continue
        if j in JUNG:
            if cho < 0 and jung < 0: jung = JUNG.index(j)
            elif cho < 0 or (jung >= 0 and jong == 0):
                c = JUNG_C.get((JUNG[jung], j)) if jung >= 0 else None
                if c: jung = JUNG.index(c)
                else: flush(); jung = JUNG.index(j)
            elif jung < 0: jung = JUNG.index(j)
            else:
                rem, mv = SPLIT[JONG[jong]]
                jong = 0 if rem == "\0" else JONG.index(rem)
                flush(); cho = CHO.index(mv); jung = JUNG.index(j)
        else:
            if cho < 0 and jung < 0: cho = CHO.index(j)
            elif cho < 0 or jung < 0: flush(); cho = CHO.index(j)
            elif jong == 0:
                ji = JONG.find(j)
                if ji > 0: jong = ji
                else: flush(); cho = CHO.index(j)
            else:
                c = JONG_C.get((JONG[jong], j))
                if c: jong = JONG.index(c)
                else: flush(); cho = CHO.index(j)
    flush()
    return "".join(out)

class Model:
    def __init__(self, Q, lex=None, protect=()):
        self.Q = Q; self.lex = Q["lex"] if lex is None else lex; self.protect = set(protect)
    def pos_lp(self, kind, s):
        i = self.Q["syl_index"].get(s)
        return self.Q[kind][i] if i is not None else self.Q[kind + "_floor"]
    def ko(self, conv, colloquial=True):
        total = 0.0; units = 0; runs = []; cur = []
        for c in conv + " ":
            if is_hangul(c): cur.append(c)
            elif cur: runs.append(cur); cur = []
        seen_syl = False  # 토큰 안에서 앞서 음절이 나왔는가(영화..ㅠㅠ 의 ㅠㅠ 덩어리도 인정)
        for run in runs:
            k = len(run)
            total += self.Q["lens"][min(k, 12) - 1]
            # 낱자모: 앞서 음절이 나왔으면(재밌다ㅋㅋ) 구어체 전이 확률. 앞머리·낱자모만(zzz=조는 소리, bbbb=엄지척)은 최저
            prev = 0
            for i, c in enumerate(run):
                o = ord(c)
                if 0xAC00 <= o <= 0xD7A3:
                    total += self.pos_lp("single" if k == 1 else "first" if i == 0 else "last" if i == k - 1 else "mid", c)
                    prev = 1; seen_syl = True
                elif 0x3131 <= o <= 0x3163:
                    total += unit_tr(prev, 2 + o - 0x3131) if colloquial and seen_syl and c in COLLOQUIAL_JAMO else KO_FLOOR
                    prev = 2 + o - 0x3131
                else:
                    total += KO_FLOOR; prev = 1
            if colloquial and prev >= 2: total += unit_tr(prev, 1)
            units += k
        if not units: return None
        if len(runs) == 1 and all(0xAC00 <= ord(c) <= 0xD7A3 for c in runs[0]):
            e = self.Q["eoj"].get("".join(runs[0]))
            if e is not None: return _lae(math.log(0.5) + total, math.log(0.5) + e)
        return total
    def ko_suffix(self, conv):
        hs = [c for c in conv if is_hangul(c)]
        if not hs: return None
        return sum(KO_FLOOR if not (0xAC00 <= ord(c) <= 0xD7A3) else self.pos_lp("last" if i == len(hs) - 1 else "mid", c) for i, c in enumerate(hs))
    def acr(self, lower):
        t = 0.0; prev = 26
        for ch in lower + "\0":
            cur = (ord(ch) - 97) if 'a' <= ch <= 'z' else 26
            t += self.Q["acr"][prev * 27 + cur]; prev = cur
        return t
    def lexp(self, tok, upper):
        runs = [m.group().lower() for m in re.finditer(r"[A-Za-z]+", tok)]
        if not runs: return None
        s = 0.0
        for r in runs:
            e = self.lex.get(r)
            if e is None or (e[1] if upper else e[0]) is None: return None
            s += e[1] if upper else e[0]
        return s
    def tail(self, tok, lx):
        t = tok.strip("".join(chr(c) for c in range(128) if not ('a' <= chr(c) <= 'z' or 'A' <= chr(c) <= 'Z')))
        i = 0
        while i < len(t) and 'A' <= t[i] <= 'Z': i += 1
        if i < 2 or i >= len(t): return None
        tail = t[i:]
        if len(tail) < 2 or not all('a' <= c <= 'z' or c in "QWERTOP" for c in tail): return None
        tk = eng_to_kor(tail)
        if len(tail) == 2 and tk not in {"이", "가", "도", "에", "로", "나", "만", "랑", "야", "요", "고", "지", "게", "서", "는", "은", "를", "을", "의", "와", "과"}: return None
        ko = self.ko_suffix(tk)
        if ko is None: return None
        z = ((ko - en_logprob(tail.lower())) / len(tail) + 1.0 - CENTER) / 2.0
        kt = self.ko(eng_to_kor(tok))
        if lx is not None and kt is not None: z = min(z, kt - lx)
        return z
    def score(self, tok, ctx=False):
        letters = [c for c in tok if c.isalpha()]; lower = "".join(letters).lower(); n = len(letters)
        mappable = sum(1 for c in letters if (ENG_UP.get(c) or ENG.get(c) or ENG.get(c.lower())))
        if n < 3 or mappable == 0 or lower in STOP: return 0.0
        if not ctx and lower in self.protect: return 0.0
        all_upper = all(c.isupper() for c in letters)
        L = [c for c in tok if 'a' <= c <= 'z' or 'A' <= c <= 'Z']
        ups = [c.isupper() for c in L]
        kind = "lower" if not any(ups) else "upper" if all(ups) else "title" if ups[0] and not any(ups[1:]) else "mixed"
        n_off = sum(1 for i, c in enumerate(L) if i > 0 and c.isupper() and c.lower() not in SHIFT_KEYS)
        n_on = sum(1 for c in L if c.islower() and c not in SHIFT_KEYS)
        acr = P["ACR"][kind] + self.acr(lower)
        # 주변 한글 문맥: 첫 글자가 그 가설로 칠 수 있는 모양(꺼짐 = 소문자 또는 Shift 키 대문자 / 켜짐 = 대문자 또는
        # Shift 키 소문자)이면 강하게, 아니면(`Dirk`·`Duden` 같은 이름) 예전 값만
        cx_off = (P["CTX_STRONG"] if L[0].islower() or L[0].lower() in SHIFT_KEYS else P["CTX"]) if ctx else 0.0
        cx_on = (P["CTX_STRONG"] if L[0].isupper() or L[0] in SHIFT_KEYS else P["CTX"]) if ctx else 0.0
        best = -1e18; lx = self.lexp(tok, False)
        if not all_upper:
            ko = self.ko(eng_to_kor(tok))
            if ko is not None:
                inner = any(c.isupper() and c.lower() in SHIFT_KEYS for c in L[1:])
                term = -P["A"] * n_off if n_off else (P["B"] if inner else 0.0)
                z = ((ko - en_logprob(lower) + cx_off) / n + term - CENTER) / 2.0
                if lx is not None: z = min(z, ko - lx)
                best = max(best, min(z, ko - acr - P["A"] * n_off))
        if sum(ups) * 2 > len(L):
            ko2 = self.ko(eng_to_kor(tok.swapcase()), colloquial=False)  # CapsLock 외침(AHHHH)을 ㅎㅎㅎ로 설명하지 않게
            if ko2 is not None:
                inner2 = any(c.islower() and c in SHIFT_KEYS for c in L)
                term = -P["A"] * n_on if n_on else (P["B"] if inner2 else 0.0)
                en = _lae(math.log(1 - P["P_ACR"]) + en_logprob(lower), math.log(P["P_ACR"]) + self.acr(lower)) if all_upper else en_logprob(lower)
                short = P["CAPS_SHORT"] if n <= 3 else 0.0; cp = P["CAPS_PRIOR"] + short
                z = ((ko2 - en + cx_on) / n + term + P["CAPS_BONUS"] - CENTER) / 2.0 + short
                lxu = self.lexp(tok, True)
                if lxu is not None: z = min(z, cp + ko2 - lxu)
                best = max(best, min(z, cp + ko2 - acr - P["A"] * n_on))
        if not ctx and best > -1e17: best += P["NOCTX_DELTA"]  # 두 한영타 가설에만(약어+꼬리 규칙은 그대로)
        tl = self.tail(tok, lx)
        if tl is not None: best = max(best, tl)
        x = best - P["SHIFT"]
        if x < -500: return 0.0  # 넘침 방지(사실상 0)
        return 1.0 / (1.0 + math.exp(-x)) * mappable / n

def _lae(a, b):
    if a < b: a, b = b, a
    return a + math.log1p(math.exp(b - a))

# ── Kotlin 내보내기 ─────────────────────────────────────────────────────
def kstr(name, s, doc):
    body = " +\n".join(f'        "{s[i:i + 100]}"' for i in range(0, len(s), 100))
    return f"    /** {doc} */\n    const val {name} =\n{body}\n"

def export(Q, lex_words, protect=()):
    out = ["package com.langsense.app.util", "", "/**",
           " * 한영타 판정 보조 표(2026-09, `TypoLanguageModel` 참조). 전부 실측 데이터에서 만든 뒤 91단계로 양자화했다 —",
           " * 만드는 방법·출처·검증 수치는 docs/한영타_검증.md. 손으로 고치지 말 것(tools/typo-model/build_tables.py 로 재생성).",
           " */", "internal object TypoTables {"]
    out.append(kstr("POS_SYLLABLES", Q["syl"], f"학습 글에 나온 한글 음절 {len(Q['syl'])}자(부호점 순 — 이진 탐색)."))
    out.append(kstr("POS_TABLE", "".join(Q[k + "_str"] for k in ["single", "first", "mid", "last"]),
                    "음절별 위치 로그확률: [홀로 | 첫 | 가운데 | 끝] 네 구간이 각각 POS_SYLLABLES 와 같은 길이로 이어 붙어 있다."))
    out.append(f"    const val POS_LO = {Q['pos_lo']!r}\n    const val POS_HI = {Q['pos_hi']!r}\n")
    out.append(f"    /** 표에 없는 음절의 위치별 로그확률(평활 바닥값): 홀로, 첫, 가운데, 끝. */\n    val POS_FLOOR = doubleArrayOf({', '.join(repr(Q[k + '_floor']) for k in ['single', 'first', 'mid', 'last'])})\n")
    out.append(f"    /** 어절(한글 연속 구간) 길이 1..12+ 의 로그확률. */\n    val POS_LEN = doubleArrayOf({', '.join(repr(v) for v in Q['lens'])})\n")
    out.append(kstr("EOJ_WORDS", ",".join(e for e, _ in Q["eoj_list"]), f"자주 쓰는 어절 상위 {len(Q['eoj_list'])}개(쉼표 구분, 빈도 순)."))
    out.append(kstr("EOJ_LEVELS", "".join(c for _, c in Q["eoj_list"]), "EOJ_WORDS 순서대로 어절 로그확률."))
    out.append(f"    const val EOJ_LO = {Q['eoj_lo']!r}\n    const val EOJ_HI = {Q['eoj_hi']!r}\n")
    out.append(kstr("LEX_WORDS", ",".join(lex_words), f"Shift 증인 사전 {len(lex_words)}개(쉼표 구분, 사전순): 한국어 글에서 Shift 가 무의미한 키에 대문자로 쓰인 적이 있는 라틴 문자열 중 판정에 영향을 주는 것."))
    out.append(kstr("LEX_LOWER", "".join(Q["lex_q"][w][0] for w in lex_words), "소문자로 쓰일 로그확률(소문자 출현 + 0.2×대문자 증인)."))
    out.append(kstr("LEX_UPPER", "".join(Q["lex_q"][w][1] for w in lex_words), "전부 대문자로 쓰일 로그확률(공백 = 대문자 출현 없음)."))
    out.append(f"    const val LEX_LO = {Q['lex_lo']!r}\n    const val LEX_HI = {Q['lex_hi']!r}\n")
    out.append(kstr("ACR_BIGRAM", Q["acr_str"], "대문자 약어 글자 bigram(27기호: a~z + 경계) 로그확률, [이전*27 + 다음]."))
    out.append(f"    const val ACR_LO = {Q['acr_lo']!r}\n    const val ACR_HI = {Q['acr_hi']!r}\n")
    out.append(kstr("COMMON_EN_WORDS", ",".join(protect), f"흔한 영어 단어 {len(protect)}개(쉼표 구분, 사전순): 자막 빈도 상위 {P['PROTECT_TOP']}개 중 주변 한글 없이도 한영타로 볼 만큼 한글 같은 것 — 주변 한글이 없으면 판정하지 않는다."))
    out.append("}\n")
    with open(OUT, "w", encoding="utf-8") as f: f.write("\n".join(out))

def main():
    print("1/4 말뭉치 집계…", file=sys.stderr)
    pos, latin = count_all()
    print("2/4 표 만들기…", file=sys.stderr)
    Q = quantize(build_tables(pos, latin))
    print("3/4 판정에 영향을 주는 증인 사전 항목 고르기…", file=sys.stderr)
    bare = Model(Q, lex={})
    need = sorted(w for w in Q["lex"]
                  if any(max(bare.score(f), bare.score(f, ctx=True)) >= P["LEX_NEED"] for f in {w, w.capitalize(), w.upper()}))
    print(f"   증인 {len(Q['lex'])}개 중 {len(need)}개", file=sys.stderr)
    protect = common_english_protect(Model(Q, lex={w: Q["lex"][w] for w in need}))
    print(f"   흔한 영어 보호 {len(protect)}개", file=sys.stderr)
    print("4/4 내보내기 →", os.path.relpath(OUT, ROOT), file=sys.stderr)
    export(Q, need, protect)

def common_english_protect(model):
    """자막 단어 빈도(tools/typo-model/data/en_subtitles_full.txt) 상위 PROTECT_TOP 개 중 문맥 없는 판정에서
    PROTECT_NEED 이상 나오는 흔한 영어 단어(`goal`·`dude`·`gosh`·`vodka`). 한국인이 실제로 치는 영어는 흔한 단어다."""
    words, seen = [], set()
    with open(os.path.join(DATA, "en_subtitles_full.txt"), encoding="utf-8") as f:
        for line in f:  # 빈도 내림차순
            w = line.split(" ", 1)[0].lower()
            if w.isascii() and w.isalpha() and len(w) >= 3 and w not in seen:
                seen.add(w); words.append(w)
                if len(words) >= P["PROTECT_TOP"]: break
    return sorted({w for w in words if model.score(w) >= P["PROTECT_NEED"]})

if __name__ == "__main__":
    main()
