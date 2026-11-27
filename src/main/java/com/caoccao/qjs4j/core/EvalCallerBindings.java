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

import com.caoccao.qjs4j.vm.StackFrame;
import com.caoccao.qjs4j.vm.VarRef;
import java.util.*;

/** Local slots, captures and with environments exposed by one direct eval. */
final class EvalCallerBindings {
    private final String activeOwnParameterScopeFunctionName;
    private final JSBytecodeFunction callerBytecodeFunction;
    private final StackFrame callerFrame;
    private final Map<String, Integer> capturedVarOverlaySlots = new LinkedHashMap<>();
    private final Set<String> constantOverlayBindingNames = new HashSet<>();
    private final int functionNameLocalIndex;
    private final JSObject global;
    private final Set<String> inheritedParameterScopeFunctionNames;
    private final String[] localVarNames;
    private final Map<String, Integer> localVarNameToIndex = new HashMap<>();
    final EvalGlobalOverlay overlay;

    EvalCallerBindings(JSObject global, StackFrame frame, JSBytecodeFunction function) {
        this.global = global;
        this.overlay = new EvalGlobalOverlay(global);
        this.callerFrame = frame;
        this.callerBytecodeFunction = function;
        localVarNames = function.getBytecode().getLocalVarNames();
        functionNameLocalIndex = function.getSelfLocalIndex();
        inheritedParameterScopeFunctionNames = function.getInheritedParameterScopeFunctionNames();
        activeOwnParameterScopeFunctionName = function.getActiveParameterScopeFunctionName(frame);
    }

    void bindEnclosingScopes() {
        IdentityHashMap<StackFrame, Boolean> visitedLexicalFrames = new IdentityHashMap<>();
        if (callerBytecodeFunction.isEvalDynamicScopeLookupEnabled()
                && callerBytecodeFunction.getEvalDynamicScopeFrame() != null) {
            StackFrame evalScopeFrame = callerBytecodeFunction.getEvalDynamicScopeFrame();
            while (evalScopeFrame != null && visitedLexicalFrames.put(evalScopeFrame, Boolean.TRUE) == null) {
                if (evalScopeFrame != callerFrame) {
                    overlay.bindFrameLocals(evalScopeFrame, localVarNameToIndex.keySet());
                    overlay.bindDynamicVariables(evalScopeFrame);
                }
                evalScopeFrame = evalScopeFrame.getCaller();
            }
        }
        VarRef[] callerVarRefs = callerBytecodeFunction.getVarRefs();
        if (callerVarRefs != null) {
            int selfCaptureIndex = callerBytecodeFunction.getSelfCaptureIndex();
            for (int captureSlot = 0; captureSlot < callerVarRefs.length; captureSlot++) {
                String capturedVarName = callerBytecodeFunction.getCapturedVarName(captureSlot);
                if (capturedVarName == null || capturedVarName.startsWith("#")) {
                    continue;
                }
                if (localVarNameToIndex.keySet().contains(capturedVarName)) {
                    continue;
                }
                JSValue capturedValue = callerFrame.getVarRef(captureSlot);
                overlay.bind(capturedVarName, capturedValue);
                capturedVarOverlaySlots.put(capturedVarName, captureSlot);
                // Both captures represent immutable function-expression bindings.
                if (inheritedParameterScopeFunctionNames.contains(capturedVarName)
                        || selfCaptureIndex >= 0 && captureSlot == selfCaptureIndex) {
                    overlay.makeImmutable(capturedVarName, capturedValue);
                }
            }
        }
        // ES2024 15.2.5: a default initializer runs in the parameter environment, whose
        // outer environment holds the named function expression's own binding. A `var`,
        // lexical, class or function declaration of that name in the body is a
        // different binding, and it does not exist yet. Both are slots of one flattened
        // frame and the name maps to the body one, so while the frame is still
        // initializing parameters the name has to be pointed back at the function.
        overlay.bindParameterFunction(callerFrame, callerBytecodeFunction);
        overlay.bindDynamicVariables(callerFrame);
        bindWithObjects();
    }

    void bindLocals(JSContext callerContext, String code, boolean shouldOverlayTopLevelLexicals,
            Set<String> constLexicalNames) {
        visitLocals((i, name, value) -> {
            if (shouldOverlayTopLevelLexicals && value instanceof JSSymbol symbolValue
                    && "UninitializedMarker".equals(symbolValue.getDescription())) {
                return;
            }
            if (shouldOverlayTopLevelLexicals && !callerContext.hasGlobalLexDeclaration(name)) {
                boolean localBindingIsUndefined = JSTypeChecking.isUndefinedOrMissing(value);
                boolean evalSourceMentionsBinding = containsIdentifierReference(code, name);
                if (localBindingIsUndefined || !evalSourceMentionsBinding) {
                    return;
                }
            }
            localVarNameToIndex.putIfAbsent(name, i);
            overlay.bind(name, value);
            // Function name bindings are immutable: make the overlay property
            // non-writable so PUT_VAR throws TypeError in strict mode and
            // silently fails in sloppy mode.
            boolean isFunctionNameBinding = i == functionNameLocalIndex;
            boolean isConstLexicalBinding = constLexicalNames != null && constLexicalNames.contains(name);
            if (isFunctionNameBinding || isConstLexicalBinding) {
                overlay.makeImmutable(name, value);
                if (isConstLexicalBinding && !isFunctionNameBinding) {
                    constantOverlayBindingNames.add(name);
                }
            }
        });
    }

    private void bindWithObjects() {
        List<WithObjectCandidate> withObjectCandidates = new ArrayList<>();
        JSValue[] callerLocals = callerFrame.getLocals();
        for (int i = 0; i < localVarNames.length && i < callerLocals.length; i++) {
            String name = localVarNames[i];
            JSValue localValue = callerLocals[i];
            if (name == null || JSTypeChecking.isUndefinedOrMissing(localValue)) {
                continue;
            }
            if (!(localValue instanceof JSObject withObject) || !name.startsWith("#withObject")) {
                continue;
            }
            withObjectCandidates.add(new WithObjectCandidate(parseWithDepth(name), withObject));
        }
        withObjectCandidates.sort(Comparator.comparingInt(WithObjectCandidate::depth));
        for (WithObjectCandidate withObjectCandidate : withObjectCandidates) {
            JSObject withObject = withObjectCandidate.object();
            for (PropertyKey propertyKey : withObject.ownPropertyKeys()) {
                if (!propertyKey.isString()) {
                    continue;
                }
                String keyName = propertyKey.asString();
                overlay.bind(keyName, withObject.get(propertyKey));
            }
        }
    }

    void declareVariables(Set<String> declarations, Set<String> environmentNames, boolean strict) {
        for (String name : declarations) {
            Integer slot = localVarNameToIndex.get(name);
            if (slot != null) {
                if (!strict && environmentNames != null && environmentNames.contains(name)
                        && slot != functionNameLocalIndex) {
                    callerFrame.setDynamicVarBindingAlias(name, slot);
                }
                continue;
            }
            JSValue existing = callerFrame.getDynamicVarBinding(name);
            JSValue initial = existing == null ? JSUndefined.INSTANCE : existing;
            overlay.bind(name, initial);
            if (!strict) {
                callerFrame.setDynamicVarBinding(name, initial);
            }
        }
    }

    boolean hasLocal(String name) {
        return localVarNameToIndex.containsKey(name);
    }

    private JSValue modifiedValue(String name, Set<String> strictDeclarations) {
        if (strictDeclarations != null && strictDeclarations.contains(name)) {
            return null;
        }
        PropertyKey key = PropertyKey.fromString(name);
        return global.has(key) ? global.get(key) : null;
    }

    void push(JSEvalEnvironment environment) {
        constantOverlayBindingNames.remove(activeOwnParameterScopeFunctionName);
        overlay.push(environment, constantOverlayBindingNames);
    }

    private void visitLocals(LocalVisitor visitor) {
        JSValue[] locals = callerFrame.getLocals();
        for (int i = 0; i < localVarNames.length && i < locals.length; i++) {
            String name = localVarNames[i];
            if (name != null && !name.startsWith("#")) {
                visitor.visit(i, name, locals[i]);
            }
        }
    }

    void writeBack(Set<String> strictDeclarations) {
        JSValue[] locals = callerFrame.getLocals();
        visitLocals((slot, name, value) -> {
            // A nearer local shadows an inherited parameter-scope function. Only this frame's own name is immutable
            // here.
            if (slot != functionNameLocalIndex && !name.equals(activeOwnParameterScopeFunctionName)) {
                JSValue binding = modifiedValue(name, strictDeclarations);
                if (binding != null) {
                    locals[slot] = binding;
                }
            }
        });
        int selfCapture = callerBytecodeFunction.getSelfCaptureIndex();
        for (var capture : capturedVarOverlaySlots.entrySet()) {
            String name = capture.getKey();
            int slot = capture.getValue();
            if (slot != selfCapture && !inheritedParameterScopeFunctionNames.contains(name)) {
                JSValue binding = modifiedValue(name, strictDeclarations);
                if (binding != null) {
                    callerFrame.setVarRef(slot, binding);
                }
            }
        }
    }

    private static boolean containsIdentifierReference(String sourceCode, String identifierName) {
        if (sourceCode == null || sourceCode.isEmpty() || identifierName == null || identifierName.isEmpty()) {
            return false;
        }
        int searchIndex = 0;
        while (searchIndex <= sourceCode.length() - identifierName.length()) {
            int matchIndex = sourceCode.indexOf(identifierName, searchIndex);
            if (matchIndex < 0) {
                return false;
            }
            int beforeIndex = matchIndex - 1;
            int afterIndex = matchIndex + identifierName.length();
            boolean hasIdentifierCharBefore = beforeIndex >= 0 && isIdentifierPartAscii(sourceCode.charAt(beforeIndex));
            boolean hasIdentifierCharAfter = afterIndex < sourceCode.length()
                    && isIdentifierPartAscii(sourceCode.charAt(afterIndex));
            if (!hasIdentifierCharBefore && !hasIdentifierCharAfter) {
                return true;
            }
            searchIndex = matchIndex + identifierName.length();
        }
        return false;
    }

    private static boolean isIdentifierPartAscii(char character) {
        if (character >= 'a' && character <= 'z') {
            return true;
        }
        if (character >= 'A' && character <= 'Z') {
            return true;
        }
        if (character >= '0' && character <= '9') {
            return true;
        }
        return character == '_' || character == '$';
    }

    private static int parseWithDepth(String localName) {
        int depth = 0;
        int index = "#withObject".length();
        while (index < localName.length()) {
            char current = localName.charAt(index);
            if (current < '0' || current > '9') {
                return depth;
            }
            depth = depth * 10 + (current - '0');
            index++;
        }
        return depth;
    }

    @FunctionalInterface
    private interface LocalVisitor {
        void visit(int slot, String name, JSValue value);
    }

    private record WithObjectCandidate(int depth, JSObject object) {
    }
}
