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

import java.util.Locale;

/**
 * Represents an ISO 8601 time with hour, minute, second, millisecond, microsecond, and nanosecond components.
 */
public record IsoTime(int hour, int minute, int second, int millisecond, int microsecond, int nanosecond)
        implements Comparable<IsoTime> {

    public static final IsoTime MIDNIGHT = new IsoTime(0, 0, 0, 0, 0, 0);

    /**
     * Creates an IsoTime from total nanoseconds (mod 24 hours).
     */
    public static IsoTime createFromNanoseconds(long totalNanoseconds) {
        totalNanoseconds = Math.floorMod(totalNanoseconds, TemporalConstants.DAY_NANOSECONDS);
        int hourValue = (int) (totalNanoseconds / TemporalConstants.HOUR_NANOSECONDS);
        totalNanoseconds %= TemporalConstants.HOUR_NANOSECONDS;
        int minuteValue = (int) (totalNanoseconds / TemporalConstants.MINUTE_NANOSECONDS);
        totalNanoseconds %= TemporalConstants.MINUTE_NANOSECONDS;
        int secondValue = (int) (totalNanoseconds / TemporalConstants.SECOND_NANOSECONDS);
        totalNanoseconds %= TemporalConstants.SECOND_NANOSECONDS;
        int millisecondValue = (int) (totalNanoseconds / TemporalConstants.MILLISECOND_NANOSECONDS);
        totalNanoseconds %= TemporalConstants.MILLISECOND_NANOSECONDS;
        int microsecondValue = (int) (totalNanoseconds / TemporalConstants.MICROSECOND_NANOSECONDS);
        int nanosecondValue = (int) (totalNanoseconds % TemporalConstants.MICROSECOND_NANOSECONDS);
        return new IsoTime(hourValue, minuteValue, secondValue, millisecondValue, microsecondValue, nanosecondValue);
    }

    public static IsoTime createNormalized(int hour, int minute, int second, int millisecond, int microsecond, int nanosecond) {
        hour = Math.max(0, Math.min(23, hour));
        minute = Math.max(0, Math.min(59, minute));
        second = Math.max(0, Math.min(59, second));
        millisecond = Math.max(0, Math.min(999, millisecond));
        microsecond = Math.max(0, Math.min(999, microsecond));
        nanosecond = Math.max(0, Math.min(999, nanosecond));
        return new IsoTime(hour, minute, second, millisecond, microsecond, nanosecond);
    }

    public static IsoTime parseTimeString(JSContext context, String input) {
        if (input == null || input.isEmpty()) {
            context.throwRangeError("Temporal error: Invalid time");
            return null;
        }
        if (input.indexOf('\u2212') >= 0) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }

        IsoParsingState parsingState = new IsoParsingState(input);
        IsoTime time;
        if (parsingState.position() < parsingState.inputLength()
                && (parsingState.current() == 'T' || parsingState.current() == 't')) {
            parsingState.advanceOne();
            time = parsingState.parseInstantTime(context);
        } else {
            int initialPosition = parsingState.position();
            IsoDate parsedDate = parsingState.parseDate(context);
            if (parsedDate != null && !context.hasPendingException()) {
                if (parsingState.position() >= parsingState.inputLength()) {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                char separator = parsingState.current();
                if (separator != 'T' && separator != 't' && separator != ' ') {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
                parsingState.advanceOne();
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

        if (parsingState.position() < parsingState.inputLength()) {
            char marker = parsingState.current();
            if (marker == 'Z' || marker == 'z') {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            if (marker == '+' || marker == '-') {
                IsoOffset parsedOffset = parsingState.parseInstantOffsetNanoseconds(context);
                if (parsedOffset == null || context.hasPendingException()) {
                    return null;
                }
            }
        }

        parsingState.parseInstantAnnotations(context);
        if (context.hasPendingException()) {
            return null;
        }
        if (parsingState.position() != parsingState.inputLength()) {
            context.throwRangeError("Temporal error: Invalid ISO date.");
            return null;
        }
        return time;
    }

    public IsoTime clampSecondToValidRange() {
        if (second == 60) {
            return new IsoTime(hour, minute, 59, millisecond, microsecond, nanosecond);
        } else {
            return this;
        }
    }

    @Override
    public int compareTo(IsoTime otherIsoTime) {
        if (hour != otherIsoTime.hour) {
            return Integer.compare(hour, otherIsoTime.hour);
        }
        if (minute != otherIsoTime.minute) {
            return Integer.compare(minute, otherIsoTime.minute);
        }
        if (second != otherIsoTime.second) {
            return Integer.compare(second, otherIsoTime.second);
        }
        if (millisecond != otherIsoTime.millisecond) {
            return Integer.compare(millisecond, otherIsoTime.millisecond);
        }
        if (microsecond != otherIsoTime.microsecond) {
            return Integer.compare(microsecond, otherIsoTime.microsecond);
        }
        return Integer.compare(nanosecond, otherIsoTime.nanosecond);
    }

    /**
     * Formats the fractional seconds part (ms, us, ns) to the given number of digits.
     * Returns empty string if digits <= 0.
     */
    public String formatFractionalPart(int digits) {
        if (digits <= 0) {
            return "";
        }
        String nineDigits = String.format(Locale.ROOT, "%03d%03d%03d",
                millisecond, microsecond, nanosecond);
        return nineDigits.substring(0, digits);
    }

    /**
     * Formats this time as a string with configurable precision.
     * Shared by PlainTime, PlainDateTime, and ZonedDateTime toString operations.
     *
     * @param smallestUnit               the smallest unit to include (e.g. "minute" truncates seconds)
     * @param autoFractionalSecondDigits if true, auto-trim trailing zeros from fractional part
     * @param fractionalSecondDigits     number of fractional digits (0-9), ignored if auto
     */
    public String formatTimeString(String smallestUnit, boolean autoFractionalSecondDigits, int fractionalSecondDigits) {
        String hourMinute = String.format(Locale.ROOT, "%02d:%02d", hour, minute);
        if ("minute".equals(smallestUnit)) {
            return hourMinute;
        }

        String hourMinuteSecond = String.format(Locale.ROOT, "%s:%02d", hourMinute, second);
        if (autoFractionalSecondDigits) {
            String fullFraction = String.format(Locale.ROOT, "%03d%03d%03d",
                    millisecond, microsecond, nanosecond);
            int fractionEndIndex = fullFraction.length();
            while (fractionEndIndex > 0 && fullFraction.charAt(fractionEndIndex - 1) == '0') {
                fractionEndIndex--;
            }
            if (fractionEndIndex == 0) {
                return hourMinuteSecond;
            }
            return hourMinuteSecond + "." + fullFraction.substring(0, fractionEndIndex);
        }

        if (fractionalSecondDigits == 0) {
            return hourMinuteSecond;
        }
        return hourMinuteSecond + "." + formatFractionalPart(fractionalSecondDigits);
    }

    public boolean isValid() {
        if (hour < 0 || hour > 23) {
            return false;
        }
        if (minute < 0 || minute > 59) {
            return false;
        }
        if (second < 0 || second > 59) {
            return false;
        }
        if (millisecond < 0 || millisecond > 999) {
            return false;
        }
        if (microsecond < 0 || microsecond > 999) {
            return false;
        }
        return nanosecond >= 0 && nanosecond <= 999;
    }

    public String toString(Integer fractionalSecondDigits) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(String.format(
                Locale.ROOT,
                "%02d:%02d:%02d",
                hour(),
                minute(),
                second()));
        int totalFractionalNanoseconds =
                millisecond() * 1_000_000 + microsecond() * 1_000 + nanosecond();
        if (fractionalSecondDigits == null) {
            if (totalFractionalNanoseconds != 0) {
                String fraction = String.format(Locale.ROOT, "%09d", totalFractionalNanoseconds);
                int fractionEndIndex = fraction.length();
                while (fractionEndIndex > 0 && fraction.charAt(fractionEndIndex - 1) == '0') {
                    fractionEndIndex--;
                }
                stringBuilder.append('.').append(fraction, 0, fractionEndIndex);
            }
        } else if (fractionalSecondDigits > 0) {
            String fraction = String.format(Locale.ROOT, "%09d", totalFractionalNanoseconds);
            stringBuilder.append('.').append(fraction, 0, fractionalSecondDigits);
        }
        return stringBuilder.toString();
    }

    @Override
    public String toString() {
        return toString(null);
    }

    /**
     * Returns total nanoseconds from midnight.
     */
    public long totalNanoseconds() {
        return ((long) hour * TemporalConstants.HOUR_NANOSECONDS)
                + ((long) minute * TemporalConstants.MINUTE_NANOSECONDS)
                + ((long) second * TemporalConstants.SECOND_NANOSECONDS)
                + ((long) millisecond * TemporalConstants.MILLISECOND_NANOSECONDS)
                + ((long) microsecond * TemporalConstants.MICROSECOND_NANOSECONDS)
                + nanosecond;
    }

    public int totalNanosecondsWithinSecond() {
        return millisecond * 1_000_000 + microsecond * 1_000 + nanosecond;
    }
}
