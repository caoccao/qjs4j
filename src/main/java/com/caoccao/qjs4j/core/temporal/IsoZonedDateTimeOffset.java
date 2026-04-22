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

public record IsoZonedDateTimeOffset(
        IsoDate date,
        IsoTime time,
        int offsetSeconds,
        String timeZoneId,
        TemporalCalendarId calendarId) {
    public static IsoZonedDateTimeOffset parseZonedDateTimeString(JSContext context, String input) {
        if (input == null || input.isEmpty()) {
            context.throwRangeError("Temporal error: Invalid character while parsing year value.");
            return null;
        }
        if (input.indexOf('\u2212') >= 0) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        IsoParsingState parsingState = new IsoParsingState(input);
        IsoDate date = parsingState.parseDate(context);
        if (date == null) {
            return null;
        }
        IsoTime time = IsoTime.MIDNIGHT;
        boolean hasTimePart = false;
        if (parsingState.position() < parsingState.inputLength()
                && (parsingState.current() == 'T' || parsingState.current() == 't' || parsingState.current() == ' ')) {
            hasTimePart = true;
            parsingState.advanceOne();
            time = parsingState.parseInstantTime(context);
            if (time == null) {
                return null;
            }
        }
        int offsetSeconds = 0;
        if (parsingState.position() < parsingState.inputLength()) {
            char marker = parsingState.input().charAt(parsingState.position());
            if (marker == 'Z' || marker == 'z' || marker == '+' || marker == '-') {
                if (!hasTimePart) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                IsoOffset parsedOffset = parsingState.parseInstantOffsetNanoseconds(context);
                if (parsedOffset == null || context.hasPendingException()) {
                    return null;
                }
                offsetSeconds = parsedOffset.totalSeconds();
            }
        }

        IsoParsingState.ParsedAnnotations parsedAnnotations = parsingState.parseInstantAnnotations(context);
        if (context.hasPendingException() || parsedAnnotations == null) {
            return null;
        }
        if (parsingState.position() != parsingState.inputLength()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        String timeZoneId = parsedAnnotations.timeZoneAnnotation();
        if (timeZoneId == null) {
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

        return new IsoZonedDateTimeOffset(date, time, offsetSeconds, timeZoneId, calendarId);
    }
}
