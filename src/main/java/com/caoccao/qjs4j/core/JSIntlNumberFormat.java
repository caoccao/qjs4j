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

    public JSIntlNumberFormat(Locale locale, String style, String currency) {
        this(locale, style, currency, true, 1, -1, -1, false, 0);
    }

    public JSIntlNumberFormat(Locale locale, String style, String currency,
                              boolean useGrouping, int minimumIntegerDigits,
                              int minimumFractionDigits, int maximumFractionDigits,
                              boolean useSignificantDigits, int maximumSignificantDigits) {
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
        if (useSignificantDigits) {
            return formatWithSignificantDigits(BigDecimal.valueOf(value));
        }
        return createBaseFormat().format(value);
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

    public String getStyle() {
        return style;
    }
}
