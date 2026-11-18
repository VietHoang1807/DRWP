package vn.drwp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * business-app: modular monolith (ADR 001, 014). Module = package con trực tiếp của {@code vn.drwp}
 * (tenant, identity, metadata, record, workflow, file, notification, audit); {@code platform} là shared module OPEN.
 */
@SpringBootApplication
@Modulithic(systemName = "DRWP", sharedModules = "platform")
public class DrwpApplication {

    public static void main(String[] args) {
        SpringApplication.run(DrwpApplication.class, args);
    }
}
