package com.houssen.liberoshop;

import com.houssen.liberoshop.backup.ApplicationRestarter;
import com.houssen.liberoshop.backup.StartupDatabaseGuard;
import com.houssen.liberoshop.license.LicenseStartupListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        // A restore restarts the application in this process: it needs to know how.
        ApplicationRestarter.remember(BackendApplication::start, args);
        start(args);
    }

    static ConfigurableApplicationContext start(String[] args) {
        SpringApplication application = new SpringApplication(BackendApplication.class);

        application.addListeners(new LicenseStartupListener());
        // Before the database is opened: carries out a pending restore, or copies the file.
        application.addListeners(new StartupDatabaseGuard());
        return application.run(args);
    }

}
