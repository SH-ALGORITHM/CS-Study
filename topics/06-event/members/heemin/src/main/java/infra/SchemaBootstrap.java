package infra;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaBootstrap {

    private final JdbcTemplate jdbcTemplate;

    public SchemaBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS inventory (
                product_id BIGINT PRIMARY KEY,
                quantity INT NOT NULL
            )
            """);

        jdbcTemplate.execute("""
            INSERT INTO inventory(product_id, quantity)
            VALUES (1, 100)
            ON CONFLICT (product_id)
            DO NOTHING
            """);

        System.out.println("[DB] inventory table ready");
    }
}
