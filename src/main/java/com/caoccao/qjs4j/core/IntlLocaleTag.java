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

/** BCP 47 and Unicode locale identifier validation, alias resolution and extension parsing for Intl. */
final class IntlLocaleTag {
    // ---- CLDR Language Alias Data ----
    // Maps: old language code → new language code
    // Some entries have additional script/region that are added only if not already present
    private static final Map<String, String> LANGUAGE_ALIASES = new HashMap<>();

    // Maps: old language → region to add if not already present
    private static final Map<String, String> LANGUAGE_REGION_ADDITIONS = new HashMap<>();
    // Maps: old language → script to add if not already present
    private static final Map<String, String> LANGUAGE_SCRIPT_ADDITIONS = new HashMap<>();
    // ---- Region Alias Data ----
    // Maps: old region → {language/script → new region}
    // Default replacement is first, specific language/script replacements follow
    private static final Map<String, String> REGION_ALIASES = new HashMap<>();

    private static final Map<String, Map<String, String>> REGION_ALIASES_BY_LANG = new HashMap<>();
    // Script-based region resolution for SU/810
    private static final Map<String, String> REGION_BY_SCRIPT = new HashMap<>();
    // ---- Subdivision Aliases ----
    private static final Map<String, String> SUBDIVISION_ALIASES = new HashMap<>();

    // ---- Timezone Aliases ----
    private static final Map<String, String> TIMEZONE_ALIASES = new HashMap<>();

    // ---- Transform Extension tvalue Aliases ----
    private static final Map<String, Map<String, String>> TVALUE_ALIASES = new HashMap<>();

    // Unicode extension keys where "yes" → "true" (which means omit the value)
    private static final Set<String> UNICODE_EXT_BOOLEAN_KEYS = Set.of("kb", "kc", "kh", "kk", "kn");
    // ---- Unicode Extension Value Aliases ----
    private static final Map<String, Map<String, String>> UNICODE_EXT_VALUE_ALIASES = new HashMap<>();
    // ---- Variant Alias Data ----
    private static final Map<String, String> VARIANT_ALIASES = new HashMap<>();

    private static final Map<String, String> VARIANT_TO_LANGUAGE = new HashMap<>();
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

    private IntlLocaleTag() {
    }

    private static String alias(Map<String, Map<String, String>> aliases, String key, String value) {
        Map<String, String> byValue = aliases.get(key);
        return byValue == null ? value : byValue.getOrDefault(value, value);
    }

    private static String appendFields(StringJoiner result, Map<String, String> fields) {
        for (Map.Entry<String, String> field : fields.entrySet()) {
            result.add(field.getKey());
            if (!field.getValue().isEmpty()) {
                result.add(field.getValue());
            }
        }
        return result.toString();
    }

    /**
     * Apply CLDR canonicalization to a tag that has already been through Java's Locale processing.
     */
    private static String applyCLDRCanonicalization(String tag) {
        // Parse the tag into components
        List<String> parts = Arrays.asList(tag.split("-"));
        if (parts.isEmpty()) {
            return tag;
        }

        // Find boundary indices for different subtag types
        String language = parts.get(0).toLowerCase(Locale.ROOT);
        int idx = 1;
        String script = "";
        String region = "";
        List<String> variants = new ArrayList<>();
        Map<Character, String> extensions = new TreeMap<>();

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
        if (idx < parts.size() && isValidScriptSubtag(parts.get(idx))) {
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
            if (isValidRegionSubtag(p)) {
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
            if (!hasVariantShape(p)) {
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
        for (Map.Entry<Character, String> entry : extensions.entrySet()) {
            char singleton = entry.getKey();
            String extValue = entry.getValue();
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
     * Canonicalize a locale tag string per ECMA-402 CanonicalizeUnicodeLocaleId. Applies CLDR language/region/variant
     * aliases, sorts variants, and canonicalizes extensions.
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
     * Canonicalize a Transform extension value string (the part after "t-"). Sort tfield keys, sort tlang variants,
     * canonicalize tlang, replace deprecated tvalues.
     */
    private static String canonicalizeTransformExtension(String extValue) {
        if (extValue == null || extValue.isEmpty()) {
            return extValue;
        }
        // The caller has validated the extension and folded its subtags to lower case.
        String[] parts = extValue.split("-");
        StringJoiner result = new StringJoiner("-");
        int i = 0;
        if (!isTransformFieldKey(parts[i])) {
            String language = switch (parts[i++]) {
                case "iw" -> "he";
                case "in" -> "id";
                case "ji" -> "yi";
                default -> parts[0];
            };
            result.add(LANGUAGE_ALIASES.getOrDefault(language, language));
            if (i < parts.length && isValidScriptSubtag(parts[i])) {
                result.add(parts[i++]);
            }
            if (i < parts.length && isValidRegionSubtag(parts[i])) {
                result.add(parts[i++]);
            }
            int variants = i;
            while (i < parts.length && hasVariantShape(parts[i])) {
                i++;
            }
            Arrays.sort(parts, variants, i);
            for (int j = variants; j < i; j++) {
                result.add(parts[j]);
            }
        }
        Map<String, String> fields = new TreeMap<>();
        while (i < parts.length) {
            String key = parts[i++];
            StringJoiner value = new StringJoiner("-");
            while (i < parts.length && !isTransformFieldKey(parts[i])) {
                value.add(parts[i++]);
            }
            fields.put(key, alias(TVALUE_ALIASES, key, value.toString()));
        }
        return appendFields(result, fields);
    }

    /**
     * Canonicalize a Unicode extension value string (the part after "u-"). Sort key-value pairs by key, apply value
     * aliases, handle boolean keys.
     */
    private static String canonicalizeUnicodeExtension(String extValue) {
        if (extValue == null || extValue.isEmpty()) {
            return extValue;
        }
        String[] parts = extValue.split("-");
        StringJoiner result = new StringJoiner("-");
        int i = 0;
        while (i < parts.length && parts[i].length() != 2) {
            result.add(parts[i++]);
        }
        Map<String, String> fields = new TreeMap<>();
        readUnicodeFields(parts, i, parts.length, fields);
        fields.replaceAll((key, value) -> {
            String replacement = alias(UNICODE_EXT_VALUE_ALIASES, key, value);
            if ("rg".equals(key) || "sd".equals(key)) {
                replacement = SUBDIVISION_ALIASES.getOrDefault(value, replacement);
            } else if ("tz".equals(key)) {
                replacement = TIMEZONE_ALIASES.getOrDefault(value, replacement);
            }
            return UNICODE_EXT_BOOLEAN_KEYS.contains(key) && ("yes".equals(replacement) || "true".equals(replacement))
                    ? ""
                    : replacement;
        });
        return appendFields(result, fields);
    }

    private static int endOfExtension(String[] parts, int start) {
        while (start < parts.length && parts[start].length() > 1) {
            start++;
        }
        return start;
    }

    /** Scan script, region and variants following a language subtag in a language identifier. */
    private static int endOfLanguageId(String[] parts, int language, int end, String tag, boolean transform) {
        int i = language + 1;
        if (i < end && isValidScriptSubtag(parts[i])) {
            i++;
            if (transform && i < end && isValidScriptSubtag(parts[i])) {
                throw new IllegalArgumentException("Invalid language tag: duplicate script in tlang: " + tag);
            }
        }
        if (i < end && isValidRegionSubtag(parts[i])) {
            i++;
        }
        if (i < end && hasVariantShape(parts[i])) {
            Set<String> variants = new HashSet<>();
            do {
                String variant = parts[i++].toLowerCase(Locale.ROOT);
                if (!variants.add(variant)) {
                    String detail = transform ? "duplicate variant in tlang" : "duplicate variant '" + variant + "'";
                    throw new IllegalArgumentException("Invalid language tag: " + detail + ": " + tag);
                }
            } while (i < end && hasVariantShape(parts[i]));
        }
        return i;
    }

    private static int extensionOffset(String tag, int from) {
        for (int i = tag.indexOf('-', from); i >= 0; i = tag.indexOf('-', i + 1)) {
            if (i + 2 == tag.length() || i + 2 < tag.length() && tag.charAt(i + 2) == '-') {
                return i;
            }
        }
        return tag.length();
    }

    /**
     * Get the extension suffix of a locale tag (everything from the first singleton onward), including the leading
     * hyphen. Returns "" if no extensions.
     */
    static String getExtensionSuffix(String tag) {
        return tag.substring(extensionOffset(tag, 0));
    }

    private static boolean hasVariantShape(String subtag) {
        int length = subtag.length();
        return length >= 5 && length <= 8 || length == 4 && Character.isDigit(subtag.charAt(0));
    }

    private static boolean isAsciiAlpha(String subtag) {
        for (int i = 0; i < subtag.length(); i++) {
            char c = subtag.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z')) {
                return false;
            }
        }
        return true;
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

    /**
     * Validate language subtag: unicode_language_subtag = alpha{2,3} | alpha{5,8}
     */
    static boolean isValidLanguageSubtag(String lang) {
        return lang != null && lang.length() >= 2 && lang.length() <= 8 && lang.length() != 4 && isAsciiAlpha(lang);
    }

    /**
     * Validate region subtag: unicode_region_subtag = alpha{2} | digit{3}
     */
    static boolean isValidRegionSubtag(String region) {
        if (region == null || region.isEmpty()) {
            return false;
        }
        int len = region.length();
        if (len == 2) {
            return Character.isLetter(region.charAt(0)) && Character.isLetter(region.charAt(1));
        }
        if (len == 3) {
            return Character.isDigit(region.charAt(0)) && Character.isDigit(region.charAt(1))
                    && Character.isDigit(region.charAt(2));
        }
        return false;
    }

    /**
     * Validate script subtag: unicode_script_subtag = alpha{4}
     */
    static boolean isValidScriptSubtag(String script) {
        return script != null && script.length() == 4 && isAsciiAlpha(script);
    }

    /**
     * Validate variants subtag: each variant is (alphanum{5,8} | digit alphanum{3}), separated by hyphens. No
     * duplicates (case-insensitive).
     */
    static boolean isValidVariantsSubtag(String variants) {
        if (variants == null || variants.isEmpty()) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        for (String part : variants.split("-", -1)) {
            if (!hasVariantShape(part) || !seen.add(part.toLowerCase(Locale.ROOT))) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isLetterOrDigit(part.charAt(i))) {
                    return false;
                }
            }
        }
        return true;
    }

    static Locale parseLocaleTag(String localeTag) {
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
     * Parse Unicode extension keys and attributes from a BCP 47 locale tag. Attributes (3-8 alphanum subtags before any
     * 2-char key) are stored under the special key "_attr" as a sorted hyphen-separated string.
     */
    static Map<String, String> parseUnicodeExtensions(String localeTag) {
        Map<String, String> extensions = new HashMap<>();
        if (localeTag == null) {
            return extensions;
        }
        String[] parts = localeTag.toLowerCase(Locale.ROOT).split("-");
        for (int i = 1; i < parts.length; i++) {
            if ("x".equals(parts[i])) {
                break;
            }
            if ("u".equals(parts[i])) {
                int end = endOfExtension(parts, ++i);
                int start = i;
                while (i < end && parts[i].length() != 2) {
                    i++;
                }
                if (i > start) {
                    Arrays.sort(parts, start, i);
                    extensions.put("_attr", String.join("-", Arrays.asList(parts).subList(start, i)));
                }
                readUnicodeFields(parts, i, end, extensions);
                break;
            }
        }
        return extensions;
    }

    private static void readUnicodeFields(String[] parts, int start, int end, Map<String, String> fields) {
        while (start < end) {
            String key = parts[start++];
            StringJoiner value = new StringJoiner("-");
            while (start < end && parts[start].length() != 2) {
                value.add(parts[start++]);
            }
            fields.put(key, value.toString());
        }
    }

    /**
     * Strip all extensions (-u-..., -x-..., etc.) from a locale tag, returning just the language-script-region-variant
     * part.
     */
    static String stripExtensions(String tag) {
        if (tag.length() == 1 || tag.length() > 1 && tag.charAt(1) == '-') {
            return "";
        }
        return tag.substring(0, extensionOffset(tag, 0));
    }

    /**
     * Fix Java's handling of grandfathered tags that add unnecessary -x- private use.
     */
    private static String stripJavaGrandfatheredPrivateUse(String originalTag, String javaTag) {
        String original = originalTag.toLowerCase(Locale.ROOT);
        if ("zh-guoyu".equals(original)) {
            return "zh";
        }
        int privateUse = javaTag.indexOf("-x-");
        if (privateUse >= 0 && ("cel-gaulish".equals(original) || "zh-min".equals(original))) {
            return javaTag.substring(0, privateUse);
        }
        return javaTag;
    }

    /**
     * Strip unicode extension subtags from a locale tag.
     */
    static Locale stripUnicodeExtensions(Locale locale) {
        String tag = locale.toLanguageTag();
        int start = tag.indexOf("-u-");
        int privateUse = tag.indexOf("-x-");
        if (start < 0 || privateUse >= 0 && privateUse < start || tag.startsWith("x-")) {
            return locale;
        }
        int end = extensionOffset(tag, start + 2);
        return Locale.forLanguageTag(tag.substring(0, start) + tag.substring(end));
    }

    /**
     * Validate BCP 47 structural constraints not enforced by Java's Locale.Builder. - 4+ letter primary language
     * subtags cannot be followed by extlang subtags - Variant subtags must not be duplicated - Extension singletons
     * must not be duplicated
     */
    private static void validateBcp47Structure(String tag) {
        String[] parts = tag.split("-");
        if (parts.length == 0 || !isValidLanguageSubtag(parts[0])) {
            throw new IllegalArgumentException("Invalid language tag: " + tag);
        }
        int index = endOfLanguageId(parts, 0, parts.length, tag, false);
        Set<Character> singletons = new HashSet<>();
        while (index < parts.length) {
            String part = parts[index++];
            if (part.length() != 1 || !Character.isLetterOrDigit(part.charAt(0))) {
                throw new IllegalArgumentException("Invalid language tag: " + tag);
            }
            char singleton = Character.toLowerCase(part.charAt(0));
            if (singleton == 'x') {
                break; // Locale.Builder checks the remaining private-use subtags.
            }
            if (!singletons.add(singleton)) {
                throw new IllegalArgumentException(
                        "Invalid language tag: duplicate extension singleton '" + singleton + "': " + tag);
            }
            int end = endOfExtension(parts, index);
            if (end == index) {
                throw new IllegalArgumentException("Invalid language tag: extension singleton without value: " + tag);
            }
            if (singleton == 't') {
                validateTransformExtension(tag, parts, index, end);
            } else if (singleton == 'u') {
                for (int i = index; i < end; i++) {
                    if (parts[i].length() == 2 && Character.isDigit(parts[i].charAt(1))) {
                        throw new IllegalArgumentException(
                                "Invalid language tag: Unicode extension key second char must be letter: " + tag);
                    }
                }
            }
            index = end;
        }
    }

    /**
     * Validate the structure of a transform extension (t-extension) in a BCP 47 tag. Per UTS 35: t-extension = "t" ("-"
     * tlang)? ("-" tfield)* tlang = unicode_language_id (2-3 or 5-8 alpha language, optional script/region/variants)
     * tfield = tkey tvalue+ (tkey = 2 chars alphanum+alpha, tvalue = 3-8 alphanum)
     */
    private static void validateTransformExtension(String tag, String[] parts, int index, int end) {
        if (!isTransformFieldKey(parts[index])) {
            if (!isValidLanguageSubtag(parts[index])) {
                throw new IllegalArgumentException("Invalid language tag: invalid tlang in t-extension: " + tag);
            }
            if (index + 1 < end && parts[index + 1].length() == 3 && isAsciiAlpha(parts[index + 1])) {
                throw new IllegalArgumentException("Invalid language tag: extlang not allowed in tlang: " + tag);
            }
            index = endOfLanguageId(parts, index, end, tag, true);
        }
        while (index < end) {
            if (!isTransformFieldKey(parts[index++])) {
                throw new IllegalArgumentException("Invalid language tag: unexpected subtag in t-extension: " + tag);
            }
            int start = index;
            while (index < end && !isTransformFieldKey(parts[index])) {
                int length = parts[index++].length();
                if (length < 3 || length > 8) {
                    throw new IllegalArgumentException("Invalid language tag: invalid tvalue: " + tag);
                }
            }
            if (index == start) {
                throw new IllegalArgumentException("Invalid language tag: tfield key without value: " + tag);
            }
        }
    }
}
