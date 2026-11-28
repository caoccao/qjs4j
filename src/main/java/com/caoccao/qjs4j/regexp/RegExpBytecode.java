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

package com.caoccao.qjs4j.regexp;

import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

import java.util.Arrays;

/**
 * Represents compiled regex bytecode. Based on QuickJS libregexp.h.
 */
public record RegExpBytecode(byte[] instructions, int flags, int captureCount, int registerCount, String[] groupNames) {
    public static final int FLAG_DOTALL = 1 << 3;
    // Regex flags
    public static final int FLAG_GLOBAL = 1 << 0;
    public static final int FLAG_IGNORECASE = 1 << 1;
    public static final int FLAG_INDICES = 1 << 6;
    public static final int FLAG_MULTILINE = 1 << 2;
    public static final int FLAG_NAMED_GROUPS = 1 << 7;
    public static final int FLAG_STICKY = 1 << 5;
    public static final int FLAG_UNICODE = 1 << 4;
    public static final int FLAG_UNICODE_SETS = 1 << 8;

    /** Flag order in the compiled bytecode's string representation. */
    private static final String FLAG_CHARACTERS = "gimsuydv";
    private static final int[] FLAG_BITS = {FLAG_GLOBAL, FLAG_IGNORECASE, FLAG_MULTILINE, FLAG_DOTALL, FLAG_UNICODE,
            FLAG_STICKY, FLAG_INDICES, FLAG_UNICODE_SETS};

    public RegExpBytecode(byte[] instructions, int flags, int captureCount) {
        this(instructions, flags, captureCount, ExecutionLimits.MAX_REGISTERS, null);
    }

    /**
     * Parse flags from a flag string (e.g., "gi", "gim").
     */
    public static int parseFlags(String flagStr) {
        int flags = 0;
        if (flagStr != null) {
            for (int i = 0; i < flagStr.length(); i++) {
                char c = flagStr.charAt(i);
                int index = FLAG_CHARACTERS.indexOf(c);
                if (index < 0 || (flags & FLAG_BITS[index]) != 0) {
                    throw new JSSyntaxErrorException("Invalid regular expression flags");
                }
                int flagBit = FLAG_BITS[index];
                flags |= flagBit;
            }
        }
        if ((flags & FLAG_UNICODE) != 0 && (flags & FLAG_UNICODE_SETS) != 0) {
            throw new JSSyntaxErrorException("Invalid regular expression flags");
        }
        return flags;
    }

    /**
     * Convert flags to a string.
     */
    public String flagsToString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < FLAG_BITS.length; i++) {
            if ((flags & FLAG_BITS[i]) != 0) {
                sb.append(FLAG_CHARACTERS.charAt(i));
            }
        }
        return sb.toString();
    }

    /**
     * Read a 16-bit unsigned value from bytecode (little-endian).
     */
    static int readU16(byte[] bc, int offset) {
        return (bc[offset] & 0xFF) | ((bc[offset + 1] & 0xFF) << 8);
    }

    /**
     * Read a 32-bit unsigned value from bytecode (little-endian).
     */
    static int readU32(byte[] bc, int offset) {
        return (bc[offset] & 0xFF) | ((bc[offset + 1] & 0xFF) << 8) | ((bc[offset + 2] & 0xFF) << 16)
                | ((bc[offset + 3] & 0xFF) << 24);
    }

    public boolean hasIndices() {
        return (flags & FLAG_INDICES) != 0;
    }

    public boolean hasNamedGroups() {
        return (flags & FLAG_NAMED_GROUPS) != 0;
    }

    public boolean hasUnicodeSets() {
        return (flags & FLAG_UNICODE_SETS) != 0;
    }

    public boolean isDotAll() {
        return (flags & FLAG_DOTALL) != 0;
    }

    public boolean isGlobal() {
        return (flags & FLAG_GLOBAL) != 0;
    }

    public boolean isIgnoreCase() {
        return (flags & FLAG_IGNORECASE) != 0;
    }

    public boolean isMultiline() {
        return (flags & FLAG_MULTILINE) != 0;
    }

    public boolean isSticky() {
        return (flags & FLAG_STICKY) != 0;
    }

    public boolean isUnicode() {
        return (flags & FLAG_UNICODE) != 0;
    }

    @Override
    public String toString() {
        return "RegExpBytecode{" + "instructions=" + instructions.length + " bytes, " + "flags=" + flagsToString()
                + ", " + "captureCount=" + captureCount
                + (groupNames != null ? ", groupNames=" + Arrays.toString(groupNames) : "") + '}';
    }

    /**
     * Registers the matcher must be able to hold.
     * <p>
     * Only the zero-advance check allocates one, so most patterns use none and the maximum any pattern can reach is
     * {@link ExecutionLimits#MAX_REGISTERS}. The matcher saves and restores this many on every backtrack point where
     * state changed, so sizing it to what the pattern actually uses — rather than always to the maximum — is what keeps
     * the backtrack stack inside the memory budget for a long subject.
     */
    public static final class ExecutionLimits {
        /**
         * The register operand is an unsigned byte.
         */
        public static final int MAX_REGISTERS = 256;

        private ExecutionLimits() {
        }
    }
}
