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
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Intl.PluralRules instance object.
 */
public final class JSIntlPluralRules extends JSObject {
    public static final String NAME = "Intl.PluralRules";
    private static final List<String> CATEGORIES_AR = List.of("zero", "one", "two", "few", "many", "other");
    private static final List<String> CATEGORIES_EN = List.of("one", "other");
    private static final List<String> CATEGORIES_FA = List.of("one", "other");
    private static final List<String> CATEGORIES_FR = List.of("one", "many", "other");
    private static final List<String> CATEGORIES_GV = List.of("one", "two", "few", "many", "other");
    private static final List<String> CATEGORIES_KO = List.of("other");
    private static final List<String> CATEGORIES_ORDINAL = List.of("one", "two", "few", "other");
    private static final List<String> CATEGORIES_SL = List.of("one", "two", "few", "other");
    private final Locale locale;
    private final int maximumFractionDigits;
    private final Integer maximumSignificantDigits;
    private final int minimumFractionDigits;
    private final int minimumIntegerDigits;
    private final Integer minimumSignificantDigits;
    private final String notation;
    private final String type;

    public JSIntlPluralRules(
            Locale locale,
            String type,
            String notation,
            int minimumIntegerDigits,
            int minimumFractionDigits,
            int maximumFractionDigits,
            Integer minimumSignificantDigits,
            Integer maximumSignificantDigits) {
        super();
        this.locale = locale;
        this.type = type;
        this.notation = notation;
        this.minimumIntegerDigits = minimumIntegerDigits;
        this.minimumFractionDigits = minimumFractionDigits;
        this.maximumFractionDigits = maximumFractionDigits;
        this.minimumSignificantDigits = minimumSignificantDigits;
        this.maximumSignificantDigits = maximumSignificantDigits;
    }

    private static boolean isInteger(double value) {
        return Double.isFinite(value) && Math.floor(value) == value;
    }

    private static boolean isNegativeZero(double value) {
        return Double.doubleToRawLongBits(value) == Double.doubleToRawLongBits(-0.0d);
    }

    private static double toAbsolute(double value) {
        if (isNegativeZero(value)) {
            return 0.0d;
        }
        return Math.abs(value);
    }

    private String applyRounding(double value) {
        BigDecimal decimal = BigDecimal.valueOf(toAbsolute(value));
        if (maximumSignificantDigits != null) {
            MathContext mathContext = new MathContext(Math.max(1, maximumSignificantDigits), RoundingMode.HALF_UP);
            decimal = decimal.round(mathContext);
        } else {
            decimal = decimal.setScale(Math.max(0, maximumFractionDigits), RoundingMode.HALF_UP);
        }
        return decimal.stripTrailingZeros().toPlainString();
    }

    private List<String> computePluralCategories() {
        if ("ordinal".equals(type)) {
            return CATEGORIES_ORDINAL;
        }
        String language = locale.getLanguage();
        if ("ar".equals(language)) {
            return CATEGORIES_AR;
        }
        if ("fa".equals(language)) {
            return CATEGORIES_FA;
        }
        if ("fr".equals(language)) {
            return CATEGORIES_FR;
        }
        if ("gv".equals(language)) {
            return CATEGORIES_GV;
        }
        if ("ko".equals(language)) {
            return CATEGORIES_KO;
        }
        if ("sl".equals(language)) {
            return CATEGORIES_SL;
        }
        return CATEGORIES_EN;
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
        return computePluralCategories();
    }

    public String getType() {
        return type;
    }

    public String select(double number) {
        if (!Double.isFinite(number)) {
            return "other";
        }
        if ("ordinal".equals(type)) {
            return selectOrdinal(number);
        }
        return selectCardinal(number);
    }

    private String selectArabic(double rounded) {
        if (!isInteger(rounded)) {
            return "other";
        }
        long integerValue = Math.abs((long) rounded);
        if (integerValue == 0) {
            return "zero";
        }
        if (integerValue == 1) {
            return "one";
        }
        if (integerValue == 2) {
            return "two";
        }
        long mod100 = integerValue % 100;
        if (mod100 >= 3 && mod100 <= 10) {
            return "few";
        }
        if (mod100 >= 11 && mod100 <= 99) {
            return "many";
        }
        return "other";
    }

    private String selectCardinal(double number) {
        String language = locale.getLanguage();
        double rounded = Double.parseDouble(applyRounding(number));
        if ("ko".equals(language)) {
            return "other";
        }
        if ("ar".equals(language)) {
            return selectArabic(rounded);
        }
        if ("fa".equals(language)) {
            if (isInteger(rounded) && (rounded == 0 || rounded == 1)) {
                return "one";
            }
            return "other";
        }
        if ("sl".equals(language)) {
            if (!isInteger(rounded)) {
                return "few";
            }
            long mod100 = Math.abs((long) rounded) % 100;
            if (mod100 == 1) {
                return "one";
            }
            if (mod100 == 2) {
                return "two";
            }
            if (mod100 == 3 || mod100 == 4) {
                return "few";
            }
            return "other";
        }
        if ("fr".equals(language)) {
            double absoluteValue = toAbsolute(number);
            if ("scientific".equals(notation) || "engineering".equals(notation)) {
                return "many";
            }
            if ("compact".equals(notation) && absoluteValue >= 1_000_000d) {
                return "many";
            }
            if ("standard".equals(notation) && isInteger(absoluteValue) && absoluteValue >= 1_000_000d
                    && ((long) absoluteValue) % 1_000_000L == 0L) {
                return "many";
            }
            if (isInteger(rounded) && (rounded == 0 || rounded == 1)) {
                return "one";
            }
            return "other";
        }
        if (isInteger(rounded) && rounded == 1) {
            return "one";
        }
        return "other";
    }

    private String selectOrdinal(double number) {
        String language = locale.getLanguage();
        if ("en".equals(language) && isInteger(number)) {
            long n = Math.abs((long) number);
            long mod10 = n % 10;
            long mod100 = n % 100;
            if (mod10 == 1 && mod100 != 11) {
                return "one";
            }
            if (mod10 == 2 && mod100 != 12) {
                return "two";
            }
            if (mod10 == 3 && mod100 != 13) {
                return "few";
            }
        }
        return "other";
    }

    public String selectRange(double start, double end) {
        if (Double.isNaN(start) || Double.isNaN(end)) {
            throw new IllegalArgumentException("NaN is not allowed");
        }
        return select(end);
    }
}
