# 멤버 가이드

이 문서 한 장에 멤버가 알아야 할 모든 것이 있습니다. 첫날 정독 1번 + 가끔 검색.

> AI 활용 룰은 [CLAUDE.md](./CLAUDE.md) 별도 (Claude Code · Gemini CLI가 자동으로 읽음).

---

## 목차

1. [환경 셋업 (첫날 1번)](#1-환경-셋업-첫날-1번)
2. [본인 폴더 위치](#2-본인-폴더-위치)
3. [주차별 빌드 방식 (1주차 vs 4주차+)](#3-주차별-빌드-방식)
4. [매주 흐름](#4-매주-흐름)
5. [브랜치 / 커밋](#5-브랜치--커밋)
6. [측정 가이드 (s3 단계)](#6-측정-가이드-s3-단계)
7. [학습 기록 (자유)](#7-학습-기록-자유)
8. [AI에게 어떻게 질문할까](#8-ai에게-어떻게-질문할까)
9. [막힐 때](#9-막힐-때)

---

## 1. 환경 셋업 (첫날 1번)

### 필수 설치

| 도구 | macOS | Windows | 확인 |
|---|---|---|---|
| **Java 21** | `brew install --cask temurin@21` | https://adoptium.net | `java -version` |
| **IntelliJ IDEA Community** | https://jetbrains.com/idea/download/ | 동일 | 실행 |
| **Docker Desktop** | https://docker.com/products/docker-desktop | 동일 | `docker --version` |
| **AI 도구 (둘 중 하나)** | Claude Code 또는 Gemini CLI | 동일 | 자동 로드 |

> 1주차는 Docker 없어도 OK. 2주차부터 DB 띄움.

### IntelliJ로 프로젝트 열기

CSstudy 폴더 통째로 열기. 멀티 모듈 Gradle 셋업이 되어 있어서:
- 1주차 `example/`, 4주차+ 모든 멤버 폴더가 자동 인식됨
- 각 `Application.java` / `Stage*.java` 옆 ▶ 버튼 표시
- 운영자가 `scaffold-week.sh` 실행하면 → 우측 하단 **Reload Gradle Project** 한 번만

처음 열 때 Gradle sync 1~2분 (JDK 21 자동 다운로드 가능).

### Docker 띄우기 (2주차+)

```bash
docker compose up -d         # postgres + redis 시작
docker compose down          # 종료
docker compose down -v       # 데이터 볼륨까지 삭제 (DB 완전 초기화)
```

postgres 접속 확인:
```bash
docker exec csstudy-postgres psql -U csstudy -d csstudy -c "SELECT 1"
# 결과에 1 나오면 정상
```

> 각자 자기 PC에서 docker 띄우니 DB는 본인 전용. 다른 멤버와 충돌 X.

### 환경 검증 — example 한 번 돌려보기 (필수, 10분)

본격 학습 전에 **example 4단계를 다 실행**해서 환경/측정 흐름을 직접 체험합니다. 이걸 안 하면 1주차 시작할 때 "Run 버튼 어디?", "measurements.md 어디 생기지?" 헤매게 됩니다.

#### 1. example 폴더 위치
```
topics/01-jvm-thread/example/src/main/java/
├── BankAccount.java       (도메인 클래스)
├── MeasurementLog.java    (측정 자동 기록 helper)
├── Stage1Race.java        ← 먼저 실행
├── Stage2Visibility.java
├── Stage3Measurement.java
└── Stage4VirtualThreads.java
```

#### 2. 4개 Stage 순서대로 ▶

각 파일 열고 main 메서드 옆 **녹색 ▶** 클릭. 콘솔 출력 + 자동 기록 메시지 확인:

```
→ /Users/.../topics/01-jvm-thread/example/measurements.md 에 기록됨
```

#### 3. measurements.md 자동 생성 확인

좌측 트리에 **`topics/01-jvm-thread/example/measurements.md`** 자동 생성됨. 열어보면:

```
- [05-01 17:30] s1 · race 재현 (200스레드 × 1000회): 누락 5.0 / 8.0ms
- [05-01 17:31] s2 · stop flag visibility (volatile 없음): 누락 1.0 / 6010.0ms
- [05-01 17:32] s3 · 해결책 없음: 누락 2.8 / 12.3ms
- [05-01 17:32] s3 · synchronized: 누락 0.0 / 7.6ms
- [05-01 17:32] s3 · AtomicInteger: 누락 0.0 / 6.9ms
- [05-01 17:33] s4 · Virtual Threads (10000개): 누락 13.0 / 30.0ms
```

#### 4. 본인 폴더 Main.java도 ▶

```
topics/01-jvm-thread/members/{본인이름}/src/main/java/Main.java
```

콘솔에 `1주차 학습 시작! ...` 출력되면 본인 모듈 환경 OK.

#### 검증 체크리스트

- [ ] example 4개 Stage 다 실행 성공
- [ ] example/measurements.md 6줄 누적 확인
- [ ] 본인 폴더 Main.java ▶ 실행 성공
- [ ] (4주차+ 시작 전엔) docker compose up -d → postgres 접속 확인

→ 다 ✅면 본격 1주차 작업 시작 가능.

> example/measurements.md는 `.gitignore`에 등록되어 있어 git에 안 올라감 (참고 코드 부산물). 본인 폴더의 measurements.md는 정상 commit.

---

## 2. 본인 폴더 위치

```
topics/{이번주차}/members/{본인이름}/
예) topics/01-jvm-thread/members/chanhyeok/
```

폴더명(영문) 매핑은 [README.md](./README.md#멤버) 참고.

> **도메인은 매주 새로 선택.** 1주차에 쿠폰을 했다고 2주차도 쿠폰일 필요 없음. 매 주차 `scenario.md` 첫 부분에 그 주제에 맞는 도메인 후보가 제시됨 — 거기서 1개 골라 시작.

---

## 3. 주차별 빌드 방식

| 주차 | 코드 형태 | 멤버가 할 일 |
|---|---|---|
| **1주차** (JVM/스레드) | 순수 Java `main()` | 본인 폴더에 `Main.java` 만들고 ▶ |
| **2~3주차** (트랜잭션/락) | Java + DB 연결 | `main()` + JDBC |
| **4주차부터** (Spring 본격) | Spring Boot | **이미 운영자가 다 깔아둠** — IntelliJ에서 ▶만 |

### 1주차 — 가장 단순

본인 폴더에 이미 `Main.java`가 들어있음. 열어서 ▶ 한 번 눌러 환경 검증 후, 본인 도메인 코드로 채워나가기.

```
topics/01-jvm-thread/members/{본인이름}/
├── build.gradle              ← Java 21 설정 (손 안 댐)
└── src/main/java/Main.java   ← 여기에 코드
```

> 코드 어떻게 시작할지 막히면 [`topics/01-jvm-thread/example/`](./topics/01-jvm-thread/example/) 참고 (은행 잔고 도메인 완성 코드 — 베끼지 말고 흐름만).

### 4주차부터 — 운영자가 미리 깔아둔 폴더 사용

운영자가 페이즈 시작 며칠 전에 `scripts/scaffold-week.sh`로 7명 폴더에 Spring Boot 프로젝트 자동 생성. 멤버는:

1. IntelliJ Git → Pull (또는 Cmd+T)
2. 좌측 트리에서 `topics/04-ioc-bean/members/{본인이름}/` 펼치기
3. `Application.java` 옆 ▶ 클릭
4. 기본 포트 8080으로 본인 Spring Boot 서버 뜸. 끝.

> 각자 자기 PC에서 띄우니 기본 포트 8080 그대로 사용. 다른 멤버와 충돌 X.

---

## 4. 매주 흐름

> 시나리오는 **금요일에 공개**됩니다. (목 모임 회고 → 운영자가 다음 주 다듬음 → 금요일 공개)

| 시점 | 무엇 |
|---|---|
| **목요일 모임 끝** | 운영자: 발표/회고 듣고 다음 주 시나리오 방향 정리 |
| **금요일 (운영자)** | 다음 주 `scenario.md` 작성 완료 → 디스코드 공지 |
| 토 ~ 일 | 멤버: scenario.md 읽기 + 도메인 선택 + s1 (race) + s2 (visibility) 시도 |
| 월요일 모임 직전 | 본인 폴더에서 s1 + s2 코드 → **Draft PR** (브랜치 `{이름}/w{NN}`) |
| **월요일 모임 (2h)** | 각자 Draft PR 띄우고 시도 발표 (**머지 X**) |
| 화 ~ 수 | 같은 브랜치에 s3 (측정 + 해결) 커밋 추가 |
| 수 23:59 | PR을 **Ready for review**로 전환 (목 모임 24h 전) |
| **목요일 모임 (2h)** | 각자 해결 발표 → 운영자가 머지 |
| 금 ~ 일 | (자유) 학습 기록 정리 — 블로그/노션/메모 + 다음 주 사이클 시작 |

### PR 상태 흐름

**한 주 = 한 브랜치 = 한 PR.** 같은 PR이 상태만 Draft → Ready → Merged로 바뀝니다. 새 PR 만들지 않음.

| 단계 | PR 상태 | 머지 가능? | 리뷰 코멘트? |
|---|---|---|---|
| 1. 브랜치 만들고 s1 + s2 코드 → **Draft PR 만듦** | Draft | ❌ 버튼 회색 | ✅ 가능 |
| 2. 월 모임 발표 (화면 띄움) | Draft 그대로 | ❌ | ✅ |
| 3. 화~수 같은 브랜치에 s3 푸시 | Draft 그대로 (자동 누적) | ❌ | ✅ |
| 4. 수 23:59 — **"Ready for review"로 전환** | Ready | ✅ 버튼 활성화 | ✅ + 자동 알림 |
| 5. 목 모임 발표 | Ready | ✅ | ✅ |
| 6. 운영자가 머지 | Merged | — | — |
| 7. 브랜치 삭제 | (PR 닫힘) | — | — |

#### Draft PR이란?

GitHub 기능 — "아직 작업 중이니까 머지하지 말고 보여주기만 할게" 표시. 머지 버튼이 회색으로 막혀있어서 **실수 머지 방지 안전장치** 역할. 화~수에 운영자/멤버가 코멘트로 리뷰 남기면, 본인은 그거 보고 다음 커밋에 반영.

#### Draft 만드는 법

| 도구 | 방법 |
|---|---|
| GitHub 웹 | PR 만들 때 "Create pull request" 옆 ▼ → "Create draft pull request" |
| gh CLI | `gh pr create --draft` |
| IntelliJ | PR 생성 대화상자에 "Mark as draft" 체크 |

#### Ready로 전환하는 법

| 도구 | 방법 |
|---|---|
| GitHub 웹 | PR 페이지 하단 "Ready for review" 버튼 클릭 |
| gh CLI | `gh pr ready` |

### 멤버 체크리스트 (매주)

- [ ] 일요일 — `topics/{이번주차}/scenario.md` 읽기
- [ ] 월요일 전 — 도메인 1개 골라 s1 (race) + s2 (visibility) 코드 작성 → Draft PR
- [ ] 월요일 모임 — 시도 발표
- [ ] 수요일 23:59까지 — s3 (측정 + 해결) 완료 → Ready for review
- [ ] 목요일 모임 — 발표 + 머지
- [ ] 일요일까지 — (자유) 학습 기록 정리 (블로그/노션/메모 등)

### 결석 / 휴식

- 결석 시 디스코드 미리 공유. PR은 다음 주에 추가 머지 가능
- 한 주 통째로 빠지면 다음 주 모임에서 짧게 회복 발표

---

## 5. 브랜치 / 커밋

### 브랜치 (1주 = 1브랜치)

```
{본인이름}/w{주차번호}
예) chanhyeok/w01, gabin/w03
```

### 커밋 (영어, Conventional Commits)

```
<type>(w<NN>): s<단계> <subject>

예)
feat(w01): s1 reproduce race — 105 coupons issued for 100 stock
feat(w01): s2 add cutoff flag, visibility violation observed
feat(w01): s3 measure — Atomic 0 misses vs sync 600ms
```

상세 컨벤션 → [CLAUDE.md](./CLAUDE.md#commit-convention-english)

---

## 6. 측정 가이드 (s3 단계)

### 측정 표준 양식 (PR 본문에 포함)

PR 본문에 아래 형식으로 측정 결과를 작성합니다. (GitHub에서 그대로 표로 렌더됨)

**환경**: Java 21 / 스레드 50 / 1000회 시도 / 5회 평균
*(선택: OS / CPU 추가 — 예: macOS Apple Silicon)*

**결과**:

| 방식 | 기대값 | 실제값 | 정확도 | 평균 응답시간 |
|---|---|---|---|---|
| 단순 if/decrement | 100 | 87 | 87% | 0.5ms |
| volatile만 | 100 | 89 | 89% | 0.6ms |
| AtomicInteger | 100 | 100 | 100% | 0.4ms |
| synchronized | 100 | 100 | 100% | 12ms |

### 측정 원칙 4개

1. **JIT 워밍업** — 측정 전 5,000회 무측정 실행 (특히 1주차에서 차이 큼)
2. **5회 평균** — 1회만 보면 GC/스케줄링 영향 큼
3. **측정 코드 안에 println / log 절대 X** — 출력 한 줄에 동기화 효과 섞여 측정 망가짐. 결과는 측정 끝난 후만 출력.
4. **숫자 옆에 "왜 이런 결과"** — 단순 표만 있으면 학습 X. "synchronized가 12배 느린 이유는 락 획득 비용" 같은 해석 필수.

#### JIT 워밍업 — 코드로는 이렇게

JVM은 처음에 모든 메서드를 **인터프리터로 느리게** 실행. 같은 메서드를 ~10,000번 호출하면 그제야 **native 코드로 컴파일** (빠름).
워밍업 없이 바로 측정하면 첫 결과는 "인터프리터 + 컴파일 시간"이 섞여서 비정상적으로 느림.

→ 측정 직전에 **같은 코드를 5,000~10,000번 미리 실행** (결과는 안 봄):

```java
// === 워밍업 (결과 안 봄, JVM 데우는 용도) ===
for (int i = 0; i < 5_000; i++) {
    BankAccount warmup = new BankAccount(100);
    warmup.withdraw(50);
    // 결과 무시
}

// === 진짜 측정 ===
long start = System.nanoTime();
// ... 본 측정 코드 ...
long elapsed = System.nanoTime() - start;
```

규칙:
- 측정과 **같은 코드** 돌릴 것 (다른 메서드면 다른 컴파일 됨)
- 결과 안 봄 (그냥 JVM 데우는 용도)
- 5,000~10,000번 (JVM 기본 컴파일 임계값 = 10,000)

전체 워밍업 + 측정 + 비교 예시 → [`topics/01-jvm-thread/example/src/main/java/Stage3Measurement.java`](./topics/01-jvm-thread/example/src/main/java/Stage3Measurement.java)

> 4주차+ Spring Boot 환경에서 더 정확한 측정 원하면 **JMH** (Java Microbenchmark Harness) 도입. 워밍업/통계를 자동으로 처리.

#### 측정 결과 자동 저장 — `MeasurementLog.save()`

매번 측정 결과를 손으로 적기 번거로우니 **자동 누적 기록 helper** 제공.
**본인 폴더에 `MeasurementLog.java`가 이미 들어있음.** 추가 import / 설정 X.

##### 사용법 — 한 줄

```java
// 측정 끝나고 호출
MeasurementLog.save("s1", "쿠폰 race 재현", 5.0, 8.0);
//                  ↑      ↑              ↑    ↑
//                stage   방식 이름      누락  응답시간(ms)
```

##### 인자 4개

| 인자 | 의미 | 예시 |
|---|---|---|
| 1번째 | STAGE 단계 | `"s1"`, `"s3"` |
| 2번째 | 측정한 방식 (한국어 자유) | `"단순 if/decrement"`, `"synchronized"` |
| 3번째 | 평균 누락 건수 (race 발생 수) | `5.0`, `0.0` |
| 4번째 | 평균 응답시간 (ms) | `8.0`, `600.0` |

##### 호출 예시 (Stage3 패턴)

```java
// 워밍업 후 3가지 방식 측정
Result none = measure(SimpleAccount::new);
Result sync = measure(SyncAccount::new);
Result atom = measure(AtomicAccount::new);

// 한 줄씩 자동 저장
MeasurementLog.save("s3", "해결책 없음",   none.misses, none.millis);
MeasurementLog.save("s3", "synchronized", sync.misses, sync.millis);
MeasurementLog.save("s3", "AtomicInteger", atom.misses, atom.millis);
```

##### 어디에 저장되나

본인 폴더에 자동 생성:
```
topics/01-jvm-thread/members/{본인이름}/measurements.md
```

내용:
```
# 측정 기록

자동 누적. 옆에 해석 메모는 직접 추가하세요.

- [05-01 17:00] s3 · 해결책 없음: 누락 2.8 / 12.3ms
- [05-01 17:01] s3 · synchronized: 누락 0.0 / 7.6ms
- [05-01 17:02] s3 · AtomicInteger: 누락 0.0 / 6.9ms
```

→ 옆에 손으로 해석 메모 추가:
```
- [05-01 17:01] s3 · synchronized: 누락 0.0 / 7.6ms
  → 누락 0이지만 락 획득 비용 때문에 Atomic보다 느림
```

##### 동작 원리 (참고)

`MeasurementLog`는 자기 클래스 위치를 자동 감지해서 가장 가까운 `build.gradle` 폴더에 `measurements.md`를 만듭니다. **IntelliJ Working Directory 설정 신경 X.** 어떤 환경에서든 본인 폴더에 정확히 생성됨.

##### 참고 코드

- `topics/01-jvm-thread/example/src/main/java/MeasurementLog.java` (helper 본체)
- `topics/01-jvm-thread/example/src/main/java/Stage3Measurement.java` (사용 예시)

### 측정 도구 (주차별)

| 주차 | 도구 |
|---|---|
| 1주차 | `ExecutorService` + `AtomicInteger` 카운팅 |
| 4주차+ | JMH 마이크로 벤치마크 |
| 10주차+ | `wrk` 또는 `ab` HTTP 부하 도구 |

### 단일 vs 멀티 비교 (거의 모든 주차 필수)

- **단일 스레드 (1)**: 시간 X ms / 결과 100% 정확
- **멀티 스레드 (50)**: 시간 Y ms / 결과 87% 정확

→ 단일에선 안 일어나는 race가 멀티에선 13% 발생.
→ 해결책 적용 후 멀티에서도 100%여야 진짜 해결.

---

## 7. 학습 기록 (자유)

매주 학습한 거를 어떻게 기록할지는 **각자 자유**. 블로그 / 노션 / 본인 PC 메모장 / 본인 폴더에 md 등 — 형식/분량 알아서.

### 12주차 끝나면

운영자가 **모의 면접 시간** 마련. 각자 12주 동안 한 거를 면접 형태로 질문받음.
그때 본인 학습 기록을 참고할 수 있게 평소에 정리해두면 됨.

추천 정리 방식 (강제 X):
- **블로그** (가장 추천) — "내가 깨뜨려본 경험" 톤. 면접 답변 자료로도 사용 가능.
- 노션 / 옵시디언 / 본인 메모장
- `measurements.md` 옆에 메모 추가 (자동 기록 데이터에 직접 해석 메모)

### 한 가지만 기억

> **책에서 외운 답변 X, 본인 측정 데이터 인용 ✅**

`measurements.md`에 자동 누적된 본인 수치 그대로 활용하면 자연스러운 답변이 됨.

---

## 8. AI에게 어떻게 질문할까

`CLAUDE.md` 룰 (코드 직답 X, 3단계 힌트만)은 **AI의 행동 룰**입니다.
이 섹션은 **멤버가 어떻게 질문해야** 빠르게 도움받을지 가이드.

### 핵심 원칙 3개

1. **본인 시도부터 보여줄 것** — 코드 + 결과 + 가설
2. **모호하지 말 것** — "안 돼" → 어떤 에러? 어떤 기대?
3. **개념과 코드를 분리할 것** — 개념 질문은 직답 OK, 코드 직답은 X

### AI가 답하는 방식

| 종류 | 예시 질문 | AI 응답 |
|---|---|---|
| 개념 질문 | "프로세스 vs 스레드?" | 개념 직답 ✅ |
| 본인 코드 리뷰 | "내 코드(첨부) race가 안 보여" | 분석 + 가능 원인 ✅ |
| 코드 직접 짜달라 | "쿠폰 코드 짜줘" | "30분 시도했어?" 거부 ❌ |

### 좋은 질문 예시

```
30분 동안 race 재현 코드를 짰는데 안 보여요. 코드:
[코드 첨부]
스레드 50개로 1000번 돌렸는데 항상 100번 다 성공이에요.
제 가설: 스레드 더 늘려야 하나요? sleep 넣어야 하나요?
```

→ 시도 + 가설 보여줬으니 AI가 정확한 힌트 가능.

### 나쁜 질문 5가지

| ❌ | 왜 |
|---|---|
| "쿠폰 코드 짜줘" | 시도 0개. 거부됨. |
| "이거 왜 안 돼?" + 코드 X | 뭐가 안 되는지 모름 |
| "synchronized 코드 보여줘" | 시나리오 금지 키워드 |
| "race condition 답 알려줘" | "답"이 모호 |
| "이 코드 맞아? 답만 줘" | 본인 학습 X |

### 30분 룰 — 어떻게 채울까

1. **5분**: 시나리오 다시 정독 (놓친 가이드 있나?)
2. **10분**: 검색 (블로그, 공식 문서 — 시나리오 금지 키워드 제외)
3. **10분**: 본인 코드에서 가설 세우고 변경 시도
4. **5분**: 변경 결과 측정/관찰

→ 이 30분 동안 한 것을 AI에 같이 보여주면 좋은 질문이 됨.

---

## 9. 막힐 때

순서대로 단계 진행. 위 단계에서 안 풀리면 다음 단계로.

1. **시도 (30분)** — 위 8번 "30분 룰" 가이드 따라 채우기
2. **검색** — 블로그 / 공식 문서 (단, 시나리오 금지 키워드 제외)
3. **AI에 질문** — 본인 시도 + 가설 첨부 (위 8번 가이드)
4. **디스코드 #질문 채널** — 코드 + 에러 + 본인 가설 그대로
5. **운영자 @멘션** — 모임 1일 전부터
6. **다음 모임에서 공개 질문**
