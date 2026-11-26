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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.core.temporal.IsoCalendarDate;
import com.caoccao.qjs4j.core.temporal.IsoDate;
import com.caoccao.qjs4j.core.temporal.TemporalCalendarId;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.*;
import java.time.chrono.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Intl.DateTimeFormat instance object. Stores all resolved options per ECMA-402.
 */
public final class JSIntlDateTimeFormat extends JSObject {
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
    private static final String[] EARTHLY_BRANCHES = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
    private static final String[] HEAVENLY_STEMS = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
    /**
     * {@code DateTimeFormatterBuilder.getLocalizedDateTimePattern(String, Chronology, Locale)}, the CLDR skeleton
     * lookup added in JDK 19. Null on JDK 17 and 18, where component patterns are derived from the short and medium
     * date styles instead.
     */
    private static final Method LOCALIZED_SKELETON_PATTERN = findLocalizedSkeletonPatternMethod();
    public static final String NAME = "Intl.DateTimeFormat";
    private JSFunction boundFormatFunction;
    private final String calendar;
    private final FormatStyle dateStyle;
    private final String dayOption;
    private final String dayPeriodOption;
    private final String eraOption;
    private final String formatPattern;
    private final Integer fractionalSecondDigits;
    private final boolean hasDefaultDateComponents;
    private final boolean hasDefaultTimeComponents;
    private final String hourCycle;
    private final String hourCycleForInstant;
    private final String hourOption;
    private final Locale locale;
    private final String minuteOption;
    private final String monthOption;
    private final String numberingSystem;
    private final String secondOption;
    private final FormatStyle timeStyle;
    private final String timeZone;
    private final String timeZoneNameOption;
    private final String weekdayOption;
    private final String yearOption;

    public JSIntlDateTimeFormat(JSContext context, Locale locale, FormatStyle dateStyle, FormatStyle timeStyle,
            String calendar, String numberingSystem, String timeZone, String hourCycle, String hourCycleForInstant,
            String weekdayOption, String eraOption, String yearOption, String monthOption, String dayOption,
            String dayPeriodOption, String hourOption, String minuteOption, String secondOption,
            Integer fractionalSecondDigits, String timeZoneNameOption, boolean hasDefaultDateComponents,
            boolean hasDefaultTimeComponents) {
        super(context);
        this.locale = locale;
        this.dateStyle = dateStyle;
        this.timeStyle = timeStyle;
        this.calendar = calendar;
        this.numberingSystem = numberingSystem;
        this.timeZone = timeZone;
        this.hourCycle = hourCycle;
        this.hourCycleForInstant = hourCycleForInstant;
        this.weekdayOption = weekdayOption;
        this.eraOption = eraOption;
        this.yearOption = yearOption;
        this.monthOption = monthOption;
        this.dayOption = dayOption;
        this.dayPeriodOption = dayPeriodOption;
        this.hourOption = hourOption;
        this.minuteOption = minuteOption;
        this.secondOption = secondOption;
        this.fractionalSecondDigits = fractionalSecondDigits;
        this.timeZoneNameOption = timeZoneNameOption;
        this.hasDefaultDateComponents = hasDefaultDateComponents;
        this.hasDefaultTimeComponents = hasDefaultTimeComponents;
        this.formatPattern = buildFormatPattern();
    }

    /**
     * Adjust a localized base pattern by replacing field widths based on options and removing fields that are not
     * requested.
     */
    private String adjustPatternFields(String basePattern, boolean isDate) {
        // First, parse the pattern into tokens (fields and literals)
        List<Object[]> tokens = new ArrayList<>(); // [type, value] where type is 'field' or 'literal'
        int i = 0;
        while (i < basePattern.length()) {
            char c = basePattern.charAt(i);
            if (c == '\'') {
                int end = basePattern.indexOf('\'', i + 1);
                if (end < 0) {
                    end = basePattern.length();
                }
                tokens.add(new Object[]{"literal", basePattern.substring(i + 1, end)});
                i = end + 1;
            } else if (Character.isLetter(c)) {
                int fieldStart = i;
                while (i < basePattern.length() && basePattern.charAt(i) == c) {
                    i++;
                }
                tokens.add(new Object[]{"field", String.valueOf(c), i - fieldStart});
            } else {
                int litStart = i;
                while (i < basePattern.length() && !Character.isLetter(basePattern.charAt(i))
                        && basePattern.charAt(i) != '\'') {
                    i++;
                }
                tokens.add(new Object[]{"literal", basePattern.substring(litStart, i)});
            }
        }

        // Filter out fields that are not requested and adjust widths
        List<Object[]> filtered = new ArrayList<>();
        for (Object[] token : tokens) {
            if ("field".equals(token[0])) {
                char fieldChar = ((String) token[1]).charAt(0);
                String replacement = getFieldReplacement(fieldChar);
                if (replacement != null) {
                    filtered.add(new Object[]{"field", replacement});
                }
                // If null, the field is not requested — skip it and adjacent separator
            } else {
                filtered.add(token);
            }
        }

        // Clean up: remove leading/trailing separators, and double separators
        List<Object[]> cleaned = new ArrayList<>();
        for (int j = 0; j < filtered.size(); j++) {
            Object[] token = filtered.get(j);
            if ("literal".equals(token[0])) {
                // Skip if at start, at end, or between two literals (no field between)
                boolean prevIsField = j > 0
                        && "field".equals(cleaned.isEmpty() ? null : cleaned.get(cleaned.size() - 1)[0]);
                boolean nextIsField = false;
                for (int k = j + 1; k < filtered.size(); k++) {
                    if ("field".equals(filtered.get(k)[0])) {
                        nextIsField = true;
                        break;
                    }
                }
                if (prevIsField && nextIsField) {
                    cleaned.add(token);
                }
            } else {
                cleaned.add(token);
            }
        }

        // Build final pattern
        StringBuilder result = new StringBuilder();
        for (Object[] token : cleaned) {
            if ("field".equals(token[0])) {
                result.append(token[1]);
            } else {
                String lit = (String) token[1];
                // Quote literals that contain letters
                boolean hasLetters = false;
                for (char ch : lit.toCharArray()) {
                    if (Character.isLetter(ch)) {
                        hasLetters = true;
                        break;
                    }
                }
                if (hasLetters) {
                    result.append('\'').append(lit).append('\'');
                } else {
                    result.append(lit);
                }
            }
        }
        return result.toString();
    }

    private String applyNumberingSystem(String text) {
        String system = numberingSystem;
        if (system == null) {
            system = "ar".equals(locale.getLanguage()) ? "arab" : "latn";
        }
        return switch (system) {
            case "arab" -> IntlNumberingSystem.localize(text, system, true);
            case "deva", "hanidec" -> IntlNumberingSystem.localize(text, system, false);
            default -> text;
        };
    }

    /**
     * Build format pattern from individual component options.
     */
    private String buildComponentPattern() {
        boolean hasDate = yearOption != null || monthOption != null || dayOption != null || weekdayOption != null
                || eraOption != null;
        boolean hasTime = hourOption != null || minuteOption != null || secondOption != null || dayPeriodOption != null
                || fractionalSecondDigits != null || timeZoneNameOption != null;

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
            boolean useTextMonth = "short".equals(monthOption) || "long".equals(monthOption)
                    || "narrow".equals(monthOption);
            FormatStyle baseStyle = useTextMonth ? FormatStyle.MEDIUM : FormatStyle.SHORT;
            String basePattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(baseStyle, null,
                    IsoChronology.INSTANCE, locale);
            adjustedPattern = adjustPatternFields(basePattern, true);
        }
        if (weekdayOption != null && adjustedPattern.indexOf('E') < 0 && adjustedPattern.indexOf('e') < 0
                && adjustedPattern.indexOf('c') < 0) {
            String weekdayPattern = switch (weekdayOption) {
                case "long" -> "EEEE";
                case "narrow" -> "EEEEE";
                default -> "E";
            };
            adjustedPattern = adjustedPattern.isEmpty() ? weekdayPattern : weekdayPattern + ", " + adjustedPattern;
        }
        if (eraOption != null && !"chinese".equals(calendar) && !"dangi".equals(calendar)
                && adjustedPattern.indexOf('G') < 0) {
            adjustedPattern = adjustedPattern + " G";
        }
        return adjustedPattern;
    }

    /**
     * Build the format pattern based on options. For dateStyle/timeStyle, uses Java's localized pattern. For component
     * options, builds a custom pattern.
     */
    private String buildFormatPattern() {
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
        if (LOCALIZED_SKELETON_PATTERN == null || resolveChronology() != IsoChronology.INSTANCE
                || (yearOption == null && monthOption == null && dayOption == null)) {
            return null;
        }
        String era = getTextFieldPattern('G', eraOption, 1);
        String month = monthOption == null
                ? ""
                : "numeric".equals(monthOption) || "2-digit".equals(monthOption)
                        ? "M"
                        : getTextFieldPattern('M', monthOption, 3);
        String weekday = getTextFieldPattern('E', weekdayOption, 3);
        String year = yearOption == null ? "" : "y";
        String day = dayOption == null ? "" : "d";
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
                case 'y' -> field = "2-digit".equals(yearOption) ? "yy" : "y";
                case 'M', 'L' -> {
                    if ("2-digit".equals(monthOption)) {
                        field = String.valueOf(c).repeat(2);
                    } else if (field.length() >= 3) {
                        field = getTextFieldPattern(c, monthOption, 3);
                    }
                }
                case 'd' -> field = "2-digit".equals(dayOption) ? "dd" : field;
                case 'E', 'c', 'e' -> field = getTextFieldPattern(c, weekdayOption, 3);
                case 'G' -> field = getTextFieldPattern('G', eraOption, 1);
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
        boolean hasClockField = hourOption != null || minuteOption != null || secondOption != null
                || fractionalSecondDigits != null;
        if (!hasClockField && dayPeriodOption != null) {
            switch (dayPeriodOption) {
                case "long" -> pattern.append("BBBB");
                case "narrow" -> pattern.append("BBBBB");
                default -> pattern.append("B");
            }
        }
        if (!hasClockField && dayPeriodOption == null && timeZoneNameOption == null) {
            return pattern.toString();
        }
        if (hourOption != null) {
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
            boolean useTwoDigitHour = "2-digit".equals(hourOption) || !use12Hour;
            if (useTwoDigitHour) {
                pattern.append(String.valueOf(hourChar).repeat(2));
            } else {
                pattern.append(hourChar);
            }
        }
        if (minuteOption != null) {
            if (pattern.length() > 0) {
                pattern.append(':');
            }
            pattern.append("mm");
        }
        if (secondOption != null) {
            if (pattern.length() > 0) {
                pattern.append(':');
            }
            pattern.append("ss");
        }
        if (fractionalSecondDigits != null) {
            pattern.append('.');
            pattern.append("S".repeat(fractionalSecondDigits));
        }
        if (hourOption != null) {
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

    /**
     * Decompose a formatted date into typed parts using the format pattern.
     */
    private List<DatePart> decomposeParts(ZonedDateTime dateTime, ZoneId zoneId) {
        List<DatePart> parts = new ArrayList<>();
        LunarDate lunarDate = null;
        boolean useLunarParts = isChineseOrDangiCalendar();
        if (useLunarParts) {
            lunarDate = toLunisolarDate(dateTime.toLocalDate(), resolveTemporalCalendarId());
            if (isLunarYearOnlyPattern()) {
                String yearName = chineseYearName(lunarDate.relatedYear());
                if ("zh".equals(locale.getLanguage())) {
                    parts.add(new DatePart("yearName", yearName));
                    parts.add(new DatePart("literal", "年"));
                } else {
                    parts.add(new DatePart("relatedYear", Integer.toString(lunarDate.relatedYear())));
                    parts.add(new DatePart("yearName", yearName));
                }
                return parts;
            }
        }
        int i = 0;
        while (i < formatPattern.length()) {
            char c = formatPattern.charAt(i);
            if (c == '\'') {
                int end = formatPattern.indexOf('\'', i + 1);
                if (end < 0) {
                    end = formatPattern.length();
                }
                String literal = formatPattern.substring(i + 1, end);
                if (!literal.isEmpty()) {
                    parts.add(new DatePart("literal", literal));
                }
                i = end + 1;
            } else if (Character.isLetter(c)) {
                int fieldStart = i;
                while (i < formatPattern.length() && formatPattern.charAt(i) == c) {
                    i++;
                }
                int fieldWidth = i - fieldStart;
                if (useLunarParts && c == 'y' && lunarDate != null) {
                    parts.add(new DatePart("relatedYear",
                            applyNumberingSystem(Integer.toString(lunarDate.relatedYear()))));
                    parts.add(new DatePart("yearName", chineseYearName(lunarDate.relatedYear())));
                    continue;
                }
                String type = fieldCharToType(c, useLunarParts);
                String value = formatField(dateTime, zoneId, c, fieldWidth, lunarDate);
                if (isNumericDatePartType(type)) {
                    value = applyNumberingSystem(value);
                }
                if ("second".equals(type) && fractionalSecondDigits != null && i < formatPattern.length()
                        && formatPattern.charAt(i) == '.') {
                    // The second field itself; fractional part comes next via '.' + 'S' tokens
                    parts.add(new DatePart(type, value));
                } else {
                    parts.add(new DatePart(type, value));
                }
            } else {
                int litStart = i;
                while (i < formatPattern.length() && !Character.isLetter(formatPattern.charAt(i))
                        && formatPattern.charAt(i) != '\'') {
                    i++;
                }
                parts.add(new DatePart("literal", formatPattern.substring(litStart, i)));
            }
        }
        return normalizeLiterals(parts);
    }

    public String format(double epochMillis) {
        if (!Double.isFinite(epochMillis)) {
            return "Invalid Date";
        }
        ZoneId zoneId = resolveZoneId();
        ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli((long) epochMillis), zoneId);
        List<DatePart> parts = decomposeParts(dateTime, zoneId);
        StringBuilder result = new StringBuilder();
        for (DatePart part : parts) {
            result.append(part.value());
        }
        return applyNumberingSystem(result.toString());
    }

    /**
     * Format a single field from a ZonedDateTime.
     */
    private String formatField(ZonedDateTime dateTime, ZoneId zoneId, char field, int width, LunarDate lunarDate) {
        if ((field == 'a' || field == 'b' || field == 'B') && dayPeriodOption != null
                && "en".equals(locale.getLanguage())) {
            return resolveEnglishDayPeriod(dateTime.getHour(), dayPeriodOption);
        }
        Chronology chronology = resolveChronology();
        IsoCalendarDate calendarDateFields = null;
        boolean useTemporalIslamicFields = isIslamicCalendar() && hasExplicitNumericDateOptions();
        boolean useTemporalCalendarFields = (chronology == null && calendar != null && !isChineseOrDangiCalendar())
                || useTemporalIslamicFields;
        if (useTemporalCalendarFields) {
            IsoDate isoDate = new IsoDate(dateTime.getYear(), dateTime.getMonthValue(), dateTime.getDayOfMonth());
            calendarDateFields = isoDate.toIsoCalendarDate(resolveTemporalCalendarId());
        }
        if (field == 'G' && eraOption != null) {
            int year = dateTime.getYear();
            return resolveEraValue(year);
        }
        if (lunarDate != null) {
            if (field == 'y') {
                return Integer.toString(lunarDate.relatedYear());
            }
            if (field == 'M' || field == 'L') {
                String monthValue;
                if (width >= 2) {
                    monthValue = String.format(locale, "%02d", lunarDate.month());
                } else {
                    monthValue = Integer.toString(lunarDate.month());
                }
                if (lunarDate.leapMonth()) {
                    return monthValue + "bis";
                }
                return monthValue;
            }
            if (field == 'd') {
                if (width >= 2) {
                    return String.format(locale, "%02d", lunarDate.day());
                }
                return Integer.toString(lunarDate.day());
            }
        }
        if (calendarDateFields != null) {
            if (field == 'y') {
                return Integer.toString(calendarDateFields.year());
            }
            if (field == 'M' || field == 'L') {
                if ("hebrew".equals(calendar)) {
                    return resolveHebrewMonthName(calendarDateFields.monthCode());
                }
                if (width >= 2) {
                    return String.format(locale, "%02d", calendarDateFields.month());
                }
                return Integer.toString(calendarDateFields.month());
            }
            if (field == 'd') {
                if (width >= 2) {
                    return String.format(locale, "%02d", calendarDateFields.day());
                }
                return Integer.toString(calendarDateFields.day());
            }
        }
        String pattern = String.valueOf(field).repeat(width);
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(pattern, locale).withZone(zoneId);
        if (chronology != null) {
            dateTimeFormatter = dateTimeFormatter.withChronology(chronology);
        }
        try {
            return dateTimeFormatter.format(dateTime);
        } catch (DateTimeException ignored) {
            if (field == 'G' && eraOption != null) {
                return resolveEraValue(dateTime.getYear());
            }
            DateTimeFormatter isoFormatter = DateTimeFormatter.ofPattern(pattern, locale).withZone(zoneId);
            return isoFormatter.format(dateTime);
        }
    }

    /**
     * Format a date into typed parts for formatToParts().
     */
    public List<DatePart> formatToPartsList(double epochMillis) {
        if (!Double.isFinite(epochMillis)) {
            return List.of(new DatePart("literal", "Invalid Date"));
        }
        ZoneId zoneId = resolveZoneId();
        ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli((long) epochMillis), zoneId);
        return decomposeParts(dateTime, zoneId);
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

    public JSFunction getBoundFormatFunction() {
        return boundFormatFunction;
    }

    public String getCalendar() {
        return calendar;
    }

    public FormatStyle getDateStyle() {
        return dateStyle;
    }

    public String getDayOption() {
        return dayOption;
    }

    /**
     * Adjust hour-related fields and AM/PM markers in a pattern to match the requested hourCycle. h11=K(0-11),
     * h12=h(1-12), h23=H(0-23), h24=k(1-24).
     */

    public String getDayPeriodOption() {
        return dayPeriodOption;
    }

    public String getEraOption() {
        return eraOption;
    }

    /**
     * Get the replacement pattern for a field character, or null if the field is not requested.
     */
    private String getFieldReplacement(char field) {
        return switch (field) {
            case 'y' -> {
                if (yearOption == null) {
                    yield null;
                }
                yield "2-digit".equals(yearOption) ? "yy" : "y";
            }
            case 'M', 'L' -> {
                if (monthOption == null) {
                    yield null;
                }
                yield switch (monthOption) {
                    case "numeric" -> "M";
                    case "2-digit" -> "MM";
                    case "short" -> "MMM";
                    case "long" -> "MMMM";
                    case "narrow" -> "MMMMM";
                    default -> "M";
                };
            }
            case 'd' -> {
                if (dayOption == null) {
                    yield null;
                }
                yield "2-digit".equals(dayOption) ? "dd" : "d";
            }
            case 'E', 'e', 'c' -> {
                if (weekdayOption == null) {
                    yield null;
                }
                yield switch (weekdayOption) {
                    case "long" -> "EEEE";
                    case "short" -> "E";
                    case "narrow" -> "EEEEE";
                    default -> "E";
                };
            }
            case 'G' -> {
                if (eraOption == null) {
                    yield null;
                }
                yield switch (eraOption) {
                    case "long" -> "GGGG";
                    case "short" -> "G";
                    case "narrow" -> "GGGGG";
                    default -> "G";
                };
            }
            default -> null; // Skip unknown date fields
        };
    }

    public Integer getFractionalSecondDigits() {
        return fractionalSecondDigits;
    }

    public String getHourCycle() {
        return hourCycle;
    }

    public String getHourCycleForInstant() {
        return hourCycleForInstant;
    }

    public String getHourOption() {
        return hourOption;
    }

    public Locale getLocale() {
        return locale;
    }

    public String getMinuteOption() {
        return minuteOption;
    }

    public String getMonthOption() {
        return monthOption;
    }

    public String getNumberingSystem() {
        return numberingSystem;
    }

    public String getSecondOption() {
        return secondOption;
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

    public FormatStyle getTimeStyle() {
        return timeStyle;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public String getTimeZoneNameOption() {
        return timeZoneNameOption;
    }

    public String getWeekdayOption() {
        return weekdayOption;
    }

    public String getYearOption() {
        return yearOption;
    }

    public boolean hasDefaultDateComponents() {
        return hasDefaultDateComponents;
    }

    public boolean hasDefaultTimeComponents() {
        return hasDefaultTimeComponents;
    }

    private boolean hasExplicitNumericDateOptions() {
        boolean hasNumericYear = "numeric".equals(yearOption) || "2-digit".equals(yearOption);
        boolean hasNumericMonth = "numeric".equals(monthOption) || "2-digit".equals(monthOption);
        boolean hasNumericDay = "numeric".equals(dayOption) || "2-digit".equals(dayOption);
        return hasNumericYear && hasNumericMonth && hasNumericDay;
    }

    /**
     * Check if this formatter uses a text-based month (short, long, or narrow).
     */
    public boolean hasTextMonth() {
        return "short".equals(monthOption) || "long".equals(monthOption) || "narrow".equals(monthOption);
    }

    private String invokeLocalizedSkeletonPattern(String skeleton) {
        try {
            return (String) LOCALIZED_SKELETON_PATTERN.invoke(null, skeleton, IsoChronology.INSTANCE, locale);
        } catch (InvocationTargetException | IllegalAccessException e) {
            // The JDK throws DateTimeException when its CLDR data has no pattern for the skeleton.
            return null;
        }
    }

    private boolean isChineseOrDangiCalendar() {
        if (calendar == null) {
            return false;
        }
        return "chinese".equals(calendar) || "dangi".equals(calendar);
    }

    private boolean isIslamicCalendar() {
        if (calendar == null) {
            return false;
        }
        return "islamic-civil".equals(calendar) || "islamic-tbla".equals(calendar)
                || "islamic-umalqura".equals(calendar) || "islamic".equals(calendar) || "islamic-rgsa".equals(calendar);
    }

    private boolean isLunarYearOnlyPattern() {
        boolean hasDateOnlyYear = yearOption != null && monthOption == null && dayOption == null
                && weekdayOption == null;
        boolean hasNoTimeFields = dayPeriodOption == null && hourOption == null && minuteOption == null
                && secondOption == null && fractionalSecondDigits == null && timeZoneNameOption == null;
        return hasDateOnlyYear && hasNoTimeFields;
    }

    private Chronology resolveChronology() {
        if (calendar == null || "gregory".equals(calendar) || "iso8601".equals(calendar)) {
            return IsoChronology.INSTANCE;
        }
        return switch (calendar) {
            case "buddhist" -> ThaiBuddhistChronology.INSTANCE;
            case "japanese" -> JapaneseChronology.INSTANCE;
            case "roc" -> MinguoChronology.INSTANCE;
            case "islamic-civil", "islamic-tbla", "islamic-umalqura", "islamic", "islamic-rgsa" ->
                HijrahChronology.INSTANCE;
            default -> null;
        };
    }

    private String resolveEraValue(int isoYear) {
        if (calendar == null || "gregory".equals(calendar)) {
            boolean beforeChrist = isoYear <= 0;
            return switch (eraOption) {
                case "long" -> beforeChrist ? "Before Christ" : "Anno Domini";
                case "narrow" -> beforeChrist ? "B" : "A";
                default -> beforeChrist ? "BC" : "AD";
            };
        }
        if ("japanese".equals(calendar)) {
            if (isoYear >= 2019) {
                return "Reiwa";
            }
            if (isoYear >= 1989) {
                return "Heisei";
            }
            if (isoYear >= 1926) {
                return "Showa";
            }
            if (isoYear >= 1912) {
                return "Taisho";
            }
            if (isoYear >= 1873) {
                return "Meiji";
            }
            if (isoYear > 0) {
                return "CE";
            }
            return "BCE";
        }
        if ("roc".equals(calendar)) {
            return isoYear >= 1912 ? "Minguo" : "Before R.O.C.";
        }
        if ("islamic-civil".equals(calendar) || "islamic-tbla".equals(calendar) || "islamic-umalqura".equals(calendar)
                || "islamic".equals(calendar) || "islamic-rgsa".equals(calendar)) {
            return isoYear >= 622 ? "AH" : "Before AH";
        }
        if ("buddhist".equals(calendar) || "ethioaa".equals(calendar) || "hebrew".equals(calendar)
                || "indian".equals(calendar) || "persian".equals(calendar)) {
            return calendar;
        }
        return isoYear <= 0 ? "BC" : "AD";
    }

    private String resolveHebrewMonthName(String monthCode) {
        return switch (monthCode) {
            case "M01" -> "Tishri";
            case "M02" -> "Heshvan";
            case "M03" -> "Kislev";
            case "M04" -> "Tevet";
            case "M05" -> "Shevat";
            case "M05L" -> "Adar I";
            case "M06" -> "Adar";
            case "M07" -> "Nisan";
            case "M08" -> "Iyar";
            case "M09" -> "Sivan";
            case "M10" -> "Tamuz";
            case "M11" -> "Av";
            case "M12" -> "Elul";
            default -> monthCode;
        };
    }

    private TemporalCalendarId resolveTemporalCalendarId() {
        TemporalCalendarId temporalCalendarId = TemporalCalendarId.fromIdentifier(calendar);
        if (temporalCalendarId == null) {
            return TemporalCalendarId.ISO8601;
        }
        return temporalCalendarId;
    }

    private ZoneId resolveZoneId() {
        if (timeZone != null) {
            try {
                return ZoneId.of(timeZone);
            } catch (Exception e) {
                // Try ZoneId.SHORT_IDS for abbreviated timezone names (EST, MST, HST, etc.)
                try {
                    return ZoneId.of(timeZone, ZoneId.SHORT_IDS);
                } catch (Exception e2) {
                    // Fall back to java.util.TimeZone which knows more aliases
                    java.util.TimeZone tz = java.util.TimeZone.getTimeZone(timeZone);
                    if (!"GMT".equals(tz.getID()) || "GMT".equalsIgnoreCase(timeZone)) {
                        return tz.toZoneId();
                    }
                }
            }
        }
        return ZoneId.systemDefault();
    }

    public void setBoundFormatFunction(JSFunction boundFormatFunction) {
        this.boundFormatFunction = boundFormatFunction;
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

    private static String chineseYearName(int relatedYear) {
        int stemIndex = Math.floorMod(relatedYear - 4, 10);
        int branchIndex = Math.floorMod(relatedYear - 4, 12);
        return HEAVENLY_STEMS[stemIndex] + EARTHLY_BRANCHES[branchIndex];
    }

    /**
     * Map a pattern field character to its ECMA-402 part type name.
     */
    private static String fieldCharToType(char c, boolean useLunarRelatedYear) {
        return switch (c) {
            case 'y' -> useLunarRelatedYear ? "relatedYear" : "year";
            case 'M', 'L' -> "month";
            case 'd' -> "day";
            case 'h', 'H', 'k', 'K' -> "hour";
            case 'm' -> "minute";
            case 's' -> "second";
            case 'S' -> "fractionalSecond";
            case 'E', 'e', 'c' -> "weekday";
            case 'G' -> "era";
            case 'a', 'b', 'B' -> "dayPeriod";
            case 'z', 'Z', 'v', 'V', 'O', 'X', 'x' -> "timeZoneName";
            default -> "literal";
        };
    }

    private static Method findLocalizedSkeletonPatternMethod() {
        try {
            return DateTimeFormatterBuilder.class.getMethod("getLocalizedDateTimePattern", String.class,
                    Chronology.class, Locale.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static boolean isNumericDatePartType(String datePartType) {
        return "year".equals(datePartType) || "relatedYear".equals(datePartType) || "month".equals(datePartType)
                || "day".equals(datePartType) || "hour".equals(datePartType) || "minute".equals(datePartType)
                || "second".equals(datePartType) || "fractionalSecond".equals(datePartType);
    }

    /**
     * Check if a character is a separator in a date/time pattern. Covers ASCII space and Unicode space characters like
     * U+202F (narrow no-break space) and U+00A0 (no-break space) used in CLDR patterns.
     */
    private static boolean isPatternSeparator(char c) {
        return c == ' ' || c == '\u202F' || c == '\u00A0';
    }

    /**
     * Finish the literal parts. Adjacent literals are one part in V8: a quoted pattern literal such as the "at" of "{1}
     * 'at' {0}" is not a separate part from the spaces around it. And V8 reverts ICU 72's U+202F NARROW NO-BREAK SPACE
     * to U+0020 in formatted dates (crbug.com/1414292): CLDR 42+ patterns put it before AM/PM and inside "2024 г.",
     * where web content expects an ordinary space. The JDK's CLDR data has the same character, and the formatter used
     * to insert it before English AM/PM even on JDK 17. V8 reverts it in format() only; doing it in the parts too keeps
     * format(), formatToParts(), formatRange() and toLocaleString() consistent, which test262 checks.
     */
    private static List<DatePart> normalizeLiterals(List<DatePart> parts) {
        List<DatePart> result = new ArrayList<>(parts.size());
        for (DatePart part : parts) {
            if (!"literal".equals(part.type())) {
                result.add(part);
                continue;
            }
            String value = part.value().replace('\u202F', ' ');
            int lastIndex = result.size() - 1;
            if (lastIndex >= 0 && "literal".equals(result.get(lastIndex).type())) {
                result.set(lastIndex, new DatePart("literal", result.get(lastIndex).value() + value));
            } else {
                result.add(new DatePart("literal", value));
            }
        }
        return result;
    }

    private static String resolveEnglishDayPeriod(int hourOfDay, String dayPeriodStyle) {
        if (hourOfDay == 12) {
            return "narrow".equals(dayPeriodStyle) ? "n" : "noon";
        }
        if (hourOfDay >= 6 && hourOfDay < 12) {
            return "in the morning";
        }
        if (hourOfDay >= 13 && hourOfDay < 18) {
            return "in the afternoon";
        }
        if (hourOfDay >= 18 && hourOfDay < 21) {
            return "in the evening";
        }
        return "at night";
    }

    private static LunarDate toLunisolarDate(LocalDate gregorianDate, TemporalCalendarId calendarId) {
        IsoDate isoDate = new IsoDate(gregorianDate.getYear(), gregorianDate.getMonthValue(),
                gregorianDate.getDayOfMonth());
        IsoCalendarDate calendarDateFields = isoDate.toIsoCalendarDate(calendarId);
        String monthCode = calendarDateFields.monthCode();
        int monthNumber = Integer.parseInt(monthCode.substring(1, 3));
        boolean leapMonth = monthCode.endsWith("L");
        return new LunarDate(calendarDateFields.year(), monthNumber, calendarDateFields.day(), leapMonth);
    }

    /**
     * A typed part from date formatting.
     */
    public record DatePart(String type, String value) {
    }

    private record LunarDate(int relatedYear, int month, int day, boolean leapMonth) {
    }
}
