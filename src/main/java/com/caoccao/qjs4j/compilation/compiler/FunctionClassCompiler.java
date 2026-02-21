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
    private final CompilerContext ctx;
    private final CompilerDelegates delegates;
    private final FunctionClassFieldCompiler fieldCompiler;

    FunctionClassCompiler(CompilerContext ctx, CompilerDelegates delegates) {
        this.ctx = ctx;
        this.delegates = delegates;
        fieldCompiler = new FunctionClassFieldCompiler(ctx, delegates);
    }

    void compileArrowFunctionExpression(ArrowFunctionExpression arrowExpr) {
        // Create a new compiler for the function body
        // Arrow functions inherit strict mode from parent (QuickJS behavior)
        BytecodeCompiler functionCompiler = new BytecodeCompiler(ctx.strictMode, ctx.captureResolver);
        CompilerContext funcCtx = functionCompiler.context();
        CompilerDelegates funcDelegates = functionCompiler.delegates();
        funcCtx.nonDeletableGlobalBindings.addAll(ctx.nonDeletableGlobalBindings);

        // Enter function scope and add parameters as locals
        funcCtx.enterScope();
        funcCtx.inGlobalScope = false;
        funcCtx.isInAsyncFunction = arrowExpr.isAsync();  // Track if this is an async function
        funcCtx.isInArrowFunction = true;  // Arrow functions don't have their own arguments
        // Arrow functions inherit arguments from enclosing non-arrow function.
        // If the parent is a regular function (not arrow, not global program), it has arguments binding.
        // If the parent is also an arrow, inherit whatever it has.
        // The global program is not a function and doesn't provide 'arguments'.
        if (ctx.isInArrowFunction) {
            funcCtx.hasEnclosingArgumentsBinding = ctx.hasEnclosingArgumentsBinding;
        } else {
            funcCtx.hasEnclosingArgumentsBinding = !ctx.isGlobalProgram;
        }

        // Check for "use strict" directive if body is a block statement
        if (arrowExpr.body() instanceof BlockStatement block && ctx.hasUseStrictDirective(block)) {
            funcCtx.strictMode = true;
        }

        for (Identifier param : arrowExpr.params()) {
            funcCtx.currentScope().declareParameter(param.name());
        }

        // Pre-declare 'arguments' as a local when the arrow function has parameter expressions
        // and the body contains 'var arguments'. This is needed so that:
        // 1. eval() in default params can set 'arguments' via the scope overlay
        // 2. Inner arrows in defaults can capture 'arguments' via closure
        // Only for arrows without enclosing arguments binding (top-level arrows), since
        // arrows inside regular functions use SPECIAL_OBJECT for 'arguments' instead.
        if (arrowExpr.defaults() != null && !funcCtx.hasEnclosingArgumentsBinding
                && arrowExpr.body() instanceof BlockStatement bodyBlock
                && CompilerContext.containsVarArgumentsDeclaration(bodyBlock.body())) {
            funcCtx.currentScope().declareLocal("arguments");
        }

        // Emit default parameter initialization following QuickJS pattern:
        // GET_ARG idx, DUP, UNDEFINED, STRICT_EQ, IF_FALSE label, DROP, <default>, DUP, PUT_ARG idx, label:
        if (arrowExpr.defaults() != null) {
            delegates.emitHelpers.emitDefaultParameterInit(functionCompiler, arrowExpr.defaults());
        }

        // Handle rest parameter if present
        // The REST opcode must be emitted early in the function to initialize the rest array
        if (arrowExpr.restParameter() != null) {
            // Calculate the index where rest arguments start
            int firstRestIndex = arrowExpr.params().size();

            // Emit REST opcode with the starting index
            funcCtx.emitter.emitOpcode(Opcode.REST);
            funcCtx.emitter.emitU16(firstRestIndex);

            // Declare the rest parameter as a local and store the rest array
            String restParamName = arrowExpr.restParameter().argument().name();
            int restLocalIndex = funcCtx.currentScope().declareLocal(restParamName);

            // Store the rest array (from stack top) to the rest parameter local
            funcCtx.emitter.emitOpcode(Opcode.PUT_LOCAL);
            funcCtx.emitter.emitU16(restLocalIndex);
        }

        // Compile function body
        // Arrow functions can have expression body or block statement body
        if (arrowExpr.body() instanceof BlockStatement block) {
            // Compile block body statements (don't call compileBlockStatement as it would create a new scope)
            for (Statement stmt : block.body()) {
                funcDelegates.statements.compileStatement(stmt);
            }

            // If body doesn't end with return, add implicit return undefined
            List<Statement> bodyStatements = block.body();
            if (bodyStatements.isEmpty() || !(bodyStatements.get(bodyStatements.size() - 1) instanceof ReturnStatement)) {
                funcCtx.emitter.emitOpcode(Opcode.UNDEFINED);
                int returnValueIndex = funcCtx.currentScope().declareLocal("$arrow_return_" + funcCtx.emitter.currentOffset());
                funcCtx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, returnValueIndex);
                funcDelegates.emitHelpers.emitCurrentScopeUsingDisposal();
                funcCtx.emitter.emitOpcodeU16(Opcode.GET_LOCAL, returnValueIndex);
                // Emit RETURN_ASYNC for async functions, RETURN for sync functions
                funcCtx.emitter.emitOpcode(arrowExpr.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
            }
        } else if (arrowExpr.body() instanceof Expression expr) {
            // Expression body - implicitly returns the expression value
            funcDelegates.expressions.compileExpression(expr);
            int returnValueIndex = funcCtx.currentScope().declareLocal("$arrow_return_" + funcCtx.emitter.currentOffset());
            funcCtx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, returnValueIndex);
            funcDelegates.emitHelpers.emitCurrentScopeUsingDisposal();
            funcCtx.emitter.emitOpcodeU16(Opcode.GET_LOCAL, returnValueIndex);
            // Emit RETURN_ASYNC for async functions, RETURN for sync functions
            funcCtx.emitter.emitOpcode(arrowExpr.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
        }

        int localCount = funcCtx.currentScope().getLocalCount();
        String[] localVarNames = CompilerContext.extractLocalVarNames(funcCtx.currentScope());
        funcCtx.exitScope();

        // Build the function bytecode
        Bytecode functionBytecode = funcCtx.emitter.build(localCount, localVarNames);

        // Arrow functions are always anonymous
        String functionName = "";

        // Extract function source code from original source
        String functionSource = ctx.extractSourceCode(arrowExpr.getLocation());

        // Create JSBytecodeFunction
        // Arrow functions cannot be constructors
        int definedArgCount = CompilerContext.computeDefinedArgCount(arrowExpr.params(), arrowExpr.defaults(), arrowExpr.restParameter() != null);
        JSBytecodeFunction function = new JSBytecodeFunction(
                functionBytecode,
                functionName,
                definedArgCount,
                new JSValue[0],
                null,            // prototype - arrow functions don't have prototype
                false,           // isConstructor - arrow functions cannot be constructors
                arrowExpr.isAsync(),
                false,           // Arrow functions cannot be generators
                true,            // isArrow - this is an arrow function
                funcCtx.strictMode,  // strict - inherit from enclosing scope
                functionSource   // source code for toString()
        );
        function.setHasParameterExpressions(CompilerContext.hasNonSimpleParameters(arrowExpr.defaults(), arrowExpr.restParameter()));

        // Prototype chain will be initialized when the function is loaded
        // during bytecode execution (see FCLOSURE opcode handler)

        delegates.emitHelpers.emitCapturedValues(functionCompiler, function);
        // Emit FCLOSURE opcode with function in constant pool
        ctx.emitter.emitOpcodeConstant(Opcode.FCLOSURE, function);
    }

    void compileClassDeclaration(ClassDeclaration classDecl) {
        // Following QuickJS implementation in quickjs.c:24700-25200

        String className = classDecl.id() != null ? classDecl.id().name() : "";

        // Compile superclass expression or emit undefined
        if (classDecl.superClass() != null) {
            delegates.expressions.compileExpression(classDecl.superClass());
        } else {
            ctx.emitter.emitOpcode(Opcode.UNDEFINED);
        }
        // Stack: superClass

        // Separate class elements by type
        List<ClassDeclaration.MethodDefinition> methods = new ArrayList<>();
        List<ClassDeclaration.MethodDefinition> privateInstanceMethods = new ArrayList<>();
        List<ClassDeclaration.MethodDefinition> privateStaticMethods = new ArrayList<>();
        List<ClassDeclaration.PropertyDefinition> instanceFields = new ArrayList<>();
        List<ClassDeclaration.ClassElement> staticInitializers = new ArrayList<>();
        List<ClassDeclaration.PropertyDefinition> computedFieldsInDefinitionOrder = new ArrayList<>();
        IdentityHashMap<ClassDeclaration.PropertyDefinition, JSSymbol> computedFieldSymbols = new IdentityHashMap<>();
        ClassDeclaration.MethodDefinition constructor = null;

        for (ClassDeclaration.ClassElement element : classDecl.body()) {
            if (element instanceof ClassDeclaration.MethodDefinition method) {
                // Check if it's a constructor
                if (method.key() instanceof Identifier id && "constructor".equals(id.name()) && !method.isStatic()) {
                    constructor = method;
                } else if (method.isPrivate()) {
                    if (method.isStatic()) {
                        privateStaticMethods.add(method);
                    } else {
                        privateInstanceMethods.add(method);
                    }
                } else {
                    methods.add(method);
                }
            } else if (element instanceof ClassDeclaration.PropertyDefinition field) {
                if (field.isStatic()) {
                    staticInitializers.add(field);
                } else {
                    instanceFields.add(field);
                }

                if (field.computed() && !field.isPrivate()) {
                    computedFieldsInDefinitionOrder.add(field);
                    computedFieldSymbols.put(
                            field,
                            new JSSymbol("__computed_field_" + computedFieldsInDefinitionOrder.size())
                    );
                }
            } else if (element instanceof ClassDeclaration.StaticBlock block) {
                staticInitializers.add(block);
            }
        }

        // Create symbols for all private names (fields + methods), once per class.
        Map<String, JSSymbol> privateSymbols = createPrivateSymbols(classDecl.body());
        LinkedHashMap<String, JSBytecodeFunction> privateInstanceMethodFunctions = compilePrivateMethodFunctions(
                privateInstanceMethods, privateSymbols, computedFieldSymbols);
        LinkedHashMap<String, JSBytecodeFunction> privateStaticMethodFunctions = compilePrivateMethodFunctions(
                privateStaticMethods, privateSymbols, computedFieldSymbols);

        // Compile constructor function (or create default) with field initialization
        JSBytecodeFunction constructorFunc;
        if (constructor != null) {
            constructorFunc = compileMethodAsFunction(
                    constructor,
                    className,
                    classDecl.superClass() != null,
                    instanceFields,
                    privateSymbols,
                    computedFieldSymbols,
                    privateInstanceMethodFunctions,
                    true
            );
        } else {
            // Create default constructor with field initialization
            constructorFunc = createDefaultConstructor(
                    className,
                    classDecl.superClass() != null,
                    instanceFields,
                    privateSymbols,
                    computedFieldSymbols,
                    privateInstanceMethodFunctions
            );
        }

        // Set the source code for the constructor to be the entire class definition
        // This matches JavaScript behavior where class.toString() returns the class source
        if (ctx.sourceCode != null && classDecl.location() != null) {
            int startPos = classDecl.location().offset();
            int endPos = classDecl.location().endOffset();
            if (startPos >= 0 && endPos <= ctx.sourceCode.length()) {
                String classSource = ctx.sourceCode.substring(startPos, endPos);
                constructorFunc.setSourceCode(classSource);
            }
        }

        // Emit constructor in constant pool
        ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, constructorFunc);
        // Stack: superClass constructor

        // Emit DEFINE_CLASS opcode with class name
        ctx.emitter.emitOpcodeAtom(Opcode.DEFINE_CLASS, className);
        // Stack: proto constructor

        // Now compile methods and add them to the prototype
        // After DEFINE_CLASS: Stack is proto constructor (constructor on TOP)
        // For simplicity, swap so proto is on top: constructor proto
        ctx.emitter.emitOpcode(Opcode.SWAP);
        // Stack: constructor proto

        for (ClassDeclaration.MethodDefinition method : methods) {
            // Stack before each iteration: constructor proto

            if (method.isStatic()) {
                // For static methods, constructor is the target
                // Current: constructor proto
                // Need to add method to constructor, so swap to get constructor on top
                ctx.emitter.emitOpcode(Opcode.SWAP);
                // Stack: proto constructor

                // Compile method. Static methods share the same private name scope.
                JSBytecodeFunction methodFunc = compileMethodAsFunction(
                        method,
                        ctx.getMethodName(method),
                        false,
                        List.of(),
                        privateSymbols,
                        computedFieldSymbols,
                        Map.of(),
                        false
                );

                String methodName = ctx.getMethodName(method);
                delegates.emitHelpers.emitClassMethodDefinition(method, methodFunc, methodName);
                // Stack: proto constructor (method added to constructor)

                // Swap back to restore order: constructor proto
                ctx.emitter.emitOpcode(Opcode.SWAP);
                // Stack: constructor proto
            } else {
                // For instance methods, proto is the target
                // Current: constructor proto
                // Compile method (no field initialization for regular methods)
                // Pass private symbols to methods so they can access private fields
                JSBytecodeFunction methodFunc = compileMethodAsFunction(
                        method,
                        ctx.getMethodName(method),
                        false,
                        List.of(),
                        privateSymbols,
                        computedFieldSymbols,
                        Map.of(),
                        false
                );

                String methodName = ctx.getMethodName(method);
                delegates.emitHelpers.emitClassMethodDefinition(method, methodFunc, methodName);
                // Stack: constructor proto (method added to proto)
            }
        }

        installPrivateStaticMethods(privateStaticMethodFunctions, privateSymbols);

        // Evaluate all computed field names once at class definition time.
        // This matches QuickJS behavior and avoids re-evaluating key side effects per instance.
        for (ClassDeclaration.PropertyDefinition field : computedFieldsInDefinitionOrder) {
            compileComputedFieldNameCache(field, computedFieldSymbols);
        }

        // Swap back to original order: proto constructor
        ctx.emitter.emitOpcode(Opcode.SWAP);
        // Stack: proto constructor

        // Execute static initializers (static fields and static blocks) in source order.
        // Each initializer runs with class constructor as `this`.
        for (ClassDeclaration.ClassElement staticInitializer : staticInitializers) {
            JSBytecodeFunction staticInitializerFunc;
            if (staticInitializer instanceof ClassDeclaration.PropertyDefinition staticField) {
                staticInitializerFunc = compileStaticFieldInitializer(
                        staticField, computedFieldSymbols, privateSymbols, className);
            } else if (staticInitializer instanceof ClassDeclaration.StaticBlock staticBlock) {
                staticInitializerFunc = compileStaticBlock(staticBlock, className, privateSymbols);
            } else {
                continue;
            }

            // Stack: proto constructor
            // DUP the constructor to use as 'this'
            ctx.emitter.emitOpcode(Opcode.DUP);
            // Stack: proto constructor constructor

            // Push the static initializer function
            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, staticInitializerFunc);
            // Stack: proto constructor constructor func

            // SWAP so we have: proto constructor func constructor
            ctx.emitter.emitOpcode(Opcode.SWAP);
            // Stack: proto constructor func constructor

            // Call the function with 0 arguments, using constructor as 'this'
            ctx.emitter.emitOpcodeU16(Opcode.CALL, 0);
            // Stack: proto constructor returnValue

            // Drop the return value
            ctx.emitter.emitOpcode(Opcode.DROP);
            // Stack: proto constructor
        }

        // Drop prototype, keep constructor
        ctx.emitter.emitOpcode(Opcode.NIP);
        // Stack: constructor

        // Store the class constructor in a variable
        if (classDecl.id() != null) {
            String varName = classDecl.id().name();
            if (!ctx.inGlobalScope) {
                ctx.currentScope().declareLocal(varName);
                Integer localIndex = ctx.currentScope().getLocal(varName);
                if (localIndex != null) {
                    ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
                }
            } else if (ctx.tdzLocals.contains(varName)) {
                // TDZ local: class was pre-declared as a local for TDZ enforcement
                Integer localIndex = ctx.findLocalInScopes(varName);
                if (localIndex != null) {
                    ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
                }
            } else {
                ctx.nonDeletableGlobalBindings.add(varName);
                ctx.emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
            }
        } else {
            // Anonymous class expression - leave on stack
            // For class declarations, we always have a name, so this shouldn't happen
        }
    }

    void compileClassExpression(ClassExpression classExpr) {
        // Class expressions are almost identical to class declarations,
        // but they leave the constructor on the stack instead of binding it to a variable

        String className = classExpr.id() != null ? classExpr.id().name() : "";

        // Compile superclass expression or emit undefined
        if (classExpr.superClass() != null) {
            delegates.expressions.compileExpression(classExpr.superClass());
        } else {
            ctx.emitter.emitOpcode(Opcode.UNDEFINED);
        }
        // Stack: superClass

        // Separate class elements by type
        List<ClassDeclaration.MethodDefinition> methods = new ArrayList<>();
        List<ClassDeclaration.MethodDefinition> privateInstanceMethods = new ArrayList<>();
        List<ClassDeclaration.MethodDefinition> privateStaticMethods = new ArrayList<>();
        List<ClassDeclaration.PropertyDefinition> instanceFields = new ArrayList<>();
        List<ClassDeclaration.ClassElement> staticInitializers = new ArrayList<>();
        List<ClassDeclaration.PropertyDefinition> computedFieldsInDefinitionOrder = new ArrayList<>();
        IdentityHashMap<ClassDeclaration.PropertyDefinition, JSSymbol> computedFieldSymbols = new IdentityHashMap<>();
        ClassDeclaration.MethodDefinition constructor = null;

        for (ClassDeclaration.ClassElement element : classExpr.body()) {
            if (element instanceof ClassDeclaration.MethodDefinition method) {
                // Check if it's a constructor
                if (method.key() instanceof Identifier id && "constructor".equals(id.name()) && !method.isStatic()) {
                    constructor = method;
                } else if (method.isPrivate()) {
                    if (method.isStatic()) {
                        privateStaticMethods.add(method);
                    } else {
                        privateInstanceMethods.add(method);
                    }
                } else {
                    methods.add(method);
                }
            } else if (element instanceof ClassDeclaration.PropertyDefinition field) {
                if (field.isStatic()) {
                    staticInitializers.add(field);
                } else {
                    instanceFields.add(field);
                }

                if (field.computed() && !field.isPrivate()) {
                    computedFieldsInDefinitionOrder.add(field);
                    computedFieldSymbols.put(
                            field,
                            new JSSymbol("__computed_field_" + computedFieldsInDefinitionOrder.size())
                    );
                }
            } else if (element instanceof ClassDeclaration.StaticBlock block) {
                staticInitializers.add(block);
            }
        }

        Map<String, JSSymbol> privateSymbols = createPrivateSymbols(classExpr.body());
        LinkedHashMap<String, JSBytecodeFunction> privateInstanceMethodFunctions = compilePrivateMethodFunctions(
                privateInstanceMethods, privateSymbols, computedFieldSymbols);
        LinkedHashMap<String, JSBytecodeFunction> privateStaticMethodFunctions = compilePrivateMethodFunctions(
                privateStaticMethods, privateSymbols, computedFieldSymbols);

        // Compile constructor function (or create default)
        JSBytecodeFunction constructorFunc;
        if (constructor != null) {
            constructorFunc = compileMethodAsFunction(
                    constructor,
                    className,
                    classExpr.superClass() != null,
                    instanceFields,
                    privateSymbols,
                    computedFieldSymbols,
                    privateInstanceMethodFunctions,
                    true
            );
        } else {
            constructorFunc = createDefaultConstructor(
                    className,
                    classExpr.superClass() != null,
                    instanceFields,
                    privateSymbols,
                    computedFieldSymbols,
                    privateInstanceMethodFunctions
            );
        }

        // Set the source code for the constructor to be the entire class definition
        // This matches JavaScript behavior where class.toString() returns the class source
        if (ctx.sourceCode != null && classExpr.location() != null) {
            int startPos = classExpr.location().offset();
            int endPos = classExpr.location().endOffset();
            if (startPos >= 0 && endPos <= ctx.sourceCode.length()) {
                String classSource = ctx.sourceCode.substring(startPos, endPos);
                constructorFunc.setSourceCode(classSource);
            }
        }

        // Emit constructor in constant pool
        ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, constructorFunc);
        // Stack: superClass constructor

        // Emit DEFINE_CLASS opcode with class name
        ctx.emitter.emitOpcodeAtom(Opcode.DEFINE_CLASS, className);
        // Stack: proto constructor

        // Compile methods
        ctx.emitter.emitOpcode(Opcode.SWAP);
        // Stack: constructor proto

        for (ClassDeclaration.MethodDefinition method : methods) {
            if (method.isStatic()) {
                // For static methods, constructor is the target object
                ctx.emitter.emitOpcode(Opcode.SWAP);
                // Stack: proto constructor

                JSBytecodeFunction methodFunc = compileMethodAsFunction(
                        method,
                        ctx.getMethodName(method),
                        false,
                        List.of(),
                        privateSymbols,
                        computedFieldSymbols,
                        Map.of(),
                        false
                );

                String methodName = ctx.getMethodName(method);
                delegates.emitHelpers.emitClassMethodDefinition(method, methodFunc, methodName);
                // Stack: proto constructor

                // Restore canonical order for next iteration
                ctx.emitter.emitOpcode(Opcode.SWAP);
                // Stack: constructor proto
            } else {
                JSBytecodeFunction methodFunc = compileMethodAsFunction(
                        method,
                        ctx.getMethodName(method),
                        false,
                        List.of(),
                        privateSymbols,
                        computedFieldSymbols,
                        Map.of(),
                        false
                );

                String methodName = ctx.getMethodName(method);
                delegates.emitHelpers.emitClassMethodDefinition(method, methodFunc, methodName);
                // Stack: constructor proto
            }
        }

        installPrivateStaticMethods(privateStaticMethodFunctions, privateSymbols);

        // Evaluate all computed field names once at class definition time.
        for (ClassDeclaration.PropertyDefinition field : computedFieldsInDefinitionOrder) {
            compileComputedFieldNameCache(field, computedFieldSymbols);
        }

        // Swap back to: proto constructor
        ctx.emitter.emitOpcode(Opcode.SWAP);
        // Stack: proto constructor

        // Execute static initializers (static fields and static blocks) in source order.
        for (ClassDeclaration.ClassElement staticInitializer : staticInitializers) {
            JSBytecodeFunction staticInitializerFunc;
            if (staticInitializer instanceof ClassDeclaration.PropertyDefinition staticField) {
                staticInitializerFunc = compileStaticFieldInitializer(
                        staticField, computedFieldSymbols, privateSymbols, className);
            } else if (staticInitializer instanceof ClassDeclaration.StaticBlock staticBlock) {
                staticInitializerFunc = compileStaticBlock(staticBlock, className, privateSymbols);
            } else {
                continue;
            }

            // Stack: proto constructor
            // DUP the constructor to use as 'this'
            ctx.emitter.emitOpcode(Opcode.DUP);
            // Stack: proto constructor constructor

            // Push the static initializer function
            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, staticInitializerFunc);
            // Stack: proto constructor constructor func

            // SWAP so we have: proto constructor func constructor
            ctx.emitter.emitOpcode(Opcode.SWAP);
            // Stack: proto constructor func constructor

            // Call the function with 0 arguments, using constructor as 'this'
            ctx.emitter.emitOpcodeU16(Opcode.CALL, 0);
            // Stack: proto constructor returnValue

            // Drop the return value
            ctx.emitter.emitOpcode(Opcode.DROP);
            // Stack: proto constructor
        }

        // Drop prototype, keep constructor on stack
        ctx.emitter.emitOpcode(Opcode.NIP);
        // Stack: constructor

        // For class expressions, we leave the constructor on the stack
        // (unlike class declarations which bind it to a variable)
    }

    /**
     * Evaluate and cache a computed class field name on the constructor object once.
     * Expects stack before/after to be: constructor proto.
     */
    void compileComputedFieldNameCache(
            ClassDeclaration.PropertyDefinition field,
            IdentityHashMap<ClassDeclaration.PropertyDefinition, JSSymbol> computedFieldSymbols) {
        fieldCompiler.compileComputedFieldNameCache(field, computedFieldSymbols);
    }

    /**
     * Compile field initialization code for instance fields.
     * Emits code to set each field on 'this' with its initializer value.
     * For private fields, uses the symbol from privateSymbols map.
     */
    void compileFieldInitialization(List<ClassDeclaration.PropertyDefinition> fields,
                                    Map<String, JSSymbol> privateSymbols,
                                    IdentityHashMap<ClassDeclaration.PropertyDefinition, JSSymbol> computedFieldSymbols) {
        fieldCompiler.compileFieldInitialization(fields, privateSymbols, computedFieldSymbols);
    }

    void compileFunctionDeclaration(FunctionDeclaration funcDecl) {
        // Pre-declare function name as a local in the current scope (if non-global and not
        // already declared). This must happen BEFORE creating the child compiler so the
        // function body can capture the name via closure for self-reference.
        // Per QuickJS: function declarations inside blocks/switch cases create a lexical
        // binding in the current scope, even if a parent scope has the same name (e.g.,
        // a parameter). This prevents the function object from overwriting the parent binding.
        String functionName = funcDecl.id().name();
        if (!ctx.inGlobalScope) {
            if (ctx.currentScope().getLocal(functionName) == null) {
                ctx.currentScope().declareLocal(functionName);
            }
        }

        // Create a new compiler for the function body
        // Nested functions inherit strict mode from parent (QuickJS behavior)
        BytecodeCompiler functionCompiler = new BytecodeCompiler(ctx.strictMode, ctx.captureResolver);
        CompilerContext funcCtx = functionCompiler.context();
        CompilerDelegates funcDelegates = functionCompiler.delegates();
        funcCtx.nonDeletableGlobalBindings.addAll(ctx.nonDeletableGlobalBindings);

        // Enter function scope and add parameters as locals
        funcCtx.enterScope();
        funcCtx.inGlobalScope = false;
        funcCtx.isInAsyncFunction = funcDecl.isAsync();  // Track if this is an async function

        // Check for "use strict" directive early and update strict mode
        // This ensures nested functions inherit the correct strict mode
        if (ctx.hasUseStrictDirective(funcDecl.body())) {
            funcCtx.strictMode = true;
        }

        for (Identifier param : funcDecl.params()) {
            funcCtx.currentScope().declareParameter(param.name());
        }

        // Emit default parameter initialization following QuickJS pattern
        if (funcDecl.defaults() != null) {
            delegates.emitHelpers.emitDefaultParameterInit(functionCompiler, funcDecl.defaults());
        }

        // Handle rest parameter if present
        // The REST opcode must be emitted early in the function to initialize the rest array
        if (funcDecl.restParameter() != null) {
            // Calculate the index where rest arguments start
            int firstRestIndex = funcDecl.params().size();

            // Emit REST opcode with the starting index
            funcCtx.emitter.emitOpcode(Opcode.REST);
            funcCtx.emitter.emitU16(firstRestIndex);

            // Declare the rest parameter as a local and store the rest array
            String restParamName = funcDecl.restParameter().argument().name();
            int restLocalIndex = funcCtx.currentScope().declareLocal(restParamName);

            // Store the rest array (from stack top) to the rest parameter local
            funcCtx.emitter.emitOpcode(Opcode.PUT_LOCAL);
            funcCtx.emitter.emitU16(restLocalIndex);
        }

        // If this is a generator function, emit INITIAL_YIELD at the start
        if (funcDecl.isGenerator()) {
            funcCtx.emitter.emitOpcode(Opcode.INITIAL_YIELD);
        }

        // Phase 0: Pre-declare all var bindings as locals before function hoisting.
        // var declarations are function-scoped and must be visible to nested function
        // declarations that may capture them. Without this, Phase 1 function hoisting
        // would fail to resolve captured var references (e.g., inner functions referencing
        // outer var variables would emit GET_VAR instead of GET_VAR_REF).
        funcDelegates.analysis.hoistVarDeclarationsAsLocals(funcDecl.body().body());

        // Phase 1: Hoist top-level function declarations (ES spec requires function
        // declarations to be initialized before any code executes).
        for (Statement stmt : funcDecl.body().body()) {
            if (stmt instanceof FunctionDeclaration innerFuncDecl) {
                funcDelegates.functions.compileFunctionDeclaration(innerFuncDecl);
            }
        }

        // Annex B.3.3.1: Hoist function declarations from blocks/if-statements
        // to the function scope as var bindings (initialized to undefined).
        Set<String> declParamNames = CompilerContext.buildParameterNames(funcDecl.params(), funcDecl.body().body());
        funcDelegates.analysis.hoistFunctionBodyAnnexBDeclarations(funcDecl.body().body(), declParamNames);

        // Phase 2: Compile non-FunctionDeclaration statements in source order
        for (Statement stmt : funcDecl.body().body()) {
            if (stmt instanceof FunctionDeclaration) {
                continue; // Already hoisted in Phase 1
            }
            funcDelegates.statements.compileStatement(stmt);
        }

        // If body doesn't end with return, add implicit return undefined
        List<Statement> bodyStatements = funcDecl.body().body();
        if (bodyStatements.isEmpty() || !(bodyStatements.get(bodyStatements.size() - 1) instanceof ReturnStatement)) {
            funcCtx.emitter.emitOpcode(Opcode.UNDEFINED);
            int returnValueIndex = funcCtx.currentScope().declareLocal("$function_return_" + funcCtx.emitter.currentOffset());
            funcCtx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, returnValueIndex);
            funcDelegates.emitHelpers.emitCurrentScopeUsingDisposal();
            funcCtx.emitter.emitOpcodeU16(Opcode.GET_LOCAL, returnValueIndex);
            // Emit RETURN_ASYNC for async functions, RETURN for sync functions
            funcCtx.emitter.emitOpcode(funcDecl.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
        }

        int localCount = funcCtx.currentScope().getLocalCount();
        String[] localVarNames = CompilerContext.extractLocalVarNames(funcCtx.currentScope());
        funcCtx.exitScope();

        // Build the function bytecode
        Bytecode functionBytecode = funcCtx.emitter.build(localCount, localVarNames);

        // Function name (already extracted at start of method)

        // Detect "use strict" directive in function body
        // Combine inherited strict mode with local "use strict" directive
        boolean isStrict = funcCtx.strictMode || ctx.hasUseStrictDirective(funcDecl.body());

        // Extract function source code from original source
        String functionSource = ctx.extractSourceCode(funcDecl.getLocation());

        // Trim trailing whitespace from the extracted source
        // This is needed because the parser's end offset may include whitespace after the closing brace
        if (functionSource != null) {
            functionSource = functionSource.stripTrailing();
        }

        // If extraction failed, build a simplified representation
        if (functionSource == null || functionSource.isEmpty()) {
            StringBuilder funcSource = new StringBuilder();
            if (funcDecl.isAsync()) { funcSource.append("async "); }
            funcSource.append("function");
            if (funcDecl.isGenerator()) { funcSource.append("*"); }
            funcSource.append(" ").append(functionName).append("(");
            for (int i = 0; i < funcDecl.params().size(); i++) {
                if (i > 0) { funcSource.append(", "); }
                funcSource.append(funcDecl.params().get(i).name());
            }
            if (funcDecl.restParameter() != null) {
                if (!funcDecl.params().isEmpty()) { funcSource.append(", "); }
                funcSource.append("...").append(funcDecl.restParameter().argument().name());
            }
            funcSource.append(") { [function body] }");
            functionSource = funcSource.toString();
        }

        // Check if the function captures its own name (e.g., block-scoped function declaration
        // where the body references f). This fixes the chicken-and-egg problem where FCLOSURE
        // captures the local before the function is stored in it.
        // Following QuickJS var_refs pattern: the closure variable pointing to the function
        // itself is patched after creation via selfCaptureIndex metadata.
        Integer selfCaptureIdx = funcCtx.findCapturedBindingIndex(functionName);
        int selfCaptureIndex = selfCaptureIdx != null ? selfCaptureIdx : -1;

        // Create JSBytecodeFunction
        int definedArgCount = CompilerContext.computeDefinedArgCount(funcDecl.params(), funcDecl.defaults(), funcDecl.restParameter() != null);
        // Per ES spec FunctionAllocate: async functions and async generators are NOT constructable
        boolean isFuncConstructor = !funcDecl.isAsync();
        JSBytecodeFunction function = new JSBytecodeFunction(
                functionBytecode,
                functionName,
                definedArgCount,
                new JSValue[0],
                null,            // prototype - will be set by VM
                isFuncConstructor,
                funcDecl.isAsync(),
                funcDecl.isGenerator(),
                false,           // isArrow - regular function, not arrow
                isStrict,        // strict - detected from "use strict" directive in function body
                functionSource,  // source code for toString()
                selfCaptureIndex // closure self-reference index (-1 if none)
        );
        function.setHasParameterExpressions(CompilerContext.hasNonSimpleParameters(funcDecl.defaults(), funcDecl.restParameter()));

        delegates.emitHelpers.emitCapturedValues(functionCompiler, function);
        // Emit FCLOSURE opcode with function in constant pool
        ctx.emitter.emitOpcodeConstant(Opcode.FCLOSURE, function);

        // Store the function in a variable with its name
        // Per B.3.3.1: the Annex B runtime hook only fires if no enclosing block
        // scope has a lexical binding for the same name (otherwise replacing this
        // function with var F would produce an Early Error).
        boolean isAnnexB = ctx.annexBFunctionNames.contains(functionName)
                && !ctx.hasEnclosingBlockScopeLocal(functionName);
        Integer localIndex = ctx.findLocalInScopes(functionName);
        if (localIndex != null) {
            if (isAnnexB) {
                // Annex B.3.3 runtime hook: store in both block scope and var scope
                ctx.emitter.emitOpcode(Opcode.DUP);
                ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
                delegates.emitHelpers.emitAnnexBVarStore(functionName);
            } else {
                ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
            }
        } else {
            // Declare the function as a global variable or in the current scope
            if (ctx.inGlobalScope) {
                ctx.nonDeletableGlobalBindings.add(functionName);
                ctx.emitter.emitOpcodeAtom(Opcode.PUT_VAR, functionName);
            } else {
                // Declare it as a local
                localIndex = ctx.currentScope().declareLocal(functionName);
                if (isAnnexB) {
                    // Annex B.3.3 runtime hook: store in both block scope and var scope
                    ctx.emitter.emitOpcode(Opcode.DUP);
                    ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
                    delegates.emitHelpers.emitAnnexBVarStore(functionName);
                } else {
                    ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
                }
            }
        }
    }

    void compileFunctionExpression(FunctionExpression funcExpr) {
        // Create a new compiler for the function body
        // Nested functions inherit strict mode from parent (QuickJS behavior)
        BytecodeCompiler functionCompiler = new BytecodeCompiler(ctx.strictMode, ctx.captureResolver);
        CompilerContext funcCtx = functionCompiler.context();
        CompilerDelegates funcDelegates = functionCompiler.delegates();
        funcCtx.nonDeletableGlobalBindings.addAll(ctx.nonDeletableGlobalBindings);

        // Enter function scope and add parameters as locals
        funcCtx.enterScope();
        funcCtx.inGlobalScope = false;
        funcCtx.isInAsyncFunction = funcExpr.isAsync();

        // Check for "use strict" directive early and update strict mode
        // This ensures nested functions inherit the correct strict mode
        if (ctx.hasUseStrictDirective(funcExpr.body())) {
            funcCtx.strictMode = true;
        }

        for (Identifier param : funcExpr.params()) {
            funcCtx.currentScope().declareParameter(param.name());
        }

        // Emit default parameter initialization following QuickJS pattern
        if (funcExpr.defaults() != null) {
            delegates.emitHelpers.emitDefaultParameterInit(functionCompiler, funcExpr.defaults());
        }

        // Handle rest parameter if present
        // The REST opcode must be emitted early in the function to initialize the rest array
        if (funcExpr.restParameter() != null) {
            // Calculate the index where rest arguments start
            int firstRestIndex = funcExpr.params().size();

            // Emit REST opcode with the starting index
            funcCtx.emitter.emitOpcode(Opcode.REST);
            funcCtx.emitter.emitU16(firstRestIndex);

            // Declare the rest parameter as a local and store the rest array
            String restParamName = funcExpr.restParameter().argument().name();
            int restLocalIndex = funcCtx.currentScope().declareLocal(restParamName);

            // Store the rest array (from stack top) to the rest parameter local
            funcCtx.emitter.emitOpcode(Opcode.PUT_LOCAL);
            funcCtx.emitter.emitU16(restLocalIndex);
        }

        // If this is a generator function, emit INITIAL_YIELD at the start
        if (funcExpr.isGenerator()) {
            funcCtx.emitter.emitOpcode(Opcode.INITIAL_YIELD);
        }

        // Phase 0: Pre-declare all var bindings as locals before function hoisting.
        // var declarations are function-scoped and must be visible to nested function
        // declarations that may capture them. Without this, Phase 1 function hoisting
        // would fail to resolve captured var references (e.g., inner functions referencing
        // outer var variables would emit GET_VAR instead of GET_VAR_REF).
        funcDelegates.analysis.hoistVarDeclarationsAsLocals(funcExpr.body().body());

        // Phase 1: Hoist top-level function declarations (ES spec requires function
        // declarations to be initialized before any code executes).
        for (Statement stmt : funcExpr.body().body()) {
            if (stmt instanceof FunctionDeclaration funcDecl) {
                funcDelegates.functions.compileFunctionDeclaration(funcDecl);
            }
        }

        // Annex B.3.3.1: Hoist function declarations from blocks/if-statements
        // to the function scope as var bindings (initialized to undefined).
        // Build parameterNames set (BoundNames of argumentsList + "arguments" binding)
        Set<String> exprParamNames = CompilerContext.buildParameterNames(funcExpr.params(), funcExpr.body().body());
        funcDelegates.analysis.hoistFunctionBodyAnnexBDeclarations(funcExpr.body().body(), exprParamNames);

        // Phase 2: Compile non-FunctionDeclaration statements in source order
        for (Statement stmt : funcExpr.body().body()) {
            if (stmt instanceof FunctionDeclaration) {
                continue; // Already hoisted in Phase 1
            }
            funcDelegates.statements.compileStatement(stmt);
        }

        // If body doesn't end with return, add implicit return undefined
        // Check if last statement is a return statement
        List<Statement> bodyStatements = funcExpr.body().body();
        if (bodyStatements.isEmpty() || !(bodyStatements.get(bodyStatements.size() - 1) instanceof ReturnStatement)) {
            funcCtx.emitter.emitOpcode(Opcode.UNDEFINED);
            int returnValueIndex = funcCtx.currentScope().declareLocal("$function_return_" + funcCtx.emitter.currentOffset());
            funcCtx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, returnValueIndex);
            funcDelegates.emitHelpers.emitCurrentScopeUsingDisposal();
            funcCtx.emitter.emitOpcodeU16(Opcode.GET_LOCAL, returnValueIndex);
            funcCtx.emitter.emitOpcode(funcExpr.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
        }

        int localCount = funcCtx.currentScope().getLocalCount();
        String[] localVarNames = CompilerContext.extractLocalVarNames(funcCtx.currentScope());
        funcCtx.exitScope();

        // Build the function bytecode
        Bytecode functionBytecode = funcCtx.emitter.build(localCount, localVarNames);

        // Get function name (empty string for anonymous)
        String functionName = funcExpr.id() != null ? funcExpr.id().name() : "";

        // Detect "use strict" directive in function body
        // Combine inherited strict mode with local "use strict" directive
        boolean isStrict = funcCtx.strictMode || ctx.hasUseStrictDirective(funcExpr.body());

        // Extract function source code from original source
        String functionSource = ctx.extractSourceCode(funcExpr.getLocation());

        // Create JSBytecodeFunction
        int definedArgCount = CompilerContext.computeDefinedArgCount(funcExpr.params(), funcExpr.defaults(), funcExpr.restParameter() != null);
        // Per ES spec FunctionAllocate: async functions and async generators are NOT constructable
        boolean isFuncConstructor = !funcExpr.isAsync();
        JSBytecodeFunction function = new JSBytecodeFunction(
                functionBytecode,
                functionName,
                definedArgCount,
                new JSValue[0],
                null,            // prototype - will be set by VM
                isFuncConstructor,
                funcExpr.isAsync(),
                funcExpr.isGenerator(),
                false,           // isArrow - regular function, not arrow
                isStrict,        // strict - detected from "use strict" directive in function body
                functionSource   // source code for toString()
        );
        function.setHasParameterExpressions(CompilerContext.hasNonSimpleParameters(funcExpr.defaults(), funcExpr.restParameter()));

        // Prototype chain will be initialized when the function is loaded
        // during bytecode execution (see FCLOSURE opcode handler)

        delegates.emitHelpers.emitCapturedValues(functionCompiler, function);
        // Emit FCLOSURE opcode with function in constant pool
        ctx.emitter.emitOpcodeConstant(Opcode.FCLOSURE, function);
    }

    /**
     * Compile a method definition as a function.
     * For constructors, instanceFields contains fields to initialize.
     * privateSymbols contains JSSymbol instances for private fields (passed as closure variables).
     */
    JSBytecodeFunction compileMethodAsFunction(
            ClassDeclaration.MethodDefinition method,
            String methodName,
            boolean isDerivedConstructor,
            List<ClassDeclaration.PropertyDefinition> instanceFields,
            Map<String, JSSymbol> privateSymbols,
            IdentityHashMap<ClassDeclaration.PropertyDefinition, JSSymbol> computedFieldSymbols,
            Map<String, JSBytecodeFunction> privateInstanceMethodFunctions,
            boolean isConstructor) {
        BytecodeCompiler methodCompiler = new BytecodeCompiler();
        CompilerContext methodCtx = methodCompiler.context();
        CompilerDelegates methodDelegates = methodCompiler.delegates();
        methodCtx.nonDeletableGlobalBindings.addAll(ctx.nonDeletableGlobalBindings);
        methodCtx.privateSymbols = privateSymbols;  // Make private symbols available in method

        FunctionExpression funcExpr = method.value();

        // Enter function scope and add parameters as locals
        methodCtx.enterScope();
        methodCtx.inGlobalScope = false;
        methodCtx.isInAsyncFunction = funcExpr.isAsync();

        for (Identifier param : funcExpr.params()) {
            methodCtx.currentScope().declareParameter(param.name());
        }

        // Emit default parameter initialization following QuickJS pattern
        if (funcExpr.defaults() != null) {
            delegates.emitHelpers.emitDefaultParameterInit(methodCompiler, funcExpr.defaults());
        }

        // If this is a generator method, emit INITIAL_YIELD at the start
        if (funcExpr.isGenerator()) {
            methodCtx.emitter.emitOpcode(Opcode.INITIAL_YIELD);
        }

        // For constructors, initialize private methods then fields before user code runs.
        if (isConstructor) {
            if (!privateInstanceMethodFunctions.isEmpty()) {
                methodDelegates.functions.compilePrivateMethodInitialization(privateInstanceMethodFunctions, privateSymbols);
            }
            if (!instanceFields.isEmpty()) {
                methodDelegates.functions.compileFieldInitialization(instanceFields, privateSymbols, computedFieldSymbols);
            }
        }

        // Compile method body statements
        for (Statement stmt : funcExpr.body().body()) {
            methodDelegates.statements.compileStatement(stmt);
        }

        // If body doesn't end with return, add implicit return undefined
        List<Statement> bodyStatements = funcExpr.body().body();
        if (bodyStatements.isEmpty() || !(bodyStatements.get(bodyStatements.size() - 1) instanceof ReturnStatement)) {
            methodCtx.emitter.emitOpcode(Opcode.UNDEFINED);
            int returnValueIndex = methodCtx.currentScope().declareLocal("$method_return_" + methodCtx.emitter.currentOffset());
            methodCtx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, returnValueIndex);
            methodDelegates.emitHelpers.emitCurrentScopeUsingDisposal();
            methodCtx.emitter.emitOpcodeU16(Opcode.GET_LOCAL, returnValueIndex);
            methodCtx.emitter.emitOpcode(funcExpr.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
        }

        int localCount = methodCtx.currentScope().getLocalCount();
        methodCtx.exitScope();

        // Build the method bytecode
        Bytecode methodBytecode = methodCtx.emitter.build(localCount);

        // Convert private symbols to closure variable array
        JSValue[] closureVars = new JSValue[privateSymbols.size()];
        int idx = 0;
        for (JSSymbol symbol : privateSymbols.values()) {
            closureVars[idx++] = symbol;
        }

        // Create JSBytecodeFunction for the method
        int definedArgCount = CompilerContext.computeDefinedArgCount(funcExpr.params(), funcExpr.defaults(), funcExpr.restParameter() != null);
        JSBytecodeFunction methodFunc = new JSBytecodeFunction(
                methodBytecode,
                methodName,
                definedArgCount,
                closureVars,     // closure vars contain private symbols
                null,            // prototype
                isConstructor,   // isConstructor - true for class constructors, false for methods
                funcExpr.isAsync(),
                funcExpr.isGenerator(),
                false,           // isArrow - methods are not arrow functions
                true,            // strict - classes are always strict mode
                "method " + methodName + "() { [method body] }"  // source for toString
        );
        methodFunc.setHasParameterExpressions(CompilerContext.hasNonSimpleParameters(funcExpr.defaults(), funcExpr.restParameter()));
        return methodFunc;
    }

    LinkedHashMap<String, JSBytecodeFunction> compilePrivateMethodFunctions(
            List<ClassDeclaration.MethodDefinition> privateMethods,
            Map<String, JSSymbol> privateSymbols,
            IdentityHashMap<ClassDeclaration.PropertyDefinition, JSSymbol> computedFieldSymbols) {
        LinkedHashMap<String, JSBytecodeFunction> privateMethodFunctions = new LinkedHashMap<>();
        for (ClassDeclaration.MethodDefinition method : privateMethods) {
            String methodName = ctx.getMethodName(method);
            JSBytecodeFunction methodFunc = compileMethodAsFunction(
                    method,
                    methodName,
                    false,
                    List.of(),
                    privateSymbols,
                    computedFieldSymbols,
                    Map.of(),
                    false
            );
            privateMethodFunctions.put(methodName, methodFunc);
        }
        return privateMethodFunctions;
    }

    void compilePrivateMethodInitialization(
            Map<String, JSBytecodeFunction> privateMethodFunctions,
            Map<String, JSSymbol> privateSymbols) {
        for (Map.Entry<String, JSBytecodeFunction> entry : privateMethodFunctions.entrySet()) {
            JSSymbol symbol = privateSymbols.get(entry.getKey());
            if (symbol == null) {
                throw new JSCompilerException("Private method symbol not found: #" + entry.getKey());
            }
            ctx.emitter.emitOpcode(Opcode.PUSH_THIS);
            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, entry.getValue());
            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
            ctx.emitter.emitOpcode(Opcode.SWAP);
            ctx.emitter.emitOpcode(Opcode.DEFINE_PRIVATE_FIELD);
            ctx.emitter.emitOpcode(Opcode.DROP);
        }
    }

    /**
     * Compile a static block as a function.
     * Static blocks are executed immediately after class definition with the class constructor as 'this'.
     */
    JSBytecodeFunction compileStaticBlock(
            ClassDeclaration.StaticBlock staticBlock,
            String className,
            Map<String, JSSymbol> privateSymbols) {
        BytecodeCompiler blockCompiler = new BytecodeCompiler();
        CompilerContext blockCtx = blockCompiler.context();
        CompilerDelegates blockDelegates = blockCompiler.delegates();
        blockCtx.nonDeletableGlobalBindings.addAll(ctx.nonDeletableGlobalBindings);
        blockCtx.privateSymbols = privateSymbols;

        blockCtx.enterScope();
        blockCtx.inGlobalScope = false;

        // Compile all statements in the static block
        for (Statement stmt : staticBlock.body()) {
            blockDelegates.statements.compileStatement(stmt);
        }

        // Static blocks always return undefined
        blockCtx.emitter.emitOpcode(Opcode.UNDEFINED);
        int returnValueIndex = blockCtx.currentScope().declareLocal("$static_block_return_" + blockCtx.emitter.currentOffset());
        blockCtx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, returnValueIndex);
        blockDelegates.emitHelpers.emitCurrentScopeUsingDisposal();
        blockCtx.emitter.emitOpcodeU16(Opcode.GET_LOCAL, returnValueIndex);
        blockCtx.emitter.emitOpcode(Opcode.RETURN);

        int localCount = blockCtx.currentScope().getLocalCount();
        blockCtx.exitScope();

        Bytecode blockBytecode = blockCtx.emitter.build(localCount);

        return new JSBytecodeFunction(
                blockBytecode,
                "<static initializer>",  // Static blocks are anonymous
                0,                        // no parameters
                new JSValue[0],           // no closure vars
                null,                     // no prototype
                false,                    // not a constructor
                false,                    // not async
                false,                    // not generator
                false,                    // isArrow - static initializers are not arrows
                true,                     // strict mode
                "static { [initializer] }"
        );
    }

    /**
     * Compile a static field initializer as a function and return it.
     * The function is called with class constructor as `this`.
     */
    JSBytecodeFunction compileStaticFieldInitializer(
            ClassDeclaration.PropertyDefinition field,
            IdentityHashMap<ClassDeclaration.PropertyDefinition, JSSymbol> computedFieldSymbols,
            Map<String, JSSymbol> privateSymbols,
            String className) {
        BytecodeCompiler initializerCompiler = new BytecodeCompiler();
        CompilerContext initCtx = initializerCompiler.context();
        CompilerDelegates initDelegates = initializerCompiler.delegates();
        initCtx.nonDeletableGlobalBindings.addAll(ctx.nonDeletableGlobalBindings);
        initCtx.privateSymbols = privateSymbols;

        initCtx.enterScope();
        initCtx.inGlobalScope = false;

        // Stack: this
        initCtx.emitter.emitOpcode(Opcode.PUSH_THIS);

        if (field.isPrivate()) {
            if (!(field.key() instanceof PrivateIdentifier privateId)) {
                throw new JSCompilerException("Invalid static private field key");
            }

            JSSymbol symbol = privateSymbols.get(privateId.name());
            if (symbol == null) {
                throw new JSCompilerException("Static private field symbol not found: #" + privateId.name());
            }

            if (field.value() != null) {
                initDelegates.expressions.compileExpression(field.value());
            } else {
                initCtx.emitter.emitOpcode(Opcode.UNDEFINED);
            }

            // Stack: this value symbol -> this symbol value
            initCtx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
            initCtx.emitter.emitOpcode(Opcode.SWAP);
            initCtx.emitter.emitOpcode(Opcode.DEFINE_PRIVATE_FIELD);
        } else {
            if (field.computed()) {
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
                initDelegates.emitHelpers.emitNonComputedPublicFieldKey(field.key());
            }

            if (field.value() != null) {
                initDelegates.expressions.compileExpression(field.value());
            } else {
                initCtx.emitter.emitOpcode(Opcode.UNDEFINED);
            }

            // Stack: this key value
            initCtx.emitter.emitOpcode(Opcode.DEFINE_PROP);
        }
        // Stack: this
        initCtx.emitter.emitOpcode(Opcode.DROP);
        initCtx.emitter.emitOpcode(Opcode.UNDEFINED);
        initCtx.emitter.emitOpcode(Opcode.RETURN);

        int localCount = initCtx.currentScope().getLocalCount();
        initCtx.exitScope();
        Bytecode initializerBytecode = initCtx.emitter.build(localCount);

        return new JSBytecodeFunction(
                initializerBytecode,
                "<static field initializer>",
                0,
                new JSValue[0],
                null,
                false,
                false,
                false,
                false,
                true,
                "static field initializer for " + className
        );
    }

    /**
     * Create a default constructor for a class.
     */
    JSBytecodeFunction createDefaultConstructor(
            String className,
            boolean hasSuper,
            List<ClassDeclaration.PropertyDefinition> instanceFields,
            Map<String, JSSymbol> privateSymbols,
            IdentityHashMap<ClassDeclaration.PropertyDefinition, JSSymbol> computedFieldSymbols,
            Map<String, JSBytecodeFunction> privateInstanceMethodFunctions) {
        BytecodeCompiler constructorCompiler = new BytecodeCompiler();
        CompilerContext ctorCtx = constructorCompiler.context();
        CompilerDelegates ctorDelegates = constructorCompiler.delegates();
        ctorCtx.nonDeletableGlobalBindings.addAll(ctx.nonDeletableGlobalBindings);
        ctorCtx.privateSymbols = privateSymbols;  // Make private symbols available

        ctorCtx.enterScope();
        ctorCtx.inGlobalScope = false;

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
            ctorDelegates.functions.compileFieldInitialization(instanceFields, privateSymbols, computedFieldSymbols);
        }

        ctorCtx.emitter.emitOpcode(hasSuper ? Opcode.PUSH_THIS : Opcode.UNDEFINED);
        ctorCtx.emitter.emitOpcode(Opcode.RETURN);

        int localCount = ctorCtx.currentScope().getLocalCount();
        ctorCtx.exitScope();

        Bytecode constructorBytecode = ctorCtx.emitter.build(localCount);

        return new JSBytecodeFunction(
                constructorBytecode,
                className,
                0,               // no parameters
                new JSValue[0],  // no closure vars
                null,            // prototype will be set by VM
                true,            // isConstructor
                false,           // not async
                false,           // not generator
                false,           // isArrow - constructors are not arrows
                true,            // strict mode
                "constructor() { [default] }"
        );
    }

    Map<String, JSSymbol> createPrivateSymbols(List<ClassDeclaration.ClassElement> classElements) {
        LinkedHashMap<String, String> privateNameKinds = new LinkedHashMap<>();
        for (ClassDeclaration.ClassElement element : classElements) {
            if (element instanceof ClassDeclaration.PropertyDefinition field) {
                if (field.isPrivate() && field.key() instanceof PrivateIdentifier privateId) {
                    registerPrivateName(privateNameKinds, privateId.name(), "field");
                }
            } else if (element instanceof ClassDeclaration.MethodDefinition method) {
                if (method.isPrivate() && method.key() instanceof PrivateIdentifier privateId) {
                    registerPrivateName(privateNameKinds, privateId.name(), method.kind());
                }
            }
        }
        LinkedHashMap<String, JSSymbol> privateSymbols = new LinkedHashMap<>();
        for (String privateName : privateNameKinds.keySet()) {
            privateSymbols.put(privateName, new JSSymbol(privateName));
        }
        return privateSymbols;
    }

    JSArray createTaggedTemplateObject(TemplateLiteral template) {
        List<String> cookedQuasis = template.quasis();
        List<String> rawQuasis = template.rawQuasis();
        int segmentCount = rawQuasis.size();

        JSArray templateObject = new JSArray();
        JSArray rawArray = new JSArray();

        for (int i = 0; i < segmentCount; i++) {
            JSString rawValue = new JSString(rawQuasis.get(i));
            rawArray.set(i, rawValue);
            rawArray.defineProperty(
                    PropertyKey.fromIndex(i),
                    PropertyDescriptor.dataDescriptor(rawValue, false, true, false));

            String cookedQuasi = cookedQuasis.get(i);
            JSValue cookedValue = cookedQuasi == null ? JSUndefined.INSTANCE : new JSString(cookedQuasi);
            templateObject.set(i, cookedValue);
            templateObject.defineProperty(
                    PropertyKey.fromIndex(i),
                    PropertyDescriptor.dataDescriptor(cookedValue, false, true, false));
        }

        // QuickJS/spec attributes for template objects.
        rawArray.definePropertyReadonlyNonConfigurable("length", JSNumber.of(segmentCount));
        templateObject.definePropertyReadonlyNonConfigurable("length", JSNumber.of(segmentCount));
        templateObject.definePropertyReadonlyNonConfigurable("raw", rawArray);

        rawArray.freeze();
        templateObject.freeze();
        return templateObject;
    }

    void installPrivateStaticMethods(
            Map<String, JSBytecodeFunction> privateStaticMethodFunctions,
            Map<String, JSSymbol> privateSymbols) {
        for (Map.Entry<String, JSBytecodeFunction> entry : privateStaticMethodFunctions.entrySet()) {
            JSSymbol symbol = privateSymbols.get(entry.getKey());
            if (symbol == null) {
                throw new JSCompilerException("Private static method symbol not found: #" + entry.getKey());
            }

            // Stack before: constructor proto
            ctx.emitter.emitOpcode(Opcode.SWAP); // proto constructor
            ctx.emitter.emitOpcode(Opcode.DUP);  // proto constructor constructor
            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, entry.getValue()); // proto constructor constructor method
            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol); // proto constructor constructor method symbol
            ctx.emitter.emitOpcode(Opcode.SWAP); // proto constructor constructor symbol method
            ctx.emitter.emitOpcode(Opcode.DEFINE_PRIVATE_FIELD); // proto constructor constructor
            ctx.emitter.emitOpcode(Opcode.DROP); // proto constructor
            ctx.emitter.emitOpcode(Opcode.SWAP); // constructor proto
        }
    }

    void registerPrivateName(Map<String, String> privateNameKinds, String privateName, String kind) {
        String existingKind = privateNameKinds.get(privateName);
        if (existingKind == null) {
            privateNameKinds.put(privateName, kind);
            return;
        }
        boolean isGetterSetterPair =
                ("get".equals(existingKind) && "set".equals(kind))
                        || ("set".equals(existingKind) && "get".equals(kind));
        if (isGetterSetterPair) {
            privateNameKinds.put(privateName, "accessor");
            return;
        }
        throw new JSCompilerException("private class field is already defined");
    }
}
