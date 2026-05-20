package kr.devslab.apilog.jpa.autoconfigure;

import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;

import javax.sql.DataSource;
import java.util.List;

/**
 * Runs the bundled {@code V1.0__create_api_log.sql} against the consumer's
 * {@link DataSource} during application startup.
 *
 * <p>Used when {@code api.log.schema.management=builtin} (the default). The SQL
 * uses {@code CREATE TABLE IF NOT EXISTS} / {@code CREATE INDEX IF NOT EXISTS},
 * so re-running it on every application boot is a no-op when the table already
 * exists — no Flyway, no Liquibase, no manual psql needed.
 *
 * <p>Extending {@link DataSourceScriptDatabaseInitializer} (instead of doing a
 * one-off {@code @Bean InitializingBean}) makes Spring Boot's
 * {@code JpaBaseConfiguration} pick up the dependency automatically — the
 * {@code EntityManagerFactory} won't validate the schema until this initializer
 * has finished, so a fresh database doesn't fail JPA's startup validation.
 *
 * <p>To opt out of this behavior, set {@code api.log.schema.management=none}
 * (apply the DDL yourself) or {@code flyway} (let Flyway own it).
 */
public class ApiLogJpaSchemaInitializer extends DataSourceScriptDatabaseInitializer {

    /** Classpath path of the bundled schema script (shared with {@code ApiLogFlywayConfig}). */
    public static final String SCHEMA_SCRIPT = "classpath:db/api-log/V1.0__create_api_log.sql";

    public ApiLogJpaSchemaInitializer(DataSource dataSource) {
        super(dataSource, settings());
    }

    private static DatabaseInitializationSettings settings() {
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of(SCHEMA_SCRIPT));
        settings.setMode(DatabaseInitializationMode.ALWAYS);
        settings.setContinueOnError(false);
        return settings;
    }
}
