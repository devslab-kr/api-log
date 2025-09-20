package com.devs.lab.test.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

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
    void jacksonConfig_shouldCreateObjectMapperWithBlackbird() {
        // When
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper.class);

        // Then
        assertThat(objectMapper).isNotNull();
        assertThat(objectMapper.getRegisteredModuleIds())
                .contains(BlackbirdModule.class.getName());
    }

    @Test
    void jacksonConfig_shouldCreateMappingJackson2HttpMessageConverter() {
        // When
        MappingJackson2HttpMessageConverter converter =
                applicationContext.getBean(MappingJackson2HttpMessageConverter.class);

        // Then
        assertThat(converter).isNotNull();
        assertThat(converter.getObjectMapper()).isNotNull();
        assertThat(converter.getObjectMapper().getRegisteredModuleIds())
                .contains(BlackbirdModule.class.getName());
    }

    @Test
    void restClientConfig_shouldCreateRestClient() {
        // When
        RestClient restClient = applicationContext.getBean(RestClient.class);

        // Then
        assertThat(restClient).isNotNull();
    }

    @Test
    void restClientConfig_shouldCreateRestTemplate() {
        // When
        RestTemplate restTemplate = applicationContext.getBean(RestTemplate.class);

        // Then
        assertThat(restTemplate).isNotNull();
        assertThat(restTemplate.getMessageConverters()).isNotEmpty();

        // Check if it has our custom MappingJackson2HttpMessageConverter
        boolean hasCustomConverter = restTemplate.getMessageConverters().stream()
                .anyMatch(converter -> converter instanceof MappingJackson2HttpMessageConverter);
        assertThat(hasCustomConverter).isTrue();
    }

    @Test
    void asyncConfig_shouldCreateVirtualThreadTaskExecutor() {
        // When
        TaskExecutor taskExecutor = applicationContext.getBean("virtualThreadTaskExecutor", TaskExecutor.class);

        // Then
        assertThat(taskExecutor).isNotNull();
        assertThat(taskExecutor).isInstanceOf(VirtualThreadTaskExecutor.class);
    }

    @Test
    void asyncConfig_shouldNotCreateThreadPoolTaskExecutor() {
        // Given & When
        boolean hasThreadPoolTaskExecutor = applicationContext.containsBean("threadPoolTaskExecutor");

        // Then - Virtual Threads가 활성화되어 있으므로 ThreadPoolTaskExecutor는 생성되지 않아야 함
        assertThat(hasThreadPoolTaskExecutor).isFalse();
    }

    @Test
    void virtualThreadConfig_shouldBeEnabled() {
        // Given
        String virtualThreadsEnabled = applicationContext.getEnvironment()
                .getProperty("spring.threads.virtual.enabled");

        // Then
        assertThat(virtualThreadsEnabled).isEqualTo("true");
    }

    @Test
    void retryConfig_shouldBeConfigured() {
        // When - RetryConfig가 자동으로 스캔되고 설정되는지 확인
        boolean hasRetryConfig = applicationContext.containsBean("retryConfig");

        // Then
        assertThat(hasRetryConfig).isTrue();
    }

    @Test
    void allConfigurationBeans_shouldBePresent() {
        // When & Then - 모든 주요 Configuration Bean들이 존재하는지 확인
        assertThat(applicationContext.containsBean("jacksonConfig")).isTrue();
        assertThat(applicationContext.containsBean("restClientConfig")).isTrue();
        assertThat(applicationContext.containsBean("asyncConfig")).isTrue();
        assertThat(applicationContext.containsBean("retryConfig")).isTrue();
    }
}

// Virtual Threads 비활성화 상태 테스트
@SpringBootTest
@Testcontainers
class ConfigurationWithoutVirtualThreadsTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("configtest2")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.threads.virtual.enabled", () -> "false");
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void asyncConfig_shouldCreateThreadPoolTaskExecutor() {
        // When
        TaskExecutor taskExecutor = applicationContext.getBean("threadPoolTaskExecutor", TaskExecutor.class);

        // Then
        assertThat(taskExecutor).isNotNull();
        assertThat(taskExecutor).isInstanceOf(ThreadPoolTaskExecutor.class);
    }

    @Test
    void asyncConfig_shouldNotCreateVirtualThreadTaskExecutor() {
        // Given & When
        boolean hasVirtualThreadTaskExecutor = applicationContext.containsBean("virtualThreadTaskExecutor");

        // Then - Virtual Threads가 비활성화되어 있으므로 VirtualThreadTaskExecutor는 생성되지 않아야 함
        assertThat(hasVirtualThreadTaskExecutor).isFalse();
    }
}