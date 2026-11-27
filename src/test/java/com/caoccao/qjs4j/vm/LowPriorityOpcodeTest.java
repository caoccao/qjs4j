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
import com.caoccao.qjs4j.compilation.compiler.BytecodeEmitter;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LowPriorityOpcodeTest extends BaseTest {
    private JSValue execute(BytecodeEmitter emitter, int localCount, JSValue[] closureVars, JSValue thisArg,
            JSValue... args) {
        JSBytecodeFunction function = new JSBytecodeFunction(context, emitter.build(localCount), "test", args.length,
                closureVars, null, true, false, false, false, true, "function test() { [bytecode] }");
        return context.getVirtualMachine().execute(function, thisArg, args);
    }

    @Test
    public void testClosureOpcodesCaptureArrowLexicalState() {
        JSBytecodeFunction template = (JSBytecodeFunction) context
                .eval("(function () { return () => this.tag + arguments[0]; })()");
        JSObject receiver = context.createJSObject();
        receiver.set("tag", JSNumber.of(40));
        for (Opcode opcode : new Opcode[]{Opcode.FCLOSURE, Opcode.FCLOSURE8}) {
            BytecodeEmitter emitter = new BytecodeEmitter();
            emitter.emitPushConstant(template);
            emitter.emitOpcode(Opcode.DROP);
            emitter.emitOpcode(opcode);
            if (opcode == Opcode.FCLOSURE8) {
                emitter.emitU8(0);
            } else {
                emitter.emitU32(0);
            }
            emitter.emitOpcode(Opcode.RETURN);

            JSValue result = execute(emitter, 0, JSValue.NO_ARGS, receiver, JSNumber.of(2));
            assertThat(result).isInstanceOf(JSBytecodeFunction.class).isNotSameAs(template);
            JSBytecodeFunction closure = (JSBytecodeFunction) result;
            assertThat(closure.getCapturedThisArg()).isSameAs(receiver);
            assertThat(closure.getPrototype()).isSameAs(template.getPrototype());
            assertNumberValue(closure.call(context, JSUndefined.INSTANCE, JSValue.NO_ARGS), 42);
        }
    }

    @Test
    public void testLowLocAndArrayOpcodes() {
        BytecodeEmitter locEmitter = new BytecodeEmitter();
        locEmitter.emitIntOperand(Opcode.PUSH_I32, 1);
        locEmitter.emitPutLocal(0);
        locEmitter.emitOpcode(Opcode.INC_LOC);
        locEmitter.emitU8(0);
        locEmitter.emitOpcode(Opcode.DEC_LOC);
        locEmitter.emitU8(0);
        locEmitter.emitIntOperand(Opcode.PUSH_I32, 5);
        locEmitter.emitOpcode(Opcode.ADD_LOC);
        locEmitter.emitU8(0);
        locEmitter.emitOpcode(Opcode.NOP);
        locEmitter.emitGetLocal(0);
        locEmitter.emitOpcode(Opcode.RETURN);

        assertNumberValue(execute(locEmitter, 1, JSValue.NO_ARGS, JSUndefined.INSTANCE), 6);

        BytecodeEmitter stringAddLocEmitter = new BytecodeEmitter();
        stringAddLocEmitter.emitPushConstant(new JSString("a"));
        stringAddLocEmitter.emitPutLocal(0);
        stringAddLocEmitter.emitPushConstant(new JSString("b"));
        stringAddLocEmitter.emitOpcode(Opcode.ADD_LOC);
        stringAddLocEmitter.emitU8(0);
        stringAddLocEmitter.emitGetLocal(0);
        stringAddLocEmitter.emitOpcode(Opcode.RETURN);

        assertStringValue(execute(stringAddLocEmitter, 1, JSValue.NO_ARGS, JSUndefined.INSTANCE), "ab");

        JSArray array = context.createJSArray(new JSNumber(9), new JSNumber(4));
        BytecodeEmitter getArrayEl3Emitter = new BytecodeEmitter();
        getArrayEl3Emitter.emitPushConstant(array);
        getArrayEl3Emitter.emitIntOperand(Opcode.PUSH_I32, 1);
        getArrayEl3Emitter.emitOpcode(Opcode.GET_ARRAY_EL3);
        getArrayEl3Emitter.emitOpcode(Opcode.ADD);
        getArrayEl3Emitter.emitOpcode(Opcode.NIP);
        getArrayEl3Emitter.emitOpcode(Opcode.RETURN);

        assertNumberValue(execute(getArrayEl3Emitter, 0, JSValue.NO_ARGS, JSUndefined.INSTANCE), 5);

        BytecodeEmitter getArrayEl3ErrorEmitter = new BytecodeEmitter();
        getArrayEl3ErrorEmitter.emitOpcode(Opcode.UNDEFINED);
        getArrayEl3ErrorEmitter.emitPushConstant(context.createJSObject());
        getArrayEl3ErrorEmitter.emitOpcode(Opcode.GET_ARRAY_EL3);
        getArrayEl3ErrorEmitter.emitOpcode(Opcode.RETURN);

        assertThatThrownBy(() -> execute(getArrayEl3ErrorEmitter, 0, JSValue.NO_ARGS, JSUndefined.INSTANCE))
                .isInstanceOf(JSVirtualMachineException.class).hasMessageContaining("value has no property");
    }

    @Test
    public void testShortArgAndVarRefOpcodes() {
        BytecodeEmitter argEmitter = new BytecodeEmitter();
        argEmitter.emitOpcode(Opcode.GET_ARG0);
        argEmitter.emitOpcode(Opcode.GET_ARG1);
        argEmitter.emitOpcode(Opcode.ADD);
        argEmitter.emitIntOperand(Opcode.PUSH_I32, 10);
        argEmitter.emitOpcode(Opcode.PUT_ARG2);
        argEmitter.emitIntOperand(Opcode.PUSH_I32, 11);
        argEmitter.emitOpcode(Opcode.PUT_ARG0);
        argEmitter.emitIntOperand(Opcode.PUSH_I32, 12);
        argEmitter.emitOpcode(Opcode.PUT_ARG1);
        argEmitter.emitIntOperand(Opcode.PUSH_I32, 13);
        argEmitter.emitOpcode(Opcode.PUT_ARG3);
        argEmitter.emitIntOperand(Opcode.PUSH_I32, 20);
        argEmitter.emitOpcode(Opcode.SET_ARG0);
        argEmitter.emitOpcode(Opcode.DROP);
        argEmitter.emitIntOperand(Opcode.PUSH_I32, 21);
        argEmitter.emitOpcode(Opcode.SET_ARG1);
        argEmitter.emitOpcode(Opcode.DROP);
        argEmitter.emitIntOperand(Opcode.PUSH_I32, 22);
        argEmitter.emitOpcode(Opcode.SET_ARG2);
        argEmitter.emitOpcode(Opcode.DROP);
        argEmitter.emitIntOperand(Opcode.PUSH_I32, 23);
        argEmitter.emitOpcode(Opcode.SET_ARG3);
        argEmitter.emitOpcode(Opcode.DROP);
        argEmitter.emitOpcode(Opcode.GET_ARG2);
        argEmitter.emitOpcode(Opcode.GET_ARG3);
        argEmitter.emitOpcode(Opcode.ADD);
        argEmitter.emitOpcode(Opcode.ADD);
        argEmitter.emitOpcode(Opcode.GET_ARG);
        argEmitter.emitU16(0);
        argEmitter.emitOpcode(Opcode.GET_ARG);
        argEmitter.emitU16(1);
        argEmitter.emitOpcode(Opcode.ADD);
        argEmitter.emitOpcode(Opcode.ADD);
        argEmitter.emitIntOperand(Opcode.PUSH_I32, 100);
        argEmitter.emitOpcode(Opcode.PUT_ARG);
        argEmitter.emitU16(1);
        argEmitter.emitGetLocal(1);
        argEmitter.emitOpcode(Opcode.ADD);
        argEmitter.emitIntOperand(Opcode.PUSH_I32, 200);
        argEmitter.emitOpcode(Opcode.SET_ARG);
        argEmitter.emitU16(0);
        argEmitter.emitOpcode(Opcode.DROP);
        argEmitter.emitOpcode(Opcode.GET_ARG0);
        argEmitter.emitOpcode(Opcode.ADD);
        argEmitter.emitOpcode(Opcode.RETURN);

        JSValue argResult = execute(argEmitter, 4, JSValue.NO_ARGS, JSUndefined.INSTANCE, new JSNumber(1),
                new JSNumber(2), new JSNumber(3), new JSNumber(4));
        assertNumberValue(argResult, 389);

        JSValue[] closureVars = numberArgs(1, 2, 3, 4);
        BytecodeEmitter varRefEmitter = new BytecodeEmitter();
        varRefEmitter.emitOpcode(Opcode.GET_VAR_REF0);
        varRefEmitter.emitOpcode(Opcode.GET_VAR_REF1);
        varRefEmitter.emitOpcode(Opcode.ADD);
        varRefEmitter.emitIntOperand(Opcode.PUSH_I32, 30);
        varRefEmitter.emitOpcode(Opcode.PUT_VAR_REF2);
        varRefEmitter.emitIntOperand(Opcode.PUSH_I32, 40);
        varRefEmitter.emitOpcode(Opcode.PUT_VAR_REF0);
        varRefEmitter.emitIntOperand(Opcode.PUSH_I32, 50);
        varRefEmitter.emitOpcode(Opcode.PUT_VAR_REF1);
        varRefEmitter.emitIntOperand(Opcode.PUSH_I32, 60);
        varRefEmitter.emitOpcode(Opcode.PUT_VAR_REF3);
        varRefEmitter.emitIntOperand(Opcode.PUSH_I32, 70);
        varRefEmitter.emitOpcode(Opcode.SET_VAR_REF0);
        varRefEmitter.emitOpcode(Opcode.DROP);
        varRefEmitter.emitIntOperand(Opcode.PUSH_I32, 71);
        varRefEmitter.emitOpcode(Opcode.SET_VAR_REF1);
        varRefEmitter.emitOpcode(Opcode.DROP);
        varRefEmitter.emitIntOperand(Opcode.PUSH_I32, 72);
        varRefEmitter.emitOpcode(Opcode.SET_VAR_REF2);
        varRefEmitter.emitOpcode(Opcode.DROP);
        varRefEmitter.emitIntOperand(Opcode.PUSH_I32, 73);
        varRefEmitter.emitOpcode(Opcode.SET_VAR_REF3);
        varRefEmitter.emitOpcode(Opcode.DROP);
        varRefEmitter.emitOpcode(Opcode.GET_VAR_REF2);
        varRefEmitter.emitOpcode(Opcode.GET_VAR_REF3);
        varRefEmitter.emitOpcode(Opcode.ADD);
        varRefEmitter.emitOpcode(Opcode.ADD);
        varRefEmitter.emitIntOperand(Opcode.GET_VAR_REF, 0);
        varRefEmitter.emitIntOperand(Opcode.GET_VAR_REF, 1);
        varRefEmitter.emitOpcode(Opcode.ADD);
        varRefEmitter.emitOpcode(Opcode.ADD);
        varRefEmitter.emitIntOperand(Opcode.PUSH_I32, 90);
        varRefEmitter.emitIntOperand(Opcode.PUT_VAR_REF, 1);
        varRefEmitter.emitIntOperand(Opcode.PUSH_I32, 80);
        varRefEmitter.emitIntOperand(Opcode.SET_VAR_REF, 0);
        varRefEmitter.emitOpcode(Opcode.DROP);
        varRefEmitter.emitOpcode(Opcode.GET_VAR_REF0);
        varRefEmitter.emitOpcode(Opcode.GET_VAR_REF1);
        varRefEmitter.emitOpcode(Opcode.ADD);
        varRefEmitter.emitOpcode(Opcode.ADD);
        varRefEmitter.emitOpcode(Opcode.RETURN);

        assertNumberValue(execute(varRefEmitter, 0, closureVars, JSUndefined.INSTANCE), 459);
        assertThat(((JSNumber) closureVars[0]).value()).isEqualTo(80);
        assertThat(((JSNumber) closureVars[1]).value()).isEqualTo(90);
        assertThat(((JSNumber) closureVars[2]).value()).isEqualTo(72);
        assertThat(((JSNumber) closureVars[3]).value()).isEqualTo(73);
    }

    @Test
    public void testShortCallAndTypeCheckOpcodes() {
        JSNativeFunction sum = new JSNativeFunction(context, "sum", 3, (ctx, thisArg, args) -> {
            double value = 0;
            for (JSValue arg : args) {
                value += JSTypeConversions.toNumber(ctx, arg).value();
            }
            return new JSNumber(value);
        }, false);

        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitPushConstant(sum);
        emitter.emitOpcode(Opcode.UNDEFINED);
        emitter.emitIntOperand(Opcode.PUSH_I32, 1);
        emitter.emitOpcode(Opcode.CALL1);

        emitter.emitPushConstant(sum);
        emitter.emitOpcode(Opcode.UNDEFINED);
        emitter.emitIntOperand(Opcode.PUSH_I32, 2);
        emitter.emitIntOperand(Opcode.PUSH_I32, 3);
        emitter.emitOpcode(Opcode.CALL2);
        emitter.emitOpcode(Opcode.ADD);

        emitter.emitPushConstant(sum);
        emitter.emitOpcode(Opcode.UNDEFINED);
        emitter.emitIntOperand(Opcode.PUSH_I32, 4);
        emitter.emitIntOperand(Opcode.PUSH_I32, 5);
        emitter.emitIntOperand(Opcode.PUSH_I32, 6);
        emitter.emitOpcode(Opcode.CALL3);
        emitter.emitOpcode(Opcode.ADD);

        emitter.emitOpcode(Opcode.UNDEFINED);
        emitter.emitOpcode(Opcode.IS_UNDEFINED);
        emitter.emitOpcode(Opcode.PLUS);
        emitter.emitOpcode(Opcode.ADD);

        emitter.emitOpcode(Opcode.NULL);
        emitter.emitOpcode(Opcode.IS_NULL);
        emitter.emitOpcode(Opcode.PLUS);
        emitter.emitOpcode(Opcode.ADD);

        emitter.emitOpcode(Opcode.UNDEFINED);
        emitter.emitOpcode(Opcode.TYPEOF_IS_UNDEFINED);
        emitter.emitOpcode(Opcode.PLUS);
        emitter.emitOpcode(Opcode.ADD);

        emitter.emitPushConstant(sum);
        emitter.emitOpcode(Opcode.TYPEOF_IS_FUNCTION);
        emitter.emitOpcode(Opcode.PLUS);
        emitter.emitOpcode(Opcode.ADD);
        emitter.emitOpcode(Opcode.RETURN);

        assertNumberValue(execute(emitter, 0, JSValue.NO_ARGS, JSUndefined.INSTANCE), 25);
    }

    @Test
    public void testShortJumpOpcodes() {
        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitOpcode(Opcode.PUSH_TRUE);
        emitter.emitIntOperand(Opcode.IF_TRUE8, 5);
        emitter.emitIntOperand(Opcode.PUSH_I32, 100);

        emitter.emitOpcode(Opcode.PUSH_FALSE);
        emitter.emitIntOperand(Opcode.IF_FALSE8, 5);
        emitter.emitIntOperand(Opcode.PUSH_I32, 200);

        emitter.emitIntOperand(Opcode.PUSH_I32, 1);
        emitter.emitIntOperand(Opcode.GOTO8, 5);
        emitter.emitIntOperand(Opcode.PUSH_I32, 999);
        emitter.emitIntOperand(Opcode.PUSH_I32, 2);
        emitter.emitOpcode(Opcode.ADD);

        emitter.emitIntOperand(Opcode.GOTO16, 5);
        emitter.emitIntOperand(Opcode.PUSH_I32, 888);
        emitter.emitIntOperand(Opcode.PUSH_I32, 4);
        emitter.emitOpcode(Opcode.ADD);
        emitter.emitOpcode(Opcode.RETURN);

        assertNumberValue(execute(emitter, 0, JSValue.NO_ARGS, JSUndefined.INSTANCE), 7);
    }

    @Test
    public void testShortLocOpcodes() {
        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitIntOperand(Opcode.PUSH_I32, 10);
        emitter.emitOpcode(Opcode.PUT_LOC0);
        emitter.emitIntOperand(Opcode.PUSH_I32, 20);
        emitter.emitOpcode(Opcode.PUT_LOC1);
        emitter.emitIntOperand(Opcode.PUSH_I32, 25);
        emitter.emitOpcode(Opcode.PUT_LOC2);
        emitter.emitIntOperand(Opcode.PUSH_I32, 30);
        emitter.emitIntOperand(Opcode.PUT_LOC8, 2);
        emitter.emitIntOperand(Opcode.PUSH_I32, 40);
        emitter.emitOpcode(Opcode.PUT_LOC3);

        emitter.emitIntOperand(Opcode.PUSH_I32, 60);
        emitter.emitOpcode(Opcode.SET_LOC0);
        emitter.emitOpcode(Opcode.DROP);
        emitter.emitIntOperand(Opcode.PUSH_I32, 70);
        emitter.emitOpcode(Opcode.SET_LOC1);
        emitter.emitOpcode(Opcode.DROP);
        emitter.emitIntOperand(Opcode.PUSH_I32, 80);
        emitter.emitOpcode(Opcode.SET_LOC2);
        emitter.emitOpcode(Opcode.DROP);
        emitter.emitIntOperand(Opcode.PUSH_I32, 90);
        emitter.emitOpcode(Opcode.SET_LOC3);
        emitter.emitOpcode(Opcode.DROP);
        emitter.emitIntOperand(Opcode.PUSH_I32, 50);
        emitter.emitIntOperand(Opcode.SET_LOC8, 0);
        emitter.emitOpcode(Opcode.DROP);

        emitter.emitOpcode(Opcode.GET_LOC0);
        emitter.emitOpcode(Opcode.GET_LOC1);
        emitter.emitOpcode(Opcode.ADD);
        emitter.emitOpcode(Opcode.GET_LOC2);
        emitter.emitOpcode(Opcode.ADD);
        emitter.emitOpcode(Opcode.GET_LOC3);
        emitter.emitOpcode(Opcode.ADD);
        emitter.emitIntOperand(Opcode.GET_LOC8, 0);
        emitter.emitOpcode(Opcode.ADD);
        emitter.emitOpcode(Opcode.RETURN);

        assertNumberValue(execute(emitter, 4, JSValue.NO_ARGS, JSUndefined.INSTANCE), 340);
    }

    @Test
    public void testShortPushAndConstOpcodes() {
        BytecodeEmitter shortPushEmitter = new BytecodeEmitter();
        shortPushEmitter.emitOpcode(Opcode.PUSH_MINUS1);
        shortPushEmitter.emitOpcode(Opcode.PUSH_7);
        shortPushEmitter.emitOpcode(Opcode.ADD);
        shortPushEmitter.emitOpcode(Opcode.PUSH_I8);
        shortPushEmitter.emitU8(0xFB);
        shortPushEmitter.emitOpcode(Opcode.ADD);
        shortPushEmitter.emitOpcode(Opcode.PUSH_I16);
        shortPushEmitter.emitU16(0xFC18);
        shortPushEmitter.emitOpcode(Opcode.ADD);
        shortPushEmitter.emitOpcode(Opcode.RETURN);

        assertNumberValue(execute(shortPushEmitter, 0, JSValue.NO_ARGS, JSUndefined.INSTANCE), -999);

        BytecodeEmitter const8Emitter = new BytecodeEmitter();
        const8Emitter.emitPushConstant(new JSNumber(42));
        const8Emitter.emitOpcode(Opcode.DROP);
        const8Emitter.emitOpcode(Opcode.PUSH_CONST8);
        const8Emitter.emitU8(0);
        const8Emitter.emitOpcode(Opcode.RETURN);

        assertNumberValue(execute(const8Emitter, 0, JSValue.NO_ARGS, JSUndefined.INSTANCE), 42);

        JSNativeFunction callable = new JSNativeFunction(context, "c", 0, (ctx, thisArg, args) -> new JSNumber(77),
                false);
        BytecodeEmitter closure8Emitter = new BytecodeEmitter();
        closure8Emitter.emitPushConstant(callable);
        closure8Emitter.emitOpcode(Opcode.DROP);
        closure8Emitter.emitOpcode(Opcode.PUSH_EMPTY_STRING);
        closure8Emitter.emitOpcode(Opcode.DROP);
        closure8Emitter.emitOpcode(Opcode.FCLOSURE8);
        closure8Emitter.emitU8(0);
        closure8Emitter.emitOpcode(Opcode.UNDEFINED);
        closure8Emitter.emitOpcode(Opcode.CALL0);
        closure8Emitter.emitOpcode(Opcode.RETURN);

        assertNumberValue(execute(closure8Emitter, 0, JSValue.NO_ARGS, JSUndefined.INSTANCE), 77);
    }
}
