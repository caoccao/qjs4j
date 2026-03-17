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

import com.caoccao.qjs4j.compilation.ast.*;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

/**
 * Compiles labeled statement AST nodes into bytecode.
 */
final class LabeledStatementCompiler {
    private final CompilerContext compilerContext;

    LabeledStatementCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    /**
     * Compile a labeled loop: the label is attached to the loop's LoopContext.
     * This is needed so that 'break label;' and 'continue label;' work on the loop.
     */
    private void compileLabeledLoop(String labelName, Statement loopStmt) {
        // We temporarily store the label name so the loop compilation methods can pick it up
        compilerContext.loopManager.setPendingLabel(labelName);
        compilerContext.statementCompiler.compileStatement(loopStmt);
        compilerContext.loopManager.clearPendingLabel();
    }

    /**
     * Compile a labeled statement following QuickJS js_parse_statement_or_decl.
     * Creates a break entry so that 'break label;' jumps past the labeled body.
     * For labeled loops (while/for/for-in/for-of), the label is attached to the
     * loop's LoopContext so labeled break/continue work on the loop.
     */
    void compileLabeledStatement(LabeledStatement labeledStmt) {
        String labelName = labeledStmt.getLabel().getName();
        Statement body = labeledStmt.getBody();

        // In strict mode, function declarations are not allowed as labeled statement body
        // (they must be at top level or inside a block). Per ES2024 B.3.3 Note.
        if (compilerContext.strictMode && body instanceof FunctionDeclaration) {
            throw new JSSyntaxErrorException(
                    "In strict mode code, functions can only be declared at top level or inside a block.");
        }

        // Check if the body is a loop statement — if so, the label applies to the loop
        if (body instanceof WhileStatement || body instanceof DoWhileStatement || body instanceof ForStatement
                || body instanceof ForInStatement || body instanceof ForOfStatement) {
            // Push a labeled loop context; the loop compilation will use loopStack.peek()
            // We need to wrap the loop compilation to attach the label
            compileLabeledLoop(labelName, body);
        } else {
            // Regular labeled statement: only 'break label;' is valid (not continue)
            LoopContext labelContext = new LoopContext(compilerContext.emitter.currentOffset(), compilerContext.scopeManager.getScopeDepth(), compilerContext.scopeManager.getScopeDepth(), labelName);
            labelContext.isRegularStmt = true;
            compilerContext.loopManager.pushLoop(labelContext);

            // Body can be null for empty statements (label: ;)
            if (body != null) {
                compilerContext.statementCompiler.compileStatement(body);
            }

            // Patch all break positions to jump here
            int breakTarget = compilerContext.emitter.currentOffset();
            for (int pos : labelContext.breakPositions) {
                compilerContext.emitter.patchJump(pos, breakTarget);
            }
            compilerContext.loopManager.popLoop();
        }
    }
}
