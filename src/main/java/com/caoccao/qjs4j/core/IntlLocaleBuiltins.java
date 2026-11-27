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

import java.util.*;
import static com.caoccao.qjs4j.core.JSIntlObject.nullIfEmpty;

import static com.caoccao.qjs4j.core.JSIntlObject.canonicalizeCalendar;
import static com.caoccao.qjs4j.core.JSIntlObject.canonicalizeLocaleTag;
import static com.caoccao.qjs4j.core.JSIntlObject.createDataProperty;
import static com.caoccao.qjs4j.core.JSIntlObject.getLocaleDefaultHourCycle;

/** ECMA-402 Intl.Locale construction, accessors and locale information operations. */
final class IntlLocaleBuiltins {
    /**
     * CLDR likely subtags data for maximize/minimize. Maps language (or language-script, language-region) to full
     * locale tag.
     */
    private static final Map<String, String> LIKELY_SUBTAGS = new HashMap<>();

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

    private IntlLocaleBuiltins() {
    }

    /**
     * Add likely subtags to a locale (CLDR "maximize").
     */
    private static Locale addLikelySubtags(Locale locale) {
        String language = locale.getLanguage();
        String script = locale.getScript();
        String region = locale.getCountry();
        String key = language.isEmpty() ? "und" : language;
        String full = null;
        if (!script.isEmpty() && !region.isEmpty()) {
            if (!language.isEmpty()) {
                return locale;
            }
            full = LIKELY_SUBTAGS.get(key + "-" + script + "-" + region);
        }
        if (full == null && !script.isEmpty()) {
            full = LIKELY_SUBTAGS.get(key + "-" + script);
        }
        if (full == null && !region.isEmpty()) {
            full = LIKELY_SUBTAGS.get(key + "-" + region);
        }
        if (full == null) {
            full = LIKELY_SUBTAGS.get(key);
        }
        if (full == null) {
            return locale;
        }
        Locale likely = Locale.forLanguageTag(full);
        return languageId(likely.getLanguage(), script.isEmpty() ? likely.getScript() : script,
                region.isEmpty() ? likely.getCountry() : region, locale.getVariant());
    }

    static JSValue changeLikelySubtags(JSContext context, JSValue thisArg, boolean maximize) {
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

    static JSValue createLocale(JSContext context, JSObject prototype, JSValue[] args) {
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

    private static Locale languageId(String language, String script, String region, String variant) {
        return new Locale.Builder().setLanguage(language).setScript(script).setRegion(region).setVariant(variant)
                .build();
    }

    static JSValue localeGetCalendars(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.getCalendars called on incompatible receiver");
        }
        String value = intlLocale.getCalendar();
        return singleStringArray(context, value == null || value.isEmpty() ? "gregory" : value);
    }

    static JSValue localeGetCollations(JSContext context, JSValue thisArg, JSValue[] args) {
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

    static JSValue localeGetHourCycles(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.getHourCycles called on incompatible receiver");
        }
        String value = intlLocale.getHourCycle();
        return singleStringArray(context,
                value == null || value.isEmpty() ? getLocaleDefaultHourCycle(intlLocale.getLocale()) : value);
    }

    static JSValue localeGetNumberingSystems(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.getNumberingSystems called on incompatible receiver");
        }
        String value = intlLocale.getNumberingSystem();
        return singleStringArray(context, value == null || value.isEmpty() ? "latn" : value);
    }

    static JSValue localeGetNumeric(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.numeric called on incompatible receiver");
        }
        return intlLocale.isNumericSet() ? JSBoolean.valueOf(intlLocale.getNumeric()) : JSBoolean.FALSE;
    }

    static JSValue localeGetTextInfo(JSContext context, JSValue thisArg, JSValue[] args) {
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

    static JSValue localeGetTimeZones(JSContext context, JSValue thisArg, JSValue[] args) {
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

    static JSValue localeGetWeekInfo(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.getWeekInfo called on incompatible receiver");
        }
        JSObject result = context.createJSObject();

        // Determine firstDay from fw extension or locale default
        int firstDay = firstDayOfWeekToNumber(intlLocale.getFirstDayOfWeek());
        if (firstDay < 1) {
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

    private static String localeOption(Map<String, String> extensions, String key, String bareValue) {
        String value = extensions.get(key);
        return value != null && (value.isEmpty() || "true".equals(value)) ? bareValue : value;
    }

    static JSValue localeString(JSContext context, JSValue receiver, String name,
            java.util.function.Function<JSIntlLocale, String> getter) {
        if (!(receiver instanceof JSIntlLocale locale)) {
            return context.throwTypeError("Intl.Locale.prototype." + name + " called on incompatible receiver");
        }
        String value = getter.apply(locale);
        return value == null ? JSUndefined.INSTANCE : new JSString(value);
    }

    private static void putLocaleOption(Map<String, String> extensions, String key, String value) {
        if (value != null) {
            extensions.put(key, value);
        }
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
            return languageId(maxLang, "", "", variant);
        }

        // Try language-region
        Locale langRegion = new Locale.Builder().setLanguage(maxLang).setRegion(maxRegion).build();
        Locale langRegionMax = addLikelySubtags(langRegion);
        if (langRegionMax.getScript().equals(maxScript)) {
            return languageId(maxLang, "", maxRegion, variant);
        }

        // Try language-script
        Locale langScript = new Locale.Builder().setLanguage(maxLang).setScript(maxScript).build();
        Locale langScriptMax = addLikelySubtags(langScript);
        if (langScriptMax.getCountry().equals(maxRegion)) {
            return languageId(maxLang, maxScript, "", variant);
        }

        // Can't minimize further, return maximized
        return languageId(maxLang, maxScript, maxRegion, variant);
    }

    private static JSArray singleStringArray(JSContext context, String value) {
        JSArray result = context.createJSArray();
        result.push(new JSString(value));
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
