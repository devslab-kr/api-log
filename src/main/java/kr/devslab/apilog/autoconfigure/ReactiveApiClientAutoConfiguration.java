package kr.devslab.apilog.autoconfigure;

import kr.devslab.apilog.util.ReactiveApiClientUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-config for the reactive HTTP client side of api-log.
 *
 * <p>Only loads when {@link WebClient} is on the classpath — i.e., when the
 * consumer has {@code spring-webflux} declared (optional in this starter).
 * Servlet-only apps don't pay for the reactive client.
 *
 * <p>Uses Spring Boot's auto-configured {@link WebClient.Builder} so the
 * consumer's {@code WebClientCustomizer} beans (base URL, default headers,
 * codecs, etc.) flow through. Provide your own {@link ReactiveApiClientUtil}
 * bean to fully replace the wiring.
 */
@AutoConfiguration(after = ApiLogAutoConfiguration.class)
@ConditionalOnClass(WebClient.class)
@ConditionalOnProperty(name = "api.log.enabled", havingValue = "true", matchIfMissing = true)
public class ReactiveApiClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ReactiveApiClientUtil reactiveApiClientUtil(WebClient.Builder webClientBuilder,
                                                        ApplicationEventPublisher eventPublisher,
                                                        ObjectMapper objectMapper) {
        return new ReactiveApiClientUtil(webClientBuilder.build(), eventPublisher, objectMapper);
    }
}
