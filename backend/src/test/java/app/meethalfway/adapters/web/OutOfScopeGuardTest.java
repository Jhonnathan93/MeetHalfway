package app.meethalfway.adapters.web;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Out-of-scope MVP guards (Task 11.3). These are "absence" assertions: the MVP
 * must not expose venue booking, participant invitations, venue-category
 * filtering, or per-participant travel constraints. Rather than trust
 * documentation, this test inspects the actual REST surface of
 * {@link MeetingController} and fails if any such endpoint appears.
 */
class OutOfScopeGuardTest {

    private static final List<String> FORBIDDEN_PATH_FRAGMENTS = List.of(
            "book", "reservation", "reserve",
            "invite", "invitation",
            "category", "categories", "venue", "place-type",
            "constraint", "per-participant-limit",
            "login", "auth", "signup", "register");

    private List<String> mappedPaths() {
        List<String> paths = new ArrayList<>();
        String base = classLevelPath();
        for (Method method : MeetingController.class.getDeclaredMethods()) {
            collect(method.getAnnotation(GetMapping.class), base, paths);
            collect(method.getAnnotation(PostMapping.class), base, paths);
            collect(method.getAnnotation(PutMapping.class), base, paths);
            collect(method.getAnnotation(DeleteMapping.class), base, paths);
        }
        return paths;
    }

    private String classLevelPath() {
        RequestMapping mapping = MeetingController.class.getAnnotation(RequestMapping.class);
        if (mapping == null || mapping.value().length == 0) {
            return "";
        }
        return mapping.value()[0];
    }

    private static void collect(GetMapping annotation, String base, List<String> out) {
        if (annotation != null) {
            for (String path : annotation.value()) {
                out.add(base + path);
            }
        }
    }

    private static void collect(PostMapping annotation, String base, List<String> out) {
        if (annotation != null) {
            for (String path : annotation.value()) {
                out.add(base + path);
            }
        }
    }

    private static void collect(PutMapping annotation, String base, List<String> out) {
        if (annotation != null) {
            for (String path : annotation.value()) {
                out.add(base + path);
            }
        }
    }

    private static void collect(DeleteMapping annotation, String base, List<String> out) {
        if (annotation != null) {
            for (String path : annotation.value()) {
                out.add(base + path);
            }
        }
    }

    @Test
    void controllerExposesOnlyTheExpectedMvpEndpoints() {
        List<String> paths = mappedPaths();
        assertThat(paths).containsExactlyInAnyOrder(
                "/api/v1/meetings",
                "/api/v1/meetings/{code}",
                "/api/v1/meetings/{code}",
                "/api/v1/meetings/{code}",
                "/api/v1/meetings/{code}/recommendations",
                "/api/v1/geocode/autocomplete",
                "/api/v1/geocode/resolve");
    }

    @Test
    void controllerExposesNoOutOfScopeEndpoints() {
        List<String> paths = mappedPaths();
        for (String path : paths) {
            String lower = path.toLowerCase(Locale.ROOT);
            for (String forbidden : FORBIDDEN_PATH_FRAGMENTS) {
                assertThat(lower)
                        .as("MVP must not expose an out-of-scope endpoint: %s", path)
                        .doesNotContain(forbidden);
            }
        }
    }

    @Test
    void controllerDoesNotExposeAuthenticationOrInvitationMethods() {
        for (Method method : MeetingController.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(Locale.ROOT);
            assertThat(name)
                    .as("method %s suggests out-of-scope behavior", method.getName())
                    .doesNotContain("invite")
                    .doesNotContain("book")
                    .doesNotContain("login")
                    .doesNotContain("register");
        }
    }
}
