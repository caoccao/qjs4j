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

package com.caoccao.qjs4j.compiler;

import com.caoccao.qjs4j.compiler.ast.*;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.vm.Bytecode;
import com.caoccao.qjs4j.vm.Opcode;

import java.math.BigInteger;
import java.util.*;

/**
 * Compiles AST into bytecode.
 * Implements visitor pattern for traversing AST nodes and emitting appropriate bytecode.
 */
public final class BytecodeCompiler {
    private final BytecodeEmitter emitter;
    private final Deque<LoopContext> loopStack;
    private final Deque<Scope> scopes;
    private boolean inGlobalScope;
    private boolean isInAsyncFunction;  // Track if we're currently compiling an async function
    private int maxLocalCount;
    private Map<String, JSSymbol> privateSymbols;  // Private field symbols for current class
    private String sourceCode;  // Original source code for extracting function sources

    public BytecodeCompiler() {
        this.emitter = new BytecodeEmitter();
        this.scopes = new ArrayDeque<>();
        this.loopStack = new ArrayDeque<>();
        this.inGlobalScope = false;
        this.isInAsyncFunction = false;
        this.maxLocalCount = 0;
        this.sourceCode = null;
        this.privateSymbols = Map.of();  // Empty by default
    }

    /**
     * Compile an AST node into bytecode.
     */
    public Bytecode compile(ASTNode ast) {
        if (ast instanceof Program program) {
            compileProgram(program);
        } else {
            throw new IllegalArgumentException("Expected Program node, got: " + ast.getClass().getName());
        }
        return emitter.build(maxLocalCount);
    }

    private void compileArrayExpression(ArrayExpression arrayExpr) {
        emitter.emitOpcode(Opcode.ARRAY_NEW);

        for (Expression element : arrayExpr.elements()) {
            if (element != null) {
                compileExpression(element);
                emitter.emitOpcode(Opcode.PUSH_ARRAY);
            }
        }
    }

    private void compileArrowFunctionExpression(ArrowFunctionExpression arrowExpr) {
        // Create a new compiler for the function body
        BytecodeCompiler functionCompiler = new BytecodeCompiler();

        // Enter function scope and add parameters as locals
        functionCompiler.enterScope();
        functionCompiler.inGlobalScope = false;
        functionCompiler.isInAsyncFunction = arrowExpr.isAsync();  // Track if this is an async function

        for (Identifier param : arrowExpr.params()) {
            functionCompiler.currentScope().declareLocal(param.name());
        }

        // Compile function body
        // Arrow functions can have expression body or block statement body
        if (arrowExpr.body() instanceof BlockStatement block) {
            // Compile block body statements (don't call compileBlockStatement as it would create a new scope)
            for (Statement stmt : block.body()) {
                functionCompiler.compileStatement(stmt);
            }

            // If body doesn't end with return, add implicit return undefined
            List<Statement> bodyStatements = block.body();
            if (bodyStatements.isEmpty() || !(bodyStatements.get(bodyStatements.size() - 1) instanceof ReturnStatement)) {
                functionCompiler.emitter.emitOpcode(Opcode.UNDEFINED);
                // Emit RETURN_ASYNC for async functions, RETURN for sync functions
                functionCompiler.emitter.emitOpcode(arrowExpr.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
            }
        } else if (arrowExpr.body() instanceof Expression expr) {
            // Expression body - implicitly returns the expression value
            functionCompiler.compileExpression(expr);
            // Emit RETURN_ASYNC for async functions, RETURN for sync functions
            functionCompiler.emitter.emitOpcode(arrowExpr.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
        }

        int localCount = functionCompiler.currentScope().getLocalCount();
        functionCompiler.exitScope();

        // Build the function bytecode
        Bytecode functionBytecode = functionCompiler.emitter.build(localCount);

        // Arrow functions are always anonymous
        String functionName = "";

        // Extract function source code from original source
        String functionSource = extractSourceCode(arrowExpr.getLocation());

        // Create JSBytecodeFunction
        // Arrow functions cannot be constructors
        JSBytecodeFunction function = new JSBytecodeFunction(
                functionBytecode,
                functionName,
                arrowExpr.params().size(),
                new JSValue[0],  // closure vars - for now empty
                null,            // prototype - arrow functions don't have prototype
                false,           // isConstructor - arrow functions cannot be constructors
                arrowExpr.isAsync(),
                false,           // Arrow functions cannot be generators
                false,           // strict - TODO: inherit from enclosing scope
                functionSource   // source code for toString()
        );

        // Prototype chain will be initialized when the function is loaded
        // during bytecode execution (see FCLOSURE opcode handler)

        // Emit FCLOSURE opcode with function in constant pool
        emitter.emitOpcodeConstant(Opcode.FCLOSURE, function);
    }

    // ==================== Program Compilation ====================

    private void compileAssignmentExpression(AssignmentExpression assignExpr) {
        Expression left = assignExpr.left();
        AssignmentExpression.AssignmentOperator operator = assignExpr.operator();

        // Handle logical assignment operators (&&=, ||=, ??=) with short-circuit evaluation
        if (operator == AssignmentExpression.AssignmentOperator.LOGICAL_AND_ASSIGN ||
                operator == AssignmentExpression.AssignmentOperator.LOGICAL_OR_ASSIGN ||
                operator == AssignmentExpression.AssignmentOperator.NULLISH_ASSIGN) {
            compileLogicalAssignment(assignExpr);
            return;
        }

        // For compound assignments (+=, -=, etc.), we need to load the current value first
        if (operator != AssignmentExpression.AssignmentOperator.ASSIGN) {
            // Load current value of left side
            if (left instanceof Identifier id) {
                String name = id.name();
                Integer localIndex = findLocalInScopes(name);
                if (localIndex != null) {
                    emitter.emitOpcodeU16(Opcode.GET_LOCAL, localIndex);
                } else {
                    emitter.emitOpcodeAtom(Opcode.GET_VAR, name);
                }
            } else if (left instanceof MemberExpression memberExpr) {
                // For obj.prop += value, we need DUP2 pattern or similar
                compileExpression(memberExpr.object());
                if (memberExpr.computed()) {
                    compileExpression(memberExpr.property());
                    emitter.emitOpcode(Opcode.DUP2);  // Duplicate obj and prop
                    emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                    // obj.#field += value
                    String fieldName = privateId.name();
                    JSSymbol symbol = privateSymbols != null ? privateSymbols.get(fieldName) : null;
                    if (symbol != null) {
                        emitter.emitOpcode(Opcode.DUP);  // Duplicate object
                        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                        emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
                    } else {
                        emitter.emitOpcode(Opcode.UNDEFINED);
                    }
                } else if (memberExpr.property() instanceof Identifier propId) {
                    emitter.emitOpcode(Opcode.DUP);  // Duplicate object
                    emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
                }
            }

            // Compile right side
            compileExpression(assignExpr.right());

            // Perform the compound operation
            switch (operator) {
                case PLUS_ASSIGN -> emitter.emitOpcode(Opcode.ADD);
                case MINUS_ASSIGN -> emitter.emitOpcode(Opcode.SUB);
                case MUL_ASSIGN -> emitter.emitOpcode(Opcode.MUL);
                case DIV_ASSIGN -> emitter.emitOpcode(Opcode.DIV);
                case MOD_ASSIGN -> emitter.emitOpcode(Opcode.MOD);
                case EXP_ASSIGN -> emitter.emitOpcode(Opcode.EXP);
                case LSHIFT_ASSIGN -> emitter.emitOpcode(Opcode.SHL);
                case RSHIFT_ASSIGN -> emitter.emitOpcode(Opcode.SAR);
                case URSHIFT_ASSIGN -> emitter.emitOpcode(Opcode.SHR);
                case AND_ASSIGN -> emitter.emitOpcode(Opcode.AND);
                case OR_ASSIGN -> emitter.emitOpcode(Opcode.OR);
                case XOR_ASSIGN -> emitter.emitOpcode(Opcode.XOR);
                default -> throw new CompilerException("Unknown assignment operator: " + operator);
            }
        } else {
            // Simple assignment: compile right side
            compileExpression(assignExpr.right());
        }

        // Store the result to left side
        if (left instanceof Identifier id) {
            String name = id.name();
            Integer localIndex = findLocalInScopes(name);

            if (localIndex != null) {
                emitter.emitOpcodeU16(Opcode.SET_LOCAL, localIndex);
            } else {
                emitter.emitOpcodeAtom(Opcode.SET_VAR, name);
            }
        } else if (left instanceof MemberExpression memberExpr) {
            // obj[prop] = value or obj.prop = value or obj.#field = value
            if (operator == AssignmentExpression.AssignmentOperator.ASSIGN) {
                // For simple assignment, compile object and property now
                compileExpression(memberExpr.object());
                if (memberExpr.computed()) {
                    compileExpression(memberExpr.property());
                    emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                    // obj.#field = value
                    // Stack: value obj
                    // Need: obj value symbol (for PUT_PRIVATE_FIELD)
                    String fieldName = privateId.name();
                    JSSymbol symbol = privateSymbols != null ? privateSymbols.get(fieldName) : null;
                    if (symbol != null) {
                        emitter.emitOpcode(Opcode.SWAP);  // Stack: obj value
                        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                        // Stack: obj value symbol
                        emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD);
                    } else {
                        // Error: private field not found - clean up stack and leave value
                        emitter.emitOpcode(Opcode.DROP);  // Drop obj, leaving value
                    }
                } else if (memberExpr.property() instanceof Identifier propId) {
                    emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name());
                }
            } else {
                // For compound assignment, object and property are already on stack from DUP
                if (memberExpr.computed()) {
                    emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                    // obj.#field += value
                    // Stack: obj value (from compound operation)
                    String fieldName = privateId.name();
                    JSSymbol symbol = privateSymbols != null ? privateSymbols.get(fieldName) : null;
                    if (symbol != null) {
                        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                        // Stack: obj value symbol
                        emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD);
                    } else {
                        // Error: private field not found - clean up stack and leave value
                        emitter.emitOpcode(Opcode.DROP);  // Drop obj, leaving value
                    }
                } else if (memberExpr.property() instanceof Identifier propId) {
                    emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name());
                }
            }
        }
    }

    private void compileAwaitExpression(AwaitExpression awaitExpr) {
        // Compile the argument expression
        compileExpression(awaitExpr.argument());

        // Emit the AWAIT opcode
        // This will convert the value to a promise (if it isn't already)
        // and pause execution until the promise resolves
        emitter.emitOpcode(Opcode.AWAIT);
    }

    // ==================== Statement Compilation ====================

    private void compileBinaryExpression(BinaryExpression binExpr) {
        // Compile operands
        compileExpression(binExpr.left());
        compileExpression(binExpr.right());

        // Emit operation
        Opcode op = switch (binExpr.operator()) {
            case ADD -> Opcode.ADD;
            case BIT_AND -> Opcode.AND;
            case BIT_OR -> Opcode.OR;
            case BIT_XOR -> Opcode.XOR;
            case DIV -> Opcode.DIV;
            case EQ -> Opcode.EQ;
            case EXP -> Opcode.EXP;
            case GE -> Opcode.GTE;
            case GT -> Opcode.GT;
            case IN -> Opcode.IN;
            case INSTANCEOF -> Opcode.INSTANCEOF;
            case LE -> Opcode.LTE;
            case LOGICAL_AND -> Opcode.LOGICAL_AND;
            case LOGICAL_OR -> Opcode.LOGICAL_OR;
            case LSHIFT -> Opcode.SHL;
            case LT -> Opcode.LT;
            case MOD -> Opcode.MOD;
            case MUL -> Opcode.MUL;
            case NE -> Opcode.NEQ;
            case NULLISH_COALESCING -> Opcode.NULLISH_COALESCE;
            case RSHIFT -> Opcode.SAR;
            case STRICT_EQ -> Opcode.STRICT_EQ;
            case STRICT_NE -> Opcode.STRICT_NEQ;
            case SUB -> Opcode.SUB;
            case URSHIFT -> Opcode.SHR;
            default -> throw new CompilerException("Unknown binary operator: " + binExpr.operator());
        };

        emitter.emitOpcode(op);
    }

    private void compileBlockStatement(BlockStatement block) {
        enterScope();
        for (Statement stmt : block.body()) {
            compileStatement(stmt);
        }
        exitScope();
    }

    private void compileBreakStatement(BreakStatement breakStmt) {
        if (loopStack.isEmpty()) {
            throw new CompilerException("Break statement outside of loop");
        }
        int jumpPos = emitter.emitJump(Opcode.GOTO);
        loopStack.peek().breakPositions.add(jumpPos);
    }

    private void compileCallExpression(CallExpression callExpr) {
        // Check if this is a method call (callee is a member expression)
        if (callExpr.callee() instanceof MemberExpression memberExpr) {
            // For method calls: obj.method()
            // We need to preserve obj as the 'this' value

            // Push object (receiver)
            compileExpression(memberExpr.object());

            // Duplicate it (one copy for 'this', one for property access)
            emitter.emitOpcode(Opcode.DUP);

            // Get the method
            if (memberExpr.computed()) {
                // obj[expr]
                compileExpression(memberExpr.property());
                emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                // obj.#privateField
                // Stack: obj obj (obj is already duplicated)
                // Need to get the private symbol and call GET_PRIVATE_FIELD
                String fieldName = privateId.name();
                JSSymbol symbol = privateSymbols != null ? privateSymbols.get(fieldName) : null;
                if (symbol != null) {
                    emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                    // Stack: obj obj symbol
                    emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
                    // Stack: obj method (GET_PRIVATE_FIELD pops symbol and one obj, pushes method)
                } else {
                    // Error: private field not found
                    // Stack: obj obj -> need to drop one obj and push undefined
                    emitter.emitOpcode(Opcode.DROP);  // Drop the duplicated obj
                    emitter.emitOpcode(Opcode.UNDEFINED);
                    // Stack: obj undefined
                }
            } else if (memberExpr.property() instanceof Identifier propId) {
                // obj.prop
                emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
            }

            // Now stack is: receiver, method
            // Swap so method is on top: method, receiver
            emitter.emitOpcode(Opcode.SWAP);

            // Push arguments
            for (Expression arg : callExpr.arguments()) {
                compileExpression(arg);
            }

            // Call with argument count (will use receiver as thisArg)
            emitter.emitOpcodeU16(Opcode.CALL, callExpr.arguments().size());
        } else {
            // Regular function call: func()
            // Push callee
            compileExpression(callExpr.callee());

            // Push undefined as receiver (thisArg for regular calls)
            emitter.emitOpcode(Opcode.UNDEFINED);

            // Push arguments
            for (Expression arg : callExpr.arguments()) {
                compileExpression(arg);
            }

            // Call with argument count
            emitter.emitOpcodeU16(Opcode.CALL, callExpr.arguments().size());
        }
    }

    private void compileClassDeclaration(ClassDeclaration classDecl) {
        // Following QuickJS implementation in quickjs.c:24700-25200

        String className = classDecl.id() != null ? classDecl.id().name() : "";

        // Compile superclass expression or emit undefined
        if (classDecl.superClass() != null) {
            compileExpression(classDecl.superClass());
        } else {
            emitter.emitOpcode(Opcode.UNDEFINED);
        }
        // Stack: superClass

        // Separate class elements by type
        List<ClassDeclaration.MethodDefinition> methods = new ArrayList<>();
        List<ClassDeclaration.PropertyDefinition> instanceFields = new ArrayList<>();
        List<ClassDeclaration.PropertyDefinition> staticFields = new ArrayList<>();
        List<ClassDeclaration.StaticBlock> staticBlocks = new ArrayList<>();
        ClassDeclaration.MethodDefinition constructor = null;

        for (ClassDeclaration.ClassElement element : classDecl.body()) {
            if (element instanceof ClassDeclaration.MethodDefinition method) {
                // Check if it's a constructor
                if (method.key() instanceof Identifier id && "constructor".equals(id.name()) && !method.isStatic()) {
                    constructor = method;
                } else {
                    methods.add(method);
                }
            } else if (element instanceof ClassDeclaration.PropertyDefinition field) {
                if (field.isStatic()) {
                    staticFields.add(field);
                } else {
                    instanceFields.add(field);
                }
            } else if (element instanceof ClassDeclaration.StaticBlock block) {
                staticBlocks.add(block);
            }
        }

        // Create private symbols once for the class (not per instance)
        // These symbols will be passed as closure variables to all methods
        List<String> privateFieldNames = instanceFields.stream()
                .filter(ClassDeclaration.PropertyDefinition::isPrivate)
                .map(field -> {
                    if (field.key() instanceof PrivateIdentifier privateId) {
                        return privateId.name();
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();

        // Create JSSymbol instances for each private field
        // These symbols are created once and shared across all instances
        Map<String, JSSymbol> privateSymbols = new LinkedHashMap<>();
        for (String privateFieldName : privateFieldNames) {
            privateSymbols.put(privateFieldName, new JSSymbol(privateFieldName));
        }

        // Compile constructor function (or create default) with field initialization
        JSBytecodeFunction constructorFunc;
        if (constructor != null) {
            constructorFunc = compileMethodAsFunction(constructor, className, classDecl.superClass() != null, instanceFields, privateSymbols, true);
        } else {
            // Create default constructor with field initialization
            constructorFunc = createDefaultConstructor(className, classDecl.superClass() != null, instanceFields, privateSymbols);
        }

        // Set the source code for the constructor to be the entire class definition
        // This matches JavaScript behavior where class.toString() returns the class source
        if (sourceCode != null && classDecl.location() != null) {
            int startPos = classDecl.location().offset();
            int endPos = classDecl.location().endOffset();
            if (startPos >= 0 && endPos <= sourceCode.length()) {
                String classSource = sourceCode.substring(startPos, endPos);
                constructorFunc.setSourceCode(classSource);
            }
        }

        // Emit constructor in constant pool
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, constructorFunc);
        // Stack: superClass constructor

        // Emit DEFINE_CLASS opcode with class name
        emitter.emitOpcodeAtom(Opcode.DEFINE_CLASS, className);
        // Stack: proto constructor

        // Now compile methods and add them to the prototype
        // After DEFINE_CLASS: Stack is proto constructor (constructor on TOP)
        // For simplicity, swap so proto is on top: constructor proto
        emitter.emitOpcode(Opcode.SWAP);
        // Stack: constructor proto

        for (ClassDeclaration.MethodDefinition method : methods) {
            // Stack before each iteration: constructor proto

            if (method.isStatic()) {
                // For static methods, constructor is the target
                // Current: constructor proto
                // Need to add method to constructor, so swap to get constructor on top
                emitter.emitOpcode(Opcode.SWAP);
                // Stack: proto constructor

                // Compile method (no field initialization for regular methods)
                JSBytecodeFunction methodFunc = compileMethodAsFunction(method, getMethodName(method), false, List.of(), Map.of(), false);
                emitter.emitOpcodeConstant(Opcode.PUSH_CONST, methodFunc);
                // Stack: proto constructor method

                // DEFINE_METHOD wants: obj method -> obj
                String methodName = getMethodName(method);
                emitter.emitOpcodeAtom(Opcode.DEFINE_METHOD, methodName);
                // Stack: proto constructor (method added to constructor)

                // Swap back to restore order: constructor proto
                emitter.emitOpcode(Opcode.SWAP);
                // Stack: constructor proto
            } else {
                // For instance methods, proto is the target
                // Current: constructor proto
                // Compile method (no field initialization for regular methods)
                // Pass private symbols to methods so they can access private fields
                JSBytecodeFunction methodFunc = compileMethodAsFunction(method, getMethodName(method), false, List.of(), privateSymbols, false);
                emitter.emitOpcodeConstant(Opcode.PUSH_CONST, methodFunc);
                // Stack: constructor proto method

                // DEFINE_METHOD wants: obj method -> obj
                // We have: constructor proto method
                // We want proto and method together: constructor proto method (already correct!)
                // But after DEFINE_METHOD we'll have: constructor proto
                // which is what we want!

                String methodName = getMethodName(method);
                emitter.emitOpcodeAtom(Opcode.DEFINE_METHOD, methodName);
                // Stack: constructor proto (method added to proto)
            }
        }

        // Swap back to original order: proto constructor
        emitter.emitOpcode(Opcode.SWAP);
        // Stack: proto constructor

        // Execute static blocks
        // Following QuickJS pattern: compile each static block as a function,
        // then call it with the constructor as 'this'
        for (ClassDeclaration.StaticBlock staticBlock : staticBlocks) {
            // Compile the static block as a function
            JSBytecodeFunction staticBlockFunc = compileStaticBlock(staticBlock, className);

            // Stack: proto constructor
            // DUP the constructor to use as 'this'
            emitter.emitOpcode(Opcode.DUP);
            // Stack: proto constructor constructor

            // Push the static block function
            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, staticBlockFunc);
            // Stack: proto constructor constructor func

            // SWAP so we have: proto constructor func constructor
            emitter.emitOpcode(Opcode.SWAP);
            // Stack: proto constructor func constructor

            // Call the function with 0 arguments, using constructor as 'this'
            emitter.emitOpcodeU16(Opcode.CALL, 0);
            // Stack: proto constructor returnValue

            // Drop the return value
            emitter.emitOpcode(Opcode.DROP);
            // Stack: proto constructor
        }

        // Drop prototype, keep constructor
        emitter.emitOpcode(Opcode.NIP);
        // Stack: constructor

        // Store the class constructor in a variable
        if (classDecl.id() != null) {
            String varName = classDecl.id().name();
            if (!inGlobalScope) {
                currentScope().declareLocal(varName);
                Integer localIndex = currentScope().getLocal(varName);
                if (localIndex != null) {
                    emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
                }
            } else {
                emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
            }
        } else {
            // Anonymous class expression - leave on stack
            // For class declarations, we always have a name, so this shouldn't happen
        }
    }

    private void compileClassExpression(ClassExpression classExpr) {
        // Class expressions are almost identical to class declarations,
        // but they leave the constructor on the stack instead of binding it to a variable

        String className = classExpr.id() != null ? classExpr.id().name() : "";

        // Compile superclass expression or emit undefined
        if (classExpr.superClass() != null) {
            compileExpression(classExpr.superClass());
        } else {
            emitter.emitOpcode(Opcode.UNDEFINED);
        }
        // Stack: superClass

        // Separate class elements by type
        List<ClassDeclaration.MethodDefinition> methods = new ArrayList<>();
        List<ClassDeclaration.PropertyDefinition> instanceFields = new ArrayList<>();
        List<ClassDeclaration.PropertyDefinition> staticFields = new ArrayList<>();
        List<ClassDeclaration.StaticBlock> staticBlocks = new ArrayList<>();
        ClassDeclaration.MethodDefinition constructor = null;

        for (ClassDeclaration.ClassElement element : classExpr.body()) {
            if (element instanceof ClassDeclaration.MethodDefinition method) {
                // Check if it's a constructor
                if (method.key() instanceof Identifier id && "constructor".equals(id.name()) && !method.isStatic()) {
                    constructor = method;
                } else {
                    methods.add(method);
                }
            } else if (element instanceof ClassDeclaration.PropertyDefinition field) {
                if (field.isStatic()) {
                    staticFields.add(field);
                } else {
                    instanceFields.add(field);
                }
            } else if (element instanceof ClassDeclaration.StaticBlock block) {
                staticBlocks.add(block);
            }
        }

        // Create private symbols once for the class
        List<String> privateFieldNames = instanceFields.stream()
                .filter(ClassDeclaration.PropertyDefinition::isPrivate)
                .map(field -> {
                    if (field.key() instanceof PrivateIdentifier privateId) {
                        return privateId.name();
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();

        Map<String, JSSymbol> privateSymbols = new LinkedHashMap<>();
        for (String privateFieldName : privateFieldNames) {
            privateSymbols.put(privateFieldName, new JSSymbol(privateFieldName));
        }

        // Compile constructor function (or create default)
        JSBytecodeFunction constructorFunc;
        if (constructor != null) {
            constructorFunc = compileMethodAsFunction(constructor, className, classExpr.superClass() != null, instanceFields, privateSymbols, true);
        } else {
            constructorFunc = createDefaultConstructor(className, classExpr.superClass() != null, instanceFields, privateSymbols);
        }

        // Set the source code for the constructor to be the entire class definition
        // This matches JavaScript behavior where class.toString() returns the class source
        if (sourceCode != null && classExpr.location() != null) {
            int startPos = classExpr.location().offset();
            int endPos = classExpr.location().endOffset();
            if (startPos >= 0 && endPos <= sourceCode.length()) {
                String classSource = sourceCode.substring(startPos, endPos);
                constructorFunc.setSourceCode(classSource);
            }
        }

        // Emit constructor in constant pool
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, constructorFunc);
        // Stack: superClass constructor

        // Emit DEFINE_CLASS opcode with class name
        emitter.emitOpcodeAtom(Opcode.DEFINE_CLASS, className);
        // Stack: proto constructor

        // Compile methods
        emitter.emitOpcode(Opcode.SWAP);
        // Stack: constructor proto

        for (ClassDeclaration.MethodDefinition method : methods) {
            if (method.isStatic()) {
                throw new CompilerException("Static methods not yet implemented");
            } else {
                JSBytecodeFunction methodFunc = compileMethodAsFunction(method, getMethodName(method), false, List.of(), privateSymbols, false);
                emitter.emitOpcodeConstant(Opcode.PUSH_CONST, methodFunc);
                // Stack: constructor proto method

                String methodName = getMethodName(method);
                emitter.emitOpcodeAtom(Opcode.DEFINE_METHOD, methodName);
                // Stack: constructor proto
            }
        }

        // Swap back to: proto constructor
        emitter.emitOpcode(Opcode.SWAP);
        // Stack: proto constructor

        // Drop prototype, keep constructor on stack
        emitter.emitOpcode(Opcode.NIP);
        // Stack: constructor

        // For class expressions, we leave the constructor on the stack
        // (unlike class declarations which bind it to a variable)
    }

    private void compileConditionalExpression(ConditionalExpression condExpr) {
        // Compile test
        compileExpression(condExpr.test());

        // Jump to alternate if false
        int jumpToAlternate = emitter.emitJump(Opcode.IF_FALSE);

        // Compile consequent
        compileExpression(condExpr.consequent());

        // Jump over alternate
        int jumpToEnd = emitter.emitJump(Opcode.GOTO);

        // Patch jump to alternate
        emitter.patchJump(jumpToAlternate, emitter.currentOffset());

        // Compile alternate
        compileExpression(condExpr.alternate());

        // Patch jump to end
        emitter.patchJump(jumpToEnd, emitter.currentOffset());
    }

    private void compileContinueStatement(ContinueStatement contStmt) {
        if (loopStack.isEmpty()) {
            throw new CompilerException("Continue statement outside of loop");
        }
        int jumpPos = emitter.emitJump(Opcode.GOTO);
        loopStack.peek().continuePositions.add(jumpPos);
    }

    private void compileExpression(Expression expr) {
        if (expr instanceof Literal literal) {
            compileLiteral(literal);
        } else if (expr instanceof Identifier id) {
            compileIdentifier(id);
        } else if (expr instanceof BinaryExpression binExpr) {
            compileBinaryExpression(binExpr);
        } else if (expr instanceof UnaryExpression unaryExpr) {
            compileUnaryExpression(unaryExpr);
        } else if (expr instanceof AssignmentExpression assignExpr) {
            compileAssignmentExpression(assignExpr);
        } else if (expr instanceof ConditionalExpression condExpr) {
            compileConditionalExpression(condExpr);
        } else if (expr instanceof CallExpression callExpr) {
            compileCallExpression(callExpr);
        } else if (expr instanceof MemberExpression memberExpr) {
            compileMemberExpression(memberExpr);
        } else if (expr instanceof NewExpression newExpr) {
            compileNewExpression(newExpr);
        } else if (expr instanceof FunctionExpression funcExpr) {
            compileFunctionExpression(funcExpr);
        } else if (expr instanceof ArrowFunctionExpression arrowExpr) {
            compileArrowFunctionExpression(arrowExpr);
        } else if (expr instanceof AwaitExpression awaitExpr) {
            compileAwaitExpression(awaitExpr);
        } else if (expr instanceof YieldExpression yieldExpr) {
            compileYieldExpression(yieldExpr);
        } else if (expr instanceof ArrayExpression arrayExpr) {
            compileArrayExpression(arrayExpr);
        } else if (expr instanceof ObjectExpression objExpr) {
            compileObjectExpression(objExpr);
        } else if (expr instanceof TemplateLiteral templateLiteral) {
            compileTemplateLiteral(templateLiteral);
        } else if (expr instanceof TaggedTemplateExpression taggedTemplate) {
            compileTaggedTemplateExpression(taggedTemplate);
        } else if (expr instanceof ClassExpression classExpr) {
            compileClassExpression(classExpr);
        }
    }

    /**
     * Compile field initialization code for instance fields.
     * Emits code to set each field on 'this' with its initializer value.
     * For private fields, uses the symbol from privateSymbols map.
     */
    private void compileFieldInitialization(List<ClassDeclaration.PropertyDefinition> fields,
                                            Map<String, JSSymbol> privateSymbols) {
        for (ClassDeclaration.PropertyDefinition field : fields) {
            // Get field name
            String fieldName = null;
            boolean isPrivate = field.isPrivate();

            if (isPrivate && field.key() instanceof PrivateIdentifier privateId) {
                fieldName = privateId.name();
            } else if (field.key() instanceof Identifier id) {
                fieldName = id.name();
            } else if (field.key() instanceof Literal literal) {
                fieldName = literal.value().toString();
            } else {
                // Computed - skip for now
                continue;
            }

            // Push 'this' onto stack
            emitter.emitOpcode(Opcode.PUSH_THIS);

            // Compile initializer or emit undefined
            if (field.value() != null) {
                compileExpression(field.value());
            } else {
                emitter.emitOpcode(Opcode.UNDEFINED);
            }

            if (isPrivate) {
                // For private fields, we need to push the private symbol
                // Stack: this value
                // Need: this symbol value (for DEFINE_PRIVATE_FIELD)

                // Get the symbol from the map and emit as constant
                JSSymbol symbol = privateSymbols.get(fieldName);
                if (symbol != null) {
                    emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                    // Stack: this value symbol

                    // DEFINE_PRIVATE_FIELD expects: obj symbol value
                    // We have: this value symbol
                    // Need to rotate stack: this symbol value
                    emitter.emitOpcode(Opcode.SWAP);  // Stack: this symbol value

                    // Emit DEFINE_PRIVATE_FIELD
                    emitter.emitOpcode(Opcode.DEFINE_PRIVATE_FIELD);
                    // Stack: this (DEFINE_PRIVATE_FIELD pops symbol and value, modifies this, pushes this back)
                } else {
                    // Error: private field symbol not found - skip this field
                    emitter.emitOpcode(Opcode.DROP);  // Drop value
                    emitter.emitOpcode(Opcode.DROP);  // Drop this
                    continue;
                }
            } else {
                // Public field
                // Stack: this value
                // Emit DEFINE_FIELD to set the field on 'this'
                emitter.emitOpcodeAtom(Opcode.DEFINE_FIELD, fieldName);
                // Stack: this (DEFINE_FIELD pops value, modifies this, pushes this back)
            }

            // Drop 'this' from stack
            emitter.emitOpcode(Opcode.DROP);
        }
    }

    private void compileForInStatement(ForInStatement forInStmt) {
        enterScope();

        // Get the loop variable name
        VariableDeclaration varDecl = forInStmt.left();
        if (varDecl.declarations().size() != 1) {
            throw new CompilerException("for-in loop must have exactly one variable");
        }
        Pattern pattern = varDecl.declarations().get(0).id();
        if (!(pattern instanceof Identifier id)) {
            throw new CompilerException("for-in loop variable must be an identifier");
        }
        String varName = id.name();
        currentScope().declareLocal(varName);
        Integer varIndex = currentScope().getLocal(varName);

        // Compile the object expression
        compileExpression(forInStmt.right());
        // Stack: obj

        // Emit FOR_IN_START to create enumerator
        emitter.emitOpcode(Opcode.FOR_IN_START);
        // Stack: enum_obj

        // Start of loop
        int loopStart = emitter.currentOffset();
        LoopContext loop = new LoopContext(loopStart);
        loopStack.push(loop);

        // Emit FOR_IN_NEXT to get next key
        emitter.emitOpcode(Opcode.FOR_IN_NEXT);
        // Stack: enum_obj key
        // key is null/undefined when iteration is done

        // Check if key is null/undefined (iteration complete)
        emitter.emitOpcode(Opcode.DUP);
        // Stack: enum_obj key key
        emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        // Stack: enum_obj key is_done
        int jumpToEnd = emitter.emitJump(Opcode.IF_TRUE);
        // Stack: enum_obj key

        // Store key in loop variable
        if (varIndex != null) {
            emitter.emitOpcodeU16(Opcode.PUT_LOCAL, varIndex);
        } else {
            emitter.emitOpcode(Opcode.DROP);
        }
        // Stack: enum_obj

        // Compile loop body
        compileStatement(forInStmt.body());

        // Jump back to loop start
        emitter.emitOpcode(Opcode.GOTO);
        int backJumpPos = emitter.currentOffset();
        emitter.emitU32(loopStart - (backJumpPos + 4));

        // Break target - patch the jump to end
        emitter.patchJump(jumpToEnd, emitter.currentOffset());
        // Stack: enum_obj key (where key is null/undefined)

        // Clean up: drop the null/undefined key
        emitter.emitOpcode(Opcode.DROP);
        // Stack: enum_obj

        // Patch break statements
        for (int breakPos : loop.breakPositions) {
            emitter.patchJump(breakPos, emitter.currentOffset());
        }

        // Patch continue statements
        for (int continuePos : loop.continuePositions) {
            emitter.patchJump(continuePos, loopStart);
        }

        // Clean up stack: drop enum_obj using FOR_IN_END
        emitter.emitOpcode(Opcode.FOR_IN_END);

        loopStack.pop();
        exitScope();
    }

    private void compileForOfStatement(ForOfStatement forOfStmt) {
        enterScope();

        // Compile the iterable expression
        compileExpression(forOfStmt.right());

        // Emit FOR_OF_START (sync) or FOR_AWAIT_OF_START (async) to get iterator, next method, and catch offset
        // Stack after: iter, next, catch_offset
        if (forOfStmt.isAsync()) {
            emitter.emitOpcode(Opcode.FOR_AWAIT_OF_START);
        } else {
            emitter.emitOpcode(Opcode.FOR_OF_START);
        }

        // Declare the loop variable
        VariableDeclaration varDecl = forOfStmt.left();
        if (varDecl.declarations().size() != 1) {
            throw new CompilerException("for-of loop must have exactly one variable");
        }
        Pattern pattern = varDecl.declarations().get(0).id();
        if (!(pattern instanceof Identifier id)) {
            throw new CompilerException("for-of loop variable must be an identifier");
        }
        String varName = id.name();
        currentScope().declareLocal(varName);
        Integer varIndex = currentScope().getLocal(varName);

        // Start of loop
        int loopStart = emitter.currentOffset();
        LoopContext loop = new LoopContext(loopStart);
        loopStack.push(loop);

        // Emit FOR_OF_NEXT (sync) or FOR_AWAIT_OF_NEXT (async) to get next value
        int jumpToEnd;

        if (forOfStmt.isAsync()) {
            // Async for-of: FOR_AWAIT_OF_NEXT pushes promise
            // Stack before: iter, next, catch_offset
            // Stack after: iter, next, catch_offset, promise
            emitter.emitOpcode(Opcode.FOR_AWAIT_OF_NEXT);

            // Await the promise to get the result object
            // Stack before: iter, next, catch_offset, promise
            // Stack after: iter, next, catch_offset, result
            emitter.emitOpcode(Opcode.AWAIT);

            // Extract {value, done} from the iterator result object
            // Stack: iter, next, catch_offset, result

            // Duplicate result object so we can extract both properties
            emitter.emitOpcode(Opcode.DUP);
            // Stack: iter, next, catch_offset, result, result

            // Get 'done' property and check if iteration is complete
            emitter.emitOpcodeAtom(Opcode.GET_FIELD, "done");
            // Stack: iter, next, catch_offset, result, done

            // If done === true, exit the loop
            jumpToEnd = emitter.emitJump(Opcode.IF_TRUE);
            // Stack: iter, next, catch_offset, result

            // Get 'value' property from result
            emitter.emitOpcodeAtom(Opcode.GET_FIELD, "value");
            // Stack: iter, next, catch_offset, value

            // Store value in loop variable
            if (varIndex != null) {
                emitter.emitOpcodeU16(Opcode.PUT_LOCAL, varIndex);
            } else {
                emitter.emitOpcode(Opcode.DROP);
            }
            // Stack: iter, next, catch_offset
        } else {
            // Sync for-of: FOR_OF_NEXT pushes value and done separately
            // Stack before: iter, next, catch_offset
            // Stack after: iter, next, catch_offset, value, done
            emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 0);  // offset parameter (0 for now)

            // Check done flag
            // Stack: iter, next, catch_offset, value, done
            jumpToEnd = emitter.emitJump(Opcode.IF_TRUE);
            // Stack: iter, next, catch_offset, value

            // Store value in loop variable
            if (varIndex != null) {
                emitter.emitOpcodeU16(Opcode.PUT_LOCAL, varIndex);
            } else {
                emitter.emitOpcode(Opcode.DROP);
            }
            // Stack: iter, next, catch_offset
        }

        // Compile loop body
        compileStatement(forOfStmt.body());

        // Jump back to loop start
        emitter.emitOpcode(Opcode.GOTO);
        int backJumpPos = emitter.currentOffset();
        emitter.emitU32(loopStart - (backJumpPos + 4));

        // Loop end - patch the done check jump
        int loopEnd = emitter.currentOffset();
        emitter.patchJump(jumpToEnd, loopEnd);

        // When done === true, clean up remaining values on stack
        if (forOfStmt.isAsync()) {
            // Async: need to drop the result object still on stack
            emitter.emitOpcode(Opcode.DROP);  // result
        } else {
            // Sync: need to drop the value still on stack
            emitter.emitOpcode(Opcode.DROP);  // value
        }

        // Break target - break statements jump here (after dropping value)
        int breakTarget = emitter.currentOffset();

        // Patch break statements to jump after the value drop
        for (int breakPos : loop.breakPositions) {
            emitter.patchJump(breakPos, breakTarget);
        }

        // Patch continue statements (jump back to loop start)
        for (int continuePos : loop.continuePositions) {
            emitter.patchJump(continuePos, loopStart);
        }

        // Clean up iterator from stack
        // Pop iter, next, catch_offset
        emitter.emitOpcode(Opcode.DROP);  // catch_offset
        emitter.emitOpcode(Opcode.DROP);  // next
        emitter.emitOpcode(Opcode.DROP);  // iter

        loopStack.pop();
        exitScope();
    }

    private void compileForStatement(ForStatement forStmt) {
        enterScope();

        // Compile init
        if (forStmt.init() != null) {
            if (forStmt.init() instanceof VariableDeclaration varDecl) {
                compileVariableDeclaration(varDecl);
            } else if (forStmt.init() instanceof Expression expr) {
                compileExpression(expr);
                emitter.emitOpcode(Opcode.DROP);
            }
        }

        int loopStart = emitter.currentOffset();
        LoopContext loop = new LoopContext(loopStart);
        loopStack.push(loop);

        int jumpToEnd = -1;
        // Compile test
        if (forStmt.test() != null) {
            compileExpression(forStmt.test());
            jumpToEnd = emitter.emitJump(Opcode.IF_FALSE);
        }

        // Compile body
        compileStatement(forStmt.body());

        // Update position for continue statements
        int updateStart = emitter.currentOffset();

        // Compile update
        if (forStmt.update() != null) {
            compileExpression(forStmt.update());
            emitter.emitOpcode(Opcode.DROP);
        }

        // Jump back to test
        emitter.emitOpcode(Opcode.GOTO);
        int backJumpPos = emitter.currentOffset();
        emitter.emitU32(loopStart - (backJumpPos + 4));

        int loopEnd = emitter.currentOffset();

        // Patch test jump
        if (jumpToEnd != -1) {
            emitter.patchJump(jumpToEnd, loopEnd);
        }

        // Patch break statements
        for (int breakPos : loop.breakPositions) {
            emitter.patchJump(breakPos, loopEnd);
        }

        // Patch continue statements
        for (int continuePos : loop.continuePositions) {
            emitter.patchJump(continuePos, updateStart);
        }

        loopStack.pop();
        exitScope();
    }

    private void compileFunctionDeclaration(FunctionDeclaration funcDecl) {
        // Create a new compiler for the function body
        BytecodeCompiler functionCompiler = new BytecodeCompiler();

        // Enter function scope and add parameters as locals
        functionCompiler.enterScope();
        functionCompiler.inGlobalScope = false;
        functionCompiler.isInAsyncFunction = funcDecl.isAsync();  // Track if this is an async function

        for (Identifier param : funcDecl.params()) {
            functionCompiler.currentScope().declareLocal(param.name());
        }

        // If this is a generator function, emit INITIAL_YIELD at the start
        if (funcDecl.isGenerator()) {
            functionCompiler.emitter.emitOpcode(Opcode.INITIAL_YIELD);
        }

        // Compile function body statements
        for (Statement stmt : funcDecl.body().body()) {
            functionCompiler.compileStatement(stmt);
        }

        // If body doesn't end with return, add implicit return undefined
        List<Statement> bodyStatements = funcDecl.body().body();
        if (bodyStatements.isEmpty() || !(bodyStatements.get(bodyStatements.size() - 1) instanceof ReturnStatement)) {
            functionCompiler.emitter.emitOpcode(Opcode.UNDEFINED);
            // Emit RETURN_ASYNC for async functions, RETURN for sync functions
            functionCompiler.emitter.emitOpcode(funcDecl.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
        }

        int localCount = functionCompiler.currentScope().getLocalCount();
        functionCompiler.exitScope();

        // Build the function bytecode
        Bytecode functionBytecode = functionCompiler.emitter.build(localCount);

        // Get function name
        String functionName = funcDecl.id().name();

        // Extract function source code from original source
        String functionSource = extractSourceCode(funcDecl.getLocation());

        // Trim trailing whitespace from the extracted source
        // This is needed because the parser's end offset may include whitespace after the closing brace
        if (functionSource != null) {
            functionSource = functionSource.stripTrailing();
        }

        // If extraction failed, build a simplified representation
        if (functionSource == null || functionSource.isEmpty()) {
            StringBuilder funcSource = new StringBuilder();
            if (funcDecl.isAsync()) funcSource.append("async ");
            funcSource.append("function");
            if (funcDecl.isGenerator()) funcSource.append("*");
            funcSource.append(" ").append(functionName).append("(");
            for (int i = 0; i < funcDecl.params().size(); i++) {
                if (i > 0) funcSource.append(", ");
                funcSource.append(funcDecl.params().get(i).name());
            }
            funcSource.append(") { [function body] }");
            functionSource = funcSource.toString();
        }

        // Create JSBytecodeFunction
        JSBytecodeFunction function = new JSBytecodeFunction(
                functionBytecode,
                functionName,
                funcDecl.params().size(),
                new JSValue[0],  // closure vars - for now empty
                null,            // prototype - will be set by VM
                true,            // isConstructor - regular functions can be constructors
                funcDecl.isAsync(),
                funcDecl.isGenerator(),
                false,           // strict - TODO: parse directives in function body
                functionSource   // source code for toString()
        );

        // Emit FCLOSURE opcode with function in constant pool
        emitter.emitOpcodeConstant(Opcode.FCLOSURE, function);

        // Store the function in a variable with its name
        Integer localIndex = currentScope().getLocal(functionName);
        if (localIndex != null) {
            emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
        } else {
            // Declare the function as a global variable or in the current scope
            if (inGlobalScope) {
                emitter.emitOpcodeAtom(Opcode.PUT_VAR, functionName);
            } else {
                // Declare it as a local
                localIndex = currentScope().declareLocal(functionName);
                emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
            }
        }
    }

    private void compileFunctionExpression(FunctionExpression funcExpr) {
        // Create a new compiler for the function body
        BytecodeCompiler functionCompiler = new BytecodeCompiler();

        // Enter function scope and add parameters as locals
        functionCompiler.enterScope();
        functionCompiler.inGlobalScope = false;

        for (Identifier param : funcExpr.params()) {
            functionCompiler.currentScope().declareLocal(param.name());
        }

        // If this is a generator function, emit INITIAL_YIELD at the start
        if (funcExpr.isGenerator()) {
            functionCompiler.emitter.emitOpcode(Opcode.INITIAL_YIELD);
        }

        // Compile function body statements (don't call compileBlockStatement as it would create a new scope)
        for (Statement stmt : funcExpr.body().body()) {
            functionCompiler.compileStatement(stmt);
        }

        // If body doesn't end with return, add implicit return undefined
        // Check if last statement is a return statement
        List<Statement> bodyStatements = funcExpr.body().body();
        if (bodyStatements.isEmpty() || !(bodyStatements.get(bodyStatements.size() - 1) instanceof ReturnStatement)) {
            functionCompiler.emitter.emitOpcode(Opcode.UNDEFINED);
            functionCompiler.emitter.emitOpcode(Opcode.RETURN);
        }

        int localCount = functionCompiler.currentScope().getLocalCount();
        functionCompiler.exitScope();

        // Build the function bytecode
        Bytecode functionBytecode = functionCompiler.emitter.build(localCount);

        // Get function name (empty string for anonymous)
        String functionName = funcExpr.id() != null ? funcExpr.id().name() : "";

        // Extract function source code from original source
        String functionSource = extractSourceCode(funcExpr.getLocation());

        // Create JSBytecodeFunction
        JSBytecodeFunction function = new JSBytecodeFunction(
                functionBytecode,
                functionName,
                funcExpr.params().size(),
                new JSValue[0],  // closure vars - for now empty
                null,            // prototype - will be set by VM
                true,            // isConstructor - regular functions can be constructors
                funcExpr.isAsync(),
                funcExpr.isGenerator(),
                false,           // strict - TODO: parse directives in function body
                functionSource   // source code for toString()
        );

        // Prototype chain will be initialized when the function is loaded
        // during bytecode execution (see FCLOSURE opcode handler)

        // Emit FCLOSURE opcode with function in constant pool
        emitter.emitOpcodeConstant(Opcode.FCLOSURE, function);
    }

    private void compileIdentifier(Identifier id) {
        String name = id.name();

        // Handle 'this' keyword
        if ("this".equals(name)) {
            emitter.emitOpcode(Opcode.PUSH_THIS);
            return;
        }

        // Handle 'arguments' keyword in function scope
        // Arguments is available in regular functions but not in arrow functions
        // In QuickJS, this is handled via SPECIAL_OBJECT opcode
        if (JSArguments.NAME.equals(name) && !inGlobalScope) {
            // Emit SPECIAL_OBJECT opcode with type 0 (SPECIAL_OBJECT_ARGUMENTS)
            emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            emitter.emitU8(0);  // Type 0 = arguments object
            return;
        }

        if (inGlobalScope) {
            // In global scope, always use GET_VAR
            emitter.emitOpcodeAtom(Opcode.GET_VAR, name);
        } else {
            // In function scope, check locals first
            // Search from innermost scope (most recently pushed) to outermost
            // ArrayDeque.push() adds to front, iterator() iterates from front to back
            Integer localIndex = null;
            for (Scope scope : scopes) {
                localIndex = scope.getLocal(name);
                if (localIndex != null) {
                    break;
                }
            }

            if (localIndex != null) {
                emitter.emitOpcodeU16(Opcode.GET_LOCAL, localIndex);
            } else {
                // Try outer scopes or global
                emitter.emitOpcodeAtom(Opcode.GET_VAR, name);
            }
        }
    }

    private void compileIfStatement(IfStatement ifStmt) {
        // Compile condition
        compileExpression(ifStmt.test());

        // Jump to else/end if condition is false
        int jumpToElse = emitter.emitJump(Opcode.IF_FALSE);

        // Compile consequent
        compileStatement(ifStmt.consequent());

        if (ifStmt.alternate() != null) {
            // Jump over else block after consequent
            int jumpToEnd = emitter.emitJump(Opcode.GOTO);

            // Patch jump to else
            emitter.patchJump(jumpToElse, emitter.currentOffset());

            // Compile alternate
            compileStatement(ifStmt.alternate());

            // Patch jump to end
            emitter.patchJump(jumpToEnd, emitter.currentOffset());
        } else {
            // Patch jump to end
            emitter.patchJump(jumpToElse, emitter.currentOffset());
        }
    }

    private void compileLiteral(Literal literal) {
        Object value = literal.value();

        if (value == null) {
            emitter.emitOpcode(Opcode.NULL);
        } else if (value instanceof Boolean bool) {
            emitter.emitOpcode(bool ? Opcode.PUSH_TRUE : Opcode.PUSH_FALSE);
        } else if (value instanceof BigInteger bigInt) {
            // Check BigInteger before Number since BigInteger extends Number
            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSBigInt(bigInt));
        } else if (value instanceof Number num) {
            // Try to emit as i32 if it's an integer in range
            if (num instanceof Integer || num instanceof Long) {
                long longValue = num.longValue();
                if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                    emitter.emitOpcode(Opcode.PUSH_I32);
                    emitter.emitI32((int) longValue);
                    return;
                }
            }
            // Otherwise emit as constant
            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSNumber(num.doubleValue()));
        } else if (value instanceof String str) {
            // Check if it's a regex literal (starts with /)
            if (str.startsWith("/") && str.length() > 1) {
                // Parse regex literal
                int lastSlash = str.lastIndexOf('/');
                if (lastSlash > 0) {
                    String pattern = str.substring(1, lastSlash);
                    String flags = lastSlash < str.length() - 1 ? str.substring(lastSlash + 1) : "";
                    try {
                        JSRegExp regexp = new JSRegExp(pattern, flags);
                        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, regexp);
                        return;
                    } catch (Exception e) {
                        // Not a valid regex, treat as string
                    }
                }
            }
            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(str));
        } else {
            // Other types as constants
            throw new CompilerException("Unsupported literal type: " + value.getClass());
        }
    }

    /**
     * Compile logical assignment operators (&&=, ||=, ??=) with short-circuit evaluation.
     * Based on QuickJS implementation in quickjs.c (lines 27635-27690).
     * <p>
     * For a ??= b:
     * 1. Load current value of a (with DUP for member expressions)
     * 2. Duplicate it
     * 3. Check if it's null or undefined
     * 4. If not null/undefined, jump to end (keep current value, cleanup lvalue stack)
     * 5. If null/undefined, drop duplicate, evaluate b, insert below lvalue, assign, jump to end2
     * 6. At end: cleanup lvalue stack with NIP operations
     * 7. At end2: continue
     * <p>
     * For a &&= b:
     * Similar but check for falsy
     * <p>
     * For a ||= b:
     * Similar but check for truthy
     */
    private void compileLogicalAssignment(AssignmentExpression assignExpr) {
        Expression left = assignExpr.left();
        AssignmentExpression.AssignmentOperator operator = assignExpr.operator();

        // Determine the depth of lvalue for proper stack manipulation
        // depth 0 = identifier, depth 1 = obj.prop, depth 2 = obj[prop]
        int depthLvalue;
        if (left instanceof Identifier) {
            depthLvalue = 0;
        } else if (left instanceof MemberExpression memberExpr) {
            depthLvalue = memberExpr.computed() ? 2 : 1;
        } else {
            throw new CompilerException("Invalid left-hand side in logical assignment");
        }

        // Load the current value
        if (left instanceof Identifier id) {
            String name = id.name();
            Integer localIndex = findLocalInScopes(name);
            if (localIndex != null) {
                emitter.emitOpcodeU16(Opcode.GET_LOCAL, localIndex);
            } else {
                emitter.emitOpcodeAtom(Opcode.GET_VAR, name);
            }
        } else if (left instanceof MemberExpression memberExpr) {
            compileExpression(memberExpr.object());
            if (memberExpr.computed()) {
                compileExpression(memberExpr.property());
                emitter.emitOpcode(Opcode.DUP2);  // Duplicate obj and prop
                emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (memberExpr.property() instanceof Identifier propId) {
                emitter.emitOpcode(Opcode.DUP);  // Duplicate object
                emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
            }
        }

        // Duplicate the current value for the test
        emitter.emitOpcode(Opcode.DUP);

        // Emit the test based on operator type
        int jumpToCleanup;
        if (operator == AssignmentExpression.AssignmentOperator.NULLISH_ASSIGN) {
            // For ??=, check if null or undefined
            emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
            // Jump to cleanup if NOT null/undefined (value is on stack)
            jumpToCleanup = emitter.emitJump(Opcode.IF_FALSE);
        } else if (operator == AssignmentExpression.AssignmentOperator.LOGICAL_OR_ASSIGN) {
            // For ||=, jump to cleanup if truthy
            jumpToCleanup = emitter.emitJump(Opcode.IF_TRUE);
        } else { // LOGICAL_AND_ASSIGN
            // For &&=, jump to cleanup if falsy
            jumpToCleanup = emitter.emitJump(Opcode.IF_FALSE);
        }

        // The current value didn't meet the condition, so we assign the new value
        // The boolean was already popped by IF_FALSE
        // Drop the old value
        emitter.emitOpcode(Opcode.DROP);

        // Compile the right-hand side expression
        compileExpression(assignExpr.right());

        // Insert the new value below the lvalue on stack for proper assignment
        // This matches QuickJS's OP_insert2, OP_insert3, OP_insert4 pattern
        // INSERT2: [a, b] -> [b, a, b]
        // INSERT3: [a, b, c] -> [c, a, b, c]
        // INSERT4: [a, b, c, d] -> [d, a, b, c, d]
        switch (depthLvalue) {
            case 0 -> {
                // For identifier: stack is [newValue]
                // We need to keep the value on stack for the result
                emitter.emitOpcode(Opcode.DUP);
            }
            case 1 -> {
                // For obj.prop: stack is [obj, newValue]
                // We need: [newValue, obj] for PUT_FIELD
                // PUT_FIELD pops obj, peeks newValue, leaves newValue on stack
                emitter.emitOpcode(Opcode.SWAP);
            }
            case 2 -> {
                // For obj[prop]: stack is [obj, prop, newValue]
                // We need: [newValue, obj, prop] for PUT_ARRAY_EL
                // ROT3R rotates right: [a, b, c] -> [c, a, b]
                // So [obj, prop, newValue] -> [newValue, obj, prop]
                emitter.emitOpcode(Opcode.ROT3R);
            }
            default -> throw new CompilerException("Invalid depth for logical assignment");
        }

        // Store the result to left side
        if (left instanceof Identifier id) {
            String name = id.name();
            Integer localIndex = findLocalInScopes(name);
            if (localIndex != null) {
                emitter.emitOpcodeU16(Opcode.SET_LOCAL, localIndex);
            } else {
                emitter.emitOpcodeAtom(Opcode.SET_VAR, name);
            }
        } else if (left instanceof MemberExpression memberExpr) {
            if (memberExpr.computed()) {
                emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
            } else if (memberExpr.property() instanceof Identifier propId) {
                emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name());
            }
        }

        // Jump over the cleanup code
        int jumpToEnd = emitter.emitJump(Opcode.GOTO);

        // Patch the jump to cleanup - if we took this branch, we need to cleanup lvalue stack
        emitter.patchJump(jumpToCleanup, emitter.currentOffset());

        // Remove the lvalue stack entries using NIP
        // NIP removes the value below the top, keeping the top value
        // For depth 0 (identifier): no cleanup needed
        // For depth 1 (obj.prop): NIP removes obj, keeps the value
        // For depth 2 (obj[prop]): NIP twice removes obj and prop, keeps the value
        for (int i = 0; i < depthLvalue; i++) {
            emitter.emitOpcode(Opcode.NIP);
        }

        // Patch the jump to end - both paths converge here
        emitter.patchJump(jumpToEnd, emitter.currentOffset());
    }

    private void compileMemberExpression(MemberExpression memberExpr) {
        compileExpression(memberExpr.object());

        if (memberExpr.computed()) {
            // obj[expr]
            compileExpression(memberExpr.property());
            emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
            // obj.#privateField
            // Stack: obj
            // Need: obj symbol
            String fieldName = privateId.name();
            JSSymbol symbol = privateSymbols.get(fieldName);
            if (symbol != null) {
                emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                // Stack: obj symbol
                emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
                // Stack: value
            } else {
                // Error: private field not found
                // For now, just emit undefined
                emitter.emitOpcode(Opcode.DROP);  // Drop obj
                emitter.emitOpcode(Opcode.UNDEFINED);
            }
        } else if (memberExpr.property() instanceof Identifier propId) {
            // obj.prop
            emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
        }
    }

    /**
     * Compile a method definition as a function.
     * For constructors, instanceFields contains fields to initialize.
     * privateSymbols contains JSSymbol instances for private fields (passed as closure variables).
     */
    private JSBytecodeFunction compileMethodAsFunction(
            ClassDeclaration.MethodDefinition method,
            String methodName,
            boolean isDerivedConstructor,
            List<ClassDeclaration.PropertyDefinition> instanceFields,
            Map<String, JSSymbol> privateSymbols,
            boolean isConstructor) {
        BytecodeCompiler methodCompiler = new BytecodeCompiler();
        methodCompiler.privateSymbols = privateSymbols;  // Make private symbols available in method

        FunctionExpression funcExpr = method.value();

        // Enter function scope and add parameters as locals
        methodCompiler.enterScope();
        methodCompiler.inGlobalScope = false;
        methodCompiler.isInAsyncFunction = funcExpr.isAsync();

        for (Identifier param : funcExpr.params()) {
            methodCompiler.currentScope().declareLocal(param.name());
        }

        // If this is a generator method, emit INITIAL_YIELD at the start
        if (funcExpr.isGenerator()) {
            methodCompiler.emitter.emitOpcode(Opcode.INITIAL_YIELD);
        }

        // For constructors, emit field initialization BEFORE the constructor body
        // This ensures fields are initialized before user code runs
        if (!instanceFields.isEmpty()) {
            methodCompiler.compileFieldInitialization(instanceFields, privateSymbols);
        }

        // Compile method body statements
        for (Statement stmt : funcExpr.body().body()) {
            methodCompiler.compileStatement(stmt);
        }

        // If body doesn't end with return, add implicit return undefined
        List<Statement> bodyStatements = funcExpr.body().body();
        if (bodyStatements.isEmpty() || !(bodyStatements.get(bodyStatements.size() - 1) instanceof ReturnStatement)) {
            methodCompiler.emitter.emitOpcode(Opcode.UNDEFINED);
            methodCompiler.emitter.emitOpcode(funcExpr.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
        }

        int localCount = methodCompiler.currentScope().getLocalCount();
        methodCompiler.exitScope();

        // Build the method bytecode
        Bytecode methodBytecode = methodCompiler.emitter.build(localCount);

        // Convert private symbols to closure variable array
        JSValue[] closureVars = new JSValue[privateSymbols.size()];
        int idx = 0;
        for (JSSymbol symbol : privateSymbols.values()) {
            closureVars[idx++] = symbol;
        }

        // Create JSBytecodeFunction for the method
        return new JSBytecodeFunction(
                methodBytecode,
                methodName,
                funcExpr.params().size(),
                closureVars,     // closure vars contain private symbols
                null,            // prototype
                isConstructor,   // isConstructor - true for class constructors, false for methods
                funcExpr.isAsync(),
                funcExpr.isGenerator(),
                true,            // strict - classes are always strict mode
                "method " + methodName + "() { [method body] }"  // source for toString
        );
    }

    private void compileNewExpression(NewExpression newExpr) {
        // Push constructor
        compileExpression(newExpr.callee());

        // Push arguments
        for (Expression arg : newExpr.arguments()) {
            compileExpression(arg);
        }

        // Call constructor
        emitter.emitOpcodeU16(Opcode.CALL_CONSTRUCTOR, newExpr.arguments().size());
    }

    private void compileObjectExpression(ObjectExpression objExpr) {
        emitter.emitOpcode(Opcode.OBJECT_NEW);

        for (ObjectExpression.Property prop : objExpr.properties()) {
            // Push key
            if (prop.key() instanceof Identifier id) {
                emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(id.name()));
            } else {
                compileExpression(prop.key());
            }

            // Push value
            compileExpression(prop.value());

            // Define property
            emitter.emitOpcode(Opcode.DEFINE_PROP);
        }
    }

    private void compilePatternAssignment(Pattern pattern) {
        if (pattern instanceof Identifier id) {
            // Simple identifier: value is on stack, just assign it
            String varName = id.name();
            if (inGlobalScope) {
                emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
            } else {
                int localIndex = currentScope().declareLocal(varName);
                emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
            }
        } else if (pattern instanceof ObjectPattern objPattern) {
            // Object destructuring: { proxy, revoke } = value
            // Stack: [object]
            for (ObjectPattern.Property prop : objPattern.properties()) {
                // Get the property name
                String propName = ((Identifier) prop.key()).name();

                // Duplicate object for each property access
                emitter.emitOpcode(Opcode.DUP);
                // Get the property value
                emitter.emitOpcodeAtom(Opcode.GET_FIELD, propName);
                // Assign to the pattern (could be nested)
                compilePatternAssignment(prop.value());
            }
            // Drop the original object
            emitter.emitOpcode(Opcode.DROP);
        } else if (pattern instanceof ArrayPattern arrPattern) {
            // Array destructuring: [a, b] = value
            // Stack: [array]
            int index = 0;
            for (Pattern element : arrPattern.elements()) {
                if (element != null) {
                    // Duplicate array
                    emitter.emitOpcode(Opcode.DUP);
                    // Push index
                    emitter.emitOpcode(Opcode.PUSH_I32);
                    emitter.emitI32(index);
                    // Get array element
                    emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                    // Assign to the pattern
                    compilePatternAssignment(element);
                }
                index++;
            }
            // Drop the original array
            emitter.emitOpcode(Opcode.DROP);
        }
    }

    // ==================== Expression Compilation ====================

    private void compileProgram(Program program) {
        inGlobalScope = true;
        enterScope();

        List<Statement> body = program.body();
        int lastIndex = body.size() - 1;
        boolean lastIsExpression = false;
        boolean lastProducesValue = false;

        for (int i = 0; i < body.size(); i++) {
            boolean isLast = (i == lastIndex);
            Statement stmt = body.get(i);

            if (isLast && stmt instanceof ExpressionStatement) {
                lastIsExpression = true;
                lastProducesValue = true;
            } else if (isLast && stmt instanceof TryStatement) {
                // Try statements can produce values
                lastProducesValue = true;
            }

            compileStatement(stmt, isLast);
        }

        // If last statement didn't produce a value, push undefined
        if (!lastProducesValue) {
            emitter.emitOpcode(Opcode.UNDEFINED);
        }

        // Return the value on top of stack
        emitter.emitOpcode(Opcode.RETURN);

        exitScope();
        inGlobalScope = false;
    }

    private void compileReturnStatement(ReturnStatement retStmt) {
        if (retStmt.argument() != null) {
            compileExpression(retStmt.argument());
        } else {
            emitter.emitOpcode(Opcode.UNDEFINED);
        }
        // Emit RETURN_ASYNC for async functions, RETURN for sync functions
        emitter.emitOpcode(isInAsyncFunction ? Opcode.RETURN_ASYNC : Opcode.RETURN);
    }

    private void compileStatement(Statement stmt) {
        compileStatement(stmt, false);
    }

    private void compileStatement(Statement stmt, boolean isLastInProgram) {
        if (stmt instanceof ExpressionStatement exprStmt) {
            compileExpression(exprStmt.expression());
            // Only drop the result if this is not the last statement in the program
            if (!isLastInProgram) {
                emitter.emitOpcode(Opcode.DROP);
            }
        } else if (stmt instanceof BlockStatement block) {
            compileBlockStatement(block);
        } else if (stmt instanceof IfStatement ifStmt) {
            compileIfStatement(ifStmt);
        } else if (stmt instanceof WhileStatement whileStmt) {
            compileWhileStatement(whileStmt);
        } else if (stmt instanceof ForStatement forStmt) {
            compileForStatement(forStmt);
        } else if (stmt instanceof ForInStatement forInStmt) {
            compileForInStatement(forInStmt);
        } else if (stmt instanceof ForOfStatement forOfStmt) {
            compileForOfStatement(forOfStmt);
        } else if (stmt instanceof ReturnStatement retStmt) {
            compileReturnStatement(retStmt);
        } else if (stmt instanceof BreakStatement breakStmt) {
            compileBreakStatement(breakStmt);
        } else if (stmt instanceof ContinueStatement contStmt) {
            compileContinueStatement(contStmt);
        } else if (stmt instanceof ThrowStatement throwStmt) {
            compileThrowStatement(throwStmt);
        } else if (stmt instanceof TryStatement tryStmt) {
            compileTryStatement(tryStmt);
        } else if (stmt instanceof SwitchStatement switchStmt) {
            compileSwitchStatement(switchStmt);
        } else if (stmt instanceof VariableDeclaration varDecl) {
            compileVariableDeclaration(varDecl);
        } else if (stmt instanceof FunctionDeclaration funcDecl) {
            compileFunctionDeclaration(funcDecl);
        } else if (stmt instanceof ClassDeclaration classDecl) {
            compileClassDeclaration(classDecl);
        }
    }

    /**
     * Compile a static block as a function.
     * Static blocks are executed immediately after class definition with the class constructor as 'this'.
     */
    private JSBytecodeFunction compileStaticBlock(ClassDeclaration.StaticBlock staticBlock, String className) {
        BytecodeCompiler blockCompiler = new BytecodeCompiler();

        blockCompiler.enterScope();
        blockCompiler.inGlobalScope = false;

        // Compile all statements in the static block
        for (Statement stmt : staticBlock.body()) {
            blockCompiler.compileStatement(stmt);
        }

        // Static blocks always return undefined
        blockCompiler.emitter.emitOpcode(Opcode.UNDEFINED);
        blockCompiler.emitter.emitOpcode(Opcode.RETURN);

        int localCount = blockCompiler.currentScope().getLocalCount();
        blockCompiler.exitScope();

        Bytecode blockBytecode = blockCompiler.emitter.build(localCount);

        return new JSBytecodeFunction(
                blockBytecode,
                "<static initializer>",  // Static blocks are anonymous
                0,                        // no parameters
                new JSValue[0],           // no closure vars
                null,                     // no prototype
                false,                    // not a constructor
                false,                    // not async
                false,                    // not generator
                true,                     // strict mode
                "static { [initializer] }"
        );
    }

    private void compileSwitchStatement(SwitchStatement switchStmt) {
        // Compile discriminant
        compileExpression(switchStmt.discriminant());

        List<Integer> caseJumps = new ArrayList<>();
        List<Integer> caseBodyStarts = new ArrayList<>();

        // Emit comparisons for each case
        for (SwitchStatement.SwitchCase switchCase : switchStmt.cases()) {
            if (switchCase.test() != null) {
                // Duplicate discriminant for comparison
                emitter.emitOpcode(Opcode.DUP);
                compileExpression(switchCase.test());
                emitter.emitOpcode(Opcode.STRICT_EQ);

                int jumpToCase = emitter.emitJump(Opcode.IF_TRUE);
                caseJumps.add(jumpToCase);
            }
        }

        // Drop discriminant
        emitter.emitOpcode(Opcode.DROP);

        // Jump to default or end
        int jumpToDefault = emitter.emitJump(Opcode.GOTO);

        // Compile case bodies
        LoopContext loop = new LoopContext(emitter.currentOffset());
        loopStack.push(loop);

        for (int i = 0; i < switchStmt.cases().size(); i++) {
            SwitchStatement.SwitchCase switchCase = switchStmt.cases().get(i);

            if (switchCase.test() != null) {
                int bodyStart = emitter.currentOffset();
                caseBodyStarts.add(bodyStart);
            }

            for (Statement stmt : switchCase.consequent()) {
                compileStatement(stmt);
            }
        }

        int switchEnd = emitter.currentOffset();

        // Patch case jumps
        for (int i = 0; i < caseJumps.size(); i++) {
            emitter.patchJump(caseJumps.get(i), caseBodyStarts.get(i));
        }

        // Patch default jump
        boolean hasDefault = switchStmt.cases().stream().anyMatch(c -> c.test() == null);
        if (hasDefault) {
            // Find default case body start
            int defaultIndex = 0;
            for (int i = 0; i < switchStmt.cases().size(); i++) {
                if (switchStmt.cases().get(i).test() == null) {
                    defaultIndex = i;
                    break;
                }
            }
            int defaultStart = caseBodyStarts.isEmpty() ? switchEnd :
                    (defaultIndex < caseBodyStarts.size() ? caseBodyStarts.get(defaultIndex) : switchEnd);
            emitter.patchJump(jumpToDefault, defaultStart);
        } else {
            emitter.patchJump(jumpToDefault, switchEnd);
        }

        // Patch break statements
        for (int breakPos : loop.breakPositions) {
            emitter.patchJump(breakPos, switchEnd);
        }

        loopStack.pop();
    }

    private void compileTaggedTemplateExpression(TaggedTemplateExpression taggedTemplate) {
        // Tagged template: tag`template`
        // The tag function receives:
        // 1. A template object (array-like) with cooked strings and a 'raw' property
        // 2. The values of the substitutions as additional arguments

        TemplateLiteral template = taggedTemplate.quasi();
        List<String> quasis = template.quasis();
        List<Expression> expressions = template.expressions();

        // Check if this is a method call (tag is a member expression)
        if (taggedTemplate.tag() instanceof MemberExpression memberExpr) {
            // For method calls: obj.method`template`
            // We need to preserve obj as the 'this' value

            // Push object (receiver)
            compileExpression(memberExpr.object());

            // Duplicate it (one copy for 'this', one for property access)
            emitter.emitOpcode(Opcode.DUP);

            // Get the method
            if (memberExpr.computed()) {
                // obj[expr]
                compileExpression(memberExpr.property());
                emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (memberExpr.property() instanceof Identifier propId) {
                // obj.prop
                emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
            }

            // Now stack is: receiver, method
            // Swap so method is on top: method, receiver
            emitter.emitOpcode(Opcode.SWAP);
        } else {
            // Regular function call: func`template`
            // Compile the tag function first (will be the callee)
            compileExpression(taggedTemplate.tag());

            // Add undefined as receiver/thisArg
            emitter.emitOpcode(Opcode.UNDEFINED);
        }

        // Stack is now: function, receiver

        // Create the template array (with cooked strings)
        emitter.emitOpcode(Opcode.ARRAY_NEW);
        for (String quasi : quasis) {
            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(quasi));
            emitter.emitOpcode(Opcode.PUSH_ARRAY);
        }
        // Stack: function, receiver, template_array

        // Duplicate template_array because we'll need it after setting the raw property
        emitter.emitOpcode(Opcode.DUP);
        // Stack: function, receiver, template_array, template_array

        // Create the raw array
        emitter.emitOpcode(Opcode.ARRAY_NEW);
        for (String quasi : quasis) {
            // For raw strings, use the same quasi (we already have the raw form)
            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(quasi));
            emitter.emitOpcode(Opcode.PUSH_ARRAY);
        }
        // Stack: function, receiver, template_array, template_array, raw_array

        // Set the raw property: template_array.raw = raw_array
        // PUT_FIELD expects: value (raw_array) on stack, object (template_array) on stack top-1
        // We have: template_array at top-2, raw_array at top
        // SWAP them: function, receiver, template_array, raw_array, template_array
        emitter.emitOpcode(Opcode.SWAP);
        // Now PUT_FIELD: pops template_array (object), peeks raw_array (value)
        emitter.emitOpcodeAtom(Opcode.PUT_FIELD, "raw");
        // Stack: function, receiver, template_array, raw_array (raw_array left as result)

        // Drop the raw_array
        emitter.emitOpcode(Opcode.DROP);
        // Stack: function, receiver, template_array

        // Add substitution expressions as additional arguments
        for (Expression expr : expressions) {
            compileExpression(expr);
        }

        // Call the tag function
        // argCount = 1 (template array) + number of expressions
        int argCount = 1 + expressions.size();
        emitter.emitOpcode(Opcode.CALL);
        emitter.emitU16(argCount);
    }

    private void compileTemplateLiteral(TemplateLiteral templateLiteral) {
        // For untagged template literals, concatenate strings and expressions
        // Example: `Hello ${name}!` becomes "Hello " + name + "!"

        List<String> quasis = templateLiteral.quasis();
        List<Expression> expressions = templateLiteral.expressions();

        if (quasis.isEmpty()) {
            // Empty template literal
            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(""));
            return;
        }

        // Start with the first quasi
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(quasis.get(0)));

        // Add each expression and subsequent quasi using string concatenation (ADD)
        for (int i = 0; i < expressions.size(); i++) {
            // Compile the expression
            compileExpression(expressions.get(i));

            // Concatenate using ADD opcode (JavaScript + operator)
            emitter.emitOpcode(Opcode.ADD);

            // Add the next quasi if it exists
            if (i + 1 < quasis.size()) {
                String quasi = quasis.get(i + 1);
                if (!quasi.isEmpty()) {
                    emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(quasi));
                    emitter.emitOpcode(Opcode.ADD);
                }
            }
        }
    }

    private void compileThrowStatement(ThrowStatement throwStmt) {
        compileExpression(throwStmt.argument());
        emitter.emitOpcode(Opcode.THROW);
    }

    /**
     * Compile a block for try/catch/finally, preserving the value of the last expression.
     */
    private void compileTryFinallyBlock(BlockStatement block) {
        enterScope();
        List<Statement> body = block.body();
        for (int i = 0; i < body.size(); i++) {
            boolean isLast = (i == body.size() - 1);
            Statement stmt = body.get(i);

            if (stmt instanceof ExpressionStatement exprStmt) {
                compileExpression(exprStmt.expression());
                // Keep the value on stack for the last expression, drop otherwise
                if (!isLast) {
                    emitter.emitOpcode(Opcode.DROP);
                }
            } else {
                compileStatement(stmt, false);
                // If last statement is not an expression, push undefined
                if (isLast) {
                    emitter.emitOpcode(Opcode.UNDEFINED);
                }
            }
        }
        // If block is empty, push undefined
        if (body.isEmpty()) {
            emitter.emitOpcode(Opcode.UNDEFINED);
        }
        exitScope();
    }

    private void compileTryStatement(TryStatement tryStmt) {
        // Mark catch handler location
        int catchJump = -1;
        if (tryStmt.handler() != null) {
            catchJump = emitter.emitJump(Opcode.CATCH);
        }

        // Compile try block - preserve value of last expression
        compileTryFinallyBlock(tryStmt.block());

        // Jump over catch block
        int jumpOverCatch = emitter.emitJump(Opcode.GOTO);

        if (tryStmt.handler() != null) {
            // Patch catch jump
            emitter.patchJump(catchJump, emitter.currentOffset());

            // Catch handler puts exception on stack
            TryStatement.CatchClause handler = tryStmt.handler();

            // Bind exception to parameter if present
            if (handler.param() != null) {
                enterScope();
                // Catch block creates a local scope - variables should use GET_LOCAL
                boolean savedGlobalScope = inGlobalScope;
                inGlobalScope = false;

                String paramName = handler.param().name();
                int localIndex = currentScope().declareLocal(paramName);
                emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);

                // Compile catch body in the SAME scope as the parameter
                List<Statement> body = handler.body().body();
                for (int i = 0; i < body.size(); i++) {
                    boolean isLast = (i == body.size() - 1);
                    Statement stmt = body.get(i);

                    if (stmt instanceof ExpressionStatement exprStmt) {
                        compileExpression(exprStmt.expression());
                        // Keep the value on stack for the last expression, drop otherwise
                        if (!isLast) {
                            emitter.emitOpcode(Opcode.DROP);
                        }
                    } else {
                        compileStatement(stmt, false);
                        // If last statement is not an expression, push undefined
                        if (isLast) {
                            emitter.emitOpcode(Opcode.UNDEFINED);
                        }
                    }
                }
                // If block is empty, push undefined
                if (body.isEmpty()) {
                    emitter.emitOpcode(Opcode.UNDEFINED);
                }

                inGlobalScope = savedGlobalScope;
                exitScope();
            } else {
                // No parameter, compile catch body without binding
                compileTryFinallyBlock(handler.body());
            }
        }

        // Patch jump over catch
        emitter.patchJump(jumpOverCatch, emitter.currentOffset());

        // Compile finally block
        if (tryStmt.finalizer() != null) {
            compileTryFinallyBlock(tryStmt.finalizer());
        }
    }

    private void compileUnaryExpression(UnaryExpression unaryExpr) {
        // DELETE operator needs special handling - it doesn't evaluate the operand,
        // but instead emits object and property separately
        if (unaryExpr.operator() == UnaryExpression.UnaryOperator.DELETE) {
            Expression operand = unaryExpr.operand();

            if (operand instanceof MemberExpression memberExpr) {
                // delete obj.prop or delete obj[expr]
                compileExpression(memberExpr.object());

                if (memberExpr.computed()) {
                    // obj[expr]
                    compileExpression(memberExpr.property());
                } else if (memberExpr.property() instanceof Identifier propId) {
                    // obj.prop
                    emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(propId.name()));
                }

                emitter.emitOpcode(Opcode.DELETE);
            } else {
                // delete identifier or delete literal - always returns true
                emitter.emitOpcode(Opcode.PUSH_TRUE);
            }
            return;
        }

        // INC and DEC operators - following QuickJS pattern:
        // 1. Compile get_lvalue (loads current value)
        // 2. Apply INC/DEC (prefix) or POST_INC/POST_DEC (postfix)
        // 3. Apply put_lvalue (stores with appropriate stack manipulation)
        if (unaryExpr.operator() == UnaryExpression.UnaryOperator.INC ||
                unaryExpr.operator() == UnaryExpression.UnaryOperator.DEC) {
            Expression operand = unaryExpr.operand();
            boolean isInc = unaryExpr.operator() == UnaryExpression.UnaryOperator.INC;
            boolean isPrefix = unaryExpr.prefix();

            if (operand instanceof Identifier id) {
                // Simple variable: get, inc/dec, set/put
                compileExpression(operand);
                emitter.emitOpcode(isPrefix ? (isInc ? Opcode.INC : Opcode.DEC)
                        : (isInc ? Opcode.POST_INC : Opcode.POST_DEC));
                Integer localIndex = currentScope().getLocal(id.name());
                if (localIndex != null) {
                    emitter.emitOpcodeU16(isPrefix ? Opcode.SET_LOCAL : Opcode.PUT_LOCAL, localIndex);
                } else {
                    emitter.emitOpcodeAtom(isPrefix ? Opcode.SET_VAR : Opcode.PUT_VAR, id.name());
                }
            } else if (operand instanceof MemberExpression memberExpr) {
                if (memberExpr.computed()) {
                    // Array element: obj[prop]
                    compileExpression(memberExpr.object());
                    compileExpression(memberExpr.property());

                    if (isPrefix) {
                        // Prefix: ++arr[i] - returns new value
                        emitter.emitOpcode(Opcode.DUP2);
                        emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSNumber(1));
                        emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB);
                        // Stack: [obj, prop, new_val] -> need [new_val, obj, prop]
                        emitter.emitOpcode(Opcode.ROT3R);
                        emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                    } else {
                        // Postfix: arr[i]++ - returns old value
                        emitter.emitOpcode(Opcode.DUP2); // obj prop obj prop
                        emitter.emitOpcode(Opcode.GET_ARRAY_EL); // obj prop old_val
                        emitter.emitOpcode(Opcode.DUP); // obj prop old_val old_val
                        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSNumber(1));
                        emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB); // obj prop old_val new_val
                        // SWAP2 to rearrange: [obj, prop, old_val, new_val] -> [old_val, new_val, obj, prop]
                        emitter.emitOpcode(Opcode.SWAP2); // old_val new_val obj prop
                        emitter.emitOpcode(Opcode.PUT_ARRAY_EL); // old_val new_val
                        emitter.emitOpcode(Opcode.DROP); // old_val
                    }
                } else {
                    // Object property: obj.prop or obj.#field
                    if (memberExpr.property() instanceof Identifier propId) {
                        compileExpression(memberExpr.object());

                        if (isPrefix) {
                            // Prefix: ++obj.prop - returns new value
                            emitter.emitOpcode(Opcode.DUP);
                            emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
                            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSNumber(1));
                            emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB);
                            // Stack: [obj, new_val] -> need [new_val, obj] for PUT_FIELD
                            emitter.emitOpcode(Opcode.SWAP);
                            // PUT_FIELD pops obj, peeks new_val, leaves [new_val]
                            emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name());
                        } else {
                            // Postfix: obj.prop++ - returns old value
                            emitter.emitOpcode(Opcode.DUP); // obj obj
                            emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name()); // obj old_val
                            emitter.emitOpcode(Opcode.DUP); // obj old_val old_val
                            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSNumber(1));
                            emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB); // obj old_val new_val
                            // Stack: [obj, old_val, new_val] - need [old_val, new_val, obj] for PUT_FIELD
                            // ROT3L: [old_val, new_val, obj]
                            emitter.emitOpcode(Opcode.ROT3L); // old_val new_val obj
                            // PUT_FIELD pops obj, peeks new_val, leaves [old_val, new_val]
                            emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name()); // old_val new_val
                            emitter.emitOpcode(Opcode.DROP); // old_val
                        }
                    } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                        // Private field: obj.#field
                        String fieldName = privateId.name();
                        JSSymbol symbol = privateSymbols.get(fieldName);
                        if (symbol == null) {
                            throw new CompilerException("Private field not found: #" + fieldName);
                        }

                        compileExpression(memberExpr.object());

                        if (isPrefix) {
                            // Prefix: ++obj.#field - returns new value
                            emitter.emitOpcode(Opcode.DUP); // obj obj
                            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                            emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD); // obj old_val
                            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSNumber(1));
                            emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB); // obj new_val
                            emitter.emitOpcode(Opcode.DUP); // obj new_val new_val
                            emitter.emitOpcode(Opcode.ROT3R); // new_val obj new_val
                            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol); // new_val obj new_val symbol
                            emitter.emitOpcode(Opcode.SWAP); // new_val obj symbol new_val
                            emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD); // new_val
                        } else {
                            // Postfix: obj.#field++ - returns old value
                            emitter.emitOpcode(Opcode.DUP); // obj obj
                            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                            emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD); // obj old_val
                            emitter.emitOpcode(Opcode.DUP); // obj old_val old_val
                            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSNumber(1));
                            emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB); // obj old_val new_val
                            emitter.emitOpcode(Opcode.ROT3L); // old_val new_val obj
                            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol); // old_val new_val obj symbol
                            // Need: obj symbol new_val for PUT_PRIVATE_FIELD
                            // Have: old_val new_val obj symbol
                            // SWAP to get: old_val new_val symbol obj
                            emitter.emitOpcode(Opcode.SWAP); // old_val new_val symbol obj
                            // ROT3L to get: old_val obj symbol new_val
                            emitter.emitOpcode(Opcode.ROT3L); // old_val obj symbol new_val
                            emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD); // old_val
                        }
                    } else {
                        throw new CompilerException("Invalid member expression property for increment/decrement");
                    }
                }
            } else {
                throw new CompilerException("Invalid operand for increment/decrement operator");
            }
            return;
        }

        compileExpression(unaryExpr.operand());

        Opcode op = switch (unaryExpr.operator()) {
            case BIT_NOT -> Opcode.NOT;
            case MINUS -> Opcode.NEG;
            case NOT -> Opcode.LOGICAL_NOT;
            case PLUS -> Opcode.PLUS;
            case TYPEOF -> Opcode.TYPEOF;
            case VOID -> {
                emitter.emitOpcode(Opcode.DROP);
                yield Opcode.UNDEFINED;
            }
            default -> throw new CompilerException("Unknown unary operator: " + unaryExpr.operator());
        };

        emitter.emitOpcode(op);
    }

    private void compileVariableDeclaration(VariableDeclaration varDecl) {
        for (VariableDeclaration.VariableDeclarator declarator : varDecl.declarations()) {
            // Compile initializer or push undefined
            if (declarator.init() != null) {
                compileExpression(declarator.init());
            } else {
                emitter.emitOpcode(Opcode.UNDEFINED);
            }

            // Assign to pattern (handles Identifier, ObjectPattern, ArrayPattern)
            compilePatternAssignment(declarator.id());
        }
    }

    private void compileWhileStatement(WhileStatement whileStmt) {
        int loopStart = emitter.currentOffset();
        LoopContext loop = new LoopContext(loopStart);
        loopStack.push(loop);

        // Compile test condition
        compileExpression(whileStmt.test());

        // Jump to end if false
        int jumpToEnd = emitter.emitJump(Opcode.IF_FALSE);

        // Compile body
        compileStatement(whileStmt.body());

        // Jump back to start
        emitter.emitOpcode(Opcode.GOTO);
        int backJumpPos = emitter.currentOffset();
        emitter.emitU32(loopStart - (backJumpPos + 4));

        // Patch end jump
        int loopEnd = emitter.currentOffset();
        emitter.patchJump(jumpToEnd, loopEnd);

        // Patch all break statements
        for (int breakPos : loop.breakPositions) {
            emitter.patchJump(breakPos, loopEnd);
        }

        // Patch all continue statements
        for (int continuePos : loop.continuePositions) {
            emitter.patchJump(continuePos, loopStart);
        }

        loopStack.pop();
    }

    private void compileYieldExpression(YieldExpression yieldExpr) {
        // Compile the argument expression (if present)
        if (yieldExpr.argument() != null) {
            compileExpression(yieldExpr.argument());
        } else {
            // No argument means yield undefined
            emitter.emitConstant(null);
        }

        // Emit the appropriate yield opcode
        if (yieldExpr.delegate()) {
            // yield* delegates to another generator/iterable
            // For now, use YIELD_STAR for sync generators
            // TODO: Need to check if in async generator for ASYNC_YIELD_STAR
            emitter.emitOpcode(Opcode.YIELD_STAR);
        } else {
            // Regular yield
            emitter.emitOpcode(Opcode.YIELD);
        }
    }

    /**
     * Create a default constructor for a class.
     */
    private JSBytecodeFunction createDefaultConstructor(
            String className,
            boolean hasSuper,
            List<ClassDeclaration.PropertyDefinition> instanceFields,
            Map<String, JSSymbol> privateSymbols) {
        BytecodeCompiler constructorCompiler = new BytecodeCompiler();
        constructorCompiler.privateSymbols = privateSymbols;  // Make private symbols available

        constructorCompiler.enterScope();
        constructorCompiler.inGlobalScope = false;

        // Initialize fields before constructor body
        if (!instanceFields.isEmpty()) {
            constructorCompiler.compileFieldInitialization(instanceFields, privateSymbols);
        }

        // Default constructor just returns undefined (or calls super for derived classes)
        if (hasSuper) {
            // TODO: Implement super() call for derived class constructor
            // For now, just return undefined
            constructorCompiler.emitter.emitOpcode(Opcode.UNDEFINED);
        } else {
            constructorCompiler.emitter.emitOpcode(Opcode.UNDEFINED);
        }
        constructorCompiler.emitter.emitOpcode(Opcode.RETURN);

        int localCount = constructorCompiler.currentScope().getLocalCount();
        constructorCompiler.exitScope();

        Bytecode constructorBytecode = constructorCompiler.emitter.build(localCount);

        return new JSBytecodeFunction(
                constructorBytecode,
                className,
                0,               // no parameters
                new JSValue[0],  // no closure vars
                null,            // prototype will be set by VM
                true,            // isConstructor
                false,           // not async
                false,           // not generator
                true,            // strict mode
                "constructor() { [default] }"
        );
    }

    private Scope currentScope() {
        if (scopes.isEmpty()) {
            throw new CompilerException("No scope available");
        }
        return scopes.peek();
    }

    // ==================== Scope Management ====================

    private void enterScope() {
        int baseIndex = scopes.isEmpty() ? 0 : currentScope().getLocalCount();
        scopes.push(new Scope(baseIndex));
    }

    private void exitScope() {
        Scope exitingScope = scopes.pop();

        // Track the maximum local count reached
        int localCount = exitingScope.getLocalCount();
        if (localCount > maxLocalCount) {
            maxLocalCount = localCount;
        }

        // Update parent scope's nextLocalIndex to reflect locals allocated in child scope
        if (!scopes.isEmpty()) {
            Scope parentScope = currentScope();
            if (localCount > parentScope.getLocalCount()) {
                parentScope.setLocalCount(localCount);
            }
        }
    }

    /**
     * Extract source code substring from a source location.
     * Returns null if source code is not available or location is invalid.
     */
    private String extractSourceCode(SourceLocation location) {
        if (sourceCode == null || location == null) {
            return null;
        }

        int startOffset = location.offset();
        int endOffset = location.endOffset();

        if (startOffset < 0 || endOffset > sourceCode.length() || startOffset > endOffset) {
            return null;
        }

        return sourceCode.substring(startOffset, endOffset);
    }

    private Integer findLocalInScopes(String name) {
        // Search from innermost scope (most recently pushed) to outermost
        for (Scope scope : scopes) {
            Integer localIndex = scope.getLocal(name);
            if (localIndex != null) {
                return localIndex;
            }
        }
        return null;
    }

    /**
     * Get the name of a method from its key.
     */
    private String getMethodName(ClassDeclaration.MethodDefinition method) {
        Expression key = method.key();
        if (key instanceof Identifier id) {
            return id.name();
        } else if (key instanceof Literal literal) {
            return literal.value().toString();
        } else if (key instanceof PrivateIdentifier privateId) {
            // Private identifier - return name without # prefix
            return privateId.name();
        } else {
            // Computed property name - for now use a placeholder
            return "[computed]";
        }
    }

    /**
     * Set the original source code (used for extracting function source in toString()).
     */
    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    /**
     * Compiler exception for compilation errors.
     */
    public static class CompilerException extends RuntimeException {
        public CompilerException(String message) {
            super(message);
        }
    }

    /**
     * Tracks loop context for break/continue statements.
     */
    private static class LoopContext {
        final List<Integer> breakPositions = new ArrayList<>();
        final List<Integer> continuePositions = new ArrayList<>();
        final int startOffset;

        LoopContext(int startOffset) {
            this.startOffset = startOffset;
        }
    }

    /**
     * Represents a lexical scope for tracking local variables.
     */
    private static class Scope {
        private final Map<String, Integer> locals = new HashMap<>();
        private int nextLocalIndex;

        Scope() {
            this.nextLocalIndex = 0;
        }

        Scope(int baseIndex) {
            this.nextLocalIndex = baseIndex;
        }

        int declareLocal(String name) {
            if (locals.containsKey(name)) {
                return locals.get(name);
            }
            int index = nextLocalIndex++;
            locals.put(name, index);
            return index;
        }

        Integer getLocal(String name) {
            return locals.get(name);
        }

        int getLocalCount() {
            return nextLocalIndex;
        }

        void setLocalCount(int count) {
            this.nextLocalIndex = count;
        }
    }
}
