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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ProgramCompiler {
    private ProgramCompiler() {
    }

    static void compile(CompilerContext compilerContext, Program program) {
        if (program.isModule()) {
            compileModule(compilerContext, program);
        } else {
            compileScript(compilerContext, program);
        }
    }

    private static void compileModule(CompilerContext compilerContext, Program program) {
        BytecodeEmitter emitter = compilerContext.emitter;
        compilerContext.inGlobalScope = false;
        compilerContext.isGlobalProgram = false;
        compilerContext.strictMode = true;
        compilerContext.scopeManager.enterScope();

        List<Statement> body = program.getBody();

        Set<String> hoistedFunctionNames = new HashSet<>();
        Set<String> hoistedVarNames = new HashSet<>();
        for (Statement statement : body) {
            if (statement instanceof ClassDeclaration classDeclaration && classDeclaration.getId() != null) {
                String className = classDeclaration.getId().getName();
                if (compilerContext.scopeManager.currentScope().getLocal(className) == null) {
                    int localIndex = compilerContext.scopeManager.currentScope().declareLocal(className);
                    emitter.emitIntOperand(Opcode.SET_LOC_UNINITIALIZED, localIndex);
                }
                // ES2024: Class declarations in modules create let-like bindings (mutable),
                // not const-like. Do NOT call markConstLocal here.
                compilerContext.tdzLocals.add(className);
                continue;
            }
            if (statement instanceof VariableDeclaration variableDeclaration) {
                if (variableDeclaration.getKind() == VariableKind.VAR) {
                    for (VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                        CompilerAnalysis.collectPatternBindingNames(declarator.getId(), hoistedVarNames);
                    }
                } else {
                    declareLexicals(compilerContext, variableDeclaration, true);
                }
                continue;
            }
            FunctionDeclaration functionDeclaration = statement.unwrapLabeledFunctionDeclaration();
            if (functionDeclaration != null && functionDeclaration.getId() != null) {
                hoistedFunctionNames.add(functionDeclaration.getId().getName());
                FunctionDeclarationCompiler.compile(compilerContext, functionDeclaration);
                continue;
            }
            CompilerAnalysis.collectVarNamesFromStatement(statement, hoistedVarNames);
        }

        for (String variableName : hoistedVarNames) {
            if (hoistedFunctionNames.contains(variableName)) {
                continue;
            }
            if (compilerContext.scopeManager.currentScope().getLocal(variableName) == null) {
                int localIndex = compilerContext.scopeManager.currentScope().declareLocal(variableName);
                emitter.emitUndefinedLocal(localIndex);
            }
        }

        int effectiveLastIndex = -1;
        for (int statementIndex = body.size() - 1; statementIndex >= 0; statementIndex--) {
            if (body.get(statementIndex).unwrapLabeledFunctionDeclaration() == null) {
                effectiveLastIndex = statementIndex;
                break;
            }
        }
        boolean lastProducesValue = false;

        for (int statementIndex = 0; statementIndex < body.size(); statementIndex++) {
            Statement statement = body.get(statementIndex);
            if (statement.unwrapLabeledFunctionDeclaration() != null) {
                continue;
            }

            boolean isLast = statementIndex == effectiveLastIndex;
            if (isLast && (statement instanceof ExpressionStatement || statement instanceof TryStatement)) {
                lastProducesValue = true;
            }
            compilerContext.isLastInProgram = isLast;
            compilerContext.compileStatement(statement);
        }

        if (!lastProducesValue) {
            emitter.emitOpcode(Opcode.UNDEFINED);
        }

        finishProgram(compilerContext);
    }

    private static void compileScript(CompilerContext compilerContext, Program program) {
        BytecodeEmitter emitter = compilerContext.emitter;
        compilerContext.strictMode = program.isStrict(); // Set strict mode from program directive
        boolean useLocalProgramScope = compilerContext.predeclareProgramLexicalsAsLocals && compilerContext.strictMode;
        compilerContext.inGlobalScope = !useLocalProgramScope;
        compilerContext.isGlobalProgram = !useLocalProgramScope;
        compilerContext.scopeManager.enterScope();

        List<Statement> body = program.getBody();
        boolean deferFunctionHoistingUntilAfterVarLocals = useLocalProgramScope;

        // Single pass: pre-declare TDZ locals, register global bindings, and hoist functions
        Set<String> hoistedFunctionNames = new HashSet<>();
        Set<String> varNames = new HashSet<>();
        for (Statement stmt : body) {
            // Pre-declare class declarations as locals with TDZ (ES spec EvalDeclarationInstantiation step 14)
            if (stmt instanceof ClassDeclaration classDecl && classDecl.getId() != null) {
                String className = classDecl.getId().getName();
                int localIndex = compilerContext.scopeManager.currentScope().declareLocal(className);
                emitter.emitIntOperand(Opcode.SET_LOC_UNINITIALIZED, localIndex);
                compilerContext.tdzLocals.add(className);
                compilerContext.nonDeletableGlobalBindings.add(className);
            } else if (stmt instanceof VariableDeclaration variableDeclaration) {
                if (compilerContext.predeclareProgramLexicalsAsLocals
                        && variableDeclaration.getKind() != VariableKind.VAR) {
                    declareLexicals(compilerContext, variableDeclaration, false);
                }
                // Script/global var bindings are non-deletable. Direct eval var/function
                // bindings are handled dynamically in JSGlobalObject.eval().
                if (!compilerContext.evalMode) {
                    for (VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                        CompilerAnalysis.collectPatternBindingNames(declarator.getId(),
                                compilerContext.nonDeletableGlobalBindings);
                    }
                }
                // Collect var names for hoisting
                if (variableDeclaration.getKind() == VariableKind.VAR) {
                    CompilerAnalysis.collectVarNamesFromStatement(stmt, varNames);
                }
            } else {
                FunctionDeclaration funcDecl = stmt.unwrapLabeledFunctionDeclaration();
                if (funcDecl != null) {
                    if (funcDecl.getId() != null) {
                        hoistedFunctionNames.add(funcDecl.getId().getName());
                        if (!compilerContext.evalMode) {
                            compilerContext.nonDeletableGlobalBindings.add(funcDecl.getId().getName());
                        }
                    }
                    if (!deferFunctionHoistingUntilAfterVarLocals) {
                        FunctionDeclarationCompiler.compile(compilerContext, funcDecl);
                    }
                } else {
                    // Collect var names from other statements (for, try, if, etc.)
                    CompilerAnalysis.collectVarNamesFromStatement(stmt, varNames);
                }
            }
        }

        // Phase 1.25: Var hoisting — create undefined bindings for var names not
        // already covered by hoisted function declarations.
        // Per ES2024 CreateGlobalVarBinding, only create binding if it doesn't already exist.
        for (String varName : varNames) {
            if (!hoistedFunctionNames.contains(varName)) {
                if (useLocalProgramScope) {
                    Integer localIndex = compilerContext.scopeManager.currentScope().getLocal(varName);
                    if (localIndex == null) {
                        localIndex = compilerContext.scopeManager.currentScope().declareLocal(varName);
                    }
                    emitter.emitUndefinedLocal(localIndex);
                } else {
                    EmitHelpers.emitConditionalVarInit(compilerContext, varName);
                }
            }
        }

        if (deferFunctionHoistingUntilAfterVarLocals) {
            for (Statement statement : body) {
                FunctionDeclaration deferredFuncDecl = statement.unwrapLabeledFunctionDeclaration();
                if (deferredFuncDecl != null) {
                    FunctionDeclarationCompiler.compile(compilerContext, deferredFuncDecl);
                }
            }
        }

        Set<String> declaredFuncVarNames = new HashSet<>();
        declaredFuncVarNames.addAll(hoistedFunctionNames);
        declaredFuncVarNames.addAll(varNames);

        // Phase 1.5: Annex B.3.3.3 - create var bindings for function declarations
        // nested inside blocks, if-statements, catch clauses, etc.
        CompilerAnalysis.scanAnnexBFunctions(compilerContext, body, declaredFuncVarNames);

        {
            // Scripts and eval share ECMAScript statement completion semantics.
            if (!body.isEmpty()) {
                emitter.emitSourcePosition(body.get(0).getLocation());
            }
            // Every expression statement stores its result here (like QuickJS's eval_ret_idx).
            int evalRetLocalIndex = compilerContext.scopeManager.currentScope().declareLocal("#eval_ret");
            compilerContext.evalReturnLocalIndex = evalRetLocalIndex;
            emitter.emitUndefinedLocal(evalRetLocalIndex);

            // Phase 2: Compile all non-FunctionDeclaration statements in source order.
            for (int i = 0; i < body.size(); i++) {
                Statement stmt = body.get(i);
                if (stmt.unwrapLabeledFunctionDeclaration() != null) {
                    continue; // Already hoisted in Phase 1
                }
                compilerContext.compileStatement(stmt);
            }

            compilerContext.evalReturnLocalIndex = -1;

            emitter.emitGetLocal(evalRetLocalIndex);
        }
        finishProgram(compilerContext);
    }

    private static void declareLexicals(CompilerContext compilerContext, VariableDeclaration declaration,
            boolean onlyInitializeNew) {
        CompilerScope scope = compilerContext.scopeManager.currentScope();
        Set<String> names = new HashSet<>();
        for (VariableDeclarator declarator : declaration.getDeclarations()) {
            CompilerAnalysis.collectPatternBindingNames(declarator.getId(), names);
        }
        for (String name : names) {
            if (!onlyInitializeNew || scope.getLocal(name) == null) {
                compilerContext.emitter.emitIntOperand(Opcode.SET_LOC_UNINITIALIZED, scope.declareLocal(name));
            }
            if (declaration.getKind().isImmutable()) {
                scope.markConstLocal(name);
            }
            compilerContext.tdzLocals.add(name);
        }
    }

    private static void finishProgram(CompilerContext compilerContext) {
        BytecodeEmitter emitter = compilerContext.emitter;
        int programResultLocalIndex = compilerContext.storeTemporary("#program_result_");
        EmitHelpers.emitCurrentScopeUsingDisposal(compilerContext);
        emitter.emitGetLocal(programResultLocalIndex);
        emitter.emitOpcode(Opcode.RETURN);

        int localCount = compilerContext.scopeManager.currentScope().getLocalCount();
        compilerContext.scopeManager.updateMaxLocalCount(localCount);
        compilerContext.inGlobalScope = false;
    }
}
