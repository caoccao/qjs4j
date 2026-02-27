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

import java.text.Collator;
import java.util.Locale;

/**
 * Intl.Collator instance object.
 */
public final class JSIntlCollator extends JSObject {
    public static final String NAME = "Intl.Collator";
    private final String caseFirst;
    private final String collation;
    private final Collator collator;
    private final boolean ignorePunctuation;
    private final Locale locale;
    private final boolean numeric;
    private final String sensitivity;
    private final String usage;
    private JSFunction boundCompareFunction;

    public JSIntlCollator(Locale locale, String sensitivity, String usage, String collation,
                          boolean numeric, String caseFirst, boolean ignorePunctuation) {
        super();
        this.locale = locale;
        this.sensitivity = sensitivity;
        this.usage = usage;
        this.collation = collation;
        this.numeric = numeric;
        this.caseFirst = caseFirst;
        this.ignorePunctuation = ignorePunctuation;
        this.collator = Collator.getInstance(locale);
        switch (sensitivity) {
            case "base" -> collator.setStrength(Collator.PRIMARY);
            case "accent" -> collator.setStrength(Collator.SECONDARY);
            case "case" -> collator.setStrength(Collator.TERTIARY);
            case "variant" -> collator.setStrength(Collator.IDENTICAL);
            default -> collator.setStrength(Collator.TERTIARY);
        }
        // Enable decomposition for canonical equivalence
        collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
    }

    public int compare(String left, String right) {
        return Integer.signum(collator.compare(left, right));
    }

    public JSFunction getBoundCompareFunction() {
        return boundCompareFunction;
    }

    public String getCaseFirst() {
        return caseFirst;
    }

    public String getCollation() {
        return collation;
    }

    public boolean getIgnorePunctuation() {
        return ignorePunctuation;
    }

    public Locale getLocale() {
        return locale;
    }

    public boolean getNumeric() {
        return numeric;
    }

    public String getSensitivity() {
        return sensitivity;
    }

    public String getUsage() {
        return usage;
    }

    public void setBoundCompareFunction(JSFunction boundCompareFunction) {
        this.boundCompareFunction = boundCompareFunction;
    }
}
