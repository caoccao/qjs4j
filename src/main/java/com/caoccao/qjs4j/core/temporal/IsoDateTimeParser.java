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

/** ISO date and time productions shared by the Temporal string factories. */
final class IsoDateTimeParser {
    private IsoDateTimeParser() {
    }

    static IsoDate parseDateString(JSContext context, String input) {
        return parseDateString(context, input, true);
    }

    private static IsoDate parseDateString(JSContext context, String input, boolean enforceIsoDateRange) {
        IsoParsingState parsingState = IsoParsingState.start(context, input,
                "Temporal error: Invalid character while parsing year value.");
        if (parsingState == null) {
            return null;
        }
        IsoDate date = parsingState.parseDate(context, enforceIsoDateRange);
        if (date == null) {
            return null;
        }

        return parseDateSuffix(context, parsingState) ? date : null;
    }

    private static boolean parseDateSuffix(JSContext context, IsoParsingState parsingState) {
        boolean hasTime = parsingState.consumeTimeSeparator();
        if (hasTime && parsingState.parseInstantTime(context) == null) {
            return false;
        }
        return parsingState.parseOptionalOffset(context, hasTime, false) != null
                && parsingState.finishAnnotations(context) != null;
    }

    static IsoZonedDateTimeOffset parseDateTime(JSContext context, String input, boolean zoned) {
        IsoParsingState parsingState = IsoParsingState.start(context, input,
                "Temporal error: Invalid character while parsing year value.");
        if (parsingState == null) {
            return null;
        }
        IsoDate date = parsingState.parseDate(context);
        if (date == null) {
            return null;
        }
        boolean hasTime = parsingState.consumeTimeSeparator();
        IsoTime time = hasTime ? parsingState.parseInstantTime(context) : IsoTime.MIDNIGHT;
        if (time == null) {
            return null;
        }
        IsoOffset offset = parsingState.parseOptionalOffset(context, hasTime, zoned);
        if (offset == null) {
            return null;
        }
        IsoParsingState.ParsedAnnotations parsedAnnotations = parsingState.finishAnnotations(context);
        if (parsedAnnotations == null) {
            return null;
        }

        String timeZoneId = parsedAnnotations.timeZoneAnnotation();
        if (zoned && timeZoneId == null) {
            context.throwRangeError("Temporal error: Must specify time zone.");
            return null;
        }

        TemporalCalendarId calendarId = TemporalCalendarId.ISO8601;
        String calendarAnnotation = parsedAnnotations.calendarAnnotation();
        if (calendarAnnotation != null) {
            calendarId = TemporalCalendarId.createFromCalendarString(context, new JSString(calendarAnnotation));
            if (context.hasPendingException()) {
                return null;
            }
        }

        return new IsoZonedDateTimeOffset(date, time, offset.totalSeconds(), timeZoneId, calendarId);
    }

    static IsoDateTimeOffset parseInstantString(JSContext context, String input) {
        IsoParsingState parsingState = IsoParsingState.start(context, input,
                "Temporal error: Invalid character while parsing year value.");
        if (parsingState == null) {
            return null;
        }
        IsoDate date = parsingState.parseDate(context);
        if (date == null) {
            return null;
        }
        if (!parsingState.consumeTimeSeparator()) {
            context.throwRangeError("Temporal error: Instant argument must be Instant or string.");
            return null;
        }
        IsoTime time = parsingState.parseInstantTime(context);
        if (time == null) {
            return null;
        }
        IsoOffset parsedOffset = parsingState.parseInstantOffsetNanoseconds(context);
        if (parsedOffset == null || context.hasPendingException()) {
            return null;
        }
        if (parsingState.finishAnnotations(context) == null) {
            return null;
        }
        return new IsoDateTimeOffset(date, time, parsedOffset);
    }

    static IsoDate parseMonthDayString(JSContext context, String input) {
        if (input == null || input.isEmpty()) {
            context.throwRangeError("Temporal error: Invalid character while parsing month value.");
            return null;
        }

        int start = input.startsWith("--") ? 2 : 0;
        boolean extended = input.length() > start + 2 && input.charAt(start + 2) == '-';
        int dayStart = start + (extended ? 3 : 2);
        if (IsoParsingState.hasTwoDigits(input, start) && IsoParsingState.hasTwoDigits(input, dayStart)
                && (start == 2 || extended || input.length() == 4 || input.charAt(4) == '[')) {
            IsoDate date = new IsoDate(1972, IsoParsingState.parseFixedTwoDigits(input, start),
                    IsoParsingState.parseFixedTwoDigits(input, dayStart));
            if (!date.isValid() || input.indexOf('\u2212') >= 0) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            IsoParsingState state = new IsoParsingState(input);
            state.setPosition(dayStart + 2);
            return parseDateSuffix(context, state) ? date : null;
        }

        IsoDate date = parseDateString(context, input, false);
        return date == null ? null : new IsoDate(1972, date.month(), date.day());
    }

    static IsoTime parseTimeString(JSContext context, String input) {
        IsoParsingState parsingState = IsoParsingState.start(context, input, "Temporal error: Invalid time");
        if (parsingState == null) {
            return null;
        }
        IsoTime time;
        if (parsingState.position() < parsingState.inputLength()
                && (parsingState.current() == 'T' || parsingState.current() == 't')) {
            parsingState.advanceOne();
            time = parsingState.parseInstantTime(context);
        } else {
            int initialPosition = parsingState.position();
            IsoDate parsedDate = parsingState.parseDate(context);
            if (parsedDate != null && !context.hasPendingException()) {
                if (!parsingState.consumeTimeSeparator()) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                time = parsingState.parseInstantTime(context);
            } else {
                context.clearPendingException();
                parsingState.setPosition(initialPosition);
                if (IsoParsingState.isAmbiguousTimeStringWithoutDesignator(input)) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                time = parsingState.parseInstantTime(context);
            }
        }
        if (time == null || context.hasPendingException()) {
            return null;
        }

        return parsingState.parseOptionalOffset(context, true, false) != null
                && parsingState.finishAnnotations(context) != null ? time : null;
    }

    static IsoDate parseYearMonthString(JSContext context, String input) {
        IsoParsingState parsingState = IsoParsingState.start(context, input,
                "Temporal error: Invalid character while parsing year value.");
        if (parsingState == null) {
            return null;
        }

        int year = parsingState.parseYear(context);
        if (context.hasPendingException()) {
            return null;
        }

        boolean hasSeparator = parsingState.position() < parsingState.inputLength()
                && parsingState.input().charAt(parsingState.position()) == '-';
        if (hasSeparator) {
            parsingState.advanceOne();
        }

        int month = parsingState.parseTwoDigits(context, "month");
        if (context.hasPendingException()) {
            return null;
        }
        if (month < 1 || month > 12) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        int dayOfMonth = 1;
        if (hasSeparator) {
            if (parsingState.position() < parsingState.inputLength()
                    && parsingState.input().charAt(parsingState.position()) == '-') {
                parsingState.advanceOne();
                dayOfMonth = parsingState.parseTwoDigits(context, "day");
                if (context.hasPendingException()) {
                    return null;
                }
            }
        } else {
            if (parsingState.position() < parsingState.inputLength()
                    && IsoParsingState.isAsciiDigit(parsingState.input().charAt(parsingState.position()))) {
                dayOfMonth = parsingState.parseTwoDigits(context, "day");
                if (context.hasPendingException()) {
                    return null;
                }
            }
            if (parsingState.position() < parsingState.inputLength()
                    && parsingState.input().charAt(parsingState.position()) == '-') {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        }

        if (!IsoParsingState.isValidIsoYearMonthDateForParsing(year, month, dayOfMonth)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        return parseDateSuffix(context, parsingState) ? new IsoDate(year, month, dayOfMonth) : null;
    }
}
