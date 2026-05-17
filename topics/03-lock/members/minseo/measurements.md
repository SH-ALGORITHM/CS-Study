## STAGE 1 — DB 락 + 분산락 + 데드락 (직접 관찰)

- [05-18 04:14] s1 · FOR UPDATE 손 측정 (관찰)
  - A가 `SELECT FOR UPDATE` 후 대기 중일 때:
    - B의 일반 `SELECT`: 막히지 않고 읽음 (단, 커밋 전 데이터)
    - B의 일반 `UPDATE`: A가 커밋할 때까지 대기 (Lock Wait)
    - B의 `FOR UPDATE`: A가 커밋할 때까지 대기 (Lock Wait)
    - A가 `COMMIT` 하면: B가 대기 상태에서 풀려나 최신 데이터를 기준으로 작업 수행.

- [05-18 04:32] s1 · 데드락 시퀀스 재현 (관찰)
  - A가 좌석 1 점유, B가 좌석 2 점유.
  - 이후 A가 좌석 2를, B가 좌석 1을 교차로 요구.
  - 결과: PG의 `deadlock_timeout` 기본값(1초) 경과 후 `ERROR: deadlock detected` 발생. 한 세션이 강제 종료(Abort)됨을 확인.

- [05-18 04:46] s1 · Redis 분산락 손 측정 (관찰)
  - `SET lock:seat:1 "minseo" NX EX 10` 실행 시 `OK` 반환 (락 획득).
  - 10초 이내에 동일 키로 락 획득 시도 시 `(nil)` 반환 (락 획득 실패 - 상호 배제 확인).
  - 10초(TTL) 경과 후 자동 해제되어 다시 락 획득 시도 시 `OK` 반환 확인.
