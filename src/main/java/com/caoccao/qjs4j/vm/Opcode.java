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
 * Total: 262 opcodes (144 original + 118 newly added from QuickJS).
 * <p>
 * Note: This implementation uses custom opcode numbers that differ from QuickJS.
 * Comments indicate the corresponding QuickJS opcode number for reference.
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
    AWAIT(125, 1, 1, 1),
    RETURN_ASYNC(126, 1, 1, 0),  // Return from async function - pops value and returns
    FOR_AWAIT_OF_START(127, 1, 1, 3),  // Start async iteration: iterable -> iter next catch_offset
    FOR_AWAIT_OF_NEXT(128, 1, 3, 4),   // Get next from async iterator: iter next catch_offset -> iter next catch_offset obj

    // Sync iteration operations
    FOR_OF_START(129, 1, 1, 3),  // Start sync iteration: iterable -> iter next catch_offset
    FOR_OF_NEXT(130, 2, 3, 5),   // Get next from sync iterator: iter next catch_offset -> iter next catch_offset value done

    // For-in iteration operations (object enumeration)
    FOR_IN_START(136, 1, 1, 1),  // Start for-in enumeration: obj -> enum_obj
    FOR_IN_NEXT(137, 1, 1, 2),   // Get next property: enum_obj -> enum_obj key
    FOR_IN_END(138, 1, 1, 0),    // End for-in iteration: enum_obj ->

    // Private field operations
    GET_PRIVATE_FIELD(131, 1, 2, 1),     // Get private field: obj prop -> value
    PUT_PRIVATE_FIELD(132, 1, 3, 0),     // Set private field: obj value prop ->
    DEFINE_PRIVATE_FIELD(133, 1, 3, 1),  // Define private field: obj prop value -> obj
    DEFINE_FIELD(134, 5, 2, 1),          // Define regular field: obj value -> obj (takes atom parameter)
    PRIVATE_IN(135, 1, 2, 1),            // Check if object has private field: obj prop -> boolean

    // Missing QuickJS opcodes - added for completeness (139-173, 175-211, 216-244)

    // Additional opcodes (139-173)
    DUP1(139, 1, 2, 3),                  // a b -> a a b (QuickJS opcode 18)
    INIT_CTOR(140, 1, 0, 1),             // Initialize constructor (QuickJS opcode 44)
    GET_VAR_UNDEF(141, 3, 0, 1),         // Push undefined if variable doesn't exist (QuickJS opcode 55)
    PUT_VAR_INIT(142, 3, 1, 0),          // Initialize global lexical variable (QuickJS opcode 58)
    GET_REF_VALUE(143, 1, 2, 3),         // Get reference value (QuickJS opcode 59)
    PUT_REF_VALUE(144, 1, 3, 0),         // Put reference value (QuickJS opcode 60)
    GET_ARRAY_EL2(145, 1, 2, 2),         // obj prop -> obj value (QuickJS opcode 68)
    GET_ARRAY_EL3(146, 1, 2, 3),         // obj prop -> obj prop1 value (QuickJS opcode 69)
    SET_NAME(147, 5, 1, 1),              // Set function name (QuickJS opcode 74)
    SET_NAME_COMPUTED(148, 1, 2, 2),     // Set computed function name (QuickJS opcode 75)
    SET_PROTO(149, 1, 2, 1),             // Set prototype (QuickJS opcode 76)
    SET_HOME_OBJECT(150, 1, 2, 2),       // Set home object for super (QuickJS opcode 77)
    DEFINE_ARRAY_EL(151, 1, 3, 2),       // Define array element (QuickJS opcode 78)
    APPEND(152, 1, 3, 2),                // Append enumerated object, update length (QuickJS opcode 79)
    COPY_DATA_PROPERTIES(153, 2, 3, 3),  // Copy data properties (QuickJS opcode 80)
    DEFINE_METHOD_COMPUTED(154, 2, 3, 1), // Define method with computed name (QuickJS opcode 82)
    DEFINE_CLASS_COMPUTED(155, 6, 3, 3),  // Define class with computed name (QuickJS opcode 84)
    GET_LOC(156, 3, 0, 1),               // Get local variable (QuickJS opcode 85)
    PUT_LOC(157, 3, 1, 0),               // Put local variable (QuickJS opcode 86)
    SET_LOC(158, 3, 1, 1),               // Set local variable (QuickJS opcode 87)
    GET_VAR_REF(159, 3, 0, 1),           // Get variable reference (QuickJS opcode 91)
    PUT_VAR_REF(160, 3, 1, 0),           // Put variable reference (QuickJS opcode 92)
    SET_VAR_REF(161, 3, 1, 1),           // Set variable reference (QuickJS opcode 93)
    SET_LOC_UNINITIALIZED(162, 3, 0, 0), // Set local uninitialized (QuickJS opcode 94)
    GET_LOC_CHECK(163, 3, 0, 1),         // Get local with check (QuickJS opcode 95)
    PUT_LOC_CHECK(164, 3, 1, 0),         // Put local with check (QuickJS opcode 96)
    SET_LOC_CHECK(165, 3, 1, 1),         // Set local with check (QuickJS opcode 97)
    PUT_LOC_CHECK_INIT(166, 3, 1, 0),    // Put local check init (QuickJS opcode 98)
    GET_LOC_CHECKTHIS(167, 3, 0, 1),     // Get local check this (QuickJS opcode 99)
    GET_VAR_REF_CHECK(168, 3, 0, 1),     // Get var ref with check (QuickJS opcode 100)
    PUT_VAR_REF_CHECK(169, 3, 1, 0),     // Put var ref with check (QuickJS opcode 101)
    PUT_VAR_REF_CHECK_INIT(170, 3, 1, 0), // Put var ref check init (QuickJS opcode 102)
    CLOSE_LOC(171, 3, 0, 0),             // Close local variable (QuickJS opcode 103)
    NIP_CATCH(172, 1, 2, 1),             // catch ... a -> a (QuickJS opcode 110)
    TO_STRING(173, 1, 1, 1),             // Convert to string (QuickJS opcode commented out in QuickJS)

    // Type checking operations
    IS_UNDEFINED_OR_NULL(174, 1, 1, 1),  // Check if value is null or undefined - replaces value with boolean

    // More missing opcodes (175-211)
    PUSH_BIGINT_I32(175, 5, 0, 1),       // Push BigInt from i32 (QuickJS opcode 176)
    NOP(176, 1, 0, 0),                   // No operation (QuickJS opcode 177)
    MAKE_LOC_REF(177, 7, 0, 2),          // Make local reference (QuickJS opcode 118)
    MAKE_ARG_REF(178, 7, 0, 2),          // Make argument reference (QuickJS opcode 119)
    MAKE_VAR_REF_REF(179, 7, 0, 2),      // Make var ref reference (QuickJS opcode 120)
    MAKE_VAR_REF(180, 5, 0, 2),          // Make var reference (QuickJS opcode 121)
    ITERATOR_CHECK_OBJECT(181, 1, 1, 1), // Check if object is iterable (QuickJS opcode 128)
    ITERATOR_GET_VALUE_DONE(182, 1, 2, 3), // Get value and done from iterator result (QuickJS opcode 129)
    ITERATOR_CLOSE(183, 1, 3, 0),        // Close iterator (QuickJS opcode 130)
    ITERATOR_NEXT(184, 1, 4, 4),         // Get next from iterator (QuickJS opcode 131)
    ITERATOR_CALL(185, 2, 4, 5),         // Call iterator (QuickJS opcode 132)
    DEC_LOC(186, 2, 0, 0),               // Decrement local (QuickJS opcode 144)
    INC_LOC(187, 2, 0, 0),               // Increment local (QuickJS opcode 145)
    ADD_LOC(188, 2, 1, 0),               // Add to local (QuickJS opcode 146)
    LNOT(189, 1, 1, 1),                  // Logical not (QuickJS opcode 148)
    DELETE_VAR(190, 5, 0, 1),            // Delete variable (QuickJS opcode 151)
    POW(191, 1, 2, 1),                   // Power operation (QuickJS opcode 157)

    // SHORT_OPCODES (192-211)
    PUSH_MINUS1(192, 1, 0, 1),           // Push -1 (QuickJS opcode 178)
    PUSH_0(193, 1, 0, 1),                // Push 0 (QuickJS opcode 179)
    PUSH_1(194, 1, 0, 1),                // Push 1 (QuickJS opcode 180)
    PUSH_2(195, 1, 0, 1),                // Push 2 (QuickJS opcode 181)
    PUSH_3(196, 1, 0, 1),                // Push 3 (QuickJS opcode 182)
    PUSH_4(197, 1, 0, 1),                // Push 4 (QuickJS opcode 183)
    PUSH_5(198, 1, 0, 1),                // Push 5 (QuickJS opcode 184)
    PUSH_6(199, 1, 0, 1),                // Push 6 (QuickJS opcode 185)
    PUSH_7(200, 1, 0, 1),                // Push 7 (QuickJS opcode 186)
    PUSH_I8(201, 2, 0, 1),               // Push i8 (QuickJS opcode 187)
    PUSH_I16(202, 3, 0, 1),              // Push i16 (QuickJS opcode 188)
    PUSH_CONST8(203, 2, 0, 1),           // Push const8 (QuickJS opcode 189)
    FCLOSURE8(204, 2, 0, 1),             // Function closure 8-bit (QuickJS opcode 190)
    PUSH_EMPTY_STRING(205, 1, 0, 1),     // Push empty string (QuickJS opcode 191)
    GET_LOC8(206, 2, 0, 1),              // Get local 8-bit (QuickJS opcode 192)
    PUT_LOC8(207, 2, 1, 0),              // Put local 8-bit (QuickJS opcode 193)
    SET_LOC8(208, 2, 1, 1),              // Set local 8-bit (QuickJS opcode 194)
    GET_LOC0(209, 1, 0, 1),              // Get local 0 (QuickJS opcode 195)
    GET_LOC1(210, 1, 0, 1),              // Get local 1 (QuickJS opcode 196)
    GET_LOC2(211, 1, 0, 1),              // Get local 2 (QuickJS opcode 197)

    // Generator operations (following QuickJS opcode numbers)
    INITIAL_YIELD(212, 1, 0, 0),  // Initial yield in generator - suspends generator at start
    YIELD(213, 1, 1, 2),          // Yield value from generator - pops value, yields it
    YIELD_STAR(214, 1, 1, 2),     // Yield* delegating to another generator
    ASYNC_YIELD_STAR(215, 1, 1, 2),  // Async yield* for async generators

    // More SHORT_OPCODES (216-244)
    GET_LOC3(216, 1, 0, 1),              // Get local 3 (QuickJS opcode 198)
    PUT_LOC0(217, 1, 1, 0),              // Put local 0 (QuickJS opcode 199)
    PUT_LOC1(218, 1, 1, 0),              // Put local 1 (QuickJS opcode 200)
    PUT_LOC2(219, 1, 1, 0),              // Put local 2 (QuickJS opcode 201)
    PUT_LOC3(220, 1, 1, 0),              // Put local 3 (QuickJS opcode 202)
    SET_LOC0(221, 1, 1, 1),              // Set local 0 (QuickJS opcode 203)
    SET_LOC1(222, 1, 1, 1),              // Set local 1 (QuickJS opcode 204)
    SET_LOC2(223, 1, 1, 1),              // Set local 2 (QuickJS opcode 205)
    SET_LOC3(224, 1, 1, 1),              // Set local 3 (QuickJS opcode 206)
    GET_ARG0(225, 1, 0, 1),              // Get arg 0 (QuickJS opcode 207)
    GET_ARG1(226, 1, 0, 1),              // Get arg 1 (QuickJS opcode 208)
    GET_ARG2(227, 1, 0, 1),              // Get arg 2 (QuickJS opcode 209)
    GET_ARG3(228, 1, 0, 1),              // Get arg 3 (QuickJS opcode 210)
    PUT_ARG0(229, 1, 1, 0),              // Put arg 0 (QuickJS opcode 211)
    PUT_ARG1(230, 1, 1, 0),              // Put arg 1 (QuickJS opcode 212)
    PUT_ARG2(231, 1, 1, 0),              // Put arg 2 (QuickJS opcode 213)
    PUT_ARG3(232, 1, 1, 0),              // Put arg 3 (QuickJS opcode 214)
    SET_ARG0(233, 1, 1, 1),              // Set arg 0 (QuickJS opcode 215)
    SET_ARG1(234, 1, 1, 1),              // Set arg 1 (QuickJS opcode 216)
    SET_ARG2(235, 1, 1, 1),              // Set arg 2 (QuickJS opcode 217)
    SET_ARG3(236, 1, 1, 1),              // Set arg 3 (QuickJS opcode 218)
    GET_VAR_REF0(237, 1, 0, 1),          // Get var ref 0 (QuickJS opcode 219)
    GET_VAR_REF1(238, 1, 0, 1),          // Get var ref 1 (QuickJS opcode 220)
    GET_VAR_REF2(239, 1, 0, 1),          // Get var ref 2 (QuickJS opcode 221)
    GET_VAR_REF3(240, 1, 0, 1),          // Get var ref 3 (QuickJS opcode 222)
    PUT_VAR_REF0(241, 1, 1, 0),          // Put var ref 0 (QuickJS opcode 223)
    PUT_VAR_REF1(242, 1, 1, 0),          // Put var ref 1 (QuickJS opcode 224)
    PUT_VAR_REF2(243, 1, 1, 0),          // Put var ref 2 (QuickJS opcode 225)
    PUT_VAR_REF3(244, 1, 1, 0),          // Put var ref 3 (QuickJS opcode 226)
    SET_VAR_REF0(245, 1, 1, 1),          // Set var ref 0 (QuickJS opcode 227)
    SET_VAR_REF1(246, 1, 1, 1),          // Set var ref 1 (QuickJS opcode 228)
    SET_VAR_REF2(247, 1, 1, 1),          // Set var ref 2 (QuickJS opcode 229)
    SET_VAR_REF3(248, 1, 1, 1),          // Set var ref 3 (QuickJS opcode 230)
    GET_LENGTH(249, 1, 1, 1),            // Get length (QuickJS opcode 231)
    IF_FALSE8(250, 2, 1, 0),             // If false 8-bit (QuickJS opcode 232)
    IF_TRUE8(251, 2, 1, 0),              // If true 8-bit (QuickJS opcode 233)
    GOTO8(252, 2, 0, 0),                 // Goto 8-bit (QuickJS opcode 234)
    GOTO16(253, 3, 0, 0),                // Goto 16-bit (QuickJS opcode 235)
    CALL0(254, 1, 1, 1),                 // Call with 0 args (QuickJS opcode 236)
    CALL1(255, 1, 1, 1),                 // Call with 1 arg (QuickJS opcode 237)
    CALL2(256, 1, 1, 1),                 // Call with 2 args (QuickJS opcode 238)
    CALL3(257, 1, 1, 1),                 // Call with 3 args (QuickJS opcode 239)
    IS_UNDEFINED(258, 1, 1, 1),          // Is undefined (QuickJS opcode 240)
    IS_NULL(259, 1, 1, 1),               // Is null (QuickJS opcode 241)
    TYPEOF_IS_UNDEFINED(260, 1, 1, 1),   // Typeof is undefined (QuickJS opcode 242)
    TYPEOF_IS_FUNCTION(261, 1, 1, 1);    // Typeof is function (QuickJS opcode 243)

    // Total: 262 opcodes (144 original + 118 missing = 262 total)

    private static final Opcode[] opcodes = new Opcode[262];  // Use fixed size to accommodate all opcode numbers

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
