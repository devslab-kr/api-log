package kr.devslab.apilog.mybatis.autoconfigure;

import kr.devslab.apilog.autoconfigure.ApiLogCoreAutoConfiguration;
import kr.devslab.apilog.mybatis.mapper.ApiLogMapper;
import kr.devslab.apilog.mybatis.writer.MybatisApiLogWriter;
import kr.devslab.apilog.spi.ApiLogWriter;
import kr.devslab.apilog.spi.PayloadJsonMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * MyBatis backend auto-configuration. Loads when MyBatis is on the classpath
 * ({@code org.apache.ibatis.session.SqlSessionFactory}).
 *
 * <p>Registers:
 * <ul>
 *   <li>{@link MybatisApiLogWriter} — the {@link ApiLogWriter} implementation
 *       the core listener routes events through.</li>
 *   <li>{@link ApiLogMybatisSchemaInitializer} — runs
 *       {@code V1.0__create_api_log.sql} when
 *       {@code api.log.schema.management=builtin} (default).</li>
 * </ul>
 *
 * <p>{@link MapperScan} is pointed at {@link ApiLogMapper}'s package so the
 * consumer doesn't need to add their own {@code @MapperScan} or
 * {@code @Mapper} bean override. If the consumer already drives MyBatis with
 * their own scan, our mapper still gets picked up because MapperScan annotations
 * compose additively.
 *
 * <p>Schema management strategies: {@code BUILTIN} (default) registers the
 * initializer above; {@code NONE} does nothing; {@code FLYWAY} is honored if
 * the JPA module is also on the classpath (its FlywayConfigurationCustomizer
 * activates via the same property). Pure-MyBatis setups can install Flyway
 * directly — Spring Boot's stock Flyway autoconfig will pick up
 * {@code classpath:db/api-log} when added to their {@code spring.flyway.locations}.
 */
@AutoConfiguration(after = {ApiLogCoreAutoConfiguration.class, MybatisAutoConfiguration.class})
@ConditionalOnClass(org.apache.ibatis.session.SqlSessionFactory.class)
@ConditionalOnProperty(name = "api.log.enabled", havingValue = "true", matchIfMissing = true)
@MapperScan(basePackageClasses = ApiLogMapper.class)
public class ApiLogMybatisAutoConfiguration {

    /**
     * Wire the MyBatis writer as the {@link ApiLogWriter} implementation.
     * Spring DI resolves {@link ApiLogMapper} (registered via the
     * {@code @MapperScan} above) and {@link PayloadJsonMapper} (from
     * {@code ApiLogCoreAutoConfiguration}) lazily — no
     * {@code @ConditionalOnBean} guards, since those evaluate before the
     * sibling beans are registered and were what bit the first CI run.
     */
    @Bean
    @ConditionalOnMissingBean(ApiLogWriter.class)
    public MybatisApiLogWriter mybatisApiLogWriter(ApiLogMapper mapper, PayloadJsonMapper jsonMapper) {
        return new MybatisApiLogWriter(mapper, jsonMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "api.log.schema",
            name = "management",
            havingValue = "builtin",
            matchIfMissing = true
    )
    public ApiLogMybatisSchemaInitializer apiLogMybatisSchemaInitializer(DataSource dataSource) {
        return new ApiLogMybatisSchemaInitializer(dataSource);
    }
}
