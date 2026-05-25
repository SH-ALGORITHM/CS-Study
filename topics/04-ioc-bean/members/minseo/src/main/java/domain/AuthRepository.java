package domain;

import org.springframework.stereotype.Repository;
import javax.sql.DataSource;

@Repository
public class AuthRepository {

    private final DataSource dataSource;

    public AuthRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public String dataSourceType() {
        return dataSource.getClass().getSimpleName();
    }
}
