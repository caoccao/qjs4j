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
 * Temporal rounding mode enum.
 * <p>
 * Replaces duplicated {@code negateRoundingMode()}, {@code isValidRoundingMode()},
 * and {@code isValidDifferenceRoundingMode()} methods across temporal prototype files.
 */
public enum TemporalRoundingMode {
    CEIL("ceil"),
    FLOOR("floor"),
    TRUNC("trunc"),
    EXPAND("expand"),
    HALF_EXPAND("halfExpand"),
    HALF_TRUNC("halfTrunc"),
    HALF_EVEN("halfEven"),
    HALF_CEIL("halfCeil"),
    HALF_FLOOR("halfFloor");

    private final String jsName;

    TemporalRoundingMode(String jsName) {
        this.jsName = jsName;
    }

    /**
     * Parses a JS rounding mode string. Returns {@code null} if not recognized.
     */
    public static TemporalRoundingMode fromString(String text) {
        if (text == null) {
            return null;
        }
        return switch (text) {
            case "ceil" -> CEIL;
            case "floor" -> FLOOR;
            case "trunc" -> TRUNC;
            case "expand" -> EXPAND;
            case "halfExpand" -> HALF_EXPAND;
            case "halfTrunc" -> HALF_TRUNC;
            case "halfEven" -> HALF_EVEN;
            case "halfCeil" -> HALF_CEIL;
            case "halfFloor" -> HALF_FLOOR;
            default -> null;
        };
    }

    /**
     * Checks whether the given string is a valid rounding mode.
     */
    public static boolean isValid(String text) {
        return fromString(text) != null;
    }

    /**
     * Returns the JS-canonical name (e.g. "halfExpand").
     */
    public String jsName() {
        return jsName;
    }

    /**
     * Returns the negated rounding mode, used for .since() operations.
     * ceil ↔ floor, halfCeil ↔ halfFloor, others unchanged.
     */
    public TemporalRoundingMode negate() {
        return switch (this) {
            case CEIL -> FLOOR;
            case FLOOR -> CEIL;
            case HALF_CEIL -> HALF_FLOOR;
            case HALF_FLOOR -> HALF_CEIL;
            default -> this;
        };
    }

    public TemporalUnsignedRoundingMode toUnsigned(boolean negativeSign) {
        return switch (this) {
            case CEIL -> negativeSign ? TemporalUnsignedRoundingMode.ZERO : TemporalUnsignedRoundingMode.INFINITY;
            case FLOOR -> negativeSign ? TemporalUnsignedRoundingMode.INFINITY : TemporalUnsignedRoundingMode.ZERO;
            case EXPAND -> TemporalUnsignedRoundingMode.INFINITY;
            case TRUNC -> TemporalUnsignedRoundingMode.ZERO;
            case HALF_CEIL ->
                    negativeSign ? TemporalUnsignedRoundingMode.HALF_ZERO : TemporalUnsignedRoundingMode.HALF_INFINITY;
            case HALF_FLOOR ->
                    negativeSign ? TemporalUnsignedRoundingMode.HALF_INFINITY : TemporalUnsignedRoundingMode.HALF_ZERO;
            case HALF_EXPAND -> TemporalUnsignedRoundingMode.HALF_INFINITY;
            case HALF_TRUNC -> TemporalUnsignedRoundingMode.HALF_ZERO;
            default -> TemporalUnsignedRoundingMode.HALF_EVEN;
        };
    }
}
