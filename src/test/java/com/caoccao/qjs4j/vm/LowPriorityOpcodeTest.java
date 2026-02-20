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
                true,
                "function test() { [bytecode] }");
        return context.getVirtualMachine().execute(function, thisArg, args);
    }

    @Test
    public void testLowLocAndArrayOpcodes() {
        BytecodeEmitter locEmitter = new BytecodeEmitter();
        locEmitter.emitOpcodeU32(Opcode.PUSH_I32, 1);
        locEmitter.emitOpcodeU16(Opcode.PUT_LOC, 0);
        locEmitter.emitOpcode(Opcode.INC_LOC);
        locEmitter.emitU8(0);
        locEmitter.emitOpcode(Opcode.DEC_LOC);
        locEmitter.emitU8(0);
        locEmitter.emitOpcodeU32(Opcode.PUSH_I32, 5);
        locEmitter.emitOpcode(Opcode.ADD_LOC);
        locEmitter.emitU8(0);
        locEmitter.emitOpcode(Opcode.NOP);
        locEmitter.emitOpcodeU16(Opcode.GET_LOC, 0);
        locEmitter.emitOpcode(Opcode.RETURN);

        JSValue locResult = execute(locEmitter, 1, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(locResult).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) locResult).value()).isEqualTo(6);

        BytecodeEmitter stringAddLocEmitter = new BytecodeEmitter();
        stringAddLocEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("a"));
        stringAddLocEmitter.emitOpcodeU16(Opcode.PUT_LOC, 0);
        stringAddLocEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("b"));
        stringAddLocEmitter.emitOpcode(Opcode.ADD_LOC);
        stringAddLocEmitter.emitU8(0);
        stringAddLocEmitter.emitOpcodeU16(Opcode.GET_LOC, 0);
        stringAddLocEmitter.emitOpcode(Opcode.RETURN);

        JSValue stringAddLocResult = execute(stringAddLocEmitter, 1, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(stringAddLocResult).isInstanceOf(JSString.class);
        assertThat(((JSString) stringAddLocResult).value()).isEqualTo("ab");

        JSArray array = context.createJSArray(new JSNumber(9), new JSNumber(4));
        BytecodeEmitter getArrayEl3Emitter = new BytecodeEmitter();
        getArrayEl3Emitter.emitOpcodeConstant(Opcode.PUSH_CONST, array);
        getArrayEl3Emitter.emitOpcodeU32(Opcode.PUSH_I32, 1);
        getArrayEl3Emitter.emitOpcode(Opcode.GET_ARRAY_EL3);
        getArrayEl3Emitter.emitOpcode(Opcode.ADD);
        getArrayEl3Emitter.emitOpcode(Opcode.NIP);
        getArrayEl3Emitter.emitOpcode(Opcode.RETURN);

        JSValue getArrayEl3Result = execute(getArrayEl3Emitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(getArrayEl3Result).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) getArrayEl3Result).value()).isEqualTo(5);

        BytecodeEmitter getArrayEl3ErrorEmitter = new BytecodeEmitter();
        getArrayEl3ErrorEmitter.emitOpcode(Opcode.UNDEFINED);
        getArrayEl3ErrorEmitter.emitOpcodeConstant(Opcode.PUSH_CONST, context.createJSObject());
        getArrayEl3ErrorEmitter.emitOpcode(Opcode.GET_ARRAY_EL3);
        getArrayEl3ErrorEmitter.emitOpcode(Opcode.RETURN);

        assertThatThrownBy(() -> execute(getArrayEl3ErrorEmitter, 0, new JSValue[0], JSUndefined.INSTANCE))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("value has no property");
    }

    @Test
    public void testShortArgAndVarRefOpcodes() {
        BytecodeEmitter argEmitter = new BytecodeEmitter();
        argEmitter.emitOpcode(Opcode.GET_ARG0);
        argEmitter.emitOpcode(Opcode.GET_ARG1);
        argEmitter.emitOpcode(Opcode.ADD);
        argEmitter.emitOpcodeU32(Opcode.PUSH_I32, 10);
        argEmitter.emitOpcode(Opcode.PUT_ARG2);
        argEmitter.emitOpcodeU32(Opcode.PUSH_I32, 11);
        argEmitter.emitOpcode(Opcode.PUT_ARG0);
        argEmitter.emitOpcodeU32(Opcode.PUSH_I32, 12);
        argEmitter.emitOpcode(Opcode.PUT_ARG1);
        argEmitter.emitOpcodeU32(Opcode.PUSH_I32, 13);
        argEmitter.emitOpcode(Opcode.PUT_ARG3);
        argEmitter.emitOpcodeU32(Opcode.PUSH_I32, 20);
        argEmitter.emitOpcode(Opcode.SET_ARG0);
        argEmitter.emitOpcode(Opcode.DROP);
        argEmitter.emitOpcodeU32(Opcode.PUSH_I32, 21);
        argEmitter.emitOpcode(Opcode.SET_ARG1);
        argEmitter.emitOpcode(Opcode.DROP);
        argEmitter.emitOpcodeU32(Opcode.PUSH_I32, 22);
        argEmitter.emitOpcode(Opcode.SET_ARG2);
        argEmitter.emitOpcode(Opcode.DROP);
        argEmitter.emitOpcodeU32(Opcode.PUSH_I32, 23);
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
        argEmitter.emitOpcodeU32(Opcode.PUSH_I32, 100);
        argEmitter.emitOpcode(Opcode.PUT_ARG);
        argEmitter.emitU16(1);
        argEmitter.emitOpcodeU16(Opcode.GET_LOC, 1);
        argEmitter.emitOpcode(Opcode.ADD);
        argEmitter.emitOpcodeU32(Opcode.PUSH_I32, 200);
        argEmitter.emitOpcode(Opcode.SET_ARG);
        argEmitter.emitU16(0);
        argEmitter.emitOpcode(Opcode.DROP);
        argEmitter.emitOpcode(Opcode.GET_ARG0);
        argEmitter.emitOpcode(Opcode.ADD);
        argEmitter.emitOpcode(Opcode.RETURN);

        JSValue argResult = execute(
                argEmitter, 4, new JSValue[0], JSUndefined.INSTANCE,
                new JSNumber(1), new JSNumber(2), new JSNumber(3), new JSNumber(4));
        assertThat(argResult).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) argResult).value()).isEqualTo(389);

        JSValue[] closureVars = new JSValue[]{
                new JSNumber(1), new JSNumber(2), new JSNumber(3), new JSNumber(4)
        };
        BytecodeEmitter varRefEmitter = new BytecodeEmitter();
        varRefEmitter.emitOpcode(Opcode.GET_VAR_REF0);
        varRefEmitter.emitOpcode(Opcode.GET_VAR_REF1);
        varRefEmitter.emitOpcode(Opcode.ADD);
        varRefEmitter.emitOpcodeU32(Opcode.PUSH_I32, 30);
        varRefEmitter.emitOpcode(Opcode.PUT_VAR_REF2);
        varRefEmitter.emitOpcodeU32(Opcode.PUSH_I32, 40);
        varRefEmitter.emitOpcode(Opcode.PUT_VAR_REF0);
        varRefEmitter.emitOpcodeU32(Opcode.PUSH_I32, 50);
        varRefEmitter.emitOpcode(Opcode.PUT_VAR_REF1);
        varRefEmitter.emitOpcodeU32(Opcode.PUSH_I32, 60);
        varRefEmitter.emitOpcode(Opcode.PUT_VAR_REF3);
        varRefEmitter.emitOpcodeU32(Opcode.PUSH_I32, 70);
        varRefEmitter.emitOpcode(Opcode.SET_VAR_REF0);
        varRefEmitter.emitOpcode(Opcode.DROP);
        varRefEmitter.emitOpcodeU32(Opcode.PUSH_I32, 71);
        varRefEmitter.emitOpcode(Opcode.SET_VAR_REF1);
        varRefEmitter.emitOpcode(Opcode.DROP);
        varRefEmitter.emitOpcodeU32(Opcode.PUSH_I32, 72);
        varRefEmitter.emitOpcode(Opcode.SET_VAR_REF2);
        varRefEmitter.emitOpcode(Opcode.DROP);
        varRefEmitter.emitOpcodeU32(Opcode.PUSH_I32, 73);
        varRefEmitter.emitOpcode(Opcode.SET_VAR_REF3);
        varRefEmitter.emitOpcode(Opcode.DROP);
        varRefEmitter.emitOpcode(Opcode.GET_VAR_REF2);
        varRefEmitter.emitOpcode(Opcode.GET_VAR_REF3);
        varRefEmitter.emitOpcode(Opcode.ADD);
        varRefEmitter.emitOpcode(Opcode.ADD);
        varRefEmitter.emitOpcodeU16(Opcode.GET_VAR_REF, 0);
        varRefEmitter.emitOpcodeU16(Opcode.GET_VAR_REF, 1);
        varRefEmitter.emitOpcode(Opcode.ADD);
        varRefEmitter.emitOpcode(Opcode.ADD);
        varRefEmitter.emitOpcodeU32(Opcode.PUSH_I32, 90);
        varRefEmitter.emitOpcodeU16(Opcode.PUT_VAR_REF, 1);
        varRefEmitter.emitOpcodeU32(Opcode.PUSH_I32, 80);
        varRefEmitter.emitOpcodeU16(Opcode.SET_VAR_REF, 0);
        varRefEmitter.emitOpcode(Opcode.DROP);
        varRefEmitter.emitOpcode(Opcode.GET_VAR_REF0);
        varRefEmitter.emitOpcode(Opcode.GET_VAR_REF1);
        varRefEmitter.emitOpcode(Opcode.ADD);
        varRefEmitter.emitOpcode(Opcode.ADD);
        varRefEmitter.emitOpcode(Opcode.RETURN);

        JSValue varRefResult = execute(varRefEmitter, 0, closureVars, JSUndefined.INSTANCE);
        assertThat(varRefResult).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) varRefResult).value()).isEqualTo(459);
        assertThat(((JSNumber) closureVars[0]).value()).isEqualTo(80);
        assertThat(((JSNumber) closureVars[1]).value()).isEqualTo(90);
        assertThat(((JSNumber) closureVars[2]).value()).isEqualTo(72);
        assertThat(((JSNumber) closureVars[3]).value()).isEqualTo(73);
    }

    @Test
    public void testShortCallAndTypeCheckOpcodes() {
        JSNativeFunction sum = new JSNativeFunction("sum", 3, (ctx, thisArg, args) -> {
            double value = 0;
            for (JSValue arg : args) {
                value += JSTypeConversions.toNumber(ctx, arg).value();
            }
            return new JSNumber(value);
        }, false);

        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, sum);
        emitter.emitOpcode(Opcode.UNDEFINED);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 1);
        emitter.emitOpcode(Opcode.CALL1);

        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, sum);
        emitter.emitOpcode(Opcode.UNDEFINED);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 2);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 3);
        emitter.emitOpcode(Opcode.CALL2);
        emitter.emitOpcode(Opcode.ADD);

        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, sum);
        emitter.emitOpcode(Opcode.UNDEFINED);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 4);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 5);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 6);
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

        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, sum);
        emitter.emitOpcode(Opcode.TYPEOF_IS_FUNCTION);
        emitter.emitOpcode(Opcode.PLUS);
        emitter.emitOpcode(Opcode.ADD);
        emitter.emitOpcode(Opcode.RETURN);

        JSValue result = execute(emitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(result).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) result).value()).isEqualTo(25);
    }

    @Test
    public void testShortJumpOpcodes() {
        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitOpcode(Opcode.PUSH_TRUE);
        emitter.emitOpcode(Opcode.IF_TRUE8);
        emitter.emitU8(5);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 100);

        emitter.emitOpcode(Opcode.PUSH_FALSE);
        emitter.emitOpcode(Opcode.IF_FALSE8);
        emitter.emitU8(5);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 200);

        emitter.emitOpcodeU32(Opcode.PUSH_I32, 1);
        emitter.emitOpcode(Opcode.GOTO8);
        emitter.emitU8(5);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 999);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 2);
        emitter.emitOpcode(Opcode.ADD);

        emitter.emitOpcode(Opcode.GOTO16);
        emitter.emitU16(5);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 888);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 4);
        emitter.emitOpcode(Opcode.ADD);
        emitter.emitOpcode(Opcode.RETURN);

        JSValue result = execute(emitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(result).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) result).value()).isEqualTo(7);
    }

    @Test
    public void testShortLocOpcodes() {
        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 10);
        emitter.emitOpcode(Opcode.PUT_LOC0);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 20);
        emitter.emitOpcode(Opcode.PUT_LOC1);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 25);
        emitter.emitOpcode(Opcode.PUT_LOC2);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 30);
        emitter.emitOpcode(Opcode.PUT_LOC8);
        emitter.emitU8(2);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 40);
        emitter.emitOpcode(Opcode.PUT_LOC3);

        emitter.emitOpcodeU32(Opcode.PUSH_I32, 60);
        emitter.emitOpcode(Opcode.SET_LOC0);
        emitter.emitOpcode(Opcode.DROP);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 70);
        emitter.emitOpcode(Opcode.SET_LOC1);
        emitter.emitOpcode(Opcode.DROP);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 80);
        emitter.emitOpcode(Opcode.SET_LOC2);
        emitter.emitOpcode(Opcode.DROP);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 90);
        emitter.emitOpcode(Opcode.SET_LOC3);
        emitter.emitOpcode(Opcode.DROP);
        emitter.emitOpcodeU32(Opcode.PUSH_I32, 50);
        emitter.emitOpcode(Opcode.SET_LOC8);
        emitter.emitU8(0);
        emitter.emitOpcode(Opcode.DROP);

        emitter.emitOpcode(Opcode.GET_LOC0);
        emitter.emitOpcode(Opcode.GET_LOC1);
        emitter.emitOpcode(Opcode.ADD);
        emitter.emitOpcode(Opcode.GET_LOC2);
        emitter.emitOpcode(Opcode.ADD);
        emitter.emitOpcode(Opcode.GET_LOC3);
        emitter.emitOpcode(Opcode.ADD);
        emitter.emitOpcode(Opcode.GET_LOC8);
        emitter.emitU8(0);
        emitter.emitOpcode(Opcode.ADD);
        emitter.emitOpcode(Opcode.RETURN);

        JSValue result = execute(emitter, 4, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(result).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) result).value()).isEqualTo(340);
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

        JSValue shortPushResult = execute(shortPushEmitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(shortPushResult).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) shortPushResult).value()).isEqualTo(-999);

        BytecodeEmitter const8Emitter = new BytecodeEmitter();
        const8Emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSNumber(42));
        const8Emitter.emitOpcode(Opcode.DROP);
        const8Emitter.emitOpcode(Opcode.PUSH_CONST8);
        const8Emitter.emitU8(0);
        const8Emitter.emitOpcode(Opcode.RETURN);

        JSValue const8Result = execute(const8Emitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(const8Result).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) const8Result).value()).isEqualTo(42);

        JSNativeFunction callable = new JSNativeFunction("c", 0, (ctx, thisArg, args) -> new JSNumber(77), false);
        BytecodeEmitter closure8Emitter = new BytecodeEmitter();
        closure8Emitter.emitOpcodeConstant(Opcode.PUSH_CONST, callable);
        closure8Emitter.emitOpcode(Opcode.DROP);
        closure8Emitter.emitOpcode(Opcode.PUSH_EMPTY_STRING);
        closure8Emitter.emitOpcode(Opcode.DROP);
        closure8Emitter.emitOpcode(Opcode.FCLOSURE8);
        closure8Emitter.emitU8(0);
        closure8Emitter.emitOpcode(Opcode.UNDEFINED);
        closure8Emitter.emitOpcode(Opcode.CALL0);
        closure8Emitter.emitOpcode(Opcode.RETURN);

        JSValue closure8Result = execute(closure8Emitter, 0, new JSValue[0], JSUndefined.INSTANCE);
        assertThat(closure8Result).isInstanceOf(JSNumber.class);
        assertThat(((JSNumber) closure8Result).value()).isEqualTo(77);
    }
}
