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

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HighPriorityOpcodeTest extends BaseTest {
    private JSValue execute(
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
                false,
                "function test() { [bytecode] }");
        return context.getVirtualMachine().execute(function, thisArg, args);
    }

    @Test
    public void testInitCtor() {
        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitOpcode(Opcode.INIT_CTOR);
        emitter.emitOpcode(Opcode.RETURN);

        JSObject instance = context.createJSObject();
        JSValue value = execute(emitter, 0, new JSValue[0], instance);
        assertThat(value).isSameAs(instance);

        assertThatThrownBy(() -> execute(emitter, 0, new JSValue[0], JSUndefined.INSTANCE))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("class constructors must be invoked with 'new'");
    }

    @Test
    public void testIteratorCallAndClose() {
        AtomicInteger closeCount = new AtomicInteger(0);

        JSObject iteratorWithReturn = context.createJSObject();
        JSNativeFunction returnMethod = new JSNativeFunction("return", 0, (ctx, thisArg, args) -> {
            closeCount.incrementAndGet();
            JSObject result = ctx.createJSObject();
            result.set("done", JSBoolean.TRUE);
            return result;
        });
        iteratorWithReturn.set("return", returnMethod);

        BytecodeEmitter closeEmitter = new BytecodeEmitter();
        closeEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, iteratorWithReturn);
        closeEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSUndefined.INSTANCE);
        closeEmitter.emitOpcodeU32(Opcode.PUSH_I32, 0);
        closeEmitter.emitOpcode(Opcode.ITERATOR_CLOSE);
        closeEmitter.emitOpcode(Opcode.PUSH_TRUE);
        closeEmitter.emitOpcode(Opcode.RETURN);

        JSValue closeResult = execute(closeEmitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(closeResult).isSameAs(JSBoolean.TRUE);
        assertThat(closeCount.get()).isEqualTo(1);

        JSObject iteratorWithoutThrow = context.createJSObject();
        BytecodeEmitter callEmitter = new BytecodeEmitter();
        callEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, iteratorWithoutThrow);
        callEmitter.emitOpcode(Opcode.UNDEFINED);
        callEmitter.emitOpcodeU32(Opcode.PUSH_I32, 0);
        callEmitter.emitOpcodeU32(Opcode.PUSH_I32, 1);
        callEmitter.emitOpcodeU8(Opcode.ITERATOR_CALL, 1);
        callEmitter.emitOpcode(Opcode.RETURN);

        JSValue callResult = execute(callEmitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(callResult).isSameAs(JSBoolean.TRUE);
    }

    @Test
    public void testIteratorNextAndGetValueDone() {
        JSObject iterator = context.createJSObject();
        JSNativeFunction nextMethod = new JSNativeFunction("next", 1, (ctx, thisArg, args) -> {
            JSObject result = ctx.createJSObject();
            result.set("value", args.length > 0 ? args[0] : JSUndefined.INSTANCE);
            result.set("done", JSBoolean.FALSE);
            return result;
        });
        iterator.set("next", nextMethod);

        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, iterator);
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, nextMethod);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 0);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 9);
        emitter.emitOpcode(Opcode.ITERATOR_NEXT);
        emitter.emitOpcode(Opcode.ITERATOR_CHECK_OBJECT);
        emitter.emitOpcode(Opcode.ITERATOR_GET_VALUE_DONE);
        emitter.emitOpcode(Opcode.DROP);
        emitter.emitOpcode(Opcode.RETURN);

        JSValue value = execute(emitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(value).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) value).value()).isEqualTo(9);
    }

    @Test
    public void testLocAndMathOpcodes() {
        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 2);
        emitter.emitOpcodeU16(Opcode.SET_LOC, 0);
        emitter.emitOpcode(Opcode.DROP);
        emitter.emitOpcodeU16(Opcode.GET_LOC, 0);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 3);
        emitter.emitOpcode(Opcode.POW);
        emitter.emitOpcode(Opcode.LNOT);
        emitter.emitOpcode(Opcode.TO_STRING);
        emitter.emitOpcode(Opcode.RETURN);

        JSValue value = execute(emitter, 1, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(value).isInstanceOf(JSString.class);
        assertThat(((JSString) value).value()).isEqualTo("false");
    }

    @Test
    public void testSetProtoAndHomeObject() {
        JSObject prototype = context.createJSObject();
        prototype.set("marker", new JSString("ok"));
        JSObject object = context.createJSObject();

        BytecodeEmitter setProtoEmitter = new BytecodeEmitter();
        setProtoEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, object);
        setProtoEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, prototype);
        setProtoEmitter.emitOpcode(Opcode.SET_PROTO);
        setProtoEmitter.emitOpcodeAtom(Opcode.GET_FIELD, "marker");
        setProtoEmitter.emitOpcode(Opcode.RETURN);

        JSValue marker = execute(setProtoEmitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(marker).isInstanceOf(JSString.class);
        assertThat(((JSString) marker).value()).isEqualTo("ok");

        JSObject homeObject = context.createJSObject();
        JSNativeFunction method = new JSNativeFunction("m", 0, (ctx, thisArg, args) -> JSUndefined.INSTANCE);

        BytecodeEmitter homeObjectEmitter = new BytecodeEmitter();
        homeObjectEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, homeObject);
        homeObjectEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, method);
        homeObjectEmitter.emitOpcode(Opcode.SET_HOME_OBJECT);
        homeObjectEmitter.emitOpcodeAtom(Opcode.GET_FIELD, "[[HomeObject]]");
        homeObjectEmitter.emitOpcode(Opcode.NIP);
        homeObjectEmitter.emitOpcode(Opcode.RETURN);

        JSValue storedHomeObject = execute(homeObjectEmitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(storedHomeObject).isSameAs(homeObject);
    }

    @Test
    public void testVarRefAndVarInitOpcodes() {
        BytecodeEmitter localRefEmitter = new BytecodeEmitter();
        localRefEmitter.emitOpcodeU32(Opcode.PUSH_I32, 41);
        localRefEmitter.emitOpcodeU16(Opcode.PUT_LOC, 0);
        localRefEmitter.emitOpcodeU16(Opcode.CLOSE_LOC, 0);
        localRefEmitter.emitOpcodeU16(Opcode.GET_LOC, 0);
        localRefEmitter.emitOpcodeU32(Opcode.PUSH_I32, 1);
        localRefEmitter.emitOpcode(Opcode.ADD);
        localRefEmitter.emitOpcodeU16(Opcode.SET_VAR_REF, 0);
        localRefEmitter.emitOpcode(Opcode.DROP);
        localRefEmitter.emitOpcodeU16(Opcode.GET_VAR_REF, 0);
        localRefEmitter.emitOpcode(Opcode.RETURN);

        JSValue[] closureVars = new JSValue[]{JSUndefined.INSTANCE};
        JSValue localRefResult = execute(localRefEmitter, 1, closureVars, JSUndefined.INSTANCE);
        assertThat(localRefResult).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) localRefResult).value()).isEqualTo(42);

        BytecodeEmitter varInitEmitter = new BytecodeEmitter();
        varInitEmitter.emitOpcodeU16(Opcode.GET_VAR_UNDEF, 0);
        varInitEmitter.emitOpcode(Opcode.DROP);
        varInitEmitter.emitOpcodeU32(Opcode.PUSH_I32, 7);
        varInitEmitter.emitOpcodeU16(Opcode.PUT_VAR_INIT, 0);
        varInitEmitter.emitOpcodeU16(Opcode.GET_VAR_UNDEF, 0);
        varInitEmitter.emitOpcode(Opcode.RETURN);

        JSValue[] initClosureVars = new JSValue[]{JSUndefined.INSTANCE};
        JSValue varInitResult = execute(varInitEmitter, 0, initClosureVars, JSUndefined.INSTANCE);
        assertThat(varInitResult).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) varInitResult).value()).isEqualTo(7);
        assertThat(initClosureVars[0]).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) initClosureVars[0]).value()).isEqualTo(7);
    }
}
