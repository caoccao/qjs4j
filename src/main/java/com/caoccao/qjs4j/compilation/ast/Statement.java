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

import com.caoccao.qjs4j.core.JSKeyword;

import java.util.List;

/**
 * Base sealed class for all statement nodes.
 */
public abstract sealed class Statement extends ASTNode permits
        ExpressionStatement, BlockStatement, IfStatement, WhileStatement, DoWhileStatement,
        ForStatement, ForOfStatement, ForInStatement, ReturnStatement, BreakStatement, ContinueStatement,
        ThrowStatement, TryStatement, SwitchStatement, WithStatement, DebuggerStatement, VariableDeclaration,
        LabeledStatement, Declaration {

    protected List<VariableDeclarator> varDeclarators;

    protected Statement(SourceLocation location) {
        super(location);
        this.varDeclarators = null;
    }

    public boolean containsVarArguments() {
        for (VariableDeclarator declarator : getVarDeclarators()) {
            if (declarator != null
                    && declarator.getId() instanceof Identifier identifier
                    && JSKeyword.ARGUMENTS.equals(identifier.getName())) {
                return true;
            }
        }
        return false;
    }

    public List<VariableDeclarator> getVarDeclarators() {
        if (varDeclarators == null) {
            varDeclarators = List.of();
        }
        return varDeclarators;
    }

    /**
     * Unwrap nested labeled statements to find a FunctionDeclaration.
     * Per Annex B.3.2, labeled function declarations in sloppy mode are
     * hoisted like regular function declarations.
     * Returns null if the statement is not a (possibly labeled) function declaration.
     */
    public FunctionDeclaration unwrapLabeledFunctionDeclaration() {
        if (this instanceof FunctionDeclaration functionDeclaration) {
            return functionDeclaration;
        }
        if (this instanceof LabeledStatement labeledStatement && labeledStatement.getBody() != null) {
            return labeledStatement.getBody().unwrapLabeledFunctionDeclaration();
        }
        return null;
    }
}
