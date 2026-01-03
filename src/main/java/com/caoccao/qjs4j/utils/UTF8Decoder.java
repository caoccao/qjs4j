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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * UTF-8 encoder/decoder utilities.
 * Based on QuickJS cutils.c implementation.
 */
public final class UTF8Decoder {

    public static final int UTF8_CHAR_LEN_MAX = 6;

    /**
     * Count the number of UTF-8 code points in a byte array.
     */
    public static int countCodePoints(byte[] bytes) {
        return countCodePoints(bytes, 0, bytes.length);
    }

    /**
     * Count the number of UTF-8 code points in part of a byte array.
     */
    public static int countCodePoints(byte[] bytes, int offset, int length) {
        int count = 0;
        int end = offset + length;

        while (offset < end) {
            int seqLen = getSequenceLength(bytes[offset]);
            if (seqLen <= 0) {
                offset++;
            } else {
                offset += seqLen;
                count++;
            }
        }

        return count;
    }

    /**
     * Decode UTF-8 bytes to a string.
     */
    public static String decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        return decode(bytes, 0, bytes.length);
    }

    /**
     * Decode part of a UTF-8 byte array to a string.
     */
    public static String decode(byte[] bytes, int offset, int length) {
        if (bytes == null || length == 0) {
            return "";
        }

        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);

        try {
            CharBuffer result = decoder.decode(ByteBuffer.wrap(bytes, offset, length));
            return result.toString();
        } catch (CharacterCodingException e) {
            // Fallback to simple String constructor
            return new String(bytes, offset, length, StandardCharsets.UTF_8);
        }
    }

    /**
     * Encode a string to UTF-8 bytes.
     */
    public static byte[] encode(String str) {
        if (str == null || str.isEmpty()) {
            return new byte[0];
        }
        return str.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Encode a Unicode code point to UTF-8.
     * Returns the number of bytes written, or -1 on error.
     */
    public static int encodeCodePoint(byte[] buf, int codePoint) {
        if (buf == null || codePoint < 0) {
            return -1;
        }

        // ASCII (1 byte)
        if (codePoint < 0x80) {
            if (buf.length < 1) return -1;
            buf[0] = (byte) codePoint;
            return 1;
        }

        // 2 bytes
        if (codePoint < 0x800) {
            if (buf.length < 2) return -1;
            buf[0] = (byte) (0xC0 | (codePoint >> 6));
            buf[1] = (byte) (0x80 | (codePoint & 0x3F));
            return 2;
        }

        // 3 bytes
        if (codePoint < 0x10000) {
            if (buf.length < 3) return -1;
            buf[0] = (byte) (0xE0 | (codePoint >> 12));
            buf[1] = (byte) (0x80 | ((codePoint >> 6) & 0x3F));
            buf[2] = (byte) (0x80 | (codePoint & 0x3F));
            return 3;
        }

        // 4 bytes
        if (codePoint < 0x110000) {
            if (buf.length < 4) return -1;
            buf[0] = (byte) (0xF0 | (codePoint >> 18));
            buf[1] = (byte) (0x80 | ((codePoint >> 12) & 0x3F));
            buf[2] = (byte) (0x80 | ((codePoint >> 6) & 0x3F));
            buf[3] = (byte) (0x80 | (codePoint & 0x3F));
            return 4;
        }

        return -1;
    }

    /**
     * Get a Unicode code point from UTF-8 bytes at the specified offset.
     * Returns -1 if invalid UTF-8 sequence.
     */
    public static int getCodePoint(byte[] bytes, int offset) {
        if (bytes == null || offset >= bytes.length) {
            return -1;
        }

        int b = bytes[offset] & 0xFF;

        // ASCII character (1 byte)
        if ((b & 0x80) == 0) {
            return b;
        }

        // 2-byte sequence
        if ((b & 0xE0) == 0xC0) {
            if (offset + 1 >= bytes.length) return -1;
            int b2 = bytes[offset + 1] & 0xFF;
            if ((b2 & 0xC0) != 0x80) return -1;
            return ((b & 0x1F) << 6) | (b2 & 0x3F);
        }

        // 3-byte sequence
        if ((b & 0xF0) == 0xE0) {
            if (offset + 2 >= bytes.length) return -1;
            int b2 = bytes[offset + 1] & 0xFF;
            int b3 = bytes[offset + 2] & 0xFF;
            if ((b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80) return -1;
            return ((b & 0x0F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F);
        }

        // 4-byte sequence
        if ((b & 0xF8) == 0xF0) {
            if (offset + 3 >= bytes.length) return -1;
            int b2 = bytes[offset + 1] & 0xFF;
            int b3 = bytes[offset + 2] & 0xFF;
            int b4 = bytes[offset + 3] & 0xFF;
            if ((b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80 || (b4 & 0xC0) != 0x80) return -1;
            return ((b & 0x07) << 18) | ((b2 & 0x3F) << 12) | ((b3 & 0x3F) << 6) | (b4 & 0x3F);
        }

        return -1;
    }

    /**
     * Get the length (in bytes) of the UTF-8 sequence starting at the given byte.
     */
    public static int getSequenceLength(byte firstByte) {
        int b = firstByte & 0xFF;

        if ((b & 0x80) == 0) return 1;        // 0xxxxxxx
        if ((b & 0xE0) == 0xC0) return 2;     // 110xxxxx
        if ((b & 0xF0) == 0xE0) return 3;     // 1110xxxx
        if ((b & 0xF8) == 0xF0) return 4;     // 11110xxx
        if ((b & 0xFC) == 0xF8) return 5;     // 111110xx (invalid in current Unicode)
        if ((b & 0xFE) == 0xFC) return 6;     // 1111110x (invalid in current Unicode)

        return -1; // Invalid UTF-8
    }
}
