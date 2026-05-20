package kr.devslab.apilog.mybatis.autoconfigure;

import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;

import javax.sql.DataSource;
import java.util.List;

/**
 * Runs the bundled {@code V1.0__create_api_log.sql} against the consumer's
 * {@link DataSource} when {@code api.log.schema.management=builtin} (default).
 *
 * <p>Identical shape to {@code ApiLogJpaSchemaInitializer} — both backends
 * use JDBC, so both can reuse Spring Boot's
 * {@link DataSourceScriptDatabaseInitializer}. Kept as separate classes to
 * keep each backend self-contained (no awkward "import the JPA module's bean
 * just for the initializer").
 *
 * <p>The DDL is idempotent ({@code CREATE TABLE IF NOT EXISTS}) so re-running
 * it on every boot is safe.
 */
public class ApiLogMybatisSchemaInitializer extends DataSourceScriptDatabaseInitializer {

    public static final String SCHEMA_SCRIPT = "classpath:db/api-log/V1.0__create_api_log.sql";

    public ApiLogMybatisSchemaInitializer(DataSource dataSource) {
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
