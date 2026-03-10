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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.core.*;

import java.util.Arrays;

/**
 * Implements Uint8Array base64 and hex encoding/decoding methods.
 * <p>
 * Static methods: fromBase64, fromHex
 * Prototype methods: toBase64, toHex, setFromBase64, setFromHex
 */
public final class Uint8ArrayBase64Hex {
    private static final String BASE64URL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    private static final int[] BASE64URL_DECODE = new int[128];
    private static final String BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private static final int[] BASE64_DECODE = new int[128];

    static {
        Arrays.fill(BASE64_DECODE, -1);
        Arrays.fill(BASE64URL_DECODE, -1);
        for (int i = 0; i < BASE64_CHARS.length(); i++) {
            BASE64_DECODE[BASE64_CHARS.charAt(i)] = i;
        }
        for (int i = 0; i < BASE64URL_CHARS.length(); i++) {
            BASE64URL_DECODE[BASE64URL_CHARS.charAt(i)] = i;
        }
    }

    private Uint8ArrayBase64Hex() {
    }

    private static int base64CharValue(char ch, int[] decodeTable) {
        if (ch >= 128) {
            return -1;
        }
        return decodeTable[ch];
    }

    private static byte[] copyBytes(byte[] source, int length) {
        byte[] result = new byte[length];
        System.arraycopy(source, 0, result, 0, length);
        return result;
    }

    /**
     * Uint8Array.fromBase64(string [, options])
     */
    public static JSValue fromBase64(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 1 || !(args[0] instanceof JSString inputStr)) {
            return context.throwTypeError("input argument must be a string");
        }

        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        String alphabet = getAlphabetOption(context, options);
        if (alphabet == null) {
            return context.getPendingException();
        }

        String lastChunkHandling = getLastChunkHandlingOption(context, options);
        if (lastChunkHandling == null) {
            return context.getPendingException();
        }
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        int[] decodeTable = "base64url".equals(alphabet) ? BASE64URL_DECODE : BASE64_DECODE;
        DecodeResult result = fromBase64Core(inputStr.value(), decodeTable, lastChunkHandling, Integer.MAX_VALUE);
        if (result.error != null) {
            return context.throwSyntaxError(result.error);
        }

        JSUint8Array array = context.createJSUint8Array(result.bytes.length);
        for (int i = 0; i < result.bytes.length; i++) {
            array.setElement(i, result.bytes[i] & 0xFF);
        }
        return array;
    }

    /**
     * Core base64 decoding algorithm (FromBase64 in the spec).
     * Returns a DecodeResult with read position, decoded bytes, and optional error message.
     * The error is returned separately to allow callers to write partial data before throwing.
     *
     * @param input             the base64 string
     * @param decodeTable       the decode lookup table for the chosen alphabet
     * @param lastChunkHandling the lastChunkHandling option
     * @param maxLength         maximum number of bytes to decode (Integer.MAX_VALUE for unlimited)
     * @return a DecodeResult with read, bytes, and optional error
     */
    private static DecodeResult fromBase64Core(String input, int[] decodeTable,
                                               String lastChunkHandling, int maxLength) {
        if (maxLength == 0) {
            return new DecodeResult(0, new byte[0], null);
        }

        int initialCapacity = maxLength == Integer.MAX_VALUE ? ((input.length() / 4 + 1) * 3) : maxLength;
        byte[] output = new byte[initialCapacity];
        int outputIndex = 0;
        int chunkBuffer = 0;
        int chunkLength = 0;
        int read = 0;
        int index = 0;
        int length = input.length();

        while (index < length) {
            char ch = input.charAt(index);
            index++;

            if (isAsciiWhitespace(ch)) {
                continue;
            }

            if (ch == '=') {
                // Padding encountered
                if (chunkLength < 2) {
                    return new DecodeResult(read, copyBytes(output, outputIndex), "invalid base64 string");
                }

                index = skipAsciiWhitespace(input, index);

                if (chunkLength == 2) {
                    // Need second '='
                    if (index >= length) {
                        // Single '=' at end with 2 data chars - partial padding
                        if ("stop-before-partial".equals(lastChunkHandling)) {
                            return new DecodeResult(read, copyBytes(output, outputIndex), null);
                        }
                        return new DecodeResult(read, copyBytes(output, outputIndex), "invalid base64 string");
                    }
                    if (input.charAt(index) != '=') {
                        return new DecodeResult(read, copyBytes(output, outputIndex), "invalid base64 string");
                    }
                    index++;
                    index = skipAsciiWhitespace(input, index);
                }

                // After padding: no more non-whitespace chars allowed
                if (index < length) {
                    return new DecodeResult(read, copyBytes(output, outputIndex), "invalid base64 string");
                }

                // Check if all decoded bytes from padded chunk would fit within maxLength.
                // Same all-or-nothing semantics as complete chunks: if not all bytes fit,
                // don't consume the padded chunk at all.
                int paddedByteCount = (chunkLength == 2) ? 1 : 2;
                if (outputIndex + paddedByteCount > maxLength) {
                    return new DecodeResult(read, copyBytes(output, outputIndex), null);
                }

                // Decode the padded chunk
                if (chunkLength == 2) {
                    // 2 data chars + == → 1 byte
                    if ("strict".equals(lastChunkHandling) && (chunkBuffer & 0xF) != 0) {
                        return new DecodeResult(read, copyBytes(output, outputIndex), "invalid base64 string");
                    }
                    output[outputIndex++] = (byte) ((chunkBuffer >> 4) & 0xFF);
                } else {
                    // 3 data chars + = → 2 bytes
                    if ("strict".equals(lastChunkHandling) && (chunkBuffer & 0x3) != 0) {
                        return new DecodeResult(read, copyBytes(output, outputIndex), "invalid base64 string");
                    }
                    output[outputIndex++] = (byte) ((chunkBuffer >> 10) & 0xFF);
                    output[outputIndex++] = (byte) ((chunkBuffer >> 2) & 0xFF);
                }
                return new DecodeResult(length, copyBytes(output, outputIndex), null);
            }

            // Regular base64 character
            int charValue = base64CharValue(ch, decodeTable);
            if (charValue == -1) {
                return new DecodeResult(read, copyBytes(output, outputIndex), "invalid base64 string");
            }

            chunkBuffer = (chunkBuffer << 6) | charValue;
            chunkLength++;

            if (chunkLength == 4) {
                // Decode complete chunk → 3 bytes
                int byte1 = (chunkBuffer >> 16) & 0xFF;
                int byte2 = (chunkBuffer >> 8) & 0xFF;
                int byte3 = chunkBuffer & 0xFF;

                if (outputIndex + 3 > maxLength) {
                    // Can't fit all 3 bytes, stop before this chunk
                    return new DecodeResult(read, copyBytes(output, outputIndex), null);
                }

                output[outputIndex++] = (byte) byte1;
                output[outputIndex++] = (byte) byte2;
                output[outputIndex++] = (byte) byte3;
                chunkBuffer = 0;
                chunkLength = 0;
                read = index;

                if (outputIndex >= maxLength) {
                    return new DecodeResult(read, copyBytes(output, outputIndex), null);
                }
            }
        }

        // End of input - handle remaining partial chunk
        if (chunkLength > 0) {
            if (chunkLength == 1) {
                // Single char is always invalid (not enough data for even 1 byte)
                if ("stop-before-partial".equals(lastChunkHandling)) {
                    return new DecodeResult(read, copyBytes(output, outputIndex), null);
                }
                return new DecodeResult(
                        read,
                        copyBytes(output, outputIndex),
                        "The base64 input terminates with a single character, excluding padding (=).");
            }

            if ("stop-before-partial".equals(lastChunkHandling)) {
                return new DecodeResult(read, copyBytes(output, outputIndex), null);
            }

            if ("strict".equals(lastChunkHandling)) {
                return new DecodeResult(read, copyBytes(output, outputIndex), "invalid base64 string");
            }

            // "loose" mode: decode partial chunk
            if (chunkLength == 2) {
                if (outputIndex < maxLength) {
                    output[outputIndex++] = (byte) ((chunkBuffer >> 4) & 0xFF);
                }
            } else if (chunkLength == 3) {
                if (outputIndex + 2 <= maxLength) {
                    output[outputIndex++] = (byte) ((chunkBuffer >> 10) & 0xFF);
                    output[outputIndex++] = (byte) ((chunkBuffer >> 2) & 0xFF);
                } else if (outputIndex + 1 <= maxLength) {
                    output[outputIndex++] = (byte) ((chunkBuffer >> 10) & 0xFF);
                }
            }
            read = length;
        } else {
            read = length;
        }

        return new DecodeResult(read, copyBytes(output, outputIndex), null);
    }

    /**
     * Uint8Array.fromHex(string)
     */
    public static JSValue fromHex(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length < 1 || !(args[0] instanceof JSString inputStr)) {
            return context.throwTypeError("input argument must be a string");
        }

        DecodeResult result = fromHexCore(inputStr.value(), Integer.MAX_VALUE);
        if (result.error != null) {
            return context.throwSyntaxError(result.error);
        }

        JSUint8Array array = context.createJSUint8Array(result.bytes.length);
        for (int i = 0; i < result.bytes.length; i++) {
            array.setElement(i, result.bytes[i] & 0xFF);
        }
        return array;
    }

    /**
     * Core hex decoding algorithm (FromHex in the spec).
     * Returns a DecodeResult with read position, decoded bytes, and optional error message.
     *
     * @param input     the hex string
     * @param maxLength maximum number of bytes to decode
     * @return a DecodeResult with read, bytes, and optional error
     */
    private static DecodeResult fromHexCore(String input, int maxLength) {
        int length = input.length();

        if (length % 2 != 0) {
            return new DecodeResult(0, new byte[0], "Input string must contain hex characters in even length");
        }

        byte[] output = new byte[Math.min(length / 2, maxLength)];
        int written = 0;
        int index = 0;

        while (index + 1 < length && written < maxLength) {
            int high = hexCharValue(input.charAt(index));
            int low = hexCharValue(input.charAt(index + 1));
            if (high == -1 || low == -1) {
                return new DecodeResult(index, copyBytes(output, written), "Input string must contain hex characters in even length");
            }
            output[written++] = (byte) ((high << 4) | low);
            index += 2;
        }

        return new DecodeResult(index, copyBytes(output, written), null);
    }

    /**
     * Parse the "alphabet" option from the options argument.
     * Returns "base64" or "base64url", or null if an error was thrown.
     */
    private static String getAlphabetOption(JSContext context, JSValue options) {
        if (options instanceof JSUndefined || options == null) {
            return "base64";
        }
        JSObject optionsObj = JSTypeConversions.toObject(context, options);
        if (context.hasPendingException() || optionsObj == null) {
            return null;
        }
        JSValue alphabetValue = optionsObj.get(PropertyKey.fromString("alphabet"));
        if (context.hasPendingException()) {
            return null;
        }
        if (alphabetValue instanceof JSUndefined) {
            return "base64";
        }
        if (!(alphabetValue instanceof JSString alphabetString)) {
            context.throwTypeError("invalid option " + alphabetValue.toJavaObject());
            return null;
        }
        String alphabet = alphabetString.value();
        if (!"base64".equals(alphabet) && !"base64url".equals(alphabet)) {
            context.throwTypeError("invalid option " + alphabet);
            return null;
        }
        return alphabet;
    }

    /**
     * Parse the "lastChunkHandling" option from the options argument.
     * Returns "loose", "strict", or "stop-before-partial", or null if an error was thrown.
     */
    private static String getLastChunkHandlingOption(JSContext context, JSValue options) {
        if (options instanceof JSUndefined || options == null) {
            return "loose";
        }
        JSObject optionsObj = JSTypeConversions.toObject(context, options);
        if (context.hasPendingException() || optionsObj == null) {
            return null;
        }
        JSValue lastChunkValue = optionsObj.get(PropertyKey.fromString("lastChunkHandling"));
        if (context.hasPendingException()) {
            return null;
        }
        if (lastChunkValue instanceof JSUndefined) {
            return "loose";
        }
        if (!(lastChunkValue instanceof JSString lastChunkString)) {
            context.throwTypeError("invalid option " + lastChunkValue.toJavaObject());
            return null;
        }
        String lastChunkHandling = lastChunkString.value();
        if (!"loose".equals(lastChunkHandling) && !"strict".equals(lastChunkHandling) && !"stop-before-partial".equals(lastChunkHandling)) {
            context.throwTypeError("invalid option " + lastChunkHandling);
            return null;
        }
        return lastChunkHandling;
    }

    /**
     * Parse the "omitPadding" option from the options argument.
     */
    private static boolean getOmitPaddingOption(JSContext context, JSValue options) {
        if (options instanceof JSUndefined || options == null) {
            return false;
        }
        JSObject optionsObj = JSTypeConversions.toObject(context, options);
        if (context.hasPendingException() || optionsObj == null) {
            return false;
        }
        JSValue omitPaddingValue = optionsObj.get(PropertyKey.fromString("omitPadding"));
        if (context.hasPendingException()) {
            return false;
        }
        return JSTypeConversions.toBoolean(omitPaddingValue).isBooleanTrue();
    }

    private static int hexCharValue(char ch) {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        }
        if (ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 10;
        }
        if (ch >= 'A' && ch <= 'F') {
            return ch - 'A' + 10;
        }
        return -1;
    }

    // ==================== Static Methods ====================

    private static boolean isAsciiWhitespace(char ch) {
        return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\f' || ch == '\r';
    }

    /**
     * Uint8Array.prototype.setFromBase64(string [, options])
     */
    public static JSValue setFromBase64(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSUint8Array uint8Array)) {
            return context.throwTypeError("setFromBase64 called on non-Uint8Array");
        }

        if (args.length < 1 || !(args[0] instanceof JSString inputStr)) {
            return context.throwTypeError("expected first argument to be a string");
        }

        JSValue options = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        String alphabet = getAlphabetOption(context, options);
        if (alphabet == null) {
            return context.getPendingException();
        }

        String lastChunkHandling = getLastChunkHandlingOption(context, options);
        if (lastChunkHandling == null) {
            return context.getPendingException();
        }
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Check for detached buffer AFTER reading options (per spec)
        if (uint8Array.isOutOfBounds()) {
            return context.throwTypeError("cannot perform setFromBase64 on a detached or out-of-bounds buffer");
        }

        int[] decodeTable = "base64url".equals(alphabet) ? BASE64URL_DECODE : BASE64_DECODE;
        int maxLength = uint8Array.getLength();
        DecodeResult result = fromBase64Core(inputStr.value(), decodeTable, lastChunkHandling, maxLength);

        // Write decoded bytes to the array (even if there's an error, partial data is written)
        for (int i = 0; i < result.bytes.length; i++) {
            uint8Array.setElement(i, result.bytes[i] & 0xFF);
        }

        // Throw error after writing partial data
        if (result.error != null) {
            return context.throwSyntaxError(result.error);
        }

        // Return { read, written }
        JSObject resultObj = context.createJSObject();
        resultObj.set(PropertyKey.fromString("read"), JSNumber.of(result.read));
        resultObj.set(PropertyKey.fromString("written"), JSNumber.of(result.bytes.length));
        return resultObj;
    }

    // ==================== Prototype Methods ====================

    /**
     * Uint8Array.prototype.setFromHex(string)
     */
    public static JSValue setFromHex(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSUint8Array uint8Array)) {
            return context.throwTypeError("setFromHex called on non-Uint8Array");
        }
        if (uint8Array.isOutOfBounds()) {
            return context.throwTypeError("cannot perform setFromHex on a detached or out-of-bounds buffer");
        }

        if (args.length < 1 || !(args[0] instanceof JSString inputStr)) {
            return context.throwTypeError("expected first argument to be a string");
        }

        int maxLength = uint8Array.getLength();
        DecodeResult result = fromHexCore(inputStr.value(), maxLength);

        // Write decoded bytes to the array (even if there's an error, partial data is written)
        for (int i = 0; i < result.bytes.length; i++) {
            uint8Array.setElement(i, result.bytes[i] & 0xFF);
        }

        // Throw error after writing partial data
        if (result.error != null) {
            return context.throwSyntaxError(result.error);
        }

        // Return { read, written }
        JSObject resultObj = context.createJSObject();
        resultObj.set(PropertyKey.fromString("read"), JSNumber.of(result.read));
        resultObj.set(PropertyKey.fromString("written"), JSNumber.of(result.bytes.length));
        return resultObj;
    }

    /**
     * Skip ASCII whitespace starting from position index.
     * Returns the index of the first non-whitespace character, or length if end of string.
     */
    private static int skipAsciiWhitespace(String input, int index) {
        while (index < input.length() && isAsciiWhitespace(input.charAt(index))) {
            index++;
        }
        return index;
    }

    /**
     * Uint8Array.prototype.toBase64([options])
     */
    public static JSValue toBase64(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSUint8Array uint8Array)) {
            return context.throwTypeError("toBase64 called on non-Uint8Array");
        }

        JSValue options = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        String alphabet = getAlphabetOption(context, options);
        if (alphabet == null) {
            return context.getPendingException();
        }
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        boolean omitPadding = getOmitPaddingOption(context, options);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Check for detached buffer AFTER reading options (per spec)
        if (uint8Array.isOutOfBounds()) {
            return context.throwTypeError("cannot perform toBase64 on a detached or out-of-bounds buffer");
        }

        String chars = "base64url".equals(alphabet) ? BASE64URL_CHARS : BASE64_CHARS;
        int length = uint8Array.getLength();
        StringBuilder sb = new StringBuilder();

        int i = 0;
        while (i + 2 < length) {
            int byte1 = (int) uint8Array.getElement(i) & 0xFF;
            int byte2 = (int) uint8Array.getElement(i + 1) & 0xFF;
            int byte3 = (int) uint8Array.getElement(i + 2) & 0xFF;
            sb.append(chars.charAt(byte1 >> 2));
            sb.append(chars.charAt(((byte1 & 0x3) << 4) | (byte2 >> 4)));
            sb.append(chars.charAt(((byte2 & 0xF) << 2) | (byte3 >> 6)));
            sb.append(chars.charAt(byte3 & 0x3F));
            i += 3;
        }

        int remaining = length - i;
        if (remaining == 1) {
            int byte1 = (int) uint8Array.getElement(i) & 0xFF;
            sb.append(chars.charAt(byte1 >> 2));
            sb.append(chars.charAt((byte1 & 0x3) << 4));
            if (!omitPadding) {
                sb.append("==");
            }
        } else if (remaining == 2) {
            int byte1 = (int) uint8Array.getElement(i) & 0xFF;
            int byte2 = (int) uint8Array.getElement(i + 1) & 0xFF;
            sb.append(chars.charAt(byte1 >> 2));
            sb.append(chars.charAt(((byte1 & 0x3) << 4) | (byte2 >> 4)));
            sb.append(chars.charAt((byte2 & 0xF) << 2));
            if (!omitPadding) {
                sb.append('=');
            }
        }

        return new JSString(sb.toString());
    }

    /**
     * Uint8Array.prototype.toHex()
     */
    public static JSValue toHex(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSUint8Array uint8Array)) {
            return context.throwTypeError("toHex called on non-Uint8Array");
        }
        if (uint8Array.isOutOfBounds()) {
            return context.throwTypeError("cannot perform toHex on a detached or out-of-bounds buffer");
        }

        int length = uint8Array.getLength();
        StringBuilder sb = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            int byteValue = (int) uint8Array.getElement(i) & 0xFF;
            sb.append(Character.forDigit((byteValue >> 4) & 0xF, 16));
            sb.append(Character.forDigit(byteValue & 0xF, 16));
        }
        return new JSString(sb.toString());
    }

    /**
     * Result of base64/hex decoding.
     * The error field is non-null when a SyntaxError should be thrown.
     * Bytes may still contain valid partial data even when error is set.
     */
    private record DecodeResult(int read, byte[] bytes, String error) {
    }
}
