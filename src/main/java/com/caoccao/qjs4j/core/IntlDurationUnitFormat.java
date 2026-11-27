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

import com.caoccao.qjs4j.core.JSIntlDurationFormat.FormatPart;
import java.math.*;
import java.text.NumberFormat;
import java.util.*;

/** Localized number and unit rendering shared by duration strings and their parts. */
final class IntlDurationUnitFormat {
    /**
     * English CLDR unit names: {singular long, plural long, singular short, plural short, narrow}. Narrow uses no space
     * between the number and the unit symbol.
     */
    private static final Map<String, String[]> EN_UNIT_DATA = new HashMap<>();

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

    private final String display;

    private final Locale locale;

    private final int maximumFractionDigits;

    private final int minimumFractionDigits;

    private final int minimumIntegerDigits;

    private NumberFormat numberFormat;

    private final boolean truncate;

    private final String unit;
    IntlDurationUnitFormat(Locale locale, String unit, String display, boolean twoDigit, int minFraction,
            int maxFraction, boolean truncate) {
        this.locale = locale;
        this.unit = unit;
        this.display = display;
        minimumIntegerDigits = twoDigit ? 2 : 1;
        minimumFractionDigits = minFraction;
        maximumFractionDigits = maxFraction;
        this.truncate = truncate;
    }

    // NumberFormat is mutable. Keep access to a cached formatter inside its owner's monitor.
    private synchronized String digits(double value, BigDecimal fraction, boolean magnitude) {
        NumberFormat format = numberFormat();
        if (fraction != null) {
            return format.format(magnitude ? fraction.abs() : fraction);
        }
        if (!magnitude && Double.doubleToRawLongBits(value) == Long.MIN_VALUE) {
            return unit == null ? "-" + format.format(0.0) : "-0";
        }
        return format.format(magnitude ? Math.abs(value) : value);
    }

    String format(double value, BigDecimal fraction, boolean suppressSign) {
        String number = digits(value, fraction, suppressSign);
        return unit == null ? number : joinNumberAndUnit(number, unit, display, locale);
    }

    private NumberFormat numberFormat() {
        if (numberFormat == null) {
            numberFormat = createNumberFormat(minimumIntegerDigits, minimumFractionDigits, maximumFractionDigits,
                    unit != null, truncate, locale);
        }
        return numberFormat;
    }

    List<FormatPart> parts(double value, BigDecimal fraction, boolean suppressSign) {
        boolean negative = fraction == null
                ? (value < 0 || Double.doubleToRawLongBits(value) == Long.MIN_VALUE)
                : fraction.signum() < 0;
        return numberParts(digits(value, fraction, true), !suppressSign && negative, unit, display, locale);
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

    static NumberFormat createNumberFormat(int minimumIntegerDigits, int minimumFractionDigits,
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
    static String formatEnglishTimeUnit(String numberPart, String unit, String unitDisplay, Locale locale) {
        String unitName = getEnglishTimeUnitName(numberPart, unit, unitDisplay, locale);
        if (unitName == null) {
            return null;
        }
        return "narrow".equals(unitDisplay) ? numberPart + unitName : numberPart + " " + unitName;
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

    private static String joinNumberAndUnit(String numberPart, String unit, String display, Locale locale) {
        String english = formatEnglishTimeUnit(numberPart, unit, display, locale);
        return english != null ? english : numberPart + " " + unit;
    }

    private static List<FormatPart> numberParts(String magnitude, boolean negative, String unit, String display,
            Locale locale) {
        List<FormatPart> parts = new ArrayList<>();
        if (negative) {
            parts.add(new FormatPart("minusSign", "-", null));
        }
        addNumberPartsToList(parts, magnitude);
        if (unit != null) {
            addUnitParts(parts, magnitude, unit, display, locale);
        }
        return parts;
    }
}
