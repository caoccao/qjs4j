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
import static com.caoccao.qjs4j.core.temporal.TemporalConstants.*;

/** ISO date and time productions shared by the Temporal string factories. */
final class IsoDateTimeParser extends IsoParsingState {
    private static final String INVALID_DATE = "Temporal error: Invalid ISO date.";
    private static final String INVALID_INSTANT = "Temporal error: Instant argument must be Instant or string.";
    private static final String INVALID_TIME = "Temporal error: Invalid time";
    private static final String INVALID_YEAR = "Temporal error: Invalid character while parsing year value.";
    private static final int NO_SECOND = -2;
    private IsoDateTimeParser(JSContext context, String input) {
        super(context, input);
    }

    private int clockFraction(boolean hasSeconds, String error) {
        if (position == input.length()) {
            return 0;
        }
        char next = input.charAt(position);
        if (next != '.' && next != ',') {
            return 0;
        }
        if (!hasSeconds) {
            context.throwRangeError(error);
            return -1;
        }
        position++;
        return readFraction(INVALID_INSTANT, INVALID_DATE);
    }

    private ParsedAnnotations endOfDateTime(boolean retainValues) {
        return position == input.length() ? ParsedAnnotations.EMPTY : readAnnotations(retainValues);
    }

    private ParsedAnnotations invalidAnnotation() {
        context.throwRangeError(INVALID_DATE);
        return null;
    }

    private int invalidDigits(String field) {
        context.throwRangeError("Temporal error: Invalid character while parsing " + field + " value.");
        return -1;
    }

    private boolean isLowercaseAnnotationKey(int start, int end) {
        if (start == end) {
            return false;
        }
        for (int index = start; index < end; index++) {
            char character = input.charAt(index);
            if (character >= 'A' && character <= 'Z') {
                return false;
            }
            if (!Character.isLetterOrDigit(character) && character != '-' && character != '_') {
                return false;
            }
        }
        return true;
    }

    private boolean isMinutePrecisionOffsetTimeZoneAnnotation(int start, int end) {
        if (end - start < 3 || !hasTwoDigits(input, start + 1) || parseFixedTwoDigits(input, start + 1) > 23) {
            return false;
        }
        if (end - start == 3) {
            return true;
        }
        int minutes = start + (input.charAt(start + 3) == ':' ? 4 : 3);
        return end == minutes + 2 && hasTwoDigits(input, minutes) && parseFixedTwoDigits(input, minutes) < 60;
    }

    private IsoOffset optionalOffset(boolean hasTime, boolean allowZulu) {
        boolean zulu = at('Z') || at('z');
        if (!zulu && !at('+') && !at('-')) {
            return IsoOffset.ZERO;
        }
        if (!hasTime || zulu && !allowZulu) {
            context.throwRangeError(INVALID_DATE);
            return null;
        }
        return parseOffset();
    }

    private IsoDate parseDate(boolean enforceRange, boolean yearMonth) {
        int year = parseYear();
        if (context.hasPendingException()) {
            return null;
        }
        boolean separated = take('-');
        int month = readTwoDigits("month");
        if (month < 0) {
            return null;
        }
        if (yearMonth && (month < 1 || month > 12)) {
            context.throwRangeError(INVALID_DATE);
            return null;
        }
        int day = 1;
        if (separated ? take('-') : atDigit()) {
            day = readTwoDigits("day");
            if (day < 0) {
                return null;
            }
        } else if (!yearMonth) {
            if (separated || at('-')) {
                context.throwRangeError(INVALID_DATE);
            } else {
                invalidDigits("day");
            }
            return null;
        }
        if (yearMonth && !separated && at('-')) {
            context.throwRangeError(INVALID_DATE);
            return null;
        }
        IsoDate parsed = new IsoDate(year, month, day);
        boolean valid = yearMonth
                ? isValidIsoYearMonthDateForParsing(year, month, day)
                : month >= 1 && month <= 12 && day >= 1 && day <= IsoDate.daysInMonth(year, month)
                        && (!enforceRange || parsed.isWithinSupportedRange());
        if (!valid) {
            context.throwRangeError(INVALID_DATE);
            return null;
        }
        return parsed;
    }

    private IsoOffset parseOffset() {
        if (position == input.length()) {
            context.throwRangeError(INVALID_INSTANT);
            return null;
        }
        char sign = input.charAt(position++);
        if (sign == 'Z' || sign == 'z') {
            return IsoOffset.ZERO;
        }
        if (sign != '+' && sign != '-') {
            context.throwRangeError(INVALID_INSTANT);
            return null;
        }
        int hour = readOffsetPair("offset hour", 23);
        if (hour < 0) {
            return null;
        }
        boolean extended = take(':');
        int minute = 0;
        int second = NO_SECOND;
        if (extended || atDigit()) {
            minute = readOffsetPair("offset minute", 59);
            if (minute < 0) {
                return null;
            }
            second = readClockSecond(extended, true);
        }
        if (second == -1) {
            return null;
        }
        int fraction = clockFraction(second != NO_SECOND, INVALID_INSTANT);
        if (fraction < 0) {
            return null;
        }
        int seconds = hour * 3600 + minute * 60 + Math.max(0, second);
        long nanos = seconds * SECOND_NANOSECONDS + fraction;
        return sign == '-'
                ? new IsoOffset(-seconds, BigInteger.valueOf(-nanos))
                : new IsoOffset(seconds, BigInteger.valueOf(nanos));
    }

    private IsoTime parseTime() {
        int hour = readTwoDigits("hour");
        if (hour < 0) {
            return null;
        }
        int minute = 0;
        int second = NO_SECOND;
        boolean extended = take(':');
        if (extended || atDigit()) {
            minute = twoDigits();
            if (minute < 0) {
                if (extended) {
                    context.throwRangeError(INVALID_TIME);
                } else {
                    invalidDigits("minute");
                }
                return null;
            }
            second = readClockSecond(extended, false);
        }
        if (second == -1) {
            return null;
        }
        int fraction = clockFraction(second != NO_SECOND, INVALID_TIME);
        if (fraction < 0) {
            return null;
        }
        if (hour > 23 || minute > 59 || second > 60) {
            context.throwRangeError(INVALID_TIME);
            return null;
        }
        return new IsoTime(hour, minute, Math.min(59, Math.max(0, second)), fraction / 1_000_000,
                fraction / 1000 % 1000, fraction % 1000);
    }

    private int parseYear() {
        char first = input.charAt(position);
        boolean signed = first == '-' || first == '+';
        if (signed) {
            position++;
        }
        int high = twoDigits();
        int low = twoDigits();
        if (high < 0 || low < 0) {
            return invalidDigits("year");
        }
        int year = high * 100 + low;
        if (signed) {
            low = twoDigits();
            if (low < 0) {
                return invalidDigits("year");
            }
            year = year * 100 + low;
            if (first == '-') {
                if (year == 0) {
                    context.throwRangeError(INVALID_DATE);
                }
                return -year;
            }
        }
        return year;
    }

    private ParsedAnnotations readAnnotations(boolean retainValues) {
        int calendars = 0;
        boolean criticalCalendar = false;
        boolean hasZone = false;
        String zone = null;
        String calendar = null;
        while (take('[')) {
            boolean critical = take('!');
            int start = position;
            int separator = -1;
            while (position < input.length()) {
                char ch = input.charAt(position);
                if (ch == ']') {
                    break;
                }
                if (ch == '=' && separator < 0) {
                    separator = position;
                }
                position++;
            }
            int end = position;
            if (end == start || !take(']')) {
                return invalidAnnotation();
            }
            if (separator < 0) {
                char first = input.charAt(start);
                if (hasZone
                        || (first == '+' || first == '-') && !isMinutePrecisionOffsetTimeZoneAnnotation(start, end)) {
                    return invalidAnnotation();
                }
                hasZone = true;
                if (retainValues) {
                    zone = input.substring(start, end);
                }
            } else {
                if (separator + 1 == end || !isLowercaseAnnotationKey(start, separator)) {
                    return invalidAnnotation();
                }
                if (separator - start == 4 && input.startsWith("u-ca", start)) {
                    calendars++;
                    criticalCalendar |= critical;
                    if (retainValues && calendars == 1) {
                        calendar = input.substring(separator + 1, end);
                    }
                } else if (critical) {
                    return invalidAnnotation();
                }
            }
        }
        if (position != input.length() || criticalCalendar && calendars > 1) {
            return invalidAnnotation();
        }
        return zone == null && calendar == null ? ParsedAnnotations.EMPTY : new ParsedAnnotations(calendar, zone);
    }

    /** Read an optional second, preserving the difference between an absent component and an explicit zero. */
    private int readClockSecond(boolean extended, boolean offset) {
        if (extended ? take(':') : atDigit()) {
            return offset ? readOffsetPair("offset second", 59) : readTwoDigits("second");
        }
        if (extended ? atDigit() : offset && at(':')) {
            context.throwRangeError(offset ? INVALID_INSTANT : INVALID_TIME);
            return -1;
        }
        return NO_SECOND;
    }

    private IsoDateTimeOffset readDateTime(boolean instant, boolean zoned) {
        IsoDate date = parseDate(true, false);
        if (date == null) {
            return null;
        }
        boolean hasTime = takeTimeSeparator();
        if (instant && !hasTime) {
            context.throwRangeError(INVALID_INSTANT);
            return null;
        }
        IsoTime time = hasTime ? parseTime() : IsoTime.MIDNIGHT;
        if (time == null) {
            return null;
        }
        IsoOffset offset = instant ? parseOffset() : optionalOffset(hasTime, zoned);
        return offset == null ? null : new IsoDateTimeOffset(date, time, offset);
    }

    private int readOffsetPair(String field, int maximum) {
        int value = readTwoDigits(field);
        if (value > maximum) {
            context.throwRangeError(INVALID_INSTANT);
            return -1;
        }
        return value;
    }

    private boolean readPlainDateSuffix() {
        boolean hasTime = takeTimeSeparator();
        return (!hasTime || parseTime() != null) && optionalOffset(hasTime, false) != null
                && endOfDateTime(false) != null;
    }

    private int readTwoDigits(String field) {
        int value = twoDigits();
        return value < 0 ? invalidDigits(field) : value;
    }

    private boolean takeTimeSeparator() {
        if (position < input.length()) {
            char next = input.charAt(position);
            if (next == 'T' || next == 't' || next == ' ') {
                position++;
                return true;
            }
        }
        return false;
    }

    private int twoDigits() {
        int start = position;
        if (start + 2 <= input.length()) {
            int tens = input.charAt(start) - '0';
            int ones = input.charAt(start + 1) - '0';
            if (tens >= 0 && tens < 10 && ones >= 0 && ones < 10) {
                position = start + 2;
                return tens * 10 + ones;
            }
        }
        return -1;
    }

    private static boolean isAmbiguousTimeStringWithoutDesignator(String input) {
        int end = input.indexOf('[');
        if (end < 0) {
            end = input.length();
        }
        if ((end == 4 || end == 5 && input.charAt(2) == '-') && hasTwoDigits(input, 0)
                && hasTwoDigits(input, end - 2)) {
            return new IsoDate(1972, parseFixedTwoDigits(input, 0), parseFixedTwoDigits(input, end - 2)).isValid();
        }
        if ((end == 6 || end == 7 && input.charAt(4) == '-') && hasTwoDigits(input, 0) && hasTwoDigits(input, 2)
                && hasTwoDigits(input, end - 2)) {
            int month = parseFixedTwoDigits(input, end - 2);
            return month >= 1 && month <= 12;
        }
        return false;
    }

    static boolean isValidIsoYearMonthDateForParsing(int year, int month, int dayOfMonth) {
        return IsoDate.isValidYearMonth(year, month) && dayOfMonth >= 1
                && dayOfMonth <= IsoDate.daysInMonth(year, month);
    }

    static IsoDate parseDateString(JSContext context, String input) {
        return readPlainDate(context, input, true, false);
    }

    static IsoZonedDateTimeOffset parseDateTime(JSContext context, String input, boolean zoned) {
        IsoDateTimeParser state = start(context, input, INVALID_YEAR);
        IsoDateTimeOffset parsed = state == null ? null : state.readDateTime(false, zoned);
        if (parsed == null) {
            return null;
        }
        ParsedAnnotations annotations = state.endOfDateTime(true);
        if (annotations == null) {
            return null;
        }
        String timeZoneId = annotations.timeZoneAnnotation();
        if (zoned && timeZoneId == null) {
            context.throwRangeError("Temporal error: Must specify time zone.");
            return null;
        }

        TemporalCalendarId calendarId = TemporalCalendarId.ISO8601;
        String calendarAnnotation = annotations.calendarAnnotation();
        if (calendarAnnotation != null) {
            calendarId = TemporalCalendarId.createFromCalendarString(context, new JSString(calendarAnnotation));
            if (context.hasPendingException()) {
                return null;
            }
        }

        return new IsoZonedDateTimeOffset(parsed.date(), parsed.time(), parsed.offset().totalSeconds(), timeZoneId,
                calendarId);
    }

    static IsoDateTimeOffset parseInstantString(JSContext context, String input) {
        IsoDateTimeParser state = start(context, input, INVALID_YEAR);
        IsoDateTimeOffset parsed = state == null ? null : state.readDateTime(true, false);
        return parsed != null && state.endOfDateTime(false) != null ? parsed : null;
    }

    static IsoDate parseMonthDayString(JSContext context, String input) {
        if (input == null || input.isEmpty()) {
            context.throwRangeError("Temporal error: Invalid character while parsing month value.");
            return null;
        }

        int start = input.startsWith("--") ? 2 : 0;
        boolean extended = input.length() > start + 2 && input.charAt(start + 2) == '-';
        int dayStart = start + (extended ? 3 : 2);
        if (hasTwoDigits(input, start) && hasTwoDigits(input, dayStart)
                && (start == 2 || extended || input.length() == 4 || input.charAt(4) == '[')) {
            IsoDate date = new IsoDate(1972, parseFixedTwoDigits(input, start), parseFixedTwoDigits(input, dayStart));
            if (!date.isValid() || input.indexOf('\u2212') >= 0) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            IsoDateTimeParser state = new IsoDateTimeParser(context, input);
            state.position = dayStart + 2;
            return state.readPlainDateSuffix() ? date : null;
        }

        IsoDate date = readPlainDate(context, input, false, false);
        return date == null ? null : new IsoDate(1972, date.month(), date.day());
    }

    static IsoTime parseTimeString(JSContext context, String input) {
        IsoDateTimeParser state = start(context, input, INVALID_TIME);
        if (state == null) {
            return null;
        }
        if (!state.take('T') && !state.take('t')) {
            if (state.parseDate(true, false) != null && !context.hasPendingException()) {
                if (!state.takeTimeSeparator()) {
                    context.throwRangeError(INVALID_DATE);
                    return null;
                }
            } else {
                context.clearPendingException();
                state.position = 0;
                if (isAmbiguousTimeStringWithoutDesignator(input)) {
                    context.throwRangeError(INVALID_DATE);
                    return null;
                }
            }
        }
        IsoTime time = state.parseTime();
        return time != null && state.optionalOffset(true, false) != null && state.endOfDateTime(false) != null
                ? time
                : null;
    }

    static IsoDate parseYearMonthString(JSContext context, String input) {
        return readPlainDate(context, input, false, true);
    }

    private static IsoDate readPlainDate(JSContext context, String input, boolean enforceRange, boolean yearMonth) {
        IsoDateTimeParser state = start(context, input, INVALID_YEAR);
        if (state == null) {
            return null;
        }
        IsoDate date = state.parseDate(enforceRange, yearMonth);
        if (date == null || !state.readPlainDateSuffix()) {
            return null;
        }
        return date;
    }

    private static IsoDateTimeParser start(JSContext context, String input, String emptyError) {
        if (input == null || input.isEmpty()) {
            context.throwRangeError(emptyError);
            return null;
        }
        if (input.indexOf('\u2212') >= 0) {
            context.throwRangeError(INVALID_DATE);
            return null;
        }
        return new IsoDateTimeParser(context, input);
    }
    record ParsedAnnotations(String calendarAnnotation, String timeZoneAnnotation) {
        static final ParsedAnnotations EMPTY = new ParsedAnnotations(null, null);
    }
}
