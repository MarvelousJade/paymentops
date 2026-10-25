package dev.ledger.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Event payloads use the Jackson 2 API required by Spring Kafka's JSON
 * integration. Spring Boot 4 uses Jackson 3 for HTTP, so this mapper is kept
 * deliberately separate from MVC serialization.
 */
@Configuration
public class EventJsonConfiguration {

    @Bean
    ObjectMapper eventObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
