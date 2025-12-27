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

import java.text.Normalizer;

/**
 * Unicode normalization algorithms (NFC, NFD, NFKC, NFKD).
 * Based on QuickJS libunicode.c implementation.
 * Uses Java's built-in Normalizer for Unicode normalization.
 */
public final class UnicodeNormalization {

    /**
     * Unicode normalization forms.
     */
    public enum Form {
        /**
         * Canonical Decomposition, followed by Canonical Composition.
         */
        NFC,

        /**
         * Canonical Decomposition.
         */
        NFD,

        /**
         * Compatibility Decomposition, followed by Canonical Composition.
         */
        NFKC,

        /**
         * Compatibility Decomposition.
         */
        NFKD
    }

    /**
     * Normalize a string using the specified normalization form.
     *
     * @param input The string to normalize
     * @param form  The normalization form to use
     * @return The normalized string
     */
    public static String normalize(String input, Form form) {
        if (input == null) {
            return null;
        }

        if (input.isEmpty()) {
            return input;
        }

        Normalizer.Form javaForm = switch (form) {
            case NFC -> Normalizer.Form.NFC;
            case NFD -> Normalizer.Form.NFD;
            case NFKC -> Normalizer.Form.NFKC;
            case NFKD -> Normalizer.Form.NFKD;
        };

        return Normalizer.normalize(input, javaForm);
    }

    /**
     * Normalize to NFC (Canonical Composition).
     * This is the most common normalization form.
     */
    public static String toNFC(String input) {
        return normalize(input, Form.NFC);
    }

    /**
     * Normalize to NFD (Canonical Decomposition).
     */
    public static String toNFD(String input) {
        return normalize(input, Form.NFD);
    }

    /**
     * Normalize to NFKC (Compatibility Composition).
     */
    public static String toNFKC(String input) {
        return normalize(input, Form.NFKC);
    }

    /**
     * Normalize to NFKD (Compatibility Decomposition).
     */
    public static String toNFKD(String input) {
        return normalize(input, Form.NFKD);
    }

    /**
     * Check if a string is already normalized in the specified form.
     */
    public static boolean isNormalized(String input, Form form) {
        if (input == null || input.isEmpty()) {
            return true;
        }

        Normalizer.Form javaForm = switch (form) {
            case NFC -> Normalizer.Form.NFC;
            case NFD -> Normalizer.Form.NFD;
            case NFKC -> Normalizer.Form.NFKC;
            case NFKD -> Normalizer.Form.NFKD;
        };

        return Normalizer.isNormalized(input, javaForm);
    }

    /**
     * Normalize an array of code points.
     *
     * @param codePoints Array of Unicode code points
     * @param form       The normalization form
     * @return Array of normalized code points
     */
    public static int[] normalize(int[] codePoints, Form form) {
        if (codePoints == null || codePoints.length == 0) {
            return codePoints;
        }

        // Convert code points to String
        String str = new String(codePoints, 0, codePoints.length);

        // Normalize
        String normalized = normalize(str, form);

        // Convert back to code points
        return normalized.codePoints().toArray();
    }
}
