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

package com.caoccao.qjs4j.core.temporal.duration;

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.temporal.TemporalConstants;
import com.caoccao.qjs4j.core.temporal.TemporalError;
import com.caoccao.qjs4j.core.temporal.TemporalUnit;
import com.caoccao.qjs4j.core.temporal.format.TemporalDurationToStringOptions;
import com.caoccao.qjs4j.core.temporal.iso.IsoDateTime;
import com.caoccao.qjs4j.core.temporal.iso.IsoDateTimeFormat;
import com.caoccao.qjs4j.core.temporal.iso.IsoDurationParser;
import com.caoccao.qjs4j.core.temporal.iso.IsoTime;
import com.caoccao.qjs4j.core.temporal.rounding.TemporalRoundingMode;
import com.caoccao.qjs4j.core.temporal.timezone.TemporalTimeZone;
import com.caoccao.qjs4j.core.temporal.timezone.TemporalZonedDateTimeComputation;

import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * Internal data type representing a Temporal.Duration's component fields.
 */
public record TemporalDuration(long years, long months, long weeks, long days, long hours, long minutes, long seconds,
        long milliseconds, long microseconds, long nanoseconds) {
    public static final TemporalDuration ZERO = new TemporalDuration(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    private static final long FLOAT64_SAFE_INTEGER_MAX = 9_007_199_254_740_991L;
    private static final BigInteger NS_MAX_INSTANT = new BigInteger("8640000000000000000000");
    private static final BigInteger NS_MIN_INSTANT = new BigInteger("-8640000000000000000000");

    public static TemporalDuration createBalance(BigInteger totalNanoseconds, TemporalUnit largestUnit) {
        long factor = largestUnit.getNanosecondFactor();
        if (factor <= 1) {
            return new TemporalDuration(0, 0, 0, 0, 0, 0, 0, 0, 0, totalNanoseconds.longValue());
        }
        long whole;
        long remainder;
        if (totalNanoseconds.bitLength() <= 63) {
            long total = totalNanoseconds.longValue();
            whole = total / factor;
            remainder = total % factor;
        } else {
            BigInteger[] parts = totalNanoseconds.divideAndRemainder(BigInteger.valueOf(factor));
            whole = parts[0].longValue();
            remainder = parts[1].longValue();
        }

        // The signed remainder is smaller than a day, even when the largest component exceeds a long.
        long days = largestUnit == TemporalUnit.DAY ? whole : 0;
        long hours = largestUnit == TemporalUnit.HOUR ? whole : remainder / TemporalConstants.HOUR_NANOSECONDS;
        long minutes = largestUnit == TemporalUnit.MINUTE
                ? whole
                : remainder / TemporalConstants.MINUTE_NANOSECONDS % 60;
        long seconds = largestUnit == TemporalUnit.SECOND
                ? whole
                : remainder / TemporalConstants.SECOND_NANOSECONDS % 60;
        long milliseconds = largestUnit == TemporalUnit.MILLISECOND
                ? whole
                : remainder / TemporalConstants.MILLISECOND_NANOSECONDS % 1000;
        long microseconds = largestUnit == TemporalUnit.MICROSECOND
                ? whole
                : remainder / TemporalConstants.MICROSECOND_NANOSECONDS % 1000;
        long nanoseconds = remainder % 1000;
        return new TemporalDuration(0, 0, 0, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds);
    }

    public static TemporalDuration differenceEpochNanoseconds(BigInteger startEpochNanoseconds,
            BigInteger endEpochNanoseconds, TemporalUnit largestUnit, long smallestUnitNanoseconds,
            long roundingIncrement, TemporalRoundingMode roundingMode) {
        BigInteger differenceNanoseconds = endEpochNanoseconds.subtract(startEpochNanoseconds);
        BigInteger incrementNanoseconds = BigInteger.valueOf(smallestUnitNanoseconds)
                .multiply(BigInteger.valueOf(roundingIncrement));
        BigInteger roundedNanoseconds = roundingMode.roundBigIntegerToIncrementSigned(differenceNanoseconds,
                incrementNanoseconds);
        return createBalance(roundedNanoseconds, largestUnit);
    }

    public static boolean isDurationRecordTimeRangeValid(TemporalDuration durationRecord) {
        BigInteger totalNanoseconds = durationRecord.dayTimeNanoseconds();
        return totalNanoseconds.abs().compareTo(TemporalConstants.MAX_ABSOLUTE_TIME_NANOSECONDS) <= 0;
    }

    private static boolean isFloat64RepresentableLong(long value) {
        if (value >= -FLOAT64_SAFE_INTEGER_MAX && value <= FLOAT64_SAFE_INTEGER_MAX) {
            return true;
        } else {
            return (long) ((double) value) == value;
        }
    }

    public static TemporalDuration parseDurationString(JSContext context, String input) {
        if (input == null || input.isEmpty()) {
            return context.rejectRangeError("Temporal error: Invalid duration string.");
        }
        return IsoDurationParser.parse(context, input);
    }

    public TemporalDuration abs() {
        return new TemporalDuration(Math.abs(years), Math.abs(months), Math.abs(weeks), Math.abs(days), Math.abs(hours),
                Math.abs(minutes), Math.abs(seconds), Math.abs(milliseconds), Math.abs(microseconds),
                Math.abs(nanoseconds));
    }

    public TemporalZonedDateTimeComputation addDurationToZonedDateTime(JSContext context,
            TemporalRelativeToOption relativeToOption) {
        LocalDateTime dateBalancedDateTime = relativeToOption.startDateTime().plusYears(years).plusMonths(months)
                .plusWeeks(weeks).plusDays(days);
        BigInteger intermediateEpochNanoseconds;
        if (dateBalancedDateTime.equals(relativeToOption.startDateTime())) {
            intermediateEpochNanoseconds = relativeToOption.epochNanoseconds();
        } else {
            intermediateEpochNanoseconds = IsoDateTime.zonedLocalDateTimeToEpochNanoseconds(context, relativeToOption,
                    dateBalancedDateTime);
        }
        if (context.hasPendingException() || intermediateEpochNanoseconds == null) {
            return null;
        }

        BigInteger timeNanoseconds = timeNanoseconds();
        BigInteger endEpochNanoseconds = intermediateEpochNanoseconds.add(timeNanoseconds);
        if (endEpochNanoseconds.compareTo(NS_MIN_INSTANT) < 0 || endEpochNanoseconds.compareTo(NS_MAX_INSTANT) > 0) {
            return TemporalError.DURATION_FIELD_OUT_OF_RANGE.reject(context);
        }

        IsoDateTime endIsoDateTime = IsoDateTime.createFromEpochNsAndTimeZoneId(endEpochNanoseconds,
                relativeToOption.timeZoneId());
        LocalDateTime endDateTime = endIsoDateTime.toLocalDateTime();
        int endOffsetSeconds = TemporalTimeZone.getOffsetSecondsFor(endEpochNanoseconds, relativeToOption.timeZoneId());
        return new TemporalZonedDateTimeComputation(endDateTime, endEpochNanoseconds, endOffsetSeconds);
    }

    /**
     * Adds this duration's time components to the given IsoTime, returning the new time and day carry. Mirrors Rust's
     * TimeDuration.addToTime().
     * <p>
     * Fast path: pure {@code long} arithmetic via {@link Math#addExact}; falls back to BigInteger on overflow.
     *
     * @return result with normalized time and day overflow, or null on overflow
     */
    public TemporalTimeAddResult addToTime(IsoTime time) {
        // Fast path: long arithmetic (covers virtually all real-world durations).
        try {
            long durationNs = timeNanosecondsLong();
            long totalNs = Math.addExact(time.totalNanoseconds(), durationNs);
            long dayCarry = Math.floorDiv(totalNs, TemporalConstants.DAY_NANOSECONDS);
            long remainder = Math.floorMod(totalNs, TemporalConstants.DAY_NANOSECONDS);
            return new TemporalTimeAddResult(IsoTime.createFromNanoseconds(remainder), dayCarry);
        } catch (ArithmeticException overflow) {
            // Fallback: BigInteger path for extreme durations that exceed long range.
        }

        BigInteger durationTimeNs = timeNanoseconds();
        BigInteger totalNs = BigInteger.valueOf(time.totalNanoseconds()).add(durationTimeNs);
        BigInteger[] dayAndRemainder = totalNs.divideAndRemainder(TemporalConstants.BI_DAY_NANOSECONDS);
        BigInteger dayCarryBig = dayAndRemainder[0];
        BigInteger remainder = dayAndRemainder[1];
        if (remainder.signum() < 0) {
            remainder = remainder.add(TemporalConstants.BI_DAY_NANOSECONDS);
            dayCarryBig = dayCarryBig.subtract(BigInteger.ONE);
        }
        try {
            long dayCarry = dayCarryBig.longValueExact();
            IsoTime normalizedTime = IsoTime.createFromNanoseconds(remainder.longValue());
            return new TemporalTimeAddResult(normalizedTime, dayCarry);
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /**
     * Returns total nanoseconds for days through nanoseconds. Fast path uses {@code long} arithmetic; falls back to
     * BigInteger on overflow.
     */
    public BigInteger dayTimeNanoseconds() {
        try {
            long dayTimeLong = Math.addExact(Math.multiplyExact(days, TemporalConstants.DAY_NANOSECONDS),
                    timeNanosecondsLong());
            return BigInteger.valueOf(dayTimeLong);
        } catch (ArithmeticException overflow) {
            return BigInteger.valueOf(days).multiply(TemporalConstants.BI_DAY_NANOSECONDS).add(timeNanosecondsSlow());
        }
    }

    public String formatWithPrecision(TemporalDurationToStringOptions options) {
        return options.precisionAuto() ? toString() : formatDuration(options.fractionalSecondDigits(), sign() < 0);
    }

    public boolean hasAnyDateUnits() {
        return years != 0 || months != 0 || weeks != 0 || days != 0;
    }

    public boolean hasCalendarUnits() {
        return years != 0 || months != 0 || weeks != 0;
    }

    public boolean hasTimeUnits() {
        return hours != 0 || minutes != 0 || seconds != 0 || milliseconds != 0 || microseconds != 0 || nanoseconds != 0;
    }

    public boolean isBlank() {
        return years == 0 && months == 0 && weeks == 0 && days == 0 && hours == 0 && minutes == 0 && seconds == 0
                && milliseconds == 0 && microseconds == 0 && nanoseconds == 0;
    }

    public boolean isValid() {
        // All non-zero fields must have the same sign
        boolean hasPositive = years > 0 || months > 0 || weeks > 0 || days > 0 || hours > 0 || minutes > 0
                || seconds > 0 || milliseconds > 0 || microseconds > 0 || nanoseconds > 0;
        boolean hasNegative = years < 0 || months < 0 || weeks < 0 || days < 0 || hours < 0 || minutes < 0
                || seconds < 0 || milliseconds < 0 || microseconds < 0 || nanoseconds < 0;
        return !hasPositive || !hasNegative;
    }

    public TemporalUnit largestDayTimeUnit() {
        return largestDayTimeUnit(TemporalUnit.SECOND);
    }

    public TemporalUnit largestUnitOfDuration() {
        if (years != 0L) {
            return TemporalUnit.YEAR;
        } else if (months != 0L) {
            return TemporalUnit.MONTH;
        } else if (weeks != 0L) {
            return TemporalUnit.WEEK;
        }
        return largestDayTimeUnit(TemporalUnit.NANOSECOND);
    }

    public TemporalDuration negated() {
        return new TemporalDuration(-years, -months, -weeks, -days, -hours, -minutes, -seconds, -milliseconds,
                -microseconds, -nanoseconds);
    }

    public TemporalDuration normalizeFloat64RepresentableFields() {
        boolean allFieldsAlreadyRepresentable = isFloat64RepresentableLong(years) && isFloat64RepresentableLong(months)
                && isFloat64RepresentableLong(weeks) && isFloat64RepresentableLong(days)
                && isFloat64RepresentableLong(hours) && isFloat64RepresentableLong(minutes)
                && isFloat64RepresentableLong(seconds) && isFloat64RepresentableLong(milliseconds)
                && isFloat64RepresentableLong(microseconds) && isFloat64RepresentableLong(nanoseconds);
        if (allFieldsAlreadyRepresentable) {
            return this;
        }

        return new TemporalDuration((long) ((double) years), (long) ((double) months), (long) ((double) weeks),
                (long) ((double) days), (long) ((double) hours), (long) ((double) minutes), (long) ((double) seconds),
                (long) ((double) milliseconds), (long) ((double) microseconds), (long) ((double) nanoseconds));
    }

    public int sign() {
        if (years > 0 || months > 0 || weeks > 0 || days > 0 || hours > 0 || minutes > 0 || seconds > 0
                || milliseconds > 0 || microseconds > 0 || nanoseconds > 0) {
            return 1;
        }
        if (years < 0 || months < 0 || weeks < 0 || days < 0 || hours < 0 || minutes < 0 || seconds < 0
                || milliseconds < 0 || microseconds < 0 || nanoseconds < 0) {
            return -1;
        }
        return 0;
    }

    /**
     * Returns total nanoseconds for hours through nanoseconds (excluding days). Fast path uses {@code long} arithmetic;
     * falls back to BigInteger on overflow.
     */
    public BigInteger timeNanoseconds() {
        try {
            return BigInteger.valueOf(timeNanosecondsLong());
        } catch (ArithmeticException overflow) {
            return timeNanosecondsSlow();
        }
    }

    /**
     * Fast-path time nanosecond computation using {@code long} arithmetic. Throws {@link ArithmeticException} if the
     * result exceeds long range.
     */
    private long timeNanosecondsLong() {
        long total = Math.multiplyExact(hours, TemporalConstants.HOUR_NANOSECONDS);
        total = Math.addExact(total, Math.multiplyExact(minutes, TemporalConstants.MINUTE_NANOSECONDS));
        total = Math.addExact(total, Math.multiplyExact(seconds, TemporalConstants.SECOND_NANOSECONDS));
        total = Math.addExact(total, Math.multiplyExact(milliseconds, TemporalConstants.MILLISECOND_NANOSECONDS));
        total = Math.addExact(total, Math.multiplyExact(microseconds, TemporalConstants.MICROSECOND_NANOSECONDS));
        total = Math.addExact(total, nanoseconds);
        return total;
    }

    /**
     * Slow-path time nanosecond computation using BigInteger arithmetic. Handles values that exceed long range.
     */
    private BigInteger timeNanosecondsSlow() {
        return BigInteger.valueOf(hours).multiply(TemporalConstants.BI_HOUR_NANOSECONDS)
                .add(BigInteger.valueOf(minutes).multiply(TemporalConstants.BI_MINUTE_NANOSECONDS))
                .add(BigInteger.valueOf(seconds).multiply(TemporalConstants.BI_SECOND_NANOSECONDS))
                .add(BigInteger.valueOf(milliseconds).multiply(TemporalConstants.BI_MILLISECOND_NANOSECONDS))
                .add(BigInteger.valueOf(microseconds).multiply(TemporalConstants.BI_MICROSECOND_NANOSECONDS))
                .add(BigInteger.valueOf(nanoseconds));
    }

    @Override
    public String toString() {
        return formatDuration(-1, years < 0 || months < 0 || weeks < 0 || days < 0 || hours < 0 || minutes < 0
                || seconds < 0 || milliseconds < 0 || microseconds < 0 || nanoseconds < 0);
    }

    /** Components in constructor argument order, from years through nanoseconds. */
    public long[] toComponents() {
        return new long[]{years, months, weeks, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds};
    }

    public static TemporalDuration fromComponents(long[] components) {
        return new TemporalDuration(components[0], components[1], components[2], components[3], components[4],
                components[5], components[6], components[7], components[8], components[9]);
    }

    private String formatDuration(int fractionalDigits, boolean negative) {
        StringBuilder result = new StringBuilder();
        if (negative) {
            result.append('-');
        }
        result.append('P');
        appendMagnitude(result, years, 'Y');
        appendMagnitude(result, months, 'M');
        appendMagnitude(result, weeks, 'W');
        appendMagnitude(result, days, 'D');

        BigInteger secondsValue = BigInteger.valueOf(seconds).abs();
        BigInteger millisecondsValue = BigInteger.valueOf(milliseconds).abs();
        BigInteger microsecondsValue = BigInteger.valueOf(microseconds).abs();
        BigInteger nanosecondsValue = BigInteger.valueOf(nanoseconds).abs();

        BigInteger totalSubsecondNanoseconds = millisecondsValue.multiply(TemporalConstants.BI_MILLISECOND_NANOSECONDS)
                .add(microsecondsValue.multiply(TemporalConstants.BI_MICROSECOND_NANOSECONDS)).add(nanosecondsValue);
        BigInteger[] secondCarryAndRemainder = totalSubsecondNanoseconds
                .divideAndRemainder(TemporalConstants.BI_SECOND_NANOSECONDS);
        BigInteger secondsWithCarry = secondsValue.add(secondCarryAndRemainder[0]);
        BigInteger subsecondNanosecondsRemainder = secondCarryAndRemainder[1];

        int nanos = subsecondNanosecondsRemainder.intValue();
        boolean showSeconds = fractionalDigits >= 0 || secondsWithCarry.signum() != 0 || nanos != 0;
        if (hours != 0 || minutes != 0 || showSeconds) {
            result.append('T');
            appendMagnitude(result, hours, 'H');
            appendMagnitude(result, minutes, 'M');
            if (showSeconds) {
                result.append(secondsWithCarry);
                IsoDateTimeFormat.appendFraction(result, nanos, fractionalDigits);
                result.append('S');
            }
        } else if (years == 0 && months == 0 && weeks == 0 && days == 0) {
            result.append("T0S");
        }
        return result.toString();
    }

    private static void appendMagnitude(StringBuilder result, long value, char unit) {
        if (value == Long.MIN_VALUE) {
            result.append(BigInteger.valueOf(value).abs()).append(unit);
        } else if (value != 0) {
            result.append(Math.abs(value)).append(unit);
        }
    }

    private TemporalUnit largestDayTimeUnit(TemporalUnit fallback) {
        if (days != 0L) {
            return TemporalUnit.DAY;
        } else if (hours != 0L) {
            return TemporalUnit.HOUR;
        } else if (minutes != 0L) {
            return TemporalUnit.MINUTE;
        } else if (seconds != 0L) {
            return TemporalUnit.SECOND;
        } else if (milliseconds != 0L) {
            return TemporalUnit.MILLISECOND;
        } else if (microseconds != 0L) {
            return TemporalUnit.MICROSECOND;
        } else if (nanoseconds != 0L) {
            return TemporalUnit.NANOSECOND;
        } else {
            return fallback;
        }
    }

    public long wholeTimeDays() {
        try {
            return timeNanosecondsLong() / TemporalConstants.DAY_NANOSECONDS;
        } catch (ArithmeticException overflow) {
            return timeNanosecondsSlow().divide(TemporalConstants.BI_DAY_NANOSECONDS).longValueExact();
        }
    }
}
