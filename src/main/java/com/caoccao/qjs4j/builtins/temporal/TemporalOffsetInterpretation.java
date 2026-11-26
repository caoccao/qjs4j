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

package com.caoccao.qjs4j.builtins.temporal;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.temporal.*;
import java.math.BigInteger;
import java.time.*;

/** Resolve a wall time and its explicit offset against the offsets allowed by a time zone. */
final class TemporalOffsetInterpretation {
    private TemporalOffsetInterpretation() {
    }

    private static BigInteger computeWallEpochNanoseconds(JSContext context, IsoDate isoDate, IsoTime isoTime,
            String timeZoneId, String disambiguation) {
        if (!isoDate.isWithinInstantDateTimeRange(isoTime)) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        try {
            return isoDate.atTime(isoTime).toEpochNs(timeZoneId, disambiguation);
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
    }

    private static BigInteger findMatchingEpochNanosecondsForExplicitOffset(JSContext context, IsoDate isoDate,
            IsoTime isoTime, String timeZoneId, int explicitOffsetSeconds, boolean offsetTimeZoneIdentifier,
            boolean allowMinuteRounding) {
        if (offsetTimeZoneIdentifier) {
            int timeZoneOffsetSeconds = TemporalTimeZone.parseOffsetSeconds(timeZoneId);
            if (matches(timeZoneOffsetSeconds, explicitOffsetSeconds, allowMinuteRounding)) {
                return TemporalTimeZone.utcDateTimeToEpochNs(isoDate, isoTime, timeZoneOffsetSeconds);
            } else {
                return null;
            }
        }

        try {
            return selectMatchingEpochNanosecondsForExplicitOffset(isoDate, isoTime, timeZoneId, explicitOffsetSeconds,
                    allowMinuteRounding);
        } catch (DateTimeException dateTimeException) {
            context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
            return null;
        }
    }

    static BigInteger interpretOffset(JSContext context, IsoDate isoDate, IsoTime isoTime, String timeZoneId,
            Integer explicitOffsetSeconds, boolean hasExplicitOffset, boolean hasZuluOffset, boolean stringInput,
            boolean stringOffsetIncludesSecondsOrFraction, TemporalZonedDateTimeOptions options) {
        boolean offsetTimeZoneIdentifier = isOffsetTimeZoneIdentifier(timeZoneId);

        BigInteger epochNanoseconds;
        String option = options.offset();
        if (hasExplicitOffset && hasZuluOffset) {
            epochNanoseconds = TemporalTimeZone.utcDateTimeToEpochNs(isoDate, isoTime, 0);
        } else if (!hasExplicitOffset || "ignore".equals(option)) {
            epochNanoseconds = resolveEpochNanosecondsIgnoringOffset(context, isoDate, isoTime, timeZoneId,
                    offsetTimeZoneIdentifier, options.disambiguation());
        } else if ("use".equals(option)) {
            epochNanoseconds = TemporalTimeZone.utcDateTimeToEpochNs(isoDate, isoTime,
                    explicitOffsetSeconds == null ? 0 : explicitOffsetSeconds);
        } else {
            if (!isoDate.isWithinInstantDateTimeRange(isoTime)) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            epochNanoseconds = findMatchingEpochNanosecondsForExplicitOffset(context, isoDate, isoTime, timeZoneId,
                    explicitOffsetSeconds == null ? 0 : explicitOffsetSeconds, offsetTimeZoneIdentifier,
                    stringInput && !stringOffsetIncludesSecondsOrFraction);
            if (epochNanoseconds == null && !context.hasPendingException()) {
                if ("reject".equals(option)) {
                    context.throwRangeError("Temporal error: Invalid offset.");
                    return null;
                }
                epochNanoseconds = resolveEpochNanosecondsIgnoringOffset(context, isoDate, isoTime, timeZoneId,
                        offsetTimeZoneIdentifier, options.disambiguation());
            }
        }
        if (context.hasPendingException() || epochNanoseconds == null) {
            return null;
        }
        if (!TemporalUtils.isValidEpochNanoseconds(epochNanoseconds)) {
            context.throwRangeError("Temporal error: Nanoseconds out of range.");
            return null;
        }

        return epochNanoseconds;
    }

    static boolean isOffsetTimeZoneIdentifier(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.length() < 3) {
            return false;
        }
        char signCharacter = timeZoneId.charAt(0);
        if (signCharacter != '+' && signCharacter != '-' && signCharacter != '\u2212') {
            return false;
        }

        int hours;
        int minutes;
        if (timeZoneId.length() == 6 && timeZoneId.charAt(3) == ':') {
            if (!Character.isDigit(timeZoneId.charAt(1)) || !Character.isDigit(timeZoneId.charAt(2))
                    || !Character.isDigit(timeZoneId.charAt(4)) || !Character.isDigit(timeZoneId.charAt(5))) {
                return false;
            }
            hours = Integer.parseInt(timeZoneId.substring(1, 3));
            minutes = Integer.parseInt(timeZoneId.substring(4, 6));
        } else if (timeZoneId.length() == 5) {
            if (!Character.isDigit(timeZoneId.charAt(1)) || !Character.isDigit(timeZoneId.charAt(2))
                    || !Character.isDigit(timeZoneId.charAt(3)) || !Character.isDigit(timeZoneId.charAt(4))) {
                return false;
            }
            hours = Integer.parseInt(timeZoneId.substring(1, 3));
            minutes = Integer.parseInt(timeZoneId.substring(3, 5));
        } else {
            return false;
        }

        return hours <= 23 && minutes <= 59;
    }

    static boolean matches(int actualSeconds, int parsedSeconds, boolean minutePrecision) {
        return (minutePrecision
                ? TemporalUtils.roundOffsetSecondsToMinute(actualSeconds)
                : actualSeconds) == parsedSeconds;
    }

    /** Pick the earliest matching instant when the local time occurs twice. */
    static ZoneOffset matchingOffset(LocalDateTime local, String timeZoneId, int parsedSeconds,
            boolean minutePrecision) {
        ZoneOffset selected = null;
        for (ZoneOffset offset : TemporalTimeZone.resolveTimeZone(timeZoneId).getRules().getValidOffsets(local)) {
            if (matches(offset.getTotalSeconds(), parsedSeconds, minutePrecision)
                    && (selected == null || offset.getTotalSeconds() > selected.getTotalSeconds())) {
                selected = offset;
            }
        }
        return selected;
    }

    static String normalizeTimeZoneIdentifier(JSContext context, String timeZoneText) {
        String normalizedTimeZoneId = TemporalTimeZone.parseTimeZoneIdentifierString(context, timeZoneText);
        if (context.hasPendingException() || normalizedTimeZoneId == null) {
            return null;
        }

        if (TemporalTimeZone.isValidTimeZoneOffsetWithoutSeconds(timeZoneText)) {
            return TemporalTimeZone.formatOffset(TemporalTimeZone.parseOffsetSeconds(timeZoneText));
        }
        if (TemporalTimeZone.isValidTimeZoneOffsetWithoutSeconds(normalizedTimeZoneId)) {
            return TemporalTimeZone.formatOffset(TemporalTimeZone.parseOffsetSeconds(normalizedTimeZoneId));
        }
        return normalizedTimeZoneId;
    }

    private static BigInteger resolveEpochNanosecondsIgnoringOffset(JSContext context, IsoDate isoDate, IsoTime isoTime,
            String timeZoneId, boolean offsetTimeZoneIdentifier, String disambiguation) {
        if (offsetTimeZoneIdentifier) {
            int timeZoneOffsetSeconds = TemporalTimeZone.parseOffsetSeconds(timeZoneId);
            return TemporalTimeZone.utcDateTimeToEpochNs(isoDate, isoTime, timeZoneOffsetSeconds);
        } else {
            return computeWallEpochNanoseconds(context, isoDate, isoTime, timeZoneId, disambiguation);
        }
    }

    static String resolveTimeZoneIdentifier(JSContext context, String text) {
        String timeZoneId = normalizeTimeZoneIdentifier(context, text);
        if (timeZoneId == null || context.hasPendingException()) {
            return null;
        }
        if (!isOffsetTimeZoneIdentifier(timeZoneId)) {
            try {
                TemporalTimeZone.resolveTimeZone(timeZoneId);
            } catch (DateTimeException invalidTimeZoneException) {
                context.throwRangeError("Temporal error: Invalid time zone: " + timeZoneId);
                return null;
            }
        }
        return timeZoneId;
    }

    private static BigInteger selectMatchingEpochNanosecondsForExplicitOffset(IsoDate isoDate, IsoTime isoTime,
            String timeZoneId, int parsedOffsetSeconds, boolean allowMinuteRounding) {
        int second = isoTime.second();
        BigInteger leapSecondNanoseconds = BigInteger.ZERO;
        if (second == 60) {
            leapSecondNanoseconds = BigInteger.valueOf(1_000_000_000L);
        }

        LocalDateTime localDateTime = isoDate.atTime(isoTime).withClampedSecondToValidRange().toLocalDateTime();
        ZoneOffset offset = matchingOffset(localDateTime, timeZoneId, parsedOffsetSeconds, allowMinuteRounding);
        if (offset == null) {
            return null;
        }
        Instant instant = localDateTime.atOffset(offset).toInstant();
        return BigInteger.valueOf(instant.getEpochSecond()).multiply(TemporalConstants.BI_SECOND_NANOSECONDS)
                .add(BigInteger.valueOf(instant.getNano())).add(leapSecondNanoseconds);
    }
}
