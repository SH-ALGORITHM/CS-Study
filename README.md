# CS 스터디

12주 · 7명 · 백엔드 면접 정복

가이드 페이지: https://cs-study-page.pages.dev

---

## 멤버

| 이름 | 폴더 | 1주차 예시 도메인 |
|---|---|---|
| 오찬혁 | `chanhyeok` | 선착순 쿠폰 |
| 김가빈 | `gabin` | 이중 환불 |
| 김민서 | `minseo` | 출퇴근 기록 |
| 강희민 | `huimin` | 재고 관리 |
| 한재훈 | `jaehoon` | 조회수 / 좋아요 |
| 이가은 | `gaeun` | 콘서트 좌석 |
| 박수진 | `sujin` | volatile 집중 |

> **도메인은 매 주차마다 새로 선택.** 위 표는 1주차 예시일 뿐, 2주차부터는 각자 해당 주차 `scenario.md`의 도메인 목록에서 자유롭게 고른다 (겹쳐도 OK). 폴더 이름(`chanhyeok` 등)만 12주 동안 고정.

## 일정

- **모임**: 매주 월/목 (2시간씩, 월 모임이 끝난 다음 주차의 시작은 직전 주 목 모임 종료 직후)
- **시나리오 공개**: 매주 목 모임 직후 ~ 금요일 안 (운영자)
- **Draft PR 마감**: **월 11:00** — 개념 정리 + "겪기 단계" 결과 (손으로 race / DBeaver 두 세션 등)
- **Ready 전환**: **목 11:00** — 코드 단계 (자동화 + 측정 + 면접 질문 정리)
- **학습 기록 (자유)**: 블로그 / 노션 / 메모 등 — 12주 끝나면 모의 면접 시간

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
│   │   ├── scenario.md             ← 매주 일요일 읽기
│   │   ├── example/                참고 코드 (은행 잔고 도메인)
│   │   └── members/{본인이름}/     ← 본인 코드
│   └── ... (12주차)
├── template/                       4주차+ Spring Boot 스켈레톤 (운영자가 자동 적용)
└── scripts/scaffold-week.sh        운영자 자동화
```

## 빠른 시작

```bash
git clone https://github.com/SH-ALGORITHM/CS-Study.git
cd CS-Study
```

이후 모든 가이드 → **[CONTRIBUTING.md](./CONTRIBUTING.md)**

## AI 사용 규칙

이 스터디는 **바이브 코딩 금지** — AI는 힌트와 리뷰로만 돕고, 코드는 직접 작성.
상세 → [CLAUDE.md](./CLAUDE.md) (Claude Code · Gemini CLI 자동 로드)

## 라이선스

학습용 저장소. 멤버 외부 공유 시 운영자에게 문의.
