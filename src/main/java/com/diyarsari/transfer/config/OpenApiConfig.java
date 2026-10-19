package com.diyarsari.transfer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI transferOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Idempotent Transfer API")
                .version("1.0.0")
                .description("Money transfers that are never applied twice and never overdraw an account.")
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }
}
