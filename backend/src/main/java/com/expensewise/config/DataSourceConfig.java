package com.expensewise.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * Built explicitly instead of relying on spring.datasource.* autoconfiguration
 * — see GroqProperties for the general problem. spring.datasource.url is
 * Spring Boot's own documented override property, so it's independently
 * resolvable from a plain SPRING_DATASOURCE_URL/USERNAME/PASSWORD env var
 * regardless of what application.yml says, which is an even likelier
 * collision than GROQ_API_KEY since it's Spring's own canonical name for
 * any project that hasn't customized it. Binding directly from the literal
 * *_EXPENSEWISE names here removes that risk entirely. See DECISIONS.md.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Profile("local")
    public DataSource localDataSource(
            @Value("${DB_URL_EXPENSEWISE:jdbc:postgresql://localhost:5433/expensewise}") String url,
            @Value("${DB_USER_EXPENSEWISE:dev}") String username,
            @Value("${DB_PASSWORD_EXPENSEWISE:devpass}") String password) {
        return build(url, username, password, 10, null);
    }

    @Bean
    @Profile("!local")
    public DataSource prodDataSource(
            @Value("${DB_URL_EXPENSEWISE}") String url,
            @Value("${DB_USER_EXPENSEWISE}") String username,
            @Value("${DB_PASSWORD_EXPENSEWISE}") String password) {
        return build(url, username, password, 8, 2);
    }

    private DataSource build(String url, String username, String password, int maxPoolSize, Integer minIdle) {
        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(url)
                .username(username)
                .password(password)
                .build();
        dataSource.setMaximumPoolSize(maxPoolSize);
        if (minIdle != null) {
            dataSource.setMinimumIdle(minIdle);
        }
        return dataSource;
    }
}
