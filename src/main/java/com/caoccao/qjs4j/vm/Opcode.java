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
 * Enumeration of all JavaScript bytecode opcodes. Total: 244 opcodes (0-177 main, 178-243 SHORT_OPCODES).
 * <p>
 * Opcode numbering matches QuickJS (quickjs-opcode.h). Right-side comments show the QuickJS definition summary.
 */
public enum Opcode {
    /** a b -> a+b */
    ADD(155, 1, 2, 1, OpcodeHandler::handleAdd),
    /** this_obj home_obj -> */
    ADD_BRAND(46, 1, 2, 0, OpcodeHandler::handleInvalid),
    /** val -> (add to local variable, u8 loc index) */
    ADD_LOC(146, 2, 1, 0, OpcodeHandler::handleAddLoc),
    /** a b -&gt; a&amp;b (bitwise AND) */
    AND(171, 1, 2, 1, OpcodeHandler::handleAnd),
    /** array idx enum_obj -> array idx (append enumerated, update length) */
    APPEND(79, 1, 3, 2, OpcodeHandler::handleAppend),
    /** func this args -> ret */
    APPLY(39, 3, 3, 1, OpcodeHandler::handleApply),
    /** func array -> ret_eval */
    APPLY_EVAL(51, 3, 2, 1, OpcodeHandler::handleApplyEval),
    /** args -> array (npop args not counted) */
    ARRAY_FROM(38, 3, -1, 1, OpcodeHandler::handleArrayFrom),
    /** val -> sent_val resume_kind (async generator delegation) */
    ASYNC_YIELD_STAR(136, 1, 1, 2, OpcodeHandler::handleAsyncYieldStar),
    /** promise -> resolved_val (await expression) */
    AWAIT(137, 1, 1, 1, OpcodeHandler::handleAwait),
    /** func args -> ret (npop args not counted) */
    CALL(34, 3, -1, 1, OpcodeHandler::handleCall),
    /** func new.target args -> ret (npop args not counted) */
    CALL_CONSTRUCTOR(33, 3, -1, 1, OpcodeHandler::handleCallConstructor),
    /** func receiver args -> ret */
    CALL_METHOD(36, 3, -1, 1, OpcodeHandler::handleCallMethod),
    /** func -> ret (call with 0 args) */
    CALL0(236, 1, 1, 1, OpcodeHandler::handleCall0),
    /** func arg -> ret (call with 1 arg) */
    CALL1(237, 1, 1, 1, OpcodeHandler::handleCall1),
    /** func arg1 arg2 -> ret (call with 2 args) */
    CALL2(238, 1, 1, 1, OpcodeHandler::handleCall2),
    /** func arg1 arg2 arg3 -> ret (call with 3 args) */
    CALL3(239, 1, 1, 1, OpcodeHandler::handleCall3),
    /** -> val (set catch handler at label offset) */
    CATCH(107, 5, 0, 1, OpcodeHandler::handleCatch),
    /** this_obj func -> this_obj func (verify private brand) */
    CHECK_BRAND(45, 1, 2, 2, OpcodeHandler::handleInvalid),
    /** check that function is called as constructor */
    CHECK_CTOR(43, 1, 0, 0, OpcodeHandler::handleInvalid),
    /** check constructor return value */
    CHECK_CTOR_RETURN(42, 1, 1, 1, OpcodeHandler::handleInvalid),
    /** close over local variable (capture into closure) */
    CLOSE_LOC(103, 3, 0, 0, OpcodeHandler::handleCloseLoc),
    /** target source excludeList -> target source excludeList (u8 mask) */
    COPY_DATA_PROPERTIES(80, 2, 3, 3, OpcodeHandler::handleCopyDataProperties),
    /** val -> val-1 (prefix decrement value) */
    DEC(140, 1, 1, 1, OpcodeHandler::handleDec),
    /** decrement local variable in place (u8 loc index) */
    DEC_LOC(144, 2, 0, 0, OpcodeHandler::handleDecLoc),
    /** array idx val -> array idx (define indexed element) */
    DEFINE_ARRAY_EL(78, 1, 3, 2, OpcodeHandler::handleDefineArrayEl),
    /** parent ctor -> ctor proto (define class by atom) */
    DEFINE_CLASS(83, 5, 2, 1, OpcodeHandler::handleDefineClass),
    /** name parent ctor -> name ctor proto (class with computed name) */
    DEFINE_CLASS_COMPUTED(84, 6, 3, 3, OpcodeHandler::handleDefineClassComputed),
    /** obj val -> obj (define own property by atom) */
    DEFINE_FIELD(73, 5, 2, 1, OpcodeHandler::handleDefineField),
    /** obj func -> obj (define method by atom, u8 flags) */
    DEFINE_METHOD(81, 5, 2, 1, OpcodeHandler::handleDefineMethod),
    /** obj key func -> obj (define method with computed name, u8 flags) */
    DEFINE_METHOD_COMPUTED(82, 2, 3, 1, OpcodeHandler::handleDefineMethodComputed),
    /** obj prop value -> obj */
    DEFINE_PRIVATE_FIELD(66, 1, 3, 1, OpcodeHandler::handleDefinePrivateField),
    /** obj prop -> bool (delete property) */
    DELETE(150, 1, 2, 1, OpcodeHandler::handleDelete),
    /** -> bool (delete variable by atom name) */
    DELETE_VAR(151, 5, 0, 1, OpcodeHandler::handleDeleteVar),
    /** a b -> a/b */
    DIV(153, 1, 2, 1, OpcodeHandler::handleDiv),
    /** a -> */
    DROP(14, 1, 1, 0, OpcodeHandler::handleDrop),
    /** a -> a a */
    DUP(17, 1, 1, 2, OpcodeHandler::handleDup),
    /** a b -> a a b */
    DUP1(18, 1, 2, 3, OpcodeHandler::handleDup1),
    /** a b -> a b a b */
    DUP2(19, 1, 2, 4, OpcodeHandler::handleDup2),
    /** a b c -> a b c a b c */
    DUP3(20, 1, 3, 6, OpcodeHandler::handleDup3),
    /** a b -> a==b (abstract equality) */
    EQ(167, 1, 2, 1, OpcodeHandler::handleEq),
    /** func args... -> ret_val */
    EVAL(50, 5, -1, 1, OpcodeHandler::handleEval),
    /** create function closure from constant -> func */
    FCLOSURE(3, 5, 0, 1, OpcodeHandler::handleFclosure),
    /** -> func (create closure, u8 index) */
    FCLOSURE8(190, 2, 0, 1, OpcodeHandler::handleFclosure8),
    /** iter next catch -> iter next catch obj (await next) */
    FOR_AWAIT_OF_NEXT(127, 1, 3, 4, OpcodeHandler::handleForAwaitOfNext),
    /** iterable -> iter next catch_offset (start async iteration) */
    FOR_AWAIT_OF_START(124, 1, 1, 3, OpcodeHandler::handleForAwaitOfStart),
    /** enum_obj -> enum_obj key (get next property name) */
    FOR_IN_NEXT(125, 1, 1, 2, OpcodeHandler::handleForInNext),
    /** obj -> enum_obj (start for-in enumeration) */
    FOR_IN_START(122, 1, 1, 1, OpcodeHandler::handleForInStart),
    /** iter next catch -> iter next catch val done (u8 offset) */
    FOR_OF_NEXT(126, 2, 3, 5, OpcodeHandler::handleForOfNext),
    /** iterable -> iter next catch_offset (start sync iteration) */
    FOR_OF_START(123, 1, 1, 3, OpcodeHandler::handleForOfStart),
    /** -> val (get argument by u16 index) */
    GET_ARG(88, 3, 0, 1, OpcodeHandler::handleGetArg),
    /** -> val (get argument 0) */
    GET_ARG0(207, 1, 0, 1, OpcodeHandler::handleGetArgShort),
    /** -> val (get argument 1) */
    GET_ARG1(208, 1, 0, 1, OpcodeHandler::handleGetArgShort),
    /** -> val (get argument 2) */
    GET_ARG2(209, 1, 0, 1, OpcodeHandler::handleGetArgShort),
    /** -> val (get argument 3) */
    GET_ARG3(210, 1, 0, 1, OpcodeHandler::handleGetArgShort),
    /** obj prop -> val (get computed property) */
    GET_ARRAY_EL(67, 1, 2, 1, OpcodeHandler::handleGetArrayEl),
    /** obj prop -> obj val (get computed, keep object) */
    GET_ARRAY_EL2(68, 1, 2, 2, OpcodeHandler::handleGetArrayEl2),
    /** obj prop -> obj prop1 val */
    GET_ARRAY_EL3(69, 1, 2, 3, OpcodeHandler::handleGetArrayEl3),
    /** obj -> val (get named property by atom) */
    GET_FIELD(61, 5, 1, 1, OpcodeHandler::handleGetField),
    /** obj -> obj val (get field, keep object) */
    GET_FIELD2(62, 5, 1, 2, OpcodeHandler::handleGetField2),
    /** obj -> length (get length property) */
    GET_LENGTH(231, 1, 1, 1, OpcodeHandler::handleGetLength),
    /** -> val (get local variable by u16 index) */
    GET_LOC(85, 3, 0, 1, OpcodeHandler::handleGetLoc),
    /** -> val (get local, throw if uninitialized) */
    GET_LOC_CHECK(95, 3, 0, 1, OpcodeHandler::handleGetLocCheck),
    /** -> val (get local this, throw if uninitialized) */
    GET_LOC_CHECKTHIS(99, 3, 0, 1, OpcodeHandler::handleGetLocCheck),
    /** -> val (get local 0) */
    GET_LOC0(195, 1, 0, 1, OpcodeHandler::handleGetLoc0),
    /** -> val (get local 1) */
    GET_LOC1(196, 1, 0, 1, OpcodeHandler::handleGetLoc1),
    /** -> val (get local 2) */
    GET_LOC2(197, 1, 0, 1, OpcodeHandler::handleGetLoc2),
    /** -> val (get local 3) */
    GET_LOC3(198, 1, 0, 1, OpcodeHandler::handleGetLoc3),
    /** -> val (get local, u8 index) */
    GET_LOC8(192, 2, 0, 1, OpcodeHandler::handleGetLoc8),
    /** obj prop -> value */
    GET_PRIVATE_FIELD(64, 1, 2, 1, OpcodeHandler::handleGetPrivateField),
    /** obj prop -> obj prop val (get reference value) */
    GET_REF_VALUE(59, 1, 2, 3, OpcodeHandler::handleGetRefValue),
    /** obj -> super */
    GET_SUPER(53, 1, 1, 1, OpcodeHandler::handleGetSuper),
    /** this obj prop -> value */
    GET_SUPER_VALUE(71, 1, 3, 1, OpcodeHandler::handleGetSuperValue),
    /** throw exception if variable does not exist -> val */
    GET_VAR(56, 5, 0, 1, OpcodeHandler::handleGetVar),
    /** -> val (get closure variable by u16 index) */
    GET_VAR_REF(91, 3, 0, 1, OpcodeHandler::handleGetVarRef),
    /** -> val (get closure var, throw if uninitialized) */
    GET_VAR_REF_CHECK(100, 3, 0, 1, OpcodeHandler::handleGetVarRefCheck),
    /** -> val (get closure var 0) */
    GET_VAR_REF0(219, 1, 0, 1, OpcodeHandler::handleGetVarRefShort),
    /** -> val (get closure var 1) */
    GET_VAR_REF1(220, 1, 0, 1, OpcodeHandler::handleGetVarRefShort),
    /** -> val (get closure var 2) */
    GET_VAR_REF2(221, 1, 0, 1, OpcodeHandler::handleGetVarRefShort),
    /** -> val (get closure var 3) */
    GET_VAR_REF3(222, 1, 0, 1, OpcodeHandler::handleGetVarRefShort),
    /** push undefined if variable does not exist -> val */
    GET_VAR_UNDEF(55, 5, 0, 1, OpcodeHandler::handleGetVarUndef),
    /** jump to finally block (push return address) */
    GOSUB(108, 5, 0, 0, OpcodeHandler::handleGosub),
    /** unconditional jump (i32 label offset) */
    GOTO(106, 5, 0, 0, OpcodeHandler::handleGoto),
    /** unconditional jump (i16 offset) */
    GOTO16(235, 3, 0, 0, OpcodeHandler::handleGoto16),
    /** unconditional jump (i8 offset) */
    GOTO8(234, 2, 0, 0, OpcodeHandler::handleGoto8),
    /** a b -> a>b */
    GT(163, 1, 2, 1, OpcodeHandler::handleGt),
    /** a b -> a>=b */
    GTE(164, 1, 2, 1, OpcodeHandler::handleGte),
    /** val -> (branch if false, i32 label offset) */
    IF_FALSE(104, 5, 1, 0, OpcodeHandler::handleIfFalse),
    /** val -> (branch if false, i8 offset) */
    IF_FALSE8(232, 2, 1, 0, OpcodeHandler::handleIfFalse8),
    /** val -> (branch if true, i32 label offset) */
    IF_TRUE(105, 5, 1, 0, OpcodeHandler::handleIfTrue),
    /** val -> (branch if true, i8 offset) */
    IF_TRUE8(233, 2, 1, 0, OpcodeHandler::handleIfTrue8),
    /** specifier options -> promise (dynamic module import) */
    IMPORT(54, 1, 2, 1, OpcodeHandler::handleImport),
    /** prop obj -> bool (in operator) */
    IN(166, 1, 2, 1, OpcodeHandler::handleIn),
    /** val -> val+1 (prefix increment value) */
    INC(141, 1, 1, 1, OpcodeHandler::handleInc),
    /** increment local variable in place (u8 loc index) */
    INC_LOC(145, 2, 0, 0, OpcodeHandler::handleIncLoc),
    /** initialize constructor this object -> this */
    INIT_CTOR(44, 1, 0, 1, OpcodeHandler::handleInitCtor),
    /** suspend generator at start */
    INITIAL_YIELD(133, 1, 0, 0, OpcodeHandler::handleInitialYield),
    /** obj a -> a obj a (dup_x1) */
    INSERT2(21, 1, 2, 3, OpcodeHandler::handleInsert2),
    /** obj prop a -> a obj prop a (dup_x2) */
    INSERT3(22, 1, 3, 4, OpcodeHandler::handleInsert3),
    /** this obj prop a -> a this obj prop a */
    INSERT4(23, 1, 4, 5, OpcodeHandler::handleInsert4),
    /** obj ctor -> bool (instanceof operator) */
    INSTANCEOF(165, 1, 2, 1, OpcodeHandler::handleInstanceof),
    /** never emitted */
    INVALID(0, 1, 0, 0, OpcodeHandler::handleInvalid),
    /** val -> bool (test if null) */
    IS_NULL(241, 1, 1, 1, OpcodeHandler::handleIsNull),
    /** val -> bool (test if undefined) */
    IS_UNDEFINED(240, 1, 1, 1, OpcodeHandler::handleIsUndefined),
    /** val -> bool (test null or undefined) */
    IS_UNDEFINED_OR_NULL(174, 1, 1, 1, OpcodeHandler::handleIsUndefinedOrNull),
    /** iter next catch val -> iter next catch val ret (u8 method) */
    ITERATOR_CALL(132, 2, 4, 5, OpcodeHandler::handleIteratorCall),
    /** val -> val (check that iterator result is an object) */
    ITERATOR_CHECK_OBJECT(128, 1, 1, 1, OpcodeHandler::handleIteratorCheckObject),
    /** iter next catch -> (close iterator) */
    ITERATOR_CLOSE(130, 1, 3, 0, OpcodeHandler::handleIteratorClose),
    /** catch obj -> catch value done */
    ITERATOR_GET_VALUE_DONE(129, 1, 2, 3, OpcodeHandler::handleIteratorGetValueDone),
    /** iter next catch val -> iter next catch result (call next) */
    ITERATOR_NEXT(131, 1, 4, 4, OpcodeHandler::handleIteratorNext),
    /** val -> !val (logical NOT) */
    LNOT(148, 1, 1, 1, OpcodeHandler::handleLogicalNot),
    /** a b -&gt; a&lt;b */
    LT(161, 1, 2, 1, OpcodeHandler::handleLt),
    /** a b -&gt; a&lt;=b */
    LTE(162, 1, 2, 1, OpcodeHandler::handleLte),
    /** -> obj prop (make argument reference, atom+u16) */
    MAKE_ARG_REF(119, 7, 0, 2, OpcodeHandler::handleMakeScopedRef),
    /** -> obj prop (make local variable reference, atom+u16) */
    MAKE_LOC_REF(118, 7, 0, 2, OpcodeHandler::handleMakeScopedRef),
    /** -> obj prop (make global variable reference by atom) */
    MAKE_VAR_REF(121, 5, 0, 2, OpcodeHandler::handleMakeVarRef),
    /** -> obj prop (make closure var reference, atom+u16) */
    MAKE_VAR_REF_REF(120, 7, 0, 2, OpcodeHandler::handleMakeScopedRef),
    /** a b -> a%b */
    MOD(154, 1, 2, 1, OpcodeHandler::handleMod),
    /** a b -> a*b */
    MUL(152, 1, 2, 1, OpcodeHandler::handleMul),
    /** val -> -val (unary minus) */
    NEG(138, 1, 1, 1, OpcodeHandler::handleNeg),
    /** a b -> a!=b */
    NEQ(168, 1, 2, 1, OpcodeHandler::handleNeq),
    /** a b -> b */
    NIP(15, 1, 2, 1, OpcodeHandler::handleNip),
    /** catch ... a -> a (remove catch handler) */
    NIP_CATCH(110, 1, 2, 1, OpcodeHandler::handleNipCatch),
    /** a b c -> b c */
    NIP1(16, 1, 3, 2, OpcodeHandler::handleInvalid),
    /** no operation (last non-short opcode) */
    NOP(177, 1, 0, 0, OpcodeHandler::handleNop),
    /** val -> ~val (bitwise NOT) */
    NOT(147, 1, 1, 1, OpcodeHandler::handleNot),
    /** push null -> null */
    NULL(7, 1, 0, 1, OpcodeHandler::handleNull),
    /** create empty object -> obj */
    OBJECT(11, 1, 0, 1, OpcodeHandler::handleObjectNew),
    /** a b -> a|b (bitwise OR) */
    OR(173, 1, 2, 1, OpcodeHandler::handleOr),
    /** obj a b -> a obj b */
    PERM3(24, 1, 3, 3, OpcodeHandler::handlePerm3),
    /** obj prop a b -> a obj prop b */
    PERM4(25, 1, 4, 4, OpcodeHandler::handlePerm4),
    /** this obj prop a b -> a this obj prop b */
    PERM5(26, 1, 5, 5, OpcodeHandler::handlePerm5),
    /** val -> +val (unary plus, ToNumber) */
    PLUS(139, 1, 1, 1, OpcodeHandler::handlePlus),
    /** val -> val-1 (postfix decrement value) */
    POST_DEC(142, 1, 1, 1, OpcodeHandler::handlePostDec),
    /** val -> val+1 (postfix increment value) */
    POST_INC(143, 1, 1, 1, OpcodeHandler::handlePostInc),
    /** a b -> a**b */
    POW(157, 1, 2, 1, OpcodeHandler::handleExp),
    /** sym obj -> bool (private field existence check) */
    PRIVATE_IN(175, 1, 2, 1, OpcodeHandler::handlePrivateIn),
    /** push private symbol from atom -> sym */
    PRIVATE_SYMBOL(5, 5, 0, 1, OpcodeHandler::handlePrivateSymbol),
    /** -> 0 (push integer 0) */
    PUSH_0(179, 1, 0, 1, OpcodeHandler::handlePush0),
    /** -> 1 (push integer 1) */
    PUSH_1(180, 1, 0, 1, OpcodeHandler::handlePush1),
    /** -> 2 (push integer 2) */
    PUSH_2(181, 1, 0, 1, OpcodeHandler::handlePush2),
    /** -> 3 (push integer 3) */
    PUSH_3(182, 1, 0, 1, OpcodeHandler::handlePush3),
    /** -> 4 (push integer 4) */
    PUSH_4(183, 1, 0, 1, OpcodeHandler::handlePush4),
    /** -> 5 (push integer 5) */
    PUSH_5(184, 1, 0, 1, OpcodeHandler::handlePush5),
    /** -> 6 (push integer 6) */
    PUSH_6(185, 1, 0, 1, OpcodeHandler::handlePush6),
    /** -> 7 (push integer 7) */
    PUSH_7(186, 1, 0, 1, OpcodeHandler::handlePush7),
    /** push atom as string value -> val */
    PUSH_ATOM_VALUE(4, 5, 0, 1, OpcodeHandler::handlePushAtomValue),
    /** push BigInt from i32 immediate -> bigint */
    PUSH_BIGINT_I32(176, 5, 0, 1, OpcodeHandler::handlePushBigintI32),
    /** push constant from pool -> val */
    PUSH_CONST(2, 5, 0, 1, OpcodeHandler::handlePushConst),
    /** -> val (push constant, u8 index) */
    PUSH_CONST8(189, 2, 0, 1, OpcodeHandler::handlePushConst8),
    /** -> "" (push empty string) */
    PUSH_EMPTY_STRING(191, 1, 0, 1, OpcodeHandler::handlePushEmptyString),
    /** push false -> false */
    PUSH_FALSE(9, 1, 0, 1, OpcodeHandler::handlePushFalse),
    /** -> val (push i16 immediate) */
    PUSH_I16(188, 3, 0, 1, OpcodeHandler::handlePushI16),
    /** push i32 immediate -> val */
    PUSH_I32(1, 5, 0, 1, OpcodeHandler::handlePushI32),
    /** -> val (push i8 immediate) */
    PUSH_I8(187, 2, 0, 1, OpcodeHandler::handlePushI8),
    // SHORT_OPCODES — compact 1-2 byte variants of common operations
    /** -> -1 (push integer -1) */
    PUSH_MINUS1(178, 1, 0, 1, OpcodeHandler::handlePushMinus1),
    /** push this value -> this (only at function start) */
    PUSH_THIS(8, 1, 0, 1, OpcodeHandler::handlePushThis),
    /** push true -> true */
    PUSH_TRUE(10, 1, 0, 1, OpcodeHandler::handlePushTrue),
    /** val -> (put argument) */
    PUT_ARG(89, 3, 1, 0, OpcodeHandler::handlePutArg),
    /** val -> (put argument 0) */
    PUT_ARG0(211, 1, 1, 0, OpcodeHandler::handlePutArgShort),
    /** val -> (put argument 1) */
    PUT_ARG1(212, 1, 1, 0, OpcodeHandler::handlePutArgShort),
    /** val -> (put argument 2) */
    PUT_ARG2(213, 1, 1, 0, OpcodeHandler::handlePutArgShort),
    /** val -> (put argument 3) */
    PUT_ARG3(214, 1, 1, 0, OpcodeHandler::handlePutArgShort),
    /** obj prop val -> (set computed property) */
    PUT_ARRAY_EL(70, 1, 3, 0, OpcodeHandler::handlePutArrayEl),
    /** obj val -> (set named property by atom) */
    PUT_FIELD(63, 5, 2, 0, OpcodeHandler::handlePutField),
    /** val -> (put local variable) */
    PUT_LOC(86, 3, 1, 0, OpcodeHandler::handlePutLoc),
    /** val -> (put local, throw if uninitialized) */
    PUT_LOC_CHECK(96, 3, 1, 0, OpcodeHandler::handlePutLocCheck),
    /** val -> (put local, initialize if needed) */
    PUT_LOC_CHECK_INIT(98, 3, 1, 0, OpcodeHandler::handlePutLocCheckInit),
    /** val -> (put local 0) */
    PUT_LOC0(199, 1, 1, 0, OpcodeHandler::handlePutLoc0),
    /** val -> (put local 1) */
    PUT_LOC1(200, 1, 1, 0, OpcodeHandler::handlePutLoc1),
    /** val -> (put local 2) */
    PUT_LOC2(201, 1, 1, 0, OpcodeHandler::handlePutLoc2),
    /** val -> (put local 3) */
    PUT_LOC3(202, 1, 1, 0, OpcodeHandler::handlePutLoc3),
    /** val -> (put local, u8 index) */
    PUT_LOC8(193, 2, 1, 0, OpcodeHandler::handlePutLoc8),
    /** obj value prop -> */
    PUT_PRIVATE_FIELD(65, 1, 3, 0, OpcodeHandler::handlePutPrivateField),
    /** obj prop val -> (put reference value) */
    PUT_REF_VALUE(60, 1, 3, 0, OpcodeHandler::handlePutRefValue),
    /** this obj prop value -> */
    PUT_SUPER_VALUE(72, 1, 4, 1, OpcodeHandler::handlePutSuperValue),
    /** val -> (store to variable) */
    PUT_VAR(57, 5, 1, 0, OpcodeHandler::handlePutVar),
    /** val -> (initialize global lexical variable) */
    PUT_VAR_INIT(58, 3, 1, 0, OpcodeHandler::handlePutVarInit),

    /** val -> (put closure variable) */
    PUT_VAR_REF(92, 3, 1, 0, OpcodeHandler::handlePutVarRef),
    /** val -> (put closure var, throw if uninitialized) */
    PUT_VAR_REF_CHECK(101, 3, 1, 0, OpcodeHandler::handlePutVarRefCheck),
    /** val -> (put closure var, initialize if needed) */
    PUT_VAR_REF_CHECK_INIT(102, 3, 1, 0, OpcodeHandler::handlePutVarRefCheckInit),
    /** val -> (put closure var 0) */
    PUT_VAR_REF0(223, 1, 1, 0, OpcodeHandler::handlePutVarRefShort),
    /** val -> (put closure var 1) */
    PUT_VAR_REF1(224, 1, 1, 0, OpcodeHandler::handlePutVarRefShort),
    /** val -> (put closure var 2) */
    PUT_VAR_REF2(225, 1, 1, 0, OpcodeHandler::handlePutVarRefShort),
    /** val -> (put closure var 3) */
    PUT_VAR_REF3(226, 1, 1, 0, OpcodeHandler::handlePutVarRefShort),
    /** create new RegExp from constant template */
    REGEXP(52, 5, 0, 1, OpcodeHandler::handleRegexp),
    /** collect rest parameters (u16 index) -> array (only at function start) */
    REST(13, 3, 0, 1, OpcodeHandler::handleRest),
    /** addr -> (return from finally block) */
    RET(109, 1, 1, 0, OpcodeHandler::handleRet),
    /** val -> */
    RETURN(40, 1, 1, 0, OpcodeHandler::handleReturn),
    /** return from async function, resolve promise */
    RETURN_ASYNC(47, 1, 1, 0, OpcodeHandler::handleReturnAsync),
    /** return undefined from function */
    RETURN_UNDEF(41, 1, 0, 0, OpcodeHandler::handleReturnUndef),
    /** x a b -> a b x */
    ROT3L(29, 1, 3, 3, OpcodeHandler::handleRot3l),
    /** a b x -> x a b */
    ROT3R(30, 1, 3, 3, OpcodeHandler::handleRot3r),
    /** x a b c -> a b c x */
    ROT4L(31, 1, 4, 4, OpcodeHandler::handleRot4l),
    /** x a b c d -> a b c d x */
    ROT5L(32, 1, 5, 5, OpcodeHandler::handleRot5l),
    /** a b -> a>>b (signed right shift) */
    SAR(159, 1, 2, 1, OpcodeHandler::handleSar),
    /** val -> val (set argument, keep value) */
    SET_ARG(90, 3, 1, 1, OpcodeHandler::handleSetArg),
    /** val -> val (set argument 0) */
    SET_ARG0(215, 1, 1, 1, OpcodeHandler::handleSetArgShort),
    /** val -> val (set argument 1) */
    SET_ARG1(216, 1, 1, 1, OpcodeHandler::handleSetArgShort),
    /** val -> val (set argument 2) */
    SET_ARG2(217, 1, 1, 1, OpcodeHandler::handleSetArgShort),
    /** val -> val (set argument 3) */
    SET_ARG3(218, 1, 1, 1, OpcodeHandler::handleSetArgShort),
    /** obj func -> obj func (set home object for super) */
    SET_HOME_OBJECT(77, 1, 2, 2, OpcodeHandler::handleSetHomeObject),
    /** val -> val (set local, keep value) */
    SET_LOC(87, 3, 1, 1, OpcodeHandler::handleSetLoc),
    /** val -> val (set local with TDZ check) */
    SET_LOC_CHECK(97, 3, 1, 1, OpcodeHandler::handleSetLocCheck),
    /** mark local as uninitialized (TDZ) */
    SET_LOC_UNINITIALIZED(94, 3, 0, 0, OpcodeHandler::handleSetLocUninitialized),
    /** val -> val (set local 0) */
    SET_LOC0(203, 1, 1, 1, OpcodeHandler::handleSetLoc0),
    /** val -> val (set local 1) */
    SET_LOC1(204, 1, 1, 1, OpcodeHandler::handleSetLoc1),
    /** val -> val (set local 2) */
    SET_LOC2(205, 1, 1, 1, OpcodeHandler::handleSetLoc2),
    /** val -> val (set local 3) */
    SET_LOC3(206, 1, 1, 1, OpcodeHandler::handleSetLoc3),
    /** val -> val (set local, u8 index) */
    SET_LOC8(194, 2, 1, 1, OpcodeHandler::handleSetLoc8),
    /** func -> func (set function name by atom) */
    SET_NAME(74, 5, 1, 1, OpcodeHandler::handleSetName),
    /** name func -> name func (set computed function name) */
    SET_NAME_COMPUTED(75, 1, 2, 2, OpcodeHandler::handleSetNameComputed),
    /** obj proto -> obj (set prototype) */
    SET_PROTO(76, 1, 2, 1, OpcodeHandler::handleSetProto),
    /** val -> val (set closure variable, keep value) */
    SET_VAR_REF(93, 3, 1, 1, OpcodeHandler::handleSetVarRef),
    /** val -> val (set closure var 0) */
    SET_VAR_REF0(227, 1, 1, 1, OpcodeHandler::handleSetVarRefShort),
    /** val -> val (set closure var 1) */
    SET_VAR_REF1(228, 1, 1, 1, OpcodeHandler::handleSetVarRefShort),
    /** val -> val (set closure var 2) */
    SET_VAR_REF2(229, 1, 1, 1, OpcodeHandler::handleSetVarRefShort),
    /** val -> val (set closure var 3) */
    SET_VAR_REF3(230, 1, 1, 1, OpcodeHandler::handleSetVarRefShort),
    /** a b -&gt; a&lt;&lt;b */
    SHL(158, 1, 2, 1, OpcodeHandler::handleShl),
    /** a b -> a>>>b (unsigned right shift) */
    SHR(160, 1, 2, 1, OpcodeHandler::handleShr),
    /** create special object (u8 type) -> obj (only at function start) */
    SPECIAL_OBJECT(12, 2, 0, 1, OpcodeHandler::handleSpecialObject),
    /** a b -> a===b (strict equality) */
    STRICT_EQ(169, 1, 2, 1, OpcodeHandler::handleStrictEq),
    /** a b -> a!==b */
    STRICT_NEQ(170, 1, 2, 1, OpcodeHandler::handleStrictNeq),
    /** a b -> a-b */
    SUB(156, 1, 2, 1, OpcodeHandler::handleSub),
    /** a b -> b a */
    SWAP(27, 1, 2, 2, OpcodeHandler::handleSwap),
    /** a b c d -> c d a b */
    SWAP2(28, 1, 4, 4, OpcodeHandler::handleSwap2),
    /** func args -> (npop args not counted) */
    TAIL_CALL(35, 3, -1, 0, OpcodeHandler::handleTailCall),
    /** func receiver args -> (tail call) */
    TAIL_CALL_METHOD(37, 3, -1, 0, OpcodeHandler::handleTailCallMethod),
    /** val -> (throw exception) */
    THROW(48, 1, 1, 0, OpcodeHandler::handleThrow),
    /** throw error by atom name and u8 type */
    THROW_ERROR(49, 6, 0, 0, OpcodeHandler::handleThrowError),
    /** val -> obj (convert to object) */
    TO_OBJECT(111, 1, 1, 1, OpcodeHandler::handleToObject),
    /** val -> key (convert to property key) */
    TO_PROPKEY(112, 1, 1, 1, OpcodeHandler::handleToPropKey),
    /** val -> type_string (typeof operator) */
    TYPEOF(149, 1, 1, 1, OpcodeHandler::handleTypeof),
    /** val -> bool (typeof === "function") */
    TYPEOF_IS_FUNCTION(243, 1, 1, 1, OpcodeHandler::handleTypeofIsFunction),
    /** val -> bool (typeof === "undefined") */
    TYPEOF_IS_UNDEFINED(242, 1, 1, 1, OpcodeHandler::handleTypeofIsUndefined),
    /** push undefined -> undefined */
    UNDEFINED(6, 1, 0, 1, OpcodeHandler::handleUndefined),
    /** delete variable in with scope (atom, label, u8) */
    WITH_DELETE_VAR(115, 10, 0, 1, OpcodeHandler::handleInvalid),
    /** get reference in with scope (atom, label, u8) */
    WITH_GET_REF(117, 10, 1, 0, OpcodeHandler::handleInvalid),
    /** get variable in with scope (atom, label, u8) */
    WITH_GET_VAR(113, 10, 0, 1, OpcodeHandler::handleInvalid),
    /** make reference in with scope (atom, label, u8) */
    WITH_MAKE_REF(116, 10, 0, 2, OpcodeHandler::handleInvalid),
    /** put variable in with scope (atom, label, u8) */
    WITH_PUT_VAR(114, 10, 1, 0, OpcodeHandler::handleInvalid),
    /** a b -> a^b (bitwise XOR) */
    XOR(172, 1, 2, 1, OpcodeHandler::handleXor),
    /** val -> sent_val resume_kind (yield value) */
    YIELD(134, 1, 1, 2, OpcodeHandler::handleYield),
    /** val -> sent_val resume_kind (delegate to iterable) */
    YIELD_STAR(135, 1, 1, 2, OpcodeHandler::handleYieldStar);

    // Total: 244 opcodes

    private static final Opcode[] opcodes = new Opcode[244]; // Use fixed size to accommodate all opcode numbers

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

    public static Opcode fromInt(int code) {
        if (code >= 0 && code < opcodes.length) {
            return opcodes[code];
        }
        return INVALID;
    }

    @FunctionalInterface
    public interface Handler {
        void call(Opcode op, ExecutionContext executionContext);
    }
}
