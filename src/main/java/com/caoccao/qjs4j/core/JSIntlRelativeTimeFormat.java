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

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Intl.RelativeTimeFormat instance object.
 */
public final class JSIntlRelativeTimeFormat extends JSObject {
    public static final String NAME = "Intl.RelativeTimeFormat";
    private final Locale locale;
    private final String numeric;
    private final String style;

    public JSIntlRelativeTimeFormat(Locale locale, String style, String numeric) {
        super();
        this.locale = locale;
        this.style = style;
        this.numeric = numeric;
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
        if (!Double.isFinite(value)) {
            return "infinite";
        }
        if ("auto".equals(numeric) && value == 0) {
            return "this " + normalizedUnit;
        }
        NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
        String absValue = numberFormat.format(Math.abs(value));
        String unitText = Math.abs(value) == 1 ? normalizedUnit : normalizedUnit + "s";
        if (value < 0) {
            return absValue + " " + unitText + " ago";
        }
        return "in " + absValue + " " + unitText;
    }

    public Locale getLocale() {
        return locale;
    }

    public String getNumeric() {
        return numeric;
    }

    public String getStyle() {
        return style;
    }
}
