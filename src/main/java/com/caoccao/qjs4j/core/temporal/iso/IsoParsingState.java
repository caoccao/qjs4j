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

package com.caoccao.qjs4j.core.temporal.iso;

import com.caoccao.qjs4j.core.JSContext;

/** Cursor and digit productions shared by the ISO date/time and duration grammars. */
abstract class IsoParsingState {
    private static final int[] FRACTION_SCALE = {100_000_000, 10_000_000, 1_000_000, 100_000, 10_000, 1000, 100, 10, 1};

    final JSContext context;
    final String input;
    int position;

    IsoParsingState(JSContext context, String input) {
        this.context = context;
        this.input = input;
    }

    final boolean at(char ch) {
        return position < input.length() && input.charAt(position) == ch;
    }

    final boolean atDigit() {
        return position < input.length() && isAsciiDigit(input.charAt(position));
    }

    final int readFraction(String missing, String tooLong) {
        int start = position;
        position = fractionEnd(input, start);
        return fractionNanoseconds(context, input, start, position, missing, tooLong);
    }

    final boolean take(char ch) {
        if (!at(ch)) {
            return false;
        }
        position++;
        return true;
    }

    static int fractionEnd(String text, int start) {
        int end = start;
        int limit = start + Math.min(text.length() - start, 10);
        while (end < limit && isAsciiDigit(text.charAt(end))) {
            end++;
        }
        return end;
    }

    static int fractionNanoseconds(JSContext context, String text, int start, int end, String missing, String tooLong) {
        int count = end - start;
        if (count == 0 || count > 9) {
            context.throwRangeError(count == 0 ? missing : tooLong);
            return -1;
        }
        int fraction = 0;
        for (int i = start; i < end; i++) {
            fraction = fraction * 10 + (text.charAt(i) - '0');
        }
        return fraction * FRACTION_SCALE[count - 1];
    }

    static boolean hasTwoDigits(String value, int index) {
        return index + 2 <= value.length() && isAsciiDigit(value.charAt(index))
                && isAsciiDigit(value.charAt(index + 1));
    }

    static boolean isAsciiDigit(char character) {
        return character >= '0' && character <= '9';
    }

    static int parseFixedTwoDigits(String value, int index) {
        return (value.charAt(index) - '0') * 10 + (value.charAt(index + 1) - '0');
    }
}
