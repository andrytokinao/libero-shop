package com.houssen.liberoshop.backup;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * H2's own online backup: {@code BACKUP TO} writes a consistent copy of the database into a zip
 * while other connections keep reading and writing -- no need to stop the till.
 *
 * <p>Verifying unzips the copy into a temporary folder and opens it read-only with its own
 * connection, so a damaged backup is found on the day it is made, not on the day it is needed.
 */
public class H2BackupEngine implements BackupEngine {

    private final DataSource live;
    private final DatabaseFile database;
    private final String username;
    private final String password;

    public H2BackupEngine(DataSource live, DatabaseFile database, String username, String password) {
        this.live = live;
        this.database = database;
        this.username = username;
        this.password = password == null ? "" : password;
    }

    @Override
    public void snapshot(Path zip) throws IOException {
        Files.createDirectories(zip.getParent());
        Path temporary = zip.resolveSibling(zip.getFileName() + ".part");
        Files.deleteIfExists(temporary);
        try {
            // A path, not user input -- but quoted all the same.
            new JdbcTemplate(live).execute("BACKUP TO '" + temporary.toString().replace("'", "''") + "'");
        } catch (DataAccessException e) {
            throw new IOException("Sauvegarde H2 impossible : " + e.getMostSpecificCause().getMessage(), e);
        }
        Files.move(temporary, zip, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public Verification verify(Path zip) {
        Path folder = null;
        try {
            folder = Files.createTempDirectory("liberoshop-verify-");
            Path copy = folder.resolve(database.name() + DatabaseFile.SUFFIX);
            DatabaseArchive.extractDatabase(zip, copy);
            String url = "jdbc:h2:file:" + folder.resolve(database.name()) + ";ACCESS_MODE_DATA=r";
            SingleConnectionDataSource dataSource = new SingleConnectionDataSource(url, username, password, true);
            try {
                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                return new Verification(true, count(jdbc, "invoice"), count(jdbc, "product"), null);
            } finally {
                dataSource.destroy();
            }
        } catch (IOException | DataAccessException e) {
            return new Verification(false, 0, 0, e.getMessage());
        } finally {
            deleteQuietly(folder);
        }
    }

    /** Zero for a table the backup predates. */
    private static long count(JdbcTemplate jdbc, String table) {
        try {
            Long count = jdbc.queryForObject("select count(*) from " + table, Long.class);
            return count == null ? 0 : count;
        } catch (DataAccessException e) {
            return 0;
        }
    }

    private static void deleteQuietly(Path folder) {
        if (folder == null) {
            return;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
            // A temporary folder left behind is the system's to clean.
        }
    }
}
