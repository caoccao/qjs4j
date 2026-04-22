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

import java.math.BigInteger;

/**
 * Temporal rounding mode enum.
 * <p>
 * Replaces duplicated {@code negateRoundingMode()}, {@code isValidRoundingMode()},
 * and {@code isValidDifferenceRoundingMode()} methods across temporal prototype files.
 */
public enum TemporalRoundingMode {
    CEIL,
    FLOOR,
    TRUNC,
    EXPAND,
    HALF_EXPAND,
    HALF_TRUNC,
    HALF_EVEN,
    HALF_CEIL,
    HALF_FLOOR;

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

    public BigInteger roundBigIntegerToIncrementAsIfPositive(BigInteger quantity, BigInteger increment) {
        if (increment.signum() == 0) {
            return quantity;
        }
        BigInteger[] quotientAndRemainder = quantity.divideAndRemainder(increment);
        BigInteger floorQuotient = quotientAndRemainder[0];
        BigInteger remainder = quotientAndRemainder[1];
        if (remainder.signum() < 0) {
            floorQuotient = floorQuotient.subtract(BigInteger.ONE);
            remainder = remainder.add(increment);
        }
        BigInteger lower = floorQuotient.multiply(increment);
        if (remainder.signum() == 0) {
            return lower;
        }
        BigInteger upper = lower.add(increment);

        return switch (this) {
            case CEIL, EXPAND -> upper;
            case FLOOR, TRUNC -> lower;
            case HALF_EXPAND, HALF_CEIL -> remainder.shiftLeft(1).compareTo(increment) >= 0 ? upper : lower;
            case HALF_TRUNC, HALF_FLOOR -> remainder.shiftLeft(1).compareTo(increment) > 0 ? upper : lower;
            case HALF_EVEN -> {
                BigInteger doubledRemainder = remainder.shiftLeft(1);
                int halfComparison = doubledRemainder.compareTo(increment);
                if (halfComparison < 0) {
                    yield lower;
                } else if (halfComparison > 0) {
                    yield upper;
                } else if (floorQuotient.testBit(0)) {
                    yield upper;
                } else {
                    yield lower;
                }
            }
        };
    }

    public BigInteger roundBigIntegerToIncrementSigned(BigInteger value, BigInteger increment) {
        if (increment.signum() == 0) {
            return value;
        }
        BigInteger[] quotientAndRemainder = value.divideAndRemainder(increment);
        BigInteger floorQuotient = quotientAndRemainder[0];
        BigInteger remainder = quotientAndRemainder[1];
        if (remainder.signum() < 0) {
            floorQuotient = floorQuotient.subtract(BigInteger.ONE);
            remainder = remainder.add(increment);
        }
        BigInteger floorValue = floorQuotient.multiply(increment);
        if (remainder.signum() == 0) {
            return floorValue;
        }
        BigInteger ceilValue = floorValue.add(increment);
        int sign = value.signum();

        return switch (this) {
            case FLOOR -> floorValue;
            case CEIL -> ceilValue;
            case TRUNC -> sign < 0 ? ceilValue : floorValue;
            case EXPAND -> sign < 0 ? floorValue : ceilValue;
            case HALF_EXPAND, HALF_TRUNC, HALF_EVEN, HALF_CEIL, HALF_FLOOR -> {
                BigInteger doubledRemainder = remainder.shiftLeft(1);
                int halfComparison = doubledRemainder.compareTo(increment);
                if (halfComparison < 0) {
                    yield floorValue;
                } else if (halfComparison > 0) {
                    yield ceilValue;
                } else {
                    yield switch (this) {
                        case HALF_EXPAND -> sign < 0 ? floorValue : ceilValue;
                        case HALF_TRUNC -> sign < 0 ? ceilValue : floorValue;
                        case HALF_EVEN -> floorQuotient.testBit(0) ? ceilValue : floorValue;
                        case HALF_CEIL -> ceilValue;
                        case HALF_FLOOR -> floorValue;
                        default -> ceilValue;
                    };
                }
            }
        };
    }

    public long roundLongToIncrementAsIfPositive(long quantity, long increment) {
        long quotient = Math.floorDiv(quantity, increment);
        long lower = quotient * increment;
        long remainder = quantity - lower;
        if (remainder == 0L) {
            return quantity;
        }
        long upper = lower + increment;
        return switch (this) {
            case CEIL, EXPAND -> upper;
            case FLOOR, TRUNC -> lower;
            case HALF_EXPAND, HALF_CEIL -> remainder * 2L >= increment ? upper : lower;
            case HALF_TRUNC, HALF_FLOOR -> remainder * 2L > increment ? upper : lower;
            case HALF_EVEN -> {
                long doubleRemainder = remainder * 2L;
                if (doubleRemainder < increment) {
                    yield lower;
                } else if (doubleRemainder > increment) {
                    yield upper;
                } else if (Math.floorMod(quotient, 2L) == 0L) {
                    yield lower;
                } else {
                    yield upper;
                }
            }
        };
    }

    public long roundNumberToIncrement(long quantity, long increment) {
        long quotient = quantity / increment;
        long remainder = quantity % increment;
        TemporalSign sign = TemporalSign.fromSignum(quantity);
        long roundingFloor = Math.abs(quotient);
        long roundingCeiling = roundingFloor + 1L;
        int comparison = Integer.compare(Long.compare(Math.abs(remainder * 2L), increment), 0);
        boolean evenCardinality = Math.floorMod(roundingFloor, 2L) == 0L;
        TemporalUnsignedRoundingMode unsignedRoundingMode = toUnsigned(sign);
        long rounded;
        if (remainder == 0L) {
            rounded = roundingFloor;
        } else {
            rounded = unsignedRoundingMode.getRoundedUnit(
                    roundingFloor,
                    roundingCeiling,
                    comparison,
                    evenCardinality);
        }
        if (sign.isPositive()) {
            return increment * rounded;
        } else {
            return -increment * rounded;
        }
    }

    public TemporalUnsignedRoundingMode toUnsigned(TemporalSign sign) {
        boolean negativeSign = sign.isNegative();
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
