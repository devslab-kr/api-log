package kr.devslab.apilog.jpa.autoconfigure;

import kr.devslab.apilog.spi.ApiLogWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots a full Spring context with the JPA backend installed and verifies the
 * v0.6.0 module split wires everything up correctly:
 * <ul>
 *   <li>The three auto-configurations from :core load (core / rest / reactive).</li>
 *   <li>The JPA auto-config from :jpa loads, registering a {@link ApiLogWriter}.</li>
 *   <li>Blackbird-enabled ObjectMapper + RestClient + virtual-thread executor
 *       are all in the context.</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
class ConfigurationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("configtest")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.threads.virtual.enabled", () -> "true");
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void jacksonCustomizer_installsBlackbird() {
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper.class);
        assertThat(objectMapper).isNotNull();
        assertThat(objectMapper.getRegisteredModuleIds())
                .contains(BlackbirdModule.class.getName());
    }

    @Test
    void mappingJackson2HttpMessageConverter_isRegistered_withBlackbird() {
        MappingJackson2HttpMessageConverter converter =
                applicationContext.getBean(MappingJackson2HttpMessageConverter.class);
        assertThat(converter).isNotNull();
        assertThat(converter.getObjectMapper().getRegisteredModuleIds())
                .contains(BlackbirdModule.class.getName());
    }

    @Test
    void restClient_isAutoConfigured() {
        RestClient restClient = applicationContext.getBean(RestClient.class);
        assertThat(restClient).isNotNull();
    }

    @Test
    void virtualThreadExecutor_isWhatWeUseWhenSpringVirtualThreadsAreOn() {
        TaskExecutor taskExecutor = applicationContext.getBean("apiLogVirtualThreadExecutor", TaskExecutor.class);
        assertThat(taskExecutor).isInstanceOf(VirtualThreadTaskExecutor.class);
    }

    @Test
    void platformThreadExecutor_isAbsentWhenVirtualThreadsAreOn() {
        assertThat(applicationContext.containsBean("apiLogPlatformThreadExecutor")).isFalse();
    }

    @Test
    void retryConfig_isImported() {
        assertThat(applicationContext.containsBean("retryConfig")).isTrue();
    }

    @Test
    void apiLogWriter_isProvidedByJpaBackend() {
        ApiLogWriter writer = applicationContext.getBean(ApiLogWriter.class);
        assertThat(writer.getClass().getSimpleName()).isEqualTo("JpaApiLogWriter");
    }

    @Test
    void allAutoConfigurationsAreLoaded() {
        // From :core
        assertThat(applicationContext.containsBean(
                "kr.devslab.apilog.autoconfigure.ApiLogCoreAutoConfiguration")).isTrue();
        assertThat(applicationContext.containsBean(
                "kr.devslab.apilog.autoconfigure.RestApiClientAutoConfiguration")).isTrue();
        assertThat(applicationContext.containsBean(
                "kr.devslab.apilog.autoconfigure.ReactiveApiClientAutoConfiguration")).isTrue();
        // From :jpa
        assertThat(applicationContext.containsBean(
                "kr.devslab.apilog.jpa.autoconfigure.ApiLogJpaAutoConfiguration")).isTrue();
        // RetryConfig is @Imported by ApiLogCoreAutoConfiguration.
        assertThat(applicationContext.containsBean("retryConfig")).isTrue();
    }
}
