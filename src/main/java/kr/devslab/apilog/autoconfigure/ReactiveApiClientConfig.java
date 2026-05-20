package kr.devslab.apilog.autoconfigure;

import kr.devslab.apilog.util.ReactiveApiClientUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Registers {@link ReactiveApiClientUtil} when {@code spring-webflux} is on the
 * classpath. The starter declares {@code spring-webflux} as {@code optional}, so
 * consumers who don't want the reactive client don't pay for it (no bean, no
 * dependency).
 *
 * <p>Uses {@link WebClient.Builder} (auto-configured by Spring Boot whenever
 * WebClient is on the classpath) so consumers can customize the underlying
 * client — base URL, default headers, codecs, etc. — via standard Spring
 * {@code WebClientCustomizer} beans, and the customizations flow through.
 */
@Configuration
@ConditionalOnClass(WebClient.class)
public class ReactiveApiClientConfig {

    @Bean
    @ConditionalOnMissingBean
    public ReactiveApiClientUtil reactiveApiClientUtil(WebClient.Builder webClientBuilder,
                                                        ApplicationEventPublisher eventPublisher,
                                                        ObjectMapper objectMapper) {
        return new ReactiveApiClientUtil(webClientBuilder.build(), eventPublisher, objectMapper);
    }
}
