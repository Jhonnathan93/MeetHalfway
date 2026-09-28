package app.meethalfway.meetings.web.support;

import app.meethalfway.meetings.domain.model.Meeting;
import app.meethalfway.meetings.domain.model.MeetingRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Map-backed fake of the {@link MeetingRepository} port for web-adapter tests.
 *
 * <p>The application use cases are {@code final} classes and (under JDK 25 with
 * Spring 3.3.5's Byte Buddy) cannot be mocked; instead they are constructed for
 * real against this in-memory port so controller tests exercise the true wiring
 * offline. Keyed by {@code urlCode} with create-or-update-in-place semantics.
 */
public final class TestMeetingRepository implements MeetingRepository {

    private final Map<String, Meeting> byUrlCode = new LinkedHashMap<>();

    @Override
    public Meeting save(Meeting meeting) {
        byUrlCode.put(meeting.urlCode(), meeting);
        return meeting;
    }

    @Override
    public Optional<Meeting> findByUrlCode(String urlCode) {
        return Optional.ofNullable(byUrlCode.get(urlCode));
    }

    @Override
    public void deleteByUrlCode(String urlCode) {
        byUrlCode.remove(urlCode);
    }

    /**
     * Seeds a meeting directly, bypassing the use cases.
     *
     * @param meeting the meeting to store
     */
    public void seed(Meeting meeting) {
        byUrlCode.put(meeting.urlCode(), meeting);
    }

    /**
     * @param urlCode the code to check
     * @return whether a meeting exists for the code
     */
    public boolean contains(String urlCode) {
        return byUrlCode.containsKey(urlCode);
    }
}
