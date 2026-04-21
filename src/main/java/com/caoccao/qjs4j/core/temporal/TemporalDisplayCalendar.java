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

package com.caoccao.qjs4j.core.temporal;

/**
 * Temporal calendarName display option enum (auto / always / never / critical).
 * <p>
 * Controls how a calendar annotation is rendered in {@code toString()} output.
 */
public enum TemporalDisplayCalendar {
    AUTO("auto"),
    ALWAYS("always"),
    NEVER("never"),
    CRITICAL("critical");

    private final String jsName;

    TemporalDisplayCalendar(String jsName) {
        this.jsName = jsName;
    }

    /**
     * Parses a JS calendarName string. Returns {@code null} if not recognized.
     */
    public static TemporalDisplayCalendar fromString(String text) {
        if (text == null) {
            return null;
        }
        return switch (text) {
            case "auto" -> AUTO;
            case "always" -> ALWAYS;
            case "never" -> NEVER;
            case "critical" -> CRITICAL;
            default -> null;
        };
    }

    /**
     * Returns the JS-canonical name (e.g. "auto").
     */
    public String jsName() {
        return jsName;
    }

    public boolean requiresAnnotation() {
        return this == ALWAYS || this == CRITICAL;
    }
}
