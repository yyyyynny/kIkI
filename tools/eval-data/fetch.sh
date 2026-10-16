#!/usr/bin/env bash
# 한영타 판정 검증용 공개 데이터셋 내려받기.
# 원본(약 55MB)은 저장소에 넣지 않는다 — git 기록에 영구히 남아 클론이 무거워지기 때문.
# 대신 출처와 SHA-256 을 여기 고정해, 언제 받아도 같은 파일인지 확인한다.
# 사용법: tools/eval-data/fetch.sh   → tools/eval-data/data/ 에 저장(.gitignore 대상)
# 받은 뒤: ./gradlew testDebugUnitTest --tests "com.langsense.app.eval.*" -i
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)/data"
mkdir -p "$DIR"

# 파일명|URL|SHA-256
FILES=(
  # 네이버 영화 리뷰(NSMC) — 한국어 구어체 문장 20만 개(train 15만/test 5만)
  "ratings_train.txt|https://raw.githubusercontent.com/e9t/nsmc/master/ratings_train.txt|e03b7d14e9e41be8d464a28057cd25d7396c53e67aa7fd5f7e552c59b0ee2940"
  "ratings_test.txt|https://raw.githubusercontent.com/e9t/nsmc/master/ratings_test.txt|8ac9f64052f11dbf6ae0acb5e038f03d90a76f0eda7820cfb3a92d02edfcebda"
  # AG News — 영어 뉴스 기사 12.7만 개(train 12만/test 7.6천)
  "train.csv|https://raw.githubusercontent.com/mhjabreel/CharCnn_Keras/master/data/ag_news_csv/train.csv|76a0a2d2f92b286371fe4d4044640910a04a803fdd2538e0f3f29a5c6f6b672e"
  "test.csv|https://raw.githubusercontent.com/mhjabreel/CharCnn_Keras/master/data/ag_news_csv/test.csv|521465c2428ed7f02f8d6db6ffdd4b5447c1c701962353eb2c40d548c3c85699"
  # dwyl english-words — 영어 단어 목록 47만 개(대소문자·약어 포함)
  "words.txt|https://raw.githubusercontent.com/dwyl/english-words/master/words.txt|39a4ead879cc8283de87f2ec58e7b4b340d6caa724db1b52a91dccf2f273c5e7"
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
    # 출처가 파일을 바꿨다면 검증 수치가 달라질 수 있으니 조용히 넘어가지 않는다.
    echo "⚠️ 체크섬 불일치: $name (출처 파일이 바뀌었을 수 있음 — 수치 비교 전에 확인할 것)" >&2
    fail=1
  fi
done
exit $fail
