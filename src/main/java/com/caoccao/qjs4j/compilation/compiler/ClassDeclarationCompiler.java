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
import com.caoccao.qjs4j.core.JSBytecodeFunction;
import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Bytecode;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.*;

/**
 * Handles compilation of class declarations, class expressions, and related constructs (methods, fields, static blocks,
 * private members, constructors).
 */
final class ClassDeclarationCompiler extends AstNodeCompiler<ClassDeclaration> {
    ClassDeclarationCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(ClassDeclaration classDecl) {
        compilerContext.classExpressionCompiler.compile(new ClassExpression(classDecl.getId(),
                classDecl.getSuperClass(), classDecl.getBody(), classDecl.getLocation()));

        // Store the class constructor in a variable
        if (classDecl.getId() != null) {
            String varName = classDecl.getId().getName();
            if (!compilerContext.inGlobalScope) {
                compilerContext.scopeManager.currentScope().declareLocal(varName);
                Integer localIndex = compilerContext.scopeManager.currentScope().getLocal(varName);
                if (localIndex != null) {
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
                }
            } else if (compilerContext.tdzLocals.contains(varName)) {
                // TDZ local: class was pre-declared as a local for TDZ enforcement
                Integer localIndex = compilerContext.scopeManager.findLocalInScopes(varName);
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

    /**
     * Evaluate and cache a computed class field name on the constructor object once. Expects stack before/after to be:
     * constructor proto.
     */
    void compileComputedFieldNameCache(PropertyDefinition field,
            IdentityHashMap<PropertyDefinition, JSSymbol> computedFieldSymbols, Map<String, JSSymbol> privateSymbols) {
        JSSymbol computedFieldSymbol = computedFieldSymbols.get(field);
        if (computedFieldSymbol == null) {
            throw new JSCompilerException("Computed field key symbol not found", field);
        }

        compilerContext.pushState();
        compilerContext.privateSymbols = privateSymbols;

        // Stack: ctor proto -> proto ctor -> proto ctor symbol key -> proto ctor -> ctor proto.
        // DEFINE_METHOD_COMPUTED keeps its object, so the constructor is not duplicated first: a copy would
        // outlive the class expression and displace the receiver and arguments of a call it is passed to.
        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, computedFieldSymbol);
        try {
            if (compilerContext.inClassBody) {
                compilerContext.functionExpressionCompiler.emitStrictClassBodyExpression(field.getKey());
            } else {
                compilerContext.expressionCompiler.compile(field.getKey());
            }
            compilerContext.emitter.emitOpcode(Opcode.TO_PROPKEY);
            compilerContext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, 4);
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
        } finally {
            compilerContext.popState();
        }
    }

    /**
     * Compile field initialization code for instance fields. Emits code to set each field on 'this' with its
     * initializer value. For private fields, uses the symbol from privateSymbols map.
     */
    private void compileFieldInitialization(List<PropertyDefinition> fields, Map<String, JSSymbol> privateSymbols,
            IdentityHashMap<PropertyDefinition, JSSymbol> computedFieldSymbols,
            IdentityHashMap<PropertyDefinition, JSSymbol> autoAccessorBackingSymbols) {
        for (PropertyDefinition field : fields) {
            boolean isPrivate = field.isPrivate();

            compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);

            // Track class field initializer context for ContainsArguments check in eval
            compilerContext.pushState();
            compilerContext.inClassFieldInitializer = true;

            if (isPrivate) {
                if (!(field.getKey() instanceof PrivateIdentifier privateId)) {
                    throw new JSCompilerException("Invalid private field key", field);
                }
                String fieldName = privateId.getName();

                if (field.getValue() != null) {
                    compilerContext.expressionCompiler.compile(field.getValue());
                } else {
                    compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
                }

                JSSymbol symbol = privateSymbols.get(fieldName);
                if (symbol != null) {
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                    compilerContext.emitter.emitOpcode(Opcode.SWAP);
                    if (field.getValue() != null && field.getValue().isAnonymousFunction()) {
                        compilerContext.emitter.emitOpcode(Opcode.SET_NAME_COMPUTED);
                    }
                    compilerContext.emitter.emitOpcode(Opcode.DEFINE_PRIVATE_FIELD);
                } else {
                    compilerContext.emitter.emitOpcode(Opcode.DROP);
                    compilerContext.emitter.emitOpcode(Opcode.DROP);
                    continue;
                }
            } else if (field.isAutoAccessor()) {
                JSSymbol backingSymbol = autoAccessorBackingSymbols.get(field);
                if (backingSymbol == null) {
                    throw new JSCompilerException("Auto-accessor backing symbol not found", field);
                }

                if (field.getValue() != null) {
                    compilerContext.expressionCompiler.compile(field.getValue());
                } else {
                    compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
                }

                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, backingSymbol);
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
                if (field.getValue() != null && field.getValue().isAnonymousFunction()) {
                    compilerContext.emitter.emitOpcode(Opcode.SET_NAME_COMPUTED);
                }
                compilerContext.emitter.emitOpcode(Opcode.DEFINE_PRIVATE_FIELD);
            } else {
                if (field.isComputed()) {
                    JSSymbol computedFieldSymbol = computedFieldSymbols.get(field);
                    if (computedFieldSymbol == null) {
                        throw new JSCompilerException("Computed field key not found", field);
                    }
                    compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                    compilerContext.emitter.emitU8(2);
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, computedFieldSymbol);
                    compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                } else {
                    compilerContext.emitHelpers.emitNonComputedPublicFieldKey(field.getKey());
                }

                if (field.getValue() != null) {
                    compilerContext.expressionCompiler.compile(field.getValue());
                } else {
                    compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
                }

                compilerContext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, 4);
            }

            compilerContext.popState();
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }
    }

    /**
     * Compile a method definition as a function. For constructors, instanceFields contains fields to initialize.
     * privateSymbols contains JSSymbol instances for private fields (passed as closure variables).
     */
    JSBytecodeFunction compileMethodAsFunction(MethodDefinition method, String methodName, boolean isDerivedConstructor,
            List<PropertyDefinition> instanceFields, Map<String, JSSymbol> privateSymbols,
            IdentityHashMap<PropertyDefinition, JSSymbol> computedFieldSymbols,
            IdentityHashMap<PropertyDefinition, JSSymbol> autoAccessorBackingSymbols,
            List<PrivateMethodEntry> privateInstanceMethodFunctions, boolean isConstructor) {
        // Pass parent captureResolver so class methods can capture outer scope variables (closures)
        BytecodeCompiler methodCompiler = compilerContext.newNestedCompiler(true, privateSymbols);
        CompilerContext functionContext = methodCompiler.context();
        compilerContext.functionExpressionCompiler.inheritVisibleWithObjectBindings(functionContext);

        FunctionExpression functionExpression = method.getValue();

        // Enter function scope and add parameters as locals
        functionContext.enterFunctionScope(functionExpression.isAsync(), functionExpression.isGenerator());

        // Force-capture the class inner name binding so eval() inside
        // constructor/method body can resolve it at runtime.
        if (isConstructor && methodName != null && !methodName.isEmpty()) {
            functionContext.captureResolver.resolveCapturedBindingIndex(methodName);
            // Propagate to nested functions (arrows, etc.) so they also capture the name.
            functionContext.classInnerNameToCapture = methodName;
        }

        List<Integer> parameterSlotIndexes = new ArrayList<>();
        List<int[]> methodDestructuringParams = compilerContext.functionExpressionCompiler
                .declareParameters(functionExpression.getParams(), functionContext, parameterSlotIndexes);
        if (method.getValue().needsArguments()) {
            compilerContext.functionExpressionCompiler.declareAndInitializeImplicitArgumentsBinding(functionContext);
        }

        // For base constructors, instance private methods/fields are initialized before
        // parameter default evaluation so defaults can access private members on `this`.
        if (isConstructor && !isDerivedConstructor) {
            if (!privateInstanceMethodFunctions.isEmpty()) {
                functionContext.classDeclarationCompiler
                        .compilePrivateMethodInitialization(privateInstanceMethodFunctions, privateSymbols);
            }
            if (!instanceFields.isEmpty()) {
                functionContext.classDeclarationCompiler.compileFieldInitialization(instanceFields, privateSymbols,
                        computedFieldSymbols, autoAccessorBackingSymbols);
            }
        }

        compilerContext.functionExpressionCompiler.initializeParameters(methodCompiler, functionExpression,
                parameterSlotIndexes, methodDestructuringParams, functionExpression.isGenerator());

        // For derived constructors, defer until after super() (INIT_CTOR) per ES spec 14.6.13 step 11.
        if (isConstructor) {
            if (isDerivedConstructor) {
                functionContext.pendingPostSuperInitialization = () -> {
                    if (!privateInstanceMethodFunctions.isEmpty()) {
                        functionContext.classDeclarationCompiler
                                .compilePrivateMethodInitialization(privateInstanceMethodFunctions, privateSymbols);
                    }
                    if (!instanceFields.isEmpty()) {
                        functionContext.classDeclarationCompiler.compileFieldInitialization(instanceFields,
                                privateSymbols, computedFieldSymbols, autoAccessorBackingSymbols);
                    }
                };
            }
        }

        FunctionBodyCompiler.METHOD.compile(functionContext, functionExpression, functionExpression.getBody(),
                functionExpression.isAsync());

        Bytecode methodBytecode = functionContext.finishFunctionScope();

        // Create JSBytecodeFunction for the method
        // Private symbols are accessed via PUSH_CONST (bytecode constants), not closureVars
        int definedArgCount = functionExpression.getFunctionParams().computeDefinedArgCount();

        // Extract method source code from original source for Function.prototype.toString
        String methodSource = compilerContext.extractSourceCode(functionExpression.getLocation());

        String functionName = isConstructor ? methodName : "";
        JSBytecodeFunction methodFunc = new JSBytecodeFunction(compilerContext.context, methodBytecode, functionName,
                definedArgCount, JSValue.NO_ARGS, // closureVars empty; private symbols use PUSH_CONST, captures use
                                                  // VarRefs
                null, // prototype
                isConstructor, // isConstructor - true for class constructors, false for methods
                functionExpression.isAsync(), functionExpression.isGenerator(), false, // isArrow - methods are not
                                                                                       // arrow functions
                true, // strict - classes are always strict mode
                methodSource // source code for toString()
        );
        methodFunc.setHasParameterExpressions(functionExpression.getFunctionParams().hasNonSimpleParameters());

        // Set up capture source infos for outer variable closure capture
        compilerContext.emitHelpers.emitCapturedValues(methodCompiler, methodFunc);

        return methodFunc;
    }

    List<PrivateMethodEntry> compilePrivateMethodFunctions(List<MethodDefinition> privateMethods,
            Map<String, JSSymbol> privateSymbols, IdentityHashMap<PropertyDefinition, JSSymbol> computedFieldSymbols) {
        List<PrivateMethodEntry> privateMethodEntries = new ArrayList<>();
        for (MethodDefinition method : privateMethods) {
            String methodName = method.getSimpleName();
            JSBytecodeFunction methodFunc = compileMethodAsFunction(method, methodName, false, List.of(),
                    privateSymbols, computedFieldSymbols, new IdentityHashMap<>(), List.of(), false);
            String bindingName = "#private_method_" + method.getLocation().offset();
            if (!method.isStatic()) {
                compilerContext.scopeManager.currentScope().declareConstLocal(bindingName);
            }
            privateMethodEntries
                    .add(new PrivateMethodEntry(methodName, methodFunc, method.getKind(), method, bindingName));
        }
        return privateMethodEntries;
    }

    void compilePrivateMethodInitialization(List<PrivateMethodEntry> privateMethodEntries,
            Map<String, JSSymbol> privateSymbols) {
        for (PrivateMethodEntry entry : privateMethodEntries) {
            JSSymbol symbol = privateSymbols.get(entry.name());
            if (symbol == null) {
                throw new JSCompilerException("Private method symbol not found: #" + entry.name(), entry.ast());
            }
            if (JSKeyword.GET.equals(entry.kind()) || JSKeyword.SET.equals(entry.kind())) {
                // Private getter/setter: use DEFINE_METHOD_COMPUTED with accessor flags
                // Stack: [this, symbol, func] → [this]
                int methodKind = JSKeyword.GET.equals(entry.kind()) ? 1 : 2;
                compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                int capture = compilerContext.captureResolver.resolveCapturedBindingIndex(entry.bindingName());
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF, capture);
                compilerContext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, methodKind);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            } else {
                // Regular private method: use DEFINE_METHOD_COMPUTED with non-writable flag (bit 3)
                // so that PUT_PRIVATE_FIELD can detect methods and throw TypeError on [[Set]]
                // Stack: [this, symbol, func] → [this]
                compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                int capture = compilerContext.captureResolver.resolveCapturedBindingIndex(entry.bindingName());
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF, capture);
                compilerContext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, 8);
                compilerContext.emitter.emitOpcode(Opcode.DROP);
            }
        }
    }

    /**
     * Compile a static block as a function. Static blocks are executed immediately after class definition with the
     * class constructor as 'this'.
     */
    JSBytecodeFunction compileStaticBlock(StaticBlock staticBlock, String className,
            Map<String, JSSymbol> privateSymbols) {
        // Pass parent captureResolver so static blocks can capture outer scope variables
        BytecodeCompiler blockCompiler = compilerContext.newNestedCompiler(true, privateSymbols);
        CompilerContext blockCtx = blockCompiler.context();

        blockCtx.scopeManager.enterScope();
        blockCtx.inGlobalScope = false;

        // Force-capture the class inner name binding so eval() inside the
        // static block can resolve it at runtime.
        if (className != null && !className.isEmpty()) {
            blockCtx.captureResolver.resolveCapturedBindingIndex(className);
            blockCtx.classInnerNameToCapture = className;
        }

        // Compile all statements in the static block
        for (Statement stmt : staticBlock.getBody()) {
            blockCtx.statementCompiler.compile(stmt);
        }

        // Static blocks always return undefined
        blockCtx.emitter.emitOpcode(Opcode.UNDEFINED);
        int returnValueIndex = blockCtx.scopeManager.currentScope()
                .declareLocal("#static_block_return_" + blockCtx.emitter.currentOffset());
        blockCtx.emitter.emitOpcodeU16(Opcode.PUT_LOC, returnValueIndex);
        blockCtx.emitHelpers.emitCurrentScopeUsingDisposal();
        blockCtx.emitter.emitOpcodeU16(Opcode.GET_LOC, returnValueIndex);
        blockCtx.emitter.emitOpcode(Opcode.RETURN);

        int localCount = blockCtx.scopeManager.currentScope().getLocalCount();
        blockCtx.scopeManager.exitScope();

        Bytecode blockBytecode = blockCtx.emitter.build(localCount);

        JSBytecodeFunction blockFunc = new JSBytecodeFunction(compilerContext.context, blockBytecode,
                "<static initializer>", // Static blocks are anonymous
                0, // no parameters
                JSValue.NO_ARGS, // no closure vars
                null, // no prototype
                false, // not a constructor
                false, // not async
                false, // not generator
                false, // isArrow - static initializers are not arrows
                true, // strict mode
                "static { [initializer] }");

        // Set up capture source infos for outer variable closure capture
        compilerContext.emitHelpers.emitCapturedValues(blockCompiler, blockFunc);

        return blockFunc;
    }

    /**
     * Compile a static field initializer as a function and return it. The function is called with class constructor as
     * `this`.
     */
    JSBytecodeFunction compileStaticFieldInitializer(PropertyDefinition field,
            IdentityHashMap<PropertyDefinition, JSSymbol> computedFieldSymbols, Map<String, JSSymbol> privateSymbols,
            IdentityHashMap<PropertyDefinition, JSSymbol> autoAccessorBackingSymbols, String className) {
        // Pass parent captureResolver so static field initializers can capture outer scope variables
        BytecodeCompiler initializerCompiler = compilerContext.newNestedCompiler(true, privateSymbols);
        CompilerContext initCtx = initializerCompiler.context();

        initCtx.scopeManager.enterScope();
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
                throw new JSCompilerException("Invalid static private field key", field);
            }

            JSSymbol symbol = privateSymbols.get(privateId.getName());
            if (symbol == null) {
                throw new JSCompilerException("Static private field symbol not found: #" + privateId.getName(),
                        privateId);
            }

            if (field.getValue() != null) {
                initCtx.expressionCompiler.compile(field.getValue());
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
                throw new JSCompilerException("Auto-accessor static backing symbol not found", field);
            }

            if (field.getValue() != null) {
                initCtx.expressionCompiler.compile(field.getValue());
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
                    throw new JSCompilerException("Computed static field key not found", field);
                }
                // Load precomputed key from constructor hidden storage:
                // this this hiddenSymbol -> this key
                initCtx.emitter.emitOpcode(Opcode.PUSH_THIS);
                initCtx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, computedFieldSymbol);
                initCtx.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else {
                initCtx.emitHelpers.emitNonComputedPublicFieldKey(field.getKey());
            }

            if (field.getValue() != null) {
                initCtx.expressionCompiler.compile(field.getValue());
            } else {
                initCtx.emitter.emitOpcode(Opcode.UNDEFINED);
            }

            // Stack: this key value -> this
            initCtx.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, 4);
            initCtx.emitter.emitOpcode(Opcode.DROP);
        }
        initCtx.emitter.emitOpcode(Opcode.UNDEFINED);
        initCtx.emitter.emitOpcode(Opcode.RETURN);

        int localCount = initCtx.scopeManager.currentScope().getLocalCount();
        initCtx.scopeManager.exitScope();
        Bytecode initializerBytecode = initCtx.emitter.build(localCount);

        JSBytecodeFunction initFunc = new JSBytecodeFunction(compilerContext.context, initializerBytecode,
                "<static field initializer>", 0, JSValue.NO_ARGS, null, false, false, false, false, true,
                "static field initializer for " + className);

        // Set up capture source infos for outer variable closure capture
        compilerContext.emitHelpers.emitCapturedValues(initializerCompiler, initFunc);

        return initFunc;
    }

    /**
     * Create a default constructor for a class.
     */
    JSBytecodeFunction createDefaultConstructor(String className, boolean hasSuper,
            List<PropertyDefinition> instanceFields, Map<String, JSSymbol> privateSymbols,
            IdentityHashMap<PropertyDefinition, JSSymbol> computedFieldSymbols,
            IdentityHashMap<PropertyDefinition, JSSymbol> autoAccessorBackingSymbols,
            List<PrivateMethodEntry> privateInstanceMethodFunctions, SourceLocation classLocation) {
        // Pass parent captureResolver so default constructors can capture outer scope variables
        BytecodeCompiler constructorCompiler = compilerContext.newNestedCompiler(true, privateSymbols);
        CompilerContext ctorCtx = constructorCompiler.context();

        ctorCtx.scopeManager.enterScope();
        ctorCtx.inGlobalScope = false;

        // Force-capture the class inner name binding so eval() inside the
        // default constructor can resolve it at runtime.
        if (className != null && !className.isEmpty()) {
            ctorCtx.captureResolver.resolveCapturedBindingIndex(className);
            ctorCtx.classInnerNameToCapture = className;
        }

        // A frame in the implicit constructor reports the position of its class, as V8 does.
        ctorCtx.emitter.emitSourcePosition(classLocation);

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
            ctorCtx.classDeclarationCompiler.compilePrivateMethodInitialization(privateInstanceMethodFunctions,
                    privateSymbols);
        }
        if (!instanceFields.isEmpty()) {
            ctorCtx.classDeclarationCompiler.compileFieldInitialization(instanceFields, privateSymbols,
                    computedFieldSymbols, autoAccessorBackingSymbols);
        }

        ctorCtx.emitter.emitOpcode(hasSuper ? Opcode.PUSH_THIS : Opcode.UNDEFINED);
        ctorCtx.emitter.emitOpcode(Opcode.RETURN);

        int localCount = ctorCtx.scopeManager.currentScope().getLocalCount();
        ctorCtx.scopeManager.exitScope();

        Bytecode constructorBytecode = ctorCtx.emitter.build(localCount);

        JSBytecodeFunction ctorFunc = new JSBytecodeFunction(compilerContext.context, constructorBytecode, className, 0, // no
                                                                                                                         // parameters
                JSValue.NO_ARGS, // no closure vars
                null, // prototype will be set by VM
                true, // isConstructor
                false, // not async
                false, // not generator
                false, // isArrow - constructors are not arrows
                true, // strict mode
                "constructor() { [default] }");

        // Set up capture source infos for outer variable closure capture
        compilerContext.emitHelpers.emitCapturedValues(constructorCompiler, ctorFunc);

        return ctorFunc;
    }

    void installPrivateStaticMethods(List<PrivateMethodEntry> privateStaticMethodEntries,
            Map<String, JSSymbol> privateSymbols) {
        for (PrivateMethodEntry entry : privateStaticMethodEntries) {
            JSSymbol symbol = privateSymbols.get(entry.name());
            if (symbol == null) {
                throw new JSCompilerException("Private static method symbol not found: #" + entry.name(), entry.ast());
            }

            if (JSKeyword.GET.equals(entry.kind()) || JSKeyword.SET.equals(entry.kind())) {
                // Private static getter/setter: use DEFINE_METHOD_COMPUTED with accessor flags
                int methodKind = JSKeyword.GET.equals(entry.kind()) ? 1 : 2;
                // Stack before: constructor proto
                compilerContext.emitter.emitOpcode(Opcode.SWAP); // proto constructor
                compilerContext.emitter.emitOpcode(Opcode.DUP); // proto constructor constructor
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol); // proto constructor constructor
                                                                                       // symbol
                compilerContext.emitter.emitOpcodeConstant(Opcode.FCLOSURE, entry.function()); // proto constructor
                                                                                               // constructor symbol
                                                                                               // method
                compilerContext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, methodKind); // proto constructor
                                                                                                 // constructor
                compilerContext.emitter.emitOpcode(Opcode.DROP); // proto constructor
                compilerContext.emitter.emitOpcode(Opcode.SWAP); // constructor proto
            } else {
                // Regular private static method: use DEFINE_METHOD_COMPUTED with non-writable flag
                // Stack before: constructor proto
                compilerContext.emitter.emitOpcode(Opcode.SWAP); // proto constructor
                compilerContext.emitter.emitOpcode(Opcode.DUP); // proto constructor constructor
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol); // proto constructor constructor
                                                                                       // symbol
                compilerContext.emitter.emitOpcodeConstant(Opcode.FCLOSURE, entry.function()); // proto constructor
                                                                                               // constructor symbol
                                                                                               // method
                compilerContext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, 8); // proto constructor constructor
                compilerContext.emitter.emitOpcode(Opcode.DROP); // proto constructor
                compilerContext.emitter.emitOpcode(Opcode.SWAP); // constructor proto
            }
        }
    }

    void registerPrivateName(Map<String, String> privateNameKinds, String privateName, String kind, ASTNode ast) {
        String existingKind = privateNameKinds.get(privateName);
        if (existingKind == null) {
            privateNameKinds.put(privateName, kind);
            return;
        }
        boolean isGetterSetterPair = (JSKeyword.GET.equals(existingKind) && JSKeyword.SET.equals(kind))
                || (JSKeyword.SET.equals(existingKind) && JSKeyword.GET.equals(kind));
        if (isGetterSetterPair) {
            privateNameKinds.put(privateName, "accessor");
            return;
        }
        throw new JSCompilerException("private class field is already defined", ast);
    }

    record PrivateMethodEntry(String name, JSBytecodeFunction function, String kind, MethodDefinition ast,
            String bindingName) {
    }
}
