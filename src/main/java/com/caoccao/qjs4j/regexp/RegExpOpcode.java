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
 * Regular expression bytecode opcodes.
 * Based on QuickJS libregexp-opcode.h.
 */
public enum RegExpOpcode {
    INVALID(0, 1),                          // never used
    CHAR(1, 3),                             // match single character
    CHAR_I(2, 3),                           // match single character (case insensitive)
    CHAR32(3, 5),                           // match 32-bit character
    CHAR32_I(4, 5),                         // match 32-bit character (case insensitive)
    DOT(5, 1),                              // match any character except line terminator
    ANY(6, 1),                              // match any character including line terminator
    SPACE(7, 1),                            // match whitespace
    NOT_SPACE(8, 1),                        // match non-whitespace
    LINE_START(9, 1),                       // match line start (^)
    LINE_START_M(10, 1),                    // match line start in multiline mode
    LINE_END(11, 1),                        // match line end ($)
    LINE_END_M(12, 1),                      // match line end in multiline mode
    GOTO(13, 5),                            // unconditional jump
    SPLIT_GOTO_FIRST(14, 5),                // split: try first branch first
    SPLIT_NEXT_FIRST(15, 5),                // split: try second branch first
    MATCH(16, 1),                           // successful match
    LOOKAHEAD_MATCH(17, 1),                 // successful lookahead match
    NEGATIVE_LOOKAHEAD_MATCH(18, 1),        // successful negative lookahead
    SAVE_START(19, 2),                      // save capture group start
    SAVE_END(20, 2),                        // save capture group end
    SAVE_RESET(21, 3),                      // reset save positions
    LOOP(22, 6),                            // decrement counter and jump if != 0
    LOOP_SPLIT_GOTO_FIRST(23, 10),          // loop then split (goto first)
    LOOP_SPLIT_NEXT_FIRST(24, 10),          // loop then split (next first)
    LOOP_CHECK_ADV_SPLIT_GOTO_FIRST(25, 10), // loop, check advance, then split
    LOOP_CHECK_ADV_SPLIT_NEXT_FIRST(26, 10), // loop, check advance, then split
    SET_I32(27, 6),                         // store immediate value to register
    WORD_BOUNDARY(28, 1),                   // match word boundary (\b)
    WORD_BOUNDARY_I(29, 1),                 // match word boundary (case insensitive)
    NOT_WORD_BOUNDARY(30, 1),               // match non-word boundary (\B)
    NOT_WORD_BOUNDARY_I(31, 1),             // match non-word boundary (case insensitive)
    BACK_REFERENCE(32, 2),                  // match back reference (variable length)
    BACK_REFERENCE_I(33, 2),                // match back reference (case insensitive)
    BACKWARD_BACK_REFERENCE(34, 2),         // backward back reference
    BACKWARD_BACK_REFERENCE_I(35, 2),       // backward back reference (case insensitive)
    RANGE(36, 3),                           // character range (variable length)
    RANGE_I(37, 3),                         // character range (case insensitive)
    RANGE32(38, 3),                         // 32-bit character range (variable length)
    RANGE32_I(39, 3),                       // 32-bit character range (case insensitive)
    LOOKAHEAD(40, 5),                       // positive lookahead
    NEGATIVE_LOOKAHEAD(41, 5),              // negative lookahead
    SET_CHAR_POS(42, 2),                    // store character position to register
    CHECK_ADVANCE(43, 2),                   // check that register != character position
    PREV(44, 1);                            // go to previous character

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
     * Get opcode by code value.
     */
    public static RegExpOpcode fromCode(int code) {
        for (RegExpOpcode op : values()) {
            if (op.code == code) {
                return op;
            }
        }
        return INVALID;
    }

    /**
     * Check if this is a case-insensitive variant.
     */
    public boolean isCaseInsensitive() {
        return this == CHAR_I || this == CHAR32_I || this == RANGE_I || this == RANGE32_I ||
                this == WORD_BOUNDARY_I || this == NOT_WORD_BOUNDARY_I ||
                this == BACK_REFERENCE_I || this == BACKWARD_BACK_REFERENCE_I;
    }

    /**
     * Check if this opcode has variable length.
     */
    public boolean isVariableLength() {
        return this == BACK_REFERENCE || this == BACK_REFERENCE_I ||
                this == BACKWARD_BACK_REFERENCE || this == BACKWARD_BACK_REFERENCE_I ||
                this == RANGE || this == RANGE_I || this == RANGE32 || this == RANGE32_I;
    }
}
