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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;

/**
 * Intl.DurationFormat instance object.
 * Stores all resolved options per the Intl.DurationFormat proposal.
 */
public final class JSIntlDurationFormat extends JSObject {
    /**
     * Valid styles per unit category.
     */
    public static final String[] DATE_UNIT_STYLES = {"long", "short", "narrow"};
    public static final String NAME = "Intl.DurationFormat";
    /**
     * Singular unit names (for NumberFormat unit option).
     */
    public static final String[] SINGULAR_UNIT_NAMES = {
            "year", "month", "week", "day", "hour", "minute",
            "second", "millisecond", "microsecond", "nanosecond"
    };
    public static final String[] SUBSECOND_UNIT_STYLES = {"long", "short", "narrow", "numeric"};
    public static final String[] TIME_UNIT_STYLES = {"long", "short", "narrow", "numeric", "2-digit"};
    /**
     * Units in table order.
     */
    public static final String[] UNIT_NAMES = {
            "years", "months", "weeks", "days", "hours", "minutes",
            "seconds", "milliseconds", "microseconds", "nanoseconds"
    };
    /**
     * Digital defaults per unit (null means no digital default, use outer style).
     */
    private static final String[] DIGITAL_DEFAULTS = {
            "long", "long", "long", "long",    // years, months, weeks, days
            "numeric", "numeric", "numeric",    // hours, minutes, seconds
            "numeric", "numeric", "numeric"     // milliseconds, microseconds, nanoseconds
    };
    /**
     * Unit display data for en locale.
     * Pattern: {singularLong, otherLong, singularShort, otherShort, narrow}
     * Narrow uses no space between number and unit symbol.
     */
    private static final Map<String, String[]> EN_UNIT_DATA = new LinkedHashMap<>();

    static {
        EN_UNIT_DATA.put("year", new String[]{"year", "years", "yr", "yrs", "y"});
        EN_UNIT_DATA.put("month", new String[]{"month", "months", "mo", "mos", "mo"});
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

    public JSIntlDurationFormat(Locale locale, String numberingSystem, String style,
                                String[] unitStyles, String[] unitDisplays,
                                Integer fractionalDigits) {
        super();
        this.locale = locale;
        this.numberingSystem = numberingSystem;
        this.style = style;
        this.unitStyles = unitStyles.clone();
        this.unitDisplays = unitDisplays.clone();
        this.fractionalDigits = fractionalDigits;
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

    /**
     * Compute the fractional value by combining sub-second units via BigDecimal.
     * Equivalent to the harness durationToFractional function.
     *
     * @param durationValues map of unit name to double value
     * @param baseUnit       "seconds", "milliseconds", or "microseconds"
     * @return the combined fractional value as BigDecimal
     */
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
            default -> throw new IllegalArgumentException("Invalid base unit: " + baseUnit);
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
            case 9:
                ns = ns.add(new BigDecimal(seconds).toBigInteger().multiply(BigInteger.valueOf(1_000_000_000L)));
                // fallthrough
            case 6:
                ns = ns.add(new BigDecimal(milliseconds).toBigInteger().multiply(BigInteger.valueOf(1_000_000L)));
                // fallthrough
            case 3:
                ns = ns.add(new BigDecimal(microseconds).toBigInteger().multiply(BigInteger.valueOf(1_000L)));
        }

        BigInteger divisor = BigInteger.TEN.pow(exponent);
        BigInteger[] quotientAndRemainder = ns.divideAndRemainder(divisor);
        BigInteger quotient = quotientAndRemainder[0];
        BigInteger remainder = quotientAndRemainder[1];

        // Build the decimal string "{quotient}.{paddedRemainder}"
        String remainderStr = remainder.abs().toString();
        // Pad to exponent digits
        while (remainderStr.length() < exponent) {
            remainderStr = "0" + remainderStr;
        }

        String decimalStr = quotient.toString() + "." + remainderStr;
        return new BigDecimal(decimalStr);
    }

    /**
     * Format a BigDecimal number for precise fractional seconds.
     */
    public static String formatBigDecimalNumber(BigDecimal value, int minimumIntegerDigits,
                                                int minimumFractionDigits, int maximumFractionDigits,
                                                boolean useGrouping, boolean truncate, Locale locale) {
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
        return format.format(value);
    }

    /**
     * Simplified ListFormat.formatToParts that matches our JSIntlListFormat logic.
     * For "unit" type: separators are ", " (long/short) or " " (narrow),
     * with no conjunction word (unlike "conjunction" type which uses "and"/"or").
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
                                                 int minFractionDigits, int maxFractionDigits,
                                                 boolean truncate, Locale locale) {
        BigDecimal formatValue = suppressSign ? value.abs() : value;
        int minIntDigits = is2Digit ? 2 : 1;
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setGroupingUsed(false);
        format.setMinimumIntegerDigits(minIntDigits);
        if (minFractionDigits >= 0) {
            format.setMinimumFractionDigits(minFractionDigits);
        }
        if (maxFractionDigits >= 0) {
            format.setMaximumFractionDigits(maxFractionDigits);
        }
        if (truncate) {
            format.setRoundingMode(RoundingMode.DOWN);
        }
        return format.format(formatValue);
    }

    /**
     * Format a number for numeric/2-digit style with options for sign suppression.
     */
    public static String formatNumericValue(double value, boolean is2Digit, boolean suppressSign,
                                            int minFractionDigits, int maxFractionDigits,
                                            boolean truncate, Locale locale) {
        double formatValue = suppressSign ? Math.abs(value) : value;
        boolean isNegativeZero = !suppressSign && Double.doubleToRawLongBits(value) == Long.MIN_VALUE;

        int minIntDigits = is2Digit ? 2 : 1;
        String result = formatPlainNumber(formatValue, minIntDigits, minFractionDigits, maxFractionDigits,
                false, truncate, locale);

        if (isNegativeZero) {
            result = "-" + formatPlainNumber(0.0, minIntDigits, minFractionDigits, maxFractionDigits,
                    false, truncate, locale);
        }
        return result;
    }

    // =========================================================================
    // Unit formatting helpers — produce the same output as our Intl.NumberFormat
    // with style "unit" so that the test262 harness matches.
    // =========================================================================

    /**
     * Format a plain number (numeric/2-digit style) matching Intl.NumberFormat default output.
     */
    public static String formatPlainNumber(double value, int minimumIntegerDigits,
                                           int minimumFractionDigits, int maximumFractionDigits,
                                           boolean useGrouping, boolean truncate, Locale locale) {
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
        return format.format(value);
    }

    /**
     * Format a BigDecimal value to parts for numeric style (used for fractional seconds).
     */
    public static List<FormatPart> formatToPartsBigDecimal(BigDecimal value, boolean is2Digit, boolean suppressSign,
                                                           int minFractionDigits, int maxFractionDigits,
                                                           boolean truncate, Locale locale) {
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
                                                        int minFractionDigits, int maxFractionDigits,
                                                        boolean truncate, Locale locale) {
        List<FormatPart> parts = new ArrayList<>();
        double formatValue = suppressSign ? Math.abs(value) : value;
        boolean isNegativeZero = !suppressSign && Double.doubleToRawLongBits(value) == Long.MIN_VALUE;
        boolean isNegative = formatValue < 0 || isNegativeZero;

        if (isNegative) {
            parts.add(new FormatPart("minusSign", "-", null));
            formatValue = Math.abs(formatValue);
        }

        int minIntDigits = is2Digit ? 2 : 1;
        String formatted = formatPlainNumber(formatValue, minIntDigits, minFractionDigits, maxFractionDigits,
                false, truncate, locale);
        addNumberPartsToList(parts, formatted);
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

        // Format the absolute number
        String numberStr = formatPlainNumber(formatValue, 1, -1, -1, true, false, locale);
        addNumberPartsToList(parts, numberStr);

        // Add unit
        String[] unitData = EN_UNIT_DATA.get(singularUnit);
        if (unitData != null) {
            boolean isSingular = (Math.abs(suppressSign ? Math.abs(value) : value) == 1.0);
            if (isNegative) {
                isSingular = false; // -1 uses plural in unit format for sign considerations
            }
            // Actually for English, abs(value)==1 → singular regardless of sign
            isSingular = (formatValue == 1.0);

            String unitString;
            switch (unitDisplay) {
                case "long" -> unitString = isSingular ? unitData[0] : unitData[1];
                case "short" -> unitString = isSingular ? unitData[2] : unitData[3];
                case "narrow" -> unitString = unitData[4];
                default -> unitString = isSingular ? unitData[0] : unitData[1];
            }

            if (!"narrow".equals(unitDisplay)) {
                parts.add(new FormatPart("literal", " ", null));
            }
            parts.add(new FormatPart("unit", unitString, null));
        }

        return parts;
    }

    /**
     * Format a number with unit display, matching Intl.NumberFormat({style:"unit",...}).format() output.
     *
     * @param value        the number to format
     * @param singularUnit the singular unit name (e.g., "year")
     * @param unitDisplay  "long", "short", or "narrow"
     * @param suppressSign if true, format absolute value (signDisplay: "never")
     * @param locale       the locale
     * @return formatted string
     */
    public static String formatWithUnit(double value, String singularUnit, String unitDisplay,
                                        boolean suppressSign, Locale locale) {
        double formatValue = suppressSign ? Math.abs(value) : value;
        // Handle -0 → show minus sign when not suppressed
        boolean isNegativeZero = !suppressSign && Double.doubleToRawLongBits(value) == Long.MIN_VALUE;

        String numberPart = formatPlainNumber(formatValue, 1, -1, -1, true, false, locale);
        if (isNegativeZero) {
            numberPart = "-0";
        }

        String[] unitData = EN_UNIT_DATA.get(singularUnit);
        if (unitData == null) {
            // Fallback for unknown units
            return numberPart + " " + singularUnit;
        }

        // Determine plural form: "one" if abs value == 1, else "other"
        double absValue = Math.abs(formatValue);
        boolean isSingular = (absValue == 1.0);

        String unitString;
        switch (unitDisplay) {
            case "long" -> unitString = isSingular ? unitData[0] : unitData[1];
            case "short" -> unitString = isSingular ? unitData[2] : unitData[3];
            case "narrow" -> unitString = unitData[4];
            default -> unitString = isSingular ? unitData[0] : unitData[1];
        }

        if ("narrow".equals(unitDisplay)) {
            return numberPart + unitString;
        } else {
            return numberPart + " " + unitString;
        }
    }

    /**
     * Format a number with unit display, supporting BigDecimal for precision.
     */
    public static String formatWithUnit(BigDecimal value, String singularUnit, String unitDisplay,
                                        boolean suppressSign, int minFractionDigits, int maxFractionDigits,
                                        Locale locale) {
        BigDecimal formatValue = suppressSign ? value.abs() : value;

        String numberPart = formatBigDecimalNumber(formatValue, 1, minFractionDigits, maxFractionDigits,
                true, false, locale);

        String[] unitData = EN_UNIT_DATA.get(singularUnit);
        if (unitData == null) {
            return numberPart + " " + singularUnit;
        }

        boolean isSingular = (formatValue.abs().compareTo(BigDecimal.ONE) == 0);
        String unitString;
        switch (unitDisplay) {
            case "long" -> unitString = isSingular ? unitData[0] : unitData[1];
            case "short" -> unitString = isSingular ? unitData[2] : unitData[3];
            case "narrow" -> unitString = unitData[4];
            default -> unitString = isSingular ? unitData[0] : unitData[1];
        }

        if ("narrow".equals(unitDisplay)) {
            return numberPart + unitString;
        } else {
            return numberPart + " " + unitString;
        }
    }

    /**
     * Returns the digital default style for the given unit index.
     */
    public static String getDigitalDefault(int unitIndex) {
        return DIGITAL_DEFAULTS[unitIndex];
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

    // =========================================================================
    // formatToParts helpers — produce part arrays matching Intl.NumberFormat
    // =========================================================================

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
        //   + milliseconds*10^-3 + microseconds*10^-6 + nanoseconds*10^-9
        // We scale everything to nanoseconds to use integer arithmetic:
        //   normalizedNanos = (days*86400 + hours*3600 + minutes*60 + seconds) * 10^9
        //                   + milliseconds * 10^6 + microseconds * 10^3 + nanoseconds
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
                .add(bdHours.multiply(BigInteger.valueOf(3600L)))
                .add(bdMinutes.multiply(BigInteger.valueOf(60L)))
                .add(bdSeconds)
                .multiply(BILLION)
                .add(bdMilliseconds.multiply(BigInteger.valueOf(1_000_000L)))
                .add(bdMicroseconds.multiply(BigInteger.valueOf(1_000L)))
                .add(bdNanoseconds);

        // limit = 2^53 * 10^9
        BigInteger limit = BigInteger.valueOf(9007199254740992L).multiply(BILLION);
        if (normalizedNanos.abs().compareTo(limit) >= 0) {
            context.throwRangeError("Duration total seconds out of range");
            return false;
        }

        return true;
    }

    /**
     * Validate and extract a duration record from a JSValue.
     * Returns null if the value is invalid (exception already thrown on context).
     */
    public static Map<String, Double> toDurationRecord(JSContext context, JSValue input) {
        // Step 1: If Type(input) is String, throw RangeError (temporal duration string)
        if (input instanceof JSString) {
            context.throwRangeError("Invalid duration");
            return null;
        }

        // Step 2: If Type(input) is not Object, throw TypeError
        if (!(input instanceof JSObject inputObj)) {
            context.throwTypeError("Invalid duration value");
            return null;
        }

        Map<String, Double> result = new LinkedHashMap<>();
        boolean anyDefined = false;

        for (String unitName : UNIT_NAMES) {
            JSValue val = inputObj.get(context, PropertyKey.fromString(unitName));
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

    /**
     * Format a duration to a string (Intl.DurationFormat.prototype.format).
     */
    public String formatDuration(Map<String, Double> durationValues) {
        List<FormatPart> parts = partitionDurationFormatPattern(durationValues);
        StringBuilder sb = new StringBuilder();
        for (FormatPart part : parts) {
            sb.append(part.value());
        }
        return sb.toString();
    }

    public Integer getFractionalDigits() {
        return fractionalDigits;
    }

    public Locale getLocale() {
        return locale;
    }

    // =========================================================================
    // Duration formatting — implements PartitionDurationFormatPattern
    // =========================================================================

    public String getNumberingSystem() {
        return numberingSystem;
    }

    public String getStyle() {
        return style;
    }

    public String getUnitDisplay(int index) {
        return unitDisplays[index];
    }

    // =========================================================================
    // ListFormat formatToParts helper
    // =========================================================================

    public String getUnitStyle(int index) {
        return unitStyles[index];
    }

    /**
     * Partition a duration into formatted parts (PartitionDurationFormatPattern).
     *
     * @param durationValues map of unit name → value
     * @return list of FormatPart representing the formatted duration
     */
    public List<FormatPart> partitionDurationFormatPattern(Map<String, Double> durationValues) {
        List<List<FormatPart>> result = new ArrayList<>();
        boolean needSeparator = false;
        boolean displayNegativeSign = true;

        for (int i = 0; i < UNIT_NAMES.length; i++) {
            String unitName = UNIT_NAMES[i];
            String singularUnit = SINGULAR_UNIT_NAMES[i];
            double value = durationValues.getOrDefault(unitName, 0.0);

            String unitStyle = unitStyles[i];
            String display = unitDisplays[i];

            // Handle fractional combining for seconds/milliseconds/microseconds
            boolean done = false;
            BigDecimal fractionalBigDecimal = null;
            if ("seconds".equals(unitName) || "milliseconds".equals(unitName) || "microseconds".equals(unitName)) {
                int nextIndex = i + 1;
                if (nextIndex < UNIT_NAMES.length) {
                    String nextStyle = unitStyles[nextIndex];
                    if ("numeric".equals(nextStyle)) {
                        // Combine sub-second units into fractional value
                        fractionalBigDecimal = computeFractionalValue(durationValues, unitName);
                        value = fractionalBigDecimal.doubleValue();
                        done = true;
                    }
                }
            }

            // Check displayRequired for minutes when part of a numeric/digital group.
            // Per spec: Display zero numeric minutes when seconds will be displayed,
            // but only when a previous numeric unit (like hours) was already displayed
            // (i.e., needSeparator is true from a prior numeric unit in the group).
            boolean displayRequired = false;
            if ("minutes".equals(unitName) && needSeparator) {
                boolean hasSubsequentValue = "always".equals(unitDisplays[6]) // secondsDisplay
                        || durationValues.getOrDefault("seconds", 0.0) != 0
                        || durationValues.getOrDefault("milliseconds", 0.0) != 0
                        || durationValues.getOrDefault("microseconds", 0.0) != 0
                        || durationValues.getOrDefault("nanoseconds", 0.0) != 0;
                displayRequired = hasSubsequentValue;
            }

            // Determine if this unit should be displayed
            boolean shouldDisplay;
            if (fractionalBigDecimal != null) {
                shouldDisplay = fractionalBigDecimal.signum() != 0 || !"auto".equals(display) || displayRequired;
            } else {
                shouldDisplay = value != 0 || !"auto".equals(display) || displayRequired;
            }

            if (shouldDisplay) {
                // Handle negative sign display
                boolean suppressSign;
                if (displayNegativeSign) {
                    displayNegativeSign = false;
                    suppressSign = false;

                    // Set to -0 if value is 0 but duration has negative components
                    if (value == 0 && (fractionalBigDecimal == null || fractionalBigDecimal.signum() == 0)) {
                        boolean hasNegative = false;
                        for (String u : UNIT_NAMES) {
                            double v = durationValues.getOrDefault(u, 0.0);
                            if (v < 0) {
                                hasNegative = true;
                                break;
                            }
                        }
                        if (hasNegative) {
                            value = -0.0;
                            if (fractionalBigDecimal != null) {
                                fractionalBigDecimal = BigDecimal.ZERO.negate();
                            }
                        }
                    }
                } else {
                    suppressSign = true;
                }

                // Format the value
                List<FormatPart> unitParts;
                boolean isNumericStyle = "numeric".equals(unitStyle) || "2-digit".equals(unitStyle);

                if (isNumericStyle) {
                    // Numeric/2-digit formatting
                    boolean is2Digit = "2-digit".equals(unitStyle);
                    if (done && fractionalBigDecimal != null) {
                        // Fractional seconds with BigDecimal precision
                        int minFrac = fractionalDigits != null ? fractionalDigits : 0;
                        int maxFrac = fractionalDigits != null ? fractionalDigits : 9;
                        unitParts = formatToPartsBigDecimal(fractionalBigDecimal, is2Digit, suppressSign,
                                minFrac, maxFrac, true, locale);
                    } else {
                        unitParts = formatToPartsNumeric(value, is2Digit, suppressSign, -1, -1, false, locale);
                    }
                } else {
                    // Unit display formatting (long/short/narrow)
                    unitParts = formatToPartsWithUnit(value, singularUnit, unitStyle, suppressSign, locale);
                }

                // Add unit identifier to each part
                List<FormatPart> taggedParts = new ArrayList<>();
                for (FormatPart part : unitParts) {
                    taggedParts.add(new FormatPart(part.type(), part.value(), singularUnit));
                }

                if (!needSeparator) {
                    List<FormatPart> list = new ArrayList<>(taggedParts);
                    if (isNumericStyle) {
                        needSeparator = true;
                    }
                    result.add(list);
                } else {
                    // Append to last group with time separator
                    List<FormatPart> lastGroup = result.get(result.size() - 1);
                    lastGroup.add(new FormatPart("literal", ":", null));
                    lastGroup.addAll(taggedParts);
                }
            }

            if (done) {
                break;
            }
        }

        // Join groups using ListFormat
        String listStyle = "digital".equals(style) ? "short" : style;
        JSIntlListFormat listFormat = new JSIntlListFormat(locale, listStyle, "unit");

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
        List<ListFormatPart> listParts = formatListToParts(listFormat, strings);
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

    // =========================================================================
    // ToDurationRecord validation
    // =========================================================================

    /**
     * Represents a single part in formatToParts output.
     */
    public record FormatPart(String type, String value, String unit) {
    }

    public record ListFormatPart(String type, String value) {
    }
}
