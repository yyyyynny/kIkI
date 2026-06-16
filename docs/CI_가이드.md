# CI 가이드 (GitHub Actions)

> 이 문서는 kIkI 저장소의 자동 검사(CI)가 **무엇을 하는지**, **실패하면 어떻게 원인을 찾는지**,
> 그리고 **GitHub 웹에서 보호 규칙을 어떻게 켜는지**(비전공자용 클릭 안내)를 설명합니다.

---

## 1. CI 가 하는 일

코드를 올리거나(push) PR 을 만들면 GitHub 가 자동으로 아래 검사를 돌립니다.
설정 파일: `.github/workflows/ci.yml`

| 잡(Job) 이름 | 하는 일 | 실행 명령 |
|---|---|---|
| **빌드 · 린트** | 코드 정적 검사 + 디버그 APK 빌드 | `./gradlew lintDebug` → `./gradlew assembleDebug` |
| **단위 테스트** | 순수 로직 단위 테스트 | `./gradlew testDebugUnitTest` |

- 두 잡은 **동시에** 돌아갑니다(따로 실행 → 빠르고, 실패 원인이 분리되어 보임).
- 빌드가 끝나면 결과물을 **아티팩트**로 올립니다:
  - `kiki-debug-apk` — 빌드된 디버그 APK(내려받아 기기에 설치해 볼 수 있음)
  - `lint-report`, `unit-test-report` — 검사/테스트 상세 리포트(실패해도 항상 업로드)

### 언제 도는가 (트리거)
- **PR**을 만들거나 PR에 새 커밋을 올릴 때
- **`main`** 또는 **`claude/**`** 브랜치에 push 할 때
- **수동 실행**: 저장소 상단 **Actions** 탭 → 왼쪽 **CI** → 오른쪽 **Run workflow** 버튼

### 툴체인(고정 버전)
- JDK **17** (Temurin) · Gradle **8.11.1** · Android compileSdk **35** · Node.js **24**
- 자세한 고정 버전은 루트 `CLAUDE.md` 의 "빌드/툴체인 버전(고정)" 표 참조.

---

## 2. CI 가 실패했을 때 — 원인 찾는 법

### 2-1. 어디서 보나
1. 저장소 상단 **Actions** 탭을 누릅니다.
2. 빨간 ❌ 가 붙은 실행을 클릭합니다.
3. 실패한 잡(**빌드 · 린트** 또는 **단위 테스트**)을 클릭합니다.
4. 빨간 ❌ 가 붙은 **단계(step)** 를 펼치면 로그가 보입니다.
   로그는 `▸ Android Lint 실행` 처럼 단계별로 접혀 있어(그룹) 원하는 부분만 펼쳐 봅니다.

### 2-2. 증상별 원인
| 로그에 보이는 것 | 의미 | 보통 해결 |
|---|---|---|
| `> Task :app:lintDebug FAILED` + `Error: ...` | 린트 **오류**(경고는 통과, 오류만 실패) | 해당 파일/줄 수정. 상세는 아티팩트 `lint-report` 의 HTML |
| `> Task :app:testDebugUnitTest FAILED` | 단위 테스트 실패 | 로그의 `expected:<..> but was:<..>` 확인, `unit-test-report` 다운로드 |
| `> Task :app:assembleDebug FAILED` | 컴파일/빌드 오류 | 로그 맨 위의 `e: ...Kotlin` 메시지 위치 수정 |
| `SDK location not found` / `Failed to install` | SDK 설치 단계 문제(드묾) | 재실행(아래 2-3) |
| `Could not resolve ...` (네트워크) | 일시적 네트워크 | 재실행 |

> 팁: 거의 모든 빌드 명령에 `--stacktrace` 가 붙어 있어, 실패 시 어떤 작업에서 났는지 로그에 바로 나옵니다.

### 2-3. 다시 돌리기
- 실행 화면 오른쪽 위 **Re-run jobs** → **Re-run failed jobs** 를 누르면 실패한 잡만 다시 돕니다.

### 2-4. 로컬에서 미리 확인(권장)
PR 올리기 전에 같은 검사를 컴퓨터에서 먼저 돌려볼 수 있습니다.
```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```
모두 통과하면 CI 도 통과합니다.

---

## 3. (사람이 직접) 브랜치 보호 규칙 켜기 — 클릭 안내

> CI 가 실패한 코드가 `main` 에 바로 들어가지 못하게 막는 설정입니다.
> 아래는 GitHub **웹사이트**에서 마우스로 하는 작업입니다. (코드 작업 아님)
> 화면 문구는 영어로 표기했습니다. 괄호 안은 한국어 설명입니다.

### 준비
- 먼저 이 브랜치를 한 번 push 해서 **Actions 탭에 CI 가 최소 1번 실행**되게 하세요.
  (그래야 4-④ 단계에서 검사 이름이 목록에 떠서 고를 수 있습니다.)

### 단계 (Rulesets 방식 — 최신 GitHub 권장)
1. 저장소 페이지 맨 위 메뉴에서 **Settings**(설정, 톱니바퀴 모양 아님 · 글자 메뉴) 를 클릭합니다.
   - 안 보이면 `···` 또는 화면을 오른쪽으로 넓혀 보세요. 저장소 주인만 보입니다.
2. 왼쪽 사이드바를 아래로 내려 **Rules**(규칙) 를 펼치고 → **Rulesets** 를 클릭합니다.
3. 오른쪽 위 초록/회색 **New ruleset** 버튼 → **New branch ruleset**(브랜치 규칙) 를 클릭합니다.
4. 항목을 다음처럼 채웁니다.
   - **Ruleset Name**(규칙 이름): 아무 이름, 예) `protect-main`
   - **Enforcement status**(적용 상태): **Active** 로 바꿉니다. (이게 "켜짐"입니다)
   - **Target branches**(적용 대상 브랜치): **Add target** → **Include default branch** 를 고릅니다.
     (= 기본 브랜치 `main` 에 적용)
   - 아래 **Rules** 목록에서 체크박스를 켭니다:
     - ☑ **Require a pull request before merging**(병합 전 PR 필수)
       - 펼쳐서 **Required approvals**(필요 승인 수) 를 **0** 으로 둡니다. (혼자 운영이면 0)
     - ☑ **Require status checks to pass**(검사 통과 필수)
       - 그 안의 **Add checks**(검사 추가) 버튼을 누르고, 검색창에 아래 두 이름을 하나씩 입력해 추가:
         - `빌드 · 린트`
         - `단위 테스트`
       - (이름이 안 뜨면 "준비" 단계대로 CI 가 한 번 돌았는지 확인하세요.)
       - 같은 칸 안의 세부 옵션 2개도 켜둡니다(둘 다 권장):
         - ☑ **Require branches to be up to date before merging**(최신 코드로 검사 후 병합)
           — PR 병합 전, 브랜치가 `main` 최신 코드를 반영한 상태에서 CI 를 통과해야 함. 각각은 통과해도
           합치면 깨지는 충돌을 막아준다. 혼자 운영이면 가끔 "Update branch" 한 번 누르는 정도라 켜둬도 무해(정석).
           ※ 상태 검사를 최소 1개 켜야 효과가 생긴다(위에서 2개 추가했으므로 충족).
         - ☑ **Do not require status checks on creation**(브랜치 생성 시에는 검사 면제)
           — 새 브랜치를 처음 만드는 순간에는 아직 검사가 돌 수 없으므로, 이 옵션으로 "검사 때문에 브랜치
           생성 자체가 막히는" 닭-달걀 문제를 방지한다. 켜두는 게 권장 기본값이며, 실제 `main` 병합 보호는 그대로 유지된다.
     - ☑ **Block force pushes**(강제 푸시 차단)
5. 맨 아래 초록색 **Create**(생성) 버튼을 누릅니다. 끝.

이제 `main` 으로 가는 PR 은 **두 검사가 모두 초록 ✅ 이어야** 병합 버튼이 활성화됩니다.

### (구버전 화면일 때) Branch protection rules 방식
저장소가 옛 화면이면 **Settings → Branches → Add branch protection rule** 에서:
- **Branch name pattern**: `main`
- ☑ Require a pull request before merging (Approvals: 0)
- ☑ Require status checks to pass before merging → 검색해 `빌드 · 린트`, `단위 테스트` 추가
- ☑ (선택) Do not allow bypassing / force push 차단
- **Create** 클릭.

---

## 4. 자주 묻는 것
- **Q. CI 가 비용이 드나요?** 공개(public) 저장소면 GitHub Actions 는 무료·무제한입니다.
- **Q. APK 는 어디서 받나요?** Actions → 해당 실행 → 맨 아래 **Artifacts** 의 `kiki-debug-apk`.
- **Q. 검사 이름을 바꾸고 싶어요.** `.github/workflows/ci.yml` 의 각 잡 `name:` 값을 바꾸면 됩니다.
  단, 바꾸면 위 3-④에서 추가한 status check 이름도 새 이름으로 다시 추가해야 합니다.
