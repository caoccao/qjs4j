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
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.regexp.RegExpLiteralValue;
import com.caoccao.qjs4j.vm.Bytecode;
import com.caoccao.qjs4j.vm.Opcode;

import java.math.BigInteger;
import java.util.*;

/**
 * Compiles AST into bytecode.
 * Implements visitor pattern for traversing AST nodes and emitting appropriate bytecode.
 */
public final class BytecodeCompiler {
    private final Set<String> annexBFunctionNames;
    private final CaptureResolver captureResolver;
    private final BytecodeEmitter emitter;
    private final Deque<LoopContext> loopStack;
    private final Set<String> nonDeletableGlobalBindings;
    private final Deque<Scope> scopes;
    private boolean inGlobalScope;
    private boolean isGlobalProgram;  // true when compiling top-level program (not inside a function)
    private boolean isInArrowFunction;  // Track if we're currently compiling an arrow function
    private boolean isInAsyncFunction;  // Track if we're currently compiling an async function
    private int maxLocalCount;
    private String pendingLoopLabel;  // Label to attach to next loop context (for labeled loops)
    private Map<String, JSSymbol> privateSymbols;  // Private field symbols for current class
    private int scopeDepth;  // Current lexical scope depth (1+ when inside a scope)
    private String sourceCode;  // Original source code for extracting function sources
    private boolean strictMode;  // Track strict mode context (inherited from parent or set by "use strict")
    private boolean varInGlobalProgram;  // true when compiling a var declaration in a global program

    public BytecodeCompiler() {
        this(false, null);  // Default to non-strict mode
    }

    /**
     * Create a BytecodeCompiler with inherited strict mode.
     * Following QuickJS pattern where nested functions inherit parent's strict mode.
     *
     * @param inheritedStrictMode Strict mode inherited from parent function
     */
    public BytecodeCompiler(boolean inheritedStrictMode) {
        this(inheritedStrictMode, null);
    }

    private BytecodeCompiler(boolean inheritedStrictMode, CaptureResolver parentCaptureResolver) {
        this.annexBFunctionNames = new HashSet<>();
        this.emitter = new BytecodeEmitter();
        this.scopes = new ArrayDeque<>();
        this.loopStack = new ArrayDeque<>();
        this.captureResolver = new CaptureResolver(parentCaptureResolver, this::findLocalInScopes);
        this.inGlobalScope = false;
        this.isGlobalProgram = false;
        this.isInAsyncFunction = false;
        this.isInArrowFunction = false;
        this.maxLocalCount = 0;
        this.nonDeletableGlobalBindings = new HashSet<>();
        this.sourceCode = null;
        this.scopeDepth = 0;
        this.privateSymbols = Map.of();  // Empty by default
        this.strictMode = inheritedStrictMode;
        this.varInGlobalProgram = false;
    }

    private static String[] extractLocalVarNames(Scope scope) {
        int count = scope.getLocalCount();
        if (count == 0) {
            return null;
        }
        String[] names = new String[count];
        for (var entry : scope.locals.entrySet()) {
            String name = entry.getKey();
            int index = entry.getValue();
            if (index >= 0 && index < count && !name.startsWith("$")) {
                names[index] = name;
            }
        }
        return names;
    }

    private void collectLexicalBindings(List<Statement> body, Set<String> lexicals) {
        for (Statement s : body) {
            if (s instanceof VariableDeclaration vd && vd.kind() != VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator d : vd.declarations()) {
                    collectPatternBindingNames(d.id(), lexicals);
                }
            }
        }
    }

    private void collectPatternBindingNames(Pattern pattern, Set<String> names) {
        if (pattern instanceof Identifier id) {
            names.add(id.name());
        } else if (pattern instanceof ArrayPattern arrPattern) {
            for (Pattern element : arrPattern.elements()) {
                if (element != null) {
                    collectPatternBindingNames(element, names);
                }
            }
        } else if (pattern instanceof ObjectPattern objPattern) {
            for (ObjectPattern.Property prop : objPattern.properties()) {
                collectPatternBindingNames(prop.value(), names);
            }
        } else if (pattern instanceof RestElement restElement) {
            collectPatternBindingNames(restElement.argument(), names);
        }
    }

    /**
     * Recursively collect all var-declared names from a statement tree.
     * var declarations are function/global-scoped, so they must be hoisted
     * out of any block nesting (for, try, if, switch, etc.).
     * Does NOT recurse into function declarations/expressions (they have their own scope).
     */
    private void collectVarNamesFromStatement(Statement stmt, Set<String> varNames) {
        if (stmt instanceof VariableDeclaration varDecl && varDecl.kind() == VariableKind.VAR) {
            for (VariableDeclaration.VariableDeclarator d : varDecl.declarations()) {
                collectPatternBindingNames(d.id(), varNames);
            }
        } else if (stmt instanceof ForStatement forStmt) {
            if (forStmt.init() instanceof VariableDeclaration varDecl && varDecl.kind() == VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator d : varDecl.declarations()) {
                    collectPatternBindingNames(d.id(), varNames);
                }
            }
            if (forStmt.body() != null) {
                collectVarNamesFromStatement(forStmt.body(), varNames);
            }
        } else if (stmt instanceof ForInStatement forInStmt) {
            if (forInStmt.left() instanceof VariableDeclaration varDecl && varDecl.kind() == VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator d : varDecl.declarations()) {
                    collectPatternBindingNames(d.id(), varNames);
                }
            }
            if (forInStmt.body() != null) {
                collectVarNamesFromStatement(forInStmt.body(), varNames);
            }
        } else if (stmt instanceof ForOfStatement forOfStmt) {
            if (forOfStmt.left() instanceof VariableDeclaration varDecl && varDecl.kind() == VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator d : varDecl.declarations()) {
                    collectPatternBindingNames(d.id(), varNames);
                }
            }
            if (forOfStmt.body() != null) {
                collectVarNamesFromStatement(forOfStmt.body(), varNames);
            }
        } else if (stmt instanceof BlockStatement block) {
            for (Statement s : block.body()) {
                collectVarNamesFromStatement(s, varNames);
            }
        } else if (stmt instanceof IfStatement ifStmt) {
            collectVarNamesFromStatement(ifStmt.consequent(), varNames);
            if (ifStmt.alternate() != null) {
                collectVarNamesFromStatement(ifStmt.alternate(), varNames);
            }
        } else if (stmt instanceof WhileStatement whileStmt) {
            collectVarNamesFromStatement(whileStmt.body(), varNames);
        } else if (stmt instanceof TryStatement tryStmt) {
            for (Statement s : tryStmt.block().body()) {
                collectVarNamesFromStatement(s, varNames);
            }
            if (tryStmt.handler() != null) {
                for (Statement s : tryStmt.handler().body().body()) {
                    collectVarNamesFromStatement(s, varNames);
                }
            }
            if (tryStmt.finalizer() != null) {
                for (Statement s : tryStmt.finalizer().body()) {
                    collectVarNamesFromStatement(s, varNames);
                }
            }
        } else if (stmt instanceof SwitchStatement switchStmt) {
            for (SwitchStatement.SwitchCase sc : switchStmt.cases()) {
                for (Statement s : sc.consequent()) {
                    collectVarNamesFromStatement(s, varNames);
                }
            }
        } else if (stmt instanceof LabeledStatement labeledStmt) {
            collectVarNamesFromStatement(labeledStmt.body(), varNames);
        }
    }

    // ==================== Program Compilation ====================

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

        // Check if we have any spread elements or holes
        boolean hasSpread = arrayExpr.elements().stream()
                .anyMatch(e -> e instanceof SpreadElement);
        boolean hasHoles = arrayExpr.elements().stream()
                .anyMatch(e -> e == null);

        if (!hasSpread && !hasHoles) {
            // Simple case: no spread elements, no holes - use PUSH_ARRAY
            for (Expression element : arrayExpr.elements()) {
                compileExpression(element);
                emitter.emitOpcode(Opcode.PUSH_ARRAY);
            }
        } else {
            // Complex case: has spread elements or holes
            // Following QuickJS: emit position tracking
            // Stack starts with: array
            int idx = 0;
            boolean needsIndex = false;
            boolean needsLength = false;

            for (Expression element : arrayExpr.elements()) {
                if (element instanceof SpreadElement spreadElement) {
                    // Emit index if not already on stack
                    if (!needsIndex) {
                        emitter.emitOpcodeU32(Opcode.PUSH_I32, idx);
                        needsIndex = true;
                    }
                    // Compile the iterable expression
                    compileExpression(spreadElement.argument());
                    // Emit APPEND to spread elements into the array
                    // Stack: array pos iterable -> array pos
                    emitter.emitOpcode(Opcode.APPEND);
                    // After APPEND, index is updated on stack
                    needsLength = false;
                } else if (element != null) {
                    if (needsIndex) {
                        // We have index on stack, use DEFINE_ARRAY_EL
                        compileExpression(element);
                        emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
                        emitter.emitOpcode(Opcode.INC);
                        needsLength = false;
                    } else {
                        // No index on stack yet
                        // Start using index-based assignment since we have holes or spread
                        emitter.emitOpcodeU32(Opcode.PUSH_I32, idx);
                        needsIndex = true;
                        compileExpression(element);
                        emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
                        emitter.emitOpcode(Opcode.INC);
                        needsLength = false;
                    }
                } else {
                    // Hole in array
                    if (needsIndex) {
                        // We have position on stack, just increment it
                        emitter.emitOpcode(Opcode.INC);
                    } else {
                        idx++;
                    }
                    needsLength = true;
                }
            }

            // If we have a trailing hole, set the array length explicitly
            // This handles cases like [1, 2, ,] where we need length=3 but only 2 elements
            if (needsLength) {
                if (needsIndex) {
                    // Stack: array idx
                    // QuickJS pattern: dup1 (duplicate array), put_field "length"
                    // dup1: array idx -> array array idx
                    emitter.emitOpcode(Opcode.DUP1);  // array array idx
                    emitter.emitOpcodeAtom(Opcode.PUT_FIELD, "length");  // array idx (PUT_FIELD leaves value)
                    emitter.emitOpcode(Opcode.DROP);  // array
                } else {
                    // Stack: array (idx is compile-time constant)
                    // QuickJS pattern: dup, push idx, swap, put_field "length", drop
                    emitter.emitOpcode(Opcode.DUP);  // array array
                    emitter.emitOpcodeU32(Opcode.PUSH_I32, idx);  // array array idx
                    emitter.emitOpcode(Opcode.SWAP);  // array idx array
                    emitter.emitOpcodeAtom(Opcode.PUT_FIELD, "length");  // array idx
                    emitter.emitOpcode(Opcode.DROP);  // array
                }
            } else if (needsIndex) {
                // No trailing hole, just drop the index
                emitter.emitOpcode(Opcode.DROP);
            }
        }
    }

    // ==================== Statement Compilation ====================

    private void compileArrowFunctionExpression(ArrowFunctionExpression arrowExpr) {
        // Create a new compiler for the function body
        // Arrow functions inherit strict mode from parent (QuickJS behavior)
        BytecodeCompiler functionCompiler = new BytecodeCompiler(this.strictMode, this.captureResolver);
        functionCompiler.nonDeletableGlobalBindings.addAll(this.nonDeletableGlobalBindings);

        // Enter function scope and add parameters as locals
        functionCompiler.enterScope();
        functionCompiler.inGlobalScope = false;
        functionCompiler.isInAsyncFunction = arrowExpr.isAsync();  // Track if this is an async function
        functionCompiler.isInArrowFunction = true;  // Arrow functions don't have their own arguments

        // Check for "use strict" directive if body is a block statement
        if (arrowExpr.body() instanceof BlockStatement block && hasUseStrictDirective(block)) {
            functionCompiler.strictMode = true;
        }

        for (Identifier param : arrowExpr.params()) {
            functionCompiler.currentScope().declareLocal(param.name());
        }

        // Emit default parameter initialization following QuickJS pattern:
        // GET_ARG idx, DUP, UNDEFINED, STRICT_EQ, IF_FALSE label, DROP, <default>, DUP, PUT_ARG idx, label:
        if (arrowExpr.defaults() != null) {
            emitDefaultParameterInit(functionCompiler, arrowExpr.defaults());
        }

        // Handle rest parameter if present
        // The REST opcode must be emitted early in the function to initialize the rest array
        if (arrowExpr.restParameter() != null) {
            // Calculate the index where rest arguments start
            int firstRestIndex = arrowExpr.params().size();

            // Emit REST opcode with the starting index
            functionCompiler.emitter.emitOpcode(Opcode.REST);
            functionCompiler.emitter.emitU16(firstRestIndex);

            // Declare the rest parameter as a local and store the rest array
            String restParamName = arrowExpr.restParameter().argument().name();
            int restLocalIndex = functionCompiler.currentScope().declareLocal(restParamName);

            // Store the rest array (from stack top) to the rest parameter local
            functionCompiler.emitter.emitOpcode(Opcode.PUT_LOCAL);
            functionCompiler.emitter.emitU16(restLocalIndex);
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
                int returnValueIndex = functionCompiler.currentScope().declareLocal("$arrow_return_" + functionCompiler.emitter.currentOffset());
                functionCompiler.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, returnValueIndex);
                functionCompiler.emitCurrentScopeUsingDisposal();
                functionCompiler.emitter.emitOpcodeU16(Opcode.GET_LOCAL, returnValueIndex);
                // Emit RETURN_ASYNC for async functions, RETURN for sync functions
                functionCompiler.emitter.emitOpcode(arrowExpr.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
            }
        } else if (arrowExpr.body() instanceof Expression expr) {
            // Expression body - implicitly returns the expression value
            functionCompiler.compileExpression(expr);
            int returnValueIndex = functionCompiler.currentScope().declareLocal("$arrow_return_" + functionCompiler.emitter.currentOffset());
            functionCompiler.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, returnValueIndex);
            functionCompiler.emitCurrentScopeUsingDisposal();
            functionCompiler.emitter.emitOpcodeU16(Opcode.GET_LOCAL, returnValueIndex);
            // Emit RETURN_ASYNC for async functions, RETURN for sync functions
            functionCompiler.emitter.emitOpcode(arrowExpr.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
        }

        int localCount = functionCompiler.currentScope().getLocalCount();
        String[] localVarNames = extractLocalVarNames(functionCompiler.currentScope());
        functionCompiler.exitScope();

        // Build the function bytecode
        Bytecode functionBytecode = functionCompiler.emitter.build(localCount, localVarNames);

        // Arrow functions are always anonymous
        String functionName = "";

        // Extract function source code from original source
        String functionSource = extractSourceCode(arrowExpr.getLocation());

        // Create JSBytecodeFunction
        // Arrow functions cannot be constructors
        int definedArgCount = computeDefinedArgCount(arrowExpr.params(), arrowExpr.defaults(), arrowExpr.restParameter() != null);
        JSBytecodeFunction function = new JSBytecodeFunction(
                functionBytecode,
                functionName,
                definedArgCount,
                new JSValue[functionCompiler.captureResolver.getCapturedBindingCount()],
                null,            // prototype - arrow functions don't have prototype
                false,           // isConstructor - arrow functions cannot be constructors
                arrowExpr.isAsync(),
                false,           // Arrow functions cannot be generators
                true,            // isArrow - this is an arrow function
                false,           // strict - TODO: inherit from enclosing scope
                functionSource   // source code for toString()
        );

        // Prototype chain will be initialized when the function is loaded
        // during bytecode execution (see FCLOSURE opcode handler)

        emitCapturedValues(functionCompiler);
        // Emit FCLOSURE opcode with function in constant pool
        emitter.emitOpcodeConstant(Opcode.FCLOSURE, function);
    }

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
                    Integer capturedIndex = resolveCapturedBindingIndex(name);
                    if (capturedIndex != null) {
                        emitter.emitOpcodeU16(Opcode.GET_VAR_REF, capturedIndex);
                    } else {
                        emitter.emitOpcodeAtom(Opcode.GET_VAR, name);
                    }
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
                } else {
                    if (memberExpr.property() instanceof Identifier propId) {
                        emitter.emitOpcode(Opcode.DUP);  // Duplicate object
                        emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
                    }
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
                Integer capturedIndex = resolveCapturedBindingIndex(name);
                if (capturedIndex != null) {
                    emitter.emitOpcodeU16(Opcode.SET_VAR_REF, capturedIndex);
                } else {
                    emitter.emitOpcodeAtom(Opcode.SET_VAR, name);
                }
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

    private void compileBinaryExpression(BinaryExpression binExpr) {
        if (binExpr.operator() == BinaryExpression.BinaryOperator.IN &&
                binExpr.left() instanceof PrivateIdentifier privateIdentifier) {
            compilePrivateInExpression(privateIdentifier, binExpr.right());
            return;
        }

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
        boolean savedGlobalScope = inGlobalScope;
        enterScope();
        inGlobalScope = false;
        for (Statement stmt : block.body()) {
            compileStatement(stmt);
        }
        emitCurrentScopeUsingDisposal();
        inGlobalScope = savedGlobalScope;
        exitScope();
    }

    private void compileBreakStatement(BreakStatement breakStmt) {
        if (breakStmt.label() != null) {
            // Labeled break: find the matching label in the loop stack
            String labelName = breakStmt.label().name();
            LoopContext target = null;
            for (LoopContext ctx : loopStack) {
                if (labelName.equals(ctx.label)) {
                    target = ctx;
                    break;
                }
            }
            if (target == null) {
                throw new CompilerException("Undefined label '" + labelName + "'");
            }
            // Close iterators for any for-of loops between here and the target
            emitIteratorCloseForLoopsUntil(target);
            emitUsingDisposalsForScopeDepthGreaterThan(target.breakTargetScopeDepth);
            int jumpPos = emitter.emitJump(Opcode.GOTO);
            target.breakPositions.add(jumpPos);
        } else {
            if (loopStack.isEmpty()) {
                throw new CompilerException("Break statement outside of loop");
            }
            // Unlabeled break: find the nearest non-regular-stmt (loop/switch) context
            LoopContext loopContext = null;
            for (LoopContext ctx : loopStack) {
                if (!ctx.isRegularStmt) {
                    loopContext = ctx;
                    break;
                }
            }
            if (loopContext == null) {
                throw new CompilerException("Break statement outside of loop");
            }
            emitUsingDisposalsForScopeDepthGreaterThan(loopContext.breakTargetScopeDepth);
            int jumpPos = emitter.emitJump(Opcode.GOTO);
            loopContext.breakPositions.add(jumpPos);
        }
    }

    private void compileCallExpression(CallExpression callExpr) {
        // Check if any arguments contain spread
        boolean hasSpread = callExpr.arguments().stream()
                .anyMatch(arg -> arg instanceof SpreadElement);

        if (hasSpread) {
            // Use APPLY for calls with spread arguments
            compileCallExpressionWithSpread(callExpr);
        } else {
            // Use regular CALL for calls without spread
            compileCallExpressionRegular(callExpr);
        }
    }

    private void compileCallExpressionRegular(CallExpression callExpr) {
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

    private void compileCallExpressionWithSpread(CallExpression callExpr) {
        // For calls with spread: func(...args) or obj.method(...args)
        // Strategy: Build an arguments array and use APPLY

        // Determine thisArg and function
        if (callExpr.callee() instanceof MemberExpression memberExpr) {
            // Method call: obj.method(...args)
            // Stack should be: thisArg function argsArray

            // Push object (will be thisArg)
            compileExpression(memberExpr.object());

            // Duplicate it for getting the method
            emitter.emitOpcode(Opcode.DUP);

            // Get the method
            if (memberExpr.computed()) {
                compileExpression(memberExpr.property());
                emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (memberExpr.property() instanceof Identifier propId) {
                emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
            } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                String fieldName = privateId.name();
                JSSymbol symbol = privateSymbols != null ? privateSymbols.get(fieldName) : null;
                if (symbol != null) {
                    emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                    emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
                } else {
                    emitter.emitOpcode(Opcode.DROP);
                    emitter.emitOpcode(Opcode.UNDEFINED);
                }
            }

            // Stack: thisArg function
        } else {
            // Regular function call: func(...args)
            // Push undefined as thisArg
            emitter.emitOpcode(Opcode.UNDEFINED);

            // Push function
            compileExpression(callExpr.callee());

            // Stack: thisArg function
        }

        emitArgumentsArrayWithSpread(callExpr.arguments());

        // Stack: thisArg function argsArray
        // Use APPLY to call with the array
        // Parameter: isConstructorCall (0 for regular call, 1 for new)
        emitter.emitOpcodeU16(Opcode.APPLY, 0);
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

                // Compile method. Static methods share the same private name scope.
                JSBytecodeFunction methodFunc = compileMethodAsFunction(
                        method,
                        getMethodName(method),
                        false,
                        List.of(),
                        privateSymbols,
                        computedFieldSymbols,
                        Map.of(),
                        false
                );
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
                JSBytecodeFunction methodFunc = compileMethodAsFunction(
                        method,
                        getMethodName(method),
                        false,
                        List.of(),
                        privateSymbols,
                        computedFieldSymbols,
                        Map.of(),
                        false
                );
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

        installPrivateStaticMethods(privateStaticMethodFunctions, privateSymbols);

        // Evaluate all computed field names once at class definition time.
        // This matches QuickJS behavior and avoids re-evaluating key side effects per instance.
        for (ClassDeclaration.PropertyDefinition field : computedFieldsInDefinitionOrder) {
            compileComputedFieldNameCache(field, computedFieldSymbols);
        }

        // Swap back to original order: proto constructor
        emitter.emitOpcode(Opcode.SWAP);
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
            emitter.emitOpcode(Opcode.DUP);
            // Stack: proto constructor constructor

            // Push the static initializer function
            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, staticInitializerFunc);
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
                nonDeletableGlobalBindings.add(varName);
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
                // For static methods, constructor is the target object
                emitter.emitOpcode(Opcode.SWAP);
                // Stack: proto constructor

                JSBytecodeFunction methodFunc = compileMethodAsFunction(
                        method,
                        getMethodName(method),
                        false,
                        List.of(),
                        privateSymbols,
                        computedFieldSymbols,
                        Map.of(),
                        false
                );
                emitter.emitOpcodeConstant(Opcode.PUSH_CONST, methodFunc);
                // Stack: proto constructor method

                String methodName = getMethodName(method);
                emitter.emitOpcodeAtom(Opcode.DEFINE_METHOD, methodName);
                // Stack: proto constructor

                // Restore canonical order for next iteration
                emitter.emitOpcode(Opcode.SWAP);
                // Stack: constructor proto
            } else {
                JSBytecodeFunction methodFunc = compileMethodAsFunction(
                        method,
                        getMethodName(method),
                        false,
                        List.of(),
                        privateSymbols,
                        computedFieldSymbols,
                        Map.of(),
                        false
                );
                emitter.emitOpcodeConstant(Opcode.PUSH_CONST, methodFunc);
                // Stack: constructor proto method

                String methodName = getMethodName(method);
                emitter.emitOpcodeAtom(Opcode.DEFINE_METHOD, methodName);
                // Stack: constructor proto
            }
        }

        installPrivateStaticMethods(privateStaticMethodFunctions, privateSymbols);

        // Evaluate all computed field names once at class definition time.
        for (ClassDeclaration.PropertyDefinition field : computedFieldsInDefinitionOrder) {
            compileComputedFieldNameCache(field, computedFieldSymbols);
        }

        // Swap back to: proto constructor
        emitter.emitOpcode(Opcode.SWAP);
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
            emitter.emitOpcode(Opcode.DUP);
            // Stack: proto constructor constructor

            // Push the static initializer function
            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, staticInitializerFunc);
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

        // Drop prototype, keep constructor on stack
        emitter.emitOpcode(Opcode.NIP);
        // Stack: constructor

        // For class expressions, we leave the constructor on the stack
        // (unlike class declarations which bind it to a variable)
    }

    /**
     * Evaluate and cache a computed class field name on the constructor object once.
     * Expects stack before/after to be: constructor proto.
     */
    private void compileComputedFieldNameCache(
            ClassDeclaration.PropertyDefinition field,
            IdentityHashMap<ClassDeclaration.PropertyDefinition, JSSymbol> computedFieldSymbols) {
        JSSymbol computedFieldSymbol = computedFieldSymbols.get(field);
        if (computedFieldSymbol == null) {
            throw new CompilerException("Computed field key symbol not found");
        }

        // constructor proto -> proto constructor
        emitter.emitOpcode(Opcode.SWAP);
        // proto constructor -> proto constructor constructor
        emitter.emitOpcode(Opcode.DUP);
        // proto constructor constructor -> proto constructor constructor hiddenSymbol
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, computedFieldSymbol);
        // proto constructor constructor hiddenSymbol -> proto constructor constructor hiddenSymbol rawKey
        compileExpression(field.key());
        // Convert once to property key (QuickJS OP_to_propkey behavior)
        emitter.emitOpcode(Opcode.TO_PROPKEY);
        // proto constructor constructor hiddenSymbol key -> proto constructor constructor
        emitter.emitOpcode(Opcode.DEFINE_PROP);
        // Drop duplicated constructor
        emitter.emitOpcode(Opcode.DROP);
        // Restore canonical order: constructor proto
        emitter.emitOpcode(Opcode.SWAP);
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
        if (contStmt.label() != null) {
            // Labeled continue: find the matching label in the loop stack (must be a loop, not regular stmt)
            String labelName = contStmt.label().name();
            LoopContext target = null;
            for (LoopContext ctx : loopStack) {
                if (labelName.equals(ctx.label) && !ctx.isRegularStmt) {
                    target = ctx;
                    break;
                }
            }
            if (target == null) {
                throw new CompilerException("Undefined label '" + labelName + "'");
            }
            // Close iterators for any for-of loops between here and the target
            emitIteratorCloseForLoopsUntil(target);
            emitUsingDisposalsForScopeDepthGreaterThan(target.continueTargetScopeDepth);
            int jumpPos = emitter.emitJump(Opcode.GOTO);
            target.continuePositions.add(jumpPos);
        } else {
            // Unlabeled continue: find the nearest loop (skip regular stmt entries)
            LoopContext loopContext = null;
            for (LoopContext ctx : loopStack) {
                if (!ctx.isRegularStmt) {
                    loopContext = ctx;
                    break;
                }
            }
            if (loopContext == null) {
                throw new CompilerException("Continue statement outside of loop");
            }
            emitUsingDisposalsForScopeDepthGreaterThan(loopContext.continueTargetScopeDepth);
            int jumpPos = emitter.emitJump(Opcode.GOTO);
            loopContext.continuePositions.add(jumpPos);
        }
    }

    private void compileExpression(Expression expr) {
        if (expr instanceof Literal literal) {
            compileLiteral(literal);
        } else if (expr instanceof Identifier id) {
            compileIdentifier(id);
        } else if (expr instanceof PrivateIdentifier privateIdentifier) {
            throw new CompilerException("undefined private field '#" + privateIdentifier.name() + "'");
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
        } else if (expr instanceof SequenceExpression seqExpr) {
            compileSequenceExpression(seqExpr);
        }
    }

    /**
     * Compile field initialization code for instance fields.
     * Emits code to set each field on 'this' with its initializer value.
     * For private fields, uses the symbol from privateSymbols map.
     */
    private void compileFieldInitialization(List<ClassDeclaration.PropertyDefinition> fields,
                                            Map<String, JSSymbol> privateSymbols,
                                            IdentityHashMap<ClassDeclaration.PropertyDefinition, JSSymbol> computedFieldSymbols) {
        for (ClassDeclaration.PropertyDefinition field : fields) {
            boolean isPrivate = field.isPrivate();

            // Push 'this' onto stack
            emitter.emitOpcode(Opcode.PUSH_THIS);

            if (isPrivate) {
                if (!(field.key() instanceof PrivateIdentifier privateId)) {
                    throw new CompilerException("Invalid private field key");
                }
                String fieldName = privateId.name();

                // Compile initializer or emit undefined
                if (field.value() != null) {
                    compileExpression(field.value());
                } else {
                    emitter.emitOpcode(Opcode.UNDEFINED);
                }

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
                if (field.computed()) {
                    // Stack: this
                    JSSymbol computedFieldSymbol = computedFieldSymbols.get(field);
                    if (computedFieldSymbol == null) {
                        throw new CompilerException("Computed field key not found");
                    }
                    // Load the precomputed key from the current constructor function:
                    // Stack: this func symbol -> this key
                    emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                    emitter.emitU8(2); // SPECIAL_OBJECT_THIS_FUNC
                    emitter.emitOpcodeConstant(Opcode.PUSH_CONST, computedFieldSymbol);
                    emitter.emitOpcode(Opcode.GET_ARRAY_EL);
                } else {
                    emitNonComputedPublicFieldKey(field.key());
                }

                // Compile initializer or emit undefined
                if (field.value() != null) {
                    compileExpression(field.value());
                } else {
                    emitter.emitOpcode(Opcode.UNDEFINED);
                }

                // Stack: this key value
                emitter.emitOpcode(Opcode.DEFINE_PROP);
                // Stack: this
            }

            // Drop 'this' from stack
            emitter.emitOpcode(Opcode.DROP);
        }
    }

    private void compileForInStatement(ForInStatement forInStmt) {
        // Determine how to assign the key: VariableDeclaration or expression
        boolean isExpressionBased = forInStmt.left() instanceof Expression;
        String varName = null;
        VariableDeclaration varDecl = null;

        if (!isExpressionBased) {
            varDecl = (VariableDeclaration) forInStmt.left();
            if (varDecl.declarations().size() != 1) {
                throw new CompilerException("for-in loop must have exactly one variable");
            }
            Pattern pattern = varDecl.declarations().get(0).id();
            if (!(pattern instanceof Identifier id)) {
                throw new CompilerException("for-in loop variable must be an identifier");
            }
            varName = id.name();

            // `var` in for-in is function/global scoped, not the loop lexical scope.
            if (varDecl.kind() == VariableKind.VAR) {
                currentScope().declareLocal(varName);
            }
        }

        enterScope();

        Integer varIndex = null;
        if (!isExpressionBased) {
            // For let/const, declare in the loop scope; for var, find it in the parent scope
            if (varDecl.kind() != VariableKind.VAR) {
                currentScope().declareLocal(varName);
            }
            varIndex = findLocalInScopes(varName);
        }

        // Compile the object expression
        compileExpression(forInStmt.right());
        // Stack: obj

        // Emit FOR_IN_START to create enumerator
        emitter.emitOpcode(Opcode.FOR_IN_START);
        // Stack: enum_obj

        // Start of loop
        int loopStart = emitter.currentOffset();
        LoopContext loop = createLoopContext(loopStart, scopeDepth - 1, scopeDepth);
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
        if (isExpressionBased) {
            // Expression-based: for (expr in obj) — assign via PUT_VAR or member expression
            Expression leftExpr = (Expression) forInStmt.left();
            if (leftExpr instanceof Identifier id) {
                Integer localIdx = findLocalInScopes(id.name());
                if (localIdx != null) {
                    emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIdx);
                } else {
                    emitter.emitOpcodeAtom(Opcode.PUT_VAR, id.name());
                }
            } else if (leftExpr instanceof MemberExpression memberExpr) {
                // For member expressions like obj.prop or obj[expr]
                compileExpression(memberExpr.object());
                if (memberExpr.computed()) {
                    compileExpression(memberExpr.property());
                    // Stack: enum_obj key obj index
                    // Need to reorder: swap obj and index under key
                    // Use PUT_ARRAY_EL: pops [value, obj, index] -> obj[index] = value
                    // But stack is: enum_obj key obj index - need key on top
                    // This is complex; use a simpler approach with a local temp
                    throw new CompilerException("Computed member expression in for-in not yet supported");
                } else {
                    // Stack: enum_obj key obj
                    String propName = ((Identifier) memberExpr.property()).name();
                    // Swap key and obj: we need obj on top then key, then PUT_FIELD
                    emitter.emitOpcode(Opcode.SWAP);
                    // Stack: enum_obj obj key
                    emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propName);
                    // Stack: enum_obj obj (PUT_FIELD leaves obj on stack)
                    emitter.emitOpcode(Opcode.DROP);
                }
            } else {
                emitter.emitOpcode(Opcode.DROP);
            }
        } else if (varIndex != null) {
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
        emitCurrentScopeUsingDisposal();
        exitScope();
    }

    private void compileForOfStatement(ForOfStatement forOfStmt) {
        // Declare the loop variable(s) - supports both Identifier and destructuring patterns
        if (!(forOfStmt.left() instanceof VariableDeclaration varDecl)) {
            throw new CompilerException("Expression-based for-of not yet supported");
        }
        if (varDecl.declarations().size() != 1) {
            throw new CompilerException("for-of loop must have exactly one variable");
        }
        Pattern pattern = varDecl.declarations().get(0).id();
        boolean isVar = varDecl.kind() == VariableKind.VAR;

        // `var` in for-of is function/global scoped, not the loop lexical scope.
        // Pre-declare var bindings in the parent scope so they survive after the loop exits.
        // Only do this when inside a function (not global scope), since at global scope
        // var bindings are stored as global object properties via PUT_VAR.
        if (isVar && !inGlobalScope) {
            declarePatternVariables(pattern);
        }

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

        // For let/const, declare in the loop scope.
        // For var at global scope, declare in the loop scope (will use PUT_VAR for storage).
        // For var inside a function, already declared in parent scope above.
        if (!isVar || inGlobalScope) {
            declarePatternVariables(pattern);
        }

        // Save and temporarily disable inGlobalScope since for-of loop variables are always local
        boolean savedInGlobalScope = inGlobalScope;
        inGlobalScope = false;

        // Start of loop
        int loopStart = emitter.currentOffset();
        LoopContext loop = createLoopContext(loopStart, scopeDepth - 1, scopeDepth);
        loop.hasIterator = true;
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

            // Assign value to pattern
            compileForOfValueAssignment(pattern, isVar);
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

            // Assign value to pattern
            compileForOfValueAssignment(pattern, isVar);
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

        // Break target - break statements jump here and close the iterator record.
        int breakTarget = emitter.currentOffset();
        emitter.emitOpcode(Opcode.ITERATOR_CLOSE);

        // Patch break statements to jump after the value drop
        for (int breakPos : loop.breakPositions) {
            emitter.patchJump(breakPos, breakTarget);
        }

        // Patch continue statements (jump back to loop start)
        for (int continuePos : loop.continuePositions) {
            emitter.patchJump(continuePos, loopStart);
        }

        loopStack.pop();

        // Restore inGlobalScope flag
        inGlobalScope = savedInGlobalScope;

        emitCurrentScopeUsingDisposal();
        exitScope();
    }

    /**
     * Assign the iteration value in a for-of loop.
     * For var declarations, use findLocalInScopes to store to the parent scope's local
     * (since var is function-scoped, not block-scoped). For let/const, use the normal
     * compilePatternAssignment which creates locals in the current (loop) scope.
     */
    private void compileForOfValueAssignment(Pattern pattern, boolean isVar) {
        if (isVar && pattern instanceof Identifier id) {
            Integer localIndex = findLocalInScopes(id.name());
            if (localIndex != null) {
                emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
            } else if (inGlobalScope) {
                emitter.emitOpcodeAtom(Opcode.PUT_VAR, id.name());
            } else {
                int idx = currentScope().declareLocal(id.name());
                emitter.emitOpcodeU16(Opcode.PUT_LOCAL, idx);
            }
        } else {
            compilePatternAssignment(pattern);
        }
    }

    private void compileForStatement(ForStatement forStmt) {
        boolean initCompiled = false;
        if (forStmt.init() instanceof VariableDeclaration varDecl && varDecl.kind() == VariableKind.VAR) {
            // `var` in for-init is function/global scoped, not the loop lexical scope.
            compileVariableDeclaration(varDecl);
            initCompiled = true;
        }

        enterScope();

        // Compile init
        if (!initCompiled && forStmt.init() != null) {
            if (forStmt.init() instanceof VariableDeclaration varDecl) {
                boolean savedInGlobalScope = inGlobalScope;
                if (inGlobalScope && varDecl.kind() != VariableKind.VAR) {
                    // Top-level lexical loop bindings should stay lexical, not become global object properties.
                    inGlobalScope = false;
                }
                compileVariableDeclaration(varDecl);
                inGlobalScope = savedInGlobalScope;
            } else if (forStmt.init() instanceof Expression expr) {
                compileExpression(expr);
                emitter.emitOpcode(Opcode.DROP);
            }
        }

        int loopStart = emitter.currentOffset();
        LoopContext loop = createLoopContext(loopStart, scopeDepth - 1, scopeDepth);
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
        emitCurrentScopeUsingDisposal();
        exitScope();
    }

    private void compileFunctionDeclaration(FunctionDeclaration funcDecl) {
        // Pre-declare function name as a local in the current scope (if non-global and not
        // already declared). This must happen BEFORE creating the child compiler so the
        // function body can capture the name via closure for self-reference.
        String functionName = funcDecl.id().name();
        if (!inGlobalScope && findLocalInScopes(functionName) == null) {
            currentScope().declareLocal(functionName);
        }

        // Create a new compiler for the function body
        // Nested functions inherit strict mode from parent (QuickJS behavior)
        BytecodeCompiler functionCompiler = new BytecodeCompiler(this.strictMode, this.captureResolver);
        functionCompiler.nonDeletableGlobalBindings.addAll(this.nonDeletableGlobalBindings);

        // Enter function scope and add parameters as locals
        functionCompiler.enterScope();
        functionCompiler.inGlobalScope = false;
        functionCompiler.isInAsyncFunction = funcDecl.isAsync();  // Track if this is an async function

        // Check for "use strict" directive early and update strict mode
        // This ensures nested functions inherit the correct strict mode
        if (hasUseStrictDirective(funcDecl.body())) {
            functionCompiler.strictMode = true;
        }

        for (Identifier param : funcDecl.params()) {
            functionCompiler.currentScope().declareLocal(param.name());
        }

        // Emit default parameter initialization following QuickJS pattern
        if (funcDecl.defaults() != null) {
            emitDefaultParameterInit(functionCompiler, funcDecl.defaults());
        }

        // Handle rest parameter if present
        // The REST opcode must be emitted early in the function to initialize the rest array
        if (funcDecl.restParameter() != null) {
            // Calculate the index where rest arguments start
            int firstRestIndex = funcDecl.params().size();

            // Emit REST opcode with the starting index
            functionCompiler.emitter.emitOpcode(Opcode.REST);
            functionCompiler.emitter.emitU16(firstRestIndex);

            // Declare the rest parameter as a local and store the rest array
            String restParamName = funcDecl.restParameter().argument().name();
            int restLocalIndex = functionCompiler.currentScope().declareLocal(restParamName);

            // Store the rest array (from stack top) to the rest parameter local
            functionCompiler.emitter.emitOpcode(Opcode.PUT_LOCAL);
            functionCompiler.emitter.emitU16(restLocalIndex);
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
            int returnValueIndex = functionCompiler.currentScope().declareLocal("$function_return_" + functionCompiler.emitter.currentOffset());
            functionCompiler.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, returnValueIndex);
            functionCompiler.emitCurrentScopeUsingDisposal();
            functionCompiler.emitter.emitOpcodeU16(Opcode.GET_LOCAL, returnValueIndex);
            // Emit RETURN_ASYNC for async functions, RETURN for sync functions
            functionCompiler.emitter.emitOpcode(funcDecl.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
        }

        int localCount = functionCompiler.currentScope().getLocalCount();
        String[] localVarNames = extractLocalVarNames(functionCompiler.currentScope());
        functionCompiler.exitScope();

        // Build the function bytecode
        Bytecode functionBytecode = functionCompiler.emitter.build(localCount, localVarNames);

        // Function name (already extracted at start of method)

        // Detect "use strict" directive in function body
        // Combine inherited strict mode with local "use strict" directive
        boolean isStrict = functionCompiler.strictMode || hasUseStrictDirective(funcDecl.body());

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
            if (funcDecl.restParameter() != null) {
                if (!funcDecl.params().isEmpty()) funcSource.append(", ");
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
        Integer selfCaptureIdx = functionCompiler.captureResolver.findCapturedBindingIndex(functionName);
        int selfCaptureIndex = selfCaptureIdx != null ? selfCaptureIdx : -1;

        // Create JSBytecodeFunction
        int definedArgCount = computeDefinedArgCount(funcDecl.params(), funcDecl.defaults(), funcDecl.restParameter() != null);
        JSBytecodeFunction function = new JSBytecodeFunction(
                functionBytecode,
                functionName,
                definedArgCount,
                new JSValue[functionCompiler.captureResolver.getCapturedBindingCount()],
                null,            // prototype - will be set by VM
                true,            // isConstructor - regular functions can be constructors
                funcDecl.isAsync(),
                funcDecl.isGenerator(),
                false,           // isArrow - regular function, not arrow
                isStrict,        // strict - detected from "use strict" directive in function body
                functionSource,  // source code for toString()
                selfCaptureIndex // closure self-reference index (-1 if none)
        );

        emitCapturedValues(functionCompiler);
        // Emit FCLOSURE opcode with function in constant pool
        emitter.emitOpcodeConstant(Opcode.FCLOSURE, function);

        // Store the function in a variable with its name
        Integer localIndex = findLocalInScopes(functionName);
        if (localIndex != null) {
            if (annexBFunctionNames.contains(functionName)) {
                // Annex B.3.3.3 runtime hook: store in both block scope and global scope
                emitter.emitOpcode(Opcode.DUP);
                emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
                emitter.emitOpcodeAtom(Opcode.PUT_VAR, functionName);
            } else {
                emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
            }
        } else {
            // Declare the function as a global variable or in the current scope
            if (inGlobalScope) {
                nonDeletableGlobalBindings.add(functionName);
                emitter.emitOpcodeAtom(Opcode.PUT_VAR, functionName);
            } else {
                // Declare it as a local
                localIndex = currentScope().declareLocal(functionName);
                if (annexBFunctionNames.contains(functionName)) {
                    // Annex B.3.3.3 runtime hook: store in both block scope and global scope
                    emitter.emitOpcode(Opcode.DUP);
                    emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
                    emitter.emitOpcodeAtom(Opcode.PUT_VAR, functionName);
                } else {
                    emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
                }
            }
        }
    }

    private void compileFunctionExpression(FunctionExpression funcExpr) {
        // Create a new compiler for the function body
        // Nested functions inherit strict mode from parent (QuickJS behavior)
        BytecodeCompiler functionCompiler = new BytecodeCompiler(this.strictMode, this.captureResolver);
        functionCompiler.nonDeletableGlobalBindings.addAll(this.nonDeletableGlobalBindings);

        // Enter function scope and add parameters as locals
        functionCompiler.enterScope();
        functionCompiler.inGlobalScope = false;
        functionCompiler.isInAsyncFunction = funcExpr.isAsync();

        // Check for "use strict" directive early and update strict mode
        // This ensures nested functions inherit the correct strict mode
        if (hasUseStrictDirective(funcExpr.body())) {
            functionCompiler.strictMode = true;
        }

        for (Identifier param : funcExpr.params()) {
            functionCompiler.currentScope().declareLocal(param.name());
        }

        // Emit default parameter initialization following QuickJS pattern
        if (funcExpr.defaults() != null) {
            emitDefaultParameterInit(functionCompiler, funcExpr.defaults());
        }

        // Handle rest parameter if present
        // The REST opcode must be emitted early in the function to initialize the rest array
        if (funcExpr.restParameter() != null) {
            // Calculate the index where rest arguments start
            int firstRestIndex = funcExpr.params().size();

            // Emit REST opcode with the starting index
            functionCompiler.emitter.emitOpcode(Opcode.REST);
            functionCompiler.emitter.emitU16(firstRestIndex);

            // Declare the rest parameter as a local and store the rest array
            String restParamName = funcExpr.restParameter().argument().name();
            int restLocalIndex = functionCompiler.currentScope().declareLocal(restParamName);

            // Store the rest array (from stack top) to the rest parameter local
            functionCompiler.emitter.emitOpcode(Opcode.PUT_LOCAL);
            functionCompiler.emitter.emitU16(restLocalIndex);
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
            int returnValueIndex = functionCompiler.currentScope().declareLocal("$function_return_" + functionCompiler.emitter.currentOffset());
            functionCompiler.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, returnValueIndex);
            functionCompiler.emitCurrentScopeUsingDisposal();
            functionCompiler.emitter.emitOpcodeU16(Opcode.GET_LOCAL, returnValueIndex);
            functionCompiler.emitter.emitOpcode(funcExpr.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
        }

        int localCount = functionCompiler.currentScope().getLocalCount();
        String[] localVarNames = extractLocalVarNames(functionCompiler.currentScope());
        functionCompiler.exitScope();

        // Build the function bytecode
        Bytecode functionBytecode = functionCompiler.emitter.build(localCount, localVarNames);

        // Get function name (empty string for anonymous)
        String functionName = funcExpr.id() != null ? funcExpr.id().name() : "";

        // Detect "use strict" directive in function body
        // Combine inherited strict mode with local "use strict" directive
        boolean isStrict = functionCompiler.strictMode || hasUseStrictDirective(funcExpr.body());

        // Extract function source code from original source
        String functionSource = extractSourceCode(funcExpr.getLocation());

        // Create JSBytecodeFunction
        int definedArgCount = computeDefinedArgCount(funcExpr.params(), funcExpr.defaults(), funcExpr.restParameter() != null);
        JSBytecodeFunction function = new JSBytecodeFunction(
                functionBytecode,
                functionName,
                definedArgCount,
                new JSValue[functionCompiler.captureResolver.getCapturedBindingCount()],
                null,            // prototype - will be set by VM
                true,            // isConstructor - regular functions can be constructors
                funcExpr.isAsync(),
                funcExpr.isGenerator(),
                false,           // isArrow - regular function, not arrow
                isStrict,        // strict - detected from "use strict" directive in function body
                functionSource   // source code for toString()
        );

        // Prototype chain will be initialized when the function is loaded
        // during bytecode execution (see FCLOSURE opcode handler)

        emitCapturedValues(functionCompiler);
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
        // For arrow functions, SPECIAL_OBJECT will walk up to find parent's arguments
        // For regular functions, SPECIAL_OBJECT will create the arguments object
        // Following QuickJS: arrow functions inherit arguments from enclosing scope
        if (JSArguments.NAME.equals(name) && !inGlobalScope) {
            // Emit SPECIAL_OBJECT opcode with type 0 (SPECIAL_OBJECT_ARGUMENTS)
            // The VM will handle differently for arrow vs regular functions
            emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            emitter.emitU8(0);  // Type 0 = arguments object
            return;
        }

        // Always check local scopes first, even in global scope (for nested blocks/loops)
        // Search from innermost scope (most recently pushed) to outermost
        // ArrayDeque.push() adds to front, iterator() iterates from front to back
        Integer localIndex = findLocalInScopes(name);

        if (localIndex != null) {
            emitter.emitOpcodeU16(Opcode.GET_LOCAL, localIndex);
            return;
        }

        Integer capturedIndex = resolveCapturedBindingIndex(name);
        if (capturedIndex != null) {
            emitter.emitOpcodeU16(Opcode.GET_VAR_REF, capturedIndex);
        } else {
            // Not found in local scopes, use global variable
            emitter.emitOpcodeAtom(Opcode.GET_VAR, name);
        }
    }

    private void compileIfStatement(IfStatement ifStmt) {
        // In strict mode, function declarations are not allowed as direct body of if/else
        // (they must be at top level or inside a block). Per ES2024 B.3.3 Note.
        if (strictMode) {
            if (ifStmt.consequent() instanceof FunctionDeclaration
                    || ifStmt.alternate() instanceof FunctionDeclaration) {
                throw new JSSyntaxErrorException(
                        "In strict mode code, functions can only be declared at top level or inside a block.");
            }
        }

        // Compile condition
        compileExpression(ifStmt.test());

        // Jump to else/end if condition is false
        int jumpToElse = emitter.emitJump(Opcode.IF_FALSE);

        // Compile consequent — wrap bare function declarations in implicit block scope
        compileImplicitBlockStatement(ifStmt.consequent());

        if (ifStmt.alternate() != null) {
            // Jump over else block after consequent
            int jumpToEnd = emitter.emitJump(Opcode.GOTO);

            // Patch jump to else
            emitter.patchJump(jumpToElse, emitter.currentOffset());

            // Compile alternate — wrap bare function declarations in implicit block scope
            compileImplicitBlockStatement(ifStmt.alternate());

            // Patch jump to end
            emitter.patchJump(jumpToEnd, emitter.currentOffset());
        } else {
            // Patch jump to end
            emitter.patchJump(jumpToElse, emitter.currentOffset());
        }
    }

    /**
     * Compile a statement that may be a bare function declaration in sloppy mode.
     * Per ES2024 B.3.3, function declarations in if-statement positions are treated
     * as if wrapped in a block. This ensures the function binding is block-scoped
     * and does not overwrite outer let/const bindings when Annex B is skipped.
     */
    private void compileImplicitBlockStatement(Statement stmt) {
        if (stmt instanceof FunctionDeclaration funcDecl && funcDecl.id() != null) {
            // Per ES2024 B.3.3, function declarations in if-statement positions
            // are treated as if wrapped in a block scope.
            enterScope();
            currentScope().declareLocal(funcDecl.id().name());
            compileFunctionDeclaration(funcDecl);
            emitCurrentScopeUsingDisposal();
            exitScope();
        } else {
            compileStatement(stmt);
        }
    }

    /**
     * Compile a labeled loop: the label is attached to the loop's LoopContext.
     * This is needed so that 'break label;' and 'continue label;' work on the loop.
     */
    private void compileLabeledLoop(String labelName, Statement loopStmt) {
        // We temporarily store the label name so the loop compilation methods can pick it up
        pendingLoopLabel = labelName;
        compileStatement(loopStmt);
        pendingLoopLabel = null;
    }

    /**
     * Compile a labeled statement following QuickJS js_parse_statement_or_decl.
     * Creates a break entry so that 'break label;' jumps past the labeled body.
     * For labeled loops (while/for/for-in/for-of), the label is attached to the
     * loop's LoopContext so labeled break/continue work on the loop.
     */
    private void compileLabeledStatement(LabeledStatement labeledStmt) {
        String labelName = labeledStmt.label().name();
        Statement body = labeledStmt.body();

        // In strict mode, function declarations are not allowed as labeled statement body
        // (they must be at top level or inside a block). Per ES2024 B.3.3 Note.
        if (strictMode && body instanceof FunctionDeclaration) {
            throw new JSSyntaxErrorException(
                    "In strict mode code, functions can only be declared at top level or inside a block.");
        }

        // Check if the body is a loop statement — if so, the label applies to the loop
        if (body instanceof WhileStatement || body instanceof ForStatement
                || body instanceof ForInStatement || body instanceof ForOfStatement) {
            // Push a labeled loop context; the loop compilation will use loopStack.peek()
            // We need to wrap the loop compilation to attach the label
            compileLabeledLoop(labelName, body);
        } else {
            // Regular labeled statement: only 'break label;' is valid (not continue)
            LoopContext labelContext = new LoopContext(emitter.currentOffset(), scopeDepth, scopeDepth, labelName);
            labelContext.isRegularStmt = true;
            loopStack.push(labelContext);

            // Body can be null for empty statements (label: ;)
            if (body != null) {
                compileStatement(body);
            }

            // Patch all break positions to jump here
            int breakTarget = emitter.currentOffset();
            for (int pos : labelContext.breakPositions) {
                emitter.patchJump(pos, breakTarget);
            }
            loopStack.pop();
        }
    }

    private void compileLiteral(Literal literal) {
        Object value = literal.value();

        if (value == null) {
            emitter.emitOpcode(Opcode.NULL);
        } else if (value instanceof Boolean bool) {
            emitter.emitOpcode(bool ? Opcode.PUSH_TRUE : Opcode.PUSH_FALSE);
        } else if (value instanceof BigInteger bigInt) {
            // Check BigInteger before Number since BigInteger extends Number.
            // Match QuickJS: emit PUSH_BIGINT_I32 when the literal fits in signed i32.
            if (bigInt.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
                    && bigInt.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
                emitter.emitOpcode(Opcode.PUSH_BIGINT_I32);
                emitter.emitI32(bigInt.intValue());
            } else {
                emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSBigInt(bigInt));
            }
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
        } else if (value instanceof RegExpLiteralValue regExpLiteralValue) {
            String source = regExpLiteralValue.source();
            int lastSlash = source.lastIndexOf('/');
            if (lastSlash > 0) {
                String pattern = source.substring(1, lastSlash);
                String flags = lastSlash < source.length() - 1 ? source.substring(lastSlash + 1) : "";
                try {
                    JSRegExp regexp = new JSRegExp(pattern, flags);
                    emitter.emitOpcodeConstant(Opcode.PUSH_CONST, regexp);
                    return;
                } catch (Exception e) {
                    throw new CompilerException("Invalid regular expression literal: " + source);
                }
            }
            throw new CompilerException("Invalid regular expression literal: " + source);
        } else if (value instanceof String str) {
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
                Integer capturedIndex = resolveCapturedBindingIndex(name);
                if (capturedIndex != null) {
                    emitter.emitOpcodeU16(Opcode.GET_VAR_REF, capturedIndex);
                } else {
                    emitter.emitOpcodeAtom(Opcode.GET_VAR, name);
                }
            }
        } else if (left instanceof MemberExpression memberExpr) {
            compileExpression(memberExpr.object());
            if (memberExpr.computed()) {
                compileExpression(memberExpr.property());
                emitter.emitOpcode(Opcode.DUP2);  // Duplicate obj and prop
                emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else {
                if (memberExpr.property() instanceof Identifier propId) {
                    emitter.emitOpcode(Opcode.DUP);  // Duplicate object
                    emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
                }
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
                Integer capturedIndex = resolveCapturedBindingIndex(name);
                if (capturedIndex != null) {
                    emitter.emitOpcodeU16(Opcode.SET_VAR_REF, capturedIndex);
                } else {
                    emitter.emitOpcodeAtom(Opcode.SET_VAR, name);
                }
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
            IdentityHashMap<ClassDeclaration.PropertyDefinition, JSSymbol> computedFieldSymbols,
            Map<String, JSBytecodeFunction> privateInstanceMethodFunctions,
            boolean isConstructor) {
        BytecodeCompiler methodCompiler = new BytecodeCompiler();
        methodCompiler.nonDeletableGlobalBindings.addAll(this.nonDeletableGlobalBindings);
        methodCompiler.privateSymbols = privateSymbols;  // Make private symbols available in method

        FunctionExpression funcExpr = method.value();

        // Enter function scope and add parameters as locals
        methodCompiler.enterScope();
        methodCompiler.inGlobalScope = false;
        methodCompiler.isInAsyncFunction = funcExpr.isAsync();

        for (Identifier param : funcExpr.params()) {
            methodCompiler.currentScope().declareLocal(param.name());
        }

        // Emit default parameter initialization following QuickJS pattern
        if (funcExpr.defaults() != null) {
            emitDefaultParameterInit(methodCompiler, funcExpr.defaults());
        }

        // If this is a generator method, emit INITIAL_YIELD at the start
        if (funcExpr.isGenerator()) {
            methodCompiler.emitter.emitOpcode(Opcode.INITIAL_YIELD);
        }

        // For constructors, initialize private methods then fields before user code runs.
        if (isConstructor) {
            if (!privateInstanceMethodFunctions.isEmpty()) {
                methodCompiler.compilePrivateMethodInitialization(privateInstanceMethodFunctions, privateSymbols);
            }
            if (!instanceFields.isEmpty()) {
                methodCompiler.compileFieldInitialization(instanceFields, privateSymbols, computedFieldSymbols);
            }
        }

        // Compile method body statements
        for (Statement stmt : funcExpr.body().body()) {
            methodCompiler.compileStatement(stmt);
        }

        // If body doesn't end with return, add implicit return undefined
        List<Statement> bodyStatements = funcExpr.body().body();
        if (bodyStatements.isEmpty() || !(bodyStatements.get(bodyStatements.size() - 1) instanceof ReturnStatement)) {
            methodCompiler.emitter.emitOpcode(Opcode.UNDEFINED);
            int returnValueIndex = methodCompiler.currentScope().declareLocal("$method_return_" + methodCompiler.emitter.currentOffset());
            methodCompiler.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, returnValueIndex);
            methodCompiler.emitCurrentScopeUsingDisposal();
            methodCompiler.emitter.emitOpcodeU16(Opcode.GET_LOCAL, returnValueIndex);
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
        int definedArgCount = computeDefinedArgCount(funcExpr.params(), funcExpr.defaults(), funcExpr.restParameter() != null);
        return new JSBytecodeFunction(
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
    }

    private void compileNewExpression(NewExpression newExpr) {
        boolean hasSpread = newExpr.arguments().stream()
                .anyMatch(arg -> arg instanceof SpreadElement);

        // Push constructor
        compileExpression(newExpr.callee());

        if (hasSpread) {
            // QuickJS `OP_apply` constructor path: thisArg/newTarget, function, argsArray.
            emitter.emitOpcode(Opcode.DUP);
            emitArgumentsArrayWithSpread(newExpr.arguments());
            emitter.emitOpcodeU16(Opcode.APPLY, 1);
            return;
        }

        for (Expression arg : newExpr.arguments()) {
            compileExpression(arg);
        }
        emitter.emitOpcodeU16(Opcode.CALL_CONSTRUCTOR, newExpr.arguments().size());
    }

    private void compileObjectExpression(ObjectExpression objExpr) {
        emitter.emitOpcode(Opcode.OBJECT_NEW);

        for (ObjectExpression.Property prop : objExpr.properties()) {
            String kind = prop.kind();

            if ("get".equals(kind) || "set".equals(kind)) {
                // Getter/setter property: use DEFINE_METHOD_COMPUTED
                // Stack: obj -> obj key method -> obj
                // Push key
                if (prop.computed()) {
                    compileExpression(prop.key());
                } else if (prop.key() instanceof Identifier id) {
                    emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(id.name()));
                } else {
                    compileExpression(prop.key());
                }

                // Compile the getter/setter function
                compileFunctionExpression((FunctionExpression) prop.value());

                // DEFINE_METHOD_COMPUTED with flags: kind (1=get, 2=set) | enumerable (4)
                int methodKind = "get".equals(kind) ? 1 : 2;
                int flags = methodKind | 4; // enumerable = true for object literal properties
                emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, flags);
            } else {
                // Regular property: key: value
                // Push key
                if (prop.key() instanceof Identifier id && !prop.computed()) {
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
    }

    private void compilePatternAssignment(Pattern pattern) {
        if (pattern instanceof Identifier id) {
            // Simple identifier: value is on stack, just assign it
            String varName = id.name();
            if (inGlobalScope) {
                emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
            } else if (varInGlobalProgram) {
                // var declaration in global program inside a block (for, try, if, etc.).
                // var is global-scoped, so use PUT_VAR — UNLESS the name is already
                // a local (e.g., catch parameter per ES B.3.5), in which case use PUT_LOCAL.
                Integer existingLocal = findLocalInScopes(varName);
                if (existingLocal != null) {
                    emitter.emitOpcodeU16(Opcode.PUT_LOCAL, existingLocal);
                } else {
                    emitter.emitOpcodeAtom(Opcode.PUT_VAR, varName);
                }
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

            // Check if there's a rest element
            boolean hasRest = false;
            int restIndex = -1;
            for (int i = 0; i < arrPattern.elements().size(); i++) {
                if (arrPattern.elements().get(i) instanceof RestElement) {
                    hasRest = true;
                    restIndex = i;
                    break;
                }
            }

            if (hasRest) {
                // Use iterator-based approach for rest elements (following QuickJS js_emit_spread_code)
                // Stack: [iterable]

                // Start iteration: iterable -> iter next catch_offset
                emitter.emitOpcode(Opcode.FOR_OF_START);

                // Process elements before rest
                for (int i = 0; i < restIndex; i++) {
                    Pattern element = arrPattern.elements().get(i);
                    if (element != null) {
                        // Get next value: iter next -> iter next catch_offset value done
                        emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 0);
                        // Stack: iter next catch_offset value done
                        // Drop done flag
                        emitter.emitOpcode(Opcode.DROP);
                        // Stack: iter next catch_offset value
                        // Assign value to pattern
                        compilePatternAssignment(element);
                        // Stack: iter next catch_offset (after assignment drops the value)
                    } else {
                        // Skip element
                        emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 0);
                        // Stack: iter next catch_offset value done
                        emitter.emitOpcode(Opcode.DROP);  // Drop done
                        emitter.emitOpcode(Opcode.DROP);  // Drop value
                        // Stack: iter next catch_offset
                    }
                }

                // Now handle the rest element
                // Following QuickJS js_emit_spread_code at line 25663
                // Stack: iter next catch_offset -> iter next catch_offset array

                // Create empty array with 0 elements
                emitter.emitOpcodeU16(Opcode.ARRAY_FROM, 0);
                // Push initial index 0
                emitter.emitOpcode(Opcode.PUSH_I32);
                emitter.emitI32(0);

                // Loop to collect remaining elements
                int labelRestNext = emitter.currentOffset();

                // Get next value: iter next catch_offset array idx -> iter next catch_offset array idx value done
                emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 2);  // depth = 2 (array and idx)

                // Check if done
                int jumpRestDone = emitter.emitJump(Opcode.IF_TRUE);

                // Not done: array idx value -> array idx
                emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
                // Increment index
                emitter.emitOpcode(Opcode.INC);
                // Continue loop - jump back to labelRestNext
                emitter.emitOpcode(Opcode.GOTO);
                int backJumpPos = emitter.currentOffset();
                emitter.emitU32(labelRestNext - (backJumpPos + 4));

                // Done collecting - patch the IF_TRUE jump
                emitter.patchJump(jumpRestDone, emitter.currentOffset());
                // Stack: iter next catch_offset array idx undef
                // Drop undef and idx
                emitter.emitOpcode(Opcode.DROP);
                emitter.emitOpcode(Opcode.DROP);
                // Stack: iter next catch_offset array

                // Assign array to rest pattern
                RestElement restElement = (RestElement) arrPattern.elements().get(restIndex);
                compilePatternAssignment(restElement.argument());

                // Clean up iterator state: drop catch_offset, next, iter
                emitter.emitOpcode(Opcode.DROP);
                emitter.emitOpcode(Opcode.DROP);
                emitter.emitOpcode(Opcode.DROP);
            } else {
                // Simple indexed access (no rest element)
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
        } else if (pattern instanceof RestElement) {
            // RestElement should only appear inside ArrayPattern, shouldn't reach here
            throw new RuntimeException("RestElement can only appear inside ArrayPattern");
        }
    }

    private void compilePrivateInExpression(PrivateIdentifier privateIdentifier, Expression right) {
        compileExpression(right);

        JSSymbol symbol = privateSymbols != null ? privateSymbols.get(privateIdentifier.name()) : null;
        if (symbol == null) {
            throw new CompilerException("undefined private field '#" + privateIdentifier.name() + "'");
        }

        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
        emitter.emitOpcode(Opcode.PRIVATE_IN);
    }

    private LinkedHashMap<String, JSBytecodeFunction> compilePrivateMethodFunctions(
            List<ClassDeclaration.MethodDefinition> privateMethods,
            Map<String, JSSymbol> privateSymbols,
            IdentityHashMap<ClassDeclaration.PropertyDefinition, JSSymbol> computedFieldSymbols) {
        LinkedHashMap<String, JSBytecodeFunction> privateMethodFunctions = new LinkedHashMap<>();
        for (ClassDeclaration.MethodDefinition method : privateMethods) {
            String methodName = getMethodName(method);
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

    private void compilePrivateMethodInitialization(
            Map<String, JSBytecodeFunction> privateMethodFunctions,
            Map<String, JSSymbol> privateSymbols) {
        for (Map.Entry<String, JSBytecodeFunction> entry : privateMethodFunctions.entrySet()) {
            JSSymbol symbol = privateSymbols.get(entry.getKey());
            if (symbol == null) {
                throw new CompilerException("Private method symbol not found: #" + entry.getKey());
            }
            emitter.emitOpcode(Opcode.PUSH_THIS);
            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, entry.getValue());
            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
            emitter.emitOpcode(Opcode.SWAP);
            emitter.emitOpcode(Opcode.DEFINE_PRIVATE_FIELD);
            emitter.emitOpcode(Opcode.DROP);
        }
    }

    private void compileProgram(Program program) {
        inGlobalScope = true;
        isGlobalProgram = true;
        strictMode = program.strict();  // Set strict mode from program directive
        enterScope();
        registerGlobalProgramBindings(program.body());

        List<Statement> body = program.body();

        // Phase 1: Hoist top-level function declarations (ES spec requires function
        // declarations to be initialized before any code executes).
        Set<String> hoistedFunctionNames = new HashSet<>();
        Set<String> varNames = new HashSet<>();
        for (Statement stmt : body) {
            if (stmt instanceof FunctionDeclaration funcDecl) {
                if (funcDecl.id() != null) {
                    hoistedFunctionNames.add(funcDecl.id().name());
                }
                compileFunctionDeclaration(funcDecl);
            } else {
                // Collect var names from all statements, including nested ones.
                // var declarations are function/global-scoped and must be hoisted
                // regardless of block nesting (for, try, if, etc.).
                collectVarNamesFromStatement(stmt, varNames);
            }
        }

        // Phase 1.25: Var hoisting — create undefined bindings for var names not
        // already covered by hoisted function declarations.
        // Per ES2024 CreateGlobalVarBinding, only create binding if it doesn't already exist.
        for (String varName : varNames) {
            if (!hoistedFunctionNames.contains(varName)) {
                emitConditionalVarInit(varName);
            }
        }

        Set<String> declaredFuncVarNames = new HashSet<>();
        declaredFuncVarNames.addAll(hoistedFunctionNames);
        declaredFuncVarNames.addAll(varNames);

        // Phase 1.5: Annex B.3.3.3 - create var bindings for function declarations
        // nested inside blocks, if-statements, catch clauses, etc.
        scanAnnexBFunctions(body, declaredFuncVarNames);

        // Find the effective last statement index (last non-FunctionDeclaration),
        // since function declarations don't contribute a completion value.
        int effectiveLastIndex = -1;
        for (int i = body.size() - 1; i >= 0; i--) {
            if (!(body.get(i) instanceof FunctionDeclaration)) {
                effectiveLastIndex = i;
                break;
            }
        }

        // Phase 2: Compile all non-FunctionDeclaration statements in source order.
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
                // Try statements can produce values
                lastProducesValue = true;
            }

            compileStatement(stmt, isLast);
        }

        // If last statement didn't produce a value, push undefined
        if (!lastProducesValue) {
            emitter.emitOpcode(Opcode.UNDEFINED);
        }

        int programResultLocalIndex = currentScope().declareLocal("$program_result_" + emitter.currentOffset());
        emitter.emitOpcodeU16(Opcode.PUT_LOCAL, programResultLocalIndex);
        emitCurrentScopeUsingDisposal();
        emitter.emitOpcodeU16(Opcode.GET_LOCAL, programResultLocalIndex);

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

        int returnValueIndex = currentScope().declareLocal("$return_value_" + emitter.currentOffset());
        emitter.emitOpcodeU16(Opcode.PUT_LOCAL, returnValueIndex);

        emitUsingDisposalsForScopeDepthGreaterThan(0);

        if (hasActiveIteratorLoops()) {
            emitAbruptCompletionIteratorClose();
        }

        emitter.emitOpcodeU16(Opcode.GET_LOCAL, returnValueIndex);
        // Emit RETURN_ASYNC for async functions, RETURN for sync functions
        emitter.emitOpcode(isInAsyncFunction ? Opcode.RETURN_ASYNC : Opcode.RETURN);
    }

    private void compileSequenceExpression(SequenceExpression seqExpr) {
        // Following QuickJS: evaluate each expression in order,
        // dropping all but the last one's value
        List<Expression> expressions = seqExpr.expressions();

        for (int i = 0; i < expressions.size(); i++) {
            compileExpression(expressions.get(i));

            // Drop the value of all expressions except the last one
            if (i < expressions.size() - 1) {
                emitter.emitOpcode(Opcode.DROP);
            }
        }
        // The last expression's value remains on the stack
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
            // Try statements produce a value on the stack (the try/catch result).
            // Drop it when not the last statement in a program.
            if (!isLastInProgram) {
                emitter.emitOpcode(Opcode.DROP);
            }
        } else if (stmt instanceof SwitchStatement switchStmt) {
            compileSwitchStatement(switchStmt);
        } else if (stmt instanceof VariableDeclaration varDecl) {
            compileVariableDeclaration(varDecl);
        } else if (stmt instanceof FunctionDeclaration funcDecl) {
            compileFunctionDeclaration(funcDecl);
        } else if (stmt instanceof ClassDeclaration classDecl) {
            compileClassDeclaration(classDecl);
        } else if (stmt instanceof LabeledStatement labeledStmt) {
            compileLabeledStatement(labeledStmt);
        }
    }

    /**
     * Compile a static block as a function.
     * Static blocks are executed immediately after class definition with the class constructor as 'this'.
     */
    private JSBytecodeFunction compileStaticBlock(
            ClassDeclaration.StaticBlock staticBlock,
            String className,
            Map<String, JSSymbol> privateSymbols) {
        BytecodeCompiler blockCompiler = new BytecodeCompiler();
        blockCompiler.nonDeletableGlobalBindings.addAll(this.nonDeletableGlobalBindings);
        blockCompiler.privateSymbols = privateSymbols;

        blockCompiler.enterScope();
        blockCompiler.inGlobalScope = false;

        // Compile all statements in the static block
        for (Statement stmt : staticBlock.body()) {
            blockCompiler.compileStatement(stmt);
        }

        // Static blocks always return undefined
        blockCompiler.emitter.emitOpcode(Opcode.UNDEFINED);
        int returnValueIndex = blockCompiler.currentScope().declareLocal("$static_block_return_" + blockCompiler.emitter.currentOffset());
        blockCompiler.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, returnValueIndex);
        blockCompiler.emitCurrentScopeUsingDisposal();
        blockCompiler.emitter.emitOpcodeU16(Opcode.GET_LOCAL, returnValueIndex);
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
                false,                    // isArrow - static initializers are not arrows
                true,                     // strict mode
                "static { [initializer] }"
        );
    }

    // ==================== Expression Compilation ====================

    /**
     * Compile a static field initializer as a function and return it.
     * The function is called with class constructor as `this`.
     */
    private JSBytecodeFunction compileStaticFieldInitializer(
            ClassDeclaration.PropertyDefinition field,
            IdentityHashMap<ClassDeclaration.PropertyDefinition, JSSymbol> computedFieldSymbols,
            Map<String, JSSymbol> privateSymbols,
            String className) {
        BytecodeCompiler initializerCompiler = new BytecodeCompiler();
        initializerCompiler.nonDeletableGlobalBindings.addAll(this.nonDeletableGlobalBindings);
        initializerCompiler.privateSymbols = privateSymbols;

        initializerCompiler.enterScope();
        initializerCompiler.inGlobalScope = false;

        // Stack: this
        initializerCompiler.emitter.emitOpcode(Opcode.PUSH_THIS);

        if (field.isPrivate()) {
            if (!(field.key() instanceof PrivateIdentifier privateId)) {
                throw new CompilerException("Invalid static private field key");
            }

            JSSymbol symbol = privateSymbols.get(privateId.name());
            if (symbol == null) {
                throw new CompilerException("Static private field symbol not found: #" + privateId.name());
            }

            if (field.value() != null) {
                initializerCompiler.compileExpression(field.value());
            } else {
                initializerCompiler.emitter.emitOpcode(Opcode.UNDEFINED);
            }

            // Stack: this value symbol -> this symbol value
            initializerCompiler.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
            initializerCompiler.emitter.emitOpcode(Opcode.SWAP);
            initializerCompiler.emitter.emitOpcode(Opcode.DEFINE_PRIVATE_FIELD);
        } else {
            if (field.computed()) {
                JSSymbol computedFieldSymbol = computedFieldSymbols.get(field);
                if (computedFieldSymbol == null) {
                    throw new CompilerException("Computed static field key not found");
                }
                // Load precomputed key from constructor hidden storage:
                // this this hiddenSymbol -> this key
                initializerCompiler.emitter.emitOpcode(Opcode.PUSH_THIS);
                initializerCompiler.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, computedFieldSymbol);
                initializerCompiler.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else {
                initializerCompiler.emitNonComputedPublicFieldKey(field.key());
            }

            if (field.value() != null) {
                initializerCompiler.compileExpression(field.value());
            } else {
                initializerCompiler.emitter.emitOpcode(Opcode.UNDEFINED);
            }

            // Stack: this key value
            initializerCompiler.emitter.emitOpcode(Opcode.DEFINE_PROP);
        }
        // Stack: this
        initializerCompiler.emitter.emitOpcode(Opcode.DROP);
        initializerCompiler.emitter.emitOpcode(Opcode.UNDEFINED);
        initializerCompiler.emitter.emitOpcode(Opcode.RETURN);

        int localCount = initializerCompiler.currentScope().getLocalCount();
        initializerCompiler.exitScope();
        Bytecode initializerBytecode = initializerCompiler.emitter.build(localCount);

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

    private void compileSwitchStatement(SwitchStatement switchStmt) {
        // Compile discriminant
        compileExpression(switchStmt.discriminant());

        List<Integer> caseJumps = new ArrayList<>();
        List<Integer> caseBodyStarts = new ArrayList<>();

        // Emit comparisons for each case.
        // Following QuickJS pattern: when a case matches, drop the discriminant
        // before jumping to the case body. This ensures the stack depth is
        // consistent regardless of which case (or no case) matches.
        for (SwitchStatement.SwitchCase switchCase : switchStmt.cases()) {
            if (switchCase.test() != null) {
                // Duplicate discriminant for comparison
                emitter.emitOpcode(Opcode.DUP);
                compileExpression(switchCase.test());
                emitter.emitOpcode(Opcode.STRICT_EQ);

                // If no match, skip to next test
                int jumpToNextTest = emitter.emitJump(Opcode.IF_FALSE);
                // Match: drop discriminant and jump to case body
                emitter.emitOpcode(Opcode.DROP);
                int jumpToBody = emitter.emitJump(Opcode.GOTO);
                caseJumps.add(jumpToBody);
                // Patch IF_FALSE to continue with next test
                emitter.patchJump(jumpToNextTest, emitter.currentOffset());
            }
        }

        // No case matched: drop discriminant
        emitter.emitOpcode(Opcode.DROP);

        // Jump to default or end
        int jumpToDefault = emitter.emitJump(Opcode.GOTO);

        // Compile case bodies
        // The switch body always creates a block scope for lexical declarations (let/const).
        // Per QuickJS: push_scope is unconditional for switch statements.
        boolean savedGlobalScope = inGlobalScope;
        enterScope();
        inGlobalScope = false;

        LoopContext loop = createLoopContext(emitter.currentOffset(), scopeDepth, scopeDepth);
        loopStack.push(loop);

        int defaultBodyStart = -1;
        for (int i = 0; i < switchStmt.cases().size(); i++) {
            SwitchStatement.SwitchCase switchCase = switchStmt.cases().get(i);

            if (switchCase.test() != null) {
                int bodyStart = emitter.currentOffset();
                caseBodyStarts.add(bodyStart);
            } else {
                defaultBodyStart = emitter.currentOffset();
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
        if (defaultBodyStart >= 0) {
            emitter.patchJump(jumpToDefault, defaultBodyStart);
        } else {
            emitter.patchJump(jumpToDefault, switchEnd);
        }

        // Patch break statements
        for (int breakPos : loop.breakPositions) {
            emitter.patchJump(breakPos, switchEnd);
        }

        loopStack.pop();

        inGlobalScope = savedGlobalScope;
        emitCurrentScopeUsingDisposal();
        exitScope();
    }

    private void compileTaggedTemplateExpression(TaggedTemplateExpression taggedTemplate) {
        // Tagged template: tag`template`
        // The tag function receives:
        // 1. A template object (array-like) with cooked strings and a 'raw' property
        // 2. The values of the substitutions as additional arguments

        TemplateLiteral template = taggedTemplate.quasi();
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

        // QuickJS behavior: each call site uses a stable, frozen template object.
        // Build it once in the constant pool and pass it as the first argument.
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, createTaggedTemplateObject(template));
        // Stack: function, receiver, template_object

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
        String firstQuasi = quasis.get(0);
        if (firstQuasi == null) {
            throw new CompilerException("Invalid escape sequence in untagged template literal");
        }
        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(firstQuasi));

        // Add each expression and subsequent quasi using string concatenation (ADD)
        for (int i = 0; i < expressions.size(); i++) {
            // Compile the expression
            compileExpression(expressions.get(i));

            // Concatenate using ADD opcode (JavaScript + operator)
            emitter.emitOpcode(Opcode.ADD);

            // Add the next quasi if it exists
            if (i + 1 < quasis.size()) {
                String quasi = quasis.get(i + 1);
                if (quasi == null) {
                    throw new CompilerException("Invalid escape sequence in untagged template literal");
                }
                if (!quasi.isEmpty()) {
                    emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(quasi));
                    emitter.emitOpcode(Opcode.ADD);
                }
            }
        }
    }

    private void compileThrowStatement(ThrowStatement throwStmt) {
        compileExpression(throwStmt.argument());
        int throwValueIndex = currentScope().declareLocal("$throw_value_" + emitter.currentOffset());
        emitter.emitOpcodeU16(Opcode.PUT_LOCAL, throwValueIndex);

        emitUsingDisposalsForScopeDepthGreaterThan(0);

        if (hasActiveIteratorLoops()) {
            emitAbruptCompletionIteratorClose();
        }
        emitter.emitOpcodeU16(Opcode.GET_LOCAL, throwValueIndex);
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
        emitCurrentScopeUsingDisposal();
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

        // Remove the CatchOffset marker from the stack (normal path, no exception).
        // NIP_CATCH pops everything down to and including the CatchOffset marker,
        // then re-pushes the try result value.
        if (tryStmt.handler() != null) {
            emitter.emitOpcode(Opcode.NIP_CATCH);
        }

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

                // Declare all pattern variables and assign the exception value
                Pattern catchParam = handler.param();
                if (catchParam instanceof Identifier id) {
                    // Simple catch parameter: catch (e)
                    int localIndex = currentScope().declareLocal(id.name());
                    emitter.emitOpcodeU16(Opcode.PUT_LOCAL, localIndex);
                } else {
                    // Destructuring catch parameter: catch ({ f }) or catch ([a, b])
                    declarePatternVariables(catchParam);
                    compilePatternAssignment(catchParam);
                }

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
                emitCurrentScopeUsingDisposal();
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
            } else if (operand instanceof Identifier id) {
                // Match QuickJS scope_delete_var lowering:
                // - local/arg/closure/implicit arguments bindings => false
                // - unresolved/global binding => DELETE_VAR runtime check
                boolean isLocalBinding = findLocalInScopes(id.name()) != null
                        || resolveCapturedBindingIndex(id.name()) != null
                        || (JSArguments.NAME.equals(id.name()) && !inGlobalScope)
                        || nonDeletableGlobalBindings.contains(id.name());
                if (isLocalBinding) {
                    emitter.emitOpcode(Opcode.PUSH_FALSE);
                } else {
                    emitter.emitOpcodeAtom(Opcode.DELETE_VAR, id.name());
                }
            } else {
                // delete literal / non-reference expression => true
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
                Integer localIndex = findLocalInScopes(id.name());
                if (localIndex != null) {
                    emitter.emitOpcodeU16(isPrefix ? Opcode.SET_LOCAL : Opcode.PUT_LOCAL, localIndex);
                } else {
                    Integer capturedIndex = resolveCapturedBindingIndex(id.name());
                    if (capturedIndex != null) {
                        emitter.emitOpcodeU16(isPrefix ? Opcode.SET_VAR_REF : Opcode.PUT_VAR_REF, capturedIndex);
                    } else {
                        emitter.emitOpcodeAtom(isPrefix ? Opcode.SET_VAR : Opcode.PUT_VAR, id.name());
                    }
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
                        emitter.emitOpcode(Opcode.PLUS); // ToNumber conversion
                        emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSNumber(1));
                        emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB);
                        // Stack: [obj, prop, new_val] -> need [new_val, obj, prop]
                        emitter.emitOpcode(Opcode.ROT3R);
                        emitter.emitOpcode(Opcode.PUT_ARRAY_EL);
                    } else {
                        // Postfix: arr[i]++ - returns old value (must be ToNumber'd per ES spec)
                        emitter.emitOpcode(Opcode.DUP2); // obj prop obj prop
                        emitter.emitOpcode(Opcode.GET_ARRAY_EL); // obj prop old_val
                        emitter.emitOpcode(Opcode.PLUS); // obj prop old_numeric (ToNumber conversion)
                        emitter.emitOpcode(Opcode.DUP); // obj prop old_numeric old_numeric
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
                            emitter.emitOpcode(Opcode.PLUS); // ToNumber conversion
                            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSNumber(1));
                            emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB);
                            // Stack: [obj, new_val] -> need [new_val, obj] for PUT_FIELD
                            emitter.emitOpcode(Opcode.SWAP);
                            // PUT_FIELD pops obj, peeks new_val, leaves [new_val]
                            emitter.emitOpcodeAtom(Opcode.PUT_FIELD, propId.name());
                        } else {
                            // Postfix: obj.prop++ - returns old value (must be ToNumber'd per ES spec)
                            emitter.emitOpcode(Opcode.DUP); // obj obj
                            emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name()); // obj old_val
                            emitter.emitOpcode(Opcode.PLUS); // obj old_numeric (ToNumber conversion)
                            emitter.emitOpcode(Opcode.DUP); // obj old_numeric old_numeric
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
                            emitter.emitOpcode(Opcode.PLUS); // obj old_numeric (ToNumber conversion)
                            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSNumber(1));
                            emitter.emitOpcode(isInc ? Opcode.ADD : Opcode.SUB); // obj new_val
                            emitter.emitOpcode(Opcode.DUP); // obj new_val new_val
                            emitter.emitOpcode(Opcode.ROT3R); // new_val obj new_val
                            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol); // new_val obj new_val symbol
                            emitter.emitOpcode(Opcode.SWAP); // new_val obj symbol new_val
                            emitter.emitOpcode(Opcode.PUT_PRIVATE_FIELD); // new_val
                        } else {
                            // Postfix: obj.#field++ - returns old value (must be ToNumber'd per ES spec)
                            emitter.emitOpcode(Opcode.DUP); // obj obj
                            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                            emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD); // obj old_val
                            emitter.emitOpcode(Opcode.PLUS); // obj old_numeric (ToNumber conversion)
                            emitter.emitOpcode(Opcode.DUP); // obj old_numeric old_numeric
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

        if (unaryExpr.operator() == UnaryExpression.UnaryOperator.TYPEOF
                && unaryExpr.operand() instanceof Identifier id) {
            String name = id.name();
            if ("this".equals(name)) {
                emitter.emitOpcode(Opcode.PUSH_THIS);
            } else if (JSArguments.NAME.equals(name) && !inGlobalScope) {
                emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
                emitter.emitU8(0);
            } else {
                Integer localIndex = findLocalInScopes(name);
                if (localIndex != null) {
                    emitter.emitOpcodeU16(Opcode.GET_LOCAL, localIndex);
                } else {
                    Integer capturedIndex = resolveCapturedBindingIndex(name);
                    if (capturedIndex != null) {
                        emitter.emitOpcodeU16(Opcode.GET_VAR_REF, capturedIndex);
                    } else {
                        emitter.emitOpcodeAtom(Opcode.GET_VAR, "globalThis");
                        emitter.emitOpcodeAtom(Opcode.GET_FIELD, name);
                    }
                }
            }
            emitter.emitOpcode(Opcode.TYPEOF);
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
        boolean isUsingDeclaration = varDecl.kind() == VariableKind.USING || varDecl.kind() == VariableKind.AWAIT_USING;
        boolean isAwaitUsingDeclaration = varDecl.kind() == VariableKind.AWAIT_USING;
        // Track whether this is a var declaration in global program scope
        // so compilePatternAssignment can use PUT_VAR for global-scoped vars.
        boolean savedVarInGlobalProgram = varInGlobalProgram;
        if (isGlobalProgram && varDecl.kind() == VariableKind.VAR) {
            varInGlobalProgram = true;
        }
        for (VariableDeclaration.VariableDeclarator declarator : varDecl.declarations()) {
            if (inGlobalScope || varInGlobalProgram) {
                collectPatternBindingNames(declarator.id(), nonDeletableGlobalBindings);
            }
            if (isUsingDeclaration) {
                if (declarator.init() == null) {
                    throw new CompilerException(varDecl.kind() + " declaration requires an initializer");
                }

                compileExpression(declarator.init());
                int usingStackLocalIndex = ensureUsingStackLocal(isAwaitUsingDeclaration);
                emitMethodCallWithSingleArgOnLocalObject(usingStackLocalIndex, "use");
                compilePatternAssignment(declarator.id());
                continue;
            }

            // Compile initializer or push undefined
            if (declarator.init() != null) {
                compileExpression(declarator.init());
            } else {
                emitter.emitOpcode(Opcode.UNDEFINED);
            }
            // Assign to pattern (handles Identifier, ObjectPattern, ArrayPattern)
            compilePatternAssignment(declarator.id());
        }
        varInGlobalProgram = savedVarInGlobalProgram;
    }

    private void compileWhileStatement(WhileStatement whileStmt) {
        int loopStart = emitter.currentOffset();
        LoopContext loop = createLoopContext(loopStart, scopeDepth, scopeDepth);
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
            emitter.emitOpcode(isInAsyncFunction ? Opcode.ASYNC_YIELD_STAR : Opcode.YIELD_STAR);
        } else {
            // Regular yield
            emitter.emitOpcode(Opcode.YIELD);
        }
    }

    /**
     * Compute the defined_arg_count for Function.length per ES2024 spec.
     * Following QuickJS js_parse_function_decl2: length stops counting at the first
     * parameter with a default value or a rest parameter.
     */
    private int computeDefinedArgCount(List<Identifier> params, List<Expression> defaults, boolean hasRest) {
        if (defaults == null) {
            return params.size();
        }
        int count = 0;
        for (int i = 0; i < params.size(); i++) {
            if (i < defaults.size() && defaults.get(i) != null) {
                break; // Stop at first default parameter
            }
            count++;
        }
        return count;
    }

    /**
     * Create a default constructor for a class.
     */
    private JSBytecodeFunction createDefaultConstructor(
            String className,
            boolean hasSuper,
            List<ClassDeclaration.PropertyDefinition> instanceFields,
            Map<String, JSSymbol> privateSymbols,
            IdentityHashMap<ClassDeclaration.PropertyDefinition, JSSymbol> computedFieldSymbols,
            Map<String, JSBytecodeFunction> privateInstanceMethodFunctions) {
        BytecodeCompiler constructorCompiler = new BytecodeCompiler();
        constructorCompiler.nonDeletableGlobalBindings.addAll(this.nonDeletableGlobalBindings);
        constructorCompiler.privateSymbols = privateSymbols;  // Make private symbols available

        constructorCompiler.enterScope();
        constructorCompiler.inGlobalScope = false;

        // Initialize private methods and fields before constructor body
        if (!privateInstanceMethodFunctions.isEmpty()) {
            constructorCompiler.compilePrivateMethodInitialization(privateInstanceMethodFunctions, privateSymbols);
        }
        if (!instanceFields.isEmpty()) {
            constructorCompiler.compileFieldInitialization(instanceFields, privateSymbols, computedFieldSymbols);
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
                false,           // isArrow - constructors are not arrows
                true,            // strict mode
                "constructor() { [default] }"
        );
    }

    /**
     * Create a LoopContext, consuming any pending loop label.
     */
    private LoopContext createLoopContext(int startOffset, int breakScopeDepth, int continueScopeDepth) {
        String label = pendingLoopLabel;
        pendingLoopLabel = null;
        return new LoopContext(startOffset, breakScopeDepth, continueScopeDepth, label);
    }

    private Map<String, JSSymbol> createPrivateSymbols(List<ClassDeclaration.ClassElement> classElements) {
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

    private JSArray createTaggedTemplateObject(TemplateLiteral template) {
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
        rawArray.definePropertyReadonlyNonConfigurable("length", new JSNumber(segmentCount));
        templateObject.definePropertyReadonlyNonConfigurable("length", new JSNumber(segmentCount));
        templateObject.definePropertyReadonlyNonConfigurable("raw", rawArray);

        rawArray.freeze();
        templateObject.freeze();
        return templateObject;
    }

    private Scope currentScope() {
        if (scopes.isEmpty()) {
            throw new CompilerException("No scope available");
        }
        return scopes.peek();
    }

    /**
     * Declare all variables in a pattern (used for for-of loops with destructuring).
     * This recursively declares variables for Identifier, ArrayPattern, and ObjectPattern.
     */
    private void declarePatternVariables(Pattern pattern) {
        if (pattern instanceof Identifier id) {
            // Simple identifier: declare it as a local variable
            currentScope().declareLocal(id.name());
        } else if (pattern instanceof ArrayPattern arrPattern) {
            // Array destructuring: declare all element variables
            for (Pattern element : arrPattern.elements()) {
                if (element != null) {
                    if (element instanceof RestElement restElement) {
                        // Rest element: declare the argument pattern
                        declarePatternVariables(restElement.argument());
                    } else {
                        // Regular element: recursively declare
                        declarePatternVariables(element);
                    }
                }
            }
        } else if (pattern instanceof ObjectPattern objPattern) {
            // Object destructuring: declare all property variables
            for (ObjectPattern.Property prop : objPattern.properties()) {
                declarePatternVariables(prop.value());
            }
        } else if (pattern instanceof RestElement restElement) {
            // Rest element at top level (shouldn't normally happen, but handle it)
            declarePatternVariables(restElement.argument());
        }
    }

    private void emitAbruptCompletionIteratorClose() {
        for (LoopContext loopContext : loopStack) {
            if (loopContext.hasIterator) {
                emitter.emitOpcode(Opcode.ITERATOR_CLOSE);
            }
        }
    }

    private void emitArgumentsArrayWithSpread(List<Expression> arguments) {
        emitter.emitOpcode(Opcode.ARRAY_NEW);

        boolean hasSpread = arguments.stream()
                .anyMatch(arg -> arg instanceof SpreadElement);

        if (!hasSpread) {
            for (Expression arg : arguments) {
                compileExpression(arg);
                emitter.emitOpcode(Opcode.PUSH_ARRAY);
            }
            return;
        }

        // QuickJS-style lowering keeps an explicit append index once spread appears.
        int idx = 0;
        boolean needsIndex = false;
        for (Expression arg : arguments) {
            if (arg instanceof SpreadElement spreadElement) {
                if (!needsIndex) {
                    emitter.emitOpcodeU32(Opcode.PUSH_I32, idx);
                    needsIndex = true;
                }
                compileExpression(spreadElement.argument());
                emitter.emitOpcode(Opcode.APPEND);
            } else if (needsIndex) {
                compileExpression(arg);
                emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
                emitter.emitOpcode(Opcode.INC);
            } else {
                compileExpression(arg);
                emitter.emitOpcode(Opcode.PUSH_ARRAY);
                idx++;
            }
        }
        if (needsIndex) {
            emitter.emitOpcode(Opcode.DROP);
        }
    }

    /**
     * Emit default parameter initialization bytecode following QuickJS pattern.
     * For each parameter with a default value, emits:
     * GET_ARG idx
     * DUP
     * UNDEFINED
     * STRICT_EQ
     * IF_FALSE label
     * DROP
     * <compile default expression>
     * DUP
     * PUT_ARG idx
     * label:
     * PUT_LOCAL idx  (store into the local variable slot)
     */

    private void emitCaptureBindingLoad(CaptureSource captureSource) {
        if (captureSource.type == CaptureSourceType.LOCAL) {
            emitter.emitOpcodeU16(Opcode.GET_LOCAL, captureSource.index);
        } else {
            emitter.emitOpcodeU16(Opcode.GET_VAR_REF, captureSource.index);
        }
    }

    private void emitCapturedValues(BytecodeCompiler nestedCompiler) {
        if (nestedCompiler.captureResolver.getCapturedBindingCount() == 0) {
            return;
        }
        for (CaptureBinding binding : nestedCompiler.captureResolver.getCapturedBindings()) {
            emitCaptureBindingLoad(binding.source);
        }
    }

    /**
     * Emit a conditional var initialization: only create the binding with undefined
     * if the property doesn't already exist on the global object.
     * Implements ES2024 CreateGlobalVarBinding semantics.
     * <p>
     * Bytecode sequence:
     * <pre>
     *   PUSH_ATOM_VALUE "name"   // push property name
     *   PUSH_THIS                // push global object
     *   IN                       // "name" in globalObject -> boolean
     *   IF_TRUE skip             // if exists, skip initialization
     *   UNDEFINED                // push undefined
     *   PUT_VAR "name"           // create the binding
     *   skip:
     * </pre>
     */
    private void emitConditionalVarInit(String name) {
        emitter.emitOpcodeAtom(Opcode.PUSH_ATOM_VALUE, name);
        emitter.emitOpcode(Opcode.PUSH_THIS);
        emitter.emitOpcode(Opcode.IN);
        int skipJump = emitter.emitJump(Opcode.IF_TRUE);
        emitter.emitOpcode(Opcode.UNDEFINED);
        emitter.emitOpcodeAtom(Opcode.PUT_VAR, name);
        emitter.patchJump(skipJump, emitter.currentOffset());
    }

    private void emitCurrentScopeUsingDisposal() {
        emitScopeUsingDisposal(currentScope());
    }

    private void emitDefaultParameterInit(BytecodeCompiler functionCompiler, List<Expression> defaults) {
        for (int i = 0; i < defaults.size(); i++) {
            Expression defaultExpr = defaults.get(i);
            if (defaultExpr != null) {
                // GET_ARG idx - push the argument value onto the stack
                functionCompiler.emitter.emitOpcodeU16(Opcode.GET_ARG, i);
                // DUP - duplicate for the comparison
                functionCompiler.emitter.emitOpcode(Opcode.DUP);
                // UNDEFINED - push undefined for comparison
                functionCompiler.emitter.emitOpcode(Opcode.UNDEFINED);
                // STRICT_EQ - check if arg === undefined
                functionCompiler.emitter.emitOpcode(Opcode.STRICT_EQ);
                // IF_FALSE label - if arg !== undefined, skip default
                int skipLabel = functionCompiler.emitter.emitJump(Opcode.IF_FALSE);
                // DROP - drop the duplicated arg value (it was undefined)
                functionCompiler.emitter.emitOpcode(Opcode.DROP);
                // Compile the default expression
                functionCompiler.compileExpression(defaultExpr);
                // DUP - duplicate for PUT_ARG
                functionCompiler.emitter.emitOpcode(Opcode.DUP);
                // PUT_ARG idx - store back into the argument slot
                functionCompiler.emitter.emitOpcodeU16(Opcode.PUT_ARG, i);
                // label: - skip target (value is on stack, either original arg or default)
                functionCompiler.emitter.patchJump(skipLabel, functionCompiler.emitter.currentOffset());
                // PUT_LOCAL idx - store into the local variable slot
                functionCompiler.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, i);
            }
        }
    }

    /**
     * Emit ITERATOR_CLOSE for any for-of loops between the current position and the target
     * loop context. This is needed when labeled break/continue crosses for-of loop boundaries,
     * to properly close inner iterators whose cleanup code would otherwise be skipped.
     * Following QuickJS close_scopes pattern for iterator cleanup.
     */
    private void emitIteratorCloseForLoopsUntil(LoopContext target) {
        for (LoopContext ctx : loopStack) {
            if (ctx == target) {
                break;
            }
            if (ctx.hasIterator) {
                emitter.emitOpcode(Opcode.ITERATOR_CLOSE);
            }
        }
    }

    private void emitMethodCallOnLocalObject(int localIndex, String methodName, int argCount) {
        emitter.emitOpcodeU16(Opcode.GET_LOCAL, localIndex);
        emitter.emitOpcode(Opcode.DUP);
        emitter.emitOpcodeAtom(Opcode.GET_FIELD, methodName);
        emitter.emitOpcode(Opcode.SWAP);
        emitter.emitOpcodeU16(Opcode.CALL, argCount);
    }

    private void emitMethodCallWithSingleArgOnLocalObject(int localIndex, String methodName) {
        int argLocalIndex = currentScope().declareLocal("$using_arg_" + emitter.currentOffset());
        emitter.emitOpcodeU16(Opcode.PUT_LOCAL, argLocalIndex);

        emitter.emitOpcodeU16(Opcode.GET_LOCAL, localIndex);
        emitter.emitOpcode(Opcode.DUP);
        emitter.emitOpcodeAtom(Opcode.GET_FIELD, methodName);
        emitter.emitOpcode(Opcode.SWAP);
        emitter.emitOpcodeU16(Opcode.GET_LOCAL, argLocalIndex);
        emitter.emitOpcodeU16(Opcode.CALL, 1);
    }

    private void emitNonComputedPublicFieldKey(Expression key) {
        if (key instanceof Identifier id) {
            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(id.name()));
            return;
        }
        if (key instanceof Literal literal) {
            Object value = literal.value();
            if (value == null) {
                emitter.emitOpcode(Opcode.NULL);
            } else if (value instanceof Boolean bool) {
                emitter.emitOpcode(bool ? Opcode.PUSH_TRUE : Opcode.PUSH_FALSE);
            } else if (value instanceof BigInteger bigInt) {
                emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSBigInt(bigInt));
            } else if (value instanceof Number num) {
                emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSNumber(num.doubleValue()));
            } else if (value instanceof String str) {
                emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(str));
            } else {
                throw new CompilerException("Unsupported field key literal type: " + value.getClass());
            }
            return;
        }
        throw new CompilerException("Invalid non-computed field key");
    }

    private void emitScopeUsingDisposal(Scope scope) {
        Integer usingStackLocalIndex = scope.getUsingStackLocalIndex();
        if (usingStackLocalIndex == null) {
            return;
        }

        if (scope.isUsingStackAsync()) {
            emitMethodCallOnLocalObject(usingStackLocalIndex, "disposeAsync", 0);
            emitter.emitOpcode(Opcode.AWAIT);
            emitter.emitOpcode(Opcode.DROP);
        } else {
            emitMethodCallOnLocalObject(usingStackLocalIndex, "dispose", 0);
            emitter.emitOpcode(Opcode.DROP);
        }
    }

    private void emitUsingDisposalsForScopeDepthGreaterThan(int targetScopeDepth) {
        for (Scope scope : scopes) {
            if (scope.getScopeDepth() > targetScopeDepth) {
                emitScopeUsingDisposal(scope);
            }
        }
    }

    private int ensureUsingStackLocal(boolean asyncUsingDeclaration) {
        Scope scope = currentScope();
        Integer existingLocalIndex = scope.getUsingStackLocalIndex();
        if (existingLocalIndex != null) {
            if (asyncUsingDeclaration && !scope.isUsingStackAsync()) {
                throw new CompilerException("Cannot mix await using with sync using stack in the same scope");
            }
            return existingLocalIndex;
        }

        boolean useAsyncStack = asyncUsingDeclaration || isInAsyncFunction;
        String constructorName = useAsyncStack ? JSAsyncDisposableStack.NAME : JSDisposableStack.NAME;
        int stackLocalIndex = scope.declareLocal("$using_stack_" + scope.getScopeDepth() + "_" + emitter.currentOffset());

        emitter.emitOpcodeAtom(Opcode.GET_VAR, constructorName);
        emitter.emitOpcodeU16(Opcode.CALL_CONSTRUCTOR, 0);
        emitter.emitOpcodeU16(Opcode.PUT_LOCAL, stackLocalIndex);

        scope.setUsingStackLocal(stackLocalIndex, useAsyncStack);
        return stackLocalIndex;
    }

    private void enterScope() {
        scopeDepth++;
        int baseIndex = scopes.isEmpty() ? 0 : currentScope().getLocalCount();
        scopes.push(new Scope(baseIndex, scopeDepth));
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
        scopeDepth--;
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

    // ==================== Scope Management ====================

    private Integer findCapturedBindingIndex(String name) {
        return captureResolver.findCapturedBindingIndex(name);
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

    private boolean hasActiveIteratorLoops() {
        for (LoopContext loopContext : loopStack) {
            if (loopContext.hasIterator) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a block statement has a "use strict" directive as its first statement.
     * Following ECMAScript specification section 10.2.1 (Directive Prologues).
     */
    private boolean hasUseStrictDirective(BlockStatement block) {
        if (block == null || block.body().isEmpty()) {
            return false;
        }

        // Check the first statement
        Statement firstStmt = block.body().get(0);
        if (!(firstStmt instanceof ExpressionStatement exprStmt)) {
            return false;
        }

        // Check if it's a string literal expression
        if (!(exprStmt.expression() instanceof Literal literal)) {
            return false;
        }

        // Check if the literal value is "use strict"
        Object value = literal.value();
        return "use strict".equals(value);
    }

    private void installPrivateStaticMethods(
            Map<String, JSBytecodeFunction> privateStaticMethodFunctions,
            Map<String, JSSymbol> privateSymbols) {
        for (Map.Entry<String, JSBytecodeFunction> entry : privateStaticMethodFunctions.entrySet()) {
            JSSymbol symbol = privateSymbols.get(entry.getKey());
            if (symbol == null) {
                throw new CompilerException("Private static method symbol not found: #" + entry.getKey());
            }

            // Stack before: constructor proto
            emitter.emitOpcode(Opcode.SWAP); // proto constructor
            emitter.emitOpcode(Opcode.DUP);  // proto constructor constructor
            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, entry.getValue()); // proto constructor constructor method
            emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol); // proto constructor constructor method symbol
            emitter.emitOpcode(Opcode.SWAP); // proto constructor constructor symbol method
            emitter.emitOpcode(Opcode.DEFINE_PRIVATE_FIELD); // proto constructor constructor
            emitter.emitOpcode(Opcode.DROP); // proto constructor
            emitter.emitOpcode(Opcode.SWAP); // constructor proto
        }
    }

    private void registerGlobalProgramBindings(List<Statement> body) {
        for (Statement stmt : body) {
            if (stmt instanceof VariableDeclaration varDecl) {
                for (VariableDeclaration.VariableDeclarator declarator : varDecl.declarations()) {
                    collectPatternBindingNames(declarator.id(), nonDeletableGlobalBindings);
                }
            } else if (stmt instanceof FunctionDeclaration funcDecl) {
                if (funcDecl.id() != null) {
                    nonDeletableGlobalBindings.add(funcDecl.id().name());
                }
            } else if (stmt instanceof ClassDeclaration classDecl) {
                if (classDecl.id() != null) {
                    nonDeletableGlobalBindings.add(classDecl.id().name());
                }
            }
        }
    }

    private void registerPrivateName(Map<String, String> privateNameKinds, String privateName, String kind) {
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
        throw new CompilerException("private class field is already defined");
    }

    private Integer resolveCapturedBindingIndex(String name) {
        return captureResolver.resolveCapturedBindingIndex(name);
    }

    private void scanAnnexBBlock(List<Statement> body, Set<String> parentLexicals, Set<String> result) {
        Set<String> blockLexicals = new HashSet<>(parentLexicals);
        collectLexicalBindings(body, blockLexicals);
        for (Statement s : body) {
            if (s instanceof FunctionDeclaration fd && fd.id() != null) {
                if (!blockLexicals.contains(fd.id().name())) {
                    result.add(fd.id().name());
                }
            }
            scanAnnexBStatement(s, blockLexicals, result);
        }
    }

    /**
     * Scan the program body for Annex B.3.3.3 eligible function declarations.
     * These are function declarations nested inside blocks, if-statements, catch clauses,
     * switch cases, etc. (not top-level in the program body).
     * <p>
     * For each eligible name not already in declaredFuncVarNames, a var binding
     * initialized to undefined is created, and the name is recorded for the runtime
     * hook (copying the block-scoped value to the var-scoped value when evaluated).
     */
    private void scanAnnexBFunctions(List<Statement> programBody, Set<String> declaredFuncVarNames) {
        if (strictMode) {
            return; // Annex B does not apply in strict mode
        }
        // Collect top-level lexical bindings (let/const) from the program body.
        // Per B.3.3.3 step ii, if replacing the function declaration with "var F"
        // would produce an early error (conflict with let/const), the extension is skipped.
        Set<String> topLevelLexicals = new HashSet<>();
        collectLexicalBindings(programBody, topLevelLexicals);

        Set<String> candidates = new HashSet<>();
        for (Statement stmt : programBody) {
            // Only recurse into compound statements; top-level FunctionDeclarations
            // are regular hoisting, not Annex B.
            scanAnnexBStatement(stmt, topLevelLexicals, candidates);
        }
        for (String name : candidates) {
            annexBFunctionNames.add(name);
            if (!declaredFuncVarNames.contains(name)) {
                // Create initial var binding only if the property doesn't already exist
                // on the global object (ES2024 CreateGlobalVarBinding semantics).
                emitConditionalVarInit(name);
            }
        }
    }

    /**
     * Recursively scan a statement for Annex B eligible function declarations.
     * A function declaration is Annex B eligible if it appears inside a block, if-statement,
     * catch clause, or switch case (not at the top level of the program).
     * The early error check prevents hoisting when a let/const with the same name
     * exists in the same block scope.
     */
    private void scanAnnexBStatement(Statement stmt, Set<String> lexicalBindings, Set<String> result) {
        if (stmt instanceof BlockStatement block) {
            scanAnnexBBlock(block.body(), lexicalBindings, result);
        } else if (stmt instanceof IfStatement ifStmt) {
            if (ifStmt.consequent() instanceof FunctionDeclaration fd && fd.id() != null) {
                if (!lexicalBindings.contains(fd.id().name())) {
                    result.add(fd.id().name());
                }
            } else {
                scanAnnexBStatement(ifStmt.consequent(), lexicalBindings, result);
            }
            if (ifStmt.alternate() != null) {
                if (ifStmt.alternate() instanceof FunctionDeclaration fd && fd.id() != null) {
                    if (!lexicalBindings.contains(fd.id().name())) {
                        result.add(fd.id().name());
                    }
                } else {
                    scanAnnexBStatement(ifStmt.alternate(), lexicalBindings, result);
                }
            }
        } else if (stmt instanceof TryStatement tryStmt) {
            scanAnnexBBlock(tryStmt.block().body(), lexicalBindings, result);
            if (tryStmt.handler() != null) {
                // Per B.3.5, simple catch parameter (catch(e)) does NOT block Annex B var hoisting.
                // But destructuring catch parameter (catch({ f })) creates let-like bindings
                // that DO block hoisting (following QuickJS: destructuring uses TOK_LET).
                Set<String> catchLexicals = new HashSet<>(lexicalBindings);
                Pattern catchParam = tryStmt.handler().param();
                if (catchParam != null && !(catchParam instanceof Identifier)) {
                    // Destructuring pattern: collect binding names as lexical blockers
                    collectPatternBindingNames(catchParam, catchLexicals);
                }
                scanAnnexBBlock(tryStmt.handler().body().body(), catchLexicals, result);
            }
            if (tryStmt.finalizer() != null) {
                scanAnnexBBlock(tryStmt.finalizer().body(), lexicalBindings, result);
            }
        } else if (stmt instanceof SwitchStatement switchStmt) {
            // Collect lexical bindings across all cases (switch shares one scope)
            Set<String> switchLexicals = new HashSet<>(lexicalBindings);
            for (SwitchStatement.SwitchCase sc : switchStmt.cases()) {
                collectLexicalBindings(sc.consequent(), switchLexicals);
            }
            for (SwitchStatement.SwitchCase sc : switchStmt.cases()) {
                for (Statement s : sc.consequent()) {
                    if (s instanceof FunctionDeclaration fd && fd.id() != null) {
                        if (!switchLexicals.contains(fd.id().name())) {
                            result.add(fd.id().name());
                        }
                    }
                    scanAnnexBStatement(s, switchLexicals, result);
                }
            }
        } else if (stmt instanceof ForStatement forStmt) {
            if (forStmt.body() != null) {
                // Collect lexical bindings from the for-loop's init clause (e.g. "let f" in "for (let f; ; )")
                // Per B.3.3.3 step ii, if replacing the function declaration with "var F" would produce
                // an early error (conflict with let/const), the Annex B extension is skipped.
                Set<String> forLexicals = new HashSet<>(lexicalBindings);
                if (forStmt.init() instanceof VariableDeclaration vd && vd.kind() != VariableKind.VAR) {
                    for (VariableDeclaration.VariableDeclarator d : vd.declarations()) {
                        collectPatternBindingNames(d.id(), forLexicals);
                    }
                }
                scanAnnexBStatement(forStmt.body(), forLexicals, result);
            }
        } else if (stmt instanceof WhileStatement whileStmt) {
            scanAnnexBStatement(whileStmt.body(), lexicalBindings, result);
        } else if (stmt instanceof ForInStatement forInStmt) {
            Set<String> forInLexicals = new HashSet<>(lexicalBindings);
            if (forInStmt.left() instanceof VariableDeclaration varDecl && varDecl.kind() != VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator d : varDecl.declarations()) {
                    collectPatternBindingNames(d.id(), forInLexicals);
                }
            }
            scanAnnexBStatement(forInStmt.body(), forInLexicals, result);
        } else if (stmt instanceof ForOfStatement forOfStmt) {
            Set<String> forOfLexicals = new HashSet<>(lexicalBindings);
            if (forOfStmt.left() instanceof VariableDeclaration varDecl && varDecl.kind() != VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator d : varDecl.declarations()) {
                    collectPatternBindingNames(d.id(), forOfLexicals);
                }
            }
            scanAnnexBStatement(forOfStmt.body(), forOfLexicals, result);
        }
    }

    /**
     * Set the original source code (used for extracting function source in toString()).
     */
    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    private enum CaptureSourceType {
        LOCAL,
        VAR_REF
    }

    @FunctionalInterface
    private interface LocalLookup {
        Integer findLocal(String name);
    }

    private record CaptureBinding(int slot, CaptureSource source) {
    }

    private static class CaptureResolver {
        private final LinkedHashMap<String, CaptureBinding> capturedBindings;
        private final LocalLookup localLookup;
        private final CaptureResolver parentResolver;

        CaptureResolver(CaptureResolver parentResolver, LocalLookup localLookup) {
            this.parentResolver = parentResolver;
            this.localLookup = localLookup;
            this.capturedBindings = new LinkedHashMap<>();
        }

        Integer findCapturedBindingIndex(String name) {
            CaptureBinding binding = capturedBindings.get(name);
            return binding != null ? binding.slot : null;
        }

        int getCapturedBindingCount() {
            return capturedBindings.size();
        }

        Collection<CaptureBinding> getCapturedBindings() {
            return capturedBindings.values();
        }

        private int registerCapturedBinding(String name, CaptureSource source) {
            CaptureBinding existing = capturedBindings.get(name);
            if (existing != null) {
                return existing.slot;
            }
            int slot = capturedBindings.size();
            capturedBindings.put(name, new CaptureBinding(slot, source));
            return slot;
        }

        private CaptureSource resolveCaptureSourceForChild(String name) {
            Integer localIndex = localLookup.findLocal(name);
            if (localIndex != null) {
                return new CaptureSource(CaptureSourceType.LOCAL, localIndex);
            }

            Integer capturedIndex = findCapturedBindingIndex(name);
            if (capturedIndex != null) {
                return new CaptureSource(CaptureSourceType.VAR_REF, capturedIndex);
            }

            if (parentResolver == null) {
                return null;
            }

            CaptureSource parentSource = parentResolver.resolveCaptureSourceForChild(name);
            if (parentSource == null) {
                return null;
            }

            int capturedSlot = registerCapturedBinding(name, parentSource);
            return new CaptureSource(CaptureSourceType.VAR_REF, capturedSlot);
        }

        Integer resolveCapturedBindingIndex(String name) {
            Integer capturedIndex = findCapturedBindingIndex(name);
            if (capturedIndex != null || parentResolver == null) {
                return capturedIndex;
            }
            CaptureSource captureSource = parentResolver.resolveCaptureSourceForChild(name);
            if (captureSource == null) {
                return null;
            }
            return registerCapturedBinding(name, captureSource);
        }
    }

    private record CaptureSource(CaptureSourceType type, int index) {
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
     * Also used for labeled statements (where isRegularStmt is true).
     */
    private static class LoopContext {
        final List<Integer> breakPositions = new ArrayList<>();
        final int breakTargetScopeDepth;
        final List<Integer> continuePositions = new ArrayList<>();
        final int continueTargetScopeDepth;
        final String label;
        final int startOffset;
        boolean hasIterator;
        boolean isRegularStmt; // true for labeled non-loop statements (break allowed, continue not)

        LoopContext(int startOffset, int breakTargetScopeDepth, int continueTargetScopeDepth) {
            this(startOffset, breakTargetScopeDepth, continueTargetScopeDepth, null);
        }

        LoopContext(int startOffset, int breakTargetScopeDepth, int continueTargetScopeDepth, String label) {
            this.startOffset = startOffset;
            this.breakTargetScopeDepth = breakTargetScopeDepth;
            this.continueTargetScopeDepth = continueTargetScopeDepth;
            this.label = label;
        }
    }

    /**
     * Represents a lexical scope for tracking local variables.
     */
    private static class Scope {
        private final Map<String, Integer> locals = new HashMap<>();
        private final int scopeDepth;
        private int nextLocalIndex;
        private boolean usingStackAsync;
        private Integer usingStackLocalIndex;

        Scope() {
            this(0, 0);
        }

        Scope(int baseIndex, int scopeDepth) {
            this.nextLocalIndex = baseIndex;
            this.scopeDepth = scopeDepth;
            this.usingStackAsync = false;
            this.usingStackLocalIndex = null;
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

        int getScopeDepth() {
            return scopeDepth;
        }

        Integer getUsingStackLocalIndex() {
            return usingStackLocalIndex;
        }

        boolean isUsingStackAsync() {
            return usingStackAsync;
        }

        void setLocalCount(int count) {
            this.nextLocalIndex = count;
        }

        void setUsingStackLocal(int usingStackLocalIndex, boolean usingStackAsync) {
            this.usingStackLocalIndex = usingStackLocalIndex;
            this.usingStackAsync = usingStackAsync;
        }
    }
}
