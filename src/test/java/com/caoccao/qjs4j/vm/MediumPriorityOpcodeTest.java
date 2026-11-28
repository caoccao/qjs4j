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

import com.caoccao.qjs4j.compilation.compiler.BytecodeEmitter;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MediumPriorityOpcodeTest extends OpcodeTest {

    @Test
    public void testCopyDataProperties() {
        JSObject target = context.createJSObject();
        JSObject source = context.createJSObject();
        source.set("a", new JSNumber(1));
        source.set("b", new JSNumber(2));
        source.defineProperty(PropertyKey.fromString("hidden"),
                PropertyDescriptor.dataDescriptor(new JSNumber(99), PropertyDescriptor.DataState.ConfigurableWritable));
        JSSymbol symbol = new JSSymbol("sym");
        source.defineProperty(PropertyKey.fromSymbol(symbol),
                PropertyDescriptor.dataDescriptor(new JSNumber(7), PropertyDescriptor.DataState.All));

        JSArray excludeList = context.createJSArray();
        excludeList.push(new JSString("a"));
        excludeList.push(symbol);

        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitPushConstant(target);
        emitter.emitPushConstant(source);
        emitter.emitPushConstant(excludeList);
        // target offset=2, source offset=1, exclude offset=0 => 0b00000110
        emitter.emitIntOperand(Opcode.COPY_DATA_PROPERTIES, 6);
        emitOpcodes(emitter, Opcode.DROP, Opcode.DROP, Opcode.RETURN);

        assertThat(execute(emitter, 0)).isSameAs(target);
        assertThat(target.get("a")).isSameAs(JSUndefined.INSTANCE);
        assertNumberValue(target.get("b"), 2);
        assertThat(target.get("hidden")).isSameAs(JSUndefined.INSTANCE);
        assertThat(target.get(PropertyKey.fromSymbol(symbol))).isSameAs(JSUndefined.INSTANCE);

        BytecodeEmitter typeErrorEmitter = new BytecodeEmitter();
        typeErrorEmitter.emitIntOperand(Opcode.PUSH_I32, 1);
        typeErrorEmitter.emitPushConstant(source);
        typeErrorEmitter.emitOpcode(Opcode.NULL);
        typeErrorEmitter.emitIntOperand(Opcode.COPY_DATA_PROPERTIES, 6);
        typeErrorEmitter.emitOpcode(Opcode.RETURN_UNDEF);

        assertThatThrownBy(() -> execute(typeErrorEmitter, 0)).isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("copy target must be an object");
    }

    @Test
    public void testDefineClassComputed() {
        JSNativeFunction constructor = new JSNativeFunction(context, "old", 0,
                (ctx, thisArg, args) -> JSUndefined.INSTANCE);

        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitPushConstant(new JSString("DynamicClass"));
        emitter.emitOpcode(Opcode.UNDEFINED);
        emitter.emitPushConstant(constructor);
        emitter.emitOpcodeAtom(Opcode.DEFINE_CLASS_COMPUTED, "FallbackClass");
        emitter.emitU8(0);
        emitter.emitOpcode(Opcode.RETURN);

        JSValue classCtorValue = execute(emitter, 0);
        assertThat(classCtorValue).isSameAs(constructor);
        assertThat(classCtorValue).isInstanceOf(JSObject.class);
        JSObject classCtor = (JSObject) classCtorValue;
        assertStringValue(classCtor.get("name"), "DynamicClass");
        assertThat(classCtor.get("prototype")).isInstanceOf(JSObject.class);

        BytecodeEmitter fallbackEmitter = new BytecodeEmitter();
        fallbackEmitter.emitPushConstant(new JSString(""));
        fallbackEmitter.emitOpcode(Opcode.UNDEFINED);
        fallbackEmitter.emitPushConstant(constructor);
        fallbackEmitter.emitOpcodeAtom(Opcode.DEFINE_CLASS_COMPUTED, "FallbackClass");
        fallbackEmitter.emitU8(0);
        fallbackEmitter.emitOpcode(Opcode.RETURN);

        JSValue fallbackCtor = execute(fallbackEmitter, 0);
        assertThat(fallbackCtor).isSameAs(constructor);
        assertThat(((JSString) ((JSObject) fallbackCtor).get("name")).value()).isEqualTo("FallbackClass");
    }

    @Test
    public void testDefineClassComputedPreservesHeritageAndStack() {
        JSBytecodeFunction constructor = (JSBytecodeFunction) context.eval("(function () {})");
        JSFunction parent = (JSFunction) context.eval("(class Parent {})");
        JSSymbol name = new JSSymbol("DynamicClass");
        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitPushConstant(name);
        emitter.emitPushConstant(parent);
        emitter.emitPushConstant(constructor);
        emitter.emitOpcodeAtom(Opcode.DEFINE_CLASS_COMPUTED, "FallbackClass");
        emitter.emitU8(1);
        emitOpcodes(emitter, Opcode.DROP, Opcode.DROP, Opcode.RETURN);

        assertThat(execute(emitter, 0)).isSameAs(name);
        assertThat(constructor.getPrototype()).isSameAs(parent);
        JSObject prototype = (JSObject) constructor.get("prototype");
        assertThat(prototype.getPrototype()).isSameAs(parent.get("prototype"));
        assertThat(prototype.get("constructor")).isSameAs(constructor);
        assertThat(constructor.getHomeObject()).isSameAs(prototype);
        assertThat(constructor.isClassConstructor()).isTrue();
        assertThat(constructor.isDerivedConstructor()).isTrue();
        assertThat(((JSString) constructor.get("name")).value()).isEqualTo("[DynamicClass]");
    }

    @Test
    public void testDefineClassComputedRejectsUndefinedHeritage() {
        JSNativeFunction constructor = new JSNativeFunction(context, "old", 0,
                (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitPushConstant(new JSString("DynamicClass"));
        emitter.emitOpcode(Opcode.UNDEFINED);
        emitter.emitPushConstant(constructor);
        emitter.emitOpcodeAtom(Opcode.DEFINE_CLASS_COMPUTED, "FallbackClass");
        emitter.emitU8(1);
        emitter.emitOpcode(Opcode.RETURN);

        assertThatThrownBy(() -> execute(emitter, 0)).isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("parent class must be constructor");
    }

    @Test
    public void testDefineMethodComputed() {
        JSObject target = context.createJSObject();
        JSNativeFunction method = new JSNativeFunction(context, "m", 0, (ctx, thisArg, args) -> new JSNumber(1));
        JSNativeFunction getter = new JSNativeFunction(context, "g", 0, (ctx, thisArg, args) -> new JSNumber(42));
        JSNativeFunction setter = new JSNativeFunction(context, "s", 1, (ctx, thisArg, args) -> JSUndefined.INSTANCE);

        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitPushConstant(target);
        emitter.emitPushConstant(new JSString("m"));
        emitter.emitPushConstant(method);
        emitter.emitIntOperand(Opcode.DEFINE_METHOD_COMPUTED, 4);
        emitter.emitPushConstant(new JSString("x"));
        emitter.emitPushConstant(getter);
        emitter.emitIntOperand(Opcode.DEFINE_METHOD_COMPUTED, 1);
        emitter.emitPushConstant(new JSString("x"));
        emitter.emitPushConstant(setter);
        emitter.emitIntOperand(Opcode.DEFINE_METHOD_COMPUTED, 2);
        emitter.emitPushConstant(new JSString("x"));
        emitOpcodes(emitter, Opcode.GET_ARRAY_EL, Opcode.RETURN);

        assertNumberValue(execute(emitter, 0), 42);

        PropertyDescriptor methodDescriptor = target.getOwnPropertyDescriptor(PropertyKey.fromString("m"));
        assertThat(methodDescriptor).isNotNull();
        assertThat(methodDescriptor.isEnumerable()).isTrue();
        assertThat(target.get("m")).isSameAs(method);
        assertThat(method.getHomeObject()).isSameAs(target);
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
        getArrayEl2Emitter.emitPushConstant(array);
        getArrayEl2Emitter.emitIntOperand(Opcode.PUSH_I32, 1);
        emitOpcodes(getArrayEl2Emitter, Opcode.GET_ARRAY_EL2, Opcode.SWAP, Opcode.GET_LENGTH, Opcode.ADD,
                Opcode.RETURN);

        assertNumberValue(execute(getArrayEl2Emitter, 0), 22);

        BytecodeEmitter getLengthStringEmitter = new BytecodeEmitter();
        getLengthStringEmitter.emitPushConstant(new JSString("abcd"));
        emitOpcodes(getLengthStringEmitter, Opcode.GET_LENGTH, Opcode.RETURN);

        assertNumberValue(execute(getLengthStringEmitter, 0), 4);
    }

    @Test
    public void testMakeRefOpcodes() {
        BytecodeEmitter locRefEmitter = new BytecodeEmitter();
        locRefEmitter.emitIntOperand(Opcode.PUSH_I32, 3);
        locRefEmitter.emitPutLocal(0);
        locRefEmitter.emitOpcodeAtom(Opcode.MAKE_LOC_REF, "x");
        locRefEmitter.emitU16(0);
        locRefEmitter.emitIntOperand(Opcode.PUSH_I32, 9);
        locRefEmitter.emitOpcode(Opcode.PUT_REF_VALUE);
        locRefEmitter.emitGetLocal(0);
        locRefEmitter.emitOpcode(Opcode.RETURN);

        assertNumberValue(execute(locRefEmitter, 1), 9);

        BytecodeEmitter argRefEmitter = new BytecodeEmitter();
        argRefEmitter.emitOpcodeAtom(Opcode.MAKE_ARG_REF, "arg0");
        argRefEmitter.emitU16(0);
        argRefEmitter.emitIntOperand(Opcode.PUSH_I32, 12);
        argRefEmitter.emitOpcode(Opcode.PUT_REF_VALUE);
        argRefEmitter.emitOpcodeAtom(Opcode.MAKE_ARG_REF, "arg0");
        argRefEmitter.emitU16(0);
        emitOpcodes(argRefEmitter, Opcode.GET_REF_VALUE, Opcode.RETURN);

        assertNumberValue(execute(argRefEmitter, 0, JSValue.NO_ARGS, JSUndefined.INSTANCE, new JSNumber(5)), 12);

        BytecodeEmitter varRefRefEmitter = new BytecodeEmitter();
        varRefRefEmitter.emitOpcodeAtom(Opcode.MAKE_VAR_REF_REF, "v");
        varRefRefEmitter.emitU16(0);
        varRefRefEmitter.emitIntOperand(Opcode.PUSH_I32, 15);
        varRefRefEmitter.emitOpcode(Opcode.PUT_REF_VALUE);
        varRefRefEmitter.emitIntOperand(Opcode.GET_VAR_REF, 0);
        varRefRefEmitter.emitOpcode(Opcode.RETURN);

        JSValue[] closureVars = nativeArguments(1);
        assertNumberValue(execute(varRefRefEmitter, 0, closureVars, JSUndefined.INSTANCE), 15);

        context.getGlobalObject().set("g", new JSNumber(0));
        BytecodeEmitter varRefEmitter = new BytecodeEmitter();
        varRefEmitter.emitOpcodeAtom(Opcode.MAKE_VAR_REF, "g");
        varRefEmitter.emitIntOperand(Opcode.PUSH_I32, 7);
        varRefEmitter.emitOpcode(Opcode.PUT_REF_VALUE);
        varRefEmitter.emitOpcodeAtom(Opcode.GET_VAR, "g");
        varRefEmitter.emitOpcode(Opcode.RETURN);

        assertNumberValue(execute(varRefEmitter, 0), 7);
    }

    @Test
    public void testNipCatch() {
        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitIntOperand(Opcode.CATCH, 0);
        emitter.emitIntOperand(Opcode.PUSH_I32, 1);
        emitter.emitIntOperand(Opcode.PUSH_I32, 2);
        emitOpcodes(emitter, Opcode.NIP_CATCH, Opcode.RETURN);

        assertNumberValue(execute(emitter, 0), 2);

        BytecodeEmitter noCatchEmitter = new BytecodeEmitter();
        noCatchEmitter.emitIntOperand(Opcode.PUSH_I32, 1);
        emitOpcodes(noCatchEmitter, Opcode.NIP_CATCH, Opcode.RETURN);

        assertThatThrownBy(() -> execute(noCatchEmitter, 0)).isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("nip_catch");
    }

    @Test
    public void testRefValueOpcodes() {
        JSObject obj = context.createJSObject();
        obj.set("x", new JSNumber(1));

        BytecodeEmitter getRefEmitter = new BytecodeEmitter();
        getRefEmitter.emitPushConstant(obj);
        getRefEmitter.emitPushConstant(new JSString("x"));
        emitOpcodes(getRefEmitter, Opcode.GET_REF_VALUE, Opcode.RETURN);

        assertNumberValue(execute(getRefEmitter, 0), 1);

        BytecodeEmitter putRefEmitter = new BytecodeEmitter();
        putRefEmitter.emitPushConstant(obj);
        putRefEmitter.emitPushConstant(new JSString("x"));
        putRefEmitter.emitIntOperand(Opcode.PUSH_I32, 5);
        putRefEmitter.emitOpcode(Opcode.PUT_REF_VALUE);
        putRefEmitter.emitPushConstant(obj);
        putRefEmitter.emitOpcodeAtom(Opcode.GET_FIELD, "x");
        putRefEmitter.emitOpcode(Opcode.RETURN);

        assertNumberValue(execute(putRefEmitter, 0), 5);

        BytecodeEmitter nonStrictGlobalEmitter = new BytecodeEmitter();
        nonStrictGlobalEmitter.emitOpcode(Opcode.UNDEFINED);
        nonStrictGlobalEmitter.emitPushConstant(new JSString("newGlobalRef"));
        nonStrictGlobalEmitter.emitIntOperand(Opcode.PUSH_I32, 11);
        nonStrictGlobalEmitter.emitOpcode(Opcode.PUT_REF_VALUE);
        nonStrictGlobalEmitter.emitOpcodeAtom(Opcode.GET_VAR, "newGlobalRef");
        nonStrictGlobalEmitter.emitOpcode(Opcode.RETURN);

        assertNumberValue(executeWithStrict(false, nonStrictGlobalEmitter, 0, JSValue.NO_ARGS, JSUndefined.INSTANCE),
                11);

        BytecodeEmitter strictMissingGetEmitter = new BytecodeEmitter();
        strictMissingGetEmitter.emitOpcode(Opcode.UNDEFINED);
        strictMissingGetEmitter.emitPushConstant(new JSString("missing"));
        emitOpcodes(strictMissingGetEmitter, Opcode.GET_REF_VALUE, Opcode.RETURN);

        assertThatThrownBy(() -> execute(strictMissingGetEmitter, 0)).isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("missing is not defined");

        BytecodeEmitter strictMissingPutEmitter = new BytecodeEmitter();
        strictMissingPutEmitter.emitPushConstant(context.createJSObject());
        strictMissingPutEmitter.emitPushConstant(new JSString("missing"));
        strictMissingPutEmitter.emitIntOperand(Opcode.PUSH_I32, 1);
        emitOpcodes(strictMissingPutEmitter, Opcode.PUT_REF_VALUE, Opcode.RETURN_UNDEF);

        assertThatThrownBy(() -> execute(strictMissingPutEmitter, 0)).isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("missing is not defined");
    }

    @Test
    public void testRemainingMediumOpcodes() {
        BytecodeEmitter pushBigIntI32Emitter = new BytecodeEmitter();
        pushBigIntI32Emitter.emitOpcode(Opcode.PUSH_BIGINT_I32);
        pushBigIntI32Emitter.emitI32(Integer.MIN_VALUE);
        pushBigIntI32Emitter.emitOpcode(Opcode.RETURN);

        JSValue minBigIntResult = execute(pushBigIntI32Emitter, 0);
        assertThat(minBigIntResult).isInstanceOf(JSBigInt.class);
        assertThat(((JSBigInt) minBigIntResult).value().intValueExact()).isEqualTo(Integer.MIN_VALUE);

        BytecodeEmitter pushBigIntI32MaxEmitter = new BytecodeEmitter();
        pushBigIntI32MaxEmitter.emitOpcode(Opcode.PUSH_BIGINT_I32);
        pushBigIntI32MaxEmitter.emitI32(Integer.MAX_VALUE);
        pushBigIntI32MaxEmitter.emitOpcode(Opcode.RETURN);

        JSValue maxBigIntResult = execute(pushBigIntI32MaxEmitter, 0);
        assertThat(maxBigIntResult).isInstanceOf(JSBigInt.class);
        assertThat(((JSBigInt) maxBigIntResult).value().intValueExact()).isEqualTo(Integer.MAX_VALUE);

        context.getGlobalObject().set("tmpDeleteVar", new JSNumber(1));
        BytecodeEmitter deleteVarEmitter = new BytecodeEmitter();
        deleteVarEmitter.emitOpcodeAtom(Opcode.DELETE_VAR, "tmpDeleteVar");
        deleteVarEmitter.emitOpcode(Opcode.RETURN);

        assertThat(executeWithStrict(false, deleteVarEmitter, 0, JSValue.NO_ARGS, JSUndefined.INSTANCE))
                .isEqualTo(JSBoolean.TRUE);
        assertThat(context.getGlobalObject().get("tmpDeleteVar")).isSameAs(JSUndefined.INSTANCE);

        BytecodeEmitter deleteMissingVarEmitter = new BytecodeEmitter();
        deleteMissingVarEmitter.emitOpcodeAtom(Opcode.DELETE_VAR, "missingDeleteVar");
        deleteMissingVarEmitter.emitOpcode(Opcode.RETURN);

        assertThat(executeWithStrict(false, deleteMissingVarEmitter, 0, JSValue.NO_ARGS, JSUndefined.INSTANCE))
                .isEqualTo(JSBoolean.TRUE);

        context.getGlobalObject().defineProperty(PropertyKey.fromString("lockedDeleteVar"),
                PropertyDescriptor.dataDescriptor(new JSNumber(2), PropertyDescriptor.DataState.EnumerableWritable));
        BytecodeEmitter deleteLockedVarEmitter = new BytecodeEmitter();
        deleteLockedVarEmitter.emitOpcodeAtom(Opcode.DELETE_VAR, "lockedDeleteVar");
        deleteLockedVarEmitter.emitOpcode(Opcode.RETURN);

        assertThat(executeWithStrict(false, deleteLockedVarEmitter, 0, JSValue.NO_ARGS, JSUndefined.INSTANCE))
                .isEqualTo(JSBoolean.FALSE);
        assertThat(context.getGlobalObject().get("lockedDeleteVar")).isInstanceOf(JSNumber.class);
    }

    @Test
    public void testSetNameOpcodes() {
        JSNativeFunction f1 = new JSNativeFunction(context, "oldName", 0, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        BytecodeEmitter setNameEmitter = new BytecodeEmitter();
        setNameEmitter.emitPushConstant(f1);
        setNameEmitter.emitOpcodeAtom(Opcode.SET_NAME, "renamed");
        setNameEmitter.emitOpcodeAtom(Opcode.GET_FIELD, "name");
        setNameEmitter.emitOpcode(Opcode.RETURN);

        assertStringValue(execute(setNameEmitter, 0), "renamed");

        JSNativeFunction f2 = new JSNativeFunction(context, "", 0, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        BytecodeEmitter setNameComputedEmitter = new BytecodeEmitter();
        setNameComputedEmitter.emitPushConstant(new JSString("dynamic"));
        setNameComputedEmitter.emitPushConstant(f2);
        setNameComputedEmitter.emitOpcode(Opcode.SET_NAME_COMPUTED);
        setNameComputedEmitter.emitOpcodeAtom(Opcode.GET_FIELD, "name");
        setNameComputedEmitter.emitOpcode(Opcode.RETURN);

        assertStringValue(execute(setNameComputedEmitter, 0), "dynamic");

        JSNativeFunction f3 = new JSNativeFunction(context, "", 0, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        BytecodeEmitter setNameSymbolEmitter = new BytecodeEmitter();
        setNameSymbolEmitter.emitPushConstant(new JSSymbol("s"));
        setNameSymbolEmitter.emitPushConstant(f3);
        setNameSymbolEmitter.emitOpcode(Opcode.SET_NAME_COMPUTED);
        setNameSymbolEmitter.emitOpcodeAtom(Opcode.GET_FIELD, "name");
        setNameSymbolEmitter.emitOpcode(Opcode.RETURN);

        assertStringValue(execute(setNameSymbolEmitter, 0), "[s]");
    }

    @Test
    public void testTdzCheckOpcodes() {
        BytecodeEmitter getLocCheckEmitter = new BytecodeEmitter();
        getLocCheckEmitter.emitIntOperand(Opcode.SET_LOC_UNINITIALIZED, 0);
        getLocCheckEmitter.emitIntOperand(Opcode.GET_LOC_CHECK, 0);
        getLocCheckEmitter.emitOpcode(Opcode.RETURN);

        assertThatThrownBy(() -> execute(getLocCheckEmitter, 1)).isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("before initialization");

        BytecodeEmitter getLocCheckThisEmitter = new BytecodeEmitter();
        getLocCheckThisEmitter.emitIntOperand(Opcode.SET_LOC_UNINITIALIZED, 0);
        getLocCheckThisEmitter.emitIntOperand(Opcode.GET_LOC_CHECKTHIS, 0);
        getLocCheckThisEmitter.emitOpcode(Opcode.RETURN);

        assertThatThrownBy(() -> execute(getLocCheckThisEmitter, 1)).isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("before initialization");

        BytecodeEmitter putLocCheckEmitter = new BytecodeEmitter();
        putLocCheckEmitter.emitIntOperand(Opcode.SET_LOC_UNINITIALIZED, 0);
        putLocCheckEmitter.emitIntOperand(Opcode.PUSH_I32, 1);
        putLocCheckEmitter.emitIntOperand(Opcode.PUT_LOC_CHECK, 0);
        putLocCheckEmitter.emitOpcode(Opcode.RETURN_UNDEF);

        assertThatThrownBy(() -> execute(putLocCheckEmitter, 1)).isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("before initialization");

        BytecodeEmitter setLocCheckEmitter = new BytecodeEmitter();
        setLocCheckEmitter.emitIntOperand(Opcode.SET_LOC_UNINITIALIZED, 0);
        setLocCheckEmitter.emitIntOperand(Opcode.PUSH_I32, 1);
        setLocCheckEmitter.emitIntOperand(Opcode.SET_LOC_CHECK, 0);
        setLocCheckEmitter.emitOpcode(Opcode.RETURN_UNDEF);

        assertThatThrownBy(() -> execute(setLocCheckEmitter, 1)).isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("before initialization");

        BytecodeEmitter putLocCheckInitEmitter = new BytecodeEmitter();
        putLocCheckInitEmitter.emitIntOperand(Opcode.SET_LOC_UNINITIALIZED, 0);
        putLocCheckInitEmitter.emitIntOperand(Opcode.PUSH_I32, 2);
        putLocCheckInitEmitter.emitIntOperand(Opcode.PUT_LOC_CHECK_INIT, 0);
        putLocCheckInitEmitter.emitIntOperand(Opcode.PUSH_I32, 3);
        putLocCheckInitEmitter.emitIntOperand(Opcode.PUT_LOC_CHECK_INIT, 0);
        putLocCheckInitEmitter.emitOpcode(Opcode.RETURN_UNDEF);

        assertThatThrownBy(() -> execute(putLocCheckInitEmitter, 1)).isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("'this' can be initialized only once");

        BytecodeEmitter varRefCheckEmitter = new BytecodeEmitter();
        varRefCheckEmitter.emitIntOperand(Opcode.SET_LOC_UNINITIALIZED, 0);
        varRefCheckEmitter.emitIntOperand(Opcode.CLOSE_LOC, 0);
        varRefCheckEmitter.emitIntOperand(Opcode.GET_VAR_REF_CHECK, 0);
        varRefCheckEmitter.emitOpcode(Opcode.RETURN);

        assertThatThrownBy(
                () -> execute(varRefCheckEmitter, 1, new JSValue[]{JSUndefined.INSTANCE}, JSUndefined.INSTANCE))
                .isInstanceOf(JSVirtualMachineException.class).hasMessageContaining("before initialization");

        BytecodeEmitter putVarRefCheckEmitter = new BytecodeEmitter();
        putVarRefCheckEmitter.emitIntOperand(Opcode.SET_LOC_UNINITIALIZED, 0);
        putVarRefCheckEmitter.emitIntOperand(Opcode.CLOSE_LOC, 0);
        putVarRefCheckEmitter.emitIntOperand(Opcode.PUSH_I32, 1);
        putVarRefCheckEmitter.emitIntOperand(Opcode.PUT_VAR_REF_CHECK, 0);
        putVarRefCheckEmitter.emitOpcode(Opcode.RETURN_UNDEF);

        assertThatThrownBy(
                () -> execute(putVarRefCheckEmitter, 1, new JSValue[]{JSUndefined.INSTANCE}, JSUndefined.INSTANCE))
                .isInstanceOf(JSVirtualMachineException.class).hasMessageContaining("before initialization");

        BytecodeEmitter putVarRefCheckInitEmitter = new BytecodeEmitter();
        putVarRefCheckInitEmitter.emitIntOperand(Opcode.SET_LOC_UNINITIALIZED, 0);
        putVarRefCheckInitEmitter.emitIntOperand(Opcode.CLOSE_LOC, 0);
        putVarRefCheckInitEmitter.emitIntOperand(Opcode.PUSH_I32, 10);
        putVarRefCheckInitEmitter.emitIntOperand(Opcode.PUT_VAR_REF_CHECK_INIT, 0);
        putVarRefCheckInitEmitter.emitIntOperand(Opcode.GET_VAR_REF_CHECK, 0);
        putVarRefCheckInitEmitter.emitOpcode(Opcode.RETURN);

        assertNumberValue(
                execute(putVarRefCheckInitEmitter, 1, new JSValue[]{JSUndefined.INSTANCE}, JSUndefined.INSTANCE), 10);

        BytecodeEmitter putVarRefCheckInitTwiceEmitter = new BytecodeEmitter();
        putVarRefCheckInitTwiceEmitter.emitIntOperand(Opcode.SET_LOC_UNINITIALIZED, 0);
        putVarRefCheckInitTwiceEmitter.emitIntOperand(Opcode.CLOSE_LOC, 0);
        putVarRefCheckInitTwiceEmitter.emitIntOperand(Opcode.PUSH_I32, 10);
        putVarRefCheckInitTwiceEmitter.emitIntOperand(Opcode.PUT_VAR_REF_CHECK_INIT, 0);
        putVarRefCheckInitTwiceEmitter.emitIntOperand(Opcode.PUSH_I32, 11);
        putVarRefCheckInitTwiceEmitter.emitIntOperand(Opcode.PUT_VAR_REF_CHECK_INIT, 0);
        putVarRefCheckInitTwiceEmitter.emitOpcode(Opcode.RETURN_UNDEF);

        assertThatThrownBy(() -> execute(putVarRefCheckInitTwiceEmitter, 1, new JSValue[]{JSUndefined.INSTANCE},
                JSUndefined.INSTANCE)).isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("variable is already initialized");
    }
}
