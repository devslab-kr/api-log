package kr.devslab.apilog.autoconfigure;

import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Registers a {@link FlywayConfigurationCustomizer} that appends this starter's
 * migration location to Flyway's existing locations, so the bundled
 * {@code V1.0__create_api_log.sql} runs as part of the consumer's Flyway sweep.
 *
 * <p>Activated only when:
 * <ul>
 *   <li>{@code org.flywaydb.core} is on the classpath (Flyway is optional in this starter), AND</li>
 *   <li>{@code api.log.schema.management=flyway} is set (default is {@code NONE}).</li>
 * </ul>
 *
 * <p>Behavior is additive: existing {@code spring.flyway.locations} are preserved, and the
 * starter's location is appended. The consumer's own migrations continue to work alongside ours.
 */
@Configuration
@ConditionalOnClass(FluentConfiguration.class)
@ConditionalOnProperty(prefix = "api.log.schema", name = "management", havingValue = "flyway")
public class ApiLogFlywayConfig {

    /** Classpath location of this starter's Flyway migrations. */
    public static final String MIGRATION_LOCATION = "classpath:db/api-log";

    @Bean
    public FlywayConfigurationCustomizer apiLogFlywayCustomizer() {
        return configuration -> {
            String[] merged = Stream.concat(
                    Arrays.stream(configuration.getLocations()).map(Location::getDescriptor),
                    Stream.of(MIGRATION_LOCATION)
            ).toArray(String[]::new);
            configuration.locations(merged);
        };
    }
}
