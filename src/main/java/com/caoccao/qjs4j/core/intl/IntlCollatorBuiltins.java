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
import static com.caoccao.qjs4j.core.intl.IntlBuiltins.*;

/** Intl.Collator construction, locale options and bound comparison. */
final class IntlCollatorBuiltins {
    /**
     * Check if a collation value is valid/supported.
     */
    private static final Set<String> VALID_COLLATION_TYPES = Set.of("big5han", "compat", "dict", "direct", "ducet",
            "emoji", "eor", "gb2312", "phonebk", "phonetic", "pinyin", "reformed", "searchjl", "stroke", "trad",
            "unihan", "zhuyin");

    private IntlCollatorBuiltins() {
    }

    /**
     * Build resolved locale tag including applied unicode extensions. Extension keys are only included when the
     * resolved value matches the original unicode extension value. Sorted alphabetically per BCP 47.
     */
    private static String buildResolvedLocaleTag(Locale baseLocale, String collation, boolean numeric, String caseFirst,
            Map<String, String> unicodeExtensions) {
        StringBuilder tag = new StringBuilder(IntlLocaleTag.stripUnicodeExtensions(baseLocale).toLanguageTag());
        int baseLength = tag.length();
        appendMatchingExtension(tag, baseLength, "co", collation, unicodeExtensions.get("co"));
        appendMatchingExtension(tag, baseLength, "kf", caseFirst, unicodeExtensions.get("kf"));
        if (numeric && unicodeExtensions.containsKey("kn") && !"false".equals(unicodeExtensions.get("kn"))) {
            tag.append(tag.length() == baseLength ? "-u-kn" : "-kn");
        }
        return tag.toString();
    }

    static JSValue collatorCompare(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlCollator collator)) {
            return context.throwTypeError("Intl.Collator.prototype.compare called on incompatible receiver");
        }
        String left = args.length > 0 ? JSTypeConversions.toString(context, args[0]).value() : "undefined";
        String right = args.length > 1 ? JSTypeConversions.toString(context, args[1]).value() : "undefined";
        return JSNumber.of(collator.compare(left, right));
    }

    /**
     * Getter for Intl.Collator.prototype.compare. Per ECMA-402 §10.3.3, this is an accessor property that returns a
     * bound compare function.
     */
    static JSValue collatorCompareGetter(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlCollator collator)) {
            return context.throwTypeError("Intl.Collator.prototype.compare called on incompatible receiver");
        }
        // Return the cached bound compare function, creating it on first access
        JSFunction cached = collator.getBoundCompareFunction();
        if (cached != null) {
            return cached;
        }
        JSNativeFunction boundCompare = new JSNativeFunction(context, "", 2,
                (ctx, thisVal, compareArgs) -> collatorCompare(ctx, collator, compareArgs));
        context.transferPrototype(boundCompare, JSFunction.NAME);
        collator.setBoundCompareFunction(boundCompare);
        return boundCompare;
    }

    static JSValue collatorResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlCollator collator)) {
            return context.throwTypeError("Intl.Collator.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        createDataProperty(resolvedOptions, "locale", new JSString(collator.getResolvedLocaleTag()));
        createDataProperty(resolvedOptions, "usage", new JSString(collator.getUsage()));
        createDataProperty(resolvedOptions, "sensitivity", new JSString(collator.getSensitivity()));
        createDataProperty(resolvedOptions, "ignorePunctuation", JSBoolean.valueOf(collator.getIgnorePunctuation()));
        createDataProperty(resolvedOptions, "collation", new JSString(collator.getCollation()));
        createDataProperty(resolvedOptions, "numeric", JSBoolean.valueOf(collator.getNumeric()));
        createDataProperty(resolvedOptions, "caseFirst", new JSString(collator.getCaseFirst()));
        return resolvedOptions;
    }

    static JSValue createCollator(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            JSValue optionsValue = JSValue.argumentOrUndefined(args, 1);

            // Parse unicode extension keys from locale tag (e.g. en-u-kn-true-co-phonebk)
            Map<String, String> unicodeExtensions = IntlLocaleTag.parseUnicodeExtensions(locale.toLanguageTag());

            // Read options in ECMA-402 spec order

            // 1. usage
            String usage = IntlFormatOptions.normalizedStringOption(context, optionsValue, "usage", "sort", "sort",
                    "search");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }

            // 2. localeMatcher
            String localeMatcher = IntlFormatOptions.readStringOption(context, optionsValue, "localeMatcher");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (!IntlFormatOptions.isValidLocaleMatcher(localeMatcher)) {
                return context.throwRangeError("Invalid option value " + localeMatcher + " for property localeMatcher");
            }

            // 3. collation: resolve from option and/or extension per ECMA-402
            String optionCollation = IntlFormatOptions.readStringOption(context, optionsValue, "collation");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            String extensionCollation = unicodeExtensions.get("co");
            String language = locale.getLanguage();
            String collation = supportedCollation(language, optionCollation)
                    ? optionCollation
                    : supportedCollation(language, extensionCollation) ? extensionCollation : "default";

            // 4. numeric: option overrides extension key "kn"
            boolean numericSet = false;
            boolean numeric = false;
            if (optionsValue instanceof JSObject optionsObject) {
                JSValue numericValue = optionsObject.get(PropertyKey.fromString("numeric"));
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                if (numericValue != null && !numericValue.isNullOrUndefined()) {
                    numeric = JSTypeConversions.toBoolean(numericValue).value();
                    numericSet = true;
                }
            }
            if (!numericSet && unicodeExtensions.containsKey("kn")) {
                String extNumeric = unicodeExtensions.get("kn");
                numeric = !"false".equals(extNumeric);
            }

            // 5. caseFirst: option overrides extension key "kf"
            String caseFirst = IntlFormatOptions.readStringOption(context, optionsValue, "caseFirst");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (caseFirst != null && !"upper".equals(caseFirst) && !"lower".equals(caseFirst)
                    && !"false".equals(caseFirst)) {
                return context.throwRangeError("Invalid option value " + caseFirst + " for property caseFirst");
            }
            if (caseFirst == null && unicodeExtensions.containsKey("kf")) {
                String extCaseFirst = unicodeExtensions.get("kf");
                if ("upper".equals(extCaseFirst) || "lower".equals(extCaseFirst) || "false".equals(extCaseFirst)) {
                    caseFirst = extCaseFirst;
                }
            }
            if (caseFirst == null) {
                // CLDR's Danish tailoring orders uppercase first, and ICU reports that as the resolved default.
                caseFirst = "da".equals(locale.getLanguage()) ? "upper" : "false";
            }

            // 6. sensitivity - default depends on usage per ECMA-402 Table 4
            String sensitivity = IntlFormatOptions.readStringOption(context, optionsValue, "sensitivity");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            String sensitivityDefault = "search".equals(usage) ? "base" : "variant";
            sensitivity = IntlFormatOptions.normalizeOption(sensitivity, sensitivityDefault, "base", "accent", "case",
                    "variant");

            // 7. ignorePunctuation - Thai locale defaults to true per ECMA-402
            boolean ignorePunctuation = "th".equals(locale.getLanguage());
            if (optionsValue instanceof JSObject optionsObject2) {
                JSValue ipValue = optionsObject2.get(PropertyKey.fromString("ignorePunctuation"));
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                if (ipValue != null && !ipValue.isNullOrUndefined()) {
                    ignorePunctuation = JSTypeConversions.toBoolean(ipValue).value();
                }
            }

            // Build resolved locale with applied unicode extensions
            Locale resolvedLocale = IntlLocaleTag.stripUnicodeExtensions(locale);
            String resolvedLocaleTag = buildResolvedLocaleTag(locale, collation, numeric, caseFirst, unicodeExtensions);

            JSIntlCollator collator = new JSIntlCollator(context, resolvedLocale, sensitivity, usage, collation,
                    numeric, caseFirst, ignorePunctuation, resolvedLocaleTag);
            JSObject resolvedPrototype = IntlFormatOptions.resolveIntlPrototype(context, prototype, "Collator");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            collator.setPrototype(resolvedPrototype);
            return collator;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    /**
     * Check if a collation type is supported for a given locale language. Based on CLDR data for common locale-specific
     * collation types.
     */
    private static boolean supportedCollation(String language, String collation) {
        if (collation == null || !VALID_COLLATION_TYPES.contains(collation)) {
            return false;
        }
        return switch (language) {
            case "de" -> "phonebk".equals(collation) || "eor".equals(collation);
            case "es" -> "trad".equals(collation) || "eor".equals(collation);
            case "zh" -> "pinyin".equals(collation) || "stroke".equals(collation) || "zhuyin".equals(collation)
                    || "big5han".equals(collation) || "gb2312".equals(collation) || "unihan".equals(collation);
            case "ja", "ko" -> "unihan".equals(collation) || "searchjl".equals(collation);
            case "si" -> "dict".equals(collation);
            case "sv" -> "reformed".equals(collation);
            case "en" -> "ducet".equals(collation) || "emoji".equals(collation) || "eor".equals(collation);
            case "ln" -> "phonetic".equals(collation);
            case "hi" -> "direct".equals(collation);
            default -> false;
        };
    }
}
