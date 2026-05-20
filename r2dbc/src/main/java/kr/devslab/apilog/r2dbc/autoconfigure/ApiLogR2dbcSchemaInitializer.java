package kr.devslab.apilog.r2dbc.autoconfigure;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.boot.r2dbc.init.R2dbcScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;

import java.util.List;

/**
 * Pure-reactive schema initializer — runs {@code V1.0__create_api_log.sql}
 * against a {@link ConnectionFactory} without ever opening a JDBC connection.
 *
 * <p>Mirrors {@code ApiLogJpaSchemaInitializer} but routes through Spring
 * Boot's {@link R2dbcScriptDatabaseInitializer} (vs. {@code DataSourceScript...}).
 * That keeps R2DBC-only applications honest — no surprise JDBC driver pull-in
 * just because the audit table needs to be created.
 *
 * <p>Activated when {@code api.log.schema.management=builtin} (default) AND
 * the R2DBC autoconfig is active. The DDL is idempotent
 * ({@code CREATE TABLE IF NOT EXISTS}), so re-running on every boot is safe.
 */
public class ApiLogR2dbcSchemaInitializer extends R2dbcScriptDatabaseInitializer {

    /** Classpath path of the bundled schema script (shared with the JPA + MyBatis backends). */
    public static final String SCHEMA_SCRIPT = "classpath:db/api-log/V1.0__create_api_log.sql";

    public ApiLogR2dbcSchemaInitializer(ConnectionFactory connectionFactory) {
        super(connectionFactory, settings());
    }

    private static DatabaseInitializationSettings settings() {
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of(SCHEMA_SCRIPT));
        settings.setMode(DatabaseInitializationMode.ALWAYS);
        settings.setContinueOnError(false);
        return settings;
    }
}
