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

package com.caoccao.qjs4j.vm;

import com.caoccao.qjs4j.core.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a call frame (activation record) on the call stack.
 */
public final class StackFrame {
    private static final VarRef[] EMPTY_VARREFS = new VarRef[0];

    private final JSValue[] arguments;
    private final StackFrame caller;
    private final JSValue[] closureVars;
    private final int frameDepth;
    private final JSFunction function;
    private final JSValue[] locals;
    private final JSValue newTarget;
    private final int stackBase;
    private final VarRef[] varRefs;
    private VarRef[] closedVarRefs;
    private VarRef derivedThisRef;
    private Map<String, Integer> dynamicVarBindingLocalIndexes;
    private Map<String, JSValue> dynamicVarBindings;
    private Map<String, VarRef> evalScopeVarRefs;
    private VarRef[] localVarRefs;
    private JSArguments mappedArgumentsObject;
    private int programCounter;
    private JSValue thisArg;
    private JSArguments unmappedArgumentsObject;

    /**
     * Create a stack frame for a function call.
     *
     * @param args     argument values — may be a reusable buffer; values are copied
     *                 into arguments and locals, so the caller can reuse the array after this returns.
     * @param argCount the actual number of arguments (may be &lt; args.length when
     *                 a shared buffer is used)
     */
    public StackFrame(JSFunction function, JSValue thisArg, JSValue[] args, int argCount, StackFrame caller, JSValue newTarget, int stackBase) {
        this.function = function;
        this.thisArg = thisArg;
        this.newTarget = newTarget;
        this.stackBase = stackBase;
        if (caller == null) {
            frameDepth = 1;
        } else {
            frameDepth = caller.frameDepth + 1;
        }

        // Allocate locals array based on function's local count
        // For bytecode functions, get local count from bytecode metadata
        // For native functions, just use args
        int localCount = 0;
        if (function instanceof JSBytecodeFunction bytecodeFunc) {
            localCount = bytecodeFunc.getBytecode().getLocalCount();
        }

        if (localCount > 0) {
            // Create owned copy of arguments from the (possibly shared) buffer.
            // Arguments and locals are separate arrays: GET_ARG reads arguments,
            // GET_LOC reads locals. SET_LOC_UNINITIALIZED may overwrite locals
            // for default-parameter TDZ, but arguments must retain original values.
            this.arguments = new JSValue[argCount];
            System.arraycopy(args, 0, this.arguments, 0, argCount);
            this.locals = new JSValue[localCount];
            System.arraycopy(args, 0, this.locals, 0, Math.min(argCount, localCount));
            // Initialize remaining locals to undefined
            for (int i = argCount; i < localCount; i++) {
                this.locals[i] = JSUndefined.INSTANCE;
            }
            if (function instanceof JSBytecodeFunction bytecodeFunction) {
                int selfLocalIndex = bytecodeFunction.getSelfLocalIndex();
                if (selfLocalIndex >= 0 && selfLocalIndex < localCount) {
                    this.locals[selfLocalIndex] = function;
                }
            }
        } else {
            // For native functions or functions with no locals.
            // args is always an owned array for native functions (allocated in CALL handler).
            this.arguments = args;
            this.locals = args;
        }

        // Initialize closure variable references
        // Prefer VarRef[] (reference-based) over JSValue[] (value-based)
        if (function instanceof JSBytecodeFunction bytecodeFunc) {
            VarRef[] funcVarRefs = bytecodeFunc.getVarRefs();
            if (funcVarRefs != null) {
                this.varRefs = funcVarRefs;
                this.closureVars = JSValue.NO_ARGS;
            } else if (bytecodeFunc.getClosureVars() != null) {
                this.closureVars = bytecodeFunc.getClosureVars();
                this.varRefs = EMPTY_VARREFS;
            } else {
                this.closureVars = JSValue.NO_ARGS;
                this.varRefs = EMPTY_VARREFS;
            }
        } else {
            this.closureVars = JSValue.NO_ARGS;
            this.varRefs = EMPTY_VARREFS;
        }
        this.programCounter = 0;
        this.caller = caller;
        this.dynamicVarBindings = null;
        this.dynamicVarBindingLocalIndexes = null;
    }

    /**
     * Close a local variable, following QuickJS close_var_refs pattern.
     * Detaches any VarRef for this local from the locals array, freezing its
     * current value. This enables per-iteration binding in for loops:
     * closures from different iterations each get their own frozen value.
     * Also makes the local accessible via getVarRef() for TDZ checks.
     */
    public void closeLocal(int index) {
        if (index < 0 || index >= locals.length) {
            return;
        }

        // Get existing VarRef or create one
        VarRef ref;
        if (localVarRefs != null && index < localVarRefs.length && localVarRefs[index] != null) {
            ref = localVarRefs[index];
        } else {
            ref = getOrCreateLocalVarRef(index);
        }

        // Detach from locals array — copies current value to standalone storage
        ref.close();

        // Clear localVarRefs entry so next FCLOSURE creates a fresh VarRef
        localVarRefs[index] = null;

        // Store the closed VarRef for access via getVarRef (needed for TDZ checks)
        if (closedVarRefs == null) {
            closedVarRefs = new VarRef[locals.length];
        }
        closedVarRefs[index] = ref;
    }

    /**
     * Read a single argument value by index.
     * Reads from the arguments array (not locals), so TDZ markers set by
     * SET_LOC_UNINITIALIZED do not affect the result.
     */
    public JSValue getArgument(int index) {
        if (index >= 0 && index < arguments.length) {
            JSValue value = arguments[index];
            return value != null ? value : JSUndefined.INSTANCE;
        }
        return JSUndefined.INSTANCE;
    }

    /**
     * Return the argument count (number of arguments actually passed to this call).
     */
    public int getArgumentCount() {
        return arguments.length;
    }

    /**
     * Return the original arguments array.
     * For bytecode functions this is a separate owned copy that is never modified
     * by LOC opcodes, preserving pre-default-parameter values.
     */
    public JSValue[] getArguments() {
        return arguments;
    }

    public JSArguments getArgumentsObject(boolean mapped) {
        return mapped ? mappedArgumentsObject : unmappedArgumentsObject;
    }

    public StackFrame getCaller() {
        return caller;
    }

    public VarRef getDerivedThisRef() {
        return derivedThisRef;
    }

    public JSValue getDynamicVarBinding(String name) {
        if (dynamicVarBindingLocalIndexes != null) {
            Integer localIndex = dynamicVarBindingLocalIndexes.get(name);
            if (localIndex != null
                    && localIndex >= 0
                    && localIndex < locals.length) {
                return locals[localIndex];
            }
        }
        if (dynamicVarBindings == null) {
            return null;
        }
        return dynamicVarBindings.get(name);
    }

    public Map<String, JSValue> getDynamicVarBindings() {
        return dynamicVarBindings;
    }

    public int getFrameDepth() {
        return frameDepth;
    }

    public JSFunction getFunction() {
        return function;
    }

    public JSValue[] getLocals() {
        return locals;
    }

    public JSValue getNewTarget() {
        return newTarget;
    }

    /**
     * Get or create a VarRef for a local variable at the given index.
     * Used during FCLOSURE to create shared references for captured locals.
     * Multiple closures capturing the same local will share the same VarRef.
     */
    public VarRef getOrCreateLocalVarRef(int localIndex) {
        if (localVarRefs == null) {
            localVarRefs = new VarRef[locals.length];
        }
        if (localVarRefs[localIndex] == null) {
            localVarRefs[localIndex] = new VarRef(locals, localIndex);
        }
        return localVarRefs[localIndex];
    }

    public int getProgramCounter() {
        return programCounter;
    }

    public int getStackBase() {
        return stackBase;
    }

    public JSValue getThisArg() {
        return thisArg;
    }

    /**
     * Get a closure variable by index.
     * Checks VarRefs (from FCLOSURE) first, then closed VarRefs (from CLOSE_LOC),
     * then falls back to direct JSValue access (class methods, legacy).
     * VarRefs must be checked before closedVarRefs because they use different
     * index spaces (VarRef indices vs local indices) that can collide.
     */
    public JSValue getVarRef(int index) {
        // Use VarRef if available (reference-based capture from FCLOSURE)
        if (index >= 0 && index < varRefs.length) {
            return varRefs[index].get();
        }
        // Check closed VarRefs (from CLOSE_LOC — promotes local to var ref space)
        if (closedVarRefs != null && index >= 0 && index < closedVarRefs.length) {
            VarRef ref = closedVarRefs[index];
            if (ref != null) {
                return ref.get();
            }
        }
        // Fall back to direct JSValue access (class methods, legacy)
        if (index >= 0 && index < closureVars.length) {
            JSValue value = closureVars[index];
            return value != null ? value : JSUndefined.INSTANCE;
        }
        return JSUndefined.INSTANCE;
    }

    /**
     * Get the VarRef at the given closure variable index.
     * Used during FCLOSURE to share VarRef objects with child closures.
     */
    public VarRef getVarRefCell(int index) {
        if (index >= 0 && index < varRefs.length) {
            return varRefs[index];
        }
        return null;
    }

    public boolean hasDynamicVarBinding(String name) {
        if (dynamicVarBindingLocalIndexes != null && dynamicVarBindingLocalIndexes.containsKey(name)) {
            return true;
        }
        return dynamicVarBindings != null && dynamicVarBindings.containsKey(name);
    }

    public boolean hasDynamicVarBindingAlias(String name) {
        return dynamicVarBindingLocalIndexes != null && dynamicVarBindingLocalIndexes.containsKey(name);
    }

    public boolean removeDynamicVarBinding(String name) {
        if (hasDynamicVarBindingAlias(name)) {
            return false;
        }
        if (dynamicVarBindings == null) {
            return false;
        }
        return dynamicVarBindings.remove(name) != null;
    }

    /**
     * Write a single argument value by index.
     * Writes to both arguments and locals to keep them in sync for PUT_ARG.
     */
    public void setArgument(int index, JSValue value) {
        if (index >= 0 && index < arguments.length) {
            arguments[index] = value;
            if (index < locals.length) {
                locals[index] = value;
            }
        }
    }

    public void setArgumentsObject(boolean mapped, JSArguments argumentsObject) {
        if (mapped) {
            mappedArgumentsObject = argumentsObject;
        } else {
            unmappedArgumentsObject = argumentsObject;
        }
    }

    public void setDerivedThisRef(VarRef derivedThisRef) {
        this.derivedThisRef = derivedThisRef;
    }

    public void setDynamicVarBinding(String name, JSValue value) {
        if (dynamicVarBindingLocalIndexes != null) {
            Integer localIndex = dynamicVarBindingLocalIndexes.get(name);
            if (localIndex != null
                    && localIndex >= 0
                    && localIndex < locals.length) {
                locals[localIndex] = value;
                return;
            }
        }
        if (dynamicVarBindings == null) {
            dynamicVarBindings = new HashMap<>();
        }
        dynamicVarBindings.put(name, value);
    }

    public void setDynamicVarBindingAlias(String name, int localIndex) {
        if (localIndex < 0 || localIndex >= locals.length) {
            return;
        }
        if (dynamicVarBindingLocalIndexes == null) {
            dynamicVarBindingLocalIndexes = new HashMap<>();
        }
        dynamicVarBindingLocalIndexes.put(name, localIndex);
    }

    public void setProgramCounter(int pc) {
        this.programCounter = pc;
    }

    public void setThisArg(JSValue thisArg) {
        this.thisArg = thisArg;
    }

    /**
     * Set a closure variable by index.
     * Checks VarRefs first, then closed VarRefs, then falls back to direct access.
     * VarRefs must be checked before closedVarRefs because they use different
     * index spaces (VarRef indices vs local indices) that can collide.
     */
    public void setVarRef(int index, JSValue value) {
        // Use VarRef if available (reference-based capture)
        if (index >= 0 && index < varRefs.length) {
            varRefs[index].set(value);
            return;
        }
        // Check closed VarRefs (from CLOSE_LOC)
        if (closedVarRefs != null && index >= 0 && index < closedVarRefs.length) {
            VarRef ref = closedVarRefs[index];
            if (ref != null) {
                ref.set(value);
                return;
            }
        }
        // Fall back to direct JSValue access (class methods, legacy)
        if (index >= 0 && index < closureVars.length) {
            closureVars[index] = value;
        }
    }
}
