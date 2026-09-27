package app.meethalfway.adapters.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal inbound web adapter establishing the versioned REST surface under
 * {@code /api/v1} (Requirement 11.1). Full meeting endpoints are added in later
 * tasks; this liveness endpoint lets the scaffold build and run end-to-end.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
