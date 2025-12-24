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

package com.caoccao.qjs4j.vm;

/**
 * Enumeration of all JavaScript bytecode opcodes.
 * Total: 244 opcodes from QuickJS.
 */
public enum Opcode {
    INVALID(0, 1, 0, 0),
    PUSH_I32(1, 5, 0, 1),
    PUSH_CONST(2, 5, 0, 1),
    FCLOSURE(3, 5, 0, 1),
    PUSH_ATOM_VALUE(4, 5, 0, 1),
    PRIVATE_SYMBOL(5, 5, 0, 1),
    UNDEFINED(6, 1, 0, 1),
    NULL(7, 1, 0, 1),
    PUSH_THIS(8, 1, 0, 1),
    PUSH_FALSE(9, 1, 0, 1),
    PUSH_TRUE(10, 1, 0, 1),
    OBJECT(11, 1, 0, 1),
    SPECIAL_OBJECT(12, 2, 0, 1),
    REST(13, 3, 0, 1),
    DROP(14, 1, 1, 0),
    NIP(15, 1, 2, 1),
    NIP1(16, 1, 3, 2),
    DUP(17, 1, 1, 2),
    DUP2(18, 1, 2, 4),
    DUP3(19, 1, 3, 6),
    INSERT2(20, 1, 2, 3),
    INSERT3(21, 1, 3, 4),
    INSERT4(22, 1, 4, 5),
    PERM3(23, 1, 3, 3),
    PERM4(24, 1, 4, 4),
    PERM5(25, 1, 5, 5),
    SWAP(26, 1, 2, 2),
    SWAP2(27, 1, 4, 4),
    ROT3L(28, 1, 3, 3),
    ROT3R(29, 1, 3, 3),
    ROT4L(30, 1, 4, 4),
    ROT5L(31, 1, 5, 5),
    CALL_CONSTRUCTOR(32, 3, -1, 1),
    CALL(33, 3, -1, 1),
    TAIL_CALL(34, 3, -1, 0),
    CALL_METHOD(35, 3, -1, 1),
    TAIL_CALL_METHOD(36, 3, -1, 0),
    ARRAY_FROM(37, 3, -1, 1),
    APPLY(38, 3, 3, 1),
    RETURN(39, 1, 1, 0),
    RETURN_UNDEF(40, 1, 0, 0),
    CHECK_CTOR_RETURN(41, 1, 1, 1),
    CHECK_CTOR(42, 1, 0, 0),
    CHECK_BRAND(43, 1, 2, 2),
    ADD_BRAND(44, 1, 2, 0),
    THROW(45, 1, 1, 0),
    THROW_ERROR(46, 6, 0, 0),
    EVAL(47, 5, -1, 1),
    APPLY_EVAL(48, 3, 2, 1),
    REGEXP(49, 1, 0, 1),
    GET_SUPER(50, 1, 1, 1),
    IMPORT(51, 1, 1, 1);

    // Additional opcodes will be added here...
    // This is a simplified set - QuickJS has 244 opcodes total

    private final int code;
    private final int size;
    private final int nPop;
    private final int nPush;

    Opcode(int code, int size, int nPop, int nPush) {
        this.code = code;
        this.size = size;
        this.nPop = nPop;
        this.nPush = nPush;
    }

    public int getCode() {
        return code;
    }

    public int getSize() {
        return size;
    }

    public int getNPop() {
        return nPop;
    }

    public int getNPush() {
        return nPush;
    }

    public static Opcode fromInt(int code) {
        for (Opcode op : values()) {
            if (op.code == code) {
                return op;
            }
        }
        return INVALID;
    }
}
