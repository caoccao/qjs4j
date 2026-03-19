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

final class ProgramCompiler extends AstNodeCompiler<Program> {
    ProgramCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(Program program) {
        if (program.isModule()) {
            compileModule(program);
        } else {
            compileScript(program);
        }
    }

    private void compileModule(Program program) {
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
                    compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, localIndex);
                }
                // ES2024: Class declarations in modules create let-like bindings (mutable),
                // not const-like. Do NOT call markConstLocal here.
                compilerContext.tdzLocals.add(className);
                continue;
            }
            if (statement instanceof VariableDeclaration variableDeclaration) {
                if (variableDeclaration.getKind() == VariableKind.VAR) {
                    for (VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                        compilerContext.compilerAnalysis.collectPatternBindingNames(declarator.getId(), hoistedVarNames);
                    }
                } else {
                    Set<String> lexicalNames = new HashSet<>();
                    for (VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                        compilerContext.compilerAnalysis.collectPatternBindingNames(declarator.getId(), lexicalNames);
                    }
                    for (String lexicalName : lexicalNames) {
                        if (compilerContext.scopeManager.currentScope().getLocal(lexicalName) == null) {
                            int localIndex = compilerContext.scopeManager.currentScope().declareLocal(lexicalName);
                            compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, localIndex);
                        }
                        if (variableDeclaration.getKind() == VariableKind.CONST
                                || variableDeclaration.getKind() == VariableKind.USING
                                || variableDeclaration.getKind() == VariableKind.AWAIT_USING) {
                            compilerContext.scopeManager.currentScope().markConstLocal(lexicalName);
                        }
                        compilerContext.tdzLocals.add(lexicalName);
                    }
                }
                continue;
            }
            if (statement instanceof FunctionDeclaration functionDeclaration && functionDeclaration.getId() != null) {
                hoistedFunctionNames.add(functionDeclaration.getId().getName());
                compilerContext.functionDeclarationCompiler.compile(functionDeclaration);
                continue;
            }
            compilerContext.compilerAnalysis.collectVarNamesFromStatement(statement, hoistedVarNames);
        }

        for (String variableName : hoistedVarNames) {
            if (hoistedFunctionNames.contains(variableName)) {
                continue;
            }
            if (compilerContext.scopeManager.currentScope().getLocal(variableName) == null) {
                int localIndex = compilerContext.scopeManager.currentScope().declareLocal(variableName);
                compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
            }
        }

        int effectiveLastIndex = -1;
        for (int statementIndex = body.size() - 1; statementIndex >= 0; statementIndex--) {
            if (!(body.get(statementIndex) instanceof FunctionDeclaration)) {
                effectiveLastIndex = statementIndex;
                break;
            }
        }
        boolean lastProducesValue = false;

        for (int statementIndex = 0; statementIndex < body.size(); statementIndex++) {
            Statement statement = body.get(statementIndex);
            if (statement instanceof FunctionDeclaration) {
                continue;
            }

            boolean isLast = statementIndex == effectiveLastIndex;
            if (isLast && (statement instanceof ExpressionStatement || statement instanceof TryStatement)) {
                lastProducesValue = true;
            }
            compilerContext.isLastInProgram = isLast;
            compilerContext.statementCompiler.compile(statement);
        }

        if (!lastProducesValue) {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        }

        int programResultLocalIndex = compilerContext.scopeManager.currentScope().declareLocal(
                "$program_result_" + compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, programResultLocalIndex);
        compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, programResultLocalIndex);
        compilerContext.emitter.emitOpcode(Opcode.RETURN);

        int localCount = compilerContext.scopeManager.currentScope().getLocalCount();
        compilerContext.scopeManager.updateMaxLocalCount(localCount);
        compilerContext.inGlobalScope = false;
    }

    private void compileScript(Program program) {
        compilerContext.strictMode = program.isStrict();  // Set strict mode from program directive
        boolean useLocalProgramScope =
                compilerContext.predeclareProgramLexicalsAsLocals && compilerContext.strictMode;
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
                compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, localIndex);
                compilerContext.tdzLocals.add(className);
                compilerContext.nonDeletableGlobalBindings.add(className);
            } else if (stmt instanceof VariableDeclaration variableDeclaration) {
                if (compilerContext.predeclareProgramLexicalsAsLocals
                        && variableDeclaration.getKind() != VariableKind.VAR) {
                    Set<String> lexicalNames = new HashSet<>();
                    for (VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                        compilerContext.compilerAnalysis.collectPatternBindingNames(declarator.getId(), lexicalNames);
                    }
                    for (String lexicalName : lexicalNames) {
                        int localIndex = compilerContext.scopeManager.currentScope().declareLocal(lexicalName);
                        if (variableDeclaration.getKind() == VariableKind.CONST
                                || variableDeclaration.getKind() == VariableKind.USING
                                || variableDeclaration.getKind() == VariableKind.AWAIT_USING) {
                            compilerContext.scopeManager.currentScope().markConstLocal(lexicalName);
                        }
                        compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, localIndex);
                        compilerContext.tdzLocals.add(lexicalName);
                    }
                }
                // Script/global var bindings are non-deletable. Direct eval var/function
                // bindings are handled dynamically in JSGlobalObject.eval().
                if (!compilerContext.evalMode) {
                    for (VariableDeclarator declarator : variableDeclaration.getDeclarations()) {
                        compilerContext.compilerAnalysis.collectPatternBindingNames(
                                declarator.getId(),
                                compilerContext.nonDeletableGlobalBindings);
                    }
                }
                // Collect var names for hoisting
                if (variableDeclaration.getKind() == VariableKind.VAR) {
                    compilerContext.compilerAnalysis.collectVarNamesFromStatement(stmt, varNames);
                }
            } else if (stmt instanceof FunctionDeclaration funcDecl) {
                if (funcDecl.getId() != null) {
                    hoistedFunctionNames.add(funcDecl.getId().getName());
                    if (!compilerContext.evalMode) {
                        compilerContext.nonDeletableGlobalBindings.add(funcDecl.getId().getName());
                    }
                }
                if (!deferFunctionHoistingUntilAfterVarLocals) {
                    compilerContext.functionDeclarationCompiler.compile(funcDecl);
                }
            } else {
                // Collect var names from other statements (for, try, if, etc.)
                compilerContext.compilerAnalysis.collectVarNamesFromStatement(stmt, varNames);
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
                    compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
                } else {
                    compilerContext.emitHelpers.emitConditionalVarInit(varName);
                }
            }
        }

        if (deferFunctionHoistingUntilAfterVarLocals) {
            for (Statement statement : body) {
                if (statement instanceof FunctionDeclaration functionDeclaration) {
                    compilerContext.functionDeclarationCompiler.compile(functionDeclaration);
                }
            }
        }

        Set<String> declaredFuncVarNames = new HashSet<>();
        declaredFuncVarNames.addAll(hoistedFunctionNames);
        declaredFuncVarNames.addAll(varNames);

        // Phase 1.5: Annex B.3.3.3 - create var bindings for function declarations
        // nested inside blocks, if-statements, catch clauses, etc.
        compilerContext.compilerAnalysis.scanAnnexBFunctions(body, declaredFuncVarNames);

        if (compilerContext.evalMode) {
            // Eval mode: allocate a hidden local to track the eval completion value.
            // Every expression statement stores its result here (like QuickJS's eval_ret_idx).
            int evalRetLocalIndex = compilerContext.scopeManager.currentScope().declareLocal("$eval_ret");
            compilerContext.evalReturnLocalIndex = evalRetLocalIndex;
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, evalRetLocalIndex);

            // Phase 2: Compile all non-FunctionDeclaration statements in source order.
            for (int i = 0; i < body.size(); i++) {
                Statement stmt = body.get(i);
                if (stmt instanceof FunctionDeclaration) {
                    continue; // Already hoisted in Phase 1
                }
                compilerContext.statementCompiler.compile(stmt);
            }

            compilerContext.evalReturnLocalIndex = -1;

            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, evalRetLocalIndex);
            int programResultLocalIndex = compilerContext.scopeManager.currentScope().declareLocal("$program_result_" + compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, programResultLocalIndex);
            compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, programResultLocalIndex);
        } else {
            // Script mode: preserve value of the last expression/try statement.
            int effectiveLastIndex = -1;
            for (int i = body.size() - 1; i >= 0; i--) {
                if (!(body.get(i) instanceof FunctionDeclaration)) {
                    effectiveLastIndex = i;
                    break;
                }
            }
            boolean lastProducesValue = false;

            for (int i = 0; i < body.size(); i++) {
                Statement stmt = body.get(i);
                if (stmt instanceof FunctionDeclaration) {
                    continue; // Already hoisted in Phase 1
                }

                boolean isLast = (i == effectiveLastIndex);

                if (isLast && stmt instanceof ExpressionStatement) {
                    lastProducesValue = true;
                } else if (isLast && stmt instanceof TryStatement) {
                    lastProducesValue = true;
                }

                compilerContext.isLastInProgram = isLast;
                compilerContext.statementCompiler.compile(stmt);
            }

            // If last statement didn't produce a value, push undefined
            if (!lastProducesValue) {
                compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            }

            int programResultLocalIndex = compilerContext.scopeManager.currentScope().declareLocal("$program_result_" + compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, programResultLocalIndex);
            compilerContext.emitHelpers.emitCurrentScopeUsingDisposal();
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, programResultLocalIndex);
        }

        // Return the value on top of stack
        compilerContext.emitter.emitOpcode(Opcode.RETURN);

        int localCount = compilerContext.scopeManager.currentScope().getLocalCount();
        compilerContext.scopeManager.updateMaxLocalCount(localCount);
        compilerContext.inGlobalScope = false;
    }
}
