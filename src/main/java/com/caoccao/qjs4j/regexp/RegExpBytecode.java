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

import java.util.Arrays;

/**
 * Represents compiled regex bytecode.
 * Based on QuickJS libregexp.h.
 */
public record RegExpBytecode(byte[] instructions, int flags, int captureCount, String[] groupNames) {
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

    public RegExpBytecode(byte[] instructions, int flags, int captureCount) {
        this(instructions, flags, captureCount, null);
    }

    /**
     * Parse flags from a flag string (e.g., "gi", "gim").
     */
    public static int parseFlags(String flagStr) {
        int flags = 0;
        if (flagStr != null) {
            for (int i = 0; i < flagStr.length(); i++) {
                char c = flagStr.charAt(i);
                int flagBit = switch (c) {
                    case 'g' -> FLAG_GLOBAL;
                    case 'i' -> FLAG_IGNORECASE;
                    case 'm' -> FLAG_MULTILINE;
                    case 's' -> FLAG_DOTALL;
                    case 'u' -> FLAG_UNICODE;
                    case 'y' -> FLAG_STICKY;
                    case 'd' -> FLAG_INDICES;
                    case 'v' -> FLAG_UNICODE_SETS;
                    default -> throw new IllegalArgumentException("Invalid regular expression flags");
                };
                if ((flags & flagBit) != 0) {
                    throw new IllegalArgumentException("Invalid regular expression flags");
                }
                flags |= flagBit;
            }
        }
        if ((flags & FLAG_UNICODE) != 0 && (flags & FLAG_UNICODE_SETS) != 0) {
            throw new IllegalArgumentException("Invalid regular expression flags");
        }
        return flags;
    }

    /**
     * Convert flags to a string.
     */
    public String flagsToString() {
        StringBuilder sb = new StringBuilder();
        if (isGlobal()) {
            sb.append('g');
        }
        if (isIgnoreCase()) {
            sb.append('i');
        }
        if (isMultiline()) {
            sb.append('m');
        }
        if (isDotAll()) {
            sb.append('s');
        }
        if (isUnicode()) {
            sb.append('u');
        }
        if (isSticky()) {
            sb.append('y');
        }
        if (hasIndices()) {
            sb.append('d');
        }
        if (hasUnicodeSets()) {
            sb.append('v');
        }
        return sb.toString();
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
        return "RegExpBytecode{" +
                "instructions=" + instructions.length + " bytes, " +
                "flags=" + flagsToString() + ", " +
                "captureCount=" + captureCount +
                (groupNames != null ? ", groupNames=" + Arrays.toString(groupNames) : "") +
                '}';
    }
}
