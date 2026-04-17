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

import java.math.BigInteger;
import java.util.Locale;

/**
 * Internal data type representing a Temporal.Duration's component fields.
 */
public record TemporalDuration(
        long years, long months, long weeks, long days,
        long hours, long minutes, long seconds,
        long milliseconds, long microseconds, long nanoseconds) {

    public static final TemporalDuration ZERO = new TemporalDuration(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public TemporalDuration abs() {
        return new TemporalDuration(
                Math.abs(years), Math.abs(months), Math.abs(weeks), Math.abs(days),
                Math.abs(hours), Math.abs(minutes), Math.abs(seconds),
                Math.abs(milliseconds), Math.abs(microseconds), Math.abs(nanoseconds));
    }

    /**
     * Adds this duration's time components to the given IsoTime, returning
     * the new time and day carry. Mirrors Rust's TimeDuration.addToTime().
     * <p>
     * Fast path: pure {@code long} arithmetic via {@link Math#addExact};
     * falls back to BigInteger on overflow.
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
     * Returns total nanoseconds for days through nanoseconds.
     * Fast path uses {@code long} arithmetic; falls back to BigInteger on overflow.
     */
    public BigInteger dayTimeNanoseconds() {
        try {
            long dayTimeLong = Math.addExact(
                    Math.multiplyExact(days, TemporalConstants.DAY_NANOSECONDS),
                    timeNanosecondsLong());
            return BigInteger.valueOf(dayTimeLong);
        } catch (ArithmeticException overflow) {
            return BigInteger.valueOf(days).multiply(TemporalConstants.BI_DAY_NANOSECONDS)
                    .add(timeNanosecondsSlow());
        }
    }

    public boolean isBlank() {
        return years == 0 && months == 0 && weeks == 0 && days == 0 &&
                hours == 0 && minutes == 0 && seconds == 0 &&
                milliseconds == 0 && microseconds == 0 && nanoseconds == 0;
    }

    public boolean isValid() {
        // All non-zero fields must have the same sign
        boolean hasPositive = years > 0 || months > 0 || weeks > 0 || days > 0
                || hours > 0 || minutes > 0 || seconds > 0
                || milliseconds > 0 || microseconds > 0 || nanoseconds > 0;
        boolean hasNegative = years < 0 || months < 0 || weeks < 0 || days < 0
                || hours < 0 || minutes < 0 || seconds < 0
                || milliseconds < 0 || microseconds < 0 || nanoseconds < 0;
        return !hasPositive || !hasNegative;
    }

    public TemporalDuration negated() {
        return new TemporalDuration(
                -years, -months, -weeks, -days,
                -hours, -minutes, -seconds,
                -milliseconds, -microseconds, -nanoseconds);
    }

    public int sign() {
        if (years > 0 || months > 0 || weeks > 0 || days > 0 ||
                hours > 0 || minutes > 0 || seconds > 0 ||
                milliseconds > 0 || microseconds > 0 || nanoseconds > 0) {
            return 1;
        }
        if (years < 0 || months < 0 || weeks < 0 || days < 0 ||
                hours < 0 || minutes < 0 || seconds < 0 ||
                milliseconds < 0 || microseconds < 0 || nanoseconds < 0) {
            return -1;
        }
        return 0;
    }

    /**
     * Returns total nanoseconds for hours through nanoseconds (excluding days).
     * Fast path uses {@code long} arithmetic; falls back to BigInteger on overflow.
     */
    public BigInteger timeNanoseconds() {
        try {
            return BigInteger.valueOf(timeNanosecondsLong());
        } catch (ArithmeticException overflow) {
            return timeNanosecondsSlow();
        }
    }

    /**
     * Fast-path time nanosecond computation using {@code long} arithmetic.
     * Throws {@link ArithmeticException} if the result exceeds long range.
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
     * Slow-path time nanosecond computation using BigInteger arithmetic.
     * Handles values that exceed long range.
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
        boolean negative = years < 0 || months < 0 || weeks < 0 || days < 0
                || hours < 0 || minutes < 0 || seconds < 0
                || milliseconds < 0 || microseconds < 0 || nanoseconds < 0;
        BigInteger yearsValue = BigInteger.valueOf(years);
        BigInteger monthsValue = BigInteger.valueOf(months);
        BigInteger weeksValue = BigInteger.valueOf(weeks);
        BigInteger daysValue = BigInteger.valueOf(days);
        BigInteger hoursValue = BigInteger.valueOf(hours);
        BigInteger minutesValue = BigInteger.valueOf(minutes);
        BigInteger secondsValue = BigInteger.valueOf(seconds);
        BigInteger millisecondsValue = BigInteger.valueOf(milliseconds);
        BigInteger microsecondsValue = BigInteger.valueOf(microseconds);
        BigInteger nanosecondsValue = BigInteger.valueOf(nanoseconds);
        if (negative) {
            yearsValue = yearsValue.abs();
            monthsValue = monthsValue.abs();
            weeksValue = weeksValue.abs();
            daysValue = daysValue.abs();
            hoursValue = hoursValue.abs();
            minutesValue = minutesValue.abs();
            secondsValue = secondsValue.abs();
            millisecondsValue = millisecondsValue.abs();
            microsecondsValue = microsecondsValue.abs();
            nanosecondsValue = nanosecondsValue.abs();
        }

        StringBuilder stringBuilder = new StringBuilder();
        if (negative) {
            stringBuilder.append('-');
        }
        stringBuilder.append('P');

        if (yearsValue.signum() != 0) {
            stringBuilder.append(yearsValue).append('Y');
        }
        if (monthsValue.signum() != 0) {
            stringBuilder.append(monthsValue).append('M');
        }
        if (weeksValue.signum() != 0) {
            stringBuilder.append(weeksValue).append('W');
        }
        if (daysValue.signum() != 0) {
            stringBuilder.append(daysValue).append('D');
        }

        BigInteger totalSubsecondNanoseconds = millisecondsValue.multiply(BigInteger.valueOf(1_000_000L))
                .add(microsecondsValue.multiply(BigInteger.valueOf(1_000L)))
                .add(nanosecondsValue);
        BigInteger[] secondCarryAndSubsecondNanoseconds =
                totalSubsecondNanoseconds.divideAndRemainder(BigInteger.valueOf(1_000_000_000L));
        BigInteger secondsWithCarry = secondsValue.add(secondCarryAndSubsecondNanoseconds[0]);
        BigInteger subsecondNanosecondsRemainder = secondCarryAndSubsecondNanoseconds[1];

        boolean hasTimePart = hoursValue.signum() != 0 || minutesValue.signum() != 0 || secondsWithCarry.signum() != 0
                || subsecondNanosecondsRemainder.signum() != 0;
        if (hasTimePart) {
            stringBuilder.append('T');
            if (hoursValue.signum() != 0) {
                stringBuilder.append(hoursValue).append('H');
            }
            if (minutesValue.signum() != 0) {
                stringBuilder.append(minutesValue).append('M');
            }
            if (secondsWithCarry.signum() != 0 || subsecondNanosecondsRemainder.signum() != 0) {
                stringBuilder.append(secondsWithCarry);
                if (subsecondNanosecondsRemainder.signum() != 0) {
                    String fractional = String.format(Locale.ROOT, "%09d", subsecondNanosecondsRemainder.intValue());
                    int fractionalEndIndex = fractional.length();
                    while (fractionalEndIndex > 0 && fractional.charAt(fractionalEndIndex - 1) == '0') {
                        fractionalEndIndex--;
                    }
                    stringBuilder.append('.').append(fractional, 0, fractionalEndIndex);
                }
                stringBuilder.append('S');
            }
        }

        if (stringBuilder.length() == 1 || (stringBuilder.length() == 2 && negative)) {
            stringBuilder.append("T0S");
        }

        return stringBuilder.toString();
    }
}
