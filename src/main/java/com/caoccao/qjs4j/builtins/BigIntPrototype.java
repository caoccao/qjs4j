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

/**
 * Implementation of BigInt.prototype methods.
 * Based on ES2020 BigInt specification.
 */
public final class BigIntPrototype {

    /**
     * BigInt.prototype.toLocaleString(locales, options)
     * ES2020 20.2.3.2
     * Returns a localized string representation.
     * Simplified implementation - just calls toString.
     */
    public static JSValue toLocaleString(JSContext ctx, JSValue thisArg, JSValue[] args) {
        // For now, just delegate to toString with radix 10
        return toString(ctx, thisArg, new JSValue[]{new JSNumber(10)});
    }

    /**
     * BigInt.prototype.toString(radix)
     * ES2020 20.2.3.3
     * Returns a string representation of the BigInt in the specified radix.
     */
    public static JSValue toString(JSContext context, JSValue thisArg, JSValue[] args) {
        JSBigInt bigInt;

        if (thisArg instanceof JSBigInt bi) {
            bigInt = bi;
        } else if (thisArg instanceof JSObject obj) {
            JSValue primitiveValue = obj.get("[[PrimitiveValue]]");
            if (primitiveValue instanceof JSBigInt bi) {
                bigInt = bi;
            } else {
                return context.throwTypeError("BigInt.prototype.toString called on non-BigInt");
            }
        } else {
            return context.throwTypeError("BigInt.prototype.toString called on non-BigInt");
        }

        int radix = 10;
        if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
            if (args[0] instanceof JSNumber num) {
                radix = (int) num.value();
            }
        }

        if (radix < 2 || radix > 36) {
            return context.throwRangeError("toString() radix must be between 2 and 36");
        }

        return new JSString(bigInt.value().toString(radix));
    }

    /**
     * BigInt.prototype.valueOf()
     * ES2020 20.2.3.4
     * Returns the primitive value of the BigInt.
     */
    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        if (thisArg instanceof JSBigInt bigInt) {
            return bigInt;
        } else if (thisArg instanceof JSObject obj) {
            JSValue primitiveValue = obj.get("[[PrimitiveValue]]");
            if (primitiveValue instanceof JSBigInt bigInt) {
                return bigInt;
            }
        }

        return context.throwTypeError("BigInt.prototype.valueOf called on non-BigInt");
    }
}
