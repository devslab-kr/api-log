package kr.devslab.apilog.autoconfigure;

import kr.devslab.apilog.config.RetryConfig;
import kr.devslab.apilog.listener.ApiEventListener;
import kr.devslab.apilog.repository.ApiLogRepository;
import kr.devslab.apilog.service.ApiLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;

/**
 * Core auto-configuration — always loads when the starter is on the classpath.
 *
 * <p>Wires the event pipeline (service, listener), schema initializer, async
 * executor, and a BlackbirdModule-equipped {@link ObjectMapper}. Does not
 * register either HTTP client — those live in
 * {@link RestApiClientAutoConfiguration} (Web/Servlet) and
 * {@link ReactiveApiClientAutoConfiguration} (WebFlux), each gated by
 * {@code @ConditionalOnClass} so consumers only pay for what's on their
 * classpath.
 */
@AutoConfiguration
@ConditionalOnClass({ApiEventListener.class, ApiLogService.class})
@EnableConfigurationProperties(ApiLogProperties.class)
@ConditionalOnProperty(name = "api.log.enabled", havingValue = "true", matchIfMissing = true)
@EntityScan(basePackages = "kr.devslab.apilog.model")
@EnableJpaRepositories(basePackages = "kr.devslab.apilog.repository")
@Import({RetryConfig.class, ApiLogFlywayConfig.class})
@EnableAsync
public class ApiLogAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ObjectMapper.class)
    public ApiLogService apiLogService(ApiLogRepository repository, ObjectMapper objectMapper) {
        return new ApiLogService(repository, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ApiLogService.class)
    public ApiEventListener apiEventListener(ApiLogService apiLogService) {
        return new ApiEventListener(apiLogService);
    }

    /**
     * Creates the api_log table at startup when the consumer hasn't picked a
     * different management strategy (the default — BUILTIN). The CREATE TABLE
     * statements use IF NOT EXISTS, so this is idempotent and safe to re-run
     * on every boot.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "api.log.schema",
            name = "management",
            havingValue = "builtin",
            matchIfMissing = true
    )
    public ApiLogSchemaInitializer apiLogSchemaInitializer(DataSource dataSource) {
        return new ApiLogSchemaInitializer(dataSource);
    }

    /**
     * High-throughput {@link ObjectMapper} with the Blackbird module registered.
     * Marked {@link Primary} so JPA's JSONB conversion picks it up over
     * Spring Boot's default — Blackbird gives ~30-50% serialization speedup
     * which matters for log writes on every API call.
     *
     * <p>Define your own primary {@code ObjectMapper} bean to override.
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "apiLogObjectMapper")
    public ObjectMapper apiLogObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new BlackbirdModule());
        return mapper;
    }

    /**
     * Virtual-thread executor for the async event listener — preferred on
     * Java 21+. Activated when {@code spring.threads.virtual.enabled=true}.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.threads.virtual.enabled", havingValue = "true")
    public TaskExecutor apiLogVirtualThreadExecutor() {
        return new VirtualThreadTaskExecutor("ApiLogEvent-");
    }

    /**
     * Fallback platform-thread executor for the async event listener.
     * Active when {@code spring.threads.virtual.enabled} is missing or false.
     */
    @Bean
    @ConditionalOnMissingBean(TaskExecutor.class)
    @ConditionalOnProperty(name = "spring.threads.virtual.enabled", havingValue = "false", matchIfMissing = true)
    public ThreadPoolTaskExecutor apiLogPlatformThreadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ApiLogEvent-");
        executor.initialize();
        return executor;
    }
}
