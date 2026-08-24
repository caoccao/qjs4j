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

package com.caoccao.qjs4j.exceptions;

import com.caoccao.qjs4j.compilation.ast.ASTNode;
import com.caoccao.qjs4j.compilation.ast.SourceLocation;

/**
 * Exception thrown when compilation fails.
 */
public class JSCompilerException extends RuntimeException {
    private final ASTNode ast;

    public JSCompilerException(String message) {
        this(message, null, null);
    }

    public JSCompilerException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public JSCompilerException(String message, ASTNode ast) {
        this(message, null, ast);
    }

    public JSCompilerException(String message, Throwable cause, ASTNode ast) {
        super(message, cause);
        this.ast = ast;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    /**
     * Gets the AST node associated with this compilation failure.
     *
     * @return the AST node, or {@code null} when the failure is not tied to an AST node
     */
    public ASTNode getAst() {
        return ast;
    }

    /**
     * Gets the source location of the associated AST node.
     *
     * @return the source location, or {@code null} when no AST node is associated
     */
    public SourceLocation getSourceLocation() {
        return ast == null ? null : ast.getLocation();
    }
}
