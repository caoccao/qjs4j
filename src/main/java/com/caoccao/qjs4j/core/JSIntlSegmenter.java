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

import java.text.BreakIterator;
import java.util.Locale;

/**
 * Intl.Segmenter instance object.
 */
public final class JSIntlSegmenter extends JSObject {
    public static final String NAME = "Intl.Segmenter";
    private final String granularity;
    private final Locale locale;
    private final JSObject segmentsPrototype;

    public JSIntlSegmenter(Locale locale, String granularity, JSObject segmentsPrototype) {
        super();
        this.locale = locale;
        this.granularity = granularity;
        this.segmentsPrototype = segmentsPrototype;
    }

    public BreakIterator createBreakIterator() {
        return switch (granularity) {
            case "word" -> BreakIterator.getWordInstance(locale);
            case "sentence" -> BreakIterator.getSentenceInstance(locale);
            default -> BreakIterator.getCharacterInstance(locale);
        };
    }

    public JSIntlSegments createSegments(String input) {
        JSIntlSegments segments = new JSIntlSegments(this, input);
        if (segmentsPrototype != null) {
            segments.setPrototype(segmentsPrototype);
        }
        return segments;
    }

    public String getGranularity() {
        return granularity;
    }

    public Locale getLocale() {
        return locale;
    }

    public boolean isWordGranularity() {
        return "word".equals(granularity);
    }
}
