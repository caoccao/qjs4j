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

/**
 * Intl.PluralRules instance object.
 */
public final class JSIntlPluralRules extends JSObject {
    private final Locale locale;
    private final String type;

    public JSIntlPluralRules(Locale locale, String type) {
        super();
        this.locale = locale;
        this.type = type;
    }

    private static boolean isInteger(double value) {
        return Double.isFinite(value) && Math.floor(value) == value;
    }

    public Locale getLocale() {
        return locale;
    }

    public String getType() {
        return type;
    }

    public String select(double number) {
        if (!Double.isFinite(number)) {
            return "other";
        }
        String language = locale.getLanguage();
        if ("ordinal".equals(type)) {
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
        if ("fr".equals(language)) {
            return isInteger(number) && number >= 0 && number < 2 ? "one" : "other";
        }
        return number == 1 ? "one" : "other";
    }
}
