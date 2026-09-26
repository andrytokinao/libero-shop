package com.houssen.liberoshop;

import com.houssen.liberoshop.license.LicenseStartupListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(BackendApplication.class);

        application.addListeners(new LicenseStartupListener());
        application.run(args);
    }

}
