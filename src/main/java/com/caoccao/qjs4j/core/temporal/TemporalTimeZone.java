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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TimeZone operations backed by java.time.
 */
public final class TemporalTimeZone {
    private static final Map<String, String> AVAILABLE_TIME_ZONE_IDS_BY_LOWERCASE = createAvailableTimeZoneIdentifierLookup();
    private static final BigInteger BILLION = TemporalConstants.BI_BILLION;
    private static final Pattern OFFSET_BASIC_PATTERN = Pattern
            .compile("^([+\\-\\u2212])(\\d{2})(\\d{2})(?:(\\d{2})(?:\\.(\\d{1,9}))?)?$");
    private static final Pattern OFFSET_EXTENDED_PATTERN = Pattern
            .compile("^([+\\-\\u2212])(\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.(\\d{1,9}))?)?$");
    private static final Pattern OFFSET_HOUR_ONLY_PATTERN = Pattern.compile("^([+\\-\\u2212])(\\d{2})$");
    private static final int SECONDS_PER_HOUR = 3_600;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final Pattern SIMPLE_OFFSET_PATTERN = Pattern.compile("^([+\\-\\u2212])(\\d{2}):(\\d{2})$");
    private static final Map<String, String> SUPPLEMENTARY_TIME_ZONE_IDS = Map.of("est", "EST", "mst", "MST", "hst",
            "HST", "gmt+0", "GMT+0", "gmt-0", "GMT-0", "gmt0", "GMT0", "roc", "ROC");

    private static final Map<String, String> TIME_ZONE_PRIMARY_IDENTIFIERS = Map.ofEntries(
            Map.entry("europe/nicosia", "Asia/Nicosia"), Map.entry("asia/ashkhabad", "Asia/Ashgabat"),
            Map.entry("asia/calcutta", "Asia/Kolkata"), Map.entry("asia/choibalsan", "Asia/Ulaanbaatar"),
            Map.entry("asia/chongqing", "Asia/Shanghai"), Map.entry("asia/chungking", "Asia/Shanghai"),
            Map.entry("asia/dacca", "Asia/Dhaka"), Map.entry("asia/harbin", "Asia/Shanghai"),
            Map.entry("asia/istanbul", "Europe/Istanbul"), Map.entry("asia/kashgar", "Asia/Urumqi"),
            Map.entry("asia/katmandu", "Asia/Kathmandu"), Map.entry("asia/macao", "Asia/Macau"),
            Map.entry("asia/rangoon", "Asia/Yangon"), Map.entry("asia/saigon", "Asia/Ho_Chi_Minh"),
            Map.entry("asia/tel_aviv", "Asia/Jerusalem"), Map.entry("asia/thimbu", "Asia/Thimphu"),
            Map.entry("asia/ujung_pandang", "Asia/Makassar"), Map.entry("asia/ulan_bator", "Asia/Ulaanbaatar"),
            Map.entry("africa/asmera", "Africa/Asmara"), Map.entry("africa/timbuktu", "Africa/Bamako"),
            Map.entry("antarctica/south_pole", "Antarctica/McMurdo"), Map.entry("australia/act", "Australia/Sydney"),
            Map.entry("australia/canberra", "Australia/Sydney"), Map.entry("australia/currie", "Australia/Hobart"),
            Map.entry("australia/lhi", "Australia/Lord_Howe"), Map.entry("australia/nsw", "Australia/Sydney"),
            Map.entry("australia/north", "Australia/Darwin"), Map.entry("australia/queensland", "Australia/Brisbane"),
            Map.entry("australia/south", "Australia/Adelaide"), Map.entry("australia/tasmania", "Australia/Hobart"),
            Map.entry("australia/victoria", "Australia/Melbourne"), Map.entry("australia/west", "Australia/Perth"),
            Map.entry("australia/yancowinna", "Australia/Broken_Hill"),
            Map.entry("pacific/enderbury", "Pacific/Kanton"), Map.entry("pacific/johnston", "Pacific/Honolulu"),
            Map.entry("pacific/ponape", "Pacific/Pohnpei"), Map.entry("pacific/samoa", "Pacific/Pago_Pago"),
            Map.entry("pacific/truk", "Pacific/Chuuk"), Map.entry("pacific/yap", "Pacific/Chuuk"),
            Map.entry("europe/belfast", "Europe/London"), Map.entry("europe/kiev", "Europe/Kyiv"),
            Map.entry("europe/tiraspol", "Europe/Chisinau"), Map.entry("europe/uzhgorod", "Europe/Kyiv"),
            Map.entry("europe/zaporozhye", "Europe/Kyiv"),
            Map.entry("america/argentina/comodrivadavia", "America/Argentina/Catamarca"),
            Map.entry("america/atka", "America/Adak"),
            Map.entry("america/buenos_aires", "America/Argentina/Buenos_Aires"),
            Map.entry("america/catamarca", "America/Argentina/Catamarca"),
            Map.entry("america/coral_harbour", "America/Atikokan"),
            Map.entry("america/cordoba", "America/Argentina/Cordoba"), Map.entry("america/ensenada", "America/Tijuana"),
            Map.entry("america/fort_wayne", "America/Indiana/Indianapolis"),
            Map.entry("america/godthab", "America/Nuuk"),
            Map.entry("america/indianapolis", "America/Indiana/Indianapolis"),
            Map.entry("america/jujuy", "America/Argentina/Jujuy"), Map.entry("america/knox_in", "America/Indiana/Knox"),
            Map.entry("america/louisville", "America/Kentucky/Louisville"),
            Map.entry("america/mendoza", "America/Argentina/Mendoza"), Map.entry("america/montreal", "America/Toronto"),
            Map.entry("america/nipigon", "America/Toronto"), Map.entry("america/pangnirtung", "America/Iqaluit"),
            Map.entry("america/porto_acre", "America/Rio_Branco"), Map.entry("america/rainy_river", "America/Winnipeg"),
            Map.entry("america/rosario", "America/Argentina/Cordoba"),
            Map.entry("america/santa_isabel", "America/Tijuana"), Map.entry("america/shiprock", "America/Denver"),
            Map.entry("america/thunder_bay", "America/Toronto"), Map.entry("america/virgin", "America/St_Thomas"),
            Map.entry("america/yellowknife", "America/Edmonton"), Map.entry("us/alaska", "America/Anchorage"),
            Map.entry("us/aleutian", "America/Adak"), Map.entry("us/arizona", "America/Phoenix"),
            Map.entry("us/central", "America/Chicago"), Map.entry("us/east-indiana", "America/Indiana/Indianapolis"),
            Map.entry("us/eastern", "America/New_York"), Map.entry("us/hawaii", "Pacific/Honolulu"),
            Map.entry("us/indiana-starke", "America/Indiana/Knox"), Map.entry("us/michigan", "America/Detroit"),
            Map.entry("us/mountain", "America/Denver"), Map.entry("us/pacific", "America/Los_Angeles"),
            Map.entry("us/samoa", "Pacific/Pago_Pago"), Map.entry("atlantic/faeroe", "Atlantic/Faroe"),
            Map.entry("atlantic/jan_mayen", "Arctic/Longyearbyen"), Map.entry("brazil/acre", "America/Rio_Branco"),
            Map.entry("brazil/denoronha", "America/Noronha"), Map.entry("brazil/east", "America/Sao_Paulo"),
            Map.entry("brazil/west", "America/Manaus"), Map.entry("cet", "Europe/Brussels"),
            Map.entry("cst6cdt", "America/Chicago"), Map.entry("canada/atlantic", "America/Halifax"),
            Map.entry("canada/central", "America/Winnipeg"), Map.entry("canada/eastern", "America/Toronto"),
            Map.entry("canada/mountain", "America/Edmonton"), Map.entry("canada/newfoundland", "America/St_Johns"),
            Map.entry("canada/pacific", "America/Vancouver"), Map.entry("canada/saskatchewan", "America/Regina"),
            Map.entry("canada/yukon", "America/Whitehorse"), Map.entry("chile/continental", "America/Santiago"),
            Map.entry("chile/easterisland", "Pacific/Easter"), Map.entry("cuba", "America/Havana"),
            Map.entry("eet", "Europe/Athens"), Map.entry("est", "America/Panama"),
            Map.entry("est5edt", "America/New_York"), Map.entry("egypt", "Africa/Cairo"),
            Map.entry("eire", "Europe/Dublin"), Map.entry("etc/gmt", "UTC"), Map.entry("etc/gmt+0", "UTC"),
            Map.entry("etc/gmt-0", "UTC"), Map.entry("etc/gmt0", "UTC"), Map.entry("etc/greenwich", "UTC"),
            Map.entry("etc/uct", "UTC"), Map.entry("etc/utc", "UTC"), Map.entry("etc/universal", "UTC"),
            Map.entry("etc/zulu", "UTC"), Map.entry("gb", "Europe/London"), Map.entry("gb-eire", "Europe/London"),
            Map.entry("gmt", "UTC"), Map.entry("gmt+0", "UTC"), Map.entry("gmt-0", "UTC"), Map.entry("gmt0", "UTC"),
            Map.entry("greenwich", "UTC"), Map.entry("hst", "Pacific/Honolulu"),
            Map.entry("hongkong", "Asia/Hong_Kong"), Map.entry("iceland", "Atlantic/Reykjavik"),
            Map.entry("iran", "Asia/Tehran"), Map.entry("israel", "Asia/Jerusalem"),
            Map.entry("jamaica", "America/Jamaica"), Map.entry("japan", "Asia/Tokyo"),
            Map.entry("kwajalein", "Pacific/Kwajalein"), Map.entry("libya", "Africa/Tripoli"),
            Map.entry("met", "Europe/Brussels"), Map.entry("mst", "America/Phoenix"),
            Map.entry("mst7mdt", "America/Denver"), Map.entry("mexico/bajanorte", "America/Tijuana"),
            Map.entry("mexico/bajasur", "America/Mazatlan"), Map.entry("mexico/general", "America/Mexico_City"),
            Map.entry("nz", "Pacific/Auckland"), Map.entry("nz-chat", "Pacific/Chatham"),
            Map.entry("navajo", "America/Denver"), Map.entry("prc", "Asia/Shanghai"),
            Map.entry("pst8pdt", "America/Los_Angeles"), Map.entry("poland", "Europe/Warsaw"),
            Map.entry("portugal", "Europe/Lisbon"), Map.entry("roc", "Asia/Taipei"), Map.entry("rok", "Asia/Seoul"),
            Map.entry("singapore", "Asia/Singapore"), Map.entry("turkey", "Europe/Istanbul"), Map.entry("uct", "UTC"),
            Map.entry("universal", "UTC"), Map.entry("w-su", "Europe/Moscow"), Map.entry("wet", "Europe/Lisbon"),
            Map.entry("utc", "UTC"), Map.entry("zulu", "UTC"));

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

    public static String canonicalizeTimeZoneIdentifierForEquals(JSContext context, String timeZoneId) {
        return canonicalizeTimeZoneIdentifierForEquals(context, timeZoneId, TIME_ZONE_PRIMARY_IDENTIFIERS);
    }

    public static String canonicalizeTimeZoneIdentifierForEquals(JSContext context, String timeZoneId,
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
        Map<String, String> availableTimeZoneIdentifiersByLowercase = new HashMap<>(
                availableTimeZoneIdentifiers.size());
        for (String availableTimeZoneIdentifier : availableTimeZoneIdentifiers) {
            String normalizedTimeZoneIdentifier = availableTimeZoneIdentifier.toLowerCase(Locale.ROOT);
            if (!availableTimeZoneIdentifiersByLowercase.containsKey(normalizedTimeZoneIdentifier)) {
                availableTimeZoneIdentifiersByLowercase.put(normalizedTimeZoneIdentifier, availableTimeZoneIdentifier);
            }
        }
        return Map.copyOf(availableTimeZoneIdentifiersByLowercase);
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

    /**
     * Gets the next timezone transition after the given instant. Returns null if no further transition.
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
     * Gets the previous timezone transition before the given instant. Returns null if no previous transition.
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

    public static boolean offsetTextIncludesSecondsOrFraction(String offsetText) {
        TemporalOffsetParts offsetParts = parseOffsetParts(offsetText);
        if (offsetParts == null) {
            return false;
        }
        return offsetParts.secondsText() != null || offsetParts.fractionText() != null;
    }

    static Integer parseFixedOffsetSeconds(String timeZoneId) {
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
            return new TemporalOffsetParts(extendedMatcher.group(1), Integer.parseInt(extendedMatcher.group(2)),
                    Integer.parseInt(extendedMatcher.group(3)), extendedMatcher.group(4), extendedMatcher.group(5));
        }
        Matcher basicMatcher = OFFSET_BASIC_PATTERN.matcher(offsetText);
        if (basicMatcher.matches()) {
            return new TemporalOffsetParts(basicMatcher.group(1), Integer.parseInt(basicMatcher.group(2)),
                    Integer.parseInt(basicMatcher.group(3)), basicMatcher.group(4), basicMatcher.group(5));
        }
        Matcher hourOnlyMatcher = OFFSET_HOUR_ONLY_PATTERN.matcher(offsetText);
        if (hourOnlyMatcher.matches()) {
            return new TemporalOffsetParts(hourOnlyMatcher.group(1), Integer.parseInt(hourOnlyMatcher.group(2)), 0,
                    null, null);
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
        boolean startsWithDateCharacter = Character.isDigit(firstCharacter) || firstCharacter == '+'
                || firstCharacter == '-' || firstCharacter == '\u2212';
        boolean looksLikeIsoDateTime = startsWithDateCharacter
                && (timeZoneText.contains("T") || timeZoneText.contains("t")) && timeZoneText.contains("-");
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
            IsoZonedDateTimeOffset parsedZonedDateTime = IsoZonedDateTimeOffset.parseZonedDateTimeString(context,
                    adjustedTimeZoneText);
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
        IsoDateTimeOffset parsedInstant = IsoDateTimeOffset.parseInstantString(context, timeZoneText);
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
        // Resolve links through the same primary identifiers used by equals(). Older
        // JDK time-zone databases may still give a link its own historical rules.
        // The ZonedDateTime retains the supplied identifier for timeZoneId and display.
        String primaryTimeZoneId = TIME_ZONE_PRIMARY_IDENTIFIERS.getOrDefault(normalizedTimeZoneId, timeZoneId);
        try {
            return ZoneId.of(primaryTimeZoneId);
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
     * Converts a date-time with an explicit offset (nanosecond precision) to epoch nanoseconds.
     */
    public static BigInteger utcDateTimeToEpochNs(IsoDate date, IsoTime time, BigInteger offsetNanoseconds) {
        long epochDay = date.toEpochDay();
        BigInteger dayNs = BigInteger.valueOf(epochDay).multiply(BigInteger.valueOf(86_400_000_000_000L));
        BigInteger timeNs = BigInteger.valueOf(time.totalNanoseconds());
        return dayNs.add(timeNs).subtract(offsetNanoseconds);
    }

    /**
     * Converts a date-time with an explicit offset to epoch nanoseconds.
     */
    public static BigInteger utcDateTimeToEpochNs(IsoDate date, IsoTime time, int offsetSeconds) {
        return utcDateTimeToEpochNs(date, time, BigInteger.valueOf(offsetSeconds).multiply(BILLION));
    }

}
