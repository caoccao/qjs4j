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
    private final Collator collator;
    private final Locale locale;
    private final String sensitivity;

    public JSIntlCollator(Locale locale, String sensitivity) {
        super();
        this.locale = locale;
        this.sensitivity = sensitivity;
        this.collator = Collator.getInstance(locale);
        switch (sensitivity) {
            case "base" -> collator.setStrength(Collator.PRIMARY);
            case "accent" -> collator.setStrength(Collator.SECONDARY);
            case "case" -> collator.setStrength(Collator.TERTIARY);
            case "variant" -> collator.setStrength(Collator.IDENTICAL);
            default -> collator.setStrength(Collator.TERTIARY);
        }
    }

    public int compare(String left, String right) {
        return Integer.signum(collator.compare(left, right));
    }

    public Locale getLocale() {
        return locale;
    }

    public String getSensitivity() {
        return sensitivity;
    }
}
