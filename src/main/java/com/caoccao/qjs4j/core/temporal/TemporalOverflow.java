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
 * Temporal overflow handling mode enum.
 * <p>
 * Replaces scattered {@code "reject".equals(overflow)} and
 * {@code "constrain".equals(overflow)} string comparisons.
 */
public enum TemporalOverflow {
    CONSTRAIN("constrain"),
    REJECT("reject");

    private final String jsName;

    TemporalOverflow(String jsName) {
        this.jsName = jsName;
    }

    /**
     * Parses a JS overflow string. Returns {@code null} if not recognized.
     */
    public static TemporalOverflow fromString(String text) {
        if (text == null) {
            return null;
        }
        return switch (text) {
            case "constrain" -> CONSTRAIN;
            case "reject" -> REJECT;
            default -> null;
        };
    }

    /**
     * Returns the JS-canonical name (e.g. "constrain").
     */
    public String jsName() {
        return jsName;
    }

    public boolean matches(String text) {
        return jsName.equals(text);
    }
}
