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

import java.math.BigInteger;

/**
 * ISO 8601 string parser for Temporal types.
 */
public final class TemporalParser {
    private static final BigInteger BILLION = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger NS_PER_HOUR = BigInteger.valueOf(3_600_000_000_000L);
    private static final BigInteger NS_PER_MINUTE = BigInteger.valueOf(60_000_000_000L);
    private static final BigInteger NS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);

    private final String input;
    private int pos;

    public TemporalParser(String input) {
        this.input = input;
        this.pos = 0;
    }

    private static long[] decomposeTimeFraction(BigInteger fractionalNanoseconds) {
        BigInteger[] minuteDivision = fractionalNanoseconds.divideAndRemainder(BigInteger.valueOf(60_000_000_000L));
        long minutePortion = minuteDivision[0].longValue();
        BigInteger remainingAfterMinutes = minuteDivision[1];

        BigInteger[] secondDivision = remainingAfterMinutes.divideAndRemainder(BigInteger.valueOf(1_000_000_000L));
        long secondPortion = secondDivision[0].longValue();
        BigInteger remainingAfterSeconds = secondDivision[1];

        BigInteger[] millisecondDivision = remainingAfterSeconds.divideAndRemainder(BigInteger.valueOf(1_000_000L));
        long millisecondPortion = millisecondDivision[0].longValue();
        BigInteger remainingAfterMilliseconds = millisecondDivision[1];

        BigInteger[] microsecondDivision = remainingAfterMilliseconds.divideAndRemainder(BigInteger.valueOf(1_000L));
        long microsecondPortion = microsecondDivision[0].longValue();
        long nanosecondPortion = microsecondDivision[1].longValue();

        return new long[]{
                minutePortion,
                secondPortion,
                millisecondPortion,
                microsecondPortion,
                nanosecondPortion
        };
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
        if (parser.pos != parser.input.length()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
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
        if (parser.pos != parser.input.length()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
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
        if (input.indexOf('\u2212') >= 0) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        TemporalParser parser = new TemporalParser(input);
        IsoDate date = parser.parseDate(context);
        if (date == null) {
            return null;
        }
        if (parser.pos >= parser.input.length()
                || (parser.current() != 'T' && parser.current() != 't' && parser.current() != ' ')) {
            context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
            return null;
        }
        parser.pos++;
        IsoTime time = parser.parseInstantTime(context);
        if (time == null) {
            return null;
        }
        ParsedOffset parsedOffset = parser.parseInstantOffsetNanoseconds(context);
        if (parsedOffset == null || context.hasPendingException()) {
            return null;
        }
        parser.parseInstantAnnotations(context);
        if (context.hasPendingException()) {
            return null;
        }
        if (parser.pos != parser.input.length()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return new ParsedInstant(date, time, parsedOffset.totalSeconds(), parsedOffset.totalNanoseconds());
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
        if (parser.pos != parser.input.length()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return new ParsedZonedDateTime(date, time, offsetSeconds, timeZoneId, calendarId);
    }

    private char current() {
        return input.charAt(pos);
    }

    private boolean hasTwoDigits(int index) {
        return hasTwoDigits(input, index);
    }

    private boolean hasTwoDigits(String value, int index) {
        return index + 2 <= value.length()
                && Character.isDigit(value.charAt(index))
                && Character.isDigit(value.charAt(index + 1));
    }

    private boolean isLowercaseAnnotationKey(String key) {
        if (key.isEmpty()) {
            return false;
        }
        for (int index = 0; index < key.length(); index++) {
            char character = key.charAt(index);
            if (character >= 'A' && character <= 'Z') {
                return false;
            }
            if (!Character.isLetterOrDigit(character) && character != '-' && character != '_') {
                return false;
            }
        }
        return true;
    }

    private boolean isMinutePrecisionOffsetTimeZoneAnnotation(String value) {
        if (value.length() < 3) {
            return false;
        }
        char sign = value.charAt(0);
        if (sign != '+' && sign != '-') {
            return false;
        }
        int index = 1;
        if (!hasTwoDigits(value, index)) {
            return false;
        }
        int hours = parseTwoDigitNumber(value, index);
        if (hours > 23) {
            return false;
        }
        index += 2;
        if (index == value.length()) {
            return true;
        }

        boolean extendedFormat;
        if (value.charAt(index) == ':') {
            extendedFormat = true;
            index++;
        } else if (Character.isDigit(value.charAt(index))) {
            extendedFormat = false;
        } else {
            return false;
        }

        if (index + 2 > value.length()
                || !Character.isDigit(value.charAt(index))
                || !Character.isDigit(value.charAt(index + 1))) {
            return false;
        }
        int minutes = parseTwoDigitNumber(value, index);
        if (minutes > 59) {
            return false;
        }
        index += 2;
        if (index == value.length()) {
            return true;
        }

        if (extendedFormat) {
            if (value.charAt(index) != ':') {
                return false;
            }
        } else if (!Character.isDigit(value.charAt(index))) {
            return false;
        }
        return false;
    }

    private boolean isOffsetTimeZoneAnnotation(String annotationValue) {
        return !annotationValue.isEmpty()
                && (annotationValue.charAt(0) == '+' || annotationValue.charAt(0) == '-');
    }

    private void parseAnnotations() {
        while (pos < input.length() && input.charAt(pos) == '[') {
            int end = input.indexOf(']', pos);
            if (end < 0) {
                break;
            }
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
        boolean hasComponent = false;
        boolean hasDateComponent = false;
        boolean hasTimeComponent = false;
        boolean hasFractionalTimeComponent = false;
        int lastDateUnitOrder = -1;
        int lastTimeUnitOrder = -1;

        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == 'T' || c == 't') {
                if (inTimePart) {
                    context.throwRangeError("Temporal error: Invalid duration string.");
                    return null;
                }
                inTimePart = true;
                pos++;
                continue;
            }
            if (!Character.isDigit(c)) {
                context.throwRangeError("Temporal error: Invalid duration string.");
                return null;
            }
            // Parse number
            int numberStart = pos;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
            BigInteger numberBigInteger = new BigInteger(input.substring(numberStart, pos));
            long number = toLongWithRangeCheck(context, numberBigInteger);
            if (context.hasPendingException()) {
                return null;
            }
            // Parse optional fractional part
            BigInteger fractionalNanoseconds = BigInteger.ZERO;
            boolean hasFraction = false;
            if (pos < input.length() && (input.charAt(pos) == '.' || input.charAt(pos) == ',')) {
                hasFraction = true;
                pos++;
                int fracStart = pos;
                while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                    pos++;
                }
                if (fracStart == pos) {
                    context.throwRangeError("Temporal error: Invalid duration string.");
                    return null;
                }
                String fracStr = input.substring(fracStart, pos);
                if (fracStr.length() > 9) {
                    context.throwRangeError("Temporal error: Invalid duration string.");
                    return null;
                }
                while (fracStr.length() < 9) {
                    fracStr += "0";
                }
                fractionalNanoseconds = new BigInteger(fracStr);
            }

            if (pos >= input.length()) {
                context.throwRangeError("Temporal error: Invalid duration string.");
                return null;
            }
            char unit = input.charAt(pos);
            pos++;

            if (!inTimePart) {
                if (hasFraction) {
                    context.throwRangeError("Temporal error: Invalid duration string.");
                    return null;
                }
                int currentDateUnitOrder = switch (unit) {
                    case 'Y', 'y' -> 0;
                    case 'M', 'm' -> 1;
                    case 'W', 'w' -> 2;
                    case 'D', 'd' -> 3;
                    default -> -1;
                };
                if (currentDateUnitOrder < 0 || currentDateUnitOrder <= lastDateUnitOrder) {
                    context.throwRangeError("Temporal error: Invalid duration string.");
                    return null;
                }
                lastDateUnitOrder = currentDateUnitOrder;
                hasDateComponent = true;
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
                int currentTimeUnitOrder = switch (unit) {
                    case 'H', 'h' -> 0;
                    case 'M', 'm' -> 1;
                    case 'S', 's' -> 2;
                    default -> -1;
                };
                if (currentTimeUnitOrder < 0 || currentTimeUnitOrder <= lastTimeUnitOrder || hasFractionalTimeComponent) {
                    context.throwRangeError("Temporal error: Invalid duration string.");
                    return null;
                }
                lastTimeUnitOrder = currentTimeUnitOrder;
                hasTimeComponent = true;
                switch (unit) {
                    case 'H', 'h' -> {
                        hours = number;
                        if (hasFraction) {
                            hasFractionalTimeComponent = true;
                            BigInteger fractionalHourNanoseconds = fractionalNanoseconds
                                    .multiply(BigInteger.valueOf(3_600_000_000_000L))
                                    .divide(BILLION);
                            long[] decomposition = decomposeTimeFraction(fractionalHourNanoseconds);
                            minutes = decomposition[0];
                            seconds = decomposition[1];
                            milliseconds = decomposition[2];
                            microseconds = decomposition[3];
                            nanoseconds = decomposition[4];
                        }
                    }
                    case 'M', 'm' -> {
                        minutes = number;
                        if (hasFraction) {
                            hasFractionalTimeComponent = true;
                            BigInteger fractionalMinuteNanoseconds = fractionalNanoseconds
                                    .multiply(BigInteger.valueOf(60_000_000_000L))
                                    .divide(BILLION);
                            long[] decomposition = decomposeTimeFraction(fractionalMinuteNanoseconds);
                            seconds = decomposition[1];
                            milliseconds = decomposition[2];
                            microseconds = decomposition[3];
                            nanoseconds = decomposition[4];
                        }
                    }
                    case 'S', 's' -> {
                        seconds = number;
                        if (hasFraction) {
                            hasFractionalTimeComponent = true;
                            BigInteger[] millisecondDivision = fractionalNanoseconds.divideAndRemainder(BigInteger.valueOf(1_000_000L));
                            milliseconds = millisecondDivision[0].longValue();
                            BigInteger remainingFractionalNanoseconds = millisecondDivision[1];
                            BigInteger[] microsecondDivision = remainingFractionalNanoseconds.divideAndRemainder(BigInteger.valueOf(1_000L));
                            microseconds = microsecondDivision[0].longValue();
                            nanoseconds = microsecondDivision[1].longValue();
                        }
                    }
                    default -> {
                        context.throwRangeError("Temporal error: Invalid duration string.");
                        return null;
                    }
                }
            }
            hasComponent = true;
        }

        if (!hasComponent) {
            context.throwRangeError("Temporal error: Invalid duration string.");
            return null;
        }
        if (inTimePart && !hasTimeComponent) {
            context.throwRangeError("Temporal error: Invalid duration string.");
            return null;
        }
        if (!hasDateComponent && !hasTimeComponent) {
            context.throwRangeError("Temporal error: Invalid duration string.");
            return null;
        }

        int sign = negative ? -1 : 1;
        return new DurationFields(
                years * sign, months * sign, weeks * sign, days * sign,
                hours * sign, minutes * sign, seconds * sign,
                milliseconds * sign, microseconds * sign, nanoseconds * sign
        );
    }

    private BigInteger parseFractionalNanoseconds(JSContext context, boolean rejectMoreThanNineDigits) {
        int fractionStart = pos;
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }
        if (fractionStart == pos) {
            context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
            return BigInteger.ZERO;
        }

        String fraction = input.substring(fractionStart, pos);
        if (fraction.length() > 9 && rejectMoreThanNineDigits) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return BigInteger.ZERO;
        }
        if (fraction.length() > 9) {
            fraction = fraction.substring(0, 9);
        } else {
            while (fraction.length() < 9) {
                fraction += "0";
            }
        }
        return new BigInteger(fraction);
    }

    private void parseInstantAnnotations(JSContext context) {
        int timeZoneAnnotationCount = 0;
        int calendarAnnotationCount = 0;
        boolean hasCriticalCalendarAnnotation = false;
        while (pos < input.length() && input.charAt(pos) == '[') {
            pos++;
            boolean critical = false;
            if (pos < input.length() && input.charAt(pos) == '!') {
                critical = true;
                pos++;
            }
            int contentStart = pos;
            while (pos < input.length() && input.charAt(pos) != ']') {
                pos++;
            }
            if (pos >= input.length() || contentStart == pos) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return;
            }
            String content = input.substring(contentStart, pos);
            pos++;

            int equalSignIndex = content.indexOf('=');
            if (equalSignIndex >= 0) {
                String key = content.substring(0, equalSignIndex);
                if (equalSignIndex == content.length() - 1 || !isLowercaseAnnotationKey(key)) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return;
                }
                if ("u-ca".equals(key)) {
                    calendarAnnotationCount++;
                    if (critical) {
                        hasCriticalCalendarAnnotation = true;
                    }
                } else if (critical) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return;
                }
            } else {
                timeZoneAnnotationCount++;
                if (timeZoneAnnotationCount > 1) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return;
                }
                if (isOffsetTimeZoneAnnotation(content)
                        && !isMinutePrecisionOffsetTimeZoneAnnotation(content)) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return;
                }
            }
        }
        if (calendarAnnotationCount > 1 && hasCriticalCalendarAnnotation) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
        }
    }

    private ParsedOffset parseInstantOffsetNanoseconds(JSContext context) {
        if (pos >= input.length()) {
            context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
            return null;
        }
        char signCharacter = input.charAt(pos);
        if (signCharacter == 'Z' || signCharacter == 'z') {
            pos++;
            return new ParsedOffset(0, BigInteger.ZERO);
        }
        int sign;
        if (signCharacter == '+') {
            sign = 1;
        } else if (signCharacter == '-') {
            sign = -1;
        } else {
            context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
            return null;
        }
        pos++;

        int offsetHour = parseTwoDigits(context, "offset hour");
        if (context.hasPendingException()) {
            return null;
        }
        if (offsetHour > 23) {
            context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
            return null;
        }

        int offsetMinute = 0;
        int offsetSecond = 0;
        BigInteger fractionalNanoseconds = BigInteger.ZERO;
        boolean hasMinute = false;
        boolean hasSecond = false;
        boolean extendedFormat = false;

        if (pos < input.length() && input.charAt(pos) == ':') {
            extendedFormat = true;
            pos++;
            hasMinute = true;
            offsetMinute = parseTwoDigits(context, "offset minute");
            if (context.hasPendingException()) {
                return null;
            }
        } else if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            hasMinute = true;
            offsetMinute = parseTwoDigits(context, "offset minute");
            if (context.hasPendingException()) {
                return null;
            }
        }

        if (hasMinute) {
            if (offsetMinute > 59) {
                context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
                return null;
            }
            if (extendedFormat) {
                if (pos < input.length() && input.charAt(pos) == ':') {
                    pos++;
                    hasSecond = true;
                    offsetSecond = parseTwoDigits(context, "offset second");
                    if (context.hasPendingException()) {
                        return null;
                    }
                } else if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                    context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
                    return null;
                }
            } else if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                hasSecond = true;
                offsetSecond = parseTwoDigits(context, "offset second");
                if (context.hasPendingException()) {
                    return null;
                }
            } else if (pos < input.length() && input.charAt(pos) == ':') {
                context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
                return null;
            }
        } else if (pos < input.length() && (input.charAt(pos) == ':' || input.charAt(pos) == '.' || input.charAt(pos) == ',')) {
            context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
            return null;
        }

        if (hasSecond) {
            if (offsetSecond > 59) {
                context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
                return null;
            }
            if (pos < input.length() && (input.charAt(pos) == '.' || input.charAt(pos) == ',')) {
                pos++;
                fractionalNanoseconds = parseFractionalNanoseconds(context, true);
                if (context.hasPendingException()) {
                    return null;
                }
            }
        } else if (pos < input.length() && (input.charAt(pos) == '.' || input.charAt(pos) == ',')) {
            context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
            return null;
        }

        int offsetSeconds = sign * (offsetHour * 3600 + offsetMinute * 60 + offsetSecond);
        BigInteger offsetNanoseconds = BigInteger.valueOf(offsetHour).multiply(NS_PER_HOUR)
                .add(BigInteger.valueOf(offsetMinute).multiply(NS_PER_MINUTE))
                .add(BigInteger.valueOf(offsetSecond).multiply(NS_PER_SECOND))
                .add(fractionalNanoseconds);
        if (sign < 0) {
            offsetNanoseconds = offsetNanoseconds.negate();
        }
        return new ParsedOffset(offsetSeconds, offsetNanoseconds);
    }

    private IsoTime parseInstantTime(JSContext context) {
        int hour = parseTwoDigits(context, "hour");
        if (context.hasPendingException()) {
            return null;
        }
        boolean extendedFormat = pos < input.length() && input.charAt(pos) == ':';
        int minute = 0;
        int second = 0;
        int millisecond = 0;
        int microsecond = 0;
        int nanosecond = 0;
        boolean hasSecond = false;

        if (extendedFormat) {
            pos++;
            if (!hasTwoDigits(pos)) {
                context.throwRangeError("Temporal error: Invalid time");
                return null;
            }
            minute = parseTwoDigits(context, "minute");
            if (context.hasPendingException()) {
                return null;
            }
            if (pos < input.length() && input.charAt(pos) == ':') {
                pos++;
                hasSecond = true;
                second = parseTwoDigits(context, "second");
                if (context.hasPendingException()) {
                    return null;
                }
            } else if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                context.throwRangeError("Temporal error: Invalid time");
                return null;
            }
        } else if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            minute = parseTwoDigits(context, "minute");
            if (context.hasPendingException()) {
                return null;
            }
            if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                hasSecond = true;
                second = parseTwoDigits(context, "second");
                if (context.hasPendingException()) {
                    return null;
                }
            }
        }

        if (hasSecond && pos < input.length() && (input.charAt(pos) == '.' || input.charAt(pos) == ',')) {
            pos++;
            BigInteger fractionalNanoseconds = parseFractionalNanoseconds(context, true);
            if (context.hasPendingException()) {
                return null;
            }
            millisecond = fractionalNanoseconds.divide(BigInteger.valueOf(1_000_000L)).intValue();
            BigInteger remainingAfterMilliseconds = fractionalNanoseconds.remainder(BigInteger.valueOf(1_000_000L));
            microsecond = remainingAfterMilliseconds.divide(BigInteger.valueOf(1_000L)).intValue();
            nanosecond = remainingAfterMilliseconds.remainder(BigInteger.valueOf(1_000L)).intValue();
        } else if (!hasSecond && pos < input.length() && (input.charAt(pos) == '.' || input.charAt(pos) == ',')) {
            context.throwRangeError("Temporal error: Invalid time");
            return null;
        }

        int constrainedSecond = second == 60 ? 59 : second;
        if (!IsoTime.isValidTime(hour, minute, constrainedSecond, millisecond, microsecond, nanosecond)) {
            context.throwRangeError("Temporal error: Invalid time");
            return null;
        }
        return new IsoTime(hour, minute, constrainedSecond, millisecond, microsecond, nanosecond);
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
        if (hours > 23) {
            context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
            return 0;
        }
        int minutes = 0;
        if (pos < input.length() && input.charAt(pos) == ':') {
            pos++;
        }
        if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            minutes = parseTwoDigits(context, "offset minute");
            if (context.hasPendingException()) return 0;
            if (minutes > 59) {
                context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
                return 0;
            }
        }
        int seconds = 0;
        if (pos < input.length() && input.charAt(pos) == ':') {
            pos++;
            if (pos + 2 > input.length() || !Character.isDigit(input.charAt(pos)) || !Character.isDigit(input.charAt(pos + 1))) {
                context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
                return 0;
            }
            seconds = parseTwoDigits(context, "offset second");
            if (context.hasPendingException()) {
                return 0;
            }
            if (seconds > 59) {
                context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
                return 0;
            }
            if (pos < input.length() && (input.charAt(pos) == '.' || input.charAt(pos) == ',')) {
                pos++;
                int fractionalStart = pos;
                while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                    pos++;
                }
                if (fractionalStart == pos) {
                    context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
                    return 0;
                }
            }
        }
        return sign * (hours * 3600 + minutes * 60 + seconds);
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

    private int parseTwoDigitNumber(String value, int index) {
        return (value.charAt(index) - '0') * 10 + (value.charAt(index + 1) - '0');
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
        if (c == '+' || c == '-') {
            boolean negative = c == '-';
            pos++;
            if (pos + 6 > input.length()) {
                context.throwRangeError("Temporal error: Invalid character while parsing year value.");
                return 0;
            }
            int startPos = pos;
            int endPos = pos + 6;
            for (int index = startPos; index < endPos; index++) {
                if (!Character.isDigit(input.charAt(index))) {
                    context.throwRangeError("Temporal error: Invalid character while parsing year value.");
                    return 0;
                }
            }
            pos = endPos;
            int year = Integer.parseInt(input.substring(startPos, endPos));
            if (negative && year == 0) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return 0;
            }
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

    private long toLongWithRangeCheck(JSContext context, BigInteger value) {
        if (value.bitLength() > 63) {
            context.throwRangeError("Temporal error: Duration was not valid.");
            return Long.MIN_VALUE;
        }
        return value.longValue();
    }

    public record DurationFields(long years, long months, long weeks, long days,
                                 long hours, long minutes, long seconds,
                                 long milliseconds, long microseconds, long nanoseconds) {
    }

    public record ParsedDateTime(IsoDate date, IsoTime time, String calendar) {
    }

    public record ParsedInstant(IsoDate date, IsoTime time, int offsetSeconds, BigInteger offsetNanoseconds) {
    }

    private record ParsedOffset(int totalSeconds, BigInteger totalNanoseconds) {
    }

    public record ParsedZonedDateTime(IsoDate date, IsoTime time, int offsetSeconds, String timeZoneId,
                                      String calendarId) {
    }
}
