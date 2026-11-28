package com.monssef.orderservice.db;

import com.monssef.orderservice.support.IntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The schema comes from the Flyway scripts in db/migration, and Hibernate only validates it.
 * Two databases have to end up the same: a new one, migrated from V1, and one that Hibernate
 * created before Flyway, baselined at version 2.
 */
class FlywayMigrationIT extends IntegrationTest {

    private static final String LEGACY_SCHEMA = "hibernate_legacy";

    @Autowired
    private Flyway flyway;

    @Test
    void migratesAnEmptyDatabaseFromTheFirstScript() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).extracting(info -> info.getVersion().getVersion())
                .containsExactly("1", "2", "3", "4");
        assertThat(applied).extracting(MigrationInfo::getState).containsOnly(MigrationState.SUCCESS);
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void baselinesADatabaseCreatedByHibernateAndRunsTheLaterScripts() {
        Flyway legacy = loadHibernateSchema();

        legacy.migrate();

        MigrationInfo[] applied = legacy.info().applied();
        assertThat(applied).extracting(info -> info.getVersion().getVersion())
                .containsExactly("2", "3", "4");
        assertThat(applied[0].getType().isBaseline()).isTrue();
        assertThat(Arrays.stream(applied).skip(1)).extracting(MigrationInfo::getState)
                .containsOnly(MigrationState.SUCCESS);
    }

    @Test
    void baselinedDatabaseEndsWithTheSameSchemaAsAMigratedOne() {
        loadHibernateSchema().migrate();

        assertThat(columns(LEGACY_SCHEMA)).isEqualTo(columns("public"));
        assertThat(constraints(LEGACY_SCHEMA)).isEqualTo(constraints("public"));
        assertThat(indexes(LEGACY_SCHEMA)).isEqualTo(indexes("public"));
    }

    // A fresh schema holding the tables exactly as Hibernate generated them (ddl-auto: update),
    // and a Flyway with the application's settings (baseline included) pointed at it
    private Flyway loadHibernateSchema() {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + LEGACY_SCHEMA + " CASCADE");
        jdbcTemplate.execute("CREATE SCHEMA " + LEGACY_SCHEMA);
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            connection.createStatement().execute("SET search_path TO " + LEGACY_SCHEMA);
            try {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/hibernate-schema.sql"));
            } finally {
                connection.createStatement().execute("SET search_path TO public");
            }
            return null;
        });
        return Flyway.configure()
                .configuration(flyway.getConfiguration())
                .schemas(LEGACY_SCHEMA)
                .load();
    }

    private List<String> columns(String schema) {
        return jdbcTemplate.queryForList("""
                SELECT table_name || '.' || column_name || ' ' || data_type
                       || coalesce('(' || character_maximum_length || ')', '')
                       || coalesce('(' || numeric_precision || ',' || numeric_scale || ')', '')
                       || ' nullable=' || is_nullable
                       || ' default=' || coalesce(column_default, '-')
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name <> 'flyway_schema_history'
                ORDER BY 1
                """, String.class, schema)
                .stream()
                // identity sequences carry the schema name: compare without it
                .map(column -> column.replace(schema + ".", ""))
                .toList();
    }

    private List<String> constraints(String schema) {
        return jdbcTemplate.queryForList("""
                SELECT c.conrelid::regclass::text || ' ' || c.conname || ' ' || pg_get_constraintdef(c.oid)
                FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE n.nspname = ? AND c.conrelid::regclass::text NOT LIKE '%flyway_schema_history%'
                ORDER BY 1
                """, String.class, schema)
                .stream()
                .map(constraint -> constraint.replace(schema + ".", ""))
                .toList();
    }

    private List<String> indexes(String schema) {
        return jdbcTemplate.queryForList("""
                SELECT indexname || ' ' || replace(indexdef, ' ' || schemaname || '.', ' ')
                FROM pg_indexes
                WHERE schemaname = ? AND tablename <> 'flyway_schema_history'
                ORDER BY 1
                """, String.class, schema);
    }
}
