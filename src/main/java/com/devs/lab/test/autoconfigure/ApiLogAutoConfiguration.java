package com.devs.lab.test.autoconfigure;

import com.devs.lab.test.config.RetryConfig;
import com.devs.lab.test.listener.ApiEventListener;
import com.devs.lab.test.repository.ApiLogRepository;
import com.devs.lab.test.service.ApiLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ConditionalOnClass({ApiEventListener.class, ApiLogService.class})
@EnableConfigurationProperties(ApiLogProperties.class)
@ConditionalOnProperty(name = "api.log.enabled", havingValue = "true", matchIfMissing = true)
@EntityScan(basePackages = "com.devs.lab.test.model")
@EnableJpaRepositories(basePackages = "com.devs.lab.test.repository")
@Import(RetryConfig.class)
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
}