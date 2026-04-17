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

package com.caoccao.qjs4j.builtins.temporal;

import com.caoccao.qjs4j.core.temporal.TemporalRoundingMode;
import com.caoccao.qjs4j.core.temporal.TemporalUnsignedRoundingMode;

import java.math.BigInteger;

final class TemporalMathKernel {
    private TemporalMathKernel() {
    }

    /**
     * Selects floor or ceiling based on an unsigned rounding mode string.
     */
    static long applyUnsignedRoundingMode(
            long roundingFloor,
            long roundingCeiling,
            int comparison,
            boolean evenCardinality,
            String unsignedRoundingMode) {
        if ("zero".equals(unsignedRoundingMode)) {
            return roundingFloor;
        }
        if ("infinity".equals(unsignedRoundingMode)) {
            return roundingCeiling;
        }
        if (comparison < 0) {
            return roundingFloor;
        }
        if (comparison > 0) {
            return roundingCeiling;
        }
        if ("half-zero".equals(unsignedRoundingMode)) {
            return roundingFloor;
        }
        if ("half-infinity".equals(unsignedRoundingMode)) {
            return roundingCeiling;
        }
        return evenCardinality ? roundingFloor : roundingCeiling;
    }

    static BigInteger[] floorDivideAndRemainder(BigInteger value, BigInteger divisor) {
        BigInteger[] quotientAndRemainder = value.divideAndRemainder(divisor);
        BigInteger quotient = quotientAndRemainder[0];
        BigInteger remainder = quotientAndRemainder[1];
        if (remainder.signum() < 0) {
            quotient = quotient.subtract(BigInteger.ONE);
            remainder = remainder.add(divisor);
        }
        return new BigInteger[]{quotient, remainder};
    }

    /**
     * Maps a signed rounding mode to an unsigned rounding mode string given the sign.
     * Used by long-based increment rounding (roundNumberToIncrement).
     */
    static String getUnsignedRoundingMode(String roundingMode, String sign) {
        boolean negativeSign = "negative".equals(sign);
        TemporalRoundingMode mode = TemporalRoundingMode.fromString(roundingMode);
        if (mode == null) {
            return TemporalUnsignedRoundingMode.HALF_INFINITY.jsName();
        }
        return TemporalUnsignedRoundingMode.of(mode, negativeSign).jsName();
    }

    static BigInteger roundBigIntegerToIncrementAsIfPositive(
            BigInteger quantity,
            BigInteger increment,
            TemporalRoundingMode roundingMode) {
        if (increment.signum() == 0) {
            return quantity;
        }
        BigInteger[] floorQuotientAndRemainder = floorDivideAndRemainder(quantity, increment);
        BigInteger floorQuotient = floorQuotientAndRemainder[0];
        BigInteger lower = floorQuotient.multiply(increment);
        BigInteger remainder = floorQuotientAndRemainder[1];
        if (remainder.signum() == 0) {
            return lower;
        }
        BigInteger upper = lower.add(increment);

        return switch (roundingMode) {
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

    static BigInteger roundBigIntegerToIncrementSigned(
            BigInteger value,
            BigInteger increment,
            TemporalRoundingMode roundingMode) {
        if (increment.signum() == 0) {
            return value;
        }
        BigInteger[] floorQuotientAndRemainder = floorDivideAndRemainder(value, increment);
        BigInteger floorQuotient = floorQuotientAndRemainder[0];
        BigInteger remainder = floorQuotientAndRemainder[1];
        BigInteger floorValue = floorQuotient.multiply(increment);
        if (remainder.signum() == 0) {
            return floorValue;
        }
        BigInteger ceilValue = floorValue.add(increment);
        int sign = value.signum();

        return switch (roundingMode) {
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
                    yield switch (roundingMode) {
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

    static long roundLongToIncrementAsIfPositive(long quantity, long increment, TemporalRoundingMode roundingMode) {
        long quotient = Math.floorDiv(quantity, increment);
        long lower = quotient * increment;
        long remainder = quantity - lower;
        if (remainder == 0L) {
            return quantity;
        }
        long upper = lower + increment;

        return switch (roundingMode) {
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

    /**
     * Rounds a signed long to the nearest multiple of increment using the given rounding mode.
     */
    static long roundNumberToIncrement(long quantity, long increment, String roundingMode) {
        long quotient = quantity / increment;
        long remainder = quantity % increment;
        String sign = quantity < 0 ? "negative" : "positive";
        long roundingFloor = Math.abs(quotient);
        long roundingCeiling = roundingFloor + 1L;
        int comparison = Integer.compare(Long.compare(Math.abs(remainder * 2L), increment), 0);
        boolean evenCardinality = Math.floorMod(roundingFloor, 2L) == 0L;
        String unsignedRoundingMode = getUnsignedRoundingMode(roundingMode, sign);
        long rounded;
        if (remainder == 0L) {
            rounded = roundingFloor;
        } else {
            rounded = applyUnsignedRoundingMode(
                    roundingFloor,
                    roundingCeiling,
                    comparison,
                    evenCardinality,
                    unsignedRoundingMode);
        }
        return "positive".equals(sign) ? increment * rounded : -increment * rounded;
    }
}
