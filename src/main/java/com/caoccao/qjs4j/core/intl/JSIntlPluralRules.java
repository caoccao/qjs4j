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

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSObject;
import com.caoccao.qjs4j.exceptions.JSRangeErrorException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Intl.PluralRules instance object.
 */
public final class JSIntlPluralRules extends JSObject {
    public static final String NAME = "Intl.PluralRules";
    private final Locale locale;
    private final int maximumFractionDigits;
    private final Integer maximumSignificantDigits;
    private final int minimumFractionDigits;
    private final int minimumIntegerDigits;
    private final Integer minimumSignificantDigits;
    private final String notation;
    private final String type;

    public JSIntlPluralRules(JSContext context, Locale locale, String type, String notation, int minimumIntegerDigits,
            int minimumFractionDigits, int maximumFractionDigits, Integer minimumSignificantDigits,
            Integer maximumSignificantDigits) {
        super(context);
        this.locale = locale;
        this.type = type;
        this.notation = notation;
        this.minimumIntegerDigits = minimumIntegerDigits;
        this.minimumFractionDigits = minimumFractionDigits;
        this.maximumFractionDigits = maximumFractionDigits;
        this.minimumSignificantDigits = minimumSignificantDigits;
        this.maximumSignificantDigits = maximumSignificantDigits;
    }

    private BigDecimal applyRounding(double value) {
        BigDecimal decimal = BigDecimal.valueOf(Math.abs(value));
        if (maximumSignificantDigits != null) {
            decimal = decimal.round(new MathContext(maximumSignificantDigits, RoundingMode.HALF_UP))
                    .stripTrailingZeros();
            int minimumScale = minimumSignificantDigits == null
                    ? 0
                    : Math.max(0, minimumSignificantDigits - decimal.precision() + decimal.scale());
            return decimal.setScale(Math.max(minimumScale, decimal.scale()));
        }
        decimal = decimal.setScale(maximumFractionDigits, RoundingMode.HALF_UP).stripTrailingZeros();
        return decimal.setScale(Math.max(minimumFractionDigits, decimal.scale()));
    }

    public Locale getLocale() {
        return locale;
    }

    public int getMaximumFractionDigits() {
        return maximumFractionDigits;
    }

    public Integer getMaximumSignificantDigits() {
        return maximumSignificantDigits;
    }

    public int getMinimumFractionDigits() {
        return minimumFractionDigits;
    }

    public int getMinimumIntegerDigits() {
        return minimumIntegerDigits;
    }

    public Integer getMinimumSignificantDigits() {
        return minimumSignificantDigits;
    }

    public String getNotation() {
        return notation;
    }

    public List<String> getPluralCategories() {
        return PluralRulesData.categories(locale, "ordinal".equals(type));
    }

    public String getType() {
        return type;
    }

    public String select(double number) {
        if (!Double.isFinite(number)) {
            return "other";
        }
        BigDecimal rounded = applyRounding(number);
        int magnitude = rounded.signum() == 0 ? 0 : rounded.precision() - rounded.scale() - 1;
        int exponent = switch (notation) {
            case "compact" -> Math.max(0, Math.floorDiv(magnitude, 3) * 3);
            case "engineering" -> Math.floorDiv(magnitude, 3) * 3;
            case "scientific" -> magnitude;
            default -> 0;
        };
        return PluralRulesData.select(locale, "ordinal".equals(type), rounded, exponent);
    }

    public String selectRange(double start, double end) {
        if (Double.isNaN(start) || Double.isNaN(end)) {
            throw new JSRangeErrorException("NaN is not allowed");
        }
        return PluralRulesData.range(locale, select(start), select(end));
    }
}
