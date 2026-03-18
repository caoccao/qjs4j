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

import com.caoccao.qjs4j.compilation.ast.Expression;
import com.caoccao.qjs4j.compilation.ast.Identifier;
import com.caoccao.qjs4j.compilation.ast.MemberExpression;
import com.caoccao.qjs4j.compilation.ast.PrivateIdentifier;
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.ArrayList;

final class MemberExpressionCompiler {
    private final CompilerContext compilerContext;

    MemberExpressionCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compile(MemberExpression memberExpr) {
        if (memberExpr.getObject().isSuperIdentifier()) {
            compilerContext.emitHelpers.emitGetSuperValue(memberExpr, false);
            return;
        }

        // Detect non-optional continuation of an optional chain (e.g., `.#f` in `o?.c.#f`)
        // The entire chain after `?.` must short-circuit together.
        if (!memberExpr.isOptional() && memberExpr.getObject().isPartOfOptionalChain()) {
            compileOptionalChainFull(memberExpr);
            return;
        }

        compilerContext.expressionCompiler.compile(memberExpr.getObject());

        if (memberExpr.isOptional()) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
            int jumpToUndefined = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

            emitPropertyAccess(memberExpr);

            int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

            compilerContext.emitter.patchJump(jumpToUndefined, compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);

            compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
            return;
        }

        emitPropertyAccess(memberExpr);
    }

    /**
     * Compile an optional chain as a single unit so all accesses after `?.` share
     * one short-circuit exit. E.g., `o?.c.#f` -> null-check o, then access .c and .#f
     * inside the non-null branch.
     */
    private void compileOptionalChainFull(MemberExpression memberExpr) {
        var chain = new ArrayList<MemberExpression>();
        Expression current = memberExpr;
        while (current instanceof MemberExpression mem) {
            chain.add(0, mem);
            if (mem.isOptional()) {
                break;
            }
            current = mem.getObject();
        }

        MemberExpression optionalRoot = chain.get(0);

        compilerContext.expressionCompiler.compile(optionalRoot.getObject());

        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int jumpToUndefined = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        for (MemberExpression link : chain) {
            emitPropertyAccess(link);
        }

        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        compilerContext.emitter.patchJump(jumpToUndefined, compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);

        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
    }

    private void emitPropertyAccess(MemberExpression memberExpr) {
        if (memberExpr.isComputed()) {
            compilerContext.expressionCompiler.compile(memberExpr.getProperty());
            compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        } else if (memberExpr.getProperty() instanceof PrivateIdentifier privateId) {
            String fieldName = privateId.getName();
            JSSymbol symbol = compilerContext.privateSymbols != null ? compilerContext.privateSymbols.get(fieldName) : null;
            if (symbol != null) {
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                compilerContext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
            } else {
                throw new JSSyntaxErrorException("Unexpected private field");
            }
        } else if (memberExpr.getProperty() instanceof Identifier propId) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.getName());
        }
    }
}
