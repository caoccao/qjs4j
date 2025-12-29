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

import java.util.stream.Stream;

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
    IMPORT(51, 1, 1, 1),

    // Arithmetic operations
    ADD(52, 1, 2, 1),
    SUB(53, 1, 2, 1),
    MUL(54, 1, 2, 1),
    DIV(55, 1, 2, 1),
    MOD(56, 1, 2, 1),
    EXP(57, 1, 2, 1),
    PLUS(58, 1, 1, 1),
    NEG(59, 1, 1, 1),
    INC(60, 1, 1, 1),
    DEC(61, 1, 1, 1),
    POST_INC(62, 1, 1, 1),
    POST_DEC(63, 1, 1, 1),

    // Bitwise operations
    SHL(64, 1, 2, 1),
    SAR(65, 1, 2, 1),
    SHR(66, 1, 2, 1),
    AND(67, 1, 2, 1),
    OR(68, 1, 2, 1),
    XOR(69, 1, 2, 1),
    NOT(70, 1, 1, 1),

    // Comparison operations
    EQ(71, 1, 2, 1),
    NEQ(72, 1, 2, 1),
    STRICT_EQ(73, 1, 2, 1),
    STRICT_NEQ(74, 1, 2, 1),
    LT(75, 1, 2, 1),
    LTE(76, 1, 2, 1),
    GT(77, 1, 2, 1),
    GTE(78, 1, 2, 1),
    INSTANCEOF(79, 1, 2, 1),
    IN(80, 1, 2, 1),

    // Logical operations
    LOGICAL_NOT(81, 1, 1, 1),
    LOGICAL_AND(82, 1, 2, 1),
    LOGICAL_OR(83, 1, 2, 1),
    NULLISH_COALESCE(84, 1, 2, 1),

    // Variable access
    GET_VAR(85, 5, 0, 1),
    PUT_VAR(86, 5, 1, 0),
    SET_VAR(87, 5, 1, 1),
    GET_LOCAL(88, 3, 0, 1),
    PUT_LOCAL(89, 3, 1, 0),
    SET_LOCAL(90, 3, 1, 1),
    GET_ARG(91, 3, 0, 1),
    PUT_ARG(92, 3, 1, 0),
    SET_ARG(93, 3, 1, 1),

    // Property access
    GET_FIELD(94, 5, 1, 1),
    GET_FIELD2(95, 5, 1, 1),
    PUT_FIELD(96, 5, 2, 0),
    GET_ARRAY_EL(97, 1, 2, 1),
    PUT_ARRAY_EL(98, 1, 3, 0),
    GET_SUPER_VALUE(99, 1, 2, 1),
    PUT_SUPER_VALUE(100, 1, 3, 0),

    // Control flow
    IF_FALSE(101, 5, 1, 0),
    IF_TRUE(102, 5, 1, 0),
    GOTO(103, 5, 0, 0),
    CATCH(104, 5, 0, 1),
    GOSUB(105, 5, 0, 0),
    RET(106, 1, 1, 0),

    // Function operations
    DEFINE_FUNC(107, 5, 0, 1),
    DEFINE_METHOD(108, 5, 2, 1),
    DEFINE_CLASS(109, 5, 2, 1),

    // Array/Object operations
    ARRAY_NEW(110, 1, 0, 1),
    PUSH_ARRAY(111, 1, 2, 1),
    OBJECT_NEW(112, 1, 0, 1),
    DEFINE_PROP(113, 1, 3, 1),

    // Type operations
    TYPEOF(114, 1, 1, 1),
    DELETE(115, 1, 2, 1),
    TO_OBJECT(116, 1, 1, 1),
    TO_PROPKEY(117, 1, 1, 1),
    TO_PROPKEY2(118, 1, 2, 2),

    // Scope operations
    WITH_GET_VAR(119, 10, 0, 1),
    WITH_PUT_VAR(120, 10, 1, 0),
    WITH_DELETE_VAR(121, 10, 0, 1),
    WITH_MAKE_REF(122, 10, 0, 2),
    WITH_GET_REF(123, 10, 1, 0),
    WITH_GET_REF_UNDEF(124, 10, 1, 0),

    // Async operations
    AWAIT(125, 1, 1, 1);

    // This is a subset of QuickJS opcodes - full implementation would have ~244 opcodes

    private static final Opcode[] opcodes = new Opcode[values().length];

    static {
        Stream.of(values()).forEach(opcode -> opcodes[opcode.code] = opcode);
    }

    private final int code;
    private final int nPop;
    private final int nPush;
    private final int size;

    Opcode(int code, int size, int nPop, int nPush) {
        this.code = code;
        this.size = size;
        this.nPop = nPop;
        this.nPush = nPush;
    }

    public static Opcode fromInt(int code) {
        if (code >= 0 && code < opcodes.length) {
            return opcodes[code];
        }
        return INVALID;
    }

    public int getCode() {
        return code;
    }

    public int getNPop() {
        return nPop;
    }

    public int getNPush() {
        return nPush;
    }

    public int getSize() {
        return size;
    }
}
