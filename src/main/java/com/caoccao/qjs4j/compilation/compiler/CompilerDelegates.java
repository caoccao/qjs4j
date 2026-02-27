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

/**
 * Container for all compiler delegate instances.
 * Provides cross-delegate access so each delegate can call methods on other delegates.
 */
final class CompilerDelegates {
    final CompilerAnalysis analysis;
    final EmitHelpers emitHelpers;
    final ExpressionCompiler expressions;
    final FunctionClassCompiler functions;
    final PatternCompiler patterns;
    final StatementCompiler statements;

    CompilerDelegates(CompilerContext compilerContext) {
        this.expressions = new ExpressionCompiler(compilerContext, this);
        this.statements = new StatementCompiler(compilerContext, this);
        this.functions = new FunctionClassCompiler(compilerContext, this);
        this.patterns = new PatternCompiler(compilerContext, this);
        this.emitHelpers = new EmitHelpers(compilerContext, this);
        this.analysis = new CompilerAnalysis(compilerContext, this);
    }
}
