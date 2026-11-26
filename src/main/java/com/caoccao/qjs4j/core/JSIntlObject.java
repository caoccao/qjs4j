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

import com.caoccao.qjs4j.core.temporal.TemporalCalendarId;
import com.caoccao.qjs4j.exceptions.JSException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implementation of Intl object and Intl.* prototype methods.
 */
public final class JSIntlObject {
    private static final Set<String> AVAILABLE_LOCALE_LANGUAGES;
    private static final String[] COMPACT_DISPLAY_VALUES = {"short", "long"};
    private static final String[] CURRENCY_DISPLAY_VALUES = {"code", "symbol", "narrowSymbol", "name"};
    private static final String[] CURRENCY_SIGN_VALUES = {"standard", "accounting"};
    private static final String[] FORMAT_STYLE_VALUES = {"short", "medium", "long", "full"};
    /**
     * CLDR likely subtags data for maximize/minimize. Maps language (or language-script, language-region) to full
     * locale tag.
     */
    private static final Map<String, String> LIKELY_SUBTAGS = new HashMap<>();
    private static final BigInteger NANOS_PER_MILLISECOND = BigInteger.valueOf(1_000_000L);
    private static final String[] NOTATION_VALUES = {"standard", "scientific", "engineering", "compact"};
    private static final String[] NUMBER_STYLE_VALUES = {"decimal", "currency", "percent", "unit"};
    private static final String[] ROUNDING_MODE_VALUES = {"ceil", "floor", "expand", "trunc", "halfCeil", "halfFloor",
            "halfExpand", "halfTrunc", "halfEven"};
    private static final String[] ROUNDING_PRIORITY_VALUES = {"auto", "morePrecision", "lessPrecision"};
    private static final Set<String> SANCTIONED_SIMPLE_UNITS = Set.of("acre", "bit", "byte", "celsius", "centimeter",
            "day", "degree", "fahrenheit", "fluid-ounce", "foot", "gallon", "gigabit", "gigabyte", "gram", "hectare",
            "hour", "inch", "kilobit", "kilobyte", "kilogram", "kilometer", "liter", "megabit", "megabyte", "meter",
            "microsecond", "mile", "mile-scandinavian", "milliliter", "millimeter", "millisecond", "minute", "month",
            "nanosecond", "ounce", "percent", "petabyte", "pound", "second", "stone", "terabit", "terabyte", "week",
            "yard", "year");
    private static final String[] SIGN_DISPLAY_VALUES = {"auto", "never", "always", "exceptZero", "negative"};
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
     * Supported numbering systems with simple digit mappings (per ECMA-402 Table 10). Used by
     * supportedValuesOf("numberingSystem") and validated in constructors.
     */
    private static final Set<String> SUPPORTED_NUMBERING_SYSTEMS = Set.of("adlm", "ahom", "arab", "arabext", "bali",
            "beng", "bhks", "brah", "cakm", "cham", "deva", "diak", "fullwide", "gong", "gara", "gonm", "gujr", "gukh",
            "guru", "hanidec", "hmng", "hmnp", "java", "kali", "kawi", "khmr", "knda", "krai", "lana", "lanatham",
            "laoo", "latn", "lepc", "limb", "mathbold", "mathdbl", "mathmono", "mathsanb", "mathsans", "mlym", "modi",
            "mong", "mroo", "mtei", "mymr", "mymrepka", "mymrpao", "mymrshan", "mymrtlng", "nagm", "newa", "nkoo",
            "olck", "onao", "orya", "osma", "outlined", "rohg", "saur", "segment", "shrd", "sind", "sinh", "sora",
            "sund", "sunu", "takr", "talu", "tamldec", "telu", "thai", "tibt", "tirh", "tnsa", "tols", "vaii", "wara",
            "wcho");
    /**
     * Lazy-initialized map of lowercased timezone ID → canonical casing. Built from ZoneId.getAvailableZoneIds() plus
     * supplementary valid IANA timezone IDs that Java's ZoneId does not include.
     */
    private static volatile Map<String, String> TIMEZONE_LOOKUP_MAP;
    private static final String[] TRAILING_ZERO_DISPLAY_VALUES = {"auto", "stripIfInteger"};
    /**
     * Pattern for Unicode Locale Identifier type: (3*8alphanum) *("-" (3*8alphanum))
     */
    private static final Pattern UNICODE_TYPE_PATTERN = Pattern.compile("^[a-zA-Z0-9]{3,8}(-[a-zA-Z0-9]{3,8})*$");
    private static final String[] UNIT_DISPLAY_VALUES = {"short", "narrow", "long"};
    /**
     * Check if a collation value is valid/supported.
     */
    private static final Set<String> VALID_COLLATION_TYPES = Set.of("big5han", "compat", "dict", "direct", "ducet",
            "emoji", "eor", "gb2312", "phonebk", "phonetic", "pinyin", "reformed", "searchjl", "stroke", "trad",
            "unihan", "zhuyin");
    private static final Set<Integer> VALID_ROUNDING_INCREMENTS = Set.of(1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500,
            1000, 2000, 2500, 5000);

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

    private static void appendMatchingExtension(StringBuilder tag, int baseLength, String key, String resolved,
            String requested) {
        if (resolved != null && resolved.equals(requested)) {
            tag.append(tag.length() == baseLength ? "-u-" : "-").append(key).append('-').append(resolved);
        }
    }

    private static void appendRangePartsWithSource(JSContext context, JSArray target, JSArray sourceParts,
            String sourceName) {
        int length = (int) sourceParts.getLength();
        for (int index = 0; index < length; index++) {
            JSValue partValue = sourceParts.get(index);
            if (!(partValue instanceof JSObject partObject)) {
                continue;
            }
            JSValue typeValue = partObject.get(PropertyKey.fromString("type"));
            JSValue valueValue = partObject.get(PropertyKey.VALUE);
            if (!(typeValue instanceof JSString typeString) || !(valueValue instanceof JSString valueString)) {
                continue;
            }
            target.push(createPartObject(context, typeString.value(), valueString.value(), sourceName));
        }
    }

    /**
     * Build resolved locale tag according to ECMA-402 ResolveLocale rules. Unicode extension keys are included only if:
     * - The extension value matched the resolved value AND - No option explicitly overrode it
     */
    private static Locale buildResolvedLocale(Locale locale, Map<String, String> unicodeExtensions, String calendar,
            String hourCycle, Boolean hour12, String numberingSystem) {
        Locale base = IntlLocaleTag.stripUnicodeExtensions(locale);
        StringBuilder tag = new StringBuilder(base.toLanguageTag());
        int baseLength = tag.length();
        appendMatchingExtension(tag, baseLength, "ca", calendar, canonicalizeCalendar(unicodeExtensions.get("ca")));
        if (hour12 == null) {
            appendMatchingExtension(tag, baseLength, "hc", hourCycle, unicodeExtensions.get("hc"));
        }
        String nu = unicodeExtensions.get("nu");
        appendMatchingExtension(tag, baseLength, "nu", numberingSystem,
                nu == null ? null : nu.toLowerCase(Locale.ROOT));
        return tag.length() == baseLength ? base : Locale.forLanguageTag(tag.toString());
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
    private static String canonicalizeCalendar(String calendar) {
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

    private static JSValue coerceOptionsObject(JSContext context, JSValue options) {
        if (options instanceof JSUndefined) {
            JSObject empty = context.createJSObject();
            empty.setPrototype(null);
            return empty;
        }
        if (options instanceof JSNull) {
            return context.throwTypeError("Cannot convert null to object");
        }
        JSObject object = JSTypeConversions.toObject(context, options);
        return object == null || context.hasPendingException() ? JSUndefined.INSTANCE : object;
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

    private static String commonLeadingNonNumericPrefix(String start, String end) {
        int limit = Math.min(start.length(), end.length());
        int i = 0;
        while (i < limit) {
            char a = start.charAt(i);
            char b = end.charAt(i);
            if (a != b) {
                break;
            }
            if (Character.isDigit(a) || a == '.' || a == ',' || a == '-') {
                break;
            }
            i++;
        }
        if (i <= 0) {
            return "";
        }
        return start.substring(0, i);
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
            String usage = normalizedStringOption(context, optionsValue, "usage", "sort", "sort", "search");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }

            // 2. localeMatcher
            String localeMatcher = getOptionString(context, optionsValue, "localeMatcher");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (localeMatcher != null && !"lookup".equals(localeMatcher) && !"best fit".equals(localeMatcher)) {
                return context.throwRangeError("Invalid option value " + localeMatcher + " for property localeMatcher");
            }

            // 3. collation: resolve from option and/or extension per ECMA-402
            String optionCollation = getOptionString(context, optionsValue, "collation");
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
            String caseFirst = getOptionString(context, optionsValue, "caseFirst");
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
            String sensitivity = getOptionString(context, optionsValue, "sensitivity");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            String sensitivityDefault = "search".equals(usage) ? "base" : "variant";
            sensitivity = normalizeOption(sensitivity, sensitivityDefault, "base", "accent", "case", "variant");

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
            JSObject resolvedPrototype = resolveIntlPrototype(context, prototype, "Collator");
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
    private static void createDataProperty(JSObject obj, String key, JSValue value) {
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
        try {
            Locale locale = resolveLocale(context, args, 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            JSValue rawOptions = JSValue.argumentOrUndefined(args, 1);
            boolean optionsIsUndefined = rawOptions instanceof JSUndefined;

            // Convert options to Object per spec: undefined → null-prototype object, null → TypeError
            // Per ToDateTimeOptions: undefined → OrdinaryObjectCreate(null) to avoid Object.prototype pollution
            JSObject optionsObject;
            if (optionsIsUndefined) {
                optionsObject = context.createJSObject();
                optionsObject.setPrototype(null);
            } else if (rawOptions instanceof JSNull) {
                return context.throwTypeError("Cannot convert null to object");
            } else if (rawOptions instanceof JSObject obj) {
                optionsObject = obj;
            } else {
                // Primitive → ToObject (wraps as a wrapper object with no own properties)
                optionsObject = context.createJSObject();
            }

            // Read ALL options in ECMA-402 CreateDateTimeFormat spec order,
            // triggering getters and propagating exceptions.

            // 1. localeMatcher
            String localeMatcher = readStringOption(context, optionsObject, "localeMatcher");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (localeMatcher != null && !"lookup".equals(localeMatcher) && !"best fit".equals(localeMatcher)) {
                return context.throwRangeError("Invalid option value " + localeMatcher + " for property localeMatcher");
            }

            // 2. calendar
            String calendar = readStringOption(context, optionsObject, "calendar");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (calendar != null && !UNICODE_TYPE_PATTERN.matcher(calendar).matches()) {
                return context.throwRangeError("Invalid calendar: " + calendar);
            }
            if (calendar != null) {
                calendar = canonicalizeCalendar(calendar);
            }

            // 3. numberingSystem
            String numberingSystem = readStringOption(context, optionsObject, "numberingSystem");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (numberingSystem != null && !UNICODE_TYPE_PATTERN.matcher(numberingSystem).matches()) {
                return context.throwRangeError("Invalid numberingSystem: " + numberingSystem);
            }

            // 4. hour12
            JSValue hour12Value = optionsObject.get(PropertyKey.fromString("hour12"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // 5. hourCycle
            String hourCycle = readStringOption(context, optionsObject, "hourCycle");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (hourCycle != null && !"h11".equals(hourCycle) && !"h12".equals(hourCycle) && !"h23".equals(hourCycle)
                    && !"h24".equals(hourCycle)) {
                return context.throwRangeError("Invalid hourCycle: " + hourCycle);
            }

            // 6. timeZone
            String timeZone = readStringOption(context, optionsObject, "timeZone");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (timeZone != null) {
                timeZone = validateAndCanonicalizeTimeZone(timeZone);
                if (timeZone == null) {
                    return context.throwRangeError("Invalid time zone");
                }
            } else {
                timeZone = ZoneId.systemDefault().getId();
            }

            // 7-15. Component options
            String weekdayOption = readValidatedOption(context, optionsObject, "weekday", "long", "short", "narrow");
            String eraOption = readValidatedOption(context, optionsObject, "era", "long", "short", "narrow");
            String yearOption = readValidatedOption(context, optionsObject, "year", "numeric", "2-digit");
            String monthOption = readValidatedOption(context, optionsObject, "month", "numeric", "2-digit", "long",
                    "short", "narrow");
            String dayOption = readValidatedOption(context, optionsObject, "day", "numeric", "2-digit");
            String dayPeriodOption = readValidatedOption(context, optionsObject, "dayPeriod", "long", "short",
                    "narrow");
            String hourOption = readValidatedOption(context, optionsObject, "hour", "numeric", "2-digit");
            String minuteOption = readValidatedOption(context, optionsObject, "minute", "numeric", "2-digit");
            String secondOption = readValidatedOption(context, optionsObject, "second", "numeric", "2-digit");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // 16. fractionalSecondDigits (GetNumberOption: ToNumber, range check, then floor)
            Integer fractionalSecondDigits = null;
            JSValue fsdValue = optionsObject.get(PropertyKey.fromString("fractionalSecondDigits"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (fsdValue != null && !(fsdValue instanceof JSUndefined)) {
                double fsdDouble = JSTypeConversions.toNumber(context, fsdValue).value();
                // DefaultNumberOption: check NaN and range BEFORE flooring
                if (Double.isNaN(fsdDouble) || fsdDouble < 1 || fsdDouble > 3) {
                    return context.throwRangeError("fractionalSecondDigits value is out of range.");
                }
                fractionalSecondDigits = (int) Math.floor(fsdDouble);
            }

            // 17. timeZoneName
            String timeZoneNameOption = readValidatedOption(context, optionsObject, "timeZoneName", "long", "short",
                    "longOffset", "shortOffset", "longGeneric", "shortGeneric");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // 18. formatMatcher
            String formatMatcher = readStringOption(context, optionsObject, "formatMatcher");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (formatMatcher != null && !"basic".equals(formatMatcher) && !"best fit".equals(formatMatcher)) {
                return context.throwRangeError("Invalid formatMatcher: " + formatMatcher);
            }

            // 19-20. dateStyle and timeStyle
            String dateStyleStr = readValidatedOption(context, optionsObject, "dateStyle", "full", "long", "medium",
                    "short");
            String timeStyleStr = readValidatedOption(context, optionsObject, "timeStyle", "full", "long", "medium",
                    "short");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            FormatStyle dateStyle = parseFormatStyle(dateStyleStr);
            FormatStyle timeStyle = parseFormatStyle(timeStyleStr);

            // Check dateStyle/timeStyle conflict with explicit components
            if (dateStyle != null || timeStyle != null) {
                if (weekdayOption != null || eraOption != null || yearOption != null || monthOption != null
                        || dayOption != null || dayPeriodOption != null || hourOption != null || minuteOption != null
                        || secondOption != null || fractionalSecondDigits != null || timeZoneNameOption != null) {
                    return context.throwTypeError(
                            "dateStyle and timeStyle may not be used with other DateTimeFormat options");
                }
            }

            // Apply defaults per ECMA-402 ToDateTimeOptions(options, required, defaults):
            // 'required' determines which component types to check for needDefaults.
            // 'defaults' determines which defaults to add when needDefaults is true.
            boolean hasDefaultDateComponents = false;
            boolean hasDefaultTimeComponents = false;
            if (dateStyle == null && timeStyle == null) {
                boolean hasDateComponent = weekdayOption != null || yearOption != null || monthOption != null
                        || dayOption != null;
                boolean hasTimeComponent = dayPeriodOption != null || hourOption != null || minuteOption != null
                        || secondOption != null || fractionalSecondDigits != null;
                boolean needDefaults;
                if ("date".equals(required)) {
                    needDefaults = !hasDateComponent;
                } else if ("time".equals(required)) {
                    needDefaults = !hasTimeComponent;
                } else {
                    // "any"
                    needDefaults = !hasDateComponent && !hasTimeComponent;
                }
                if (needDefaults) {
                    if ("date".equals(defaults) || "all".equals(defaults)) {
                        yearOption = "numeric";
                        monthOption = "numeric";
                        dayOption = "numeric";
                        hasDefaultDateComponents = true;
                    }
                    if ("time".equals(defaults) || "all".equals(defaults)) {
                        hourOption = "numeric";
                        minuteOption = "numeric";
                        secondOption = "numeric";
                        hasDefaultTimeComponents = true;
                    }
                }
            }

            // Resolve calendar from locale unicode extension if not specified in options
            Map<String, String> unicodeExtensions = IntlLocaleTag.parseUnicodeExtensions(locale.toLanguageTag());
            // If option value is not a supported calendar, treat as if not specified
            if (calendar != null && !isSupportedCalendar(calendar)) {
                calendar = null;
            }
            if (calendar == null) {
                if (unicodeExtensions.containsKey("ca")) {
                    String extensionCalendar = canonicalizeCalendar(unicodeExtensions.get("ca"));
                    if (isSupportedCalendar(extensionCalendar)) {
                        calendar = extensionCalendar;
                    }
                }
            }
            if (calendar == null) {
                calendar = "gregory";
            }

            // Resolve numberingSystem
            // If option value is not supported, treat as if not specified
            if (numberingSystem != null && !isSupportedDateTimeNumberingSystem(numberingSystem)) {
                numberingSystem = null;
            }
            if (numberingSystem == null && unicodeExtensions.containsKey("nu")) {
                String localeNumberingSystem = unicodeExtensions.get("nu").toLowerCase(Locale.ROOT);
                if (isSupportedDateTimeNumberingSystem(localeNumberingSystem)) {
                    numberingSystem = localeNumberingSystem;
                }
            }

            // Resolve hourCycle per ECMA-402:
            // 1. If hour12 is not undefined, it takes precedence (sets hourCycle to null, resolved later)
            // 2. Else if hourCycle option is set, use it
            // 3. Else if locale has -u-hc- extension, use it
            // 4. Else use locale default
            Boolean hour12 = null;
            if (!(hour12Value instanceof JSUndefined) && hour12Value != null) {
                hour12 = JSTypeConversions.toBoolean(hour12Value).value();
                // hour12 takes precedence over hourCycle
                hourCycle = null;
            }

            String hourCycleFromExtension = unicodeExtensions.containsKey("hc") ? unicodeExtensions.get("hc") : null;

            if (hourCycle == null && hour12 == null && hourCycleFromExtension != null) {
                if ("h11".equals(hourCycleFromExtension) || "h12".equals(hourCycleFromExtension)
                        || "h23".equals(hourCycleFromExtension) || "h24".equals(hourCycleFromExtension)) {
                    hourCycle = hourCycleFromExtension;
                }
            }

            // If hour12 is set, derive hourCycle from locale default
            if (hour12 != null) {
                if (hour12) {
                    // Prefer h12 for most locales, h11 for ja
                    String lang = locale.getLanguage();
                    hourCycle = "ja".equals(lang) ? "h11" : "h12";
                } else {
                    hourCycle = "h23";
                }
            }

            // Resolve the locale tag BEFORE modifying hourCycle for no-hour-component.
            // Per spec, ResolveLocale (step 9) runs before [[HourCycle]] nullification (step 32).
            // The pre-nullification hourCycle value determines which extensions appear in the locale tag.
            Locale resolvedLocale = buildResolvedLocale(locale, unicodeExtensions, calendar, hourCycle, hour12,
                    numberingSystem);

            // Finalize hourCycle for the instance:
            // - If no hour component, set to null (step 32 of CreateDateTimeFormat)
            // - If still null (no option/extension/hour12), use locale default
            String hourCycleForInstant = hourCycle;
            boolean hasHourComponent = hourOption != null || timeStyle != null;
            if (!hasHourComponent) {
                hourCycle = null;
            } else if (hourCycle == null) {
                hourCycle = getLocaleDefaultHourCycle(locale);
                hourCycleForInstant = hourCycle;
            }

            JSIntlDateTimeFormat dateTimeFormat = new JSIntlDateTimeFormat(context, resolvedLocale, dateStyle,
                    timeStyle, calendar, numberingSystem, timeZone, hourCycle, hourCycleForInstant, weekdayOption,
                    eraOption, yearOption, monthOption, dayOption, dayPeriodOption, hourOption, minuteOption,
                    secondOption, fractionalSecondDigits, timeZoneNameOption, hasDefaultDateComponents,
                    hasDefaultTimeComponents);
            JSObject resolvedDtfPrototype = resolveIntlPrototype(context, prototype, "DateTimeFormat");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            dateTimeFormat.setPrototype(resolvedDtfPrototype);
            return dateTimeFormat;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    /**
     * Intl.DisplayNames constructor factory. Per ECMA-402 §12.1.1: OrdinaryCreateFromConstructor (step 2) runs before
     * locale/options processing. Options are read in order: localeMatcher → style → type → fallback → languageDisplay
     */
    public static JSValue createDisplayNames(JSContext context, JSObject prototype, JSValue[] args) {
        // Step 2: OrdinaryCreateFromConstructor — resolve prototype from NewTarget FIRST
        JSObject resolvedPrototype = resolveIntlPrototype(context, prototype, "DisplayNames");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        // Step 3: CanonicalizeLocaleList
        Locale locale = resolveLocale(context, args, 0);
        if (context.hasPendingException()) {
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
        String localeMatcher = getOptionString(context, options, "localeMatcher");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (localeMatcher != null) {
            if (!"lookup".equals(localeMatcher) && !"best fit".equals(localeMatcher)) {
                return context.throwRangeError("localeMatcher must be \"lookup\" or \"best fit\"");
            }
        }

        // style (default: "long")
        String style = getOptionString(context, options, "style");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (style == null) {
            style = "long";
        } else if (!"narrow".equals(style) && !"short".equals(style) && !"long".equals(style)) {
            return context.throwRangeError("style must be \"narrow\", \"short\", or \"long\"");
        }

        // type (required — TypeError if undefined)
        String type = getOptionString(context, options, "type");
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
        String fallback = getOptionString(context, options, "fallback");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (fallback == null) {
            fallback = "code";
        } else if (!"code".equals(fallback) && !"none".equals(fallback)) {
            return context.throwRangeError("fallback must be \"code\" or \"none\"");
        }

        // languageDisplay (default: "dialect")
        String languageDisplay = getOptionString(context, options, "languageDisplay");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (languageDisplay == null) {
            languageDisplay = "dialect";
        } else if (!"dialect".equals(languageDisplay) && !"standard".equals(languageDisplay)) {
            return context.throwRangeError("languageDisplay must be \"dialect\" or \"standard\"");
        }

        // Create instance and set prototype
        JSIntlDisplayNames displayNames = new JSIntlDisplayNames(context, locale, style, type, fallback,
                languageDisplay);
        if (resolvedPrototype != null) {
            displayNames.setPrototype(resolvedPrototype);
        }
        return displayNames;
    }

    /**
     * Intl.DurationFormat constructor.
     */
    public static JSValue createDurationFormat(JSContext context, JSObject prototype, JSValue[] args) {
        JSObject resolvedPrototype = resolveIntlPrototype(context, prototype, "DurationFormat");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        // Resolve locale
        Locale locale = resolveLocale(context, args, 0);
        if (context.hasPendingException()) {
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
        if (options != null) {
            String localeMatcher = getOptionString(context, options, "localeMatcher");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (localeMatcher != null) {
                if (!"lookup".equals(localeMatcher) && !"best fit".equals(localeMatcher)) {
                    return context.throwRangeError("localeMatcher must be \"lookup\" or \"best fit\"");
                }
            }
        }

        // Read numberingSystem from options
        String numberingSystemOption = options != null ? getOptionString(context, options, "numberingSystem") : null;
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (numberingSystemOption != null && !UNICODE_TYPE_PATTERN.matcher(numberingSystemOption).matches()) {
            return context.throwRangeError("Invalid numberingSystem: " + numberingSystemOption);
        }

        // Parse unicode extension "nu" from locale
        Map<String, String> unicodeExtensions = IntlLocaleTag.parseUnicodeExtensions(locale.toLanguageTag());
        String extensionNu = unicodeExtensions.get("nu");

        // Resolve numberingSystem: options > unicode extension > default
        String numberingSystem;
        boolean useExtensionInLocale = false;
        if (numberingSystemOption != null && SUPPORTED_NUMBERING_SYSTEMS.contains(numberingSystemOption)) {
            numberingSystem = numberingSystemOption;
            // Extension is reflected in locale only if it matches the option
            if (numberingSystem.equals(extensionNu)) {
                useExtensionInLocale = true;
            }
        } else if (extensionNu != null && SUPPORTED_NUMBERING_SYSTEMS.contains(extensionNu)) {
            numberingSystem = extensionNu;
            useExtensionInLocale = true;
        } else {
            numberingSystem = "latn";
        }

        // Build resolved locale: strip existing unicode extensions and re-add if needed
        Locale strippedLocale = IntlLocaleTag.stripUnicodeExtensions(locale);
        if (useExtensionInLocale) {
            locale = new Locale.Builder().setLocale(strippedLocale).setUnicodeLocaleKeyword("nu", numberingSystem)
                    .build();
        } else {
            locale = strippedLocale;
        }

        // Read style (default "short")
        String style = options != null ? getOptionString(context, options, "style") : null;
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
            String unitStyle = options != null ? getOptionString(context, options, unitName) : null;
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
            String display = options != null ? getOptionString(context, options, unitName + "Display") : null;
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

        JSIntlDurationFormat durationFormat = new JSIntlDurationFormat(context, locale, numberingSystem, style,
                unitStyles, unitDisplays, fractionalDigits);
        if (resolvedPrototype != null) {
            durationFormat.setPrototype(resolvedPrototype);
        }
        return durationFormat;
    }

    public static JSValue createListFormat(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // GetOptionsObject: validate options argument
            JSValue optionsArg = JSValue.argumentOrUndefined(args, 1);
            JSValue optionsObj = getOptionsObject(context, optionsArg);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // Read options in spec order: localeMatcher, type, style
            String localeMatcher = getOptionString(context, optionsObj, "localeMatcher");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (localeMatcher != null && !"lookup".equals(localeMatcher) && !"best fit".equals(localeMatcher)) {
                return context.throwRangeError(
                        "Value " + localeMatcher + " out of range for Intl.ListFormat options property localeMatcher");
            }

            String type = normalizeOption(getOptionString(context, optionsObj, "type"), "conjunction", "conjunction",
                    "disjunction", "unit");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            String style = normalizeOption(getOptionString(context, optionsObj, "style"), "long", "long", "short",
                    "narrow");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            JSIntlListFormat listFormat = new JSIntlListFormat(context, locale, style, type);
            JSObject resolvedPrototype = resolveIntlPrototype(context, prototype, "ListFormat");
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
            String languageOpt = getOptionString(context, optionsValue, "language");
            String scriptOpt = getOptionString(context, optionsValue, "script");
            String regionOpt = getOptionString(context, optionsValue, "region");
            String variantsOpt = getOptionString(context, optionsValue, "variants");
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

            String calendarOpt = getOptionString(context, optionsValue, "calendar");
            String collationOpt = getOptionString(context, optionsValue, "collation");
            String hourCycleOpt = getOptionString(context, optionsValue, "hourCycle");
            String caseFirstOpt = getOptionString(context, optionsValue, "caseFirst");
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

            String numberingSystemOpt = getOptionString(context, optionsValue, "numberingSystem");
            String firstDay = getOptionString(context, optionsValue, "firstDayOfWeek");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String firstDayOfWeekOpt = firstDay == null ? null : weekdayToString(firstDay);
            if (firstDayOfWeekOpt != null && !"true".equals(firstDayOfWeekOpt)
                    && !UNICODE_TYPE_PATTERN.matcher(firstDayOfWeekOpt).matches()) {
                return context.throwRangeError("invalid firstDayOfWeek: " + firstDay);
            }

            // Validate extension values
            if (calendarOpt != null
                    && (calendarOpt.isEmpty() || !UNICODE_TYPE_PATTERN.matcher(calendarOpt).matches())) {
                return context.throwRangeError("invalid calendar: " + calendarOpt);
            }
            if (collationOpt != null
                    && (collationOpt.isEmpty() || !UNICODE_TYPE_PATTERN.matcher(collationOpt).matches())) {
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
            if (numberingSystemOpt != null
                    && (numberingSystemOpt.isEmpty() || !UNICODE_TYPE_PATTERN.matcher(numberingSystemOpt).matches())) {
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
            JSObject resolvedPrototype = resolveIntlPrototype(context, prototype, "Locale");
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
        try {
            JSObject resolvedPrototype = resolveIntlPrototype(context, prototype, "NumberFormat");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            Locale locale = resolveLocale(context, args, 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            JSValue optionsArgument = JSValue.argumentOrUndefined(args, 1);
            JSObject optionsObject;
            if (optionsArgument instanceof JSUndefined || optionsArgument == null) {
                optionsObject = context.createJSObject();
                optionsObject.setPrototype(null);
            } else {
                optionsObject = JSTypeConversions.toObject(context, optionsArgument);
                if (optionsObject == null) {
                    return context.throwTypeError("Cannot convert "
                            + JSTypeConversions.toString(context, optionsArgument).value() + " to object");
                }
            }

            // Option read order must match ECMA-402.
            String localeMatcher = getOptionString(context, optionsObject, "localeMatcher");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (localeMatcher != null && !"lookup".equals(localeMatcher) && !"best fit".equals(localeMatcher)) {
                return context.throwRangeError("Value " + localeMatcher
                        + " out of range for Intl.NumberFormat options property localeMatcher");
            }

            String numberingSystemOption = getOptionString(context, optionsObject, "numberingSystem");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (numberingSystemOption != null && !UNICODE_TYPE_PATTERN.matcher(numberingSystemOption).matches()) {
                return context.throwRangeError("Invalid numberingSystem: " + numberingSystemOption);
            }
            if (numberingSystemOption != null) {
                numberingSystemOption = numberingSystemOption.toLowerCase(Locale.ROOT);
            }

            String style = normalizedStringOption(context, optionsObject, "style", "decimal", NUMBER_STYLE_VALUES);
            String currency = getOptionString(context, optionsObject, "currency");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (currency != null) {
                if (!isWellFormedCurrencyCode(currency)) {
                    return context.throwRangeError("Invalid currency code: " + currency);
                }
                currency = currency.toUpperCase(Locale.ROOT);
            }

            String currencyDisplay = normalizedStringOption(context, optionsObject, "currencyDisplay", "symbol",
                    CURRENCY_DISPLAY_VALUES);
            String currencySign = normalizedStringOption(context, optionsObject, "currencySign", "standard",
                    CURRENCY_SIGN_VALUES);
            String unit = getOptionString(context, optionsObject, "unit");
            String unitDisplay = normalizedStringOption(context, optionsObject, "unitDisplay", "short",
                    UNIT_DISPLAY_VALUES);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            if ("currency".equals(style) && currency == null) {
                return context.throwTypeError("Currency code is required with currency style");
            }
            if ("unit".equals(style) && unit == null) {
                return context.throwTypeError("Unit is required with unit style");
            }
            if (unit != null) {
                if (!isWellFormedUnitIdentifier(unit)) {
                    return context.throwRangeError("Invalid unit argument: " + unit);
                }
            }

            String notation = normalizedStringOption(context, optionsObject, "notation", "standard", NOTATION_VALUES);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            int minimumIntegerDigits = readMinimumIntegerDigits(context, optionsObject);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            DigitRange fractionDigits = DigitRange.readFraction(context, optionsObject);
            if (fractionDigits == null) {
                return JSUndefined.INSTANCE;
            }

            DigitRange significantDigits = DigitRange.readSignificant(context, optionsObject);
            if (significantDigits == null) {
                return JSUndefined.INSTANCE;
            }

            RoundingOptions rounding = readRoundingOptions(context, optionsObject, true);
            if (rounding == null) {
                return JSUndefined.INSTANCE;
            }

            String compactDisplay = normalizedStringOption(context, optionsObject, "compactDisplay", "short",
                    COMPACT_DISPLAY_VALUES);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            JSValue useGroupingValue = optionsObject.get(PropertyKey.fromString("useGrouping"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String useGroupingMode = parseUseGroupingOption(context, useGroupingValue, notation);
            String signDisplay = normalizedStringOption(context, optionsObject, "signDisplay", "auto",
                    SIGN_DISPLAY_VALUES);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            int currencyDigits = 2;
            if ("currency".equals(style) && currency != null) {
                currencyDigits = getCurrencyDigits(currency);
            }
            int minimumFractionDefault;
            int maximumFractionDefault;
            if ("currency".equals(style) && "standard".equals(notation)) {
                minimumFractionDefault = currencyDigits;
                maximumFractionDefault = currencyDigits;
            } else if ("percent".equals(style)) {
                minimumFractionDefault = 0;
                maximumFractionDefault = 0;
            } else if ("currency".equals(style) && "compact".equals(notation)) {
                minimumFractionDefault = 0;
                maximumFractionDefault = 0;
            } else {
                minimumFractionDefault = 0;
                maximumFractionDefault = 3;
            }

            boolean useSignificantDigits = significantDigits.specified();
            if (useSignificantDigits) {
                significantDigits = significantDigits.resolveSignificant();
            }
            fractionDigits = useSignificantDigits && "auto".equals(rounding.roundingPriority())
                    ? new DigitRange(minimumFractionDefault, maximumFractionDefault)
                    : fractionDigits.resolveFraction(minimumFractionDefault, maximumFractionDefault);

            if (rounding.roundingIncrement() != 1) {
                if (useSignificantDigits || "morePrecision".equals(rounding.roundingPriority())
                        || "lessPrecision".equals(rounding.roundingPriority())) {
                    return context.throwTypeError("Invalid roundingIncrement for current rounding settings");
                }
                if (!fractionDigits.minimum().equals(fractionDigits.maximum())) {
                    return context.throwRangeError(
                            "maximumFractionDigits and minimumFractionDigits must be equal when roundingIncrement is used");
                }
            }

            NumberingLocale numbering = NumberingLocale.resolve(locale, numberingSystemOption);
            JSIntlNumberFormat numberFormat = new JSIntlNumberFormat(context, numbering.locale(), style, currency,
                    useGroupingMode, minimumIntegerDigits, fractionDigits.minimum(), fractionDigits.maximum(),
                    useSignificantDigits, useSignificantDigits ? significantDigits.minimum() : 0,
                    useSignificantDigits ? significantDigits.maximum() : 0, unit, unitDisplay, signDisplay,
                    rounding.roundingMode(), numbering.numberingSystem(), notation,
                    "compact".equals(notation) ? compactDisplay : null, currencyDisplay, currencySign,
                    rounding.roundingIncrement(), rounding.roundingPriority(), rounding.trailingZeroDisplay());
            if (resolvedPrototype != null) {
                numberFormat.setPrototype(resolvedPrototype);
            }
            return numberFormat;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    private static JSObject createPartObject(JSContext context, String type, String value) {
        JSObject part = context.createJSObject();
        part.set("type", new JSString(type));
        part.set("value", new JSString(value));
        return part;
    }

    private static JSObject createPartObject(JSContext context, String type, String value, String source) {
        JSObject part = createPartObject(context, type, value);
        part.set("source", new JSString(source));
        return part;
    }

    public static JSValue createPluralRules(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            JSObject resolvedPrototype = resolveIntlPrototype(context, prototype, "PluralRules");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            Locale locale = resolveLocale(context, args, 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            JSValue options = coerceOptionsObject(context, JSValue.argumentOrUndefined(args, 1));
            if (context.hasPendingException() || !(options instanceof JSObject optionsObject)) {
                return options;
            }

            String localeMatcher = normalizedStringOption(context, optionsObject, "localeMatcher", "best fit", "lookup",
                    "best fit");
            String type = normalizedStringOption(context, optionsObject, "type", "cardinal", "cardinal", "ordinal");
            String notation = normalizedStringOption(context, optionsObject, "notation", "standard", NOTATION_VALUES);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            int minimumIntegerDigits = readMinimumIntegerDigits(context, optionsObject);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            DigitRange fractionDigits = DigitRange.readFraction(context, optionsObject);
            if (fractionDigits == null) {
                return JSUndefined.INSTANCE;
            }

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
            JSIntlPluralRules pluralRules = new JSIntlPluralRules(context, locale, type, notation, minimumIntegerDigits,
                    fractionDigits.minimum(), fractionDigits.maximum(), significantDigits.minimum(),
                    significantDigits.maximum());
            if (resolvedPrototype != null) {
                pluralRules.setPrototype(resolvedPrototype);
            }
            return pluralRules;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue createRelativeTimeFormat(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            JSObject resolvedPrototype = resolveIntlPrototype(context, prototype, "RelativeTimeFormat");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            Locale locale = resolveLocale(context, args, 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            JSValue options = coerceOptionsObject(context, JSValue.argumentOrUndefined(args, 1));
            if (context.hasPendingException() || !(options instanceof JSObject optionsObject)) {
                return options;
            }

            String localeMatcher = normalizedStringOption(context, optionsObject, "localeMatcher", "best fit", "lookup",
                    "best fit");
            String numberingSystemOption = getOptionString(context, optionsObject, "numberingSystem");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (numberingSystemOption != null) {
                numberingSystemOption = numberingSystemOption.toLowerCase(Locale.ROOT);
                if (!UNICODE_TYPE_PATTERN.matcher(numberingSystemOption).matches()) {
                    return context.throwRangeError("Invalid numberingSystem: " + numberingSystemOption);
                }
            }

            String style = normalizedStringOption(context, optionsObject, "style", "long", "long", "short", "narrow");
            String numeric = normalizedStringOption(context, optionsObject, "numeric", "always", "always", "auto");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            NumberingLocale numbering = NumberingLocale.resolve(locale, numberingSystemOption);
            JSIntlRelativeTimeFormat relativeTimeFormat = new JSIntlRelativeTimeFormat(context, numbering.locale(),
                    style, numeric, numbering.numberingSystem());
            if (resolvedPrototype != null) {
                relativeTimeFormat.setPrototype(resolvedPrototype);
            }
            return relativeTimeFormat;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    private static JSObject createRelativeTimeLiteralPart(JSContext context, String value) {
        JSObject part = context.createJSObject();
        createDataProperty(part, "type", new JSString("literal"));
        createDataProperty(part, "value", new JSString(value));
        return part;
    }

    private static JSIntlNumberFormat createRelativeTimeNumberFormat(JSIntlRelativeTimeFormat relativeTimeFormat) {
        String useGroupingMode = "pl".equals(relativeTimeFormat.getLocale().getLanguage()) ? "min2" : "auto";
        return new JSIntlNumberFormat(relativeTimeFormat.context, relativeTimeFormat.getLocale(), "decimal", null,
                useGroupingMode, 1, 0, 3, false, 0, null, null, "auto", "halfExpand",
                relativeTimeFormat.getNumberingSystem());
    }

    public static JSValue createSegmenter(JSContext context, JSObject prototype, JSObject segmentsPrototype,
            JSValue[] args) {
        try {
            JSObject resolvedPrototype = resolveIntlPrototype(context, prototype, "Segmenter");
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

            JSValue options = coerceOptionsObject(context, JSValue.argumentOrUndefined(args, 1));
            if (context.hasPendingException() || !(options instanceof JSObject optionsObject)) {
                return options;
            }

            String localeMatcher = normalizedStringOption(context, optionsObject, "localeMatcher", "best fit", "lookup",
                    "best fit");
            String granularity = normalizedStringOption(context, optionsObject, "granularity", "grapheme", "grapheme",
                    "word", "sentence");
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

    private static JSIntlDateTimeFormat createTemporalDateTimeFormat(JSContext context,
            JSIntlDateTimeFormat baseDateTimeFormat, DateTimeFormattableKind formattableKind) {
        boolean instant = formattableKind == DateTimeFormattableKind.INSTANT;
        boolean fullDate = instant || formattableKind == DateTimeFormattableKind.PLAIN_DATE
                || formattableKind == DateTimeFormattableKind.PLAIN_DATE_TIME;
        boolean supportsYear = fullDate || formattableKind == DateTimeFormattableKind.PLAIN_YEAR_MONTH;
        boolean supportsDay = fullDate || formattableKind == DateTimeFormattableKind.PLAIN_MONTH_DAY;
        boolean supportsMonth = supportsYear || supportsDay;
        boolean supportsTime = instant || formattableKind == DateTimeFormattableKind.PLAIN_DATE_TIME
                || formattableKind == DateTimeFormattableKind.PLAIN_TIME;

        FormatStyle dateStyle = baseDateTimeFormat.getDateStyle();
        FormatStyle timeStyle = baseDateTimeFormat.getTimeStyle();
        String weekdayOption = baseDateTimeFormat.getWeekdayOption();
        String eraOption = baseDateTimeFormat.getEraOption();
        String yearOption = baseDateTimeFormat.getYearOption();
        String monthOption = baseDateTimeFormat.getMonthOption();
        String dayOption = baseDateTimeFormat.getDayOption();
        String dayPeriodOption = baseDateTimeFormat.getDayPeriodOption();
        String hourOption = baseDateTimeFormat.getHourOption();
        String minuteOption = baseDateTimeFormat.getMinuteOption();
        String secondOption = baseDateTimeFormat.getSecondOption();
        Integer fractionalSecondDigits = baseDateTimeFormat.getFractionalSecondDigits();

        if (baseDateTimeFormat.hasDefaultDateComponents()) {
            yearOption = null;
            monthOption = null;
            dayOption = null;
        }
        if (baseDateTimeFormat.hasDefaultTimeComponents()) {
            hourOption = null;
            minuteOption = null;
            secondOption = null;
        }

        if ((formattableKind == DateTimeFormattableKind.PLAIN_YEAR_MONTH
                || formattableKind == DateTimeFormattableKind.PLAIN_MONTH_DAY) && dateStyle != null) {
            DateStyleOptionFields dateStyleOptionFields = toDateStyleOptionFields(dateStyle);
            if (weekdayOption == null) {
                weekdayOption = dateStyleOptionFields.weekdayOption();
            }
            if (yearOption == null) {
                yearOption = dateStyleOptionFields.yearOption();
            }
            if (monthOption == null) {
                monthOption = dateStyleOptionFields.monthOption();
            }
            if (dayOption == null) {
                dayOption = dateStyleOptionFields.dayOption();
            }
            dateStyle = null;
        }

        boolean hasTime = timeStyle != null || dayPeriodOption != null || hourOption != null || minuteOption != null
                || secondOption != null || fractionalSecondDigits != null;
        // An era by itself does not trigger the incompatible-options error.
        boolean requestedComponents = hasTime || dateStyle != null || weekdayOption != null || yearOption != null
                || monthOption != null || dayOption != null;

        if (!supportsTime) {
            timeStyle = null;
            dayPeriodOption = null;
            hourOption = null;
            minuteOption = null;
            secondOption = null;
            fractionalSecondDigits = null;
            hasTime = false;
        }
        if (!supportsMonth) {
            dateStyle = null;
            monthOption = null;
        }
        if (!fullDate) {
            weekdayOption = null;
        }
        if (!supportsYear) {
            eraOption = null;
            yearOption = null;
        }
        if (!supportsDay) {
            dayOption = null;
        }

        boolean hasDate = dateStyle != null || weekdayOption != null || yearOption != null || monthOption != null
                || dayOption != null;
        if (!hasDate && !hasTime) {
            if (requestedComponents && eraOption == null) {
                context.throwTypeError("Invalid date/time formatting options");
                return null;
            }
            if (supportsYear) {
                yearOption = "numeric";
            }
            if (supportsMonth) {
                monthOption = "numeric";
            }
            if (supportsDay) {
                dayOption = "numeric";
            }
            if (supportsTime) {
                hourOption = "numeric";
                minuteOption = "numeric";
                secondOption = "numeric";
                hasTime = true;
            }
        }
        String effectiveHourCycle = instant || hasTime
                ? resolveHourCycle(baseDateTimeFormat)
                : baseDateTimeFormat.getHourCycle();

        return new JSIntlDateTimeFormat(context, baseDateTimeFormat.getLocale(), dateStyle, timeStyle,
                baseDateTimeFormat.getCalendar(), baseDateTimeFormat.getNumberingSystem(),
                instant ? baseDateTimeFormat.getTimeZone() : "UTC", effectiveHourCycle,
                instant ? effectiveHourCycle : baseDateTimeFormat.getHourCycleForInstant(), weekdayOption, eraOption,
                yearOption, monthOption, dayOption, dayPeriodOption, hourOption, minuteOption, secondOption,
                fractionalSecondDigits, instant ? baseDateTimeFormat.getTimeZoneNameOption() : null, false, false);
    }

    private static JSValue datePartsToJSArray(JSContext context, List<JSIntlDateTimeFormat.DatePart> parts) {
        JSArray array = context.createJSArray();
        for (JSIntlDateTimeFormat.DatePart part : parts) {
            JSObject partObj = createPartObject(context, part.type(), part.value());
            array.push(partObj);
        }
        return array;
    }

    public static JSValue dateTimeFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        return formatDateTime(context, thisArg, args, false);
    }

    /**
     * Getter for Intl.DateTimeFormat.prototype.format.
     */
    public static JSValue dateTimeFormatFormatGetter(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context.throwTypeError("Intl.DateTimeFormat.prototype.format called on incompatible receiver");
        }
        JSFunction cachedBoundFormatFunction = dateTimeFormat.getBoundFormatFunction();
        if (cachedBoundFormatFunction != null) {
            return cachedBoundFormatFunction;
        }
        JSNativeFunction boundFormatFunction = new JSNativeFunction(context, "", 1, (childContext, thisValue,
                formatArgs) -> dateTimeFormatFormat(childContext, dateTimeFormat, formatArgs));
        context.transferPrototype(boundFormatFunction, JSFunction.NAME);
        dateTimeFormat.setBoundFormatFunction(boundFormatFunction);
        return boundFormatFunction;
    }

    /**
     * Intl.DateTimeFormat.prototype.formatRange(startDate, endDate)
     */
    public static JSValue dateTimeFormatFormatRange(JSContext context, JSValue thisArg, JSValue[] args) {
        return formatDateRange(context, thisArg, args, false);
    }

    /**
     * Intl.DateTimeFormat.prototype.formatRangeToParts(startDate, endDate)
     */
    public static JSValue dateTimeFormatFormatRangeToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return formatDateRange(context, thisArg, args, true);
    }

    /**
     * Intl.DateTimeFormat.prototype.formatToParts(date)
     */
    public static JSValue dateTimeFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return formatDateTime(context, thisArg, args, true);
    }

    public static JSValue dateTimeFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context
                    .throwTypeError("Intl.DateTimeFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();

        // Property order per ECMA-402: locale, calendar, numberingSystem, timeZone,
        // hourCycle, hour12, weekday, era, year, month, day, dayPeriod,
        // hour, minute, second, fractionalSecondDigits, timeZoneName, dateStyle, timeStyle
        createDataProperty(resolvedOptions, "locale", new JSString(dateTimeFormat.getLocale().toLanguageTag()));
        defineStringOption(resolvedOptions, "calendar", dateTimeFormat.getCalendar());
        if (dateTimeFormat.getNumberingSystem() != null) {
            createDataProperty(resolvedOptions, "numberingSystem", new JSString(dateTimeFormat.getNumberingSystem()));
        } else {
            createDataProperty(resolvedOptions, "numberingSystem", new JSString("latn"));
        }
        createDataProperty(resolvedOptions, "timeZone", new JSString(
                dateTimeFormat.getTimeZone() != null ? dateTimeFormat.getTimeZone() : ZoneId.systemDefault().getId()));

        // hourCycle and hour12: only present when hour component is in use
        String hourCycle = dateTimeFormat.getHourCycle();
        if (hourCycle != null) {
            createDataProperty(resolvedOptions, "hourCycle", new JSString(hourCycle));
            boolean isHour12 = "h11".equals(hourCycle) || "h12".equals(hourCycle);
            createDataProperty(resolvedOptions, "hour12", isHour12 ? JSBoolean.TRUE : JSBoolean.FALSE);
        }

        // When dateStyle or timeStyle is set, individual component options are NOT reported
        boolean hasDateStyle = dateTimeFormat.getDateStyle() != null;
        boolean hasTimeStyle = dateTimeFormat.getTimeStyle() != null;

        if (!hasDateStyle && !hasTimeStyle) {
            // Component options only when not using styles
            defineStringOption(resolvedOptions, "weekday", dateTimeFormat.getWeekdayOption());
            defineStringOption(resolvedOptions, "era", dateTimeFormat.getEraOption());
            defineStringOption(resolvedOptions, "year", dateTimeFormat.getYearOption());
            defineStringOption(resolvedOptions, "month", dateTimeFormat.getMonthOption());
            defineStringOption(resolvedOptions, "day", dateTimeFormat.getDayOption());
            defineStringOption(resolvedOptions, "dayPeriod", dateTimeFormat.getDayPeriodOption());
            defineStringOption(resolvedOptions, "hour", dateTimeFormat.getHourOption());
            defineStringOption(resolvedOptions, "minute", dateTimeFormat.getMinuteOption());
            defineStringOption(resolvedOptions, "second", dateTimeFormat.getSecondOption());
            if (dateTimeFormat.getFractionalSecondDigits() != null) {
                createDataProperty(resolvedOptions, "fractionalSecondDigits",
                        JSNumber.of(dateTimeFormat.getFractionalSecondDigits()));
            }
            defineStringOption(resolvedOptions, "timeZoneName", dateTimeFormat.getTimeZoneNameOption());
        }
        if (hasDateStyle) {
            createDataProperty(resolvedOptions, "dateStyle",
                    new JSString(dateTimeFormat.getDateStyle().name().toLowerCase(Locale.ROOT)));
        }
        if (hasTimeStyle) {
            createDataProperty(resolvedOptions, "timeStyle",
                    new JSString(dateTimeFormat.getTimeStyle().name().toLowerCase(Locale.ROOT)));
        }
        return resolvedOptions;
    }

    private static void defineDigitRange(JSObject options, boolean significant, int minimum, int maximum) {
        createDataProperty(options, significant ? "minimumSignificantDigits" : "minimumFractionDigits",
                JSNumber.of(minimum));
        createDataProperty(options, significant ? "maximumSignificantDigits" : "maximumFractionDigits",
                JSNumber.of(maximum));
    }

    private static void defineStringOption(JSObject options, String key, String value) {
        if (value != null) {
            createDataProperty(options, key, new JSString(value));
        }
    }

    private static boolean differsOnlyInTimeParts(List<JSIntlDateTimeFormat.DatePart> startParts,
            List<JSIntlDateTimeFormat.DatePart> endParts) {
        if (startParts.size() != endParts.size()) {
            return false;
        }
        boolean hasDifference = false;
        boolean hasHourDifference = false;
        for (int index = 0; index < startParts.size(); index++) {
            JSIntlDateTimeFormat.DatePart startPart = startParts.get(index);
            JSIntlDateTimeFormat.DatePart endPart = endParts.get(index);
            if (startPart.equals(endPart)) {
                continue;
            }
            hasDifference = true;
            String type = startPart.type();
            if (!"hour".equals(type) && !"minute".equals(type) && !"second".equals(type)
                    && !"fractionalSecond".equals(type) && !"dayPeriod".equals(type)) {
                return false;
            }
            if ("hour".equals(type)) {
                hasHourDifference = true;
            }
        }
        return hasDifference && hasHourDifference;
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

    private static int findFirstDigitIndex(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (Character.isDigit(text.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static int findLastDigitIndex(String text) {
        for (int index = text.length() - 1; index >= 0; index--) {
            if (Character.isDigit(text.charAt(index))) {
                return index;
            }
        }
        return -1;
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

    private static JSValue formatDateRange(JSContext context, JSValue thisArg, JSValue[] args, boolean toParts) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            String method = toParts ? "formatRangeToParts" : "formatRange";
            return context
                    .throwTypeError("Intl.DateTimeFormat.prototype." + method + " called on incompatible receiver");
        }
        JSValue startArg = JSValue.argumentOrUndefined(args, 0);
        JSValue endArg = JSValue.argumentOrUndefined(args, 1);
        if (startArg instanceof JSUndefined || endArg instanceof JSUndefined) {
            return context.throwTypeError("start date or end date is undefined");
        }
        DateTimeFormattable startFormattable = toDateTimeFormattable(context, startArg, false);
        if (startFormattable == null || context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        DateTimeFormattable endFormattable = toDateTimeFormattable(context, endArg, false);
        if (endFormattable == null || context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (startFormattable.kind() != endFormattable.kind()) {
            return context.throwTypeError("Invalid range");
        }
        if (startFormattable.calendarId() != null && endFormattable.calendarId() != null
                && !startFormattable.calendarId().equals(endFormattable.calendarId())) {
            return context.throwRangeError("Invalid date/time value");
        }
        JSIntlDateTimeFormat effectiveDateTimeFormat = getEffectiveDateTimeFormat(context, dateTimeFormat,
                startFormattable.kind());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!isTemporalCalendarCompatibleWithDateTimeFormat(context, startFormattable, effectiveDateTimeFormat)
                || !isTemporalCalendarCompatibleWithDateTimeFormat(context, endFormattable, effectiveDateTimeFormat)) {
            return JSUndefined.INSTANCE;
        }
        if (!isFormattableEpochMillisValid(context, startFormattable)
                || !isFormattableEpochMillisValid(context, endFormattable)) {
            return JSUndefined.INSTANCE;
        }
        double startDate = startFormattable.epochMillis();
        double endDate = endFormattable.epochMillis();

        if (!toParts && Double.compare(startDate, endDate) == 0) {
            return new JSString(effectiveDateTimeFormat.format(startDate));
        }

        List<JSIntlDateTimeFormat.DatePart> startParts = effectiveDateTimeFormat.formatToPartsList(startDate);
        List<JSIntlDateTimeFormat.DatePart> endParts = effectiveDateTimeFormat.formatToPartsList(endDate);

        if (toParts) {
            JSArray result = context.createJSArray();
            writeDateRange(effectiveDateTimeFormat, startParts, endParts,
                    (type, value, source) -> result.push(createPartObject(context, type, value, source)));
            return result;
        }
        StringBuilder text = new StringBuilder();
        writeDateRange(effectiveDateTimeFormat, startParts, endParts, (type, value, source) -> text.append(value));
        return new JSString(text.toString());
    }

    private static JSValue formatDateTime(JSContext context, JSValue thisArg, JSValue[] args, boolean toParts) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context.throwTypeError(toParts
                    ? "Intl.DateTimeFormat.prototype.formatToParts called on incompatible receiver"
                    : "Intl.DateTimeFormat.prototype.format called on incompatible receiver");
        }
        DateTimeFormattable formattable = toDateTimeFormattable(context, JSValue.argumentOrUndefined(args, 0), true);
        if (formattable == null || context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSIntlDateTimeFormat effectiveDateTimeFormat = getEffectiveDateTimeFormat(context, dateTimeFormat,
                formattable.kind());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!isTemporalCalendarCompatibleWithDateTimeFormat(context, formattable, effectiveDateTimeFormat)) {
            return JSUndefined.INSTANCE;
        }
        if (!isFormattableEpochMillisValid(context, formattable)) {
            return JSUndefined.INSTANCE;
        }
        if (toParts) {
            return datePartsToJSArray(context, effectiveDateTimeFormat.formatToPartsList(formattable.epochMillis()));
        }
        return new JSString(effectiveDateTimeFormat.format(formattable.epochMillis()));
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

    private static String formatIntlNumberValue(JSContext context, JSIntlNumberFormat numberFormat, JSValue value) {
        if (value instanceof JSBigInt bigInt) {
            return numberFormat.format(bigInt.value());
        }
        if (value instanceof JSString jsString) {
            return numberFormat.format(jsString.value());
        }
        double numeric = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return "";
        }
        return numberFormat.format(numeric);
    }

    private static JSValue formatNumberRange(JSContext context, JSValue thisArg, JSValue[] args, boolean toParts) {
        if (!(thisArg instanceof JSIntlNumberFormat numberFormat)) {
            String method = toParts ? "formatRangeToParts" : "formatRange";
            return context.throwTypeError("Intl.NumberFormat.prototype." + method + " called on incompatible receiver");
        }
        JSValue startValue = JSValue.argumentOrUndefined(args, 0);
        JSValue endValue = JSValue.argumentOrUndefined(args, 1);
        if (startValue instanceof JSUndefined || endValue instanceof JSUndefined) {
            return context.throwTypeError("start and end must not be undefined");
        }
        if (isNaNRangeNumberValue(context, startValue) || isNaNRangeNumberValue(context, endValue)) {
            return context.throwRangeError("start or end is NaN");
        }
        String startFormatted = formatIntlNumberValue(context, numberFormat, startValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String endFormatted = formatIntlNumberValue(context, numberFormat, endValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (toParts) {
            return numberRangeParts(context, numberFormat, startValue, endValue, startFormatted.equals(endFormatted));
        }
        if (startFormatted.equals(endFormatted)) {
            return new JSString("~" + startFormatted);
        }

        String localeTag = numberFormat.getLocale().toLanguageTag();
        String separator = localeTag.startsWith("pt") ? " - " : " \u2013 ";
        if ("currency".equals(numberFormat.getStyle())) {
            int startDigitStart = findFirstDigitIndex(startFormatted);
            int startDigitEnd = findLastDigitIndex(startFormatted);
            int endDigitStart = findFirstDigitIndex(endFormatted);
            int endDigitEnd = findLastDigitIndex(endFormatted);
            if (startDigitStart >= 0 && startDigitEnd >= startDigitStart && endDigitStart >= 0
                    && endDigitEnd >= endDigitStart) {
                String startPrefix = startFormatted.substring(0, startDigitStart);
                String startCore = startFormatted.substring(startDigitStart, startDigitEnd + 1);
                String startSuffix = startFormatted.substring(startDigitEnd + 1);
                String endPrefix = endFormatted.substring(0, endDigitStart);
                String endCore = endFormatted.substring(endDigitStart, endDigitEnd + 1);
                String endSuffix = endFormatted.substring(endDigitEnd + 1);

                if (!startSuffix.isEmpty() && startSuffix.equals(endSuffix)) {
                    if (startCore.startsWith("+") && endCore.startsWith("+")) {
                        endCore = endCore.substring(1);
                    }
                    return new JSString(startPrefix + startCore + separator + endCore + startSuffix);
                }

                if ("always".equals(numberFormat.getSignDisplay()) && !startPrefix.isEmpty()
                        && startPrefix.equals(endPrefix) && startPrefix.startsWith("+")) {
                    return new JSString(startPrefix + startCore + "\u2013" + endCore + startSuffix);
                }
            }
        }

        if ("currency".equals(numberFormat.getStyle())) {
            return new JSString(startFormatted + separator + endFormatted);
        }
        if (localeTag.startsWith("pt")) {
            return new JSString(startFormatted + " - " + endFormatted);
        }
        return new JSString(startFormatted + "\u2013" + endFormatted);
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

    private static int getCurrencyDigits(String currencyCode) {
        try {
            Currency currency = Currency.getInstance(currencyCode);
            int digits = currency.getDefaultFractionDigits();
            if (digits < 0) {
                return 2;
            }
            return digits;
        } catch (IllegalArgumentException e) {
            return 2;
        }
    }

    private static JSIntlDateTimeFormat getEffectiveDateTimeFormat(JSContext context,
            JSIntlDateTimeFormat baseDateTimeFormat, DateTimeFormattableKind formattableKind) {
        return switch (formattableKind) {
            case NUMBER -> baseDateTimeFormat;
            case ZONED_DATE_TIME -> {
                context.throwTypeError("Invalid date/time value");
                yield null;
            }
            default -> createTemporalDateTimeFormat(context, baseDateTimeFormat, formattableKind);
        };
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
    private static String getLocaleDefaultHourCycle(Locale locale) {
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

    private static Integer getOptionIntegerOrUndefined(JSContext context, JSObject optionsObject, String key,
            int minimum, int maximum) {
        JSValue rawValue = optionsObject.get(PropertyKey.fromString(key));
        if (context.hasPendingException()) {
            return null;
        }
        if (rawValue == null || rawValue instanceof JSUndefined) {
            return null;
        }
        double numberValue = JSTypeConversions.toNumber(context, rawValue).value();
        if (context.hasPendingException()) {
            return null;
        }
        if (Double.isNaN(numberValue) || Double.isInfinite(numberValue)) {
            context.throwRangeError(
                    "Value " + numberValue + " out of range for Intl.NumberFormat options property " + key);
            return null;
        }
        int integerValue = (int) Math.floor(numberValue);
        if (integerValue < minimum || integerValue > maximum || integerValue != numberValue) {
            context.throwRangeError(
                    "Value " + numberValue + " out of range for Intl.NumberFormat options property " + key);
            return null;
        }
        return integerValue;
    }

    /**
     * GetOptionsObject (ECMA-402 §9.2.11). Converts the options argument to an object per spec: - undefined → empty
     * object - object → return as-is - anything else (null, boolean, number, string, symbol, bigint) → TypeError
     */
    private static JSValue getOptionsObject(JSContext context, JSValue options) {
        if (options instanceof JSUndefined || options == null) {
            // Create null-prototype object to avoid triggering Object.prototype getters
            JSObject obj = context.createJSObject();
            obj.setPrototype(null);
            return obj;
        }
        if (options instanceof JSObject) {
            return options;
        }
        context.throwTypeError("Cannot convert " + JSTypeConversions.toString(context, options).value() + " to object");
        return JSUndefined.INSTANCE;
    }

    private static String getOptionString(JSContext context, JSValue optionsValue, String key) {
        if (!(optionsValue instanceof JSObject optionsObject)) {
            return null;
        }
        return readStringOption(context, optionsObject, key);
    }

    /**
     * Like getOptionString but checks for pending exceptions after property access. Returns null if an exception
     * occurred (caller must check context.hasPendingException()).
     */
    private static String getPolishOtherUnit(String style, String unit) {
        if ("long".equals(style)) {
            return switch (unit) {
                case "second" -> "sekundy";
                case "minute" -> "minuty";
                case "hour" -> "godziny";
                case "day" -> "dnia";
                case "week" -> "tygodnia";
                case "month" -> "miesiąca";
                case "quarter" -> "kwartału";
                case "year" -> "roku";
                default -> unit;
            };
        }
        if ("short".equals(style)) {
            return switch (unit) {
                case "day" -> "dnia";
                case "year" -> "roku";
                default -> switch (unit) {
                    case "second" -> "sek.";
                    case "minute" -> "min";
                    case "hour" -> "godz.";
                    case "week" -> "tyg.";
                    case "month" -> "mies.";
                    case "quarter" -> "kw.";
                    default -> unit;
                };
            };
        }
        return switch (unit) {
            case "day" -> "dnia";
            case "year" -> "roku";
            default -> switch (unit) {
                case "second" -> "s";
                case "minute" -> "min";
                case "hour" -> "g.";
                case "week" -> "tyg.";
                case "month" -> "mies.";
                case "quarter" -> "kw.";
                default -> unit;
            };
        };
    }

    private static Map<String, String> getTimezoneLookupMap() {
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

    private static boolean isFormattableEpochMillisValid(JSContext context, DateTimeFormattable formattable) {
        if (!Double.isFinite(formattable.epochMillis())) {
            context.throwRangeError("Invalid time value");
            return false;
        }
        if (formattable.kind() == DateTimeFormattableKind.NUMBER && Math.abs(formattable.epochMillis()) > 8.64e15) {
            context.throwRangeError("Invalid time value");
            return false;
        }
        return true;
    }

    private static boolean isNaNRangeNumberValue(JSContext context, JSValue value) {
        if (value instanceof JSBigInt) {
            return false;
        }
        if (value instanceof JSString jsString) {
            String text = jsString.value().trim();
            if (text.isEmpty()) {
                return false;
            }
            try {
                new BigDecimal(text);
                return false;
            } catch (RuntimeException ignored) {
                try {
                    double parsed = Double.parseDouble(text);
                    return Double.isNaN(parsed);
                } catch (RuntimeException parseError) {
                    return true;
                }
            }
        }
        double numericValue = JSTypeConversions.toNumber(context, value).value();
        return !context.hasPendingException() && Double.isNaN(numericValue);
    }

    private static boolean isNegativeZero(double value) {
        return Double.doubleToRawLongBits(value) == Double.doubleToRawLongBits(-0.0d);
    }

    private static boolean isPolishIntegerPlural(double absoluteValue) {
        return Double.isFinite(absoluteValue) && Math.floor(absoluteValue) == absoluteValue;
    }

    /**
     * Check if a calendar name is supported by the Java platform.
     */
    private static boolean isSupportedCalendar(String calendar) {
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

    private static boolean isSupportedDateTimeNumberingSystem(String numberingSystem) {
        if (numberingSystem == null) {
            return false;
        }
        return SUPPORTED_NUMBERING_SYSTEMS.contains(numberingSystem);
    }

    private static boolean isTemporalCalendarCompatibleWithDateTimeFormat(JSContext context,
            DateTimeFormattable formattable, JSIntlDateTimeFormat dateTimeFormat) {
        TemporalCalendarId temporalCalendarId = formattable.calendarId();
        if (temporalCalendarId == null) {
            return true;
        }
        String canonicalTemporalCalendarId = canonicalizeCalendar(temporalCalendarId.identifier());
        if ("iso8601".equals(canonicalTemporalCalendarId)) {
            return true;
        }
        String formatterCalendarId = dateTimeFormat.getCalendar();
        if (formatterCalendarId == null || formatterCalendarId.isEmpty()) {
            return true;
        }
        String canonicalFormatterCalendarId = canonicalizeCalendar(formatterCalendarId);
        if (!canonicalTemporalCalendarId.equals(canonicalFormatterCalendarId)) {
            context.throwRangeError("Invalid date/time value");
            return false;
        }
        return true;
    }

    private static boolean isValidCollation(String collation) {
        if (collation == null || collation.isEmpty()) {
            return false;
        }
        return VALID_COLLATION_TYPES.contains(collation);
    }

    private static boolean isWellFormedCurrencyCode(String currencyCode) {
        if (currencyCode.length() != 3) {
            return false;
        }
        for (int index = 0; index < currencyCode.length(); index++) {
            char character = currencyCode.charAt(index);
            if (!((character >= 'A' && character <= 'Z') || (character >= 'a' && character <= 'z'))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWellFormedUnitIdentifier(String unitIdentifier) {
        if (!unitIdentifier.equals(unitIdentifier.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (SANCTIONED_SIMPLE_UNITS.contains(unitIdentifier)) {
            return true;
        }
        String[] parts = unitIdentifier.split("-per-", -1);
        if (parts.length != 2) {
            return false;
        }
        return SANCTIONED_SIMPLE_UNITS.contains(parts[0]) && SANCTIONED_SIMPLE_UNITS.contains(parts[1]);
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

    private static String normalizedStringOption(JSContext context, JSValue options, String key, String fallback,
            String... allowed) {
        String value = getOptionString(context, options, key);
        return context.hasPendingException() ? null : normalizeOption(value, fallback, allowed);
    }

    /**
     * Normalize an offset timezone string to the canonical form: +HH:MM or -HH:MM. -00 and -00:00 normalize to +00:00.
     */
    private static String normalizeOffsetTimeZone(String timeZone) {
        char sign = timeZone.charAt(0);
        String offsetPart = timeZone.substring(1);

        // Reject any non-digit, non-colon characters (e.g., '.', ',')
        for (int i = 0; i < offsetPart.length(); i++) {
            char c = offsetPart.charAt(i);
            if (c != ':' && (c < '0' || c > '9')) {
                return null;
            }
        }

        int hours;
        int minutes;

        if (offsetPart.contains(":")) {
            String[] hm = offsetPart.split(":", -1);
            if (hm.length != 2) {
                return null;
            }
            // Both parts must be exactly 2 digits
            if (hm[0].length() != 2 || hm[1].length() != 2) {
                return null;
            }
            int hourTens = hm[0].charAt(0) - '0';
            int hourOnes = hm[0].charAt(1) - '0';
            int minuteTens = hm[1].charAt(0) - '0';
            int minuteOnes = hm[1].charAt(1) - '0';
            if (hourTens < 0 || hourTens > 9 || hourOnes < 0 || hourOnes > 9 || minuteTens < 0 || minuteTens > 9
                    || minuteOnes < 0 || minuteOnes > 9) {
                return null;
            }
            hours = hourTens * 10 + hourOnes;
            minutes = minuteTens * 10 + minuteOnes;
        } else if (offsetPart.length() == 4) {
            int h0 = offsetPart.charAt(0) - '0';
            int h1 = offsetPart.charAt(1) - '0';
            int m0 = offsetPart.charAt(2) - '0';
            int m1 = offsetPart.charAt(3) - '0';
            if (h0 < 0 || h0 > 9 || h1 < 0 || h1 > 9 || m0 < 0 || m0 > 9 || m1 < 0 || m1 > 9) {
                return null;
            }
            hours = h0 * 10 + h1;
            minutes = m0 * 10 + m1;
        } else if (offsetPart.length() == 2) {
            int h0 = offsetPart.charAt(0) - '0';
            int h1 = offsetPart.charAt(1) - '0';
            if (h0 < 0 || h0 > 9 || h1 < 0 || h1 > 9) {
                return null;
            }
            hours = h0 * 10 + h1;
            minutes = 0;
        } else {
            return null;
        }

        if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
            return null;
        }

        // Normalize -00:00 to +00:00
        if (hours == 0 && minutes == 0) {
            sign = '+';
        }

        return String.format("%c%02d:%02d", sign, hours, minutes);
    }

    private static String normalizeOption(String rawValue, String defaultValue, String... allowedValues) {
        if (rawValue == null) {
            return defaultValue;
        }
        for (String allowedValue : allowedValues) {
            if (allowedValue.equals(rawValue)) {
                return allowedValue;
            }
        }
        throw new IllegalArgumentException("Invalid option value: " + rawValue);
    }

    private static String normalizeRelativeTimeUnit(String unit) {
        return switch (unit) {
            case "second", "seconds" -> "second";
            case "minute", "minutes" -> "minute";
            case "hour", "hours" -> "hour";
            case "day", "days" -> "day";
            case "week", "weeks" -> "week";
            case "month", "months" -> "month";
            case "quarter", "quarters" -> "quarter";
            case "year", "years" -> "year";
            default -> throw new IllegalArgumentException("Invalid unit: " + unit);
        };
    }

    private static String nullIfEmpty(String value) {
        return (value != null && !value.isEmpty()) ? value : null;
    }

    public static JSValue numberFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlNumberFormat numberFormat)) {
            return context.throwTypeError("Intl.NumberFormat.prototype.format called on incompatible receiver");
        }
        JSValue value = JSValue.argumentOrUndefined(args, 0);
        if (value instanceof JSBigInt bigInt) {
            return new JSString(numberFormat.format(bigInt.value()));
        }
        if (value instanceof JSString jsString) {
            return new JSString(numberFormat.format(jsString.value()));
        }
        double number = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(numberFormat.format(number));
    }

    // ---- Locale option validation helpers ----

    public static JSValue numberFormatFormatGetter(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlNumberFormat numberFormat)) {
            return context.throwTypeError("Intl.NumberFormat.prototype.format called on incompatible receiver");
        }
        JSFunction cachedBoundFormatFunction = numberFormat.getBoundFormatFunction();
        if (cachedBoundFormatFunction != null) {
            return cachedBoundFormatFunction;
        }
        JSNativeFunction boundFormatFunction = new JSNativeFunction(context, "", 1,
                (childContext, thisValue, formatArgs) -> numberFormatFormat(childContext, numberFormat, formatArgs));
        context.transferPrototype(boundFormatFunction, JSFunction.NAME);
        numberFormat.setBoundFormatFunction(boundFormatFunction);
        return boundFormatFunction;
    }

    public static JSValue numberFormatFormatRange(JSContext context, JSValue thisArg, JSValue[] args) {
        return formatNumberRange(context, thisArg, args, false);
    }

    public static JSValue numberFormatFormatRangeToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return formatNumberRange(context, thisArg, args, true);
    }

    public static JSValue numberFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlNumberFormat numberFormat)) {
            return context.throwTypeError("Intl.NumberFormat.prototype.formatToParts called on incompatible receiver");
        }
        if (args.length == 0) {
            return numberFormat.formatToParts(context, Double.NaN);
        }
        JSValue arg = args[0];
        if (arg instanceof JSBigInt jsBigInt) {
            return numberFormat.formatToParts(context, jsBigInt.value().toString());
        }
        if (arg instanceof JSString str) {
            return numberFormat.formatToParts(context, str.value());
        }
        double numValue = JSTypeConversions.toNumber(context, arg).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return numberFormat.formatToParts(context, numValue);
    }

    public static JSValue numberFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlNumberFormat numberFormat)) {
            return context
                    .throwTypeError("Intl.NumberFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        createDataProperty(resolvedOptions, "locale", new JSString(numberFormat.getLocale().toLanguageTag()));
        createDataProperty(resolvedOptions, "numberingSystem", new JSString(numberFormat.getNumberingSystem()));
        createDataProperty(resolvedOptions, "style", new JSString(numberFormat.getStyle()));
        if ("currency".equals(numberFormat.getStyle()) && numberFormat.getCurrency() != null) {
            createDataProperty(resolvedOptions, "currency", new JSString(numberFormat.getCurrency()));
            createDataProperty(resolvedOptions, "currencyDisplay", new JSString(numberFormat.getCurrencyDisplay()));
            createDataProperty(resolvedOptions, "currencySign", new JSString(numberFormat.getCurrencySign()));
        }
        if ("unit".equals(numberFormat.getStyle()) && numberFormat.getUnit() != null) {
            createDataProperty(resolvedOptions, "unit", new JSString(numberFormat.getUnit()));
            createDataProperty(resolvedOptions, "unitDisplay", new JSString(numberFormat.getUnitDisplay()));
        }
        createDataProperty(resolvedOptions, "minimumIntegerDigits",
                JSNumber.of(numberFormat.getMinimumIntegerDigits()));
        if (numberFormat.getUseSignificantDigits()) {
            defineDigitRange(resolvedOptions, true, numberFormat.getMinimumSignificantDigits(),
                    numberFormat.getMaximumSignificantDigits());
        } else {
            defineDigitRange(resolvedOptions, false, numberFormat.getMinimumFractionDigits(),
                    numberFormat.getMaximumFractionDigits());
        }
        if ("false".equals(numberFormat.getUseGroupingMode())) {
            createDataProperty(resolvedOptions, "useGrouping", JSBoolean.FALSE);
        } else {
            createDataProperty(resolvedOptions, "useGrouping", new JSString(numberFormat.getUseGroupingMode()));
        }
        createDataProperty(resolvedOptions, "notation", new JSString(numberFormat.getNotation()));
        if ("compact".equals(numberFormat.getNotation()) && numberFormat.getCompactDisplay() != null) {
            createDataProperty(resolvedOptions, "compactDisplay", new JSString(numberFormat.getCompactDisplay()));
        }
        createDataProperty(resolvedOptions, "signDisplay", new JSString(numberFormat.getSignDisplay()));
        createDataProperty(resolvedOptions, "roundingIncrement", JSNumber.of(numberFormat.getRoundingIncrement()));
        createDataProperty(resolvedOptions, "roundingMode", new JSString(numberFormat.getRoundingMode()));
        createDataProperty(resolvedOptions, "roundingPriority", new JSString(numberFormat.getRoundingPriority()));
        createDataProperty(resolvedOptions, "trailingZeroDisplay", new JSString(numberFormat.getTrailingZeroDisplay()));
        return resolvedOptions;
    }

    private static JSValue numberRangeParts(JSContext context, JSIntlNumberFormat numberFormat, JSValue startValue,
            JSValue endValue, boolean equalAfterFormatting) {
        JSValue startPartsValue = numberFormatFormatToParts(context, numberFormat, new JSValue[]{startValue});
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSValue endPartsValue = numberFormatFormatToParts(context, numberFormat, new JSValue[]{endValue});
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(startPartsValue instanceof JSArray startParts) || !(endPartsValue instanceof JSArray endParts)) {
            return context.throwTypeError("formatToParts did not return an array");
        }

        JSArray result = context.createJSArray();
        if (equalAfterFormatting) {
            result.push(createPartObject(context, "approximatelySign", "~", "shared"));
            appendRangePartsWithSource(context, result, startParts, "shared");
            return result;
        }

        appendRangePartsWithSource(context, result, startParts, "startRange");
        String localeTag = numberFormat.getLocale().toLanguageTag();
        String separator = localeTag.startsWith("pt") ? " - " : " \u2013 ";
        result.push(createPartObject(context, "literal", separator, "shared"));
        appendRangePartsWithSource(context, result, endParts, "endRange");
        return result;
    }

    private static FormatStyle parseFormatStyle(String rawStyle) {
        if (rawStyle == null) {
            return null;
        }
        return switch (rawStyle) {
            case "short" -> FormatStyle.SHORT;
            case "medium" -> FormatStyle.MEDIUM;
            case "long" -> FormatStyle.LONG;
            case "full" -> FormatStyle.FULL;
            default -> null;
        };
    }

    private static String parseUseGroupingOption(JSContext context, JSValue useGroupingValue, String notation) {
        String defaultValue = "compact".equals(notation) ? "min2" : "auto";
        if (useGroupingValue == null || useGroupingValue instanceof JSUndefined) {
            return defaultValue;
        }
        if (useGroupingValue instanceof JSBoolean jsBoolean) {
            if (jsBoolean.value()) {
                return "always";
            }
            return "false";
        }
        if (useGroupingValue instanceof JSNull) {
            return "false";
        }
        if (useGroupingValue instanceof JSString jsString) {
            String value = jsString.value();
            if ("auto".equals(value) || "always".equals(value) || "min2".equals(value)) {
                return value;
            }
            if ("true".equals(value) || "false".equals(value)) {
                return defaultValue;
            }
            if (value.isEmpty()) {
                return "false";
            }
            context.throwRangeError("Invalid option value: " + value);
            return defaultValue;
        }
        if (useGroupingValue instanceof JSNumber jsNumber) {
            double number = jsNumber.value();
            if (number == 0) {
                return "false";
            }
            context.throwRangeError("Invalid option value: " + useGroupingValue);
            return defaultValue;
        }
        boolean booleanValue = JSTypeConversions.toBoolean(useGroupingValue).value();
        if (!booleanValue) {
            return "false";
        }
        context.throwRangeError(
                "Invalid option value: " + JSTypeConversions.toString(context, useGroupingValue).value());
        return defaultValue;
    }

    // ---- New Locale prototype methods ----

    public static JSValue pluralRulesResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlPluralRules pluralRules)) {
            return context.throwTypeError("Intl.PluralRules.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        createDataProperty(resolvedOptions, "locale", new JSString(pluralRules.getLocale().toLanguageTag()));
        createDataProperty(resolvedOptions, "type", new JSString(pluralRules.getType()));
        createDataProperty(resolvedOptions, "notation", new JSString(pluralRules.getNotation()));
        createDataProperty(resolvedOptions, "minimumIntegerDigits", JSNumber.of(pluralRules.getMinimumIntegerDigits()));
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
        createDataProperty(resolvedOptions, "pluralCategories", categories);
        return resolvedOptions;
    }

    public static JSValue pluralRulesSelect(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlPluralRules pluralRules)) {
            return context.throwTypeError("Intl.PluralRules.prototype.select called on incompatible receiver");
        }
        double value = args.length > 0 ? JSTypeConversions.toNumber(context, args[0]).value() : Double.NaN;
        return new JSString(pluralRules.select(value));
    }

    public static JSValue pluralRulesSelectRange(JSContext context, JSValue thisArg, JSValue[] args) {
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

    private static void putLocaleOption(Map<String, String> extensions, String key, String value) {
        if (value != null) {
            extensions.put(key, value);
        }
    }

    private static int readMinimumIntegerDigits(JSContext context, JSObject options) {
        Integer digits = getOptionIntegerOrUndefined(context, options, "minimumIntegerDigits", 1, 21);
        return digits == null ? 1 : digits;
    }

    private static RoundingOptions readRoundingOptions(JSContext context, JSObject optionsObject,
            boolean validateIncrement) {
        Integer increment = getOptionIntegerOrUndefined(context, optionsObject, "roundingIncrement", 1, 5000);
        if (context.hasPendingException()) {
            return null;
        }
        int roundingIncrement = increment == null ? 1 : increment;
        if (validateIncrement && !VALID_ROUNDING_INCREMENTS.contains(roundingIncrement)) {
            context.throwRangeError("Invalid roundingIncrement value: " + roundingIncrement);
            return null;
        }
        String mode = normalizedStringOption(context, optionsObject, "roundingMode", "halfExpand",
                ROUNDING_MODE_VALUES);
        String priority = normalizedStringOption(context, optionsObject, "roundingPriority", "auto",
                ROUNDING_PRIORITY_VALUES);
        String trailingZero = normalizedStringOption(context, optionsObject, "trailingZeroDisplay", "auto",
                TRAILING_ZERO_DISPLAY_VALUES);
        return context.hasPendingException()
                ? null
                : new RoundingOptions(roundingIncrement, mode, priority, trailingZero);
    }

    /**
     * Read a string option from options object. Returns null if option is undefined.
     */
    private static String readStringOption(JSContext context, JSObject optionsObject, String key) {
        if (context.hasPendingException()) {
            return null;
        }
        JSValue rawValue = optionsObject.get(PropertyKey.fromString(key));
        if (context.hasPendingException()) {
            return null;
        }
        if (rawValue == null || rawValue instanceof JSUndefined) {
            return null;
        }
        return JSTypeConversions.toString(context, rawValue).value();
    }

    /**
     * Read a string option from options object, validate against allowed values. Returns null if option is undefined.
     * Throws RangeError for invalid values.
     */
    private static String readValidatedOption(JSContext context, JSObject optionsObject, String key,
            String... allowedValues) {
        String stringValue = readStringOption(context, optionsObject, key);
        if (stringValue == null || context.hasPendingException()) {
            return null;
        }
        for (String allowed : allowedValues) {
            if (allowed.equals(stringValue)) {
                return stringValue;
            }
        }
        context.throwRangeError("Value " + stringValue + " out of range for property " + key);
        return null;
    }

    public static JSValue relativeTimeFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlRelativeTimeFormat relativeTimeFormat)) {
            return context.throwTypeError("Intl.RelativeTimeFormat.prototype.format called on incompatible receiver");
        }
        if (args.length < 2) {
            return context.throwTypeError("Intl.RelativeTimeFormat.prototype.format requires value and unit");
        }
        double value = JSTypeConversions.toNumber(context, args[0]).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String unit = JSTypeConversions.toString(context, args[1]).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        try {
            return new JSString(relativeTimeFormat.format(value, unit));
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue relativeTimeFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlRelativeTimeFormat relativeTimeFormat)) {
            return context
                    .throwTypeError("Intl.RelativeTimeFormat.prototype.formatToParts called on incompatible receiver");
        }
        if (args.length < 2) {
            return context.throwTypeError("Intl.RelativeTimeFormat.prototype.formatToParts requires value and unit");
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
            String normalizedUnit = normalizeRelativeTimeUnit(unitValue);
            String formatted = relativeTimeFormat.format(value, normalizedUnit);
            double absoluteValue = Math.abs(value);
            JSIntlNumberFormat numberFormat = createRelativeTimeNumberFormat(relativeTimeFormat);
            String numberText = numberFormat.format(absoluteValue);
            int numberStart = formatted.indexOf(numberText);

            JSArray result = context.createJSArray();
            if (numberStart < 0) {
                result.push(createRelativeTimeLiteralPart(context, formatted));
                return result;
            }

            String prefix = formatted.substring(0, numberStart);
            if (!prefix.isEmpty()) {
                result.push(createRelativeTimeLiteralPart(context, prefix));
            }

            JSArray numberParts = numberFormat.formatToParts(context, absoluteValue);
            for (int index = 0; index < numberParts.getLength(); index++) {
                JSValue numberPartValue = numberParts.get(index);
                if (numberPartValue instanceof JSObject numberPartObject) {
                    createDataProperty(numberPartObject, "unit", new JSString(normalizedUnit));
                    result.push(numberPartObject);
                }
            }

            String suffix = formatted.substring(numberStart + numberText.length());
            if ("pl".equals(relativeTimeFormat.getLocale().getLanguage()) && !isPolishIntegerPlural(absoluteValue)) {
                boolean isPast = value < 0 || isNegativeZero(value);
                String polishOtherUnit = getPolishOtherUnit(relativeTimeFormat.getStyle(), normalizedUnit);
                suffix = isPast ? " " + polishOtherUnit + " temu" : " " + polishOtherUnit;
            }
            if (!suffix.isEmpty()) {
                result.push(createRelativeTimeLiteralPart(context, suffix));
            }
            return result;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
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

    private static String resolveHourCycle(JSIntlDateTimeFormat formatter) {
        String cycle = formatter.getHourCycle();
        if (cycle != null) {
            return cycle;
        }
        cycle = formatter.getHourCycleForInstant();
        if (cycle != null) {
            return cycle;
        }
        Locale locale = formatter.getLocale();
        cycle = IntlLocaleTag.parseUnicodeExtensions(locale.toLanguageTag()).get("hc");
        return "h11".equals(cycle) || "h12".equals(cycle) || "h23".equals(cycle) || "h24".equals(cycle)
                ? cycle
                : getLocaleDefaultHourCycle(locale);
    }

    /**
     * Resolve the prototype for an Intl constructor per GetPrototypeFromConstructor. When called via Reflect.construct
     * with a newTarget from another realm whose prototype is not an object, falls back to that realm's
     * Intl.X.prototype.
     */
    private static JSObject resolveIntlPrototype(JSContext context, JSObject defaultPrototype,
            String intlConstructorName) {
        JSValue newTarget = context.getNativeConstructorNewTarget();
        if (!(newTarget instanceof JSObject newTargetObject)) {
            return defaultPrototype;
        }
        JSValue newTargetProto = newTargetObject.get(PropertyKey.PROTOTYPE);
        if (context.hasPendingException()) {
            return null;
        }
        if (newTargetProto instanceof JSObject newTargetProtoObj) {
            return newTargetProtoObj;
        }
        // newTarget.prototype is not an object; fall back to realm's Intl.X.prototype
        JSContext functionRealm = context.getFunctionRealm(newTargetObject);
        if (context.hasPendingException()) {
            return null;
        }
        JSValue intlObj = functionRealm.getGlobalObject().get("Intl");
        if (intlObj instanceof JSObject intlObject) {
            JSValue ctor = intlObject.get(intlConstructorName);
            if (ctor instanceof JSObject ctorObj) {
                JSValue proto = ctorObj.get("prototype");
                if (proto instanceof JSObject protoObj) {
                    return protoObj;
                }
            }
        }
        return defaultPrototype;
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
                String localeMatcher = getOptionString(context, optionsObj, "localeMatcher");
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (localeMatcher != null && !"lookup".equals(localeMatcher) && !"best fit".equals(localeMatcher)) {
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
                values = new ArrayList<>(SUPPORTED_NUMBERING_SYSTEMS);
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
            case "unit" -> {
                values = new ArrayList<>(List.of("acre", "bit", "byte", "celsius", "centimeter", "day", "degree",
                        "fahrenheit", "fluid-ounce", "foot", "gallon", "gigabit", "gigabyte", "gram", "hectare", "hour",
                        "inch", "kilobit", "kilobyte", "kilogram", "kilometer", "liter", "megabit", "megabyte", "meter",
                        "microsecond", "mile", "mile-scandinavian", "milliliter", "millimeter", "millisecond", "minute",
                        "month", "nanosecond", "ounce", "percent", "petabyte", "pound", "second", "stone", "terabit",
                        "terabyte", "week", "yard", "year"));
            }
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

    private static DateStyleOptionFields toDateStyleOptionFields(FormatStyle dateStyle) {
        return switch (dateStyle) {
            case FULL -> new DateStyleOptionFields("long", "numeric", "long", "numeric");
            case LONG -> new DateStyleOptionFields(null, "numeric", "long", "numeric");
            case MEDIUM -> new DateStyleOptionFields(null, "numeric", "short", "numeric");
            case SHORT -> new DateStyleOptionFields(null, "2-digit", "numeric", "numeric");
        };
    }

    private static DateTimeFormattable toDateTimeFormattable(JSContext context, JSValue value,
            boolean useCurrentTimeForUndefined) {
        if (useCurrentTimeForUndefined && value instanceof JSUndefined) {
            return new DateTimeFormattable(DateTimeFormattableKind.NUMBER, System.currentTimeMillis(), null);
        }

        if (value instanceof JSDate jsDate) {
            return new DateTimeFormattable(DateTimeFormattableKind.NUMBER, jsDate.getTimeValue(), null);
        }
        if (value instanceof JSTemporalInstant temporalInstant) {
            return new DateTimeFormattable(DateTimeFormattableKind.INSTANT, toEpochMillis(temporalInstant), null);
        }
        if (value instanceof JSTemporalPlainDate temporalPlainDate) {
            double epochMillis = toEpochMillis(temporalPlainDate, context);
            if (context.hasPendingException()) {
                return null;
            }
            return new DateTimeFormattable(DateTimeFormattableKind.PLAIN_DATE, epochMillis,
                    temporalPlainDate.getCalendarId());
        }
        if (value instanceof JSTemporalPlainDateTime temporalPlainDateTime) {
            double epochMillis = toEpochMillis(temporalPlainDateTime, context);
            if (context.hasPendingException()) {
                return null;
            }
            return new DateTimeFormattable(DateTimeFormattableKind.PLAIN_DATE_TIME, epochMillis,
                    temporalPlainDateTime.getCalendarId());
        }
        if (value instanceof JSTemporalPlainMonthDay temporalPlainMonthDay) {
            double epochMillis = toEpochMillis(temporalPlainMonthDay, context);
            if (context.hasPendingException()) {
                return null;
            }
            return new DateTimeFormattable(DateTimeFormattableKind.PLAIN_MONTH_DAY, epochMillis,
                    temporalPlainMonthDay.getCalendarId());
        }
        if (value instanceof JSTemporalPlainTime temporalPlainTime) {
            double epochMillis = toEpochMillis(temporalPlainTime, context);
            if (context.hasPendingException()) {
                return null;
            }
            return new DateTimeFormattable(DateTimeFormattableKind.PLAIN_TIME, epochMillis, null);
        }
        if (value instanceof JSTemporalPlainYearMonth temporalPlainYearMonth) {
            double epochMillis = toEpochMillis(temporalPlainYearMonth, context);
            if (context.hasPendingException()) {
                return null;
            }
            return new DateTimeFormattable(DateTimeFormattableKind.PLAIN_YEAR_MONTH, epochMillis,
                    temporalPlainYearMonth.getCalendarId());
        }
        if (value instanceof JSTemporalZonedDateTime temporalZonedDateTime) {
            return new DateTimeFormattable(DateTimeFormattableKind.ZONED_DATE_TIME,
                    toEpochMillis(temporalZonedDateTime), temporalZonedDateTime.getCalendarId());
        }

        double epochMillis = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return null;
        }
        return new DateTimeFormattable(DateTimeFormattableKind.NUMBER, epochMillis, null);
    }

    private static double toEpochMillis(int year, int month, int day, int hour, int minute, int second, int millisecond,
            int microsecond, int nanosecond, JSContext context, String errorMessage) {
        int nanosecondOfSecond = millisecond * 1_000_000 + microsecond * 1_000 + nanosecond;
        try {
            LocalDate localDate = LocalDate.of(year, month, day);
            return localDate.atTime(hour, minute, second, nanosecondOfSecond).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeException e) {
            context.throwRangeError(errorMessage);
            return Double.NaN;
        }
    }

    private static double toEpochMillis(JSTemporalInstant temporalInstant) {
        return toEpochMillisFromEpochNanoseconds(temporalInstant.getEpochNanoseconds());
    }

    private static double toEpochMillis(JSTemporalPlainDate temporalPlainDate, JSContext context) {
        try {
            LocalDate localDate = LocalDate.of(temporalPlainDate.getIsoDate().year(),
                    temporalPlainDate.getIsoDate().month(), temporalPlainDate.getIsoDate().day());
            return localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        } catch (DateTimeException e) {
            context.throwRangeError("Invalid Temporal.PlainDate value");
            return Double.NaN;
        }
    }

    private static double toEpochMillis(JSTemporalPlainDateTime temporalPlainDateTime, JSContext context) {
        try {
            return toEpochMillis(temporalPlainDateTime.getIsoDateTime().date().year(),
                    temporalPlainDateTime.getIsoDateTime().date().month(),
                    temporalPlainDateTime.getIsoDateTime().date().day(),
                    temporalPlainDateTime.getIsoDateTime().time().hour(),
                    temporalPlainDateTime.getIsoDateTime().time().minute(),
                    temporalPlainDateTime.getIsoDateTime().time().second(),
                    temporalPlainDateTime.getIsoDateTime().time().millisecond(),
                    temporalPlainDateTime.getIsoDateTime().time().microsecond(),
                    temporalPlainDateTime.getIsoDateTime().time().nanosecond(), context,
                    "Invalid Temporal.PlainDateTime value");
        } catch (DateTimeException e) {
            context.throwRangeError("Invalid Temporal.PlainDateTime value");
            return Double.NaN;
        }
    }

    private static double toEpochMillis(JSTemporalPlainMonthDay temporalPlainMonthDay, JSContext context) {
        try {
            LocalDate referenceDate = LocalDate.of(temporalPlainMonthDay.getIsoDate().year(),
                    temporalPlainMonthDay.getIsoDate().month(), temporalPlainMonthDay.getIsoDate().day());
            return referenceDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        } catch (DateTimeException e) {
            context.throwRangeError("Invalid Temporal.PlainMonthDay value");
            return Double.NaN;
        }
    }

    private static double toEpochMillis(JSTemporalPlainTime temporalPlainTime, JSContext context) {
        try {
            return toEpochMillis(1970, 1, 1, temporalPlainTime.getIsoTime().hour(),
                    temporalPlainTime.getIsoTime().minute(), temporalPlainTime.getIsoTime().second(),
                    temporalPlainTime.getIsoTime().millisecond(), temporalPlainTime.getIsoTime().microsecond(),
                    temporalPlainTime.getIsoTime().nanosecond(), context, "Invalid Temporal.PlainTime value");
        } catch (DateTimeException e) {
            context.throwRangeError("Invalid Temporal.PlainTime value");
            return Double.NaN;
        }
    }

    private static double toEpochMillis(JSTemporalPlainYearMonth temporalPlainYearMonth, JSContext context) {
        try {
            LocalDate localDate = LocalDate.of(temporalPlainYearMonth.getIsoDate().year(),
                    temporalPlainYearMonth.getIsoDate().month(), temporalPlainYearMonth.getIsoDate().day());
            return localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        } catch (DateTimeException e) {
            context.throwRangeError("Invalid Temporal.PlainYearMonth value");
            return Double.NaN;
        }
    }

    private static double toEpochMillis(JSTemporalZonedDateTime temporalZonedDateTime) {
        return toEpochMillisFromEpochNanoseconds(temporalZonedDateTime.getEpochNanoseconds());
    }

    private static double toEpochMillisFromEpochNanoseconds(BigInteger epochNanoseconds) {
        BigInteger[] quotientAndRemainder = epochNanoseconds.divideAndRemainder(NANOS_PER_MILLISECOND);
        BigInteger epochMilliseconds = quotientAndRemainder[0];
        if (epochNanoseconds.signum() < 0 && quotientAndRemainder[1].signum() != 0) {
            epochMilliseconds = epochMilliseconds.subtract(BigInteger.ONE);
        }
        return epochMilliseconds.doubleValue();
    }

    /**
     * Validate and canonicalize a time zone string. Returns the canonical time zone name, or null if invalid.
     */
    private static String validateAndCanonicalizeTimeZone(String timeZone) {
        if (timeZone == null || timeZone.isEmpty()) {
            return null;
        }
        // Reject U+2212 MINUS SIGN per ECMA-402: only ASCII hyphen-minus is valid
        if (timeZone.indexOf('\u2212') >= 0) {
            return null;
        }
        // Reject non-ASCII letters
        for (int i = 0; i < timeZone.length(); i++) {
            char c = timeZone.charAt(i);
            if (c > 127 && Character.isLetter(c)) {
                return null;
            }
        }
        // Check for offset timezone format
        if (timeZone.startsWith("+") || timeZone.startsWith("-")) {
            return normalizeOffsetTimeZone(timeZone);
        }
        // Named timezone - case-insensitive lookup via the comprehensive map
        String canonical = getTimezoneLookupMap().get(timeZone.toLowerCase(Locale.ROOT));
        return canonical;
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

    private static void writeDateRange(JSIntlDateTimeFormat format, List<JSIntlDateTimeFormat.DatePart> start,
            List<JSIntlDateTimeFormat.DatePart> end, DateRangeOutput output) {
        int first = 0;
        int startLimit = start.size();
        int endLimit = end.size();
        if (startLimit == endLimit) {
            while (first < startLimit && start.get(first).equals(end.get(first))) {
                first++;
            }
            if (first == startLimit) {
                output.append(start, 0, first, "shared");
                return;
            }
            boolean sameYear = true;
            for (int i = first; i < startLimit; i++) {
                if ("year".equals(start.get(i).type()) && !start.get(i).equals(end.get(i))) {
                    sameYear = false;
                    break;
                }
            }
            if ((format.hasTextMonth() && sameYear) || differsOnlyInTimeParts(start, end)) {
                while (startLimit > first && start.get(startLimit - 1).equals(end.get(endLimit - 1))) {
                    startLimit--;
                    endLimit--;
                }
            } else {
                first = 0;
            }
        }
        output.append(start, 0, first, "shared");
        output.append(start, first, startLimit, "startRange");
        output.append("literal", " \u2013 ", "shared");
        output.append(end, first, endLimit, "endRange");
        output.append(start, startLimit, start.size(), "shared");
    }

    private interface DateRangeOutput {
        default void append(List<JSIntlDateTimeFormat.DatePart> parts, int start, int end, String source) {
            for (int i = start; i < end; i++) {
                JSIntlDateTimeFormat.DatePart part = parts.get(i);
                append(part.type(), part.value(), source);
            }
        }

        void append(String type, String value, String source);
    }

    private record DateStyleOptionFields(String weekdayOption, String yearOption, String monthOption,
            String dayOption) {
    }

    private record DateTimeFormattable(DateTimeFormattableKind kind, double epochMillis,
            TemporalCalendarId calendarId) {
    }

    private enum DateTimeFormattableKind {
        INSTANT, NUMBER, PLAIN_DATE, PLAIN_DATE_TIME, PLAIN_MONTH_DAY, PLAIN_TIME, PLAIN_YEAR_MONTH, ZONED_DATE_TIME
    }

    private record DigitRange(Integer minimum, Integer maximum) {
        static DigitRange readFraction(JSContext context, JSObject options) {
            return read(context, options, "minimumFractionDigits", "maximumFractionDigits", 0, 100);
        }

        static DigitRange readSignificant(JSContext context, JSObject options) {
            return read(context, options, "minimumSignificantDigits", "maximumSignificantDigits", 1, 21);
        }

        static DigitRange read(JSContext context, JSObject options, String minKey, String maxKey, int low, int high) {
            Integer min = getOptionIntegerOrUndefined(context, options, minKey, low, high);
            if (context.hasPendingException()) {
                return null;
            }
            Integer max = getOptionIntegerOrUndefined(context, options, maxKey, low, high);
            return context.hasPendingException() ? null : new DigitRange(min, max);
        }

        boolean specified() {
            return minimum != null || maximum != null;
        }

        DigitRange resolveFraction(int defaultMin, int defaultMax) {
            int min = minimum != null ? minimum : maximum == null ? defaultMin : Math.min(defaultMin, maximum);
            int max = maximum != null ? maximum : Math.max(min, defaultMax);
            if (min > max) {
                throw new IllegalArgumentException("minimumFractionDigits value is out of range");
            }
            return new DigitRange(min, max);
        }

        DigitRange resolveSignificant() {
            int min = minimum == null ? 1 : minimum;
            int max = maximum == null ? 21 : maximum;
            if (min > max) {
                throw new IllegalArgumentException("minimumSignificantDigits value is out of range");
            }
            return new DigitRange(min, max);
        }
    }

    private record NumberingLocale(Locale locale, String numberingSystem) {
        static NumberingLocale resolve(Locale locale, String option) {
            boolean hasExtensions = locale.hasExtensions();
            String extension = hasExtensions
                    ? IntlLocaleTag.parseUnicodeExtensions(locale.toLanguageTag()).get("nu")
                    : null;
            if (extension != null) {
                extension = extension.toLowerCase(Locale.ROOT);
            }
            String system = "latn";
            if (option != null && SUPPORTED_NUMBERING_SYSTEMS.contains(option)) {
                system = option;
            } else if (extension != null && SUPPORTED_NUMBERING_SYSTEMS.contains(extension)) {
                system = extension;
            }
            Locale resolved = hasExtensions ? IntlLocaleTag.stripUnicodeExtensions(locale) : locale;
            if (system.equals(extension)) {
                resolved = new Locale.Builder().setLocale(resolved).setUnicodeLocaleKeyword("nu", system).build();
            }
            return new NumberingLocale(resolved, system);
        }
    }

    private record RoundingOptions(int roundingIncrement, String roundingMode, String roundingPriority,
            String trailingZeroDisplay) {
    }
}
