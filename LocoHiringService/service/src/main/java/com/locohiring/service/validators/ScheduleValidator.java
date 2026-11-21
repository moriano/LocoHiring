package com.locohiring.service.validators;

import com.locohiring.api.model.AvailabilityCreate;
import com.locohiring.service.ApiException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/** Checks times sent by clients. Hours are interpreted in the service's time zone. */
@Component
public class ScheduleValidator {

    private static final int MAX_RANGE_DAYS = 366;

    private final Clock clock;

    public ScheduleValidator(Clock clock) {
        this.clock = clock;
    }

    /** Interviews last one hour, start on the hour and cannot be scheduled in the past. */
    public void validateInterviewTime(OffsetDateTime scheduledAt) {
        validateFuture(scheduledAt, "An interview");
        validateOnTheHour(scheduledAt);
    }

    public void validateOnTheHour(OffsetDateTime scheduledAt) {
        LocalDateTime local = scheduledAt.atZoneSameInstant(clock.getZone()).toLocalDateTime();
        if (local.getMinute() != 0 || local.getSecond() != 0 || local.getNano() != 0) {
            throw ApiException.badRequest("Interviews must start on the hour");
        }
    }

    public void validateFuture(OffsetDateTime time, String what) {
        if (!time.isAfter(OffsetDateTime.now(clock))) {
            throw ApiException.badRequest(what + " cannot be scheduled in the past");
        }
    }

    public void validateAvailability(AvailabilityCreate request) {
        if (request.getStartHour() >= request.getEndHour()) {
            throw ApiException.badRequest("startHour must be before endHour");
        }
        LocalDateTime firstBlock = request.getDate().atTime(request.getStartHour(), 0);
        if (!firstBlock.isAfter(LocalDateTime.now(clock))) {
            throw ApiException.badRequest("Availability cannot start in the past");
        }
    }

    public void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw ApiException.badRequest("from must not be after to");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw ApiException.badRequest("The range cannot exceed " + MAX_RANGE_DAYS + " days");
        }
    }
}
