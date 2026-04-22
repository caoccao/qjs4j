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
 * Internal unsigned rounding mode, obtained by applying the sign of a value
 * to a {@link TemporalRoundingMode}.
 * <p>
 * Mirrors Rust temporal_rs {@code UnsignedRoundingMode} (options.rs:791-804).
 */
public enum TemporalUnsignedRoundingMode {
    INFINITY("infinity"),
    ZERO("zero"),
    HALF_INFINITY("half-infinity"),
    HALF_ZERO("half-zero"),
    HALF_EVEN("half-even");

    private final String jsName;

    TemporalUnsignedRoundingMode(String jsName) {
        this.jsName = jsName;
    }

    /**
     * Parses a JS unsigned rounding mode string. Returns {@code null} if not recognized.
     */
    public static TemporalUnsignedRoundingMode fromString(String text) {
        if (text == null) {
            return null;
        }
        return switch (text) {
            case "infinity" -> INFINITY;
            case "zero" -> ZERO;
            case "half-infinity" -> HALF_INFINITY;
            case "half-zero" -> HALF_ZERO;
            case "half-even" -> HALF_EVEN;
            default -> null;
        };
    }

    public long getRoundedUnit(
            long roundingFloor,
            long roundingCeiling,
            int comparison,
            boolean evenCardinality) {
        return switch (this) {
            case ZERO -> roundingFloor;
            case INFINITY -> roundingCeiling;
            case HALF_ZERO -> {
                if (comparison <= 0) {
                    yield roundingFloor;
                } else {
                    yield roundingCeiling;
                }
            }
            case HALF_INFINITY -> {
                if (comparison < 0) {
                    yield roundingFloor;
                } else {
                    yield roundingCeiling;
                }
            }
            case HALF_EVEN -> {
                if (comparison < 0) {
                    yield roundingFloor;
                } else if (comparison > 0) {
                    yield roundingCeiling;
                } else if (evenCardinality) {
                    yield roundingFloor;
                } else {
                    yield roundingCeiling;
                }
            }
        };
    }

    /**
     * Returns the JS-canonical name (e.g. "half-infinity").
     */
    public String jsName() {
        return jsName;
    }
}
