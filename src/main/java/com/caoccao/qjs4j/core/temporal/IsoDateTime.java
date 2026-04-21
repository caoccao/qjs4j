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
import java.time.*;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;

/**
 * Represents an ISO 8601 date-time combining IsoDate and IsoTime.
 */
public record IsoDateTime(IsoDate date, IsoTime time) implements Comparable<IsoDateTime> {
    public static final IsoDateTime MIN_SUPPORTED = new IsoDateTime(new IsoDate(-271821, 4, 20), IsoTime.MIDNIGHT);
    public static final IsoDateTime MAX_SUPPORTED = new IsoDateTime(
            IsoDate.MAX_SUPPORTED,
            new IsoTime(23, 59, 59, 999, 999, 999));

    public static IsoDateTime createByEpochNs(BigInteger epochNanoseconds) {
        BigInteger[] secondAndNanosecond = epochNanoseconds.divideAndRemainder(TemporalConstants.BI_BILLION);
        long epochSeconds = secondAndNanosecond[0].longValueExact();
        int nanosecondAdjustment = secondAndNanosecond[1].intValue();
        if (nanosecondAdjustment < 0) {
            epochSeconds--;
            nanosecondAdjustment += 1_000_000_000;
        }
        Instant instant = Instant.ofEpochSecond(epochSeconds, nanosecondAdjustment);
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        return createFromLocalDateTime(localDateTime);
    }

    public static IsoDateTime createFromEpochNsAndTimeZoneId(BigInteger epochNanoseconds, String timeZoneId) {
        int offsetSeconds = TemporalTimeZone.getOffsetSecondsFor(epochNanoseconds, timeZoneId);
        BigInteger offsetNanoseconds = BigInteger.valueOf(offsetSeconds).multiply(TemporalConstants.BI_BILLION);
        return createByEpochNs(epochNanoseconds.add(offsetNanoseconds));
    }

    public static IsoDateTime createFromLocalDateTime(LocalDateTime localDateTime) {
        int nanosecondOfSecond = localDateTime.getNano();
        int millisecond = nanosecondOfSecond / 1_000_000;
        int microsecond = (nanosecondOfSecond / 1_000) % 1_000;
        int nanosecond = nanosecondOfSecond % 1_000;
        IsoDate isoDate = new IsoDate(
                localDateTime.getYear(),
                localDateTime.getMonthValue(),
                localDateTime.getDayOfMonth());
        IsoTime isoTime = new IsoTime(
                localDateTime.getHour(),
                localDateTime.getMinute(),
                localDateTime.getSecond(),
                millisecond,
                microsecond,
                nanosecond);
        return new IsoDateTime(isoDate, isoTime);
    }

    public static IsoDateTime createFromZonedDateTime(ZonedDateTime zonedDateTime) {
        return createFromLocalDateTime(zonedDateTime.toLocalDateTime());
    }

    @Override
    public int compareTo(IsoDateTime otherIsoDateTime) {
        int dateCompare = date.compareTo(otherIsoDateTime.date);
        if (dateCompare != 0) {
            return dateCompare;
        }
        return time.compareTo(otherIsoDateTime.time);
    }

    public BigInteger toEpochNs(String timeZoneId, String disambiguation) {
        return toEpochNs(timeZoneId, disambiguation, null);
    }

    public BigInteger toEpochNs(
            String timeZoneId,
            String disambiguation,
            Integer preferredOffsetSeconds) {
        Integer fixedOffsetSeconds = TemporalTimeZone.parseFixedOffsetSeconds(timeZoneId);
        if (fixedOffsetSeconds != null) {
            return TemporalTimeZone.utcDateTimeToEpochNs(date, time, fixedOffsetSeconds);
        }

        LocalDateTime localDateTime = toLocalDateTime();
        ZoneId zoneId = TemporalTimeZone.resolveTimeZone(timeZoneId);
        ZoneRules zoneRules = zoneId.getRules();
        List<ZoneOffset> validOffsets = zoneRules.getValidOffsets(localDateTime);

        Instant instant;
        if (validOffsets.size() == 1) {
            instant = localDateTime.atOffset(validOffsets.get(0)).toInstant();
        } else if (validOffsets.size() == 2) {
            ZoneOffset firstOffset = validOffsets.get(0);
            ZoneOffset secondOffset = validOffsets.get(1);
            if (preferredOffsetSeconds != null) {
                if (firstOffset.getTotalSeconds() == preferredOffsetSeconds.intValue()) {
                    instant = localDateTime.atOffset(firstOffset).toInstant();
                    return BigInteger.valueOf(instant.getEpochSecond()).multiply(TemporalConstants.BI_BILLION)
                            .add(BigInteger.valueOf(instant.getNano()));
                } else if (secondOffset.getTotalSeconds() == preferredOffsetSeconds.intValue()) {
                    instant = localDateTime.atOffset(secondOffset).toInstant();
                    return BigInteger.valueOf(instant.getEpochSecond()).multiply(TemporalConstants.BI_BILLION)
                            .add(BigInteger.valueOf(instant.getNano()));
                }
            }
            Instant firstInstant = localDateTime.atOffset(firstOffset).toInstant();
            Instant secondInstant = localDateTime.atOffset(secondOffset).toInstant();
            Instant earlierInstant;
            if (firstInstant.isBefore(secondInstant)) {
                earlierInstant = firstInstant;
            } else {
                earlierInstant = secondInstant;
            }
            Instant laterInstant;
            if (firstInstant.isAfter(secondInstant)) {
                laterInstant = firstInstant;
            } else {
                laterInstant = secondInstant;
            }

            if (TemporalDisambiguation.REJECT.matches(disambiguation)) {
                throw new DateTimeException("Ambiguous local time for time zone: " + timeZoneId);
            } else if (TemporalDisambiguation.LATER.matches(disambiguation)) {
                instant = laterInstant;
            } else {
                instant = earlierInstant;
            }
        } else {
            ZoneOffsetTransition transition = zoneRules.getTransition(localDateTime);
            if (transition == null) {
                throw new DateTimeException("Invalid local time for time zone: " + timeZoneId);
            }
            if (TemporalDisambiguation.REJECT.matches(disambiguation)) {
                throw new DateTimeException("Invalid local time for time zone: " + timeZoneId);
            }

            Duration gapDuration = transition.getDuration().abs();
            if (TemporalDisambiguation.EARLIER.matches(disambiguation)) {
                LocalDateTime shiftedLocalDateTime = localDateTime.minusSeconds(gapDuration.getSeconds());
                instant = shiftedLocalDateTime.atOffset(transition.getOffsetBefore()).toInstant();
            } else {
                LocalDateTime shiftedLocalDateTime = localDateTime.plusSeconds(gapDuration.getSeconds());
                instant = shiftedLocalDateTime.atOffset(transition.getOffsetAfter()).toInstant();
            }
        }

        return BigInteger.valueOf(instant.getEpochSecond()).multiply(TemporalConstants.BI_BILLION)
                .add(BigInteger.valueOf(instant.getNano()));
    }

    public BigInteger toEpochNs(String timeZoneId) {
        return toEpochNs(timeZoneId, "compatible");
    }

    public IsoDateTime withClampedSecondToValidRange() {
        IsoTime clampedTime = time.clampSecondToValidRange();
        if (clampedTime == time) {
            return this;
        } else {
            return new IsoDateTime(date, clampedTime);
        }
    }

    public IsoDateTime withDate(IsoDate isoDate) {
        return new IsoDateTime(isoDate, time);
    }

    public IsoDateTime withTime(IsoTime isoTime) {
        return new IsoDateTime(date, isoTime);
    }

    public boolean isWithinSupportedRange() {
        return compareTo(MIN_SUPPORTED) >= 0 && compareTo(MAX_SUPPORTED) <= 0;
    }

    public LocalDateTime toLocalDateTime() {
        return LocalDateTime.of(
                date.year(),
                date.month(),
                date.day(),
                time.hour(),
                time.minute(),
                time.second(),
                time.totalNanosecondsWithinSecond());
    }

    public LocalDateTime toLocalDateTimeWithClampedSecond() {
        IsoTime clampedTime = time.clampSecondToValidRange();
        return LocalDateTime.of(
                date.year(),
                date.month(),
                date.day(),
                clampedTime.hour(),
                clampedTime.minute(),
                clampedTime.second(),
                clampedTime.totalNanosecondsWithinSecond());
    }

    @Override
    public String toString() {
        return date.toString() + "T" + time.toString();
    }
}
