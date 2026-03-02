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

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Intl.ListFormat instance object.
 * Uses CLDR-based patterns for locale-specific list formatting.
 */
public final class JSIntlListFormat extends JSObject {
    public static final String NAME = "Intl.ListFormat";
    private static final Map<String, ListPatterns> PATTERNS = new HashMap<>();

    static {
        // English patterns
        PATTERNS.put("en|conjunction|long", new ListPatterns(" and ", ", ", ", ", ", and "));
        PATTERNS.put("en|conjunction|short", new ListPatterns(" & ", ", ", ", ", ", & "));
        PATTERNS.put("en|conjunction|narrow", new ListPatterns(", ", ", ", ", ", ", "));
        PATTERNS.put("en|disjunction|long", new ListPatterns(" or ", ", ", ", ", ", or "));
        PATTERNS.put("en|disjunction|short", new ListPatterns(" or ", ", ", ", ", ", or "));
        PATTERNS.put("en|disjunction|narrow", new ListPatterns(" or ", ", ", ", ", ", or "));
        PATTERNS.put("en|unit|long", new ListPatterns(", ", ", ", ", ", ", "));
        PATTERNS.put("en|unit|short", new ListPatterns(", ", ", ", ", ", ", "));
        PATTERNS.put("en|unit|narrow", new ListPatterns(" ", " ", " ", " "));

        // Spanish patterns
        PATTERNS.put("es|conjunction|long", new ListPatterns(" y ", ", ", ", ", " y "));
        PATTERNS.put("es|conjunction|short", new ListPatterns(" y ", ", ", ", ", " y "));
        PATTERNS.put("es|conjunction|narrow", new ListPatterns(", ", ", ", ", ", ", "));
        PATTERNS.put("es|disjunction|long", new ListPatterns(" o ", ", ", ", ", " o "));
        PATTERNS.put("es|disjunction|short", new ListPatterns(" o ", ", ", ", ", " o "));
        PATTERNS.put("es|disjunction|narrow", new ListPatterns(" o ", ", ", ", ", " o "));
        PATTERNS.put("es|unit|long", new ListPatterns(" y ", ", ", ", ", " y "));
        PATTERNS.put("es|unit|short", new ListPatterns(" y ", ", ", ", ", ", "));
        PATTERNS.put("es|unit|narrow", new ListPatterns(" ", " ", " ", " "));

        // German patterns
        PATTERNS.put("de|conjunction|long", new ListPatterns(" und ", ", ", ", ", " und "));
        PATTERNS.put("de|conjunction|short", new ListPatterns(" und ", ", ", ", ", " und "));
        PATTERNS.put("de|conjunction|narrow", new ListPatterns(", ", ", ", ", ", ", "));
        PATTERNS.put("de|disjunction|long", new ListPatterns(" oder ", ", ", ", ", " oder "));
        PATTERNS.put("de|disjunction|short", new ListPatterns(" oder ", ", ", ", ", " oder "));
        PATTERNS.put("de|disjunction|narrow", new ListPatterns(" oder ", ", ", ", ", " oder "));
        PATTERNS.put("de|unit|long", new ListPatterns(", ", ", ", ", ", " und "));
        PATTERNS.put("de|unit|short", new ListPatterns(", ", ", ", ", ", ", "));
        PATTERNS.put("de|unit|narrow", new ListPatterns(" ", " ", " ", " "));

        // Japanese patterns
        PATTERNS.put("ja|conjunction|long", new ListPatterns("\u3001", "\u3001", "\u3001", "\u3001"));
        PATTERNS.put("ja|conjunction|short", new ListPatterns("\u3001", "\u3001", "\u3001", "\u3001"));
        PATTERNS.put("ja|conjunction|narrow", new ListPatterns("\u3001", "\u3001", "\u3001", "\u3001"));
        PATTERNS.put("ja|disjunction|long", new ListPatterns("\u307E\u305F\u306F", "\u3001", "\u3001", "\u3001\u307E\u305F\u306F"));
        PATTERNS.put("ja|disjunction|short", new ListPatterns("\u307E\u305F\u306F", "\u3001", "\u3001", "\u3001\u307E\u305F\u306F"));
        PATTERNS.put("ja|disjunction|narrow", new ListPatterns("\u307E\u305F\u306F", "\u3001", "\u3001", "\u3001\u307E\u305F\u306F"));
        PATTERNS.put("ja|unit|long", new ListPatterns(" ", " ", " ", " "));
        PATTERNS.put("ja|unit|short", new ListPatterns(" ", " ", " ", " "));
        PATTERNS.put("ja|unit|narrow", new ListPatterns("", "", "", ""));

        // Chinese patterns
        PATTERNS.put("zh|conjunction|long", new ListPatterns("\u548C", "\u3001", "\u3001", "\u548C"));
        PATTERNS.put("zh|conjunction|short", new ListPatterns("\u548C", "\u3001", "\u3001", "\u548C"));
        PATTERNS.put("zh|conjunction|narrow", new ListPatterns("\u3001", "\u3001", "\u3001", "\u3001"));
        PATTERNS.put("zh|disjunction|long", new ListPatterns("\u6216", "\u3001", "\u3001", "\u6216"));
        PATTERNS.put("zh|disjunction|short", new ListPatterns("\u6216", "\u3001", "\u3001", "\u6216"));
        PATTERNS.put("zh|disjunction|narrow", new ListPatterns("\u6216", "\u3001", "\u3001", "\u6216"));
        PATTERNS.put("zh|unit|long", new ListPatterns("", "", "", ""));
        PATTERNS.put("zh|unit|short", new ListPatterns("", "", "", ""));
        PATTERNS.put("zh|unit|narrow", new ListPatterns("", "", "", ""));

        // French patterns
        PATTERNS.put("fr|conjunction|long", new ListPatterns(" et ", ", ", ", ", " et "));
        PATTERNS.put("fr|conjunction|short", new ListPatterns(" et ", ", ", ", ", " et "));
        PATTERNS.put("fr|conjunction|narrow", new ListPatterns(", ", ", ", ", ", ", "));
        PATTERNS.put("fr|disjunction|long", new ListPatterns(" ou ", ", ", ", ", " ou "));
        PATTERNS.put("fr|disjunction|short", new ListPatterns(" ou ", ", ", ", ", " ou "));
        PATTERNS.put("fr|disjunction|narrow", new ListPatterns(" ou ", ", ", ", ", " ou "));
        PATTERNS.put("fr|unit|long", new ListPatterns(", ", ", ", ", ", " et "));
        PATTERNS.put("fr|unit|short", new ListPatterns(", ", ", ", ", ", ", "));
        PATTERNS.put("fr|unit|narrow", new ListPatterns(" ", " ", " ", " "));
    }

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

        ListPatterns patterns = getPatterns();

        if (values.size() == 2) {
            return values.get(0) + patterns.pairSep() + values.get(1);
        }

        // 3+ items: use start/middle/end pattern
        int n = values.size();
        StringBuilder result = new StringBuilder();
        result.append(values.get(n - 2)).append(patterns.endSep()).append(values.get(n - 1));
        for (int i = n - 3; i >= 1; i--) {
            result.insert(0, values.get(i) + patterns.middleSep());
        }
        result.insert(0, values.get(0) + patterns.startSep());
        return result.toString();
    }

    public Locale getLocale() {
        return locale;
    }

    public ListPatterns getPatterns() {
        String language = locale.getLanguage();
        String key = language + "|" + type + "|" + style;
        ListPatterns patterns = PATTERNS.get(key);
        if (patterns != null) {
            return patterns;
        }
        // Fallback to English
        String fallbackKey = "en|" + type + "|" + style;
        patterns = PATTERNS.get(fallbackKey);
        if (patterns != null) {
            return patterns;
        }
        // Ultimate fallback
        return new ListPatterns(", ", ", ", ", ", ", ");
    }

    public String getStyle() {
        return style;
    }

    public String getType() {
        return type;
    }

    /**
     * CLDR list format patterns: pair, start, middle, end separators.
     */
    public record ListPatterns(String pairSep, String startSep, String middleSep, String endSep) {
    }
}
