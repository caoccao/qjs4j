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

import com.caoccao.qjs4j.exceptions.JSException;

import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of Intl object and Intl.* prototype methods.
 */
public final class JSIntlObject {
    private static final Set<String> AVAILABLE_LOCALE_LANGUAGES;

    static final Set<String> SANCTIONED_SIMPLE_UNITS;
    private static final List<String> SORTED_SIMPLE_UNITS = List.of("acre", "bit", "byte", "celsius", "centimeter",
            "day", "degree", "fahrenheit", "fluid-ounce", "foot", "gallon", "gigabit", "gigabyte", "gram", "hectare",
            "hour", "inch", "kilobit", "kilobyte", "kilogram", "kilometer", "liter", "megabit", "megabyte", "meter",
            "microsecond", "mile", "mile-scandinavian", "milliliter", "millimeter", "millisecond", "minute", "month",
            "nanosecond", "ounce", "percent", "petabyte", "pound", "second", "stone", "terabit", "terabyte", "week",
            "yard", "year");

    /**
     * Supplementary valid IANA timezone IDs not present in ZoneId.getAvailableZoneIds(). These are real IANA TZDB
     * entries that some JDK versions omit.
     */
    private static final String[] SUPPLEMENTARY_TIMEZONE_IDS = {"EST", "MST", "HST", // IANA backward-compat Zone
                                                                                     // entries
            "GMT+0", "GMT-0", "GMT0", // IANA backward-compat Link entries
            "ROC", // Republic of China → Asia/Taipei
    };

    /**
     * Lazy-initialized map of lowercased timezone ID → canonical casing. Built from ZoneId.getAvailableZoneIds() plus
     * supplementary valid IANA timezone IDs that Java's ZoneId does not include.
     */
    private static volatile Map<String, String> TIMEZONE_LOOKUP_MAP;

    /**
     * Check if a collation value is valid/supported.
     */
    private static final Set<String> VALID_COLLATION_TYPES = Set.of("big5han", "compat", "dict", "direct", "ducet",
            "emoji", "eor", "gb2312", "phonebk", "phonetic", "pinyin", "reformed", "searchjl", "stroke", "trad",
            "unihan", "zhuyin");

    static {
        SANCTIONED_SIMPLE_UNITS = Set.copyOf(SORTED_SIMPLE_UNITS);
    }

    static {
        AVAILABLE_LOCALE_LANGUAGES = Arrays.stream(Locale.getAvailableLocales()).map(Locale::toLanguageTag)
                .filter(tag -> !tag.isEmpty() && !"und".equals(tag)).map(tag -> {
                    int dashIndex = tag.indexOf('-');
                    return dashIndex > 0
                            ? tag.substring(0, dashIndex).toLowerCase(Locale.ROOT)
                            : tag.toLowerCase(Locale.ROOT);
                }).collect(Collectors.toSet());
    }

    static void appendMatchingExtension(StringBuilder tag, int baseLength, String key, String resolved,
            String requested) {
        if (resolved != null && resolved.equals(requested)) {
            tag.append(tag.length() == baseLength ? "-u-" : "-").append(key).append('-').append(resolved);
        }
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

    /**
     * Canonicalize a calendar identifier per ECMA-402: - ASCII-only lowercase (not locale-sensitive) - Legacy alias
     * mapping (e.g., "islamicc" → "islamic-civil") - Deprecated calendar fallback ("islamic", "islamic-rgsa" →
     * "islamic-civil")
     */
    static String canonicalizeCalendar(String calendar) {
        if (calendar == null) {
            return null;
        }
        // ASCII-only lowercase
        StringBuilder sb = new StringBuilder(calendar.length());
        for (int i = 0; i < calendar.length(); i++) {
            char c = calendar.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                sb.append((char) (c + 32));
            } else {
                sb.append(c);
            }
        }
        String lowered = sb.toString();
        // Legacy alias
        if ("islamicc".equals(lowered)) {
            return "islamic-civil";
        }
        if ("ethiopic-amete-alem".equals(lowered)) {
            return "ethioaa";
        }
        // Deprecated calendars: implementation-defined fallback
        if ("islamic".equals(lowered) || "islamic-rgsa".equals(lowered)) {
            return "islamic-civil";
        }
        return lowered;
    }

    private static List<String> canonicalizeLocaleList(JSContext context, JSValue localeValue) {
        List<String> localeTags = new ArrayList<>();
        if (localeValue == null || localeValue instanceof JSUndefined) {
            return localeTags;
        }
        if (localeValue instanceof JSNull) {
            context.throwTypeError("Cannot convert null to object");
            return localeTags;
        }
        // Per ECMA-402 §9.2.1 CanonicalizeLocaleList:
        // Step 3: If Type(locales) is String, wrap in array
        if (localeValue instanceof JSString) {
            localeTags.add(((JSString) localeValue).value());
        } else if (localeValue instanceof JSIntlLocale intlLocale) {
            // Handle Intl.Locale as top-level argument (use its [[Locale]] internal slot)
            localeTags.add(intlLocale.getTag());
        } else {
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
            if (lengthValue == null || lengthValue instanceof JSUndefined) {
                length = 0;
            } else {
                double lenNum = JSTypeConversions.toNumber(context, lengthValue).value();
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
                if (element == null || element instanceof JSUndefined) {
                    context.throwTypeError("undefined is not a well-formed locale value");
                    return localeTags;
                }
                if (!(element instanceof JSString) && !(element instanceof JSObject)) {
                    context.throwTypeError(JSTypeConversions.toString(context, element).value()
                            + " is not a well-formed currency value");
                    return localeTags;
                }
                // Per ECMA-402, if element has [[InitializedLocale]], use its [[Locale]] tag
                if (element instanceof JSIntlLocale intlLocale) {
                    localeTags.add(intlLocale.getTag());
                } else {
                    localeTags.add(JSTypeConversions.toString(context, element).value());
                }
                if (context.hasPendingException()) {
                    return localeTags;
                }
            }
        }
        LinkedHashSet<String> canonicalLocales = new LinkedHashSet<>();
        for (String localeTag : localeTags) {
            try {
                canonicalLocales.add(canonicalizeLocaleTag(localeTag));
            } catch (IllegalArgumentException e) {
                context.throwRangeError("Invalid language tag: " + localeTag);
                return new ArrayList<>();
            }
        }
        return new ArrayList<>(canonicalLocales);
    }

    static String canonicalizeLocaleTag(String localeTag) {
        return IntlLocaleTag.canonicalizeLocaleTag(localeTag);
    }

    public static JSValue collatorCompare(JSContext context, JSValue thisArg, JSValue[] args) {
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
    public static JSValue collatorCompareGetter(JSContext context, JSValue thisArg, JSValue[] args) {
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

    public static JSValue collatorResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
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

    public static JSValue createCollator(JSContext context, JSObject prototype, JSValue[] args) {
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
            String collation;
            if (optionCollation != null) {
                if (isValidCollation(optionCollation) && isSupportedCollation(locale.getLanguage(), optionCollation)) {
                    collation = optionCollation;
                } else if (isValidCollation(extensionCollation)
                        && isSupportedCollation(locale.getLanguage(), extensionCollation)) {
                    collation = extensionCollation;
                } else {
                    collation = "default";
                }
            } else if (isValidCollation(extensionCollation)
                    && isSupportedCollation(locale.getLanguage(), extensionCollation)) {
                collation = extensionCollation;
            } else {
                collation = "default";
            }

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
     * CreateDataProperty(O, P, V) — creates an own data property directly, bypassing [[Set]] so prototype chain
     * accessors are not triggered. Per ECMA-402 resolvedOptions() steps which use CreateDataPropertyOrThrow.
     */
    static void createDataProperty(JSObject obj, String key, JSValue value) {
        obj.defineProperty(PropertyKey.fromString(key), value, PropertyDescriptor.DataState.All);
    }

    /**
     * Default entry: uses required="any", defaults="date" per Intl.DateTimeFormat constructor spec.
     */
    public static JSValue createDateTimeFormat(JSContext context, JSObject prototype, JSValue[] args) {
        return createDateTimeFormat(context, prototype, args, "any", "date");
    }

    /**
     * Create DateTimeFormat with configurable ToDateTimeOptions behavior.
     *
     * @param required
     *            "date", "time", or "any" — which component types to check for needDefaults
     * @param defaults
     *            "date", "time", or "all" — which defaults to add when needDefaults is true
     */
    public static JSValue createDateTimeFormat(JSContext context, JSObject prototype, JSValue[] args, String required,
            String defaults) {
        return IntlDateTimeFormatBuiltins.createDateTimeFormat(context, prototype, args, required, defaults);
    }

    /**
     * Intl.DisplayNames constructor factory. Per ECMA-402 §12.1.1: OrdinaryCreateFromConstructor (step 2) runs before
     * locale/options processing. Options are read in order: localeMatcher → style → type → fallback → languageDisplay
     */
    public static JSValue createDisplayNames(JSContext context, JSObject prototype, JSValue[] args) {
        IntlFormatOptions.ConstructorLocale constructor = IntlFormatOptions.ConstructorLocale.resolve(context,
                prototype, args, "DisplayNames");
        if (constructor == null) {
            return JSUndefined.INSTANCE;
        }

        // Step 4: Get options object (required, must be an object per GetOptionsObject)
        JSValue optionsArg = JSValue.argumentOrUndefined(args, 1);
        if (optionsArg instanceof JSUndefined) {
            return context.throwTypeError("options must be an object");
        }
        if (!(optionsArg instanceof JSObject options)) {
            return context.throwTypeError(
                    "Cannot convert " + JSTypeConversions.toString(context, optionsArg).value() + " to object");
        }

        // localeMatcher
        JSValue matcherError = IntlFormatOptions.localeMatcherError(context, options);
        if (matcherError != null) {
            return matcherError;
        }

        // style (default: "long")
        String style = IntlFormatOptions.readStringOption(context, options, "style");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (style == null) {
            style = "long";
        } else if (!"narrow".equals(style) && !"short".equals(style) && !"long".equals(style)) {
            return context.throwRangeError("style must be \"narrow\", \"short\", or \"long\"");
        }

        // type (required — TypeError if undefined)
        String type = IntlFormatOptions.readStringOption(context, options, "type");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (type == null) {
            return context.throwTypeError("type is required");
        }
        if (!"language".equals(type) && !"region".equals(type) && !"script".equals(type) && !"currency".equals(type)
                && !"calendar".equals(type) && !"dateTimeField".equals(type)) {
            return context.throwRangeError(
                    "type must be \"language\", \"region\", \"script\", \"currency\", \"calendar\", or \"dateTimeField\"");
        }

        // fallback (default: "code")
        String fallback = IntlFormatOptions.readStringOption(context, options, "fallback");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (fallback == null) {
            fallback = "code";
        } else if (!"code".equals(fallback) && !"none".equals(fallback)) {
            return context.throwRangeError("fallback must be \"code\" or \"none\"");
        }

        // languageDisplay (default: "dialect")
        String languageDisplay = IntlFormatOptions.readStringOption(context, options, "languageDisplay");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (languageDisplay == null) {
            languageDisplay = "dialect";
        } else if (!"dialect".equals(languageDisplay) && !"standard".equals(languageDisplay)) {
            return context.throwRangeError("languageDisplay must be \"dialect\" or \"standard\"");
        }

        // Create instance and set prototype
        JSIntlDisplayNames displayNames = new JSIntlDisplayNames(context, constructor.locale(), style, type, fallback,
                languageDisplay);
        return constructor.finish(displayNames);
    }

    /**
     * Intl.DurationFormat constructor.
     */
    public static JSValue createDurationFormat(JSContext context, JSObject prototype, JSValue[] args) {
        return IntlDurationFormatBuiltins.createDurationFormat(context, prototype, args);
    }

    public static JSValue createListFormat(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // GetOptionsObject: validate options argument
            JSValue optionsArg = JSValue.argumentOrUndefined(args, 1);
            JSValue optionsObj = IntlFormatOptions.getOptionsObject(context, optionsArg);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // Read options in spec order: localeMatcher, type, style
            String localeMatcher = IntlFormatOptions.readStringOption(context, optionsObj, "localeMatcher");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!IntlFormatOptions.isValidLocaleMatcher(localeMatcher)) {
                return context.throwRangeError(
                        "Value " + localeMatcher + " out of range for Intl.ListFormat options property localeMatcher");
            }

            String type = IntlFormatOptions.normalizeOption(
                    IntlFormatOptions.readStringOption(context, optionsObj, "type"), "conjunction", "conjunction",
                    "disjunction", "unit");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            String style = IntlFormatOptions.normalizeOption(
                    IntlFormatOptions.readStringOption(context, optionsObj, "style"), "long", "long", "short",
                    "narrow");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            JSIntlListFormat listFormat = new JSIntlListFormat(context, locale, style, type);
            JSObject resolvedPrototype = IntlFormatOptions.resolveIntlPrototype(context, prototype, "ListFormat");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            listFormat.setPrototype(resolvedPrototype);
            return listFormat;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue createLocale(JSContext context, JSObject prototype, JSValue[] args) {
        return IntlLocaleBuiltins.createLocale(context, prototype, args);
    }

    public static JSValue createNumberFormat(JSContext context, JSObject prototype, JSValue[] args) {
        return IntlNumberFormatBuiltins.createNumberFormat(context, prototype, args);
    }

    static JSObject createPartObject(JSContext context, String type, String value) {
        JSObject part = context.createJSObject();
        part.set("type", new JSString(type));
        part.set("value", new JSString(value));
        return part;
    }

    static JSObject createPartObject(JSContext context, String type, String value, String source) {
        JSObject part = createPartObject(context, type, value);
        part.set("source", new JSString(source));
        return part;
    }

    public static JSValue createPluralRules(JSContext context, JSObject prototype, JSValue[] args) {
        return IntlNumberFormatBuiltins.createPluralRules(context, prototype, args);
    }

    public static JSValue createRelativeTimeFormat(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            IntlFormatOptions.ConstructorLocale constructor = IntlFormatOptions.ConstructorLocale.resolve(context,
                    prototype, args, "RelativeTimeFormat");
            if (constructor == null) {
                return JSUndefined.INSTANCE;
            }
            JSValue options = IntlFormatOptions.coerceOptionsObject(context, JSValue.argumentOrUndefined(args, 1));
            if (context.hasPendingException() || !(options instanceof JSObject optionsObject)) {
                return options;
            }

            String localeMatcher = IntlFormatOptions.normalizedStringOption(context, optionsObject, "localeMatcher",
                    "best fit", "lookup", "best fit");
            String numberingSystemOption = IntlFormatOptions.readStringOption(context, optionsObject,
                    "numberingSystem");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (numberingSystemOption != null) {
                numberingSystemOption = numberingSystemOption.toLowerCase(Locale.ROOT);
                if (!IntlFormatOptions.UNICODE_TYPE_PATTERN.matcher(numberingSystemOption).matches()) {
                    return context.throwRangeError("Invalid numberingSystem: " + numberingSystemOption);
                }
            }

            String style = IntlFormatOptions.normalizedStringOption(context, optionsObject, "style", "long", "long",
                    "short", "narrow");
            String numeric = IntlFormatOptions.normalizedStringOption(context, optionsObject, "numeric", "always",
                    "always", "auto");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            IntlFormatOptions.NumberingLocale numbering = IntlFormatOptions.NumberingLocale
                    .resolve(constructor.locale(), numberingSystemOption);
            JSIntlRelativeTimeFormat relativeTimeFormat = new JSIntlRelativeTimeFormat(context, numbering.locale(),
                    style, numeric, numbering.numberingSystem());
            return constructor.finish(relativeTimeFormat);
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue createSegmenter(JSContext context, JSObject prototype, JSObject segmentsPrototype,
            JSValue[] args) {
        try {
            JSObject resolvedPrototype = IntlFormatOptions.resolveIntlPrototype(context, prototype, "Segmenter");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            Locale locale = Locale.getDefault();
            if (args.length > 0 && !(args[0] instanceof JSUndefined)) {
                if (args[0] instanceof JSNull) {
                    return context.throwTypeError("Cannot convert null to object");
                }
                List<String> canonicalLocales = canonicalizeLocaleList(context, args[0]);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                for (String localeTag : canonicalLocales) {
                    Locale candidateLocale = Locale.forLanguageTag(localeTag);
                    String language = candidateLocale.getLanguage().toLowerCase(Locale.ROOT);
                    if (!language.isEmpty() && AVAILABLE_LOCALE_LANGUAGES.contains(language)) {
                        locale = candidateLocale;
                        break;
                    }
                }
            }

            JSValue options = IntlFormatOptions.coerceOptionsObject(context, JSValue.argumentOrUndefined(args, 1));
            if (context.hasPendingException() || !(options instanceof JSObject optionsObject)) {
                return options;
            }

            String localeMatcher = IntlFormatOptions.normalizedStringOption(context, optionsObject, "localeMatcher",
                    "best fit", "lookup", "best fit");
            String granularity = IntlFormatOptions.normalizedStringOption(context, optionsObject, "granularity",
                    "grapheme", "grapheme", "word", "sentence");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            JSIntlSegmenter segmenter = new JSIntlSegmenter(context, locale, granularity, segmentsPrototype);
            if (resolvedPrototype != null) {
                segmenter.setPrototype(resolvedPrototype);
            }
            return segmenter;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue dateTimeFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlDateTimeFormatBuiltins.dateTimeFormatFormat(context, thisArg, args);
    }

    /**
     * Getter for Intl.DateTimeFormat.prototype.format.
     */
    public static JSValue dateTimeFormatFormatGetter(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlDateTimeFormatBuiltins.dateTimeFormatFormatGetter(context, thisArg, args);
    }

    /**
     * Intl.DateTimeFormat.prototype.formatRange(startDate, endDate)
     */
    public static JSValue dateTimeFormatFormatRange(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlDateTimeFormatBuiltins.dateTimeFormatFormatRange(context, thisArg, args);
    }

    /**
     * Intl.DateTimeFormat.prototype.formatRangeToParts(startDate, endDate)
     */
    public static JSValue dateTimeFormatFormatRangeToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlDateTimeFormatBuiltins.dateTimeFormatFormatRangeToParts(context, thisArg, args);
    }

    /**
     * Intl.DateTimeFormat.prototype.formatToParts(date)
     */
    public static JSValue dateTimeFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlDateTimeFormatBuiltins.dateTimeFormatFormatToParts(context, thisArg, args);
    }

    public static JSValue dateTimeFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlDateTimeFormatBuiltins.dateTimeFormatResolvedOptions(context, thisArg, args);
    }

    static void defineStringOption(JSObject options, String key, String value) {
        if (value != null) {
            createDataProperty(options, key, new JSString(value));
        }
    }

    /**
     * Intl.DisplayNames.prototype.of(code)
     */
    public static JSValue displayNamesOf(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDisplayNames displayNames)) {
            return context.throwTypeError("Intl.DisplayNames.prototype.of called on incompatible receiver");
        }
        if (args.length == 0) {
            return context.throwRangeError("invalid code for DisplayNames: undefined");
        }
        String code = JSTypeConversions.toString(context, args[0]).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSValue result = displayNames.of(context, code);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return result;
    }

    /**
     * Intl.DisplayNames.prototype.resolvedOptions() Returns a new object with properties in the specified order.
     */
    public static JSValue displayNamesResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDisplayNames displayNames)) {
            return context
                    .throwTypeError("Intl.DisplayNames.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject result = context.createJSObject();
        result.set("locale", new JSString(displayNames.getLocale().toLanguageTag()));
        result.set("style", new JSString(displayNames.getStyle()));
        result.set("type", new JSString(displayNames.getType()));
        result.set("fallback", new JSString(displayNames.getFallback()));
        if ("language".equals(displayNames.getType())) {
            result.set("languageDisplay", new JSString(displayNames.getLanguageDisplay()));
        }
        return result;
    }

    /**
     * Intl.DurationFormat.prototype.format(duration)
     */
    public static JSValue durationFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlDurationFormatBuiltins.durationFormatFormat(context, thisArg, args);
    }

    /**
     * Intl.DurationFormat.prototype.formatToParts(duration)
     */
    public static JSValue durationFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlDurationFormatBuiltins.durationFormatFormatToParts(context, thisArg, args);
    }

    /**
     * Intl.DurationFormat.prototype.resolvedOptions()
     */
    public static JSValue durationFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlDurationFormatBuiltins.durationFormatResolvedOptions(context, thisArg, args);
    }

    private static JSValue formatRelativeTime(JSContext context, JSValue thisArg, JSValue[] args, boolean parts) {
        String method = parts ? "formatToParts" : "format";
        if (!(thisArg instanceof JSIntlRelativeTimeFormat relativeTimeFormat)) {
            return context
                    .throwTypeError("Intl.RelativeTimeFormat.prototype." + method + " called on incompatible receiver");
        }
        if (args.length < 2) {
            return context.throwTypeError("Intl.RelativeTimeFormat.prototype." + method + " requires value and unit");
        }

        double value = JSTypeConversions.toNumber(context, args[0]).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String unitValue = JSTypeConversions.toString(context, args[1]).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        try {
            return parts
                    ? relativeTimeFormat.formatToParts(context, value, unitValue)
                    : new JSString(relativeTimeFormat.format(value, unitValue));
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue getCanonicalLocales(JSContext context, JSValue thisArg, JSValue[] args) {
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

    /**
     * Get the default hourCycle for a locale by inspecting the locale's time format pattern.
     */
    static String getLocaleDefaultHourCycle(Locale locale) {
        java.text.DateFormat df = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, locale);
        if (df instanceof java.text.SimpleDateFormat sdf) {
            String pattern = sdf.toPattern();
            if (pattern.contains("K")) {
                return "h11"; // 0-11 hour (ja, ko)
            }
            if (pattern.contains("h")) {
                return "h12"; // 1-12 hour (en, es)
            }
            if (pattern.contains("H")) {
                return "h23"; // 0-23 hour (fr, de)
            }
            if (pattern.contains("k")) {
                return "h24"; // 1-24 hour
            }
        }
        return "h23"; // Default fallback
    }

    static Map<String, String> getTimezoneLookupMap() {
        if (TIMEZONE_LOOKUP_MAP == null) {
            synchronized (JSIntlObject.class) {
                if (TIMEZONE_LOOKUP_MAP == null) {
                    Map<String, String> map = new HashMap<>();
                    // Add all ZoneId timezone IDs
                    for (String id : ZoneId.getAvailableZoneIds()) {
                        map.putIfAbsent(id.toLowerCase(Locale.ROOT), id);
                    }
                    // Add supplementary valid IANA IDs
                    for (String id : SUPPLEMENTARY_TIMEZONE_IDS) {
                        map.putIfAbsent(id.toLowerCase(Locale.ROOT), id);
                    }
                    TIMEZONE_LOOKUP_MAP = map;
                }
            }
        }
        return TIMEZONE_LOOKUP_MAP;
    }

    /**
     * Check if a calendar name is supported by the Java platform.
     */
    static boolean isSupportedCalendar(String calendar) {
        if (calendar == null) {
            return false;
        }
        // Known supported calendars
        return switch (calendar) {
            case "buddhist", "chinese", "coptic", "dangi", "ethioaa", "ethiopic", "gregory", "hebrew", "indian",
                    "islamic-civil", "islamic-tbla", "islamic-umalqura", "iso8601", "japanese", "persian", "roc" ->
                true;
            default -> false;
        };
    }

    /**
     * Check if a collation type is supported for a given locale language. Based on CLDR data for common locale-specific
     * collation types.
     */
    private static boolean isSupportedCollation(String language, String collation) {
        if (collation == null || "default".equals(collation)) {
            return true;
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

    private static boolean isValidCollation(String collation) {
        if (collation == null || collation.isEmpty()) {
            return false;
        }
        return VALID_COLLATION_TYPES.contains(collation);
    }

    public static JSValue listFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlListFormat listFormat)) {
            return context.throwTypeError("Intl.ListFormat.prototype.format called on incompatible receiver");
        }
        JSValue listArg = JSValue.argumentOrUndefined(args, 0);
        List<String> values = stringListFromIterable(context, listArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(listFormat.format(values));
    }

    public static JSValue listFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlListFormat listFormat)) {
            return context.throwTypeError("Intl.ListFormat.prototype.formatToParts called on incompatible receiver");
        }
        JSValue listArg = JSValue.argumentOrUndefined(args, 0);
        List<String> values = stringListFromIterable(context, listArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        List<JSIntlDurationFormat.ListFormatPart> parts = JSIntlDurationFormat.formatListToParts(listFormat, values);
        JSArray result = context.createJSArray();
        for (int i = 0; i < parts.size(); i++) {
            JSIntlDurationFormat.ListFormatPart part = parts.get(i);
            JSObject partObj = createPartObject(context, part.type(), part.value());
            result.set(i, partObj);
        }
        return result;
    }

    public static JSValue listFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlListFormat listFormat)) {
            return context.throwTypeError("Intl.ListFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        // Use CreateDataProperty (defineProperty) instead of [[Set]] to avoid
        // triggering getter-only accessors on Object.prototype (per spec §9.1.6.3)
        createDataProperty(resolvedOptions, "locale", new JSString(listFormat.getLocale().toLanguageTag()));
        createDataProperty(resolvedOptions, "type", new JSString(listFormat.getType()));
        createDataProperty(resolvedOptions, "style", new JSString(listFormat.getStyle()));
        return resolvedOptions;
    }

    public static JSValue localeGetBaseName(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "baseName", JSIntlLocale::getBaseName);
    }

    public static JSValue localeGetCalendar(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "calendar", JSIntlLocale::getCalendar);
    }

    public static JSValue localeGetCalendars(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeGetCalendars(context, thisArg, args);
    }

    public static JSValue localeGetCaseFirst(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "caseFirst", JSIntlLocale::getCaseFirst);
    }

    public static JSValue localeGetCollation(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "collation", JSIntlLocale::getCollation);
    }

    public static JSValue localeGetCollations(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeGetCollations(context, thisArg, args);
    }

    public static JSValue localeGetFirstDayOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "firstDayOfWeek", JSIntlLocale::getFirstDayOfWeek);
    }

    public static JSValue localeGetHourCycle(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "hourCycle", JSIntlLocale::getHourCycle);
    }

    public static JSValue localeGetHourCycles(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeGetHourCycles(context, thisArg, args);
    }

    public static JSValue localeGetLanguage(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "language", JSIntlLocale::getLanguage);
    }

    public static JSValue localeGetNumberingSystem(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "numberingSystem", JSIntlLocale::getNumberingSystem);
    }

    public static JSValue localeGetNumberingSystems(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeGetNumberingSystems(context, thisArg, args);
    }

    public static JSValue localeGetNumeric(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeGetNumeric(context, thisArg, args);
    }

    public static JSValue localeGetRegion(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "region", locale -> nullIfEmpty(locale.getRegion()));
    }

    public static JSValue localeGetScript(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "script", locale -> nullIfEmpty(locale.getScript()));
    }

    public static JSValue localeGetTextInfo(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeGetTextInfo(context, thisArg, args);
    }

    public static JSValue localeGetTimeZones(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeGetTimeZones(context, thisArg, args);
    }

    public static JSValue localeGetVariants(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "variants",
                locale -> nullIfEmpty(locale.getVariant()));
    }

    public static JSValue localeGetWeekInfo(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeGetWeekInfo(context, thisArg, args);
    }

    public static JSValue localeMaximize(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.changeLikelySubtags(context, thisArg, true);
    }

    public static JSValue localeMinimize(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.changeLikelySubtags(context, thisArg, false);
    }

    public static JSValue localeToString(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlLocaleBuiltins.localeString(context, thisArg, "toString", JSIntlLocale::getTag);
    }

    static String nullIfEmpty(String value) {
        return (value != null && !value.isEmpty()) ? value : null;
    }

    public static JSValue numberFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlNumberFormatBuiltins.numberFormatFormat(context, thisArg, args);
    }

    public static JSValue numberFormatFormatGetter(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlNumberFormatBuiltins.numberFormatFormatGetter(context, thisArg, args);
    }

    public static JSValue numberFormatFormatRange(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlNumberFormatBuiltins.numberFormatFormatRange(context, thisArg, args);
    }

    public static JSValue numberFormatFormatRangeToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlNumberFormatBuiltins.numberFormatFormatRangeToParts(context, thisArg, args);
    }

    public static JSValue numberFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlNumberFormatBuiltins.numberFormatFormatToParts(context, thisArg, args);
    }

    public static JSValue numberFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlNumberFormatBuiltins.numberFormatResolvedOptions(context, thisArg, args);
    }

    public static JSValue pluralRulesResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlNumberFormatBuiltins.pluralRulesResolvedOptions(context, thisArg, args);
    }

    public static JSValue pluralRulesSelect(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlNumberFormatBuiltins.pluralRulesSelect(context, thisArg, args);
    }

    public static JSValue pluralRulesSelectRange(JSContext context, JSValue thisArg, JSValue[] args) {
        return IntlNumberFormatBuiltins.pluralRulesSelectRange(context, thisArg, args);
    }

    public static JSValue relativeTimeFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        return formatRelativeTime(context, thisArg, args, false);
    }

    public static JSValue relativeTimeFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return formatRelativeTime(context, thisArg, args, true);
    }

    public static JSValue relativeTimeFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlRelativeTimeFormat relativeTimeFormat)) {
            return context.throwTypeError(
                    "Intl.RelativeTimeFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        createDataProperty(resolvedOptions, "locale", new JSString(relativeTimeFormat.getLocale().toLanguageTag()));
        createDataProperty(resolvedOptions, "style", new JSString(relativeTimeFormat.getStyle()));
        createDataProperty(resolvedOptions, "numeric", new JSString(relativeTimeFormat.getNumeric()));
        createDataProperty(resolvedOptions, "numberingSystem", new JSString(relativeTimeFormat.getNumberingSystem()));
        return resolvedOptions;
    }

    public static Locale resolveLocale(JSContext context, JSValue[] args, int index) {
        if (index >= args.length || args[index].isUndefined()) {
            return Locale.getDefault();
        }
        // Per ECMA-402 CanonicalizeLocaleList: null → TypeError
        if (args[index] instanceof JSNull) {
            context.throwTypeError("Cannot convert null to object");
            return null;
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

    public static JSValue segmenterResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlSegmenter segmenter)) {
            return context.throwTypeError("Intl.Segmenter.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        createDataProperty(resolvedOptions, "locale", new JSString(segmenter.getLocale().toLanguageTag()));
        createDataProperty(resolvedOptions, "granularity", new JSString(segmenter.getGranularity()));
        return resolvedOptions;
    }

    public static JSValue segmenterSegment(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlSegmenter segmenter)) {
            return context.throwTypeError("Intl.Segmenter.prototype.segment called on incompatible receiver");
        }
        JSValue stringValue = JSValue.argumentOrUndefined(args, 0);
        JSString text = JSTypeConversions.toString(context, stringValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return segmenter.createSegments(text.value());
    }

    public static JSValue segmentsContaining(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlSegments segments)) {
            return context.throwTypeError("%Segments.prototype%.containing called on incompatible receiver");
        }
        JSValue indexValue = JSValue.argumentOrUndefined(args, 0);
        return segments.containing(context, indexValue);
    }

    public static JSValue segmentsIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlSegments segments)) {
            return context.throwTypeError("%Segments.prototype%[@@iterator] called on incompatible receiver");
        }
        return segments.createIterator(context);
    }

    /**
     * StringListFromIterable (ECMA-402 §13.5.1). Converts an iterable to a list of strings, throwing TypeError for
     * non-string elements.
     */
    private static List<String> stringListFromIterable(JSContext context, JSValue list) {
        List<String> result = new ArrayList<>();
        if (list == null || list instanceof JSUndefined) {
            return result;
        }

        // Get the iterator
        if (list instanceof JSString jsString) {
            // Strings are iterable character by character
            String s = jsString.value();
            for (int i = 0; i < s.length();) {
                int cp = s.codePointAt(i);
                result.add(new String(Character.toChars(cp)));
                i += Character.charCount(cp);
            }
            return result;
        }

        if (!(list instanceof JSObject listObj)) {
            context.throwTypeError(JSTypeConversions.toString(context, list).value() + " is not iterable");
            return result;
        }

        // Get [Symbol.iterator]
        JSValue iteratorMethod = listObj.get(PropertyKey.SYMBOL_ITERATOR);
        if (context.hasPendingException()) {
            return result;
        }
        if (!(iteratorMethod instanceof JSFunction iteratorFunc)) {
            context.throwTypeError("object is not iterable");
            return result;
        }

        // Call the iterator method
        JSValue iterator = iteratorFunc.call(context, list, JSValue.NO_ARGS);
        if (context.hasPendingException()) {
            return result;
        }
        if (!(iterator instanceof JSObject iteratorObj)) {
            context.throwTypeError("iterator must return an object");
            return result;
        }

        // Get next method
        JSValue nextMethod = iteratorObj.get(PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return result;
        }
        if (!(nextMethod instanceof JSFunction nextFunc)) {
            context.throwTypeError("iterator next is not a function");
            return result;
        }

        // Iterate
        while (true) {
            JSValue nextResult = nextFunc.call(context, iterator, JSValue.NO_ARGS);
            if (context.hasPendingException()) {
                return result;
            }
            if (!(nextResult instanceof JSObject nextResultObj)) {
                context.throwTypeError("iterator result is not an object");
                return result;
            }

            JSValue doneValue = nextResultObj.get(PropertyKey.DONE);
            if (context.hasPendingException()) {
                return result;
            }
            if (JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE) {
                break;
            }

            JSValue value = nextResultObj.get(PropertyKey.VALUE);
            if (context.hasPendingException()) {
                return result;
            }

            // Per spec, each element must be a string
            if (!(value instanceof JSString)) {
                // IteratorClose: call return() if it exists
                JSValue returnMethod = iteratorObj.get(PropertyKey.RETURN);
                if (returnMethod instanceof JSFunction returnFunc) {
                    returnFunc.call(context, iterator, JSValue.NO_ARGS);
                }
                context.throwTypeError("Iterable yielded " + JSTypeConversions.toString(context, value).value()
                        + " which is not a string");
                return result;
            }
            result.add(((JSString) value).value());
        }
        return result;
    }

    public static JSValue supportedLocalesOf(JSContext context, JSValue thisArg, JSValue[] args) {
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
                String localeMatcher = IntlFormatOptions.readStringOption(context, optionsObj, "localeMatcher");
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (!IntlFormatOptions.isValidLocaleMatcher(localeMatcher)) {
                    return context.throwRangeError("Value " + localeMatcher
                            + " out of range for supportedLocalesOf options property localeMatcher");
                }
            }
            JSArray localesArray = context.createJSArray();
            if (args.length > 0) {
                List<String> canonicalLocales = canonicalizeLocaleList(context, args[0]);
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                for (String localeTag : canonicalLocales) {
                    Locale locale = Locale.forLanguageTag(localeTag);
                    String language = locale.getLanguage().toLowerCase(Locale.ROOT);
                    if (!language.isEmpty() && AVAILABLE_LOCALE_LANGUAGES.contains(language)) {
                        localesArray.push(new JSString(localeTag));
                    }
                }
            }
            return localesArray;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    /**
     * Intl.supportedValuesOf(key) Returns a sorted array of supported unique values for the given key. See ECMA-402
     * §8.3.2
     */
    public static JSValue supportedValuesOf_Intl(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwRangeError("invalid key: undefined");
        }
        String key = JSTypeConversions.toString(context, args[0]).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        List<String> values;
        switch (key) {
            case "calendar" -> {
                // Per ECMA-402 and calendars-required-by-intl-era-monthcode test
                // "islamic" and "islamic-rgsa" are excluded because DateTimeFormat
                // resolves them to "islamic-civil" (they are not canonical calendar IDs)
                values = new ArrayList<>(List.of("buddhist", "chinese", "coptic", "dangi", "ethioaa", "ethiopic",
                        "gregory", "hebrew", "indian", "islamic-civil", "islamic-tbla", "islamic-umalqura", "iso8601",
                        "japanese", "persian", "roc"));
            }
            case "collation" -> {
                // Per ECMA-402: must NOT include "standard" or "search"
                // "compat" not accepted by any major Collator implementation
                values = new ArrayList<>(List.of("big5han", "dict", "direct", "ducet", "emoji", "eor", "gb2312",
                        "phonebk", "phonetic", "pinyin", "reformed", "searchjl", "stroke", "trad", "unihan", "zhuyin"));
            }
            case "currency" -> {
                Set<Currency> currencies = Currency.getAvailableCurrencies();
                values = currencies.stream().map(Currency::getCurrencyCode).sorted().collect(Collectors.toList());
            }
            case "numberingSystem" -> {
                values = new ArrayList<>(IntlFormatOptions.SUPPORTED_NUMBERING_SYSTEMS);
                Collections.sort(values);
            }
            case "timeZone" -> {
                Set<String> zoneIds = ZoneId.getAvailableZoneIds();
                // IANA canonical timezone names: include Etc/GMT+N, Etc/GMT-N, UTC
                // Exclude SystemV/, EST/MST/HST (deprecated short names), and non-canonical aliases
                Set<String> nonCanonical = new HashSet<>(Set.of("EST", "MST", "HST", "GMT0", "Etc/GMT0",
                        "Etc/Greenwich", "Etc/UCT", "Etc/Universal", "Etc/Zulu", "UCT", "GMT-0", "GMT+0", "Universal",
                        "Zulu", "Greenwich", "Etc/GMT-0", "Etc/GMT+0", "Etc/GMT", "Etc/UTC", "GMT", "US/Alaska",
                        "US/Aleutian", "US/Arizona", "US/Central", "US/East-Indiana", "US/Eastern", "US/Hawaii",
                        "US/Indiana-Starke", "US/Michigan", "US/Mountain", "US/Pacific", "US/Samoa", "Canada/Atlantic",
                        "Canada/Central", "Canada/Eastern", "Canada/Mountain", "Canada/Newfoundland", "Canada/Pacific",
                        "Canada/Saskatchewan", "Canada/Yukon", "Australia/ACT", "Australia/Canberra", "Australia/LHI",
                        "Australia/NSW", "Australia/North", "Australia/Queensland", "Australia/South",
                        "Australia/Tasmania", "Australia/Victoria", "Australia/West", "Australia/Yancowinna",
                        "Brazil/Acre", "Brazil/DeNoronha", "Brazil/East", "Brazil/West", "Chile/Continental",
                        "Chile/EasterIsland", "Mexico/BajaNorte", "Mexico/BajaSur", "Mexico/General", "NZ", "NZ-CHAT",
                        "Navajo", "PRC", "ROC", "ROK", "W-SU", "Cuba", "Egypt", "Eire", "Hongkong", "Iceland", "Iran",
                        "Israel", "Jamaica", "Japan", "Kwajalein", "Libya", "Poland", "Portugal", "Singapore", "Turkey",
                        "America/Atka", "America/Knox_IN", "Asia/Ashkhabad", "Asia/Calcutta", "Asia/Dacca",
                        "Asia/Istanbul", "Asia/Macao", "Asia/Thimbu", "Asia/Ujung_Pandang", "Asia/Ulan_Bator",
                        "Atlantic/Jan_Mayen", "Pacific/Truk"));
                Collections.addAll(nonCanonical, "Europe/Nicosia", "Asia/Ashkhabad", "Asia/Calcutta", "Asia/Choibalsan",
                        "Asia/Chongqing", "Asia/Chungking", "Asia/Dacca", "Asia/Harbin", "Asia/Istanbul",
                        "Asia/Kashgar", "Asia/Katmandu", "Asia/Macao", "Asia/Rangoon", "Asia/Saigon", "Asia/Tel_Aviv",
                        "Asia/Thimbu", "Asia/Ujung_Pandang", "Asia/Ulan_Bator", "Africa/Asmera", "Africa/Timbuktu",
                        "Antarctica/South_Pole", "Australia/ACT", "Australia/Canberra", "Australia/Currie",
                        "Australia/LHI", "Australia/NSW", "Australia/North", "Australia/Queensland", "Australia/South",
                        "Australia/Tasmania", "Australia/Victoria", "Australia/West", "Australia/Yancowinna",
                        "Pacific/Enderbury", "Pacific/Johnston", "Pacific/Ponape", "Pacific/Samoa", "Pacific/Truk",
                        "Pacific/Yap", "Europe/Belfast", "Europe/Kiev", "Europe/Tiraspol", "Europe/Uzhgorod",
                        "Europe/Zaporozhye", "America/Argentina/ComodRivadavia", "America/Atka", "America/Buenos_Aires",
                        "America/Catamarca", "America/Coral_Harbour", "America/Cordoba", "America/Ensenada",
                        "America/Fort_Wayne", "America/Godthab", "America/Indianapolis", "America/Jujuy",
                        "America/Knox_IN", "America/Louisville", "America/Mendoza", "America/Montreal",
                        "America/Nipigon", "America/Pangnirtung", "America/Porto_Acre", "America/Rainy_River",
                        "America/Rosario", "America/Santa_Isabel", "America/Shiprock", "America/Thunder_Bay",
                        "America/Virgin", "America/Yellowknife", "US/Alaska", "US/Aleutian", "US/Arizona", "US/Central",
                        "US/East-Indiana", "US/Eastern", "US/Hawaii", "US/Indiana-Starke", "US/Michigan", "US/Mountain",
                        "US/Pacific", "US/Samoa", "Atlantic/Faeroe", "Atlantic/Jan_Mayen", "Brazil/Acre",
                        "Brazil/DeNoronha", "Brazil/East", "Brazil/West", "CET", "CST6CDT", "Canada/Atlantic",
                        "Canada/Central", "Canada/Eastern", "Canada/Mountain", "Canada/Newfoundland", "Canada/Pacific",
                        "Canada/Saskatchewan", "Canada/Yukon", "Chile/Continental", "Chile/EasterIsland", "Cuba", "EET",
                        "EST", "EST5EDT", "Egypt", "Eire", "Etc/GMT", "Etc/GMT+0", "Etc/GMT-0", "Etc/GMT0",
                        "Etc/Greenwich", "Etc/UCT", "Etc/UTC", "Etc/Universal", "Etc/Zulu", "GB", "GB-Eire", "GMT",
                        "GMT+0", "GMT-0", "GMT0", "Greenwich", "HST", "Hongkong", "Iceland", "Iran", "Israel",
                        "Jamaica", "Japan", "Kwajalein", "Libya", "MET", "MST", "MST7MDT", "Mexico/BajaNorte",
                        "Mexico/BajaSur", "Mexico/General", "NZ", "NZ-CHAT", "Navajo", "PRC", "PST8PDT", "Poland",
                        "Portugal", "ROC", "ROK", "Singapore", "Turkey", "UCT", "Universal", "W-SU", "WET", "Zulu");
                values = zoneIds.stream()
                        .filter(id -> !id.startsWith("SystemV/") && !nonCanonical.contains(id) && !id.equals("EST5EDT")
                                && !id.equals("CST6CDT") && !id.equals("MST7MDT") && !id.equals("PST8PDT"))
                        .sorted().collect(Collectors.toList());
            }
            case "unit" -> values = SORTED_SIMPLE_UNITS;
            default -> {
                return context.throwRangeError("Invalid key : " + key);
            }
        }
        JSArray result = context.createJSArray();
        for (String value : values) {
            result.push(new JSString(value));
        }
        return result;
    }

}
