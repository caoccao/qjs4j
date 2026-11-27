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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** CLDR likely subtags used by Intl.Locale maximize and minimize. */
final class IntlLikelySubtags {
    private static final Map<String, Locale> LIKELY = new HashMap<>();

    static {
        // The lookup key for a default is the language of its maximized tag.
        for (String tag : new String[]{"aa-Latn-ET", "aae-Latn-IT", "af-Latn-ZA", "am-Ethi-ET", "ar-Arab-EG",
                "as-Beng-IN", "az-Latn-AZ", "be-Cyrl-BY", "bg-Cyrl-BG", "bn-Beng-BD", "bo-Tibt-CN", "bs-Latn-BA",
                "ca-Latn-ES", "cs-Latn-CZ", "cy-Latn-GB", "da-Latn-DK", "de-Latn-DE", "el-Grek-GR", "en-Latn-US",
                "es-Latn-ES", "et-Latn-EE", "eu-Latn-ES", "fa-Arab-IR", "fi-Latn-FI", "fil-Latn-PH", "fr-Latn-FR",
                "ga-Latn-IE", "gl-Latn-ES", "gu-Gujr-IN", "ha-Latn-NG", "he-Hebr-IL", "hi-Deva-IN", "hr-Latn-HR",
                "hu-Latn-HU", "hy-Armn-AM", "hyw-Armn-AM", "id-Latn-ID", "is-Latn-IS", "it-Latn-IT", "ja-Jpan-JP",
                "jv-Latn-ID", "ka-Geor-GE", "kk-Cyrl-KZ", "km-Khmr-KH", "kn-Knda-IN", "ko-Kore-KR", "ky-Cyrl-KG",
                "lo-Laoo-LA", "lt-Latn-LT", "lv-Latn-LV", "mk-Cyrl-MK", "ml-Mlym-IN", "mn-Cyrl-MN", "mr-Deva-IN",
                "ms-Latn-MY", "my-Mymr-MM", "nb-Latn-NO", "ne-Deva-NP", "nl-Latn-NL", "nn-Latn-NO", "no-Latn-NO",
                "or-Orya-IN", "pa-Guru-IN", "pap-Latn-CW", "pl-Latn-PL", "ps-Arab-AF", "pt-Latn-BR", "ro-Latn-RO",
                "ru-Cyrl-RU", "sd-Arab-PK", "si-Sinh-LK", "sk-Latn-SK", "sl-Latn-SI", "so-Latn-SO", "sq-Latn-AL",
                "sr-Cyrl-RS", "sv-Latn-SE", "sw-Latn-TZ", "ta-Taml-IN", "te-Telu-IN", "th-Thai-TH", "tk-Latn-TM",
                "tr-Latn-TR", "uk-Cyrl-UA", "ur-Arab-PK", "uz-Latn-UZ", "vi-Latn-VN", "yo-Latn-NG", "zh-Hans-CN",
                "zu-Latn-ZA", "jbo-Latn-001", "hak-Hans-CN", "hsn-Hans-CN"}) {
            Locale locale = Locale.forLanguageTag(tag);
            LIKELY.put(locale.getLanguage(), locale);
        }
        // More specific keys and undetermined languages may share a maximized locale.
        add("sr-Latn-RS", "sr-Latn");
        add("en-Latn-US", "und");
        add("zh-Hant-TW", "zh-Hant", "zh-TW");
        add("zh-Hant-HK", "zh-HK");
        add("en-Shaw-GB", "en-Shaw");
        add("th-Thai-TH", "und-Thai");
        add("es-Latn-419", "und-419");
        add("en-Latn-150", "und-150");
        add("de-Latn-AT", "und-AT");
        add("en-Latn-AQ", "und-AQ");
        add("pap-Latn-CW", "und-CW");
        add("bg-Cyrl-RO", "und-Cyrl-RO");
    }

    private IntlLikelySubtags() {
    }

    private static void add(String tag, String... keys) {
        Locale locale = Locale.forLanguageTag(tag);
        for (String key : keys) {
            LIKELY.put(key, locale);
        }
    }

    private static Locale languageId(String language, String script, String region, String variant) {
        return new Locale.Builder().setLanguage(language).setScript(script).setRegion(region).setVariant(variant)
                .build();
    }

    static Locale maximize(Locale locale) {
        String language = locale.getLanguage();
        String script = locale.getScript();
        String region = locale.getCountry();
        String key = language.isEmpty() ? "und" : language;
        Locale full = null;
        if (!script.isEmpty() && !region.isEmpty()) {
            if (!language.isEmpty()) {
                return locale;
            }
            full = LIKELY.get(key + "-" + script + "-" + region);
        }
        if (full == null && !script.isEmpty()) {
            full = LIKELY.get(key + "-" + script);
        }
        if (full == null && !region.isEmpty()) {
            full = LIKELY.get(key + "-" + region);
        }
        if (full == null) {
            full = LIKELY.get(key);
        }
        if (full == null) {
            return locale;
        }
        return languageId(full.getLanguage(), script.isEmpty() ? full.getScript() : script,
                region.isEmpty() ? full.getCountry() : region, locale.getVariant());
    }

    static Locale minimize(Locale locale) {
        Locale maximized = maximize(locale);
        String maxLang = maximized.getLanguage();
        String maxScript = maximized.getScript();
        String maxRegion = maximized.getCountry();
        String variant = locale.getVariant();

        // Try language only
        Locale langOnly = languageId(maxLang, "", "", "");
        Locale langOnlyMax = maximize(langOnly);
        if (langOnlyMax.getScript().equals(maxScript) && langOnlyMax.getCountry().equals(maxRegion)) {
            return languageId(maxLang, "", "", variant);
        }

        // Try language-region
        Locale langRegion = languageId(maxLang, "", maxRegion, "");
        Locale langRegionMax = maximize(langRegion);
        if (langRegionMax.getScript().equals(maxScript)) {
            return languageId(maxLang, "", maxRegion, variant);
        }

        // Try language-script
        Locale langScript = languageId(maxLang, maxScript, "", "");
        Locale langScriptMax = maximize(langScript);
        if (langScriptMax.getCountry().equals(maxRegion)) {
            return languageId(maxLang, maxScript, "", variant);
        }

        // Can't minimize further, return maximized
        return languageId(maxLang, maxScript, maxRegion, variant);
    }
}
