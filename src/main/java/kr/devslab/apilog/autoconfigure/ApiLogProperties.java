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
         *   <li><b>BUILTIN</b> (default) — the starter runs {@code CREATE TABLE IF NOT EXISTS}
         *       on application startup, so the table just exists without any other tool.
         *       The SQL is idempotent, so this is safe to leave on every boot.
         *       Use this if you don't have (or don't want) Flyway / Liquibase in your project.</li>
         *   <li><b>NONE</b> — the starter does not touch the schema. Apply the DDL yourself
         *       (see <a href="https://api-log.devslab.kr/reference/schema/">api-log.devslab.kr/reference/schema</a>).
         *       Use this if your team's policy is that third-party libraries must never touch the schema,
         *       or if you've already provisioned the table some other way.</li>
         *   <li><b>FLYWAY</b> — the starter registers a {@code FlywayConfigurationCustomizer} that
         *       appends {@code classpath:db/api-log} to Flyway's locations, so the bundled
         *       {@code V1.0__create_api_log.sql} runs alongside your own migrations and is
         *       recorded in {@code flyway_schema_history}. Requires {@code org.flywaydb:flyway-core}
         *       on the classpath (the starter declares it as optional, so the consumer must add it).</li>
         * </ul>
         */
        private Management management = Management.BUILTIN;

        public Management getManagement() {
            return management;
        }

        public void setManagement(Management management) {
            this.management = management;
        }

        public enum Management {
            BUILTIN,
            NONE,
            FLYWAY
        }
    }
}
