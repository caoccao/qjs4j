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

/**
 * Internal data type representing a Temporal.Duration's component fields.
 */
public record TemporalDurationRecord(long years, long months, long weeks, long days,
                                     long hours, long minutes, long seconds,
                                     long milliseconds, long microseconds, long nanoseconds) {

    public static final TemporalDurationRecord ZERO = new TemporalDurationRecord(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public TemporalDurationRecord abs() {
        return new TemporalDurationRecord(
                Math.abs(years), Math.abs(months), Math.abs(weeks), Math.abs(days),
                Math.abs(hours), Math.abs(minutes), Math.abs(seconds),
                Math.abs(milliseconds), Math.abs(microseconds), Math.abs(nanoseconds));
    }

    public boolean isBlank() {
        return years == 0 && months == 0 && weeks == 0 && days == 0 &&
                hours == 0 && minutes == 0 && seconds == 0 &&
                milliseconds == 0 && microseconds == 0 && nanoseconds == 0;
    }

    public boolean isValid() {
        // All non-zero fields must have the same sign
        int positive = 0;
        int negative = 0;
        long[] fields = {years, months, weeks, days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds};
        for (long f : fields) {
            if (f > 0) positive++;
            if (f < 0) negative++;
        }
        return positive == 0 || negative == 0;
    }

    public TemporalDurationRecord negated() {
        return new TemporalDurationRecord(
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

    @Override
    public String toString() {
        return TemporalUtils.formatDurationString(years, months, weeks, days,
                hours, minutes, seconds, milliseconds, microseconds, nanoseconds);
    }

    /**
     * Returns total nanoseconds for the days+time portion only (no years/months/weeks).
     */
    public long totalNanoseconds() {
        return days * 86_400_000_000_000L
                + hours * 3_600_000_000_000L
                + minutes * 60_000_000_000L
                + seconds * 1_000_000_000L
                + milliseconds * 1_000_000L
                + microseconds * 1_000L
                + nanoseconds;
    }
}
