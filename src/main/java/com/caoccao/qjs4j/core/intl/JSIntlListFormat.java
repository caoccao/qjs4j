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

package com.caoccao.qjs4j.core.intl;

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSObject;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Intl.ListFormat instance object. Uses CLDR-based patterns for locale-specific list formatting.
 */
public final class JSIntlListFormat extends JSObject {
    private static final ListPatterns COMMA = ListPatterns.uniform(", ");
    private static final ListPatterns EMPTY = ListPatterns.uniform("");

    private static final ListPatterns IDEOGRAPHIC_COMMA = ListPatterns.uniform("\u3001");
    public static final String NAME = "Intl.ListFormat";
    private static final Map<String, ListPatterns> PATTERNS = new HashMap<>();
    private static final ListPatterns SPACE = ListPatterns.uniform(" ");

    static {
        // English
        ListPatterns enDisjunction = new ListPatterns(" or ", ", ", ", ", ", or ");
        registerStyles("en|conjunction", new ListPatterns(" and ", ", ", ", ", ", and "),
                new ListPatterns(" & ", ", ", ", ", ", & "), COMMA);
        registerStyles("en|disjunction", enDisjunction, enDisjunction, enDisjunction);
        registerStyles("en|unit", COMMA, COMMA, SPACE);

        // Spanish
        ListPatterns esConjunction = new ListPatterns(" y ", ", ", ", ", " y ");
        ListPatterns esDisjunction = new ListPatterns(" o ", ", ", ", ", " o ");
        registerStyles("es|conjunction", esConjunction, esConjunction, COMMA);
        registerStyles("es|disjunction", esDisjunction, esDisjunction, esDisjunction);
        registerStyles("es|unit", esConjunction, new ListPatterns(" y ", ", ", ", ", ", "), SPACE);

        // German
        ListPatterns deConjunction = new ListPatterns(" und ", ", ", ", ", " und ");
        ListPatterns deDisjunction = new ListPatterns(" oder ", ", ", ", ", " oder ");
        registerStyles("de|conjunction", deConjunction, deConjunction, COMMA);
        registerStyles("de|disjunction", deDisjunction, deDisjunction, deDisjunction);
        registerStyles("de|unit", new ListPatterns(", ", ", ", ", ", " und "), COMMA, SPACE);

        // Japanese
        ListPatterns jaDisjunction = new ListPatterns("\u307E\u305F\u306F", "\u3001", "\u3001",
                "\u3001\u307E\u305F\u306F");
        registerStyles("ja|conjunction", IDEOGRAPHIC_COMMA, IDEOGRAPHIC_COMMA, IDEOGRAPHIC_COMMA);
        registerStyles("ja|disjunction", jaDisjunction, jaDisjunction, jaDisjunction);
        registerStyles("ja|unit", SPACE, SPACE, EMPTY);

        // Chinese
        ListPatterns zhConjunction = new ListPatterns("\u548C", "\u3001", "\u3001", "\u548C");
        ListPatterns zhDisjunction = new ListPatterns("\u6216", "\u3001", "\u3001", "\u6216");
        registerStyles("zh|conjunction", zhConjunction, zhConjunction, IDEOGRAPHIC_COMMA);
        registerStyles("zh|disjunction", zhDisjunction, zhDisjunction, zhDisjunction);
        registerStyles("zh|unit", EMPTY, EMPTY, EMPTY);

        // French
        ListPatterns frConjunction = new ListPatterns(" et ", ", ", ", ", " et ");
        ListPatterns frDisjunction = new ListPatterns(" ou ", ", ", ", ", " ou ");
        registerStyles("fr|conjunction", frConjunction, frConjunction, COMMA);
        registerStyles("fr|disjunction", frDisjunction, frDisjunction, frDisjunction);
        registerStyles("fr|unit", new ListPatterns(", ", ", ", ", ", " et "), COMMA, SPACE);
    }

    private final Locale locale;
    private final String style;
    private final String type;

    public JSIntlListFormat(JSContext context, Locale locale, String style, String type) {
        super(context);
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
        int count = values.size();
        StringBuilder result = new StringBuilder().append(values.get(0));
        for (int index = 1; index < count; index++) {
            result.append(patterns.separator(index, count)).append(values.get(index));
        }
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
        return COMMA;
    }

    public String getStyle() {
        return style;
    }

    public String getType() {
        return type;
    }

    private static void registerStyles(String key, ListPatterns longStyle, ListPatterns shortStyle,
            ListPatterns narrowStyle) {
        PATTERNS.put(key + "|long", longStyle);
        PATTERNS.put(key + "|short", shortStyle);
        PATTERNS.put(key + "|narrow", narrowStyle);
    }

    /**
     * CLDR list format patterns: pair, start, middle, end separators.
     */
    public record ListPatterns(String pairSep, String startSep, String middleSep, String endSep) {
        private static ListPatterns uniform(String separator) {
            return new ListPatterns(separator, separator, separator, separator);
        }

        String separator(int index, int count) {
            if (count == 2) {
                return pairSep;
            }
            if (index == 1) {
                return startSep;
            }
            return index == count - 1 ? endSep : middleSep;
        }
    }
}
