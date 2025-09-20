package com.devs.lab.test.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Value("${rest.client.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${rest.client.read-timeout:30000}")
    private int readTimeout;

    @Value("${rest.client.base-url:}")
    private String baseUrl;

    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    @Bean
    public RestClient restClient(ClientHttpRequestFactory requestFactory,
                                MappingJackson2HttpMessageConverter messageConverter) {
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .messageConverters(converters -> {
                    converters.removeIf(converter -> converter instanceof MappingJackson2HttpMessageConverter);
                    converters.add(messageConverter);
                });

        if (baseUrl != null && !baseUrl.isEmpty()) {
            builder.baseUrl(baseUrl);
        }

        return builder.build();
    }

    @Bean
    public RestTemplate restTemplate(ClientHttpRequestFactory requestFactory,
                                   MappingJackson2HttpMessageConverter messageConverter) {
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        restTemplate.getMessageConverters().removeIf(converter -> converter instanceof MappingJackson2HttpMessageConverter);
        restTemplate.getMessageConverters().add(messageConverter);
        return restTemplate;
    }
}