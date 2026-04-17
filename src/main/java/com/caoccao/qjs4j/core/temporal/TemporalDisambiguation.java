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
 * Temporal disambiguation mode enum for resolving ambiguous or invalid local times
 * during timezone conversions.
 */
public enum TemporalDisambiguation {
    COMPATIBLE("compatible"),
    EARLIER("earlier"),
    LATER("later"),
    REJECT("reject");

    private final String jsName;

    TemporalDisambiguation(String jsName) {
        this.jsName = jsName;
    }

    /**
     * Parses a JS disambiguation string. Returns {@code null} if not recognized.
     */
    public static TemporalDisambiguation fromString(String text) {
        if (text == null) {
            return null;
        }
        return switch (text) {
            case "compatible" -> COMPATIBLE;
            case "earlier" -> EARLIER;
            case "later" -> LATER;
            case "reject" -> REJECT;
            default -> null;
        };
    }

    /**
     * Checks whether the given string is a valid disambiguation mode.
     */
    public static boolean isValid(String text) {
        return fromString(text) != null;
    }

    /**
     * Returns true if the given disambiguation string matches "reject".
     */
    public static boolean isReject(String text) {
        return "reject".equals(text);
    }

    /**
     * Returns true if the given disambiguation string matches "earlier".
     */
    public static boolean isEarlier(String text) {
        return "earlier".equals(text);
    }

    /**
     * Returns true if the given disambiguation string matches "later".
     */
    public static boolean isLater(String text) {
        return "later".equals(text);
    }

    /**
     * Returns the JS-canonical name (e.g. "compatible").
     */
    public String jsName() {
        return jsName;
    }
}
