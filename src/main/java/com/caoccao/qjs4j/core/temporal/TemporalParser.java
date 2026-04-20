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
import com.caoccao.qjs4j.core.JSString;

import java.math.BigInteger;

/**
 * ISO 8601 string parser for Temporal types.
 */
public final class TemporalParser {
    private static final long NANOSECONDS_PER_HOUR = 3_600_000_000_000L;
    private static final long NANOSECONDS_PER_MICROSECOND = 1_000L;
    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;
    private static final long NANOSECONDS_PER_MINUTE = 60_000_000_000L;
    private static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;
    private final String input;
    private int position;

    public TemporalParser(String input) {
        this.input = input;
        this.position = 0;
    }

    private static long[] decomposeTimeFraction(long fractionalNanoseconds) {
        long minutePortion = fractionalNanoseconds / NANOSECONDS_PER_MINUTE;
        long remainingAfterMinutes = fractionalNanoseconds % NANOSECONDS_PER_MINUTE;
        long secondPortion = remainingAfterMinutes / NANOSECONDS_PER_SECOND;
        long remainingAfterSeconds = remainingAfterMinutes % NANOSECONDS_PER_SECOND;
        long millisecondPortion = remainingAfterSeconds / NANOSECONDS_PER_MILLISECOND;
        long remainingAfterMilliseconds = remainingAfterSeconds % NANOSECONDS_PER_MILLISECOND;
        long microsecondPortion = remainingAfterMilliseconds / NANOSECONDS_PER_MICROSECOND;
        long nanosecondPortion = remainingAfterMilliseconds % NANOSECONDS_PER_MICROSECOND;

        return new long[]{
                minutePortion,
                secondPortion,
                millisecondPortion,
                microsecondPortion,
                nanosecondPortion
        };
    }

    private static boolean isAmbiguousFourDigitTime(String value) {
        if (value.length() != 4
                || !isAsciiDigit(value.charAt(0))
                || !isAsciiDigit(value.charAt(1))
                || !isAsciiDigit(value.charAt(2))
                || !isAsciiDigit(value.charAt(3))) {
            return false;
        }
        int month = parseFixedTwoDigits(value, 0);
        int dayOfMonth = parseFixedTwoDigits(value, 2);
        return new IsoDate(1972, month, dayOfMonth).isValid();
    }

    private static boolean isAmbiguousSixDigitTime(String value) {
        if (value.length() != 6
                || !isAsciiDigit(value.charAt(0))
                || !isAsciiDigit(value.charAt(1))
                || !isAsciiDigit(value.charAt(2))
                || !isAsciiDigit(value.charAt(3))
                || !isAsciiDigit(value.charAt(4))
                || !isAsciiDigit(value.charAt(5))) {
            return false;
        }
        int month = parseFixedTwoDigits(value, 4);
        return month >= 1 && month <= 12;
    }

    private static boolean isAmbiguousTimeStringWithoutDesignator(String input) {
        String candidate = input;
        int annotationStart = candidate.indexOf('[');
        if (annotationStart >= 0) {
            candidate = candidate.substring(0, annotationStart);
        }
        if (isAmbiguousFourDigitTime(candidate) || isAmbiguousSixDigitTime(candidate)) {
            return true;
        }
        if (candidate.length() == 5
                && isAsciiDigit(candidate.charAt(0))
                && isAsciiDigit(candidate.charAt(1))
                && candidate.charAt(2) == '-'
                && isAsciiDigit(candidate.charAt(3))
                && isAsciiDigit(candidate.charAt(4))) {
            int month = parseFixedTwoDigits(candidate, 0);
            int dayOfMonth = parseFixedTwoDigits(candidate, 3);
            return new IsoDate(1972, month, dayOfMonth).isValid();
        }
        if (candidate.length() == 7
                && isAsciiDigit(candidate.charAt(0))
                && isAsciiDigit(candidate.charAt(1))
                && isAsciiDigit(candidate.charAt(2))
                && isAsciiDigit(candidate.charAt(3))
                && candidate.charAt(4) == '-'
                && isAsciiDigit(candidate.charAt(5))
                && isAsciiDigit(candidate.charAt(6))) {
            int month = parseFixedTwoDigits(candidate, 5);
            return month >= 1 && month <= 12;
        }
        return false;
    }

    private static boolean isAsciiDigit(char character) {
        return character >= '0' && character <= '9';
    }

    private static boolean isValidIsoYearMonthDateForParsing(int year, int month, int dayOfMonth) {
        if (month < 1 || month > 12) {
            return false;
        }
        if (dayOfMonth < 1 || dayOfMonth > IsoDate.daysInMonth(year, month)) {
            return false;
        }
        if (year < -271821 || year > 275760) {
            return false;
        }
        if (year == -271821 && month < 4) {
            return false;
        }
        return year != 275760 || month <= 9;
    }

    /**
     * Parse an ISO date string into an IsoDate.
     * Returns null and sets pending exception on error.
     */
    public static IsoDate parseDateString(JSContext context, String input) {
        return parseDateString(context, input, true);
    }

    private static IsoDate parseDateString(JSContext context, String input, boolean enforceIsoDateRange) {
        if (input == null || input.isEmpty()) {
            context.throwRangeError("Temporal error: Invalid character while parsing year value.");
            return null;
        }
        if (input.indexOf('\u2212') >= 0) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        TemporalParser parser = new TemporalParser(input);
        IsoDate date = parser.parseDate(context, enforceIsoDateRange);
        if (date == null) {
            return null;
        }

        boolean hasTimePart = false;
        if (parser.position < parser.input.length() && (parser.current() == 'T' || parser.current() == 't' || parser.current() == ' ')) {
            hasTimePart = true;
            parser.position++;
            IsoTime parsedTime = parser.parseInstantTime(context);
            if (parsedTime == null || context.hasPendingException()) {
                return null;
            }
        }

        if (parser.position < parser.input.length()) {
            char marker = parser.current();
            if (marker == 'Z' || marker == 'z') {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            if (marker == '+' || marker == '-') {
                if (!hasTimePart) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                IsoOffset parsedOffset = parser.parseInstantOffsetNanoseconds(context);
                if (parsedOffset == null || context.hasPendingException()) {
                    return null;
                }
            }
        }

        parser.parseInstantAnnotations(context);
        if (context.hasPendingException()) {
            return null;
        }

        if (parser.position != parser.input.length()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return date;
    }

    /**
     * Parse an ISO date-time string.
     * Returns null and sets pending exception on error.
     */
    public static IsoCalendarDateTime parseDateTimeString(JSContext context, String input) {
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
        IsoTime time = IsoTime.MIDNIGHT;
        boolean hasTimePart = false;
        if (parser.position < parser.input.length() && (parser.current() == 'T' || parser.current() == 't' || parser.current() == ' ')) {
            hasTimePart = true;
            parser.position++;
            time = parser.parseInstantTime(context);
            if (time == null) {
                return null;
            }
        }

        if (parser.position < parser.input.length()) {
            char marker = parser.current();
            if (marker == 'Z' || marker == 'z') {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            if (marker == '+' || marker == '-') {
                if (!hasTimePart) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                IsoOffset parsedOffset = parser.parseInstantOffsetNanoseconds(context);
                if (parsedOffset == null || context.hasPendingException()) {
                    return null;
                }
            }
        }

        ParsedAnnotations parsedAnnotations = parser.parseInstantAnnotations(context);
        if (context.hasPendingException() || parsedAnnotations == null) {
            return null;
        }
        if (parser.position != parser.input.length()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        TemporalCalendarId calendar = TemporalCalendarId.ISO8601;
        String calendarAnnotation = parsedAnnotations.calendarAnnotation;
        if (calendarAnnotation != null) {
            calendar = TemporalCalendarId.createFromCalendarString(context, new JSString(calendarAnnotation));
            if (context.hasPendingException()) {
                return null;
            }
        }
        return new IsoCalendarDateTime(date, time, calendar);
    }

    /**
     * Parse a duration string like "P1Y2M3DT4H5M6S".
     */
    public static TemporalDuration parseDurationString(JSContext context, String input) {
        if (input == null || input.isEmpty()) {
            context.throwRangeError("Temporal error: Invalid duration string.");
            return null;
        }
        TemporalParser parser = new TemporalParser(input);
        return parser.parseDuration(context);
    }

    private static int parseFixedTwoDigits(String value, int index) {
        return (value.charAt(index) - '0') * 10 + (value.charAt(index + 1) - '0');
    }

    /**
     * Parse an Instant string like "1970-01-01T00:00:00Z" or "1970-01-01T00:00:00+00:00".
     * Returns null and sets pending exception on error.
     */
    public static IsoDateTimeOffset parseInstantString(JSContext context, String input) {
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
        if (parser.position >= parser.input.length()
                || (parser.current() != 'T' && parser.current() != 't' && parser.current() != ' ')) {
            context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
            return null;
        }
        parser.position++;
        IsoTime time = parser.parseInstantTime(context);
        if (time == null) {
            return null;
        }
        IsoOffset parsedOffset = parser.parseInstantOffsetNanoseconds(context);
        if (parsedOffset == null || context.hasPendingException()) {
            return null;
        }
        parser.parseInstantAnnotations(context);
        if (context.hasPendingException()) {
            return null;
        }
        if (parser.position != parser.input.length()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return new IsoDateTimeOffset(date, time, parsedOffset);
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
        int dayOfMonth;
        int month;
        int prefixLength = -1;

        if (input.startsWith("--")
                && input.length() >= 6
                && isAsciiDigit(input.charAt(2))
                && isAsciiDigit(input.charAt(3))) {
            month = parseFixedTwoDigits(input, 2);
            if (input.length() >= 7 && input.charAt(4) == '-') {
                if (input.length() >= 7
                        && isAsciiDigit(input.charAt(5))
                        && isAsciiDigit(input.charAt(6))) {
                    dayOfMonth = parseFixedTwoDigits(input, 5);
                    prefixLength = 7;
                } else {
                    dayOfMonth = -1;
                }
            } else if (input.length() >= 6
                    && isAsciiDigit(input.charAt(4))
                    && isAsciiDigit(input.charAt(5))) {
                dayOfMonth = parseFixedTwoDigits(input, 4);
                prefixLength = 6;
            } else {
                dayOfMonth = -1;
            }
            if (prefixLength > 0) {
                String remainder = input.substring(prefixLength);
                String syntheticDateString = "1972-"
                        + (month < 10 ? "0" : "") + month
                        + "-"
                        + (dayOfMonth < 10 ? "0" : "") + dayOfMonth
                        + remainder;
                IsoDate parsedDate = parseDateString(context, syntheticDateString, false);
                if (parsedDate == null) {
                    return null;
                }
                return new IsoDate(1972, parsedDate.month(), parsedDate.day());
            }
        } else if (input.length() >= 5
                && isAsciiDigit(input.charAt(0))
                && isAsciiDigit(input.charAt(1))
                && input.charAt(2) == '-'
                && isAsciiDigit(input.charAt(3))
                && isAsciiDigit(input.charAt(4))) {
            month = parseFixedTwoDigits(input, 0);
            dayOfMonth = parseFixedTwoDigits(input, 3);
            prefixLength = 5;
            if (prefixLength > 0) {
                String remainder = input.substring(prefixLength);
                String syntheticDateString = "1972-"
                        + (month < 10 ? "0" : "") + month
                        + "-"
                        + (dayOfMonth < 10 ? "0" : "") + dayOfMonth
                        + remainder;
                IsoDate parsedDate = parseDateString(context, syntheticDateString, false);
                if (parsedDate == null) {
                    return null;
                }
                return new IsoDate(1972, parsedDate.month(), parsedDate.day());
            }
        } else if (input.length() >= 4
                && isAsciiDigit(input.charAt(0))
                && isAsciiDigit(input.charAt(1))
                && isAsciiDigit(input.charAt(2))
                && isAsciiDigit(input.charAt(3))
                && (input.length() == 4 || input.charAt(4) == '[')) {
            month = parseFixedTwoDigits(input, 0);
            dayOfMonth = parseFixedTwoDigits(input, 2);
            String remainder = input.substring(4);
            String syntheticDateString = "1972-"
                    + (month < 10 ? "0" : "") + month
                    + "-"
                    + (dayOfMonth < 10 ? "0" : "") + dayOfMonth
                    + remainder;
            IsoDate parsedDate = parseDateString(context, syntheticDateString, false);
            if (parsedDate == null) {
                return null;
            }
            return new IsoDate(1972, parsedDate.month(), parsedDate.day());
        }

        IsoDate date = parseDateString(context, input, false);
        if (date == null) {
            return null;
        }
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
        if (input.indexOf('\u2212') >= 0) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        TemporalParser parser = new TemporalParser(input);
        IsoTime time;
        if (parser.position < parser.input.length() && (parser.current() == 'T' || parser.current() == 't')) {
            parser.position++;
            time = parser.parseInstantTime(context);
        } else {
            int initialPosition = parser.position;
            IsoDate parsedDate = parser.parseDate(context);
            if (parsedDate != null && !context.hasPendingException()) {
                if (parser.position >= parser.input.length()) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                char separator = parser.current();
                if (separator != 'T' && separator != 't' && separator != ' ') {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                parser.position++;
                time = parser.parseInstantTime(context);
            } else {
                context.clearPendingException();
                parser.position = initialPosition;
                if (isAmbiguousTimeStringWithoutDesignator(input)) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                time = parser.parseInstantTime(context);
            }
        }
        if (time == null || context.hasPendingException()) {
            return null;
        }

        if (parser.position < parser.input.length()) {
            char marker = parser.current();
            if (marker == 'Z' || marker == 'z') {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            if (marker == '+' || marker == '-') {
                IsoOffset parsedOffset = parser.parseInstantOffsetNanoseconds(context);
                if (parsedOffset == null || context.hasPendingException()) {
                    return null;
                }
            }
        }

        parser.parseInstantAnnotations(context);
        if (context.hasPendingException()) {
            return null;
        }
        if (parser.position != parser.input.length()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
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
        if (input.indexOf('\u2212') >= 0) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        TemporalParser parser = new TemporalParser(input);

        int year = parser.parseYear(context);
        if (context.hasPendingException()) {
            return null;
        }

        boolean hasSeparator = parser.position < parser.input.length() && parser.input.charAt(parser.position) == '-';
        if (hasSeparator) {
            parser.position++;
        }

        int month = parser.parseTwoDigits(context, "month");
        if (context.hasPendingException()) {
            return null;
        }
        if (month < 1 || month > 12) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        int dayOfMonth = 1;
        if (hasSeparator) {
            if (parser.position < parser.input.length() && parser.input.charAt(parser.position) == '-') {
                parser.position++;
                dayOfMonth = parser.parseTwoDigits(context, "day");
                if (context.hasPendingException()) {
                    return null;
                }
            }
        } else {
            if (parser.position < parser.input.length() && isAsciiDigit(parser.input.charAt(parser.position))) {
                dayOfMonth = parser.parseTwoDigits(context, "day");
                if (context.hasPendingException()) {
                    return null;
                }
            }
            if (parser.position < parser.input.length() && parser.input.charAt(parser.position) == '-') {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        }

        if (!isValidIsoYearMonthDateForParsing(year, month, dayOfMonth)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        boolean hasTimePart = false;
        if (parser.position < parser.input.length()
                && (parser.current() == 'T' || parser.current() == 't' || parser.current() == ' ')) {
            hasTimePart = true;
            parser.position++;
            IsoTime parsedTime = parser.parseInstantTime(context);
            if (parsedTime == null || context.hasPendingException()) {
                return null;
            }
        }

        if (parser.position < parser.input.length()) {
            char marker = parser.current();
            if (marker == 'Z' || marker == 'z') {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            if (marker == '+' || marker == '-') {
                if (!hasTimePart) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                IsoOffset parsedOffset = parser.parseInstantOffsetNanoseconds(context);
                if (parsedOffset == null || context.hasPendingException()) {
                    return null;
                }
            }
        }

        parser.parseInstantAnnotations(context);
        if (context.hasPendingException()) {
            return null;
        }
        if (parser.position != parser.input.length()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        return new IsoDate(year, month, dayOfMonth);
    }

    /**
     * Parse a ZonedDateTime string like "2024-01-15T12:00:00+00:00[UTC]".
     * Returns null and sets pending exception on error.
     */
    public static IsoZonedDateTimeOffset parseZonedDateTimeString(JSContext context, String input) {
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
        IsoTime time = IsoTime.MIDNIGHT;
        boolean hasTimePart = false;
        if (parser.position < parser.input.length() && (parser.current() == 'T' || parser.current() == 't' || parser.current() == ' ')) {
            hasTimePart = true;
            parser.position++;
            time = parser.parseInstantTime(context);
            if (time == null) {
                return null;
            }
        }
        int offsetSeconds = 0;
        if (parser.position < parser.input.length()) {
            char marker = parser.input.charAt(parser.position);
            if (marker == 'Z' || marker == 'z' || marker == '+' || marker == '-') {
                if (!hasTimePart) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                IsoOffset parsedOffset = parser.parseInstantOffsetNanoseconds(context);
                if (parsedOffset == null || context.hasPendingException()) {
                    return null;
                }
                offsetSeconds = parsedOffset.totalSeconds();
            }
        }

        ParsedAnnotations parsedAnnotations = parser.parseInstantAnnotations(context);
        if (context.hasPendingException() || parsedAnnotations == null) {
            return null;
        }
        if (parser.position != parser.input.length()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        String timeZoneId = parsedAnnotations.timeZoneAnnotation;
        if (timeZoneId == null) {
            context.throwRangeError("Temporal error: Must specify time zone.");
            return null;
        }

        TemporalCalendarId calendarId = TemporalCalendarId.ISO8601;
        String calendarAnnotation = parsedAnnotations.calendarAnnotation;
        if (calendarAnnotation != null) {
            calendarId = TemporalCalendarId.createFromCalendarString(context, new JSString(calendarAnnotation));
            if (context.hasPendingException()) {
                return null;
            }
        }

        return new IsoZonedDateTimeOffset(date, time, offsetSeconds, timeZoneId, calendarId);
    }

    private char current() {
        return input.charAt(position);
    }

    private boolean hasTwoDigits(int index) {
        return hasTwoDigits(input, index);
    }

    private boolean hasTwoDigits(String value, int index) {
        return index + 2 <= value.length()
                && isAsciiDigit(value.charAt(index))
                && isAsciiDigit(value.charAt(index + 1));
    }

    private boolean isLowercaseAnnotationKey(String annotationKey) {
        if (annotationKey.isEmpty()) {
            return false;
        }
        for (int index = 0; index < annotationKey.length(); index++) {
            char character = annotationKey.charAt(index);
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
        } else if (isAsciiDigit(value.charAt(index))) {
            extendedFormat = false;
        } else {
            return false;
        }

        if (index + 2 > value.length()
                || !isAsciiDigit(value.charAt(index))
                || !isAsciiDigit(value.charAt(index + 1))) {
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
        } else if (!isAsciiDigit(value.charAt(index))) {
            return false;
        }
        return false;
    }

    private boolean isOffsetTimeZoneAnnotation(String annotationValue) {
        return !annotationValue.isEmpty()
                && (annotationValue.charAt(0) == '+' || annotationValue.charAt(0) == '-');
    }

    private boolean isValidIsoDateForParsing(int year, int month, int dayOfMonth, boolean enforceIsoDateRange) {
        if (month < 1 || month > 12) {
            return false;
        }
        if (dayOfMonth < 1 || dayOfMonth > IsoDate.daysInMonth(year, month)) {
            return false;
        }
        if (!enforceIsoDateRange) {
            return true;
        }
        return new IsoDate(year, month, dayOfMonth).isValid();
    }

    private IsoDate parseDate(JSContext context) {
        return parseDate(context, true);
    }

    private IsoDate parseDate(JSContext context, boolean enforceIsoDateRange) {
        int year = parseYear(context);
        if (context.hasPendingException()) {
            return null;
        }
        // Check for separator
        boolean hasSep = position < input.length() && input.charAt(position) == '-';
        if (hasSep) {
            position++;
        }
        int month = parseTwoDigits(context, "month");
        if (context.hasPendingException()) {
            return null;
        }
        if (hasSep) {
            if (position >= input.length() || input.charAt(position) != '-') {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            position++;
        } else {
            if (position < input.length() && input.charAt(position) == '-') {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        }
        int dayOfMonth = parseTwoDigits(context, "day");
        if (context.hasPendingException()) {
            return null;
        }
        if (!isValidIsoDateForParsing(year, month, dayOfMonth, enforceIsoDateRange)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return new IsoDate(year, month, dayOfMonth);
    }

    private TemporalDuration parseDuration(JSContext context) {
        boolean negative = false;
        if (position < input.length() && (input.charAt(position) == '-' || input.charAt(position) == '\u2212')) {
            negative = true;
            position++;
        } else if (position < input.length() && input.charAt(position) == '+') {
            position++;
        }

        if (position >= input.length() || (input.charAt(position) != 'P' && input.charAt(position) != 'p')) {
            context.throwRangeError("Temporal error: Invalid duration string.");
            return null;
        }
        position++;

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

        while (position < input.length()) {
            char currentChar = input.charAt(position);
            if (currentChar == 'T' || currentChar == 't') {
                if (inTimePart) {
                    context.throwRangeError("Temporal error: Invalid duration string.");
                    return null;
                }
                inTimePart = true;
                position++;
                continue;
            }
            if (!isAsciiDigit(currentChar)) {
                context.throwRangeError("Temporal error: Invalid duration string.");
                return null;
            }
            // Parse number
            int numberStart = position;
            while (position < input.length() && isAsciiDigit(input.charAt(position))) {
                position++;
            }
            long number = parseLongWithRangeCheckFromDigits(context, numberStart, position);
            if (context.hasPendingException()) {
                return null;
            }
            // Parse optional fractional part
            int fractionalNanoseconds = 0;
            boolean hasFraction = false;
            if (position < input.length() && (input.charAt(position) == '.' || input.charAt(position) == ',')) {
                hasFraction = true;
                position++;
                fractionalNanoseconds = parseFractionalNanoseconds(
                        context,
                        true,
                        "Temporal error: Invalid duration string.",
                        "Temporal error: Invalid duration string.");
                if (context.hasPendingException()) {
                    return null;
                }
            }

            if (position >= input.length()) {
                context.throwRangeError("Temporal error: Invalid duration string.");
                return null;
            }
            char unit = input.charAt(position);
            position++;

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
                            long fractionalHourNanoseconds = fractionalNanoseconds * 3_600L;
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
                            long fractionalMinuteNanoseconds = fractionalNanoseconds * 60L;
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
                            milliseconds = fractionalNanoseconds / NANOSECONDS_PER_MILLISECOND;
                            int remainingFractionalNanoseconds = (int) (fractionalNanoseconds % NANOSECONDS_PER_MILLISECOND);
                            microseconds = remainingFractionalNanoseconds / NANOSECONDS_PER_MICROSECOND;
                            nanoseconds = remainingFractionalNanoseconds % NANOSECONDS_PER_MICROSECOND;
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
        return new TemporalDuration(
                years * sign, months * sign, weeks * sign, days * sign,
                hours * sign, minutes * sign, seconds * sign,
                milliseconds * sign, microseconds * sign, nanoseconds * sign
        );
    }

    private int parseFractionalNanoseconds(
            JSContext context,
            boolean rejectMoreThanNineDigits,
            String emptyFractionErrorMessage,
            String overflowFractionErrorMessage) {
        int fractionStart = position;
        while (position < input.length() && isAsciiDigit(input.charAt(position))) {
            position++;
        }
        if (fractionStart == position) {
            context.throwRangeError(emptyFractionErrorMessage);
            return 0;
        }
        int digitCount = position - fractionStart;
        if (digitCount > 9 && rejectMoreThanNineDigits) {
            context.throwRangeError(overflowFractionErrorMessage);
            return 0;
        }
        int parseDigitCount = Math.min(digitCount, 9);
        int value = 0;
        for (int index = 0; index < parseDigitCount; index++) {
            value = value * 10 + (input.charAt(fractionStart + index) - '0');
        }
        for (int index = parseDigitCount; index < 9; index++) {
            value *= 10;
        }
        return value;
    }

    private ParsedAnnotations parseInstantAnnotations(JSContext context) {
        int timeZoneAnnotationCount = 0;
        int calendarAnnotationCount = 0;
        boolean hasCriticalCalendarAnnotation = false;
        String firstTimeZoneAnnotation = null;
        String firstCalendarAnnotation = null;
        while (position < input.length() && input.charAt(position) == '[') {
            position++;
            boolean critical = false;
            if (position < input.length() && input.charAt(position) == '!') {
                critical = true;
                position++;
            }
            int contentStart = position;
            while (position < input.length() && input.charAt(position) != ']') {
                position++;
            }
            if (position >= input.length() || contentStart == position) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            String content = input.substring(contentStart, position);
            position++;

            int equalSignIndex = content.indexOf('=');
            if (equalSignIndex >= 0) {
                String annotationKey = content.substring(0, equalSignIndex);
                if (equalSignIndex == content.length() - 1 || !isLowercaseAnnotationKey(annotationKey)) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                if ("u-ca".equals(annotationKey)) {
                    calendarAnnotationCount++;
                    if (firstCalendarAnnotation == null) {
                        firstCalendarAnnotation = content.substring(equalSignIndex + 1);
                    }
                    if (critical) {
                        hasCriticalCalendarAnnotation = true;
                    }
                } else if (critical) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
            } else {
                timeZoneAnnotationCount++;
                if (timeZoneAnnotationCount > 1) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                if (isOffsetTimeZoneAnnotation(content)
                        && !isMinutePrecisionOffsetTimeZoneAnnotation(content)) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                if (firstTimeZoneAnnotation == null) {
                    firstTimeZoneAnnotation = content;
                }
            }
        }
        if (calendarAnnotationCount > 1 && hasCriticalCalendarAnnotation) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return new ParsedAnnotations(firstCalendarAnnotation, firstTimeZoneAnnotation);
    }

    private IsoOffset parseInstantOffsetNanoseconds(JSContext context) {
        if (position >= input.length()) {
            context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
            return null;
        }
        char signCharacter = input.charAt(position);
        if (signCharacter == 'Z' || signCharacter == 'z') {
            position++;
            return new IsoOffset(0, BigInteger.ZERO);
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
        position++;

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
        int fractionalNanoseconds = 0;
        boolean hasMinute = false;
        boolean hasSecond = false;
        boolean extendedFormat = false;

        if (position < input.length() && input.charAt(position) == ':') {
            extendedFormat = true;
            position++;
            hasMinute = true;
            offsetMinute = parseTwoDigits(context, "offset minute");
            if (context.hasPendingException()) {
                return null;
            }
        } else if (position < input.length() && isAsciiDigit(input.charAt(position))) {
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
                if (position < input.length() && input.charAt(position) == ':') {
                    position++;
                    hasSecond = true;
                    offsetSecond = parseTwoDigits(context, "offset second");
                    if (context.hasPendingException()) {
                        return null;
                    }
                } else if (position < input.length() && isAsciiDigit(input.charAt(position))) {
                    context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
                    return null;
                }
            } else if (position < input.length() && isAsciiDigit(input.charAt(position))) {
                hasSecond = true;
                offsetSecond = parseTwoDigits(context, "offset second");
                if (context.hasPendingException()) {
                    return null;
                }
            } else if (position < input.length() && input.charAt(position) == ':') {
                context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
                return null;
            }
        } else if (position < input.length() && (input.charAt(position) == ':' || input.charAt(position) == '.' || input.charAt(position) == ',')) {
            context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
            return null;
        }

        if (hasSecond) {
            if (offsetSecond > 59) {
                context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
                return null;
            }
            if (position < input.length() && (input.charAt(position) == '.' || input.charAt(position) == ',')) {
                position++;
                fractionalNanoseconds = parseFractionalNanoseconds(
                        context,
                        true,
                        "Temporal error: Instant argument must be Instant or string.",
                        "Temporal error: Invalid ISO date.");
                if (context.hasPendingException()) {
                    return null;
                }
            }
        } else if (position < input.length() && (input.charAt(position) == '.' || input.charAt(position) == ',')) {
            context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
            return null;
        }

        int offsetSeconds = sign * (offsetHour * 3600 + offsetMinute * 60 + offsetSecond);
        long offsetNanoseconds = offsetHour * NANOSECONDS_PER_HOUR
                + offsetMinute * NANOSECONDS_PER_MINUTE
                + offsetSecond * NANOSECONDS_PER_SECOND
                + fractionalNanoseconds;
        if (sign < 0) {
            offsetNanoseconds = -offsetNanoseconds;
        }
        return new IsoOffset(offsetSeconds, BigInteger.valueOf(offsetNanoseconds));
    }

    private IsoTime parseInstantTime(JSContext context) {
        int hour = parseTwoDigits(context, "hour");
        if (context.hasPendingException()) {
            return null;
        }
        boolean extendedFormat = position < input.length() && input.charAt(position) == ':';
        int minute = 0;
        int second = 0;
        int millisecond = 0;
        int microsecond = 0;
        int nanosecond = 0;
        boolean hasSecond = false;

        if (extendedFormat) {
            position++;
            if (!hasTwoDigits(position)) {
                context.throwRangeError("Temporal error: Invalid time");
                return null;
            }
            minute = parseTwoDigits(context, "minute");
            if (context.hasPendingException()) {
                return null;
            }
            if (position < input.length() && input.charAt(position) == ':') {
                position++;
                hasSecond = true;
                second = parseTwoDigits(context, "second");
                if (context.hasPendingException()) {
                    return null;
                }
            } else if (position < input.length() && isAsciiDigit(input.charAt(position))) {
                context.throwRangeError("Temporal error: Invalid time");
                return null;
            }
        } else if (position < input.length() && isAsciiDigit(input.charAt(position))) {
            minute = parseTwoDigits(context, "minute");
            if (context.hasPendingException()) {
                return null;
            }
            if (position < input.length() && isAsciiDigit(input.charAt(position))) {
                hasSecond = true;
                second = parseTwoDigits(context, "second");
                if (context.hasPendingException()) {
                    return null;
                }
            }
        }

        if (hasSecond && position < input.length() && (input.charAt(position) == '.' || input.charAt(position) == ',')) {
            position++;
            int fractionalNanoseconds = parseFractionalNanoseconds(
                    context,
                    true,
                    "Temporal error: Instant argument must be Instant or string.",
                    "Temporal error: Invalid ISO date.");
            if (context.hasPendingException()) {
                return null;
            }
            millisecond = (int) (fractionalNanoseconds / NANOSECONDS_PER_MILLISECOND);
            int remainingAfterMilliseconds = (int) (fractionalNanoseconds % NANOSECONDS_PER_MILLISECOND);
            microsecond = (int) (remainingAfterMilliseconds / NANOSECONDS_PER_MICROSECOND);
            nanosecond = (int) (remainingAfterMilliseconds % NANOSECONDS_PER_MICROSECOND);
        } else if (!hasSecond && position < input.length() && (input.charAt(position) == '.' || input.charAt(position) == ',')) {
            context.throwRangeError("Temporal error: Invalid time");
            return null;
        }

        int constrainedSecond = second == 60 ? 59 : second;
        IsoTime parsedIsoTime = new IsoTime(hour, minute, constrainedSecond, millisecond, microsecond, nanosecond);
        if (!parsedIsoTime.isValid()) {
            context.throwRangeError("Temporal error: Invalid time");
            return null;
        }
        return parsedIsoTime;
    }

    private long parseLongWithRangeCheckFromDigits(JSContext context, int startIndex, int endIndex) {
        long value = 0L;
        for (int index = startIndex; index < endIndex; index++) {
            int digit = input.charAt(index) - '0';
            if (value > (Long.MAX_VALUE - digit) / 10L) {
                BigInteger fallbackValue = new BigInteger(input.substring(startIndex, endIndex));
                return toLongWithRangeCheck(context, fallbackValue);
            }
            value = value * 10L + digit;
        }
        return value;
    }

    private int parseTwoDigitNumber(String value, int index) {
        return (value.charAt(index) - '0') * 10 + (value.charAt(index + 1) - '0');
    }

    private int parseTwoDigits(JSContext context, String fieldName) {
        if (position + 2 > input.length()) {
            context.throwRangeError("Temporal error: Invalid character while parsing " + fieldName + " value.");
            return 0;
        }
        char firstDigit = input.charAt(position);
        char secondDigit = input.charAt(position + 1);
        if (!isAsciiDigit(firstDigit) || !isAsciiDigit(secondDigit)) {
            context.throwRangeError("Temporal error: Invalid character while parsing " + fieldName + " value.");
            return 0;
        }
        position += 2;
        return (firstDigit - '0') * 10 + (secondDigit - '0');
    }

    private int parseYear(JSContext context) {
        if (position >= input.length()) {
            context.throwRangeError("Temporal error: Invalid character while parsing year value.");
            return 0;
        }
        char signCharacter = input.charAt(position);
        if (signCharacter == '+' || signCharacter == '-') {
            boolean negative = signCharacter == '-';
            position++;
            if (position + 6 > input.length()) {
                context.throwRangeError("Temporal error: Invalid character while parsing year value.");
                return 0;
            }
            int startPos = position;
            int endPos = position + 6;
            int year = 0;
            for (int index = startPos; index < endPos; index++) {
                char currentCharacter = input.charAt(index);
                if (!isAsciiDigit(currentCharacter)) {
                    context.throwRangeError("Temporal error: Invalid character while parsing year value.");
                    return 0;
                }
                year = year * 10 + (currentCharacter - '0');
            }
            position = endPos;
            if (negative && year == 0) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return 0;
            }
            return negative ? -year : year;
        }

        if (!isAsciiDigit(signCharacter)) {
            context.throwRangeError("Temporal error: Invalid character while parsing year value.");
            return 0;
        }
        // Regular 4-digit year
        if (position + 4 > input.length()) {
            context.throwRangeError("Temporal error: Invalid character while parsing year value.");
            return 0;
        }
        int year = 0;
        for (int digitIndex = 0; digitIndex < 4; digitIndex++) {
            char currentCharacter = input.charAt(position + digitIndex);
            if (!isAsciiDigit(currentCharacter)) {
                context.throwRangeError("Temporal error: Invalid character while parsing year value.");
                return 0;
            }
            year = year * 10 + (currentCharacter - '0');
        }
        position += 4;
        return year;
    }

    private long toLongWithRangeCheck(JSContext context, BigInteger value) {
        if (value.bitLength() > 63) {
            context.throwRangeError("Temporal error: Duration was not valid.");
            return Long.MIN_VALUE;
        }
        return value.longValue();
    }

    private static final class ParsedAnnotations {
        private final String calendarAnnotation;
        private final String timeZoneAnnotation;

        private ParsedAnnotations(String calendarAnnotation, String timeZoneAnnotation) {
            this.calendarAnnotation = calendarAnnotation;
            this.timeZoneAnnotation = timeZoneAnnotation;
        }
    }
}
