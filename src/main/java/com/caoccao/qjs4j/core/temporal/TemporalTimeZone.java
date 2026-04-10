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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TimeZone operations backed by java.time.
 */
public final class TemporalTimeZone {
    private static final BigInteger BILLION = BigInteger.valueOf(1_000_000_000L);
    private static final int SECONDS_PER_HOUR = 3_600;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final Pattern SIMPLE_OFFSET_PATTERN =
            Pattern.compile("^([+\\-\\u2212])(\\d{2}):(\\d{2})$");

    private TemporalTimeZone() {
    }

    /**
     * Converts epoch nanoseconds to a local IsoDateTime in the given timezone.
     */
    public static IsoDateTime epochNsToDateTimeInZone(BigInteger epochNs, String timeZoneId) {
        Integer fixedOffsetSeconds = parseFixedOffsetSeconds(timeZoneId);
        if (fixedOffsetSeconds != null) {
            BigInteger localEpochNanoseconds = epochNs.add(BigInteger.valueOf(fixedOffsetSeconds).multiply(BILLION));
            return epochNsToUtcDateTime(localEpochNanoseconds);
        }
        Instant javaInstant = toJavaInstant(epochNs);
        ZonedDateTime zonedDateTime = javaInstant.atZone(resolveTimeZone(timeZoneId));
        return fromZonedDateTime(zonedDateTime);
    }

    /**
     * Converts epoch nanoseconds to a UTC IsoDateTime.
     */
    public static IsoDateTime epochNsToUtcDateTime(BigInteger epochNs) {
        Instant javaInstant = toJavaInstant(epochNs);
        LocalDateTime localDateTime = LocalDateTime.ofInstant(javaInstant, ZoneOffset.UTC);
        return fromLocalDateTime(localDateTime);
    }

    /**
     * Formats an offset in seconds as ±HH:MM.
     */
    public static String formatOffset(int totalSeconds) {
        String sign = totalSeconds >= 0 ? "+" : "-";
        int absoluteSeconds = Math.abs(totalSeconds);
        int absoluteMinutes = absoluteSeconds / 60;
        int remainingSeconds = absoluteSeconds % 60;
        if (remainingSeconds >= 30) {
            absoluteMinutes++;
        }
        int hours = absoluteMinutes / 60;
        int minutes = absoluteMinutes % 60;
        return String.format(Locale.ROOT, "%s%02d:%02d", sign, hours, minutes);
    }

    private static IsoDateTime fromLocalDateTime(LocalDateTime localDateTime) {
        IsoDate date = new IsoDate(localDateTime.getYear(), localDateTime.getMonthValue(), localDateTime.getDayOfMonth());
        int nano = localDateTime.getNano();
        IsoTime time = new IsoTime(localDateTime.getHour(), localDateTime.getMinute(), localDateTime.getSecond(),
                nano / 1_000_000, (nano / 1_000) % 1000, nano % 1000);
        return new IsoDateTime(date, time);
    }

    private static IsoDateTime fromZonedDateTime(ZonedDateTime zonedDateTime) {
        IsoDate date = new IsoDate(zonedDateTime.getYear(), zonedDateTime.getMonthValue(), zonedDateTime.getDayOfMonth());
        int nano = zonedDateTime.getNano();
        IsoTime time = new IsoTime(zonedDateTime.getHour(), zonedDateTime.getMinute(), zonedDateTime.getSecond(),
                nano / 1_000_000, (nano / 1_000) % 1000, nano % 1000);
        return new IsoDateTime(date, time);
    }

    /**
     * Gets the number of real hours in a day at the given instant and timezone.
     */
    public static int getHoursInDay(BigInteger epochNs, String timeZoneId) {
        Integer fixedOffsetSeconds = parseFixedOffsetSeconds(timeZoneId);
        if (fixedOffsetSeconds != null) {
            return 24;
        }
        ZoneId zone = resolveTimeZone(timeZoneId);
        Instant javaInstant = toJavaInstant(epochNs);
        ZonedDateTime zonedDateTime = javaInstant.atZone(zone);
        ZonedDateTime startOfDay = zonedDateTime.toLocalDate().atStartOfDay(zone);
        ZonedDateTime startOfNextDay = zonedDateTime.toLocalDate().plusDays(1).atStartOfDay(zone);
        long seconds = Duration.between(startOfDay, startOfNextDay).getSeconds();
        return (int) (seconds / 3600);
    }

    /**
     * Gets the next timezone transition after the given instant.
     * Returns null if no further transition.
     */
    public static BigInteger getNextTransition(BigInteger epochNs, String timeZoneId) {
        Integer fixedOffsetSeconds = parseFixedOffsetSeconds(timeZoneId);
        if (fixedOffsetSeconds != null) {
            return null;
        }
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
        Integer fixedOffsetSeconds = parseFixedOffsetSeconds(timeZoneId);
        if (fixedOffsetSeconds != null) {
            return fixedOffsetSeconds;
        }
        ZoneId zone = resolveTimeZone(timeZoneId);
        Instant javaInstant = toJavaInstant(epochNs);
        ZoneOffset offset = zone.getRules().getOffset(javaInstant);
        return offset.getTotalSeconds();
    }

    /**
     * Returns epoch nanoseconds for the first instant of the given date in the specified timezone.
     */
    public static BigInteger startOfDayToEpochNs(IsoDate isoDate, String timeZoneId) {
        ZoneId zoneId = resolveTimeZone(timeZoneId);
        LocalDate localDate = LocalDate.of(isoDate.year(), isoDate.month(), isoDate.day());
        Instant instant = localDate.atStartOfDay(zoneId).toInstant();
        return BigInteger.valueOf(instant.getEpochSecond()).multiply(BILLION)
                .add(BigInteger.valueOf(instant.getNano()));
    }

    /**
     * Gets the previous timezone transition before the given instant.
     * Returns null if no previous transition.
     */
    public static BigInteger getPreviousTransition(BigInteger epochNs, String timeZoneId) {
        Integer fixedOffsetSeconds = parseFixedOffsetSeconds(timeZoneId);
        if (fixedOffsetSeconds != null) {
            return null;
        }
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
    public static BigInteger localDateTimeToEpochNs(IsoDateTime isoDateTime, String timeZoneId) {
        return localDateTimeToEpochNs(isoDateTime, timeZoneId, "compatible");
    }

    /**
     * Converts a local date-time in a timezone to epoch nanoseconds using the specified disambiguation.
     */
    public static BigInteger localDateTimeToEpochNs(IsoDateTime isoDateTime, String timeZoneId, String disambiguation) {
        return localDateTimeToEpochNs(isoDateTime, timeZoneId, disambiguation, null);
    }

    /**
     * Converts a local date-time in a timezone to epoch nanoseconds using the specified disambiguation and
     * optionally preferring a matching offset when the local date-time is ambiguous.
     */
    public static BigInteger localDateTimeToEpochNs(
            IsoDateTime isoDateTime,
            String timeZoneId,
            String disambiguation,
            Integer preferredOffsetSeconds) {
        Integer fixedOffsetSeconds = parseFixedOffsetSeconds(timeZoneId);
        if (fixedOffsetSeconds != null) {
            return utcDateTimeToEpochNs(isoDateTime.date(), isoDateTime.time(), fixedOffsetSeconds);
        }

        LocalDateTime localDateTime = LocalDateTime.of(
                isoDateTime.date().year(), isoDateTime.date().month(), isoDateTime.date().day(),
                isoDateTime.time().hour(), isoDateTime.time().minute(), isoDateTime.time().second(),
                isoDateTime.time().millisecond() * 1_000_000 + isoDateTime.time().microsecond() * 1_000 + isoDateTime.time().nanosecond());
        ZoneId zoneId = resolveTimeZone(timeZoneId);
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
                    return BigInteger.valueOf(instant.getEpochSecond()).multiply(BILLION)
                            .add(BigInteger.valueOf(instant.getNano()));
                }
                if (secondOffset.getTotalSeconds() == preferredOffsetSeconds.intValue()) {
                    instant = localDateTime.atOffset(secondOffset).toInstant();
                    return BigInteger.valueOf(instant.getEpochSecond()).multiply(BILLION)
                            .add(BigInteger.valueOf(instant.getNano()));
                }
            }
            Instant firstInstant = localDateTime.atOffset(firstOffset).toInstant();
            Instant secondInstant = localDateTime.atOffset(secondOffset).toInstant();
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
            ZoneOffsetTransition transition = zoneRules.getTransition(localDateTime);
            if (transition == null) {
                throw new DateTimeException("Invalid local time for time zone: " + timeZoneId);
            }
            if ("reject".equals(disambiguation)) {
                throw new DateTimeException("Invalid local time for time zone: " + timeZoneId);
            }

            Duration gapDuration = transition.getDuration().abs();
            if ("earlier".equals(disambiguation)) {
                LocalDateTime shiftedLocalDateTime = localDateTime.minusSeconds(gapDuration.getSeconds());
                instant = shiftedLocalDateTime.atOffset(transition.getOffsetBefore()).toInstant();
            } else {
                LocalDateTime shiftedLocalDateTime = localDateTime.plusSeconds(gapDuration.getSeconds());
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
        Integer fixedOffsetSeconds = parseFixedOffsetSeconds(timeZoneId);
        if (fixedOffsetSeconds != null) {
            if (Math.abs(fixedOffsetSeconds) > 18 * SECONDS_PER_HOUR) {
                throw new DateTimeException("Offset zone is outside java.time range: " + timeZoneId);
            }
            return ZoneOffset.ofTotalSeconds(fixedOffsetSeconds);
        }
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

    private static Integer parseFixedOffsetSeconds(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isEmpty()) {
            return null;
        }
        if ("Z".equals(timeZoneId)) {
            return 0;
        }
        Matcher offsetMatcher = SIMPLE_OFFSET_PATTERN.matcher(timeZoneId);
        if (!offsetMatcher.matches()) {
            return null;
        }

        int hourValue = Integer.parseInt(offsetMatcher.group(2));
        int minuteValue = Integer.parseInt(offsetMatcher.group(3));
        if (hourValue > 23 || minuteValue > 59) {
            return null;
        }

        int sign = ("-".equals(offsetMatcher.group(1)) || "\u2212".equals(offsetMatcher.group(1))) ? -1 : 1;
        return sign * (hourValue * SECONDS_PER_HOUR + minuteValue * SECONDS_PER_MINUTE);
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
