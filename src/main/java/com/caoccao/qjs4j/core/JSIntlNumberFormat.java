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
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/**
 * Intl.NumberFormat instance object.
 */
public final class JSIntlNumberFormat extends JSObject {
    public static final String NAME = "Intl.NumberFormat";
    private final String currency;
    private final Locale locale;
    private final int maximumFractionDigits;
    private final int maximumSignificantDigits;
    private final int minimumFractionDigits;
    private final int minimumIntegerDigits;
    private final String style;
    private final boolean useGrouping;
    private final boolean useSignificantDigits;
    private final String unit;
    private final String unitDisplay;
    private final String signDisplay;
    private final String roundingMode;
    private final String numberingSystem;

    public JSIntlNumberFormat(Locale locale, String style, String currency) {
        this(locale, style, currency, true, 1, -1, -1, false, 0,
                null, null, "auto", "halfExpand", null);
    }

    public JSIntlNumberFormat(Locale locale, String style, String currency,
                              boolean useGrouping, int minimumIntegerDigits,
                              int minimumFractionDigits, int maximumFractionDigits,
                              boolean useSignificantDigits, int maximumSignificantDigits) {
        this(locale, style, currency, useGrouping, minimumIntegerDigits,
                minimumFractionDigits, maximumFractionDigits, useSignificantDigits, maximumSignificantDigits,
                null, null, "auto", "halfExpand", null);
    }

    public JSIntlNumberFormat(Locale locale, String style, String currency,
                              boolean useGrouping, int minimumIntegerDigits,
                              int minimumFractionDigits, int maximumFractionDigits,
                              boolean useSignificantDigits, int maximumSignificantDigits,
                              String unit, String unitDisplay, String signDisplay,
                              String roundingMode, String numberingSystem) {
        super();
        this.locale = locale;
        this.style = style;
        this.currency = currency;
        this.useGrouping = useGrouping;
        this.minimumIntegerDigits = minimumIntegerDigits;
        this.minimumFractionDigits = minimumFractionDigits;
        this.maximumFractionDigits = maximumFractionDigits;
        this.useSignificantDigits = useSignificantDigits;
        this.maximumSignificantDigits = maximumSignificantDigits;
        this.unit = unit;
        this.unitDisplay = unitDisplay;
        this.signDisplay = signDisplay != null ? signDisplay : "auto";
        this.roundingMode = roundingMode != null ? roundingMode : "halfExpand";
        this.numberingSystem = numberingSystem;
    }

    private NumberFormat createBaseFormat() {
        NumberFormat format;
        switch (style) {
            case "currency" -> {
                format = NumberFormat.getCurrencyInstance(locale);
                if (currency != null && !currency.isBlank()) {
                    try {
                        format.setCurrency(Currency.getInstance(currency.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            case "percent" -> format = NumberFormat.getPercentInstance(locale);
            default -> format = NumberFormat.getNumberInstance(locale);
        }
        format.setGroupingUsed(useGrouping);
        if (!useSignificantDigits) {
            format.setMinimumIntegerDigits(minimumIntegerDigits);
            if (minimumFractionDigits >= 0) {
                format.setMinimumFractionDigits(minimumFractionDigits);
            }
            if (maximumFractionDigits >= 0) {
                format.setMaximumFractionDigits(maximumFractionDigits);
            }
        }
        return format;
    }

    private int computeFractionDigitsForSigDigits(BigDecimal rounded) {
        if (rounded.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        BigDecimal stripped = rounded.stripTrailingZeros();
        int scale = stripped.scale();
        if (scale > 0) {
            return scale;
        }
        return 0;
    }

    private String extractPercentSuffix(String percentFormatted) {
        int lastDigitIndex = -1;
        for (int i = percentFormatted.length() - 1; i >= 0; i--) {
            char c = percentFormatted.charAt(i);
            if (Character.isDigit(c)) {
                lastDigitIndex = i;
                break;
            }
        }
        if (lastDigitIndex >= 0 && lastDigitIndex < percentFormatted.length() - 1) {
            return percentFormatted.substring(lastDigitIndex + 1);
        }
        return "%";
    }

    public String format(double value) {
        // Apply signDisplay
        double formatValue = applySignDisplay(value);

        if ("unit".equals(style) && unit != null) {
            return JSIntlDurationFormat.formatWithUnit(formatValue, unit,
                    unitDisplay != null ? unitDisplay : "short", false, locale);
        }
        if (useSignificantDigits) {
            return formatWithSignificantDigits(BigDecimal.valueOf(formatValue));
        }
        NumberFormat baseFormat = createBaseFormat();
        if ("trunc".equals(roundingMode)) {
            baseFormat.setRoundingMode(java.math.RoundingMode.DOWN);
        }
        return baseFormat.format(formatValue);
    }

    /**
     * Format a value, returning parts as JSArray for formatToParts.
     */
    public JSArray formatToParts(JSContext context, double value) {
        double formatValue = applySignDisplay(value);
        boolean isNegativeZero = isOriginalNegativeZero(value) && !"never".equals(signDisplay);
        boolean isNegative = formatValue < 0 || isNegativeZero;

        if ("unit".equals(style) && unit != null) {
            List<JSIntlDurationFormat.FormatPart> parts = JSIntlDurationFormat.formatToPartsWithUnit(
                    formatValue, unit, unitDisplay != null ? unitDisplay : "short", false, locale);
            return partsToJSArray(context, parts);
        }

        // Numeric format
        double absValue = Math.abs(formatValue);
        if (isNegativeZero) {
            absValue = 0.0;
        }
        NumberFormat baseFormat = createBaseFormat();
        if ("trunc".equals(roundingMode)) {
            baseFormat.setRoundingMode(java.math.RoundingMode.DOWN);
        }
        String formatted = baseFormat.format(absValue);

        JSArray result = context.createJSArray();
        int partIndex = 0;
        if (isNegative) {
            JSObject minusPart = context.createJSObject();
            minusPart.set("type", new JSString("minusSign"));
            minusPart.set("value", new JSString("-"));
            result.set(context, partIndex++, minusPart);
        }
        // Split number into integer/decimal/fraction
        int dotIndex = formatted.indexOf('.');
        if (dotIndex >= 0) {
            JSObject intPart = context.createJSObject();
            intPart.set("type", new JSString("integer"));
            intPart.set("value", new JSString(formatted.substring(0, dotIndex)));
            result.set(context, partIndex++, intPart);

            JSObject decPart = context.createJSObject();
            decPart.set("type", new JSString("decimal"));
            decPart.set("value", new JSString("."));
            result.set(context, partIndex++, decPart);

            if (dotIndex + 1 < formatted.length()) {
                JSObject fracPart = context.createJSObject();
                fracPart.set("type", new JSString("fraction"));
                fracPart.set("value", new JSString(formatted.substring(dotIndex + 1)));
                result.set(context, partIndex++, fracPart);
            }
        } else {
            // Check for group separators (commas in en)
            JSObject intPart = context.createJSObject();
            intPart.set("type", new JSString("integer"));
            intPart.set("value", new JSString(formatted));
            result.set(context, partIndex++, intPart);
        }
        return result;
    }

    /**
     * Format a string value (for BigDecimal precision) to parts.
     */
    public JSArray formatToParts(JSContext context, String stringValue) {
        try {
            BigDecimal bd = new BigDecimal(stringValue);
            // Format using BigDecimal for precision
            NumberFormat baseFormat = createBaseFormat();
            if ("trunc".equals(roundingMode)) {
                baseFormat.setRoundingMode(java.math.RoundingMode.DOWN);
            }
            // Check signDisplay: suppress sign when "never"
            boolean isNegative = bd.signum() < 0 && !"never".equals(signDisplay);
            BigDecimal absValue = bd.abs();
            String formatted = baseFormat.format(absValue);

            JSArray result = context.createJSArray();
            int partIndex = 0;
            if (isNegative) {
                JSObject minusPart = context.createJSObject();
                minusPart.set("type", new JSString("minusSign"));
                minusPart.set("value", new JSString("-"));
                result.set(context, partIndex++, minusPart);
            }
            int dotIndex = formatted.indexOf('.');
            if (dotIndex >= 0) {
                JSObject intPart = context.createJSObject();
                intPart.set("type", new JSString("integer"));
                intPart.set("value", new JSString(formatted.substring(0, dotIndex)));
                result.set(context, partIndex++, intPart);

                JSObject decPart = context.createJSObject();
                decPart.set("type", new JSString("decimal"));
                decPart.set("value", new JSString("."));
                result.set(context, partIndex++, decPart);

                if (dotIndex + 1 < formatted.length()) {
                    JSObject fracPart = context.createJSObject();
                    fracPart.set("type", new JSString("fraction"));
                    fracPart.set("value", new JSString(formatted.substring(dotIndex + 1)));
                    result.set(context, partIndex++, fracPart);
                }
            } else {
                JSObject intPart = context.createJSObject();
                intPart.set("type", new JSString("integer"));
                intPart.set("value", new JSString(formatted));
                result.set(context, partIndex++, intPart);
            }
            return result;
        } catch (NumberFormatException e) {
            // Fall back to parsing as double
            return formatToParts(context, Double.parseDouble(stringValue));
        }
    }

    private double applySignDisplay(double value) {
        if ("never".equals(signDisplay)) {
            return Math.abs(value);
        }
        return value;
    }

    private boolean isOriginalNegativeZero(double value) {
        return Double.doubleToRawLongBits(value) == Long.MIN_VALUE;
    }

    private JSArray partsToJSArray(JSContext context, List<JSIntlDurationFormat.FormatPart> parts) {
        JSArray result = context.createJSArray();
        for (int i = 0; i < parts.size(); i++) {
            JSIntlDurationFormat.FormatPart part = parts.get(i);
            JSObject partObj = context.createJSObject();
            partObj.set("type", new JSString(part.type()));
            partObj.set("value", new JSString(part.value()));
            result.set(context, i, partObj);
        }
        return result;
    }

    public String format(BigInteger value) {
        BigDecimal decimalValue = new BigDecimal(value);
        if (useSignificantDigits) {
            return formatWithSignificantDigits(decimalValue);
        }
        if ("percent".equals(style)) {
            BigDecimal percentValue = decimalValue.multiply(BigDecimal.valueOf(100));
            NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
            numberFormat.setGroupingUsed(useGrouping);
            numberFormat.setMinimumIntegerDigits(minimumIntegerDigits);
            if (minimumFractionDigits >= 0) {
                numberFormat.setMinimumFractionDigits(minimumFractionDigits);
            }
            if (maximumFractionDigits >= 0) {
                numberFormat.setMaximumFractionDigits(maximumFractionDigits);
            }
            String formatted = numberFormat.format(percentValue);
            NumberFormat percentRef = NumberFormat.getPercentInstance(locale);
            String refFormatted = percentRef.format(0);
            String percentSuffix = extractPercentSuffix(refFormatted);
            return formatted + percentSuffix;
        }
        return createBaseFormat().format(value);
    }

    private String formatWithSignificantDigits(BigDecimal decimalValue) {
        boolean isPercent = "percent".equals(style);
        BigDecimal valueForSigDigits;
        if (isPercent) {
            valueForSigDigits = decimalValue.multiply(BigDecimal.valueOf(100));
        } else {
            valueForSigDigits = decimalValue;
        }

        BigDecimal rounded;
        if (valueForSigDigits.compareTo(BigDecimal.ZERO) == 0) {
            rounded = BigDecimal.ZERO;
        } else {
            rounded = valueForSigDigits.round(new MathContext(maximumSignificantDigits, RoundingMode.HALF_UP));
        }

        NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
        numberFormat.setGroupingUsed(useGrouping);
        int fractionDigits = computeFractionDigitsForSigDigits(rounded);
        numberFormat.setMinimumFractionDigits(fractionDigits);
        numberFormat.setMaximumFractionDigits(fractionDigits);

        if (isPercent) {
            String formatted = numberFormat.format(rounded);
            NumberFormat percentRef = NumberFormat.getPercentInstance(locale);
            String refFormatted = percentRef.format(0);
            String percentSuffix = extractPercentSuffix(refFormatted);
            return formatted + percentSuffix;
        } else {
            return numberFormat.format(rounded);
        }
    }

    public String getCurrency() {
        return currency;
    }

    public Locale getLocale() {
        return locale;
    }

    public int getMinimumIntegerDigits() {
        return minimumIntegerDigits;
    }

    public boolean getUseGrouping() {
        return useGrouping;
    }

    public String getStyle() {
        return style;
    }

    public String getUnit() {
        return unit;
    }

    public String getUnitDisplay() {
        return unitDisplay;
    }

    public String getSignDisplay() {
        return signDisplay;
    }

    public String getRoundingMode() {
        return roundingMode;
    }

    public String getNumberingSystem() {
        return numberingSystem;
    }
}
