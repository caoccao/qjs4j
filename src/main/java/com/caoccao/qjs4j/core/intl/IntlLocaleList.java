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

import java.util.*;
import java.util.stream.Collectors;
import com.caoccao.qjs4j.exceptions.JSException;
import static com.caoccao.qjs4j.core.intl.IntlBuiltins.*;

/** Canonicalization, resolution and supported-locale matching for Intl constructors. */
final class IntlLocaleList {
    private static final Set<String> AVAILABLE_LOCALE_LANGUAGES;

    static {
        AVAILABLE_LOCALE_LANGUAGES = Arrays.stream(Locale.getAvailableLocales()).map(Locale::toLanguageTag)
                .filter(tag -> !tag.isEmpty() && !"und".equals(tag)).map(tag -> {
                    int dashIndex = tag.indexOf('-');
                    return dashIndex > 0
                            ? tag.substring(0, dashIndex).toLowerCase(Locale.ROOT)
                            : tag.toLowerCase(Locale.ROOT);
                }).collect(Collectors.toSet());
    }

    private IntlLocaleList() {
    }

    static List<String> canonicalizeLocaleList(JSContext context, JSValue localeValue) {
        if (JSTypeChecking.isUndefinedOrMissing(localeValue)) {
            return List.of();
        }
        if (localeValue instanceof JSNull) {
            context.throwTypeError("Cannot convert null to object");
            return List.of();
        }
        if (localeValue instanceof JSString string) {
            return singletonLocale(context, string.value());
        }
        if (localeValue instanceof JSIntlLocale locale) {
            return singletonLocale(context, locale.getTag());
        }
        List<String> localeTags = new ArrayList<>();
        // Step 4: Let O be ToObject(locales)
        JSObject localeObject;
        if (localeValue instanceof JSObject obj) {
            localeObject = obj;
        } else {
            localeObject = JSTypeConversions.toObject(context, localeValue);
            if (context.hasPendingException()) {
                return localeTags;
            }
        }
        // Step 5: Let len be ToLength(Get(O, "length"))
        JSValue lengthValue = localeObject.get(PropertyKey.LENGTH);
        if (context.hasPendingException()) {
            return localeTags;
        }
        long length;
        if (JSTypeChecking.isUndefinedOrMissing(lengthValue)) {
            length = 0;
        } else {
            double lenNum = JSTypeConversions.toDouble(context, lengthValue);
            if (context.hasPendingException()) {
                return localeTags;
            }
            if (Double.isNaN(lenNum) || lenNum < 0) {
                length = 0;
            } else {
                length = (long) Math.min(lenNum, (double) Long.MAX_VALUE);
            }
        }
        // Step 6: Iterate from 0 to len
        for (long i = 0; i < length; i++) {
            PropertyKey pk = PropertyKey.fromString(String.valueOf(i));
            // Step 7b: Let kPresent be HasProperty(O, Pk)
            boolean kPresent;
            try {
                kPresent = localeObject.has(pk);
            } catch (JSException e) {
                // Proxy has trap may throw
                return localeTags;
            }
            if (context.hasPendingException()) {
                return localeTags;
            }
            if (!kPresent) {
                continue;
            }
            JSValue element = localeObject.get(pk);
            if (context.hasPendingException()) {
                return localeTags;
            }
            if (JSTypeChecking.isUndefinedOrMissing(element)) {
                context.throwTypeError("undefined is not a well-formed locale value");
                return localeTags;
            }
            if (!(element instanceof JSString) && !(element instanceof JSObject)) {
                context.throwTypeError(
                        JSTypeConversions.toJavaString(context, element) + " is not a well-formed currency value");
                return localeTags;
            }
            // Per ECMA-402, if element has [[InitializedLocale]], use its [[Locale]] tag
            if (element instanceof JSIntlLocale intlLocale) {
                localeTags.add(intlLocale.getTag());
            } else {
                localeTags.add(JSTypeConversions.toJavaString(context, element));
            }
            if (context.hasPendingException()) {
                return localeTags;
            }
        }
        LinkedHashSet<String> canonicalLocales = new LinkedHashSet<>();
        for (String localeTag : localeTags) {
            String canonical = canonicalTag(context, localeTag);
            if (canonical == null) {
                return List.of();
            }
            canonicalLocales.add(canonical);
        }
        return new ArrayList<>(canonicalLocales);
    }

    private static String canonicalTag(JSContext context, String tag) {
        try {
            return IntlLocaleTag.canonicalizeLocaleTag(tag);
        } catch (IllegalArgumentException e) {
            return context.rejectRangeError("Invalid language tag: " + tag);
        }
    }

    static JSValue getCanonicalLocales(JSContext context, JSValue thisArg, JSValue[] args) {
        try {
            List<String> localeList = canonicalizeLocaleList(context, JSValue.argumentOrUndefined(args, 0));
            JSArray localesArray = context.createJSArray();
            for (String localeTag : localeList) {
                localesArray.push(new JSString(localeTag));
            }
            return localesArray;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    static Locale resolveLocale(JSContext context, JSValue[] args, int index) {
        if (index >= args.length || args[index].isUndefined()) {
            return Locale.getDefault();
        }
        // Per ECMA-402 CanonicalizeLocaleList: null → TypeError
        if (args[index] instanceof JSNull) {
            return context.rejectTypeError("Cannot convert null to object");
        }
        List<String> canonicalLocales = canonicalizeLocaleList(context, args[index]);
        if (context.hasPendingException()) {
            return null;
        }
        if (canonicalLocales.isEmpty()) {
            return Locale.getDefault();
        }
        return Locale.forLanguageTag(canonicalLocales.get(0));
    }

    private static List<String> singletonLocale(JSContext context, String tag) {
        String canonical = canonicalTag(context, tag);
        return canonical == null ? List.of() : List.of(canonical);
    }

    static Locale supportedLocale(String tag) {
        Locale locale = Locale.forLanguageTag(tag);
        return AVAILABLE_LOCALE_LANGUAGES.contains(locale.getLanguage().toLowerCase(Locale.ROOT)) ? locale : null;
    }

    static JSValue supportedLocalesOf(JSContext context, JSValue thisArg, JSValue[] args) {
        try {
            // Validate and process options argument per SupportedLocales spec
            if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
                JSValue optionsArg = args[1];
                if (optionsArg instanceof JSNull) {
                    return context.throwTypeError("Cannot convert null to object");
                }
                // ToObject for primitives
                JSValue optionsObj;
                if (optionsArg instanceof JSObject) {
                    optionsObj = optionsArg;
                } else {
                    optionsObj = JSTypeConversions.toObject(context, optionsArg);
                    if (context.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                }
                // Validate localeMatcher
                JSValue matcherError = IntlFormatOptions.localeMatcherError(context, optionsObj, "supportedLocalesOf");
                if (matcherError != null) {
                    return matcherError;
                }
            }
            JSArray localesArray = context.createJSArray();
            if (args.length > 0) {
                List<String> canonicalLocales = canonicalizeLocaleList(context, args[0]);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                for (String localeTag : canonicalLocales) {
                    if (supportedLocale(localeTag) != null) {
                        localesArray.push(new JSString(localeTag));
                    }
                }
            }
            return localesArray;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }
}
