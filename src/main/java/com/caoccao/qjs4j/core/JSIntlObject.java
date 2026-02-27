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

import com.caoccao.qjs4j.exceptions.JSTypeErrorException;

import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of Intl object and Intl.* prototype methods.
 */
public final class JSIntlObject {
    private static final Set<String> AVAILABLE_LOCALE_LANGUAGES;
    private static final String[] FORMAT_STYLE_VALUES = {"short", "medium", "long", "full"};
    /**
     * Check if a collation value is valid/supported.
     */
    private static final Set<String> VALID_COLLATION_TYPES = Set.of(
            "big5han", "compat", "dict", "direct", "ducet", "emoji", "eor",
            "gb2312", "phonebk", "phonetic", "pinyin", "reformed", "stroke",
            "trad", "unihan", "zhuyin"
    );

    static {
        AVAILABLE_LOCALE_LANGUAGES = Arrays.stream(Locale.getAvailableLocales())
                .map(Locale::toLanguageTag)
                .filter(tag -> !tag.isEmpty() && !"und".equals(tag))
                .map(tag -> {
                    int dashIndex = tag.indexOf('-');
                    return dashIndex > 0 ? tag.substring(0, dashIndex).toLowerCase(Locale.ROOT) : tag.toLowerCase(Locale.ROOT);
                })
                .collect(Collectors.toSet());
    }

    private static List<String> canonicalizeLocaleList(JSContext context, JSValue localeValue) {
        List<String> localeTags = new ArrayList<>();
        if (localeValue == null || localeValue instanceof JSUndefined) {
            return localeTags;
        }
        if (localeValue instanceof JSNull) {
            throw new JSTypeErrorException("Cannot convert null to object");
        }
        if (localeValue instanceof JSArray localeArray) {
            for (long i = 0; i < localeArray.getLength(); i++) {
                localeTags.add(JSTypeConversions.toString(context, localeArray.get(i)).value());
            }
        } else {
            localeTags.add(JSTypeConversions.toString(context, localeValue).value());
        }
        LinkedHashSet<String> canonicalLocales = new LinkedHashSet<>();
        for (String localeTag : localeTags) {
            canonicalLocales.add(parseLocaleTag(localeTag).toLanguageTag());
        }
        return new ArrayList<>(canonicalLocales);
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
     * Getter for Intl.Collator.prototype.compare.
     * Per ECMA-402 §10.3.3, this is an accessor property that returns a bound compare function.
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
        JSNativeFunction boundCompare = new JSNativeFunction("", 2,
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
        resolvedOptions.set("locale", new JSString(collator.getLocale().toLanguageTag()));
        resolvedOptions.set("usage", new JSString(collator.getUsage()));
        resolvedOptions.set("sensitivity", new JSString(collator.getSensitivity()));
        resolvedOptions.set("ignorePunctuation", JSBoolean.valueOf(collator.getIgnorePunctuation()));
        resolvedOptions.set("collation", new JSString(collator.getCollation()));
        resolvedOptions.set("numeric", JSBoolean.valueOf(collator.getNumeric()));
        resolvedOptions.set("caseFirst", new JSString(collator.getCaseFirst()));
        return resolvedOptions;
    }

    public static JSValue createCollator(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            JSValue optionsValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

            // Parse unicode extension keys from locale tag (e.g. en-u-kn-true-co-phonebk)
            Map<String, String> unicodeExtensions = parseUnicodeExtensions(locale.toLanguageTag());

            // Read options in ECMA-402 spec order: usage, localeMatcher, collation, numeric, caseFirst, sensitivity, ignorePunctuation

            // 1. usage
            String usage = getOptionStringChecked(context, optionsValue, "usage");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            usage = normalizeOption(usage, "sort", "sort", "search");

            // 2. localeMatcher
            String localeMatcher = getOptionStringChecked(context, optionsValue, "localeMatcher");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (localeMatcher != null && !"lookup".equals(localeMatcher) && !"best fit".equals(localeMatcher)) {
                return context.throwRangeError("Invalid option value " + localeMatcher + " for property localeMatcher");
            }

            // 3. collation: option overrides extension key "co"
            String collation = getOptionStringChecked(context, optionsValue, "collation");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (collation == null && unicodeExtensions.containsKey("co")) {
                String extCollation = unicodeExtensions.get("co");
                if (isValidCollation(extCollation)) {
                    collation = extCollation;
                }
            }
            if (collation == null) {
                collation = "default";
            }

            // 4. numeric: option overrides extension key "kn"
            boolean numericSet = false;
            boolean numeric = false;
            if (optionsValue instanceof JSObject optionsObject) {
                JSValue numericValue = optionsObject.get(context, PropertyKey.fromString("numeric"));
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
                // "kn" with no value or "true" → true; "false" → false
                numeric = !"false".equals(extNumeric);
            }

            // 5. caseFirst: option overrides extension key "kf"
            String caseFirst = getOptionStringChecked(context, optionsValue, "caseFirst");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (caseFirst != null && !"upper".equals(caseFirst) && !"lower".equals(caseFirst) && !"false".equals(caseFirst)) {
                return context.throwRangeError("Invalid option value " + caseFirst + " for property caseFirst");
            }
            if (caseFirst == null && unicodeExtensions.containsKey("kf")) {
                String extCaseFirst = unicodeExtensions.get("kf");
                if ("upper".equals(extCaseFirst) || "lower".equals(extCaseFirst) || "false".equals(extCaseFirst)) {
                    caseFirst = extCaseFirst;
                }
            }
            if (caseFirst == null) {
                caseFirst = "false";
            }

            // 6. sensitivity
            String sensitivity = getOptionStringChecked(context, optionsValue, "sensitivity");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            sensitivity = normalizeOption(sensitivity, "variant", "base", "accent", "case", "variant");

            // 7. ignorePunctuation - Thai locale defaults to true per ECMA-402
            boolean ignorePunctuation = "th".equals(locale.getLanguage());
            if (optionsValue instanceof JSObject optionsObject2) {
                JSValue ipValue = optionsObject2.get(context, PropertyKey.fromString("ignorePunctuation"));
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                if (ipValue != null && !ipValue.isNullOrUndefined()) {
                    ignorePunctuation = JSTypeConversions.toBoolean(ipValue).value();
                }
            }

            // Build resolved locale: strip unicode extensions that were applied
            Locale resolvedLocale = stripUnicodeExtensions(locale);

            JSIntlCollator collator = new JSIntlCollator(resolvedLocale, sensitivity, usage, collation,
                    numeric, caseFirst, ignorePunctuation);
            collator.setPrototype(prototype);
            return collator;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue createDateTimeFormat(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            String dateStyleOption = getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "dateStyle");
            String timeStyleOption = getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "timeStyle");
            FormatStyle dateStyle = parseFormatStyle(dateStyleOption);
            FormatStyle timeStyle = parseFormatStyle(timeStyleOption);
            if (dateStyle == null && timeStyle == null) {
                dateStyle = FormatStyle.MEDIUM;
                timeStyle = FormatStyle.MEDIUM;
            }
            JSIntlDateTimeFormat dateTimeFormat = new JSIntlDateTimeFormat(locale, dateStyle, timeStyle);
            dateTimeFormat.setPrototype(prototype);
            return dateTimeFormat;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue createListFormat(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            String style = normalizeOption(
                    getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "style"),
                    "long",
                    "long", "short", "narrow");
            String type = normalizeOption(
                    getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "type"),
                    "conjunction",
                    "conjunction", "disjunction", "unit");
            JSIntlListFormat listFormat = new JSIntlListFormat(locale, style, type);
            listFormat.setPrototype(prototype);
            return listFormat;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue createLocale(JSContext context, JSObject prototype, JSValue[] args) {
        if (args.length == 0 || args[0].isNullOrUndefined()) {
            return context.throwTypeError("Intl.Locale requires a locale argument");
        }
        try {
            String localeTag = JSTypeConversions.toString(context, args[0]).value();
            Locale locale = parseLocaleTag(localeTag);
            JSIntlLocale intlLocale = new JSIntlLocale(locale, locale.toLanguageTag());
            intlLocale.setPrototype(prototype);
            return intlLocale;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue createNumberFormat(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            String style = normalizeOption(
                    getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "style"),
                    "decimal",
                    "decimal", "currency", "percent");
            String currency = getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "currency");
            if ("currency".equals(style) && (currency == null || currency.length() != 3)) {
                throw new IllegalArgumentException("Currency code must be a 3-letter ISO 4217 code");
            }
            JSIntlNumberFormat numberFormat = new JSIntlNumberFormat(locale, style, currency);
            numberFormat.setPrototype(prototype);
            return numberFormat;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue createPluralRules(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            String type = normalizeOption(
                    getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "type"),
                    "cardinal",
                    "cardinal", "ordinal");
            JSIntlPluralRules pluralRules = new JSIntlPluralRules(locale, type);
            pluralRules.setPrototype(prototype);
            return pluralRules;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue createRelativeTimeFormat(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            String style = normalizeOption(
                    getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "style"),
                    "long",
                    "long", "short", "narrow");
            String numeric = normalizeOption(
                    getOptionString(context, args.length > 1 ? args[1] : JSUndefined.INSTANCE, "numeric"),
                    "always",
                    "always", "auto");
            JSIntlRelativeTimeFormat relativeTimeFormat = new JSIntlRelativeTimeFormat(locale, style, numeric);
            relativeTimeFormat.setPrototype(prototype);
            return relativeTimeFormat;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue dateTimeFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context.throwTypeError("Intl.DateTimeFormat.prototype.format called on incompatible receiver");
        }
        double epochMillis;
        if (args.length == 0 || args[0].isUndefined()) {
            epochMillis = System.currentTimeMillis();
        } else if (args[0] instanceof JSDate jsDate) {
            epochMillis = jsDate.getTimeValue();
        } else {
            epochMillis = JSTypeConversions.toNumber(context, args[0]).value();
        }
        return new JSString(dateTimeFormat.format(epochMillis));
    }

    public static JSValue dateTimeFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context.throwTypeError("Intl.DateTimeFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        resolvedOptions.set("locale", new JSString(dateTimeFormat.getLocale().toLanguageTag()));
        resolvedOptions.set("timeZone", new JSString(java.time.ZoneId.systemDefault().getId()));
        if (dateTimeFormat.getDateStyle() != null) {
            resolvedOptions.set("dateStyle",
                    new JSString(dateTimeFormat.getDateStyle().name().toLowerCase(Locale.ROOT)));
        }
        if (dateTimeFormat.getTimeStyle() != null) {
            resolvedOptions.set("timeStyle",
                    new JSString(dateTimeFormat.getTimeStyle().name().toLowerCase(Locale.ROOT)));
        }
        return resolvedOptions;
    }

    public static JSValue getCanonicalLocales(JSContext context, JSValue thisArg, JSValue[] args) {
        try {
            List<String> localeList = canonicalizeLocaleList(
                    context,
                    args.length > 0 ? args[0] : JSUndefined.INSTANCE);
            JSArray localesArray = context.createJSArray();
            for (String localeTag : localeList) {
                localesArray.push(new JSString(localeTag));
            }
            return localesArray;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    private static String getOptionString(JSContext context, JSValue optionsValue, String key) {
        if (!(optionsValue instanceof JSObject optionsObject)) {
            return null;
        }
        JSValue rawValue = optionsObject.get(context, PropertyKey.fromString(key));
        if (context.hasPendingException()) {
            return null;
        }
        if (rawValue == null || rawValue instanceof JSUndefined) {
            return null;
        }
        return JSTypeConversions.toString(context, rawValue).value();
    }

    /**
     * Like getOptionString but checks for pending exceptions after property access.
     * Returns null if an exception occurred (caller must check context.hasPendingException()).
     */
    private static String getOptionStringChecked(JSContext context, JSValue optionsValue, String key) {
        if (!(optionsValue instanceof JSObject optionsObject)) {
            return null;
        }
        JSValue rawValue = optionsObject.get(context, PropertyKey.fromString(key));
        if (context.hasPendingException()) {
            return null;
        }
        if (rawValue == null || rawValue instanceof JSUndefined) {
            return null;
        }
        return JSTypeConversions.toString(context, rawValue).value();
    }

    private static boolean isValidCollation(String collation) {
        if (collation == null || collation.isEmpty()) {
            return false;
        }
        // "standard" and "search" are not valid for explicit collation option per ECMA-402
        // Only recognized CLDR collation types are valid
        return VALID_COLLATION_TYPES.contains(collation);
    }

    public static JSValue listFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlListFormat listFormat)) {
            return context.throwTypeError("Intl.ListFormat.prototype.format called on incompatible receiver");
        }
        if (args.length == 0 || !(args[0] instanceof JSArray listArray)) {
            return context.throwTypeError("Intl.ListFormat.prototype.format requires an array");
        }
        List<String> values = new ArrayList<>();
        for (long i = 0; i < listArray.getLength(); i++) {
            values.add(JSTypeConversions.toString(context, listArray.get(i)).value());
        }
        return new JSString(listFormat.format(values));
    }

    public static JSValue listFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlListFormat listFormat)) {
            return context.throwTypeError("Intl.ListFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        resolvedOptions.set("locale", new JSString(listFormat.getLocale().toLanguageTag()));
        resolvedOptions.set("style", new JSString(listFormat.getStyle()));
        resolvedOptions.set("type", new JSString(listFormat.getType()));
        return resolvedOptions;
    }

    public static JSValue localeGetBaseName(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.baseName called on incompatible receiver");
        }
        return new JSString(intlLocale.getBaseName());
    }

    public static JSValue localeGetLanguage(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.language called on incompatible receiver");
        }
        return new JSString(intlLocale.getLanguage());
    }

    public static JSValue localeGetRegion(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.region called on incompatible receiver");
        }
        return intlLocale.getRegion().isEmpty() ? JSUndefined.INSTANCE : new JSString(intlLocale.getRegion());
    }

    public static JSValue localeGetScript(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.script called on incompatible receiver");
        }
        return intlLocale.getScript().isEmpty() ? JSUndefined.INSTANCE : new JSString(intlLocale.getScript());
    }

    public static JSValue localeToString(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.toString called on incompatible receiver");
        }
        return new JSString(intlLocale.getTag());
    }

    private static String normalizeOption(String rawValue, String defaultValue, String... allowedValues) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        for (String allowedValue : allowedValues) {
            if (allowedValue.equals(rawValue)) {
                return allowedValue;
            }
        }
        throw new IllegalArgumentException("Invalid option value: " + rawValue);
    }

    public static JSValue numberFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlNumberFormat numberFormat)) {
            return context.throwTypeError("Intl.NumberFormat.prototype.format called on incompatible receiver");
        }
        double value = args.length > 0 ? JSTypeConversions.toNumber(context, args[0]).value() : 0;
        return new JSString(numberFormat.format(value));
    }

    public static JSValue numberFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlNumberFormat numberFormat)) {
            return context.throwTypeError("Intl.NumberFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        resolvedOptions.set("locale", new JSString(numberFormat.getLocale().toLanguageTag()));
        resolvedOptions.set("style", new JSString(numberFormat.getStyle()));
        if (numberFormat.getCurrency() != null) {
            resolvedOptions.set("currency", new JSString(numberFormat.getCurrency().toUpperCase(Locale.ROOT)));
        }
        return resolvedOptions;
    }

    private static FormatStyle parseFormatStyle(String rawStyle) {
        if (rawStyle == null) {
            return null;
        }
        String normalizedStyle = normalizeOption(rawStyle, null, FORMAT_STYLE_VALUES);
        return switch (normalizedStyle) {
            case "short" -> FormatStyle.SHORT;
            case "medium" -> FormatStyle.MEDIUM;
            case "long" -> FormatStyle.LONG;
            case "full" -> FormatStyle.FULL;
            default -> throw new IllegalArgumentException("Invalid format style: " + rawStyle);
        };
    }

    private static Locale parseLocaleTag(String localeTag) {
        if (localeTag == null) {
            throw new IllegalArgumentException("Invalid language tag: null");
        }
        String normalizedTag = localeTag.strip();
        if (normalizedTag.isEmpty()) {
            throw new IllegalArgumentException("Invalid language tag: " + localeTag);
        }
        Locale locale = Locale.forLanguageTag(normalizedTag);
        // "und" is valid as the undefined language tag per BCP 47
        if (locale.getLanguage().isEmpty() && !"und".equalsIgnoreCase(normalizedTag)
                && !normalizedTag.toLowerCase(Locale.ROOT).startsWith("und-")) {
            throw new IllegalArgumentException("Invalid language tag: " + localeTag);
        }
        return locale;
    }

    /**
     * Parse Unicode extension keys from a BCP 47 locale tag.
     * E.g., "en-u-kn-true-co-phonebk" → {kn=true, co=phonebk}
     * Attribute subtags (between -u- and the first key) are ignored.
     */
    private static Map<String, String> parseUnicodeExtensions(String localeTag) {
        Map<String, String> extensions = new HashMap<>();
        if (localeTag == null) {
            return extensions;
        }
        // Find the -u- extension, but not inside a private-use (-x-) tag
        String lowerTag = localeTag.toLowerCase(Locale.ROOT);
        int uIndex = -1;
        String[] parts = lowerTag.split("-");
        for (int i = 0; i < parts.length; i++) {
            // If we hit the private-use singleton "x", stop searching
            if ("x".equals(parts[i]) && i > 0) {
                break;
            }
            if ("u".equals(parts[i]) && i > 0) {
                uIndex = i;
                break;
            }
        }
        if (uIndex < 0) {
            return extensions;
        }
        // Parse key-value pairs after -u-
        // Keys are 2-character subtags, values are 3+ character subtags
        String currentKey = null;
        for (int i = uIndex + 1; i < parts.length; i++) {
            String part = parts[i];
            // Stop if we hit another singleton extension (single char that's not "x")
            if (part.length() == 1) {
                break;
            }
            if (part.length() == 2) {
                // This is a key
                if (currentKey != null && !extensions.containsKey(currentKey)) {
                    // Previous key had no value — treat as "true"
                    extensions.put(currentKey, "true");
                }
                currentKey = part;
            } else if (currentKey != null) {
                // This is a value for the current key
                extensions.put(currentKey, part);
                currentKey = null;
            }
            // else: attribute subtag (3+ chars before the first key), ignore
        }
        // Handle trailing key with no value
        if (currentKey != null && !extensions.containsKey(currentKey)) {
            extensions.put(currentKey, "true");
        }
        return extensions;
    }

    public static JSValue pluralRulesResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlPluralRules pluralRules)) {
            return context.throwTypeError("Intl.PluralRules.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        resolvedOptions.set("locale", new JSString(pluralRules.getLocale().toLanguageTag()));
        resolvedOptions.set("type", new JSString(pluralRules.getType()));
        JSArray categories = context.createJSArray();
        if ("ordinal".equals(pluralRules.getType())) {
            categories.push(new JSString("one"));
            categories.push(new JSString("two"));
            categories.push(new JSString("few"));
            categories.push(new JSString("other"));
        } else {
            categories.push(new JSString("one"));
            categories.push(new JSString("other"));
        }
        resolvedOptions.set("pluralCategories", categories);
        return resolvedOptions;
    }

    public static JSValue pluralRulesSelect(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlPluralRules pluralRules)) {
            return context.throwTypeError("Intl.PluralRules.prototype.select called on incompatible receiver");
        }
        double value = args.length > 0 ? JSTypeConversions.toNumber(context, args[0]).value() : Double.NaN;
        return new JSString(pluralRules.select(value));
    }

    public static JSValue relativeTimeFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlRelativeTimeFormat relativeTimeFormat)) {
            return context.throwTypeError("Intl.RelativeTimeFormat.prototype.format called on incompatible receiver");
        }
        if (args.length < 2) {
            return context.throwTypeError("Intl.RelativeTimeFormat.prototype.format requires value and unit");
        }
        try {
            double value = JSTypeConversions.toNumber(context, args[0]).value();
            String unit = JSTypeConversions.toString(context, args[1]).value();
            return new JSString(relativeTimeFormat.format(value, unit));
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue relativeTimeFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlRelativeTimeFormat relativeTimeFormat)) {
            return context.throwTypeError("Intl.RelativeTimeFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        resolvedOptions.set("locale", new JSString(relativeTimeFormat.getLocale().toLanguageTag()));
        resolvedOptions.set("style", new JSString(relativeTimeFormat.getStyle()));
        resolvedOptions.set("numeric", new JSString(relativeTimeFormat.getNumeric()));
        return resolvedOptions;
    }

    private static Locale resolveLocale(JSContext context, JSValue[] args, int index) {
        if (index >= args.length || args[index].isNullOrUndefined()) {
            return Locale.getDefault();
        }
        List<String> canonicalLocales = canonicalizeLocaleList(context, args[index]);
        if (canonicalLocales.isEmpty()) {
            return Locale.getDefault();
        }
        return Locale.forLanguageTag(canonicalLocales.get(0));
    }

    /**
     * Strip unicode extension subtags from a locale tag.
     * E.g., "en-US-u-co-standard" → locale for "en-US"
     */
    private static Locale stripUnicodeExtensions(Locale locale) {
        String tag = locale.toLanguageTag();
        String lowerTag = tag.toLowerCase(Locale.ROOT);
        // Don't strip if -u- only appears inside -x- (private-use) extension
        int xIndex = lowerTag.indexOf("-x-");
        int uIndex = lowerTag.indexOf("-u-");
        if (uIndex < 0 || (xIndex >= 0 && xIndex < uIndex)) {
            return locale;
        }
        // Find where the unicode extension ends (at the next singleton or end of string)
        String[] parts = tag.split("-");
        StringBuilder builder = new StringBuilder();
        boolean inUnicodeExt = false;
        boolean inPrivateUse = false;
        for (String part : parts) {
            String lowerPart = part.toLowerCase(Locale.ROOT);
            if (!inPrivateUse && part.length() == 1 && "x".equalsIgnoreCase(part)) {
                inPrivateUse = true;
                if (!builder.isEmpty()) {
                    builder.append("-");
                }
                builder.append(part);
                continue;
            }
            if (inPrivateUse) {
                // Everything in private-use is kept as-is
                if (!builder.isEmpty()) {
                    builder.append("-");
                }
                builder.append(part);
                continue;
            }
            if (part.length() == 1 && "u".equalsIgnoreCase(part)) {
                inUnicodeExt = true;
                continue;
            }
            if (inUnicodeExt) {
                if (part.length() == 1) {
                    // Start of another extension
                    inUnicodeExt = false;
                    if (!builder.isEmpty()) {
                        builder.append("-");
                    }
                    builder.append(part);
                }
                // Skip unicode extension subtags
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("-");
            }
            builder.append(part);
        }
        String strippedTag = builder.toString();
        if (strippedTag.isEmpty()) {
            return locale;
        }
        return Locale.forLanguageTag(strippedTag);
    }

    public static JSValue supportedLocalesOf(JSContext context, JSValue thisArg, JSValue[] args) {
        try {
            JSArray localesArray = context.createJSArray();
            if (args.length > 0) {
                for (String localeTag : canonicalizeLocaleList(context, args[0])) {
                    // Filter: only include locales whose language is available in the JVM
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
}
