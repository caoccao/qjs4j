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
        ClassExpressionCompiler.compile(compilerContext, classDecl.asExpression());

        // Store the class constructor in a variable
        if (classDecl.getId() != null) {
            String varName = classDecl.getId().getName();
            if (!compilerContext.inGlobalScope) {
                compilerContext.scopeManager.currentScope().declareLocal(varName);
                Integer localIndex = compilerContext.scopeManager.currentScope().getLocal(varName);
                if (localIndex != null) {
                    emitter.emitPutLocal(localIndex);
                }
            } else if (compilerContext.tdzLocals.contains(varName)) {
                // TDZ local: class was pre-declared as a local for TDZ enforcement
                Integer localIndex = compilerContext.scopeManager.findLocalInScopes(varName);
                if (localIndex != null) {
                    emitter.emitPutLocal(localIndex);
                    // Direct eval creates an ephemeral lexical environment. When program
                    // lexicals are predeclared as locals, do not materialize class bindings
                    // on the global object.
                    if (!compilerContext.predeclareProgramLexicalsAsLocals) {
                        // Also store as global variable so class methods (compiled with separate
                        // BytecodeCompiler contexts) can access this class via GET_VAR.
                        emitter.emitGetLocal(localIndex);
                        emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
                    }
                }
            } else {
                compilerContext.nonDeletableGlobalBindings.add(varName);
                emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
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
        emitter.emitOpcode(Opcode.SWAP);
        emitter.emitPushConstant(computedFieldSymbol);
        try {
            if (compilerContext.inClassBody) {
                compilerContext.functionExpressionCompiler.emitStrictClassBodyExpression(field.getKey());
            } else {
                compilerContext.expressionCompiler.compile(field.getKey());
            }
            emitter.emitOpcode(Opcode.TO_PROPKEY);
            emitter.emitIntOperand(Opcode.DEFINE_METHOD_COMPUTED, 4);
            emitter.emitOpcode(Opcode.SWAP);
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
            emitter.emitOpcode(Opcode.PUSH_THIS);
            compilerContext.pushState();
            compilerContext.inClassFieldInitializer = true;
            emitFieldDefinition(compilerContext, field, privateSymbols, computedFieldSymbols,
                    autoAccessorBackingSymbols);
            compilerContext.popState();
            emitter.emitOpcode(Opcode.DROP);
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
            functionContext.classDeclarationCompiler.initializeInstanceElements(privateInstanceMethodFunctions,
                    instanceFields, privateSymbols, computedFieldSymbols, autoAccessorBackingSymbols);
        }

        compilerContext.functionExpressionCompiler.initializeParameters(methodCompiler, functionExpression,
                parameterSlotIndexes, methodDestructuringParams, functionExpression.isGenerator());

        // For derived constructors, defer until after super() (INIT_CTOR) per ES spec 14.6.13 step 11.
        if (isConstructor && isDerivedConstructor) {
            functionContext.pendingPostSuperInitialization = () -> functionContext.classDeclarationCompiler
                    .initializeInstanceElements(privateInstanceMethodFunctions, instanceFields, privateSymbols,
                            computedFieldSymbols, autoAccessorBackingSymbols);
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
            emitter.emitOpcode(Opcode.PUSH_THIS);
            emitter.emitPushConstant(symbol);
            int capture = compilerContext.captureResolver.resolveCapturedBindingIndex(entry.bindingName());
            emitter.emitIntOperand(Opcode.GET_VAR_REF, capture);
            emitter.emitIntOperand(Opcode.DEFINE_METHOD_COMPUTED, entry.definitionFlags());
            emitter.emitOpcode(Opcode.DROP);
        }
    }

    /**
     * Compile a static block as a function. Static blocks are executed immediately after class definition with the
     * class constructor as 'this'.
     */
    JSBytecodeFunction compileStaticBlock(StaticBlock staticBlock, String className,
            Map<String, JSSymbol> privateSymbols) {
        BytecodeCompiler blockCompiler = newStaticInitializer(privateSymbols, className);
        CompilerContext blockCtx = blockCompiler.context();

        // Compile all statements in the static block
        for (Statement stmt : staticBlock.getBody()) {
            blockCtx.statementCompiler.compile(stmt);
        }

        // Static blocks always return undefined
        blockCtx.emitter.emitOpcode(Opcode.UNDEFINED);
        int returnValueIndex = blockCtx.scopeManager.currentScope()
                .declareLocal("#static_block_return_" + blockCtx.emitter.currentOffset());
        blockCtx.emitter.emitPutLocal(returnValueIndex);
        blockCtx.emitHelpers.emitCurrentScopeUsingDisposal();
        blockCtx.emitter.emitGetLocal(returnValueIndex);
        blockCtx.emitter.emitOpcode(Opcode.RETURN);

        return finishStaticInitializer(blockCompiler, "<static initializer>", "static { [initializer] }");
    }

    /**
     * Compile a static field initializer as a function and return it. The function is called with class constructor as
     * `this`.
     */
    JSBytecodeFunction compileStaticFieldInitializer(PropertyDefinition field,
            IdentityHashMap<PropertyDefinition, JSSymbol> computedFieldSymbols, Map<String, JSSymbol> privateSymbols,
            IdentityHashMap<PropertyDefinition, JSSymbol> autoAccessorBackingSymbols, String className) {
        BytecodeCompiler initializerCompiler = newStaticInitializer(privateSymbols, className);
        CompilerContext initCtx = initializerCompiler.context();

        // Stack: this
        initCtx.emitter.emitOpcode(Opcode.PUSH_THIS);

        emitFieldDefinition(initCtx, field, privateSymbols, computedFieldSymbols, autoAccessorBackingSymbols);
        if (!field.isPrivate() && !field.isAutoAccessor()) {
            initCtx.emitter.emitOpcode(Opcode.DROP);
        }
        initCtx.emitter.emitOpcode(Opcode.UNDEFINED);
        initCtx.emitter.emitOpcode(Opcode.RETURN);

        return finishStaticInitializer(initializerCompiler, "<static field initializer>",
                "static field initializer for " + className);
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
        ctorCtx.classDeclarationCompiler.initializeInstanceElements(privateInstanceMethodFunctions, instanceFields,
                privateSymbols, computedFieldSymbols, autoAccessorBackingSymbols);

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

    private JSBytecodeFunction finishStaticInitializer(BytecodeCompiler compiler, String name, String source) {
        CompilerContext ctx = compiler.context();
        int localCount = ctx.scopeManager.currentScope().getLocalCount();
        ctx.scopeManager.exitScope();
        Bytecode bytecode = ctx.emitter.build(localCount);
        JSBytecodeFunction initializer = new JSBytecodeFunction(compilerContext.context, bytecode, name, 0,
                JSValue.NO_ARGS, null, false, false, false, false, true, source);
        compilerContext.emitHelpers.emitCapturedValues(compiler, initializer);
        return initializer;
    }

    private void initializeInstanceElements(List<PrivateMethodEntry> privateMethods, List<PropertyDefinition> fields,
            Map<String, JSSymbol> privateSymbols, IdentityHashMap<PropertyDefinition, JSSymbol> computedFieldSymbols,
            IdentityHashMap<PropertyDefinition, JSSymbol> autoAccessorBackingSymbols) {
        if (!privateMethods.isEmpty()) {
            compilePrivateMethodInitialization(privateMethods, privateSymbols);
        }
        compileFieldInitialization(fields, privateSymbols, computedFieldSymbols, autoAccessorBackingSymbols);
    }

    void installPrivateStaticMethods(List<PrivateMethodEntry> privateStaticMethodEntries,
            Map<String, JSSymbol> privateSymbols) {
        for (PrivateMethodEntry entry : privateStaticMethodEntries) {
            JSSymbol symbol = privateSymbols.get(entry.name());
            if (symbol == null) {
                throw new JSCompilerException("Private static method symbol not found: #" + entry.name(), entry.ast());
            }

            // Keep the constructor and prototype on the stack after installing the method.
            emitter.emitOpcode(Opcode.SWAP);
            emitter.emitOpcode(Opcode.DUP);
            emitter.emitPushConstant(symbol);
            emitter.emitOpcodeConstant(Opcode.FCLOSURE, entry.function());
            emitter.emitIntOperand(Opcode.DEFINE_METHOD_COMPUTED, entry.definitionFlags());
            emitter.emitOpcode(Opcode.DROP);
            emitter.emitOpcode(Opcode.SWAP);
        }
    }

    private BytecodeCompiler newStaticInitializer(Map<String, JSSymbol> privateSymbols, String className) {
        BytecodeCompiler compiler = compilerContext.newNestedCompiler(true, privateSymbols);
        CompilerContext ctx = compiler.context();
        ctx.scopeManager.enterScope();
        ctx.inGlobalScope = false;
        // Direct eval in a static initializer can reference the class's inner binding.
        if (className != null && !className.isEmpty()) {
            ctx.captureResolver.resolveCapturedBindingIndex(className);
            ctx.classInnerNameToCapture = className;
        }
        return compiler;
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

    private static void emitFieldDefinition(CompilerContext ctx, PropertyDefinition field,
            Map<String, JSSymbol> privateSymbols, IdentityHashMap<PropertyDefinition, JSSymbol> computedFieldSymbols,
            IdentityHashMap<PropertyDefinition, JSSymbol> autoAccessorBackingSymbols) {
        if (field.isPrivate() || field.isAutoAccessor()) {
            JSSymbol symbol;
            if (field.isPrivate()) {
                if (!(field.getKey() instanceof PrivateIdentifier id)) {
                    throw new JSCompilerException(
                            field.isStatic() ? "Invalid static private field key" : "Invalid private field key", field);
                }
                symbol = privateSymbols.get(id.getName());
                if (symbol == null && field.isStatic()) {
                    throw new JSCompilerException("Static private field symbol not found: #" + id.getName(), id);
                }
            } else {
                symbol = autoAccessorBackingSymbols.get(field);
                if (symbol == null) {
                    throw new JSCompilerException(field.isStatic()
                            ? "Auto-accessor static backing symbol not found"
                            : "Auto-accessor backing symbol not found", field);
                }
            }
            emitFieldValue(ctx, field);
            if (symbol == null) {
                ctx.emitter.emitOpcode(Opcode.DROP);
                return;
            }
            ctx.emitter.emitPushConstant(symbol);
            ctx.emitter.emitOpcode(Opcode.SWAP);
            if (field.getValue() != null && field.getValue().isAnonymousFunction()) {
                ctx.emitter.emitOpcode(Opcode.SET_NAME_COMPUTED);
            }
            ctx.emitter.emitOpcode(Opcode.DEFINE_PRIVATE_FIELD);
            return;
        }
        if (field.isComputed()) {
            JSSymbol symbol = computedFieldSymbols.get(field);
            if (symbol == null) {
                throw new JSCompilerException(
                        field.isStatic() ? "Computed static field key not found" : "Computed field key not found",
                        field);
            }
            // Static initializers find the saved key on this, instance initializers on their constructor.
            if (field.isStatic()) {
                ctx.emitter.emitOpcode(Opcode.PUSH_THIS);
            } else {
                ctx.emitter.emitIntOperand(Opcode.SPECIAL_OBJECT, 2);
            }
            ctx.emitter.emitPushConstant(symbol);
            ctx.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        } else {
            ctx.emitHelpers.emitNonComputedPublicFieldKey(field.getKey());
        }
        emitFieldValue(ctx, field);
        ctx.emitter.emitIntOperand(Opcode.DEFINE_METHOD_COMPUTED, 4);
    }

    private static void emitFieldValue(CompilerContext ctx, PropertyDefinition field) {
        if (field.getValue() == null) {
            ctx.emitter.emitOpcode(Opcode.UNDEFINED);
        } else {
            ctx.expressionCompiler.compile(field.getValue());
        }
    }

    record PrivateMethodEntry(String name, JSBytecodeFunction function, String kind, MethodDefinition ast,
            String bindingName) {
        int definitionFlags() {
            if (JSKeyword.GET.equals(kind)) {
                return 1;
            }
            return JSKeyword.SET.equals(kind) ? 2 : 8; // A regular private method is non-writable.
        }
    }
}
