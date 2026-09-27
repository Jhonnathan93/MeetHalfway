package app.meethalfway.application;

import app.meethalfway.domain.model.Meeting;
import app.meethalfway.domain.port.MeetingRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A Map-backed fake of the domain {@link MeetingRepository} port for use-case
 * unit tests. It keys meetings by {@code urlCode} and models
 * create-or-update-in-place semantics, so the application use cases can be tested
 * offline with no persistence infrastructure.
 */
final class InMemoryMeetingRepository implements MeetingRepository {

    private final Map<String, Meeting> byUrlCode = new LinkedHashMap<>();
    private int saveCount;

    @Override
    public Meeting save(Meeting meeting) {
        byUrlCode.put(meeting.urlCode(), meeting);
        saveCount++;
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

    int size() {
        return byUrlCode.size();
    }

    int saveCount() {
        return saveCount;
    }

    void seed(Meeting meeting) {
        byUrlCode.put(meeting.urlCode(), meeting);
    }
}
