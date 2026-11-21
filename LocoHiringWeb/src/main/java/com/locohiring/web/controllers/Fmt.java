package com.locohiring.web.controllers;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Date formatting for templates (as {@code @fmt}) and parsing of form inputs. Everything is shown in the web
 * server's time zone (TZ, Australia/Sydney in the containers).
 */
@Component("fmt")
public class Fmt {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy");
    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public String dateTime(OffsetDateTime time) {
        return time == null ? "" : local(time).format(DATE_TIME);
    }

    public String date(LocalDate date) {
        return date == null ? "" : date.format(DATE);
    }

    /** A one-hour slot as offered to a candidate, e.g. "Mon 23 Nov 2026, 10:00–11:00". */
    public String slot(OffsetDateTime start) {
        return start == null ? "" : dateTime(start) + "–" + local(start).plusHours(1).format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    /** Value for an {@code <input type="datetime-local">}. */
    public String input(OffsetDateTime time) {
        return time == null ? "" : local(time).format(INPUT);
    }

    public boolean isPast(OffsetDateTime time) {
        return time != null && time.isBefore(OffsetDateTime.now());
    }

    /** Parses the value of an {@code <input type="datetime-local">}. */
    public static OffsetDateTime parse(String value) {
        return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private static LocalDateTime local(OffsetDateTime time) {
        return time.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
