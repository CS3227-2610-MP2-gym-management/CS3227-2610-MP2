package com.gymflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.gymflow.model.Workout;
import org.junit.jupiter.api.Test;

class MemberWorkoutsViewTest {
    @Test
    void groupsSessionsByTheirRecordedDateAndOrdersEachDateEarliestFirst() {
        LocalDate date = LocalDate.of(2026, 9, 24);
        Workout latest = workout(3, date, 18, 0);
        Workout otherDate = workout(2, date.minusDays(1), 12, 0);
        Workout earliest = workout(1, date, 6, 30);

        Map<LocalDate, List<Workout>> grouped = MemberWorkoutsView.workoutsByDate(
                List.of(latest, otherDate, earliest));

        assertEquals(List.of(otherDate), grouped.get(date.minusDays(1)));
        assertEquals(List.of(earliest, latest), grouped.get(date));
    }

    @Test
    void keepsAnOvernightWorkoutOnItsSelectedEndDate() {
        LocalDate date = LocalDate.of(2026, 9, 24);
        Workout overnight = new Workout(1, 1, instant(date.minusDays(1), 23, 0),
                instant(date, 1, 0), null, Instant.EPOCH, Instant.EPOCH, List.of());

        Map<LocalDate, List<Workout>> grouped = MemberWorkoutsView.workoutsByDate(List.of(overnight));

        assertEquals(List.of(overnight), grouped.get(date));
    }

    private static Workout workout(long id, LocalDate date, int hour, int minute) {
        Instant time = instant(date, hour, minute);
        return new Workout(id, 1, time, time.plusSeconds(3_600), null, Instant.EPOCH, Instant.EPOCH, List.of());
    }

    private static Instant instant(LocalDate date, int hour, int minute) {
        return LocalDateTime.of(date.getYear(), date.getMonth(), date.getDayOfMonth(), hour, minute)
                .atZone(ZoneId.systemDefault()).toInstant();
    }
}
