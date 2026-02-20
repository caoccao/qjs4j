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

package com.caoccao.qjs4j.compilation.ast;

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.JSBytecodeFunction;
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.vm.Bytecode;
import com.caoccao.qjs4j.vm.Opcode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionExpressionCompilerTest extends BaseTest {
    private List<Opcode> decodeOpcodes(Bytecode bytecode) {
        List<Opcode> opcodes = new ArrayList<>();
        int pc = 0;
        while (pc < bytecode.getLength()) {
            int opcode = bytecode.readOpcode(pc);
            Opcode op = Opcode.fromInt(opcode);
            int step = op.getSize();
            if (op == Opcode.INVALID && pc + 1 < bytecode.getLength()) {
                int extendedOpcode = 0x100 + bytecode.readU8(pc + 1);
                Opcode extendedOp = Opcode.fromInt(extendedOpcode);
                if (extendedOp != Opcode.INVALID) {
                    op = extendedOp;
                    step = extendedOp.getSize() + 1;
                }
            }
            opcodes.add(op);
            pc += step;
        }
        return opcodes;
    }

    @Test
    void testAsyncFunctionExpressionImplicitReturnUsesReturnAsync() {
        JSValue value = context.eval("""
                const f = async function () {
                    const answer = 42;
                };
                f;
                """);
        assertThat(value).isInstanceOfSatisfying(JSBytecodeFunction.class, function -> {
            assertThat(function.isAsync()).isTrue();
            assertThat(decodeOpcodes(function.getBytecode())).contains(Opcode.RETURN_ASYNC);
        });
    }

    @Test
    void testAsyncFunctionExpressionReturnUsesReturnAsync() {
        JSValue value = context.eval("""
                const f = async function () {
                    return 42;
                };
                f;
                """);
        assertThat(value).isInstanceOfSatisfying(JSBytecodeFunction.class, function -> {
            assertThat(function.isAsync()).isTrue();
            assertThat(decodeOpcodes(function.getBytecode())).contains(Opcode.RETURN_ASYNC);
        });
    }

    @Test
    void testAsyncGeneratorFunctionExpressionYieldStarUsesAsyncYieldStarOpcode() {
        JSValue value = context.eval("""
                const g = async function* () {
                    yield* [1, 2];
                };
                g;
                """);
        assertThat(value).isInstanceOfSatisfying(JSBytecodeFunction.class, function -> {
            assertThat(function.isAsync()).isTrue();
            assertThat(function.isGenerator()).isTrue();
            List<Opcode> opcodes = decodeOpcodes(function.getBytecode());
            assertThat(opcodes).contains(Opcode.ASYNC_YIELD_STAR);
            assertThat(opcodes).doesNotContain(Opcode.YIELD_STAR);
        });
    }

    @Test
    void testAsyncIdentifierCanBeUsedAsBindingName() {
        JSValue value = context.eval("""
                let async = 3;
                async;
                """);
        assertThat(value).isInstanceOfSatisfying(JSNumber.class, number -> assertThat(number.value()).isEqualTo(3));
    }

    @Test
    void testAsyncSingleParamArrowExpressionStatementParses() {
        JSValue value = context.eval("async x => x;");
        assertThat(value).isInstanceOfSatisfying(JSBytecodeFunction.class, function -> {
            assertThat(function.isAsync()).isTrue();
            assertThat(function.isArrow()).isTrue();
        });
    }
}
