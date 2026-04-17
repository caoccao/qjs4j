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

import java.math.BigInteger;
import java.time.*;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TimeZone operations backed by java.time.
 */
public final class TemporalTimeZone {
    private static final Map<String, String> AVAILABLE_TIME_ZONE_IDS_BY_LOWERCASE =
            createAvailableTimeZoneIdentifierLookup();
    private static final BigInteger BILLION = TemporalConstants.BI_BILLION;
    private static final Pattern OFFSET_BASIC_PATTERN =
            Pattern.compile("^([+\\-\\u2212])(\\d{2})(\\d{2})(?:(\\d{2})(?:\\.(\\d{1,9}))?)?$");
    private static final Pattern OFFSET_EXTENDED_PATTERN =
            Pattern.compile("^([+\\-\\u2212])(\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.(\\d{1,9}))?)?$");
    private static final Pattern OFFSET_HOUR_ONLY_PATTERN =
            Pattern.compile("^([+\\-\\u2212])(\\d{2})$");
    private static final int SECONDS_PER_HOUR = 3_600;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final Pattern SIMPLE_OFFSET_PATTERN =
            Pattern.compile("^([+\\-\\u2212])(\\d{2}):(\\d{2})$");
    private static final Map<String, String> SUPPLEMENTARY_TIME_ZONE_IDS = Map.of(
            "est", "EST",
            "mst", "MST",
            "hst", "HST",
            "gmt+0", "GMT+0",
            "gmt-0", "GMT-0",
            "gmt0", "GMT0",
            "roc", "ROC");

    private TemporalTimeZone() {
    }

    public static String canonicalizeTimeZoneIdentifier(String timeZoneText) {
        String normalizedTimeZoneText = timeZoneText.toLowerCase(Locale.ROOT);
        String canonicalSupplementaryTimeZoneId = SUPPLEMENTARY_TIME_ZONE_IDS.get(normalizedTimeZoneText);
        if (canonicalSupplementaryTimeZoneId != null) {
            return canonicalSupplementaryTimeZoneId;
        }
        String canonicalAvailableTimeZoneId = AVAILABLE_TIME_ZONE_IDS_BY_LOWERCASE.get(normalizedTimeZoneText);
        if (canonicalAvailableTimeZoneId != null) {
            return canonicalAvailableTimeZoneId;
        }
        if ("ut".equalsIgnoreCase(timeZoneText)) {
            return "UT";
        }
        try {
            ZoneOffset zoneOffset = ZoneOffset.of(timeZoneText);
            if (zoneOffset.getTotalSeconds() % 60 != 0) {
                return timeZoneText;
            } else {
                return formatOffset(zoneOffset.getTotalSeconds());
            }
        } catch (DateTimeException ignoredException) {
            return timeZoneText;
        }
    }

    public static String canonicalizeTimeZoneIdentifierForEquals(
            JSContext context,
            String timeZoneId,
            Map<String, String> primaryTimeZoneIdentifiersByLowercase) {
        if (timeZoneId == null || timeZoneId.isEmpty()) {
            return timeZoneId;
        }
        String normalizedTimeZoneId = timeZoneId.replace('\u2212', '-');
        if ("Z".equals(normalizedTimeZoneId)) {
            return "offset:+00:00";
        }
        try {
            ZoneOffset zoneOffset = ZoneOffset.of(normalizedTimeZoneId);
            return "offset:" + formatOffset(zoneOffset.getTotalSeconds());
        } catch (DateTimeException ignoredException) {
            String canonicalTimeZoneId = parseTimeZoneIdentifierString(context, normalizedTimeZoneId);
            if (context.hasPendingException() || canonicalTimeZoneId == null) {
                return "named:" + normalizedTimeZoneId;
            }
            String lowerCaseTimeZoneId = canonicalTimeZoneId.toLowerCase(Locale.ROOT);
            String primaryTimeZoneId = primaryTimeZoneIdentifiersByLowercase.get(lowerCaseTimeZoneId);
            if (primaryTimeZoneId == null) {
                primaryTimeZoneId = canonicalTimeZoneId;
            }
            return "named:" + primaryTimeZoneId;
        }
    }

    private static Map<String, String> createAvailableTimeZoneIdentifierLookup() {
        List<String> availableTimeZoneIdentifiers = new ArrayList<>(ZoneId.getAvailableZoneIds());
        Collections.sort(availableTimeZoneIdentifiers);
        Map<String, String> availableTimeZoneIdentifiersByLowercase = new HashMap<>(availableTimeZoneIdentifiers.size());
        for (String availableTimeZoneIdentifier : availableTimeZoneIdentifiers) {
            String normalizedTimeZoneIdentifier = availableTimeZoneIdentifier.toLowerCase(Locale.ROOT);
            if (!availableTimeZoneIdentifiersByLowercase.containsKey(normalizedTimeZoneIdentifier)) {
                availableTimeZoneIdentifiersByLowercase.put(
                        normalizedTimeZoneIdentifier,
                        availableTimeZoneIdentifier);
            }
        }
        return Map.copyOf(availableTimeZoneIdentifiersByLowercase);
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

    private static String extractOffsetText(String text) {
        int timeSeparatorIndex = Math.max(text.indexOf('T'), text.indexOf('t'));
        if (timeSeparatorIndex < 0) {
            return null;
        }
        int offsetStart = -1;
        for (int index = timeSeparatorIndex + 1; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '[' || character == 'Z' || character == 'z') {
                break;
            }
            if (character == '+' || character == '-' || character == '\u2212') {
                offsetStart = index;
                break;
            }
        }
        if (offsetStart < 0) {
            return null;
        }
        int offsetEnd = text.indexOf('[', offsetStart);
        if (offsetEnd < 0) {
            offsetEnd = text.length();
        }
        return text.substring(offsetStart, offsetEnd);
    }

    /**
     * Formats an offset in seconds as ±HH:MM.
     */
    public static String formatOffset(int totalSeconds) {
        String sign = totalSeconds >= 0 ? "+" : "-";
        int absoluteSeconds = Math.abs(totalSeconds);
        int hours = absoluteSeconds / 3_600;
        int minutes = (absoluteSeconds % 3_600) / 60;
        int seconds = absoluteSeconds % 60;
        if (seconds == 0) {
            return String.format(Locale.ROOT, "%s%02d:%02d", sign, hours, minutes);
        } else {
            return String.format(Locale.ROOT, "%s%02d:%02d:%02d", sign, hours, minutes, seconds);
        }
    }

    public static String formatOffsetRoundedToMinute(int totalSeconds) {
        int sign = totalSeconds < 0 ? -1 : 1;
        int absoluteSeconds = Math.abs(totalSeconds);
        int absoluteMinutes = absoluteSeconds / 60;
        int remainingSeconds = absoluteSeconds % 60;
        if (remainingSeconds >= 30) {
            absoluteMinutes++;
        }
        int roundedTotalSeconds = sign * absoluteMinutes * 60;
        return formatOffset(roundedTotalSeconds);
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
        if (transition == null) {
            return null;
        }
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
        if (transition == null) {
            return null;
        }
        Instant transInstant = transition.getInstant();
        return BigInteger.valueOf(transInstant.getEpochSecond()).multiply(BILLION)
                .add(BigInteger.valueOf(transInstant.getNano()));
    }

    private static boolean hasOffsetDesignator(String text) {
        int timeSeparatorIndex = Math.max(text.indexOf('T'), text.indexOf('t'));
        if (timeSeparatorIndex < 0) {
            return false;
        }
        for (int index = timeSeparatorIndex + 1; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '[') {
                break;
            }
            if (character == 'Z' || character == 'z' || character == '+' || character == '-' || character == '\u2212') {
                return true;
            }
        }
        return false;
    }

    public static boolean isValidOffsetString(String offsetText) {
        TemporalOffsetParts offsetParts = parseOffsetParts(offsetText);
        if (offsetParts == null) {
            return false;
        }
        int hours = offsetParts.hours();
        int minutes = offsetParts.minutes();
        String secondsText = offsetParts.secondsText();
        if (hours > 23 || minutes > 59) {
            return false;
        }
        if (secondsText != null) {
            int seconds = Integer.parseInt(secondsText);
            return seconds <= 59;
        } else {
            return true;
        }
    }

    public static boolean isValidTimeZoneOffsetWithoutSeconds(String offsetText) {
        TemporalOffsetParts offsetParts = parseOffsetParts(offsetText);
        if (offsetParts == null) {
            return false;
        }
        int hours = offsetParts.hours();
        int minutes = offsetParts.minutes();
        String secondsText = offsetParts.secondsText();
        String fractionText = offsetParts.fractionText();
        if (hours > 23 || minutes > 59) {
            return false;
        }
        return secondsText == null && fractionText == null;
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

            if (TemporalDisambiguation.isReject(disambiguation)) {
                throw new DateTimeException("Ambiguous local time for time zone: " + timeZoneId);
            } else if (TemporalDisambiguation.isLater(disambiguation)) {
                instant = laterInstant;
            } else {
                instant = earlierInstant;
            }
        } else {
            ZoneOffsetTransition transition = zoneRules.getTransition(localDateTime);
            if (transition == null) {
                throw new DateTimeException("Invalid local time for time zone: " + timeZoneId);
            }
            if (TemporalDisambiguation.isReject(disambiguation)) {
                throw new DateTimeException("Invalid local time for time zone: " + timeZoneId);
            }

            Duration gapDuration = transition.getDuration().abs();
            if (TemporalDisambiguation.isEarlier(disambiguation)) {
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

    public static boolean offsetTextIncludesSecondsOrFraction(String offsetText) {
        TemporalOffsetParts offsetParts = parseOffsetParts(offsetText);
        if (offsetParts == null) {
            return false;
        }
        return offsetParts.secondsText() != null || offsetParts.fractionText() != null;
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

    public static TemporalOffsetParts parseOffsetParts(String offsetText) {
        Matcher extendedMatcher = OFFSET_EXTENDED_PATTERN.matcher(offsetText);
        if (extendedMatcher.matches()) {
            return new TemporalOffsetParts(
                    extendedMatcher.group(1),
                    Integer.parseInt(extendedMatcher.group(2)),
                    Integer.parseInt(extendedMatcher.group(3)),
                    extendedMatcher.group(4),
                    extendedMatcher.group(5));
        }
        Matcher basicMatcher = OFFSET_BASIC_PATTERN.matcher(offsetText);
        if (basicMatcher.matches()) {
            return new TemporalOffsetParts(
                    basicMatcher.group(1),
                    Integer.parseInt(basicMatcher.group(2)),
                    Integer.parseInt(basicMatcher.group(3)),
                    basicMatcher.group(4),
                    basicMatcher.group(5));
        }
        Matcher hourOnlyMatcher = OFFSET_HOUR_ONLY_PATTERN.matcher(offsetText);
        if (hourOnlyMatcher.matches()) {
            return new TemporalOffsetParts(
                    hourOnlyMatcher.group(1),
                    Integer.parseInt(hourOnlyMatcher.group(2)),
                    0,
                    null,
                    null);
        }
        return null;
    }

    public static int parseOffsetSeconds(String offsetText) {
        TemporalOffsetParts offsetParts = parseOffsetParts(offsetText);
        if (offsetParts == null) {
            throw new DateTimeException("Invalid offset string: " + offsetText);
        }
        String signText = offsetParts.signText();
        int sign;
        if ("-".equals(signText) || "\u2212".equals(signText)) {
            sign = -1;
        } else {
            sign = 1;
        }
        int hours = offsetParts.hours();
        int minutes = offsetParts.minutes();
        int seconds;
        if (offsetParts.secondsText() == null) {
            seconds = 0;
        } else {
            seconds = Integer.parseInt(offsetParts.secondsText());
        }
        return sign * (hours * 3600 + minutes * 60 + seconds);
    }

    public static String parseTimeZoneIdentifierString(JSContext context, String timeZoneText) {
        if (timeZoneText.isEmpty()) {
            context.throwRangeError("Temporal error: Invalid time zone.");
            return null;
        }
        char firstCharacter = timeZoneText.charAt(0);
        boolean startsWithDateCharacter =
                Character.isDigit(firstCharacter)
                        || firstCharacter == '+'
                        || firstCharacter == '-'
                        || firstCharacter == '\u2212';
        boolean looksLikeIsoDateTime =
                startsWithDateCharacter
                        && (timeZoneText.contains("T") || timeZoneText.contains("t"))
                        && timeZoneText.contains("-");
        if (!looksLikeIsoDateTime) {
            return canonicalizeTimeZoneIdentifier(timeZoneText);
        }

        if (timeZoneText.contains("[")) {
            String adjustedTimeZoneText = timeZoneText;
            if (adjustedTimeZoneText.contains(":60")) {
                adjustedTimeZoneText = adjustedTimeZoneText.replace(":60", ":59");
            }
            String offsetText = extractOffsetText(adjustedTimeZoneText);
            if (offsetText != null && !isValidOffsetString(offsetText)) {
                context.throwRangeError("Temporal error: Invalid offset string.");
                return null;
            }
            IsoZonedDateTimeOffset parsedZonedDateTime =
                    TemporalParser.parseZonedDateTimeString(context, adjustedTimeZoneText);
            if (parsedZonedDateTime == null || context.hasPendingException()) {
                return null;
            }
            return canonicalizeTimeZoneIdentifier(parsedZonedDateTime.timeZoneId());
        }

        if (!hasOffsetDesignator(timeZoneText)) {
            context.throwRangeError("Temporal error: Invalid time zone.");
            return null;
        }
        String offsetText = extractOffsetText(timeZoneText);
        if (offsetText != null && !isValidTimeZoneOffsetWithoutSeconds(offsetText)) {
            context.throwRangeError("Temporal error: Invalid offset string.");
            return null;
        }
        IsoDateTimeOffset parsedInstant = TemporalParser.parseInstantString(context, timeZoneText);
        if (parsedInstant == null || context.hasPendingException()) {
            return null;
        }
        if (parsedInstant.offset().totalSeconds() == 0) {
            return "UTC";
        } else {
            return formatOffset(parsedInstant.offset().totalSeconds());
        }
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
        String normalizedTimeZoneId = timeZoneId.toLowerCase(Locale.ROOT);
        if ("roc".equals(normalizedTimeZoneId)) {
            return ZoneId.of("Asia/Taipei");
        } else if ("est".equals(normalizedTimeZoneId)) {
            return ZoneId.of("America/Panama");
        } else if ("mst".equals(normalizedTimeZoneId)) {
            return ZoneId.of("America/Phoenix");
        } else if ("hst".equals(normalizedTimeZoneId)) {
            return ZoneId.of("Pacific/Honolulu");
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
