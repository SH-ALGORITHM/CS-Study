package com.example.study.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceConfig {

    @Bean(destroyMethod = "close")
    public DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:postgresql://localhost:5433/csstudy");
        dataSource.setUsername("csstudy");
        dataSource.setPassword("csstudy1234");
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setMaximumPoolSize(4);
        return dataSource;
    }
}
