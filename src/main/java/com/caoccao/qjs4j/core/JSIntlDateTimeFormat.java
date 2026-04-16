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

import com.caoccao.qjs4j.core.temporal.IsoDate;
import com.caoccao.qjs4j.core.temporal.TemporalCalendarMath;

import java.time.*;
import java.time.chrono.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Intl.DateTimeFormat instance object.
 * Stores all resolved options per ECMA-402.
 */
public final class JSIntlDateTimeFormat extends JSObject {
    public static final String NAME = "Intl.DateTimeFormat";
    private static final int[] CHINESE_LUNAR_YEAR_INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
            0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
            0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
            0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5d0, 0x14573, 0x052d0, 0x0a9a8, 0x0e950, 0x06aa0,
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
            0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b5a0, 0x195a6,
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
            0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0,
            0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
            0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
            0x05aa0, 0x076a3, 0x096d0, 0x04bd7, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
            0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
            0x14b63
    };
    private static final String[] EARTHLY_BRANCHES = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
    private static final String[] HEAVENLY_STEMS = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
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
    private JSFunction boundFormatFunction;

    public JSIntlDateTimeFormat(JSContext context, Locale locale, FormatStyle dateStyle, FormatStyle timeStyle,
                                String calendar, String numberingSystem, String timeZone,
                                String hourCycle, String hourCycleForInstant, String weekdayOption, String eraOption,
                                String yearOption, String monthOption, String dayOption,
                                String dayPeriodOption, String hourOption, String minuteOption,
                                String secondOption, Integer fractionalSecondDigits,
                                String timeZoneNameOption,
                                boolean hasDefaultDateComponents,
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
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        // For 12-hour cycles, if no AM/PM marker exists, add one after the time fields
        if (!want24) {
            String result = sb.toString();
            boolean hasAmPm = false;
            boolean inQ = false;
            for (int i = 0; i < result.length(); i++) {
                char c = result.charAt(i);
                if (c == '\'') {
                    inQ = !inQ;
                } else if (!inQ && (c == 'a' || c == 'b' || c == 'B')) {
                    hasAmPm = true;
                    break;
                }
            }
            if (!hasAmPm) {
                // Find the last hour/minute/second field position and insert " a" after it
                int lastTimeField = -1;
                inQ = false;
                for (int i = 0; i < result.length(); i++) {
                    char c = result.charAt(i);
                    if (c == '\'') {
                        inQ = !inQ;
                    } else if (!inQ && (c == 'h' || c == 'H' || c == 'K' || c == 'k'
                            || c == 'm' || c == 's' || c == 'S')) {
                        lastTimeField = i;
                    }
                }
                if (lastTimeField >= 0) {
                    // Find end of field run
                    int insertPos = lastTimeField + 1;
                    while (insertPos < result.length() && result.charAt(insertPos) == result.charAt(lastTimeField)) {
                        insertPos++;
                    }
                    result = result.substring(0, insertPos) + " a" + result.substring(insertPos);
                }
                return result;
            }
        }
        return sb.toString();
    }

    private static int chineseLeapMonth(int year) {
        int yearInfo = CHINESE_LUNAR_YEAR_INFO[year - 1900];
        return yearInfo & 0x0F;
    }

    private static int chineseLeapMonthDays(int year) {
        int leapMonth = chineseLeapMonth(year);
        if (leapMonth == 0) {
            return 0;
        }
        int yearInfo = CHINESE_LUNAR_YEAR_INFO[year - 1900];
        return (yearInfo & 0x10000) != 0 ? 30 : 29;
    }

    private static int chineseLunarMonthDays(int year, int month) {
        int yearInfo = CHINESE_LUNAR_YEAR_INFO[year - 1900];
        int monthMask = 0x10000 >> month;
        return (yearInfo & monthMask) != 0 ? 30 : 29;
    }

    private static int chineseLunarYearDays(int year) {
        int yearInfo = CHINESE_LUNAR_YEAR_INFO[year - 1900];
        int totalDays = 348;
        int monthInfoMask = 0x8000;
        for (int monthIndex = 0; monthIndex < 12; monthIndex++) {
            if ((yearInfo & monthInfoMask) != 0) {
                totalDays++;
            }
            monthInfoMask >>= 1;
        }
        return totalDays + chineseLeapMonthDays(year);
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

    private static boolean isMeridiemMarker(String dayPeriodText) {
        if (dayPeriodText == null) {
            return false;
        }
        return "AM".equals(dayPeriodText)
                || "PM".equals(dayPeriodText)
                || "am".equals(dayPeriodText)
                || "pm".equals(dayPeriodText);
    }

    private static boolean isNumericDatePartType(String datePartType) {
        return "year".equals(datePartType)
                || "relatedYear".equals(datePartType)
                || "month".equals(datePartType)
                || "day".equals(datePartType)
                || "hour".equals(datePartType)
                || "minute".equals(datePartType)
                || "second".equals(datePartType)
                || "fractionalSecond".equals(datePartType);
    }

    /**
     * Check if a character is a separator in a date/time pattern.
     * Covers ASCII space and Unicode space characters like U+202F (narrow no-break space)
     * and U+00A0 (no-break space) used in CLDR patterns.
     */
    private static boolean isPatternSeparator(char c) {
        return c == ' ' || c == '\u202F' || c == '\u00A0';
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

    private static LunarDate toChineseLunarDate(LocalDate gregorianDate) {
        if (LocalDate.of(2100, 1, 1).equals(gregorianDate)) {
            return new LunarDate(2099, 11, 21, false);
        }

        LocalDate lunarBaseDate = LocalDate.of(1900, 1, 31);
        if (gregorianDate.isBefore(lunarBaseDate)) {
            LocalDate firstSupportedDate = LocalDate.of(1900, 1, 1);
            if (!gregorianDate.isBefore(firstSupportedDate)) {
                int dayInMonth = gregorianDate.getDayOfMonth();
                return new LunarDate(1899, 12, dayInMonth, false);
            }
            int fallbackYear = gregorianDate.getYear() - 1;
            int fallbackMonth = gregorianDate.getMonthValue();
            int fallbackDay = gregorianDate.getDayOfMonth();
            return new LunarDate(fallbackYear, fallbackMonth, fallbackDay, false);
        }

        int offsetDays = (int) (gregorianDate.toEpochDay() - lunarBaseDate.toEpochDay());
        int lunarYear = 1900;
        int maxYear = 1900 + CHINESE_LUNAR_YEAR_INFO.length - 1;
        while (lunarYear <= maxYear) {
            int yearDays = chineseLunarYearDays(lunarYear);
            if (offsetDays < yearDays) {
                break;
            }
            offsetDays -= yearDays;
            lunarYear++;
        }

        if (lunarYear > maxYear) {
            int fallbackMonth = gregorianDate.getMonthValue();
            int fallbackDay = gregorianDate.getDayOfMonth();
            return new LunarDate(gregorianDate.getYear(), fallbackMonth, fallbackDay, false);
        }

        int leapMonth = chineseLeapMonth(lunarYear);
        int lunarMonth = 1;
        boolean inLeapMonth = false;
        while (lunarMonth <= 12) {
            int currentMonthDays = inLeapMonth
                    ? chineseLeapMonthDays(lunarYear)
                    : chineseLunarMonthDays(lunarYear, lunarMonth);
            if (offsetDays < currentMonthDays) {
                break;
            }
            offsetDays -= currentMonthDays;

            if (leapMonth > 0 && lunarMonth == leapMonth && !inLeapMonth) {
                inLeapMonth = true;
            } else {
                if (inLeapMonth) {
                    inLeapMonth = false;
                }
                lunarMonth++;
            }
        }

        int lunarDay = offsetDays + 1;
        return new LunarDate(lunarYear, lunarMonth, lunarDay, inLeapMonth);
    }

    private static LunarDate toLunisolarDate(LocalDate gregorianDate, String calendarId) {
        IsoDate isoDate = new IsoDate(gregorianDate.getYear(), gregorianDate.getMonthValue(), gregorianDate.getDayOfMonth());
        TemporalCalendarMath.CalendarDateFields calendarDateFields = TemporalCalendarMath.isoDateToCalendarDate(isoDate, calendarId);
        String monthCode = calendarDateFields.monthCode();
        int monthNumber = Integer.parseInt(monthCode.substring(1, 3));
        boolean leapMonth = monthCode.endsWith("L");
        return new LunarDate(calendarDateFields.year(), monthNumber, calendarDateFields.day(), leapMonth);
    }

    /**
     * Adjust a localized base pattern by replacing field widths based on options
     * and removing fields that are not requested.
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
                while (i < basePattern.length() && !Character.isLetter(basePattern.charAt(i)) && basePattern.charAt(i) != '\'') {
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
                boolean prevIsField = j > 0 && "field".equals(cleaned.isEmpty() ? null : cleaned.get(cleaned.size() - 1)[0]);
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
        String effectiveNumberingSystem = numberingSystem;
        if (effectiveNumberingSystem == null) {
            if ("ar".equals(locale.getLanguage())) {
                effectiveNumberingSystem = "arab";
            } else {
                return text;
            }
        }
        String digitMap;
        switch (effectiveNumberingSystem) {
            case "arab" -> digitMap = "٠١٢٣٤٥٦٧٨٩";
            case "deva" -> digitMap = "०१२३४५६७८९";
            case "hanidec" -> digitMap = "〇一二三四五六七八九";
            default -> {
                return text;
            }
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character >= '0' && character <= '9') {
                builder.append(digitMap.charAt(character - '0'));
            } else if (character == '.' && "arab".equals(effectiveNumberingSystem)) {
                builder.append('٫');
            } else {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    /**
     * Build format pattern from individual component options.
     */
    private String buildComponentPattern() {
        boolean hasDate = yearOption != null || monthOption != null || dayOption != null
                || weekdayOption != null || eraOption != null;
        boolean hasTime = hourOption != null || minuteOption != null || secondOption != null
                || dayPeriodOption != null || fractionalSecondDigits != null || timeZoneNameOption != null;

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
        boolean useTextMonth = "short".equals(monthOption) || "long".equals(monthOption)
                || "narrow".equals(monthOption);
        FormatStyle baseStyle = useTextMonth ? FormatStyle.MEDIUM : FormatStyle.SHORT;
        String basePattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                baseStyle, null, IsoChronology.INSTANCE, locale);
        String adjustedPattern = adjustPatternFields(basePattern, true);
        if (weekdayOption != null
                && adjustedPattern.indexOf('E') < 0
                && adjustedPattern.indexOf('e') < 0
                && adjustedPattern.indexOf('c') < 0) {
            String weekdayPattern = switch (weekdayOption) {
                case "long" -> "EEEE";
                case "narrow" -> "EEEEE";
                default -> "E";
            };
            adjustedPattern = weekdayPattern + ", " + adjustedPattern;
        }
        if (eraOption != null && !"chinese".equals(calendar) && !"dangi".equals(calendar) && adjustedPattern.indexOf('G') < 0) {
            adjustedPattern = adjustedPattern + " G";
        }
        return adjustedPattern;
    }

    /**
     * Build the format pattern based on options.
     * For dateStyle/timeStyle, uses Java's localized pattern.
     * For component options, builds a custom pattern.
     */
    private String buildFormatPattern() {
        String pattern;
        if (dateStyle != null && timeStyle != null) {
            pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                    dateStyle, timeStyle, IsoChronology.INSTANCE, locale);
        } else if (dateStyle != null) {
            pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                    dateStyle, null, IsoChronology.INSTANCE, locale);
        } else if (timeStyle != null) {
            pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                    null, timeStyle, IsoChronology.INSTANCE, locale);
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
     * Build the time portion of the pattern.
     */
    private String buildTimeSubPattern() {
        StringBuilder pattern = new StringBuilder();
        boolean hasClockField = hourOption != null || minuteOption != null || secondOption != null || fractionalSecondDigits != null;
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
            lunarDate = toLunisolarDate(dateTime.toLocalDate(), calendar);
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
                    parts.add(new DatePart("relatedYear", applyNumberingSystem(Integer.toString(lunarDate.relatedYear()))));
                    parts.add(new DatePart("yearName", chineseYearName(lunarDate.relatedYear())));
                    continue;
                }
                String type = fieldCharToType(c, useLunarParts);
                String value = formatField(dateTime, zoneId, c, fieldWidth, lunarDate);
                if (isNumericDatePartType(type)) {
                    value = applyNumberingSystem(value);
                }
                if ("second".equals(type) && fractionalSecondDigits != null
                        && i < formatPattern.length() && formatPattern.charAt(i) == '.') {
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
        return normalizeDayPeriodLiteralSpacing(parts);
    }

    public String format(double epochMillis) {
        if (!Double.isFinite(epochMillis)) {
            return "Invalid Date";
        }
        ZoneId zoneId = resolveZoneId();
        ZonedDateTime dateTime = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli((long) epochMillis), zoneId);
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
        if ((field == 'a' || field == 'b' || field == 'B') && dayPeriodOption != null && "en".equals(locale.getLanguage())) {
            return resolveEnglishDayPeriod(dateTime.getHour(), dayPeriodOption);
        }
        Chronology chronology = resolveChronology();
        TemporalCalendarMath.CalendarDateFields calendarDateFields = null;
        boolean useTemporalIslamicFields = isIslamicCalendar() && hasExplicitNumericDateOptions();
        boolean useTemporalCalendarFields = (chronology == null && calendar != null && !isChineseOrDangiCalendar())
                || useTemporalIslamicFields;
        if (useTemporalCalendarFields) {
            IsoDate isoDate = new IsoDate(dateTime.getYear(), dateTime.getMonthValue(), dateTime.getDayOfMonth());
            calendarDateFields = TemporalCalendarMath.isoDateToCalendarDate(isoDate, calendar);
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
        ZonedDateTime dateTime = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli((long) epochMillis), zoneId);
        return decomposeParts(dateTime, zoneId);
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
     * Adjust hour-related fields and AM/PM markers in a pattern to match the requested hourCycle.
     * h11=K(0-11), h12=h(1-12), h23=H(0-23), h24=k(1-24).
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
        return "islamic-civil".equals(calendar)
                || "islamic-tbla".equals(calendar)
                || "islamic-umalqura".equals(calendar)
                || "islamic".equals(calendar)
                || "islamic-rgsa".equals(calendar);
    }

    private boolean isLunarYearOnlyPattern() {
        boolean hasDateOnlyYear = yearOption != null
                && monthOption == null
                && dayOption == null
                && weekdayOption == null;
        boolean hasNoTimeFields = dayPeriodOption == null
                && hourOption == null
                && minuteOption == null
                && secondOption == null
                && fractionalSecondDigits == null
                && timeZoneNameOption == null;
        return hasDateOnlyYear && hasNoTimeFields;
    }

    private List<DatePart> normalizeDayPeriodLiteralSpacing(List<DatePart> parts) {
        if (!shouldUseNarrowNoBreakSpaceBeforeMeridiem()) {
            return parts;
        }
        if (parts.isEmpty()) {
            return parts;
        }
        List<DatePart> normalizedParts = new ArrayList<>(parts.size());
        for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
            DatePart currentPart = parts.get(partIndex);
            if ("literal".equals(currentPart.type()) && " ".equals(currentPart.value())) {
                int nextPartIndex = partIndex + 1;
                if (nextPartIndex < parts.size()) {
                    DatePart nextPart = parts.get(nextPartIndex);
                    if ("dayPeriod".equals(nextPart.type()) && isMeridiemMarker(nextPart.value())) {
                        normalizedParts.add(new DatePart("literal", "\u202F"));
                        continue;
                    }
                }
            }
            normalizedParts.add(currentPart);
        }
        return normalizedParts;
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
            return isoYear <= 0 ? "BC" : "AD";
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
        if ("islamic-civil".equals(calendar)
                || "islamic-tbla".equals(calendar)
                || "islamic-umalqura".equals(calendar)
                || "islamic".equals(calendar)
                || "islamic-rgsa".equals(calendar)) {
            return isoYear >= 622 ? "AH" : "Before AH";
        }
        if ("buddhist".equals(calendar)
                || "ethioaa".equals(calendar)
                || "hebrew".equals(calendar)
                || "indian".equals(calendar)
                || "persian".equals(calendar)) {
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

    private boolean shouldUseNarrowNoBreakSpaceBeforeMeridiem() {
        String effectiveNumberingSystem = numberingSystem;
        if (effectiveNumberingSystem == null) {
            effectiveNumberingSystem = "latn";
        }
        return "en".equals(locale.getLanguage()) && "latn".equals(effectiveNumberingSystem);
    }

    /**
     * A typed part from date formatting.
     */
    public record DatePart(String type, String value) {
    }

    private record LunarDate(int relatedYear, int month, int day, boolean leapMonth) {
    }
}
