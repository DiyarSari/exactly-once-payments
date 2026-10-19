package com.diyarsari.transfer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IdempotentTransferApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdempotentTransferApiApplication.class, args);
    }
}
