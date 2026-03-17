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
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Bytecode;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.*;

/**
 * Handles compilation of function declarations, function expressions,
 * arrow functions, class declarations, class expressions, and related
 * constructs (methods, fields, static blocks, private members, etc.).
 */
final class FunctionClassCompiler {
    private final CompilerContext compilerContext;
    private final CompilerDelegates delegates;
    private final FunctionClassFieldCompiler fieldCompiler;

    FunctionClassCompiler(CompilerContext compilerContext, CompilerDelegates delegates) {
        this.compilerContext = compilerContext;
        this.delegates = delegates;
        fieldCompiler = new FunctionClassFieldCompiler(compilerContext, delegates);
    }

    private static String createAutoAccessorBackingName(int index, Set<String> existingNames) {
        String candidateName = "__auto_accessor_" + index;
        while (existingNames.contains(candidateName)) {
            index++;
            candidateName = "__auto_accessor_" + index;
        }
        return candidateName;
    }

    void compileArrowFunctionExpression(ArrowFunctionExpression arrowExpr) {
        // Create a new compiler for the function body
        // Arrow functions inherit strict mode from parent (QuickJS behavior)
        BytecodeCompiler functionCompiler = new BytecodeCompiler(compilerContext.strictMode, compilerContext.captureResolver, compilerContext.context);
        CompilerContext functionContext = functionCompiler.context();
        CompilerDelegates funcDelegates = functionCompiler.delegates();
        functionContext.sourceCode = compilerContext.sourceCode;
        functionContext.nonDeletableGlobalBindings.addAll(compilerContext.nonDeletableGlobalBindings);
        functionContext.privateSymbols = compilerContext.privateSymbols;
        inheritVisibleWithObjectBindings(functionContext);

        // Enter function scope and add parameters as locals
        functionContext.enterScope();
        functionContext.inGlobalScope = false;
        functionContext.isInAsyncFunction = arrowExpr.isAsync();  // Track if this is an async function
        functionContext.isInGeneratorFunction = false;  // Arrow functions cannot be generators
        functionContext.isInArrowFunction = true;  // Arrow functions don't have their own arguments
        // Arrow functions inherit class field eval context (new.target resolves to undefined,
        // eval('arguments') should throw SyntaxError)
        functionContext.classFieldEvalContext = compilerContext.classFieldEvalContext
                || compilerContext.inClassFieldInitializer;
        // Inherit class inner name so eval() inside nested arrows can resolve it.
        inheritClassInnerNameCapture(functionContext);
        // Arrow functions inherit arguments from enclosing non-arrow function.
        // If the parent is a regular function (not arrow, not global program), it has arguments binding.
        // If the parent is also an arrow, inherit whatever it has.
        // The global program is not a function and doesn't provide 'arguments'.
        if (compilerContext.isInArrowFunction) {
            functionContext.hasEnclosingArgumentsBinding = compilerContext.hasEnclosingArgumentsBinding;
        } else {
            functionContext.hasEnclosingArgumentsBinding = !compilerContext.isGlobalProgram;
        }

        // Check for "use strict" directive if body is a block statement
        if (arrowExpr.getBody() instanceof BlockStatement block
                && block.hasUseStrictDirective()) {
            functionContext.strictMode = true;
        }

        boolean hasNonSimpleParameters = arrowExpr.getFunctionParams().hasNonSimpleParameters();
        boolean hasArgumentsParameterBinding = false;
        for (Pattern parameter : arrowExpr.getParams()) {
            if (parameter.getBoundNames().contains(JSArguments.NAME)) {
                hasArgumentsParameterBinding = true;
                break;
            }
        }
        if (!hasArgumentsParameterBinding && arrowExpr.getRestParameter() != null) {
            hasArgumentsParameterBinding = arrowExpr.getRestParameter().getArgument().getBoundNames()
                    .contains(JSArguments.NAME);
        }
        boolean hasDirectEvalVarArgumentsInDefaults = arrowExpr.getDefaults() != null
                && arrowExpr.getDefaults().stream()
                .filter(Objects::nonNull)
                .anyMatch(Expression::containsDirectEvalVarArguments);
        boolean needsSyntheticEvalArgumentsBinding = hasDirectEvalVarArgumentsInDefaults
                && arrowExpr.getDefaults() != null
                && !functionContext.hasEnclosingArgumentsBinding
                && !hasArgumentsParameterBinding;

        List<Integer> parameterSlotIndexes = new ArrayList<>();
        List<int[]> destructuringParams = declareParameters(
                arrowExpr.getParams(),
                functionContext,
                funcDelegates,
                parameterSlotIndexes);

        // For top-level arrows with parameter expressions, keep a local slot for dynamically
        // declared `arguments` from direct eval in parameter initializers.
        if (needsSyntheticEvalArgumentsBinding
                && functionContext.findLocalInScopes(JSArguments.NAME) == null) {
            int argumentsLocalIndex = functionContext.currentScope().declareLocal(JSArguments.NAME);
            functionContext.tdzLocals.add(JSArguments.NAME);
            functionContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, argumentsLocalIndex);
        }

        // Emit default parameter initialization following QuickJS pattern:
        // GET_ARG idx, DUP, UNDEFINED, STRICT_EQ, IF_FALSE label, DROP, <default>, DUP, PUT_ARG idx, label:
        if (arrowExpr.getDefaults() != null) {
            delegates.emitHelpers.emitDefaultParameterInit(
                    functionCompiler,
                    arrowExpr.getFunctionParams(),
                    parameterSlotIndexes);
        }

        // Handle rest parameter if present
        // The REST opcode must be emitted early in the function to initialize the rest array
        if (arrowExpr.getRestParameter() != null) {
            // Calculate the index where rest arguments start
            int firstRestIndex = arrowExpr.getParams().size();

            // Emit REST opcode with the starting index
            functionContext.emitter.emitOpcode(Opcode.REST);
            functionContext.emitter.emitU16(firstRestIndex);

            emitRestParameterBinding(arrowExpr.getRestParameter(), functionContext, funcDelegates);
        }

        // Emit destructuring for pattern parameters after defaults and rest
        emitParameterDestructuring(arrowExpr.getParams(), destructuringParams, functionContext, funcDelegates);

        boolean enteredBodyScope = false;
        CompilerScope savedVarDeclarationScopeOverride = functionContext.varDeclarationScopeOverride;
        int localCount;
        String[] localVarNames;
        {
            // Compile function body
            // Arrow functions can have expression body or block statement body
            if (arrowExpr.getBody() instanceof BlockStatement block) {
                if (needsSyntheticEvalArgumentsBinding) {
                    functionContext.enterScope();
                    enteredBodyScope = true;
                    functionContext.varDeclarationScopeOverride = functionContext.currentScope();
                }

                try {
                    // Phase 0: Pre-declare var and top-level function names as locals so nested
                    // closures resolve captures to VarRef slots consistently with function bodies.
                    funcDelegates.analysis.hoistAllDeclarationsAsLocals(block.getBody());

                    // Phase 1: Hoist top-level function declarations before executing body statements.
                    for (Statement statement : block.getBody()) {
                        if (statement instanceof FunctionDeclaration functionDeclaration) {
                            funcDelegates.functions.compileFunctionDeclaration(functionDeclaration);
                        }
                    }

                    // Annex B.3.3.1: Hoist function declarations from blocks/if-statements
                    // into function var bindings when allowed.
                    Set<String> declarationParameterNames = arrowExpr.getParameterNames();
                    funcDelegates.analysis.hoistFunctionBodyAnnexBDeclarations(
                            block.getBody(), declarationParameterNames);

                    // Compile block body statements.
                    for (Statement stmt : block.getBody()) {
                        if (stmt instanceof FunctionDeclaration) {
                            continue;
                        }
                        funcDelegates.statements.compileStatement(stmt);
                    }

                    // If body doesn't end with return, add implicit return undefined
                    List<Statement> bodyStatements = block.getBody();
                    if (bodyStatements.isEmpty() || !(bodyStatements.get(bodyStatements.size() - 1) instanceof ReturnStatement)) {
                        functionContext.emitter.emitOpcode(Opcode.UNDEFINED);
                        int returnValueIndex = functionContext.currentScope().declareLocal("$arrow_return_" + functionContext.emitter.currentOffset());
                        functionContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, returnValueIndex);
                        funcDelegates.emitHelpers.emitCurrentScopeUsingDisposal();
                        functionContext.emitter.emitOpcodeU16(Opcode.GET_LOC, returnValueIndex);
                        // Emit RETURN_ASYNC for async functions, RETURN for sync functions
                        functionContext.emitter.emitOpcode(arrowExpr.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
                    }
                } finally {
                    if (enteredBodyScope) {
                        functionContext.varDeclarationScopeOverride = savedVarDeclarationScopeOverride;
                    }
                }
            } else if (arrowExpr.getBody() instanceof Expression expr) {
                // Expression body - implicitly returns the expression value
                funcDelegates.expressions.compileExpression(expr);
                int returnValueIndex = functionContext.currentScope().declareLocal("$arrow_return_" + functionContext.emitter.currentOffset());
                functionContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, returnValueIndex);
                funcDelegates.emitHelpers.emitCurrentScopeUsingDisposal();
                functionContext.emitter.emitOpcodeU16(Opcode.GET_LOC, returnValueIndex);
                // Emit RETURN_ASYNC for async functions, RETURN for sync functions
                functionContext.emitter.emitOpcode(arrowExpr.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
            }

            localCount = functionContext.currentScope().getLocalCount();
            localVarNames = functionContext.getLocalVarNames();
        }
        if (enteredBodyScope) {
            functionContext.exitScope();
        }
        functionContext.exitScope();

        // Build the function bytecode
        Bytecode functionBytecode = functionContext.emitter.build(localCount, localVarNames);

        // Arrow functions are always anonymous
        String functionName = "";

        // Extract function source code from original source
        String functionSource = compilerContext.extractSourceCode(arrowExpr.getLocation());

        // Create JSBytecodeFunction
        // Arrow functions cannot be constructors
        int definedArgCount = arrowExpr.getFunctionParams().computeDefinedArgCount();
        JSBytecodeFunction function = new JSBytecodeFunction(
                compilerContext.context,
                functionBytecode,
                functionName,
                definedArgCount,
                JSValue.NO_ARGS,
                null,            // prototype - arrow functions don't have prototype
                false,           // isConstructor - arrow functions cannot be constructors
                arrowExpr.isAsync(),
                false,           // Arrow functions cannot be generators
                true,            // isArrow - this is an arrow function
                functionContext.strictMode,  // strict - inherit from enclosing scope
                functionSource   // source code for toString()
        );
        function.setHasParameterExpressions(hasNonSimpleParameters);
        function.setHasArgumentsParameterBinding(hasArgumentsParameterBinding);

        // Prototype chain will be initialized when the function is loaded
        // during bytecode execution (see FCLOSURE opcode handler)

        delegates.emitHelpers.emitCapturedValues(functionCompiler, function);
        // Emit FCLOSURE opcode with function in constant pool
        compilerContext.emitter.emitOpcodeConstant(Opcode.FCLOSURE, function);
    }

    void compileClassDeclaration(ClassDeclaration classDecl) {
        // Following QuickJS implementation in quickjs.c:24700-25200

        String className = classDecl.getId() != null ? classDecl.getId().getName() : "";
        int classNameLocalIndex = -1;
        boolean hasClassNameScope = classDecl.getId() != null;
        boolean classNameWasTDZ = hasClassNameScope && compilerContext.tdzLocals.contains(className);
        if (hasClassNameScope) {
            compilerContext.enterScope();
            classNameLocalIndex = compilerContext.currentScope().declareConstLocal(className);
            compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, classNameLocalIndex);
            compilerContext.tdzLocals.add(className);
        }

        // Compile superclass expression or emit undefined
        boolean savedStrictModeForHeritage = compilerContext.strictMode;
        compilerContext.strictMode = true;
        try {
            if (classDecl.getSuperClass() != null) {
                delegates.expressions.compileExpression(classDecl.getSuperClass());
            } else {
                compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            }
        } finally {
            compilerContext.strictMode = savedStrictModeForHeritage;
        }
        // Stack: superClass

        // Separate class elements by type and collect private symbols in a single pass
        List<MethodDefinition> methods = new ArrayList<>();
        List<MethodDefinition> privateInstanceMethods = new ArrayList<>();
        List<MethodDefinition> privateStaticMethods = new ArrayList<>();
        List<PropertyDefinition> instanceFields = new ArrayList<>();
        List<ClassElement> staticInitializers = new ArrayList<>();
        List<PropertyDefinition> computedFieldsInDefinitionOrder = new ArrayList<>();
        IdentityHashMap<PropertyDefinition, JSSymbol> computedFieldSymbols = new IdentityHashMap<>();
        IdentityHashMap<PropertyDefinition, String> autoAccessorBackingNames = new IdentityHashMap<>();
        MethodDefinition constructor = null;
        LinkedHashMap<String, String> privateNameKinds = new LinkedHashMap<>();

        for (ClassElement element : classDecl.getBody()) {
            if (element instanceof MethodDefinition method) {
                // Check if it's a constructor
                if (method.getKey() instanceof Identifier id && JSKeyword.CONSTRUCTOR.equals(id.getName()) && !method.isStatic()) {
                    constructor = method;
                } else if (method.isPrivate()) {
                    if (method.isStatic()) {
                        privateStaticMethods.add(method);
                    } else {
                        privateInstanceMethods.add(method);
                    }
                    if (method.getKey() instanceof PrivateIdentifier privateId) {
                        registerPrivateName(privateNameKinds, privateId.getName(), method.getKind());
                    }
                } else {
                    methods.add(method);
                }
            } else if (element instanceof PropertyDefinition field) {
                if (field.isStatic()) {
                    staticInitializers.add(field);
                } else {
                    instanceFields.add(field);
                }

                if (field.isPrivate() && field.getKey() instanceof PrivateIdentifier privateId) {
                    registerPrivateName(privateNameKinds, privateId.getName(), "field");
                }

                if (field.isAutoAccessor() && !field.isPrivate()) {
                    String backingName = createAutoAccessorBackingName(
                            autoAccessorBackingNames.size() + 1,
                            privateNameKinds.keySet());
                    autoAccessorBackingNames.put(field, backingName);
                    registerPrivateName(privateNameKinds, backingName, "field");
                    methods.add(field.toAutoAccessorMethod(JSKeyword.GET, backingName));
                    methods.add(field.toAutoAccessorMethod(JSKeyword.SET, backingName));
                }

                if (field.isComputed() && !field.isPrivate()) {
                    computedFieldsInDefinitionOrder.add(field);
                    computedFieldSymbols.put(
                            field,
                            new JSSymbol("__computed_field_" + computedFieldsInDefinitionOrder.size())
                    );
                }
            } else if (element instanceof StaticBlock block) {
                staticInitializers.add(block);
            }
        }

        // Create symbols for all private names collected during the scan above.
        // Merge with outer class's private symbols so nested classes can access outer private members.
        LinkedHashMap<String, JSSymbol> ownPrivateSymbols = new LinkedHashMap<>();
        for (String privateName : privateNameKinds.keySet()) {
            ownPrivateSymbols.put(privateName, new JSSymbol("#" + privateName));
        }
        Map<String, JSSymbol> privateSymbols = new LinkedHashMap<>(compilerContext.privateSymbols);
        privateSymbols.putAll(ownPrivateSymbols);
        IdentityHashMap<PropertyDefinition, JSSymbol> autoAccessorBackingSymbols = new IdentityHashMap<>();
        for (Map.Entry<PropertyDefinition, String> entry : autoAccessorBackingNames.entrySet()) {
            JSSymbol backingSymbol = privateSymbols.get(entry.getValue());
            if (backingSymbol != null) {
                autoAccessorBackingSymbols.put(entry.getKey(), backingSymbol);
            }
        }
        List<PrivateMethodEntry> privateInstanceMethodFunctions = compilePrivateMethodFunctions(
                privateInstanceMethods, privateSymbols, computedFieldSymbols);
        List<PrivateMethodEntry> privateStaticMethodFunctions = compilePrivateMethodFunctions(
                privateStaticMethods, privateSymbols, computedFieldSymbols);

        // Compile constructor function (or create default) with field initialization
        JSBytecodeFunction constructorFunc;
        if (constructor != null) {
            constructorFunc = compileMethodAsFunction(
                    constructor,
                    className,
                    classDecl.getSuperClass() != null,
                    instanceFields,
                    privateSymbols,
                    computedFieldSymbols,
                    autoAccessorBackingSymbols,
                    privateInstanceMethodFunctions,
                    true
            );
        } else {
            // Create default constructor with field initialization
            constructorFunc = createDefaultConstructor(
                    className,
                    classDecl.getSuperClass() != null,
                    instanceFields,
                    privateSymbols,
                    computedFieldSymbols,
                    autoAccessorBackingSymbols,
                    privateInstanceMethodFunctions
            );
        }

        // Set the source code for the constructor to be the entire class definition
        // This matches JavaScript behavior where class.toString() returns the class source
        if (compilerContext.sourceCode != null && classDecl.getLocation() != null) {
            int startPos = classDecl.getLocation().offset();
            int endPos = classDecl.getLocation().endOffset();
            if (startPos >= 0 && endPos <= compilerContext.sourceCode.length()) {
                String classSource = compilerContext.sourceCode.substring(startPos, endPos);
                constructorFunc.setSourceCode(classSource);
            }
        }
        constructorFunc.setClassPrivateSymbols(ownPrivateSymbols.values());

        // Class definitions must allocate a fresh constructor function per evaluation.
        compilerContext.emitter.emitOpcodeConstant(Opcode.FCLOSURE, constructorFunc);
        // Stack: superClass constructor

        // Emit DEFINE_CLASS opcode with class name
        compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_CLASS, className);
        // Stack: proto constructor

        // Now compile methods and add them to the prototype.
        // The class name binding is NOT yet initialized — computed property keys
        // that reference the class name must see it as TDZ (ReferenceError).
        // After DEFINE_CLASS: Stack is proto constructor (constructor on TOP)
        // For simplicity, swap so proto is on top: constructor proto
        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        // Stack: constructor proto

        for (MethodDefinition method : methods) {
            // Stack before each iteration: constructor proto

            if (method.isStatic()) {
                // For static methods, constructor is the target
                // Current: constructor proto
                // Need to add method to constructor, so swap to get constructor on top
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
                // Stack: proto constructor

                // Compile method. Static methods share the same private name scope.
                JSBytecodeFunction methodFunc = compileMethodAsFunction(
                        method,
                        compilerContext.getMethodName(method),
                        false,
                        List.of(),
                        privateSymbols,
                        computedFieldSymbols,
                        autoAccessorBackingSymbols,
                        List.of(),
                        false
                );

                String methodName = compilerContext.getMethodName(method);
                delegates.emitHelpers.emitClassMethodDefinition(method, methodFunc, methodName);
                // Stack: proto constructor (method added to constructor)

                // Swap back to restore order: constructor proto
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
                // Stack: constructor proto
            } else {
                // For instance methods, proto is the target
                // Current: constructor proto
                // Compile method (no field initialization for regular methods)
                // Pass private symbols to methods so they can access private fields
                JSBytecodeFunction methodFunc = compileMethodAsFunction(
                        method,
                        compilerContext.getMethodName(method),
                        false,
                        List.of(),
                        privateSymbols,
                        computedFieldSymbols,
                        autoAccessorBackingSymbols,
                        List.of(),
                        false
                );

                String methodName = compilerContext.getMethodName(method);
                delegates.emitHelpers.emitClassMethodDefinition(method, methodFunc, methodName);
                // Stack: constructor proto (method added to proto)
            }
        }

        installPrivateStaticMethods(privateStaticMethodFunctions, privateSymbols);

        // Evaluate all computed field names once at class definition time.
        // This matches QuickJS behavior and avoids re-evaluating key side effects per instance.
        for (PropertyDefinition field : computedFieldsInDefinitionOrder) {
            compileComputedFieldNameCache(field, computedFieldSymbols, privateSymbols);
        }

        // Swap back to original order: proto constructor
        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        // Stack: proto constructor

        // Drop prototype, keep constructor (following QuickJS: proto is dropped before
        // the class name is initialized and before static initializers run).
        compilerContext.emitter.emitOpcode(Opcode.NIP);
        // Stack: constructor

        // Initialize inner immutable class-name binding. This must happen AFTER all
        // method definitions and computed property key evaluation (so those see TDZ),
        // but BEFORE static initializers (which need to access the class name).
        if (hasClassNameScope) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, classNameLocalIndex);
        }

        // Execute static initializers (static fields and static blocks) in source order.
        // Each initializer runs with class constructor as `this`.
        for (ClassElement staticInitializer : staticInitializers) {
            JSBytecodeFunction staticInitializerFunc;
            if (staticInitializer instanceof PropertyDefinition staticField) {
                staticInitializerFunc = compileStaticFieldInitializer(
                        staticField, computedFieldSymbols, privateSymbols, autoAccessorBackingSymbols, className);
            } else if (staticInitializer instanceof StaticBlock staticBlock) {
                staticInitializerFunc = compileStaticBlock(staticBlock, className, privateSymbols);
            } else {
                continue;
            }

            // Stack: constructor
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            // Stack: constructor constructor

            compilerContext.emitter.emitOpcodeConstant(Opcode.FCLOSURE, staticInitializerFunc);
            // Stack: constructor constructor func

            compilerContext.emitter.emitOpcode(Opcode.SWAP);
            // Stack: constructor func constructor

            compilerContext.emitter.emitOpcodeU16(Opcode.CALL, 0);
            // Stack: constructor returnValue

            compilerContext.emitter.emitOpcode(Opcode.DROP);
            // Stack: constructor
        }

        if (hasClassNameScope) {
            compilerContext.exitScope();
            if (!classNameWasTDZ) {
                compilerContext.tdzLocals.remove(className);
            }
        }

        // Store the class constructor in a variable
        if (classDecl.getId() != null) {
            String varName = classDecl.getId().getName();
            if (!compilerContext.inGlobalScope) {
                compilerContext.currentScope().declareLocal(varName);
                Integer localIndex = compilerContext.currentScope().getLocal(varName);
                if (localIndex != null) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
                }
            } else if (compilerContext.tdzLocals.contains(varName)) {
                // TDZ local: class was pre-declared as a local for TDZ enforcement
                Integer localIndex = compilerContext.findLocalInScopes(varName);
                if (localIndex != null) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
                    // Direct eval creates an ephemeral lexical environment. When program
                    // lexicals are predeclared as locals, do not materialize class bindings
                    // on the global object.
                    if (!compilerContext.predeclareProgramLexicalsAsLocals) {
                        // Also store as global variable so class methods (compiled with separate
                        // BytecodeCompiler contexts) can access this class via GET_VAR.
                        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, localIndex);
                        compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
                    }
                }
            } else {
                compilerContext.nonDeletableGlobalBindings.add(varName);
                compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
            }
        } else {
            // Anonymous class expression - leave on stack
            // For class declarations, we always have a name, so this shouldn't happen
        }
    }

    void compileClassExpression(ClassExpression classExpr) {
        // Class expressions are almost identical to class declarations,
        // but they leave the constructor on the stack instead of binding it to a variable

        String className = classExpr.getId() != null ? classExpr.getId().getName()
                : (compilerContext.inferredClassName != null ? compilerContext.inferredClassName : "");

        // Per ES2024 ClassDefinitionEvaluation: create a new lexical scope for the class name
        // binding so that methods and heritage closures can capture it.
        int classNameLocalIndex = -1;
        boolean hasClassNameScope = classExpr.getId() != null;
        boolean classNameWasTDZ = hasClassNameScope && compilerContext.tdzLocals.contains(className);
        if (hasClassNameScope) {
            compilerContext.enterScope();
            classNameLocalIndex = compilerContext.currentScope().declareConstLocal(className);
            compilerContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, classNameLocalIndex);
            compilerContext.tdzLocals.add(className);
        }

        // Compile superclass expression or emit undefined
        boolean savedStrictModeForHeritage = compilerContext.strictMode;
        compilerContext.strictMode = true;
        try {
            if (classExpr.getSuperClass() != null) {
                delegates.expressions.compileExpression(classExpr.getSuperClass());
            } else {
                compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            }
        } finally {
            compilerContext.strictMode = savedStrictModeForHeritage;
        }
        // Stack: superClass

        // Separate class elements by type and collect private symbols in a single pass
        List<MethodDefinition> methods = new ArrayList<>();
        List<MethodDefinition> privateInstanceMethods = new ArrayList<>();
        List<MethodDefinition> privateStaticMethods = new ArrayList<>();
        List<PropertyDefinition> instanceFields = new ArrayList<>();
        List<ClassElement> staticInitializers = new ArrayList<>();
        List<PropertyDefinition> computedFieldsInDefinitionOrder = new ArrayList<>();
        IdentityHashMap<PropertyDefinition, JSSymbol> computedFieldSymbols = new IdentityHashMap<>();
        IdentityHashMap<PropertyDefinition, String> autoAccessorBackingNames = new IdentityHashMap<>();
        MethodDefinition constructor = null;
        LinkedHashMap<String, String> privateNameKinds = new LinkedHashMap<>();

        for (ClassElement element : classExpr.getBody()) {
            if (element instanceof MethodDefinition method) {
                // Check if it's a constructor
                if (method.getKey() instanceof Identifier id && JSKeyword.CONSTRUCTOR.equals(id.getName()) && !method.isStatic()) {
                    constructor = method;
                } else if (method.isPrivate()) {
                    if (method.isStatic()) {
                        privateStaticMethods.add(method);
                    } else {
                        privateInstanceMethods.add(method);
                    }
                    if (method.getKey() instanceof PrivateIdentifier privateId) {
                        registerPrivateName(privateNameKinds, privateId.getName(), method.getKind());
                    }
                } else {
                    methods.add(method);
                }
            } else if (element instanceof PropertyDefinition field) {
                if (field.isStatic()) {
                    staticInitializers.add(field);
                } else {
                    instanceFields.add(field);
                }

                if (field.isPrivate() && field.getKey() instanceof PrivateIdentifier privateId) {
                    registerPrivateName(privateNameKinds, privateId.getName(), "field");
                }

                if (field.isAutoAccessor() && !field.isPrivate()) {
                    String backingName = createAutoAccessorBackingName(
                            autoAccessorBackingNames.size() + 1,
                            privateNameKinds.keySet());
                    autoAccessorBackingNames.put(field, backingName);
                    registerPrivateName(privateNameKinds, backingName, "field");
                    methods.add(field.toAutoAccessorMethod(JSKeyword.GET, backingName));
                    methods.add(field.toAutoAccessorMethod(JSKeyword.SET, backingName));
                }

                if (field.isComputed() && !field.isPrivate()) {
                    computedFieldsInDefinitionOrder.add(field);
                    computedFieldSymbols.put(
                            field,
                            new JSSymbol("__computed_field_" + computedFieldsInDefinitionOrder.size())
                    );
                }
            } else if (element instanceof StaticBlock block) {
                staticInitializers.add(block);
            }
        }

        // Create symbols for all private names collected during the scan above.
        // Merge with outer class's private symbols so nested classes can access outer private members.
        LinkedHashMap<String, JSSymbol> ownPrivateSymbols = new LinkedHashMap<>();
        for (String privateName : privateNameKinds.keySet()) {
            ownPrivateSymbols.put(privateName, new JSSymbol("#" + privateName));
        }
        Map<String, JSSymbol> privateSymbols = new LinkedHashMap<>(compilerContext.privateSymbols);
        privateSymbols.putAll(ownPrivateSymbols);
        IdentityHashMap<PropertyDefinition, JSSymbol> autoAccessorBackingSymbols = new IdentityHashMap<>();
        for (Map.Entry<PropertyDefinition, String> entry : autoAccessorBackingNames.entrySet()) {
            JSSymbol backingSymbol = privateSymbols.get(entry.getValue());
            if (backingSymbol != null) {
                autoAccessorBackingSymbols.put(entry.getKey(), backingSymbol);
            }
        }
        List<PrivateMethodEntry> privateInstanceMethodFunctions = compilePrivateMethodFunctions(
                privateInstanceMethods, privateSymbols, computedFieldSymbols);
        List<PrivateMethodEntry> privateStaticMethodFunctions = compilePrivateMethodFunctions(
                privateStaticMethods, privateSymbols, computedFieldSymbols);

        // Compile constructor function (or create default)
        JSBytecodeFunction constructorFunc;
        if (constructor != null) {
            constructorFunc = compileMethodAsFunction(
                    constructor,
                    className,
                    classExpr.getSuperClass() != null,
                    instanceFields,
                    privateSymbols,
                    computedFieldSymbols,
                    autoAccessorBackingSymbols,
                    privateInstanceMethodFunctions,
                    true
            );
        } else {
            constructorFunc = createDefaultConstructor(
                    className,
                    classExpr.getSuperClass() != null,
                    instanceFields,
                    privateSymbols,
                    computedFieldSymbols,
                    autoAccessorBackingSymbols,
                    privateInstanceMethodFunctions
            );
        }

        // Set the source code for the constructor to be the entire class definition
        // This matches JavaScript behavior where class.toString() returns the class source
        if (compilerContext.sourceCode != null && classExpr.getLocation() != null) {
            int startPos = classExpr.getLocation().offset();
            int endPos = classExpr.getLocation().endOffset();
            if (startPos >= 0 && endPos <= compilerContext.sourceCode.length()) {
                String classSource = compilerContext.sourceCode.substring(startPos, endPos);
                constructorFunc.setSourceCode(classSource);
            }
        }
        constructorFunc.setClassPrivateSymbols(ownPrivateSymbols.values());

        // Class expressions must allocate a fresh constructor function per evaluation.
        compilerContext.emitter.emitOpcodeConstant(Opcode.FCLOSURE, constructorFunc);
        // Stack: superClass constructor

        // Emit DEFINE_CLASS opcode with class name
        compilerContext.emitter.emitOpcodeAtom(Opcode.DEFINE_CLASS, className);
        // Stack: proto constructor

        // Compile methods. The class name binding is NOT yet initialized —
        // computed property keys that reference the class name must see TDZ.
        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        // Stack: constructor proto

        for (MethodDefinition method : methods) {
            if (method.isStatic()) {
                // For static methods, constructor is the target object
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
                // Stack: proto constructor

                JSBytecodeFunction methodFunc = compileMethodAsFunction(
                        method,
                        compilerContext.getMethodName(method),
                        false,
                        List.of(),
                        privateSymbols,
                        computedFieldSymbols,
                        autoAccessorBackingSymbols,
                        List.of(),
                        false
                );

                String methodName = compilerContext.getMethodName(method);
                delegates.emitHelpers.emitClassMethodDefinition(method, methodFunc, methodName);
                // Stack: proto constructor

                // Restore canonical order for next iteration
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
                // Stack: constructor proto
            } else {
                JSBytecodeFunction methodFunc = compileMethodAsFunction(
                        method,
                        compilerContext.getMethodName(method),
                        false,
                        List.of(),
                        privateSymbols,
                        computedFieldSymbols,
                        autoAccessorBackingSymbols,
                        List.of(),
                        false
                );

                String methodName = compilerContext.getMethodName(method);
                delegates.emitHelpers.emitClassMethodDefinition(method, methodFunc, methodName);
                // Stack: constructor proto
            }
        }

        installPrivateStaticMethods(privateStaticMethodFunctions, privateSymbols);

        // Evaluate all computed field names once at class definition time.
        for (PropertyDefinition field : computedFieldsInDefinitionOrder) {
            compileComputedFieldNameCache(field, computedFieldSymbols, privateSymbols);
        }

        // Swap back to: proto constructor
        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        // Stack: proto constructor

        // Drop prototype, keep constructor (following QuickJS: proto is dropped before
        // the class name is initialized and before static initializers run).
        compilerContext.emitter.emitOpcode(Opcode.NIP);
        // Stack: constructor

        // Initialize inner immutable class-name binding. This must happen AFTER all
        // method definitions and computed property key evaluation (so those see TDZ),
        // but BEFORE static initializers (which need to access the class name).
        if (hasClassNameScope) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, classNameLocalIndex);
        }

        // Execute static initializers (static fields and static blocks) in source order.
        for (ClassElement staticInitializer : staticInitializers) {
            JSBytecodeFunction staticInitializerFunc;
            if (staticInitializer instanceof PropertyDefinition staticField) {
                staticInitializerFunc = compileStaticFieldInitializer(
                        staticField, computedFieldSymbols, privateSymbols, autoAccessorBackingSymbols, className);
            } else if (staticInitializer instanceof StaticBlock staticBlock) {
                staticInitializerFunc = compileStaticBlock(staticBlock, className, privateSymbols);
            } else {
                continue;
            }

            // Stack: constructor
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            // Stack: constructor constructor

            compilerContext.emitter.emitOpcodeConstant(Opcode.FCLOSURE, staticInitializerFunc);
            // Stack: constructor constructor func

            compilerContext.emitter.emitOpcode(Opcode.SWAP);
            // Stack: constructor func constructor

            compilerContext.emitter.emitOpcodeU16(Opcode.CALL, 0);
            // Stack: constructor returnValue

            compilerContext.emitter.emitOpcode(Opcode.DROP);
            // Stack: constructor
        }

        // Exit the class name scope
        if (hasClassNameScope) {
            compilerContext.exitScope();
            if (!classNameWasTDZ) {
                compilerContext.tdzLocals.remove(className);
            }
        }

        // For class expressions, we leave the constructor on the stack
        // (unlike class declarations which bind it to a variable)
    }

    /**
     * Evaluate and cache a computed class field name on the constructor object once.
     * Expects stack before/after to be: constructor proto.
     */
    void compileComputedFieldNameCache(
            PropertyDefinition field,
            IdentityHashMap<PropertyDefinition, JSSymbol> computedFieldSymbols,
            Map<String, JSSymbol> privateSymbols) {
        fieldCompiler.compileComputedFieldNameCache(field, computedFieldSymbols, privateSymbols);
    }

    /**
     * Compile field initialization code for instance fields.
     * Emits code to set each field on 'this' with its initializer value.
     * For private fields, uses the symbol from privateSymbols map.
     */
    void compileFieldInitialization(List<PropertyDefinition> fields,
                                    Map<String, JSSymbol> privateSymbols,
                                    IdentityHashMap<PropertyDefinition, JSSymbol> computedFieldSymbols,
                                    IdentityHashMap<PropertyDefinition, JSSymbol> autoAccessorBackingSymbols) {
        fieldCompiler.compileFieldInitialization(
                fields,
                privateSymbols,
                computedFieldSymbols,
                autoAccessorBackingSymbols);
    }

    void compileFunctionDeclaration(FunctionDeclaration funcDecl) {
        // Pre-declare function name as a local in the current scope (if non-global and not
        // already declared). This must happen BEFORE creating the child compiler so the
        // function body can capture the name via closure for self-reference.
        // Per QuickJS: function declarations inside blocks/switch cases create a lexical
        // binding in the current scope, even if a parent scope has the same name (e.g.,
        // a parameter). This prevents the function object from overwriting the parent binding.
        String functionName = funcDecl.getId().getName();
        if (!compilerContext.inGlobalScope) {
            if (compilerContext.currentScope().getLocal(functionName) == null) {
                compilerContext.currentScope().declareLocal(functionName);
            }
        }

        // Create a new compiler for the function body
        // Nested functions inherit strict mode from parent (QuickJS behavior)
        BytecodeCompiler functionCompiler = new BytecodeCompiler(compilerContext.strictMode, compilerContext.captureResolver, compilerContext.context);
        CompilerContext functionContext = functionCompiler.context();
        CompilerDelegates funcDelegates = functionCompiler.delegates();
        functionContext.sourceCode = compilerContext.sourceCode;
        functionContext.nonDeletableGlobalBindings.addAll(compilerContext.nonDeletableGlobalBindings);
        functionContext.privateSymbols = compilerContext.privateSymbols;
        inheritVisibleWithObjectBindings(functionContext);

        // Enter function scope and add parameters as locals
        functionContext.enterScope();
        functionContext.inGlobalScope = false;
        functionContext.isInAsyncFunction = funcDecl.isAsync();  // Track if this is an async function
        functionContext.isInGeneratorFunction = funcDecl.isGenerator();
        // Inherit class inner name so eval() inside nested functions can resolve it.
        inheritClassInnerNameCapture(functionContext);

        // Check for "use strict" directive early and update strict mode
        // This ensures nested functions inherit the correct strict mode
        if (funcDecl.getBody().hasUseStrictDirective()) {
            functionContext.strictMode = true;
        }

        List<Integer> parameterSlotIndexes = new ArrayList<>();
        List<int[]> destructuringParams = declareParameters(
                funcDecl.getParams(),
                functionContext,
                funcDelegates,
                parameterSlotIndexes);
        if (funcDecl.needsArguments()) {
            declareAndInitializeImplicitArgumentsBinding(functionContext);
        }

        // Emit default parameter initialization following QuickJS pattern
        if (funcDecl.getDefaults() != null) {
            delegates.emitHelpers.emitDefaultParameterInit(
                    functionCompiler,
                    funcDecl.getFunctionParams(),
                    parameterSlotIndexes);
        }

        // Handle rest parameter if present
        // The REST opcode must be emitted early in the function to initialize the rest array
        if (funcDecl.getRestParameter() != null) {
            // Calculate the index where rest arguments start
            int firstRestIndex = funcDecl.getParams().size();

            // Emit REST opcode with the starting index
            functionContext.emitter.emitOpcode(Opcode.REST);
            functionContext.emitter.emitU16(firstRestIndex);

            emitRestParameterBinding(funcDecl.getRestParameter(), functionContext, funcDelegates);
        }

        // Emit destructuring for pattern parameters after defaults and rest
        emitParameterDestructuring(funcDecl.getParams(), destructuringParams, functionContext, funcDelegates);

        // If this is a generator function, emit INITIAL_YIELD at the start
        if (funcDecl.isGenerator()) {
            functionContext.emitter.emitOpcode(Opcode.INITIAL_YIELD);
        }

        // Phase 0: Pre-declare all var bindings as locals before function hoisting.
        // var declarations are function-scoped and must be visible to nested function
        // declarations that may capture them. Without this, Phase 1 function hoisting
        // would fail to resolve captured var references (e.g., inner functions referencing
        // outer var variables would emit GET_VAR instead of GET_VAR_REF).
        funcDelegates.analysis.hoistAllDeclarationsAsLocals(funcDecl.getBody().getBody());

        // Phase 1: Hoist top-level function declarations (ES spec requires function
        // declarations to be initialized before any code executes).
        for (Statement stmt : funcDecl.getBody().getBody()) {
            if (stmt instanceof FunctionDeclaration innerFuncDecl) {
                funcDelegates.functions.compileFunctionDeclaration(innerFuncDecl);
            }
        }

        // Annex B.3.3.1: Hoist function declarations from blocks/if-statements
        // to the function scope as var bindings (initialized to undefined).
        Set<String> declParamNames = funcDecl.getParameterNames();
        funcDelegates.analysis.hoistFunctionBodyAnnexBDeclarations(funcDecl.getBody().getBody(), declParamNames);

        // Phase 2: Compile non-FunctionDeclaration statements in source order
        for (Statement stmt : funcDecl.getBody().getBody()) {
            if (stmt instanceof FunctionDeclaration) {
                continue; // Already hoisted in Phase 1
            }
            funcDelegates.statements.compileStatement(stmt);
        }

        // If body doesn't end with return, add implicit return undefined
        List<Statement> bodyStatements = funcDecl.getBody().getBody();
        if (bodyStatements.isEmpty() || !(bodyStatements.get(bodyStatements.size() - 1) instanceof ReturnStatement)) {
            functionContext.emitter.emitOpcode(Opcode.UNDEFINED);
            int returnValueIndex = functionContext.currentScope().declareLocal("$function_return_" + functionContext.emitter.currentOffset());
            functionContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, returnValueIndex);
            funcDelegates.emitHelpers.emitCurrentScopeUsingDisposal();
            functionContext.emitter.emitOpcodeU16(Opcode.GET_LOC, returnValueIndex);
            // Emit RETURN_ASYNC for async functions, RETURN for sync functions
            functionContext.emitter.emitOpcode(funcDecl.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
        }

        int localCount = functionContext.currentScope().getLocalCount();
        String[] localVarNames = functionContext.getLocalVarNames();
        functionContext.exitScope();

        // Build the function bytecode
        Bytecode functionBytecode = functionContext.emitter.build(localCount, localVarNames);

        // Function name (already extracted at start of method)

        // Detect "use strict" directive in function body
        // Combine inherited strict mode with local "use strict" directive
        boolean isStrict = functionContext.strictMode
                || funcDecl.getBody().hasUseStrictDirective();

        // Extract function source code from original source
        String functionSource = compilerContext.extractSourceCode(funcDecl.getLocation());

        // Trim trailing whitespace from the extracted source
        // This is needed because the parser's end offset may include whitespace after the closing brace
        if (functionSource != null) {
            functionSource = functionSource.stripTrailing();
        }

        // If extraction failed, build a simplified representation
        if (functionSource == null || functionSource.isEmpty()) {
            StringBuilder funcSource = new StringBuilder();
            if (funcDecl.isAsync()) {
                funcSource.append("async ");
            }
            funcSource.append(JSKeyword.FUNCTION);
            if (funcDecl.isGenerator()) {
                funcSource.append("*");
            }
            funcSource.append(" ").append(functionName).append("(");
            for (int i = 0; i < funcDecl.getParams().size(); i++) {
                if (i > 0) {
                    funcSource.append(", ");
                }
                funcSource.append(funcDecl.getParams().get(i).toPatternString());
            }
            if (funcDecl.getRestParameter() != null) {
                if (!funcDecl.getParams().isEmpty()) {
                    funcSource.append(", ");
                }
                funcSource.append("...").append(funcDecl.getRestParameter().getArgument().toPatternString());
            }
            funcSource.append(") { [function body] }");
            functionSource = funcSource.toString();
        }

        // Check if the function captures its own name (e.g., block-scoped function declaration
        // where the body references f). This fixes the chicken-and-egg problem where FCLOSURE
        // captures the local before the function is stored in it.
        // Following QuickJS var_refs pattern: the closure variable pointing to the function
        // itself is patched after creation via selfCaptureIndex metadata.
        Integer selfCaptureIdx = functionContext.findCapturedBindingIndex(functionName);
        int selfCaptureIndex = selfCaptureIdx != null ? selfCaptureIdx : -1;

        // Create JSBytecodeFunction
        int definedArgCount = funcDecl.getFunctionParams().computeDefinedArgCount();
        // Per ES spec FunctionAllocate: async functions, generator functions,
        // async generators are NOT constructable
        boolean isFuncConstructor = !funcDecl.isAsync() && !funcDecl.isGenerator();
        JSBytecodeFunction function = new JSBytecodeFunction(
                compilerContext.context,
                functionBytecode,
                functionName,
                definedArgCount,
                JSValue.NO_ARGS,
                null,            // prototype - will be set by VM
                isFuncConstructor,
                funcDecl.isAsync(),
                funcDecl.isGenerator(),
                false,           // isArrow - regular function, not arrow
                isStrict,        // strict - detected from "use strict" directive in function body
                functionSource,  // source code for toString()
                selfCaptureIndex // closure self-reference index (-1 if none)
        );
        function.setHasParameterExpressions(funcDecl.getFunctionParams().hasNonSimpleParameters());

        delegates.emitHelpers.emitCapturedValues(functionCompiler, function);
        // Emit FCLOSURE opcode with function in constant pool
        compilerContext.emitter.emitOpcodeConstant(Opcode.FCLOSURE, function);

        // Store the function in a variable with its name
        // Per B.3.3.1: the Annex B runtime hook only fires if no enclosing block
        // scope has a lexical binding for the same name (otherwise replacing this
        // function with var F would produce an Early Error).
        boolean isAnnexB = compilerContext.annexBFunctionNames.contains(functionName)
                && !funcDecl.isAsync()
                && !funcDecl.isGenerator()
                && !compilerContext.hasEnclosingBlockScopeLocal(functionName);
        Integer localIndex = compilerContext.findLocalInScopes(functionName);
        if (localIndex != null) {
            if (isAnnexB) {
                // Annex B.3.3 runtime hook: store in both block scope and var scope
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
                delegates.emitHelpers.emitAnnexBVarStore(functionName);
            } else {
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
            }
        } else {
            // Declare the function as a global variable or in the current scope
            if (compilerContext.inGlobalScope) {
                compilerContext.nonDeletableGlobalBindings.add(functionName);
                compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, functionName);
            } else {
                // Declare it as a local
                localIndex = compilerContext.currentScope().declareLocal(functionName);
                if (isAnnexB) {
                    // Annex B.3.3 runtime hook: store in both block scope and var scope
                    compilerContext.emitter.emitOpcode(Opcode.DUP);
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
                    delegates.emitHelpers.emitAnnexBVarStore(functionName);
                } else {
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
                }
            }
        }
    }

    void compileFunctionExpression(FunctionExpression functionExpression, boolean forceNonConstructor) {
        compileFunctionExpressionInternal(functionExpression, forceNonConstructor);
    }

    void compileFunctionExpression(FunctionExpression functionExpression) {
        compileFunctionExpressionInternal(functionExpression, false);
    }

    private void compileFunctionExpressionInternal(FunctionExpression functionExpression, boolean forceNonConstructor) {
        // Create a new compiler for the function body
        // Nested functions inherit strict mode from parent (QuickJS behavior)
        BytecodeCompiler functionCompiler = new BytecodeCompiler(compilerContext.strictMode, compilerContext.captureResolver, compilerContext.context);
        CompilerContext functionContext = functionCompiler.context();
        CompilerDelegates funcDelegates = functionCompiler.delegates();
        functionContext.sourceCode = compilerContext.sourceCode;
        functionContext.nonDeletableGlobalBindings.addAll(compilerContext.nonDeletableGlobalBindings);
        functionContext.privateSymbols = compilerContext.privateSymbols;
        inheritVisibleWithObjectBindings(functionContext);

        // Enter function scope and add parameters as locals
        functionContext.enterScope();
        functionContext.inGlobalScope = false;
        functionContext.isInAsyncFunction = functionExpression.isAsync();
        functionContext.isInGeneratorFunction = functionExpression.isGenerator();
        // Inherit class inner name so eval() inside nested functions can resolve it.
        inheritClassInnerNameCapture(functionContext);

        // Check for "use strict" directive early and update strict mode
        // This ensures nested functions inherit the correct strict mode
        if (functionExpression.getBody().hasUseStrictDirective()) {
            functionContext.strictMode = true;
        }

        List<Integer> parameterSlotIndexes = new ArrayList<>();
        List<int[]> destructuringParams = declareParameters(
                functionExpression.getParams(),
                functionContext,
                funcDelegates,
                parameterSlotIndexes);
        if (functionExpression.needsArguments()) {
            declareAndInitializeImplicitArgumentsBinding(functionContext);
        }

        if (functionExpression.getId() != null) {
            boolean conflictsWithParameter = false;
            Set<String> allParamNames = new HashSet<>();
            for (Pattern param : functionExpression.getParams()) {
                allParamNames.addAll(param.getBoundNames());
            }
            conflictsWithParameter = allParamNames.contains(functionExpression.getId().getName());
            if (!conflictsWithParameter && functionExpression.getRestParameter() != null) {
                List<String> restBoundNames = functionExpression.getRestParameter().getArgument().getBoundNames();
                conflictsWithParameter = restBoundNames.contains(functionExpression.getId().getName());
            }
            if (!conflictsWithParameter) {
                functionContext.currentScope().declareLocal(functionExpression.getId().getName());
                // Per ES2024 15.2.5: The BindingIdentifier in a named function expression
                // is an immutable binding. Following QuickJS add_func_var:
                // - In strict mode: mark as const so assignment throws TypeError
                // - In non-strict mode: mark as function name so assignment is silently ignored
                if (functionContext.strictMode) {
                    functionContext.currentScope().markConstLocal(functionExpression.getId().getName());
                } else {
                    functionContext.currentScope().markFunctionNameLocal(functionExpression.getId().getName());
                }
            }
        }

        // Emit default parameter initialization following QuickJS pattern
        if (functionExpression.getDefaults() != null) {
            delegates.emitHelpers.emitDefaultParameterInit(
                    functionCompiler,
                    functionExpression.getFunctionParams(),
                    parameterSlotIndexes);
        }

        // Handle rest parameter if present
        // The REST opcode must be emitted early in the function to initialize the rest array
        if (functionExpression.getRestParameter() != null) {
            // Calculate the index where rest arguments start
            int firstRestIndex = functionExpression.getParams().size();

            // Emit REST opcode with the starting index
            functionContext.emitter.emitOpcode(Opcode.REST);
            functionContext.emitter.emitU16(firstRestIndex);

            emitRestParameterBinding(functionExpression.getRestParameter(), functionContext, funcDelegates);
        }

        // Emit destructuring for pattern parameters after defaults and rest
        emitParameterDestructuring(functionExpression.getParams(), destructuringParams, functionContext, funcDelegates);

        // If this is a generator function, emit INITIAL_YIELD at the start
        if (functionExpression.isGenerator()) {
            functionContext.emitter.emitOpcode(Opcode.INITIAL_YIELD);
        }

        // Phase 0: Pre-declare all var bindings as locals before function hoisting.
        // var declarations are function-scoped and must be visible to nested function
        // declarations that may capture them. Without this, Phase 1 function hoisting
        // would fail to resolve captured var references (e.g., inner functions referencing
        // outer var variables would emit GET_VAR instead of GET_VAR_REF).
        funcDelegates.analysis.hoistAllDeclarationsAsLocals(functionExpression.getBody().getBody());

        // Phase 1: Hoist top-level function declarations (ES spec requires function
        // declarations to be initialized before any code executes).
        for (Statement stmt : functionExpression.getBody().getBody()) {
            if (stmt instanceof FunctionDeclaration funcDecl) {
                funcDelegates.functions.compileFunctionDeclaration(funcDecl);
            }
        }

        // Annex B.3.3.1: Hoist function declarations from blocks/if-statements
        // to the function scope as var bindings (initialized to undefined).
        // Build parameterNames set (BoundNames of argumentsList + "arguments" binding)
        Set<String> exprParamNames = functionExpression.getParameterNames();
        funcDelegates.analysis.hoistFunctionBodyAnnexBDeclarations(functionExpression.getBody().getBody(), exprParamNames);

        // Phase 2: Compile non-FunctionDeclaration statements in source order
        for (Statement stmt : functionExpression.getBody().getBody()) {
            if (stmt instanceof FunctionDeclaration) {
                continue; // Already hoisted in Phase 1
            }
            funcDelegates.statements.compileStatement(stmt);
        }

        // If body doesn't end with return, add implicit return undefined
        // Check if last statement is a return statement
        List<Statement> bodyStatements = functionExpression.getBody().getBody();
        if (bodyStatements.isEmpty() || !(bodyStatements.get(bodyStatements.size() - 1) instanceof ReturnStatement)) {
            functionContext.emitter.emitOpcode(Opcode.UNDEFINED);
            int returnValueIndex = functionContext.currentScope().declareLocal("$function_return_" + functionContext.emitter.currentOffset());
            functionContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, returnValueIndex);
            funcDelegates.emitHelpers.emitCurrentScopeUsingDisposal();
            functionContext.emitter.emitOpcodeU16(Opcode.GET_LOC, returnValueIndex);
            functionContext.emitter.emitOpcode(functionExpression.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
        }

        Integer functionExpressionSelfLocalIndex = null;
        if (functionExpression.getId() != null) {
            functionExpressionSelfLocalIndex = functionContext.currentScope().getLocal(functionExpression.getId().getName());
        }
        int localCount = functionContext.currentScope().getLocalCount();
        String[] localVarNames = functionContext.getLocalVarNames();
        functionContext.exitScope();

        // Build the function bytecode
        Bytecode functionBytecode = functionContext.emitter.build(localCount, localVarNames);

        // Get function name (empty string for anonymous)
        String functionName = functionExpression.getId() != null ? functionExpression.getId().getName() : "";

        // Detect "use strict" directive in function body
        // Combine inherited strict mode with local "use strict" directive
        boolean isStrict = functionContext.strictMode
                || functionExpression.getBody().hasUseStrictDirective();

        // Extract function source code from original source
        String functionSource = compilerContext.extractSourceCode(functionExpression.getLocation());

        // Create JSBytecodeFunction
        int definedArgCount = functionExpression.getFunctionParams().computeDefinedArgCount();
        // Per ES spec FunctionAllocate: async functions, generator functions,
        // async generators, and getter/setter methods are NOT constructable
        boolean isFuncConstructor = !forceNonConstructor
                && !functionExpression.isAsync()
                && !functionExpression.isGenerator();
        JSBytecodeFunction function = new JSBytecodeFunction(
                compilerContext.context,
                functionBytecode,
                functionName,
                definedArgCount,
                JSValue.NO_ARGS,
                null,            // prototype - will be set by VM
                isFuncConstructor,
                functionExpression.isAsync(),
                functionExpression.isGenerator(),
                false,           // isArrow - regular function, not arrow
                isStrict,        // strict - detected from "use strict" directive in function body
                functionSource   // source code for toString()
        );
        function.setHasParameterExpressions(functionExpression.getFunctionParams().hasNonSimpleParameters());
        if (functionExpressionSelfLocalIndex != null) {
            function.setSelfLocalIndex(functionExpressionSelfLocalIndex);
        }

        // Prototype chain will be initialized when the function is loaded
        // during bytecode execution (see FCLOSURE opcode handler)

        delegates.emitHelpers.emitCapturedValues(functionCompiler, function);
        // Emit FCLOSURE opcode with function in constant pool
        compilerContext.emitter.emitOpcodeConstant(Opcode.FCLOSURE, function);
    }

    /**
     * Compile a method definition as a function.
     * For constructors, instanceFields contains fields to initialize.
     * privateSymbols contains JSSymbol instances for private fields (passed as closure variables).
     */
    JSBytecodeFunction compileMethodAsFunction(
            MethodDefinition method,
            String methodName,
            boolean isDerivedConstructor,
            List<PropertyDefinition> instanceFields,
            Map<String, JSSymbol> privateSymbols,
            IdentityHashMap<PropertyDefinition, JSSymbol> computedFieldSymbols,
            IdentityHashMap<PropertyDefinition, JSSymbol> autoAccessorBackingSymbols,
            List<PrivateMethodEntry> privateInstanceMethodFunctions,
            boolean isConstructor) {
        // Pass parent captureResolver so class methods can capture outer scope variables (closures)
        BytecodeCompiler methodCompiler = new BytecodeCompiler(true, compilerContext.captureResolver, compilerContext.context);
        CompilerContext functionContext = methodCompiler.context();
        CompilerDelegates methodDelegates = methodCompiler.delegates();
        functionContext.sourceCode = compilerContext.sourceCode;
        functionContext.nonDeletableGlobalBindings.addAll(compilerContext.nonDeletableGlobalBindings);
        functionContext.privateSymbols = privateSymbols;  // Make private symbols available in method
        inheritVisibleWithObjectBindings(functionContext);

        FunctionExpression functionExpression = method.getValue();

        // Enter function scope and add parameters as locals
        functionContext.enterScope();
        functionContext.inGlobalScope = false;
        functionContext.isInAsyncFunction = functionExpression.isAsync();
        functionContext.isInGeneratorFunction = functionExpression.isGenerator();

        // Force-capture the class inner name binding so eval() inside
        // constructor/method body can resolve it at runtime.
        if (isConstructor && methodName != null && !methodName.isEmpty()) {
            functionContext.captureResolver.resolveCapturedBindingIndex(methodName);
            // Propagate to nested functions (arrows, etc.) so they also capture the name.
            functionContext.classInnerNameToCapture = methodName;
        }

        List<Integer> parameterSlotIndexes = new ArrayList<>();
        List<int[]> methodDestructuringParams = declareParameters(
                functionExpression.getParams(),
                functionContext,
                methodDelegates,
                parameterSlotIndexes);
        if (method.getValue().needsArguments()) {
            declareAndInitializeImplicitArgumentsBinding(functionContext);
        }

        // For base constructors, instance private methods/fields are initialized before
        // parameter default evaluation so defaults can access private members on `this`.
        if (isConstructor && !isDerivedConstructor) {
            if (!privateInstanceMethodFunctions.isEmpty()) {
                methodDelegates.functions.compilePrivateMethodInitialization(privateInstanceMethodFunctions, privateSymbols);
            }
            if (!instanceFields.isEmpty()) {
                methodDelegates.functions.compileFieldInitialization(
                        instanceFields,
                        privateSymbols,
                        computedFieldSymbols,
                        autoAccessorBackingSymbols);
            }
        }

        // Emit default parameter initialization following QuickJS pattern
        if (functionExpression.getDefaults() != null) {
            delegates.emitHelpers.emitDefaultParameterInit(
                    methodCompiler,
                    functionExpression.getFunctionParams(),
                    parameterSlotIndexes);
        }

        // Handle rest parameter if present
        if (functionExpression.getRestParameter() != null) {
            int firstRestIndex = functionExpression.getParams().size();
            functionContext.emitter.emitOpcode(Opcode.REST);
            functionContext.emitter.emitU16(firstRestIndex);
            emitRestParameterBinding(functionExpression.getRestParameter(), functionContext, methodDelegates);
        }

        // Emit destructuring for pattern parameters after defaults and rest
        emitParameterDestructuring(functionExpression.getParams(), methodDestructuringParams, functionContext, methodDelegates);

        // If this is a generator method, emit INITIAL_YIELD at the start
        if (functionExpression.isGenerator()) {
            functionContext.emitter.emitOpcode(Opcode.INITIAL_YIELD);
        }

        // For constructors, initialize private methods then fields.
        // For derived constructors, defer until after super() (INIT_CTOR) per ES spec 14.6.13 step 11.
        if (isConstructor) {
            if (isDerivedConstructor) {
                functionContext.pendingPostSuperInitialization = () -> {
                    if (!privateInstanceMethodFunctions.isEmpty()) {
                        methodDelegates.functions.compilePrivateMethodInitialization(privateInstanceMethodFunctions, privateSymbols);
                    }
                    if (!instanceFields.isEmpty()) {
                        methodDelegates.functions.compileFieldInitialization(
                                instanceFields,
                                privateSymbols,
                                computedFieldSymbols,
                                autoAccessorBackingSymbols);
                    }
                };
            }
        }

        methodDelegates.analysis.hoistAllDeclarationsAsLocals(functionExpression.getBody().getBody());

        for (Statement stmt : functionExpression.getBody().getBody()) {
            if (stmt instanceof FunctionDeclaration functionDeclaration) {
                methodDelegates.functions.compileFunctionDeclaration(functionDeclaration);
            }
        }

        for (Statement stmt : functionExpression.getBody().getBody()) {
            if (stmt instanceof FunctionDeclaration) {
                continue;
            }
            methodDelegates.statements.compileStatement(stmt);
        }

        // If body doesn't end with return, add implicit return undefined
        List<Statement> bodyStatements = functionExpression.getBody().getBody();
        if (bodyStatements.isEmpty() || !(bodyStatements.get(bodyStatements.size() - 1) instanceof ReturnStatement)) {
            functionContext.emitter.emitOpcode(Opcode.UNDEFINED);
            int returnValueIndex = functionContext.currentScope().declareLocal("$method_return_" + functionContext.emitter.currentOffset());
            functionContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, returnValueIndex);
            methodDelegates.emitHelpers.emitCurrentScopeUsingDisposal();
            functionContext.emitter.emitOpcodeU16(Opcode.GET_LOC, returnValueIndex);
            functionContext.emitter.emitOpcode(functionExpression.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
        }

        int localCount = functionContext.currentScope().getLocalCount();
        String[] localVarNames = functionContext.getLocalVarNames();
        functionContext.exitScope();

        // Build the method bytecode
        Bytecode methodBytecode = functionContext.emitter.build(localCount, localVarNames);

        // Create JSBytecodeFunction for the method
        // Private symbols are accessed via PUSH_CONST (bytecode constants), not closureVars
        int definedArgCount = functionExpression.getFunctionParams().computeDefinedArgCount();

        // Extract method source code from original source for Function.prototype.toString
        String methodSource = compilerContext.extractSourceCode(functionExpression.getLocation());

        String functionName = isConstructor ? methodName : "";
        JSBytecodeFunction methodFunc = new JSBytecodeFunction(
                compilerContext.context,
                methodBytecode,
                functionName,
                definedArgCount,
                JSValue.NO_ARGS, // closureVars empty; private symbols use PUSH_CONST, captures use VarRefs
                null,            // prototype
                isConstructor,   // isConstructor - true for class constructors, false for methods
                functionExpression.isAsync(),
                functionExpression.isGenerator(),
                false,           // isArrow - methods are not arrow functions
                true,            // strict - classes are always strict mode
                methodSource     // source code for toString()
        );
        methodFunc.setHasParameterExpressions(functionExpression.getFunctionParams().hasNonSimpleParameters());

        // Set up capture source infos for outer variable closure capture
        delegates.emitHelpers.emitCapturedValues(methodCompiler, methodFunc);

        return methodFunc;
    }

    List<PrivateMethodEntry> compilePrivateMethodFunctions(
            List<MethodDefinition> privateMethods,
            Map<String, JSSymbol> privateSymbols,
            IdentityHashMap<PropertyDefinition, JSSymbol> computedFieldSymbols) {
        List<PrivateMethodEntry> privateMethodEntries = new ArrayList<>();
        for (MethodDefinition method : privateMethods) {
            String methodName = compilerContext.getMethodName(method);
            JSBytecodeFunction methodFunc = compileMethodAsFunction(
                    method,
                    methodName,
                    false,
                    List.of(),
                    privateSymbols,
                    computedFieldSymbols,
                    new IdentityHashMap<>(),
                    List.of(),
                    false
            );
            privateMethodEntries.add(new PrivateMethodEntry(methodName, methodFunc, method.getKind()));
        }
        return privateMethodEntries;
    }

    void compilePrivateMethodInitialization(
            List<PrivateMethodEntry> privateMethodEntries,
            Map<String, JSSymbol> privateSymbols) {
        for (PrivateMethodEntry entry : privateMethodEntries) {
            JSSymbol symbol = privateSymbols.get(entry.name());
            if (symbol == null) {
                throw new JSCompilerException("Private method symbol not found: #" + entry.name());
            }
            if (JSKeyword.GET.equals(entry.kind()) || JSKeyword.SET.equals(entry.kind())) {
                // Private getter/setter: use DEFINE_METHOD_COMPUTED with accessor flags
                // Stack: [this, symbol, func] → [this]
                int methodKind = JSKeyword.GET.equals(entry.kind()) ? 1 : 2;
                compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, entry.function());
                compilerContext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, methodKind);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            } else {
                // Regular private method: use DEFINE_METHOD_COMPUTED with non-writable flag (bit 3)
                // so that PUT_PRIVATE_FIELD can detect methods and throw TypeError on [[Set]]
                // Stack: [this, symbol, func] → [this]
                compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, entry.function());
                compilerContext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, 8);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            }
        }
    }

    /**
     * Compile a static block as a function.
     * Static blocks are executed immediately after class definition with the class constructor as 'this'.
     */
    JSBytecodeFunction compileStaticBlock(
            StaticBlock staticBlock,
            String className,
            Map<String, JSSymbol> privateSymbols) {
        // Pass parent captureResolver so static blocks can capture outer scope variables
        BytecodeCompiler blockCompiler = new BytecodeCompiler(true, compilerContext.captureResolver, compilerContext.context);
        CompilerContext blockCtx = blockCompiler.context();
        CompilerDelegates blockDelegates = blockCompiler.delegates();
        blockCtx.sourceCode = compilerContext.sourceCode;
        blockCtx.nonDeletableGlobalBindings.addAll(compilerContext.nonDeletableGlobalBindings);
        blockCtx.privateSymbols = privateSymbols;

        blockCtx.enterScope();
        blockCtx.inGlobalScope = false;

        // Force-capture the class inner name binding so eval() inside the
        // static block can resolve it at runtime.
        if (className != null && !className.isEmpty()) {
            blockCtx.captureResolver.resolveCapturedBindingIndex(className);
            blockCtx.classInnerNameToCapture = className;
        }

        // Compile all statements in the static block
        for (Statement stmt : staticBlock.getBody()) {
            blockDelegates.statements.compileStatement(stmt);
        }

        // Static blocks always return undefined
        blockCtx.emitter.emitOpcode(Opcode.UNDEFINED);
        int returnValueIndex = blockCtx.currentScope().declareLocal("$static_block_return_" + blockCtx.emitter.currentOffset());
        blockCtx.emitter.emitOpcodeU16(Opcode.PUT_LOC, returnValueIndex);
        blockDelegates.emitHelpers.emitCurrentScopeUsingDisposal();
        blockCtx.emitter.emitOpcodeU16(Opcode.GET_LOC, returnValueIndex);
        blockCtx.emitter.emitOpcode(Opcode.RETURN);

        int localCount = blockCtx.currentScope().getLocalCount();
        blockCtx.exitScope();

        Bytecode blockBytecode = blockCtx.emitter.build(localCount);

        JSBytecodeFunction blockFunc = new JSBytecodeFunction(
                compilerContext.context,
                blockBytecode,
                "<static initializer>",  // Static blocks are anonymous
                0,                        // no parameters
                JSValue.NO_ARGS,          // no closure vars
                null,                     // no prototype
                false,                    // not a constructor
                false,                    // not async
                false,                    // not generator
                false,                    // isArrow - static initializers are not arrows
                true,                     // strict mode
                "static { [initializer] }"
        );

        // Set up capture source infos for outer variable closure capture
        delegates.emitHelpers.emitCapturedValues(blockCompiler, blockFunc);

        return blockFunc;
    }

    /**
     * Compile a static field initializer as a function and return it.
     * The function is called with class constructor as `this`.
     */
    JSBytecodeFunction compileStaticFieldInitializer(
            PropertyDefinition field,
            IdentityHashMap<PropertyDefinition, JSSymbol> computedFieldSymbols,
            Map<String, JSSymbol> privateSymbols,
            IdentityHashMap<PropertyDefinition, JSSymbol> autoAccessorBackingSymbols,
            String className) {
        // Pass parent captureResolver so static field initializers can capture outer scope variables
        BytecodeCompiler initializerCompiler = new BytecodeCompiler(true, compilerContext.captureResolver, compilerContext.context);
        CompilerContext initCtx = initializerCompiler.context();
        CompilerDelegates initDelegates = initializerCompiler.delegates();
        initCtx.sourceCode = compilerContext.sourceCode;
        initCtx.nonDeletableGlobalBindings.addAll(compilerContext.nonDeletableGlobalBindings);
        initCtx.privateSymbols = privateSymbols;

        initCtx.enterScope();
        initCtx.inGlobalScope = false;

        // Force-capture the class inner name binding so eval() inside the
        // initializer can resolve it at runtime (QuickJS: resolve_scope_var
        // walks the parent scope chain for eval closures).
        if (className != null && !className.isEmpty()) {
            initCtx.captureResolver.resolveCapturedBindingIndex(className);
            initCtx.classInnerNameToCapture = className;
        }

        // Stack: this
        initCtx.emitter.emitOpcode(Opcode.PUSH_THIS);

        if (field.isPrivate()) {
            if (!(field.getKey() instanceof PrivateIdentifier privateId)) {
                throw new JSCompilerException("Invalid static private field key");
            }

            JSSymbol symbol = privateSymbols.get(privateId.getName());
            if (symbol == null) {
                throw new JSCompilerException("Static private field symbol not found: #" + privateId.getName());
            }

            if (field.getValue() != null) {
                initDelegates.expressions.compileExpression(field.getValue());
            } else {
                initCtx.emitter.emitOpcode(Opcode.UNDEFINED);
            }

            // Stack: this value symbol -> this symbol value
            initCtx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
            initCtx.emitter.emitOpcode(Opcode.SWAP);
            // Set function name from private symbol (e.g., "#field") for anonymous functions
            if (field.getValue() != null && field.getValue().isAnonymousFunction()) {
                initCtx.emitter.emitOpcode(Opcode.SET_NAME_COMPUTED);
            }
            initCtx.emitter.emitOpcode(Opcode.DEFINE_PRIVATE_FIELD);
        } else if (field.isAutoAccessor()) {
            JSSymbol backingSymbol = autoAccessorBackingSymbols.get(field);
            if (backingSymbol == null) {
                throw new JSCompilerException("Auto-accessor static backing symbol not found");
            }

            if (field.getValue() != null) {
                initDelegates.expressions.compileExpression(field.getValue());
            } else {
                initCtx.emitter.emitOpcode(Opcode.UNDEFINED);
            }

            initCtx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, backingSymbol);
            initCtx.emitter.emitOpcode(Opcode.SWAP);
            if (field.getValue() != null && field.getValue().isAnonymousFunction()) {
                initCtx.emitter.emitOpcode(Opcode.SET_NAME_COMPUTED);
            }
            initCtx.emitter.emitOpcode(Opcode.DEFINE_PRIVATE_FIELD);
        } else {
            if (field.isComputed()) {
                JSSymbol computedFieldSymbol = computedFieldSymbols.get(field);
                if (computedFieldSymbol == null) {
                    throw new JSCompilerException("Computed static field key not found");
                }
                // Load precomputed key from constructor hidden storage:
                // this this hiddenSymbol -> this key
                initCtx.emitter.emitOpcode(Opcode.PUSH_THIS);
                initCtx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, computedFieldSymbol);
                initCtx.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else {
                initDelegates.emitHelpers.emitNonComputedPublicFieldKey(field.getKey());
            }

            if (field.getValue() != null) {
                initDelegates.expressions.compileExpression(field.getValue());
            } else {
                initCtx.emitter.emitOpcode(Opcode.UNDEFINED);
            }

            // Stack: this key value -> this
            initCtx.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, 4);
            initCtx.emitter.emitOpcode(Opcode.DROP);
        }
        initCtx.emitter.emitOpcode(Opcode.UNDEFINED);
        initCtx.emitter.emitOpcode(Opcode.RETURN);

        int localCount = initCtx.currentScope().getLocalCount();
        initCtx.exitScope();
        Bytecode initializerBytecode = initCtx.emitter.build(localCount);

        JSBytecodeFunction initFunc = new JSBytecodeFunction(
                compilerContext.context,
                initializerBytecode,
                "<static field initializer>",
                0,
                JSValue.NO_ARGS,
                null,
                false,
                false,
                false,
                false,
                true,
                "static field initializer for " + className
        );

        // Set up capture source infos for outer variable closure capture
        delegates.emitHelpers.emitCapturedValues(initializerCompiler, initFunc);

        return initFunc;
    }

    /**
     * Create a default constructor for a class.
     */
    JSBytecodeFunction createDefaultConstructor(
            String className,
            boolean hasSuper,
            List<PropertyDefinition> instanceFields,
            Map<String, JSSymbol> privateSymbols,
            IdentityHashMap<PropertyDefinition, JSSymbol> computedFieldSymbols,
            IdentityHashMap<PropertyDefinition, JSSymbol> autoAccessorBackingSymbols,
            List<PrivateMethodEntry> privateInstanceMethodFunctions) {
        // Pass parent captureResolver so default constructors can capture outer scope variables
        BytecodeCompiler constructorCompiler = new BytecodeCompiler(true, compilerContext.captureResolver, compilerContext.context);
        CompilerContext ctorCtx = constructorCompiler.context();
        CompilerDelegates ctorDelegates = constructorCompiler.delegates();
        ctorCtx.sourceCode = compilerContext.sourceCode;
        ctorCtx.nonDeletableGlobalBindings.addAll(compilerContext.nonDeletableGlobalBindings);
        ctorCtx.privateSymbols = privateSymbols;  // Make private symbols available

        ctorCtx.enterScope();
        ctorCtx.inGlobalScope = false;

        // Force-capture the class inner name binding so eval() inside the
        // default constructor can resolve it at runtime.
        if (className != null && !className.isEmpty()) {
            ctorCtx.captureResolver.resolveCapturedBindingIndex(className);
            ctorCtx.classInnerNameToCapture = className;
        }

        // Default derived constructor semantics:
        // constructor(...args) { super(...args); }
        // OP_init_ctor performs the super constructor call using current args/new.target.
        if (hasSuper) {
            ctorCtx.emitter.emitOpcode(Opcode.INIT_CTOR);
            ctorCtx.emitter.emitOpcode(Opcode.DROP);
        }

        // Initialize private methods and instance fields.
        // For derived constructors this runs after super() returns.
        if (!privateInstanceMethodFunctions.isEmpty()) {
            ctorDelegates.functions.compilePrivateMethodInitialization(privateInstanceMethodFunctions, privateSymbols);
        }
        if (!instanceFields.isEmpty()) {
            ctorDelegates.functions.compileFieldInitialization(
                    instanceFields,
                    privateSymbols,
                    computedFieldSymbols,
                    autoAccessorBackingSymbols);
        }

        ctorCtx.emitter.emitOpcode(hasSuper ? Opcode.PUSH_THIS : Opcode.UNDEFINED);
        ctorCtx.emitter.emitOpcode(Opcode.RETURN);

        int localCount = ctorCtx.currentScope().getLocalCount();
        ctorCtx.exitScope();

        Bytecode constructorBytecode = ctorCtx.emitter.build(localCount);

        JSBytecodeFunction ctorFunc = new JSBytecodeFunction(
                compilerContext.context,
                constructorBytecode,
                className,
                0,               // no parameters
                JSValue.NO_ARGS, // no closure vars
                null,            // prototype will be set by VM
                true,            // isConstructor
                false,           // not async
                false,           // not generator
                false,           // isArrow - constructors are not arrows
                true,            // strict mode
                "constructor() { [default] }"
        );

        // Set up capture source infos for outer variable closure capture
        delegates.emitHelpers.emitCapturedValues(constructorCompiler, ctorFunc);

        return ctorFunc;
    }

    JSArray createTaggedTemplateObject(TemplateLiteral template) {
        List<String> cookedQuasis = template.getQuasis();
        List<String> rawQuasis = template.getRawQuasis();
        int segmentCount = rawQuasis.size();

        JSArray templateObject = new JSArray(compilerContext.context);
        JSArray rawArray = new JSArray(compilerContext.context);

        for (int i = 0; i < segmentCount; i++) {
            JSString rawValue = new JSString(rawQuasis.get(i));
            rawArray.set(i, rawValue);
            rawArray.defineProperty(
                    PropertyKey.fromIndex(i),
                    PropertyDescriptor.dataDescriptor(rawValue, PropertyDescriptor.DataState.Enumerable));

            String cookedQuasi = cookedQuasis.get(i);
            JSValue cookedValue = cookedQuasi == null ? JSUndefined.INSTANCE : new JSString(cookedQuasi);
            templateObject.set(i, cookedValue);
            templateObject.defineProperty(
                    PropertyKey.fromIndex(i),
                    PropertyDescriptor.dataDescriptor(cookedValue, PropertyDescriptor.DataState.Enumerable));
        }

        // QuickJS/spec attributes for template objects.
        rawArray.defineProperty(PropertyKey.fromString("length"), JSNumber.of(segmentCount), PropertyDescriptor.DataState.None);
        templateObject.defineProperty(PropertyKey.fromString("length"), JSNumber.of(segmentCount), PropertyDescriptor.DataState.None);
        templateObject.defineProperty(PropertyKey.fromString("raw"), rawArray, PropertyDescriptor.DataState.None);

        rawArray.freeze();
        templateObject.freeze();
        return templateObject;
    }

    private void declareAndInitializeImplicitArgumentsBinding(CompilerContext functionContext) {
        if (functionContext.inGlobalScope || functionContext.isInArrowFunction) {
            return;
        }
        if (functionContext.currentScope().getLocal(JSArguments.NAME) != null) {
            return;
        }

        int argumentsLocalIndex = functionContext.currentScope().declareLocal(JSArguments.NAME);
        functionContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
        functionContext.emitter.emitU8(0);
        functionContext.emitter.emitOpcode(Opcode.PUT_LOC);
        functionContext.emitter.emitU16(argumentsLocalIndex);
    }

    /**
     * Declare function parameters and emit destructuring for pattern params.
     * For Identifier params, declares as a named parameter slot.
     * For destructuring Pattern params (ObjectPattern, ArrayPattern), declares
     * a synthetic parameter slot and emits destructuring code after defaults/rest.
     *
     * @return list of (index, pattern) pairs for destructuring params that need
     * post-processing after default/rest initialization
     */
    private List<int[]> declareParameters(
            List<Pattern> params,
            CompilerContext functionContext,
            CompilerDelegates funcDelegates,
            List<Integer> parameterSlotIndexes) {
        List<int[]> destructuringParams = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            Pattern param = params.get(i);
            if (param instanceof Identifier id) {
                int slotIndex = functionContext.currentScope().declareParameter(id.getName());
                parameterSlotIndexes.add(slotIndex);
            } else {
                // Destructuring parameter: declare a synthetic slot for the argument
                int slotIndex = functionContext.currentScope().declareParameter("$param_" + i);
                parameterSlotIndexes.add(slotIndex);
                // Declare local variables for all bound names in the pattern
                funcDelegates.patterns.declarePatternVariables(param);
                destructuringParams.add(new int[]{slotIndex, i});
            }
        }
        return destructuringParams;
    }

    private void emitParameterDestructuring(List<Pattern> params, List<int[]> destructuringParams,
                                            CompilerContext functionContext,
                                            CompilerDelegates funcDelegates) {
        for (int[] entry : destructuringParams) {
            int slotIndex = entry[0];
            int paramIndex = entry[1];
            Pattern pattern = params.get(paramIndex);
            // Push the argument value onto the stack
            functionContext.emitter.emitOpcodeU16(Opcode.GET_LOC, slotIndex);
            // Destructure and assign to local variables
            funcDelegates.patterns.compilePatternAssignment(pattern);
        }
    }

    /**
     * Emit destructuring code for pattern parameters after defaults and rest handling.
     * Reads the argument value from the synthetic parameter slot and destructures it.
     */
    private void emitRestParameterBinding(RestParameter restParameter,
                                          CompilerContext functionContext,
                                          CompilerDelegates funcDelegates) {
        if (restParameter.getArgument() instanceof Identifier restId) {
            // Simple rest: ...args → declare local and store
            String restParamName = restId.getName();
            int restLocalIndex = functionContext.currentScope().declareLocal(restParamName);
            functionContext.emitter.emitOpcode(Opcode.PUT_LOC);
            functionContext.emitter.emitU16(restLocalIndex);
        } else {
            // Destructured rest: ...[a, b] or ...{a, b} → compile pattern assignment
            funcDelegates.patterns.compilePatternAssignment(restParameter.getArgument());
        }
    }

    private void inheritClassInnerNameCapture(CompilerContext targetContext) {
        String classInnerName = compilerContext.classInnerNameToCapture;
        if (classInnerName != null) {
            targetContext.classInnerNameToCapture = classInnerName;
            targetContext.captureResolver.resolveCapturedBindingIndex(classInnerName);
        }
    }

    private void inheritVisibleWithObjectBindings(CompilerContext functionContext) {
        functionContext.inheritedWithObjectBindingNames.addAll(
                compilerContext.getVisibleWithObjectBindingNamesForNestedFunction());
    }

    void installPrivateStaticMethods(
            List<PrivateMethodEntry> privateStaticMethodEntries,
            Map<String, JSSymbol> privateSymbols) {
        for (PrivateMethodEntry entry : privateStaticMethodEntries) {
            JSSymbol symbol = privateSymbols.get(entry.name());
            if (symbol == null) {
                throw new JSCompilerException("Private static method symbol not found: #" + entry.name());
            }

            if (JSKeyword.GET.equals(entry.kind()) || JSKeyword.SET.equals(entry.kind())) {
                // Private static getter/setter: use DEFINE_METHOD_COMPUTED with accessor flags
                int methodKind = JSKeyword.GET.equals(entry.kind()) ? 1 : 2;
                // Stack before: constructor proto
                compilerContext.emitter.emitOpcode(Opcode.SWAP); // proto constructor
                compilerContext.emitter.emitOpcode(Opcode.DUP);  // proto constructor constructor
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol); // proto constructor constructor symbol
                compilerContext.emitter.emitOpcodeConstant(Opcode.FCLOSURE, entry.function()); // proto constructor constructor symbol method
                compilerContext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, methodKind); // proto constructor constructor
                compilerContext.emitter.emitOpcode(Opcode.DROP); // proto constructor
                compilerContext.emitter.emitOpcode(Opcode.SWAP); // constructor proto
            } else {
                // Regular private static method: use DEFINE_METHOD_COMPUTED with non-writable flag
                // Stack before: constructor proto
                compilerContext.emitter.emitOpcode(Opcode.SWAP); // proto constructor
                compilerContext.emitter.emitOpcode(Opcode.DUP);  // proto constructor constructor
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol); // proto constructor constructor symbol
                compilerContext.emitter.emitOpcodeConstant(Opcode.FCLOSURE, entry.function()); // proto constructor constructor symbol method
                compilerContext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, 8); // proto constructor constructor
                compilerContext.emitter.emitOpcode(Opcode.DROP); // proto constructor
                compilerContext.emitter.emitOpcode(Opcode.SWAP); // constructor proto
            }
        }
    }

    void registerPrivateName(Map<String, String> privateNameKinds, String privateName, String kind) {
        String existingKind = privateNameKinds.get(privateName);
        if (existingKind == null) {
            privateNameKinds.put(privateName, kind);
            return;
        }
        boolean isGetterSetterPair =
                (JSKeyword.GET.equals(existingKind) && JSKeyword.SET.equals(kind))
                        || (JSKeyword.SET.equals(existingKind) && JSKeyword.GET.equals(kind));
        if (isGetterSetterPair) {
            privateNameKinds.put(privateName, "accessor");
            return;
        }
        throw new JSCompilerException("private class field is already defined");
    }

    record PrivateMethodEntry(String name, JSBytecodeFunction function, String kind) {
    }
}
