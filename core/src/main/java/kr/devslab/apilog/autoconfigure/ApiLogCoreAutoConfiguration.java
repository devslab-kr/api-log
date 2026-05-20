package kr.devslab.apilog.autoconfigure;

import kr.devslab.apilog.config.RetryConfig;
import kr.devslab.apilog.listener.ApiEventListener;
import kr.devslab.apilog.spi.ApiLogWriter;
import kr.devslab.apilog.spi.PayloadJsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Backend-agnostic core auto-configuration. Loads whenever the starter is on
 * the classpath; registers everything that doesn't depend on a persistence
 * backend.
 *
 * <p>Wires the async event listener, the {@link PayloadJsonMapper} helper, the
 * Blackbird-enabled Jackson customizer, the retry config, and a virtual-thread
 * / platform-thread executor for the listener.
 *
 * <p>The actual {@link ApiLogWriter} bean comes from whichever backend module
 * the consumer added — {@code api-log-jpa}, {@code api-log-r2dbc}, or
 * {@code api-log-mybatis}. {@link ApiEventListener} is gated on
 * {@code @ConditionalOnBean(ApiLogWriter.class)} so missing-backend setups
 * fail loudly at config time rather than at first event.
 */
@AutoConfiguration
@ConditionalOnClass({ApiEventListener.class, ApiLogWriter.class})
@EnableConfigurationProperties(ApiLogProperties.class)
@ConditionalOnProperty(name = "api.log.enabled", havingValue = "true", matchIfMissing = true)
@Import(RetryConfig.class)
@EnableAsync
public class ApiLogCoreAutoConfiguration {

    /**
     * Shared JSON helper used by every backend writer. Lifted out of the old
     * {@code ApiLogService} so backend modules don't each re-implement it.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ObjectMapper.class)
    public PayloadJsonMapper apiLogPayloadJsonMapper(ObjectMapper objectMapper) {
        return new PayloadJsonMapper(objectMapper);
    }

    /**
     * Event-bus listener that routes events to the consumer's chosen
     * {@link ApiLogWriter}. Only registered when a writer bean is present —
     * makes "consumer added api-log-core but forgot to add a backend" a clear
     * "no qualifying bean of type ApiLogWriter" failure instead of silently
     * dropping events.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ApiLogWriter.class)
    public ApiEventListener apiEventListener(ApiLogWriter writer) {
        return new ApiEventListener(writer);
    }

    /**
     * Adds the Blackbird module to Spring Boot's auto-configured
     * {@code ObjectMapper} — ~30-50% Jackson serialization speedup, which
     * matters because every API call writes JSON payloads to {@code api_log}.
     *
     * <p>Using {@link Jackson2ObjectMapperBuilderCustomizer} (rather than
     * defining our own {@code @Primary ObjectMapper} bean) keeps Spring Boot's
     * default ObjectMapper as the canonical one — modules added by other
     * customizers (consumer's own, other libraries') compose cleanly.
     */
    @Bean
    @ConditionalOnMissingBean(name = "apiLogJacksonCustomizer")
    public Jackson2ObjectMapperBuilderCustomizer apiLogJacksonCustomizer() {
        return builder -> builder.modulesToInstall(new BlackbirdModule());
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
     * Fallback platform-thread executor — registered when virtual threads
     * are disabled. No {@code @ConditionalOnMissingBean(TaskExecutor.class)} here
     * because Spring Boot always registers its own {@code applicationTaskExecutor}
     * and we want our named executor to coexist; consumers explicitly choose by
     * name when they want it.
     */
    @Bean
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
