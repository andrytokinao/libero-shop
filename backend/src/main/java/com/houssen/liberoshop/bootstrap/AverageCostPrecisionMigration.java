package com.houssen.liberoshop.bootstrap;

import com.houssen.liberoshop.service.PurchaseCosting;
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
import java.util.List;
import java.util.Locale;

/**
 * Lets an installation from before purchase units keep a cost typed per tonne to the ariary.
 *
 * <p>The average purchase cost per base unit was kept to the cent. Bought by the tonne and sold by
 * the kapoka, 2 750 000 Ar a tonne became 785.71 Ar a kapoka, and read back as 2 749 985 Ar a
 * tonne. The column now keeps six places; {@code ddl-auto=update} never changes the scale of an
 * existing column, so an older database is widened here, once, keeping its values -- 785.71
 * becomes 785.710000. Nothing else is touched.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class AverageCostPrecisionMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AverageCostPrecisionMigration.class);

    static final String TABLE = "product";
    static final String COLUMN = "average_cost";
    static final int PRECISION = 18;

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public AverageCostPrecisionMigration(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer scale = columnScale();
        if (scale == null || scale >= PurchaseCosting.AVERAGE_SCALE) {
            return;
        }
        jdbc.execute("alter table " + TABLE + " alter column " + COLUMN
                + " set data type numeric(" + PRECISION + ", " + PurchaseCosting.AVERAGE_SCALE + ")");
        log.info("Colonne {}.{} passee a {} decimales.", TABLE, COLUMN, PurchaseCosting.AVERAGE_SCALE);
    }

    /** The column's declared number of decimals; null when the column is not found. */
    private Integer columnScale() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            // The stored case depends on the database: H2 folds to upper case, others do not.
            for (String name : List.of(TABLE, TABLE.toUpperCase(Locale.ROOT))) {
                for (String column : List.of(COLUMN, COLUMN.toUpperCase(Locale.ROOT))) {
                    try (ResultSet columns = metaData.getColumns(null, null, name, column)) {
                        if (columns.next()) {
                            return columns.getInt("DECIMAL_DIGITS");
                        }
                    }
                }
            }
            return null;
        } catch (SQLException e) {
            throw new IllegalStateException("Lecture du schema impossible : migration du prix d'achat abandonnee.", e);
        }
    }
}
