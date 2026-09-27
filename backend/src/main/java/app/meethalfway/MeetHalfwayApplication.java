package app.meethalfway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the MeetHalfway backend.
 *
 * <p>Lives in the base package {@code app.meethalfway} so component scanning
 * reaches the {@code adapters}, {@code application}, and {@code config} layers.
 * The pure {@code domain} layer contains no Spring components and is wired
 * explicitly (constructor injection) from the {@code config} composition root.
 */
@SpringBootApplication
public class MeetHalfwayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeetHalfwayApplication.class, args);
    }
}
