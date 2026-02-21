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

package com.caoccao.qjs4j.utils;

/**
 * QuickJS-compatible dtoa (double-to-ASCII) implementation.
 * Based on QuickJS dtoa.c by Fabrice Bellard.
 * <p>
 * This implementation uses arbitrary precision arithmetic to match
 * JavaScript's Number.prototype.toString(radix) behavior exactly.
 */
public final class JSDtoa {

    // Maximum number of digits for each radix (radix 2 to 36)
    private static final int[] DTOA_MAX_DIGITS = {
            54, 35, 28, 24, 22, 20, 19, 18, 17, 17, 16, 16, 15, 15, 15, 14, 14, 14, 14, 14, 13,
            13, 13, 13, 13, 13, 13, 12, 12, 12, 12, 12, 12, 12, 12
    };
    private static final int MAX_LIMBS = 256;
    private static final int MAX_RADIX = 36;
    // MUL_LOG2_RADIX table (multiplier for floor(log_radix(2^n)))
    private static final int MUL_LOG2_BASE = 24;
    private static final int[] MUL_LOG2_TABLE = {
            0x000000, 0xa1849d, 0x000000, 0x6e40d2,
            0x6308c9, 0x5b3065, 0x000000, 0x50c24e,
            0x4d104d, 0x4a0027, 0x4768ce, 0x452e54,
            0x433d00, 0x418677, 0x000000, 0x3ea16b,
            0x3d645a, 0x3c43c2, 0x3b3b9a, 0x3a4899,
            0x39680b, 0x3897b3, 0x37d5af, 0x372069,
            0x367686, 0x35d6df, 0x354072, 0x34b261,
            0x342bea, 0x33ac62, 0x000000, 0x32bfd9,
            0x3251dd, 0x31e8d6, 0x318465
    };

    /**
     * Compute mantissa and adjust E so that mantissa < radix^P.
     */
    private static long[] computeMantissaAndE(long m, int exp, int radix, int P, int E) {
        // We need: result = round(m * 2^exp * radix^(P-E))

        while (true) {
            long result = mulPowRound(m, exp, radix, P - E);
            long limit = pow(radix, P);

            if (result < limit) {
                return new long[]{result, E};
            }
            E++;
        }
    }

    private static char digitChar(int d) {
        return (char) (d < 10 ? '0' + d : 'a' + d - 10);
    }

    private static String findShortest(long mantissa, int e, int radix) {
        // Compute the initial exponent estimate
        int E0 = 1 + mulLog2Radix(e - 1, radix);
        int maxDigits = DTOA_MAX_DIGITS[radix - 2];

        long targetBits = makeBits(mantissa, e);

        // Search from 1 digit up to maxDigits for shortest round-trip
        for (int P = 1; P <= maxDigits; P++) {
            int E = E0;

            // Compute m = round(mantissa * 2^(e-53) * radix^(P-E))
            long[] meResult = computeMantissaAndE(mantissa, e - 53, radix, P, E);
            long m = meResult[0];
            E = (int) meResult[1];

            if (m == 0 && P == 1) {
                continue;
            }

            // Check if this round-trips correctly
            double reconstructed = reconstructDouble(m, radix, E - P);
            long recBits = Double.doubleToRawLongBits(reconstructed);

            if (recBits == targetBits) {
                // Found! Format the result
                return formatNumber(m, P, E, radix);
            }
        }

        // Fallback: use maximum precision
        int E = E0;
        long[] meResult = computeMantissaAndE(mantissa, e - 53, radix, maxDigits, E);
        return formatNumber(meResult[0], maxDigits, (int) meResult[1], radix);
    }

    private static String formatNumber(long mant, int P, int E, int radix) {
        StringBuilder digits = new StringBuilder();
        long m = mant;

        // Remove trailing zeros
        while (m > 0 && (m % radix) == 0 && P > 1) {
            m /= radix;
            P--;
        }

        // Convert to digits
        for (int i = 0; i < P; i++) {
            digits.append(digitChar((int) (m % radix)));
            m /= radix;
        }
        digits.reverse();

        StringBuilder result = new StringBuilder();

        if (E <= 0) {
            result.append("0.");
            for (int i = 0; i < -E; i++) result.append('0');
            result.append(digits);
        } else if (E >= P) {
            result.append(digits);
            for (int i = 0; i < E - P; i++) result.append('0');
        } else {
            result.append(digits, 0, E);
            result.append('.');
            result.append(digits.substring(E));
        }

        return result.toString();
    }

    private static String longToRadix(long value, int radix) {
        if (value == 0) { return "0"; }
        StringBuilder sb = new StringBuilder();
        while (value != 0) {
            sb.append(digitChar((int) (value % radix)));
            value /= radix;
        }
        return sb.reverse().toString();
    }

    private static long makeBits(long mantissa, int e) {
        if (e <= 0) {
            // Denormal
            return mantissa >>> (1 - e);
        }
        int biasedExp = e + 1022;
        long m = mantissa & 0x000FFFFFFFFFFFFFL;
        return ((long) biasedExp << 52) | m;
    }

    private static int mpDiv(int[] limbs, int len, int b) {
        long rem = 0;
        for (int i = len - 1; i >= 0; i--) {
            long val = (rem << 32) | Integer.toUnsignedLong(limbs[i]);
            limbs[i] = (int) Long.divideUnsigned(val, b);
            rem = Long.remainderUnsigned(val, b);
        }
        return (int) rem;
    }

    // ============== Helper functions ==============

    private static int mpFloorLog2(int[] limbs, int len) {
        for (int i = len - 1; i >= 0; i--) {
            if (limbs[i] != 0) {
                return i * 32 + (31 - Integer.numberOfLeadingZeros(limbs[i]));
            }
        }
        return -1;
    }

    private static int mpMul(int[] limbs, int len, int b) {
        long carry = 0;
        for (int i = 0; i < len; i++) {
            long prod = Integer.toUnsignedLong(limbs[i]) * b + carry;
            limbs[i] = (int) prod;
            carry = prod >>> 32;
        }
        while (carry != 0) {
            limbs[len++] = (int) carry;
            carry >>>= 32;
        }
        return len;
    }

    private static int mpNormalize(int[] limbs, int len) {
        while (len > 1 && limbs[len - 1] == 0) len--;
        return len;
    }

    private static int mpShiftLeft(int[] limbs, int len, int bits) {
        if (bits <= 0) { return len; }

        int words = bits / 32;
        int rem = bits % 32;

        // Move words
        for (int i = len - 1; i >= 0; i--) {
            limbs[i + words] = limbs[i];
        }
        for (int i = 0; i < words; i++) {
            limbs[i] = 0;
        }
        len += words;

        // Shift remaining bits
        if (rem > 0) {
            int carry = 0;
            for (int i = 0; i < len; i++) {
                int newCarry = limbs[i] >>> (32 - rem);
                limbs[i] = (limbs[i] << rem) | carry;
                carry = newCarry;
            }
            if (carry != 0) {
                limbs[len++] = carry;
            }
        }

        return len;
    }

    // ============== Multi-precision 32-bit limb operations ==============

    private static long mpShiftRightRound(int[] limbs, int len, int shift) {
        if (shift <= 0) {
            // Shift left
            long result = Integer.toUnsignedLong(limbs[0]);
            if (len > 1) { result |= Integer.toUnsignedLong(limbs[1]) << 32; }
            return result << (-shift);
        }

        int wordShift = shift / 32;
        int bitShift = shift % 32;

        if (wordShift >= len) { return 0; }

        // Calculate the round bit and sticky bits
        int roundBit = 0;
        boolean sticky = false;

        if (shift > 0) {
            if (shift <= len * 32) {
                roundBit = (limbs[(shift - 1) / 32] >>> ((shift - 1) % 32)) & 1;

                // Check sticky bits
                for (int i = 0; i < (shift - 1) / 32; i++) {
                    if (limbs[i] != 0) {
                        sticky = true;
                        break;
                    }
                }
                if (!sticky && (shift - 1) % 32 > 0) {
                    int mask = (1 << ((shift - 1) % 32)) - 1;
                    if ((limbs[(shift - 1) / 32] & mask) != 0) {
                        sticky = true;
                    }
                }
            }
        }

        // Extract result
        long result;
        if (bitShift == 0) {
            result = Integer.toUnsignedLong(limbs[wordShift]);
            if (wordShift + 1 < len) {
                result |= Integer.toUnsignedLong(limbs[wordShift + 1]) << 32;
            }
        } else {
            long lo = Integer.toUnsignedLong(limbs[wordShift]) >>> bitShift;
            if (wordShift + 1 < len) {
                lo |= Integer.toUnsignedLong(limbs[wordShift + 1]) << (32 - bitShift);
            }
            long hi = 0;
            if (wordShift + 1 < len) {
                hi = Integer.toUnsignedLong(limbs[wordShift + 1]) >>> bitShift;
                if (wordShift + 2 < len) {
                    hi |= Integer.toUnsignedLong(limbs[wordShift + 2]) << (32 - bitShift);
                }
            }
            result = (lo & 0xFFFFFFFFL) | (hi << 32);
        }

        // Round to nearest even
        if (roundBit != 0 && (sticky || (result & 1) != 0)) {
            result++;
        }

        return result;
    }

    private static int mulLog2Radix(int a, int radix) {
        if ((radix & (radix - 1)) == 0) {
            int bits = Integer.numberOfTrailingZeros(radix);
            return a < 0 ? (a - bits + 1) / bits : a / bits;
        }
        return (int) (((long) a * MUL_LOG2_TABLE[radix - 2]) >> MUL_LOG2_BASE);
    }

    /**
     * Compute round(m * 2^exp * radix^f) using multi-precision arithmetic.
     */
    private static long mulPowRound(long m, int exp, int radix, int f) {
        if (m == 0) { return 0; }

        // Use multi-precision with 32-bit limbs
        int[] limbs = new int[MAX_LIMBS];
        int len = 2;
        limbs[0] = (int) m;
        limbs[1] = (int) (m >>> 32);
        if (limbs[1] == 0) { len = 1; }

        // Track the binary exponent offset
        int binaryExp = exp;

        if (f >= 0) {
            // Multiply by radix^f
            for (int i = 0; i < f; i++) {
                len = mpMul(limbs, len, radix);
            }
        } else {
            // Divide by radix^(-f)
            // We need extra precision to handle the division
            int extraBits = (int) Math.ceil(-f * Math.log(radix) / Math.log(2)) + 64;
            len = mpShiftLeft(limbs, len, extraBits);
            binaryExp -= extraBits;

            for (int i = 0; i < -f; i++) {
                boolean hasRem = mpDiv(limbs, len, radix) != 0;
                len = mpNormalize(limbs, len);
                if (hasRem) {
                    // Mark that we lost some bits
                    limbs[0] |= 1;
                }
            }
        }

        // Apply the binary exponent
        return mpShiftRightRound(limbs, len, -binaryExp);
    }

    private static long pow(int base, int exp) {
        long r = 1;
        for (int i = 0; i < exp; i++) r *= base;
        return r;
    }

    /**
     * Reconstruct double from mantissa in radix representation.
     * d = m * radix^f where f = E - P (position of decimal point adjustment)
     */
    private static double reconstructDouble(long m, int radix, int f) {
        if (m == 0) { return 0.0; }

        int[] limbs = new int[MAX_LIMBS];
        int len = 2;
        limbs[0] = (int) m;
        limbs[1] = (int) (m >>> 32);
        if (limbs[1] == 0) { len = 1; }

        int binaryExp = 0;

        if (f >= 0) {
            // Multiply by radix^f
            for (int i = 0; i < f; i++) {
                len = mpMul(limbs, len, radix);
            }
        } else {
            // Divide by radix^(-f), need extra precision
            int extraBits = (int) Math.ceil(-f * Math.log(radix) / Math.log(2)) + 128;
            len = mpShiftLeft(limbs, len, extraBits);
            binaryExp -= extraBits;

            for (int i = 0; i < -f; i++) {
                int rem = mpDiv(limbs, len, radix);
                len = mpNormalize(limbs, len);
                if (rem != 0) { limbs[0] |= 1; }
            }
        }

        // Normalize to 53 bits and form the double
        int log2 = mpFloorLog2(limbs, len);
        if (log2 < 0) { return 0.0; }

        int e = log2 + 1 + binaryExp;  // The true exponent

        // Handle denormals and overflow
        if (e < -1074) { return 0.0; }
        if (e > 1024) { return Double.POSITIVE_INFINITY; }

        int prec = 53;
        if (e <= -1022) {
            prec = 53 - (-1022 - e + 1);
            if (prec <= 0) { return 0.0; }
        }

        // Shift right to extract top 'prec' bits from the limbs array
        long mant = mpShiftRightRound(limbs, len, log2 + 1 - prec);
        if (prec < 53) {
            mant <<= (53 - prec);
        }

        // Check for overflow in mantissa (rounding could push it over)
        if (mant >= (1L << 53)) {
            mant >>>= 1;
            e++;
        }

        if (e > 1024) { return Double.POSITIVE_INFINITY; }

        int biasedExp;
        if (e <= -1022) {
            // Denormal
            biasedExp = 0;
            int shift = -1022 - e;
            mant >>>= shift;
        } else {
            biasedExp = e + 1022;
            mant &= 0x000FFFFFFFFFFFFFL;  // Remove implicit bit
        }

        long bits = ((long) biasedExp << 52) | mant;
        return Double.longBitsToDouble(bits);
    }

    /**
     * Convert double to string in specified radix.
     */
    public static String toString(double d, int radix) {
        if (radix < 2 || radix > MAX_RADIX) {
            throw new IllegalArgumentException("radix must be between 2 and 36");
        }

        // Handle special values
        if (Double.isNaN(d)) {
            return "NaN";
        }
        if (Double.isInfinite(d)) {
            return d > 0 ? "Infinity" : "-Infinity";
        }

        // Extract IEEE 754 components
        long bits = Double.doubleToRawLongBits(d);
        boolean negative = (bits >>> 63) != 0;
        int biasedExp = (int) ((bits >>> 52) & 0x7FF);
        long mantissa = bits & 0x000FFFFFFFFFFFFFL;

        // Handle zero
        if (biasedExp == 0 && mantissa == 0) {
            return "0";
        }

        StringBuilder result = new StringBuilder();
        if (negative) {
            result.append('-');
        }

        // Normalize mantissa and exponent
        int e;
        if (biasedExp == 0) {
            // Denormal number
            int shift = Long.numberOfLeadingZeros(mantissa) - 11;
            mantissa <<= shift;
            e = 1 - 1022 - shift;
        } else {
            mantissa |= 0x0010000000000000L;
            e = biasedExp - 1022;
        }
        // d = mantissa * 2^(e-53)

        // Fast path for integers representable exactly
        if (e >= 1 && e <= 53 && (mantissa & ((1L << (53 - e)) - 1)) == 0) {
            result.append(longToRadix(mantissa >>> (53 - e), radix));
            return result.toString();
        }

        // Find shortest representation using round-trip validation
        String best = findShortest(mantissa, e, radix);
        result.append(best);

        return result.toString();
    }
}
