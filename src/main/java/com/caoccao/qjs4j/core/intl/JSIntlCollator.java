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

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSFunction;
import com.caoccao.qjs4j.core.JSObject;

import java.text.Collator;
import java.text.Normalizer;
import java.text.ParseException;
import java.text.RuleBasedCollator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Intl.Collator instance object.
 */
public final class JSIntlCollator extends JSObject {
    /** Forward French collators by their rules; every French locale shares one rule set, so this holds one entry. */
    static final Map<String, Collator> FORWARD_FRENCH_COLLATORS = new ConcurrentHashMap<>();
    public static final String NAME = "Intl.Collator";
    private final Collator accentCollator;
    private final boolean backwardSecondary;
    private JSFunction boundCompareFunction;
    private final String caseFirst;
    private final String collation;
    private final Collator collator;
    private final boolean ignorePunctuation;
    private final Locale locale;
    private final boolean numeric;
    private final boolean phonebookMode;
    private final Collator primaryCollator;
    private final String resolvedLocaleTag;
    private final String sensitivity;
    /** Turkish and Azeri pair I with dotless ı and İ with i; a locale-blind fold would merge I into i. */
    private final boolean turkic;
    private final String usage;

    public JSIntlCollator(JSContext context, Locale locale, String sensitivity, String usage, String collation,
            boolean numeric, String caseFirst, boolean ignorePunctuation, String resolvedLocaleTag) {
        super(context);
        this.locale = locale;
        this.sensitivity = sensitivity;
        this.usage = usage;
        this.collation = collation;
        this.numeric = numeric;
        this.caseFirst = caseFirst;
        this.ignorePunctuation = ignorePunctuation;
        this.resolvedLocaleTag = resolvedLocaleTag;
        // Phonebook mode: explicit phonebk collation or German search
        this.phonebookMode = "phonebk".equals(collation)
                || ("search".equals(usage) && "de".equals(locale.getLanguage()));
        this.collator = createCollator(locale);
        switch (sensitivity) {
            case "base" -> collator.setStrength(Collator.PRIMARY);
            case "accent" -> collator.setStrength(Collator.SECONDARY);
            case "case" -> collator.setStrength(Collator.TERTIARY);
            case "variant" -> collator.setStrength(Collator.IDENTICAL);
            default -> collator.setStrength(Collator.TERTIARY);
        }
        // Enable decomposition for canonical equivalence
        collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
        accentCollator = (Collator) collator.clone();
        accentCollator.setStrength(Collator.SECONDARY);
        primaryCollator = (Collator) collator.clone();
        primaryCollator.setStrength(Collator.PRIMARY);
        // Canadian French weighs accents from the end of the string: the last accent difference decides.
        backwardSecondary = accentCollator.compare("\u00E9a", "e\u00E1") < 0;
        turkic = "tr".equals(locale.getLanguage()) || "az".equals(locale.getLanguage());
    }

    public int compare(String left, String right) {
        String effectiveLeft = left;
        String effectiveRight = right;
        if (ignorePunctuation) {
            effectiveLeft = stripPunctuation(effectiveLeft);
            effectiveRight = stripPunctuation(effectiveRight);
        }
        if (phonebookMode) {
            effectiveLeft = expandGermanUmlauts(effectiveLeft);
            effectiveRight = expandGermanUmlauts(effectiveRight);
        }
        // Every script's decimal digits share the primary weight of the ASCII digit with the same value, so compare
        // the folded forms; the JDK collator would otherwise rank an unmapped digit after every letter. Numeric
        // collation loses nothing by folding: ICU gives a digit run one primary weight and no tertiary ones. An
        // ignorable character carries no weight but still ends a digit run, so the numeric path keeps it for the
        // tokenizer and drops it from each text segment.
        if (numeric) {
            return compareNumeric(normalizeDigits(effectiveLeft), normalizeDigits(effectiveRight));
        }
        return compareText(effectiveLeft, effectiveRight);
    }

    private int compareNumeric(String left, String right) {
        int i = 0;
        int j = 0;
        int secondaryResult = 0;
        int tertiaryResult = 0;
        while (true) {
            context.checkExecutionInterrupt();
            i = skipIgnorable(left, i);
            j = skipIgnorable(right, j);
            if (i >= left.length() && j >= right.length()) {
                break;
            }
            boolean leftDigit = i < left.length() && Character.isDigit(left.codePointAt(i));
            boolean rightDigit = j < right.length() && Character.isDigit(right.codePointAt(j));
            if (leftDigit && rightDigit) {
                // A digit run is one primary weight, its numeric value: leading zeros do not count.
                int leftEnd = digitEnd(left, i);
                int rightEnd = digitEnd(right, j);
                while (i < leftEnd && Character.digit(left.codePointAt(i), 10) == 0) {
                    i += Character.charCount(left.codePointAt(i));
                }
                while (j < rightEnd && Character.digit(right.codePointAt(j), 10) == 0) {
                    j += Character.charCount(right.codePointAt(j));
                }
                int lengthComparison = Integer.compare(left.codePointCount(i, leftEnd),
                        right.codePointCount(j, rightEnd));
                if (lengthComparison != 0) {
                    return lengthComparison;
                }
                while (i < leftEnd) {
                    int a = left.codePointAt(i);
                    int b = right.codePointAt(j);
                    int comparison = Integer.compare(Character.digit(a, 10), Character.digit(b, 10));
                    if (comparison != 0) {
                        return comparison;
                    }
                    i += Character.charCount(a);
                    j += Character.charCount(b);
                }
                i = leftEnd;
                j = rightEnd;
            } else {
                // A side that has run out contributes an empty segment, so a trailing accent or a trailing letter
                // weighs exactly what the sensitivity says it does, rather than counting as extra length.
                int leftEnd = i < left.length() ? textEnd(left, i) : i;
                int rightEnd = j < right.length() ? textEnd(right, j) : j;
                String a = left.substring(i, leftEnd);
                String b = right.substring(j, rightEnd);
                int comparison = compareText(a, b);
                if (comparison != 0) {
                    if (comparePrimary(a, b) != 0) {
                        return comparison;
                    }
                    // ICU compares every secondary weight before any tertiary one, so an accent difference later in
                    // the string outranks a case difference before it. Keep the first difference of each level —
                    // or, where the locale weighs accents backward, the last secondary one. Case sensitivity has
                    // no secondary level at all: there every non-primary difference is a case difference.
                    if (!"case".equals(sensitivity) && compareSecondary(a, b) != 0) {
                        if (backwardSecondary || secondaryResult == 0) {
                            secondaryResult = comparison;
                        }
                    } else if (tertiaryResult == 0) {
                        tertiaryResult = comparison;
                    }
                }
                i = leftEnd;
                j = rightEnd;
            }
        }
        return secondaryResult != 0 ? secondaryResult : tertiaryResult;
    }

    /** The primary level alone, with case folded away. */
    private int comparePrimary(String left, String right) {
        return Integer.signum(primaryCollator.compare(foldCase(left), foldCase(right)));
    }

    /**
     * The secondary level alone: accents, with case folded away first because the JDK's Danish collator weighs case at
     * this level, where ICU keeps it tertiary for every locale.
     */
    private int compareSecondary(String left, String right) {
        return Integer.signum(accentCollator.compare(foldCase(left), foldCase(right)));
    }

    /**
     * Settle the tertiary level the way ICU does, one weight per character in order: the first position where the case
     * differs, or where a plain digit meets its fullwidth or mathematical variant, decides. Lowercase sorts first
     * unless caseFirst says otherwise; Danish, whose tailoring puts uppercase first, resolves caseFirst to "upper".
     * Returns 0 when nothing at this level separates the strings.
     */
    private int compareTertiary(String left, String right) {
        boolean digitVariants = "variant".equals(sensitivity);
        boolean upperFirst = "upper".equals(caseFirst);
        String a = Normalizer.normalize(left, Normalizer.Form.NFD);
        String b = Normalizer.normalize(right, Normalizer.Form.NFD);
        for (int i = 0, j = 0; i < a.length() && j < b.length();) {
            int x = a.codePointAt(i);
            int y = b.codePointAt(j);
            if (x != y) {
                if (Character.isDigit(x) && Character.isDigit(y)) {
                    int comparison = digitVariants ? Integer.compare(digitVariantRank(x), digitVariantRank(y)) : 0;
                    if (comparison != 0) {
                        return comparison;
                    }
                } else if (Character.isUpperCase(x) != Character.isUpperCase(y)) {
                    return Character.isUpperCase(x) == upperFirst ? -1 : 1;
                }
            }
            i += Character.charCount(x);
            j += Character.charCount(y);
        }
        return 0;
    }

    /**
     * Compare two strings whose digits may still be in any script or width. The primary and secondary levels come from
     * the collator over digit-folded forms, because every script's decimal digit shares the primary weight of the ASCII
     * digit with the same value and the JDK collator would otherwise rank an unmapped digit after every letter. The
     * tertiary level is settled in character order, where a digit's width and a letter's case both carry weight.
     */
    private int compareText(String left, String right) {
        left = stripIgnorable(left);
        right = stripIgnorable(right);
        String foldedLeft = normalizeDigits(left);
        String foldedRight = normalizeDigits(right);
        if ("base".equals(sensitivity)) {
            return comparePrimary(foldedLeft, foldedRight);
        }
        if ("accent".equals(sensitivity)) {
            return compareSecondary(foldedLeft, foldedRight);
        }
        if ("case".equals(sensitivity)) {
            // ICU runs primary strength with a case level: accents carry no weight, but a letter the locale keeps
            // apart at the primary level, such as Turkish ş or dotless ı, stays apart. Accents are removed only
            // for the case walk, so the two strings line up character by character.
            int primary = comparePrimary(foldedLeft, foldedRight);
            return primary != 0 ? primary : compareTertiary(stripAccents(left), stripAccents(right));
        }
        int result = Integer.signum(collator.compare(foldedLeft, foldedRight));
        if (result != 0 && compareSecondary(foldedLeft, foldedRight) != 0) {
            return result;
        }
        int tertiary = compareTertiary(left, right);
        return tertiary != 0 ? tertiary : result;
    }

    /**
     * Lowercase without adding accents. Turkic casing handles both precomposed İ and its canonical decomposition, I +
     * dot above. Other locales use the root rules: Lithuanian lowercasing would add a dot to accented I and make Í
     * differ from í at secondary strength.
     */
    private String foldCase(String text) {
        return text.toLowerCase(turkic ? locale : Locale.ROOT);
    }

    public JSFunction getBoundCompareFunction() {
        return boundCompareFunction;
    }

    public String getCaseFirst() {
        return caseFirst;
    }

    public String getCollation() {
        return collation;
    }

    public boolean getIgnorePunctuation() {
        return ignorePunctuation;
    }

    public Locale getLocale() {
        return locale;
    }

    public boolean getNumeric() {
        return numeric;
    }

    public String getResolvedLocaleTag() {
        return resolvedLocaleTag;
    }

    public String getSensitivity() {
        return sensitivity;
    }

    public String getUsage() {
        return usage;
    }

    public void setBoundCompareFunction(JSFunction boundCompareFunction) {
        this.boundCompareFunction = boundCompareFunction;
    }

    /**
     * The JDK's French tailoring is the root rules plus the backward-secondary modifier, so every French locale weighs
     * accents from the end of the string. ICU, and so V8, does that only for Canadian French; the other French locales
     * get the same rules without the modifier.
     */
    private static Collator createCollator(Locale locale) {
        Collator collator = Collator.getInstance(locale);
        if (!"fr".equals(locale.getLanguage()) || "CA".equals(locale.getCountry())
                || !(collator instanceof RuleBasedCollator ruleBasedCollator)) {
            return collator;
        }
        String rules = ruleBasedCollator.getRules();
        if (!rules.endsWith("@")) {
            return collator;
        }
        String forwardRules = rules.substring(0, rules.length() - 1);
        Collator forward = FORWARD_FRENCH_COLLATORS.computeIfAbsent(forwardRules, key -> {
            try {
                return new RuleBasedCollator(key);
            } catch (ParseException e) {
                return null;
            }
        });
        return forward == null ? collator : (Collator) forward.clone();
    }

    private static int digitEnd(String value, int start) {
        int end = start;
        while (end < value.length() && Character.isDigit(value.codePointAt(end))) {
            end += Character.charCount(value.codePointAt(end));
        }
        return end;
    }

    /**
     * The tertiary class of a decimal digit: 0 for one with no compatibility decomposition, 1 for a fullwidth digit and
     * 2 for a font variant, in the order the Unicode Collation Algorithm assigns their tertiary weights.
     */
    private static int digitVariantRank(int codePoint) {
        if (codePoint >= 0xFF10 && codePoint <= 0xFF19) {
            return 1;
        }
        String text = new String(Character.toChars(codePoint));
        return Normalizer.normalize(text, Normalizer.Form.NFKD).equals(text) ? 0 : 2;
    }

    /**
     * Expand German umlauts for phonebook collation (DIN 5007-2). ä→ae, ö→oe, ü→ue, Ä→Ae, Ö→Oe, Ü→Ue, ß→ss
     */
    private static String expandGermanUmlauts(String input) {
        StringBuilder result = null;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            String expansion = switch (c) {
                case '\u00E4' -> "ae"; // ä
                case '\u00F6' -> "oe"; // ö
                case '\u00FC' -> "ue"; // ü
                case '\u00C4' -> "Ae"; // Ä
                case '\u00D6' -> "Oe"; // Ö
                case '\u00DC' -> "Ue"; // Ü
                case '\u00DF' -> "ss"; // ß
                default -> null;
            };
            if (expansion != null) {
                if (result == null) {
                    result = new StringBuilder(input.length() + 8);
                    result.append(input, 0, i);
                }
                result.append(expansion);
            } else if (result != null) {
                result.append(c);
            }
        }
        return result != null ? result.toString() : input;
    }

    /**
     * Whether ICU gives a character no weight at any level: format characters and controls, apart from the five
     * controls and NEL that the root collation treats as whitespace. The JDK collator weighs several of these.
     */
    private static boolean isIgnorable(int codePoint) {
        int type = Character.getType(codePoint);
        if (type == Character.FORMAT) {
            return true;
        }
        return type == Character.CONTROL && !(codePoint >= 0x09 && codePoint <= 0x0D) && codePoint != 0x85;
    }

    private static String normalizeDigits(String text) {
        StringBuilder result = null;
        for (int offset = 0; offset < text.length();) {
            int point = text.codePointAt(offset);
            int digit = Character.digit(point, 10);
            if (digit >= 0 && point > '9') {
                if (result == null) {
                    result = new StringBuilder(text.length()).append(text, 0, offset);
                }
                result.append((char) ('0' + digit));
            } else if (result != null) {
                result.appendCodePoint(point);
            }
            offset += Character.charCount(point);
        }
        return result == null ? text : result.toString();
    }

    private static int skipIgnorable(String value, int start) {
        while (start < value.length() && isIgnorable(value.codePointAt(start))) {
            start += Character.charCount(value.codePointAt(start));
        }
        return start;
    }

    /**
     * Strip accent marks (combining diacritical marks) for "case" sensitivity. This allows comparison that considers
     * case but ignores accents.
     */
    private static String stripAccents(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        StringBuilder result = null;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            // Skip combining diacritical marks (U+0300 - U+036F)
            if (c >= '\u0300' && c <= '\u036F') {
                if (result == null) {
                    result = new StringBuilder(normalized.length());
                    result.append(normalized, 0, i);
                }
            } else if (result != null) {
                result.append(c);
            }
        }
        return result != null ? result.toString() : normalized;
    }

    private static String stripIgnorable(String text) {
        StringBuilder result = null;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (isIgnorable(codePoint)) {
                if (result == null) {
                    result = new StringBuilder(text.length()).append(text, 0, offset);
                }
            } else if (result != null) {
                result.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
        }
        return result == null ? text : result.toString();
    }

    /**
     * Strip punctuation and whitespace characters for ignorePunctuation comparison.
     */
    private static String stripPunctuation(String input) {
        StringBuilder result = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            int type = Character.getType(c);
            // Skip punctuation (Pc, Pd, Ps, Pe, Pi, Pf, Po) and separator (Zs, Zl, Zp) categories
            if (type != Character.CONNECTOR_PUNCTUATION && type != Character.DASH_PUNCTUATION
                    && type != Character.START_PUNCTUATION && type != Character.END_PUNCTUATION
                    && type != Character.INITIAL_QUOTE_PUNCTUATION && type != Character.FINAL_QUOTE_PUNCTUATION
                    && type != Character.OTHER_PUNCTUATION && type != Character.SPACE_SEPARATOR
                    && type != Character.LINE_SEPARATOR && type != Character.PARAGRAPH_SEPARATOR) {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static int textEnd(String value, int start) {
        int end = start + Character.charCount(value.codePointAt(start));
        while (end < value.length() && !Character.isDigit(value.codePointAt(end))) {
            end += Character.charCount(value.codePointAt(end));
        }
        return end;
    }
}
