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

import java.math.BigInteger;

final class TemporalMathKernel {
    private TemporalMathKernel() {
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

    static long getMaximumSubDayRoundingIncrement(String smallestUnit) {
        if ("hour".equals(smallestUnit)) {
            return 24L;
        } else if ("minute".equals(smallestUnit) || "second".equals(smallestUnit)) {
            return 60L;
        } else if ("millisecond".equals(smallestUnit)
                || "microsecond".equals(smallestUnit)
                || "nanosecond".equals(smallestUnit)) {
            return 1_000L;
        } else {
            return -1L;
        }
    }

    static boolean isValidSubDayRoundingIncrement(String smallestUnit, long roundingIncrement) {
        long maximumIncrement = getMaximumSubDayRoundingIncrement(smallestUnit);
        if (maximumIncrement < 0L) {
            return false;
        }
        if (roundingIncrement < 1L || roundingIncrement >= maximumIncrement) {
            return false;
        }
        return maximumIncrement % roundingIncrement == 0L;
    }

    static BigInteger roundBigIntegerToIncrementAsIfPositive(
            BigInteger quantity,
            BigInteger increment,
            String roundingMode) {
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

        if ("ceil".equals(roundingMode) || "expand".equals(roundingMode)) {
            return upper;
        } else if ("floor".equals(roundingMode) || "trunc".equals(roundingMode)) {
            return lower;
        } else if ("halfExpand".equals(roundingMode) || "halfCeil".equals(roundingMode)) {
            if (remainder.shiftLeft(1).compareTo(increment) >= 0) {
                return upper;
            } else {
                return lower;
            }
        } else if ("halfTrunc".equals(roundingMode) || "halfFloor".equals(roundingMode)) {
            if (remainder.shiftLeft(1).compareTo(increment) > 0) {
                return upper;
            } else {
                return lower;
            }
        } else if ("halfEven".equals(roundingMode)) {
            BigInteger doubledRemainder = remainder.shiftLeft(1);
            int halfComparison = doubledRemainder.compareTo(increment);
            if (halfComparison < 0) {
                return lower;
            } else if (halfComparison > 0) {
                return upper;
            } else if (floorQuotient.testBit(0)) {
                return upper;
            } else {
                return lower;
            }
        } else {
            return lower;
        }
    }

    static BigInteger roundBigIntegerToIncrementSigned(
            BigInteger value,
            BigInteger increment,
            String roundingMode) {
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

        if ("floor".equals(roundingMode)) {
            return floorValue;
        } else if ("ceil".equals(roundingMode)) {
            return ceilValue;
        } else if ("trunc".equals(roundingMode)) {
            if (sign < 0) {
                return ceilValue;
            } else {
                return floorValue;
            }
        } else if ("expand".equals(roundingMode)) {
            if (sign < 0) {
                return floorValue;
            } else {
                return ceilValue;
            }
        } else if ("halfExpand".equals(roundingMode)
                || "halfTrunc".equals(roundingMode)
                || "halfEven".equals(roundingMode)
                || "halfCeil".equals(roundingMode)
                || "halfFloor".equals(roundingMode)) {
            BigInteger doubledRemainder = remainder.shiftLeft(1);
            int halfComparison = doubledRemainder.compareTo(increment);
            if (halfComparison < 0) {
                return floorValue;
            } else if (halfComparison > 0) {
                return ceilValue;
            } else if ("halfExpand".equals(roundingMode)) {
                if (sign < 0) {
                    return floorValue;
                } else {
                    return ceilValue;
                }
            } else if ("halfTrunc".equals(roundingMode)) {
                if (sign < 0) {
                    return ceilValue;
                } else {
                    return floorValue;
                }
            } else if ("halfEven".equals(roundingMode)) {
                if (floorQuotient.testBit(0)) {
                    return ceilValue;
                } else {
                    return floorValue;
                }
            } else if ("halfCeil".equals(roundingMode)) {
                return ceilValue;
            } else {
                return floorValue;
            }
        } else {
            return ceilValue;
        }
    }

    static long roundLongToIncrementAsIfPositive(long quantity, long increment, String roundingMode) {
        long quotient = Math.floorDiv(quantity, increment);
        long lower = quotient * increment;
        long remainder = quantity - lower;
        if (remainder == 0L) {
            return quantity;
        }
        long upper = lower + increment;

        if ("ceil".equals(roundingMode) || "expand".equals(roundingMode)) {
            return upper;
        } else if ("floor".equals(roundingMode) || "trunc".equals(roundingMode)) {
            return lower;
        } else if ("halfExpand".equals(roundingMode) || "halfCeil".equals(roundingMode)) {
            if (remainder * 2L >= increment) {
                return upper;
            } else {
                return lower;
            }
        } else if ("halfTrunc".equals(roundingMode) || "halfFloor".equals(roundingMode)) {
            if (remainder * 2L > increment) {
                return upper;
            } else {
                return lower;
            }
        } else if ("halfEven".equals(roundingMode)) {
            long doubleRemainder = remainder * 2L;
            if (doubleRemainder < increment) {
                return lower;
            } else if (doubleRemainder > increment) {
                return upper;
            } else if (Math.floorMod(quotient, 2L) == 0L) {
                return lower;
            } else {
                return upper;
            }
        } else {
            return lower;
        }
    }
}
