package io.github.kmadsdev.cardengine.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI cardEngineOpenApi() {
        return new OpenAPI().info(new Info()
                .title("credit-card-engine")
                .version("v1")
                .description("Acquisition and upgrade flows for card variants. Study project: "
                        + "the bureau, the card vault and the event broker are simulated."));
    }
}
