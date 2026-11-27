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

package com.caoccao.qjs4j.core.intl;

import java.lang.reflect.*;
import java.time.chrono.*;
import java.time.format.*;
import java.util.*;

/** Builds localized date/time patterns from the requested ECMA-402 components. */
record IntlDateTimePattern(Locale locale, String calendar, FormatStyle dateStyle, FormatStyle timeStyle,
        String hourCycle, IntlDateFields dateFields, IntlTimeFields timeFields, Integer fractionalSecondDigits,
        String timeZoneNameOption) {
    /**
     * The CLDR "atTime" date-time glue ICU uses when dateStyle is full or long and a timeStyle is present: {1} is the
     * date and {0} the time. The JDK's localized patterns join every style with the plain glue ("{1}, {0}" in English),
     * so "Thursday, January 1, 1970 at 12:00:00 AM UTC" came out as "Thursday, January 1, 1970, 12:00:00 AM UTC". A key
     * suffixed with "-long" applies to the long date style only.
     */
    private static final Map<String, String> AT_TIME_GLUE = Map.ofEntries(Map.entry("ar", "{1} 'في' {0}"),
            Map.entry("bg", "{1} 'в' {0}"), Map.entry("cs", "{1} 'v' {0}"), Map.entry("da", "{1} 'kl'. {0}"),
            Map.entry("de", "{1} 'um' {0}"), Map.entry("el", "{1} 'στις' {0}"), Map.entry("en", "{1} 'at' {0}"),
            Map.entry("es-long", "{1} 'a las' {0}"), Map.entry("et", "{1}, 'kell' {0}"),
            Map.entry("fi", "{1} 'klo' {0}"), Map.entry("fr", "{1} 'à' {0}"), Map.entry("he", "{1} 'בשעה' {0}"),
            Map.entry("hi", "{1} 'को' {0} 'बजे'"), Map.entry("hr", "{1} 'u' {0}"), Map.entry("id", "{1} 'pukul' {0}"),
            Map.entry("it", "{1} 'alle ore' {0}"), Map.entry("nb", "{1} 'kl'. {0}"), Map.entry("nl", "{1} 'om' {0}"),
            Map.entry("pl-long", "{1} {0}"), Map.entry("pt", "{1} 'às' {0}"), Map.entry("ro", "{1} 'la' {0}"),
            Map.entry("ru", "{1} 'в' {0}"), Map.entry("sk", "{1} 'o' {0}"), Map.entry("sl", "{1} 'ob' {0}"),
            Map.entry("sv", "{1} 'kl'. {0}"), Map.entry("th", "{1} 'เวลา' {0}"), Map.entry("uk", "{1} 'о' {0}"),
            Map.entry("vi", "'lúc' {0} {1}"));

    /**
     * {@code DateTimeFormatterBuilder.getLocalizedDateTimePattern(String, Chronology, Locale)}, the CLDR skeleton
     * lookup added in JDK 19. Null on JDK 17 and 18, where component patterns are derived from the short and medium
     * date styles instead.
     */
    private static final Method LOCALIZED_SKELETON_PATTERN = findLocalizedSkeletonPatternMethod();

    /**
     * Adjust a localized base pattern by replacing field widths based on options and removing fields that are not
     * requested.
     */
    private String adjustPatternFields(String basePattern) {
        StringBuilder result = new StringBuilder();
        String separator = null;
        for (Token token : tokenize(basePattern)) {
            if (token.field == '\0') {
                if (result.length() > 0 && separator == null) {
                    separator = token.text;
                }
                continue;
            }
            String replacement = getFieldReplacement(token.field);
            if (replacement == null) {
                continue;
            }
            if (separator != null) {
                if (separator.chars().anyMatch(ch -> Character.isLetter((char) ch))) {
                    result.append('\'').append(separator).append('\'');
                } else {
                    result.append(separator);
                }
                separator = null;
            }
            result.append(replacement);
        }
        return result.toString();
    }

    private static String applyHourCycleToPattern(String pattern, String hc) {
        boolean want24 = "h23".equals(hc) || "h24".equals(hc);
        char targetHourChar = switch (hc) {
            case "h11" -> 'K';
            case "h12" -> 'h';
            case "h23" -> 'H';
            case "h24" -> 'k';
            default -> 'h';
        };
        StringBuilder sb = new StringBuilder(pattern.length());
        boolean inQuote = false;
        boolean hasDayPeriod = false;
        int timeFieldEnd = -1;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                inQuote = !inQuote;
                sb.append(c);
            } else if (inQuote) {
                sb.append(c);
            } else if (c == 'h' || c == 'H' || c == 'K' || c == 'k') {
                // Replace any hour field letter with the target
                sb.append(targetHourChar);
                timeFieldEnd = sb.length();
            } else if (c == 'a' || c == 'b' || c == 'B') {
                if (want24) {
                    // Remove AM/PM / dayPeriod markers for 24-hour cycles.
                    // Remove whitespace before the marker, including Unicode space chars
                    // like U+202F (narrow no-break space) used in CLDR patterns.
                    while (sb.length() > 0 && isPatternSeparator(sb.charAt(sb.length() - 1))) {
                        sb.deleteCharAt(sb.length() - 1);
                    }
                    // Skip all consecutive 'a'/'b'/'B' characters
                    while (i + 1 < pattern.length() && pattern.charAt(i + 1) == c) {
                        i++;
                    }
                    // Skip whitespace after the AM/PM marker too, then add back
                    // a single space if more pattern content follows (e.g. timezone)
                    while (i + 1 < pattern.length() && isPatternSeparator(pattern.charAt(i + 1))) {
                        i++;
                    }
                    if (i + 1 < pattern.length()) {
                        sb.append(' ');
                    }
                } else {
                    hasDayPeriod = true;
                    sb.append(c);
                }
            } else {
                sb.append(c);
                if (c == 'm' || c == 's' || c == 'S') {
                    timeFieldEnd = sb.length();
                }
            }
        }
        if (!want24 && !hasDayPeriod && timeFieldEnd >= 0) {
            sb.insert(timeFieldEnd, " a");
        }
        return sb.toString();
    }

    /**
     * Build format pattern from individual component options.
     */
    private String buildComponentPattern() {
        boolean hasDate = dateFields.year() != null || dateFields.month() != null || dateFields.day() != null
                || dateFields.weekday() != null || dateFields.era() != null;
        boolean hasTime = timeFields.hour() != null || timeFields.minute() != null || timeFields.second() != null
                || timeFields.dayPeriod() != null || fractionalSecondDigits != null || timeZoneNameOption != null;

        if (hasDate && hasTime) {
            String datePattern = buildDateSubPattern();
            String timePattern = buildTimeSubPattern();
            return datePattern + ", " + timePattern;
        } else if (hasDate) {
            return buildDateSubPattern();
        } else if (hasTime) {
            return buildTimeSubPattern();
        }
        return "M/d/y";
    }

    /**
     * Build the date portion of the pattern using locale-specific ordering.
     */
    private String buildDateSubPattern() {
        String adjustedPattern = buildSkeletonDatePattern();
        if (adjustedPattern == null) {
            boolean useTextMonth = "short".equals(dateFields.month()) || "long".equals(dateFields.month())
                    || "narrow".equals(dateFields.month());
            FormatStyle baseStyle = useTextMonth ? FormatStyle.MEDIUM : FormatStyle.SHORT;
            String basePattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(baseStyle, null,
                    IsoChronology.INSTANCE, locale);
            adjustedPattern = adjustPatternFields(basePattern);
        }
        if (dateFields.weekday() != null && adjustedPattern.indexOf('E') < 0 && adjustedPattern.indexOf('e') < 0
                && adjustedPattern.indexOf('c') < 0) {
            String weekdayPattern = getTextFieldPattern('E', dateFields.weekday(), 1);
            adjustedPattern = adjustedPattern.isEmpty() ? weekdayPattern : weekdayPattern + ", " + adjustedPattern;
        }
        if (dateFields.era() != null && !"chinese".equals(calendar) && !"dangi".equals(calendar)
                && adjustedPattern.indexOf('G') < 0) {
            adjustedPattern = adjustedPattern + " G";
        }
        return adjustedPattern;
    }

    /**
     * Build the format pattern based on options. For dateStyle/timeStyle, uses Java's localized pattern. For component
     * options, builds a custom pattern.
     */
    String buildFormatPattern() {
        String pattern;
        String atTimeGlue = getAtTimeGlue();
        if (atTimeGlue != null) {
            String datePattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(dateStyle, null,
                    IsoChronology.INSTANCE, locale);
            // Every constructor resolves an hour cycle whenever a timeStyle is present.
            String timePattern = applyHourCycleToPattern(DateTimeFormatterBuilder.getLocalizedDateTimePattern(null,
                    timeStyle, IsoChronology.INSTANCE, locale), hourCycle);
            return atTimeGlue.replace("{1}", datePattern).replace("{0}", timePattern);
        } else if (dateStyle != null && timeStyle != null) {
            pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(dateStyle, timeStyle, IsoChronology.INSTANCE,
                    locale);
        } else if (dateStyle != null) {
            pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(dateStyle, null, IsoChronology.INSTANCE,
                    locale);
        } else if (timeStyle != null) {
            pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(null, timeStyle, IsoChronology.INSTANCE,
                    locale);
        } else {
            return buildComponentPattern();
        }
        // Apply hourCycle override to the localized pattern.
        // Java's localized pattern reflects the locale's default hour cycle,
        // but ECMA-402 allows overriding via hourCycle option or -u-hc- extension.
        if (hourCycle != null && (timeStyle != null)) {
            pattern = applyHourCycleToPattern(pattern, hourCycle);
        }
        return pattern;
    }

    /**
     * Build the date portion of a component pattern from the locale's CLDR skeleton pattern, the way ICU's
     * DateTimePatternGenerator does: numeric day and month keep the width the locale prefers ("dd/MM/y" for en-GB,
     * where the short date style used to be shrunk to "d/M/y") unless 2-digit is requested, and text fields take the
     * requested width.
     *
     * @return the pattern, or null when the skeleton lookup is unavailable or does not apply
     */
    private String buildSkeletonDatePattern() {
        if (LOCALIZED_SKELETON_PATTERN == null
                || JSIntlDateTimeFormat.resolveChronology(calendar) != IsoChronology.INSTANCE
                || (dateFields.year() == null && dateFields.month() == null && dateFields.day() == null)) {
            return null;
        }
        String era = getTextFieldPattern('G', dateFields.era(), 1);
        String month = dateFields.month() == null
                ? ""
                : "numeric".equals(dateFields.month()) || "2-digit".equals(dateFields.month())
                        ? "M"
                        : getTextFieldPattern('M', dateFields.month(), 3);
        String weekday = getTextFieldPattern('E', dateFields.weekday(), 3);
        String year = dateFields.year() == null ? "" : "y";
        String day = dateFields.day() == null ? "" : "d";
        String skeleton = era + year + month + weekday + day;
        String pattern = invokeLocalizedSkeletonPattern(skeleton);
        String abbreviatedSkeleton = (era.isEmpty() ? "" : "G") + year + (month.length() > 3 ? "MMM" : month)
                + (weekday.isEmpty() ? "" : "E") + day;
        if (pattern == null && !abbreviatedSkeleton.equals(skeleton)) {
            // CLDR has no skeleton for every text width (the JDK rejects "GGGGyMMMMd"); ICU widens the fields of the
            // abbreviated pattern instead, as the loop below does.
            pattern = invokeLocalizedSkeletonPattern(abbreviatedSkeleton);
        }
        if (pattern == null) {
            return null;
        }
        // The skeleton names only requested fields, so every field of the pattern has an option.
        StringBuilder result = new StringBuilder(pattern.length());
        boolean quoted = false;
        int index = 0;
        while (index < pattern.length()) {
            char c = pattern.charAt(index);
            if (c == '\'' || quoted || !Character.isLetter(c)) {
                quoted ^= c == '\'';
                result.append(c);
                index++;
                continue;
            }
            int end = index;
            while (end < pattern.length() && pattern.charAt(end) == c) {
                end++;
            }
            String field = pattern.substring(index, end);
            switch (c) {
                case 'y' -> field = "2-digit".equals(dateFields.year()) ? "yy" : "y";
                case 'M', 'L' -> {
                    if ("2-digit".equals(dateFields.month())) {
                        field = String.valueOf(c).repeat(2);
                    } else if (field.length() >= 3) {
                        field = getTextFieldPattern(c, dateFields.month(), 3);
                    }
                }
                case 'd' -> field = "2-digit".equals(dateFields.day()) ? "dd" : field;
                case 'E', 'c', 'e' -> field = getTextFieldPattern(c, dateFields.weekday(), 3);
                case 'G' -> field = getTextFieldPattern('G', dateFields.era(), 1);
                default -> {
                }
            }
            result.append(field);
            index = end;
        }
        return result.toString();
    }

    /**
     * Build the time portion of the pattern.
     */
    private String buildTimeSubPattern() {
        StringBuilder pattern = new StringBuilder();
        boolean hasClockField = timeFields.hour() != null || timeFields.minute() != null || timeFields.second() != null
                || fractionalSecondDigits != null;
        if (!hasClockField && timeFields.dayPeriod() != null) {
            switch (timeFields.dayPeriod()) {
                case "long" -> pattern.append("BBBB");
                case "narrow" -> pattern.append("BBBBB");
                default -> pattern.append("B");
            }
        }
        if (!hasClockField && timeFields.dayPeriod() == null && timeZoneNameOption == null) {
            return pattern.toString();
        }
        if (timeFields.hour() != null) {
            boolean use12Hour = hourCycle == null || "h12".equals(hourCycle) || "h11".equals(hourCycle);
            char hourChar;
            if ("h11".equals(hourCycle)) {
                hourChar = 'K';
            } else if ("h24".equals(hourCycle)) {
                hourChar = 'k';
            } else if (use12Hour) {
                hourChar = 'h';
            } else {
                hourChar = 'H';
            }
            boolean useTwoDigitHour = "2-digit".equals(timeFields.hour()) || !use12Hour;
            if (useTwoDigitHour) {
                pattern.append(String.valueOf(hourChar).repeat(2));
            } else {
                pattern.append(hourChar);
            }
        }
        if (timeFields.minute() != null) {
            if (pattern.length() > 0) {
                pattern.append(':');
            }
            pattern.append("mm");
        }
        if (timeFields.second() != null) {
            if (pattern.length() > 0) {
                pattern.append(':');
            }
            pattern.append("ss");
        }
        if (fractionalSecondDigits != null) {
            pattern.append('.');
            pattern.append("S".repeat(fractionalSecondDigits));
        }
        if (timeFields.hour() != null) {
            boolean use12Hour = hourCycle == null || "h12".equals(hourCycle) || "h11".equals(hourCycle);
            if (use12Hour) {
                pattern.append(" a");
            }
        }
        if (timeZoneNameOption != null) {
            if (pattern.length() > 0) {
                pattern.append(' ');
            }
            pattern.append(switch (timeZoneNameOption) {
                case "long", "longGeneric" -> "zzzz";
                case "longOffset" -> "OOOO";
                case "shortOffset" -> "O";
                case "shortGeneric", "short" -> "z";
                default -> "z";
            });
        }
        return pattern.toString();
    }

    private static Method findLocalizedSkeletonPatternMethod() {
        try {
            return DateTimeFormatterBuilder.class.getMethod("getLocalizedDateTimePattern", String.class,
                    Chronology.class, Locale.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private String getAtTimeGlue() {
        if (timeStyle == null || (dateStyle != FormatStyle.FULL && dateStyle != FormatStyle.LONG)) {
            return null;
        }
        String language = locale.getLanguage();
        if (dateStyle == FormatStyle.LONG && AT_TIME_GLUE.containsKey(language + "-long")) {
            return AT_TIME_GLUE.get(language + "-long");
        }
        return AT_TIME_GLUE.get(language);
    }

    /**
     * Get the replacement pattern for a field character, or null if the field is not requested.
     */
    private String getFieldReplacement(char field) {
        return switch (field) {
            case 'y' -> numericPattern("y", dateFields.year());
            case 'd' -> numericPattern("d", dateFields.day());
            case 'M', 'L' -> {
                String month = dateFields.month();
                if (month == null || "numeric".equals(month) || "2-digit".equals(month)) {
                    yield numericPattern("M", month);
                }
                yield switch (month) {
                    case "short", "long", "narrow" -> getTextFieldPattern('M', month, 3);
                    default -> "M";
                };
            }
            case 'E', 'e', 'c' ->
                dateFields.weekday() == null ? null : getTextFieldPattern('E', dateFields.weekday(), 1);
            case 'G' -> dateFields.era() == null ? null : getTextFieldPattern('G', dateFields.era(), 1);
            default -> null;
        };
    }

    /**
     * The pattern letters for a text field: short, long and narrow widths are 1-or-3, 4 and 5 letters.
     *
     * @param field
     *            the pattern letter
     * @param option
     *            short, long or narrow, or null when the field is not requested
     * @param shortWidth
     *            the width of the short form (3 for months and weekdays, 1 for eras)
     * @return the field pattern, or an empty string when the field is not requested
     */
    private String getTextFieldPattern(char field, String option, int shortWidth) {
        if (option == null) {
            return "";
        }
        int width = switch (option) {
            case "long" -> 4;
            case "narrow" -> 5;
            default -> shortWidth;
        };
        return String.valueOf(field).repeat(width);
    }

    private String invokeLocalizedSkeletonPattern(String skeleton) {
        try {
            return (String) LOCALIZED_SKELETON_PATTERN.invoke(null, skeleton, IsoChronology.INSTANCE, locale);
        } catch (InvocationTargetException | IllegalAccessException e) {
            // The JDK throws DateTimeException when its CLDR data has no pattern for the skeleton.
            return null;
        }
    }

    /**
     * Check if a character is a separator in a date/time pattern. Covers ASCII space and Unicode space characters like
     * U+202F (narrow no-break space) and U+00A0 (no-break space) used in CLDR patterns.
     */
    private static boolean isPatternSeparator(char c) {
        return c == ' ' || c == '\u202F' || c == '\u00A0';
    }

    /** Each token belongs to one pattern. Its formatter can be reused for every value of that instance. */
    static final class Token {
        final char field;
        private volatile DateTimeFormatter formatter;
        final String text;

        Token(char field, String text) {
            this.field = field;
            this.text = text;
        }

        String format(java.time.ZonedDateTime value, Locale locale, Chronology chronology) {
            DateTimeFormatter local = formatter;
            if (local == null) {
                local = DateTimeFormatter.ofPattern(text, locale);
                if (chronology != null) {
                    local = local.withChronology(chronology);
                }
                formatter = local;
            }
            try {
                return local.format(value);
            } catch (java.time.DateTimeException ignored) {
                return DateTimeFormatter.ofPattern(text, locale).format(value);
            }
        }
    }

    static List<Token> tokenize(String pattern) {
        List<Token> tokens = new ArrayList<>();
        for (int start = 0; start < pattern.length();) {
            char c = pattern.charAt(start);
            int end = start + 1;
            if (c == '\'') {
                int close = pattern.indexOf('\'', end);
                end = close < 0 ? pattern.length() : close;
                tokens.add(new Token('\0', pattern.substring(start + 1, end)));
                start = end + 1;
            } else {
                boolean field = Character.isLetter(c);
                while (end < pattern.length()) {
                    char next = pattern.charAt(end);
                    if (field ? next != c : Character.isLetter(next) || next == '\'') {
                        break;
                    }
                    end++;
                }
                tokens.add(new Token(field ? c : '\0', pattern.substring(start, end)));
                start = end;
            }
        }
        return tokens;
    }

    private static String numericPattern(String field, String option) {
        if (option == null) {
            return null;
        }
        return "2-digit".equals(option) ? field + field : field;
    }
}
