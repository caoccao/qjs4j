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

/**
 * Regular expression bytecode opcodes. Based on QuickJS libregexp-opcode.h.
 */
public enum RegExpOpcode {
    /** match any character including line terminator */
    ANY(6, 1),
    /** match back reference (variable length) */
    BACK_REFERENCE(32, 2),
    /** match back reference (case insensitive) */
    BACK_REFERENCE_I(33, 2),
    /** backward back reference */
    BACKWARD_BACK_REFERENCE(34, 2),
    /** backward back reference (case insensitive) */
    BACKWARD_BACK_REFERENCE_I(35, 2),
    /** match single character */
    CHAR(1, 3),
    /** match single character (case insensitive) */
    CHAR_I(2, 3),
    /** match 32-bit character */
    CHAR32(3, 5),
    /** match 32-bit character (case insensitive) */
    CHAR32_I(4, 5),
    /** check that register != character position */
    CHECK_ADVANCE(43, 2),
    /** match any character except line terminator */
    DOT(5, 1),
    /** unconditional jump */
    GOTO(13, 5),
    /** never used */
    INVALID(0, 1),
    /** match line end ($) */
    LINE_END(11, 1),
    /** match line end in multiline mode */
    LINE_END_M(12, 1),
    /** match line start (^) */
    LINE_START(9, 1),
    /** match line start in multiline mode */
    LINE_START_M(10, 1),
    /** positive lookahead */
    LOOKAHEAD(40, 5),
    /** successful lookahead match */
    LOOKAHEAD_MATCH(17, 1),
    /** positive lookbehind */
    LOOKBEHIND(47, 5),
    /** successful lookbehind match */
    LOOKBEHIND_MATCH(45, 1),
    /** decrement counter and jump if != 0 */
    LOOP(22, 6),
    /** loop, check advance, then split */
    LOOP_CHECK_ADV_SPLIT_GOTO_FIRST(25, 10),
    /** loop, check advance, then split */
    LOOP_CHECK_ADV_SPLIT_NEXT_FIRST(26, 10),
    /** loop then split (goto first) */
    LOOP_SPLIT_GOTO_FIRST(23, 10),
    /** loop then split (next first) */
    LOOP_SPLIT_NEXT_FIRST(24, 10),
    /** successful match */
    MATCH(16, 1),
    /** negative lookahead */
    NEGATIVE_LOOKAHEAD(41, 5),
    /** successful negative lookahead */
    NEGATIVE_LOOKAHEAD_MATCH(18, 1),
    /** negative lookbehind */
    NEGATIVE_LOOKBEHIND(48, 5),
    /** successful negative lookbehind */
    NEGATIVE_LOOKBEHIND_MATCH(46, 1),
    /** inverted character range (variable length) */
    NOT_RANGE(64, 5),
    /** inverted character range (case insensitive) */
    NOT_RANGE_I(65, 5),
    /** match non-whitespace */
    NOT_SPACE(8, 1),
    /** match non-word boundary (\B) */
    NOT_WORD_BOUNDARY(30, 1),
    /** match non-word boundary (case insensitive) */
    NOT_WORD_BOUNDARY_I(31, 1),
    /** go to previous character */
    PREV(44, 1),
    /** character range (variable length) */
    RANGE(36, 5),
    /** character range (case insensitive) */
    RANGE_I(37, 5),
    /** 32-bit character range (variable length) */
    RANGE32(38, 3),
    /** 32-bit character range (case insensitive) */
    RANGE32_I(39, 3),
    /** save capture group end */
    SAVE_END(20, 2),
    /** reset save positions */
    SAVE_RESET(21, 3),
    /** save capture group start */
    SAVE_START(19, 2),
    /** store character position to register */
    SET_CHAR_POS(42, 2),
    /** store immediate value to register */
    SET_I32(27, 6),
    /** match whitespace */
    SPACE(7, 1),
    /** split: try first branch first */
    SPLIT_GOTO_FIRST(14, 5),
    /** split: try second branch first */
    SPLIT_NEXT_FIRST(15, 5),
    /** match word boundary (\b) */
    WORD_BOUNDARY(28, 1),
    /** match word boundary (case insensitive) */
    WORD_BOUNDARY_I(29, 1);

    private static final RegExpOpcode[] LOOKUP;

    static {
        // Build a fast lookup table indexed by opcode code value.
        int max = 0;
        for (RegExpOpcode op : values()) {
            if (op.code > max) {
                max = op.code;
            }
        }
        LOOKUP = new RegExpOpcode[max + 1];
        for (RegExpOpcode op : values()) {
            LOOKUP[op.code] = op;
        }
    }

    private final int code;
    private final int length;

    RegExpOpcode(int code, int length) {
        this.code = code;
        this.length = length;
    }

    public int getCode() {
        return code;
    }

    public int getLength() {
        return length;
    }

    /**
     * Check if this is a case-insensitive variant.
     */
    public boolean isCaseInsensitive() {
        return this == CHAR_I || this == CHAR32_I || this == RANGE_I || this == RANGE32_I || this == WORD_BOUNDARY_I
                || this == NOT_WORD_BOUNDARY_I || this == BACK_REFERENCE_I || this == BACKWARD_BACK_REFERENCE_I;
    }

    /**
     * Check if this opcode has variable length.
     */
    public boolean isVariableLength() {
        return this == BACK_REFERENCE || this == BACK_REFERENCE_I || this == BACKWARD_BACK_REFERENCE
                || this == BACKWARD_BACK_REFERENCE_I || this == RANGE || this == RANGE_I || this == RANGE32
                || this == RANGE32_I;
    }

    /**
     * Get opcode by code value.
     */
    public static RegExpOpcode fromCode(int code) {
        if (code >= 0 && code < LOOKUP.length && LOOKUP[code] != null) {
            return LOOKUP[code];
        }
        return INVALID;
    }
}
