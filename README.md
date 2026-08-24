# CS 스터디

2026-05 ~ 08 · 종료

백엔드 면접에서 답이 막히는 지점을 직접 재현하고 측정해서 넘는다.
주제 성격에 따라 형식을 바꿔 가며 세 챕터를 진행했다.

---

## 챕터

| 부 | 기간 | 인원 | 형식 |
|---|---|---|---|
| **Chapter 1** 도메인 기반 CS | 2026-05-01 ~ 06-15 (6주) | 7명 | 주차별 시나리오 → 각자 다른 도메인으로 재현 · 측정 → PR 리뷰 |
| **Chapter 2** 이력서 · 포트폴리오 면접 | 2026-06-16 ~ 07-19 (5주) | 5명 | 각자 이력서/포트폴리오를 서로 파고드는 상호 질문 (동시성 위주) |
| **Chapter 3** Spring MVC | 2026-07-20 ~ 08-17 (4주) | 5명 | 김영한 강의 완주 + 각자 정리 → 정리본 공유 |

## Chapter 1 — 멤버별 주차 도메인 (제출 코드 기준)

| 이름 | 폴더 | 1주차<br>JVM · 스레드 | 2주차<br>트랜잭션 | 3주차<br>락 | 4주차<br>IoC · Bean | 5주차<br>Proxy · AOP | 6주차<br>Spring Event |
|---|---|---|---|---|---|---|---|
| [오찬혁](https://github.com/ochanhyeok) | `chanhyeok` | 선착순 쿠폰 | 호텔 객실 예약 | P2P 송금 | 결제 PG 연동 | 분산락 AOP | P2P 송금 |
| [김가빈](https://github.com/Gabee-ni) | `gabin` | 이중 환불 | 장바구니 결제 | 장바구니 결제 | 할인 정책 | 캐싱 | 주문 완료 |
| [김민서](https://github.com/minseokim0113) | `minseo` | 출퇴근 기록 | 스터디룸 예약 | 좌석 예매 | 인증 전략 | 감사 로그 | |
| [박수진](https://github.com/cl-o-lc) | `sujin` | 배치 처리 | | 주식 매수 / 매도 | 알림 발송 | 권한 검증 | 결제 완료 |
| [강희민](https://github.com/kkhhmm3103) | `heemin` | 재고 관리 | | | | | 재고 변경 |
| [한재훈](https://github.com/hjh79gw) | `jaehoon` | 조회수 / 좋아요 | 환전 / 통화 거래 | | | | |
| [이가은](https://github.com/gaeunnlee) | `gaeun` | 콘서트 좌석 | | | | | |

> 도메인은 매 주차마다 새로 선택하고, 폴더 이름만 고정한다. 빈칸은 제출 코드가 없는 주차.

### Chapter 1 운영 방식

- **모임**: 매주 월/목 (2시간씩, 월 모임이 끝난 다음 주차의 시작은 직전 주 목 모임 종료 직후)
- **시나리오 공개**: 매주 목 모임 직후 ~ 금요일 안 (운영자)
- **Draft PR 마감**: **월 11:00** — 개념 정리 + "겪기 단계" 결과 (손으로 race / DBeaver 두 세션 등)
- **Ready 전환**: **목 11:00** — 코드 단계 (자동화 + 측정 + 면접 질문 정리)

## Chapter 2 — 이력서 · 포트폴리오 면접

각자 이력서와 포트폴리오를 놓고 서로 질문하는 형식으로 5주간 진행했다.
Chapter 1 에서 다룬 동시성 주제가 각자 프로젝트 어디에 실제로 걸려 있는지를 주로 파고들었다.

## Chapter 3 — Spring MVC

김영한 *스프링 MVC* 강의를 함께 듣고, 각자 정리한 것을 모임에서 공유했다.
정리본은 개인 블로그 / 노션에 쓰고 여기에 링크만 모은다.

| 이름 | 정리 링크 |
|---|---|
| [오찬혁](https://github.com/ochanhyeok) | [MVC 정리](https://app.notion.com/p/MVC-3a4408702c75802da414c5e7af521b01?source=copy_link) |
| [김가빈](https://github.com/Gabee-ni) | [MVC 정리](https://ga-been.tistory.com/category/Spring) |
| [김민서](https://github.com/minseokim0113) | [MVC 정리](https://app.notion.com/p/MVC-3a4a9f67e19a80689892fcc23a3edef1) |
| [박수진](https://github.com/cl-o-lc) | [MVC 정리](https://velog.io/@cl-o-lc/series/Spring-MVC-1) |
| [강희민](https://github.com/kkhhmm3103) | |

## 폴더 구조

```
cs-study/
├── README.md                       이 파일 (5분 안내)
├── CONTRIBUTING.md                 멤버 가이드 (셋업·흐름·측정·AI 질문법 다 포함)
├── OPERATIONS.md                   운영자 가이드 (페이즈 시작·머지·트러블 응대)
├── CLAUDE.md / GEMINI.md           AI 도구 자동 로드 룰
├── docker-compose.yml              postgres + redis
├── topics/
│   ├── 01-jvm-thread/
│   │   ├── scenario.md             주차 시작 전 읽기
│   │   ├── example/                참고 코드 (은행 잔고 도메인)
│   │   └── members/{본인이름}/     ← 본인 코드
│   └── ... (12주차분 시나리오, 멤버 제출은 06 까지)
├── template/                       4주차+ Spring Boot 스켈레톤 (운영자가 자동 적용)
└── scripts/scaffold-week.sh        운영자 자동화
```

## 가이드

셋업 · 진행 흐름 · 측정 방법 → **[CONTRIBUTING.md](./CONTRIBUTING.md)**

## AI 사용 규칙

이 스터디는 **바이브 코딩 금지** — AI는 힌트와 리뷰로만 돕고, 코드는 직접 작성.
상세 → [CLAUDE.md](./CLAUDE.md) (Claude Code · Gemini CLI 자동 로드)

## 라이선스

학습용 저장소. 멤버 외부 공유 시 운영자에게 문의.
