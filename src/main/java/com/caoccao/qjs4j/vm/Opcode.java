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
 * Total: 244 opcodes.
 * <p>
 * Note: This implementation uses custom opcode numbers that differ from QuickJS.
 * Comments indicate the corresponding QuickJS opcode number for reference.
 */
public enum Opcode {
    INVALID(0, 1, 0, 0, OpcodeHandler::handleInvalid),
    PUSH_I32(1, 5, 0, 1, OpcodeHandler::handlePushI32),
    PUSH_CONST(2, 5, 0, 1, OpcodeHandler::handlePushConst),
    FCLOSURE(3, 5, 0, 1, OpcodeHandler::handleFclosure),
    PUSH_ATOM_VALUE(4, 5, 0, 1, OpcodeHandler::handlePushAtomValue),
    PRIVATE_SYMBOL(5, 5, 0, 1, OpcodeHandler::handlePrivateSymbol),
    UNDEFINED(6, 1, 0, 1, OpcodeHandler::handleUndefined),
    NULL(7, 1, 0, 1, OpcodeHandler::handleNull),
    PUSH_THIS(8, 1, 0, 1, OpcodeHandler::handlePushThis),
    PUSH_FALSE(9, 1, 0, 1, OpcodeHandler::handlePushFalse),
    PUSH_TRUE(10, 1, 0, 1, OpcodeHandler::handlePushTrue),
    OBJECT(11, 1, 0, 1, OpcodeHandler::handleObjectNew),
    SPECIAL_OBJECT(12, 2, 0, 1, OpcodeHandler::handleSpecialObject),
    REST(13, 3, 0, 1, OpcodeHandler::handleRest),
    DROP(14, 1, 1, 0, OpcodeHandler::handleDrop),
    NIP(15, 1, 2, 1, OpcodeHandler::handleNip),
    NIP1(16, 1, 3, 2, OpcodeHandler::handleInvalid),
    DUP(17, 1, 1, 2, OpcodeHandler::handleDup),
    DUP2(18, 1, 2, 4, OpcodeHandler::handleDup2),
    DUP3(19, 1, 3, 6, OpcodeHandler::handleInvalid),
    INSERT2(20, 1, 2, 3, OpcodeHandler::handleInsert2),
    INSERT3(21, 1, 3, 4, OpcodeHandler::handleInsert3),
    INSERT4(22, 1, 4, 5, OpcodeHandler::handleInsert4),
    PERM3(23, 1, 3, 3, OpcodeHandler::handlePerm3),
    PERM4(24, 1, 4, 4, OpcodeHandler::handlePerm4),
    PERM5(25, 1, 5, 5, OpcodeHandler::handlePerm5),
    SWAP(26, 1, 2, 2, OpcodeHandler::handleSwap),
    SWAP2(27, 1, 4, 4, OpcodeHandler::handleSwap2),
    ROT3L(28, 1, 3, 3, OpcodeHandler::handleRot3l),
    ROT3R(29, 1, 3, 3, OpcodeHandler::handleRot3r),
    ROT4L(30, 1, 4, 4, OpcodeHandler::handleInvalid),
    ROT5L(31, 1, 5, 5, OpcodeHandler::handleInvalid),
    CALL_CONSTRUCTOR(32, 3, -1, 1, OpcodeHandler::handleCallConstructor),
    CALL(33, 3, -1, 1, OpcodeHandler::handleCall),
    TAIL_CALL(34, 3, -1, 0, OpcodeHandler::handleTailCall),
    CALL_METHOD(35, 3, -1, 1, OpcodeHandler::handleInvalid),
    TAIL_CALL_METHOD(36, 3, -1, 0, OpcodeHandler::handleInvalid),
    ARRAY_FROM(37, 3, -1, 1, OpcodeHandler::handleArrayFrom),
    APPLY(38, 3, 3, 1, OpcodeHandler::handleApply),
    RETURN(39, 1, 1, 0, OpcodeHandler::handleReturn),
    RETURN_UNDEF(40, 1, 0, 0, OpcodeHandler::handleReturnUndef),
    CHECK_CTOR_RETURN(41, 1, 1, 1, OpcodeHandler::handleInvalid),
    CHECK_CTOR(42, 1, 0, 0, OpcodeHandler::handleInvalid),
    CHECK_BRAND(43, 1, 2, 2, OpcodeHandler::handleInvalid),
    ADD_BRAND(44, 1, 2, 0, OpcodeHandler::handleInvalid),
    THROW(45, 1, 1, 0, OpcodeHandler::handleThrow),
    THROW_ERROR(46, 6, 0, 0, OpcodeHandler::handleThrowError),
    EVAL(47, 5, -1, 1, OpcodeHandler::handleEval),
    APPLY_EVAL(48, 3, 2, 1, OpcodeHandler::handleApplyEval),
    REGEXP(49, 1, 0, 1, OpcodeHandler::handleInvalid),
    GET_SUPER(50, 1, 1, 1, OpcodeHandler::handleGetSuper),
    IMPORT(51, 1, 1, 1, OpcodeHandler::handleInvalid),

    // Arithmetic operations
    ADD(52, 1, 2, 1, OpcodeHandler::handleAdd),
    SUB(53, 1, 2, 1, OpcodeHandler::handleSub),
    MUL(54, 1, 2, 1, OpcodeHandler::handleMul),
    DIV(55, 1, 2, 1, OpcodeHandler::handleDiv),
    MOD(56, 1, 2, 1, OpcodeHandler::handleMod),
    PLUS(57, 1, 1, 1, OpcodeHandler::handlePlus),
    NEG(58, 1, 1, 1, OpcodeHandler::handleNeg),
    INC(59, 1, 1, 1, OpcodeHandler::handleInc),
    DEC(60, 1, 1, 1, OpcodeHandler::handleDec),
    POST_INC(61, 1, 1, 1, OpcodeHandler::handlePostInc),
    POST_DEC(62, 1, 1, 1, OpcodeHandler::handlePostDec),

    // Bitwise operations
    SHL(63, 1, 2, 1, OpcodeHandler::handleShl),
    SAR(64, 1, 2, 1, OpcodeHandler::handleSar),
    SHR(65, 1, 2, 1, OpcodeHandler::handleShr),
    AND(66, 1, 2, 1, OpcodeHandler::handleAnd),
    OR(67, 1, 2, 1, OpcodeHandler::handleOr),
    XOR(68, 1, 2, 1, OpcodeHandler::handleXor),
    NOT(69, 1, 1, 1, OpcodeHandler::handleNot),

    // Comparison operations
    EQ(70, 1, 2, 1, OpcodeHandler::handleEq),
    NEQ(71, 1, 2, 1, OpcodeHandler::handleNeq),
    STRICT_EQ(72, 1, 2, 1, OpcodeHandler::handleStrictEq),
    STRICT_NEQ(73, 1, 2, 1, OpcodeHandler::handleStrictNeq),
    LT(74, 1, 2, 1, OpcodeHandler::handleLt),
    LTE(75, 1, 2, 1, OpcodeHandler::handleLte),
    GT(76, 1, 2, 1, OpcodeHandler::handleGt),
    GTE(77, 1, 2, 1, OpcodeHandler::handleGte),
    INSTANCEOF(78, 1, 2, 1, OpcodeHandler::handleInstanceof),
    IN(79, 1, 2, 1, OpcodeHandler::handleIn),

    // Variable access
    GET_VAR(80, 5, 0, 1, OpcodeHandler::handleGetVar),
    PUT_VAR(81, 5, 1, 0, OpcodeHandler::handlePutVar),
    GET_ARG(82, 3, 0, 1, OpcodeHandler::handleGetArg),
    PUT_ARG(83, 3, 1, 0, OpcodeHandler::handlePutArg),
    SET_ARG(84, 3, 1, 1, OpcodeHandler::handleSetArg),

    // Property access
    GET_FIELD(85, 5, 1, 1, OpcodeHandler::handleGetField),
    GET_FIELD2(86, 5, 1, 1, OpcodeHandler::handleInvalid),
    PUT_FIELD(87, 5, 2, 0, OpcodeHandler::handlePutField),
    GET_ARRAY_EL(88, 1, 2, 1, OpcodeHandler::handleGetArrayEl),
    PUT_ARRAY_EL(89, 1, 3, 0, OpcodeHandler::handlePutArrayEl),
    GET_SUPER_VALUE(90, 1, 3, 1, OpcodeHandler::handleGetSuperValue),
    PUT_SUPER_VALUE(91, 1, 4, 1, OpcodeHandler::handlePutSuperValue),

    // Control flow
    IF_FALSE(92, 5, 1, 0, OpcodeHandler::handleIfFalse),
    IF_TRUE(93, 5, 1, 0, OpcodeHandler::handleIfTrue),
    GOTO(94, 5, 0, 0, OpcodeHandler::handleGoto),
    CATCH(95, 5, 0, 1, OpcodeHandler::handleCatch),
    GOSUB(96, 5, 0, 0, OpcodeHandler::handleGosub),
    RET(97, 1, 1, 0, OpcodeHandler::handleRet),

    // Function operations
    DEFINE_METHOD(98, 5, 2, 1, OpcodeHandler::handleDefineMethod),
    DEFINE_CLASS(99, 5, 2, 1, OpcodeHandler::handleDefineClass),

    // Type operations
    TYPEOF(100, 1, 1, 1, OpcodeHandler::handleTypeof),
    DELETE(101, 1, 2, 1, OpcodeHandler::handleDelete),
    TO_OBJECT(102, 1, 1, 1, OpcodeHandler::handleToObject),
    TO_PROPKEY(103, 1, 1, 1, OpcodeHandler::handleToPropKey),

    // Scope operations
    WITH_GET_VAR(104, 10, 0, 1, OpcodeHandler::handleInvalid),
    WITH_PUT_VAR(105, 10, 1, 0, OpcodeHandler::handleInvalid),
    WITH_DELETE_VAR(106, 10, 0, 1, OpcodeHandler::handleInvalid),
    WITH_MAKE_REF(107, 10, 0, 2, OpcodeHandler::handleInvalid),
    WITH_GET_REF(108, 10, 1, 0, OpcodeHandler::handleInvalid),

    // Async operations
    AWAIT(109, 1, 1, 1, OpcodeHandler::handleAwait),
    RETURN_ASYNC(110, 1, 1, 0, OpcodeHandler::handleReturnAsync),  // Return from async function - pops value and returns
    FOR_AWAIT_OF_START(111, 1, 1, 3, OpcodeHandler::handleForAwaitOfStart),  // Start async iteration: iterable -> iter next catch_offset
    FOR_AWAIT_OF_NEXT(112, 1, 3, 4, OpcodeHandler::handleForAwaitOfNext),   // Get next from async iterator: iter next catch_offset -> iter next catch_offset obj

    // Sync iteration operations
    FOR_OF_START(113, 1, 1, 3, OpcodeHandler::handleForOfStart),  // Start sync iteration: iterable -> iter next catch_offset
    FOR_OF_NEXT(114, 2, 3, 5, OpcodeHandler::handleForOfNext),   // Get next from sync iterator: iter next catch_offset -> iter next catch_offset value done

    // Private field operations
    GET_PRIVATE_FIELD(115, 1, 2, 1, OpcodeHandler::handleGetPrivateField),     // Get private field: obj prop -> value
    PUT_PRIVATE_FIELD(116, 1, 3, 0, OpcodeHandler::handlePutPrivateField),     // Set private field: obj value prop ->
    DEFINE_PRIVATE_FIELD(117, 1, 3, 1, OpcodeHandler::handleDefinePrivateField),  // Define private field: obj prop value -> obj
    DEFINE_FIELD(118, 5, 2, 1, OpcodeHandler::handleDefineField),          // Define regular field: obj value -> obj (takes atom parameter)
    PRIVATE_IN(119, 1, 2, 1, OpcodeHandler::handlePrivateIn),            // Check if object has private field: obj prop -> boolean

    // For-in iteration operations (object enumeration)
    FOR_IN_START(120, 1, 1, 1, OpcodeHandler::handleForInStart),  // Start for-in enumeration: obj -> enum_obj
    FOR_IN_NEXT(121, 1, 1, 2, OpcodeHandler::handleForInNext),   // Get next property: enum_obj -> enum_obj key

    // Additional QuickJS opcodes
    DUP1(122, 1, 2, 3, OpcodeHandler::handleDup1),                  // a b -> a a b (QuickJS opcode 18)
    INIT_CTOR(123, 1, 0, 1, OpcodeHandler::handleInitCtor),             // Initialize constructor (QuickJS opcode 44)
    GET_VAR_UNDEF(124, 3, 0, 1, OpcodeHandler::handleGetVarUndef),         // Push undefined if variable doesn't exist (QuickJS opcode 55)
    PUT_VAR_INIT(125, 3, 1, 0, OpcodeHandler::handlePutVarInit),          // Initialize global lexical variable (QuickJS opcode 58)
    GET_REF_VALUE(126, 1, 2, 3, OpcodeHandler::handleGetRefValue),         // Get reference value (QuickJS opcode 59)
    PUT_REF_VALUE(127, 1, 3, 0, OpcodeHandler::handlePutRefValue),         // Put reference value (QuickJS opcode 60)
    GET_ARRAY_EL2(128, 1, 2, 2, OpcodeHandler::handleGetArrayEl2),         // obj prop -> obj value (QuickJS opcode 68)
    GET_ARRAY_EL3(129, 1, 2, 3, OpcodeHandler::handleGetArrayEl3),         // obj prop -> obj prop1 value (QuickJS opcode 69)
    SET_NAME(130, 5, 1, 1, OpcodeHandler::handleSetName),              // Set function name (QuickJS opcode 74)
    SET_NAME_COMPUTED(131, 1, 2, 2, OpcodeHandler::handleSetNameComputed),     // Set computed function name (QuickJS opcode 75)
    SET_PROTO(132, 1, 2, 1, OpcodeHandler::handleSetProto),             // Set prototype (QuickJS opcode 76)
    SET_HOME_OBJECT(133, 1, 2, 2, OpcodeHandler::handleSetHomeObject),       // Set home object for super (QuickJS opcode 77)
    DEFINE_ARRAY_EL(134, 1, 3, 2, OpcodeHandler::handleDefineArrayEl),       // Define array element (QuickJS opcode 78)
    APPEND(135, 1, 3, 2, OpcodeHandler::handleAppend),                // Append enumerated object, update length (QuickJS opcode 79)
    COPY_DATA_PROPERTIES(136, 2, 3, 3, OpcodeHandler::handleCopyDataProperties),  // Copy data properties (QuickJS opcode 80)
    DEFINE_METHOD_COMPUTED(137, 2, 3, 1, OpcodeHandler::handleDefineMethodComputed), // Define method with computed name (QuickJS opcode 82)
    DEFINE_CLASS_COMPUTED(138, 6, 3, 3, OpcodeHandler::handleDefineClassComputed),  // Define class with computed name (QuickJS opcode 84)
    GET_LOC(139, 3, 0, 1, OpcodeHandler::handleGetLoc),               // Get local variable (QuickJS opcode 85)
    PUT_LOC(140, 3, 1, 0, OpcodeHandler::handlePutLoc),               // Put local variable (QuickJS opcode 86)
    SET_LOC(141, 3, 1, 1, OpcodeHandler::handleSetLoc),               // Set local variable (QuickJS opcode 87)
    GET_VAR_REF(142, 3, 0, 1, OpcodeHandler::handleGetVarRef),           // Get variable reference (QuickJS opcode 91)
    PUT_VAR_REF(143, 3, 1, 0, OpcodeHandler::handlePutVarRef),           // Put variable reference (QuickJS opcode 92)
    SET_VAR_REF(144, 3, 1, 1, OpcodeHandler::handleSetVarRef),           // Set variable reference (QuickJS opcode 93)
    SET_LOC_UNINITIALIZED(145, 3, 0, 0, OpcodeHandler::handleSetLocUninitialized), // Set local uninitialized (QuickJS opcode 94)
    GET_LOC_CHECK(146, 3, 0, 1, OpcodeHandler::handleGetLocCheck),         // Get local with check (QuickJS opcode 95)
    PUT_LOC_CHECK(147, 3, 1, 0, OpcodeHandler::handlePutLocCheck),         // Put local with check (QuickJS opcode 96)
    SET_LOC_CHECK(148, 3, 1, 1, OpcodeHandler::handleSetLocCheck),         // Set local with check (QuickJS opcode 97)
    PUT_LOC_CHECK_INIT(149, 3, 1, 0, OpcodeHandler::handlePutLocCheckInit),    // Put local check init (QuickJS opcode 98)
    GET_LOC_CHECKTHIS(150, 3, 0, 1, OpcodeHandler::handleGetLocCheck),     // Get local check this (QuickJS opcode 99)
    GET_VAR_REF_CHECK(151, 3, 0, 1, OpcodeHandler::handleGetVarRefCheck),     // Get var ref with check (QuickJS opcode 100)
    PUT_VAR_REF_CHECK(152, 3, 1, 0, OpcodeHandler::handlePutVarRefCheck),     // Put var ref with check (QuickJS opcode 101)
    PUT_VAR_REF_CHECK_INIT(153, 3, 1, 0, OpcodeHandler::handlePutVarRefCheckInit), // Put var ref check init (QuickJS opcode 102)
    CLOSE_LOC(154, 3, 0, 0, OpcodeHandler::handleCloseLoc),             // Close local variable (QuickJS opcode 103)
    NIP_CATCH(155, 1, 2, 1, OpcodeHandler::handleNipCatch),             // catch ... a -> a (QuickJS opcode 110)

    // Type checking operations
    IS_UNDEFINED_OR_NULL(156, 1, 1, 1, OpcodeHandler::handleIsUndefinedOrNull),  // Check if value is null or undefined - replaces value with boolean

    // More QuickJS opcodes
    PUSH_BIGINT_I32(157, 5, 0, 1, OpcodeHandler::handlePushBigintI32),       // Push BigInt from i32 (QuickJS opcode 176)
    NOP(158, 1, 0, 0, OpcodeHandler::handleNop),                   // No operation (QuickJS opcode 177)
    MAKE_LOC_REF(159, 7, 0, 2, OpcodeHandler::handleMakeScopedRef),          // Make local reference (QuickJS opcode 118)
    MAKE_ARG_REF(160, 7, 0, 2, OpcodeHandler::handleMakeScopedRef),          // Make argument reference (QuickJS opcode 119)
    MAKE_VAR_REF_REF(161, 7, 0, 2, OpcodeHandler::handleMakeScopedRef),      // Make var ref reference (QuickJS opcode 120)
    MAKE_VAR_REF(162, 5, 0, 2, OpcodeHandler::handleMakeVarRef),          // Make var reference (QuickJS opcode 121)
    ITERATOR_CHECK_OBJECT(163, 1, 1, 1, OpcodeHandler::handleIteratorCheckObject), // Check if object is iterable (QuickJS opcode 128)
    ITERATOR_GET_VALUE_DONE(164, 1, 2, 3, OpcodeHandler::handleIteratorGetValueDone), // Get value and done from iterator result (QuickJS opcode 129)
    ITERATOR_CLOSE(165, 1, 3, 0, OpcodeHandler::handleIteratorClose),        // Close iterator (QuickJS opcode 130)
    ITERATOR_NEXT(166, 1, 4, 4, OpcodeHandler::handleIteratorNext),         // Get next from iterator (QuickJS opcode 131)
    ITERATOR_CALL(167, 2, 4, 5, OpcodeHandler::handleIteratorCall),         // Call iterator (QuickJS opcode 132)
    DEC_LOC(168, 2, 0, 0, OpcodeHandler::handleDecLoc),               // Decrement local (QuickJS opcode 144)
    INC_LOC(169, 2, 0, 0, OpcodeHandler::handleIncLoc),               // Increment local (QuickJS opcode 145)
    ADD_LOC(170, 2, 1, 0, OpcodeHandler::handleAddLoc),               // Add to local (QuickJS opcode 146)
    LNOT(171, 1, 1, 1, OpcodeHandler::handleLogicalNot),               // Logical not (QuickJS opcode 148)
    DELETE_VAR(172, 5, 0, 1, OpcodeHandler::handleDeleteVar),            // Delete variable (QuickJS opcode 151)
    POW(173, 1, 2, 1, OpcodeHandler::handleExp),                   // Power operation (QuickJS opcode 157)

    // SHORT_OPCODES
    PUSH_MINUS1(174, 1, 0, 1, OpcodeHandler::handlePushMinus1),           // Push -1 (QuickJS opcode 178)
    PUSH_0(175, 1, 0, 1, OpcodeHandler::handlePush0),                // Push 0 (QuickJS opcode 179)
    PUSH_1(176, 1, 0, 1, OpcodeHandler::handlePush1),                // Push 1 (QuickJS opcode 180)
    PUSH_2(177, 1, 0, 1, OpcodeHandler::handlePush2),                // Push 2 (QuickJS opcode 181)
    PUSH_3(178, 1, 0, 1, OpcodeHandler::handlePush3),                // Push 3 (QuickJS opcode 182)
    PUSH_4(179, 1, 0, 1, OpcodeHandler::handlePush4),                // Push 4 (QuickJS opcode 183)
    PUSH_5(180, 1, 0, 1, OpcodeHandler::handlePush5),                // Push 5 (QuickJS opcode 184)
    PUSH_6(181, 1, 0, 1, OpcodeHandler::handlePush6),                // Push 6 (QuickJS opcode 185)
    PUSH_7(182, 1, 0, 1, OpcodeHandler::handlePush7),                // Push 7 (QuickJS opcode 186)
    PUSH_I8(183, 2, 0, 1, OpcodeHandler::handlePushI8),               // Push i8 (QuickJS opcode 187)
    PUSH_I16(184, 3, 0, 1, OpcodeHandler::handlePushI16),              // Push i16 (QuickJS opcode 188)
    PUSH_CONST8(185, 2, 0, 1, OpcodeHandler::handlePushConst8),           // Push const8 (QuickJS opcode 189)
    FCLOSURE8(186, 2, 0, 1, OpcodeHandler::handleFclosure8),             // Function closure 8-bit (QuickJS opcode 190)
    PUSH_EMPTY_STRING(187, 1, 0, 1, OpcodeHandler::handlePushEmptyString),     // Push empty string (QuickJS opcode 191)
    GET_LOC8(188, 2, 0, 1, OpcodeHandler::handleGetLoc8),              // Get local 8-bit (QuickJS opcode 192)
    PUT_LOC8(189, 2, 1, 0, OpcodeHandler::handlePutLoc8),              // Put local 8-bit (QuickJS opcode 193)
    SET_LOC8(190, 2, 1, 1, OpcodeHandler::handleSetLoc8),              // Set local 8-bit (QuickJS opcode 194)
    GET_LOC0(191, 1, 0, 1, OpcodeHandler::handleGetLoc0),              // Get local 0 (QuickJS opcode 195)
    GET_LOC1(192, 1, 0, 1, OpcodeHandler::handleGetLoc1),              // Get local 1 (QuickJS opcode 196)
    GET_LOC2(193, 1, 0, 1, OpcodeHandler::handleGetLoc2),              // Get local 2 (QuickJS opcode 197)

    // Generator operations
    INITIAL_YIELD(194, 1, 0, 0, OpcodeHandler::handleInitialYield),  // Initial yield in generator - suspends generator at start
    YIELD(195, 1, 1, 2, OpcodeHandler::handleYield),          // Yield value from generator - pops value, yields it
    YIELD_STAR(196, 1, 1, 2, OpcodeHandler::handleYieldStar),     // Yield* delegating to another generator
    ASYNC_YIELD_STAR(197, 1, 1, 2, OpcodeHandler::handleAsyncYieldStar),  // Async yield* for async generators

    // More SHORT_OPCODES
    GET_LOC3(198, 1, 0, 1, OpcodeHandler::handleGetLoc3),              // Get local 3 (QuickJS opcode 198)
    PUT_LOC0(199, 1, 1, 0, OpcodeHandler::handlePutLoc0),              // Put local 0 (QuickJS opcode 199)
    PUT_LOC1(200, 1, 1, 0, OpcodeHandler::handlePutLoc1),              // Put local 1 (QuickJS opcode 200)
    PUT_LOC2(201, 1, 1, 0, OpcodeHandler::handlePutLoc2),              // Put local 2 (QuickJS opcode 201)
    PUT_LOC3(202, 1, 1, 0, OpcodeHandler::handlePutLoc3),              // Put local 3 (QuickJS opcode 202)
    SET_LOC0(203, 1, 1, 1, OpcodeHandler::handleSetLoc0),              // Set local 0 (QuickJS opcode 203)
    SET_LOC1(204, 1, 1, 1, OpcodeHandler::handleSetLoc1),              // Set local 1 (QuickJS opcode 204)
    SET_LOC2(205, 1, 1, 1, OpcodeHandler::handleSetLoc2),              // Set local 2 (QuickJS opcode 205)
    SET_LOC3(206, 1, 1, 1, OpcodeHandler::handleSetLoc3),              // Set local 3 (QuickJS opcode 206)
    GET_ARG0(207, 1, 0, 1, OpcodeHandler::handleGetArgShort),              // Get arg 0 (QuickJS opcode 207)
    GET_ARG1(208, 1, 0, 1, OpcodeHandler::handleGetArgShort),              // Get arg 1 (QuickJS opcode 208)
    GET_ARG2(209, 1, 0, 1, OpcodeHandler::handleGetArgShort),              // Get arg 2 (QuickJS opcode 209)
    GET_ARG3(210, 1, 0, 1, OpcodeHandler::handleGetArgShort),              // Get arg 3 (QuickJS opcode 210)
    PUT_ARG0(211, 1, 1, 0, OpcodeHandler::handlePutArgShort),              // Put arg 0 (QuickJS opcode 211)
    PUT_ARG1(212, 1, 1, 0, OpcodeHandler::handlePutArgShort),              // Put arg 1 (QuickJS opcode 212)
    PUT_ARG2(213, 1, 1, 0, OpcodeHandler::handlePutArgShort),              // Put arg 2 (QuickJS opcode 213)
    PUT_ARG3(214, 1, 1, 0, OpcodeHandler::handlePutArgShort),              // Put arg 3 (QuickJS opcode 214)
    SET_ARG0(215, 1, 1, 1, OpcodeHandler::handleSetArgShort),              // Set arg 0 (QuickJS opcode 215)
    SET_ARG1(216, 1, 1, 1, OpcodeHandler::handleSetArgShort),              // Set arg 1 (QuickJS opcode 216)
    SET_ARG2(217, 1, 1, 1, OpcodeHandler::handleSetArgShort),              // Set arg 2 (QuickJS opcode 217)
    SET_ARG3(218, 1, 1, 1, OpcodeHandler::handleSetArgShort),              // Set arg 3 (QuickJS opcode 218)
    GET_VAR_REF0(219, 1, 0, 1, OpcodeHandler::handleGetVarRefShort),          // Get var ref 0 (QuickJS opcode 219)
    GET_VAR_REF1(220, 1, 0, 1, OpcodeHandler::handleGetVarRefShort),          // Get var ref 1 (QuickJS opcode 220)
    GET_VAR_REF2(221, 1, 0, 1, OpcodeHandler::handleGetVarRefShort),          // Get var ref 2 (QuickJS opcode 221)
    GET_VAR_REF3(222, 1, 0, 1, OpcodeHandler::handleGetVarRefShort),          // Get var ref 3 (QuickJS opcode 222)
    PUT_VAR_REF0(223, 1, 1, 0, OpcodeHandler::handlePutVarRefShort),          // Put var ref 0 (QuickJS opcode 223)
    PUT_VAR_REF1(224, 1, 1, 0, OpcodeHandler::handlePutVarRefShort),          // Put var ref 1 (QuickJS opcode 224)
    PUT_VAR_REF2(225, 1, 1, 0, OpcodeHandler::handlePutVarRefShort),          // Put var ref 2 (QuickJS opcode 225)
    PUT_VAR_REF3(226, 1, 1, 0, OpcodeHandler::handlePutVarRefShort),          // Put var ref 3 (QuickJS opcode 226)
    SET_VAR_REF0(227, 1, 1, 1, OpcodeHandler::handleSetVarRefShort),          // Set var ref 0 (QuickJS opcode 227)
    SET_VAR_REF1(228, 1, 1, 1, OpcodeHandler::handleSetVarRefShort),          // Set var ref 1 (QuickJS opcode 228)
    SET_VAR_REF2(229, 1, 1, 1, OpcodeHandler::handleSetVarRefShort),          // Set var ref 2 (QuickJS opcode 229)
    SET_VAR_REF3(230, 1, 1, 1, OpcodeHandler::handleSetVarRefShort),          // Set var ref 3 (QuickJS opcode 230)
    GET_LENGTH(231, 1, 1, 1, OpcodeHandler::handleGetLength),            // Get length (QuickJS opcode 231)
    IF_FALSE8(232, 2, 1, 0, OpcodeHandler::handleIfFalse8),             // If false 8-bit (QuickJS opcode 232)
    IF_TRUE8(233, 2, 1, 0, OpcodeHandler::handleIfTrue8),              // If true 8-bit (QuickJS opcode 233)
    GOTO8(234, 2, 0, 0, OpcodeHandler::handleGoto8),                 // Goto 8-bit (QuickJS opcode 234)
    GOTO16(235, 3, 0, 0, OpcodeHandler::handleGoto16),                // Goto 16-bit (QuickJS opcode 235)
    CALL0(236, 1, 1, 1, OpcodeHandler::handleCall0),                 // Call with 0 args (QuickJS opcode 236)
    CALL1(237, 1, 1, 1, OpcodeHandler::handleCall1),                 // Call with 1 arg (QuickJS opcode 237)
    CALL2(238, 1, 1, 1, OpcodeHandler::handleCall2),                 // Call with 2 args (QuickJS opcode 238)
    CALL3(239, 1, 1, 1, OpcodeHandler::handleCall3),                 // Call with 3 args (QuickJS opcode 239)
    IS_UNDEFINED(240, 1, 1, 1, OpcodeHandler::handleIsUndefined),          // Is undefined (QuickJS opcode 240)
    IS_NULL(241, 1, 1, 1, OpcodeHandler::handleIsNull),               // Is null (QuickJS opcode 241)
    TYPEOF_IS_UNDEFINED(242, 1, 1, 1, OpcodeHandler::handleTypeofIsUndefined),   // Typeof is undefined (QuickJS opcode 242)
    TYPEOF_IS_FUNCTION(243, 1, 1, 1, OpcodeHandler::handleTypeofIsFunction);    // Typeof is function (QuickJS opcode 243)

    // Total: 244 opcodes

    private static final Opcode[] opcodes = new Opcode[244];  // Use fixed size to accommodate all opcode numbers

    static {
        Stream.of(values()).forEach(opcode -> opcodes[opcode.code] = opcode);
    }

    private final int code;
    private final Handler handler;
    private final int nPop;
    private final int nPush;
    private final int size;

    Opcode(int code, int size, int nPop, int nPush, Handler handler) {
        this.code = code;
        this.size = size;
        this.nPop = nPop;
        this.nPush = nPush;
        this.handler = handler;
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

    public Handler getHandler() {
        return handler;
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


    @FunctionalInterface
    public interface Handler {
        void call(Opcode op, ExecutionContext executionContext);
    }
}
