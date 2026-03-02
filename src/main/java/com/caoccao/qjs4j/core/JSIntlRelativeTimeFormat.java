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

import java.util.Locale;
import java.util.Map;

/**
 * Intl.RelativeTimeFormat instance object.
 */
public final class JSIntlRelativeTimeFormat extends JSObject {
    public static final String NAME = "Intl.RelativeTimeFormat";
    private static final Map<String, String> EN_AUTO_MINUS_ONE = Map.of(
            "year", "last year",
            "quarter", "last quarter",
            "month", "last month",
            "week", "last week",
            "day", "yesterday");
    private static final Map<String, String> EN_AUTO_ONE = Map.of(
            "year", "next year",
            "quarter", "next quarter",
            "month", "next month",
            "week", "next week",
            "day", "tomorrow");
    private static final Map<String, String> EN_AUTO_ZERO = Map.of(
            "year", "this year",
            "quarter", "this quarter",
            "month", "this month",
            "week", "this week",
            "day", "today",
            "hour", "this hour",
            "minute", "this minute",
            "second", "now");
    private static final Map<String, String> EN_LONG_PLURAL = Map.of(
            "second", "seconds",
            "minute", "minutes",
            "hour", "hours",
            "day", "days",
            "week", "weeks",
            "month", "months",
            "quarter", "quarters",
            "year", "years");
    private static final Map<String, String> EN_NARROW_ONE = Map.of(
            "second", "s",
            "minute", "m",
            "hour", "h",
            "day", "d",
            "week", "w",
            "month", "mo",
            "quarter", "q",
            "year", "y");
    private static final Map<String, String> EN_NARROW_MANY = EN_NARROW_ONE;
    private static final Map<String, String> EN_SHORT_MANY = Map.of(
            "second", "sec.",
            "minute", "min.",
            "hour", "hr.",
            "day", "days",
            "week", "wk.",
            "month", "mo.",
            "quarter", "qtrs.",
            "year", "yr.");
    private static final Map<String, String> EN_SHORT_ONE = Map.of(
            "second", "sec.",
            "minute", "min.",
            "hour", "hr.",
            "day", "day",
            "week", "wk.",
            "month", "mo.",
            "quarter", "qtr.",
            "year", "yr.");
    private static final Map<String, String> PL_LONG_FEW = Map.of(
            "second", "sekundy",
            "minute", "minuty",
            "hour", "godziny",
            "day", "dni",
            "week", "tygodnie",
            "month", "miesiące",
            "quarter", "kwartały",
            "year", "lata");
    private static final Map<String, String> PL_LONG_MANY = Map.of(
            "second", "sekund",
            "minute", "minut",
            "hour", "godzin",
            "day", "dni",
            "week", "tygodni",
            "month", "miesięcy",
            "quarter", "kwartałów",
            "year", "lat");
    private static final Map<String, String> PL_LONG_ONE = Map.of(
            "second", "sekundę",
            "minute", "minutę",
            "hour", "godzinę",
            "day", "dzień",
            "week", "tydzień",
            "month", "miesiąc",
            "quarter", "kwartał",
            "year", "rok");
    private static final Map<String, String> PL_NARROW_FEW = Map.of(
            "second", "s",
            "minute", "min",
            "hour", "g.",
            "day", "dni",
            "week", "tyg.",
            "month", "mies.",
            "quarter", "kw.",
            "year", "lata");
    private static final Map<String, String> PL_NARROW_MANY = Map.of(
            "second", "s",
            "minute", "min",
            "hour", "g.",
            "day", "dni",
            "week", "tyg.",
            "month", "mies.",
            "quarter", "kw.",
            "year", "lat");
    private static final Map<String, String> PL_NARROW_ONE = Map.of(
            "second", "s",
            "minute", "min",
            "hour", "g.",
            "day", "dzień",
            "week", "tydz.",
            "month", "mies.",
            "quarter", "kw.",
            "year", "rok");
    private static final Map<String, String> PL_SHORT_FEW = Map.of(
            "second", "sek.",
            "minute", "min",
            "hour", "godz.",
            "day", "dni",
            "week", "tyg.",
            "month", "mies.",
            "quarter", "kw.",
            "year", "lata");
    private static final Map<String, String> PL_SHORT_MANY = Map.of(
            "second", "sek.",
            "minute", "min",
            "hour", "godz.",
            "day", "dni",
            "week", "tyg.",
            "month", "mies.",
            "quarter", "kw.",
            "year", "lat");
    private static final Map<String, String> PL_SHORT_ONE = Map.of(
            "second", "sek.",
            "minute", "min",
            "hour", "godz.",
            "day", "dzień",
            "week", "tydz.",
            "month", "mies.",
            "quarter", "kw.",
            "year", "rok");
    private final Locale locale;
    private final String numberingSystem;
    private final String numeric;
    private final String style;

    public JSIntlRelativeTimeFormat(Locale locale, String style, String numeric, String numberingSystem) {
        super();
        this.locale = locale;
        this.style = style;
        this.numeric = numeric;
        this.numberingSystem = numberingSystem != null ? numberingSystem : "latn";
    }

    private static boolean isInteger(double value) {
        return Double.isFinite(value) && Math.floor(value) == value;
    }

    private static boolean isNegativeZero(double value) {
        return Double.doubleToRawLongBits(value) == Double.doubleToRawLongBits(-0.0d);
    }

    private static String normalizeUnit(String unit) {
        return switch (unit) {
            case "seconds", "second" -> "second";
            case "minutes", "minute" -> "minute";
            case "hours", "hour" -> "hour";
            case "days", "day" -> "day";
            case "weeks", "week" -> "week";
            case "months", "month" -> "month";
            case "quarters", "quarter" -> "quarter";
            case "years", "year" -> "year";
            default -> throw new IllegalArgumentException("Invalid unit: " + unit);
        };
    }

    public String format(double value, String unit) {
        String normalizedUnit = normalizeUnit(unit);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Invalid value");
        }

        boolean isPast = value < 0 || isNegativeZero(value);
        double absoluteValue = Math.abs(value);
        String numberText = formatNumber(absoluteValue);

        String language = locale.getLanguage();
        if ("pl".equals(language)) {
            return formatPolish(value, normalizedUnit, numberText, isPast);
        }
        if ("en".equals(language)) {
            return formatEnglish(value, normalizedUnit, numberText, isPast);
        }
        if (isPast) {
            return numberText + " " + normalizedUnit + " ago";
        }
        return "in " + numberText + " " + normalizedUnit;
    }

    private String formatEnglish(double value, String normalizedUnit, String numberText, boolean isPast) {
        if ("auto".equals(numeric)) {
            if (value == -1 && EN_AUTO_MINUS_ONE.containsKey(normalizedUnit)) {
                return EN_AUTO_MINUS_ONE.get(normalizedUnit);
            }
            if (value == 0) {
                return EN_AUTO_ZERO.getOrDefault(normalizedUnit, "in 0 " + EN_LONG_PLURAL.get(normalizedUnit));
            }
            if (value == 1 && EN_AUTO_ONE.containsKey(normalizedUnit)) {
                return EN_AUTO_ONE.get(normalizedUnit);
            }
        }

        boolean singular = Math.abs(value) == 1.0d;
        String unitText;
        if ("short".equals(style)) {
            unitText = singular ? EN_SHORT_ONE.get(normalizedUnit) : EN_SHORT_MANY.get(normalizedUnit);
        } else if ("narrow".equals(style)) {
            unitText = singular ? EN_NARROW_ONE.get(normalizedUnit) : EN_NARROW_MANY.get(normalizedUnit);
        } else {
            unitText = singular ? normalizedUnit : EN_LONG_PLURAL.get(normalizedUnit);
        }
        if (isPast) {
            return numberText + " " + unitText + " ago";
        }
        return "in " + numberText + " " + unitText;
    }

    private String formatNumber(double absoluteValue) {
        String useGroupingMode = locale.getLanguage().equals("pl") ? "false" : "auto";
        JSIntlNumberFormat numberFormat = new JSIntlNumberFormat(
                locale,
                "decimal",
                null,
                useGroupingMode,
                1,
                0,
                3,
                false,
                0,
                null,
                null,
                "auto",
                "halfExpand",
                numberingSystem);
        return numberFormat.format(absoluteValue);
    }

    private String formatPolish(double value, String normalizedUnit, String numberText, boolean isPast) {
        String pluralCategory = selectPolishPluralCategory(Math.abs(value));
        Map<String, String> oneMap;
        Map<String, String> fewMap;
        Map<String, String> manyMap;
        if ("short".equals(style)) {
            oneMap = PL_SHORT_ONE;
            fewMap = PL_SHORT_FEW;
            manyMap = PL_SHORT_MANY;
        } else if ("narrow".equals(style)) {
            oneMap = PL_NARROW_ONE;
            fewMap = PL_NARROW_FEW;
            manyMap = PL_NARROW_MANY;
        } else {
            oneMap = PL_LONG_ONE;
            fewMap = PL_LONG_FEW;
            manyMap = PL_LONG_MANY;
        }
        String unitText;
        if ("one".equals(pluralCategory)) {
            unitText = oneMap.get(normalizedUnit);
        } else if ("few".equals(pluralCategory)) {
            unitText = fewMap.get(normalizedUnit);
        } else {
            unitText = manyMap.get(normalizedUnit);
        }
        if (isPast) {
            return numberText + " " + unitText + " temu";
        }
        return "za " + numberText + " " + unitText;
    }

    public Locale getLocale() {
        return locale;
    }

    public String getNumberingSystem() {
        return numberingSystem;
    }

    public String getNumeric() {
        return numeric;
    }

    public String getStyle() {
        return style;
    }

    private String selectPolishPluralCategory(double absoluteValue) {
        if (!isInteger(absoluteValue)) {
            return "many";
        }
        long integerValue = (long) absoluteValue;
        if (integerValue == 1) {
            return "one";
        }
        long mod10 = integerValue % 10;
        long mod100 = integerValue % 100;
        if (mod10 >= 2 && mod10 <= 4 && !(mod100 >= 12 && mod100 <= 14)) {
            return "few";
        }
        return "many";
    }
}
