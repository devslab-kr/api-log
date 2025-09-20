package com.devs.lab.test.util;

import com.devs.lab.test.model.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RestApiClientUtilIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestClient restClient;

    private RestApiClientUtil restApiClientUtil;

    // 테스트용 DTO
    static class TestDto {
        public String name;
        public String email;

        public TestDto() {}

        public TestDto(String name, String email) {
            this.name = name;
            this.email = email;
        }
    }

    @BeforeEach
    void setUp() {
        restApiClientUtil = new RestApiClientUtil(
            restClient,
            eventPublisher,
            objectMapper
        );
    }

    @Test
    void restApiClientUtil_shouldBeInstantiatedCorrectly() {
        // When & Then
        assertThat(restApiClientUtil).isNotNull();
        assertThat(restClient).isNotNull();
        assertThat(objectMapper).isNotNull();
        assertThat(eventPublisher).isNotNull();
    }

    @Test
    void objectMapper_shouldSerializeAndDeserializeCorrectly() throws Exception {
        // Given
        TestDto dto = new TestDto("강신", "jlc488@gmail.com");

        // When
        String json = objectMapper.writeValueAsString(dto);
        TestDto deserialized = objectMapper.readValue(json, TestDto.class);

        // Then
        assertThat(json).contains("강신");
        assertThat(json).contains("jlc488@gmail.com");
        assertThat(deserialized.name).isEqualTo("강신");
        assertThat(deserialized.email).isEqualTo("jlc488@gmail.com");
    }
}