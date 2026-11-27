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

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HighPriorityOpcodeTest extends OpcodeTest {
    private JSValue execute(BytecodeEmitter emitter, int localCount, JSValue[] closureVars, JSValue thisArg,
            JSValue... args) {
        return executeWithStrict(false, emitter, localCount, closureVars, thisArg, args);
    }

    @Test
    public void testCallResultSurvivesOperandStackGrowth() {
        JSNativeFunction grow = new JSNativeFunction(context, "grow", 0, (ctx, receiver, args) -> {
            CallStack stack = ctx.getVirtualMachine().valueStack;
            int top = stack.getStackTop();
            stack.setStackTop(stack.stack.length);
            stack.push(JSUndefined.INSTANCE);
            stack.setStackTop(top);
            return JSNumber.of(41);
        });
        context.getGlobalObject().set("grow", grow);
        assertNumberValue(context.eval("1 + grow()"), 42);
        assertNumberValue(context.eval("1 + grow(...[])"), 42);
    }

    @Test
    public void testInitCtor() {
        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitOpcode(Opcode.INIT_CTOR);
        emitter.emitOpcode(Opcode.RETURN);

        JSObject instance = context.createJSObject();
        assertThat(execute(emitter, 0, JSValue.NO_ARGS, instance)).isSameAs(instance);

        assertThatThrownBy(() -> execute(emitter, 0, JSValue.NO_ARGS, JSUndefined.INSTANCE))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("class constructors must be invoked with 'new'");
    }

    @Test
    public void testIteratorCallAndClose() {
        AtomicInteger closeCount = new AtomicInteger(0);

        JSObject iteratorWithReturn = context.createJSObject();
        JSNativeFunction returnMethod = new JSNativeFunction(context, "return", 0, (ctx, thisArg, args) -> {
            closeCount.incrementAndGet();
            JSObject result = ctx.createJSObject();
            result.set("done", JSBoolean.TRUE);
            return result;
        });
        iteratorWithReturn.set("return", returnMethod);

        BytecodeEmitter closeEmitter = new BytecodeEmitter();
        closeEmitter.emitPushConstant(iteratorWithReturn);
        closeEmitter.emitPushConstant(JSUndefined.INSTANCE);
        closeEmitter.emitIntOperand(Opcode.PUSH_I32, 0);
        closeEmitter.emitOpcode(Opcode.ITERATOR_CLOSE);
        closeEmitter.emitOpcode(Opcode.PUSH_TRUE);
        closeEmitter.emitOpcode(Opcode.RETURN);

        assertThat(execute(closeEmitter, 0, JSValue.NO_ARGS, JSUndefined.INSTANCE)).isSameAs(JSBoolean.TRUE);
        assertThat(closeCount.get()).isEqualTo(1);

        JSObject iteratorWithoutThrow = context.createJSObject();
        BytecodeEmitter callEmitter = new BytecodeEmitter();
        callEmitter.emitPushConstant(iteratorWithoutThrow);
        callEmitter.emitOpcode(Opcode.UNDEFINED);
        callEmitter.emitIntOperand(Opcode.PUSH_I32, 0);
        callEmitter.emitIntOperand(Opcode.PUSH_I32, 1);
        callEmitter.emitIntOperand(Opcode.ITERATOR_CALL, 1);
        callEmitter.emitOpcode(Opcode.RETURN);

        assertThat(execute(callEmitter, 0, JSValue.NO_ARGS, JSUndefined.INSTANCE)).isSameAs(JSBoolean.TRUE);
    }

    @Test
    public void testIteratorNextAndGetValueDone() {
        JSObject iterator = context.createJSObject();
        JSNativeFunction nextMethod = new JSNativeFunction(context, "next", 1, (ctx, thisArg, args) -> {
            JSObject result = ctx.createJSObject();
            result.set("value", args.length > 0 ? args[0] : JSUndefined.INSTANCE);
            result.set("done", JSBoolean.FALSE);
            return result;
        });
        iterator.set("next", nextMethod);

        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitPushConstant(iterator);
        emitter.emitPushConstant(nextMethod);
        emitter.emitIntOperand(Opcode.PUSH_I32, 0);
        emitter.emitIntOperand(Opcode.PUSH_I32, 9);
        emitter.emitOpcode(Opcode.ITERATOR_NEXT);
        emitter.emitOpcode(Opcode.ITERATOR_CHECK_OBJECT);
        emitter.emitOpcode(Opcode.ITERATOR_GET_VALUE_DONE);
        emitter.emitOpcode(Opcode.DROP);
        emitter.emitOpcode(Opcode.RETURN);

        assertNumberValue(execute(emitter, 0, JSValue.NO_ARGS, JSUndefined.INSTANCE), 9);
    }

    @Test
    public void testLocAndMathOpcodes() {
        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitIntOperand(Opcode.PUSH_I32, 2);
        emitter.emitIntOperand(Opcode.SET_LOC, 0);
        emitter.emitOpcode(Opcode.DROP);
        emitter.emitGetLocal(0);
        emitter.emitIntOperand(Opcode.PUSH_I32, 3);
        emitter.emitOpcode(Opcode.POW);
        emitter.emitOpcode(Opcode.LNOT);
        emitter.emitOpcode(Opcode.RETURN);

        assertThat(execute(emitter, 1, JSValue.NO_ARGS, JSUndefined.INSTANCE)).isEqualTo(JSBoolean.FALSE);
    }

    @Test
    public void testRemainderNumberOperands() {
        assertThat(context.eval("""
                [[2147483647, 3, 1], [2147483648, 3, 2], [5.5, 2, 1.5],
                 [7, 2.5, 2], [-4, 2, -0], [-0, 3, -0], [4, -2, 0],
                 [1, 0, NaN], [Infinity, 3, NaN], [3, Infinity, 3]]
                  .every(([x, y, expected]) => Object.is(x % y, expected))
                """)).isEqualTo(JSBoolean.TRUE);
    }

    @Test
    public void testSetProtoAndHomeObject() {
        JSObject prototype = context.createJSObject();
        prototype.set("marker", new JSString("ok"));
        JSObject object = context.createJSObject();

        BytecodeEmitter setProtoEmitter = new BytecodeEmitter();
        setProtoEmitter.emitPushConstant(object);
        setProtoEmitter.emitPushConstant(prototype);
        setProtoEmitter.emitOpcode(Opcode.SET_PROTO);
        setProtoEmitter.emitOpcodeAtom(Opcode.GET_FIELD, "marker");
        setProtoEmitter.emitOpcode(Opcode.RETURN);

        assertStringValue(execute(setProtoEmitter, 0, JSValue.NO_ARGS, JSUndefined.INSTANCE), "ok");

        JSObject homeObject = context.createJSObject();
        JSNativeFunction method = new JSNativeFunction(context, "m", 0, (ctx, thisArg, args) -> JSUndefined.INSTANCE);

        BytecodeEmitter homeObjectEmitter = new BytecodeEmitter();
        homeObjectEmitter.emitPushConstant(homeObject);
        homeObjectEmitter.emitPushConstant(method);
        homeObjectEmitter.emitOpcode(Opcode.SET_HOME_OBJECT);
        homeObjectEmitter.emitOpcode(Opcode.NIP);
        homeObjectEmitter.emitOpcode(Opcode.RETURN);

        execute(homeObjectEmitter, 0, JSValue.NO_ARGS, JSUndefined.INSTANCE);
        assertThat(method.getHomeObject()).isSameAs(homeObject);
    }

    @Test
    public void testVarRefAndVarInitOpcodes() {
        BytecodeEmitter localRefEmitter = new BytecodeEmitter();
        localRefEmitter.emitIntOperand(Opcode.PUSH_I32, 41);
        localRefEmitter.emitPutLocal(0);
        localRefEmitter.emitIntOperand(Opcode.CLOSE_LOC, 0);
        localRefEmitter.emitGetLocal(0);
        localRefEmitter.emitIntOperand(Opcode.PUSH_I32, 1);
        localRefEmitter.emitOpcode(Opcode.ADD);
        localRefEmitter.emitIntOperand(Opcode.SET_VAR_REF, 0);
        localRefEmitter.emitOpcode(Opcode.DROP);
        localRefEmitter.emitIntOperand(Opcode.GET_VAR_REF, 0);
        localRefEmitter.emitOpcode(Opcode.RETURN);

        JSValue[] closureVars = new JSValue[]{JSUndefined.INSTANCE};
        assertNumberValue(execute(localRefEmitter, 1, closureVars, JSUndefined.INSTANCE), 42);

        BytecodeEmitter varInitEmitter = new BytecodeEmitter();
        varInitEmitter.emitIntOperand(Opcode.GET_VAR_UNDEF, 0);
        varInitEmitter.emitOpcode(Opcode.DROP);
        varInitEmitter.emitIntOperand(Opcode.PUSH_I32, 7);
        varInitEmitter.emitIntOperand(Opcode.PUT_VAR_INIT, 0);
        varInitEmitter.emitIntOperand(Opcode.GET_VAR_UNDEF, 0);
        varInitEmitter.emitOpcode(Opcode.RETURN);

        JSValue[] initClosureVars = new JSValue[]{JSUndefined.INSTANCE};
        assertNumberValue(execute(varInitEmitter, 0, initClosureVars, JSUndefined.INSTANCE), 7);
        assertNumberValue(initClosureVars[0], 7);
    }
}
