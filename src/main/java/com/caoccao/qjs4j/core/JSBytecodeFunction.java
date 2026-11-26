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

import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;
import com.caoccao.qjs4j.vm.Bytecode;
import com.caoccao.qjs4j.vm.StackFrame;
import com.caoccao.qjs4j.vm.VarRef;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Represents a JavaScript function compiled to bytecode. Based on QuickJS JSFunctionBytecode structure.
 * <p>
 * Bytecode functions are created by: - Function declarations - Function expressions - Arrow functions - Method
 * definitions
 * <p>
 * They contain: - Compiled bytecode for execution - Closure variables (captured from outer scopes) - Prototype object
 * (for constructors) - Function metadata (name, length)
 */
public final class JSBytecodeFunction extends JSFunction {
    public static final String NAME = JSFunction.NAME;
    /**
     * The local slot a named function expression sets on entering its body, or -1.
     * <p>
     * See {@link #getParameterScopeFunctionName()} for what it is for.
     */
    private int bodyScopeEnteredLocalIndex = -1;
    private final Bytecode bytecode;
    private JSFunction capturedActiveFunction;
    private JSValue capturedArguments;
    private VarRef capturedDerivedThisRef;
    private JSValue capturedNewTarget;
    private JSValue capturedThisArg;
    private String[] capturedVarNames;
    private int[] captureSourceInfos;
    private boolean classConstructor;
    private IdentityHashMap<JSSymbol, JSSymbol> classPrivateSymbolRemap;
    private Set<JSSymbol> classPrivateSymbols;
    private final JSValue[] closureVars;
    private boolean derivedConstructor;
    private boolean displaysAsArgumentsObjectInToString;
    private StackFrame evalDynamicScopeFrame;
    private boolean evalDynamicScopeLookupEnabled;
    private boolean evalSuperCallAllowed;
    private boolean hasArgumentsParameterBinding;
    private boolean hasParameterExpressions;
    /**
     * The named-function-expression bindings this function captured from enclosing parameter environments. Never null.
     * <p>
     * See {@link #getInheritedParameterScopeFunctionNames()}.
     */
    private Set<String> inheritedParameterScopeFunctionNames = Set.of();
    private final boolean isArrow;
    private final boolean isAsync;
    private final boolean isConstructor;
    private final boolean isGenerator;
    private final int length;
    private final String name;
    private boolean newTargetAllowed;
    /**
     * The name of this named function expression when its body redeclares it, else null.
     * <p>
     * See {@link #getActiveParameterScopeFunctionName(StackFrame)}.
     */
    private String parameterScopeFunctionName;
    /**
     * Whether this is the function a script, module or eval body was compiled into, rather than a function the source
     * declared. A stack trace prints such a frame by its position alone, as V8 does.
     */
    private boolean program;
    private final JSObject prototype;
    private final int selfCaptureIndex;
    private int selfLocalIndex;
    private String sourceCode;
    private final boolean strict;
    private VarRef[] varRefs;

    /**
     * Create a bytecode function with full configuration.
     */
    public JSBytecodeFunction(JSContext context, Bytecode bytecode, String name, int length, JSValue[] closureVars,
            JSObject prototype, boolean isConstructor, boolean isAsync, boolean isGenerator, boolean isArrow,
            boolean strict, String sourceCode) {
        this(context, bytecode, name, length, closureVars, prototype, isConstructor, isAsync, isGenerator, isArrow,
                strict, sourceCode, -1);
    }

    /**
     * Create a bytecode function with full configuration and self-capture index.
     */
    public JSBytecodeFunction(JSContext context, Bytecode bytecode, String name, int length, JSValue[] closureVars,
            JSObject prototype, boolean isConstructor, boolean isAsync, boolean isGenerator, boolean isArrow,
            boolean strict, String sourceCode, int selfCaptureIndex) {
        super(context); // Initialize as JSObject
        this.bytecode = bytecode;
        this.name = name != null ? name : "";
        this.length = length;
        this.closureVars = closureVars != null ? closureVars : JSValue.NO_ARGS;
        this.prototype = prototype;
        this.isConstructor = isConstructor;
        this.isAsync = isAsync;
        this.isGenerator = isGenerator;
        this.isArrow = isArrow;
        this.strict = strict;
        this.sourceCode = sourceCode;
        this.selfCaptureIndex = selfCaptureIndex;
        selfLocalIndex = -1;

        // Set up function properties on the object
        // Per ES spec, length comes before name in property order
        // Both are {writable: false, enumerable: false, configurable: true}
        this.defineProperty(PropertyKey.LENGTH,
                PropertyDescriptor.dataDescriptor(JSNumber.of(this.length), PropertyDescriptor.DataState.Configurable));
        this.defineProperty(PropertyKey.NAME,
                PropertyDescriptor.dataDescriptor(new JSString(this.name), PropertyDescriptor.DataState.Configurable));

        // Every function (except arrow functions) has a prototype property
        if (prototype != null) {
            // Class constructor: prototype is {writable: false, enumerable: false, configurable: false}
            this.defineProperty(PropertyKey.PROTOTYPE, prototype, PropertyDescriptor.DataState.None);
        } else if (isConstructor) {
            // User-defined function constructor: prototype is {writable: true, enumerable: false, configurable: false}
            JSObject funcPrototype = new JSObject(context);
            funcPrototype.defineProperty(PropertyKey.CONSTRUCTOR,
                    PropertyDescriptor.dataDescriptor(this, PropertyDescriptor.DataState.ConfigurableWritable));
            this.defineProperty(PropertyKey.PROTOTYPE,
                    PropertyDescriptor.dataDescriptor(funcPrototype, PropertyDescriptor.DataState.Writable));
        }
    }

    public JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        return callInternal(context, thisArg, args, args.length);
    }

    /**
     * Call with an explicit argument count. When {@code args} comes from a reusable buffer, {@code argCount} may be
     * less than {@code args.length}. For generators/async the buffer is copied to a right-sized array before being
     * stored in the long-lived {@link JSGeneratorState}.
     */
    public JSValue call(JSContext context, JSValue thisArg, JSValue[] args, int argCount) {
        // For generators and async functions that persist args in JSGeneratorState,
        // ensure we own a right-sized copy so the shared buffer can be reused.
        if ((isGenerator || isAsync) && args.length != argCount) {
            JSValue[] owned = new JSValue[argCount];
            System.arraycopy(args, 0, owned, 0, argCount);
            return call(context, thisArg, owned);
        }
        return callInternal(context, thisArg, args, argCount);
    }

    private JSValue callInternal(JSContext context, JSValue thisArg, JSValue[] args, int argCount) {
        // Per ES spec, each function has a [[Realm]] internal slot. When called cross-realm,
        // the function should execute in its own realm, not the caller's realm.
        JSContext executionContext = getHomeContext() != null
                ? getHomeContext()
                : (context != null ? context : getContext());

        if (classConstructor) {
            return executionContext
                    .throwTypeError("Class constructor " + getName() + " cannot be invoked without 'new'");
        }

        // Arrow functions use the lexically captured this, ignoring the provided thisArg
        if (isArrow && capturedThisArg != null) {
            thisArg = capturedThisArg;
        }

        // OrdinaryCallBindThis: in non-strict mode, coerce this value
        if (!strict && !(thisArg instanceof JSObject)) {
            if (thisArg instanceof JSUndefined || thisArg instanceof JSNull) {
                thisArg = executionContext.getGlobalObject();
            } else {
                // Auto-box primitives to wrapper objects (String, Number, Boolean, etc.)
                thisArg = JSTypeConversions.toObject(executionContext, thisArg);
            }
        }

        if (isGenerator) {
            return startGenerator(executionContext, thisArg, args);
        }

        // If this is an async function, wrap execution in a promise
        if (isAsync) {
            JSPromise promise = context.createJSPromise();
            JSGeneratorState asyncFunctionState = new JSGeneratorState(this, thisArg, args);
            resumeAsyncFunctionExecution(context, asyncFunctionState, promise);
            return promise;
        }

        // For non-async functions, execute normally and let exceptions propagate
        // Arrow functions pass their captured new.target to the frame
        if (isArrow && capturedNewTarget != null) {
            return executionContext.getVirtualMachine().execute(this, thisArg, args, argCount, capturedNewTarget);
        }
        return executionContext.getVirtualMachine().execute(this, thisArg, args, argCount, JSUndefined.INSTANCE);
    }

    private void copyClosureStateTo(JSBytecodeFunction target) {
        target.hasParameterExpressions = hasParameterExpressions;
        target.hasArgumentsParameterBinding = hasArgumentsParameterBinding;
        target.selfLocalIndex = selfLocalIndex;
        target.parameterScopeFunctionName = parameterScopeFunctionName;
        target.bodyScopeEnteredLocalIndex = bodyScopeEnteredLocalIndex;
        target.inheritedParameterScopeFunctionNames = inheritedParameterScopeFunctionNames;
        target.capturedActiveFunction = capturedActiveFunction;
        target.capturedDerivedThisRef = capturedDerivedThisRef;
        target.capturedThisArg = capturedThisArg;
        target.capturedArguments = capturedArguments;
        target.capturedNewTarget = capturedNewTarget;
        target.capturedVarNames = capturedVarNames;
        target.classPrivateSymbols = classPrivateSymbols;
        target.classPrivateSymbolRemap = classPrivateSymbolRemap;
        target.displaysAsArgumentsObjectInToString = displaysAsArgumentsObjectInToString;
        target.evalDynamicScopeFrame = evalDynamicScopeFrame;
        target.evalDynamicScopeLookupEnabled = evalDynamicScopeLookupEnabled;
    }

    public JSBytecodeFunction copyTemplateWithRemappedPrivateSymbols(IdentityHashMap<JSSymbol, JSSymbol> symbolRemap) {
        if (symbolRemap == null || symbolRemap.isEmpty()) {
            return this;
        }
        return internalCopyTemplateWithRemappedPrivateSymbols(symbolRemap, new IdentityHashMap<>());
    }

    public JSBytecodeFunction copyWithClosureVars(JSValue[] capturedClosureVars) {
        JSBytecodeFunction copiedFunction = new JSBytecodeFunction(context, bytecode, name, length, capturedClosureVars,
                prototype, isConstructor, isAsync, isGenerator, isArrow, strict, sourceCode, selfCaptureIndex);
        copyClosureStateTo(copiedFunction);
        return copiedFunction;
    }

    /**
     * Create a copy of this function with the given VarRef array for closure variables. Used by FCLOSURE when creating
     * closures with reference-based capture.
     */
    public JSBytecodeFunction copyWithVarRefs(VarRef[] capturedVarRefs) {
        JSBytecodeFunction copiedFunction = new JSBytecodeFunction(context, bytecode, name, length, JSValue.NO_ARGS,
                prototype, isConstructor, isAsync, isGenerator, isArrow, strict, sourceCode, selfCaptureIndex);
        copiedFunction.varRefs = capturedVarRefs;
        copyClosureStateTo(copiedFunction);
        return copiedFunction;
    }

    public boolean displaysAsArgumentsObjectInToString() {
        return displaysAsArgumentsObjectInToString;
    }

    /**
     * This function's own named-function-expression binding, when the given frame is still initializing parameters and
     * so resolves the name through the parameter environment.
     * <p>
     * Kept apart from {@link #getInheritedParameterScopeFunctionNames()} because the two answer different questions.
     * This one names a binding of <em>this</em> frame, so it holds whatever the frame's own slots say; an inherited one
     * names a binding of an enclosing frame, which any binding this function declares under the same spelling shadows.
     *
     * @param frame
     *            the frame to answer for
     * @return the name, or null when the frame is not in that phase
     */
    public String getActiveParameterScopeFunctionName(StackFrame frame) {
        return isOwnParameterScopeActive(frame) ? parameterScopeFunctionName : null;
    }

    /**
     * The local slot this function sets to {@code true} the moment it enters its body, or -1 when it has no such slot.
     *
     * @return the slot index, or -1
     * @see #getParameterScopeFunctionName()
     */
    public int getBodyScopeEnteredLocalIndex() {
        return bodyScopeEnteredLocalIndex;
    }

    /**
     * Get the bytecode for this function.
     */
    public Bytecode getBytecode() {
        return bytecode;
    }

    /**
     * Get the captured active function for arrow functions. Used by SPECIAL_OBJECT 2 (THIS_FUNC) to resolve the
     * enclosing constructor for super() calls inside arrow functions.
     */
    public JSFunction getCapturedActiveFunction() {
        return capturedActiveFunction;
    }

    /**
     * Get the captured arguments object for arrow functions. Arrow functions lexically inherit arguments from the
     * enclosing non-arrow function.
     */
    public JSValue getCapturedArguments() {
        return capturedArguments;
    }

    public VarRef getCapturedDerivedThisRef() {
        return capturedDerivedThisRef;
    }

    /**
     * Get the captured new.target value for arrow functions. Arrow functions lexically inherit new.target from the
     * enclosing function.
     */
    public JSValue getCapturedNewTarget() {
        return capturedNewTarget;
    }

    /**
     * Get the captured this value for arrow functions.
     */
    public JSValue getCapturedThisArg() {
        return capturedThisArg;
    }

    public String getCapturedVarName(int captureSlot) {
        if (capturedVarNames == null || captureSlot < 0 || captureSlot >= capturedVarNames.length) {
            return null;
        }
        return capturedVarNames[captureSlot];
    }

    public String[] getCapturedVarNames() {
        return capturedVarNames;
    }

    /**
     * Get the capture source info array for template functions. Each entry encodes the source of a closure capture: -
     * {@code value >= 0}: LOCAL capture at that local slot index - {@code value < 0}: VAR_REF capture at
     * {@code -(value + 1)} Returns null for non-template (instantiated) functions.
     */
    public int[] getCaptureSourceInfos() {
        return captureSourceInfos;
    }

    public IdentityHashMap<JSSymbol, JSSymbol> getClassPrivateSymbolRemap() {
        return classPrivateSymbolRemap;
    }

    public Set<JSSymbol> getClassPrivateSymbols() {
        return classPrivateSymbols;
    }

    /**
     * Get the closure variables (captured from outer scopes).
     */
    public JSValue[] getClosureVars() {
        return closureVars;
    }

    /**
     * Get the constructor's prototype property (for constructor calls / new). Note: This is different from
     * getPrototype() which returns the internal [[Prototype]].
     */
    public JSObject getConstructorPrototype() {
        return prototype;
    }

    public StackFrame getEvalDynamicScopeFrame() {
        return evalDynamicScopeFrame;
    }

    /**
     * The named-function-expression bindings this function captured from enclosing parameter environments.
     * <p>
     * A closure written inside two nested initializers is inside both environments, so one name was never enough — the
     * inner one displaced the outer, and the outer binding silently vanished from everything compiled there.
     * <p>
     * These are names, not binding identities, and a name is not a binding: a function nested in an enclosing
     * initializer may declare its own parameter, {@code var}, lexical, class or function of the same spelling, and that
     * nearer binding is a different one — mutable, except for {@code const}. Callers must therefore treat an entry here
     * as authoritative only for a name the frame does not bind itself.
     *
     * @return the inherited bindings; empty when there are none
     */
    public Set<String> getInheritedParameterScopeFunctionNames() {
        return inheritedParameterScopeFunctionNames;
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * The name of this named function expression, when the body declares that name again.
     * <p>
     * ES2024 15.2.5 binds the {@code BindingIdentifier} of a named function expression in a function environment that
     * <em>wraps</em> the parameter and variable environments. A default initializer therefore resolves the name to the
     * function itself, while a {@code var}, lexical, class or function declaration of the same name in the body is a
     * different binding that starts as {@code undefined}. Both live in one flattened frame here, in two slots, and
     * compiled code refers to whichever is correct by index.
     * <p>
     * Direct {@code eval} cannot: it resolves by name, at run time, and the answer depends on which of the two
     * environments the frame is currently executing in. {@link #getBodyScopeEnteredLocalIndex()} is what tells the two
     * phases apart — it is false for the whole of parameter initialization and true from the first instruction of the
     * body — so a direct {@code eval} in a default initializer can be given the function, and one in the body the
     * body's own binding.
     *
     * @return the shadowed function-expression name, or null when this function has none
     */
    public String getParameterScopeFunctionName() {
        return parameterScopeFunctionName;
    }

    /**
     * Get the self-capture index for closure self-reference patching. Returns -1 if this function does not capture its
     * own name. Following QuickJS var_refs pattern where a function's closure variable pointing to itself is patched
     * after creation.
     */
    public int getSelfCaptureIndex() {
        return selfCaptureIndex;
    }

    public int getSelfLocalIndex() {
        return selfLocalIndex;
    }

    /**
     * Get the source code for this function.
     */
    public String getSourceCode() {
        return sourceCode;
    }

    /**
     * Get the VarRef array for closure variables (reference-based capture). Returns null if this function uses
     * value-based closureVars instead.
     */
    public VarRef[] getVarRefs() {
        return varRefs;
    }

    public boolean hasArgumentsParameterBinding() {
        return hasArgumentsParameterBinding;
    }

    /**
     * Check if this function has parameter expressions (default values, rest, or destructuring). Following QuickJS
     * has_parameter_expressions flag. Used by eval() to detect when var declarations would conflict with implicit
     * arguments binding.
     */
    public boolean hasParameterExpressions() {
        return hasParameterExpressions;
    }

    private Bytecode internalCopyBytecodeWithRemappedPrivateSymbols(IdentityHashMap<JSSymbol, JSSymbol> symbolRemap,
            IdentityHashMap<JSBytecodeFunction, JSBytecodeFunction> functionMemo) {
        JSValue[] originalConstants = bytecode.getConstants();
        if (originalConstants.length == 0) {
            return bytecode;
        }
        JSValue[] copiedConstants = new JSValue[originalConstants.length];
        boolean changed = false;
        for (int i = 0; i < originalConstants.length; i++) {
            JSValue originalConstant = originalConstants[i];
            JSValue copiedConstant = originalConstant;
            if (originalConstant instanceof JSSymbol originalSymbol) {
                JSSymbol remappedSymbol = symbolRemap.get(originalSymbol);
                if (remappedSymbol != null) {
                    copiedConstant = remappedSymbol;
                }
            } else if (originalConstant instanceof JSBytecodeFunction bytecodeFunction) {
                copiedConstant = bytecodeFunction.internalCopyTemplateWithRemappedPrivateSymbols(symbolRemap,
                        functionMemo);
            }
            copiedConstants[i] = copiedConstant;
            if (copiedConstant != originalConstant) {
                changed = true;
            }
        }
        if (!changed) {
            return bytecode;
        }
        Bytecode copiedBytecode = new Bytecode(bytecode.getInstructions(), copiedConstants, bytecode.getAtoms(),
                bytecode.getLocalCount(), bytecode.getLocalVarNames());
        copiedBytecode.setSourcePositions(bytecode.getSourcePositions());
        copiedBytecode.setCallDescriptions(bytecode.getCallDescriptions());
        return copiedBytecode;
    }

    private JSBytecodeFunction internalCopyTemplateWithRemappedPrivateSymbols(
            IdentityHashMap<JSSymbol, JSSymbol> symbolRemap,
            IdentityHashMap<JSBytecodeFunction, JSBytecodeFunction> functionMemo) {
        JSBytecodeFunction memoizedFunction = functionMemo.get(this);
        if (memoizedFunction != null) {
            return memoizedFunction;
        }
        Bytecode remappedBytecode = internalCopyBytecodeWithRemappedPrivateSymbols(symbolRemap, functionMemo);
        JSBytecodeFunction copiedFunction = new JSBytecodeFunction(context, remappedBytecode, name, length, closureVars,
                prototype, isConstructor, isAsync, isGenerator, isArrow, strict, sourceCode, selfCaptureIndex);
        functionMemo.put(this, copiedFunction);
        copiedFunction.captureSourceInfos = captureSourceInfos;
        copyClosureStateTo(copiedFunction);
        copiedFunction.classConstructor = classConstructor;
        copiedFunction.derivedConstructor = derivedConstructor;
        copiedFunction.varRefs = varRefs;
        return copiedFunction;
    }

    /**
     * Check if this is an arrow function.
     */
    public boolean isArrow() {
        return isArrow;
    }

    /**
     * Check if this is an async function.
     */
    public boolean isAsync() {
        return isAsync;
    }

    /**
     * Check if this is a class constructor. Class constructors throw TypeError when called without 'new'.
     */
    public boolean isClassConstructor() {
        return classConstructor;
    }

    /**
     * Check if this function can be used as a constructor.
     */
    public boolean isConstructor() {
        return isConstructor;
    }

    /**
     * Check if this is a derived class constructor (extends a parent class).
     */
    public boolean isDerivedConstructor() {
        return derivedConstructor;
    }

    public boolean isEvalDynamicScopeLookupEnabled() {
        return evalDynamicScopeLookupEnabled;
    }

    public boolean isEvalSuperCallAllowed() {
        return evalSuperCallAllowed;
    }

    /**
     * Check if this is a generator function.
     */
    public boolean isGenerator() {
        return isGenerator;
    }

    public boolean isNewTargetAllowed() {
        return newTargetAllowed;
    }

    /**
     * Whether this frame is still initializing the parameters of a named function expression whose body redeclares its
     * own name.
     *
     * @param frame
     *            the frame to answer for
     * @return true while the parameter environment, not the body, is what the name resolves through
     */
    public boolean isOwnParameterScopeActive(StackFrame frame) {
        if (parameterScopeFunctionName == null || bodyScopeEnteredLocalIndex < 0) {
            return false;
        }
        JSValue[] locals = frame == null ? null : frame.getLocals();
        return locals != null && bodyScopeEnteredLocalIndex < locals.length
                && locals[bodyScopeEnteredLocalIndex] != JSBoolean.TRUE;
    }

    public boolean isProgram() {
        return program;
    }

    /**
     * Check if this function is in strict mode. Following QuickJS {@code js_mode & JS_MODE_STRICT}.
     */
    public boolean isStrict() {
        return strict;
    }

    /**
     * Set the captured active function for arrow functions. Called during FCLOSURE to capture the enclosing non-arrow
     * function reference for super() resolution.
     */
    public void setCapturedActiveFunction(JSFunction capturedActiveFunction) {
        this.capturedActiveFunction = capturedActiveFunction;
    }

    /**
     * Set the captured arguments object for arrow functions. Called during FCLOSURE to capture the enclosing non-arrow
     * function's arguments.
     */
    public void setCapturedArguments(JSValue capturedArguments) {
        this.capturedArguments = capturedArguments;
    }

    public void setCapturedDerivedThisRef(VarRef ref) {
        this.capturedDerivedThisRef = ref;
    }

    /**
     * Set the captured new.target value for arrow functions. Called during FCLOSURE to capture the enclosing function's
     * new.target.
     */
    public void setCapturedNewTarget(JSValue capturedNewTarget) {
        this.capturedNewTarget = capturedNewTarget;
    }

    /**
     * Set the captured this value for arrow functions. Called during FCLOSURE to capture the enclosing scope's this.
     */
    public void setCapturedThisArg(JSValue thisArg) {
        this.capturedThisArg = thisArg;
    }

    public void setCapturedVarNames(String[] capturedVarNames) {
        this.capturedVarNames = capturedVarNames;
    }

    /**
     * Set the capture source info array for this template function. Called by the compiler to record where each closure
     * variable comes from.
     */
    public void setCaptureSourceInfos(int[] captureSourceInfos) {
        this.captureSourceInfos = captureSourceInfos;
    }

    /**
     * Mark this function as a class constructor. Called during DEFINE_CLASS opcode execution.
     */
    public void setClassConstructor(boolean classConstructor) {
        this.classConstructor = classConstructor;
    }

    public void setClassPrivateSymbolRemap(IdentityHashMap<JSSymbol, JSSymbol> classPrivateSymbolRemap) {
        this.classPrivateSymbolRemap = classPrivateSymbolRemap;
    }

    public void setClassPrivateSymbols(Collection<JSSymbol> classPrivateSymbols) {
        if (classPrivateSymbols == null || classPrivateSymbols.isEmpty()) {
            this.classPrivateSymbols = Set.of();
            return;
        }
        Set<JSSymbol> symbolSet = Collections.newSetFromMap(new IdentityHashMap<>());
        symbolSet.addAll(classPrivateSymbols);
        this.classPrivateSymbols = symbolSet;
    }

    /**
     * Mark this function as a derived class constructor. Called during DEFINE_CLASS opcode execution when a superclass
     * is present.
     */
    public void setDerivedConstructor(boolean derivedConstructor) {
        this.derivedConstructor = derivedConstructor;
    }

    public void setDisplaysAsArgumentsObjectInToString(boolean displaysAsArgumentsObjectInToString) {
        this.displaysAsArgumentsObjectInToString = displaysAsArgumentsObjectInToString;
    }

    public void setEvalDynamicScopeFrame(StackFrame evalDynamicScopeFrame) {
        this.evalDynamicScopeFrame = evalDynamicScopeFrame;
    }

    public void setEvalDynamicScopeLookupEnabled(boolean evalDynamicScopeLookupEnabled) {
        this.evalDynamicScopeLookupEnabled = evalDynamicScopeLookupEnabled;
    }

    public void setEvalSuperCallAllowed(boolean evalSuperCallAllowed) {
        this.evalSuperCallAllowed = evalSuperCallAllowed;
    }

    public void setHasArgumentsParameterBinding(boolean hasArgumentsParameterBinding) {
        this.hasArgumentsParameterBinding = hasArgumentsParameterBinding;
    }

    /**
     * Set whether this function has parameter expressions.
     */
    public void setHasParameterExpressions(boolean hasParameterExpressions) {
        this.hasParameterExpressions = hasParameterExpressions;
    }

    /**
     * Record the named-function-expression bindings this function captured from the parameter environments it was
     * created inside.
     *
     * @param inheritedParameterScopeFunctionNames
     *            the names
     * @see #getInheritedParameterScopeFunctionNames()
     */
    public void setInheritedParameterScopeFunctionNames(Set<String> inheritedParameterScopeFunctionNames) {
        this.inheritedParameterScopeFunctionNames = inheritedParameterScopeFunctionNames == null
                ? Set.of()
                : inheritedParameterScopeFunctionNames;
    }

    public void setNewTargetAllowed(boolean newTargetAllowed) {
        this.newTargetAllowed = newTargetAllowed;
    }

    /**
     * Record the two-environment shape of a named function expression whose body redeclares its name.
     *
     * @param parameterScopeFunctionName
     *            the function-expression name
     * @param bodyScopeEnteredLocalIndex
     *            the slot set to true on entering the body
     * @see #getActiveParameterScopeFunctionName(StackFrame)
     */
    public void setOwnParameterScopeFunctionName(String parameterScopeFunctionName, int bodyScopeEnteredLocalIndex) {
        this.parameterScopeFunctionName = parameterScopeFunctionName;
        this.bodyScopeEnteredLocalIndex = bodyScopeEnteredLocalIndex;
    }

    public void setProgram(boolean program) {
        this.program = program;
    }

    public void setSelfLocalIndex(int selfLocalIndex) {
        this.selfLocalIndex = selfLocalIndex;
    }

    /**
     * Set the source code for this function. Used to override the default toString() representation.
     */
    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    private JSObject startGenerator(JSContext executionContext, JSValue thisArg, JSValue[] args) {
        // Both kinds execute through INITIAL_YIELD so parameter errors surface at the call site.
        JSGeneratorState state = new JSGeneratorState(this, thisArg, args);
        executionContext.getVirtualMachine().executeGenerator(state, executionContext);
        JSGenerator driver = new JSGenerator(executionContext, state);
        JSObject generator = isAsync ? AsyncGeneratorExecution.create(executionContext, state, driver) : driver;
        JSValue prototype = get(PropertyKey.PROTOTYPE);
        if (prototype instanceof JSObject object) {
            generator.setPrototype(object);
        } else if (isAsync && executionContext.getAsyncGeneratorPrototype() != null) {
            generator.setPrototype(executionContext.getAsyncGeneratorPrototype());
        } else {
            JSObject functionPrototype = isAsync
                    ? executionContext.getAsyncGeneratorFunctionPrototype()
                    : executionContext.getGeneratorFunctionPrototype();
            if (functionPrototype != null
                    && functionPrototype.get(PropertyKey.PROTOTYPE) instanceof JSObject fallback) {
                generator.setPrototype(fallback);
            }
        }
        return generator;
    }

    @Override
    public Object toJavaObject() {
        return toString();
    }

    @Override
    public String toString() {
        // If source code is available, return it (QuickJS: b->debug.source)
        if (sourceCode != null) {
            return sourceCode;
        }

        // QuickJS format: "[prefix]name() {\n [native code]\n}"
        String prefix;
        if (isAsync && isGenerator) {
            prefix = "async function *";
        } else if (isAsync) {
            prefix = "async function ";
        } else if (isGenerator) {
            prefix = "function *";
        } else {
            prefix = "function ";
        }
        String displayName = name != null ? name : "";
        return prefix + displayName + "() {\n    [native code]\n}";
    }

    @Override
    public JSValueType type() {
        return JSValueType.FUNCTION;
    }

    private static void resumeAsyncFunctionExecution(JSContext context, JSGeneratorState asyncFunctionState,
            JSPromise outerPromise) {
        try {
            JSValue result = context.getVirtualMachine().executeAsyncFunction(asyncFunctionState, context);
            JSPromise awaitedPromise = context.getVirtualMachine().consumeAwaitSuspensionPromise();
            if (awaitedPromise != null) {
                awaitedPromise.observe(context, new JSNativeFunction(context, "onAwaitResolve", 1,
                        (childContext, callbackThisArg, callbackArgs) -> {
                            JSValue resolvedValue = JSValue.argumentOrUndefined(callbackArgs, 0);
                            asyncFunctionState.setPendingResumeRecord(JSGeneratorState.ResumeKind.NEXT, resolvedValue);
                            resumeAsyncFunctionExecution(context, asyncFunctionState, outerPromise);
                            return JSUndefined.INSTANCE;
                        }), new JSNativeFunction(context, "onAwaitReject", 1,
                                (childContext, callbackThisArg, callbackArgs) -> {
                                    JSValue rejectionValue = JSValue.argumentOrUndefined(callbackArgs, 0);
                                    asyncFunctionState.setPendingResumeRecord(JSGeneratorState.ResumeKind.THROW,
                                            rejectionValue);
                                    resumeAsyncFunctionExecution(context, asyncFunctionState, outerPromise);
                                    return JSUndefined.INSTANCE;
                                }));
                return;
            }
            asyncFunctionState.clearSuspendedExecutionState();
            if (context.hasPendingException()) {
                outerPromise.reject(context.takeExecutionException());
                return;
            }
            outerPromise.resolve(context, result);
        } catch (JSVirtualMachineException e) {
            if (context.hasPendingException()) {
                outerPromise.reject(context.takeExecutionException());
            } else if (e.getJsValue() != null) {
                outerPromise.reject(e.getJsValue());
            } else if (e.getJsError() != null) {
                outerPromise.reject(e.getJsError());
            } else {
                String errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
                JSObject errorObj = context.createJSObject();
                errorObj.set(PropertyKey.MESSAGE, new JSString(errorMessage));
                outerPromise.reject(errorObj);
            }
        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
            JSObject errorObj = context.createJSObject();
            errorObj.set(PropertyKey.MESSAGE, new JSString(errorMessage));
            outerPromise.reject(errorObj);
        }
    }
}
