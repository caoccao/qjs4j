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

package com.caoccao.qjs4j.utils;

/** Decodes a single ASCII hexadecimal digit, including in code-point input. */
public final class AsciiHex {
    private static final byte[] HEX_VALUES = hexValues();

    private AsciiHex() {
    }

    private static byte[] hexValues() {
        byte[] values = new byte[128];
        java.util.Arrays.fill(values, (byte) -1);
        for (int i = 0; i < 16; i++) {
            if (i < 10) {
                values['0' + i] = (byte) i;
            } else {
                values['a' + i - 10] = (byte) i;
                values['A' + i - 10] = (byte) i;
            }
        }
        return values;
    }

    public static int value(int ch) {
        return ch >= 0 && ch < HEX_VALUES.length ? HEX_VALUES[ch] : -1;
    }
}
