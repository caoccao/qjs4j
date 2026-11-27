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

import com.caoccao.qjs4j.core.temporal.duration.TemporalDuration;
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

    private final Integer fractionalDigits;
    private JSIntlListFormat listFormat;
    private final Locale locale;
    private final String numberingSystem;
    private final String style;
    private final String[] unitDisplays;
    private IntlDurationUnitFormat[] unitFormats;
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
        String formatted = unitFormat(index, fraction != null).format(value, fraction, suppressSign);
        if ("digital".equals(style) && "day".equals(SINGULAR_UNIT_NAMES[index]) && Math.abs(value) >= 1000
                && formatted.endsWith(" day")) {
            return formatted + "s";
        }
        return formatted;
    }

    private List<FormatPart> formatUnitParts(int index, double value, BigDecimal fraction, boolean suppressSign) {
        return unitFormat(index, fraction != null).parts(value, fraction, suppressSign);
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

    private synchronized IntlDurationUnitFormat unitFormat(int index, boolean fractional) {
        if (unitFormats == null) {
            unitFormats = new IntlDurationUnitFormat[unitStyles.length * 2];
        }
        int slot = fractional ? index + unitStyles.length : index;
        IntlDurationUnitFormat format = unitFormats[slot];
        if (format == null) {
            String display = unitStyles[index];
            String unit = isNumericStyle(display) ? null : SINGULAR_UNIT_NAMES[index];
            int min = fractional ? (fractionalDigits == null ? 0 : fractionalDigits) : -1;
            int max = fractional ? (fractionalDigits == null ? 9 : fractionalDigits) : -1;
            format = new IntlDurationUnitFormat(locale, unit, display, "2-digit".equals(display), min, max, fractional);
            unitFormats[slot] = format;
        }
        return format;
    }

    private synchronized JSIntlListFormat unitListFormat() {
        if (listFormat == null) {
            listFormat = new JSIntlListFormat(context, locale, "digital".equals(style) ? "short" : style, "unit");
        }
        return listFormat;
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

    /**
     * Format a BigDecimal number for precise fractional seconds.
     */
    public static String formatBigDecimalNumber(BigDecimal value, int minimumIntegerDigits, int minimumFractionDigits,
            int maximumFractionDigits, boolean useGrouping, boolean truncate, Locale locale) {
        return IntlDurationUnitFormat.createNumberFormat(minimumIntegerDigits, minimumFractionDigits,
                maximumFractionDigits, useGrouping, truncate, locale).format(value);
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
        return IntlDurationUnitFormat.formatEnglishTimeUnit(numberPart, unit, unitDisplay, locale);
    }

    /**
     * Simplified ListFormat.formatToParts that matches our JSIntlListFormat logic. For "unit" type: separators are ", "
     * (long/short) or " " (narrow), with no conjunction word (unlike "conjunction" type which uses "and"/"or").
     */
    public static List<ListFormatPart> formatListToParts(JSIntlListFormat listFormat, List<String> values) {
        int count = values.size();
        List<ListFormatPart> parts = new ArrayList<>();
        if (count > 0) {
            parts.add(new ListFormatPart("element", values.get(0)));
            if (count > 1) {
                JSIntlListFormat.ListPatterns patterns = listFormat.getPatterns();
                for (int i = 1; i < count; i++) {
                    parts.add(new ListFormatPart("literal", patterns.separator(i, count)));
                    parts.add(new ListFormatPart("element", values.get(i)));
                }
            }
        }
        return parts;
    }

    /**
     * Format a BigDecimal for numeric/2-digit style.
     */
    public static String formatNumericBigDecimal(BigDecimal value, boolean is2Digit, boolean suppressSign,
            int minFractionDigits, int maxFractionDigits, boolean truncate, Locale locale) {
        return new IntlDurationUnitFormat(locale, null, null, is2Digit, minFractionDigits, maxFractionDigits, truncate)
                .format(0, value, suppressSign);
    }

    /**
     * Format a number for numeric/2-digit style with options for sign suppression.
     */
    public static String formatNumericValue(double value, boolean is2Digit, boolean suppressSign, int minFractionDigits,
            int maxFractionDigits, boolean truncate, Locale locale) {
        return new IntlDurationUnitFormat(locale, null, null, is2Digit, minFractionDigits, maxFractionDigits, truncate)
                .format(value, null, suppressSign);
    }

    /**
     * Format a plain number (numeric/2-digit style) matching Intl.NumberFormat default output.
     */
    public static String formatPlainNumber(double value, int minimumIntegerDigits, int minimumFractionDigits,
            int maximumFractionDigits, boolean useGrouping, boolean truncate, Locale locale) {
        return IntlDurationUnitFormat.createNumberFormat(minimumIntegerDigits, minimumFractionDigits,
                maximumFractionDigits, useGrouping, truncate, locale).format(value);
    }

    /**
     * Format a BigDecimal value to parts for numeric style (used for fractional seconds).
     */
    public static List<FormatPart> formatToPartsBigDecimal(BigDecimal value, boolean is2Digit, boolean suppressSign,
            int minFractionDigits, int maxFractionDigits, boolean truncate, Locale locale) {
        return new IntlDurationUnitFormat(locale, null, null, is2Digit, minFractionDigits, maxFractionDigits, truncate)
                .parts(0, value, suppressSign);
    }

    /**
     * Format a value to parts for numeric/2-digit style.
     */
    public static List<FormatPart> formatToPartsNumeric(double value, boolean is2Digit, boolean suppressSign,
            int minFractionDigits, int maxFractionDigits, boolean truncate, Locale locale) {
        return new IntlDurationUnitFormat(locale, null, null, is2Digit, minFractionDigits, maxFractionDigits, truncate)
                .parts(value, null, suppressSign);
    }

    public static List<FormatPart> formatToPartsWithUnit(BigDecimal value, String singularUnit, String unitDisplay,
            boolean suppressSign, int minFractionDigits, int maxFractionDigits, Locale locale) {
        return new IntlDurationUnitFormat(locale, singularUnit, unitDisplay, false, minFractionDigits,
                maxFractionDigits, true).parts(0, value, suppressSign);
    }

    /**
     * Format a value to parts for unit style formatting.
     */
    public static List<FormatPart> formatToPartsWithUnit(double value, String singularUnit, String unitDisplay,
            boolean suppressSign, Locale locale) {
        return new IntlDurationUnitFormat(locale, singularUnit, unitDisplay, false, -1, -1, false).parts(value, null,
                suppressSign);
    }

    /**
     * Format a number with unit display, supporting BigDecimal for precision.
     */
    public static String formatWithUnit(BigDecimal value, String singularUnit, String unitDisplay, boolean suppressSign,
            int minFractionDigits, int maxFractionDigits, Locale locale) {
        return new IntlDurationUnitFormat(locale, singularUnit, unitDisplay, false, minFractionDigits,
                maxFractionDigits, true).format(0, value, suppressSign);
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
        return new IntlDurationUnitFormat(locale, singularUnit, unitDisplay, false, -1, -1, false).format(value, null,
                suppressSign);
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

    static boolean isNumericStyle(String unitStyle) {
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
