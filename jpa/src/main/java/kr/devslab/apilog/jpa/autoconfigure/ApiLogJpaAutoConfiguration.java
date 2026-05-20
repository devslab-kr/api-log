package kr.devslab.apilog.jpa.autoconfigure;

import kr.devslab.apilog.autoconfigure.ApiLogCoreAutoConfiguration;
import kr.devslab.apilog.jpa.model.ApiLogEntity;
import kr.devslab.apilog.jpa.repository.ApiLogRepository;
import kr.devslab.apilog.jpa.writer.JpaApiLogWriter;
import kr.devslab.apilog.spi.ApiLogWriter;
import kr.devslab.apilog.spi.PayloadJsonMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.sql.DataSource;

/**
 * JPA backend auto-configuration. Loads when JPA is on the classpath
 * ({@link jakarta.persistence.Entity}) and registers:
 *
 * <ul>
 *   <li>{@link JpaApiLogWriter} — the {@link ApiLogWriter} implementation that
 *       the core listener routes events through.</li>
 *   <li>{@link ApiLogJpaSchemaInitializer} — runs {@code V1.0__create_api_log.sql}
 *       when {@code api.log.schema.management=builtin} (default).</li>
 * </ul>
 *
 * <p>{@code @EntityScan} + {@code @EnableJpaRepositories} are pointed explicitly
 * at this module's packages so the consumer doesn't need to add them to their
 * own {@code @SpringBootApplication} setup.
 *
 * <p>{@code ApiLogFlywayConfig} is {@code @Imported} so it gets picked up too
 * — its own {@code @ConditionalOnClass} + {@code @ConditionalOnProperty}
 * gates keep it dormant unless Flyway is on the classpath AND the consumer
 * opted in via {@code api.log.schema.management=flyway}.
 */
@AutoConfiguration(after = ApiLogCoreAutoConfiguration.class)
@ConditionalOnClass(jakarta.persistence.Entity.class)
@ConditionalOnProperty(name = "api.log.enabled", havingValue = "true", matchIfMissing = true)
@EntityScan(basePackageClasses = ApiLogEntity.class)
@EnableJpaRepositories(basePackageClasses = ApiLogRepository.class)
@Import(ApiLogFlywayConfig.class)
public class ApiLogJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ApiLogWriter.class)
    @ConditionalOnBean({ApiLogRepository.class, PayloadJsonMapper.class})
    public JpaApiLogWriter jpaApiLogWriter(ApiLogRepository repository, PayloadJsonMapper jsonMapper) {
        return new JpaApiLogWriter(repository, jsonMapper);
    }

    /**
     * Creates the {@code api_log} table at startup when the consumer hasn't
     * picked a different management strategy (the default — BUILTIN). The
     * CREATE TABLE statements use IF NOT EXISTS, so this is idempotent and
     * safe to re-run on every boot.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "api.log.schema",
            name = "management",
            havingValue = "builtin",
            matchIfMissing = true
    )
    public ApiLogJpaSchemaInitializer apiLogJpaSchemaInitializer(DataSource dataSource) {
        return new ApiLogJpaSchemaInitializer(dataSource);
    }
}
