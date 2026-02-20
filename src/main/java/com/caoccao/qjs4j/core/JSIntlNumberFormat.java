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
import java.util.Currency;
import java.util.Locale;

/**
 * Intl.NumberFormat instance object.
 */
public final class JSIntlNumberFormat extends JSObject {
    public static final String NAME = "Intl.NumberFormat";
    private final String currency;
    private final Locale locale;
    private final String style;

    public JSIntlNumberFormat(Locale locale, String style, String currency) {
        super();
        this.locale = locale;
        this.style = style;
        this.currency = currency;
    }

    public String format(double value) {
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
        return format.format(value);
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
