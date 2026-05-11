package com.channel.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .servers(List.of(
                new Server()
                    .url("https://integration-framework-production.up.railway.app")
                    .description("Railway Production")
            ))
            .info(new Info()
                .title("Integration Framework — Demo Service")
                .version("1.0.0")
                .description("Technical Lead Assessment — Section C")
                .contact(new Contact().name("Technical Assessment")));
    }
}