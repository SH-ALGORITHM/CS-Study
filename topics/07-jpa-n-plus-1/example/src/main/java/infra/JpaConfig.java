package infra;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 검증 결과 — scanBasePackages 만으로는 Spring Data JPA Repository 자동 등록 안 됨
 * ("Found 0 JPA repository interfaces"). @EnableJpaRepositories 명시 필요.
 * @EntityScan 도 같이 명시해서 동작 보장 (Boot 자동 @EntityScan 의 기본 패키지가 main 클래스 위치라
 * scanBasePackages 와 별개로 동작).
 */
@Configuration
@EntityScan("domain")
@EnableJpaRepositories("domain")
public class JpaConfig {
}
