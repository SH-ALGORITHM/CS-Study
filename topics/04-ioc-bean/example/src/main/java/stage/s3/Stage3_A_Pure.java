package stage.s3;

import domain.EmailSender;
import domain.NotificationSender;
import domain.NotificationService;
import domain.PushSender;
import domain.SlackSender;
import domain.SmsSender;
import infra.MeasurementLog;

/**
 * STAGE 3-1 (A): 순수 main() 부팅 시간 — Spring 안 씀.
 *
 * <h3>JVM 웜업 주의</h3>
 * 같은 JVM 에서 A → B → C 순차 실행하면 JIT 웜업으로 뒤쪽이 빨라 보임.
 * **이 파일은 단독 JVM 실행** — Stage3_B_Spring / Stage3_C_Boot 도 각각 별도 JVM 으로.
 * 각 5 회 실행 후 평균.
 *
 * <h3>실행</h3>
 * <pre>
 * ./gradlew run -PmainClass=stage.Stage3_A_Pure
 * </pre>
 */
public class Stage3_A_Pure {

    public static void main(String[] args) {
        long t1 = System.nanoTime();

        NotificationSender email = new EmailSender();
        NotificationSender sms = new SmsSender();
        NotificationSender push = new PushSender();
        NotificationSender slack = new SlackSender();
        NotificationService service = new NotificationService(email);

        long elapsed = (System.nanoTime() - t1) / 1_000_000;

        System.out.println("\n=== 순수 main() 부팅 ===");
        System.out.println("부팅 시간: " + elapsed + "ms");
        System.out.println("생성한 객체: 4 sender + 1 service = 5 개");

        service.notify("user@example.com", "순수 main() 으로 만든 서비스");

        MeasurementLog.save("s3-1", "순수 main()", "부팅 시간 " + elapsed + "ms / 객체 5 개");
    }
}
