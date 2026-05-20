package kr.devslab.apilog.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Top-level configuration namespace for the api-log starter.
 *
 * <p>v0.6.0 — the {@code schema.management} property still applies to the
 * JPA / MyBatis backends (both run a JDBC initializer). The R2DBC backend has
 * its own toggle ({@code api.log.r2dbc.schema.enabled}) because reactive
 * initialization runs against a {@code ConnectionFactory} rather than a
 * {@code DataSource}.
 */
@ConfigurationProperties(prefix = "api.log")
public class ApiLogProperties {

    /**
     * Master switch — when false no api-log beans are registered (listener,
     * writer, schema initializer, HTTP utilities). Default: true.
     */
    private boolean enabled = true;

    /**
     * How the {@code api_log} table's schema is provisioned. See {@link Schema}.
     * Applies to the JPA + MyBatis backends. The R2DBC backend uses its own
     * reactive initializer keyed off {@code api.log.r2dbc.schema.enabled}.
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
         *   <li><b>BUILTIN</b> (default) — the starter runs the bundled DDL on
         *       application startup. The SQL uses {@code IF NOT EXISTS}, so it's
         *       idempotent and safe to leave on every boot.
         *       Use this if you don't have (or don't want) Flyway / Liquibase
         *       in your project.</li>
         *   <li><b>NONE</b> — the starter does not touch the schema. Apply the
         *       DDL yourself (see <a href="https://api-log.devslab.kr/reference/schema/">api-log.devslab.kr/reference/schema</a>).
         *       Use this if your team's policy is that third-party libraries
         *       must never touch the schema.</li>
         *   <li><b>FLYWAY</b> — the starter registers a
         *       {@code FlywayConfigurationCustomizer} that appends
         *       {@code classpath:db/api-log} to Flyway's locations, so the
         *       bundled {@code V1.0__create_api_log.sql} runs alongside your
         *       own migrations and gets recorded in
         *       {@code flyway_schema_history}. Requires
         *       {@code org.flywaydb:flyway-core} on the classpath (the starter
         *       declares it as optional, so the consumer must add it). Only
         *       applies when the JPA backend is in use.</li>
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
