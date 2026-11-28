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
 * Delegate compiler for pattern matching and destructuring assignment compilation. Handles assignment targets, pattern
 * assignments, and destructuring for arrays and objects.
 */
final class PatternCompiler {

    private PatternCompiler() {
    }

    static void compile(CompilerContext compilerContext, Pattern pattern) {
        if (pattern instanceof Identifier id) {
            IdentifierPatternCompiler.compile(compilerContext, id);
        } else if (pattern instanceof ObjectPattern objPattern) {
            ObjectPatternCompiler.compile(compilerContext, objPattern);
        } else if (pattern instanceof ArrayPattern arrPattern) {
            ArrayPatternCompiler.compile(compilerContext, arrPattern);
        } else if (pattern instanceof AssignmentPattern assignPattern) {
            AssignmentPatternCompiler.compile(compilerContext, assignPattern);
        } else if (pattern instanceof RestElement) {
            // RestElement should only appear inside ArrayPattern or ObjectPattern, shouldn't reach here
            throw new RuntimeException("RestElement can only appear inside ArrayPattern or ObjectPattern");
        }
    }

    /**
     * Assign the iteration value in a for-of loop. For var declarations, use findLocalInScopes to store to the parent
     * scope's local (since var is function-scoped, not block-scoped). For let/const, use the normal
     * compilePatternAssignment which creates locals in the current (loop) scope.
     */
    static void compileForOfValueAssignment(CompilerContext compilerContext, Pattern pattern, boolean isVar) {
        BytecodeEmitter emitter = compilerContext.emitter;
        if (isVar && pattern instanceof Identifier id) {
            Integer localIndex = compilerContext.scopeManager.findLocalInScopes(id.getName());
            if (localIndex != null) {
                emitter.emitPutLocal(localIndex);
            } else if (compilerContext.inGlobalScope) {
                emitter.emitOpcodeAtom(Opcode.PUT_VAR, id.getName());
            } else {
                int idx = compilerContext.scopeManager.currentScope().declareLocal(id.getName());
                emitter.emitPutLocal(idx);
            }
        } else {
            if (isVar) {
                compileVarPatternAssignment(compilerContext, pattern);
            } else {
                compile(compilerContext, pattern);
            }
        }
    }

    static void compileVarPatternAssignment(CompilerContext compilerContext, Pattern pattern) {
        boolean previousValue = compilerContext.useExistingBindingInParentScopes;
        compilerContext.useExistingBindingInParentScopes = true;
        compile(compilerContext, pattern);
        compilerContext.useExistingBindingInParentScopes = previousValue;
    }

    /**
     * Declare all variables in a pattern (used for for-of loops with destructuring). This recursively declares
     * variables for Identifier, ArrayPattern, and ObjectPattern.
     */
    static void declarePatternVariables(CompilerContext compilerContext, Pattern pattern) {
        if (pattern instanceof Identifier id) {
            // Simple identifier: declare it as a local variable
            compilerContext.scopeManager.currentScope().declareLocal(id.getName());
        } else if (pattern instanceof ArrayPattern arrPattern) {
            // Array destructuring: declare all element variables
            for (Pattern element : arrPattern.getElements()) {
                if (element != null) {
                    if (element instanceof RestElement restElement) {
                        // Rest element: declare the argument pattern
                        declarePatternVariables(compilerContext, restElement.getArgument());
                    } else {
                        // Regular element: recursively declare
                        declarePatternVariables(compilerContext, element);
                    }
                }
            }
        } else if (pattern instanceof ObjectPattern objPattern) {
            // Object destructuring: declare all property variables
            for (ObjectPatternProperty prop : objPattern.getProperties()) {
                declarePatternVariables(compilerContext, prop.getValue());
            }
            if (objPattern.getRestElement() != null) {
                declarePatternVariables(compilerContext, objPattern.getRestElement().getArgument());
            }
        } else if (pattern instanceof AssignmentPattern assignPattern) {
            // Default value pattern: declare the left-hand side
            declarePatternVariables(compilerContext, assignPattern.getLeft());
        } else if (pattern instanceof RestElement restElement) {
            // Rest element at top level (shouldn't normally happen, but handle it)
            declarePatternVariables(compilerContext, restElement.getArgument());
        }
    }

    /**
     * Emit CLOSE_LOC opcodes for variables in a pattern. Recursively handles Identifier, ArrayPattern, ObjectPattern,
     * AssignmentPattern, and RestElement.
     */
    static void emitCloseLocForPattern(CompilerContext compilerContext, Pattern pattern) {
        if (pattern != null) {
            for (String name : pattern.getBoundNames()) {
                Integer localIndex = compilerContext.scopeManager.findLocalInScopes(name);
                if (localIndex != null) {
                    compilerContext.emitter.emitIntOperand(Opcode.CLOSE_LOC, localIndex);
                }
            }
        }
    }

    /**
     * Emit CLOSE_LOC opcodes for variables declared in a VariableDeclaration. Used at the end of for-loop iteration
     * bodies to freeze VarRefs for per-iteration binding.
     */
    static void emitCloseLocForPattern(CompilerContext compilerContext, VariableDeclaration varDecl) {
        for (VariableDeclarator decl : varDecl.getDeclarations()) {
            emitCloseLocForPattern(compilerContext, decl.getId());
        }
    }

    /** Stack: value -> value, evaluating the initializer only for undefined. */
    static void emitDefaultValue(CompilerContext compilerContext, String inferredName, Expression initializer) {
        BytecodeEmitter emitter = compilerContext.emitter;
        emitter.emitOpcode(Opcode.DUP);
        emitter.emitOpcode(Opcode.IS_UNDEFINED);
        int skip = emitter.emitJump(Opcode.IF_FALSE);
        emitter.emitOpcode(Opcode.DROP);
        compilerContext.compileExpression(initializer);
        if (inferredName != null && initializer.isAnonymousFunction()) {
            emitter.emitOpcodeAtom(Opcode.SET_NAME, inferredName);
        }
        emitter.patchJumpToHere(skip);
    }

    static void markPatternConstBindings(CompilerContext compilerContext, Pattern pattern) {
        if (pattern instanceof Identifier id) {
            compilerContext.scopeManager.currentScope().markConstLocal(id.getName());
            return;
        }
        if (pattern instanceof ArrayPattern arrayPattern) {
            for (Pattern element : arrayPattern.getElements()) {
                if (element != null) {
                    markPatternConstBindings(compilerContext, element);
                }
            }
            return;
        }
        if (pattern instanceof ObjectPattern objectPattern) {
            for (ObjectPatternProperty property : objectPattern.getProperties()) {
                markPatternConstBindings(compilerContext, property.getValue());
            }
            if (objectPattern.getRestElement() != null) {
                markPatternConstBindings(compilerContext, objectPattern.getRestElement().getArgument());
            }
            return;
        }
        if (pattern instanceof AssignmentPattern assignmentPattern) {
            markPatternConstBindings(compilerContext, assignmentPattern.getLeft());
            return;
        }
        if (pattern instanceof RestElement restElement) {
            markPatternConstBindings(compilerContext, restElement.getArgument());
        }
    }
}
