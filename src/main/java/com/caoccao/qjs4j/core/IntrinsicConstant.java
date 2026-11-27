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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.builtins.number.MathObject;
import com.caoccao.qjs4j.builtins.number.NumberPrototype;

import java.util.List;

/** Immutable values installed as non-writable, non-enumerable, non-configurable own properties. */
record IntrinsicConstant(PropertyKey key, JSValue value) {
    static final List<IntrinsicConstant> MATH = List.of(number("E", MathObject.E), number("LN10", MathObject.LN10),
            number("LN2", MathObject.LN2), number("LOG10E", MathObject.LOG10E), number("LOG2E", MathObject.LOG2E),
            number("PI", MathObject.PI), number("SQRT1_2", MathObject.SQRT1_2), number("SQRT2", MathObject.SQRT2));
    static final List<IntrinsicConstant> NUMBER = List.of(number("EPSILON", Math.ulp(1.0)),
            number("MAX_SAFE_INTEGER", (double) NumberPrototype.MAX_SAFE_INTEGER),
            number("MAX_VALUE", Double.MAX_VALUE),
            number("MIN_SAFE_INTEGER", (double) -NumberPrototype.MAX_SAFE_INTEGER),
            number("MIN_VALUE", Double.MIN_VALUE), number("NEGATIVE_INFINITY", Double.NEGATIVE_INFINITY),
            number("NaN", Double.NaN), number("POSITIVE_INFINITY", Double.POSITIVE_INFINITY));
    static final List<IntrinsicConstant> SYMBOL = List.of(symbol(JSSymbol.ITERATOR), symbol(JSSymbol.ASYNC_ITERATOR),
            symbol(JSSymbol.TO_STRING_TAG), symbol(JSSymbol.HAS_INSTANCE), symbol(JSSymbol.IS_CONCAT_SPREADABLE),
            symbol(JSSymbol.TO_PRIMITIVE), symbol(JSSymbol.MATCH), symbol(JSSymbol.MATCH_ALL), symbol(JSSymbol.REPLACE),
            symbol(JSSymbol.SEARCH), symbol(JSSymbol.SPLIT), symbol(JSSymbol.SPECIES), symbol(JSSymbol.UNSCOPABLES),
            symbol(JSSymbol.DISPOSE), symbol(JSSymbol.ASYNC_DISPOSE));

    static void install(JSObject target, List<IntrinsicConstant> constants) {
        for (IntrinsicConstant constant : constants) {
            target.defineProperty(constant.key, constant.value, PropertyDescriptor.DataState.None);
        }
    }

    private static IntrinsicConstant number(String name, double value) {
        return new IntrinsicConstant(PropertyKey.fromString(name), JSNumber.of(value));
    }

    private static IntrinsicConstant symbol(JSSymbol symbol) {
        String name = symbol.getDescription().substring("Symbol.".length());
        return new IntrinsicConstant(PropertyKey.fromString(name), symbol);
    }
}
