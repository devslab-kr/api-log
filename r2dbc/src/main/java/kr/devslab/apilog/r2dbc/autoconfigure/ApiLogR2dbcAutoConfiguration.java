package kr.devslab.apilog.r2dbc.autoconfigure;

import kr.devslab.apilog.autoconfigure.ApiLogCoreAutoConfiguration;
import kr.devslab.apilog.r2dbc.writer.R2dbcApiLogWriter;
import kr.devslab.apilog.spi.ApiLogWriter;
import kr.devslab.apilog.spi.PayloadJsonMapper;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.r2dbc.core.DatabaseClient;

/**
 * R2DBC backend auto-configuration. Loads when the reactive R2DBC stack
 * ({@link ConnectionFactory}) is on the classpath.
 *
 * <p>Registers:
 * <ul>
 *   <li>{@link DatabaseClient} (only if the consumer didn't already provide one)
 *       built off the consumer's {@code ConnectionFactory} bean.</li>
 *   <li>{@link R2dbcApiLogWriter} — the reactive {@link ApiLogWriter} the core
 *       listener routes through.</li>
 *   <li>{@link ApiLogR2dbcSchemaInitializer} — pure-reactive
 *       {@code CREATE TABLE IF NOT EXISTS} initializer when
 *       {@code api.log.schema.management=builtin} (default).</li>
 * </ul>
 *
 * <p>Schema management strategies under R2DBC:
 * <ul>
 *   <li><b>BUILTIN</b> (default) — registers the reactive initializer above.</li>
 *   <li><b>NONE</b> — does nothing; apply the DDL yourself.</li>
 *   <li><b>FLYWAY</b> — <em>not supported in R2DBC mode</em>. Flyway needs a
 *       JDBC {@code DataSource}; consumers who want Flyway should run it from
 *       a separate JDBC connection at boot (Spring Boot's standard Flyway
 *       autoconfig works fine alongside R2DBC for this) or switch to the JPA
 *       backend.</li>
 * </ul>
 */
@AutoConfiguration(after = {ApiLogCoreAutoConfiguration.class, R2dbcAutoConfiguration.class})
@ConditionalOnClass(ConnectionFactory.class)
@ConditionalOnProperty(name = "api.log.enabled", havingValue = "true", matchIfMissing = true)
public class ApiLogR2dbcAutoConfiguration {

    /**
     * Lazily-built {@link DatabaseClient}. Skipped if the consumer (or
     * Spring Boot's reactive autoconfig) already registered one — most
     * R2DBC apps will already have it via {@code spring-boot-starter-data-r2dbc}.
     * {@link ConnectionFactory} is supplied via constructor injection; we sit
     * {@code after = R2dbcAutoConfiguration.class} so Spring Boot's auto-built
     * one is in place by the time this method is invoked.
     */
    @Bean
    @ConditionalOnMissingBean
    public DatabaseClient apiLogR2dbcDatabaseClient(ConnectionFactory connectionFactory) {
        return DatabaseClient.create(connectionFactory);
    }

    /**
     * Wire the R2DBC writer as the {@link ApiLogWriter} implementation. No
     * {@code @ConditionalOnBean} guards on {@link DatabaseClient} /
     * {@link PayloadJsonMapper} — those would race against the same class's
     * own {@code @Bean} declarations (a Spring Boot pitfall). Spring DI
     * resolves the parameters lazily, which avoids the ordering problem.
     */
    @Bean
    @ConditionalOnMissingBean(ApiLogWriter.class)
    public R2dbcApiLogWriter r2dbcApiLogWriter(DatabaseClient databaseClient, PayloadJsonMapper jsonMapper) {
        return new R2dbcApiLogWriter(databaseClient, jsonMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "api.log.schema",
            name = "management",
            havingValue = "builtin",
            matchIfMissing = true
    )
    public ApiLogR2dbcSchemaInitializer apiLogR2dbcSchemaInitializer(ConnectionFactory connectionFactory) {
        return new ApiLogR2dbcSchemaInitializer(connectionFactory);
    }
}
