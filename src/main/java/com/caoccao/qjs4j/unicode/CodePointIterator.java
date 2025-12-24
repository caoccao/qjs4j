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

package com.caoccao.qjs4j.unicode;

/**
 * Iterator for Unicode code points in a string.
 */
public final class CodePointIterator {
    private final String string;
    private int index;

    public CodePointIterator(String string) {
        this.string = string;
        this.index = 0;
    }

    public boolean hasNext() {
        return index < string.length();
    }

    public int next() {
        int codePoint = string.codePointAt(index);
        index += Character.charCount(codePoint);
        return codePoint;
    }

    public int peek() {
        return string.codePointAt(index);
    }

    public void reset() {
        index = 0;
    }
}
