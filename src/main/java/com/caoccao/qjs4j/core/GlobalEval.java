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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.compilation.ast.*;
import com.caoccao.qjs4j.compilation.compiler.Compiler;
import com.caoccao.qjs4j.core.error.JSStackFrame;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.vm.StackFrame;

import java.util.*;

/** The global eval function, including direct-eval caller scope and cross-realm execution. */
final class GlobalEval {
    private GlobalEval() {
    }

    /**
     * Analyze caller function var-environment names in one parse. bodyVarNames excludes parameter names.
     * functionVarEnvironmentNames includes parameter names and body var declarations.
     */
    private static CallerVarEnvironmentAnalysis analyzeCallerVarEnvironment(JSBytecodeFunction callerBytecodeFunction) {
        String source = callerBytecodeFunction.getSourceCode();
        if (source == null || source.isBlank()) {
            return null;
        }
        Expression functionLikeExpression = extractFunctionLikeExpression(source);
        if (!(functionLikeExpression instanceof FunctionNode function)) {
            return null;
        }
        Set<String> bodyVars = new HashSet<>();
        Set<String> constants = new HashSet<>();
        Set<String> names = function.getParameterNames();
        if (function.getRestParameter() != null) {
            names.addAll(function.getRestParameter().getArgument().getBoundNames());
        }
        ASTNode body = function instanceof FunctionExpression ordinary
                ? ordinary.getBody()
                : ((ArrowFunctionExpression) function).getBody();
        if (body instanceof BlockStatement block) {
            collectCallerBindings(block, bodyVars, constants);
        }
        names.addAll(bodyVars);
        return new CallerVarEnvironmentAnalysis(bodyVars, names, constants);
    }

    private static void collectCallerBindings(ASTNode node, Set<String> vars, Set<String> constants) {
        if (node instanceof FunctionDeclaration function && function.getId() != null) {
            vars.add(function.getId().getName());
        } else if (node instanceof VariableDeclaration declaration) {
            Set<String> target = switch (declaration.getKind()) {
                case VAR -> vars;
                case CONST -> constants;
                default -> null;
            };
            if (target != null) {
                for (VariableDeclarator binding : declaration.getDeclarations()) {
                    target.addAll(binding.getId().getBoundNames());
                }
            }
        } else if (node instanceof BlockStatement block) {
            collectCallerBindings(block.getBody(), vars, constants);
        } else if (node instanceof IfStatement branch) {
            collectCallerBindings(branch.getConsequent(), vars, constants);
            collectCallerBindings(branch.getAlternate(), vars, constants);
        } else if (node instanceof ForLoop loop) {
            collectCallerBindings(loop.getHead(), vars, constants);
            collectCallerBindings(loop.getBody(), vars, constants);
        } else if (node instanceof WhileLoop loop) {
            collectCallerBindings(loop.getBody(), vars, constants);
        } else if (node instanceof SwitchStatement selection) {
            for (SwitchStatement.SwitchCase clause : selection.getCases()) {
                collectCallerBindings(clause.getConsequent(), vars, constants);
            }
        } else if (node instanceof TryStatement attempt) {
            collectCallerBindings(attempt.getBlock(), vars, constants);
            if (attempt.getHandler() != null) {
                if (attempt.getHandler().getParam() != null) {
                    vars.addAll(attempt.getHandler().getParam().getBoundNames());
                }
                collectCallerBindings(attempt.getHandler().getBody(), vars, constants);
            }
            collectCallerBindings(attempt.getFinalizer(), vars, constants);
        } else if (node instanceof LabeledStatement labeled) {
            collectCallerBindings(labeled.getBody(), vars, constants);
        }
    }

    private static void collectCallerBindings(List<Statement> statements, Set<String> vars, Set<String> constants) {
        for (Statement statement : statements) {
            collectCallerBindings(statement, vars, constants);
        }
    }

    static JSValue evaluate(JSContext realmContext, JSContext callerContext, JSValue[] args,
            boolean forceDirectEvalCall) {
        JSValue x = JSValue.argumentOrUndefined(args, 0);

        // If x is not a string, return it unchanged
        if (!(x instanceof JSString)) {
            return x;
        }

        String code = ((JSString) x).value();
        boolean isDirectEvalCall = forceDirectEvalCall;
        boolean isSameRealm = (realmContext == callerContext);
        boolean shouldUseCallerFrameSemantics = isDirectEvalCall && isSameRealm;

        // Scope overlay: capture enclosing function's local variables onto the global
        // object so that eval code's GET_VAR/PUT_VAR can access them.
        StackFrame callerFrame = callerContext.getVirtualMachine().getCurrentFrame();
        boolean hasSameRealmCallerFrame = isSameRealm && callerFrame != null;
        // Eval is "inside a function" only if same-realm and the callerFrame is NOT the top-level program.
        boolean isEvalInFunction = shouldUseCallerFrameSemantics && hasSameRealmCallerFrame
                && callerFrame.getFunction() instanceof JSBytecodeFunction && callerFrame.getCaller() != null;
        boolean shouldOverlayLocals = isEvalInFunction;
        boolean shouldOverlayTopLevelLexicals = shouldUseCallerFrameSemantics && hasSameRealmCallerFrame
                && !isEvalInFunction && callerFrame.getFunction() instanceof JSBytecodeFunction;
        boolean inheritedStrictMode = shouldUseCallerFrameSemantics && callerFrame != null
                && callerFrame.getFunction() instanceof JSBytecodeFunction bytecodeFunction
                && bytecodeFunction.isStrict();
        EvalCallerBindings bindings = null;
        Set<String> evalVarDeclarations = null;
        Set<String> evalLexDeclarations = null;
        Set<String> evalFunctionDeclarations = null;
        Set<String> functionVarEnvironmentNames = null;
        Set<String> constLexicalNames = null;
        CallerVarEnvironmentAnalysis callerVarEnvironmentAnalysis = null;
        boolean parsedEvalDeclarations = false;
        boolean evalCodeStrict = inheritedStrictMode;
        boolean overlayStatePushed = false;
        JSObject global = realmContext.getGlobalObject();
        JSBytecodeFunction callerBytecodeFunction = callerFrame != null
                && callerFrame.getFunction() instanceof JSBytecodeFunction bytecodeFunction ? bytecodeFunction : null;
        // When eval runs inside a function, snapshot global property names so we can
        // clean up var/function bindings that should be function-scoped (not global).
        Set<String> globalKeysBefore = null;
        if (hasSameRealmCallerFrame) {
            globalKeysBefore = new HashSet<>();
            for (PropertyKey propertyKey : global.ownPropertyKeys()) {
                if (propertyKey.isString()) {
                    globalKeysBefore.add(propertyKey.asString());
                }
            }
        }
        if (callerBytecodeFunction != null && shouldOverlayLocals) {
            callerVarEnvironmentAnalysis = analyzeCallerVarEnvironment(callerBytecodeFunction);
            if (callerVarEnvironmentAnalysis != null) {
                functionVarEnvironmentNames = callerVarEnvironmentAnalysis.functionVarEnvironmentNames();
                constLexicalNames = callerVarEnvironmentAnalysis.constLexicalNames();
            }
        }

        if ((shouldOverlayLocals || shouldOverlayTopLevelLexicals) && callerBytecodeFunction != null
                && callerBytecodeFunction.getBytecode().getLocalVarNames() != null) {
            bindings = new EvalCallerBindings(global, callerFrame, callerBytecodeFunction);
            bindings.bindLocals(callerContext, code, shouldOverlayTopLevelLexicals, constLexicalNames);
            if (shouldOverlayLocals) {
                bindings.bindEnclosingScopes();
            }
        }

        // This eval's lexical environment, nested in the caller's when the caller is itself eval code. The
        // compiled eval code and every function created inside it carry it; only they may consult the overlay.
        JSEvalEnvironment evalEnvironment = new JSEvalEnvironment(
                callerFrame != null && callerFrame.getFunction() != null
                        ? callerFrame.getFunction().getEvalEnvironment()
                        : null);
        realmContext.setPendingDirectEvalEnvironment(evalEnvironment);
        if (bindings != null) {
            bindings.push(evalEnvironment);
            overlayStatePushed = true;
        }

        try {
            if (hasSameRealmCallerFrame) {
                try {
                    Compiler evalCompiler = new Compiler(code, "<eval>").setContext(realmContext);
                    Program evalAst = evalCompiler.parse(false);
                    Program.GlobalDeclarations globalDeclarations = evalAst.getGlobalDeclarations();
                    evalVarDeclarations = globalDeclarations.varDeclarations();
                    evalLexDeclarations = globalDeclarations.lexicalDeclarations();
                    evalFunctionDeclarations = globalDeclarations.functionDeclarations();
                    parsedEvalDeclarations = true;
                    evalCodeStrict = evalCodeStrict || evalAst.isStrict();
                } catch (Exception ignored) {
                    // Parse error in eval code - let the normal eval path report it.
                }
            }

            // EvalDeclarationInstantiation: check if eval code declares "var arguments"
            // in a function with parameter expressions. Per QuickJS add_arguments_arg(),
            // a lexical "arguments" binding exists in the argument scope when the function
            // has parameter expressions (non-strict mode), preventing eval from redeclaring it.
            if (isEvalInFunction && callerBytecodeFunction != null && callerBytecodeFunction.hasParameterExpressions()
                    && !callerBytecodeFunction.isStrict() && !evalCodeStrict && parsedEvalDeclarations
                    && evalVarDeclarations != null && evalVarDeclarations.contains("arguments")) {
                if (!callerBytecodeFunction.isArrow() || callerBytecodeFunction.hasArgumentsParameterBinding()) {
                    throw new JSException(
                            realmContext.throwError("SyntaxError", "Identifier 'arguments' has already been declared"));
                }
            }
            // EvalDeclarationInstantiation: in functions with parameter expressions,
            // eval cannot create a var with the same name as a parameter.
            // Per spec, the parameter scope is separate from the var scope,
            // so "var x" in eval conflicts with parameter "x".
            if (isEvalInFunction && callerBytecodeFunction != null && callerBytecodeFunction.hasParameterExpressions()
                    && !callerBytecodeFunction.isStrict() && !evalCodeStrict && parsedEvalDeclarations
                    && evalVarDeclarations != null && bindings != null) {
                Set<String> bodyVarNames = callerVarEnvironmentAnalysis != null
                        ? callerVarEnvironmentAnalysis.bodyVarNames()
                        : null;
                if (bodyVarNames != null) {
                    for (String evalVar : evalVarDeclarations) {
                        if (JSKeyword.ARGUMENTS.equals(evalVar)) {
                            continue;
                        }
                        if (bindings.hasLocal(evalVar) && !bodyVarNames.contains(evalVar)) {
                            throw new JSException(realmContext.throwError("SyntaxError",
                                    "Identifier '" + evalVar + "' has already been declared"));
                        }
                    }
                }
            }
            // EvalDeclarationInstantiation step 5 is conditioned on the strictness of the
            // *eval code*, not of the caller. A direct eval in a strict function is strict by
            // inheritance and is already excluded by !evalCodeStrict; an indirect eval is
            // never strict by inheritance, so testing the caller's strictness here silently
            // dropped the global-lexical collision check for `(0,eval)('var x')` written
            // inside a strict script.
            if (hasSameRealmCallerFrame && !evalCodeStrict && parsedEvalDeclarations && evalVarDeclarations != null) {
                if (callerBytecodeFunction != null && callerFrame != null && callerFrame.getCaller() != null) {
                    if (callerVarEnvironmentAnalysis == null) {
                        callerVarEnvironmentAnalysis = analyzeCallerVarEnvironment(callerBytecodeFunction);
                    }
                    functionVarEnvironmentNames = callerVarEnvironmentAnalysis != null
                            ? callerVarEnvironmentAnalysis.functionVarEnvironmentNames()
                            : null;
                }
                for (String declarationName : evalVarDeclarations) {
                    if (isEvalInFunction && JSKeyword.ARGUMENTS.equals(declarationName)
                            && callerBytecodeFunction != null) {
                        continue;
                    }
                    if (callerBytecodeFunction != null && callerBytecodeFunction.isArrow()
                            && JSKeyword.ARGUMENTS.equals(declarationName)
                            && !callerBytecodeFunction.hasArgumentsParameterBinding()) {
                        continue;
                    }
                    // An indirect eval's variable environment is the global environment
                    // wherever the call appears, so the global-lexical collision check applies
                    // even when the calling frame is a function. Only a direct eval has to be
                    // at top level for that to be true.
                    if (callerFrame != null && (!isDirectEvalCall || callerFrame.getCaller() == null)
                            && callerContext.hasGlobalLexDeclaration(declarationName)) {
                        throw new JSException(realmContext.throwError("SyntaxError",
                                "Identifier '" + declarationName + "' has already been declared"));
                    }
                    if (bindings != null && bindings.hasLocal(declarationName) && functionVarEnvironmentNames != null
                            && !functionVarEnvironmentNames.contains(declarationName)) {
                        throw new JSException(realmContext.throwError("SyntaxError",
                                "Identifier '" + declarationName + "' has already been declared"));
                    }
                }
            }

            if (isEvalInFunction && parsedEvalDeclarations && evalVarDeclarations != null && bindings != null) {
                bindings.declareVariables(evalVarDeclarations, functionVarEnvironmentNames, evalCodeStrict);
            }

            if (!isDirectEvalCall && !evalCodeStrict && parsedEvalDeclarations) {
                if (evalFunctionDeclarations != null) {
                    Map<String, PropertyDescriptor> existingFunctionDescriptors = new HashMap<>();
                    for (String functionDeclarationName : evalFunctionDeclarations) {
                        existingFunctionDescriptors.put(functionDeclarationName,
                                GlobalFunctionBindings.checkFunction(global, functionDeclarationName));
                    }
                    for (String functionDeclarationName : evalFunctionDeclarations) {
                        GlobalFunctionBindings.prepareEvalFunction(global, functionDeclarationName,
                                existingFunctionDescriptors.get(functionDeclarationName));
                    }
                }
                if (evalVarDeclarations != null) {
                    GlobalFunctionBindings.checkEvalVars(global, evalVarDeclarations, evalFunctionDeclarations);
                }
            }

            JSGlobalObject.EvalOverlaySnapshot suspendedOverlaySnapshot = null;
            if (!isDirectEvalCall) {
                suspendedOverlaySnapshot = realmContext.suspendEvalOverlays();
                realmContext.pushEvalOverlayLookupSuppression();
            }
            String evalFilename = "<eval>";
            JSStackFrame callerStackFrame = callerContext.getCurrentStackFrame();
            if (callerStackFrame != null && callerStackFrame.filename() != null
                    && !callerStackFrame.filename().isEmpty()) {
                evalFilename = callerStackFrame.filename();
            }
            JSValue result = isDirectEvalCall
                    ? realmContext.evalDirectInternal(code, evalFilename, inheritedStrictMode)
                    : realmContext.evalIndirectInternal(code, evalFilename);
            if (!isDirectEvalCall) {
                realmContext.popEvalOverlayLookupSuppression();
            }
            if (suspendedOverlaySnapshot != null) {
                realmContext.resumeEvalOverlays(suspendedOverlaySnapshot);
            }
            if (result == null) {
                JSValue error = realmContext.getPendingException();
                if (error != null) {
                    realmContext.clearPendingException();
                    callerContext.setPendingException(error);
                }
                return JSUndefined.INSTANCE;
            }

            if (bindings != null) {
                bindings.writeBack(evalCodeStrict ? evalVarDeclarations : null);
            }
            if (isEvalInFunction && !evalCodeStrict && parsedEvalDeclarations && evalVarDeclarations != null
                    && callerFrame != null) {
                for (String declarationName : evalVarDeclarations) {
                    if (bindings != null && bindings.hasLocal(declarationName)) {
                        continue;
                    }
                    PropertyKey key = PropertyKey.fromString(declarationName);
                    if (global.has(key)) {
                        callerFrame.setDynamicVarBinding(declarationName, global.get(key));
                    }
                }
            }

            if (!isDirectEvalCall && !evalCodeStrict && evalFunctionDeclarations != null) {
                GlobalFunctionBindings.finish(global, evalFunctionDeclarations, true);
            }

            return result;
        } catch (JSException e) {
            // Propagate exception to the caller's context so the caller's VM sees it
            callerContext.setPendingException(e.getErrorValue());
            return JSUndefined.INSTANCE;
        } finally {
            if (bindings != null) {
                boolean keepGlobalVars = callerFrame != null && callerFrame.getCaller() == null && !evalCodeStrict
                        && parsedEvalDeclarations;
                bindings.overlay.restore(keepGlobalVars ? evalVarDeclarations : null, callerContext);
            }
            // Clean up eval-created bindings that should be function-scoped (not global).
            // Per ES2024 B.3.3.3, var/function declarations in eval inside a function
            // go to the function's variable environment, not the global.
            if (hasSameRealmCallerFrame && globalKeysBefore != null && parsedEvalDeclarations
                    && evalVarDeclarations != null && (isEvalInFunction || evalCodeStrict)) {
                for (String declarationName : evalVarDeclarations) {
                    if (globalKeysBefore.contains(declarationName)) {
                        continue;
                    }
                    if (bindings != null && bindings.hasLocal(declarationName)) {
                        continue;
                    }
                    global.delete(PropertyKey.fromString(declarationName));
                }
            }
            if (globalKeysBefore != null && parsedEvalDeclarations && evalLexDeclarations != null) {
                for (String declarationName : evalLexDeclarations) {
                    if (globalKeysBefore.contains(declarationName)) {
                        continue;
                    }
                    global.delete(PropertyKey.fromString(declarationName));
                }
            }
            if (overlayStatePushed) {
                realmContext.popEvalOverlay();
            }
            // For functions with parameter expressions, eval-created vars need to
            // persist on the global object so closures created in parameter defaults
            // can see them. Per spec, these vars go into the parameter scope's var
            // environment, which closures in defaults close over.
            if (isEvalInFunction && callerBytecodeFunction != null && callerBytecodeFunction.hasParameterExpressions()
                    && !evalCodeStrict && parsedEvalDeclarations && evalVarDeclarations != null
                    && callerFrame != null) {
                for (String declarationName : evalVarDeclarations) {
                    if (bindings != null && bindings.hasLocal(declarationName)) {
                        continue;
                    }
                    JSValue dynValue = callerFrame.getDynamicVarBinding(declarationName);
                    if (dynValue != null) {
                        global.set(PropertyKey.fromString(declarationName), dynValue);
                    }
                }
            }
        }
    }

    private static Expression extractFunctionLikeExpression(String source) {
        Expression directExpression = parseTopLevelExpression("(" + source + ")");
        if (directExpression instanceof FunctionExpression || directExpression instanceof ArrowFunctionExpression) {
            return directExpression;
        }

        Expression objectLiteralExpression = parseTopLevelExpression("({" + source + "})");
        if (!(objectLiteralExpression instanceof ObjectExpression objectExpression)
                || objectExpression.getProperties().isEmpty()) {
            return null;
        }
        ObjectExpressionProperty firstProperty = objectExpression.getProperties().get(0);
        if (firstProperty.getValue() instanceof FunctionExpression functionExpression) {
            return functionExpression;
        }
        return null;
    }

    private static Expression parseTopLevelExpression(String wrappedSource) {
        try {
            Program parsedProgram = new Compiler(wrappedSource, "<eval-caller>").parse(false);
            if (parsedProgram.getBody().isEmpty()
                    || !(parsedProgram.getBody().get(0) instanceof ExpressionStatement expressionStatement)) {
                return null;
            }
            return expressionStatement.getExpression();
        } catch (Exception ignored) {
            return null;
        }
    }

    private record CallerVarEnvironmentAnalysis(Set<String> bodyVarNames, Set<String> functionVarEnvironmentNames,
            Set<String> constLexicalNames) {
    }

}
