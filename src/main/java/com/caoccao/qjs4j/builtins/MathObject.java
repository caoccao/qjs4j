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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.utils.Float16;

/**
 * Implementation of JavaScript Math object.
 * Based on ES2020 Math object specification.
 */
public final class MathObject {
    // Math constants
    public static final double E = Math.E;           // 2.718281828459045
    public static final double LN10 = Math.log(10);  // 2.302585092994046
    public static final double LN2 = Math.log(2);    // 0.6931471805599453
    public static final double LOG10E = Math.log10(Math.E);  // 0.4342944819032518
    public static final double LOG2E = 1.0 / Math.log(2);    // 1.4426950408889634
    public static final double PI = Math.PI;          // 3.141592653589793
    public static final double SQRT1_2 = Math.sqrt(0.5);     // 0.7071067811865476
    public static final double SQRT2 = Math.sqrt(2); // 1.4142135623730951
    private static final int SP_LIMB_BITS = 56;
    private static final long SP_LIMB_MASK = (1L << SP_LIMB_BITS) - 1;
    private static final int SP_RND_BITS = SP_LIMB_BITS - 53;
    private static final int SUM_PRECISE_ACC_LEN = 39;
    private static final int SUM_PRECISE_COUNTER_INIT = 250;

    /**
     * Math.abs(x)
     * ES2020 20.2.2.1
     */
    public static JSValue abs(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.abs(x));
    }

    /**
     * Math.acos(x)
     * ES2020 20.2.2.2
     */
    public static JSValue acos(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.acos(x));
    }

    /**
     * Math.acosh(x)
     * ES2020 20.2.2.3
     */
    public static JSValue acosh(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        // acosh(x) = ln(x + sqrt(x*x - 1))
        return new JSNumber(Math.log(x + Math.sqrt(x * x - 1)));
    }

    /**
     * Math.asin(x)
     * ES2020 20.2.2.4
     */
    public static JSValue asin(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.asin(x));
    }

    /**
     * Math.asinh(x)
     * ES2020 20.2.2.5
     */
    public static JSValue asinh(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        // asinh(x) = ln(x + sqrt(x*x + 1))
        return new JSNumber(Math.log(x + Math.sqrt(x * x + 1)));
    }

    /**
     * Math.atan(x)
     * ES2020 20.2.2.6
     */
    public static JSValue atan(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.atan(x));
    }

    /**
     * Math.atan2(y, x)
     * ES2020 20.2.2.8
     */
    public static JSValue atan2(JSContext context, JSValue thisArg, JSValue[] args) {
        double y = args.length > 0 ? JSTypeConversions.toNumber(context, args[0]).value() : Double.NaN;
        double x = args.length > 1 ? JSTypeConversions.toNumber(context, args[1]).value() : Double.NaN;
        return new JSNumber(Math.atan2(y, x));
    }

    /**
     * Math.atanh(x)
     * ES2020 20.2.2.7
     */
    public static JSValue atanh(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        // atanh(x) = 0.5 * ln((1+x)/(1-x))
        return new JSNumber(0.5 * Math.log((1 + x) / (1 - x)));
    }

    /**
     * Math.cbrt(x)
     * ES2020 20.2.2.9
     */
    public static JSValue cbrt(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.cbrt(x));
    }

    /**
     * Math.ceil(x)
     * ES2020 20.2.2.10
     */
    public static JSValue ceil(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.ceil(x));
    }

    private static void closeIterator(JSContext context, JSObject iterator) {
        JSValue pendingException = context.getPendingException();
        if (pendingException != null) {
            context.clearPendingException();
        }
        JSValue returnMethod = iterator.get("return");
        if (returnMethod instanceof JSFunction returnFunction) {
            returnFunction.call(context, iterator, new JSValue[0]);
        }
        if (pendingException != null) {
            context.setPendingException(pendingException);
        }
    }

    /**
     * Math.clz32(x)
     * ES2020 20.2.2.11
     * Count leading zeros in 32-bit representation
     */
    public static JSValue clz32(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(32);
        }
        int n = JSTypeConversions.toInt32(context, args[0]);
        return new JSNumber(Integer.numberOfLeadingZeros(n));
    }

    /**
     * Math.cos(x)
     * ES2020 20.2.2.12
     */
    public static JSValue cos(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.cos(x));
    }

    /**
     * Math.cosh(x)
     * ES2020 20.2.2.13
     */
    public static JSValue cosh(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.cosh(x));
    }

    /**
     * Math.exp(x)
     * ES2020 20.2.2.14
     */
    public static JSValue exp(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.exp(x));
    }

    /**
     * Math.expm1(x)
     * ES2020 20.2.2.15
     * Returns e^x - 1
     */
    public static JSValue expm1(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.expm1(x));
    }

    /**
     * Math.f16round(x)
     * QuickJS extension.
     */
    public static JSValue f16round(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        short half = Float16.toHalf((float) x);
        return new JSNumber(Float16.toFloat(half));
    }

    /**
     * Math.floor(x)
     * ES2020 20.2.2.16
     */
    public static JSValue floor(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.floor(x));
    }

    /**
     * Math.fround(x)
     * ES2020 20.2.2.17
     * Round to nearest 32-bit float
     */
    public static JSValue fround(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber((float) x);
    }

    /**
     * Math.hypot(...values)
     * ES2020 20.2.2.18
     * Returns sqrt(sum of squares)
     */
    public static JSValue hypot(JSContext context, JSValue thisArg, JSValue[] args) {
        double result = 0.0;
        if (args.length > 0) {
            result = JSTypeConversions.toNumber(context, args[0]).value();
            if (args.length == 1) {
                result = Math.abs(result);
            } else {
                for (int i = 1; i < args.length; i++) {
                    double x = JSTypeConversions.toNumber(context, args[i]).value();
                    result = Math.hypot(result, x);
                }
            }
        }
        return new JSNumber(result);
    }

    /**
     * Math.imul(x, y)
     * ES2020 20.2.2.19
     * 32-bit integer multiplication
     */
    public static JSValue imul(JSContext context, JSValue thisArg, JSValue[] args) {
        int a = args.length > 0 ? JSTypeConversions.toInt32(context, args[0]) : 0;
        int b = args.length > 1 ? JSTypeConversions.toInt32(context, args[1]) : 0;
        return new JSNumber(a * b);
    }

    /**
     * Math.log(x)
     * ES2020 20.2.2.20
     */
    public static JSValue log(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.log(x));
    }

    /**
     * Math.log10(x)
     * ES2020 20.2.2.22
     */
    public static JSValue log10(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.log10(x));
    }

    /**
     * Math.log1p(x)
     * ES2020 20.2.2.21
     * Returns ln(1 + x)
     */
    public static JSValue log1p(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.log1p(x));
    }

    /**
     * Math.log2(x)
     * ES2020 20.2.2.23
     */
    public static JSValue log2(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.log(x) / Math.log(2));
    }

    /**
     * Math.max(...values)
     * ES2020 20.2.2.24
     */
    public static JSValue max(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NEGATIVE_INFINITY);
        }

        double max = Double.NEGATIVE_INFINITY;
        for (JSValue arg : args) {
            double x = JSTypeConversions.toNumber(context, arg).value();
            if (Double.isNaN(x)) {
                return new JSNumber(Double.NaN);
            }
            max = Math.max(max, x);
        }
        return new JSNumber(max);
    }

    /**
     * Math.min(...values)
     * ES2020 20.2.2.25
     */
    public static JSValue min(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.POSITIVE_INFINITY);
        }

        double min = Double.POSITIVE_INFINITY;
        for (JSValue arg : args) {
            double x = JSTypeConversions.toNumber(context, arg).value();
            if (Double.isNaN(x)) {
                return new JSNumber(Double.NaN);
            }
            min = Math.min(min, x);
        }
        return new JSNumber(min);
    }

    /**
     * Math.pow(base, exponent)
     * ES2020 20.2.2.26
     */
    public static JSValue pow(JSContext context, JSValue thisArg, JSValue[] args) {
        double base = args.length > 0 ? JSTypeConversions.toNumber(context, args[0]).value() : Double.NaN;
        double exp = args.length > 1 ? JSTypeConversions.toNumber(context, args[1]).value() : Double.NaN;
        return new JSNumber(Math.pow(base, exp));
    }

    /**
     * Math.random()
     * ES2020 20.2.2.27
     */
    public static JSValue random(JSContext context, JSValue thisArg, JSValue[] args) {
        return new JSNumber(Math.random());
    }

    /**
     * Math.round(x)
     * ES2020 20.2.2.28
     */
    public static JSValue round(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        if (Double.isNaN(x) || x == 0.0 || Double.isInfinite(x)) {
            return new JSNumber(x);
        }
        if (x >= -0.5 && x < 0) {
            return new JSNumber(-0.0);
        }
        return new JSNumber(Math.floor(x + 0.5));
    }

    /**
     * Math.sign(x)
     * ES2020 20.2.2.29
     */
    public static JSValue sign(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.signum(x));
    }

    /**
     * Math.sin(x)
     * ES2020 20.2.2.30
     */
    public static JSValue sin(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.sin(x));
    }

    /**
     * Math.sinh(x)
     * ES2020 20.2.2.31
     */
    public static JSValue sinh(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.sinh(x));
    }

    /**
     * Math.sqrt(x)
     * ES2020 20.2.2.32
     */
    public static JSValue sqrt(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.sqrt(x));
    }

    /**
     * Math.sumPrecise(iterable)
     * QuickJS extension.
     */
    public static JSValue sumPrecise(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue iterable = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue iteratorValue = JSIteratorHelper.getIterator(context, iterable);
        if (!(iteratorValue instanceof JSObject iterator)) {
            return context.throwTypeError("value is not iterable");
        }

        SumPreciseState state = new SumPreciseState();
        while (true) {
            JSObject nextResult = JSIteratorHelper.iteratorNext(iterator, context);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (nextResult == null) {
                return context.throwTypeError("Iterator result must be an object");
            }
            JSValue done = nextResult.get("done");
            if (JSTypeConversions.toBoolean(done).isBooleanTrue()) {
                break;
            }

            JSValue item = nextResult.get("value");
            if (!(item instanceof JSNumber number)) {
                context.throwTypeError("not a number");
                closeIterator(context, iterator);
                return JSUndefined.INSTANCE;
            }
            state.add(number.value());
        }
        return new JSNumber(state.getResult());
    }

    /**
     * Math.tan(x)
     * ES2020 20.2.2.33
     */
    public static JSValue tan(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.tan(x));
    }

    /**
     * Math.tanh(x)
     * ES2020 20.2.2.34
     */
    public static JSValue tanh(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        return new JSNumber(Math.tanh(x));
    }

    /**
     * Math.trunc(x)
     * ES2020 20.2.2.35
     */
    public static JSValue trunc(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSNumber(Double.NaN);
        }
        double x = JSTypeConversions.toNumber(context, args[0]).value();
        if (Double.isNaN(x) || Double.isInfinite(x) || x == 0.0) {
            return new JSNumber(x);
        }
        return new JSNumber(x > 0 ? Math.floor(x) : Math.ceil(x));
    }

    private enum SumPreciseStateType {
        FINITE,
        INFINITY,
        MINUS_INFINITY,
        NAN,
    }

    private static final class SumPreciseState {
        private final long[] acc;
        private int counter;
        private int nLimbs;
        private SumPreciseStateType state;

        private SumPreciseState() {
            this.acc = new long[SUM_PRECISE_ACC_LEN];
            this.counter = SUM_PRECISE_COUNTER_INIT;
            this.nLimbs = 0;
            this.state = SumPreciseStateType.FINITE;
        }

        private void add(double d) {
            long bits = Double.doubleToRawLongBits(d);
            int sign = (int) (bits >>> 63);
            int exponent = (int) ((bits >>> 52) & 0x7ffL);
            long mantissa = bits & ((1L << 52) - 1);

            if (exponent == 2047) {
                if (mantissa == 0) {
                    if (state == SumPreciseStateType.NAN
                            || (state == SumPreciseStateType.MINUS_INFINITY && sign == 0)
                            || (state == SumPreciseStateType.INFINITY && sign != 0)) {
                        state = SumPreciseStateType.NAN;
                    } else {
                        state = sign == 0 ? SumPreciseStateType.INFINITY : SumPreciseStateType.MINUS_INFINITY;
                    }
                } else {
                    state = SumPreciseStateType.NAN;
                }
                return;
            }

            int p;
            int shift;
            if (exponent == 0) {
                if (mantissa == 0) {
                    if (nLimbs == 0 && sign == 0) {
                        nLimbs = 1;
                    }
                    return;
                }
                p = 0;
                shift = 0;
            } else {
                mantissa |= (1L << 52);
                shift = exponent - 1;
                p = shift / SP_LIMB_BITS;
                shift = shift % SP_LIMB_BITS;
            }

            long a0 = (mantissa << shift) & SP_LIMB_MASK;
            long a1 = mantissa >>> (SP_LIMB_BITS - shift);
            if (sign == 0) {
                acc[p] += a0;
                acc[p + 1] += a1;
            } else {
                acc[p] -= a0;
                acc[p + 1] -= a1;
            }
            nLimbs = Math.max(nLimbs, p + 2);

            if (--counter == 0) {
                counter = SUM_PRECISE_COUNTER_INIT;
                renorm();
            }
        }

        private double getResult() {
            if (state != SumPreciseStateType.FINITE) {
                return switch (state) {
                    case INFINITY -> Double.POSITIVE_INFINITY;
                    case MINUS_INFINITY -> Double.NEGATIVE_INFINITY;
                    default -> Double.NaN;
                };
            }

            renorm();
            int n = nLimbs;
            if (n == 0) {
                return -0.0;
            }
            while (n > 0 && acc[n - 1] == 0) {
                n--;
            }
            if (n == 0) {
                return 0.0;
            }

            boolean isNeg = acc[n - 1] < 0;
            if (isNeg) {
                long carry = 1;
                for (int i = 0; i < n - 1; i++) {
                    long v = SP_LIMB_MASK - acc[i] + carry;
                    carry = v >> SP_LIMB_BITS;
                    acc[i] = v & SP_LIMB_MASK;
                }
                acc[n - 1] = -acc[n - 1] + carry - 1;
                while (n > 1 && acc[n - 1] == 0) {
                    n--;
                }
            }

            if (n == 1 && Long.compareUnsigned(acc[0], 1L << 52) < 0) {
                long bits = ((isNeg ? 1L : 0L) << 63) | acc[0];
                return Double.longBitsToDouble(bits);
            }

            int exponent = n * SP_LIMB_BITS;
            int p = n - 1;
            long mantissa = acc[p];
            int shift = Long.numberOfLeadingZeros(mantissa) - (64 - SP_LIMB_BITS);
            exponent = exponent - shift - 52;
            if (shift != 0) {
                mantissa <<= shift;
                if (p > 0) {
                    p--;
                    int shift1 = SP_LIMB_BITS - shift;
                    long nz = acc[p] & ((1L << shift1) - 1);
                    mantissa = mantissa | (acc[p] >>> shift1) | (nz != 0 ? 1L : 0L);
                }
            }

            long roundMask = (1L << SP_RND_BITS) - 1;
            long half = 1L << (SP_RND_BITS - 1);
            if ((mantissa & roundMask) == half) {
                while (p > 0) {
                    p--;
                    if (acc[p] != 0) {
                        mantissa |= 1L;
                        break;
                    }
                }
            }

            long addend = half - 1 + ((mantissa >> SP_RND_BITS) & 1L);
            mantissa = (mantissa + addend) >> SP_RND_BITS;
            if (mantissa == (1L << 53)) {
                exponent++;
            }
            if (exponent >= 2047) {
                long bits = ((isNeg ? 1L : 0L) << 63) | ((long) 2047 << 52);
                return Double.longBitsToDouble(bits);
            }

            mantissa &= ((1L << 52) - 1);
            long bits = ((isNeg ? 1L : 0L) << 63) | ((long) exponent << 52) | mantissa;
            return Double.longBitsToDouble(bits);
        }

        private void renorm() {
            long carry = 0;
            for (int i = 0; i < nLimbs; i++) {
                long v = acc[i] + carry;
                acc[i] = v & SP_LIMB_MASK;
                carry = v >> SP_LIMB_BITS;
            }
            if (carry != 0 && nLimbs < SUM_PRECISE_ACC_LEN) {
                acc[nLimbs++] = carry;
            }
        }
    }
}
