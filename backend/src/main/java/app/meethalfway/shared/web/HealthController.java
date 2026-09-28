package app.meethalfway.shared.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal inbound web adapter exposing the liveness endpoint on the versioned
 * REST surface under {@code /api/v1} (Requirement 11.1). It owns the
 * {@code /api/v1/health} base path declared in the {@link RouteRegistry}.
 */
@RestController
@RequestMapping(RouteRegistry.HEALTH_BASE_PATH)
public class HealthController {

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
