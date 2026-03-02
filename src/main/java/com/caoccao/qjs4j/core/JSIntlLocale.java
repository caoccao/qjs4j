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
 * Intl.Locale instance object.
 */
public final class JSIntlLocale extends JSObject {
    public static final String NAME = "Intl.Locale";
    private final String calendar;
    private final String caseFirst;
    private final String collation;
    private final String hourCycle;
    private final Locale locale;
    private final String numberingSystem;
    private final boolean numeric;
    private final boolean numericSet;
    private final String tag;

    public JSIntlLocale(Locale locale, String tag) {
        this(locale, tag, null, null, null, null, null, false, false);
    }

    public JSIntlLocale(Locale locale, String tag, String calendar, String caseFirst,
                        String collation, String hourCycle, String numberingSystem,
                        boolean numeric, boolean numericSet) {
        super();
        this.locale = locale;
        this.tag = tag;
        this.calendar = calendar;
        this.caseFirst = caseFirst;
        this.collation = collation;
        this.hourCycle = hourCycle;
        this.numberingSystem = numberingSystem;
        this.numeric = numeric;
        this.numericSet = numericSet;
    }

    public String getBaseName() {
        Locale stripped = locale.stripExtensions();
        StringBuilder sb = new StringBuilder(stripped.getLanguage());
        if (!stripped.getScript().isEmpty()) {
            sb.append("-").append(stripped.getScript());
        }
        if (!stripped.getCountry().isEmpty()) {
            sb.append("-").append(stripped.getCountry());
        }
        String variant = stripped.getVariant();
        if (variant != null && !variant.isEmpty()) {
            // Java uses underscore for multiple variants; convert to BCP 47 hyphen
            sb.append("-").append(variant.replace('_', '-'));
        }
        return sb.toString();
    }

    public String getCalendar() {
        return calendar;
    }

    public String getCaseFirst() {
        return caseFirst;
    }

    public String getCollation() {
        return collation;
    }

    public String getHourCycle() {
        return hourCycle;
    }

    public String getLanguage() {
        return locale.getLanguage();
    }

    public Locale getLocale() {
        return locale;
    }

    public String getNumberingSystem() {
        return numberingSystem;
    }

    public boolean getNumeric() {
        return numeric;
    }

    public String getRegion() {
        return locale.getCountry();
    }

    public String getScript() {
        return locale.getScript();
    }

    public String getTag() {
        return tag;
    }

    public String getVariant() {
        return locale.getVariant();
    }

    public boolean isNumericSet() {
        return numericSet;
    }
}
