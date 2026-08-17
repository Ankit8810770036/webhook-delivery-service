package com.webhook.delivery.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI webhookDeliveryOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Reliable Webhook Delivery Service API")
                        .description("High-reliability, multi-tenant webhook ingestion and delivery engine featuring database-level claiming, exponential backoff with jitter, HMAC-SHA256 signatures, and circuit breaker resilience.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Engineering Assignment")
                                .email("dev@example.com"))
                        .license(new License().name("Apache 2.0")));
    }
}
