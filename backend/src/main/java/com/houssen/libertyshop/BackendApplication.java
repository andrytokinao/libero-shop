package com.houssen.libertyshop;

import com.houssen.libertyshop.license.LicenseStartupListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(BackendApplication.class);
        // Verify the license before any bean is created or any database connection is
        // opened. Registered here rather than through spring.factories so that
        // @SpringBootTest, which does not run main(), is not affected.
        application.addListeners(new LicenseStartupListener());
        application.run(args);
    }

}
