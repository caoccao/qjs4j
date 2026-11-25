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

import com.caoccao.qjs4j.core.temporal.TemporalDuration;
import com.caoccao.qjs4j.exceptions.JSRangeErrorException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;

/**
 * Intl.DurationFormat instance object. Stores all resolved options per the Intl.DurationFormat proposal.
 */
public final class JSIntlDurationFormat extends JSObject {
    /**
     * Valid styles per unit category.
     */
    public static final String[] DATE_UNIT_STYLES = {"long", "short", "narrow"};
    /**
     * The Digital Default column of the DurationFormat unit table.
     */
    private static final String[] DIGITAL_DEFAULTS = {"short", "short", "short", "short", // years, months, weeks, days
            "numeric", "numeric", "numeric", // hours, minutes, seconds
            "numeric", "numeric", "numeric" // milliseconds, microseconds, nanoseconds
    };
    /**
     * English CLDR unit names: {singular long, plural long, singular short, plural short, narrow}. Narrow uses no space
     * between the number and the unit symbol.
     */
    private static final Map<String, String[]> EN_UNIT_DATA = new HashMap<>();
    public static final String NAME = "Intl.DurationFormat";
    /**
     * Singular unit names (for NumberFormat unit option).
     */
    public static final String[] SINGULAR_UNIT_NAMES = {"year", "month", "week", "day", "hour", "minute", "second",
            "millisecond", "microsecond", "nanosecond"};
    public static final String[] SUBSECOND_UNIT_STYLES = {"long", "short", "narrow", "numeric"};
    public static final String[] TIME_UNIT_STYLES = {"long", "short", "narrow", "numeric", "2-digit"};
    /**
     * Units in table order.
     */
    public static final String[] UNIT_NAMES = {"years", "months", "weeks", "days", "hours", "minutes", "seconds",
            "milliseconds", "microseconds", "nanoseconds"};

    static {
        EN_UNIT_DATA.put("year", new String[]{"year", "years", "yr", "yrs", "y"});
        EN_UNIT_DATA.put("month", new String[]{"month", "months", "mth", "mths", "m"});
        EN_UNIT_DATA.put("week", new String[]{"week", "weeks", "wk", "wks", "w"});
        EN_UNIT_DATA.put("day", new String[]{"day", "days", "day", "days", "d"});
        EN_UNIT_DATA.put("hour", new String[]{"hour", "hours", "hr", "hr", "h"});
        EN_UNIT_DATA.put("minute", new String[]{"minute", "minutes", "min", "min", "m"});
        EN_UNIT_DATA.put("second", new String[]{"second", "seconds", "sec", "sec", "s"});
        EN_UNIT_DATA.put("millisecond", new String[]{"millisecond", "milliseconds", "ms", "ms", "ms"});
        EN_UNIT_DATA.put("microsecond", new String[]{"microsecond", "microseconds", "\u03BCs", "\u03BCs", "\u03BCs"});
        EN_UNIT_DATA.put("nanosecond", new String[]{"nanosecond", "nanoseconds", "ns", "ns", "ns"});
    }

    private final Integer fractionalDigits;
    private final Locale locale;
    private final String numberingSystem;
    private final String style;
    private final String[] unitDisplays;
    private final String[] unitStyles;

    public JSIntlDurationFormat(JSContext context, Locale locale, String numberingSystem, String style,
            String[] unitStyles, String[] unitDisplays, Integer fractionalDigits) {
        super(context);
        this.locale = locale;
        this.numberingSystem = numberingSystem;
        this.style = style;
        this.unitStyles = unitStyles.clone();
        this.unitDisplays = unitDisplays.clone();
        this.fractionalDigits = fractionalDigits;
    }

    private void forEachDisplayedUnit(Map<String, Double> durationValues, DisplayedUnitConsumer consumer) {
        boolean inNumericGroup = false;
        boolean first = true;
        for (int i = 0; i < UNIT_NAMES.length; i++) {
            String unit = UNIT_NAMES[i];
            double value = durationValues.getOrDefault(unit, 0.0);
            BigDecimal fraction = null;
            if (("seconds".equals(unit) || "milliseconds".equals(unit) || "microseconds".equals(unit))
                    && i + 1 < UNIT_NAMES.length && "numeric".equals(unitStyles[i + 1])) {
                fraction = computeFractionalValue(durationValues, unit);
                value = fraction.doubleValue();
            }

            // Keep the middle minutes field in a digital hours:minutes:seconds group, even if zero.
            boolean requiredMinute = inNumericGroup && "minutes".equals(unit)
                    && ("always".equals(unitDisplays[6]) || durationValues.getOrDefault("seconds", 0.0) != 0
                            || durationValues.getOrDefault("milliseconds", 0.0) != 0
                            || durationValues.getOrDefault("microseconds", 0.0) != 0
                            || durationValues.getOrDefault("nanoseconds", 0.0) != 0);
            boolean zero = fraction == null ? value == 0 : fraction.signum() == 0;
            if (!zero || !"auto".equals(unitDisplays[i]) || requiredMinute) {
                if (first && zero) {
                    for (String component : UNIT_NAMES) {
                        if (durationValues.getOrDefault(component, 0.0) < 0) {
                            value = -0.0;
                            if (fraction != null) {
                                fraction = BigDecimal.ZERO;
                            }
                            break;
                        }
                    }
                }
                consumer.accept(i, value, fraction, !first, inNumericGroup);
                first = false;
                inNumericGroup |= isNumericStyle(unitStyles[i]);
            }
            if (fraction != null) {
                break;
            }
        }
    }

    /**
     * Format a duration to a string (Intl.DurationFormat.prototype.format).
     */
    public String formatDuration(Map<String, Double> durationValues) {
        List<String> result = new ArrayList<>();
        forEachDisplayedUnit(durationValues, (index, value, fraction, suppressSign, append) -> {
            String formatted = formatUnit(index, value, fraction, suppressSign);
            if (append) {
                int last = result.size() - 1;
                result.set(last, result.get(last) + ":" + formatted);
            } else {
                result.add(formatted);
            }
        });
        return unitListFormat().format(result);
    }

    private String formatUnit(int index, double value, BigDecimal fraction, boolean suppressSign) {
        String unitStyle = unitStyles[index];
        int minFrac = fractionalDigits != null ? fractionalDigits : 0;
        int maxFrac = fractionalDigits != null ? fractionalDigits : 9;
        if (isNumericStyle(unitStyle)) {
            boolean twoDigit = "2-digit".equals(unitStyle);
            return fraction == null
                    ? formatNumericValue(value, twoDigit, suppressSign, -1, -1, false, locale)
                    : formatNumericBigDecimal(fraction, twoDigit, suppressSign, minFrac, maxFrac, true, locale);
        }
        String unit = SINGULAR_UNIT_NAMES[index];
        String formatted = fraction == null
                ? formatWithUnit(value, unit, unitStyle, suppressSign, locale)
                : formatWithUnit(fraction, unit, unitStyle, suppressSign, minFrac, maxFrac, locale);
        if ("digital".equals(style) && "day".equals(unit) && Math.abs(value) >= 1000 && formatted.endsWith(" day")) {
            return formatted + "s";
        }
        return formatted;
    }

    private List<FormatPart> formatUnitParts(int index, double value, BigDecimal fraction, boolean suppressSign) {
        String unitStyle = unitStyles[index];
        int minFrac = fractionalDigits != null ? fractionalDigits : 0;
        int maxFrac = fractionalDigits != null ? fractionalDigits : 9;
        if (isNumericStyle(unitStyle)) {
            boolean twoDigit = "2-digit".equals(unitStyle);
            return fraction == null
                    ? formatToPartsNumeric(value, twoDigit, suppressSign, -1, -1, false, locale)
                    : formatToPartsBigDecimal(fraction, twoDigit, suppressSign, minFrac, maxFrac, true, locale);
        }
        String unit = SINGULAR_UNIT_NAMES[index];
        return fraction == null
                ? formatToPartsWithUnit(value, unit, unitStyle, suppressSign, locale)
                : formatToPartsWithUnit(fraction, unit, unitStyle, suppressSign, minFrac, maxFrac, locale);
    }

    public Integer getFractionalDigits() {
        return fractionalDigits;
    }

    public Locale getLocale() {
        return locale;
    }

    public String getNumberingSystem() {
        return numberingSystem;
    }

    public String getStyle() {
        return style;
    }

    public String getUnitDisplay(int index) {
        return unitDisplays[index];
    }

    public String getUnitStyle(int index) {
        return unitStyles[index];
    }

    /**
     * Partition a duration into formatted parts (PartitionDurationFormatPattern).
     *
     * @param durationValues
     *            map of unit name → value
     * @return list of FormatPart representing the formatted duration
     */
    public List<FormatPart> partitionDurationFormatPattern(Map<String, Double> durationValues) {
        List<List<FormatPart>> result = new ArrayList<>();
        forEachDisplayedUnit(durationValues, (index, value, fraction, suppressSign, append) -> {
            List<FormatPart> group;
            if (append) {
                group = result.get(result.size() - 1);
                group.add(new FormatPart("literal", ":", null));
            } else {
                group = new ArrayList<>();
                result.add(group);
            }
            for (FormatPart part : formatUnitParts(index, value, fraction, suppressSign)) {
                group.add(new FormatPart(part.type(), part.value(), SINGULAR_UNIT_NAMES[index]));
            }
        });

        // Collect formatted strings from each group
        List<String> strings = new ArrayList<>();
        for (List<FormatPart> group : result) {
            StringBuilder sb = new StringBuilder();
            for (FormatPart part : group) {
                sb.append(part.value());
            }
            strings.add(sb.toString());
        }

        // Use ListFormat to join and produce final flattened parts
        List<FormatPart> flattened = new ArrayList<>();
        List<ListFormatPart> listParts = formatListToParts(unitListFormat(), strings);
        int groupIndex = 0;
        for (ListFormatPart listPart : listParts) {
            if ("element".equals(listPart.type())) {
                if (groupIndex < result.size()) {
                    flattened.addAll(result.get(groupIndex));
                    groupIndex++;
                }
            } else {
                flattened.add(new FormatPart(listPart.type(), listPart.value(), null));
            }
        }

        return flattened;
    }

    private JSIntlListFormat unitListFormat() {
        return new JSIntlListFormat(context, locale, "digital".equals(style) ? "short" : style, "unit");
    }

    /**
     * Split a formatted number string into integer/decimal/fraction parts.
     */
    private static void addNumberPartsToList(List<FormatPart> parts, String formatted) {
        int dotIndex = formatted.indexOf('.');
        if (dotIndex >= 0) {
            parts.add(new FormatPart("integer", formatted.substring(0, dotIndex), null));
            parts.add(new FormatPart("decimal", ".", null));
            if (dotIndex + 1 < formatted.length()) {
                parts.add(new FormatPart("fraction", formatted.substring(dotIndex + 1), null));
            }
        } else {
            parts.add(new FormatPart("integer", formatted, null));
        }
    }

    private static void addUnitParts(List<FormatPart> parts, String numberPart, String singularUnit, String unitDisplay,
            Locale locale) {
        String unitName = getEnglishTimeUnitName(numberPart, singularUnit, unitDisplay, locale);
        if (unitName == null) {
            parts.add(new FormatPart("literal", " ", null));
            parts.add(new FormatPart("unit", singularUnit, null));
        } else {
            if (!"narrow".equals(unitDisplay)) {
                parts.add(new FormatPart("literal", " ", null));
            }
            parts.add(new FormatPart("unit", unitName, null));
        }
    }

    /**
     * Compute the fractional value by combining sub-second units via BigDecimal. Equivalent to the harness
     * durationToFractional function.
     *
     * @param durationValues
     *            map of unit name to double value
     * @param baseUnit
     *            "seconds", "milliseconds", or "microseconds"
     * @return the combined fractional value as BigDecimal
     */
    // The switch below accumulates sub-second components and falls through deliberately: an
    // exponent of 9 must add milliseconds and microseconds too.
    @SuppressWarnings("fallthrough")
    public static BigDecimal computeFractionalValue(Map<String, Double> durationValues, String baseUnit) {
        double seconds = durationValues.getOrDefault("seconds", 0.0);
        double milliseconds = durationValues.getOrDefault("milliseconds", 0.0);
        double microseconds = durationValues.getOrDefault("microseconds", 0.0);
        double nanoseconds = durationValues.getOrDefault("nanoseconds", 0.0);

        int exponent;
        switch (baseUnit) {
            case "seconds" -> exponent = 9;
            case "milliseconds" -> exponent = 6;
            case "microseconds" -> exponent = 3;
            default -> throw new JSRangeErrorException("Invalid base unit: " + baseUnit);
        }

        // Check if no sub-units are present
        boolean noSubUnits = switch (exponent) {
            case 9 -> milliseconds == 0 && microseconds == 0 && nanoseconds == 0;
            case 6 -> microseconds == 0 && nanoseconds == 0;
            case 3 -> nanoseconds == 0;
            default -> false;
        };

        if (noSubUnits) {
            // Return simple value
            return switch (baseUnit) {
                case "seconds" -> BigDecimal.valueOf(seconds);
                case "milliseconds" -> BigDecimal.valueOf(milliseconds);
                case "microseconds" -> BigDecimal.valueOf(microseconds);
                default -> BigDecimal.ZERO;
            };
        }

        // Use BigInteger for precision (matching the harness BigInt approach)
        // Use BigDecimal for exact conversion to handle values outside long range
        BigInteger ns = new BigDecimal(nanoseconds).toBigInteger();
        switch (exponent) {
            case 9 :
                ns = ns.add(new BigDecimal(seconds).toBigInteger().multiply(BigInteger.valueOf(1_000_000_000L)));
                // fallthrough
            case 6 :
                ns = ns.add(new BigDecimal(milliseconds).toBigInteger().multiply(BigInteger.valueOf(1_000_000L)));
                // fallthrough
            case 3 :
                ns = ns.add(new BigDecimal(microseconds).toBigInteger().multiply(BigInteger.valueOf(1_000L)));
        }

        BigDecimal fraction = new BigDecimal(ns, exponent);
        // The duration formatter takes its sign from the whole-unit quotient. When that
        // quotient is zero, the fractional digits represent the unsigned remainder.
        return fraction.signum() < 0 && fraction.precision() <= exponent ? fraction.abs() : fraction;
    }

    private static NumberFormat createNumberFormat(int minimumIntegerDigits, int minimumFractionDigits,
            int maximumFractionDigits, boolean useGrouping, boolean truncate, Locale locale) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setGroupingUsed(useGrouping);
        format.setMinimumIntegerDigits(minimumIntegerDigits);
        if (minimumFractionDigits >= 0) {
            format.setMinimumFractionDigits(minimumFractionDigits);
        }
        if (maximumFractionDigits >= 0) {
            format.setMaximumFractionDigits(maximumFractionDigits);
        }
        if (truncate) {
            format.setRoundingMode(RoundingMode.DOWN);
        }
        return format;
    }

    /**
     * Format a BigDecimal number for precise fractional seconds.
     */
    public static String formatBigDecimalNumber(BigDecimal value, int minimumIntegerDigits, int minimumFractionDigits,
            int maximumFractionDigits, boolean useGrouping, boolean truncate, Locale locale) {
        return createNumberFormat(minimumIntegerDigits, minimumFractionDigits, maximumFractionDigits, useGrouping,
                truncate, locale).format(value);
    }

    /**
     * Format a number with an English time unit, choosing the plural and display width as CLDR does. Unit formatting
     * used to print the singular unit identifier for every count and width ("30 minute" where V8 prints "30 minutes",
     * "30 min" or "30m").
     *
     * @param numberPart
     *            the formatted number
     * @param unit
     *            the singular unit identifier
     * @param unitDisplay
     *            long, short or narrow
     * @param locale
     *            the locale
     * @return the formatted unit, or null when the locale is not English or the unit is not a time unit
     */
    public static String formatEnglishTimeUnit(String numberPart, String unit, String unitDisplay, Locale locale) {
        String unitName = getEnglishTimeUnitName(numberPart, unit, unitDisplay, locale);
        if (unitName == null) {
            return null;
        }
        return "narrow".equals(unitDisplay) ? numberPart + unitName : numberPart + " " + unitName;
    }

    /**
     * Simplified ListFormat.formatToParts that matches our JSIntlListFormat logic. For "unit" type: separators are ", "
     * (long/short) or " " (narrow), with no conjunction word (unlike "conjunction" type which uses "and"/"or").
     */
    public static List<ListFormatPart> formatListToParts(JSIntlListFormat listFormat, List<String> values) {
        List<ListFormatPart> parts = new ArrayList<>();
        if (values.isEmpty()) {
            return parts;
        }
        if (values.size() == 1) {
            parts.add(new ListFormatPart("element", values.get(0)));
            return parts;
        }

        JSIntlListFormat.ListPatterns patterns = listFormat.getPatterns();

        if (values.size() == 2) {
            parts.add(new ListFormatPart("element", values.get(0)));
            parts.add(new ListFormatPart("literal", patterns.pairSep()));
            parts.add(new ListFormatPart("element", values.get(1)));
            return parts;
        }

        // 3+ items: use start/middle/end separators
        int n = values.size();
        parts.add(new ListFormatPart("element", values.get(0)));
        parts.add(new ListFormatPart("literal", patterns.startSep()));
        for (int i = 1; i < n - 1; i++) {
            parts.add(new ListFormatPart("element", values.get(i)));
            if (i < n - 2) {
                parts.add(new ListFormatPart("literal", patterns.middleSep()));
            } else {
                parts.add(new ListFormatPart("literal", patterns.endSep()));
            }
        }
        parts.add(new ListFormatPart("element", values.get(n - 1)));

        return parts;
    }

    /**
     * Format a BigDecimal for numeric/2-digit style.
     */
    public static String formatNumericBigDecimal(BigDecimal value, boolean is2Digit, boolean suppressSign,
            int minFractionDigits, int maxFractionDigits, boolean truncate, Locale locale) {
        BigDecimal formatValue = suppressSign ? value.abs() : value;
        return formatBigDecimalNumber(formatValue, is2Digit ? 2 : 1, minFractionDigits, maxFractionDigits, false,
                truncate, locale);
    }

    /**
     * Format a number for numeric/2-digit style with options for sign suppression.
     */
    public static String formatNumericValue(double value, boolean is2Digit, boolean suppressSign, int minFractionDigits,
            int maxFractionDigits, boolean truncate, Locale locale) {
        double formatValue = suppressSign ? Math.abs(value) : value;
        boolean isNegativeZero = !suppressSign && Double.doubleToRawLongBits(value) == Long.MIN_VALUE;

        int minIntDigits = is2Digit ? 2 : 1;
        String result = formatPlainNumber(formatValue, minIntDigits, minFractionDigits, maxFractionDigits, false,
                truncate, locale);

        if (isNegativeZero) {
            result = "-" + formatPlainNumber(0.0, minIntDigits, minFractionDigits, maxFractionDigits, false, truncate,
                    locale);
        }
        return result;
    }

    /**
     * Format a plain number (numeric/2-digit style) matching Intl.NumberFormat default output.
     */
    public static String formatPlainNumber(double value, int minimumIntegerDigits, int minimumFractionDigits,
            int maximumFractionDigits, boolean useGrouping, boolean truncate, Locale locale) {
        return createNumberFormat(minimumIntegerDigits, minimumFractionDigits, maximumFractionDigits, useGrouping,
                truncate, locale).format(value);
    }

    /**
     * Format a BigDecimal value to parts for numeric style (used for fractional seconds).
     */
    public static List<FormatPart> formatToPartsBigDecimal(BigDecimal value, boolean is2Digit, boolean suppressSign,
            int minFractionDigits, int maxFractionDigits, boolean truncate, Locale locale) {
        List<FormatPart> parts = new ArrayList<>();
        BigDecimal formatValue = suppressSign ? value.abs() : value;
        boolean isNegative = formatValue.signum() < 0;

        if (isNegative) {
            parts.add(new FormatPart("minusSign", "-", null));
            formatValue = formatValue.abs();
        }

        int minIntDigits = is2Digit ? 2 : 1;
        String formatted = formatBigDecimalNumber(formatValue, minIntDigits, minFractionDigits, maxFractionDigits,
                false, truncate, locale);
        addNumberPartsToList(parts, formatted);
        return parts;
    }

    /**
     * Format a value to parts for numeric/2-digit style.
     */
    public static List<FormatPart> formatToPartsNumeric(double value, boolean is2Digit, boolean suppressSign,
            int minFractionDigits, int maxFractionDigits, boolean truncate, Locale locale) {
        List<FormatPart> parts = new ArrayList<>();
        double formatValue = suppressSign ? Math.abs(value) : value;
        boolean isNegativeZero = !suppressSign && Double.doubleToRawLongBits(value) == Long.MIN_VALUE;
        boolean isNegative = formatValue < 0 || isNegativeZero;

        if (isNegative) {
            parts.add(new FormatPart("minusSign", "-", null));
            formatValue = Math.abs(formatValue);
        }

        int minIntDigits = is2Digit ? 2 : 1;
        String formatted = formatPlainNumber(formatValue, minIntDigits, minFractionDigits, maxFractionDigits, false,
                truncate, locale);
        addNumberPartsToList(parts, formatted);
        return parts;
    }

    public static List<FormatPart> formatToPartsWithUnit(BigDecimal value, String singularUnit, String unitDisplay,
            boolean suppressSign, int minFractionDigits, int maxFractionDigits, Locale locale) {
        List<FormatPart> parts = new ArrayList<>();
        BigDecimal formatValue = suppressSign ? value.abs() : value;
        boolean isNegative = formatValue.signum() < 0;

        if (isNegative) {
            parts.add(new FormatPart("minusSign", "-", null));
            formatValue = formatValue.abs();
        }

        String numberStr = formatBigDecimalNumber(formatValue, 1, minFractionDigits, maxFractionDigits, true, true,
                locale);
        addNumberPartsToList(parts, numberStr);
        addUnitParts(parts, numberStr, singularUnit, unitDisplay, locale);
        return parts;
    }

    /**
     * Format a value to parts for unit style formatting.
     */
    public static List<FormatPart> formatToPartsWithUnit(double value, String singularUnit, String unitDisplay,
            boolean suppressSign, Locale locale) {
        List<FormatPart> parts = new ArrayList<>();
        double formatValue = suppressSign ? Math.abs(value) : value;
        boolean isNegativeZero = !suppressSign && Double.doubleToRawLongBits(value) == Long.MIN_VALUE;
        boolean isNegative = formatValue < 0 || isNegativeZero;

        if (isNegative) {
            parts.add(new FormatPart("minusSign", "-", null));
            formatValue = Math.abs(formatValue);
        }

        String numberStr = formatPlainNumber(formatValue, 1, -1, -1, true, false, locale);
        addNumberPartsToList(parts, numberStr);
        addUnitParts(parts, numberStr, singularUnit, unitDisplay, locale);

        return parts;
    }

    /**
     * Format a number with unit display, supporting BigDecimal for precision.
     */
    public static String formatWithUnit(BigDecimal value, String singularUnit, String unitDisplay, boolean suppressSign,
            int minFractionDigits, int maxFractionDigits, Locale locale) {
        BigDecimal formatValue = suppressSign ? value.abs() : value;

        String numberPart = formatBigDecimalNumber(formatValue, 1, minFractionDigits, maxFractionDigits, true, true,
                locale);
        String english = formatEnglishTimeUnit(numberPart, singularUnit, unitDisplay, locale);
        return english != null ? english : numberPart + " " + singularUnit;
    }

    /**
     * Format a number with unit display, matching Intl.NumberFormat({style:"unit",...}).format() output.
     *
     * @param value
     *            the number to format
     * @param singularUnit
     *            the singular unit name (e.g., "year")
     * @param unitDisplay
     *            "long", "short", or "narrow"
     * @param suppressSign
     *            if true, format absolute value (signDisplay: "never")
     * @param locale
     *            the locale
     * @return formatted string
     */
    public static String formatWithUnit(double value, String singularUnit, String unitDisplay, boolean suppressSign,
            Locale locale) {
        double formatValue = suppressSign ? Math.abs(value) : value;
        // Handle -0 → show minus sign when not suppressed
        boolean isNegativeZero = !suppressSign && Double.doubleToRawLongBits(value) == Long.MIN_VALUE;

        String numberPart = formatPlainNumber(formatValue, 1, -1, -1, true, false, locale);
        if (isNegativeZero) {
            numberPart = "-0";
        }
        String english = formatEnglishTimeUnit(numberPart, singularUnit, unitDisplay, locale);
        return english != null ? english : numberPart + " " + singularUnit;
    }

    /**
     * Returns the digital default style for the given unit index.
     */
    public static String getDigitalDefault(int unitIndex) {
        return DIGITAL_DEFAULTS[unitIndex];
    }

    private static String getEnglishTimeUnitName(String numberPart, String unit, String unitDisplay, Locale locale) {
        String[] names = EN_UNIT_DATA.get(unit);
        if (names == null || !"en".equals(locale.getLanguage())) {
            return null;
        }
        // CLDR English plural category "one": exactly 1 with no visible fraction digits, whatever the sign.
        String digits = numberPart.startsWith("-") || numberPart.startsWith("+") ? numberPart.substring(1) : numberPart;
        boolean one = "1".equals(digits);
        return switch (unitDisplay) {
            case "long" -> one ? names[0] : names[1];
            case "narrow" -> names[4];
            default -> one ? names[2] : names[3];
        };
    }

    /**
     * Returns the valid unit styles for the given unit index.
     */
    public static String[] getValidStylesForUnit(int unitIndex) {
        if (unitIndex <= 3) {
            return DATE_UNIT_STYLES;
        } else if (unitIndex <= 6) {
            return TIME_UNIT_STYLES;
        } else {
            return SUBSECOND_UNIT_STYLES;
        }
    }

    private static boolean isNumericStyle(String unitStyle) {
        return "numeric".equals(unitStyle) || "2-digit".equals(unitStyle);
    }

    /**
     * IsValidDurationRecord — checks sign consistency and range limits.
     */
    private static boolean isValidDurationRecord(JSContext context, Map<String, Double> record) {
        // Check mixed signs: all non-zero values must have the same sign
        int positiveCount = 0;
        int negativeCount = 0;
        for (double value : record.values()) {
            if (value > 0) {
                positiveCount++;
            }
            if (value < 0) {
                negativeCount++;
            }
        }
        if (positiveCount > 0 && negativeCount > 0) {
            context.throwRangeError("Mixed-sign duration is not allowed");
            return false;
        }

        // Check years, months, weeks: abs < 2^32
        double twoTo32 = 4294967296.0; // 2^32
        for (String unit : new String[]{"years", "months", "weeks"}) {
            double value = record.getOrDefault(unit, 0.0);
            if (Math.abs(value) >= twoTo32) {
                context.throwRangeError("Duration " + unit + " value out of range");
                return false;
            }
        }

        // Check normalizedSeconds: abs < 2^53
        // Use BigInteger for exact computation to avoid floating-point imprecision.
        // The spec computes: days*86400 + hours*3600 + minutes*60 + seconds
        // + milliseconds*10^-3 + microseconds*10^-6 + nanoseconds*10^-9
        // We scale everything to nanoseconds to use integer arithmetic:
        // normalizedNanos = (days*86400 + hours*3600 + minutes*60 + seconds) * 10^9
        // + milliseconds * 10^6 + microseconds * 10^3 + nanoseconds
        // Then check: abs(normalizedNanos) >= 2^53 * 10^9
        double days = record.getOrDefault("days", 0.0);
        double hours = record.getOrDefault("hours", 0.0);
        double minutes = record.getOrDefault("minutes", 0.0);
        double seconds = record.getOrDefault("seconds", 0.0);
        double milliseconds = record.getOrDefault("milliseconds", 0.0);
        double microseconds = record.getOrDefault("microseconds", 0.0);
        double nanoseconds = record.getOrDefault("nanoseconds", 0.0);

        // Convert doubles to their exact BigInteger values (since all are integers per prior check)
        // Use BigDecimal for exact conversion to handle values larger than Long.MAX_VALUE
        BigInteger bdDays = new BigDecimal(days).toBigInteger();
        BigInteger bdHours = new BigDecimal(hours).toBigInteger();
        BigInteger bdMinutes = new BigDecimal(minutes).toBigInteger();
        BigInteger bdSeconds = new BigDecimal(seconds).toBigInteger();
        BigInteger bdMilliseconds = new BigDecimal(milliseconds).toBigInteger();
        BigInteger bdMicroseconds = new BigDecimal(microseconds).toBigInteger();
        BigInteger bdNanoseconds = new BigDecimal(nanoseconds).toBigInteger();

        BigInteger BILLION = BigInteger.valueOf(1_000_000_000L);
        BigInteger normalizedNanos = bdDays.multiply(BigInteger.valueOf(86400L))
                .add(bdHours.multiply(BigInteger.valueOf(3600L))).add(bdMinutes.multiply(BigInteger.valueOf(60L)))
                .add(bdSeconds).multiply(BILLION).add(bdMilliseconds.multiply(BigInteger.valueOf(1_000_000L)))
                .add(bdMicroseconds.multiply(BigInteger.valueOf(1_000L))).add(bdNanoseconds);

        // limit = 2^53 * 10^9
        BigInteger limit = BigInteger.valueOf(9007199254740992L).multiply(BILLION);
        if (normalizedNanos.abs().compareTo(limit) >= 0) {
            context.throwRangeError("Duration total seconds out of range");
            return false;
        }

        return true;
    }

    /**
     * Validate and extract a duration record from a JSValue. Returns null if the value is invalid (exception already
     * thrown on context).
     */
    public static Map<String, Double> toDurationRecord(JSContext context, JSValue input) {
        if (input instanceof JSTemporalDuration temporalDurationObject) {
            return toDurationRecordMap(temporalDurationObject.getDuration());
        }

        if (input instanceof JSString durationString) {
            TemporalDuration parsedDuration = TemporalDuration.parseDurationString(context, durationString.value());
            if (parsedDuration == null || context.hasPendingException()) {
                return null;
            }
            Map<String, Double> durationRecord = toDurationRecordMap(parsedDuration);
            if (!isValidDurationRecord(context, durationRecord)) {
                return null;
            }
            return durationRecord;
        }

        // Step 2: If Type(input) is not Object, throw TypeError
        if (!(input instanceof JSObject inputObj)) {
            context.throwTypeError("Invalid duration value");
            return null;
        }

        Map<String, Double> result = new LinkedHashMap<>();
        boolean anyDefined = false;

        for (String unitName : UNIT_NAMES) {
            JSValue val = inputObj.get(PropertyKey.fromString(unitName));
            if (context.hasPendingException()) {
                return null;
            }

            if (val == null || val instanceof JSUndefined) {
                result.put(unitName, 0.0);
            } else {
                anyDefined = true;
                double numericValue = JSTypeConversions.toNumber(context, val).value();
                if (context.hasPendingException()) {
                    return null;
                }
                if (Double.isNaN(numericValue) || Double.isInfinite(numericValue)) {
                    context.throwRangeError("Invalid duration value for " + unitName);
                    return null;
                }
                // Duration values must be integers
                if (numericValue != Math.floor(numericValue)) {
                    context.throwRangeError("Duration " + unitName + " must be an integer");
                    return null;
                }
                // Convert -0 to 0
                if (numericValue == 0.0) {
                    numericValue = 0.0;
                }
                result.put(unitName, numericValue);
            }
        }

        // If no properties were defined, throw TypeError
        if (!anyDefined) {
            context.throwTypeError("Invalid duration value");
            return null;
        }

        // Validate: IsValidDurationRecord
        if (!isValidDurationRecord(context, result)) {
            return null;
        }

        return result;
    }

    private static Map<String, Double> toDurationRecordMap(TemporalDuration temporalDuration) {
        Map<String, Double> durationRecord = new LinkedHashMap<>();
        durationRecord.put("years", (double) temporalDuration.years());
        durationRecord.put("months", (double) temporalDuration.months());
        durationRecord.put("weeks", (double) temporalDuration.weeks());
        durationRecord.put("days", (double) temporalDuration.days());
        durationRecord.put("hours", (double) temporalDuration.hours());
        durationRecord.put("minutes", (double) temporalDuration.minutes());
        durationRecord.put("seconds", (double) temporalDuration.seconds());
        durationRecord.put("milliseconds", (double) temporalDuration.milliseconds());
        durationRecord.put("microseconds", (double) temporalDuration.microseconds());
        durationRecord.put("nanoseconds", (double) temporalDuration.nanoseconds());
        return durationRecord;
    }

    @FunctionalInterface
    private interface DisplayedUnitConsumer {
        void accept(int index, double value, BigDecimal fraction, boolean suppressSign, boolean appendToPrevious);
    }

    /**
     * Represents a single part in formatToParts output.
     */
    public record FormatPart(String type, String value, String unit) {
    }

    public record ListFormatPart(String type, String value) {
    }
}
