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

import static com.caoccao.qjs4j.core.intl.IntlBuiltins.canonicalizeLocaleTag;
import static com.caoccao.qjs4j.core.intl.IntlBuiltins.createDataProperty;
import static com.caoccao.qjs4j.core.intl.IntlBuiltins.getLocaleDefaultHourCycle;

/** ECMA-402 Intl.Locale construction, accessors and locale information operations. */
final class IntlLocaleBuiltins {
    private IntlLocaleBuiltins() {
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
            Locale updated = maximize ? IntlLikelySubtags.maximize(baseLocale) : IntlLikelySubtags.minimize(baseLocale);
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

            JSValue options = JSValue.argumentOrUndefined(args, 1);
            if (!(options instanceof JSUndefined)) {
                options = IntlFormatOptions.coerceOptionsObject(context, options);
                if (context.hasPendingException()) {
                    return options;
                }
            }
            IntlLocaleOptions overrides = IntlLocaleOptions.read(context, options);
            if (overrides == null) {
                return JSUndefined.INSTANCE;
            }

            Locale locale = IntlLocaleTag.parseLocaleTag(localeTag);

            // Strip Java-added private use for grandfathered tags.
            // Java maps grandfathered tags like "cel-gaulish" to "xtg-x-cel-gaulish"
            // but the x-cel-gaulish part is not from user input and must be removed.
            if (!localeTag.toLowerCase(Locale.ROOT).contains("-x-") && locale.getExtension('x') != null) {
                locale = new Locale.Builder().setLocale(locale).setExtension('x', null).build();
            }

            // Apply language/script/region/variants overrides from options
            Locale.Builder builder = new Locale.Builder().setLocale(locale);
            overrides.applyLanguageId(builder);
            Map<String, String> extensions = new TreeMap<>(
                    IntlLocaleTag.parseUnicodeExtensions(locale.toLanguageTag()));
            overrides.applyExtensions(builder, extensions);
            locale = builder.build();
            String rawTag = locale.toLanguageTag();
            // Java omits "und" prefix for undetermined language with only extensions/private-use
            if (locale.getLanguage().isEmpty() && rawTag.startsWith("x-")) {
                rawTag = "und-" + rawTag;
            }
            String tag = canonicalizeLocaleTag(rawTag);

            // Compute final values for the JSIntlLocale fields from the merged extensions
            String calendar = localeOption(extensions, "ca", null);
            String caseFirst = localeOption(extensions, "kf", "");
            String collation = localeOption(extensions, "co", null);
            String firstDayOfWeek = localeOption(extensions, "fw", "");
            String hourCycle = localeOption(extensions, "hc", null);
            String numberingSystem = localeOption(extensions, "nu", null);
            String finalKn = extensions.get("kn");
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

    private static JSArray singleStringArray(JSContext context, String value) {
        JSArray result = context.createJSArray();
        result.push(new JSString(value));
        return result;
    }

}
