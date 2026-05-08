package race;

/**
 * 배치 작업 처리 카운터 — Race Condition 문제
 *
 * 시나리오: 야간 배치(정산, 메일 발송 등) 실행 중
 * 50개 워커 스레드가 같은 카운터를 동시에 증가시킬 때
 * processedCount++ 연산의 Read-Modify-Write 과정이 겹쳐
 * 일부 증가 횟수가 누락되는 문제
 */

public class BatchProcessor implements Counter {

    // ⚠️ 문제: 원자성 미보장 — ++ 연산은 하나의 연산이 아님
    private int processedCount = 0;

    public void process() {
        processedCount++; // read → modify → write 중 다른 스레드와 충돌 가능
    }

    public int getProcessedCount() {
        return processedCount;
    }
}
