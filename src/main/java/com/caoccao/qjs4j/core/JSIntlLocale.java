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
    private final Locale locale;
    private final String tag;

    public JSIntlLocale(Locale locale, String tag) {
        super();
        this.locale = locale;
        this.tag = tag;
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
        return sb.toString();
    }

    public String getLanguage() {
        return locale.getLanguage();
    }

    public Locale getLocale() {
        return locale;
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
}
