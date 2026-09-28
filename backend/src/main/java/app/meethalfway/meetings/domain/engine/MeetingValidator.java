package app.meethalfway.meetings.domain.engine;

import app.meethalfway.shared.domain.Coordinate;
import app.meethalfway.meetings.domain.model.EngineConfig;
import app.meethalfway.meetings.domain.model.MeetingInput;
import app.meethalfway.meetings.domain.model.ParticipantInput;
import app.meethalfway.meetings.domain.model.ServiceBounds;

/**
 * Framework-free domain validator that decides whether a {@link MeetingInput} is
 * acceptable for the configured service region (Requirements 1.6, 1.7).
 *
 * <p>Two layers of coordinate validation exist, split by responsibility:
 * <ul>
 *   <li><b>Coordinate range (Requirement 1.6)</b> — latitude in {@code [-90, 90]}
 *       and longitude in {@code [-180, 180]} — is a universal, city-agnostic
 *       invariant enforced at {@link Coordinate} construction, so an
 *       out-of-range coordinate can never reach this validator.</li>
 *   <li><b>Service bounds (Requirement 1.7)</b> — whether a location falls inside
 *       the configured {@link ServiceBounds} — is city-specific and therefore
 *       config-driven ({@link EngineConfig#serviceBounds()}). It is checked here
 *       rather than on the value object so the served region can change without
 *       touching the optimization logic (Requirements 5.4, 11.5).</li>
 * </ul>
 *
 * <p>When a location falls outside the service bounds the meeting is rejected and
 * the offending participant is identified, so the web adapter can surface an
 * actionable error and the Creator can correct or remove that specific location.
 * The first out-of-bounds participant encountered (in list order) is reported.
 */
public final class MeetingValidator {

    /**
     * Validates that every participant location in the meeting falls within the
     * configured service bounds.
     *
     * @param meeting the meeting to validate; must not be null. Its coordinates
     *                are already range-valid by construction (Requirement 1.6).
     * @param config  the engine configuration carrying the service bounds; must
     *                not be null (Requirement 1.7)
     * @throws IllegalArgumentException if {@code meeting} or {@code config} is
     *                                  null
     * @throws OutOfServiceBoundsException if any participant location falls
     *                                     outside {@code config.serviceBounds()};
     *                                     the exception identifies the offending
     *                                     participant
     */
    public void validate(MeetingInput meeting, EngineConfig config) {
        if (meeting == null) {
            throw new IllegalArgumentException("meeting must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        ServiceBounds bounds = config.serviceBounds();
        for (ParticipantInput participant : meeting.participants()) {
            Coordinate location = participant.location();
            if (!bounds.contains(location)) {
                throw new OutOfServiceBoundsException(participant, bounds);
            }
        }
    }

    /**
     * Signals that a participant location falls outside the configured service
     * bounds (Requirement 1.7). Carries the offending participant so the failure
     * can be traced to the exact location the Creator must correct or remove.
     */
    public static final class OutOfServiceBoundsException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        private final transient ParticipantInput offendingParticipant;

        OutOfServiceBoundsException(ParticipantInput offendingParticipant, ServiceBounds bounds) {
            super(buildMessage(offendingParticipant, bounds));
            this.offendingParticipant = offendingParticipant;
        }

        private static String buildMessage(ParticipantInput participant, ServiceBounds bounds) {
            Coordinate location = participant.location();
            return "participant '" + participant.id().value()
                    + "' location (lat=" + location.lat() + ", lng=" + location.lng()
                    + ") is outside the configured service bounds [lat "
                    + bounds.minLat() + ".." + bounds.maxLat() + ", lng "
                    + bounds.minLng() + ".." + bounds.maxLng() + "]";
        }

        /**
         * @return the participant whose location fell outside the service bounds
         */
        public ParticipantInput offendingParticipant() {
            return offendingParticipant;
        }
    }
}
