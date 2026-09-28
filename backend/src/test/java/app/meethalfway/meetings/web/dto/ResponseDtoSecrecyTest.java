package app.meethalfway.meetings.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import app.meethalfway.locations.web.dto.AddressSuggestionResponse;
import app.meethalfway.meetings.web.dto.MeetingResponse;
import app.meethalfway.meetings.web.dto.OutlierTradeoffResponse;
import app.meethalfway.meetings.web.dto.ParticipantResponse;
import app.meethalfway.meetings.web.dto.RecommendationResponse;
import app.meethalfway.meetings.web.dto.RoutingErrorResponse;
import app.meethalfway.meetings.web.dto.RoutingFailureResponse;
import app.meethalfway.meetings.web.dto.StrategyResultResponse;
import app.meethalfway.meetings.web.dto.StrategyResultsResponse;
import app.meethalfway.shared.web.dto.CoordinateResponse;
import app.meethalfway.shared.web.dto.ErrorResponse;
import app.meethalfway.shared.web.dto.FieldErrorResponse;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Structural secrecy test for the web response DTOs (Task 12.5, Requirement
 * 11.4). Confirms no response DTO carries a field whose name suggests a provider
 * key or secret, so a credential can never be serialized to a client by
 * construction.
 */
class ResponseDtoSecrecyTest {

    private static final List<Class<?>> RESPONSE_DTOS = List.of(
            MeetingResponse.class,
            ParticipantResponse.class,
            CoordinateResponse.class,
            RecommendationResponse.class,
            StrategyResultsResponse.class,
            StrategyResultResponse.class,
            OutlierTradeoffResponse.class,
            RoutingFailureResponse.class,
            RoutingErrorResponse.class,
            AddressSuggestionResponse.class,
            ErrorResponse.class,
            FieldErrorResponse.class);

    private static final List<String> FORBIDDEN_FRAGMENTS = List.of(
            "apikey", "secret", "password", "token", "credential", "privatekey");

    @Test
    void noResponseDtoExposesASecretField() {
        for (Class<?> dto : RESPONSE_DTOS) {
            RecordComponent[] components = dto.getRecordComponents();
            assertThat(components)
                    .as("%s should be a record DTO", dto.getSimpleName())
                    .isNotNull();
            for (RecordComponent component : components) {
                String name = component.getName().toLowerCase(Locale.ROOT);
                for (String forbidden : FORBIDDEN_FRAGMENTS) {
                    assertThat(name)
                            .as("%s.%s must not expose a secret", dto.getSimpleName(), component.getName())
                            .doesNotContain(forbidden);
                }
            }
        }
    }
}
