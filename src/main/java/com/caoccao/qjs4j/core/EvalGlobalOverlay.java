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

import java.util.*;
import com.caoccao.qjs4j.vm.StackFrame;

/** Caller bindings temporarily installed on the global object for one direct eval. */
final class EvalGlobalOverlay {

    private final Set<String> absentKeys = new HashSet<>();
    private final JSObject global;
    private final Set<String> immutableNames = new HashSet<>();
    private final Map<String, PropertyDescriptor> savedDescriptors = new HashMap<>();
    private final Map<String, JSValue> savedGlobals = new HashMap<>();
    private final Set<String> touchedOverlayKeys = new HashSet<>();

    EvalGlobalOverlay(JSObject global) {
        this.global = global;
    }

    void bind(String name, JSValue value) {
        if (name == null) {
            return;
        }
        remember(name);
        PropertyKey key = PropertyKey.fromString(name);
        JSValue overlayValue = value != null ? value : JSUndefined.INSTANCE;
        if (global.getContext().hasEvalOverlayBinding(name)) {
            PropertyDescriptor current = global.getOwnPropertyDescriptor(key);
            if (current != null && !current.isConfigurable() && current.isDataDescriptor() && current.isWritable()) {
                // A global var keeps its descriptor when a nested eval overlays its value.
                global.set(key, overlayValue);
            } else {
                global.createDataProperty(key, overlayValue);
            }
        } else {
            global.set(key, overlayValue);
        }
    }

    void bindDynamicVariables(StackFrame frame) {
        Map<String, JSValue> bindings = frame.getDynamicVarBindings();
        if (bindings != null) {
            for (var entry : bindings.entrySet()) {
                bind(entry.getKey(), entry.getValue());
            }
        }
    }

    void bindFrameLocals(StackFrame frame, Set<String> localVarNameSet) {
        if (frame == null || !(frame.getFunction() instanceof JSBytecodeFunction lexicalBytecodeFunction)) {
            return;
        }
        String[] lexicalLocalVarNames = lexicalBytecodeFunction.getBytecode().getLocalVarNames();
        JSValue[] lexicalLocals = frame.getLocals();
        if (lexicalLocalVarNames == null || lexicalLocals == null) {
            return;
        }
        for (int localIndex = 0; localIndex < lexicalLocalVarNames.length
                && localIndex < lexicalLocals.length; localIndex++) {
            String localName = lexicalLocalVarNames[localIndex];
            if (localName == null || localName.startsWith("#")) {
                continue;
            }
            if (localVarNameSet != null && localVarNameSet.contains(localName)) {
                continue;
            }
            if (touchedOverlayKeys.contains(localName)) {
                continue;
            }
            if (global.getContext().hasEvalOverlayBinding(localName)) {
                continue;
            }
            JSValue lexicalValue = lexicalLocals[localIndex];
            if (lexicalValue instanceof JSSymbol symbolValue
                    && "UninitializedMarker".equals(symbolValue.getDescription())) {
                continue;
            }
            bind(localName, lexicalValue);
        }
    }

    /** Restore the immutable self binding while a named function evaluates its parameter defaults. */
    void bindParameterFunction(StackFrame callerFrame, JSBytecodeFunction callerBytecodeFunction) {
        if (callerBytecodeFunction == null || !callerBytecodeFunction.isOwnParameterScopeActive(callerFrame)) {
            return;
        }
        int selfLocalIndex = callerBytecodeFunction.getSelfLocalIndex();
        JSValue[] locals = callerFrame.getLocals();
        if (locals == null || selfLocalIndex < 0 || selfLocalIndex >= locals.length) {
            return;
        }
        String name = callerBytecodeFunction.getParameterScopeFunctionName();
        JSValue selfValue = locals[selfLocalIndex] != null ? locals[selfLocalIndex] : JSUndefined.INSTANCE;
        remember(name);
        makeImmutable(name, selfValue);
    }

    void makeImmutable(String name, JSValue value) {
        global.defineProperty(PropertyKey.fromString(name), value == null ? JSUndefined.INSTANCE : value,
                PropertyDescriptor.DataState.EnumerableConfigurable);
        immutableNames.add(name);
    }

    void push(JSEvalEnvironment environment, Set<String> constants) {
        global.getContext().pushEvalOverlay(savedGlobals, savedDescriptors, absentKeys, environment, constants);
    }

    private void remember(String name) {
        if (touchedOverlayKeys.add(name)) {
            PropertyKey key = PropertyKey.fromString(name);
            if (global.has(key)) {
                savedGlobals.put(name, global.get(key));
                PropertyDescriptor descriptor = global.getOwnPropertyDescriptor(key);
                if (descriptor != null) {
                    savedDescriptors.put(name, descriptor);
                }
            } else {
                absentKeys.add(name);
            }
        }
    }

    void restore(Set<String> retainedVars, JSContext caller) {
        for (String name : immutableNames) {
            if (name != null && !name.isEmpty()) {
                global.delete(PropertyKey.fromString(name));
            }
        }
        for (var entry : savedGlobals.entrySet()) {
            String name = entry.getKey();
            if (retainedVars != null && retainedVars.contains(name) && !caller.hasGlobalLexDeclaration(name)) {
                continue;
            }
            PropertyKey key = PropertyKey.fromString(name);
            PropertyDescriptor descriptor = savedDescriptors.get(name);
            if (descriptor == null) {
                global.set(key, entry.getValue());
            } else {
                if (descriptor.isDataDescriptor()) {
                    descriptor.setValue(entry.getValue());
                }
                global.defineProperty(key, descriptor);
            }
        }
        for (String name : absentKeys) {
            global.delete(PropertyKey.fromString(name));
        }
    }

}
