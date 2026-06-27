package com.upi.offline.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI offlineUPIOpenAPI() {

        return new OpenAPI()
                .info(new Info()
                        .title("Offline UPI Payment Simulator API")
                        .description("REST API for Offline UPI Payment Simulation with AES Encryption and Network Synchronization.")
                        .version("1.0")
                        .contact(new Contact()
                                .name("Anushka")
                                .url("https://github.com/anushka0809/OfflineUPI")));
    }
}