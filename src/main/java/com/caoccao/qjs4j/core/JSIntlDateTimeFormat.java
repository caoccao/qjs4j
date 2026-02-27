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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.time.chrono.IsoChronology;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Intl.DateTimeFormat instance object.
 * Stores all resolved options per ECMA-402.
 */
public final class JSIntlDateTimeFormat extends JSObject {
    public static final String NAME = "Intl.DateTimeFormat";
    private final String calendar;
    private final FormatStyle dateStyle;
    private final String dayOption;
    private final String dayPeriodOption;
    private final String eraOption;
    private final String formatPattern;
    private final Integer fractionalSecondDigits;
    private final String hourCycle;
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

    /**
     * A typed part from date formatting.
     */
    public record DatePart(String type, String value) {
    }

    public JSIntlDateTimeFormat(Locale locale, FormatStyle dateStyle, FormatStyle timeStyle,
                                String calendar, String numberingSystem, String timeZone,
                                String hourCycle, String weekdayOption, String eraOption,
                                String yearOption, String monthOption, String dayOption,
                                String dayPeriodOption, String hourOption, String minuteOption,
                                String secondOption, Integer fractionalSecondDigits,
                                String timeZoneNameOption) {
        super();
        this.locale = locale;
        this.dateStyle = dateStyle;
        this.timeStyle = timeStyle;
        this.calendar = calendar;
        this.numberingSystem = numberingSystem;
        this.timeZone = timeZone;
        this.hourCycle = hourCycle;
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
        this.formatPattern = buildFormatPattern();
    }

    public String format(double epochMillis) {
        if (!Double.isFinite(epochMillis)) {
            return "Invalid Date";
        }
        ZoneId zoneId = resolveZoneId();
        ZonedDateTime dateTime = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli((long) epochMillis), zoneId);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(formatPattern, locale);
        return formatter.withZone(zoneId).format(dateTime);
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

    public String getCalendar() {
        return calendar;
    }

    public FormatStyle getDateStyle() {
        return dateStyle;
    }

    public String getDayOption() {
        return dayOption;
    }

    public String getDayPeriodOption() {
        return dayPeriodOption;
    }

    public String getEraOption() {
        return eraOption;
    }

    public Integer getFractionalSecondDigits() {
        return fractionalSecondDigits;
    }

    public String getHourCycle() {
        return hourCycle;
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

    /**
     * Build the format pattern based on options.
     * For dateStyle/timeStyle, uses Java's localized pattern.
     * For component options, builds a custom pattern.
     */
    private String buildFormatPattern() {
        if (dateStyle != null && timeStyle != null) {
            return DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                    dateStyle, timeStyle, IsoChronology.INSTANCE, locale);
        } else if (dateStyle != null) {
            return DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                    dateStyle, null, IsoChronology.INSTANCE, locale);
        } else if (timeStyle != null) {
            return DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                    null, timeStyle, IsoChronology.INSTANCE, locale);
        }
        return buildComponentPattern();
    }

    /**
     * Build format pattern from individual component options.
     */
    private String buildComponentPattern() {
        boolean hasDate = yearOption != null || monthOption != null || dayOption != null
                || weekdayOption != null || eraOption != null;
        boolean hasTime = hourOption != null || minuteOption != null || secondOption != null
                || dayPeriodOption != null || fractionalSecondDigits != null;

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
        return adjustPatternFields(basePattern, true);
    }

    /**
     * Build the time portion of the pattern.
     */
    private String buildTimeSubPattern() {
        StringBuilder pattern = new StringBuilder();
        if (hourOption != null) {
            boolean use12Hour = hourCycle == null || "h12".equals(hourCycle) || "h11".equals(hourCycle);
            char hourChar = use12Hour ? 'h' : 'H';
            pattern.append("2-digit".equals(hourOption) ? String.valueOf(hourChar).repeat(2) : String.valueOf(hourChar));
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
        return pattern.toString();
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

    /**
     * Decompose a formatted date into typed parts using the format pattern.
     */
    private List<DatePart> decomposeParts(ZonedDateTime dateTime, ZoneId zoneId) {
        List<DatePart> parts = new ArrayList<>();
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
                String type = fieldCharToType(c);
                String value = formatField(dateTime, zoneId, c, fieldWidth);
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
        return parts;
    }

    /**
     * Map a pattern field character to its ECMA-402 part type name.
     */
    private static String fieldCharToType(char c) {
        return switch (c) {
            case 'y' -> "year";
            case 'M', 'L' -> "month";
            case 'd' -> "day";
            case 'h', 'H', 'k', 'K' -> "hour";
            case 'm' -> "minute";
            case 's' -> "second";
            case 'S' -> "fractionalSecond";
            case 'E', 'e', 'c' -> "weekday";
            case 'G' -> "era";
            case 'a', 'b', 'B' -> "dayPeriod";
            case 'z', 'Z', 'v', 'V' -> "timeZoneName";
            default -> "literal";
        };
    }

    /**
     * Format a single field from a ZonedDateTime.
     */
    private String formatField(ZonedDateTime dateTime, ZoneId zoneId, char field, int width) {
        String pattern = String.valueOf(field).repeat(width);
        return DateTimeFormatter.ofPattern(pattern, locale).withZone(zoneId).format(dateTime);
    }

    /**
     * Check if this formatter uses a text-based month (short, long, or narrow).
     */
    public boolean hasTextMonth() {
        return "short".equals(monthOption) || "long".equals(monthOption) || "narrow".equals(monthOption);
    }

    private ZoneId resolveZoneId() {
        if (timeZone != null) {
            try {
                return ZoneId.of(timeZone);
            } catch (Exception e) {
                // fall through to default
            }
        }
        return ZoneId.systemDefault();
    }
}
