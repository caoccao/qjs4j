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
 * Getters derive from the canonical {@code tag} string per ECMA-402 spec.
 */
public final class JSIntlLocale extends JSObject {
    public static final String NAME = "Intl.Locale";
    private final String calendar;
    private final String caseFirst;
    private final String collation;
    private final String firstDayOfWeek;
    private final String hourCycle;
    private final Locale locale;
    private final String numberingSystem;
    private final boolean numeric;
    private final boolean numericSet;
    private final String tag;

    public JSIntlLocale(JSContext context, Locale locale, String tag) {
        this(context, locale, tag, null, null, null, null, null, null, false, false);
    }

    public JSIntlLocale(JSContext context, Locale locale, String tag, String calendar, String caseFirst,
                        String collation, String hourCycle, String numberingSystem,
                        boolean numeric, boolean numericSet) {
        this(context, locale, tag, calendar, caseFirst, collation, null, hourCycle, numberingSystem, numeric, numericSet);
    }

    public JSIntlLocale(JSContext context, Locale locale, String tag, String calendar, String caseFirst,
                        String collation, String firstDayOfWeek, String hourCycle,
                        String numberingSystem, boolean numeric, boolean numericSet) {
        super(context);
        this.locale = locale;
        this.tag = tag;
        this.calendar = calendar;
        this.caseFirst = caseFirst;
        this.collation = collation;
        this.firstDayOfWeek = firstDayOfWeek;
        this.hourCycle = hourCycle;
        this.numberingSystem = numberingSystem;
        this.numeric = numeric;
        this.numericSet = numericSet;
    }

    /**
     * Per spec: GetLocaleBaseName returns the longest prefix of the locale tag
     * matched by unicode_language_id (everything before the first singleton extension).
     */
    public String getBaseName() {
        String[] parts = tag.split("-");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.length() == 1) {
                break; // singleton extension
            }
            if (!sb.isEmpty()) {
                sb.append("-");
            }
            sb.append(part);
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

    public String getFirstDayOfWeek() {
        return firstDayOfWeek;
    }

    public String getHourCycle() {
        return hourCycle;
    }

    /**
     * Per spec: GetLocaleLanguage returns the first subtag of baseName.
     */
    public String getLanguage() {
        String[] parts = tag.split("-", 2);
        return parts[0];
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

    /**
     * Per spec: GetLocaleRegion returns the region subtag from baseName, or empty string.
     * Region is 2 alpha or 3 digit, appearing after language and optional script.
     */
    public String getRegion() {
        String baseName = getBaseName();
        String[] parts = baseName.split("-");
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            if ((p.length() == 2 && p.chars().allMatch(Character::isLetter)) ||
                    (p.length() == 3 && p.chars().allMatch(Character::isDigit))) {
                return p;
            }
        }
        return "";
    }

    /**
     * Per spec: GetLocaleScript returns the script subtag from baseName, or empty string.
     * Script is exactly 4 alpha letters.
     */
    public String getScript() {
        String baseName = getBaseName();
        String[] parts = baseName.split("-");
        if (parts.length >= 2 && parts[1].length() == 4 && parts[1].chars().allMatch(Character::isLetter)) {
            return parts[1];
        }
        return "";
    }

    public String getTag() {
        return tag;
    }

    /**
     * Per spec: GetLocaleVariants returns the longest suffix of baseName starting
     * with a variant subtag (5-8 alphanum or digit+3alphanum), or empty string.
     */
    public String getVariant() {
        String baseName = getBaseName();
        String[] parts = baseName.split("-");
        int variantStart = -1;
        for (int i = parts.length - 1; i >= 1; i--) {
            String p = parts[i];
            boolean isVariant = (p.length() >= 5 && p.length() <= 8) ||
                    (p.length() == 4 && Character.isDigit(p.charAt(0)));
            if (isVariant) {
                variantStart = i;
            } else {
                break;
            }
        }
        if (variantStart < 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = variantStart; i < parts.length; i++) {
            if (!sb.isEmpty()) {
                sb.append("-");
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    public boolean isNumericSet() {
        return numericSet;
    }
}
