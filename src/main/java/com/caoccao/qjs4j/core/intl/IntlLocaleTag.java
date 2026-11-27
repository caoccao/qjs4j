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

import java.util.*;

/** BCP 47 and Unicode locale identifier validation, alias resolution and extension parsing for Intl. */
final class IntlLocaleTag {
    private IntlLocaleTag() {
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

    private static int appendTransformLanguage(String[] parts, StringJoiner result) {
        int i = 0;
        if (!isTransformFieldKey(parts[i])) {
            String language = switch (parts[i++]) {
                case "iw" -> "he";
                case "in" -> "id";
                case "ji" -> "yi";
                default -> parts[0];
            };
            result.add(IntlLocaleAliases.language(language));
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
        return i;
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
        String originalLanguage = parts.get(0).toLowerCase(Locale.ROOT);
        String language = IntlLocaleAliases.language(originalLanguage);
        int idx = 1;
        String script = "";
        String region = "";
        List<String> variants = new ArrayList<>();
        Map<Character, String> extensions = new TreeMap<>();

        // Check for sign language prefix mappings first (sgn-XX → replacement)
        if (originalLanguage.equals("sgn") && idx < parts.size()) {
            String signKey = "sgn-" + parts.get(idx).toLowerCase(Locale.ROOT);
            String replacement = null;
            if (idx + 1 < parts.size()) {
                replacement = IntlLocaleAliases
                        .signLanguage(signKey + "-" + parts.get(idx + 1).toLowerCase(Locale.ROOT));
            }
            if (replacement == null) {
                replacement = IntlLocaleAliases.signLanguage(signKey);
            }
            if (replacement != null) {
                return replacement;
            }
        }

        // Parse script (4 alpha letters, title case after Java processing)
        if (idx < parts.size() && isValidScriptSubtag(parts.get(idx))) {
            script = parts.get(idx);
            idx++;
        }

        // For complex language aliases: add script if not already present
        if (script.isEmpty() && originalLanguage.equals("sh")) {
            script = "Latn";
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
        if (region.isEmpty() && originalLanguage.equals("cnr")) {
            region = "ME";
        }
        region = IntlLocaleAliases.region(region, language, script);

        // Parse variants
        while (idx < parts.size()) {
            String p = parts.get(idx);
            if (!hasVariantShape(p)) {
                break;
            }
            String lowerVariant = p.toLowerCase(Locale.ROOT);
            // Check variant-to-language mappings (e.g., hy-arevela → hy, hy-arevmda → hyw)
            String variantLanguage = IntlLocaleAliases.variantLanguage(lowerVariant);
            if (variantLanguage != null) {
                language = variantLanguage;
            } else {
                variants.add(lowerVariant.equals("heploc") ? "alalc97" : lowerVariant);
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
                String value = extValue.toString();
                extensions.put(singleton,
                        singleton == 'u' || singleton == 't' ? canonicalizeExtension(value, singleton) : value);
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
     * Canonicalize a Unicode or transform extension, ordering its fields and applying CLDR value aliases.
     */
    private static String canonicalizeExtension(String value, char singleton) {
        if (value.isEmpty()) {
            return value;
        }
        String[] parts = value.split("-");
        StringJoiner result = new StringJoiner("-");
        boolean transform = singleton == 't';
        int i = 0;
        if (transform) {
            i = appendTransformLanguage(parts, result);
        } else {
            while (i < parts.length && parts[i].length() != 2) {
                result.add(parts[i++]);
            }
        }
        Map<String, String> fields = new TreeMap<>();
        readFields(parts, i, parts.length, fields, transform);
        if (!transform) {
            fields.replaceAll(IntlLocaleAliases::unicodeValue);
        }
        return appendFields(result, fields);
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
                readFields(parts, i, end, extensions, false);
                break;
            }
        }
        return extensions;
    }

    private static void readFields(String[] parts, int start, int end, Map<String, String> fields, boolean transform) {
        while (start < end) {
            String key = parts[start++];
            StringJoiner value = new StringJoiner("-");
            while (start < end && (transform ? !isTransformFieldKey(parts[start]) : parts[start].length() != 2)) {
                value.add(parts[start++]);
            }
            String field = value.toString();
            fields.put(key, transform && key.equals("m0") && field.equals("names") ? "prprname" : field);
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
