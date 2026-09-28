package app.meethalfway.meetings.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.meethalfway.meetings.web.support.NoOpRoutingProvider;
import app.meethalfway.meetings.web.support.StubRecommendationEngine;
import app.meethalfway.meetings.web.support.TestMeetingRepository;
import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.meetings.domain.model.Meeting;
import app.meethalfway.meetings.domain.model.MeetingInput;
import app.meethalfway.shared.domain.Minutes;
import app.meethalfway.meetings.domain.model.OutlierRule;
import app.meethalfway.shared.domain.ParticipantId;
import app.meethalfway.meetings.domain.model.ParticipantInput;
import app.meethalfway.meetings.domain.model.RecommendationOutcome;
import app.meethalfway.meetings.domain.model.RoutingError;
import app.meethalfway.meetings.domain.model.ServiceBounds;
import app.meethalfway.meetings.domain.model.StrategyResult;
import app.meethalfway.meetings.domain.model.StrategyResults;
import app.meethalfway.shared.domain.TransportMode;
import app.meethalfway.meetings.application.ComputeRecommendations;
import app.meethalfway.meetings.application.CreateMeeting;
import app.meethalfway.meetings.application.DeleteMeeting;
import app.meethalfway.meetings.application.EditMeeting;
import app.meethalfway.meetings.application.GetMeeting;
import app.meethalfway.meetings.application.UrlCodeGenerator;
import app.meethalfway.meetings.web.MeetingController;
import app.meethalfway.meetings.web.dto.WebMapper;
import app.meethalfway.shared.web.GlobalExceptionHandler;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Standalone MockMvc tests for {@link MeetingController} and the shared
 * {@link GlobalExceptionHandler} (Task 12.4).
 *
 * <p>Uses {@link MockMvcBuilders#standaloneSetup} rather than {@code @WebMvcTest}:
 * the slice annotations trigger Spring's classpath scanning, whose bundled ASM
 * (Spring 3.3.5) cannot parse JDK 25 bytecode (class file major version 69) — the
 * same limitation the pom documents for ArchUnit. Additionally the application
 * use cases are {@code final}, which JDK 25's Byte Buddy cannot mock, so they are
 * constructed for real against in-memory fakes ({@link TestMeetingRepository},
 * {@link StubRecommendationEngine}). This exercises the true wiring — routing,
 * status codes, validation, and the actionable 422 body — fully offline.
 */
class MeetingControllerTest {

    private final TestMeetingRepository repository = new TestMeetingRepository();
    private final StubRecommendationEngine engine = new StubRecommendationEngine();
    private final EngineConfig engineConfig = new EngineConfig(
            new ServiceBounds(-90.0, 90.0, -180.0, 180.0),
            100, 20000.0, new OutlierRule.MedianMultiple(2.0), 0.5);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CreateMeeting createMeeting = new CreateMeeting(repository, new UrlCodeGenerator());
        GetMeeting getMeeting = new GetMeeting(repository);
        EditMeeting editMeeting = new EditMeeting(repository);
        DeleteMeeting deleteMeeting = new DeleteMeeting(repository);
        ComputeRecommendations computeRecommendations = new ComputeRecommendations(
                engine, repository, new NoOpRoutingProvider(), engineConfig);
        MeetingController controller = new MeetingController(
                createMeeting, getMeeting, editMeeting, deleteMeeting,
                computeRecommendations, new WebMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Meeting seedMeeting(String code) {
        List<ParticipantInput> participants = new ArrayList<>();
        participants.add(new ParticipantInput(new ParticipantId("p1"), "Ana", new Coordinate(6.24, -75.58)));
        participants.add(new ParticipantInput(new ParticipantId("p2"), "Bruno", new Coordinate(6.25, -75.56)));
        Meeting meeting = Meeting.of(code, new MeetingInput(participants, TransportMode.DRIVING));
        repository.seed(meeting);
        return meeting;
    }

    private static StrategyResult sampleResult() {
        Map<ParticipantId, Minutes> perParticipant = new LinkedHashMap<>();
        perParticipant.put(new ParticipantId("p1"), new Minutes(10));
        perParticipant.put(new ParticipantId("p2"), new Minutes(12));
        return new StrategyResult(new Coordinate(6.24, -75.57), perParticipant, 22.0, 12, 1.0);
    }

    private static final String VALID_CREATE_BODY = """
            {
              "participants": [
                {"name": "Ana", "lat": 6.24, "lng": -75.58},
                {"name": "Bruno", "lat": 6.25, "lng": -75.56}
              ],
              "transportMode": "driving"
            }
            """;

    @Test
    void createReturns201WithMeetingBodyUnderApiV1() throws Exception {
        mockMvc.perform(post("/api/v1/meetings")
                        .contentType("application/json")
                        .content(VALID_CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.urlCode").isNotEmpty())
                .andExpect(jsonPath("$.transportMode").value("driving"))
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.recommendation").doesNotExist());
        assertThat(repository.findByUrlCode("nope")).isEmpty();
    }

    @Test
    void getReturns200WhenPresent() throws Exception {
        seedMeeting("ABC12345");

        mockMvc.perform(get("/api/v1/meetings/ABC12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urlCode").value("ABC12345"))
                .andExpect(jsonPath("$.participants[0].name").value("Ana"));
    }

    @Test
    void getReturns404WhenUnknownCode() throws Exception {
        mockMvc.perform(get("/api/v1/meetings/MISSING0"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void editReturns200WithUpdatedMeeting() throws Exception {
        seedMeeting("ABC12345");
        String body = """
                {
                  "participants": [
                    {"name": "Carla", "lat": 6.20, "lng": -75.60},
                    {"name": "Diego", "lat": 6.21, "lng": -75.61}
                  ],
                  "transportMode": "walking"
                }
                """;

        mockMvc.perform(put("/api/v1/meetings/ABC12345")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urlCode").value("ABC12345"))
                .andExpect(jsonPath("$.transportMode").value("walking"));
    }

    @Test
    void editReturns404WhenUnknownCode() throws Exception {
        mockMvc.perform(put("/api/v1/meetings/MISSING0")
                        .contentType("application/json")
                        .content(VALID_CREATE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void deleteReturns204() throws Exception {
        seedMeeting("ABC12345");

        mockMvc.perform(delete("/api/v1/meetings/ABC12345"))
                .andExpect(status().isNoContent());
        assertThat(repository.contains("ABC12345")).isFalse();
    }

    @Test
    void createRejectsSingleParticipantWith400() throws Exception {
        String body = """
                {
                  "participants": [
                    {"name": "Solo", "lat": 6.24, "lng": -75.58}
                  ],
                  "transportMode": "driving"
                }
                """;

        mockMvc.perform(post("/api/v1/meetings")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createRejectsElevenParticipantsWith400() throws Exception {
        StringBuilder participants = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            if (i > 0) {
                participants.append(",");
            }
            participants.append("{\"name\":\"P").append(i).append("\",\"lat\":6.2,\"lng\":-75.5}");
        }
        String body = "{\"participants\":[" + participants + "],\"transportMode\":\"driving\"}";

        mockMvc.perform(post("/api/v1/meetings")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createRejectsUnsupportedTransportModeWith400NamingSupportedModes() throws Exception {
        String body = """
                {
                  "participants": [
                    {"name": "Ana", "lat": 6.24, "lng": -75.58},
                    {"name": "Bruno", "lat": 6.25, "lng": -75.56}
                  ],
                  "transportMode": "flying"
                }
                """;

        mockMvc.perform(post("/api/v1/meetings")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("driving"),
                                org.hamcrest.Matchers.containsString("walking"))));
    }

    @Test
    void createRejectsOutOfRangeCoordinateWith400() throws Exception {
        String body = """
                {
                  "participants": [
                    {"name": "Ana", "lat": 200.0, "lng": -75.58},
                    {"name": "Bruno", "lat": 6.25, "lng": -75.56}
                  ],
                  "transportMode": "driving"
                }
                """;

        mockMvc.perform(post("/api/v1/meetings")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void computeReturns200WithThreeStrategiesOnSuccess() throws Exception {
        seedMeeting("ABC12345");
        StrategyResult result = sampleResult();
        engine.willReturn(RecommendationOutcome.Success.of(
                new StrategyResults(result, result, result)));

        mockMvc.perform(post("/api/v1/meetings/ABC12345/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.fastest.point.lat").value(6.24))
                .andExpect(jsonPath("$.results.minimax.maxTime").value(12))
                .andExpect(jsonPath("$.results.fairest.perParticipant.p1").value(10))
                .andExpect(jsonPath("$.outlierTradeoff").doesNotExist());
    }

    @Test
    void computeReturns422WithActionableMessageOnRoutingFailure() throws Exception {
        seedMeeting("ABC12345");
        List<RoutingError> errors = new ArrayList<>();
        errors.add(new RoutingError(new ParticipantId("p2"), new Coordinate(6.25, -75.56),
                "location is outside the routing coverage area"));
        engine.willReturn(new RecommendationOutcome.RoutingFailure(errors));

        mockMvc.perform(post("/api/v1/meetings/ABC12345/recommendations"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ROUTING_FAILURE"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("correct or remove")))
                .andExpect(jsonPath("$.errors[0].participantId").value("p2"))
                .andExpect(jsonPath("$.errors[0].location.lat").value(6.25))
                .andExpect(jsonPath("$.errors[0].reason").value(
                        "location is outside the routing coverage area"));
    }

    @Test
    void computeReturns404WhenMeetingMissing() throws Exception {
        mockMvc.perform(post("/api/v1/meetings/MISSING0/recommendations"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

}
