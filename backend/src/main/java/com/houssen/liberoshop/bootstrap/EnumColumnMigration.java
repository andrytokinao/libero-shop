package com.houssen.liberoshop.bootstrap;

import com.houssen.liberoshop.entity.DeliveryStatus;
import com.houssen.liberoshop.entity.RoleApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Lets an installation created before an enum constant existed store it.
 *
 * <p>On H2 an {@code @Enumerated(STRING)} column is a native {@code ENUM} of the constants known
 * when the table was created, and {@code ddl-auto=update} never widens it: granting "Prise de
 * commande", or cancelling an order, would fail with "valeur non permise". Same remedy as
 * {@link PaymentStatusMigration}: widen each column below to every constant, once, and leave any
 * other column type alone.
 *
 * <p>Runs after {@link UserRoleMigration}, which creates the role rows.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class EnumColumnMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EnumColumnMigration.class);

    /** A column and the enum it stores. */
    record Target(String table, String column, Class<? extends Enum<?>> type) {
    }

    static final List<Target> TARGETS = List.of(
            new Target("user_app_role", "role", RoleApp.class),
            new Target("invoice", "delivery_status", DeliveryStatus.class));

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public EnumColumnMigration(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        TARGETS.forEach(this::widenIfNeeded);
    }

    private void widenIfNeeded(Target target) {
        String type = columnType(target);
        if (type == null || !type.toUpperCase(Locale.ROOT).startsWith("ENUM")) {
            return;
        }
        List<String> names = Arrays.stream(target.type().getEnumConstants()).map(Enum::name).toList();
        if (names.stream().allMatch(name -> type.contains("'" + name + "'"))) {
            return;
        }
        String values = names.stream().map(name -> "'" + name + "'").collect(Collectors.joining(", "));
        jdbc.execute("alter table " + target.table() + " alter column " + target.column()
                + " set data type enum(" + values + ")");
        log.info("Colonne {}.{} elargie a {}.", target.table(), target.column(), values);
    }

    /** The column's declared type, e.g. {@code ENUM('CASHIER', ...)}; null when not found. */
    private String columnType(Target target) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            // The stored case depends on the database: H2 folds to upper case, others do not.
            for (String name : List.of(target.table(), target.table().toUpperCase(Locale.ROOT))) {
                for (String column : List.of(target.column(), target.column().toUpperCase(Locale.ROOT))) {
                    try (ResultSet columns = metaData.getColumns(null, null, name, column)) {
                        if (columns.next()) {
                            return columns.getString("TYPE_NAME");
                        }
                    }
                }
            }
            return null;
        } catch (SQLException e) {
            throw new IllegalStateException("Lecture du schema impossible : migration des colonnes abandonnee.", e);
        }
    }
}
