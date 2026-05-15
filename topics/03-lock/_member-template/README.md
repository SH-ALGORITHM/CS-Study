# 3주차 멤버 셋업 템플릿

`scripts/scaffold-week.sh 03` 가 이 폴더의 내용을 7 명 멤버 폴더로 복사한다.

## 들어가는 것

| 파일 | 무엇 |
|---|---|
| `build.gradle` | Java 21 + HikariCP + PostgreSQL JDBC + **Lettuce (Redis 클라이언트)**. 2 주차에서 Redis 추가됨 |
| `src/main/java/` | 빈 폴더 — 멤버가 본인 도메인 코드 작성 (도메인 클래스, Stage2*.java 등) |
| `src/main/resources/` | 빈 폴더 — 멤버가 본인 `schema.sql` 작성 (version 컬럼 포함) |

## 2 주차와 다른 점

- **Redis 추가** — 분산락 학습용. Lettuce 클라이언트로 직접 `SET NX EX` / Lua script 다룸
- **version 컬럼** — 낙관적 락용. `schema.sql` 에 `version BIGINT NOT NULL DEFAULT 0` 포함
- **awaitTermination 길게** — 2 주차 timeout 컷 교훈으로 기본 300 초 권장

## 멤버가 첫 작업 시 — example 참고

`topics/03-lock/example/` 에 계좌이체 도메인의 완성된 참고 코드가 있다.
- 본인 도메인이 같으면 (계좌 이체) 거의 그대로
- 다르면 (주식 매수/매도 / 재고 / 경매 등) 컬럼/테이블/메서드 이름만 본인 도메인으로 변환
- 절대 통째 복사 금지 — 손으로 타이핑 = 학습 자산 (`CLAUDE.md`)

## 본인 도메인 코드 시작 순서

1. **`schema.sql`** 작성 — 본인 도메인 테이블 + `version` 컬럼 + 초기 데이터
2. **도메인 클래스** 작성 — RMW 패턴 (SELECT → 앱 계산 → UPDATE)
3. **STAGE 1** — DBeaver 두 세션으로 FOR UPDATE / 데드락 / Redis SETNX 손 측정
4. **STAGE 2-1/2-2/2-3** — 비관 / 낙관 / 분산락 각각 구현
5. **STAGE 3** — 충돌 빈도별 정량 비교
6. **STAGE 4** — 데드락 재현 + 4 조건 매핑
