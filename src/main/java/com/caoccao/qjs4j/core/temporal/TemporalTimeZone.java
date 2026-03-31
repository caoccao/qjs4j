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
import java.util.Locale;

/**
 * TimeZone operations backed by java.time.
 */
public final class TemporalTimeZone {
    private static final BigInteger BILLION = BigInteger.valueOf(1_000_000_000L);

    private TemporalTimeZone() {
    }

    /**
     * Converts epoch nanoseconds to a local IsoDateTime in the given timezone.
     */
    public static IsoDateTime epochNsToDateTimeInZone(BigInteger epochNs, String timeZoneId) {
        Instant javaInstant = toJavaInstant(epochNs);
        ZonedDateTime zdt = javaInstant.atZone(resolveTimeZone(timeZoneId));
        return fromZonedDateTime(zdt);
    }

    /**
     * Converts epoch nanoseconds to a UTC IsoDateTime.
     */
    public static IsoDateTime epochNsToUtcDateTime(BigInteger epochNs) {
        Instant javaInstant = toJavaInstant(epochNs);
        LocalDateTime ldt = LocalDateTime.ofInstant(javaInstant, ZoneOffset.UTC);
        return fromLocalDateTime(ldt);
    }

    /**
     * Formats an offset in seconds as ±HH:MM.
     */
    public static String formatOffset(int totalSeconds) {
        String sign = totalSeconds >= 0 ? "+" : "-";
        int abs = Math.abs(totalSeconds);
        int hours = abs / 3600;
        int minutes = (abs % 3600) / 60;
        return String.format(Locale.ROOT, "%s%02d:%02d", sign, hours, minutes);
    }

    private static IsoDateTime fromLocalDateTime(LocalDateTime ldt) {
        IsoDate date = new IsoDate(ldt.getYear(), ldt.getMonthValue(), ldt.getDayOfMonth());
        int nano = ldt.getNano();
        IsoTime time = new IsoTime(ldt.getHour(), ldt.getMinute(), ldt.getSecond(),
                nano / 1_000_000, (nano / 1_000) % 1000, nano % 1000);
        return new IsoDateTime(date, time);
    }

    private static IsoDateTime fromZonedDateTime(ZonedDateTime zdt) {
        IsoDate date = new IsoDate(zdt.getYear(), zdt.getMonthValue(), zdt.getDayOfMonth());
        int nano = zdt.getNano();
        IsoTime time = new IsoTime(zdt.getHour(), zdt.getMinute(), zdt.getSecond(),
                nano / 1_000_000, (nano / 1_000) % 1000, nano % 1000);
        return new IsoDateTime(date, time);
    }

    /**
     * Gets the number of real hours in a day at the given instant and timezone.
     */
    public static int getHoursInDay(BigInteger epochNs, String timeZoneId) {
        ZoneId zone = resolveTimeZone(timeZoneId);
        Instant javaInstant = toJavaInstant(epochNs);
        ZonedDateTime zdt = javaInstant.atZone(zone);
        ZonedDateTime startOfDay = zdt.toLocalDate().atStartOfDay(zone);
        ZonedDateTime startOfNextDay = zdt.toLocalDate().plusDays(1).atStartOfDay(zone);
        long seconds = Duration.between(startOfDay, startOfNextDay).getSeconds();
        return (int) (seconds / 3600);
    }

    /**
     * Gets the next timezone transition after the given instant.
     * Returns null if no further transition.
     */
    public static BigInteger getNextTransition(BigInteger epochNs, String timeZoneId) {
        ZoneId zone = resolveTimeZone(timeZoneId);
        Instant javaInstant = toJavaInstant(epochNs);
        ZoneOffsetTransition transition = zone.getRules().nextTransition(javaInstant);
        if (transition == null) return null;
        Instant transInstant = transition.getInstant();
        return BigInteger.valueOf(transInstant.getEpochSecond()).multiply(BILLION)
                .add(BigInteger.valueOf(transInstant.getNano()));
    }

    /**
     * Gets the offset in seconds for the given instant and timezone.
     */
    public static int getOffsetSecondsFor(BigInteger epochNs, String timeZoneId) {
        ZoneId zone = resolveTimeZone(timeZoneId);
        Instant javaInstant = toJavaInstant(epochNs);
        ZoneOffset offset = zone.getRules().getOffset(javaInstant);
        return offset.getTotalSeconds();
    }

    /**
     * Gets the previous timezone transition before the given instant.
     * Returns null if no previous transition.
     */
    public static BigInteger getPreviousTransition(BigInteger epochNs, String timeZoneId) {
        ZoneId zone = resolveTimeZone(timeZoneId);
        Instant javaInstant = toJavaInstant(epochNs);
        ZoneOffsetTransition transition = zone.getRules().previousTransition(javaInstant);
        if (transition == null) return null;
        Instant transInstant = transition.getInstant();
        return BigInteger.valueOf(transInstant.getEpochSecond()).multiply(BILLION)
                .add(BigInteger.valueOf(transInstant.getNano()));
    }

    /**
     * Converts a local date-time in a timezone to epoch nanoseconds using 'compatible' disambiguation.
     */
    public static BigInteger localDateTimeToEpochNs(IsoDateTime dt, String timeZoneId) {
        return localDateTimeToEpochNs(dt, timeZoneId, "compatible");
    }

    /**
     * Converts a local date-time in a timezone to epoch nanoseconds using the specified disambiguation.
     */
    public static BigInteger localDateTimeToEpochNs(IsoDateTime dt, String timeZoneId, String disambiguation) {
        LocalDateTime ldt = LocalDateTime.of(
                dt.date().year(), dt.date().month(), dt.date().day(),
                dt.time().hour(), dt.time().minute(), dt.time().second(),
                dt.time().millisecond() * 1_000_000 + dt.time().microsecond() * 1_000 + dt.time().nanosecond());
        ZoneId zoneId = resolveTimeZone(timeZoneId);
        ZoneRules zoneRules = zoneId.getRules();
        List<ZoneOffset> validOffsets = zoneRules.getValidOffsets(ldt);

        Instant instant;
        if (validOffsets.size() == 1) {
            instant = ldt.atOffset(validOffsets.get(0)).toInstant();
        } else if (validOffsets.size() == 2) {
            ZoneOffset firstOffset = validOffsets.get(0);
            ZoneOffset secondOffset = validOffsets.get(1);
            Instant firstInstant = ldt.atOffset(firstOffset).toInstant();
            Instant secondInstant = ldt.atOffset(secondOffset).toInstant();
            Instant earlierInstant = firstInstant.isBefore(secondInstant) ? firstInstant : secondInstant;
            Instant laterInstant = firstInstant.isAfter(secondInstant) ? firstInstant : secondInstant;

            if ("reject".equals(disambiguation)) {
                throw new DateTimeException("Ambiguous local time for time zone: " + timeZoneId);
            } else if ("later".equals(disambiguation)) {
                instant = laterInstant;
            } else {
                instant = earlierInstant;
            }
        } else {
            ZoneOffsetTransition transition = zoneRules.getTransition(ldt);
            if (transition == null) {
                throw new DateTimeException("Invalid local time for time zone: " + timeZoneId);
            }
            if ("reject".equals(disambiguation)) {
                throw new DateTimeException("Invalid local time for time zone: " + timeZoneId);
            }

            Duration gapDuration = transition.getDuration().abs();
            if ("earlier".equals(disambiguation)) {
                LocalDateTime shiftedLocalDateTime = ldt.minusSeconds(gapDuration.getSeconds());
                instant = shiftedLocalDateTime.atOffset(transition.getOffsetBefore()).toInstant();
            } else {
                LocalDateTime shiftedLocalDateTime = ldt.plusSeconds(gapDuration.getSeconds());
                instant = shiftedLocalDateTime.atOffset(transition.getOffsetAfter()).toInstant();
            }
        }

        return BigInteger.valueOf(instant.getEpochSecond()).multiply(BILLION)
                .add(BigInteger.valueOf(instant.getNano()));
    }

    /**
     * Resolves a ZoneId string, validating it exists.
     */
    public static ZoneId resolveTimeZone(String timeZoneId) {
        try {
            return ZoneId.of(timeZoneId);
        } catch (DateTimeException ignored) {
            ZoneOffset zoneOffset = ZoneOffset.of(timeZoneId);
            if (zoneOffset.getTotalSeconds() % 60 != 0) {
                throw new DateTimeException("Invalid sub-minute offset zone: " + timeZoneId);
            }
            return zoneOffset;
        }
    }

    private static Instant toJavaInstant(BigInteger epochNs) {
        BigInteger[] secAndNano = epochNs.divideAndRemainder(BILLION);
        long seconds = secAndNano[0].longValueExact();
        int nanoAdjust = secAndNano[1].intValue();
        if (nanoAdjust < 0) {
            seconds--;
            nanoAdjust += 1_000_000_000;
        }
        return Instant.ofEpochSecond(seconds, nanoAdjust);
    }

    /**
     * Converts a date-time with an explicit offset to epoch nanoseconds.
     */
    public static BigInteger utcDateTimeToEpochNs(IsoDate date, IsoTime time, int offsetSeconds) {
        return utcDateTimeToEpochNs(date, time, BigInteger.valueOf(offsetSeconds).multiply(BILLION));
    }

    /**
     * Converts a date-time with an explicit offset (nanosecond precision) to epoch nanoseconds.
     */
    public static BigInteger utcDateTimeToEpochNs(IsoDate date, IsoTime time, BigInteger offsetNanoseconds) {
        long epochDay = date.toEpochDay();
        BigInteger dayNs = BigInteger.valueOf(epochDay).multiply(BigInteger.valueOf(86_400_000_000_000L));
        BigInteger timeNs = BigInteger.valueOf(time.totalNanoseconds());
        return dayNs.add(timeNs).subtract(offsetNanoseconds);
    }
}
