# 2주차 멤버 셋업 템플릿

`scripts/scaffold-week.sh 02` 가 이 폴더의 내용을 7 명 멤버 폴더로 복사한다.

## 들어가는 것

| 파일 | 무엇 |
|---|---|
| `build.gradle` | Java 21 + HikariCP + PostgreSQL JDBC. 1 주차의 단순 Java 와 다름 |
| `src/main/java/` | 빈 폴더 — 멤버가 본인 도메인 코드 작성 (BankAccount, Stage*RaceJdbc 등) |
| `src/main/resources/` | 빈 폴더 — 멤버가 본인 schema.sql 작성 |

## 멤버가 첫 작업 시 — example 참고

`topics/02-transaction/example/` 에 계좌이체 도메인의 완성된 참고 코드가 있다.
- 본인 도메인이 같으면 (계좌 이체) 거의 그대로
- 다르면 (콘서트 좌석 / 회의실 예약 등) 컬럼/테이블/메서드 이름만 본인 도메인으로 변환
- 절대 통째 복사 금지 — 손으로 타이핑 = 학습 자산 (`CLAUDE.md`)
