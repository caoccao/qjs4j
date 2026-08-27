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

import java.util.Arrays;

/**
 * One realm's RegExp legacy static state: {@code RegExp.input}, {@code RegExp.lastMatch},
 * {@code RegExp.lastParen}, {@code RegExp.leftContext}, {@code RegExp.rightContext} and
 * {@code RegExp.$1} through {@code RegExp.$9}.
 * <p>
 * A plain state holder with no reference back to the context: nothing here needs the realm, and
 * every write goes through {@link #update} after a successful match. {@link JSContext} keeps the
 * public accessors and delegates to this class, so {@code RegExpConstructor} and
 * {@code RegExpPrototype} are unaffected.
 */
final class RegExpLegacyStatics {
    /**
     * {@code RegExp.$1} through {@code RegExp.$9}, in that order.
     */
    private final String[] captures;
    private String input;
    private String lastMatch;
    private String lastParen;
    private String leftContext;
    private String rightContext;

    RegExpLegacyStatics() {
        captures = new String[9];
        input = "";
        lastMatch = "";
        lastParen = "";
        leftContext = "";
        rightContext = "";
        Arrays.fill(captures, "");
    }

    /**
     * The value of {@code RegExp.$n}.
     *
     * @param captureIndex the one-based capture index
     * @return the capture, or the empty string when there is none
     */
    String getCapture(int captureIndex) {
        if (captureIndex < 1 || captureIndex > captures.length) {
            return "";
        }
        String captureValue = captures[captureIndex - 1];
        if (captureValue == null) {
            return "";
        } else {
            return captureValue;
        }
    }

    String getInput() {
        return input;
    }

    String getLastMatch() {
        return lastMatch;
    }

    String getLastParen() {
        return lastParen;
    }

    String getLeftContext() {
        return leftContext;
    }

    String getRightContext() {
        return rightContext;
    }

    /**
     * Drop everything this holds.
     * <p>
     * Called from {@link JSContext#close()}: the last subject string can be arbitrarily large, and a
     * closed context must own nothing.
     */
    void release() {
        Arrays.fill(captures, "");
        input = null;
        lastMatch = null;
        lastParen = null;
        leftContext = null;
        rightContext = null;
    }

    void setInput(String inputValue) {
        if (inputValue == null) {
            input = "";
        } else {
            input = inputValue;
        }
    }

    /**
     * Record the result of a successful match.
     *
     * @param inputValue         the subject string
     * @param captureValues      the match and its captures, index 0 being the whole match
     * @param captureIndices     the start/end offsets of each capture, or null when unavailable
     * @param fallbackStartIndex where to start searching for the match when offsets are unavailable
     */
    void update(
            String inputValue,
            String[] captureValues,
            int[][] captureIndices,
            int fallbackStartIndex) {
        String normalizedInput = inputValue != null ? inputValue : "";
        input = normalizedInput;

        String matchedText = "";
        if (captureValues != null && captureValues.length > 0 && captureValues[0] != null) {
            matchedText = captureValues[0];
        }

        int inputLength = normalizedInput.length();
        int matchStart = 0;
        int matchEnd = 0;
        if (captureIndices != null
                && captureIndices.length > 0
                && captureIndices[0] != null
                && captureIndices[0].length >= 2) {
            matchStart = Math.max(0, Math.min(inputLength, captureIndices[0][0]));
            matchEnd = Math.max(matchStart, Math.min(inputLength, captureIndices[0][1]));
            if (matchedText.isEmpty() && matchEnd >= matchStart) {
                matchedText = normalizedInput.substring(matchStart, matchEnd);
            }
        } else if (!matchedText.isEmpty()) {
            int normalizedFallbackStart = Math.max(0, fallbackStartIndex);
            int foundIndex = normalizedInput.indexOf(matchedText, normalizedFallbackStart);
            if (foundIndex < 0) {
                foundIndex = normalizedInput.indexOf(matchedText);
            }
            if (foundIndex >= 0) {
                matchStart = foundIndex;
                matchEnd = Math.min(inputLength, foundIndex + matchedText.length());
            }
        }

        lastMatch = matchedText;
        leftContext = normalizedInput.substring(0, matchStart);
        rightContext = normalizedInput.substring(matchEnd);

        for (int captureIndex = 0; captureIndex < captures.length; captureIndex++) {
            String captureValue = "";
            int captureValueIndex = captureIndex + 1;
            if (captureValues != null
                    && captureValueIndex < captureValues.length
                    && captureValues[captureValueIndex] != null) {
                captureValue = captureValues[captureValueIndex];
            }
            captures[captureIndex] = captureValue;
        }

        String lastParenValue = "";
        if (captureValues != null && captureValues.length > 1) {
            for (int captureIndex = captureValues.length - 1; captureIndex >= 1; captureIndex--) {
                String captureValue = captureValues[captureIndex];
                if (captureValue != null) {
                    lastParenValue = captureValue;
                    break;
                }
            }
        }
        lastParen = lastParenValue;
    }
}
