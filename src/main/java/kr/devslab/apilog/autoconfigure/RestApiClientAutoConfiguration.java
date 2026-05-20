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
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
 *   <li>{@link MappingJackson2HttpMessageConverter} using the core
 *       {@code apiLogObjectMapper} (Blackbird-enabled)</li>
 *   <li>{@link RestClient} with the converter wired in, optional base URL via
 *       {@code rest.client.base-url}</li>
 *   <li>{@link RestApiClientUtil} — the actual API surface consumers inject</li>
 * </ul>
 *
 * <p>Each bean is {@link ConditionalOnMissingBean} so the consumer can swap any
 * piece (e.g., provide their own {@code RestClient} with auth headers).
 */
@AutoConfiguration(after = ApiLogAutoConfiguration.class)
@ConditionalOnClass(RestClient.class)
@ConditionalOnProperty(name = "api.log.enabled", havingValue = "true", matchIfMissing = true)
public class RestApiClientAutoConfiguration {

    @Value("${rest.client.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${rest.client.read-timeout:30000}")
    private int readTimeout;

    @Value("${rest.client.base-url:}")
    private String baseUrl;

    @Bean
    @ConditionalOnMissingBean
    public ClientHttpRequestFactory apiLogClientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
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
