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
    private static final String[] FORMAT_STYLE_VALUES = {"short", "medium", "long", "full"};
    // ---- CLDR Language Alias Data ----
    // Maps: old language code → new language code
    // Some entries have additional script/region that are added only if not already present
    private static final Map<String, String> LANGUAGE_ALIASES = new HashMap<>();
    // Maps: old language → region to add if not already present
    private static final Map<String, String> LANGUAGE_REGION_ADDITIONS = new HashMap<>();
    // Maps: old language → script to add if not already present
    private static final Map<String, String> LANGUAGE_SCRIPT_ADDITIONS = new HashMap<>();
    /**
     * CLDR likely subtags data for maximize/minimize.
     * Maps language (or language-script, language-region) to full locale tag.
     */
    private static final Map<String, String> LIKELY_SUBTAGS = new HashMap<>();
    private static final BigInteger NANOS_PER_MILLISECOND = BigInteger.valueOf(1_000_000L);
    // ---- Region Alias Data ----
    // Maps: old region → {language/script → new region}
    // Default replacement is first, specific language/script replacements follow
    private static final Map<String, String> REGION_ALIASES = new HashMap<>();
    private static final Map<String, Map<String, String>> REGION_ALIASES_BY_LANG = new HashMap<>();
    // Script-based region resolution for SU/810
    private static final Map<String, String> REGION_BY_SCRIPT = new HashMap<>();
    private static final Set<String> SANCTIONED_SIMPLE_UNITS = Set.of(
            "acre", "bit", "byte", "celsius", "centimeter", "day",
            "degree", "fahrenheit", "fluid-ounce", "foot", "gallon",
            "gigabit", "gigabyte", "gram", "hectare", "hour", "inch",
            "kilobit", "kilobyte", "kilogram", "kilometer", "liter",
            "megabit", "megabyte", "meter", "microsecond", "mile",
            "mile-scandinavian", "milliliter", "millimeter", "millisecond",
            "minute", "month", "nanosecond", "ounce", "percent", "petabyte",
            "pound", "second", "stone", "terabit", "terabyte", "week",
            "yard", "year"
    );
    // ---- Subdivision Aliases ----
    private static final Map<String, String> SUBDIVISION_ALIASES = new HashMap<>();
    /**
     * Supplementary valid IANA timezone IDs not present in ZoneId.getAvailableZoneIds().
     * These are real IANA TZDB entries that some JDK versions omit.
     */
    private static final String[] SUPPLEMENTARY_TIMEZONE_IDS = {
            "EST", "MST", "HST",         // IANA backward-compat Zone entries
            "GMT+0", "GMT-0", "GMT0",     // IANA backward-compat Link entries
            "ROC",                         // Republic of China → Asia/Taipei
    };
    /**
     * Supported numbering systems with simple digit mappings (per ECMA-402 Table 10).
     * Used by supportedValuesOf("numberingSystem") and validated in constructors.
     */
    private static final Set<String> SUPPORTED_NUMBERING_SYSTEMS = Set.of(
            "adlm", "ahom", "arab", "arabext", "bali", "beng", "bhks",
            "brah", "cakm", "cham", "deva", "diak", "fullwide", "gong",
            "gara", "gonm", "gujr", "gukh", "guru", "hanidec", "hmng", "hmnp", "java",
            "kali", "kawi", "khmr", "knda", "krai", "lana", "lanatham", "laoo", "latn",
            "lepc", "limb", "mathbold", "mathdbl", "mathmono", "mathsanb",
            "mathsans", "mlym", "modi", "mong", "mroo", "mtei", "mymr",
            "mymrepka", "mymrpao", "mymrshan", "mymrtlng", "nagm", "newa", "nkoo", "olck", "onao", "orya",
            "osma", "outlined", "rohg", "saur", "segment", "shrd", "sind", "sinh",
            "sora", "sund", "sunu", "takr", "talu", "tamldec", "telu", "thai",
            "tibt", "tirh", "tnsa", "tols", "vaii", "wara", "wcho"
    );
    // ---- Timezone Aliases ----
    private static final Map<String, String> TIMEZONE_ALIASES = new HashMap<>();
    // ---- Transform Extension tvalue Aliases ----
    private static final Map<String, Map<String, String>> TVALUE_ALIASES = new HashMap<>();
    // Unicode extension keys where "yes" → "true" (which means omit the value)
    private static final Set<String> UNICODE_EXT_BOOLEAN_KEYS = Set.of("kb", "kc", "kh", "kk", "kn");
    // ---- Unicode Extension Value Aliases ----
    private static final Map<String, Map<String, String>> UNICODE_EXT_VALUE_ALIASES = new HashMap<>();
    /**
     * Pattern for Unicode Locale Identifier type: (3*8alphanum) *("-" (3*8alphanum))
     */
    private static final Pattern UNICODE_TYPE_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9]{3,8}(-[a-zA-Z0-9]{3,8})*$");
    /**
     * Check if a collation value is valid/supported.
     */
    private static final Set<String> VALID_COLLATION_TYPES = Set.of(
            "big5han", "compat", "dict", "direct", "ducet", "emoji", "eor",
            "gb2312", "phonebk", "phonetic", "pinyin", "reformed", "searchjl",
            "stroke", "trad", "unihan", "zhuyin"
    );
    private static final Set<Integer> VALID_ROUNDING_INCREMENTS = Set.of(
            1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000, 2500, 5000
    );
    // ---- Variant Alias Data ----
    private static final Map<String, String> VARIANT_ALIASES = new HashMap<>();
    private static final Map<String, String> VARIANT_TO_LANGUAGE = new HashMap<>();
    /**
     * Lazy-initialized map of lowercased timezone ID → canonical casing.
     * Built from ZoneId.getAvailableZoneIds() plus supplementary valid IANA
     * timezone IDs that Java's ZoneId does not include.
     */
    private static volatile Map<String, String> TIMEZONE_LOOKUP_MAP;

    private enum DateTimeFormattableKind {
        NUMBER,
        INSTANT,
        PLAIN_DATE,
        PLAIN_DATE_TIME,
        PLAIN_MONTH_DAY,
        PLAIN_TIME,
        PLAIN_YEAR_MONTH,
        ZONED_DATE_TIME
    }

    private record DateTimeFormattable(DateTimeFormattableKind kind, double epochMillis, String calendarId) {
    }

    private record DateStyleOptionFields(
            String weekdayOption,
            String yearOption,
            String monthOption,
            String dayOption) {
    }

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

    static {
        // 3-letter to 2-letter language aliases
        LANGUAGE_ALIASES.put("aar", "aa");
        LANGUAGE_ALIASES.put("abk", "ab");
        LANGUAGE_ALIASES.put("afr", "af");
        LANGUAGE_ALIASES.put("aka", "ak");
        LANGUAGE_ALIASES.put("amh", "am");
        LANGUAGE_ALIASES.put("ara", "ar");
        LANGUAGE_ALIASES.put("arg", "an");
        LANGUAGE_ALIASES.put("asm", "as");
        LANGUAGE_ALIASES.put("ava", "av");
        LANGUAGE_ALIASES.put("ave", "ae");
        LANGUAGE_ALIASES.put("aym", "ay");
        LANGUAGE_ALIASES.put("aze", "az");
        LANGUAGE_ALIASES.put("bak", "ba");
        LANGUAGE_ALIASES.put("bam", "bm");
        LANGUAGE_ALIASES.put("bel", "be");
        LANGUAGE_ALIASES.put("ben", "bn");
        LANGUAGE_ALIASES.put("bis", "bi");
        LANGUAGE_ALIASES.put("bod", "bo");
        LANGUAGE_ALIASES.put("bos", "bs");
        LANGUAGE_ALIASES.put("bre", "br");
        LANGUAGE_ALIASES.put("bul", "bg");
        LANGUAGE_ALIASES.put("cat", "ca");
        LANGUAGE_ALIASES.put("ces", "cs");
        LANGUAGE_ALIASES.put("cha", "ch");
        LANGUAGE_ALIASES.put("che", "ce");
        LANGUAGE_ALIASES.put("chu", "cu");
        LANGUAGE_ALIASES.put("chv", "cv");
        LANGUAGE_ALIASES.put("cor", "kw");
        LANGUAGE_ALIASES.put("cos", "co");
        LANGUAGE_ALIASES.put("cre", "cr");
        LANGUAGE_ALIASES.put("cym", "cy");
        LANGUAGE_ALIASES.put("dan", "da");
        LANGUAGE_ALIASES.put("deu", "de");
        LANGUAGE_ALIASES.put("div", "dv");
        LANGUAGE_ALIASES.put("dzo", "dz");
        LANGUAGE_ALIASES.put("ell", "el");
        LANGUAGE_ALIASES.put("eng", "en");
        LANGUAGE_ALIASES.put("epo", "eo");
        LANGUAGE_ALIASES.put("est", "et");
        LANGUAGE_ALIASES.put("eus", "eu");
        LANGUAGE_ALIASES.put("ewe", "ee");
        LANGUAGE_ALIASES.put("fao", "fo");
        LANGUAGE_ALIASES.put("fas", "fa");
        LANGUAGE_ALIASES.put("fij", "fj");
        LANGUAGE_ALIASES.put("fin", "fi");
        LANGUAGE_ALIASES.put("fra", "fr");
        LANGUAGE_ALIASES.put("fry", "fy");
        LANGUAGE_ALIASES.put("ful", "ff");
        LANGUAGE_ALIASES.put("gla", "gd");
        LANGUAGE_ALIASES.put("gle", "ga");
        LANGUAGE_ALIASES.put("glg", "gl");
        LANGUAGE_ALIASES.put("glv", "gv");
        LANGUAGE_ALIASES.put("grn", "gn");
        LANGUAGE_ALIASES.put("guj", "gu");
        LANGUAGE_ALIASES.put("hat", "ht");
        LANGUAGE_ALIASES.put("hau", "ha");
        LANGUAGE_ALIASES.put("hbs", "sr");
        LANGUAGE_ALIASES.put("heb", "he");
        LANGUAGE_ALIASES.put("her", "hz");
        LANGUAGE_ALIASES.put("hin", "hi");
        LANGUAGE_ALIASES.put("hmo", "ho");
        LANGUAGE_ALIASES.put("hrv", "hr");
        LANGUAGE_ALIASES.put("hun", "hu");
        LANGUAGE_ALIASES.put("hye", "hy");
        LANGUAGE_ALIASES.put("ibo", "ig");
        LANGUAGE_ALIASES.put("ido", "io");
        LANGUAGE_ALIASES.put("iii", "ii");
        LANGUAGE_ALIASES.put("iku", "iu");
        LANGUAGE_ALIASES.put("ile", "ie");
        LANGUAGE_ALIASES.put("ina", "ia");
        LANGUAGE_ALIASES.put("ind", "id");
        LANGUAGE_ALIASES.put("ipk", "ik");
        LANGUAGE_ALIASES.put("isl", "is");
        LANGUAGE_ALIASES.put("ita", "it");
        LANGUAGE_ALIASES.put("jav", "jv");
        LANGUAGE_ALIASES.put("jpn", "ja");
        LANGUAGE_ALIASES.put("kal", "kl");
        LANGUAGE_ALIASES.put("kan", "kn");
        LANGUAGE_ALIASES.put("kas", "ks");
        LANGUAGE_ALIASES.put("kat", "ka");
        LANGUAGE_ALIASES.put("kau", "kr");
        LANGUAGE_ALIASES.put("kaz", "kk");
        LANGUAGE_ALIASES.put("khm", "km");
        LANGUAGE_ALIASES.put("kik", "ki");
        LANGUAGE_ALIASES.put("kin", "rw");
        LANGUAGE_ALIASES.put("kir", "ky");
        LANGUAGE_ALIASES.put("kom", "kv");
        LANGUAGE_ALIASES.put("kon", "kg");
        LANGUAGE_ALIASES.put("kor", "ko");
        LANGUAGE_ALIASES.put("kua", "kj");
        LANGUAGE_ALIASES.put("kur", "ku");
        LANGUAGE_ALIASES.put("lao", "lo");
        LANGUAGE_ALIASES.put("lat", "la");
        LANGUAGE_ALIASES.put("lav", "lv");
        LANGUAGE_ALIASES.put("lim", "li");
        LANGUAGE_ALIASES.put("lin", "ln");
        LANGUAGE_ALIASES.put("lit", "lt");
        LANGUAGE_ALIASES.put("ltz", "lb");
        LANGUAGE_ALIASES.put("lub", "lu");
        LANGUAGE_ALIASES.put("lug", "lg");
        LANGUAGE_ALIASES.put("mah", "mh");
        LANGUAGE_ALIASES.put("mal", "ml");
        LANGUAGE_ALIASES.put("mar", "mr");
        LANGUAGE_ALIASES.put("mkd", "mk");
        LANGUAGE_ALIASES.put("mlg", "mg");
        LANGUAGE_ALIASES.put("mlt", "mt");
        LANGUAGE_ALIASES.put("mon", "mn");
        LANGUAGE_ALIASES.put("mri", "mi");
        LANGUAGE_ALIASES.put("msa", "ms");
        LANGUAGE_ALIASES.put("mya", "my");
        LANGUAGE_ALIASES.put("nau", "na");
        LANGUAGE_ALIASES.put("nav", "nv");
        LANGUAGE_ALIASES.put("nbl", "nr");
        LANGUAGE_ALIASES.put("nde", "nd");
        LANGUAGE_ALIASES.put("ndo", "ng");
        LANGUAGE_ALIASES.put("nep", "ne");
        LANGUAGE_ALIASES.put("nld", "nl");
        LANGUAGE_ALIASES.put("nno", "nn");
        LANGUAGE_ALIASES.put("nob", "nb");
        LANGUAGE_ALIASES.put("nor", "no");
        LANGUAGE_ALIASES.put("nya", "ny");
        LANGUAGE_ALIASES.put("oci", "oc");
        LANGUAGE_ALIASES.put("oji", "oj");
        LANGUAGE_ALIASES.put("ori", "or");
        LANGUAGE_ALIASES.put("orm", "om");
        LANGUAGE_ALIASES.put("oss", "os");
        LANGUAGE_ALIASES.put("pan", "pa");
        LANGUAGE_ALIASES.put("pli", "pi");
        LANGUAGE_ALIASES.put("pol", "pl");
        LANGUAGE_ALIASES.put("por", "pt");
        LANGUAGE_ALIASES.put("pus", "ps");
        LANGUAGE_ALIASES.put("que", "qu");
        LANGUAGE_ALIASES.put("roh", "rm");
        LANGUAGE_ALIASES.put("ron", "ro");
        LANGUAGE_ALIASES.put("run", "rn");
        LANGUAGE_ALIASES.put("rus", "ru");
        LANGUAGE_ALIASES.put("sag", "sg");
        LANGUAGE_ALIASES.put("san", "sa");
        LANGUAGE_ALIASES.put("sin", "si");
        LANGUAGE_ALIASES.put("slk", "sk");
        LANGUAGE_ALIASES.put("slv", "sl");
        LANGUAGE_ALIASES.put("sme", "se");
        LANGUAGE_ALIASES.put("smo", "sm");
        LANGUAGE_ALIASES.put("sna", "sn");
        LANGUAGE_ALIASES.put("snd", "sd");
        LANGUAGE_ALIASES.put("som", "so");
        LANGUAGE_ALIASES.put("sot", "st");
        LANGUAGE_ALIASES.put("spa", "es");
        LANGUAGE_ALIASES.put("sqi", "sq");
        LANGUAGE_ALIASES.put("srd", "sc");
        LANGUAGE_ALIASES.put("srp", "sr");
        LANGUAGE_ALIASES.put("ssw", "ss");
        LANGUAGE_ALIASES.put("sun", "su");
        LANGUAGE_ALIASES.put("swa", "sw");
        LANGUAGE_ALIASES.put("swe", "sv");
        LANGUAGE_ALIASES.put("tah", "ty");
        LANGUAGE_ALIASES.put("tam", "ta");
        LANGUAGE_ALIASES.put("tat", "tt");
        LANGUAGE_ALIASES.put("tel", "te");
        LANGUAGE_ALIASES.put("tgk", "tg");
        LANGUAGE_ALIASES.put("tgl", "tl");
        LANGUAGE_ALIASES.put("tha", "th");
        LANGUAGE_ALIASES.put("tir", "ti");
        LANGUAGE_ALIASES.put("ton", "to");
        LANGUAGE_ALIASES.put("tsn", "tn");
        LANGUAGE_ALIASES.put("tso", "ts");
        LANGUAGE_ALIASES.put("tuk", "tk");
        LANGUAGE_ALIASES.put("tur", "tr");
        LANGUAGE_ALIASES.put("twi", "tw");
        LANGUAGE_ALIASES.put("uig", "ug");
        LANGUAGE_ALIASES.put("ukr", "uk");
        LANGUAGE_ALIASES.put("urd", "ur");
        LANGUAGE_ALIASES.put("uzb", "uz");
        LANGUAGE_ALIASES.put("ven", "ve");
        LANGUAGE_ALIASES.put("vie", "vi");
        LANGUAGE_ALIASES.put("vol", "vo");
        LANGUAGE_ALIASES.put("wln", "wa");
        LANGUAGE_ALIASES.put("wol", "wo");
        LANGUAGE_ALIASES.put("xho", "xh");
        LANGUAGE_ALIASES.put("yid", "yi");
        LANGUAGE_ALIASES.put("yor", "yo");
        LANGUAGE_ALIASES.put("zha", "za");
        LANGUAGE_ALIASES.put("zho", "zh");
        LANGUAGE_ALIASES.put("zul", "zu");

        // Deprecated 2-letter codes
        LANGUAGE_ALIASES.put("mo", "ro");
        LANGUAGE_ALIASES.put("jw", "jv");
        LANGUAGE_ALIASES.put("tl", "fil");

        // Complex language aliases (language maps to different language + script)
        LANGUAGE_ALIASES.put("cmn", "zh");
        LANGUAGE_ALIASES.put("sh", "sr");
        LANGUAGE_SCRIPT_ADDITIONS.put("sh", "Latn");
        LANGUAGE_ALIASES.put("cnr", "sr");
        LANGUAGE_REGION_ADDITIONS.put("cnr", "ME");

        // Sign language aliases
        LANGUAGE_ALIASES.put("sgn-gr", "gss");
        LANGUAGE_ALIASES.put("sgn-be-fr", "sfb");
        LANGUAGE_ALIASES.put("sgn-be-nl", "vgt");
        LANGUAGE_ALIASES.put("sgn-ch-de", "sgg");
    }

    static {
        REGION_ALIASES.put("DD", "DE");
        REGION_ALIASES.put("YD", "YE");
        REGION_ALIASES.put("AN", "CW");
        REGION_ALIASES.put("BU", "MM");
        REGION_ALIASES.put("CS", "RS");
        REGION_ALIASES.put("TP", "TL");
        REGION_ALIASES.put("YU", "RS");
        REGION_ALIASES.put("ZR", "CD");
        REGION_ALIASES.put("200", "CZ");
        REGION_ALIASES.put("530", "CW");
        REGION_ALIASES.put("532", "CW");
        REGION_ALIASES.put("536", "SA");
        REGION_ALIASES.put("582", "FM");
        REGION_ALIASES.put("736", "SD");
        REGION_ALIASES.put("554", "NZ");
        REGION_ALIASES.put("890", "RS");

        // SU (Soviet Union) and 810 → default RU, but hy→AM, ka→GE, etc.
        REGION_ALIASES.put("SU", "RU");
        REGION_ALIASES.put("810", "RU");
        Map<String, String> suByLang = new HashMap<>();
        suByLang.put("hy", "AM");
        suByLang.put("ka", "GE");
        suByLang.put("az", "AZ");
        suByLang.put("be", "BY");
        suByLang.put("et", "EE");
        suByLang.put("lv", "LV");
        suByLang.put("lt", "LT");
        suByLang.put("tk", "TM");
        suByLang.put("uk", "UA");
        suByLang.put("uz", "UZ");
        suByLang.put("kk", "KZ");
        suByLang.put("ky", "KG");
        suByLang.put("tg", "TJ");
        suByLang.put("mn", "MN");
        REGION_ALIASES_BY_LANG.put("SU", suByLang);
        REGION_ALIASES_BY_LANG.put("810", suByLang);

        // NT (Neutral Zone) → SA (for az)
        REGION_ALIASES.put("NT", "SA");
        Map<String, String> ntByLang = new HashMap<>();
        ntByLang.put("az", "SA");
        REGION_ALIASES_BY_LANG.put("NT", ntByLang);
    }

    static {
        REGION_BY_SCRIPT.put("Armn", "AM");
        REGION_BY_SCRIPT.put("Geor", "GE");
    }

    static {
        VARIANT_ALIASES.put("heploc", "alalc97");
        // When heploc is replaced by alalc97, hepburn should also be removed
        // (heploc was a sub-variant of hepburn)
        // hy-arevela → hy (remove variant, language stays hy)
        VARIANT_TO_LANGUAGE.put("arevela", "hy");
        // hy-arevmda → hyw (remove variant, change language to hyw)
        VARIANT_TO_LANGUAGE.put("arevmda", "hyw");

        // Grandfathered tag variant-to-language aliases (CLDR supplementalMetadata languageAlias)
        VARIANT_TO_LANGUAGE.put("lojban", "jbo");
        VARIANT_TO_LANGUAGE.put("gaulish", "xtg");
        VARIANT_TO_LANGUAGE.put("guoyu", "zh");
        VARIANT_TO_LANGUAGE.put("hakka", "hak");
        VARIANT_TO_LANGUAGE.put("xiang", "hsn");
    }

    static {
        // ca (calendar)
        Map<String, String> calendarAliases = new HashMap<>();
        calendarAliases.put("ethiopic-amete-alem", "ethioaa");
        calendarAliases.put("islamicc", "islamic-civil");
        UNICODE_EXT_VALUE_ALIASES.put("ca", calendarAliases);

        // ks (collation strength)
        Map<String, String> colStrengthAliases = new HashMap<>();
        colStrengthAliases.put("primary", "level1");
        colStrengthAliases.put("secondary", "level2");
        colStrengthAliases.put("tertiary", "level3");
        colStrengthAliases.put("quaternary", "level4");
        colStrengthAliases.put("identical", "level5");
        UNICODE_EXT_VALUE_ALIASES.put("ks", colStrengthAliases);

        // ms (measurement system)
        Map<String, String> msAliases = new HashMap<>();
        msAliases.put("imperial", "uksystem");
        UNICODE_EXT_VALUE_ALIASES.put("ms", msAliases);
    }

    static {
        SUBDIVISION_ALIASES.put("no23", "no50");
        SUBDIVISION_ALIASES.put("cn11", "cnbj");
        SUBDIVISION_ALIASES.put("cz10a", "cz110");
        SUBDIVISION_ALIASES.put("fra", "frges");
        SUBDIVISION_ALIASES.put("frg", "frges");
        SUBDIVISION_ALIASES.put("lud", "lucl");
    }

    static {
        TIMEZONE_ALIASES.put("cnckg", "cnsha");
        TIMEZONE_ALIASES.put("eire", "iedub");
        TIMEZONE_ALIASES.put("est", "papty");
        TIMEZONE_ALIASES.put("gmt0", "gmt");
        TIMEZONE_ALIASES.put("uct", "utc");
        TIMEZONE_ALIASES.put("zulu", "utc");
    }

    static {
        Map<String, String> m0Aliases = new HashMap<>();
        m0Aliases.put("names", "prprname");
        TVALUE_ALIASES.put("m0", m0Aliases);
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
                Locale.Builder builder = new Locale.Builder()
                        .setLanguage(fullLocale.getLanguage())
                        .setScript(script)
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
                Locale.Builder builder = new Locale.Builder()
                        .setLanguage(fullLocale.getLanguage())
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
                Locale.Builder builder = new Locale.Builder()
                        .setLanguage(fullLocale.getLanguage())
                        .setScript(script.isEmpty() ? fullLocale.getScript() : script)
                        .setRegion(region);
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
            Locale.Builder builder = new Locale.Builder()
                    .setLanguage(fullLocale.getLanguage())
                    .setScript(script.isEmpty() ? fullLocale.getScript() : script)
                    .setRegion(region.isEmpty() ? fullLocale.getCountry() : region);
            if (!variant.isEmpty()) {
                builder.setVariant(variant);
            }
            return builder.build();
        }

        return locale;
    }

    private static void appendRangePartsWithSource(JSContext context, JSArray target, JSArray sourceParts, String sourceName) {
        int length = (int) sourceParts.getLength();
        for (int index = 0; index < length; index++) {
            JSValue partValue = sourceParts.get(index);
            if (!(partValue instanceof JSObject partObject)) {
                continue;
            }
            JSValue typeValue = partObject.get(PropertyKey.fromString("type"));
            JSValue valueValue = partObject.get(PropertyKey.fromString("value"));
            if (!(typeValue instanceof JSString typeString) || !(valueValue instanceof JSString valueString)) {
                continue;
            }
            target.push(createPartObject(context, typeString.value(), valueString.value(), sourceName));
        }
    }

    /**
     * Apply CLDR canonicalization to a tag that has already been through Java's Locale processing.
     */
    private static String applyCLDRCanonicalization(String tag) {
        // Parse the tag into components
        List<String> parts = new ArrayList<>(Arrays.asList(tag.split("-")));
        if (parts.isEmpty()) {
            return tag;
        }

        // Find boundary indices for different subtag types
        String language = parts.get(0).toLowerCase(Locale.ROOT);
        int idx = 1;
        String script = "";
        String region = "";
        List<String> variants = new ArrayList<>();
        Map<Character, String> extensions = new LinkedHashMap<>();

        // Check for sign language prefix mappings first (sgn-XX → replacement)
        String signKey = language;
        if (idx < parts.size()) {
            signKey += "-" + parts.get(idx).toLowerCase(Locale.ROOT);
            if (idx + 1 < parts.size()) {
                String threePartKey = signKey + "-" + parts.get(idx + 1).toLowerCase(Locale.ROOT);
                if (LANGUAGE_ALIASES.containsKey(threePartKey)) {
                    return LANGUAGE_ALIASES.get(threePartKey);
                }
            }
            if (LANGUAGE_ALIASES.containsKey(signKey) && language.equals("sgn")) {
                return LANGUAGE_ALIASES.get(signKey);
            }
        }

        // Apply language alias
        String newLanguage = LANGUAGE_ALIASES.get(language);
        if (newLanguage != null) {
            language = newLanguage;
        }

        // Parse script (4 alpha letters, title case after Java processing)
        if (idx < parts.size() && parts.get(idx).length() == 4 && parts.get(idx).chars().allMatch(Character::isLetter)) {
            script = parts.get(idx);
            idx++;
        }

        // For complex language aliases: add script if not already present
        String scriptAddition = LANGUAGE_SCRIPT_ADDITIONS.get(parts.get(0).toLowerCase(Locale.ROOT));
        if (scriptAddition != null && script.isEmpty()) {
            script = scriptAddition;
        }

        // Parse region (2 alpha or 3 digits)
        if (idx < parts.size()) {
            String p = parts.get(idx);
            if ((p.length() == 2 && p.chars().allMatch(Character::isLetter)) ||
                    (p.length() == 3 && p.chars().allMatch(Character::isDigit))) {
                region = p.toUpperCase(Locale.ROOT);
                idx++;
            }
        }

        // For complex language aliases: add region if not already present
        String regionAddition = LANGUAGE_REGION_ADDITIONS.get(parts.get(0).toLowerCase(Locale.ROOT));
        if (regionAddition != null && region.isEmpty()) {
            region = regionAddition;
        }

        // Apply region alias
        if (!region.isEmpty()) {
            String regionUpper = region.toUpperCase(Locale.ROOT);
            if (REGION_ALIASES.containsKey(regionUpper)) {
                // Check language/script-specific replacements
                Map<String, String> byLang = REGION_ALIASES_BY_LANG.get(regionUpper);
                if (byLang != null && byLang.containsKey(language)) {
                    region = byLang.get(language);
                } else if (byLang != null && !script.isEmpty() && REGION_BY_SCRIPT.containsKey(script)) {
                    region = REGION_BY_SCRIPT.get(script);
                } else {
                    region = REGION_ALIASES.get(regionUpper);
                }
            }
        }

        // Parse variants
        while (idx < parts.size()) {
            String p = parts.get(idx);
            if (p.length() == 1) {
                break; // Extension singleton
            }
            boolean isVariant = (p.length() >= 5 && p.length() <= 8) ||
                    (p.length() == 4 && Character.isDigit(p.charAt(0)));
            if (!isVariant) {
                break;
            }
            String lowerVariant = p.toLowerCase(Locale.ROOT);
            // Check variant-to-language mappings (e.g., hy-arevela → hy, hy-arevmda → hyw)
            if (VARIANT_TO_LANGUAGE.containsKey(lowerVariant)) {
                language = VARIANT_TO_LANGUAGE.get(lowerVariant);
                // Don't add this variant
            } else {
                // Apply variant alias
                String aliased = VARIANT_ALIASES.getOrDefault(lowerVariant, lowerVariant);
                variants.add(aliased);
            }
            idx++;
        }

        // When heploc → alalc97, remove the companion hepburn variant
        if (variants.contains("alalc97")) {
            variants.remove("hepburn");
        }

        // Sort variants alphabetically per CLDR canonicalization
        variants.sort(String.CASE_INSENSITIVE_ORDER);

        // Parse extensions
        while (idx < parts.size()) {
            String p = parts.get(idx);
            if (p.length() == 1 && !p.equalsIgnoreCase("x")) {
                char singleton = p.toLowerCase(Locale.ROOT).charAt(0);
                StringBuilder extValue = new StringBuilder();
                idx++;
                while (idx < parts.size() && parts.get(idx).length() > 1) {
                    if (!extValue.isEmpty()) {
                        extValue.append("-");
                    }
                    extValue.append(parts.get(idx).toLowerCase(Locale.ROOT));
                    idx++;
                }
                extensions.put(singleton, extValue.toString());
            } else if (p.equalsIgnoreCase("x")) {
                // Private use
                StringBuilder privateUse = new StringBuilder("x");
                idx++;
                while (idx < parts.size()) {
                    privateUse.append("-").append(parts.get(idx));
                    idx++;
                }
                extensions.put('x', privateUse.toString());
            } else {
                idx++;
            }
        }

        // Canonicalize unicode extension (u)
        if (extensions.containsKey('u')) {
            extensions.put('u', canonicalizeUnicodeExtension(extensions.get('u')));
        }

        // Canonicalize transform extension (t)
        if (extensions.containsKey('t')) {
            extensions.put('t', canonicalizeTransformExtension(extensions.get('t')));
        }

        // Reconstruct the tag
        StringBuilder result = new StringBuilder(language);
        if (!script.isEmpty()) {
            // Title case for script
            result.append("-").append(Character.toUpperCase(script.charAt(0)))
                    .append(script.substring(1).toLowerCase(Locale.ROOT));
        }
        if (!region.isEmpty()) {
            result.append("-").append(region.toUpperCase(Locale.ROOT));
        }
        for (String variant : variants) {
            result.append("-").append(variant);
        }
        // Extensions in singleton order
        List<Character> sortedSingletons = new ArrayList<>(extensions.keySet());
        sortedSingletons.sort(Character::compare);
        for (char singleton : sortedSingletons) {
            String extValue = extensions.get(singleton);
            if (singleton == 'x') {
                result.append("-").append(extValue);
            } else {
                result.append("-").append(singleton);
                if (!extValue.isEmpty()) {
                    result.append("-").append(extValue);
                }
            }
        }

        return result.toString();
    }

    /**
     * Build resolved locale tag according to ECMA-402 ResolveLocale rules.
     * Unicode extension keys are included only if:
     * - The extension value matched the resolved value AND
     * - No option explicitly overrode it
     */
    private static Locale buildResolvedLocale(Locale locale, Map<String, String> unicodeExtensions,
                                              String calendarFromOption, String resolvedCalendar,
                                              String hourCycleFromOption, String resolvedHourCycle,
                                              Boolean hour12,
                                              String numberingSystemFromOption, String resolvedNumberingSystem) {
        // Start from base locale without unicode extensions
        Locale baseLocale = stripUnicodeExtensions(locale);
        String baseTag = baseLocale.toLanguageTag();

        StringBuilder extensionBuilder = new StringBuilder();

        // Calendar extension: include if extension value == resolved AND no option override
        String extensionCalendar = unicodeExtensions.get("ca");
        if (extensionCalendar != null && calendarFromOption == null) {
            String canonicalizedExtCal = canonicalizeCalendar(extensionCalendar);
            if (canonicalizedExtCal != null && canonicalizedExtCal.equals(resolvedCalendar)) {
                if (extensionBuilder.length() > 0) {
                    extensionBuilder.append("-");
                }
                extensionBuilder.append("ca-").append(resolvedCalendar);
            }
        } else if (extensionCalendar != null && calendarFromOption != null) {
            // Option overrides extension: include only if they match
            String canonicalizedExtCal = canonicalizeCalendar(extensionCalendar);
            if (canonicalizedExtCal != null && canonicalizedExtCal.equals(resolvedCalendar)) {
                if (extensionBuilder.length() > 0) {
                    extensionBuilder.append("-");
                }
                extensionBuilder.append("ca-").append(resolvedCalendar);
            }
        }

        // Hour cycle extension: include if extension value == resolved AND no option/hour12 override
        String extensionHourCycle = unicodeExtensions.get("hc");
        if (extensionHourCycle != null && hourCycleFromOption == null && hour12 == null) {
            if (extensionHourCycle.equals(resolvedHourCycle)) {
                if (extensionBuilder.length() > 0) {
                    extensionBuilder.append("-");
                }
                extensionBuilder.append("hc-").append(resolvedHourCycle);
            }
        } else if (extensionHourCycle != null && hourCycleFromOption != null && hour12 == null) {
            // hourCycle option overrides: include only if they match
            if (extensionHourCycle.equals(resolvedHourCycle)) {
                if (extensionBuilder.length() > 0) {
                    extensionBuilder.append("-");
                }
                extensionBuilder.append("hc-").append(resolvedHourCycle);
            }
        }
        // If hour12 was set, don't include hc extension (hour12 clears hc)

        // Numbering system extension: include if extension value == resolved AND no option override
        String extensionNu = unicodeExtensions.get("nu");
        if (extensionNu != null && numberingSystemFromOption == null) {
            if (extensionNu.toLowerCase(Locale.ROOT).equals(resolvedNumberingSystem)) {
                if (extensionBuilder.length() > 0) {
                    extensionBuilder.append("-");
                }
                extensionBuilder.append("nu-").append(resolvedNumberingSystem);
            }
        } else if (extensionNu != null && numberingSystemFromOption != null) {
            if (extensionNu.toLowerCase(Locale.ROOT).equals(resolvedNumberingSystem)) {
                if (extensionBuilder.length() > 0) {
                    extensionBuilder.append("-");
                }
                extensionBuilder.append("nu-").append(resolvedNumberingSystem);
            }
        }

        if (extensionBuilder.length() > 0) {
            return Locale.forLanguageTag(baseTag + "-u-" + extensionBuilder);
        }
        return baseLocale;
    }

    /**
     * Build resolved locale tag including applied unicode extensions.
     * Extension keys are only included when the resolved value matches the
     * original unicode extension value. Sorted alphabetically per BCP 47.
     */
    private static String buildResolvedLocaleTag(Locale baseLocale, String collation, boolean numeric,
                                                 String caseFirst,
                                                 Map<String, String> unicodeExtensions) {
        String baseTag = stripUnicodeExtensions(baseLocale).toLanguageTag();
        TreeMap<String, String> extensions = new TreeMap<>();

        // Include co extension only if resolved collation matches original extension
        if (unicodeExtensions.containsKey("co")) {
            String extCollation = unicodeExtensions.get("co");
            if (collation != null && collation.equals(extCollation)) {
                extensions.put("co", collation);
            }
        }

        // Include kf extension only if resolved caseFirst matches original extension
        if (unicodeExtensions.containsKey("kf")) {
            String extCaseFirst = unicodeExtensions.get("kf");
            if (caseFirst != null && caseFirst.equals(extCaseFirst)) {
                extensions.put("kf", caseFirst);
            }
        }

        // Include kn extension only if resolved numeric matches original extension
        if (unicodeExtensions.containsKey("kn")) {
            String extKnValue = unicodeExtensions.get("kn");
            boolean extNumeric = !"false".equals(extKnValue);
            if (numeric == extNumeric && numeric) {
                extensions.put("kn", null); // canonical form: kn without value
            }
        }

        if (extensions.isEmpty()) {
            return baseTag;
        }
        StringBuilder builder = new StringBuilder(baseTag);
        builder.append("-u");
        for (Map.Entry<String, String> entry : extensions.entrySet()) {
            builder.append("-").append(entry.getKey());
            if (entry.getValue() != null) {
                builder.append("-").append(entry.getValue());
            }
        }
        return builder.toString();
    }

    /**
     * Canonicalize a calendar identifier per ECMA-402:
     * - ASCII-only lowercase (not locale-sensitive)
     * - Legacy alias mapping (e.g., "islamicc" → "islamic-civil")
     * - Deprecated calendar fallback ("islamic", "islamic-rgsa" → "islamic-civil")
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
            JSValue lengthValue = localeObject.get(PropertyKey.fromString("length"));
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
                    context.throwTypeError(
                            JSTypeConversions.toString(context, element).value()
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

    /**
     * Canonicalize a locale tag string per ECMA-402 CanonicalizeUnicodeLocaleId.
     * Applies CLDR language/region/variant aliases, sorts variants, and canonicalizes extensions.
     */
    static String canonicalizeLocaleTag(String localeTag) {
        // First, let Java parse and normalize basic structure (case normalization, grandfathered tag mapping)
        Locale locale = parseLocaleTag(localeTag);
        String javaTag = locale.toLanguageTag();

        // Java omits "und" prefix for undetermined language with only extensions/private-use,
        // producing "x-private" instead of "und-x-private". Fix this.
        if (locale.getLanguage().isEmpty() && javaTag.startsWith("x-")) {
            javaTag = "und-" + javaTag;
        }

        // Strip private-use suffixes that Java adds for grandfathered tags
        // e.g. "xtg-x-cel-gaulish" → "xtg", "en-GB-x-oed" → needs special handling
        javaTag = stripJavaGrandfatheredPrivateUse(localeTag, javaTag);

        // Now parse and canonicalize the tag further
        return applyCLDRCanonicalization(javaTag);
    }

    /**
     * Canonicalize a Transform extension value string (the part after "t-").
     * Sort tfield keys, sort tlang variants, canonicalize tlang, replace deprecated tvalues.
     */
    private static String canonicalizeTransformExtension(String extValue) {
        if (extValue == null || extValue.isEmpty()) {
            return extValue;
        }
        String[] parts = extValue.split("-");
        int idx = 0;

        // Parse tlang (optional) - starts with a language subtag
        String tlang = null;
        StringBuilder tlangBuilder = new StringBuilder();
        if (idx < parts.length && parts[idx].length() >= 2 && parts[idx].chars().allMatch(Character::isLetter)) {
            // This is a language subtag
            tlangBuilder.append(parts[idx]);
            idx++;
            // Parse optional script, region, variants
            List<String> tlangVariants = new ArrayList<>();
            String tlangScript = "";
            String tlangRegion = "";

            // Script
            if (idx < parts.length && parts[idx].length() == 4 && parts[idx].chars().allMatch(Character::isLetter)) {
                tlangScript = parts[idx];
                idx++;
            }
            // Region
            if (idx < parts.length) {
                String p = parts[idx];
                if ((p.length() == 2 && p.chars().allMatch(Character::isLetter)) ||
                        (p.length() == 3 && p.chars().allMatch(Character::isDigit))) {
                    tlangRegion = p;
                    idx++;
                }
            }
            // Variants
            while (idx < parts.length && parts[idx].length() > 1 &&
                    !isTransformFieldKey(parts[idx])) {
                boolean isVariant = (parts[idx].length() >= 5 && parts[idx].length() <= 8) ||
                        (parts[idx].length() == 4 && Character.isDigit(parts[idx].charAt(0)));
                if (!isVariant) {
                    break;
                }
                tlangVariants.add(parts[idx].toLowerCase(Locale.ROOT));
                idx++;
            }

            // Sort tlang variants
            tlangVariants.sort(String.CASE_INSENSITIVE_ORDER);

            // Canonicalize tlang language (apply aliases like iw→he)
            String tlangLang = tlangBuilder.toString().toLowerCase(Locale.ROOT);
            // Java's legacy code mapping
            if ("iw".equals(tlangLang)) {
                tlangLang = "he";
            } else if ("in".equals(tlangLang)) {
                tlangLang = "id";
            } else if ("ji".equals(tlangLang)) {
                tlangLang = "yi";
            }
            // CLDR language aliases
            String aliased = LANGUAGE_ALIASES.get(tlangLang);
            if (aliased != null) {
                tlangLang = aliased;
            }

            StringBuilder tlangResult = new StringBuilder(tlangLang);
            if (!tlangScript.isEmpty()) {
                tlangResult.append("-").append(tlangScript.toLowerCase(Locale.ROOT));
            }
            if (!tlangRegion.isEmpty()) {
                tlangResult.append("-").append(tlangRegion.toLowerCase(Locale.ROOT));
            }
            for (String v : tlangVariants) {
                tlangResult.append("-").append(v);
            }
            tlang = tlangResult.toString();
        }

        // Parse tfields (key-value pairs where key is 2 chars starting with letter/digit)
        Map<String, List<String>> tfields = new TreeMap<>(); // TreeMap for sorted order
        while (idx < parts.length) {
            if (isTransformFieldKey(parts[idx])) {
                String key = parts[idx].toLowerCase(Locale.ROOT);
                idx++;
                List<String> values = new ArrayList<>();
                while (idx < parts.length && !isTransformFieldKey(parts[idx])) {
                    values.add(parts[idx].toLowerCase(Locale.ROOT));
                    idx++;
                }
                // Apply tvalue aliases
                Map<String, String> aliases = TVALUE_ALIASES.get(key);
                if (aliases != null) {
                    String joinedValue = String.join("-", values);
                    if (aliases.containsKey(joinedValue)) {
                        String replacement = aliases.get(joinedValue);
                        values = new ArrayList<>(Arrays.asList(replacement.split("-")));
                    }
                }
                tfields.put(key, values);
            } else {
                idx++;
            }
        }

        // Reconstruct
        StringBuilder result = new StringBuilder();
        if (tlang != null) {
            result.append(tlang);
        }
        for (Map.Entry<String, List<String>> entry : tfields.entrySet()) {
            if (!result.isEmpty()) {
                result.append("-");
            }
            result.append(entry.getKey());
            if (!entry.getValue().isEmpty()) {
                result.append("-").append(String.join("-", entry.getValue()));
            }
        }
        return result.toString();
    }

    /**
     * Canonicalize a Unicode extension value string (the part after "u-").
     * Sort key-value pairs by key, apply value aliases, handle boolean keys.
     */
    private static String canonicalizeUnicodeExtension(String extValue) {
        if (extValue == null || extValue.isEmpty()) {
            return extValue;
        }
        String[] parts = extValue.split("-");
        List<String> attributes = new ArrayList<>();
        Map<String, List<String>> keywords = new LinkedHashMap<>();
        String currentKey = null;
        List<String> currentValues = new ArrayList<>();

        for (String part : parts) {
            if (part.length() == 2 && Character.isLetter(part.charAt(0))) {
                // New keyword key
                if (currentKey != null) {
                    keywords.put(currentKey, new ArrayList<>(currentValues));
                    currentValues.clear();
                }
                currentKey = part;
            } else if (currentKey != null) {
                currentValues.add(part);
            } else {
                // Attribute (before any key)
                attributes.add(part);
            }
        }
        if (currentKey != null) {
            keywords.put(currentKey, new ArrayList<>(currentValues));
        }

        // Sort keywords by key
        Map<String, List<String>> sortedKeywords = new TreeMap<>(keywords);

        // Apply value aliases and boolean canonicalization
        for (Map.Entry<String, List<String>> entry : sortedKeywords.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            String joinedValue = String.join("-", values);

            // Apply value aliases
            Map<String, String> valueAliases = UNICODE_EXT_VALUE_ALIASES.get(key);
            if (valueAliases != null && valueAliases.containsKey(joinedValue)) {
                String replacement = valueAliases.get(joinedValue);
                entry.setValue(replacement.isEmpty() ? List.of() : List.of(replacement.split("-")));
            }

            // Handle "rg" and "sd" subdivision aliases
            if (("rg".equals(key) || "sd".equals(key)) && !values.isEmpty()) {
                String sdValue = String.join("-", values);
                if (SUBDIVISION_ALIASES.containsKey(sdValue)) {
                    entry.setValue(List.of(SUBDIVISION_ALIASES.get(sdValue)));
                }
            }

            // Handle "tz" timezone aliases
            if ("tz".equals(key) && !values.isEmpty()) {
                String tzValue = String.join("-", values);
                if (TIMEZONE_ALIASES.containsKey(tzValue)) {
                    entry.setValue(List.of(TIMEZONE_ALIASES.get(tzValue)));
                }
            }

            // Handle boolean keys where "yes" → "true" → omit value
            if (UNICODE_EXT_BOOLEAN_KEYS.contains(key)) {
                String val = String.join("-", entry.getValue());
                if ("yes".equals(val) || "true".equals(val)) {
                    entry.setValue(List.of()); // Empty value (meaning "true", the default)
                }
            }
        }

        // Reconstruct
        StringBuilder result = new StringBuilder();
        for (String attr : attributes) {
            if (!result.isEmpty()) {
                result.append("-");
            }
            result.append(attr);
        }
        for (Map.Entry<String, List<String>> entry : sortedKeywords.entrySet()) {
            if (!result.isEmpty()) {
                result.append("-");
            }
            result.append(entry.getKey());
            List<String> values = entry.getValue();
            if (!values.isEmpty()) {
                result.append("-").append(String.join("-", values));
            }
        }
        return result.toString();
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
            JSValue optionsValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

            // Parse unicode extension keys from locale tag (e.g. en-u-kn-true-co-phonebk)
            Map<String, String> unicodeExtensions = parseUnicodeExtensions(locale.toLanguageTag());

            // Read options in ECMA-402 spec order

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

            // 3. collation: resolve from option and/or extension per ECMA-402
            String optionCollation = getOptionStringChecked(context, optionsValue, "collation");
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

            // 6. sensitivity - default depends on usage per ECMA-402 Table 4
            String sensitivity = getOptionStringChecked(context, optionsValue, "sensitivity");
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
            Locale resolvedLocale = stripUnicodeExtensions(locale);
            String resolvedLocaleTag = buildResolvedLocaleTag(locale, collation, numeric, caseFirst,
                    unicodeExtensions);

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
     * CreateDataProperty(O, P, V) — creates an own data property directly,
     * bypassing [[Set]] so prototype chain accessors are not triggered.
     * Per ECMA-402 resolvedOptions() steps which use CreateDataPropertyOrThrow.
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
     * @param required "date", "time", or "any" — which component types to check for needDefaults
     * @param defaults "date", "time", or "all" — which defaults to add when needDefaults is true
     */
    public static JSValue createDateTimeFormat(JSContext context, JSObject prototype, JSValue[] args, String required, String defaults) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            JSValue rawOptions = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
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
            if (hourCycle != null && !"h11".equals(hourCycle) && !"h12".equals(hourCycle)
                    && !"h23".equals(hourCycle) && !"h24".equals(hourCycle)) {
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
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String eraOption = readValidatedOption(context, optionsObject, "era", "long", "short", "narrow");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String yearOption = readValidatedOption(context, optionsObject, "year", "numeric", "2-digit");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String monthOption = readValidatedOption(context, optionsObject, "month",
                    "numeric", "2-digit", "long", "short", "narrow");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String dayOption = readValidatedOption(context, optionsObject, "day", "numeric", "2-digit");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String dayPeriodOption = readValidatedOption(context, optionsObject, "dayPeriod", "long", "short", "narrow");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String hourOption = readValidatedOption(context, optionsObject, "hour", "numeric", "2-digit");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String minuteOption = readValidatedOption(context, optionsObject, "minute", "numeric", "2-digit");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
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
            String timeZoneNameOption = readValidatedOption(context, optionsObject, "timeZoneName",
                    "long", "short", "longOffset", "shortOffset", "longGeneric", "shortGeneric");
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
            String dateStyleStr = readValidatedOption(context, optionsObject, "dateStyle",
                    "full", "long", "medium", "short");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String timeStyleStr = readValidatedOption(context, optionsObject, "timeStyle",
                    "full", "long", "medium", "short");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            FormatStyle dateStyle = parseFormatStyle(dateStyleStr);
            FormatStyle timeStyle = parseFormatStyle(timeStyleStr);

            // Check dateStyle/timeStyle conflict with explicit components
            if (dateStyle != null || timeStyle != null) {
                if (weekdayOption != null || eraOption != null || yearOption != null
                        || monthOption != null || dayOption != null || dayPeriodOption != null
                        || hourOption != null || minuteOption != null || secondOption != null
                        || fractionalSecondDigits != null || timeZoneNameOption != null) {
                    return context.throwTypeError("dateStyle and timeStyle may not be used with other DateTimeFormat options");
                }
            }

            // Apply defaults per ECMA-402 ToDateTimeOptions(options, required, defaults):
            // 'required' determines which component types to check for needDefaults.
            // 'defaults' determines which defaults to add when needDefaults is true.
            boolean hasDefaultDateComponents = false;
            boolean hasDefaultTimeComponents = false;
            if (dateStyle == null && timeStyle == null) {
                boolean hasDateComponent = weekdayOption != null
                        || yearOption != null || monthOption != null || dayOption != null;
                boolean hasTimeComponent = dayPeriodOption != null || hourOption != null
                        || minuteOption != null || secondOption != null
                        || fractionalSecondDigits != null;
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
            Map<String, String> unicodeExtensions = parseUnicodeExtensions(locale.toLanguageTag());
            String calendarFromOption = calendar;
            // If option value is not a supported calendar, treat as if not specified
            if (calendarFromOption != null && !isSupportedCalendar(calendarFromOption)) {
                calendarFromOption = null;
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
            String numberingSystemFromOption = numberingSystem;
            // If option value is not supported, treat as if not specified
            if (numberingSystemFromOption != null && !isSupportedDateTimeNumberingSystem(numberingSystemFromOption)) {
                numberingSystemFromOption = null;
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

            String hourCycleFromOption = hourCycle;
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
            Locale resolvedLocale = buildResolvedLocale(locale, unicodeExtensions,
                    calendarFromOption, calendar,
                    hourCycleFromOption, hourCycle, hour12,
                    numberingSystemFromOption, numberingSystem);

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

            JSIntlDateTimeFormat dateTimeFormat = new JSIntlDateTimeFormat(context,
                    resolvedLocale, dateStyle, timeStyle, calendar, numberingSystem, timeZone,
                    hourCycle, hourCycleForInstant, weekdayOption, eraOption, yearOption, monthOption, dayOption,
                    dayPeriodOption, hourOption, minuteOption, secondOption, fractionalSecondDigits,
                    timeZoneNameOption, hasDefaultDateComponents, hasDefaultTimeComponents);
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
     * Intl.DisplayNames constructor factory.
     * Per ECMA-402 §12.1.1: OrdinaryCreateFromConstructor (step 2) runs before
     * locale/options processing. Options are read in order:
     * localeMatcher → style → type → fallback → languageDisplay
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
        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        if (optionsArg instanceof JSUndefined) {
            return context.throwTypeError("options must be an object");
        }
        if (!(optionsArg instanceof JSObject options)) {
            return context.throwTypeError("Cannot convert " + JSTypeConversions.toString(context, optionsArg).value() + " to object");
        }

        // localeMatcher
        String localeMatcher = getOptionStringChecked(context, options, "localeMatcher");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (localeMatcher != null) {
            if (!"lookup".equals(localeMatcher) && !"best fit".equals(localeMatcher)) {
                return context.throwRangeError("localeMatcher must be \"lookup\" or \"best fit\"");
            }
        }

        // style (default: "long")
        String style = getOptionStringChecked(context, options, "style");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (style == null) {
            style = "long";
        } else if (!"narrow".equals(style) && !"short".equals(style) && !"long".equals(style)) {
            return context.throwRangeError("style must be \"narrow\", \"short\", or \"long\"");
        }

        // type (required — TypeError if undefined)
        String type = getOptionStringChecked(context, options, "type");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (type == null) {
            return context.throwTypeError("type is required");
        }
        if (!"language".equals(type) && !"region".equals(type)
                && !"script".equals(type) && !"currency".equals(type)
                && !"calendar".equals(type) && !"dateTimeField".equals(type)) {
            return context.throwRangeError("type must be \"language\", \"region\", \"script\", \"currency\", \"calendar\", or \"dateTimeField\"");
        }

        // fallback (default: "code")
        String fallback = getOptionStringChecked(context, options, "fallback");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (fallback == null) {
            fallback = "code";
        } else if (!"code".equals(fallback) && !"none".equals(fallback)) {
            return context.throwRangeError("fallback must be \"code\" or \"none\"");
        }

        // languageDisplay (default: "dialect")
        String languageDisplay = getOptionStringChecked(context, options, "languageDisplay");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (languageDisplay == null) {
            languageDisplay = "dialect";
        } else if (!"dialect".equals(languageDisplay) && !"standard".equals(languageDisplay)) {
            return context.throwRangeError("languageDisplay must be \"dialect\" or \"standard\"");
        }

        // Create instance and set prototype
        JSIntlDisplayNames displayNames = new JSIntlDisplayNames(context, locale, style, type, fallback, languageDisplay);
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
        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        JSObject options = null;
        if (optionsArg instanceof JSObject opt) {
            options = opt;
        } else if (!(optionsArg instanceof JSUndefined)) {
            return context.throwTypeError("Options is not an object");
        }

        // Read localeMatcher (not in resolvedOptions, but read from options)
        if (options != null) {
            String localeMatcher = getOptionStringChecked(context, options, "localeMatcher");
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
        String numberingSystemOption = options != null ? getOptionStringChecked(context, options, "numberingSystem") : null;
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (numberingSystemOption != null && !UNICODE_TYPE_PATTERN.matcher(numberingSystemOption).matches()) {
            return context.throwRangeError("Invalid numberingSystem: " + numberingSystemOption);
        }

        // Parse unicode extension "nu" from locale
        Map<String, String> unicodeExtensions = parseUnicodeExtensions(locale.toLanguageTag());
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
        Locale strippedLocale = stripUnicodeExtensions(locale);
        if (useExtensionInLocale) {
            locale = new Locale.Builder().setLocale(strippedLocale)
                    .setUnicodeLocaleKeyword("nu", numberingSystem).build();
        } else {
            locale = strippedLocale;
        }

        // Read style (default "short")
        String style = options != null ? getOptionStringChecked(context, options, "style") : null;
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (style == null) {
            style = "short";
        } else if (!"long".equals(style) && !"short".equals(style) && !"narrow".equals(style) && !"digital".equals(style)) {
            return context.throwRangeError("Value " + style + " out of range for Intl.DurationFormat options property style");
        }

        // Process unit options
        String prevStyle = null;
        String[] unitStyles = new String[10];
        String[] unitDisplays = new String[10];

        for (int i = 0; i < JSIntlDurationFormat.UNIT_NAMES.length; i++) {
            String unitName = JSIntlDurationFormat.UNIT_NAMES[i];

            // Read unit style from options
            String unitStyle = options != null ? getOptionStringChecked(context, options, unitName) : null;
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // Track whether the unit style was explicitly provided
            boolean unitStyleExplicit = (unitStyle != null);

            // Read unit display from options
            String display = options != null ? getOptionStringChecked(context, options, unitName + "Display") : null;
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // Get valid styles for this unit
            String[] validStyles = JSIntlDurationFormat.getValidStylesForUnit(i);

            // Validate unit style
            if (unitStyle != null) {
                boolean valid = false;
                for (String vs : validStyles) {
                    if (vs.equals(unitStyle)) {
                        valid = true;
                        break;
                    }
                }
                if (!valid) {
                    return context.throwRangeError("Value " + unitStyle + " out of range for Intl.DurationFormat options property " + unitName);
                }
            }

            // Apply defaults if unit style not specified
            if (unitStyle == null) {
                if ("digital".equals(style)) {
                    unitStyle = JSIntlDurationFormat.getDigitalDefault(i);
                } else if (("numeric".equals(prevStyle) || "2-digit".equals(prevStyle))) {
                    unitStyle = "numeric";
                } else {
                    unitStyle = style;
                    // For date units with digital style base, ensure the style is valid
                    if ("digital".equals(unitStyle)) {
                        unitStyle = "short"; // fallback for date units
                    }
                }
            }

            // Style conflict check (spec step 6/9)
            if (("numeric".equals(prevStyle) || "2-digit".equals(prevStyle))) {
                if (!"numeric".equals(unitStyle) && !"2-digit".equals(unitStyle)) {
                    return context.throwRangeError("Cannot use style \"" + unitStyle + "\" for " + unitName +
                            " when previous unit uses \"" + prevStyle + "\" style");
                }
                // Upgrade minutes/seconds to 2-digit
                if ("minutes".equals(unitName) || "seconds".equals(unitName)) {
                    unitStyle = "2-digit";
                }
            }

            // Display default: "always" when unit style was explicitly set in options,
            // "auto" when unit style was defaulted from the base style or digital defaults
            if (display == null) {
                if (unitStyleExplicit && !"digital".equals(style)) {
                    display = "always";
                } else {
                    display = "auto";
                }
            } else if (!"auto".equals(display) && !"always".equals(display)) {
                return context.throwRangeError("Value " + display + " out of range for Intl.DurationFormat options property " + unitName + "Display");
            }

            unitStyles[i] = unitStyle;
            unitDisplays[i] = display;

            // Update prevStyle - only propagate for time/subsecond units
            prevStyle = unitStyle;
        }

        // In digital mode, when hoursDisplay was explicitly set in options,
        // set minutesDisplay and secondsDisplay to "always" if they weren't
        // explicitly set. This ensures minutes:seconds pair is always shown
        // in digital format even when hours is hidden (hoursDisplay="auto").
        if ("digital".equals(style) && options != null) {
            JSValue hoursDisplayVal = options.get(PropertyKey.fromString("hoursDisplay"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            boolean hoursDisplayExplicit = hoursDisplayVal != null && !(hoursDisplayVal instanceof JSUndefined);
            if (hoursDisplayExplicit) {
                JSValue minutesDisplayVal = options.get(PropertyKey.fromString("minutesDisplay"));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                boolean minutesDisplayExplicit = minutesDisplayVal != null && !(minutesDisplayVal instanceof JSUndefined);
                if (!minutesDisplayExplicit) {
                    unitDisplays[5] = "always"; // minutes
                }
                JSValue secondsDisplayVal = options.get(PropertyKey.fromString("secondsDisplay"));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                boolean secondsDisplayExplicit = secondsDisplayVal != null && !(secondsDisplayVal instanceof JSUndefined);
                if (!secondsDisplayExplicit) {
                    unitDisplays[6] = "always"; // seconds
                }
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
            JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
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
                return context.throwRangeError("Value " + localeMatcher + " out of range for Intl.ListFormat options property localeMatcher");
            }

            String type = normalizeOption(
                    getOptionString(context, optionsObj, "type"),
                    "conjunction",
                    "conjunction", "disjunction", "unit");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            String style = normalizeOption(
                    getOptionString(context, optionsObj, "style"),
                    "long",
                    "long", "short", "narrow");
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
            JSValue optionsValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
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
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String scriptOpt = getOptionString(context, optionsValue, "script");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String regionOpt = getOptionString(context, optionsValue, "region");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String variantsOpt = getOptionString(context, optionsValue, "variants");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // Validate language/script/region/variants options
            if (languageOpt != null && !isValidLanguageSubtag(languageOpt)) {
                return context.throwRangeError("invalid language: " + languageOpt);
            }
            if (scriptOpt != null && !isValidScriptSubtag(scriptOpt)) {
                return context.throwRangeError("invalid script: " + scriptOpt);
            }
            if (regionOpt != null && !isValidRegionSubtag(regionOpt)) {
                return context.throwRangeError("invalid region: " + regionOpt);
            }
            if (variantsOpt != null && !isValidVariantsSubtag(variantsOpt)) {
                return context.throwRangeError("invalid variants: " + variantsOpt);
            }

            String calendarOpt = getOptionString(context, optionsValue, "calendar");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String collationOpt = getOptionString(context, optionsValue, "collation");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String hourCycleOpt = getOptionString(context, optionsValue, "hourCycle");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
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
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // Read firstDayOfWeek option (as string, then apply WeekdayToString)
            String firstDayOfWeekOpt = null;
            if (optionsValue instanceof JSObject fdowOptsObj) {
                JSValue fdowRaw = fdowOptsObj.get(PropertyKey.fromString("firstDayOfWeek"));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                if (fdowRaw != null && !(fdowRaw instanceof JSUndefined)) {
                    String fdowStr = JSTypeConversions.toString(context, fdowRaw).value();
                    if (context.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                    // Apply WeekdayToString
                    firstDayOfWeekOpt = weekdayToString(fdowStr);
                    // Validate as unicode type (unless it will be bare "true")
                    if (!"true".equals(firstDayOfWeekOpt) &&
                            (firstDayOfWeekOpt.isEmpty() || !UNICODE_TYPE_PATTERN.matcher(firstDayOfWeekOpt).matches())) {
                        return context.throwRangeError("invalid firstDayOfWeek: " + fdowStr);
                    }
                }
            }

            // Validate extension values
            if (calendarOpt != null && (calendarOpt.isEmpty() || !UNICODE_TYPE_PATTERN.matcher(calendarOpt).matches())) {
                return context.throwRangeError("invalid calendar: " + calendarOpt);
            }
            if (collationOpt != null && (collationOpt.isEmpty() || !UNICODE_TYPE_PATTERN.matcher(collationOpt).matches())) {
                return context.throwRangeError("invalid collation: " + collationOpt);
            }
            // hourCycle must be exactly one of h11, h12, h23, h24
            if (hourCycleOpt != null) {
                if (!"h11".equals(hourCycleOpt) && !"h12".equals(hourCycleOpt) &&
                        !"h23".equals(hourCycleOpt) && !"h24".equals(hourCycleOpt)) {
                    return context.throwRangeError("invalid hourCycle: " + hourCycleOpt);
                }
            }
            if (caseFirstOpt != null && !"upper".equals(caseFirstOpt) && !"lower".equals(caseFirstOpt) && !"false".equals(caseFirstOpt)) {
                return context.throwRangeError("invalid caseFirst: " + caseFirstOpt);
            }
            if (numberingSystemOpt != null && (numberingSystemOpt.isEmpty() || !UNICODE_TYPE_PATTERN.matcher(numberingSystemOpt).matches())) {
                return context.throwRangeError("invalid numberingSystem: " + numberingSystemOpt);
            }

            // Canonicalize calendar values
            calendarOpt = canonicalizeCalendar(calendarOpt);

            Locale locale = parseLocaleTag(localeTag);

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
            Map<String, String> existingExtensions = parseUnicodeExtensions(locale.toLanguageTag());

            // Use TreeMap for sorted key order (Unicode BCP 47 requires alphabetical)
            TreeMap<String, String> extMap = new TreeMap<>(existingExtensions);

            // Apply option overrides
            if (calendarOpt != null) {
                extMap.put("ca", calendarOpt);
            }
            if (collationOpt != null) {
                extMap.put("co", collationOpt);
            }
            if (firstDayOfWeekOpt != null) {
                extMap.put("fw", firstDayOfWeekOpt);
            }
            if (hourCycleOpt != null) {
                extMap.put("hc", hourCycleOpt);
            }
            if (caseFirstOpt != null) {
                extMap.put("kf", caseFirstOpt);
            }
            if (numericOptSet) {
                extMap.put("kn", Boolean.toString(numericOpt));
            }
            if (numberingSystemOpt != null) {
                extMap.put("nu", numberingSystemOpt);
            }

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
            String finalCa = extMap.get("ca");
            String calendar = (finalCa != null && !finalCa.isEmpty() && !"true".equals(finalCa)) ? finalCa : null;

            String finalKf = extMap.get("kf");
            String caseFirst;
            if (finalKf != null) {
                if (finalKf.isEmpty() || "true".equals(finalKf)) {
                    caseFirst = ""; // bare kf → empty string for getter
                } else {
                    caseFirst = finalKf;
                }
            } else {
                caseFirst = null;
            }

            String finalCo = extMap.get("co");
            String collation = (finalCo != null && !finalCo.isEmpty() && !"true".equals(finalCo)) ? finalCo : null;

            String finalFw = extMap.get("fw");
            String firstDayOfWeek;
            if (finalFw != null) {
                if (finalFw.isEmpty() || "true".equals(finalFw)) {
                    firstDayOfWeek = ""; // bare fw
                } else {
                    firstDayOfWeek = finalFw;
                }
            } else {
                firstDayOfWeek = null;
            }

            String finalHc = extMap.get("hc");
            String hourCycle = (finalHc != null && !finalHc.isEmpty() && !"true".equals(finalHc)) ? finalHc : null;

            String finalNu = extMap.get("nu");
            String numberingSystem = (finalNu != null && !finalNu.isEmpty() && !"true".equals(finalNu)) ? finalNu : null;

            String finalKn = extMap.get("kn");
            boolean numericSet = finalKn != null;
            boolean numeric = "true".equals(finalKn) || (finalKn != null && finalKn.isEmpty());

            JSIntlLocale intlLocale = new JSIntlLocale(context, locale, tag,
                    calendar, caseFirst, collation, firstDayOfWeek, hourCycle, numberingSystem,
                    numeric, numericSet);
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
            JSValue optionsArgument = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
            JSObject optionsObject;
            if (optionsArgument instanceof JSUndefined || optionsArgument == null) {
                optionsObject = context.createJSObject();
                optionsObject.setPrototype(null);
            } else {
                optionsObject = JSTypeConversions.toObject(context, optionsArgument);
                if (optionsObject == null) {
                    return context.throwTypeError("Cannot convert " + JSTypeConversions.toString(context, optionsArgument).value() + " to object");
                }
            }

            // Option read order must match ECMA-402.
            String localeMatcher = getOptionStringChecked(context, optionsObject, "localeMatcher");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (localeMatcher != null && !"lookup".equals(localeMatcher) && !"best fit".equals(localeMatcher)) {
                return context.throwRangeError("Value " + localeMatcher + " out of range for Intl.NumberFormat options property localeMatcher");
            }

            String numberingSystemOption = getOptionStringChecked(context, optionsObject, "numberingSystem");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (numberingSystemOption != null && !UNICODE_TYPE_PATTERN.matcher(numberingSystemOption).matches()) {
                return context.throwRangeError("Invalid numberingSystem: " + numberingSystemOption);
            }
            if (numberingSystemOption != null) {
                numberingSystemOption = numberingSystemOption.toLowerCase(Locale.ROOT);
            }

            String style = getOptionStringChecked(context, optionsObject, "style");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (style == null) {
                style = "decimal";
            } else if (!"decimal".equals(style) && !"currency".equals(style) && !"percent".equals(style) && !"unit".equals(style)) {
                return context.throwRangeError("Invalid option value: " + style);
            }

            String currency = getOptionStringChecked(context, optionsObject, "currency");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (currency != null) {
                if (!isWellFormedCurrencyCode(currency)) {
                    return context.throwRangeError("Invalid currency code: " + currency);
                }
                currency = currency.toUpperCase(Locale.ROOT);
            }

            String currencyDisplay = getOptionStringChecked(context, optionsObject, "currencyDisplay");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (currencyDisplay == null) {
                currencyDisplay = "symbol";
            } else if (!"code".equals(currencyDisplay) && !"symbol".equals(currencyDisplay)
                    && !"narrowSymbol".equals(currencyDisplay) && !"name".equals(currencyDisplay)) {
                return context.throwRangeError("Invalid option value: " + currencyDisplay);
            }

            String currencySign = getOptionStringChecked(context, optionsObject, "currencySign");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (currencySign == null) {
                currencySign = "standard";
            } else if (!"standard".equals(currencySign) && !"accounting".equals(currencySign)) {
                return context.throwRangeError("Invalid option value: " + currencySign);
            }

            String unit = getOptionStringChecked(context, optionsObject, "unit");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            String unitDisplay = getOptionStringChecked(context, optionsObject, "unitDisplay");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (unitDisplay == null) {
                unitDisplay = "short";
            } else if (!"short".equals(unitDisplay) && !"narrow".equals(unitDisplay) && !"long".equals(unitDisplay)) {
                return context.throwRangeError("Invalid option value: " + unitDisplay);
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

            String notation = getOptionStringChecked(context, optionsObject, "notation");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (notation == null) {
                notation = "standard";
            } else if (!"standard".equals(notation) && !"scientific".equals(notation)
                    && !"engineering".equals(notation) && !"compact".equals(notation)) {
                return context.throwRangeError("Invalid option value: " + notation);
            }

            Integer minimumIntegerDigitsOption = getOptionIntegerOrUndefined(context, optionsObject, "minimumIntegerDigits", 1, 21);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            int minimumIntegerDigits = minimumIntegerDigitsOption != null ? minimumIntegerDigitsOption : 1;

            Integer minimumFractionDigitsOption = getOptionIntegerOrUndefined(context, optionsObject, "minimumFractionDigits", 0, 100);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            Integer maximumFractionDigitsOption = getOptionIntegerOrUndefined(context, optionsObject, "maximumFractionDigits", 0, 100);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            Integer minimumSignificantDigitsOption = getOptionIntegerOrUndefined(context, optionsObject, "minimumSignificantDigits", 1, 21);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            Integer maximumSignificantDigitsOption = getOptionIntegerOrUndefined(context, optionsObject, "maximumSignificantDigits", 1, 21);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            Integer roundingIncrementOption = getOptionIntegerOrUndefined(context, optionsObject, "roundingIncrement", 1, 5000);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            int roundingIncrement = roundingIncrementOption != null ? roundingIncrementOption : 1;
            if (!VALID_ROUNDING_INCREMENTS.contains(roundingIncrement)) {
                return context.throwRangeError("Invalid roundingIncrement value: " + roundingIncrement);
            }

            String roundingMode = getOptionStringChecked(context, optionsObject, "roundingMode");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (roundingMode == null) {
                roundingMode = "halfExpand";
            } else if (!"ceil".equals(roundingMode) && !"floor".equals(roundingMode)
                    && !"expand".equals(roundingMode) && !"trunc".equals(roundingMode)
                    && !"halfCeil".equals(roundingMode) && !"halfFloor".equals(roundingMode)
                    && !"halfExpand".equals(roundingMode) && !"halfTrunc".equals(roundingMode)
                    && !"halfEven".equals(roundingMode)) {
                return context.throwRangeError("Invalid option value: " + roundingMode);
            }

            String roundingPriority = getOptionStringChecked(context, optionsObject, "roundingPriority");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (roundingPriority == null) {
                roundingPriority = "auto";
            } else if (!"auto".equals(roundingPriority) && !"morePrecision".equals(roundingPriority)
                    && !"lessPrecision".equals(roundingPriority)) {
                return context.throwRangeError("Invalid option value: " + roundingPriority);
            }

            String trailingZeroDisplay = getOptionStringChecked(context, optionsObject, "trailingZeroDisplay");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (trailingZeroDisplay == null) {
                trailingZeroDisplay = "auto";
            } else if (!"auto".equals(trailingZeroDisplay) && !"stripIfInteger".equals(trailingZeroDisplay)) {
                return context.throwRangeError("Invalid option value: " + trailingZeroDisplay);
            }

            String compactDisplay = getOptionStringChecked(context, optionsObject, "compactDisplay");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (compactDisplay == null) {
                compactDisplay = "short";
            } else if (!"short".equals(compactDisplay) && !"long".equals(compactDisplay)) {
                return context.throwRangeError("Invalid option value: " + compactDisplay);
            }

            JSValue useGroupingValue = optionsObject.get(PropertyKey.fromString("useGrouping"));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String useGroupingMode = parseUseGroupingOption(context, useGroupingValue, notation);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            String signDisplay = getOptionStringChecked(context, optionsObject, "signDisplay");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (signDisplay == null) {
                signDisplay = "auto";
            } else if (!"auto".equals(signDisplay) && !"never".equals(signDisplay)
                    && !"always".equals(signDisplay) && !"exceptZero".equals(signDisplay)
                    && !"negative".equals(signDisplay)) {
                return context.throwRangeError("Invalid option value: " + signDisplay);
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

            boolean useSignificantDigits = minimumSignificantDigitsOption != null || maximumSignificantDigitsOption != null;
            int minimumSignificantDigits = 0;
            int maximumSignificantDigits = 0;
            int minimumFractionDigits;
            int maximumFractionDigits;
            if (useSignificantDigits) {
                minimumSignificantDigits = minimumSignificantDigitsOption != null ? minimumSignificantDigitsOption : 1;
                maximumSignificantDigits = maximumSignificantDigitsOption != null ? maximumSignificantDigitsOption : 21;
                if (minimumSignificantDigits > maximumSignificantDigits) {
                    return context.throwRangeError("minimumSignificantDigits value is out of range");
                }
                if ("auto".equals(roundingPriority)) {
                    minimumFractionDigits = minimumFractionDefault;
                    maximumFractionDigits = maximumFractionDefault;
                } else {
                    minimumFractionDigits = minimumFractionDigitsOption != null ? minimumFractionDigitsOption : minimumFractionDefault;
                    maximumFractionDigits = maximumFractionDigitsOption != null
                            ? maximumFractionDigitsOption
                            : Math.max(minimumFractionDigits, maximumFractionDefault);
                    if (minimumFractionDigitsOption == null && maximumFractionDigitsOption != null) {
                        minimumFractionDigits = Math.min(minimumFractionDefault, maximumFractionDigits);
                    }
                    if (minimumFractionDigits > maximumFractionDigits) {
                        return context.throwRangeError("minimumFractionDigits value is out of range");
                    }
                }
            } else {
                minimumFractionDigits = minimumFractionDigitsOption != null ? minimumFractionDigitsOption : minimumFractionDefault;
                maximumFractionDigits = maximumFractionDigitsOption != null
                        ? maximumFractionDigitsOption
                        : Math.max(minimumFractionDigits, maximumFractionDefault);
                if (minimumFractionDigitsOption == null && maximumFractionDigitsOption != null) {
                    minimumFractionDigits = Math.min(minimumFractionDefault, maximumFractionDigits);
                }
                if (minimumFractionDigits > maximumFractionDigits) {
                    return context.throwRangeError("minimumFractionDigits value is out of range");
                }
            }

            if (roundingIncrement != 1) {
                if (useSignificantDigits || "morePrecision".equals(roundingPriority) || "lessPrecision".equals(roundingPriority)) {
                    return context.throwTypeError("Invalid roundingIncrement for current rounding settings");
                }
                if (minimumFractionDigits != maximumFractionDigits) {
                    return context.throwRangeError("maximumFractionDigits and minimumFractionDigits must be equal when roundingIncrement is used");
                }
            }

            Map<String, String> unicodeExtensions = parseUnicodeExtensions(locale.toLanguageTag());
            String extensionNu = unicodeExtensions.get("nu");
            String numberingSystem = "latn";
            boolean includeNumberingExtension = false;
            boolean numberingOptionSupported = numberingSystemOption != null
                    && SUPPORTED_NUMBERING_SYSTEMS.contains(numberingSystemOption);
            if (numberingOptionSupported) {
                numberingSystem = numberingSystemOption;
                if (extensionNu != null) {
                    String extensionNuLower = extensionNu.toLowerCase(Locale.ROOT);
                    includeNumberingExtension = numberingSystem.equals(extensionNuLower);
                }
            } else if (extensionNu != null) {
                String extensionNuLower = extensionNu.toLowerCase(Locale.ROOT);
                if (SUPPORTED_NUMBERING_SYSTEMS.contains(extensionNuLower)) {
                    numberingSystem = extensionNuLower;
                    includeNumberingExtension = true;
                }
            }
            Locale strippedLocale = stripUnicodeExtensions(locale);
            Locale resolvedLocale = strippedLocale;
            if (includeNumberingExtension) {
                resolvedLocale = new Locale.Builder()
                        .setLocale(strippedLocale)
                        .setUnicodeLocaleKeyword("nu", numberingSystem)
                        .build();
            }

            JSIntlNumberFormat numberFormat = new JSIntlNumberFormat(context,
                    resolvedLocale,
                    style,
                    currency,
                    useGroupingMode,
                    minimumIntegerDigits,
                    minimumFractionDigits,
                    maximumFractionDigits,
                    useSignificantDigits,
                    minimumSignificantDigits,
                    maximumSignificantDigits,
                    unit,
                    unitDisplay,
                    signDisplay,
                    roundingMode,
                    numberingSystem,
                    notation,
                    "compact".equals(notation) ? compactDisplay : null,
                    currencyDisplay,
                    currencySign,
                    roundingIncrement,
                    roundingPriority,
                    trailingZeroDisplay);
            if (resolvedPrototype != null) {
                numberFormat.setPrototype(resolvedPrototype);
            }
            return numberFormat;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    private static JSObject createPartObject(JSContext context, String type, String value, String source) {
        JSObject part = context.createJSObject();
        part.set("type", new JSString(type));
        part.set("value", new JSString(value));
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
            JSValue optionsValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
            JSObject optionsObject;
            if (optionsValue instanceof JSUndefined) {
                optionsObject = context.createJSObject();
                optionsObject.setPrototype(null);
            } else if (optionsValue instanceof JSNull) {
                return context.throwTypeError("Cannot convert null to object");
            } else {
                JSValue optionsObjectValue = JSTypeConversions.toObject(context, optionsValue);
                if (context.hasPendingException() || !(optionsObjectValue instanceof JSObject jsObject)) {
                    return JSUndefined.INSTANCE;
                }
                optionsObject = jsObject;
            }

            String localeMatcher = getOptionStringChecked(context, optionsObject, "localeMatcher");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            localeMatcher = normalizeOption(localeMatcher, "best fit", "lookup", "best fit");

            String type = getOptionStringChecked(context, optionsObject, "type");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            type = normalizeOption(type, "cardinal", "cardinal", "ordinal");

            String notation = getOptionStringChecked(context, optionsObject, "notation");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            notation = normalizeOption(notation, "standard", "standard", "compact", "scientific", "engineering");

            Integer minimumIntegerDigitsOption = getOptionIntegerOrUndefined(context, optionsObject, "minimumIntegerDigits", 1, 21);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            int minimumIntegerDigits = minimumIntegerDigitsOption != null ? minimumIntegerDigitsOption : 1;

            Integer minimumFractionDigitsOption = getOptionIntegerOrUndefined(context, optionsObject, "minimumFractionDigits", 0, 20);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            Integer maximumFractionDigitsOption = getOptionIntegerOrUndefined(context, optionsObject, "maximumFractionDigits", 0, 20);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            int minimumFractionDigits;
            int maximumFractionDigits;
            if (minimumFractionDigitsOption == null && maximumFractionDigitsOption == null) {
                minimumFractionDigits = 0;
                maximumFractionDigits = 3;
            } else {
                minimumFractionDigits = minimumFractionDigitsOption != null ? minimumFractionDigitsOption : 0;
                maximumFractionDigits = maximumFractionDigitsOption != null
                        ? maximumFractionDigitsOption
                        : Math.max(minimumFractionDigits, 3);
                if (minimumFractionDigits > maximumFractionDigits) {
                    return context.throwRangeError("minimumFractionDigits value is out of range");
                }
            }

            Integer minimumSignificantDigitsOption = getOptionIntegerOrUndefined(context, optionsObject, "minimumSignificantDigits", 1, 21);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            Integer maximumSignificantDigitsOption = getOptionIntegerOrUndefined(context, optionsObject, "maximumSignificantDigits", 1, 21);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            Integer minimumSignificantDigits = null;
            Integer maximumSignificantDigits = null;
            if (minimumSignificantDigitsOption != null || maximumSignificantDigitsOption != null) {
                minimumSignificantDigits = minimumSignificantDigitsOption != null ? minimumSignificantDigitsOption : 1;
                maximumSignificantDigits = maximumSignificantDigitsOption != null ? maximumSignificantDigitsOption : 21;
                if (minimumSignificantDigits > maximumSignificantDigits) {
                    return context.throwRangeError("minimumSignificantDigits value is out of range");
                }
            }

            getOptionIntegerOrUndefined(context, optionsObject, "roundingIncrement", 1, 5000);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String roundingMode = getOptionStringChecked(context, optionsObject, "roundingMode");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            normalizeOption(roundingMode, "halfExpand", "ceil", "floor", "expand", "trunc",
                    "halfCeil", "halfFloor", "halfExpand", "halfTrunc", "halfEven");
            String roundingPriority = getOptionStringChecked(context, optionsObject, "roundingPriority");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            normalizeOption(roundingPriority, "auto", "auto", "morePrecision", "lessPrecision");
            String trailingZeroDisplay = getOptionStringChecked(context, optionsObject, "trailingZeroDisplay");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            normalizeOption(trailingZeroDisplay, "auto", "auto", "stripIfInteger");

            JSIntlPluralRules pluralRules = new JSIntlPluralRules(context,
                    locale,
                    type,
                    notation,
                    minimumIntegerDigits,
                    minimumFractionDigits,
                    maximumFractionDigits,
                    minimumSignificantDigits,
                    maximumSignificantDigits);
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
            JSValue optionsValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
            JSObject optionsObject;
            if (optionsValue instanceof JSUndefined) {
                optionsObject = context.createJSObject();
                optionsObject.setPrototype(null);
            } else if (optionsValue instanceof JSNull) {
                return context.throwTypeError("Cannot convert null to object");
            } else {
                JSValue optionsObjectValue = JSTypeConversions.toObject(context, optionsValue);
                if (context.hasPendingException() || !(optionsObjectValue instanceof JSObject jsObject)) {
                    return JSUndefined.INSTANCE;
                }
                optionsObject = jsObject;
            }

            String localeMatcher = getOptionStringChecked(context, optionsObject, "localeMatcher");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            localeMatcher = normalizeOption(localeMatcher, "best fit", "lookup", "best fit");

            String numberingSystemOption = getOptionStringChecked(context, optionsObject, "numberingSystem");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (numberingSystemOption != null) {
                numberingSystemOption = numberingSystemOption.toLowerCase(Locale.ROOT);
                if (!UNICODE_TYPE_PATTERN.matcher(numberingSystemOption).matches()) {
                    return context.throwRangeError("Invalid numberingSystem: " + numberingSystemOption);
                }
            }

            String style = getOptionStringChecked(context, optionsObject, "style");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            style = normalizeOption(style, "long", "long", "short", "narrow");

            String numeric = getOptionStringChecked(context, optionsObject, "numeric");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            numeric = normalizeOption(numeric, "always", "always", "auto");

            Map<String, String> unicodeExtensions = parseUnicodeExtensions(locale.toLanguageTag());
            String extensionNu = unicodeExtensions.get("nu");
            String numberingSystem = "latn";
            Locale strippedLocale = stripUnicodeExtensions(locale);
            Locale resolvedLocale = strippedLocale;
            if (numberingSystemOption != null && SUPPORTED_NUMBERING_SYSTEMS.contains(numberingSystemOption)) {
                numberingSystem = numberingSystemOption;
                if (extensionNu != null) {
                    String extensionNuLower = extensionNu.toLowerCase(Locale.ROOT);
                    if (numberingSystem.equals(extensionNuLower)) {
                        resolvedLocale = new Locale.Builder()
                                .setLocale(strippedLocale)
                                .setUnicodeLocaleKeyword("nu", numberingSystem)
                                .build();
                    }
                }
            } else if (extensionNu != null) {
                String extensionNuLower = extensionNu.toLowerCase(Locale.ROOT);
                if (SUPPORTED_NUMBERING_SYSTEMS.contains(extensionNuLower)) {
                    numberingSystem = extensionNuLower;
                    resolvedLocale = new Locale.Builder()
                            .setLocale(strippedLocale)
                            .setUnicodeLocaleKeyword("nu", numberingSystem)
                            .build();
                }
            }

            JSIntlRelativeTimeFormat relativeTimeFormat = new JSIntlRelativeTimeFormat(context,
                    resolvedLocale, style, numeric, numberingSystem);
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
        return new JSIntlNumberFormat(relativeTimeFormat.context,
                relativeTimeFormat.getLocale(),
                "decimal",
                null,
                useGroupingMode,
                1,
                0,
                3,
                false,
                0,
                null,
                null,
                "auto",
                "halfExpand",
                relativeTimeFormat.getNumberingSystem());
    }

    public static JSValue createSegmenter(JSContext context, JSObject prototype, JSObject segmentsPrototype, JSValue[] args) {
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

            JSValue optionsValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
            JSObject optionsObject;
            if (optionsValue instanceof JSUndefined) {
                optionsObject = context.createJSObject();
                optionsObject.setPrototype(null);
            } else if (optionsValue instanceof JSNull) {
                return context.throwTypeError("Cannot convert null to object");
            } else {
                JSValue optionsObjectValue = JSTypeConversions.toObject(context, optionsValue);
                if (context.hasPendingException() || !(optionsObjectValue instanceof JSObject jsObject)) {
                    return JSUndefined.INSTANCE;
                }
                optionsObject = jsObject;
            }

            String localeMatcher = getOptionStringChecked(context, optionsObject, "localeMatcher");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            localeMatcher = normalizeOption(localeMatcher, "best fit", "lookup", "best fit");

            String granularity = getOptionStringChecked(context, optionsObject, "granularity");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            granularity = normalizeOption(granularity, "grapheme", "grapheme", "word", "sentence");

            JSIntlSegmenter segmenter = new JSIntlSegmenter(context, locale, granularity, segmentsPrototype);
            if (resolvedPrototype != null) {
                segmenter.setPrototype(resolvedPrototype);
            }
            return segmenter;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    private static JSValue datePartsToJSArray(JSContext context, List<JSIntlDateTimeFormat.DatePart> parts) {
        JSArray array = context.createJSArray();
        for (JSIntlDateTimeFormat.DatePart part : parts) {
            JSObject partObj = context.createJSObject();
            partObj.set("type", new JSString(part.type()));
            partObj.set("value", new JSString(part.value()));
            array.push(partObj);
        }
        return array;
    }

    private static boolean differsOnlyInTimeParts(
            List<JSIntlDateTimeFormat.DatePart> startParts,
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
            if (!"hour".equals(type)
                    && !"minute".equals(type)
                    && !"second".equals(type)
                    && !"fractionalSecond".equals(type)
                    && !"dayPeriod".equals(type)) {
                return false;
            }
            if ("hour".equals(type)) {
                hasHourDifference = true;
            }
        }
        return hasDifference && hasHourDifference;
    }

    public static JSValue dateTimeFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context.throwTypeError("Intl.DateTimeFormat.prototype.format called on incompatible receiver");
        }
        DateTimeFormattable formattable = toDateTimeFormattable(
                context,
                args.length > 0 ? args[0] : JSUndefined.INSTANCE,
                true);
        if (formattable == null || context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSIntlDateTimeFormat effectiveDateTimeFormat = getEffectiveDateTimeFormat(context, dateTimeFormat, formattable.kind());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!isFormattableEpochMillisValid(context, formattable)) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(effectiveDateTimeFormat.format(formattable.epochMillis()));
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
        JSNativeFunction boundFormatFunction = new JSNativeFunction(context, "", 1,
                (childContext, thisValue, formatArgs) -> dateTimeFormatFormat(childContext, dateTimeFormat, formatArgs));
        context.transferPrototype(boundFormatFunction, JSFunction.NAME);
        dateTimeFormat.setBoundFormatFunction(boundFormatFunction);
        return boundFormatFunction;
    }

    /**
     * Intl.DateTimeFormat.prototype.formatRange(startDate, endDate)
     */
    public static JSValue dateTimeFormatFormatRange(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context.throwTypeError("Intl.DateTimeFormat.prototype.formatRange called on incompatible receiver");
        }
        JSValue startArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue endArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
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
        if (startFormattable.calendarId() != null
                && endFormattable.calendarId() != null
                && !startFormattable.calendarId().equals(endFormattable.calendarId())) {
            return context.throwRangeError("Invalid date/time value");
        }
        JSIntlDateTimeFormat effectiveDateTimeFormat = getEffectiveDateTimeFormat(context, dateTimeFormat, startFormattable.kind());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!isFormattableEpochMillisValid(context, startFormattable)
                || !isFormattableEpochMillisValid(context, endFormattable)) {
            return JSUndefined.INSTANCE;
        }
        double startDate = startFormattable.epochMillis();
        double endDate = endFormattable.epochMillis();

        if (Double.compare(startDate, endDate) == 0) {
            return new JSString(effectiveDateTimeFormat.format(startDate));
        }

        List<JSIntlDateTimeFormat.DatePart> startParts = effectiveDateTimeFormat.formatToPartsList(startDate);
        List<JSIntlDateTimeFormat.DatePart> endParts = effectiveDateTimeFormat.formatToPartsList(endDate);

        StringBuilder resultBuilder = new StringBuilder();
        if (startParts.equals(endParts)) {
            for (JSIntlDateTimeFormat.DatePart startPart : startParts) {
                resultBuilder.append(startPart.value());
            }
            return new JSString(resultBuilder.toString());
        }

        boolean isYearSame = true;
        int comparablePartCount = Math.min(startParts.size(), endParts.size());
        for (int index = 0; index < comparablePartCount; index++) {
            JSIntlDateTimeFormat.DatePart startPart = startParts.get(index);
            JSIntlDateTimeFormat.DatePart endPart = endParts.get(index);
            if ("year".equals(startPart.type()) && !startPart.equals(endPart)) {
                isYearSame = false;
                break;
            }
        }
        boolean canCollapse = startParts.size() == endParts.size()
                && ((effectiveDateTimeFormat.hasTextMonth() && isYearSame)
                || differsOnlyInTimeParts(startParts, endParts));
        if (canCollapse) {
            int firstDifferenceIndex = -1;
            int lastDifferenceIndex = -1;
            for (int index = 0; index < startParts.size(); index++) {
                if (!startParts.get(index).equals(endParts.get(index))) {
                    if (firstDifferenceIndex < 0) {
                        firstDifferenceIndex = index;
                    }
                    lastDifferenceIndex = index;
                }
            }
            if (firstDifferenceIndex >= 0) {
                for (int index = 0; index < firstDifferenceIndex; index++) {
                    resultBuilder.append(startParts.get(index).value());
                }
                for (int index = firstDifferenceIndex; index <= lastDifferenceIndex; index++) {
                    resultBuilder.append(startParts.get(index).value());
                }
                resultBuilder.append(" \u2013 ");
                for (int index = firstDifferenceIndex; index <= lastDifferenceIndex; index++) {
                    resultBuilder.append(endParts.get(index).value());
                }
                for (int index = lastDifferenceIndex + 1; index < startParts.size(); index++) {
                    resultBuilder.append(startParts.get(index).value());
                }
                return new JSString(resultBuilder.toString());
            }
        }

        for (JSIntlDateTimeFormat.DatePart startPart : startParts) {
            resultBuilder.append(startPart.value());
        }
        resultBuilder.append(" \u2013 ");
        for (JSIntlDateTimeFormat.DatePart endPart : endParts) {
            resultBuilder.append(endPart.value());
        }
        return new JSString(resultBuilder.toString());
    }

    /**
     * Intl.DateTimeFormat.prototype.formatRangeToParts(startDate, endDate)
     */
    public static JSValue dateTimeFormatFormatRangeToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context.throwTypeError("Intl.DateTimeFormat.prototype.formatRangeToParts called on incompatible receiver");
        }

        // Per ECMA-402: If startDate is undefined or endDate is undefined, throw TypeError
        JSValue startArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue endArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
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
        if (startFormattable.calendarId() != null
                && endFormattable.calendarId() != null
                && !startFormattable.calendarId().equals(endFormattable.calendarId())) {
            return context.throwRangeError("Invalid date/time value");
        }
        JSIntlDateTimeFormat effectiveDateTimeFormat = getEffectiveDateTimeFormat(context, dateTimeFormat, startFormattable.kind());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!isFormattableEpochMillisValid(context, startFormattable)
                || !isFormattableEpochMillisValid(context, endFormattable)) {
            return JSUndefined.INSTANCE;
        }
        double startDate = startFormattable.epochMillis();
        double endDate = endFormattable.epochMillis();

        List<JSIntlDateTimeFormat.DatePart> startParts = effectiveDateTimeFormat.formatToPartsList(startDate);
        List<JSIntlDateTimeFormat.DatePart> endParts = effectiveDateTimeFormat.formatToPartsList(endDate);

        JSArray result = context.createJSArray();
        // Check if dates are practically equal (all parts identical)
        if (startParts.equals(endParts)) {
            for (JSIntlDateTimeFormat.DatePart part : startParts) {
                result.push(createPartObject(context, part.type(), part.value(), "shared"));
            }
            return result;
        }

        // Determine the range of differing parts for collapsed formatting.
        // For text-based formats (short/long month), collapse shared parts
        // but only when the year values are the same (per CLDR range patterns).
        boolean yearSame = true;
        for (int i = 0; i < startParts.size(); i++) {
            if ("year".equals(startParts.get(i).type()) && !startParts.get(i).equals(endParts.get(i))) {
                yearSame = false;
                break;
            }
        }
        boolean canCollapse = (effectiveDateTimeFormat.hasTextMonth() && yearSame)
                || differsOnlyInTimeParts(startParts, endParts);
        if (canCollapse && startParts.size() == endParts.size()) {
            int firstDiff = -1;
            int lastDiff = -1;
            for (int i = 0; i < startParts.size(); i++) {
                if (!startParts.get(i).equals(endParts.get(i))) {
                    if (firstDiff < 0) {
                        firstDiff = i;
                    }
                    lastDiff = i;
                }
            }
            if (firstDiff >= 0) {
                // Shared prefix
                for (int i = 0; i < firstDiff; i++) {
                    result.push(createPartObject(context, startParts.get(i).type(),
                            startParts.get(i).value(), "shared"));
                }
                // Start range parts
                for (int i = firstDiff; i <= lastDiff; i++) {
                    result.push(createPartObject(context, startParts.get(i).type(),
                            startParts.get(i).value(), "startRange"));
                }
                // Range separator
                result.push(createPartObject(context, "literal", " \u2013 ", "shared"));
                // End range parts
                for (int i = firstDiff; i <= lastDiff; i++) {
                    result.push(createPartObject(context, endParts.get(i).type(),
                            endParts.get(i).value(), "endRange"));
                }
                // Shared suffix
                for (int i = lastDiff + 1; i < startParts.size(); i++) {
                    result.push(createPartObject(context, startParts.get(i).type(),
                            startParts.get(i).value(), "shared"));
                }
                return result;
            }
        }

        // Full range: all start parts as startRange, separator, all end parts as endRange
        for (JSIntlDateTimeFormat.DatePart part : startParts) {
            result.push(createPartObject(context, part.type(), part.value(), "startRange"));
        }
        result.push(createPartObject(context, "literal", " \u2013 ", "shared"));
        for (JSIntlDateTimeFormat.DatePart part : endParts) {
            result.push(createPartObject(context, part.type(), part.value(), "endRange"));
        }
        return result;
    }

    /**
     * Intl.DateTimeFormat.prototype.formatToParts(date)
     */
    public static JSValue dateTimeFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context.throwTypeError("Intl.DateTimeFormat.prototype.formatToParts called on incompatible receiver");
        }
        DateTimeFormattable formattable = toDateTimeFormattable(
                context,
                args.length > 0 ? args[0] : JSUndefined.INSTANCE,
                true);
        if (formattable == null || context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSIntlDateTimeFormat effectiveDateTimeFormat = getEffectiveDateTimeFormat(context, dateTimeFormat, formattable.kind());
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!isFormattableEpochMillisValid(context, formattable)) {
            return JSUndefined.INSTANCE;
        }
        List<JSIntlDateTimeFormat.DatePart> partsList = effectiveDateTimeFormat.formatToPartsList(formattable.epochMillis());
        return datePartsToJSArray(context, partsList);
    }

    public static JSValue dateTimeFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDateTimeFormat dateTimeFormat)) {
            return context.throwTypeError("Intl.DateTimeFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();

        // Property order per ECMA-402: locale, calendar, numberingSystem, timeZone,
        // hourCycle, hour12, weekday, era, year, month, day, dayPeriod,
        // hour, minute, second, fractionalSecondDigits, timeZoneName, dateStyle, timeStyle
        createDataProperty(resolvedOptions, "locale", new JSString(dateTimeFormat.getLocale().toLanguageTag()));
        if (dateTimeFormat.getCalendar() != null) {
            createDataProperty(resolvedOptions, "calendar", new JSString(dateTimeFormat.getCalendar()));
        }
        if (dateTimeFormat.getNumberingSystem() != null) {
            createDataProperty(resolvedOptions, "numberingSystem", new JSString(dateTimeFormat.getNumberingSystem()));
        } else {
            createDataProperty(resolvedOptions, "numberingSystem", new JSString("latn"));
        }
        createDataProperty(resolvedOptions, "timeZone", new JSString(dateTimeFormat.getTimeZone() != null
                ? dateTimeFormat.getTimeZone() : ZoneId.systemDefault().getId()));

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
            if (dateTimeFormat.getWeekdayOption() != null) {
                createDataProperty(resolvedOptions, "weekday", new JSString(dateTimeFormat.getWeekdayOption()));
            }
            if (dateTimeFormat.getEraOption() != null) {
                createDataProperty(resolvedOptions, "era", new JSString(dateTimeFormat.getEraOption()));
            }
            if (dateTimeFormat.getYearOption() != null) {
                createDataProperty(resolvedOptions, "year", new JSString(dateTimeFormat.getYearOption()));
            }
            if (dateTimeFormat.getMonthOption() != null) {
                createDataProperty(resolvedOptions, "month", new JSString(dateTimeFormat.getMonthOption()));
            }
            if (dateTimeFormat.getDayOption() != null) {
                createDataProperty(resolvedOptions, "day", new JSString(dateTimeFormat.getDayOption()));
            }
            if (dateTimeFormat.getDayPeriodOption() != null) {
                createDataProperty(resolvedOptions, "dayPeriod", new JSString(dateTimeFormat.getDayPeriodOption()));
            }
            if (dateTimeFormat.getHourOption() != null) {
                createDataProperty(resolvedOptions, "hour", new JSString(dateTimeFormat.getHourOption()));
            }
            if (dateTimeFormat.getMinuteOption() != null) {
                createDataProperty(resolvedOptions, "minute", new JSString(dateTimeFormat.getMinuteOption()));
            }
            if (dateTimeFormat.getSecondOption() != null) {
                createDataProperty(resolvedOptions, "second", new JSString(dateTimeFormat.getSecondOption()));
            }
            if (dateTimeFormat.getFractionalSecondDigits() != null) {
                createDataProperty(resolvedOptions, "fractionalSecondDigits", JSNumber.of(dateTimeFormat.getFractionalSecondDigits()));
            }
            if (dateTimeFormat.getTimeZoneNameOption() != null) {
                createDataProperty(resolvedOptions, "timeZoneName", new JSString(dateTimeFormat.getTimeZoneNameOption()));
            }
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
     * Intl.DisplayNames.prototype.resolvedOptions()
     * Returns a new object with properties in the specified order.
     */
    public static JSValue displayNamesResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDisplayNames displayNames)) {
            return context.throwTypeError("Intl.DisplayNames.prototype.resolvedOptions called on incompatible receiver");
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
        if (!(thisArg instanceof JSIntlDurationFormat durationFormat)) {
            return context.throwTypeError("Intl.DurationFormat.prototype.format called on incompatible receiver");
        }
        if (args.length == 0) {
            return context.throwTypeError("Invalid duration value");
        }

        Map<String, Double> durationRecord = JSIntlDurationFormat.toDurationRecord(context, args[0]);
        if (durationRecord == null) {
            return JSUndefined.INSTANCE;
        }

        String result = durationFormat.formatDuration(durationRecord);
        return new JSString(result);
    }

    /**
     * Intl.DurationFormat.prototype.formatToParts(duration)
     */
    public static JSValue durationFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDurationFormat durationFormat)) {
            return context.throwTypeError("Intl.DurationFormat.prototype.formatToParts called on incompatible receiver");
        }
        if (args.length == 0) {
            return context.throwTypeError("Invalid duration value");
        }

        Map<String, Double> durationRecord = JSIntlDurationFormat.toDurationRecord(context, args[0]);
        if (durationRecord == null) {
            return JSUndefined.INSTANCE;
        }

        List<JSIntlDurationFormat.FormatPart> parts = durationFormat.partitionDurationFormatPattern(durationRecord);
        JSArray result = context.createJSArray();
        for (int i = 0; i < parts.size(); i++) {
            JSIntlDurationFormat.FormatPart part = parts.get(i);
            JSObject partObj = context.createJSObject();
            partObj.set("type", new JSString(part.type()));
            partObj.set("value", new JSString(part.value()));
            if (part.unit() != null) {
                partObj.set("unit", new JSString(part.unit()));
            }
            result.set(i, partObj);
        }
        return result;
    }

    /**
     * Intl.DurationFormat.prototype.resolvedOptions()
     */
    public static JSValue durationFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlDurationFormat df)) {
            return context.throwTypeError("Intl.DurationFormat.prototype.resolvedOptions called on incompatible receiver");
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
     * Convert a firstDayOfWeek day name to its numeric value (1=Monday through 7=Sunday).
     * Returns -1 if not a recognized day name.
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

    /**
     * Get the extension suffix of a locale tag (everything from the first singleton onward),
     * including the leading hyphen. Returns "" if no extensions.
     */
    private static String getExtensionSuffix(String tag) {
        String[] parts = tag.split("-");
        int extensionStart = -1;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].length() == 1 && i > 0) {
                extensionStart = i;
                break;
            }
        }
        if (extensionStart < 0) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (int i = extensionStart; i < parts.length; i++) {
            result.append("-").append(parts[i]);
        }
        return result.toString();
    }

    /**
     * Get the default first day of the week for a locale.
     * Returns 1-7 (1=Monday through 7=Sunday).
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

    private static Integer getOptionIntegerOrUndefined(
            JSContext context,
            JSObject optionsObject,
            String key,
            int minimum,
            int maximum) {
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
            context.throwRangeError("Value " + numberValue + " out of range for Intl.NumberFormat options property " + key);
            return null;
        }
        int integerValue = (int) Math.floor(numberValue);
        if (integerValue < minimum || integerValue > maximum || integerValue != numberValue) {
            context.throwRangeError("Value " + numberValue + " out of range for Intl.NumberFormat options property " + key);
            return null;
        }
        return integerValue;
    }

    private static String getOptionString(JSContext context, JSValue optionsValue, String key) {
        if (!(optionsValue instanceof JSObject optionsObject)) {
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
     * Like getOptionString but checks for pending exceptions after property access.
     * Returns null if an exception occurred (caller must check context.hasPendingException()).
     */
    private static String getOptionStringChecked(JSContext context, JSValue optionsValue, String key) {
        if (!(optionsValue instanceof JSObject optionsObject)) {
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
     * GetOptionsObject (ECMA-402 §9.2.11).
     * Converts the options argument to an object per spec:
     * - undefined → empty object
     * - object → return as-is
     * - anything else (null, boolean, number, string, symbol, bigint) → TypeError
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

    /**
     * Get timezone IDs for a given ISO 3166 region code.
     */
    private static List<String> getTimeZonesForRegion(String region) {
        // Common region-to-timezone mappings from CLDR
        List<String> zones = switch (region) {
            case "US" -> List.of("America/Adak", "America/Anchorage", "America/Boise", "America/Chicago",
                    "America/Denver", "America/Detroit", "America/Indiana/Indianapolis",
                    "America/Indiana/Knox", "America/Indiana/Marengo", "America/Indiana/Petersburg",
                    "America/Indiana/Tell_City", "America/Indiana/Vevay", "America/Indiana/Vincennes",
                    "America/Indiana/Winamac", "America/Juneau", "America/Kentucky/Louisville",
                    "America/Kentucky/Monticello", "America/Los_Angeles", "America/Menominee",
                    "America/Metlakatla", "America/New_York", "America/Nome",
                    "America/North_Dakota/Beulah", "America/North_Dakota/Center",
                    "America/North_Dakota/New_Salem", "America/Phoenix", "America/Sitka",
                    "America/Yakutat", "Pacific/Honolulu");
            case "GB" -> List.of("Europe/London");
            case "DE" -> List.of("Europe/Berlin", "Europe/Busingen");
            case "FR" -> List.of("Europe/Paris");
            case "JP" -> List.of("Asia/Tokyo");
            case "CN" -> List.of("Asia/Shanghai", "Asia/Urumqi");
            case "IN" -> List.of("Asia/Kolkata");
            case "BR" -> List.of("America/Araguaina", "America/Bahia", "America/Belem",
                    "America/Boa_Vista", "America/Campo_Grande", "America/Cuiaba",
                    "America/Eirunepe", "America/Fortaleza", "America/Manaus",
                    "America/Noronha", "America/Porto_Velho", "America/Recife",
                    "America/Rio_Branco", "America/Santarem", "America/Sao_Paulo");
            case "AU" -> List.of("Antarctica/Macquarie", "Australia/Adelaide", "Australia/Brisbane",
                    "Australia/Broken_Hill", "Australia/Darwin", "Australia/Eucla",
                    "Australia/Hobart", "Australia/Lindeman", "Australia/Lord_Howe",
                    "Australia/Melbourne", "Australia/Perth", "Australia/Sydney");
            case "CA" -> List.of("America/Atikokan", "America/Dawson", "America/Dawson_Creek",
                    "America/Edmonton", "America/Fort_Nelson", "America/Glace_Bay",
                    "America/Goose_Bay", "America/Halifax", "America/Iqaluit",
                    "America/Moncton", "America/Rankin_Inlet", "America/Regina",
                    "America/Resolute", "America/St_Johns", "America/Swift_Current",
                    "America/Toronto", "America/Vancouver", "America/Whitehorse",
                    "America/Winnipeg", "America/Yellowknife");
            case "RU" -> List.of("Asia/Anadyr", "Asia/Barnaul", "Asia/Chita", "Asia/Irkutsk",
                    "Asia/Kamchatka", "Asia/Khandyga", "Asia/Krasnoyarsk", "Asia/Magadan",
                    "Asia/Novokuznetsk", "Asia/Novosibirsk", "Asia/Omsk", "Asia/Sakhalin",
                    "Asia/Srednekolymsk", "Asia/Tomsk", "Asia/Ust-Nera", "Asia/Vladivostok",
                    "Asia/Yakutsk", "Asia/Yekaterinburg", "Europe/Astrakhan", "Europe/Kaliningrad",
                    "Europe/Kirov", "Europe/Moscow", "Europe/Samara", "Europe/Saratov",
                    "Europe/Ulyanovsk", "Europe/Volgograd");
            case "NZ" -> List.of("Pacific/Auckland", "Pacific/Chatham");
            case "MX" -> List.of("America/Bahia_Banderas", "America/Cancun", "America/Chihuahua",
                    "America/Ciudad_Juarez", "America/Hermosillo", "America/Matamoros",
                    "America/Mazatlan", "America/Merida", "America/Mexico_City",
                    "America/Monterrey", "America/Ojinaga", "America/Tijuana");
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
            case "AR" -> List.of("America/Argentina/Buenos_Aires", "America/Argentina/Catamarca",
                    "America/Argentina/Cordoba", "America/Argentina/Jujuy",
                    "America/Argentina/La_Rioja", "America/Argentina/Mendoza",
                    "America/Argentina/Rio_Gallegos", "America/Argentina/Salta",
                    "America/Argentina/San_Juan", "America/Argentina/San_Luis",
                    "America/Argentina/Tucuman", "America/Argentina/Ushuaia");
            case "CL" -> List.of("America/Punta_Arenas", "America/Santiago", "Pacific/Easter");
            case "CO" -> List.of("America/Bogota");
            case "PE" -> List.of("America/Lima");
            case "UA" -> List.of("Europe/Kyiv", "Europe/Simferopol");
            case "RO" -> List.of("Europe/Bucharest");
            case "HU" -> List.of("Europe/Budapest");
            case "AQ" -> List.of("Antarctica/Casey", "Antarctica/Davis", "Antarctica/DumontDUrville",
                    "Antarctica/Mawson", "Antarctica/McMurdo", "Antarctica/Palmer",
                    "Antarctica/Rothera", "Antarctica/Syowa", "Antarctica/Troll",
                    "Antarctica/Vostok");
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
            case "buddhist", "chinese", "coptic", "dangi", "ethioaa", "ethiopic",
                 "gregory", "hebrew", "indian", "islamic-civil", "islamic-tbla",
                 "islamic-umalqura", "iso8601", "japanese", "persian", "roc" -> true;
            default -> false;
        };
    }

    /**
     * Check if a collation type is supported for a given locale language.
     * Based on CLDR data for common locale-specific collation types.
     */
    private static boolean isSupportedCollation(String language, String collation) {
        if (collation == null || "default".equals(collation)) {
            return true;
        }
        return switch (language) {
            case "de" -> "phonebk".equals(collation) || "eor".equals(collation);
            case "es" -> "trad".equals(collation) || "eor".equals(collation);
            case "zh" -> "pinyin".equals(collation) || "stroke".equals(collation)
                    || "zhuyin".equals(collation) || "big5han".equals(collation)
                    || "gb2312".equals(collation) || "unihan".equals(collation);
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

    /**
     * Check if a string is a transform extension field key (2 chars: letter/digit + letter/digit).
     */
    private static boolean isTransformFieldKey(String s) {
        if (s.length() != 2) {
            return false;
        }
        char c0 = s.charAt(0);
        char c1 = s.charAt(1);
        // BCP 47 / UTS 35: tkey = alpha digit
        return Character.isLetter(c0) && Character.isDigit(c1);
    }

    private static boolean isValidCollation(String collation) {
        if (collation == null || collation.isEmpty()) {
            return false;
        }
        return VALID_COLLATION_TYPES.contains(collation);
    }

    /**
     * Validate language subtag: unicode_language_subtag = alpha{2,3} | alpha{5,8}
     */
    private static boolean isValidLanguageSubtag(String lang) {
        if (lang == null || lang.isEmpty()) {
            return false;
        }
        int len = lang.length();
        if (!((len >= 2 && len <= 3) || (len >= 5 && len <= 8))) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            char c = lang.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate region subtag: unicode_region_subtag = alpha{2} | digit{3}
     */
    private static boolean isValidRegionSubtag(String region) {
        if (region == null || region.isEmpty()) {
            return false;
        }
        int len = region.length();
        if (len == 2) {
            return Character.isLetter(region.charAt(0)) && Character.isLetter(region.charAt(1));
        }
        if (len == 3) {
            return Character.isDigit(region.charAt(0)) && Character.isDigit(region.charAt(1)) && Character.isDigit(region.charAt(2));
        }
        return false;
    }

    /**
     * Validate script subtag: unicode_script_subtag = alpha{4}
     */
    private static boolean isValidScriptSubtag(String script) {
        if (script == null || script.isEmpty()) {
            return false;
        }
        if (script.length() != 4) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            char c = script.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate variants subtag: each variant is (alphanum{5,8} | digit alphanum{3}),
     * separated by hyphens. No duplicates (case-insensitive).
     */
    private static boolean isValidVariantsSubtag(String variants) {
        if (variants == null || variants.isEmpty()) {
            return false;
        }
        String[] parts = variants.split("-", -1);
        Set<String> seen = new HashSet<>();
        for (String part : parts) {
            if (part.isEmpty()) {
                return false; // empty segment
            }
            int len = part.length();
            boolean valid;
            if (len >= 5 && len <= 8) {
                valid = true;
                for (int i = 0; i < len; i++) {
                    char c = part.charAt(i);
                    if (!Character.isLetterOrDigit(c)) {
                        valid = false;
                        break;
                    }
                }
            } else if (len == 4 && Character.isDigit(part.charAt(0))) {
                valid = true;
                for (int i = 1; i < 4; i++) {
                    char c = part.charAt(i);
                    if (!Character.isLetterOrDigit(c)) {
                        valid = false;
                        break;
                    }
                }
            } else {
                valid = false;
            }
            if (!valid) {
                return false;
            }
            if (!seen.add(part.toLowerCase(Locale.ROOT))) {
                return false; // duplicate
            }
        }
        return true;
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
        JSValue listArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
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
        JSValue listArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        List<String> values = stringListFromIterable(context, listArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        List<JSIntlDurationFormat.ListFormatPart> parts = JSIntlDurationFormat.formatListToParts(listFormat, values);
        JSArray result = context.createJSArray();
        for (int i = 0; i < parts.size(); i++) {
            JSIntlDurationFormat.ListFormatPart part = parts.get(i);
            JSObject partObj = context.createJSObject();
            partObj.set("type", new JSString(part.type()));
            partObj.set("value", new JSString(part.value()));
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
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.baseName called on incompatible receiver");
        }
        return new JSString(intlLocale.getBaseName());
    }

    public static JSValue localeGetCalendar(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.calendar called on incompatible receiver");
        }
        String calendar = intlLocale.getCalendar();
        return calendar != null ? new JSString(calendar) : JSUndefined.INSTANCE;
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
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.caseFirst called on incompatible receiver");
        }
        String caseFirst = intlLocale.getCaseFirst();
        return caseFirst != null ? new JSString(caseFirst) : JSUndefined.INSTANCE;
    }

    public static JSValue localeGetCollation(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.collation called on incompatible receiver");
        }
        String collation = intlLocale.getCollation();
        return collation != null ? new JSString(collation) : JSUndefined.INSTANCE;
    }

    public static JSValue localeGetCollations(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.getCollations called on incompatible receiver");
        }
        JSArray result = context.createJSArray();
        String collation = intlLocale.getCollation();
        if (collation != null && !collation.isEmpty()
                && !"standard".equals(collation) && !"search".equals(collation)) {
            result.push(new JSString(collation));
        }
        if (result.getLength() == 0) {
            result.push(new JSString("emoji"));
            result.push(new JSString("eor"));
        }
        return result;
    }

    public static JSValue localeGetFirstDayOfWeek(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.firstDayOfWeek called on incompatible receiver");
        }
        String firstDayOfWeek = intlLocale.getFirstDayOfWeek();
        if (firstDayOfWeek == null) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(firstDayOfWeek);
    }

    public static JSValue localeGetHourCycle(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.hourCycle called on incompatible receiver");
        }
        String hourCycle = intlLocale.getHourCycle();
        return hourCycle != null ? new JSString(hourCycle) : JSUndefined.INSTANCE;
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
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.language called on incompatible receiver");
        }
        return new JSString(intlLocale.getLanguage());
    }

    public static JSValue localeGetNumberingSystem(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.numberingSystem called on incompatible receiver");
        }
        String numberingSystem = intlLocale.getNumberingSystem();
        return numberingSystem != null ? new JSString(numberingSystem) : JSUndefined.INSTANCE;
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
        } else if ("ar".equals(lang) || "he".equals(lang) || "fa".equals(lang) || "ur".equals(lang)
                || "yi".equals(lang) || "ps".equals(lang) || "sd".equals(lang) || "ckb".equals(lang)) {
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
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.maximize called on incompatible receiver");
        }
        try {
            String tag = intlLocale.getTag();
            // Strip extensions from tag to get the base locale for maximization
            String baseTag = stripExtensions(tag);
            Locale baseLocale = Locale.forLanguageTag(baseTag);
            Locale maximized = addLikelySubtags(baseLocale);
            String maximizedTag = canonicalizeLocaleTag(maximized.toLanguageTag());
            // Append extensions from the original tag
            String extensionSuffix = getExtensionSuffix(tag);
            if (!extensionSuffix.isEmpty()) {
                maximizedTag = maximizedTag + extensionSuffix;
            }
            JSIntlLocale result = new JSIntlLocale(context, maximized, maximizedTag,
                    intlLocale.getCalendar(), intlLocale.getCaseFirst(),
                    intlLocale.getCollation(), intlLocale.getFirstDayOfWeek(), intlLocale.getHourCycle(),
                    intlLocale.getNumberingSystem(), intlLocale.getNumeric(), intlLocale.isNumericSet());
            result.setPrototype(intlLocale.getPrototype());
            return result;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue localeMinimize(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.minimize called on incompatible receiver");
        }
        try {
            String tag = intlLocale.getTag();
            String baseTag = stripExtensions(tag);
            Locale baseLocale = Locale.forLanguageTag(baseTag);
            Locale minimized = removeLikelySubtags(baseLocale);
            String minimizedTag = canonicalizeLocaleTag(minimized.toLanguageTag());
            // Append extensions from the original tag
            String extensionSuffix = getExtensionSuffix(tag);
            if (!extensionSuffix.isEmpty()) {
                minimizedTag = minimizedTag + extensionSuffix;
            }
            JSIntlLocale result = new JSIntlLocale(context, minimized, minimizedTag,
                    intlLocale.getCalendar(), intlLocale.getCaseFirst(),
                    intlLocale.getCollation(), intlLocale.getFirstDayOfWeek(), intlLocale.getHourCycle(),
                    intlLocale.getNumberingSystem(), intlLocale.getNumeric(), intlLocale.isNumericSet());
            result.setPrototype(intlLocale.getPrototype());
            return result;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    public static JSValue localeToString(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlLocale intlLocale)) {
            return context.throwTypeError("Intl.Locale.prototype.toString called on incompatible receiver");
        }
        return new JSString(intlLocale.getTag());
    }

    /**
     * Normalize an offset timezone string to the canonical form: +HH:MM or -HH:MM.
     * -00 and -00:00 normalize to +00:00.
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
            if (hourTens < 0 || hourTens > 9 || hourOnes < 0 || hourOnes > 9
                    || minuteTens < 0 || minuteTens > 9 || minuteOnes < 0 || minuteOnes > 9) {
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
        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
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
        if (!(thisArg instanceof JSIntlNumberFormat numberFormat)) {
            return context.throwTypeError("Intl.NumberFormat.prototype.formatRange called on incompatible receiver");
        }
        JSValue startValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue endValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
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
            if (startDigitStart >= 0 && startDigitEnd >= startDigitStart
                    && endDigitStart >= 0 && endDigitEnd >= endDigitStart) {
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

                if ("always".equals(numberFormat.getSignDisplay())
                        && !startPrefix.isEmpty()
                        && startPrefix.equals(endPrefix)
                        && startPrefix.startsWith("+")) {
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

    public static JSValue numberFormatFormatRangeToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlNumberFormat numberFormat)) {
            return context.throwTypeError("Intl.NumberFormat.prototype.formatRangeToParts called on incompatible receiver");
        }
        JSValue startValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue endValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
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
        if (startFormatted.equals(endFormatted)) {
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
            return context.throwTypeError("Intl.NumberFormat.prototype.resolvedOptions called on incompatible receiver");
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
        createDataProperty(resolvedOptions, "minimumIntegerDigits", JSNumber.of(numberFormat.getMinimumIntegerDigits()));
        if (numberFormat.getUseSignificantDigits()) {
            createDataProperty(resolvedOptions, "minimumSignificantDigits", JSNumber.of(numberFormat.getMinimumSignificantDigits()));
            createDataProperty(resolvedOptions, "maximumSignificantDigits", JSNumber.of(numberFormat.getMaximumSignificantDigits()));
        } else {
            createDataProperty(resolvedOptions, "minimumFractionDigits", JSNumber.of(numberFormat.getMinimumFractionDigits()));
            createDataProperty(resolvedOptions, "maximumFractionDigits", JSNumber.of(numberFormat.getMaximumFractionDigits()));
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

    private static Locale parseLocaleTag(String localeTag) {
        if (localeTag == null) {
            throw new IllegalArgumentException("Invalid language tag: null");
        }
        // Tags with leading/trailing whitespace or internal whitespace are invalid
        if (localeTag.isEmpty() || !localeTag.equals(localeTag.strip())) {
            throw new IllegalArgumentException("Invalid language tag: " + localeTag);
        }
        String normalizedTag = localeTag;
        // Additional BCP 47 structural validation that Java's Locale.Builder doesn't enforce
        validateBcp47Structure(normalizedTag);
        try {
            return new Locale.Builder().setLanguageTag(normalizedTag).build();
        } catch (java.util.IllformedLocaleException e) {
            throw new IllegalArgumentException("Invalid language tag: " + localeTag, e);
        }
    }

    /**
     * Parse Unicode extension keys and attributes from a BCP 47 locale tag.
     * Attributes (3-8 alphanum subtags before any 2-char key) are stored
     * under the special key "_attr" as a sorted hyphen-separated string.
     */
    private static Map<String, String> parseUnicodeExtensions(String localeTag) {
        Map<String, String> extensions = new HashMap<>();
        if (localeTag == null) {
            return extensions;
        }
        String lowerTag = localeTag.toLowerCase(Locale.ROOT);
        int uIndex = -1;
        String[] parts = lowerTag.split("-");
        for (int i = 0; i < parts.length; i++) {
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

        // Parse attributes first (3-8 alphanum subtags before the first 2-char key)
        List<String> attributes = new ArrayList<>();
        int i = uIndex + 1;
        while (i < parts.length) {
            String part = parts[i];
            if (part.length() == 1) {
                break; // next singleton
            }
            if (part.length() == 2) {
                break; // first keyword key
            }
            // Attribute: 3-8 alphanum
            attributes.add(part);
            i++;
        }
        if (!attributes.isEmpty()) {
            Collections.sort(attributes);
            extensions.put("_attr", String.join("-", attributes));
        }

        // Parse key-value pairs
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();
        for (; i < parts.length; i++) {
            String part = parts[i];
            if (part.length() == 1) {
                // Next singleton extension — stop parsing unicode extension
                break;
            }
            if (part.length() == 2) {
                // New key: save previous key-value pair
                if (currentKey != null) {
                    extensions.put(currentKey, currentValue.toString());
                }
                currentKey = part;
                currentValue.setLength(0);
            } else if (currentKey != null) {
                // Value part: concatenate multi-segment values (e.g., "islamic-civil")
                if (!currentValue.isEmpty()) {
                    currentValue.append("-");
                }
                currentValue.append(part);
            }
        }
        if (currentKey != null) {
            extensions.put(currentKey, currentValue.toString());
        }
        return extensions;
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
        context.throwRangeError("Invalid option value: " + JSTypeConversions.toString(context, useGroupingValue).value());
        return defaultValue;
    }

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
            createDataProperty(resolvedOptions, "minimumSignificantDigits", JSNumber.of(pluralRules.getMinimumSignificantDigits()));
            createDataProperty(resolvedOptions, "maximumSignificantDigits", JSNumber.of(pluralRules.getMaximumSignificantDigits()));
        } else {
            createDataProperty(resolvedOptions, "minimumFractionDigits", JSNumber.of(pluralRules.getMinimumFractionDigits()));
            createDataProperty(resolvedOptions, "maximumFractionDigits", JSNumber.of(pluralRules.getMaximumFractionDigits()));
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
        JSValue startValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue endValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
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

    /**
     * Read a string option from options object. Returns null if option is undefined.
     */
    private static String readStringOption(JSContext context, JSObject optionsObject, String key) {
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
     * Read a string option from options object, validate against allowed values.
     * Returns null if option is undefined. Throws RangeError for invalid values.
     */
    private static String readValidatedOption(JSContext context, JSObject optionsObject,
                                              String key, String... allowedValues) {
        JSValue rawValue = optionsObject.get(PropertyKey.fromString(key));
        if (context.hasPendingException()) {
            return null;
        }
        if (rawValue == null || rawValue instanceof JSUndefined) {
            return null;
        }
        String stringValue = JSTypeConversions.toString(context, rawValue).value();
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
            return context.throwTypeError("Intl.RelativeTimeFormat.prototype.formatToParts called on incompatible receiver");
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

    // ---- Locale option validation helpers ----

    public static JSValue relativeTimeFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlRelativeTimeFormat relativeTimeFormat)) {
            return context.throwTypeError("Intl.RelativeTimeFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        createDataProperty(resolvedOptions, "locale", new JSString(relativeTimeFormat.getLocale().toLanguageTag()));
        createDataProperty(resolvedOptions, "style", new JSString(relativeTimeFormat.getStyle()));
        createDataProperty(resolvedOptions, "numeric", new JSString(relativeTimeFormat.getNumeric()));
        createDataProperty(resolvedOptions, "numberingSystem", new JSString(relativeTimeFormat.getNumberingSystem()));
        return resolvedOptions;
    }

    /**
     * Remove likely subtags from a locale (CLDR "minimize").
     * Algorithm: maximize first, then try removing subtags.
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
        Locale.Builder builder = new Locale.Builder()
                .setLanguage(maxLang).setScript(maxScript).setRegion(maxRegion);
        if (!variant.isEmpty()) {
            builder.setVariant(variant);
        }
        return builder.build();
    }

    /**
     * Resolve the prototype for an Intl constructor per GetPrototypeFromConstructor.
     * When called via Reflect.construct with a newTarget from another realm whose
     * prototype is not an object, falls back to that realm's Intl.X.prototype.
     */
    private static JSObject resolveIntlPrototype(JSContext context, JSObject defaultPrototype, String intlConstructorName) {
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
        JSValue stringValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
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
        JSValue indexValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return segments.containing(context, indexValue);
    }

    public static JSValue segmentsIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlSegments segments)) {
            return context.throwTypeError("%Segments.prototype%[@@iterator] called on incompatible receiver");
        }
        return segments.createIterator(context);
    }

    /**
     * StringListFromIterable (ECMA-402 §13.5.1).
     * Converts an iterable to a list of strings, throwing TypeError for non-string elements.
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
            for (int i = 0; i < s.length(); ) {
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
                context.throwTypeError("Iterable yielded " + JSTypeConversions.toString(context, value).value() + " which is not a string");
                return result;
            }
            result.add(((JSString) value).value());
        }
        return result;
    }

    private static Locale stripCalendarUnicodeExtension(Locale locale) {
        String languageTag = locale.toLanguageTag();
        String[] parts = languageTag.split("-");
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if ("u".equalsIgnoreCase(part)) {
                if (builder.length() > 0) {
                    builder.append('-');
                }
                builder.append(part);
                index++;
                while (index < parts.length) {
                    String key = parts[index];
                    if (key.length() != 2) {
                        index++;
                        continue;
                    }

                    if ("ca".equalsIgnoreCase(key)) {
                        index++;
                        while (index < parts.length && parts[index].length() > 2) {
                            index++;
                        }
                        continue;
                    }

                    boolean keepKey = false;
                    if ("hc".equalsIgnoreCase(key)) {
                        keepKey = true;
                    } else if ("nu".equalsIgnoreCase(key)) {
                        int valueIndex = index + 1;
                        if (valueIndex < parts.length && parts[valueIndex].length() > 2) {
                            String value = parts[valueIndex].toLowerCase(Locale.ROOT);
                            keepKey = isSupportedDateTimeNumberingSystem(value);
                        }
                    }

                    if (keepKey) {
                        builder.append('-').append(key);
                    }
                    index++;
                    while (index < parts.length && parts[index].length() > 2) {
                        if (keepKey) {
                            builder.append('-').append(parts[index]);
                        }
                        index++;
                    }
                }
                String filteredTag = builder.toString();
                if (filteredTag.endsWith("-u")) {
                    filteredTag = filteredTag.substring(0, filteredTag.length() - 2);
                    if (filteredTag.endsWith("-")) {
                        filteredTag = filteredTag.substring(0, filteredTag.length() - 1);
                    }
                }
                if (filteredTag.isEmpty()) {
                    return locale;
                }
                return Locale.forLanguageTag(filteredTag);
            }
            if (builder.length() > 0) {
                builder.append('-');
            }
            builder.append(part);
        }
        return locale;
    }

    // ---- New Locale prototype methods ----

    /**
     * Strip all extensions (-u-..., -x-..., etc.) from a locale tag,
     * returning just the language-script-region-variant part.
     */
    private static String stripExtensions(String tag) {
        String[] parts = tag.split("-");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.length() == 1) {
                // Singleton starts an extension — stop
                break;
            }
            if (result.length() > 0) {
                result.append("-");
            }
            result.append(part);
        }
        return result.toString();
    }

    /**
     * Fix Java's handling of grandfathered tags that add unnecessary -x- private use.
     */
    private static String stripJavaGrandfatheredPrivateUse(String originalTag, String javaTag) {
        String lowerOriginal = originalTag.toLowerCase(Locale.ROOT);
        // cel-gaulish → xtg (Java gives xtg-x-cel-gaulish)
        if (lowerOriginal.equals("cel-gaulish") && javaTag.contains("-x-")) {
            return javaTag.substring(0, javaTag.indexOf("-x-"));
        }
        // zh-min → nan (Java gives nan-x-zh-min)
        if (lowerOriginal.equals("zh-min") && javaTag.contains("-x-")) {
            return javaTag.substring(0, javaTag.indexOf("-x-"));
        }
        // zh-guoyu → zh (Java gives cmn which still needs aliasing)
        if (lowerOriginal.equals("zh-guoyu")) {
            return "zh";
        }
        return javaTag;
    }

    /**
     * Strip unicode extension subtags from a locale tag.
     */
    private static Locale stripUnicodeExtensions(Locale locale) {
        String tag = locale.toLanguageTag();
        String lowerTag = tag.toLowerCase(Locale.ROOT);
        int xIndex = lowerTag.indexOf("-x-");
        int uIndex = lowerTag.indexOf("-u-");
        if (uIndex < 0 || (xIndex >= 0 && xIndex < uIndex)) {
            return locale;
        }
        String[] parts = tag.split("-");
        StringBuilder builder = new StringBuilder();
        boolean inUnicodeExt = false;
        boolean inPrivateUse = false;
        for (String part : parts) {
            if (!inPrivateUse && part.length() == 1 && "x".equalsIgnoreCase(part)) {
                inPrivateUse = true;
                if (!builder.isEmpty()) {
                    builder.append("-");
                }
                builder.append(part);
                continue;
            }
            if (inPrivateUse) {
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
                    inUnicodeExt = false;
                    if (!builder.isEmpty()) {
                        builder.append("-");
                    }
                    builder.append(part);
                }
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
                    return context.throwRangeError("Value " + localeMatcher + " out of range for supportedLocalesOf options property localeMatcher");
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
     * Intl.supportedValuesOf(key)
     * Returns a sorted array of supported unique values for the given key.
     * See ECMA-402 §8.3.2
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
                values = new ArrayList<>(List.of(
                        "buddhist", "chinese", "coptic", "dangi", "ethioaa", "ethiopic",
                        "gregory", "hebrew", "indian", "islamic-civil",
                        "islamic-tbla", "islamic-umalqura", "iso8601",
                        "japanese", "persian", "roc"
                ));
            }
            case "collation" -> {
                // Per ECMA-402: must NOT include "standard" or "search"
                // "compat" not accepted by any major Collator implementation
                values = new ArrayList<>(List.of(
                        "big5han", "dict", "direct", "ducet", "emoji", "eor",
                        "gb2312", "phonebk", "phonetic", "pinyin", "reformed",
                        "searchjl", "stroke", "trad", "unihan", "zhuyin"
                ));
            }
            case "currency" -> {
                Set<Currency> currencies = Currency.getAvailableCurrencies();
                values = currencies.stream()
                        .map(Currency::getCurrencyCode)
                        .sorted()
                        .collect(Collectors.toList());
            }
            case "numberingSystem" -> {
                values = new ArrayList<>(SUPPORTED_NUMBERING_SYSTEMS);
                Collections.sort(values);
            }
            case "timeZone" -> {
                Set<String> zoneIds = ZoneId.getAvailableZoneIds();
                // IANA canonical timezone names: include Etc/GMT+N, Etc/GMT-N, UTC
                // Exclude SystemV/, EST/MST/HST (deprecated short names), and non-canonical aliases
                Set<String> nonCanonical = Set.of(
                        "EST", "MST", "HST", "GMT0", "Etc/GMT0", "Etc/Greenwich",
                        "Etc/UCT", "Etc/Universal", "Etc/Zulu", "UCT", "GMT-0",
                        "GMT+0", "Universal", "Zulu", "Greenwich", "Etc/GMT-0", "Etc/GMT+0",
                        "Etc/GMT", "Etc/UTC", "GMT",
                        "US/Alaska", "US/Aleutian", "US/Arizona", "US/Central",
                        "US/East-Indiana", "US/Eastern", "US/Hawaii", "US/Indiana-Starke",
                        "US/Michigan", "US/Mountain", "US/Pacific", "US/Samoa",
                        "Canada/Atlantic", "Canada/Central", "Canada/Eastern",
                        "Canada/Mountain", "Canada/Newfoundland", "Canada/Pacific",
                        "Canada/Saskatchewan", "Canada/Yukon",
                        "Australia/ACT", "Australia/Canberra", "Australia/LHI",
                        "Australia/NSW", "Australia/North", "Australia/Queensland",
                        "Australia/South", "Australia/Tasmania", "Australia/Victoria",
                        "Australia/West", "Australia/Yancowinna",
                        "Brazil/Acre", "Brazil/DeNoronha", "Brazil/East", "Brazil/West",
                        "Chile/Continental", "Chile/EasterIsland",
                        "Mexico/BajaNorte", "Mexico/BajaSur", "Mexico/General",
                        "NZ", "NZ-CHAT", "Navajo", "PRC", "ROC", "ROK", "W-SU",
                        "Cuba", "Egypt", "Eire", "Hongkong", "Iceland", "Iran", "Israel",
                        "Jamaica", "Japan", "Kwajalein", "Libya", "Poland", "Portugal",
                        "Singapore", "Turkey"
                );
                values = zoneIds.stream()
                        .filter(id -> !id.startsWith("SystemV/")
                                && !nonCanonical.contains(id)
                                && !id.equals("EST5EDT") && !id.equals("CST6CDT")
                                && !id.equals("MST7MDT") && !id.equals("PST8PDT"))
                        .sorted()
                        .collect(Collectors.toList());
            }
            case "unit" -> {
                values = new ArrayList<>(List.of(
                        "acre", "bit", "byte", "celsius", "centimeter", "day",
                        "degree", "fahrenheit", "fluid-ounce", "foot", "gallon",
                        "gigabit", "gigabyte", "gram", "hectare", "hour", "inch",
                        "kilobit", "kilobyte", "kilogram", "kilometer", "liter",
                        "megabit", "megabyte", "meter", "microsecond", "mile",
                        "mile-scandinavian", "milliliter", "millimeter", "millisecond",
                        "minute", "month", "nanosecond", "ounce", "percent", "petabyte",
                        "pound", "second", "stone", "terabit", "terabyte", "week",
                        "yard", "year"
                ));
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

    private static JSIntlDateTimeFormat createTemporalDateTimeFormat(
            JSContext context,
            JSIntlDateTimeFormat baseDateTimeFormat,
            DateTimeFormattableKind formattableKind) {
        boolean supportsDateFields = switch (formattableKind) {
            case PLAIN_DATE, PLAIN_DATE_TIME, PLAIN_YEAR_MONTH, PLAIN_MONTH_DAY -> true;
            default -> false;
        };
        boolean supportsTimeFields = switch (formattableKind) {
            case PLAIN_DATE_TIME, PLAIN_TIME -> true;
            default -> false;
        };
        boolean supportsWeekday = switch (formattableKind) {
            case PLAIN_DATE, PLAIN_DATE_TIME -> true;
            default -> false;
        };
        boolean supportsEra = switch (formattableKind) {
            case PLAIN_DATE, PLAIN_DATE_TIME, PLAIN_YEAR_MONTH -> true;
            default -> false;
        };
        boolean supportsYear = switch (formattableKind) {
            case PLAIN_DATE, PLAIN_DATE_TIME, PLAIN_YEAR_MONTH -> true;
            default -> false;
        };
        boolean supportsMonth = switch (formattableKind) {
            case PLAIN_DATE, PLAIN_DATE_TIME, PLAIN_MONTH_DAY, PLAIN_YEAR_MONTH -> true;
            default -> false;
        };
        boolean supportsDay = switch (formattableKind) {
            case PLAIN_DATE, PLAIN_DATE_TIME, PLAIN_MONTH_DAY -> true;
            default -> false;
        };

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
                || formattableKind == DateTimeFormattableKind.PLAIN_MONTH_DAY)
                && dateStyle != null) {
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

        boolean droppedOptionForTypeError = false;
        if (!supportsDateFields && dateStyle != null) {
            dateStyle = null;
            droppedOptionForTypeError = true;
        }
        if (!supportsTimeFields && timeStyle != null) {
            timeStyle = null;
            droppedOptionForTypeError = true;
        }
        if (!supportsWeekday && weekdayOption != null) {
            weekdayOption = null;
            droppedOptionForTypeError = true;
        }
        if (!supportsEra && eraOption != null) {
            eraOption = null;
        }
        if (!supportsYear && yearOption != null) {
            yearOption = null;
            droppedOptionForTypeError = true;
        }
        if (!supportsMonth && monthOption != null) {
            monthOption = null;
            droppedOptionForTypeError = true;
        }
        if (!supportsDay && dayOption != null) {
            dayOption = null;
            droppedOptionForTypeError = true;
        }
        if (!supportsTimeFields && dayPeriodOption != null) {
            dayPeriodOption = null;
            droppedOptionForTypeError = true;
        }
        if (!supportsTimeFields && hourOption != null) {
            hourOption = null;
            droppedOptionForTypeError = true;
        }
        if (!supportsTimeFields && minuteOption != null) {
            minuteOption = null;
            droppedOptionForTypeError = true;
        }
        if (!supportsTimeFields && secondOption != null) {
            secondOption = null;
            droppedOptionForTypeError = true;
        }
        if (!supportsTimeFields && fractionalSecondDigits != null) {
            fractionalSecondDigits = null;
            droppedOptionForTypeError = true;
        }

        boolean hasOverlap = dateStyle != null
                || timeStyle != null
                || weekdayOption != null
                || eraOption != null
                || yearOption != null
                || monthOption != null
                || dayOption != null
                || dayPeriodOption != null
                || hourOption != null
                || minuteOption != null
                || secondOption != null
                || fractionalSecondDigits != null;
        if (!hasOverlap && droppedOptionForTypeError) {
            context.throwTypeError("Invalid date/time formatting options");
            return null;
        }

        switch (formattableKind) {
            case PLAIN_DATE -> {
                boolean hasDateComponent = weekdayOption != null || yearOption != null
                        || monthOption != null || dayOption != null || dateStyle != null;
                if (!hasDateComponent) {
                    yearOption = "numeric";
                    monthOption = "numeric";
                    dayOption = "numeric";
                }
            }
            case PLAIN_DATE_TIME -> {
                boolean hasDateComponent = weekdayOption != null || yearOption != null
                        || monthOption != null || dayOption != null || dateStyle != null;
                boolean hasTimeComponent = dayPeriodOption != null || hourOption != null
                        || minuteOption != null || secondOption != null
                        || fractionalSecondDigits != null || timeStyle != null;
                if (!hasDateComponent && !hasTimeComponent) {
                    yearOption = "numeric";
                    monthOption = "numeric";
                    dayOption = "numeric";
                    hourOption = "numeric";
                    minuteOption = "numeric";
                    secondOption = "numeric";
                }
            }
            case PLAIN_MONTH_DAY -> {
                boolean hasMonthDayComponent = monthOption != null || dayOption != null || dateStyle != null;
                if (!hasMonthDayComponent) {
                    monthOption = "numeric";
                    dayOption = "numeric";
                }
            }
            case PLAIN_TIME -> {
                boolean hasTimeComponent = dayPeriodOption != null || hourOption != null
                        || minuteOption != null || secondOption != null
                        || fractionalSecondDigits != null || timeStyle != null;
                if (!hasTimeComponent) {
                    hourOption = "numeric";
                    minuteOption = "numeric";
                    secondOption = "numeric";
                }
            }
            case PLAIN_YEAR_MONTH -> {
                boolean hasYearMonthComponent = yearOption != null || monthOption != null || dateStyle != null;
                if (!hasYearMonthComponent) {
                    yearOption = "numeric";
                    monthOption = "numeric";
                }
            }
            default -> {
            }
        }

        return new JSIntlDateTimeFormat(
                context,
                baseDateTimeFormat.getLocale(),
                dateStyle,
                timeStyle,
                baseDateTimeFormat.getCalendar(),
                baseDateTimeFormat.getNumberingSystem(),
                "UTC",
                baseDateTimeFormat.getHourCycle(),
                baseDateTimeFormat.getHourCycleForInstant(),
                weekdayOption,
                eraOption,
                yearOption,
                monthOption,
                dayOption,
                dayPeriodOption,
                hourOption,
                minuteOption,
                secondOption,
                fractionalSecondDigits,
                null,
                false,
                false);
    }

    private static JSIntlDateTimeFormat getEffectiveDateTimeFormat(
            JSContext context,
            JSIntlDateTimeFormat baseDateTimeFormat,
            DateTimeFormattableKind formattableKind) {
        return switch (formattableKind) {
            case NUMBER -> baseDateTimeFormat;
            case INSTANT -> createInstantDateTimeFormat(context, baseDateTimeFormat);
            case ZONED_DATE_TIME -> {
                context.throwTypeError("Invalid date/time value");
                yield null;
            }
            default -> createTemporalDateTimeFormat(context, baseDateTimeFormat, formattableKind);
        };
    }

    private static JSIntlDateTimeFormat createInstantDateTimeFormat(
            JSContext context,
            JSIntlDateTimeFormat baseDateTimeFormat) {
        String effectiveHourCycle = baseDateTimeFormat.getHourCycle();
        if (effectiveHourCycle == null) {
            effectiveHourCycle = baseDateTimeFormat.getHourCycleForInstant();
        }
        if (effectiveHourCycle == null) {
            Map<String, String> unicodeExtensions = parseUnicodeExtensions(baseDateTimeFormat.getLocale().toLanguageTag());
            String hourCycleFromUnicodeExtension = unicodeExtensions.get("hc");
            if ("h11".equals(hourCycleFromUnicodeExtension)
                    || "h12".equals(hourCycleFromUnicodeExtension)
                    || "h23".equals(hourCycleFromUnicodeExtension)
                    || "h24".equals(hourCycleFromUnicodeExtension)) {
                effectiveHourCycle = hourCycleFromUnicodeExtension;
            } else {
                effectiveHourCycle = getLocaleDefaultHourCycle(baseDateTimeFormat.getLocale());
            }
        }

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
        String timeZoneNameOption = baseDateTimeFormat.getTimeZoneNameOption();

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

        boolean hasDateComponent = dateStyle != null
                || weekdayOption != null
                || yearOption != null
                || monthOption != null
                || dayOption != null;
        boolean hasTimeComponent = timeStyle != null
                || dayPeriodOption != null
                || hourOption != null
                || minuteOption != null
                || secondOption != null
                || fractionalSecondDigits != null;
        if (!hasDateComponent && !hasTimeComponent) {
            yearOption = "numeric";
            monthOption = "numeric";
            dayOption = "numeric";
            hourOption = "numeric";
            minuteOption = "numeric";
            secondOption = "numeric";
        }

        return new JSIntlDateTimeFormat(
                context,
                baseDateTimeFormat.getLocale(),
                dateStyle,
                timeStyle,
                baseDateTimeFormat.getCalendar(),
                baseDateTimeFormat.getNumberingSystem(),
                baseDateTimeFormat.getTimeZone(),
                effectiveHourCycle,
                effectiveHourCycle,
                weekdayOption,
                eraOption,
                yearOption,
                monthOption,
                dayOption,
                dayPeriodOption,
                hourOption,
                minuteOption,
                secondOption,
                fractionalSecondDigits,
                timeZoneNameOption,
                false,
                false);
    }

    private static boolean isFormattableEpochMillisValid(JSContext context, DateTimeFormattable formattable) {
        if (!Double.isFinite(formattable.epochMillis())) {
            context.throwRangeError("Invalid time value");
            return false;
        }
        if (formattable.kind() == DateTimeFormattableKind.NUMBER
                && Math.abs(formattable.epochMillis()) > 8.64e15) {
            context.throwRangeError("Invalid time value");
            return false;
        }
        return true;
    }

    private static DateStyleOptionFields toDateStyleOptionFields(FormatStyle dateStyle) {
        return switch (dateStyle) {
            case FULL -> new DateStyleOptionFields("long", "numeric", "long", "numeric");
            case LONG -> new DateStyleOptionFields(null, "numeric", "long", "numeric");
            case MEDIUM -> new DateStyleOptionFields(null, "numeric", "short", "numeric");
            case SHORT -> new DateStyleOptionFields(null, "2-digit", "numeric", "numeric");
        };
    }

    private static DateTimeFormattable toDateTimeFormattable(
            JSContext context,
            JSValue value,
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
            return new DateTimeFormattable(DateTimeFormattableKind.PLAIN_DATE, epochMillis, temporalPlainDate.getCalendarId());
        }
        if (value instanceof JSTemporalPlainDateTime temporalPlainDateTime) {
            double epochMillis = toEpochMillis(temporalPlainDateTime, context);
            if (context.hasPendingException()) {
                return null;
            }
            return new DateTimeFormattable(DateTimeFormattableKind.PLAIN_DATE_TIME, epochMillis, temporalPlainDateTime.getCalendarId());
        }
        if (value instanceof JSTemporalPlainMonthDay temporalPlainMonthDay) {
            double epochMillis = toEpochMillis(temporalPlainMonthDay, context);
            if (context.hasPendingException()) {
                return null;
            }
            return new DateTimeFormattable(DateTimeFormattableKind.PLAIN_MONTH_DAY, epochMillis, temporalPlainMonthDay.getCalendarId());
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
            return new DateTimeFormattable(DateTimeFormattableKind.PLAIN_YEAR_MONTH, epochMillis, temporalPlainYearMonth.getCalendarId());
        }
        if (value instanceof JSTemporalZonedDateTime temporalZonedDateTime) {
            return new DateTimeFormattable(
                    DateTimeFormattableKind.ZONED_DATE_TIME,
                    toEpochMillis(temporalZonedDateTime),
                    temporalZonedDateTime.getCalendarId());
        }

        double epochMillis = JSTypeConversions.toNumber(context, value).value();
        if (context.hasPendingException()) {
            return null;
        }
        return new DateTimeFormattable(DateTimeFormattableKind.NUMBER, epochMillis, null);
    }

    private static double toEpochMillis(JSTemporalInstant temporalInstant) {
        return toEpochMillisFromEpochNanoseconds(temporalInstant.getEpochNanoseconds());
    }

    private static double toEpochMillis(JSTemporalPlainDate temporalPlainDate, JSContext context) {
        try {
            LocalDate localDate = LocalDate.of(
                    temporalPlainDate.getIsoDate().year(),
                    temporalPlainDate.getIsoDate().month(),
                    temporalPlainDate.getIsoDate().day());
            return localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        } catch (DateTimeException e) {
            context.throwRangeError("Invalid Temporal.PlainDate value");
            return Double.NaN;
        }
    }

    private static double toEpochMillis(JSTemporalPlainDateTime temporalPlainDateTime, JSContext context) {
        try {
            return toEpochMillis(
                    temporalPlainDateTime.getIsoDateTime().date().year(),
                    temporalPlainDateTime.getIsoDateTime().date().month(),
                    temporalPlainDateTime.getIsoDateTime().date().day(),
                    temporalPlainDateTime.getIsoDateTime().time().hour(),
                    temporalPlainDateTime.getIsoDateTime().time().minute(),
                    temporalPlainDateTime.getIsoDateTime().time().second(),
                    temporalPlainDateTime.getIsoDateTime().time().millisecond(),
                    temporalPlainDateTime.getIsoDateTime().time().microsecond(),
                    temporalPlainDateTime.getIsoDateTime().time().nanosecond(),
                    context,
                    "Invalid Temporal.PlainDateTime value");
        } catch (DateTimeException e) {
            context.throwRangeError("Invalid Temporal.PlainDateTime value");
            return Double.NaN;
        }
    }

    private static double toEpochMillis(JSTemporalPlainMonthDay temporalPlainMonthDay, JSContext context) {
        try {
            LocalDate referenceDate = LocalDate.of(
                    temporalPlainMonthDay.getIsoDate().year(),
                    temporalPlainMonthDay.getIsoDate().month(),
                    temporalPlainMonthDay.getIsoDate().day());
            return referenceDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        } catch (DateTimeException e) {
            context.throwRangeError("Invalid Temporal.PlainMonthDay value");
            return Double.NaN;
        }
    }

    private static double toEpochMillis(JSTemporalPlainTime temporalPlainTime, JSContext context) {
        try {
            return toEpochMillis(
                    1970,
                    1,
                    1,
                    temporalPlainTime.getIsoTime().hour(),
                    temporalPlainTime.getIsoTime().minute(),
                    temporalPlainTime.getIsoTime().second(),
                    temporalPlainTime.getIsoTime().millisecond(),
                    temporalPlainTime.getIsoTime().microsecond(),
                    temporalPlainTime.getIsoTime().nanosecond(),
                    context,
                    "Invalid Temporal.PlainTime value");
        } catch (DateTimeException e) {
            context.throwRangeError("Invalid Temporal.PlainTime value");
            return Double.NaN;
        }
    }

    private static double toEpochMillis(JSTemporalPlainYearMonth temporalPlainYearMonth, JSContext context) {
        try {
            LocalDate localDate = LocalDate.of(
                    temporalPlainYearMonth.getIsoDate().year(),
                    temporalPlainYearMonth.getIsoDate().month(),
                    temporalPlainYearMonth.getIsoDate().day());
            return localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        } catch (DateTimeException e) {
            context.throwRangeError("Invalid Temporal.PlainYearMonth value");
            return Double.NaN;
        }
    }

    private static double toEpochMillis(JSTemporalZonedDateTime temporalZonedDateTime) {
        return toEpochMillisFromEpochNanoseconds(temporalZonedDateTime.getEpochNanoseconds());
    }

    private static double toEpochMillis(int year, int month, int day,
                                        int hour, int minute, int second,
                                        int millisecond, int microsecond, int nanosecond,
                                        JSContext context, String errorMessage) {
        int nanosecondOfSecond = millisecond * 1_000_000 + microsecond * 1_000 + nanosecond;
        try {
            LocalDate localDate = LocalDate.of(year, month, day);
            return localDate.atTime(hour, minute, second, nanosecondOfSecond)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli();
        } catch (DateTimeException e) {
            context.throwRangeError(errorMessage);
            return Double.NaN;
        }
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
     * Validate and canonicalize a time zone string.
     * Returns the canonical time zone name, or null if invalid.
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
     * Validate BCP 47 structural constraints not enforced by Java's Locale.Builder.
     * - 4+ letter primary language subtags cannot be followed by extlang subtags
     * - Variant subtags must not be duplicated
     * - Extension singletons must not be duplicated
     */
    private static void validateBcp47Structure(String tag) {
        String[] parts = tag.split("-");
        if (parts.length == 0) {
            throw new IllegalArgumentException("Invalid language tag: " + tag);
        }

        String primaryLanguage = parts[0];
        // Primary language must be all ASCII alpha
        if (!primaryLanguage.matches("[a-zA-Z]+")) {
            throw new IllegalArgumentException("Invalid language tag: " + tag);
        }

        // Per UTS 35: language = 2-3 alpha or 5-8 alpha
        // 1-letter (i, x) and 4-letter primary subtags are invalid
        int langLen = primaryLanguage.length();
        if (langLen < 2 || langLen == 4 || langLen > 8) {
            throw new IllegalArgumentException("Invalid language tag: " + tag);
        }

        // Parse subtags in strict positional order: script?, region?, variants*, extensions*, privateuse?
        int idx = 1;
        boolean scriptSeen = false;
        boolean regionSeen = false;
        Set<String> variants = new HashSet<>();
        Set<Character> extensionSingletons = new HashSet<>();

        // Optional script (4 alpha)
        if (idx < parts.length) {
            String p = parts[idx];
            if (p.length() == 4 && p.chars().allMatch(c -> Character.isLetter((char) c))) {
                scriptSeen = true;
                idx++;
            }
        }

        // Optional region (2 alpha or 3 digit)
        if (idx < parts.length) {
            String p = parts[idx];
            if ((p.length() == 2 && p.chars().allMatch(c -> Character.isLetter((char) c))) ||
                    (p.length() == 3 && p.chars().allMatch(c -> Character.isDigit((char) c)))) {
                regionSeen = true;
                idx++;
            }
        }

        // Variants (5-8 alphanum or 4 starting with digit)
        while (idx < parts.length) {
            String p = parts[idx].toLowerCase(Locale.ROOT);
            boolean isVariant = (p.length() >= 5 && p.length() <= 8) ||
                    (p.length() == 4 && Character.isDigit(p.charAt(0)));
            if (!isVariant) {
                break;
            }
            if (!variants.add(p)) {
                throw new IllegalArgumentException(
                        "Invalid language tag: duplicate variant '" + p + "': " + tag);
            }
            idx++;
        }

        // Extensions and private use
        boolean inPrivateUse = false;
        char currentSingleton = 0;
        while (idx < parts.length) {
            String p = parts[idx];
            String pLower = p.toLowerCase(Locale.ROOT);

            if (inPrivateUse) {
                idx++;
                continue;
            }

            // Private use prefix
            if ("x".equalsIgnoreCase(p)) {
                inPrivateUse = true;
                idx++;
                continue;
            }

            // Extension singleton
            if (p.length() == 1) {
                char singleton = pLower.charAt(0);
                if (!Character.isLetterOrDigit(singleton)) {
                    throw new IllegalArgumentException("Invalid language tag: " + tag);
                }
                if (!extensionSingletons.add(singleton)) {
                    throw new IllegalArgumentException(
                            "Invalid language tag: duplicate extension singleton '" + singleton + "': " + tag);
                }
                currentSingleton = singleton;
                idx++;
                // Must have at least one extension subtag
                boolean hasValue = false;
                while (idx < parts.length && parts[idx].length() > 1) {
                    String extSubtag = parts[idx].toLowerCase(Locale.ROOT);
                    // Validate Unicode extension keys (u-extension)
                    if (currentSingleton == 'u' && extSubtag.length() == 2) {
                        char c1 = extSubtag.charAt(1);
                        if (Character.isDigit(c1)) {
                            throw new IllegalArgumentException(
                                    "Invalid language tag: Unicode extension key second char must be letter: " + tag);
                        }
                    }
                    hasValue = true;
                    idx++;
                }
                if (!hasValue) {
                    throw new IllegalArgumentException(
                            "Invalid language tag: extension singleton without value: " + tag);
                }
                continue;
            }

            // If we get here, the subtag doesn't fit any valid position
            throw new IllegalArgumentException("Invalid language tag: " + tag);
        }

        // Validate transform extension structure
        if (extensionSingletons.contains('t')) {
            validateTransformExtension(tag, parts);
        }
    }

    /**
     * Validate the structure of a transform extension (t-extension) in a BCP 47 tag.
     * Per UTS 35: t-extension = "t" ("-" tlang)? ("-" tfield)*
     * tlang = unicode_language_id (2-3 or 5-8 alpha language, optional script/region/variants)
     * tfield = tkey tvalue+ (tkey = 2 chars alphanum+alpha, tvalue = 3-8 alphanum)
     */
    private static void validateTransformExtension(String tag, String[] allParts) {
        // Find the t-extension start
        int tStart = -1;
        for (int i = 0; i < allParts.length; i++) {
            if (allParts[i].length() == 1 && allParts[i].equalsIgnoreCase("t")) {
                tStart = i + 1;
                break;
            }
        }
        if (tStart < 0 || tStart >= allParts.length) {
            throw new IllegalArgumentException("Invalid language tag: empty t-extension: " + tag);
        }

        // Find the end of the t-extension (next singleton or end)
        int tEnd = allParts.length;
        for (int i = tStart; i < allParts.length; i++) {
            if (allParts[i].length() == 1) {
                tEnd = i;
                break;
            }
        }

        if (tStart == tEnd) {
            throw new IllegalArgumentException("Invalid language tag: empty t-extension: " + tag);
        }

        // Collect t-extension subtags
        List<String> parts = new ArrayList<>();
        for (int i = tStart; i < tEnd; i++) {
            parts.add(allParts[i].toLowerCase(Locale.ROOT));
        }

        int idx = 0;
        String first = parts.get(0);

        // Determine if first subtag is a tlang language
        // Language: 2-3 alpha or 5-8 alpha. NOT 4 alpha (that would be invalid) or 1, 9+ alpha.
        boolean hasTlang = false;
        if (first.chars().allMatch(Character::isLetter)) {
            if (first.length() == 4) {
                throw new IllegalArgumentException("Invalid language tag: invalid tlang in t-extension: " + tag);
            }
            if (first.length() >= 9) {
                throw new IllegalArgumentException("Invalid language tag: invalid tlang in t-extension: " + tag);
            }
            if ((first.length() >= 2 && first.length() <= 3) || (first.length() >= 5 && first.length() <= 8)) {
                hasTlang = true;
            }
        }

        if (hasTlang) {
            idx++; // consume language

            // Check for invalid extlang (3 alpha after language) — not allowed in unicode_language_id
            if (idx < parts.size()) {
                String next = parts.get(idx);
                if (next.length() == 3 && next.chars().allMatch(Character::isLetter)) {
                    throw new IllegalArgumentException(
                            "Invalid language tag: extlang not allowed in tlang: " + tag);
                }
            }

            // Optional script (4 alpha)
            if (idx < parts.size()) {
                String next = parts.get(idx);
                if (next.length() == 4 && next.chars().allMatch(Character::isLetter)) {
                    idx++;
                    // Check duplicate script
                    if (idx < parts.size()) {
                        String afterScript = parts.get(idx);
                        if (afterScript.length() == 4 && afterScript.chars().allMatch(Character::isLetter)) {
                            throw new IllegalArgumentException(
                                    "Invalid language tag: duplicate script in tlang: " + tag);
                        }
                    }
                }
            }

            // Optional region (2 alpha or 3 digit)
            if (idx < parts.size()) {
                String next = parts.get(idx);
                if ((next.length() == 2 && next.chars().allMatch(Character::isLetter)) ||
                        (next.length() == 3 && next.chars().allMatch(Character::isDigit))) {
                    idx++;
                }
            }

            // Optional variants (5-8 alphanum or 4 starting with digit)
            Set<String> tlangVariants = new HashSet<>();
            while (idx < parts.size()) {
                String next = parts.get(idx);
                boolean isVariant = (next.length() >= 5 && next.length() <= 8) ||
                        (next.length() == 4 && Character.isDigit(next.charAt(0)));
                if (!isVariant) {
                    break;
                }
                if (!tlangVariants.add(next)) {
                    throw new IllegalArgumentException("Invalid language tag: duplicate variant in tlang: " + tag);
                }
                idx++;
            }
        }

        // Validate tfields
        while (idx < parts.size()) {
            String field = parts.get(idx);
            if (field.length() == 2 && Character.isLetter(field.charAt(0)) && Character.isDigit(field.charAt(1))) {
                idx++;
                boolean hasValue = false;
                while (idx < parts.size()) {
                    String val = parts.get(idx);
                    if (val.length() == 2 && Character.isLetter(val.charAt(0)) && Character.isDigit(val.charAt(1))) {
                        break; // Next tfield key
                    }
                    if (val.length() < 3 || val.length() > 8) {
                        throw new IllegalArgumentException("Invalid language tag: invalid tvalue: " + tag);
                    }
                    hasValue = true;
                    idx++;
                }
                if (!hasValue) {
                    throw new IllegalArgumentException("Invalid language tag: tfield key without value: " + tag);
                }
            } else {
                throw new IllegalArgumentException("Invalid language tag: unexpected subtag in t-extension: " + tag);
            }
        }
    }

    /**
     * WeekdayToString: convert numeric strings "0"-"7" to day names.
     * 0 and 7 → "sun", 1 → "mon", ..., 6 → "sat".
     * Other strings pass through unchanged.
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
