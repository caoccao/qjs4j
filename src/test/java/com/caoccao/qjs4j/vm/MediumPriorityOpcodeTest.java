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

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.compiler.BytecodeEmitter;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MediumPriorityOpcodeTest extends BaseTest {
    private JSValue execute(
            BytecodeEmitter emitter,
            int localCount,
            JSValue[] closureVars,
            JSValue thisArg,
            JSValue... args) {
        return executeWithStrict(true, emitter, localCount, closureVars, thisArg, args);
    }

    private JSValue executeWithStrict(
            boolean strict,
            BytecodeEmitter emitter,
            int localCount,
            JSValue[] closureVars,
            JSValue thisArg,
            JSValue... args) {
        JSBytecodeFunction function = new JSBytecodeFunction(
                emitter.build(localCount),
                "test",
                args.length,
                closureVars,
                null,
                true,
                false,
                false,
                false,
                strict,
                "function test() { [bytecode] }");
        return context.getVirtualMachine().execute(function, thisArg, args);
    }

    @Test
    public void testCopyDataProperties() {
        JSObject target = context.createJSObject();
        JSObject source = context.createJSObject();
        source.set("a", new JSNumber(1));
        source.set("b", new JSNumber(2));
        source.defineProperty(
                PropertyKey.fromString("hidden"),
                PropertyDescriptor.dataDescriptor(new JSNumber(99), true, false, true));
        JSSymbol symbol = new JSSymbol("sym");
        source.defineProperty(
                PropertyKey.fromSymbol(symbol),
                PropertyDescriptor.dataDescriptor(new JSNumber(7), true, true, true));

        JSArray excludeList = context.createJSArray();
        excludeList.push(new JSString("a"));
        excludeList.push(symbol);

        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, target);
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, source);
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, excludeList);
        // target offset=2, source offset=1, exclude offset=0 => 0b00000110
        emitter.emitOpcodeU8(Opcode.COPY_DATA_PROPERTIES, 6);
        emitter.emitOpcode(Opcode.DROP);
        emitter.emitOpcode(Opcode.DROP);
        emitter.emitOpcode(Opcode.RETURN);

        JSValue copied = execute(emitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(copied).isSameAs(target);
        assertThat(target.get("a")).isSameAs(JSUndefined.INSTANCE);
        assertThat(target.get("b")).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) target.get("b")).value()).isEqualTo(2);
        assertThat(target.get("hidden")).isSameAs(JSUndefined.INSTANCE);
        assertThat(target.get(PropertyKey.fromSymbol(symbol))).isSameAs(JSUndefined.INSTANCE);

        BytecodeEmitter typeErrorEmitter = new BytecodeEmitter();
        typeErrorEmitter.emitOpcodeU32(Opcode.PUSH_I32, 1);
        typeErrorEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, source);
        typeErrorEmitter.emitOpcode(Opcode.NULL);
        typeErrorEmitter.emitOpcodeU8(Opcode.COPY_DATA_PROPERTIES, 6);
        typeErrorEmitter.emitOpcode(Opcode.RETURN_UNDEF);

        assertThatThrownBy(() -> execute(typeErrorEmitter, 0, new JSValue[0], JSUndefined.INSTANCE))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("copy target must be an object");
    }

    @Test
    public void testDefineClassComputed() {
        JSNativeFunction constructor = new JSNativeFunction("old", 0, (ctx, thisArg, args) -> JSUndefined.INSTANCE);

        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("DynamicClass"));
        emitter.emitOpcode(Opcode.UNDEFINED);
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, constructor);
        emitter.emitOpcode(Opcode.DEFINE_CLASS_COMPUTED);
        emitter.emitAtom("FallbackClass");
        emitter.emitU8(0);
        emitter.emitOpcode(Opcode.RETURN);

        JSValue classCtorValue = execute(emitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(classCtorValue).isSameAs(constructor);
        assertThat(classCtorValue).isInstanceOf(JSObject.class);
        JSObject classCtor = (JSObject) classCtorValue;
        assertThat(classCtor.get("name")).isInstanceOf(JSString.class);
        assertThat(((JSString) classCtor.get("name")).value()).isEqualTo("DynamicClass");
        assertThat(classCtor.get("prototype")).isInstanceOf(JSObject.class);

        BytecodeEmitter fallbackEmitter = new BytecodeEmitter();
        fallbackEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(""));
        fallbackEmitter.emitOpcode(Opcode.UNDEFINED);
        fallbackEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, constructor);
        fallbackEmitter.emitOpcode(Opcode.DEFINE_CLASS_COMPUTED);
        fallbackEmitter.emitAtom("FallbackClass");
        fallbackEmitter.emitU8(0);
        fallbackEmitter.emitOpcode(Opcode.RETURN);

        JSValue fallbackCtor = execute(fallbackEmitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(fallbackCtor).isSameAs(constructor);
        assertThat(((JSString) ((JSObject) fallbackCtor).get("name")).value()).isEqualTo("FallbackClass");
    }

    @Test
    public void testDefineMethodComputed() {
        JSObject target = context.createJSObject();
        JSNativeFunction method = new JSNativeFunction("m", 0, (ctx, thisArg, args) -> new JSNumber(1));
        JSNativeFunction getter = new JSNativeFunction("g", 0, (ctx, thisArg, args) -> new JSNumber(42));
        JSNativeFunction setter = new JSNativeFunction("s", 1, (ctx, thisArg, args) -> JSUndefined.INSTANCE);

        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, target);
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("m"));
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, method);
        emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, 4);
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("x"));
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, getter);
        emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, 1);
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("x"));
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, setter);
        emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, 2);
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("x"));
        emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        emitter.emitOpcode(Opcode.RETURN);

        JSValue getterValue = execute(emitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(getterValue).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) getterValue).value()).isEqualTo(42);

        PropertyDescriptor methodDescriptor = target.getOwnPropertyDescriptor(PropertyKey.fromString("m"));
        assertThat(methodDescriptor).isNotNull();
        assertThat(methodDescriptor.isEnumerable()).isTrue();
        assertThat(target.get("m")).isSameAs(method);
        assertThat(((JSFunction) method).getHomeObject()).isSameAs(target);
        assertThat(((JSString) method.get("name")).value()).isEqualTo("m");

        PropertyDescriptor accessorDescriptor = target.getOwnPropertyDescriptor(PropertyKey.fromString("x"));
        assertThat(accessorDescriptor).isNotNull();
        assertThat(accessorDescriptor.hasGetter()).isTrue();
        assertThat(accessorDescriptor.hasSetter()).isTrue();
        assertThat(accessorDescriptor.getGetter()).isSameAs(getter);
        assertThat(accessorDescriptor.getSetter()).isSameAs(setter);
        assertThat(((JSString) getter.get("name")).value()).isEqualTo("get x");
        assertThat(((JSString) setter.get("name")).value()).isEqualTo("set x");
    }

    @Test
    public void testGetArrayEl2AndGetLength() {
        JSArray array = context.createJSArray(new JSNumber(10), new JSNumber(20));

        BytecodeEmitter getArrayEl2Emitter = new BytecodeEmitter();
        getArrayEl2Emitter.emitOpcodeConstant(Opcode.PUSH_CONST, array);
        getArrayEl2Emitter.emitOpcodeU32(Opcode.PUSH_I32, 1);
        getArrayEl2Emitter.emitOpcode(Opcode.GET_ARRAY_EL2);
        getArrayEl2Emitter.emitOpcode(Opcode.SWAP);
        getArrayEl2Emitter.emitOpcode(Opcode.GET_LENGTH);
        getArrayEl2Emitter.emitOpcode(Opcode.ADD);
        getArrayEl2Emitter.emitOpcode(Opcode.RETURN);

        JSValue getArrayEl2Result = execute(getArrayEl2Emitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(getArrayEl2Result).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) getArrayEl2Result).value()).isEqualTo(22);

        BytecodeEmitter getLengthStringEmitter = new BytecodeEmitter();
        getLengthStringEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("abcd"));
        getLengthStringEmitter.emitOpcode(Opcode.GET_LENGTH);
        getLengthStringEmitter.emitOpcode(Opcode.RETURN);

        JSValue getLengthStringResult = execute(getLengthStringEmitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(getLengthStringResult).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) getLengthStringResult).value()).isEqualTo(4);
    }

    @Test
    public void testMakeRefOpcodes() {
        BytecodeEmitter locRefEmitter = new BytecodeEmitter();
        locRefEmitter.emitOpcodeU32(Opcode.PUSH_I32, 3);
        locRefEmitter.emitOpcodeU16(Opcode.PUT_LOC, 0);
        locRefEmitter.emitOpcode(Opcode.MAKE_LOC_REF);
        locRefEmitter.emitAtom("x");
        locRefEmitter.emitU16(0);
        locRefEmitter.emitOpcodeU32(Opcode.PUSH_I32, 9);
        locRefEmitter.emitOpcode(Opcode.PUT_REF_VALUE);
        locRefEmitter.emitOpcodeU16(Opcode.GET_LOC, 0);
        locRefEmitter.emitOpcode(Opcode.RETURN);

        JSValue locRefResult = execute(locRefEmitter, 1, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(locRefResult).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) locRefResult).value()).isEqualTo(9);

        BytecodeEmitter argRefEmitter = new BytecodeEmitter();
        argRefEmitter.emitOpcode(Opcode.MAKE_ARG_REF);
        argRefEmitter.emitAtom("arg0");
        argRefEmitter.emitU16(0);
        argRefEmitter.emitOpcodeU32(Opcode.PUSH_I32, 12);
        argRefEmitter.emitOpcode(Opcode.PUT_REF_VALUE);
        argRefEmitter.emitOpcode(Opcode.MAKE_ARG_REF);
        argRefEmitter.emitAtom("arg0");
        argRefEmitter.emitU16(0);
        argRefEmitter.emitOpcode(Opcode.GET_REF_VALUE);
        argRefEmitter.emitOpcode(Opcode.RETURN);

        JSValue argRefResult = execute(argRefEmitter, 0, new JSValue[0], JSUndefined.INSTANCE, new JSNumber(5));
        assertThat(argRefResult).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) argRefResult).value()).isEqualTo(12);

        BytecodeEmitter varRefRefEmitter = new BytecodeEmitter();
        varRefRefEmitter.emitOpcode(Opcode.MAKE_VAR_REF_REF);
        varRefRefEmitter.emitAtom("v");
        varRefRefEmitter.emitU16(0);
        varRefRefEmitter.emitOpcodeU32(Opcode.PUSH_I32, 15);
        varRefRefEmitter.emitOpcode(Opcode.PUT_REF_VALUE);
        varRefRefEmitter.emitOpcodeU16(Opcode.GET_VAR_REF, 0);
        varRefRefEmitter.emitOpcode(Opcode.RETURN);

        JSValue[] closureVars = new JSValue[]{new JSNumber(1)};
        JSValue varRefRefResult = execute(varRefRefEmitter, 0, closureVars, JSUndefined.INSTANCE);
        assertThat(varRefRefResult).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) varRefRefResult).value()).isEqualTo(15);

        context.getGlobalObject().set("g", new JSNumber(0));
        BytecodeEmitter varRefEmitter = new BytecodeEmitter();
        varRefEmitter.emitOpcodeAtom(Opcode.MAKE_VAR_REF, "g");
        varRefEmitter.emitOpcodeU32(Opcode.PUSH_I32, 7);
        varRefEmitter.emitOpcode(Opcode.PUT_REF_VALUE);
        varRefEmitter.emitOpcodeAtom(Opcode.GET_VAR, "g");
        varRefEmitter.emitOpcode(Opcode.RETURN);

        JSValue varRefResult = execute(varRefEmitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(varRefResult).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) varRefResult).value()).isEqualTo(7);
    }

    @Test
    public void testNipCatch() {
        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitOpcode(Opcode.CATCH);
        emitter.emitI32(0);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 1);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 2);
        emitter.emitOpcode(Opcode.NIP_CATCH);
        emitter.emitOpcode(Opcode.RETURN);

        JSValue result = execute(emitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(result).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) result).value()).isEqualTo(2);

        BytecodeEmitter noCatchEmitter = new BytecodeEmitter();
        noCatchEmitter.emitOpcodeU32(Opcode.PUSH_I32, 1);
        noCatchEmitter.emitOpcode(Opcode.NIP_CATCH);
        noCatchEmitter.emitOpcode(Opcode.RETURN);

        assertThatThrownBy(() -> execute(noCatchEmitter, 0, new JSValue[0], JSUndefined.INSTANCE))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("nip_catch");
    }

    @Test
    public void testRefValueOpcodes() {
        JSObject obj = context.createJSObject();
        obj.set("x", new JSNumber(1));

        BytecodeEmitter getRefEmitter = new BytecodeEmitter();
        getRefEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, obj);
        getRefEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("x"));
        getRefEmitter.emitOpcode(Opcode.GET_REF_VALUE);
        getRefEmitter.emitOpcode(Opcode.RETURN);

        JSValue getRefResult = execute(getRefEmitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(getRefResult).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) getRefResult).value()).isEqualTo(1);

        BytecodeEmitter putRefEmitter = new BytecodeEmitter();
        putRefEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, obj);
        putRefEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("x"));
        putRefEmitter.emitOpcodeU32(Opcode.PUSH_I32, 5);
        putRefEmitter.emitOpcode(Opcode.PUT_REF_VALUE);
        putRefEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, obj);
        putRefEmitter.emitOpcodeAtom(Opcode.GET_FIELD, "x");
        putRefEmitter.emitOpcode(Opcode.RETURN);

        JSValue putRefResult = execute(putRefEmitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(putRefResult).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) putRefResult).value()).isEqualTo(5);

        BytecodeEmitter nonStrictGlobalEmitter = new BytecodeEmitter();
        nonStrictGlobalEmitter.emitOpcode(Opcode.UNDEFINED);
        nonStrictGlobalEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("newGlobalRef"));
        nonStrictGlobalEmitter.emitOpcodeU32(Opcode.PUSH_I32, 11);
        nonStrictGlobalEmitter.emitOpcode(Opcode.PUT_REF_VALUE);
        nonStrictGlobalEmitter.emitOpcodeAtom(Opcode.GET_VAR, "newGlobalRef");
        nonStrictGlobalEmitter.emitOpcode(Opcode.RETURN);

        JSValue nonStrictResult = executeWithStrict(false, nonStrictGlobalEmitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(nonStrictResult).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) nonStrictResult).value()).isEqualTo(11);

        BytecodeEmitter strictMissingGetEmitter = new BytecodeEmitter();
        strictMissingGetEmitter.emitOpcode(Opcode.UNDEFINED);
        strictMissingGetEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("missing"));
        strictMissingGetEmitter.emitOpcode(Opcode.GET_REF_VALUE);
        strictMissingGetEmitter.emitOpcode(Opcode.RETURN);

        assertThatThrownBy(() -> execute(strictMissingGetEmitter, 0, new JSValue[0], JSUndefined.INSTANCE))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("missing is not defined");

        BytecodeEmitter strictMissingPutEmitter = new BytecodeEmitter();
        strictMissingPutEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, context.createJSObject());
        strictMissingPutEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("missing"));
        strictMissingPutEmitter.emitOpcodeU32(Opcode.PUSH_I32, 1);
        strictMissingPutEmitter.emitOpcode(Opcode.PUT_REF_VALUE);
        strictMissingPutEmitter.emitOpcode(Opcode.RETURN_UNDEF);

        assertThatThrownBy(() -> execute(strictMissingPutEmitter, 0, new JSValue[0], JSUndefined.INSTANCE))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("missing is not defined");
    }

    @Test
    public void testRemainingMediumOpcodes() {
        BytecodeEmitter pushBigIntI32Emitter = new BytecodeEmitter();
        pushBigIntI32Emitter.emitOpcode(Opcode.PUSH_BIGINT_I32);
        pushBigIntI32Emitter.emitI32(Integer.MIN_VALUE);
        pushBigIntI32Emitter.emitOpcode(Opcode.RETURN);

        JSValue minBigIntResult = execute(pushBigIntI32Emitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(minBigIntResult).isInstanceOf(JSBigInt.class);
        assertThat(((JSBigInt) minBigIntResult).value().intValueExact()).isEqualTo(Integer.MIN_VALUE);

        BytecodeEmitter pushBigIntI32MaxEmitter = new BytecodeEmitter();
        pushBigIntI32MaxEmitter.emitOpcode(Opcode.PUSH_BIGINT_I32);
        pushBigIntI32MaxEmitter.emitI32(Integer.MAX_VALUE);
        pushBigIntI32MaxEmitter.emitOpcode(Opcode.RETURN);

        JSValue maxBigIntResult = execute(pushBigIntI32MaxEmitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(maxBigIntResult).isInstanceOf(JSBigInt.class);
        assertThat(((JSBigInt) maxBigIntResult).value().intValueExact()).isEqualTo(Integer.MAX_VALUE);

        context.getGlobalObject().set("tmpDeleteVar", new JSNumber(1));
        BytecodeEmitter deleteVarEmitter = new BytecodeEmitter();
        deleteVarEmitter.emitOpcodeAtom(Opcode.DELETE_VAR, "tmpDeleteVar");
        deleteVarEmitter.emitOpcode(Opcode.RETURN);

        JSValue deletedResult = executeWithStrict(false, deleteVarEmitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(deletedResult).isEqualTo(JSBoolean.TRUE);
        assertThat(context.getGlobalObject().get("tmpDeleteVar")).isSameAs(JSUndefined.INSTANCE);

        BytecodeEmitter deleteMissingVarEmitter = new BytecodeEmitter();
        deleteMissingVarEmitter.emitOpcodeAtom(Opcode.DELETE_VAR, "missingDeleteVar");
        deleteMissingVarEmitter.emitOpcode(Opcode.RETURN);

        JSValue deleteMissingResult = executeWithStrict(false, deleteMissingVarEmitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(deleteMissingResult).isEqualTo(JSBoolean.TRUE);

        context.getGlobalObject().defineProperty(
                PropertyKey.fromString("lockedDeleteVar"),
                PropertyDescriptor.dataDescriptor(new JSNumber(2), true, true, false));
        BytecodeEmitter deleteLockedVarEmitter = new BytecodeEmitter();
        deleteLockedVarEmitter.emitOpcodeAtom(Opcode.DELETE_VAR, "lockedDeleteVar");
        deleteLockedVarEmitter.emitOpcode(Opcode.RETURN);

        JSValue deleteLockedResult = executeWithStrict(false, deleteLockedVarEmitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(deleteLockedResult).isEqualTo(JSBoolean.FALSE);
        assertThat(context.getGlobalObject().get("lockedDeleteVar")).isInstanceOf(JSNumber.class);
    }

    @Test
    public void testSetNameOpcodes() {
        JSNativeFunction f1 = new JSNativeFunction("oldName", 0, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        BytecodeEmitter setNameEmitter = new BytecodeEmitter();
        setNameEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, f1);
        setNameEmitter.emitOpcodeAtom(Opcode.SET_NAME, "renamed");
        setNameEmitter.emitOpcodeAtom(Opcode.GET_FIELD, "name");
        setNameEmitter.emitOpcode(Opcode.RETURN);

        JSValue setNameResult = execute(setNameEmitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(setNameResult).isInstanceOf(JSString.class);
        assertThat(((JSString) setNameResult).value()).isEqualTo("renamed");

        JSNativeFunction f2 = new JSNativeFunction("", 0, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        BytecodeEmitter setNameComputedEmitter = new BytecodeEmitter();
        setNameComputedEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("dynamic"));
        setNameComputedEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, f2);
        setNameComputedEmitter.emitOpcode(Opcode.SET_NAME_COMPUTED);
        setNameComputedEmitter.emitOpcodeAtom(Opcode.GET_FIELD, "name");
        setNameComputedEmitter.emitOpcode(Opcode.RETURN);

        JSValue setNameComputedResult = execute(setNameComputedEmitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(setNameComputedResult).isInstanceOf(JSString.class);
        assertThat(((JSString) setNameComputedResult).value()).isEqualTo("dynamic");

        JSNativeFunction f3 = new JSNativeFunction("", 0, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        BytecodeEmitter setNameSymbolEmitter = new BytecodeEmitter();
        setNameSymbolEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSSymbol("s"));
        setNameSymbolEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, f3);
        setNameSymbolEmitter.emitOpcode(Opcode.SET_NAME_COMPUTED);
        setNameSymbolEmitter.emitOpcodeAtom(Opcode.GET_FIELD, "name");
        setNameSymbolEmitter.emitOpcode(Opcode.RETURN);

        JSValue setNameSymbolResult = execute(setNameSymbolEmitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(setNameSymbolResult).isInstanceOf(JSString.class);
        assertThat(((JSString) setNameSymbolResult).value()).isEqualTo("[s]");
    }

    @Test
    public void testTdzCheckOpcodes() {
        BytecodeEmitter getLocCheckEmitter = new BytecodeEmitter();
        getLocCheckEmitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, 0);
        getLocCheckEmitter.emitOpcodeU16(Opcode.GET_LOC_CHECK, 0);
        getLocCheckEmitter.emitOpcode(Opcode.RETURN);

        assertThatThrownBy(() -> execute(getLocCheckEmitter, 1, new JSValue[0], JSUndefined.INSTANCE))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("variable is uninitialized");

        BytecodeEmitter getLocCheckThisEmitter = new BytecodeEmitter();
        getLocCheckThisEmitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, 0);
        getLocCheckThisEmitter.emitOpcodeU16(Opcode.GET_LOC_CHECKTHIS, 0);
        getLocCheckThisEmitter.emitOpcode(Opcode.RETURN);

        assertThatThrownBy(() -> execute(getLocCheckThisEmitter, 1, new JSValue[0], JSUndefined.INSTANCE))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("variable is uninitialized");

        BytecodeEmitter putLocCheckEmitter = new BytecodeEmitter();
        putLocCheckEmitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, 0);
        putLocCheckEmitter.emitOpcodeU32(Opcode.PUSH_I32, 1);
        putLocCheckEmitter.emitOpcodeU16(Opcode.PUT_LOC_CHECK, 0);
        putLocCheckEmitter.emitOpcode(Opcode.RETURN_UNDEF);

        assertThatThrownBy(() -> execute(putLocCheckEmitter, 1, new JSValue[0], JSUndefined.INSTANCE))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("variable is uninitialized");

        BytecodeEmitter setLocCheckEmitter = new BytecodeEmitter();
        setLocCheckEmitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, 0);
        setLocCheckEmitter.emitOpcodeU32(Opcode.PUSH_I32, 1);
        setLocCheckEmitter.emitOpcodeU16(Opcode.SET_LOC_CHECK, 0);
        setLocCheckEmitter.emitOpcode(Opcode.RETURN_UNDEF);

        assertThatThrownBy(() -> execute(setLocCheckEmitter, 1, new JSValue[0], JSUndefined.INSTANCE))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("variable is uninitialized");

        BytecodeEmitter putLocCheckInitEmitter = new BytecodeEmitter();
        putLocCheckInitEmitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, 0);
        putLocCheckInitEmitter.emitOpcodeU32(Opcode.PUSH_I32, 2);
        putLocCheckInitEmitter.emitOpcodeU16(Opcode.PUT_LOC_CHECK_INIT, 0);
        putLocCheckInitEmitter.emitOpcodeU32(Opcode.PUSH_I32, 3);
        putLocCheckInitEmitter.emitOpcodeU16(Opcode.PUT_LOC_CHECK_INIT, 0);
        putLocCheckInitEmitter.emitOpcode(Opcode.RETURN_UNDEF);

        assertThatThrownBy(() -> execute(putLocCheckInitEmitter, 1, new JSValue[0], JSUndefined.INSTANCE))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("'this' can be initialized only once");

        BytecodeEmitter varRefCheckEmitter = new BytecodeEmitter();
        varRefCheckEmitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, 0);
        varRefCheckEmitter.emitOpcodeU16(Opcode.CLOSE_LOC, 0);
        varRefCheckEmitter.emitOpcodeU16(Opcode.GET_VAR_REF_CHECK, 0);
        varRefCheckEmitter.emitOpcode(Opcode.RETURN);

        assertThatThrownBy(() -> execute(varRefCheckEmitter, 1, new JSValue[]{JSUndefined.INSTANCE}, JSUndefined.INSTANCE))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("variable is uninitialized");

        BytecodeEmitter putVarRefCheckEmitter = new BytecodeEmitter();
        putVarRefCheckEmitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, 0);
        putVarRefCheckEmitter.emitOpcodeU16(Opcode.CLOSE_LOC, 0);
        putVarRefCheckEmitter.emitOpcodeU32(Opcode.PUSH_I32, 1);
        putVarRefCheckEmitter.emitOpcodeU16(Opcode.PUT_VAR_REF_CHECK, 0);
        putVarRefCheckEmitter.emitOpcode(Opcode.RETURN_UNDEF);

        assertThatThrownBy(() -> execute(putVarRefCheckEmitter, 1, new JSValue[]{JSUndefined.INSTANCE}, JSUndefined.INSTANCE))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("variable is uninitialized");

        BytecodeEmitter putVarRefCheckInitEmitter = new BytecodeEmitter();
        putVarRefCheckInitEmitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, 0);
        putVarRefCheckInitEmitter.emitOpcodeU16(Opcode.CLOSE_LOC, 0);
        putVarRefCheckInitEmitter.emitOpcodeU32(Opcode.PUSH_I32, 10);
        putVarRefCheckInitEmitter.emitOpcodeU16(Opcode.PUT_VAR_REF_CHECK_INIT, 0);
        putVarRefCheckInitEmitter.emitOpcodeU16(Opcode.GET_VAR_REF_CHECK, 0);
        putVarRefCheckInitEmitter.emitOpcode(Opcode.RETURN);

        JSValue putVarRefCheckInitResult = execute(
                putVarRefCheckInitEmitter, 1, new JSValue[]{JSUndefined.INSTANCE}, JSUndefined.INSTANCE);
        assertThat(putVarRefCheckInitResult).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) putVarRefCheckInitResult).value()).isEqualTo(10);

        BytecodeEmitter putVarRefCheckInitTwiceEmitter = new BytecodeEmitter();
        putVarRefCheckInitTwiceEmitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, 0);
        putVarRefCheckInitTwiceEmitter.emitOpcodeU16(Opcode.CLOSE_LOC, 0);
        putVarRefCheckInitTwiceEmitter.emitOpcodeU32(Opcode.PUSH_I32, 10);
        putVarRefCheckInitTwiceEmitter.emitOpcodeU16(Opcode.PUT_VAR_REF_CHECK_INIT, 0);
        putVarRefCheckInitTwiceEmitter.emitOpcodeU32(Opcode.PUSH_I32, 11);
        putVarRefCheckInitTwiceEmitter.emitOpcodeU16(Opcode.PUT_VAR_REF_CHECK_INIT, 0);
        putVarRefCheckInitTwiceEmitter.emitOpcode(Opcode.RETURN_UNDEF);

        assertThatThrownBy(() -> execute(
                putVarRefCheckInitTwiceEmitter, 1, new JSValue[]{JSUndefined.INSTANCE}, JSUndefined.INSTANCE))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("variable is already initialized");
    }
}
