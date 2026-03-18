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

package com.caoccao.qjs4j.compilation.compiler;

import com.caoccao.qjs4j.compilation.ast.Literal;
import com.caoccao.qjs4j.core.JSBigInt;
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSRegExp;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.regexp.RegExpLiteralValue;
import com.caoccao.qjs4j.vm.Opcode;

import java.math.BigInteger;

final class LiteralCompiler {
    private final CompilerContext compilerContext;

    LiteralCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compile(Literal literal) {
        Object value = literal.getValue();

        if (value == null) {
            compilerContext.emitter.emitOpcode(Opcode.NULL);
        } else if (value instanceof Boolean bool) {
            compilerContext.emitter.emitOpcode(bool ? Opcode.PUSH_TRUE : Opcode.PUSH_FALSE);
        } else if (value instanceof BigInteger bigInt) {
            // Check BigInteger before Number since BigInteger extends Number.
            // Match QuickJS: emit PUSH_BIGINT_I32 when the literal fits in signed i32.
            if (bigInt.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
                    && bigInt.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
                compilerContext.emitter.emitOpcode(Opcode.PUSH_BIGINT_I32);
                compilerContext.emitter.emitI32(bigInt.intValue());
            } else {
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSBigInt(bigInt));
            }
        } else if (value instanceof Number num) {
            // Try to emit as i32 if it's an integer in range
            if (num instanceof Integer || num instanceof Long) {
                long longValue = num.longValue();
                if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                    compilerContext.emitter.emitOpcode(Opcode.PUSH_I32);
                    compilerContext.emitter.emitI32((int) longValue);
                    return;
                }
            }
            // Otherwise emit as constant
            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(num.doubleValue()));
        } else if (value instanceof RegExpLiteralValue regExpLiteralValue) {
            String source = regExpLiteralValue.source();
            int lastSlash = source.lastIndexOf('/');
            if (lastSlash > 0) {
                String pattern = source.substring(1, lastSlash);
                String flags = lastSlash < source.length() - 1 ? source.substring(lastSlash + 1) : "";
                try {
                    JSRegExp regexp = new JSRegExp(compilerContext.context, pattern, flags);
                    compilerContext.emitter.emitOpcodeConstant(Opcode.REGEXP, regexp);
                    return;
                } catch (Exception e) {
                    throw new JSSyntaxErrorException("Invalid regular expression literal: " + source);
                }
            }
            throw new JSSyntaxErrorException("Invalid regular expression literal: " + source);
        } else if (value instanceof String str) {
            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(str));
        } else {
            // Other types as constants
            throw new JSCompilerException("Unsupported literal type: " + value.getClass());
        }
    }
}
