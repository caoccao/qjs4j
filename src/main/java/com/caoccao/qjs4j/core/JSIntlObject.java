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

    /**
     * CLDR likely subtags data for maximize/minimize. Maps language (or language-script, language-region) to full
     * locale tag.
     */
    private static final Map<String, String> LIKELY_SUBTAGS = new HashMap<>();

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

    static {
        // Common likely subtags from CLDR (subset covering test262 expectations)
        LIKELY_SUBTAGS.put("aa", "aa-Latn-ET");
        LIKELY_SUBTAGS.put("aae", "aae-Latn-IT");
        LIKELY_SUBTAGS.put("af", "af-Latn-ZA");
        LIKELY_SUBTAGS.put("am", "am-Ethi-ET");
        LIKELY_SUBTAGS.put("ar", "ar-Arab-EG");
        LIKELY_SUBTAGS.put("as", "as-Beng-IN");
        LIKELY_SUBTAGS.put("az", "az-Latn-AZ");
        LIKELY_SUBTAGS.put("be", "be-Cyrl-BY");
        LIKELY_SUBTAGS.put("bg", "bg-Cyrl-BG");
        LIKELY_SUBTAGS.put("bn", "bn-Beng-BD");
        LIKELY_SUBTAGS.put("bo", "bo-Tibt-CN");
        LIKELY_SUBTAGS.put("bs", "bs-Latn-BA");
        LIKELY_SUBTAGS.put("ca", "ca-Latn-ES");
        LIKELY_SUBTAGS.put("cs", "cs-Latn-CZ");
        LIKELY_SUBTAGS.put("cy", "cy-Latn-GB");
        LIKELY_SUBTAGS.put("da", "da-Latn-DK");
        LIKELY_SUBTAGS.put("de", "de-Latn-DE");
        LIKELY_SUBTAGS.put("el", "el-Grek-GR");
        LIKELY_SUBTAGS.put("en", "en-Latn-US");
        LIKELY_SUBTAGS.put("es", "es-Latn-ES");
        LIKELY_SUBTAGS.put("et", "et-Latn-EE");
        LIKELY_SUBTAGS.put("eu", "eu-Latn-ES");
        LIKELY_SUBTAGS.put("fa", "fa-Arab-IR");
        LIKELY_SUBTAGS.put("fi", "fi-Latn-FI");
        LIKELY_SUBTAGS.put("fil", "fil-Latn-PH");
        LIKELY_SUBTAGS.put("fr", "fr-Latn-FR");
        LIKELY_SUBTAGS.put("ga", "ga-Latn-IE");
        LIKELY_SUBTAGS.put("gl", "gl-Latn-ES");
        LIKELY_SUBTAGS.put("gu", "gu-Gujr-IN");
        LIKELY_SUBTAGS.put("ha", "ha-Latn-NG");
        LIKELY_SUBTAGS.put("he", "he-Hebr-IL");
        LIKELY_SUBTAGS.put("hi", "hi-Deva-IN");
        LIKELY_SUBTAGS.put("hr", "hr-Latn-HR");
        LIKELY_SUBTAGS.put("hu", "hu-Latn-HU");
        LIKELY_SUBTAGS.put("hy", "hy-Armn-AM");
        LIKELY_SUBTAGS.put("hyw", "hyw-Armn-AM");
        LIKELY_SUBTAGS.put("id", "id-Latn-ID");
        LIKELY_SUBTAGS.put("is", "is-Latn-IS");
        LIKELY_SUBTAGS.put("it", "it-Latn-IT");
        LIKELY_SUBTAGS.put("ja", "ja-Jpan-JP");
        LIKELY_SUBTAGS.put("jv", "jv-Latn-ID");
        LIKELY_SUBTAGS.put("ka", "ka-Geor-GE");
        LIKELY_SUBTAGS.put("kk", "kk-Cyrl-KZ");
        LIKELY_SUBTAGS.put("km", "km-Khmr-KH");
        LIKELY_SUBTAGS.put("kn", "kn-Knda-IN");
        LIKELY_SUBTAGS.put("ko", "ko-Kore-KR");
        LIKELY_SUBTAGS.put("ky", "ky-Cyrl-KG");
        LIKELY_SUBTAGS.put("lo", "lo-Laoo-LA");
        LIKELY_SUBTAGS.put("lt", "lt-Latn-LT");
        LIKELY_SUBTAGS.put("lv", "lv-Latn-LV");
        LIKELY_SUBTAGS.put("mk", "mk-Cyrl-MK");
        LIKELY_SUBTAGS.put("ml", "ml-Mlym-IN");
        LIKELY_SUBTAGS.put("mn", "mn-Cyrl-MN");
        LIKELY_SUBTAGS.put("mr", "mr-Deva-IN");
        LIKELY_SUBTAGS.put("ms", "ms-Latn-MY");
        LIKELY_SUBTAGS.put("my", "my-Mymr-MM");
        LIKELY_SUBTAGS.put("nb", "nb-Latn-NO");
        LIKELY_SUBTAGS.put("ne", "ne-Deva-NP");
        LIKELY_SUBTAGS.put("nl", "nl-Latn-NL");
        LIKELY_SUBTAGS.put("nn", "nn-Latn-NO");
        LIKELY_SUBTAGS.put("no", "no-Latn-NO");
        LIKELY_SUBTAGS.put("or", "or-Orya-IN");
        LIKELY_SUBTAGS.put("pa", "pa-Guru-IN");
        LIKELY_SUBTAGS.put("pap", "pap-Latn-CW");
        LIKELY_SUBTAGS.put("pl", "pl-Latn-PL");
        LIKELY_SUBTAGS.put("ps", "ps-Arab-AF");
        LIKELY_SUBTAGS.put("pt", "pt-Latn-BR");
        LIKELY_SUBTAGS.put("ro", "ro-Latn-RO");
        LIKELY_SUBTAGS.put("ru", "ru-Cyrl-RU");
        LIKELY_SUBTAGS.put("sd", "sd-Arab-PK");
        LIKELY_SUBTAGS.put("si", "si-Sinh-LK");
        LIKELY_SUBTAGS.put("sk", "sk-Latn-SK");
        LIKELY_SUBTAGS.put("sl", "sl-Latn-SI");
        LIKELY_SUBTAGS.put("so", "so-Latn-SO");
        LIKELY_SUBTAGS.put("sq", "sq-Latn-AL");
        LIKELY_SUBTAGS.put("sr", "sr-Cyrl-RS");
        LIKELY_SUBTAGS.put("sr-Latn", "sr-Latn-RS");
        LIKELY_SUBTAGS.put("sv", "sv-Latn-SE");
        LIKELY_SUBTAGS.put("sw", "sw-Latn-TZ");
        LIKELY_SUBTAGS.put("ta", "ta-Taml-IN");
        LIKELY_SUBTAGS.put("te", "te-Telu-IN");
        LIKELY_SUBTAGS.put("th", "th-Thai-TH");
        LIKELY_SUBTAGS.put("tk", "tk-Latn-TM");
        LIKELY_SUBTAGS.put("tr", "tr-Latn-TR");
        LIKELY_SUBTAGS.put("uk", "uk-Cyrl-UA");
        LIKELY_SUBTAGS.put("und", "en-Latn-US");
        LIKELY_SUBTAGS.put("ur", "ur-Arab-PK");
        LIKELY_SUBTAGS.put("uz", "uz-Latn-UZ");
        LIKELY_SUBTAGS.put("vi", "vi-Latn-VN");
        LIKELY_SUBTAGS.put("yo", "yo-Latn-NG");
        LIKELY_SUBTAGS.put("zh", "zh-Hans-CN");
        LIKELY_SUBTAGS.put("zh-Hant", "zh-Hant-TW");
        LIKELY_SUBTAGS.put("zh-TW", "zh-Hant-TW");
        LIKELY_SUBTAGS.put("zh-HK", "zh-Hant-HK");
        LIKELY_SUBTAGS.put("zu", "zu-Latn-ZA");

        // Additional language entries
        LIKELY_SUBTAGS.put("jbo", "jbo-Latn-001");
        LIKELY_SUBTAGS.put("hak", "hak-Hans-CN");
        LIKELY_SUBTAGS.put("hsn", "hsn-Hans-CN");

        // Script-specific entries
        LIKELY_SUBTAGS.put("en-Shaw", "en-Shaw-GB");

        // Undetermined language with script/region
        LIKELY_SUBTAGS.put("und-Thai", "th-Thai-TH");
        LIKELY_SUBTAGS.put("und-419", "es-Latn-419");
        LIKELY_SUBTAGS.put("und-150", "en-Latn-150");
        LIKELY_SUBTAGS.put("und-AT", "de-Latn-AT");
        LIKELY_SUBTAGS.put("und-AQ", "en-Latn-AQ");
        LIKELY_SUBTAGS.put("und-CW", "pap-Latn-CW");
        LIKELY_SUBTAGS.put("und-Cyrl-RO", "bg-Cyrl-RO");
    }

    /**
     * Add likely subtags to a locale (CLDR "maximize").
     */
    private static Locale addLikelySubtags(Locale locale) {
        String lang = locale.getLanguage();
        String script = locale.getScript();
        String region = locale.getCountry();
        String variant = locale.getVariant();

        // Use "und" for empty language in lookups (Java normalizes "und" to "")
        String lookupLang = lang.isEmpty() ? "und" : lang;

        // Try language-script-region first
        if (!script.isEmpty() && !region.isEmpty()) {
            if (!lang.isEmpty()) {
                return locale; // fully specified with real language, already maximized
            }
            // Language is "und" — try lookup
            String key = lookupLang + "-" + script + "-" + region;
            String full = LIKELY_SUBTAGS.get(key);
            if (full != null) {
                Locale fullLocale = Locale.forLanguageTag(full);
                Locale.Builder builder = new Locale.Builder().setLanguage(fullLocale.getLanguage()).setScript(script)
                        .setRegion(region);
                if (!variant.isEmpty()) {
                    builder.setVariant(variant);
                }
                return builder.build();
            }
            // Fall through to try language-script, language-region, language
        }

        // Try language-script
        if (!script.isEmpty()) {
            String key = lookupLang + "-" + script;
            String full = LIKELY_SUBTAGS.get(key);
            if (full != null) {
                Locale fullLocale = Locale.forLanguageTag(full);
                Locale.Builder builder = new Locale.Builder().setLanguage(fullLocale.getLanguage())
                        .setScript(fullLocale.getScript())
                        .setRegion(region.isEmpty() ? fullLocale.getCountry() : region);
                if (!variant.isEmpty()) {
                    builder.setVariant(variant);
                }
                return builder.build();
            }
        }

        // Try language-region
        if (!region.isEmpty()) {
            String key = lookupLang + "-" + region;
            String full = LIKELY_SUBTAGS.get(key);
            if (full != null) {
                Locale fullLocale = Locale.forLanguageTag(full);
                Locale.Builder builder = new Locale.Builder().setLanguage(fullLocale.getLanguage())
                        .setScript(script.isEmpty() ? fullLocale.getScript() : script).setRegion(region);
                if (!variant.isEmpty()) {
                    builder.setVariant(variant);
                }
                return builder.build();
            }
        }

        // Try language only
        String full = LIKELY_SUBTAGS.get(lookupLang);
        if (full != null) {
            Locale fullLocale = Locale.forLanguageTag(full);
            Locale.Builder builder = new Locale.Builder().setLanguage(fullLocale.getLanguage())
                    .setScript(script.isEmpty() ? fullLocale.getScript() : script)
                    .setRegion(region.isEmpty() ? fullLocale.getCountry() : region);
            if (!variant.isEmpty()) {
                builder.setVariant(variant);
            }
            return builder.build();
        }

        return locale;
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

    private static JSValue changeLikelySubtags(JSContext context, JSValue thisArg, boolean maximize) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError(maximize
                    ? "Intl.Locale.prototype.maximize called on incompatible receiver"
                    : "Intl.Locale.prototype.minimize called on incompatible receiver");
        }
        try {
            String tag = intlLocale.getTag();
            String baseTag = IntlLocaleTag.stripExtensions(tag);
            Locale baseLocale = Locale.forLanguageTag(baseTag);
            Locale updated = maximize ? addLikelySubtags(baseLocale) : removeLikelySubtags(baseLocale);
            String updatedTag = canonicalizeLocaleTag(updated.toLanguageTag());
            // Append extensions from the original tag
            String extensionSuffix = IntlLocaleTag.getExtensionSuffix(tag);
            if (!extensionSuffix.isEmpty()) {
                updatedTag = updatedTag + extensionSuffix;
            }
            JSIntlLocale result = new JSIntlLocale(context, updated, updatedTag, intlLocale.getCalendar(),
                    intlLocale.getCaseFirst(), intlLocale.getCollation(), intlLocale.getFirstDayOfWeek(),
                    intlLocale.getHourCycle(), intlLocale.getNumberingSystem(), intlLocale.getNumeric(),
                    intlLocale.isNumericSet());
            result.setPrototype(intlLocale.getPrototype());
            return result;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
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
        IntlFormatOptions.ConstructorLocale constructor = IntlFormatOptions.ConstructorLocale.resolve(context,
                prototype, args, "DurationFormat");
        if (constructor == null) {
            return JSUndefined.INSTANCE;
        }

        // Get options object (optional, defaults to undefined)
        JSValue optionsArg = JSValue.argumentOrUndefined(args, 1);
        JSObject options = null;
        if (optionsArg instanceof JSObject opt) {
            options = opt;
        } else if (!(optionsArg instanceof JSUndefined)) {
            return context.throwTypeError("Options is not an object");
        }

        // Read localeMatcher (not in resolvedOptions, but read from options)
        JSValue matcherError = IntlFormatOptions.localeMatcherError(context, options);
        if (matcherError != null) {
            return matcherError;
        }

        // Read numberingSystem from options
        String numberingSystemOption = options != null
                ? IntlFormatOptions.readStringOption(context, options, "numberingSystem")
                : null;
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!IntlFormatOptions.validateUnicodeType(context, "numberingSystem", numberingSystemOption)) {
            return context.getPendingException();
        }

        IntlFormatOptions.NumberingLocale numbering = IntlFormatOptions.NumberingLocale.resolve(constructor.locale(),
                numberingSystemOption);

        // Read style (default "short")
        String style = options != null ? IntlFormatOptions.readStringOption(context, options, "style") : null;
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (style == null) {
            style = "short";
        } else if (!"long".equals(style) && !"short".equals(style) && !"narrow".equals(style)
                && !"digital".equals(style)) {
            return context
                    .throwRangeError("Value " + style + " out of range for Intl.DurationFormat options property style");
        }

        // Process unit options
        String prevStyle = null;
        String[] unitStyles = new String[10];
        String[] unitDisplays = new String[10];

        for (int i = 0; i < JSIntlDurationFormat.UNIT_NAMES.length; i++) {
            String unitName = JSIntlDurationFormat.UNIT_NAMES[i];

            // Read unit style from options
            String unitStyle = options != null ? IntlFormatOptions.readStringOption(context, options, unitName) : null;
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // GetDurationUnitOptions: the style is validated before the display is read.
            String[] validStyles = JSIntlDurationFormat.getValidStylesForUnit(i);
            if (unitStyle != null && !Arrays.asList(validStyles).contains(unitStyle)) {
                return context.throwRangeError(
                        "Value " + unitStyle + " out of range for Intl.DurationFormat options property " + unitName);
            }
            boolean subsecondUnit = i >= 7;
            boolean prevNumeric = "numeric".equals(prevStyle) || "2-digit".equals(prevStyle);
            String displayDefault = "always";
            if (unitStyle == null) {
                if ("digital".equals(style)) {
                    unitStyle = JSIntlDurationFormat.getDigitalDefault(i);
                    if (i < 4 || subsecondUnit) {
                        displayDefault = "auto";
                    }
                } else if (prevNumeric) {
                    unitStyle = "numeric";
                    if (!"minutes".equals(unitName) && !"seconds".equals(unitName)) {
                        displayDefault = "auto";
                    }
                } else {
                    unitStyle = style;
                    displayDefault = "auto";
                }
            }
            // A numeric sub-second unit is the spec's "fractional" style: it is folded into the unit before it.
            boolean fractional = subsecondUnit && "numeric".equals(unitStyle);
            if (fractional) {
                displayDefault = "auto";
            }
            String display = options != null
                    ? IntlFormatOptions.readStringOption(context, options, unitName + "Display")
                    : null;
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (display == null) {
                display = displayDefault;
            } else if (!"auto".equals(display) && !"always".equals(display)) {
                return context.throwRangeError("Value " + display
                        + " out of range for Intl.DurationFormat options property " + unitName + "Display");
            }
            // ValidateDurationUnitStyle
            if (fractional && "always".equals(display)) {
                return context
                        .throwRangeError("Cannot use display \"always\" for " + unitName + " with \"numeric\" style");
            }
            if (prevNumeric && !"numeric".equals(unitStyle) && !"2-digit".equals(unitStyle)) {
                return context.throwRangeError("Cannot use style \"" + unitStyle + "\" for " + unitName
                        + " when previous unit uses \"" + prevStyle + "\" style");
            }
            if (prevNumeric && ("minutes".equals(unitName) || "seconds".equals(unitName))) {
                unitStyle = "2-digit";
            }

            unitStyles[i] = unitStyle;
            unitDisplays[i] = display;
            if (i >= 4) {
                prevStyle = unitStyle;
            }
        }

        // Read fractionalDigits
        Integer fractionalDigits = null;
        if (options != null) {
            JSValue fdValue = options.get(PropertyKey.fromString("fractionalDigits"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (fdValue != null && !(fdValue instanceof JSUndefined)) {
                double fdDouble = JSTypeConversions.toNumber(context, fdValue).value();
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                int fd = (int) fdDouble;
                if (Double.isNaN(fdDouble) || fd < 0 || fd > 9 || fdDouble != fd) {
                    return context.throwRangeError("fractionalDigits value is out of range");
                }
                fractionalDigits = fd;
            }
        }

        JSIntlDurationFormat durationFormat = new JSIntlDurationFormat(context, numbering.locale(),
                numbering.numberingSystem(), style, unitStyles, unitDisplays, fractionalDigits);
        return constructor.finish(durationFormat);
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
        if (args.length == 0 || args[0].isNullOrUndefined()) {
            return context.throwTypeError("Intl.Locale requires a locale argument");
        }

        // Step 1: Tag type check — must be String or Object, not boolean/number/symbol
        JSValue tagArg = args[0];
        if (!(tagArg instanceof JSString) && !(tagArg instanceof JSObject)) {
            String tagStr = JSTypeConversions.toString(context, tagArg).value();
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return context.throwTypeError(tagStr + " is an invalid tag value");
        }

        try {
            String localeTag;
            if (tagArg instanceof JSIntlLocale inputLocale) {
                localeTag = inputLocale.getTag();
            } else {
                localeTag = JSTypeConversions.toString(context, tagArg).value();
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
            }

            // Step 2: Options validation — null → TypeError, non-object → TypeError
            JSValue optionsValue = JSValue.argumentOrUndefined(args, 1);
            if (!(optionsValue instanceof JSUndefined)) {
                if (optionsValue instanceof JSNull) {
                    return context.throwTypeError("Cannot convert null to object");
                }
                if (!(optionsValue instanceof JSObject)) {
                    optionsValue = JSTypeConversions.toObject(context, optionsValue);
                    if (context.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                }
            }

            // Read options in spec order
            String languageOpt = IntlFormatOptions.readStringOption(context, optionsValue, "language");
            String scriptOpt = IntlFormatOptions.readStringOption(context, optionsValue, "script");
            String regionOpt = IntlFormatOptions.readStringOption(context, optionsValue, "region");
            String variantsOpt = IntlFormatOptions.readStringOption(context, optionsValue, "variants");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // Validate language/script/region/variants options
            if (languageOpt != null && !IntlLocaleTag.isValidLanguageSubtag(languageOpt)) {
                return context.throwRangeError("invalid language: " + languageOpt);
            }
            if (scriptOpt != null && !IntlLocaleTag.isValidScriptSubtag(scriptOpt)) {
                return context.throwRangeError("invalid script: " + scriptOpt);
            }
            if (regionOpt != null && !IntlLocaleTag.isValidRegionSubtag(regionOpt)) {
                return context.throwRangeError("invalid region: " + regionOpt);
            }
            if (variantsOpt != null && !IntlLocaleTag.isValidVariantsSubtag(variantsOpt)) {
                return context.throwRangeError("invalid variants: " + variantsOpt);
            }

            String calendarOpt = IntlFormatOptions.readStringOption(context, optionsValue, "calendar");
            String collationOpt = IntlFormatOptions.readStringOption(context, optionsValue, "collation");
            String hourCycleOpt = IntlFormatOptions.readStringOption(context, optionsValue, "hourCycle");
            String caseFirstOpt = IntlFormatOptions.readStringOption(context, optionsValue, "caseFirst");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // Read numeric as boolean (per spec: GetOption with "boolean" type)
            boolean numericOpt = false;
            boolean numericOptSet = false;
            if (optionsValue instanceof JSObject numericOptsObj) {
                JSValue numericRaw = numericOptsObj.get(PropertyKey.fromString("numeric"));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (numericRaw != null && !(numericRaw instanceof JSUndefined)) {
                    numericOpt = JSTypeConversions.toBoolean(numericRaw) == JSBoolean.TRUE;
                    numericOptSet = true;
                }
            }

            String numberingSystemOpt = IntlFormatOptions.readStringOption(context, optionsValue, "numberingSystem");
            String firstDay = IntlFormatOptions.readStringOption(context, optionsValue, "firstDayOfWeek");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String firstDayOfWeekOpt = firstDay == null ? null : weekdayToString(firstDay);
            if (firstDayOfWeekOpt != null && !"true".equals(firstDayOfWeekOpt)
                    && !IntlFormatOptions.UNICODE_TYPE_PATTERN.matcher(firstDayOfWeekOpt).matches()) {
                return context.throwRangeError("invalid firstDayOfWeek: " + firstDay);
            }

            // Validate extension values
            if (calendarOpt != null && (calendarOpt.isEmpty()
                    || !IntlFormatOptions.UNICODE_TYPE_PATTERN.matcher(calendarOpt).matches())) {
                return context.throwRangeError("invalid calendar: " + calendarOpt);
            }
            if (collationOpt != null && (collationOpt.isEmpty()
                    || !IntlFormatOptions.UNICODE_TYPE_PATTERN.matcher(collationOpt).matches())) {
                return context.throwRangeError("invalid collation: " + collationOpt);
            }
            // hourCycle must be exactly one of h11, h12, h23, h24
            if (hourCycleOpt != null) {
                if (!"h11".equals(hourCycleOpt) && !"h12".equals(hourCycleOpt) && !"h23".equals(hourCycleOpt)
                        && !"h24".equals(hourCycleOpt)) {
                    return context.throwRangeError("invalid hourCycle: " + hourCycleOpt);
                }
            }
            if (caseFirstOpt != null && !"upper".equals(caseFirstOpt) && !"lower".equals(caseFirstOpt)
                    && !"false".equals(caseFirstOpt)) {
                return context.throwRangeError("invalid caseFirst: " + caseFirstOpt);
            }
            if (numberingSystemOpt != null && (numberingSystemOpt.isEmpty()
                    || !IntlFormatOptions.UNICODE_TYPE_PATTERN.matcher(numberingSystemOpt).matches())) {
                return context.throwRangeError("invalid numberingSystem: " + numberingSystemOpt);
            }

            // Canonicalize calendar values
            calendarOpt = canonicalizeCalendar(calendarOpt);

            Locale locale = IntlLocaleTag.parseLocaleTag(localeTag);

            // Strip Java-added private use for grandfathered tags.
            // Java maps grandfathered tags like "cel-gaulish" to "xtg-x-cel-gaulish"
            // but the x-cel-gaulish part is not from user input and must be removed.
            if (!localeTag.toLowerCase(Locale.ROOT).contains("-x-") && locale.getExtension('x') != null) {
                locale = new Locale.Builder().setLocale(locale).setExtension('x', null).build();
            }

            // Apply language/script/region/variants overrides from options
            Locale.Builder builder = new Locale.Builder().setLocale(locale);
            if (languageOpt != null && !languageOpt.isEmpty()) {
                builder.setLanguage(languageOpt);
            }
            if (scriptOpt != null && !scriptOpt.isEmpty()) {
                builder.setScript(scriptOpt);
            }
            if (regionOpt != null && !regionOpt.isEmpty()) {
                builder.setRegion(regionOpt);
            }
            if (variantsOpt != null) {
                // Replace existing variants with the option value
                builder.setVariant(variantsOpt);
            }

            // Parse existing unicode extensions from locale tag — collect ALL keys
            Map<String, String> existingExtensions = IntlLocaleTag.parseUnicodeExtensions(locale.toLanguageTag());

            // Use TreeMap for sorted key order (Unicode BCP 47 requires alphabetical)
            TreeMap<String, String> extMap = new TreeMap<>(existingExtensions);

            // Apply option overrides
            putLocaleOption(extMap, "ca", calendarOpt);
            putLocaleOption(extMap, "co", collationOpt);
            putLocaleOption(extMap, "fw", firstDayOfWeekOpt);
            putLocaleOption(extMap, "hc", hourCycleOpt);
            putLocaleOption(extMap, "kf", caseFirstOpt);
            if (numericOptSet) {
                extMap.put("kn", Boolean.toString(numericOpt));
            }
            putLocaleOption(extMap, "nu", numberingSystemOpt);

            // Canonicalize calendar from tag extension if present
            String extCa = extMap.get("ca");
            if (extCa != null) {
                extMap.put("ca", canonicalizeCalendar(extCa));
            }

            // Build unicode extension string (attributes first, then sorted key-value pairs)
            // Extract attributes stored under special "_attr" key
            String attrs = extMap.remove("_attr");
            StringBuilder uExt = new StringBuilder();
            if (attrs != null && !attrs.isEmpty()) {
                uExt.append("-").append(attrs);
            }
            for (Map.Entry<String, String> entry : extMap.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                uExt.append("-").append(key);
                if (value != null && !value.isEmpty() && !"true".equals(value)) {
                    uExt.append("-").append(value);
                }
            }
            if (!uExt.isEmpty()) {
                builder.setExtension('u', uExt.substring(1)); // remove leading '-'
            }

            locale = builder.build();
            String rawTag = locale.toLanguageTag();
            // Java omits "und" prefix for undetermined language with only extensions/private-use
            if (locale.getLanguage().isEmpty() && rawTag.startsWith("x-")) {
                rawTag = "und-" + rawTag;
            }
            String tag = canonicalizeLocaleTag(rawTag);

            // Compute final values for the JSIntlLocale fields from the extMap
            String calendar = localeOption(extMap, "ca", null);
            String caseFirst = localeOption(extMap, "kf", "");
            String collation = localeOption(extMap, "co", null);
            String firstDayOfWeek = localeOption(extMap, "fw", "");
            String hourCycle = localeOption(extMap, "hc", null);
            String numberingSystem = localeOption(extMap, "nu", null);
            String finalKn = extMap.get("kn");
            boolean numericSet = finalKn != null;
            boolean numeric = "true".equals(finalKn) || (finalKn != null && finalKn.isEmpty());

            JSIntlLocale intlLocale = new JSIntlLocale(context, locale, tag, calendar, caseFirst, collation,
                    firstDayOfWeek, hourCycle, numberingSystem, numeric, numericSet);
            JSObject resolvedPrototype = IntlFormatOptions.resolveIntlPrototype(context, prototype, "Locale");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            intlLocale.setPrototype(resolvedPrototype);
            return intlLocale;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
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
        return formatDuration(context, thisArg, args, false);
    }

    /**
     * Intl.DurationFormat.prototype.formatToParts(duration)
     */
    public static JSValue durationFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return formatDuration(context, thisArg, args, true);
    }

    /**
     * Intl.DurationFormat.prototype.resolvedOptions()
     */
    public static JSValue durationFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDurationFormat df)) {
            return context
                    .throwTypeError("Intl.DurationFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject result = context.createJSObject();
        result.set("locale", new JSString(df.getLocale().toLanguageTag()));
        result.set("numberingSystem", new JSString(df.getNumberingSystem()));
        result.set("style", new JSString(df.getStyle()));

        // Add unit styles and displays in table order
        for (int i = 0; i < JSIntlDurationFormat.UNIT_NAMES.length; i++) {
            String unitName = JSIntlDurationFormat.UNIT_NAMES[i];
            result.set(unitName, new JSString(df.getUnitStyle(i)));
            result.set(unitName + "Display", new JSString(df.getUnitDisplay(i)));
        }

        // Add fractionalDigits only if explicitly set
        if (df.getFractionalDigits() != null) {
            result.set("fractionalDigits", new JSNumber(df.getFractionalDigits()));
        }

        return result;
    }

    /**
     * Convert a firstDayOfWeek day name to its numeric value (1=Monday through 7=Sunday). Returns -1 if not a
     * recognized day name.
     */
    private static int firstDayOfWeekToNumber(String dayName) {
        if (dayName == null) {
            return -1;
        }
        return switch (dayName) {
            case "mon" -> 1;
            case "tue" -> 2;
            case "wed" -> 3;
            case "thu" -> 4;
            case "fri" -> 5;
            case "sat" -> 6;
            case "sun" -> 7;
            default -> -1;
        };
    }

    private static JSValue formatDuration(JSContext context, JSValue thisArg, JSValue[] args, boolean partsRequested) {
        if (!(thisArg instanceof JSIntlDurationFormat durationFormat)) {
            String method = partsRequested ? "formatToParts" : "format";
            return context
                    .throwTypeError("Intl.DurationFormat.prototype." + method + " called on incompatible receiver");
        }
        if (args.length == 0) {
            return context.throwTypeError("Invalid duration value");
        }

        Map<String, Double> durationRecord = JSIntlDurationFormat.toDurationRecord(context, args[0]);
        if (durationRecord == null) {
            return JSUndefined.INSTANCE;
        }

        if (!partsRequested) {
            return new JSString(durationFormat.formatDuration(durationRecord));
        }
        List<JSIntlDurationFormat.FormatPart> parts = durationFormat.partitionDurationFormatPattern(durationRecord);
        JSArray result = context.createJSArray();
        for (int i = 0; i < parts.size(); i++) {
            JSIntlDurationFormat.FormatPart part = parts.get(i);
            JSObject partObj = createPartObject(context, part.type(), part.value());
            if (part.unit() != null) {
                partObj.set("unit", new JSString(part.unit()));
            }
            result.set(i, partObj);
        }
        return result;
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
     * Get the default first day of the week for a locale. Returns 1-7 (1=Monday through 7=Sunday).
     */
    private static int getLocaleDefaultFirstDay(Locale locale) {
        // Java's Calendar.getFirstDayOfWeek() returns Calendar.SUNDAY=1 through Calendar.SATURDAY=7
        int javaFirstDay = Calendar.getInstance(locale).getFirstDayOfWeek();
        // Convert Java's numbering (Sun=1..Sat=7) to ISO (Mon=1..Sun=7)
        if (javaFirstDay == Calendar.SUNDAY) {
            return 7;
        }
        return javaFirstDay - 1;
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
     * Get timezone IDs for a given ISO 3166 region code.
     */
    private static List<String> getTimeZonesForRegion(String region) {
        // Common region-to-timezone mappings from CLDR
        List<String> zones = switch (region) {
            case "US" -> List.of("America/Adak", "America/Anchorage", "America/Boise", "America/Chicago",
                    "America/Denver", "America/Detroit", "America/Indiana/Indianapolis", "America/Indiana/Knox",
                    "America/Indiana/Marengo", "America/Indiana/Petersburg", "America/Indiana/Tell_City",
                    "America/Indiana/Vevay", "America/Indiana/Vincennes", "America/Indiana/Winamac", "America/Juneau",
                    "America/Kentucky/Louisville", "America/Kentucky/Monticello", "America/Los_Angeles",
                    "America/Menominee", "America/Metlakatla", "America/New_York", "America/Nome",
                    "America/North_Dakota/Beulah", "America/North_Dakota/Center", "America/North_Dakota/New_Salem",
                    "America/Phoenix", "America/Sitka", "America/Yakutat", "Pacific/Honolulu");
            case "GB" -> List.of("Europe/London");
            case "DE" -> List.of("Europe/Berlin", "Europe/Busingen");
            case "FR" -> List.of("Europe/Paris");
            case "JP" -> List.of("Asia/Tokyo");
            case "CN" -> List.of("Asia/Shanghai", "Asia/Urumqi");
            case "IN" -> List.of("Asia/Kolkata");
            case "BR" -> List.of("America/Araguaina", "America/Bahia", "America/Belem", "America/Boa_Vista",
                    "America/Campo_Grande", "America/Cuiaba", "America/Eirunepe", "America/Fortaleza", "America/Manaus",
                    "America/Noronha", "America/Porto_Velho", "America/Recife", "America/Rio_Branco",
                    "America/Santarem", "America/Sao_Paulo");
            case "AU" ->
                List.of("Antarctica/Macquarie", "Australia/Adelaide", "Australia/Brisbane", "Australia/Broken_Hill",
                        "Australia/Darwin", "Australia/Eucla", "Australia/Hobart", "Australia/Lindeman",
                        "Australia/Lord_Howe", "Australia/Melbourne", "Australia/Perth", "Australia/Sydney");
            case "CA" -> List.of("America/Atikokan", "America/Dawson", "America/Dawson_Creek", "America/Edmonton",
                    "America/Fort_Nelson", "America/Glace_Bay", "America/Goose_Bay", "America/Halifax",
                    "America/Iqaluit", "America/Moncton", "America/Rankin_Inlet", "America/Regina", "America/Resolute",
                    "America/St_Johns", "America/Swift_Current", "America/Toronto", "America/Vancouver",
                    "America/Whitehorse", "America/Winnipeg", "America/Yellowknife");
            case "RU" ->
                List.of("Asia/Anadyr", "Asia/Barnaul", "Asia/Chita", "Asia/Irkutsk", "Asia/Kamchatka", "Asia/Khandyga",
                        "Asia/Krasnoyarsk", "Asia/Magadan", "Asia/Novokuznetsk", "Asia/Novosibirsk", "Asia/Omsk",
                        "Asia/Sakhalin", "Asia/Srednekolymsk", "Asia/Tomsk", "Asia/Ust-Nera", "Asia/Vladivostok",
                        "Asia/Yakutsk", "Asia/Yekaterinburg", "Europe/Astrakhan", "Europe/Kaliningrad", "Europe/Kirov",
                        "Europe/Moscow", "Europe/Samara", "Europe/Saratov", "Europe/Ulyanovsk", "Europe/Volgograd");
            case "NZ" -> List.of("Pacific/Auckland", "Pacific/Chatham");
            case "MX" -> List.of("America/Bahia_Banderas", "America/Cancun", "America/Chihuahua",
                    "America/Ciudad_Juarez", "America/Hermosillo", "America/Matamoros", "America/Mazatlan",
                    "America/Merida", "America/Mexico_City", "America/Monterrey", "America/Ojinaga", "America/Tijuana");
            case "IT" -> List.of("Europe/Rome");
            case "ES" -> List.of("Africa/Ceuta", "Atlantic/Canary", "Europe/Madrid");
            case "KR" -> List.of("Asia/Seoul");
            case "SE" -> List.of("Europe/Stockholm");
            case "NO" -> List.of("Europe/Oslo");
            case "FI" -> List.of("Europe/Helsinki");
            case "DK" -> List.of("Europe/Copenhagen");
            case "PL" -> List.of("Europe/Warsaw");
            case "CZ" -> List.of("Europe/Prague");
            case "AT" -> List.of("Europe/Vienna");
            case "CH" -> List.of("Europe/Zurich");
            case "NL" -> List.of("Europe/Amsterdam");
            case "BE" -> List.of("Europe/Brussels");
            case "PT" -> List.of("Atlantic/Azores", "Atlantic/Madeira", "Europe/Lisbon");
            case "GR" -> List.of("Europe/Athens");
            case "TR" -> List.of("Europe/Istanbul");
            case "IE" -> List.of("Europe/Dublin");
            case "IL" -> List.of("Asia/Jerusalem");
            case "EG" -> List.of("Africa/Cairo");
            case "ZA" -> List.of("Africa/Johannesburg");
            case "NG" -> List.of("Africa/Lagos");
            case "KE" -> List.of("Africa/Nairobi");
            case "SG" -> List.of("Asia/Singapore");
            case "TH" -> List.of("Asia/Bangkok");
            case "ID" -> List.of("Asia/Jakarta", "Asia/Jayapura", "Asia/Makassar", "Asia/Pontianak");
            case "PH" -> List.of("Asia/Manila");
            case "MY" -> List.of("Asia/Kuala_Lumpur", "Asia/Kuching");
            case "VN" -> List.of("Asia/Ho_Chi_Minh");
            case "PK" -> List.of("Asia/Karachi");
            case "BD" -> List.of("Asia/Dhaka");
            case "AR" ->
                List.of("America/Argentina/Buenos_Aires", "America/Argentina/Catamarca", "America/Argentina/Cordoba",
                        "America/Argentina/Jujuy", "America/Argentina/La_Rioja", "America/Argentina/Mendoza",
                        "America/Argentina/Rio_Gallegos", "America/Argentina/Salta", "America/Argentina/San_Juan",
                        "America/Argentina/San_Luis", "America/Argentina/Tucuman", "America/Argentina/Ushuaia");
            case "CL" -> List.of("America/Punta_Arenas", "America/Santiago", "Pacific/Easter");
            case "CO" -> List.of("America/Bogota");
            case "PE" -> List.of("America/Lima");
            case "UA" -> List.of("Europe/Kyiv", "Europe/Simferopol");
            case "RO" -> List.of("Europe/Bucharest");
            case "HU" -> List.of("Europe/Budapest");
            case "AQ" -> List.of("Antarctica/Casey", "Antarctica/Davis", "Antarctica/DumontDUrville",
                    "Antarctica/Mawson", "Antarctica/McMurdo", "Antarctica/Palmer", "Antarctica/Rothera",
                    "Antarctica/Syowa", "Antarctica/Troll", "Antarctica/Vostok");
            default -> {
                // Fallback: try to find at least one timezone for the region
                // Use Etc/UTC as last resort
                yield List.of("Etc/UTC");
            }
        };
        List<String> sorted = new ArrayList<>(zones);
        Collections.sort(sorted);
        return sorted;
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
        return localeString(context, thisArg, "baseName", JSIntlLocale::getBaseName);
    }

    public static JSValue localeGetCalendar(JSContext context, JSValue thisArg, JSValue[] args) {
        return localeString(context, thisArg, "calendar", JSIntlLocale::getCalendar);
    }

    public static JSValue localeGetCalendars(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.getCalendars called on incompatible receiver");
        }
        JSArray result = context.createJSArray();
        String calendar = intlLocale.getCalendar();
        if (calendar != null && !calendar.isEmpty()) {
            result.push(new JSString(calendar));
        }
        if (calendar == null || calendar.isEmpty()) {
            result.push(new JSString("gregory"));
        }
        return result;
    }

    public static JSValue localeGetCaseFirst(JSContext context, JSValue thisArg, JSValue[] args) {
        return localeString(context, thisArg, "caseFirst", JSIntlLocale::getCaseFirst);
    }

    public static JSValue localeGetCollation(JSContext context, JSValue thisArg, JSValue[] args) {
        return localeString(context, thisArg, "collation", JSIntlLocale::getCollation);
    }

    public static JSValue localeGetCollations(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.getCollations called on incompatible receiver");
        }
        JSArray result = context.createJSArray();
        String collation = intlLocale.getCollation();
        if (collation != null && !collation.isEmpty() && !"standard".equals(collation) && !"search".equals(collation)) {
            result.push(new JSString(collation));
        }
        if (result.getLength() == 0) {
            result.push(new JSString("emoji"));
            result.push(new JSString("eor"));
        }
        return result;
    }

    public static JSValue localeGetFirstDayOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        return localeString(context, thisArg, "firstDayOfWeek", JSIntlLocale::getFirstDayOfWeek);
    }

    public static JSValue localeGetHourCycle(JSContext context, JSValue thisArg, JSValue[] args) {
        return localeString(context, thisArg, "hourCycle", JSIntlLocale::getHourCycle);
    }

    public static JSValue localeGetHourCycles(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.getHourCycles called on incompatible receiver");
        }
        JSArray result = context.createJSArray();
        String hourCycle = intlLocale.getHourCycle();
        if (hourCycle != null && !hourCycle.isEmpty()) {
            result.push(new JSString(hourCycle));
        } else {
            String defaultHc = getLocaleDefaultHourCycle(intlLocale.getLocale());
            result.push(new JSString(defaultHc));
        }
        return result;
    }

    public static JSValue localeGetLanguage(JSContext context, JSValue thisArg, JSValue[] args) {
        return localeString(context, thisArg, "language", JSIntlLocale::getLanguage);
    }

    public static JSValue localeGetNumberingSystem(JSContext context, JSValue thisArg, JSValue[] args) {
        return localeString(context, thisArg, "numberingSystem", JSIntlLocale::getNumberingSystem);
    }

    public static JSValue localeGetNumberingSystems(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.getNumberingSystems called on incompatible receiver");
        }
        JSArray result = context.createJSArray();
        String numberingSystem = intlLocale.getNumberingSystem();
        if (numberingSystem != null && !numberingSystem.isEmpty()) {
            result.push(new JSString(numberingSystem));
        } else {
            result.push(new JSString("latn"));
        }
        return result;
    }

    public static JSValue localeGetNumeric(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.numeric called on incompatible receiver");
        }
        return intlLocale.isNumericSet() ? JSBoolean.valueOf(intlLocale.getNumeric()) : JSBoolean.FALSE;
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

    public static JSValue localeGetTextInfo(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.getTextInfo called on incompatible receiver");
        }
        JSObject result = context.createJSObject();
        // Determine text direction based on locale's script or language
        String direction = "ltr";
        String lang = intlLocale.getLanguage();
        String script = intlLocale.getScript();
        if ("Arab".equals(script) || "Hebr".equals(script) || "Thaa".equals(script) || "Syrc".equals(script)) {
            direction = "rtl";
        } else if ("ar".equals(lang) || "he".equals(lang) || "fa".equals(lang) || "ur".equals(lang) || "yi".equals(lang)
                || "ps".equals(lang) || "sd".equals(lang) || "ckb".equals(lang)) {
            direction = "rtl";
        }
        createDataProperty(result, "direction", new JSString(direction));
        return result;
    }

    public static JSValue localeGetTimeZones(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.getTimeZones called on incompatible receiver");
        }
        String region = intlLocale.getRegion();
        if (region == null || region.isEmpty()) {
            return JSUndefined.INSTANCE;
        }
        JSArray result = context.createJSArray();
        List<String> zones = getTimeZonesForRegion(region.toUpperCase(Locale.ROOT));
        for (String zone : zones) {
            result.push(new JSString(zone));
        }
        return result;
    }

    public static JSValue localeGetVariants(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.variants called on incompatible receiver");
        }
        String variant = intlLocale.getVariant();
        if (variant == null || variant.isEmpty()) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(variant);
    }

    public static JSValue localeGetWeekInfo(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.getWeekInfo called on incompatible receiver");
        }
        JSObject result = context.createJSObject();

        // Determine firstDay from fw extension or locale default
        int firstDay;
        String fdw = intlLocale.getFirstDayOfWeek();
        if (fdw != null && !fdw.isEmpty()) {
            int numericDay = firstDayOfWeekToNumber(fdw);
            if (numericDay > 0) {
                firstDay = numericDay;
            } else {
                firstDay = getLocaleDefaultFirstDay(intlLocale.getLocale());
            }
        } else {
            firstDay = getLocaleDefaultFirstDay(intlLocale.getLocale());
        }
        createDataProperty(result, "firstDay", JSNumber.of(firstDay));

        // Weekend days: default Sat-Sun (6, 7) for most locales
        JSArray weekend = context.createJSArray();
        weekend.push(JSNumber.of(6));
        weekend.push(JSNumber.of(7));
        createDataProperty(result, "weekend", weekend);

        return result;
    }

    public static JSValue localeMaximize(JSContext context, JSValue thisArg, JSValue[] args) {
        return changeLikelySubtags(context, thisArg, true);
    }

    public static JSValue localeMinimize(JSContext context, JSValue thisArg, JSValue[] args) {
        return changeLikelySubtags(context, thisArg, false);
    }

    private static String localeOption(Map<String, String> extensions, String key, String bareValue) {
        String value = extensions.get(key);
        return value != null && (value.isEmpty() || "true".equals(value)) ? bareValue : value;
    }

    private static JSValue localeString(JSContext context, JSValue receiver, String name,
            java.util.function.Function<JSIntlLocale, String> getter) {
        if (!(receiver instanceof JSIntlLocale locale)) {
            return context.throwTypeError("Intl.Locale.prototype." + name + " called on incompatible receiver");
        }
        String value = getter.apply(locale);
        return value == null ? JSUndefined.INSTANCE : new JSString(value);
    }

    public static JSValue localeToString(JSContext context, JSValue thisArg, JSValue[] args) {
        return localeString(context, thisArg, "toString", JSIntlLocale::getTag);
    }

    private static String nullIfEmpty(String value) {
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

    private static void putLocaleOption(Map<String, String> extensions, String key, String value) {
        if (value != null) {
            extensions.put(key, value);
        }
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

    /**
     * Remove likely subtags from a locale (CLDR "minimize"). Algorithm: maximize first, then try removing subtags.
     */
    private static Locale removeLikelySubtags(Locale locale) {
        Locale maximized = addLikelySubtags(locale);
        String maxLang = maximized.getLanguage();
        String maxScript = maximized.getScript();
        String maxRegion = maximized.getCountry();
        String variant = locale.getVariant();

        // Try language only
        Locale langOnly = new Locale.Builder().setLanguage(maxLang).build();
        Locale langOnlyMax = addLikelySubtags(langOnly);
        if (langOnlyMax.getScript().equals(maxScript) && langOnlyMax.getCountry().equals(maxRegion)) {
            Locale.Builder builder = new Locale.Builder().setLanguage(maxLang);
            if (!variant.isEmpty()) {
                builder.setVariant(variant);
            }
            return builder.build();
        }

        // Try language-region
        Locale langRegion = new Locale.Builder().setLanguage(maxLang).setRegion(maxRegion).build();
        Locale langRegionMax = addLikelySubtags(langRegion);
        if (langRegionMax.getScript().equals(maxScript)) {
            Locale.Builder builder = new Locale.Builder().setLanguage(maxLang).setRegion(maxRegion);
            if (!variant.isEmpty()) {
                builder.setVariant(variant);
            }
            return builder.build();
        }

        // Try language-script
        Locale langScript = new Locale.Builder().setLanguage(maxLang).setScript(maxScript).build();
        Locale langScriptMax = addLikelySubtags(langScript);
        if (langScriptMax.getCountry().equals(maxRegion)) {
            Locale.Builder builder = new Locale.Builder().setLanguage(maxLang).setScript(maxScript);
            if (!variant.isEmpty()) {
                builder.setVariant(variant);
            }
            return builder.build();
        }

        // Can't minimize further, return maximized
        Locale.Builder builder = new Locale.Builder().setLanguage(maxLang).setScript(maxScript).setRegion(maxRegion);
        if (!variant.isEmpty()) {
            builder.setVariant(variant);
        }
        return builder.build();
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

    /**
     * WeekdayToString: convert numeric strings "0"-"7" to day names. 0 and 7 → "sun", 1 → "mon", ..., 6 → "sat". Other
     * strings pass through unchanged.
     */
    private static String weekdayToString(String value) {
        return switch (value) {
            case "0", "7" -> "sun";
            case "1" -> "mon";
            case "2" -> "tue";
            case "3" -> "wed";
            case "4" -> "thu";
            case "5" -> "fri";
            case "6" -> "sat";
            default -> value;
        };
    }
}
