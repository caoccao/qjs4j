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

/** CLDR replacements used when canonicalizing Unicode locale identifiers. */
final class IntlLocaleAliases {
    private IntlLocaleAliases() {
    }

    static String calendar(String value) {
        return switch (value) {
            case "ethiopic-amete-alem" -> "ethioaa";
            case "islamicc" -> "islamic-civil";
            default -> value;
        };
    }

    static String language(String value) {
        return switch (value) {
            case "aar" -> "aa";
            case "abk" -> "ab";
            case "afr" -> "af";
            case "aka" -> "ak";
            case "amh" -> "am";
            case "ara" -> "ar";
            case "arg" -> "an";
            case "asm" -> "as";
            case "ava" -> "av";
            case "ave" -> "ae";
            case "aym" -> "ay";
            case "aze" -> "az";
            case "bak" -> "ba";
            case "bam" -> "bm";
            case "bel" -> "be";
            case "ben" -> "bn";
            case "bis" -> "bi";
            case "bod" -> "bo";
            case "bos" -> "bs";
            case "bre" -> "br";
            case "bul" -> "bg";
            case "cat" -> "ca";
            case "ces" -> "cs";
            case "cha" -> "ch";
            case "che" -> "ce";
            case "chu" -> "cu";
            case "chv" -> "cv";
            case "cor" -> "kw";
            case "cos" -> "co";
            case "cre" -> "cr";
            case "cym" -> "cy";
            case "dan" -> "da";
            case "deu" -> "de";
            case "div" -> "dv";
            case "dzo" -> "dz";
            case "ell" -> "el";
            case "eng" -> "en";
            case "epo" -> "eo";
            case "est" -> "et";
            case "eus" -> "eu";
            case "ewe" -> "ee";
            case "fao" -> "fo";
            case "fas" -> "fa";
            case "fij" -> "fj";
            case "fin" -> "fi";
            case "fra" -> "fr";
            case "fry" -> "fy";
            case "ful" -> "ff";
            case "gla" -> "gd";
            case "gle" -> "ga";
            case "glg" -> "gl";
            case "glv" -> "gv";
            case "grn" -> "gn";
            case "guj" -> "gu";
            case "hat" -> "ht";
            case "hau" -> "ha";
            case "hbs", "srp", "sh", "cnr" -> "sr";
            case "heb" -> "he";
            case "her" -> "hz";
            case "hin" -> "hi";
            case "hmo" -> "ho";
            case "hrv" -> "hr";
            case "hun" -> "hu";
            case "hye" -> "hy";
            case "ibo" -> "ig";
            case "ido" -> "io";
            case "iii" -> "ii";
            case "iku" -> "iu";
            case "ile" -> "ie";
            case "ina" -> "ia";
            case "ind" -> "id";
            case "ipk" -> "ik";
            case "isl" -> "is";
            case "ita" -> "it";
            case "jav", "jw" -> "jv";
            case "jpn" -> "ja";
            case "kal" -> "kl";
            case "kan" -> "kn";
            case "kas" -> "ks";
            case "kat" -> "ka";
            case "kau" -> "kr";
            case "kaz" -> "kk";
            case "khm" -> "km";
            case "kik" -> "ki";
            case "kin" -> "rw";
            case "kir" -> "ky";
            case "kom" -> "kv";
            case "kon" -> "kg";
            case "kor" -> "ko";
            case "kua" -> "kj";
            case "kur" -> "ku";
            case "lao" -> "lo";
            case "lat" -> "la";
            case "lav" -> "lv";
            case "lim" -> "li";
            case "lin" -> "ln";
            case "lit" -> "lt";
            case "ltz" -> "lb";
            case "lub" -> "lu";
            case "lug" -> "lg";
            case "mah" -> "mh";
            case "mal" -> "ml";
            case "mar" -> "mr";
            case "mkd" -> "mk";
            case "mlg" -> "mg";
            case "mlt" -> "mt";
            case "mon" -> "mn";
            case "mri" -> "mi";
            case "msa" -> "ms";
            case "mya" -> "my";
            case "nau" -> "na";
            case "nav" -> "nv";
            case "nbl" -> "nr";
            case "nde" -> "nd";
            case "ndo" -> "ng";
            case "nep" -> "ne";
            case "nld" -> "nl";
            case "nno" -> "nn";
            case "nob" -> "nb";
            case "nor" -> "no";
            case "nya" -> "ny";
            case "oci" -> "oc";
            case "oji" -> "oj";
            case "ori" -> "or";
            case "orm" -> "om";
            case "oss" -> "os";
            case "pan" -> "pa";
            case "pli" -> "pi";
            case "pol" -> "pl";
            case "por" -> "pt";
            case "pus" -> "ps";
            case "que" -> "qu";
            case "roh" -> "rm";
            case "ron", "mo" -> "ro";
            case "run" -> "rn";
            case "rus" -> "ru";
            case "sag" -> "sg";
            case "san" -> "sa";
            case "sin" -> "si";
            case "slk" -> "sk";
            case "slv" -> "sl";
            case "sme" -> "se";
            case "smo" -> "sm";
            case "sna" -> "sn";
            case "snd" -> "sd";
            case "som" -> "so";
            case "sot" -> "st";
            case "spa" -> "es";
            case "sqi" -> "sq";
            case "srd" -> "sc";
            case "ssw" -> "ss";
            case "sun" -> "su";
            case "swa" -> "sw";
            case "swe" -> "sv";
            case "tah" -> "ty";
            case "tam" -> "ta";
            case "tat" -> "tt";
            case "tel" -> "te";
            case "tgk" -> "tg";
            case "tgl" -> "tl";
            case "tha" -> "th";
            case "tir" -> "ti";
            case "ton" -> "to";
            case "tsn" -> "tn";
            case "tso" -> "ts";
            case "tuk" -> "tk";
            case "tur" -> "tr";
            case "twi" -> "tw";
            case "uig" -> "ug";
            case "ukr" -> "uk";
            case "urd" -> "ur";
            case "uzb" -> "uz";
            case "ven" -> "ve";
            case "vie" -> "vi";
            case "vol" -> "vo";
            case "wln" -> "wa";
            case "wol" -> "wo";
            case "xho" -> "xh";
            case "yid" -> "yi";
            case "yor" -> "yo";
            case "zha" -> "za";
            case "zho", "cmn" -> "zh";
            case "zul" -> "zu";
            case "tl" -> "fil";
            default -> value;
        };
    }

    static String region(String region, String language, String script) {
        // The former Soviet Union and the Neutral Zone have context-dependent replacements.
        if (region.equals("SU") || region.equals("810")) {
            return switch (language) {
                case "hy" -> "AM";
                case "ka" -> "GE";
                case "az" -> "AZ";
                case "be" -> "BY";
                case "et" -> "EE";
                case "lv" -> "LV";
                case "lt" -> "LT";
                case "tk" -> "TM";
                case "uk" -> "UA";
                case "uz" -> "UZ";
                case "kk" -> "KZ";
                case "ky" -> "KG";
                case "tg" -> "TJ";
                case "mn" -> "MN";
                default -> regionForScript(script, "RU");
            };
        }
        if (region.equals("NT")) {
            return language.equals("az") ? "SA" : regionForScript(script, "SA");
        }
        return switch (region) {
            case "DD" -> "DE";
            case "YD" -> "YE";
            case "AN", "530", "532" -> "CW";
            case "BU" -> "MM";
            case "CS", "YU", "890" -> "RS";
            case "TP" -> "TL";
            case "ZR" -> "CD";
            case "200" -> "CZ";
            case "536" -> "SA";
            case "582" -> "FM";
            case "736" -> "SD";
            case "554" -> "NZ";
            default -> region;
        };
    }

    private static String regionForScript(String script, String fallback) {
        return switch (script) {
            case "Armn" -> "AM";
            case "Geor" -> "GE";
            default -> fallback;
        };
    }

    static String signLanguage(String value) {
        return switch (value) {
            case "sgn-gr" -> "gss";
            case "sgn-be-fr" -> "sfb";
            case "sgn-be-nl" -> "vgt";
            case "sgn-ch-de" -> "sgg";
            default -> null;
        };
    }

    static String unicodeValue(String key, String value) {
        return switch (key) {
            case "ca" -> calendar(value);
            case "ks" -> switch (value) {
                case "primary" -> "level1";
                case "secondary" -> "level2";
                case "tertiary" -> "level3";
                case "quaternary" -> "level4";
                case "identical" -> "level5";
                default -> value;
            };
            case "ms" -> value.equals("imperial") ? "uksystem" : value;
            case "sd", "rg" -> switch (value) {
                case "no23" -> "no50";
                case "cn11" -> "cnbj";
                case "cz10a" -> "cz110";
                case "fra", "frg" -> "frges";
                case "lud" -> "lucl";
                default -> value;
            };
            case "tz" -> switch (value) {
                case "cnckg" -> "cnsha";
                case "eire" -> "iedub";
                case "est" -> "papty";
                case "gmt0" -> "gmt";
                case "uct", "zulu" -> "utc";
                default -> value;
            };
            case "kb", "kc", "kh", "kk", "kn" -> value.equals("yes") || value.equals("true") ? "" : value;
            default -> value;
        };
    }

    static String variantLanguage(String value) {
        return switch (value) {
            case "arevela" -> "hy";
            case "arevmda" -> "hyw";
            case "lojban" -> "jbo";
            case "gaulish" -> "xtg";
            case "guoyu" -> "zh";
            case "hakka" -> "hak";
            case "xiang" -> "hsn";
            default -> null;
        };
    }
}
