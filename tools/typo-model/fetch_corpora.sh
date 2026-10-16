#!/usr/bin/env bash
# 한영타 판정 보조 표(TypoTables.kt) 학습용 한국어 말뭉치 내려받기 — 원본(약 200MB)은 저장소에 넣지 않는다.
# 출처와 SHA-256 을 고정해 언제 받아도 같은 파일인지 확인한다(검증용 데이터는 tools/eval-data/fetch.sh).
# 사용법: tools/typo-model/fetch_corpora.sh   → tools/typo-model/data/ (.gitignore 대상)
# 받은 뒤: python3 tools/typo-model/build_tables.py   (방법·수치는 docs/한영타_검증.md)
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)/data"
mkdir -p "$DIR"

# 파일명|URL|SHA-256
FILES=(
  # 네이버 뉴스 기사(요약 데이터셋의 본문) — train 만 표 학습에 쓴다
  "news_train.csv|https://huggingface.co/datasets/daekeun-ml/naver-news-summarization-ko/resolve/main/train.csv|039a89adedfb305208475bc2c65e63769b021af5bb6f278168d36820386f1288"
  # KLUE — 뉴스 제목(YNAT) · 위키/뉴스 지문(MRC)
  "ynat_train.parquet|https://huggingface.co/datasets/klue/klue/resolve/main/ynat/train-00000-of-00001.parquet|062c3b51c1ca34ed23c8fd19ffa5ea0ddcd914a95aeb9077d89576d3ef71d123"
  "mrc_train.parquet|https://huggingface.co/datasets/klue/klue/resolve/main/mrc/train-00000-of-00001.parquet|45fcfd50b99aed4cd55c8c5856e42aa4a5139808cc36b36994b3c1991aca93dd"
  # 구어체: 온라인 댓글(unsmile·혐오표현) · 챗봇 대화
  "unsmile_train.tsv|https://raw.githubusercontent.com/smilegate-ai/korean_unsmile_dataset/main/unsmile_train_v1.0.tsv|f560a2f624d98f7cd9dc5ec6e5368efb7edd9bfc01cfa963538facfad9fc6001"
  "hate_train.tsv|https://raw.githubusercontent.com/kocohub/korean-hate-speech/master/labeled/train.tsv|ebebacdcd023af2c4acc8c0a37695fb6433ac04fc009feff8f222724e303a5a9"
  "chatbot.csv|https://raw.githubusercontent.com/songys/Chatbot_data/master/ChatbotData.csv|287eb129695b577321c80ad397bb3c2279164d4ca577874d129fd3db5b30afe2"
  # 구어체 보강(2026-09-30): 뉴스 댓글(K-MHaS) · 페르소나 채팅 · 다중 세션 일상 대화 · 일상 질문 — HF 자동 변환 parquet
  "kmhas_train.parquet|https://huggingface.co/api/datasets/jeanlee/kmhas_korean_hate_speech/parquet/default/train/0.parquet|b46422a5d19e3364ddb62aea68004c1c607f525a0d4ba703a95b397958abbd32"
  "persona_train.parquet|https://huggingface.co/api/datasets/NLPBada/korean-persona-chat-dataset/parquet/default/train/0.parquet|9e74835464173de5ea934fa324ec434fb204fa20d03c66b836c162fcf2a2170e"
  "multisession_train.parquet|https://huggingface.co/api/datasets/nayohan/141_korean_multi_session_dialogue/parquet/default/train/0.parquet|6a1c21b582076150b4bfdc06b0635bf146bc8da67474514a875ed82495ca8f5c"
  # 흔한 영어 단어 보호 목록(2026-10-01)용 영어 단어 빈도 — OpenSubtitles 2018(hermitdave/FrequencyWords)
  "en_subtitles_full.txt|https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_full.txt|7fea67ab954e2c01df6c608c9826e594cf36f8823b3243554f88245fb75dc506"
  "safeconv_train.parquet|https://huggingface.co/api/datasets/jojo0217/korean_safe_conversation/parquet/default/train/0.parquet|1fdc85292a4f765d1eedf4b2b1a592a3ffea2b80c70692d1a07653db6e473e9b"
  # 한국어 위키백과 2026-09-01 덤프 1번 조각(문서 2.6만 개, 93MB). 위키미디어는 날짜별 덤프를 몇 달만
  # 보관하므로 없어졌으면 다른 날짜의 같은 조각을 받아도 된다(수치가 조금 달라질 수 있음 — 체크섬 경고).
  "wiki1.xml.bz2|https://dumps.wikimedia.org/kowiki/20260901/kowiki-20260901-pages-articles1.xml-p1p82407.bz2|4155a5ac6dc45c7fd8b2eef471f52eedf3149d954a36535d10ae4add4a1f682c"
)

fail=0
for entry in "${FILES[@]}"; do
  IFS='|' read -r name url sha <<<"$entry"
  target="$DIR/$name"
  if [[ -f "$target" ]] && echo "$sha  $target" | sha256sum -c --status; then
    echo "있음    $name"
    continue
  fi
  echo "받는 중 $name"
  curl -fsSL --retry 3 -o "$target" "$url"
  if echo "$sha  $target" | sha256sum -c --status; then
    echo "확인    $name"
  else
    echo "⚠️ 체크섬 불일치: $name (출처 파일이 바뀌었을 수 있음 — 표가 달라질 수 있다)" >&2
    fail=1
  fi
done
exit $fail
