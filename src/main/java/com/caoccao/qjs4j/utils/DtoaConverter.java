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

import com.caoccao.qjs4j.exceptions.JSRangeErrorException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Double-to-ASCII converter. Implements proper JavaScript number-to-string conversion. Based on QuickJS dtoa.c
 * implementation.
 * <p>
 * Supports JavaScript's Number.prototype methods: - toString() - free format - toFixed(fractionDigits) - fixed decimal
 * notation - toExponential(fractionDigits) - exponential notation - toPrecision(precision) - significant digits
 */
public final class DtoaConverter {

    public static final double FIXED_THRESHOLD = 1e21;
    public static final int MAX_DIGITS = 100;
    public static final int MAX_PRECISION = 100;

    private static final MathContext[] NEAREST = shortestRoundingContexts();

    private static void appendExponential(StringBuilder result, String digits, int exponent) {
        result.append(digits.charAt(0));
        if (digits.length() > 1) {
            result.append('.').append(digits, 1, digits.length());
        }
        result.append('e').append(exponent >= 0 ? '+' : '-').append(Math.abs(exponent));
    }

    private static void checkFractionDigits(int fractionDigits) {
        if (fractionDigits < 0 || fractionDigits > MAX_DIGITS) {
            throw new JSRangeErrorException("fractionDigits must be between 0 and " + MAX_DIGITS);
        }
    }

    /**
     * Convert a double to string using free format (automatic best representation). This is the default JavaScript
     * toString() behavior.
     */
    public static String convert(double value) {
        return convert(value, false);
    }

    /**
     * Convert a double to string with optional minus zero handling. Implements the ES2024 Number::toString algorithm
     * using BigDecimal to find the shortest decimal representation, independent of JDK version.
     *
     * @param value
     *            The value to convert
     * @param showMinusZero
     *            If true, show "-0" for negative zero
     */
    public static String convert(double value, boolean showMinusZero) {
        return formatShortest(value, showMinusZero, false);
    }

    /**
     * Convert to exponential notation. Implements Number.prototype.toExponential(fractionDigits).
     *
     * @param value
     *            The value to convert
     * @param fractionDigits
     *            Number of digits after decimal point (0-100)
     * @return The formatted string in exponential notation
     * @throws IllegalArgumentException
     *             if fractionDigits is out of range
     */
    public static String convertExponentialWithFractionDigits(double value, int fractionDigits) {
        checkFractionDigits(fractionDigits);

        // Handle special values
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }

        return formatExponential(value, fractionDigits);
    }

    /**
     * Convert to exponential notation with automatic precision. Implements Number.prototype.toExponential() with no
     * arguments. Uses the minimum number of significant digits to uniquely represent the value.
     *
     * @param value
     *            The value to convert
     * @return The formatted string in exponential notation
     */
    public static String convertExponentialWithoutFractionDigits(double value) {
        return formatShortest(value, false, true);
    }

    /**
     * Convert to fixed-point notation. Implements Number.prototype.toFixed(fractionDigits).
     *
     * @param value
     *            The value to convert
     * @param fractionDigits
     *            Number of digits after decimal point (0-100)
     * @return The formatted string in fixed notation
     * @throws IllegalArgumentException
     *             if fractionDigits is out of range
     */
    public static String convertFixed(double value, int fractionDigits) {
        checkFractionDigits(fractionDigits);

        // Handle special values
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }

        // JavaScript uses exponential notation for very large numbers in toFixed
        if (Math.abs(value) >= FIXED_THRESHOLD) {
            return convert(value);
        }

        boolean negativeNonZeroInput = value < 0.0;
        // Use BigDecimal for precise fixed-point formatting
        // Note: Use new BigDecimal(value) instead of BigDecimal.valueOf(value)
        // to preserve the exact binary representation of the double, including
        // floating-point rounding errors, which matches JavaScript behavior.
        BigDecimal bigDecimal = new BigDecimal(value);
        bigDecimal = bigDecimal.setScale(fractionDigits, RoundingMode.HALF_UP);
        if (negativeNonZeroInput && bigDecimal.signum() == 0) {
            return "-" + bigDecimal.abs().toPlainString();
        }
        return bigDecimal.toPlainString();
    }

    /**
     * Convert integer to string (optimized path).
     */
    public static String convertInt(int value) {
        return Integer.toString(value);
    }

    /**
     * Convert integer to string with specified radix (2-36).
     */
    public static String convertIntRadix(int value, int radix) {
        if (radix < 2 || radix > 36) {
            throw new JSRangeErrorException("radix must be between 2 and 36");
        }
        return Integer.toString(value, radix);
    }

    /**
     * Convert long to string (optimized path).
     */
    public static String convertLong(long value) {
        return Long.toString(value);
    }

    /**
     * Convert long to string with specified radix (2-36).
     */
    public static String convertLongRadix(long value, int radix) {
        if (radix < 2 || radix > 36) {
            throw new JSRangeErrorException("radix must be between 2 and 36");
        }
        return Long.toString(value, radix);
    }

    /**
     * Convert a double to string with specified radix (2-36). Uses QuickJS-compatible dtoa algorithm for exact
     * JavaScript compatibility.
     *
     * @param value
     *            The value to convert
     * @param radix
     *            The radix (base) to use for conversion (2-36)
     * @return The string representation in the specified radix
     */
    public static String convertToRadix(double value, int radix) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        if (value == 0) {
            return "0";
        }
        return doubleToRadixString(value, radix);
    }

    /**
     * Convert with specified precision (significant digits). Implements Number.prototype.toPrecision(precision).
     *
     * @param value
     *            The value to convert
     * @param precision
     *            Number of significant digits (1-100)
     * @return The formatted string
     * @throws IllegalArgumentException
     *             if precision is out of range
     */
    public static String convertWithPrecision(double value, int precision) {
        if (precision < 1 || precision > MAX_DIGITS) {
            throw new JSRangeErrorException("precision must be between 1 and " + MAX_DIGITS);
        }

        // Handle special values
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }

        // Handle zero specially
        if (value == 0.0) {
            StringBuilder result = new StringBuilder("0");
            if (precision > 1) {
                result.append(".");
                for (int i = 1; i < precision; i++) {
                    result.append("0");
                }
            }
            return result.toString();
        }

        // Calculate the exponent (position of the most significant digit)
        int exponent = (int) Math.floor(Math.log10(Math.abs(value)));

        // Use exponential notation if exponent < -6 or exponent >= precision
        if (exponent < -6 || exponent >= precision) {
            // Use exponential notation with (precision - 1) fractional digits
            return formatExponential(value, precision - 1);
        } else {
            // Use fixed notation
            // Round to precision significant digits
            BigDecimal bd = new BigDecimal(value);
            MathContext mc = new MathContext(precision, RoundingMode.HALF_UP);
            bd = bd.round(mc);

            // Format with exactly precision significant digits
            String str = bd.toPlainString();

            // Count significant digits and add trailing zeros if needed
            return formatFixedPrecision(str, precision);
        }
    }

    /**
     * A port of V8's {@code DoubleToRadixCString}: fraction digits only up to the precision of the input, rounded half
     * to even with carry, and integer digits beyond 2<sup>53</sup> written as zeros.
     * <p>
     * The QuickJS algorithm this replaces printed {@code (1/3).toString(36)} as {@code 0.bzzzzzzzzzxk} where V8 prints
     * {@code 0.c}, and {@code (1e21).toString(36)} as {@code 5v1j4f4ds7a000} where V8 prints {@code 5v1j4f4ds7c000}.
     *
     * @param value
     *            a finite, non-zero value
     * @param radix
     *            the radix, 2 to 36
     * @return the digits
     */
    private static String doubleToRadixString(double value, int radix) {
        final String chars = "0123456789abcdefghijklmnopqrstuvwxyz";
        boolean negative = value < 0;
        if (negative) {
            value = -value;
        }
        // Split the value into an integer part and a fractional part.
        double integer = Math.floor(value);
        double fraction = value - integer;
        // Fraction digits are computed only up to the input's precision.
        double delta = Math.max(0.5 * (Math.nextUp(value) - value), Double.MIN_VALUE);
        StringBuilder fractionDigits = new StringBuilder();
        if (fraction >= delta) {
            do {
                // Shift up by one digit.
                fraction *= radix;
                delta *= radix;
                int digit = (int) fraction;
                fractionDigits.append(chars.charAt(digit));
                fraction -= digit;
                // Round to even.
                if (fraction > 0.5 || (fraction == 0.5 && (digit & 1) != 0)) {
                    if (fraction + delta > 1) {
                        // Back-trace the digits already written in case of a carry.
                        while (true) {
                            int last = fractionDigits.length() - 1;
                            if (last < 0) {
                                // Carry over into the integer part.
                                integer += 1;
                                break;
                            }
                            char c = fractionDigits.charAt(last);
                            int lastDigit = c > '9' ? c - 'a' + 10 : c - '0';
                            fractionDigits.setLength(last);
                            if (lastDigit + 1 < radix) {
                                fractionDigits.append(chars.charAt(lastDigit + 1));
                                break;
                            }
                        }
                        break;
                    }
                }
            } while (fraction >= delta);
        }
        // Integer digits; the ones a double cannot represent are written as zeros.
        StringBuilder integerDigits = new StringBuilder();
        while (Math.getExponent(integer / radix) > 52) {
            integer /= radix;
            integerDigits.append('0');
        }
        do {
            double remainder = integer % radix;
            integerDigits.append(chars.charAt((int) remainder));
            integer = (integer - remainder) / radix;
        } while (integer > 0);
        StringBuilder result = new StringBuilder(integerDigits.length() + fractionDigits.length() + 2);
        if (negative) {
            result.append('-');
        }
        result.append(integerDigits.reverse());
        if (!fractionDigits.isEmpty()) {
            result.append('.').append(fractionDigits);
        }
        return result.toString();
    }

    /**
     * Format a value in exponential notation.
     */
    private static String formatExponential(double value, int fractionDigits) {
        if (value == 0.0) {
            // Special case for zero
            StringBuilder sb = new StringBuilder("0");
            if (fractionDigits > 0) {
                sb.append('.');
                for (int i = 0; i < fractionDigits; i++) {
                    sb.append('0');
                }
            }
            sb.append("e+0");
            return sb.toString();
        }

        // Use BigDecimal to preserve the exact binary representation of the double
        // Note: Use new BigDecimal(value) instead of BigDecimal.valueOf(value)
        // to preserve the exact binary representation, matching JavaScript behavior.
        BigDecimal bd = new BigDecimal(value);

        // Calculate the exponent
        int exponent = bd.precision() - bd.scale() - 1;

        // Shift to get mantissa with proper decimal places
        BigDecimal mantissa = bd.scaleByPowerOfTen(-exponent);

        // Round to the desired number of fraction digits
        mantissa = mantissa.setScale(fractionDigits, RoundingMode.HALF_UP);

        // After rounding, mantissa may have carried (e.g., 9.999 -> 10.0).
        // Renormalize: if mantissa >= 10, divide by 10 and increment exponent.
        if (mantissa.abs().compareTo(BigDecimal.TEN) >= 0) {
            mantissa = mantissa.movePointLeft(1).setScale(fractionDigits, RoundingMode.HALF_UP);
            exponent++;
        }

        // Format the result - keep the exact precision as specified by fractionDigits
        String mantissaStr = mantissa.toPlainString();

        String result = mantissaStr + 'e' + (exponent >= 0 ? '+' : "") + exponent;

        return result;
    }

    /**
     * Format a string to have exactly precision significant digits in fixed notation.
     */
    private static String formatFixedPrecision(String str, int precision) {
        // Remove sign for processing
        boolean negative = str.startsWith("-");
        if (negative) {
            str = str.substring(1);
        }

        // Remove decimal point for counting
        String digits = str.replace(".", "");

        // Count leading zeros if any (for numbers like 0.001)
        int leadingZeros = 0;
        for (int i = 0; i < digits.length(); i++) {
            if (digits.charAt(i) != '0') {
                break;
            }
            leadingZeros++;
        }

        // Significant digits are all non-leading-zero digits
        int sigDigits = digits.length() - leadingZeros;

        // Add trailing zeros if we need more significant digits
        int zerosToAdd = precision - sigDigits;
        if (zerosToAdd > 0) {
            StringBuilder result = new StringBuilder(str);
            if (!str.contains(".")) {
                result.append(".");
            }
            for (int i = 0; i < zerosToAdd; i++) {
                result.append("0");
            }
            str = result.toString();
        }

        return negative ? "-" + str : str;
    }

    private static String formatShortest(double value, boolean showMinusZero, boolean exponential) {
        // Handle special values
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        if (value == 0.0) {
            // Check for negative zero
            if (showMinusZero && Double.doubleToRawLongBits(value) == Double.doubleToRawLongBits(-0.0)) {
                return "-0";
            }
            return exponential ? "0e+0" : "0";
        }

        boolean negative = value < 0;
        double absValue = Math.abs(value);

        BigDecimal shortest = shortestDecimal(absValue);
        String digits = shortest.unscaledValue().abs().toString();
        int k = digits.length();
        // n = exponent + 1 (position count of the most significant digit)
        int n = shortest.precision() - shortest.scale();

        // Format according to ES2024 Number::toString (7.1.12.1)
        StringBuilder result = new StringBuilder();
        if (negative) {
            result.append('-');
        }

        if (exponential || n <= -6 || n > 21) {
            appendExponential(result, digits, n - 1);
        } else if (k <= n) {
            // Case: integer form with trailing zeros (e.g., 1200)
            result.append(digits);
            for (int i = 0; i < n - k; i++) {
                result.append('0');
            }
        } else if (0 < n) {
            // Case: decimal form (e.g., 12.34)
            result.append(digits, 0, n);
            result.append('.');
            result.append(digits, n, k);
        } else {
            // Case: small decimal (e.g., 0.001234)
            result.append("0.");
            for (int i = 0; i < -n; i++) {
                result.append('0');
            }
            result.append(digits);
        }

        return result.toString();
    }

    /**
     * Get the base-10 exponent of a value.
     */
    private static int getExponent(double value) {
        if (value == 0.0) {
            return 0;
        }
        return (int) Math.floor(Math.log10(Math.abs(value)));
    }

    private static BigDecimal shortestDecimal(double absValue) {
        // Find the shortest decimal representation using BigDecimal.
        // ES2024 spec: find s,k,n where k>=1, 10^(k-1) <= s < 10^k,
        // s * 10^(n-k) == value as double, and k is as small as possible.
        BigDecimal exact = new BigDecimal(absValue);
        // Only a power of two has a rounding interval narrower below than above, so only there can the k-digit
        // decimal on the far side of the value read back as the value while the nearest one does not.
        boolean powerOfTwo = (Double.doubleToRawLongBits(absValue) & 0x000F_FFFF_FFFF_FFFFL) == 0;
        for (int k = 1; k <= 21; k++) {
            // The nearest k-digit decimal first, as the specification prefers; failing that, the k-digit decimal on
            // the other side of the value. Testing only the nearest one missed values such as 2 ** -24, whose
            // nearest 16-digit decimal (…062) reads back as another double while …063 reads back as itself, and
            // printed 17 digits.
            BigDecimal candidate = exact.round(NEAREST[k]);
            if (candidate.doubleValue() == absValue) {
                return candidate.stripTrailingZeros();
            }
            if (!powerOfTwo) {
                continue;
            }
            RoundingMode otherSide = candidate.compareTo(exact) < 0 ? RoundingMode.CEILING : RoundingMode.FLOOR;
            candidate = exact.round(new MathContext(k, otherSide));
            if (candidate.doubleValue() == absValue) {
                return candidate.stripTrailingZeros();
            }
        }

        return exact.stripTrailingZeros();
    }

    private static MathContext[] shortestRoundingContexts() {
        MathContext[] contexts = new MathContext[22];
        for (int i = 1; i < contexts.length; i++) {
            contexts[i] = new MathContext(i, RoundingMode.HALF_EVEN);
        }
        return contexts;
    }
}
