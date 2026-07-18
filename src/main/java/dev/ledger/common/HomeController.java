package dev.ledger.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class HomeController {

    /** Bare-domain visitors (recruiters) land on Swagger instead of a 401. */
    @GetMapping("/")
    ResponseEntity<Void> home() {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/swagger-ui.html")).build();
    }
}
