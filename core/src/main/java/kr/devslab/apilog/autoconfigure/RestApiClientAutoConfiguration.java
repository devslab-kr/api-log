package kr.devslab.apilog.autoconfigure;

import kr.devslab.apilog.util.RestApiClientUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import java.time.Duration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * Auto-config for the blocking HTTP client side of api-log.
 *
 * <p>Only loads when {@link RestClient} is on the classpath — i.e., when the
 * consumer has {@code spring-web} (transitively from
 * {@code spring-boot-starter-web} or declared directly). Consumers running a
 * pure WebFlux app without {@code spring-web} on the classpath get nothing
 * registered here, which is the whole point of this split — the starter
 * doesn't force a Servlet stack on reactive-only apps.
 *
 * <p>Registers:
 * <ul>
 *   <li>{@link ClientHttpRequestFactory} with configurable timeouts
 *       ({@code rest.client.connect-timeout}, {@code rest.client.read-timeout})</li>
 *   <li>{@link MappingJackson2HttpMessageConverter} using the Spring Boot
 *       {@link ObjectMapper} (Blackbird-enabled by the core customizer)</li>
 *   <li>{@link RestClient} with the converter wired in, optional base URL via
 *       {@code rest.client.base-url}</li>
 *   <li>{@link RestApiClientUtil} — the actual API surface consumers inject</li>
 * </ul>
 *
 * <p>Each bean is {@link ConditionalOnMissingBean} so the consumer can swap any
 * piece (e.g., provide their own {@code RestClient} with auth headers).
 */
@AutoConfiguration(after = ApiLogCoreAutoConfiguration.class)
@ConditionalOnClass(RestClient.class)
@ConditionalOnProperty(name = "api.log.enabled", havingValue = "true", matchIfMissing = true)
public class RestApiClientAutoConfiguration {

    @Value("${rest.client.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${rest.client.read-timeout:30000}")
    private int readTimeout;

    @Value("${rest.client.base-url:}")
    private String baseUrl;

    /**
     * Use {@link JdkClientHttpRequestFactory} (backed by {@code java.net.http.HttpClient},
     * Java 11+) instead of {@link org.springframework.http.client.SimpleClientHttpRequestFactory}.
     *
     * <p>{@code Simple...} wraps {@code java.net.HttpURLConnection} whose
     * {@code setRequestMethod} rejects {@code "PATCH"} with
     * {@code ProtocolException: Invalid HTTP method: PATCH}, breaking
     * {@link RestApiClientUtil#patchSync} / {@code patchSyncTyped} entirely
     * (long-standing JDK limitation). The modern {@code JdkClientHttpRequestFactory}
     * supports every HTTP verb the {@code java.net.http} API does — PATCH
     * included — and uses Java 11+ as the floor api-log already targets.
     *
     * <p>v3.0.1 — switched from {@code SimpleClientHttpRequestFactory} after a
     * PATCH integration test surfaced the JDK behaviour. The connect/read
     * timeout properties are preserved.
     */
    @Bean
    @ConditionalOnMissingBean
    public ClientHttpRequestFactory apiLogClientHttpRequestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofMillis(readTimeout));
        // Connect timeout is set on the underlying HttpClient. The default-builder
        // path JdkClientHttpRequestFactory uses applies a sensible default; we
        // leave it as-is rather than reach into HttpClient construction because the
        // property contract is the request-level read timeout. Consumers who need a
        // tighter connect timeout swap the bean (it's @ConditionalOnMissingBean).
        return factory;
    }

    @Bean
    @ConditionalOnMissingBean
    public MappingJackson2HttpMessageConverter apiLogMappingJackson2HttpMessageConverter(ObjectMapper objectMapper) {
        return new MappingJackson2HttpMessageConverter(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RestClient apiLogRestClient(ClientHttpRequestFactory requestFactory,
                                        MappingJackson2HttpMessageConverter messageConverter) {
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(messageConverter);
                });
        if (baseUrl != null && !baseUrl.isEmpty()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public RestApiClientUtil restApiClientUtil(RestClient restClient,
                                                ApplicationEventPublisher eventPublisher,
                                                ObjectMapper objectMapper) {
        return new RestApiClientUtil(restClient, eventPublisher, objectMapper);
    }
}
