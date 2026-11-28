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

package com.caoccao.qjs4j.core.intl;

import com.caoccao.qjs4j.core.*;
import static com.caoccao.qjs4j.core.intl.IntlNumberOptions.*;

/** Intl.PluralRules construction, selection and resolved options. */
final class IntlPluralRulesBuiltins {
    private IntlPluralRulesBuiltins() {
    }

    static JSValue create(JSContext context, IntlFormatOptions.ConstructorLocale constructor, JSObject optionsObject) {
        String type = IntlFormatOptions.normalizedStringOption(context, optionsObject, PropertyKey.TYPE, "cardinal",
                "cardinal", "ordinal");
        DigitOptions digits = DigitOptions.read(context, optionsObject);
        if (digits == null) {
            return JSUndefined.INSTANCE;
        }
        String notation = digits.notation();
        int minimumIntegerDigits = digits.minimumIntegerDigits();
        DigitRange fractionDigits = digits.fractionDigits();

        boolean hasFractionDigits = fractionDigits.specified();
        fractionDigits = fractionDigits.resolveFraction(0, 3);

        DigitRange significantDigits = DigitRange.readSignificant(context, optionsObject);
        if (significantDigits == null) {
            return JSUndefined.INSTANCE;
        }
        if (significantDigits.specified()) {
            significantDigits = significantDigits.resolveSignificant();
        } else if ("compact".equals(notation) && !hasFractionDigits) {
            significantDigits = new DigitRange(1, 2);
        }

        if (readRoundingOptions(context, optionsObject, false) == null) {
            return JSUndefined.INSTANCE;
        }
        JSIntlPluralRules pluralRules = new JSIntlPluralRules(context, constructor.locale(), type, notation,
                minimumIntegerDigits, fractionDigits.minimum(), fractionDigits.maximum(), significantDigits.minimum(),
                significantDigits.maximum());
        return constructor.finish(pluralRules);
    }

    static JSValue pluralRulesResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlPluralRules pluralRules)) {
            return context.throwTypeError("Intl.PluralRules.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        resolvedOptions.createDataProperty(PropertyKey.LOCALE, new JSString(pluralRules.getLocale().toLanguageTag()));
        resolvedOptions.createDataProperty(PropertyKey.TYPE, new JSString(pluralRules.getType()));
        resolvedOptions.createDataProperty(PropertyKey.NOTATION, new JSString(pluralRules.getNotation()));
        resolvedOptions.createDataProperty(PropertyKey.MINIMUM_INTEGER_DIGITS,
                JSNumber.of(pluralRules.getMinimumIntegerDigits()));
        if (pluralRules.getMinimumSignificantDigits() != null) {
            defineDigitRange(resolvedOptions, true, pluralRules.getMinimumSignificantDigits(),
                    pluralRules.getMaximumSignificantDigits());
        } else {
            defineDigitRange(resolvedOptions, false, pluralRules.getMinimumFractionDigits(),
                    pluralRules.getMaximumFractionDigits());
        }
        JSArray categories = context.createJSArray();
        for (String category : pluralRules.getPluralCategories()) {
            categories.push(new JSString(category));
        }
        resolvedOptions.createDataProperty(PropertyKey.PLURAL_CATEGORIES, categories);
        return resolvedOptions;
    }

    static JSValue pluralRulesSelect(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlPluralRules pluralRules)) {
            return context.throwTypeError("Intl.PluralRules.prototype.select called on incompatible receiver");
        }
        double value = args.length > 0 ? JSTypeConversions.toNumber(context, args[0]).value() : Double.NaN;
        return new JSString(pluralRules.select(value));
    }

    static JSValue pluralRulesSelectRange(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlPluralRules pluralRules)) {
            return context.throwTypeError("Intl.PluralRules.prototype.selectRange called on incompatible receiver");
        }
        JSValue startValue = JSValue.argumentOrUndefined(args, 0);
        JSValue endValue = JSValue.argumentOrUndefined(args, 1);
        if (startValue instanceof JSUndefined || endValue instanceof JSUndefined) {
            return context.throwTypeError("Intl.PluralRules.prototype.selectRange requires start and end");
        }
        double start = JSTypeConversions.toNumber(context, startValue).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        double end = JSTypeConversions.toNumber(context, endValue).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (Double.isNaN(start) || Double.isNaN(end)) {
            return context.throwRangeError("NaN is not allowed in Intl.PluralRules.prototype.selectRange");
        }
        try {
            return new JSString(pluralRules.selectRange(start, end));
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }
}
