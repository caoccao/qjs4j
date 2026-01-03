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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Double-to-ASCII converter.
 * Implements proper JavaScript number-to-string conversion.
 * Based on QuickJS dtoa.c implementation.
 * <p>
 * Supports JavaScript's Number.prototype methods:
 * - toString() - free format
 * - toFixed(fractionDigits) - fixed decimal notation
 * - toExponential(fractionDigits) - exponential notation
 * - toPrecision(precision) - significant digits
 */
public final class DtoaConverter {

    public static final double FIXED_THRESHOLD = 1e21;
    public static final int MAX_DIGITS = 100;
    public static final int MAX_PRECISION = 100;

    /**
     * Clean up number string by removing trailing zeros and unnecessary decimal points.
     */
    private static String cleanupNumberString(String str) {
        if (!str.contains(".")) {
            return str;
        }

        // Remove trailing zeros after decimal point
        int i = str.length() - 1;
        while (i >= 0 && str.charAt(i) == '0') {
            i--;
        }

        // Remove decimal point if no fractional part remains
        if (i >= 0 && str.charAt(i) == '.') {
            i--;
        }

        return str.substring(0, i + 1);
    }

    /**
     * Convert a double to string using free format (automatic best representation).
     * This is the default JavaScript toString() behavior.
     */
    public static String convert(double value) {
        return convert(value, false);
    }

    /**
     * Convert a double to string with optional minus zero handling.
     *
     * @param value         The value to convert
     * @param showMinusZero If true, show "-0" for negative zero
     */
    public static String convert(double value, boolean showMinusZero) {
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
            return "0";
        }

        // JavaScript uses exponential notation when exponent < -6 or >= 21
        // Java's Double.toString uses different rules, so we need custom logic

        // Calculate the exponent
        double absValue = Math.abs(value);
        int exponent = 0;
        if (absValue != 0) {
            exponent = (int) Math.floor(Math.log10(absValue));
        }

        // Use exponential notation for very small or very large numbers
        if (absValue != 0 && (exponent < -6 || exponent >= 21)) {
            // Use exponential notation
            String result = Double.toString(value);
            // Convert uppercase 'E' to lowercase 'e' for JavaScript standard
            result = result.replace('E', 'e');

            // Clean up mantissa: remove trailing zeros and unnecessary decimal point
            // Split by 'e' to get mantissa and exponent parts
            int eIndex = result.indexOf('e');
            if (eIndex != -1) {
                String mantissa = result.substring(0, eIndex);
                String exp = result.substring(eIndex);

                // Remove trailing zeros and decimal point from mantissa
                if (mantissa.contains(".")) {
                    mantissa = mantissa.replaceAll("0+$", "").replaceAll("\\.$", "");
                }

                result = mantissa + exp;
            }

            return result;
        }

        // Use decimal notation
        // For numbers in the safe range, use BigDecimal for accurate representation
        String result;
        if (value == Math.floor(value) && absValue < 1e15) {
            // Integer value - no decimal point
            result = String.format("%.0f", value);
        } else {
            // Use BigDecimal to avoid precision issues
            result = Double.toString(value);
            // If Java's toString used exponential notation, we need to convert it
            if (result.contains("E") || result.contains("e")) {
                // Convert from exponential to decimal
                result = String.format("%.15f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
            }
            // Remove trailing zeros after decimal point
            if (result.contains(".")) {
                result = result.replaceAll("0+$", "").replaceAll("\\.$", "");
            }
        }

        return result;
    }

    /**
     * Convert a decimal string to exponential notation.
     * Preserves the significant digits from the input string.
     */
    private static String convertDecimalToExponential(String decimalStr) {
        // Handle signs
        boolean negative = decimalStr.startsWith("-");
        if (negative) {
            decimalStr = decimalStr.substring(1);
        }

        // Handle special cases from Double.toString()
        if (decimalStr.equals("0.0")) {
            return "0e+0";
        }
        if (decimalStr.contains("E") || decimalStr.contains("e")) {
            // Already in exponential form, just normalize
            String result = normalizeExponentialFormat(decimalStr);
            return negative ? "-" + result : result;
        }

        // Remove decimal point and count position
        int decimalPos = decimalStr.indexOf('.');
        String digits;
        int exponent;

        if (decimalPos == -1) {
            // No decimal point - integer
            digits = decimalStr;
            // Remove trailing zeros
            int i = digits.length() - 1;
            while (i > 0 && digits.charAt(i) == '0') {
                i--;
            }
            digits = digits.substring(0, i + 1);
            exponent = digits.length() - 1;
        } else {
            // Has decimal point
            String intPart = decimalStr.substring(0, decimalPos);
            String fracPart = decimalStr.substring(decimalPos + 1);

            if (intPart.equals("0")) {
                // Number like 0.00123 → 1.23e-3
                // Find first non-zero digit
                int firstNonZero = 0;
                while (firstNonZero < fracPart.length() && fracPart.charAt(firstNonZero) == '0') {
                    firstNonZero++;
                }
                if (firstNonZero == fracPart.length()) {
                    // All zeros
                    return "0e+0";
                }
                digits = fracPart.substring(firstNonZero);
                exponent = -(firstNonZero + 1);
            } else {
                // Number like 123.456 → 1.23456e+2
                digits = intPart + fracPart;
                exponent = intPart.length() - 1;
            }
        }

        // Remove trailing zeros from digits
        int lastNonZero = digits.length() - 1;
        while (lastNonZero > 0 && digits.charAt(lastNonZero) == '0') {
            lastNonZero--;
        }
        digits = digits.substring(0, lastNonZero + 1);

        // Format mantissa
        StringBuilder result = new StringBuilder();
        if (negative) {
            result.append('-');
        }
        result.append(digits.charAt(0));
        if (digits.length() > 1) {
            result.append('.');
            result.append(digits.substring(1));
        }

        // Format exponent
        result.append('e');
        result.append(exponent >= 0 ? '+' : "");
        result.append(exponent);

        return result.toString();
    }

    /**
     * Convert to exponential notation.
     * Implements Number.prototype.toExponential(fractionDigits).
     *
     * @param value          The value to convert
     * @param fractionDigits Number of digits after decimal point (0-100)
     * @return The formatted string in exponential notation
     * @throws IllegalArgumentException if fractionDigits is out of range
     */
    public static String convertExponentialWithFractionDigits(double value, int fractionDigits) {
        if (fractionDigits < 0 || fractionDigits > MAX_DIGITS) {
            throw new IllegalArgumentException("fractionDigits must be between 0 and " + MAX_DIGITS);
        }

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
     * Convert to exponential notation with automatic precision.
     * Implements Number.prototype.toExponential() with no arguments.
     * Uses the minimum number of significant digits to uniquely represent the value.
     *
     * @param value The value to convert
     * @return The formatted string in exponential notation
     */
    public static String convertExponentialWithoutFractionDigits(double value) {
        // Handle special values
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }

        // Get the string representation which has the minimal precision
        String str = Double.toString(value);

        // Convert to exponential notation from the decimal string
        return convertDecimalToExponential(str);
    }

    /**
     * Convert to fixed-point notation.
     * Implements Number.prototype.toFixed(fractionDigits).
     *
     * @param value          The value to convert
     * @param fractionDigits Number of digits after decimal point (0-100)
     * @return The formatted string in fixed notation
     * @throws IllegalArgumentException if fractionDigits is out of range
     */
    public static String convertFixed(double value, int fractionDigits) {
        if (fractionDigits < 0 || fractionDigits > MAX_DIGITS) {
            throw new IllegalArgumentException("fractionDigits must be between 0 and " + MAX_DIGITS);
        }

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

        // Use BigDecimal for precise fixed-point formatting
        // Note: Use new BigDecimal(value) instead of BigDecimal.valueOf(value)
        // to preserve the exact binary representation of the double, including
        // floating-point rounding errors, which matches JavaScript behavior.
        try {
            BigDecimal bigDecimal = new BigDecimal(value);
            bigDecimal = bigDecimal.setScale(fractionDigits, RoundingMode.HALF_UP);
            return bigDecimal.toPlainString();
        } catch (NumberFormatException e) {
            // Fallback to String.format
            return String.format(Locale.US, "%." + fractionDigits + "f", value);
        }
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
            throw new IllegalArgumentException("radix must be between 2 and 36");
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
            throw new IllegalArgumentException("radix must be between 2 and 36");
        }
        return Long.toString(value, radix);
    }

    /**
     * Convert a double to string with specified radix (2-36).
     * Uses QuickJS-compatible dtoa algorithm for exact JavaScript compatibility.
     *
     * @param value The value to convert
     * @param radix The radix (base) to use for conversion (2-36)
     * @return The string representation in the specified radix
     */
    public static String convertToRadix(double value, int radix) {
        // Use QuickJS-compatible dtoa implementation
        return JSDtoa.toString(value, radix);
    }

    /**
     * Convert with specified precision (significant digits).
     * Implements Number.prototype.toPrecision(precision).
     *
     * @param value     The value to convert
     * @param precision Number of significant digits (1-100)
     * @return The formatted string
     * @throws IllegalArgumentException if precision is out of range
     */
    public static String convertWithPrecision(double value, int precision) {
        if (precision < 1 || precision > MAX_DIGITS) {
            throw new IllegalArgumentException("precision must be between 1 and " + MAX_DIGITS);
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
            return formatExponentialPrecision(value, precision - 1);
        } else {
            // Use fixed notation
            // Round to precision significant digits
            BigDecimal bd = new BigDecimal(value);
            MathContext mc = new MathContext(precision, RoundingMode.HALF_UP);
            bd = bd.round(mc);

            // Format with exactly precision significant digits
            String str = bd.toPlainString();

            // Count significant digits and add trailing zeros if needed
            return formatFixedPrecision(str, precision, exponent);
        }
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

        // Format the result - keep the exact precision as specified by fractionDigits
        String mantissaStr = mantissa.toPlainString();

        String result = mantissaStr +
                'e' +
                (exponent >= 0 ? '+' : "") +
                exponent;

        return result;
    }

    /**
     * Format a value in exponential notation for toPrecision.
     * fractionDigits is the number of digits after the decimal point in the mantissa.
     */
    private static String formatExponentialPrecision(double value, int fractionDigits) {
        // Use String.format for exponential notation
        String format = "%." + fractionDigits + "e";
        String result = String.format(Locale.US, format, value);

        // Clean up the exponent part (remove leading zeros after e+ or e-)
        result = result.replaceAll("e([+-])0+(\\d)", "e$1$2");

        return result;
    }

    /**
     * Format a string to have exactly precision significant digits in fixed notation.
     */
    private static String formatFixedPrecision(String str, int precision, int exponent) {
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

    /**
     * Get the base-10 exponent of a value.
     */
    private static int getExponent(double value) {
        if (value == 0.0) {
            return 0;
        }
        return (int) Math.floor(Math.log10(Math.abs(value)));
    }

    /**
     * Normalize exponential format from Java to JavaScript style.
     * Java uses "e+00", "e-00", JavaScript uses "e+0", "e-0"
     */
    private static String normalizeExponentialFormat(String str) {
        // Find 'e' or 'E'
        int eIndex = str.indexOf('e');
        if (eIndex == -1) {
            eIndex = str.indexOf('E');
        }
        if (eIndex == -1) {
            return str;
        }

        String mantissa = str.substring(0, eIndex);
        String exponentPart = str.substring(eIndex + 1);

        // Parse and reformat exponent
        char sign = '+';
        int startIndex = 0;
        if (exponentPart.charAt(0) == '+' || exponentPart.charAt(0) == '-') {
            sign = exponentPart.charAt(0);
            startIndex = 1;
        }

        int exponent = Integer.parseInt(exponentPart.substring(startIndex));
        return mantissa + "e" + sign + exponent;
    }
}
