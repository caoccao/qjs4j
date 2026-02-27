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

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Implementation of BigInt.prototype methods.
 * Based on ES2020 BigInt specification.
 */
public final class BigIntPrototype {
    /**
     * BigInt.prototype.toLocaleString(locales, options)
     * ES2020 20.2.3.2
     * Returns a localized string representation using Intl.NumberFormat semantics.
     */
    public static JSValue toLocaleString(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg.asBigIntWithDownCast()
                .map(jsBigInt -> {
                    BigInteger value = jsBigInt.value();
                    Locale locale = Locale.getDefault();
                    if (args.length > 0 && !args[0].isNullOrUndefined()) {
                        String localeTag = JSTypeConversions.toString(context, args[0]).value();
                        if (context.hasPendingException()) {
                            return (JSValue) JSUndefined.INSTANCE;
                        }
                        try {
                            locale = Locale.forLanguageTag(localeTag);
                        } catch (Exception e) {
                            return (JSValue) context.throwRangeError("Invalid language tag: " + localeTag);
                        }
                    }
                    NumberFormat numberFormat = NumberFormat.getIntegerInstance(locale);
                    numberFormat.setGroupingUsed(false);
                    return (JSValue) new JSString(numberFormat.format(value));
                })
                .orElseGet(() -> context.throwTypeError("BigInt.prototype.toLocaleString requires that 'this' be a BigInt"));
    }

    /**
     * BigInt.prototype.toString(radix)
     * ES2020 20.2.3.3
     * Returns a string representation of the BigInt in the specified radix.
     */
    public static JSValue toString(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg.asBigIntWithDownCast()
                .map(jsBigInt -> {
                    int radix = 10;
                    if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
                        radix = (int) JSTypeConversions.toInteger(context, args[0]);
                        if (context.hasPendingException()) {
                            return JSUndefined.INSTANCE;
                        }
                    }
                    if (radix < 2 || radix > 36) {
                        return context.throwRangeError("toString() radix must be between 2 and 36");
                    }
                    return new JSString(jsBigInt.value().toString(radix));
                })
                .orElseGet(() -> context.throwTypeError("BigInt.prototype.toString requires that 'this' be a BigInt"));
    }

    /**
     * BigInt.prototype.valueOf()
     * ES2020 20.2.3.4
     * Returns the primitive value of the BigInt.
     */
    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg.asBigIntWithDownCast()
                .map(jsBigInt -> (JSValue) jsBigInt)
                .orElseGet(() -> context.throwTypeError("BigInt.prototype.valueOf requires that 'this' be a BigInt"));
    }
}
