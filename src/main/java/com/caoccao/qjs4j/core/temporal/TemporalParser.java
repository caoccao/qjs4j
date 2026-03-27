/*
 * Copyright (c) 2025-2026. caoccao.com Sam Cao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.caoccao.qjs4j.core.temporal;

import com.caoccao.qjs4j.core.JSContext;

/**
 * ISO 8601 string parser for Temporal types.
 */
public final class TemporalParser {

    private final String input;
    private int pos;

    public TemporalParser(String input) {
        this.input = input;
        this.pos = 0;
    }

    /**
     * Parse an ISO date string into an IsoDate.
     * Returns null and sets pending exception on error.
     */
    public static IsoDate parseDateString(JSContext context, String input) {
        if (input == null || input.isEmpty()) {
            context.throwRangeError("Temporal error: Invalid character while parsing year value.");
            return null;
        }
        TemporalParser parser = new TemporalParser(input);
        IsoDate date = parser.parseDate(context);
        if (date == null) {
            return null;
        }
        // Consume optional time part
        if (parser.pos < parser.input.length() && (parser.current() == 'T' || parser.current() == 't' || parser.current() == ' ')) {
            parser.pos++;
            parser.parseTime(context);
            if (context.hasPendingException()) {
                return null;
            }
        }
        // Consume optional offset and annotations
        parser.parseOffsetAndAnnotations();
        return date;
    }

    /**
     * Parse an ISO date-time string.
     * Returns null and sets pending exception on error.
     */
    public static ParsedDateTime parseDateTimeString(JSContext context, String input) {
        if (input == null || input.isEmpty()) {
            context.throwRangeError("Temporal error: Invalid character while parsing year value.");
            return null;
        }
        TemporalParser parser = new TemporalParser(input);
        IsoDate date = parser.parseDate(context);
        if (date == null) {
            return null;
        }
        IsoTime time = IsoTime.MIDNIGHT;
        if (parser.pos < parser.input.length() && (parser.current() == 'T' || parser.current() == 't' || parser.current() == ' ')) {
            parser.pos++;
            time = parser.parseTime(context);
            if (time == null) {
                return null;
            }
        }
        // Parse optional calendar annotation
        String calendar = "iso8601";
        parser.parseOffsetAndAnnotations();
        // Check for calendar annotation — simple extraction
        int calIdx = input.indexOf("[u-ca=");
        if (calIdx >= 0) {
            int endIdx = input.indexOf(']', calIdx);
            if (endIdx > calIdx) {
                calendar = input.substring(calIdx + 6, endIdx).toLowerCase(java.util.Locale.ROOT);
            }
        }
        return new ParsedDateTime(date, time, calendar);
    }

    /**
     * Parse a duration string like "P1Y2M3DT4H5M6S".
     */
    public static DurationFields parseDurationString(JSContext context, String input) {
        if (input == null || input.isEmpty()) {
            context.throwRangeError("Temporal error: Invalid duration string.");
            return null;
        }
        TemporalParser parser = new TemporalParser(input);
        return parser.parseDuration(context);
    }

    /**
     * Parse an Instant string like "1970-01-01T00:00:00Z" or "1970-01-01T00:00:00+00:00".
     * Returns null and sets pending exception on error.
     */
    public static ParsedInstant parseInstantString(JSContext context, String input) {
        if (input == null || input.isEmpty()) {
            context.throwRangeError("Temporal error: Invalid character while parsing year value.");
            return null;
        }
        TemporalParser parser = new TemporalParser(input);
        IsoDate date = parser.parseDate(context);
        if (date == null) return null;
        IsoTime time = IsoTime.MIDNIGHT;
        if (parser.pos < parser.input.length() && (parser.current() == 'T' || parser.current() == 't' || parser.current() == ' ')) {
            parser.pos++;
            time = parser.parseTime(context);
            if (time == null) return null;
        }
        // Parse offset (required for Instant)
        int offsetSeconds = parser.parseOffset(context);
        if (context.hasPendingException()) return null;
        // Skip annotations
        parser.parseAnnotations();
        return new ParsedInstant(date, time, offsetSeconds);
    }

    /**
     * Parse a month-day string like "--03-15" or "--0315".
     * Returns null and sets pending exception on error.
     */
    public static IsoDate parseMonthDayString(JSContext context, String input) {
        if (input == null || input.isEmpty()) {
            context.throwRangeError("Temporal error: Invalid character while parsing month value.");
            return null;
        }
        // Try "--MM-DD" format first
        if (input.startsWith("--")) {
            TemporalParser parser = new TemporalParser(input);
            parser.pos = 2; // skip "--"
            int month = parser.parseTwoDigits(context, "month");
            if (context.hasPendingException()) return null;
            if (parser.pos < parser.input.length() && parser.input.charAt(parser.pos) == '-') {
                parser.pos++;
            }
            int day = parser.parseTwoDigits(context, "day");
            if (context.hasPendingException()) return null;
            // Reference year for MonthDay is 1972 (a leap year, so Feb 29 is valid)
            if (!IsoDate.isValidIsoDate(1972, month, day)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            return new IsoDate(1972, month, day);
        }
        // Try full date format (YYYY-MM-DD)
        IsoDate date = parseDateString(context, input);
        if (date == null) return null;
        return new IsoDate(1972, date.month(), date.day());
    }

    /**
     * Parse an ISO time string into an IsoTime.
     * Returns null and sets pending exception on error.
     */
    public static IsoTime parseTimeString(JSContext context, String input) {
        if (input == null || input.isEmpty()) {
            context.throwRangeError("Temporal error: Invalid time");
            return null;
        }
        TemporalParser parser = new TemporalParser(input);
        // Try to parse as time directly
        IsoTime time = parser.parseTime(context);
        return time;
    }

    /**
     * Parse a year-month string like "2024-03".
     * Returns null and sets pending exception on error.
     */
    public static IsoDate parseYearMonthString(JSContext context, String input) {
        if (input == null || input.isEmpty()) {
            context.throwRangeError("Temporal error: Invalid character while parsing year value.");
            return null;
        }
        TemporalParser parser = new TemporalParser(input);
        int year = parser.parseYear(context);
        if (context.hasPendingException()) return null;
        if (parser.pos < parser.input.length() && parser.input.charAt(parser.pos) == '-') {
            parser.pos++;
        }
        int month = parser.parseTwoDigits(context, "month");
        if (context.hasPendingException()) return null;
        if (month < 1 || month > 12) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        // If there's a day part, parse it (full date string)
        int day = 1;
        if (parser.pos < parser.input.length() && (parser.input.charAt(parser.pos) == '-' || Character.isDigit(parser.input.charAt(parser.pos)))) {
            if (parser.input.charAt(parser.pos) == '-') {
                parser.pos++;
            }
            day = parser.parseTwoDigits(context, "day");
            if (context.hasPendingException()) return null;
        }
        if (!IsoDate.isValidIsoDate(year, month, day)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return new IsoDate(year, month, day);
    }

    /**
     * Parse a ZonedDateTime string like "2024-01-15T12:00:00+00:00[UTC]".
     * Returns null and sets pending exception on error.
     */
    public static ParsedZonedDateTime parseZonedDateTimeString(JSContext context, String input) {
        if (input == null || input.isEmpty()) {
            context.throwRangeError("Temporal error: Invalid character while parsing year value.");
            return null;
        }
        TemporalParser parser = new TemporalParser(input);
        IsoDate date = parser.parseDate(context);
        if (date == null) return null;
        IsoTime time = IsoTime.MIDNIGHT;
        if (parser.pos < parser.input.length() && (parser.current() == 'T' || parser.current() == 't' || parser.current() == ' ')) {
            parser.pos++;
            time = parser.parseTime(context);
            if (time == null) return null;
        }
        // Parse offset
        int offsetSeconds = 0;
        if (parser.pos < parser.input.length()) {
            char c = parser.input.charAt(parser.pos);
            if (c == 'Z' || c == 'z' || c == '+' || c == '-' || c == '\u2212') {
                offsetSeconds = parser.parseOffset(context);
                if (context.hasPendingException()) return null;
            }
        }
        // Parse timezone annotation (required)
        String timeZoneId = null;
        String calendarId = "iso8601";
        while (parser.pos < parser.input.length() && parser.input.charAt(parser.pos) == '[') {
            int start = parser.pos + 1;
            // Skip '!' if present
            if (start < parser.input.length() && parser.input.charAt(start) == '!') {
                start++;
            }
            int end = parser.input.indexOf(']', parser.pos);
            if (end < 0) break;
            String content = parser.input.substring(start, end);
            if (content.startsWith("u-ca=")) {
                calendarId = content.substring(5).toLowerCase(java.util.Locale.ROOT);
            } else if (timeZoneId == null) {
                timeZoneId = content;
            }
            parser.pos = end + 1;
        }
        if (timeZoneId == null) {
            context.throwRangeError("Temporal error: Must specify time zone.");
            return null;
        }
        return new ParsedZonedDateTime(date, time, offsetSeconds, timeZoneId, calendarId);
    }

    private char current() {
        return input.charAt(pos);
    }

    private void parseAnnotations() {
        while (pos < input.length() && input.charAt(pos) == '[') {
            int end = input.indexOf(']', pos);
            if (end < 0) break;
            pos = end + 1;
        }
    }

    private IsoDate parseDate(JSContext context) {
        int year = parseYear(context);
        if (context.hasPendingException()) {
            return null;
        }
        // Check for separator
        boolean hasSep = pos < input.length() && input.charAt(pos) == '-';
        if (hasSep) {
            pos++;
        }
        int month = parseTwoDigits(context, "month");
        if (context.hasPendingException()) {
            return null;
        }
        if (hasSep && pos < input.length() && input.charAt(pos) == '-') {
            pos++;
        }
        int day = parseTwoDigits(context, "day");
        if (context.hasPendingException()) {
            return null;
        }
        if (!IsoDate.isValidIsoDate(year, month, day)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return new IsoDate(year, month, day);
    }

    private DurationFields parseDuration(JSContext context) {
        boolean negative = false;
        if (pos < input.length() && (input.charAt(pos) == '-' || input.charAt(pos) == '\u2212')) {
            negative = true;
            pos++;
        } else if (pos < input.length() && input.charAt(pos) == '+') {
            pos++;
        }

        if (pos >= input.length() || (input.charAt(pos) != 'P' && input.charAt(pos) != 'p')) {
            context.throwRangeError("Temporal error: Invalid duration string.");
            return null;
        }
        pos++;

        long years = 0, months = 0, weeks = 0, days = 0;
        long hours = 0, minutes = 0, seconds = 0;
        long milliseconds = 0, microseconds = 0, nanoseconds = 0;
        boolean inTimePart = false;

        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == 'T' || c == 't') {
                inTimePart = true;
                pos++;
                continue;
            }
            if (!Character.isDigit(c)) {
                break;
            }
            // Parse number
            long number = 0;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                number = number * 10 + (input.charAt(pos) - '0');
                pos++;
            }
            // Parse optional fractional part
            long fractionalNs = 0;
            boolean hasFraction = false;
            if (pos < input.length() && (input.charAt(pos) == '.' || input.charAt(pos) == ',')) {
                hasFraction = true;
                pos++;
                int fracStart = pos;
                while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                    pos++;
                }
                String fracStr = input.substring(fracStart, pos);
                while (fracStr.length() < 9) {
                    fracStr += "0";
                }
                if (fracStr.length() > 9) {
                    fracStr = fracStr.substring(0, 9);
                }
                fractionalNs = Long.parseLong(fracStr);
            }

            if (pos >= input.length()) {
                context.throwRangeError("Temporal error: Invalid duration string.");
                return null;
            }
            char unit = input.charAt(pos);
            pos++;

            if (!inTimePart) {
                switch (unit) {
                    case 'Y', 'y' -> years = number;
                    case 'M', 'm' -> months = number;
                    case 'W', 'w' -> weeks = number;
                    case 'D', 'd' -> days = number;
                    default -> {
                        context.throwRangeError("Temporal error: Invalid duration string.");
                        return null;
                    }
                }
            } else {
                switch (unit) {
                    case 'H', 'h' -> {
                        hours = number;
                        if (hasFraction) {
                            // Convert fractional hours to sub-components
                            long totalMinuteNs = fractionalNs * 3600;
                            minutes = totalMinuteNs / 1_000_000_000;
                            totalMinuteNs %= 1_000_000_000;
                            seconds = totalMinuteNs / (1_000_000_000 / 60);
                        }
                    }
                    case 'M', 'm' -> {
                        minutes = number;
                        if (hasFraction) {
                            long totalSecNs = fractionalNs * 60;
                            seconds = totalSecNs / 1_000_000_000;
                            totalSecNs %= 1_000_000_000;
                            milliseconds = totalSecNs / 1_000_000;
                            totalSecNs %= 1_000_000;
                            microseconds = totalSecNs / 1_000;
                            nanoseconds = totalSecNs % 1_000;
                        }
                    }
                    case 'S', 's' -> {
                        seconds = number;
                        if (hasFraction) {
                            milliseconds = fractionalNs / 1_000_000;
                            fractionalNs %= 1_000_000;
                            microseconds = fractionalNs / 1_000;
                            nanoseconds = fractionalNs % 1_000;
                        }
                    }
                    default -> {
                        context.throwRangeError("Temporal error: Invalid duration string.");
                        return null;
                    }
                }
            }
        }

        int sign = negative ? -1 : 1;
        return new DurationFields(
                years * sign, months * sign, weeks * sign, days * sign,
                hours * sign, minutes * sign, seconds * sign,
                milliseconds * sign, microseconds * sign, nanoseconds * sign
        );
    }

    private int parseOffset(JSContext context) {
        if (pos >= input.length()) {
            context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
            return 0;
        }
        char c = input.charAt(pos);
        if (c == 'Z' || c == 'z') {
            pos++;
            return 0;
        }
        int sign;
        if (c == '+') {
            sign = 1;
        } else if (c == '-' || c == '\u2212') {
            sign = -1;
        } else {
            context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
            return 0;
        }
        pos++;
        int hours = parseTwoDigits(context, "offset hour");
        if (context.hasPendingException()) return 0;
        int minutes = 0;
        if (pos < input.length() && input.charAt(pos) == ':') {
            pos++;
        }
        if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            minutes = parseTwoDigits(context, "offset minute");
            if (context.hasPendingException()) return 0;
        }
        return sign * (hours * 3600 + minutes * 60);
    }

    private void parseOffsetAndAnnotations() {
        // Skip offset (+HH:MM, -HH:MM, Z)
        if (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == 'Z' || c == 'z') {
                pos++;
            } else if (c == '+' || c == '-' || c == '\u2212') {
                pos++;
                // Skip offset digits
                while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == ':')) {
                    pos++;
                }
            }
        }
        // Skip annotations [...]
        while (pos < input.length() && input.charAt(pos) == '[') {
            int end = input.indexOf(']', pos);
            if (end < 0) {
                break;
            }
            pos = end + 1;
        }
    }

    IsoTime parseTime(JSContext context) {
        int hour = parseTwoDigits(context, "hour");
        if (context.hasPendingException()) {
            return null;
        }
        boolean hasSep = pos < input.length() && input.charAt(pos) == ':';
        if (hasSep) {
            pos++;
        }
        int minute = 0;
        int second = 0;
        int millisecond = 0;
        int microsecond = 0;
        int nanosecond = 0;

        if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            minute = parseTwoDigits(context, "minute");
            if (context.hasPendingException()) {
                return null;
            }
            if (hasSep && pos < input.length() && input.charAt(pos) == ':') {
                pos++;
            }
            if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                second = parseTwoDigits(context, "second");
                if (context.hasPendingException()) {
                    return null;
                }
                // Parse fractional seconds
                if (pos < input.length() && (input.charAt(pos) == '.' || input.charAt(pos) == ',')) {
                    pos++;
                    int fracStart = pos;
                    while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                        pos++;
                    }
                    String fracStr = input.substring(fracStart, pos);
                    // Pad to 9 digits
                    while (fracStr.length() < 9) {
                        fracStr += "0";
                    }
                    if (fracStr.length() > 9) {
                        fracStr = fracStr.substring(0, 9);
                    }
                    millisecond = Integer.parseInt(fracStr.substring(0, 3));
                    microsecond = Integer.parseInt(fracStr.substring(3, 6));
                    nanosecond = Integer.parseInt(fracStr.substring(6, 9));
                }
            }
        }

        if (!IsoTime.isValidTime(hour, minute, second, millisecond, microsecond, nanosecond)) {
            context.throwRangeError("Temporal error: Invalid time");
            return null;
        }
        return new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
    }

    private int parseTwoDigits(JSContext context, String fieldName) {
        if (pos + 2 > input.length()) {
            context.throwRangeError("Temporal error: Invalid character while parsing " + fieldName + " value.");
            return 0;
        }
        char c1 = input.charAt(pos);
        char c2 = input.charAt(pos + 1);
        if (!Character.isDigit(c1) || !Character.isDigit(c2)) {
            context.throwRangeError("Temporal error: Invalid character while parsing " + fieldName + " value.");
            return 0;
        }
        pos += 2;
        return (c1 - '0') * 10 + (c2 - '0');
    }

    private int parseYear(JSContext context) {
        if (pos >= input.length()) {
            context.throwRangeError("Temporal error: Invalid character while parsing year value.");
            return 0;
        }
        char c = input.charAt(pos);
        if (c == '+' || c == '-' || c == '\u2212') {
            // Extended year: +YYYYYY or -YYYYYY
            boolean negative = (c == '-' || c == '\u2212');
            pos++;
            int startPos = pos;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
            int digitCount = pos - startPos;
            if (digitCount < 4) {
                context.throwRangeError("Temporal error: Invalid character while parsing year value.");
                return 0;
            }
            int year = Integer.parseInt(input.substring(startPos, pos));
            return negative ? -year : year;
        }

        if (!Character.isDigit(c)) {
            context.throwRangeError("Temporal error: Invalid character while parsing year value.");
            return 0;
        }
        // Regular 4-digit year
        if (pos + 4 > input.length()) {
            context.throwRangeError("Temporal error: Invalid character while parsing year value.");
            return 0;
        }
        String yearStr = input.substring(pos, pos + 4);
        for (int i = 0; i < 4; i++) {
            if (!Character.isDigit(yearStr.charAt(i))) {
                context.throwRangeError("Temporal error: Invalid character while parsing year value.");
                return 0;
            }
        }
        pos += 4;
        return Integer.parseInt(yearStr);
    }

    public record DurationFields(long years, long months, long weeks, long days,
                                 long hours, long minutes, long seconds,
                                 long milliseconds, long microseconds, long nanoseconds) {
    }

    public record ParsedDateTime(IsoDate date, IsoTime time, String calendar) {
    }

    public record ParsedInstant(IsoDate date, IsoTime time, int offsetSeconds) {
    }

    public record ParsedZonedDateTime(IsoDate date, IsoTime time, int offsetSeconds, String timeZoneId,
                                      String calendarId) {
    }
}
