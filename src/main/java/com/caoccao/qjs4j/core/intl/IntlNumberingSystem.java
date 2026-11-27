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

import java.util.Map;

/** Digit substitution shared by the Intl formatters. */
final class IntlNumberingSystem {
    private static final Map<String, Integer> CONTIGUOUS_ZERO_CODE_POINTS = Map.ofEntries(Map.entry("adlm", 0x1E950),
            Map.entry("ahom", 0x11730), Map.entry("arab", 0x660), Map.entry("arabext", 0x6F0),
            Map.entry("bali", 0x1B50), Map.entry("beng", 0x9E6), Map.entry("bhks", 0x11C50), Map.entry("brah", 0x11066),
            Map.entry("cakm", 0x11136), Map.entry("cham", 0xAA50), Map.entry("deva", 0x966), Map.entry("diak", 0x11950),
            Map.entry("fullwide", 0xFF10), Map.entry("gara", 0x10D40), Map.entry("gong", 0x11DA0),
            Map.entry("gonm", 0x11D50), Map.entry("gujr", 0xAE6), Map.entry("gukh", 0x16130), Map.entry("guru", 0xA66),
            Map.entry("hmng", 0x16B50), Map.entry("hmnp", 0x1E140), Map.entry("java", 0xA9D0),
            Map.entry("kali", 0xA900), Map.entry("kawi", 0x11F50), Map.entry("khmr", 0x17E0), Map.entry("knda", 0xCE6),
            Map.entry("krai", 0x16D70), Map.entry("lana", 0x1A80), Map.entry("lanatham", 0x1A90),
            Map.entry("laoo", 0xED0), Map.entry("latn", 0x30), Map.entry("lepc", 0x1C40), Map.entry("limb", 0x1946),
            Map.entry("nagm", 0x1E4F0), Map.entry("mathbold", 0x1D7CE), Map.entry("mathdbl", 0x1D7D8),
            Map.entry("mathmono", 0x1D7F6), Map.entry("mathsanb", 0x1D7EC), Map.entry("mathsans", 0x1D7E2),
            Map.entry("mlym", 0xD66), Map.entry("modi", 0x11650), Map.entry("mong", 0x1810), Map.entry("mroo", 0x16A60),
            Map.entry("mtei", 0xABF0), Map.entry("mymr", 0x1040), Map.entry("mymrepka", 0x116DA),
            Map.entry("mymrpao", 0x116D0), Map.entry("mymrshan", 0x1090), Map.entry("mymrtlng", 0xA9F0),
            Map.entry("newa", 0x11450), Map.entry("nkoo", 0x7C0), Map.entry("olck", 0x1C50), Map.entry("onao", 0x1E5F1),
            Map.entry("orya", 0xB66), Map.entry("osma", 0x104A0), Map.entry("outlined", 0x1CCF0),
            Map.entry("rohg", 0x10D30), Map.entry("saur", 0xA8D0), Map.entry("segment", 0x1FBF0),
            Map.entry("shrd", 0x111D0), Map.entry("sind", 0x112F0), Map.entry("sinh", 0xDE6),
            Map.entry("sora", 0x110F0), Map.entry("sund", 0x1BB0), Map.entry("sunu", 0x11BF0),
            Map.entry("takr", 0x116C0), Map.entry("talu", 0x19D0), Map.entry("tamldec", 0xBE6),
            Map.entry("tnsa", 0x16AC0), Map.entry("tols", 0x11DE0), Map.entry("telu", 0xC66), Map.entry("thai", 0xE50),
            Map.entry("tibt", 0xF20), Map.entry("tirh", 0x114D0), Map.entry("vaii", 0xA620), Map.entry("wara", 0x118E0),
            Map.entry("wcho", 0x1E2F0));
    private static final String HANIDEC = "〇一二三四五六七八九";

    private IntlNumberingSystem() {
    }

    static String localize(String text, String system, boolean arabicDecimal) {
        if (system == null || "latn".equals(system)) {
            return text;
        }
        boolean hanidec = "hanidec".equals(system);
        Integer zero = CONTIGUOUS_ZERO_CODE_POINTS.get(system);
        if (zero == null && !hanidec) {
            return text;
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                int digit = c - '0';
                builder.appendCodePoint(hanidec ? HANIDEC.charAt(digit) : zero + digit);
            } else {
                builder.append(arabicDecimal && c == '.' ? '٫' : c);
            }
        }
        return builder.toString();
    }
}
