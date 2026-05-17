package kr.devslab.apilog.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "api.log")
public class ApiLogProperties {

    /**
     * Enable the API logging infrastructure (listener, service, repository, RestApiClientUtil).
     * When false, no beans are registered.
     * Default: true.
     */
    private boolean enabled = true;

    /**
     * How the api_log table's schema is provisioned. See {@link Schema}.
     */
    private Schema schema = new Schema();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Schema getSchema() {
        return schema;
    }

    public void setSchema(Schema schema) {
        this.schema = schema;
    }

    public static class Schema {
        /**
         * Schema management strategy for the {@code api_log} table.
         *
         * <ul>
         *   <li><b>NONE</b> (default) — the starter does not touch the schema. Apply the DDL
         *       yourself (the SQL is documented at <a href="https://api-log.devslab.kr/reference/schema/">api-log.devslab.kr/reference/schema</a>
         *       and shipped at {@code classpath:db/api-log/V1.0__create_api_log.sql} inside the JAR
         *       if you want to copy it into your own migrations).
         *       Use this with Liquibase, manual DDL, or a custom Flyway flow.</li>
         *   <li><b>FLYWAY</b> — the starter registers a {@code FlywayConfigurationCustomizer} that
         *       appends {@code classpath:db/api-log} to Flyway's locations, so the bundled
         *       {@code V1.0__create_api_log.sql} runs alongside your own migrations.
         *       Requires {@code org.flywaydb:flyway-core} on the classpath
         *       (the starter declares it as optional, so the consumer must add it).</li>
         * </ul>
         */
        private Management management = Management.NONE;

        public Management getManagement() {
            return management;
        }

        public void setManagement(Management management) {
            this.management = management;
        }

        public enum Management {
            NONE,
            FLYWAY
        }
    }
}
