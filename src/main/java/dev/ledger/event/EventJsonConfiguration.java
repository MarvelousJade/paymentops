package dev.ledger.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Existing event services use Jackson 2; Spring Boot 4 uses Jackson 3 for HTTP.
 * Keep the event mapper separate from MVC serialization. Kafka transports
 * JSON strings here; it does not require this mapper or enforce Registry schemas.
 */
@Configuration
public class EventJsonConfiguration {

    @Bean
    ObjectMapper eventObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
