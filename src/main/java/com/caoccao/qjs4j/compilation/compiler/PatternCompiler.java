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
import com.caoccao.qjs4j.vm.Opcode;

/**
 * Delegate compiler for pattern matching and destructuring assignment compilation.
 * Handles assignment targets, pattern assignments, and destructuring for arrays and objects.
 */
final class PatternCompiler extends AstNodeCompiler<Pattern> {

    PatternCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(Pattern pattern) {
        if (pattern instanceof Identifier id) {
            compilerContext.identifierPatternCompiler.compile(id);
        } else if (pattern instanceof ObjectPattern objPattern) {
            compilerContext.objectPatternCompiler.compile(objPattern);
        } else if (pattern instanceof ArrayPattern arrPattern) {
            compilerContext.arrayPatternCompiler.compile(arrPattern);
        } else if (pattern instanceof AssignmentPattern assignPattern) {
            compilerContext.assignmentPatternCompiler.compile(assignPattern);
        } else if (pattern instanceof RestElement) {
            // RestElement should only appear inside ArrayPattern or ObjectPattern, shouldn't reach here
            throw new RuntimeException("RestElement can only appear inside ArrayPattern or ObjectPattern");
        }
    }

    /**
     * Assign the iteration value in a for-of loop.
     * For var declarations, use findLocalInScopes to store to the parent scope's local
     * (since var is function-scoped, not block-scoped). For let/const, use the normal
     * compilePatternAssignment which creates locals in the current (loop) scope.
     */
    void compileForOfValueAssignment(Pattern pattern, boolean isVar) {
        if (isVar && pattern instanceof Identifier id) {
            Integer localIndex = compilerContext.scopeManager.findLocalInScopes(id.getName());
            if (localIndex != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
            } else if (compilerContext.inGlobalScope) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, id.getName());
            } else {
                int idx = compilerContext.scopeManager.currentScope().declareLocal(id.getName());
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, idx);
            }
        } else {
            if (isVar) {
                compileVarPatternAssignment(pattern);
            } else {
                compile(pattern);
            }
        }
    }

    void compileVarPatternAssignment(Pattern pattern) {
        boolean previousValue = compilerContext.useExistingBindingInParentScopes;
        compilerContext.useExistingBindingInParentScopes = true;
        compile(pattern);
        compilerContext.useExistingBindingInParentScopes = previousValue;
    }

    /**
     * Declare all variables in a pattern (used for for-of loops with destructuring).
     * This recursively declares variables for Identifier, ArrayPattern, and ObjectPattern.
     */
    void declarePatternVariables(Pattern pattern) {
        if (pattern instanceof Identifier id) {
            // Simple identifier: declare it as a local variable
            compilerContext.scopeManager.currentScope().declareLocal(id.getName());
        } else if (pattern instanceof ArrayPattern arrPattern) {
            // Array destructuring: declare all element variables
            for (Pattern element : arrPattern.getElements()) {
                if (element != null) {
                    if (element instanceof RestElement restElement) {
                        // Rest element: declare the argument pattern
                        declarePatternVariables(restElement.getArgument());
                    } else {
                        // Regular element: recursively declare
                        declarePatternVariables(element);
                    }
                }
            }
        } else if (pattern instanceof ObjectPattern objPattern) {
            // Object destructuring: declare all property variables
            for (ObjectPatternProperty prop : objPattern.getProperties()) {
                declarePatternVariables(prop.getValue());
            }
            if (objPattern.getRestElement() != null) {
                declarePatternVariables(objPattern.getRestElement().getArgument());
            }
        } else if (pattern instanceof AssignmentPattern assignPattern) {
            // Default value pattern: declare the left-hand side
            declarePatternVariables(assignPattern.getLeft());
        } else if (pattern instanceof RestElement restElement) {
            // Rest element at top level (shouldn't normally happen, but handle it)
            declarePatternVariables(restElement.getArgument());
        }
    }

    void markPatternConstBindings(Pattern pattern) {
        if (pattern instanceof Identifier id) {
            compilerContext.scopeManager.currentScope().markConstLocal(id.getName());
            return;
        }
        if (pattern instanceof ArrayPattern arrayPattern) {
            for (Pattern element : arrayPattern.getElements()) {
                if (element != null) {
                    markPatternConstBindings(element);
                }
            }
            return;
        }
        if (pattern instanceof ObjectPattern objectPattern) {
            for (ObjectPatternProperty property : objectPattern.getProperties()) {
                markPatternConstBindings(property.getValue());
            }
            if (objectPattern.getRestElement() != null) {
                markPatternConstBindings(objectPattern.getRestElement().getArgument());
            }
            return;
        }
        if (pattern instanceof AssignmentPattern assignmentPattern) {
            markPatternConstBindings(assignmentPattern.getLeft());
            return;
        }
        if (pattern instanceof RestElement restElement) {
            markPatternConstBindings(restElement.getArgument());
        }
    }

}
