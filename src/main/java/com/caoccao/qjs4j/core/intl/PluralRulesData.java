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

// Generated CLDR data: Copyright © 1991-2025 Unicode, Inc. See LICENSE-UNICODE.
package com.caoccao.qjs4j.core.intl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Generated from CLDR 48 by scripts/python/generate_plural_rules.py. */
final class PluralRulesData {
    private static final Map<String, List<Rule>> CARDINAL = new HashMap<>();
    private static final Map<String, List<Rule>> ORDINAL = new HashMap<>();
    private static final Map<String, Map<String, String>> RANGES = new HashMap<>();
    static {
        addRules(CARDINAL,
                "bm bo dz hnj id ig ii in ja jbo jv jw kde kea km ko lkt lo ms my nqo osa root sah ses sg su th to tpi vi wo yo yue zh",
                "other:");
        addRules(CARDINAL, "am as bn doi fa gu hi kn kok kok_Latn pcm zu", "one:i = 0 or n = 1", "other:");
        addRules(CARDINAL, "ff hy kab", "one:i = 0,1", "other:");
        addRules(CARDINAL, "ast de en et fi fy gl ia ie io ji lij nl sc sv sw ur yi", "one:i = 1 and v = 0", "other:");
        addRules(CARDINAL, "si", "one:n = 0,1 or i = 0 and f = 1", "other:");
        addRules(CARDINAL, "ak bho csw guw ln mg nso pa ti wa", "one:n = 0..1", "other:");
        addRules(CARDINAL, "tzm", "one:n = 0..1 or n = 11..99", "other:");
        addRules(CARDINAL,
                "af an asa az bal bem bez bg brx ce cgg chr ckb dv ee el eo eu fo fur gsw ha haw hu jgo jmc ka kaj kcg kk kkj kl ks ksb ku ky lb lg mas mgo ml mn mr nah nb nd ne nn nnh no nr ny nyn om or os pap ps rm rof rwk saq sd sdh seh sn so sq ss ssy st syr ta te teo tig tk tn tr ts ug uz ve vo vun wae xh xog",
                "one:n = 1", "other:");
        addRules(CARDINAL, "da", "one:n = 1 or t != 0 and i = 0,1", "other:");
        addRules(CARDINAL, "is", "one:t = 0 and i % 10 = 1 and i % 100 != 11 or t % 10 = 1 and t % 100 != 11",
                "other:");
        addRules(CARDINAL, "mk", "one:v = 0 and i % 10 = 1 and i % 100 != 11 or f % 10 = 1 and f % 100 != 11",
                "other:");
        addRules(CARDINAL, "ceb fil tl",
                "one:v = 0 and i = 1,2,3 or v = 0 and i % 10 != 4,6,9 or v != 0 and f % 10 != 4,6,9", "other:");
        addRules(CARDINAL, "lv prg", "zero:n % 10 = 0 or n % 100 = 11..19 or v = 2 and f % 100 = 11..19",
                "one:n % 10 = 1 and n % 100 != 11 or v = 2 and f % 10 = 1 and f % 100 != 11 or v != 2 and f % 10 = 1",
                "other:");
        addRules(CARDINAL, "lag", "zero:n = 0", "one:i = 0,1 and n != 0", "other:");
        addRules(CARDINAL, "blo cv ksh", "zero:n = 0", "one:n = 1", "other:");
        addRules(CARDINAL, "he iw", "one:i = 1 and v = 0 or i = 0 and v != 0", "two:i = 2 and v = 0", "other:");
        addRules(CARDINAL, "iu naq sat se sma smi smj smn sms", "one:n = 1", "two:n = 2", "other:");
        addRules(CARDINAL, "shi", "one:i = 0 or n = 1", "few:n = 2..10", "other:");
        addRules(CARDINAL, "mo ro", "one:i = 1 and v = 0", "few:v != 0 or n = 0 or n != 1 and n % 100 = 1..19",
                "other:");
        addRules(CARDINAL, "bs hr sh sr", "one:v = 0 and i % 10 = 1 and i % 100 != 11 or f % 10 = 1 and f % 100 != 11",
                "few:v = 0 and i % 10 = 2..4 and i % 100 != 12..14 or f % 10 = 2..4 and f % 100 != 12..14", "other:");
        addRules(CARDINAL, "fr", "one:i = 0,1", "many:e = 0 and i != 0 and i % 1000000 = 0 and v = 0 or e != 0..5",
                "other:");
        addRules(CARDINAL, "pt", "one:i = 0..1", "many:e = 0 and i != 0 and i % 1000000 = 0 and v = 0 or e != 0..5",
                "other:");
        addRules(CARDINAL, "ca it lld pt_PT scn vec", "one:i = 1 and v = 0",
                "many:e = 0 and i != 0 and i % 1000000 = 0 and v = 0 or e != 0..5", "other:");
        addRules(CARDINAL, "es", "one:n = 1", "many:e = 0 and i != 0 and i % 1000000 = 0 and v = 0 or e != 0..5",
                "other:");
        addRules(CARDINAL, "gd", "one:n = 1,11", "two:n = 2,12", "few:n = 3..10,13..19", "other:");
        addRules(CARDINAL, "sl", "one:v = 0 and i % 100 = 1", "two:v = 0 and i % 100 = 2",
                "few:v = 0 and i % 100 = 3..4 or v != 0", "other:");
        addRules(CARDINAL, "dsb hsb", "one:v = 0 and i % 100 = 1 or f % 100 = 1",
                "two:v = 0 and i % 100 = 2 or f % 100 = 2", "few:v = 0 and i % 100 = 3..4 or f % 100 = 3..4", "other:");
        addRules(CARDINAL, "cs sk", "one:i = 1 and v = 0", "few:i = 2..4 and v = 0", "many:v != 0", "other:");
        addRules(CARDINAL, "pl", "one:i = 1 and v = 0", "few:v = 0 and i % 10 = 2..4 and i % 100 != 12..14",
                "many:v = 0 and i != 1 and i % 10 = 0..1 or v = 0 and i % 10 = 5..9 or v = 0 and i % 100 = 12..14",
                "other:");
        addRules(CARDINAL, "be", "one:n % 10 = 1 and n % 100 != 11", "few:n % 10 = 2..4 and n % 100 != 12..14",
                "many:n % 10 = 0 or n % 10 = 5..9 or n % 100 = 11..14", "other:");
        addRules(CARDINAL, "lt", "one:n % 10 = 1 and n % 100 != 11..19", "few:n % 10 = 2..9 and n % 100 != 11..19",
                "many:f != 0", "other:");
        addRules(CARDINAL, "ru uk", "one:v = 0 and i % 10 = 1 and i % 100 != 11",
                "few:v = 0 and i % 10 = 2..4 and i % 100 != 12..14",
                "many:v = 0 and i % 10 = 0 or v = 0 and i % 10 = 5..9 or v = 0 and i % 100 = 11..14", "other:");
        addRules(CARDINAL, "sgs", "one:n % 10 = 1 and n % 100 != 11", "two:n = 2",
                "few:n != 2 and n % 10 = 2..9 and n % 100 != 11..19", "many:f != 0", "other:");
        addRules(CARDINAL, "br", "one:n % 10 = 1 and n % 100 != 11,71,91", "two:n % 10 = 2 and n % 100 != 12,72,92",
                "few:n % 10 = 3..4,9 and n % 100 != 10..19,70..79,90..99", "many:n != 0 and n % 1000000 = 0", "other:");
        addRules(CARDINAL, "mt", "one:n = 1", "two:n = 2", "few:n = 0 or n % 100 = 3..10", "many:n % 100 = 11..19",
                "other:");
        addRules(CARDINAL, "ga", "one:n = 1", "two:n = 2", "few:n = 3..6", "many:n = 7..10", "other:");
        addRules(CARDINAL, "gv", "one:v = 0 and i % 10 = 1", "two:v = 0 and i % 10 = 2",
                "few:v = 0 and i % 100 = 0,20,40,60,80", "many:v != 0", "other:");
        addRules(CARDINAL, "kw", "zero:n = 0", "one:n = 1",
                "two:n % 100 = 2,22,42,62,82 or n % 1000 = 0 and n % 100000 = 1000..20000,40000,60000,80000 or n != 0 and n % 1000000 = 100000",
                "few:n % 100 = 3,23,43,63,83", "many:n != 1 and n % 100 = 1,21,41,61,81", "other:");
        addRules(CARDINAL, "ar ars", "zero:n = 0", "one:n = 1", "two:n = 2", "few:n % 100 = 3..10",
                "many:n % 100 = 11..99", "other:");
        addRules(CARDINAL, "cy", "zero:n = 0", "one:n = 1", "two:n = 2", "few:n = 3", "many:n = 6", "other:");
        addRules(ORDINAL,
                "af am an ar ast bg bs ce cs cv da de dsb el es et eu fa fi fy gl gsw he hr hsb ia id ie in is iw ja km kn ko ky lt lv ml mn my nb nl no pa pl prg ps pt root ru sd sh si sk sl sr sw ta te th tpi tr ur uz yue zh zu",
                "other:");
        addRules(ORDINAL, "sv", "one:n % 10 = 1,2 and n % 100 != 11,12", "other:");
        addRules(ORDINAL, "bal fil fr ga hy lo mo ms ro tl vi", "one:n = 1", "other:");
        addRules(ORDINAL, "hu", "one:n = 1,5", "other:");
        addRules(ORDINAL, "ne", "one:n = 1..4", "other:");
        addRules(ORDINAL, "be", "few:n % 10 = 2,3 and n % 100 != 12,13", "other:");
        addRules(ORDINAL, "uk", "few:n % 10 = 3 and n % 100 != 13", "other:");
        addRules(ORDINAL, "tk", "few:n % 10 = 6,9 or n = 10", "other:");
        addRules(ORDINAL, "kk", "many:n % 10 = 6 or n % 10 = 9 or n % 10 = 0 and n != 0", "other:");
        addRules(ORDINAL, "it lld sc vec", "many:n = 11,8,80,800", "other:");
        addRules(ORDINAL, "lij scn", "many:n = 11,8,80..89,800..899", "other:");
        addRules(ORDINAL, "ka", "one:i = 1", "many:i = 0 or i % 100 = 2..20,40,60,80", "other:");
        addRules(ORDINAL, "sq", "one:n = 1", "many:n % 10 = 4 and n % 100 != 14", "other:");
        addRules(ORDINAL, "kw", "one:n = 1..4 or n % 100 = 1..4,21..24,41..44,61..64,81..84",
                "many:n = 5 or n % 100 = 5", "other:");
        addRules(ORDINAL, "blo", "zero:i = 0", "one:i = 1", "few:i = 2,3,4,5,6", "other:");
        addRules(ORDINAL, "en", "one:n % 10 = 1 and n % 100 != 11", "two:n % 10 = 2 and n % 100 != 12",
                "few:n % 10 = 3 and n % 100 != 13", "other:");
        addRules(ORDINAL, "kok kok_Latn mr", "one:n = 1", "two:n = 2,3", "few:n = 4", "other:");
        addRules(ORDINAL, "gd", "one:n = 1,11", "two:n = 2,12", "few:n = 3,13", "other:");
        addRules(ORDINAL, "ca", "one:n = 1,3", "two:n = 2", "few:n = 4", "other:");
        addRules(ORDINAL, "mk", "one:i % 10 = 1 and i % 100 != 11", "two:i % 10 = 2 and i % 100 != 12",
                "many:i % 10 = 7,8 and i % 100 != 17,18", "other:");
        addRules(ORDINAL, "az", "one:i % 10 = 1,2,5,7,8 or i % 100 = 20,50,70,80",
                "few:i % 10 = 3,4 or i % 1000 = 100,200,300,400,500,600,700,800,900",
                "many:i = 0 or i % 10 = 6 or i % 100 = 40,60,90", "other:");
        addRules(ORDINAL, "gu hi", "one:n = 1", "two:n = 2,3", "few:n = 4", "many:n = 6", "other:");
        addRules(ORDINAL, "as bn", "one:n = 1,5,7,8,9,10", "two:n = 2,3", "few:n = 4", "many:n = 6", "other:");
        addRules(ORDINAL, "or", "one:n = 1,5,7..9", "two:n = 2,3", "few:n = 4", "many:n = 6", "other:");
        addRules(ORDINAL, "cy", "zero:n = 0,7,8,9", "one:n = 1", "two:n = 2", "few:n = 3,4", "many:n = 5,6", "other:");
        addRanges("id ja km ko lo ms my th vi yue zh", "other:other:other");
        addRanges("am as bn gu hi hy kn mr ps zu", "one:one:one", "one:other:other", "other:other:other");
        addRanges("ka", "one:other:one", "other:one:other", "other:other:other");
        addRanges("az de el gl gsw hu kk ky lij ml mn ne nl sc sq sw ta te tk tr ug uz", "one:other:other",
                "other:one:one", "other:other:other");
        addRanges("af an bg en et eu fi ia io nb no pcm sv ur", "one:other:other", "other:one:other",
                "other:other:other");
        addRanges("da fil is pa", "one:one:one", "one:other:other", "other:one:one", "other:other:other");
        addRanges("si", "one:one:one", "one:other:other", "other:one:other", "other:other:other");
        addRanges("ak fa or sd", "one:one:other", "one:other:other", "other:one:one", "other:other:other");
        addRanges("mk", "one:one:other", "one:other:other", "other:one:other", "other:other:other");
        addRanges("lv", "zero:zero:other", "zero:one:one", "zero:other:other", "one:zero:other", "one:one:one",
                "one:other:other", "other:zero:other", "other:one:one", "other:other:other");
        addRanges("he", "one:two:other", "one:other:other", "two:other:other", "other:one:other", "other:two:other",
                "other:other:other");
        addRanges("ro", "one:few:few", "one:other:other", "few:one:few", "few:few:few", "few:other:other",
                "other:few:few", "other:other:other");
        addRanges("bs hr sr", "one:one:one", "one:few:few", "one:other:other", "few:one:one", "few:few:few",
                "few:other:other", "other:one:one", "other:few:few", "other:other:other");
        addRanges("fr pt", "one:one:one", "one:other:other", "other:other:other");
        addRanges("it scn", "one:other:other", "other:one:one", "other:other:other");
        addRanges("ca es", "one:other:other", "other:one:other", "other:other:other");
        addRanges("sl", "one:one:few", "one:two:two", "one:few:few", "one:other:other", "two:one:few", "two:two:two",
                "two:few:few", "two:other:other", "few:one:few", "few:two:two", "few:few:few", "few:other:other",
                "other:one:few", "other:two:two", "other:few:few", "other:other:other");
        addRanges("cs pl sk", "one:few:few", "one:many:many", "one:other:other", "few:few:few", "few:many:many",
                "few:other:other", "many:one:one", "many:few:few", "many:many:many", "many:other:other",
                "other:one:one", "other:few:few", "other:many:many", "other:other:other");
        addRanges("be lt ru uk", "one:one:one", "one:few:few", "one:many:many", "one:other:other", "few:one:one",
                "few:few:few", "few:many:many", "few:other:other", "many:one:one", "many:few:few", "many:many:many",
                "many:other:other", "other:one:one", "other:few:few", "other:many:many", "other:other:other");
        addRanges("ga", "one:two:two", "one:few:few", "one:many:many", "one:other:other", "two:few:few",
                "two:many:many", "two:other:other", "few:few:few", "few:many:many", "few:other:other", "many:many:many",
                "many:other:other", "other:one:one", "other:two:two", "other:few:few", "other:many:many",
                "other:other:other");
        addRanges("cy", "zero:one:one", "zero:two:two", "zero:few:few", "zero:many:many", "zero:other:other",
                "one:two:two", "one:few:few", "one:many:many", "one:other:other", "two:few:few", "two:many:many",
                "two:other:other", "few:many:many", "few:other:other", "many:other:other", "other:one:one",
                "other:two:two", "other:few:few", "other:many:many", "other:other:other");
        addRanges("ar", "zero:one:zero", "zero:two:zero", "zero:few:few", "zero:many:many", "zero:other:other",
                "one:two:other", "one:few:few", "one:many:many", "one:other:other", "two:few:few", "two:many:many",
                "two:other:other", "few:few:few", "few:many:many", "few:other:other", "many:few:few", "many:many:many",
                "many:other:other", "other:one:other", "other:two:other", "other:few:few", "other:many:many",
                "other:other:other");
    }

    private PluralRulesData() {
    }

    private static void addRanges(String locales, String... definitions) {
        Map<String, String> ranges = new HashMap<>();
        for (String definition : definitions) {
            int separator = definition.lastIndexOf(':');
            ranges.put(definition.substring(0, separator), definition.substring(separator + 1));
        }
        for (String locale : locales.split(" ")) {
            RANGES.put(locale, Map.copyOf(ranges));
        }
    }

    private static void addRules(Map<String, List<Rule>> data, String locales, String... definitions) {
        List<Rule> rules = Arrays.stream(definitions).map(Rule::parse).toList();
        for (String locale : locales.split(" ")) {
            data.put(locale, rules);
        }
    }

    static List<String> categories(Locale locale, boolean ordinal) {
        return rules(locale, ordinal).stream().map(Rule::category).toList();
    }

    static String range(Locale locale, String start, String end) {
        return RANGES.getOrDefault(locale.getLanguage(), Map.of()).getOrDefault(start + ':' + end, "other");
    }

    private static List<Rule> rules(Locale locale, boolean ordinal) {
        Map<String, List<Rule>> data = ordinal ? ORDINAL : CARDINAL;
        String name = locale.toLanguageTag().replace('-', '_');
        while (!name.isEmpty()) {
            List<Rule> rules = data.get(name);
            if (rules != null) {
                return rules;
            }
            int separator = name.lastIndexOf('_');
            name = separator < 0 ? "" : name.substring(0, separator);
        }
        return data.get("root");
    }

    static String select(Locale locale, boolean ordinal, BigDecimal number, int exponent) {
        Operands operands = new Operands(number, exponent);
        for (Rule rule : rules(locale, ordinal)) {
            if (rule.matches(operands)) {
                return rule.category();
            }
        }
        return "other";
    }

    private record Operands(BigDecimal number, int exponent) {
        BigDecimal value(char operand) {
            int scale = Math.max(0, number.scale());
            BigDecimal trimmed = number.stripTrailingZeros();
            return switch (operand) {
                case 'n' -> number;
                case 'i' -> number.setScale(0, RoundingMode.DOWN);
                case 'v' -> BigDecimal.valueOf(scale);
                case 'w' -> BigDecimal.valueOf(Math.max(0, trimmed.scale()));
                case 'f' -> number.remainder(BigDecimal.ONE).movePointRight(scale);
                case 't' -> trimmed.remainder(BigDecimal.ONE).movePointRight(Math.max(0, trimmed.scale()));
                // The exponent is zero for standard notation.
                default -> BigDecimal.valueOf(exponent);
            };
        }
    }

    private record Relation(char operand, BigDecimal modulus, boolean negate, List<BigDecimal[]> ranges) {
        static Relation parse(String source) {
            String[] parts = source.split(" (?:!=|=) ");
            String[] operand = parts[0].split(" % ");
            List<BigDecimal[]> ranges = new ArrayList<>();
            for (String item : parts[1].split(",")) {
                String[] ends = item.split("\\.\\.");
                ranges.add(new BigDecimal[]{new BigDecimal(ends[0]), new BigDecimal(ends[ends.length - 1])});
            }
            return new Relation(operand[0].charAt(0), operand.length == 1 ? null : new BigDecimal(operand[1]),
                    source.contains("!="), ranges);
        }

        boolean matches(Operands operands) {
            BigDecimal value = operands.value(operand);
            if (modulus != null) {
                value = value.remainder(modulus);
            }
            boolean contained = false;
            if (value.stripTrailingZeros().scale() <= 0) {
                for (BigDecimal[] range : ranges) {
                    if (value.compareTo(range[0]) >= 0 && value.compareTo(range[1]) <= 0) {
                        contained = true;
                        break;
                    }
                }
            }
            return negate != contained;
        }
    }

    private record Rule(String category, List<List<Relation>> alternatives) {
        static Rule parse(String definition) {
            int separator = definition.indexOf(':');
            String condition = definition.substring(separator + 1);
            List<List<Relation>> alternatives = condition.isEmpty()
                    ? List.of()
                    : Arrays.stream(condition.split(" or "))
                            .map(part -> Arrays.stream(part.split(" and ")).map(Relation::parse).toList()).toList();
            return new Rule(definition.substring(0, separator), alternatives);
        }

        boolean matches(Operands operands) {
            if (alternatives.isEmpty()) {
                return true;
            }
            for (List<Relation> alternative : alternatives) {
                boolean matches = true;
                for (Relation relation : alternative) {
                    if (!relation.matches(operands)) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return true;
                }
            }
            return false;
        }
    }
}
