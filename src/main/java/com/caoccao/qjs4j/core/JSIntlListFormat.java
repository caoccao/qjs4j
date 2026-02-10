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

import java.util.List;
import java.util.Locale;

/**
 * Intl.ListFormat instance object.
 */
public final class JSIntlListFormat extends JSObject {
    private final Locale locale;
    private final String style;
    private final String type;

    public JSIntlListFormat(Locale locale, String style, String type) {
        super();
        this.locale = locale;
        this.style = style;
        this.type = type;
    }

    public String format(List<String> values) {
        if (values.isEmpty()) {
            return "";
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        String conjunction = switch (type) {
            case "disjunction" -> "or";
            case "unit" -> style.equals("narrow") ? "/" : "and";
            default -> style.equals("short") ? "&" : "and";
        };
        if (values.size() == 2) {
            return values.get(0) + " " + conjunction + " " + values.get(1);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size() - 1; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(values.get(i));
        }
        sb.append(", ").append(conjunction).append(" ").append(values.get(values.size() - 1));
        return sb.toString();
    }

    public Locale getLocale() {
        return locale;
    }

    public String getStyle() {
        return style;
    }

    public String getType() {
        return type;
    }
}
