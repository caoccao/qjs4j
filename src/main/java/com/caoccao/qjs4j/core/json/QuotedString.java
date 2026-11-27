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

package com.caoccao.qjs4j.core.json;

/** Character escapes shared by JSON serialization and console inspection. */
public final class QuotedString {
    private QuotedString() {
    }

    public static void appendQuotedChar(StringBuilder out, char ch) {
        switch (ch) {
            case '"' -> out.append("\\\"");
            case '\\' -> out.append("\\\\");
            case '\b' -> out.append("\\b");
            case '\f' -> out.append("\\f");
            case '\n' -> out.append("\\n");
            case '\r' -> out.append("\\r");
            case '\t' -> out.append("\\t");
            default -> {
                if (ch < 0x20) {
                    appendUnicodeEscape(out, ch);
                } else {
                    out.append(ch);
                }
            }
        }
    }

    public static void appendUnicodeEscape(StringBuilder out, char ch) {
        out.append("\\u");
        for (int shift = 12; shift >= 0; shift -= 4) {
            out.append(Character.forDigit(ch >>> shift & 15, 16));
        }
    }
}
