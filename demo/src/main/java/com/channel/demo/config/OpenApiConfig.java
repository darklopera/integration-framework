package com.channel.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Integration Framework — Demo Service")
                .version("1.0.0")
                .description("""
                    ## Technical Lead Assessment — Section C
                    
                    This service demonstrates all resilience patterns implemented in the
                    **Reusable Integration Framework** (Section B).
                    
                    ### Quick Start
                    1. **POST `/demo/run-all-tests`** — Runs all 7 test cases automatically
                    2. **PUT `/demo/mode/{mode}`** — Change upstream behavior
                    3. **POST `/demo/payment`** — Single payment with full pipeline
                    
                    ### Patterns Demonstrated
                    | Pattern | Endpoint |
                    |---------|----------|
                    | ⏱ Timeout | `/demo/mode/SLOW_RESPONSE` then POST `/demo/payment` |
                    | ⟳ Retry + Backoff + Jitter | `/demo/mode/RANDOM_FAILURE` then POST `/demo/payment` |
                    | ⚡ Circuit Breaker | `/demo/mode/ALWAYS_FAIL` then POST `/demo/payment` x6 |
                    | 🔑 Idempotency Key | POST `/demo/payment?idempotencyKey=same-key` twice |
                    | 🔄 Full pipeline | POST `/demo/run-all-tests` |
                    
                    ### Framework (Section B)
                    Published to GitHub Packages — importable by any Maven project.
                    """)
                .contact(new Contact().name("Technical Assessment").url("https://github.com"))
            );
    }
}
