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

import com.caoccao.qjs4j.compilation.ast.Identifier;
import com.caoccao.qjs4j.core.JSArguments;
import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.List;

/**
 * Handles compilation of identifier expressions and with-scope-aware identifier resolution for reads, calls, and
 * deletes.
 */
final class IdentifierCompiler extends AstNodeCompiler<Identifier> {
    IdentifierCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(Identifier identifier) {
        String name = identifier.getName();
        // Handle 'this' keyword
        if (JSKeyword.THIS.equals(name)) {
            compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
            return;
        }

        // Handle 'new.target' meta-property
        if ("new.target".equals(name)) {
            if (compilerContext.classFieldEvalContext || compilerContext.inClassFieldInitializer) {
                // ES2024: eval in class field initializer treats new.target as undefined
                compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            } else {
                compilerContext.emitter.emitOpcodeU8(Opcode.SPECIAL_OBJECT, 3);
            }
            return;
        }

        // Handle 'import.meta' meta-property
        if ("import.meta".equals(name)) {
            compilerContext.emitter.emitOpcodeU8(Opcode.SPECIAL_OBJECT, 6);
            return;
        }

        if (compilerContext.withObjectManager.hasActiveWithObject()) {
            emitWithAwareIdentifierLookup(name);
            return;
        }

        emitIdentifierLookupWithoutWith(name);
    }

    private void emitCapturedOrGlobalIdentifierLookup(String name) {
        Integer capturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(name);
        if (capturedIndex != null) {
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF_CHECK, capturedIndex);
        } else {
            // Not found in local scopes, use global variable
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_VAR, name);
        }
    }

    private void emitIdentifierLookupWithoutWith(String name) {

        // Always check local scopes first, even in global scope (for nested blocks/loops)
        // This must happen BEFORE the 'arguments' special handling so that
        // explicit `var arguments` or `let arguments` declarations take precedence.
        // Following QuickJS: arguments is resolved through normal variable lookup first.
        Integer localIndex = compilerContext.scopeManager.findLocalInScopes(name);

        if (localIndex != null) {
            // Use GET_LOC_CHECK for TDZ locals (let/const/class in program scope)
            // to throw ReferenceError if accessed before initialization
            if (compilerContext.tdzLocals.contains(name)) {
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC_CHECK, localIndex);
            } else {
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, localIndex);
            }
            return;
        }

        // Handle 'arguments' keyword in function scope (only if not found as a local)
        // For regular functions: SPECIAL_OBJECT creates the arguments object
        // For arrow functions with enclosing regular function: SPECIAL_OBJECT walks up call stack
        // For arrow functions without enclosing regular function: resolve as normal variable
        // Following QuickJS: arrow functions inherit arguments from enclosing scope,
        // but only if there is an enclosing scope with arguments binding
        if (JSArguments.NAME.equals(name) && compilerContext.hasEnclosingArgumentsBinding) {
            // Emit SPECIAL_OBJECT opcode with type 0 (SPECIAL_OBJECT_ARGUMENTS)
            // The VM will handle differently for arrow vs regular functions
            compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            compilerContext.emitter.emitU8(0); // Type 0 = arguments object
            return;
        }

        if (emitInheritedWithAwareIdentifierLookup(name)) {
            return;
        }

        emitCapturedOrGlobalIdentifierLookup(name);
    }

    void emitInheritedWithAwareDeleteIdentifier(String name, List<String> withBindingNames, int withDepth) {
        emitInheritedWithAwareDeleteIdentifier(name, withBindingNames, withDepth, false);
    }

    void emitInheritedWithAwareDeleteIdentifier(String name, List<String> withBindingNames, int withDepth,
            boolean fallbackToFalse) {
        if (withDepth >= withBindingNames.size()) {
            if (fallbackToFalse) {
                compilerContext.emitter.emitOpcode(Opcode.PUSH_FALSE);
            } else {
                compilerContext.emitter.emitOpcodeAtom(Opcode.DELETE_VAR, name);
            }
            return;
        }

        if (!emitInheritedWithObject(withBindingNames.get(withDepth))) {
            emitInheritedWithAwareDeleteIdentifier(name, withBindingNames, withDepth + 1, fallbackToFalse);
            return;
        }

        emitWithObjectDelete(name,
                () -> emitInheritedWithAwareDeleteIdentifier(name, withBindingNames, withDepth + 1, fallbackToFalse));
    }

    private boolean emitInheritedWithAwareIdentifierLookup(String name) {
        if (compilerContext.withObjectManager.getInheritedBindingNames().isEmpty()) {
            return false;
        }
        emitInheritedWithAwareIdentifierLookup(name, compilerContext.withObjectManager.getInheritedBindingNames(), 0);
        return true;
    }

    private void emitInheritedWithAwareIdentifierLookup(String name, List<String> withBindingNames, int withDepth) {
        if (withDepth >= withBindingNames.size()) {
            emitCapturedOrGlobalIdentifierLookup(name);
            return;
        }

        if (!emitInheritedWithObject(withBindingNames.get(withDepth))) {
            emitInheritedWithAwareIdentifierLookup(name, withBindingNames, withDepth + 1);
            return;
        }

        emitWithObjectLookup(name, false,
                () -> emitInheritedWithAwareIdentifierLookup(name, withBindingNames, withDepth + 1));
    }

    private void emitInheritedWithAwareIdentifierLookupForCall(String name, List<String> withBindingNames,
            int withDepth) {
        if (withDepth >= withBindingNames.size()) {
            emitCapturedOrGlobalIdentifierLookup(name);
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            return;
        }

        if (!emitInheritedWithObject(withBindingNames.get(withDepth))) {
            emitInheritedWithAwareIdentifierLookupForCall(name, withBindingNames, withDepth + 1);
            return;
        }

        emitWithObjectLookup(name, true,
                () -> emitInheritedWithAwareIdentifierLookupForCall(name, withBindingNames, withDepth + 1));
    }

    /**
     * Load a captured with-object, or report that its binding is unavailable.
     */
    private boolean emitInheritedWithObject(String bindingName) {
        Integer localIndex = compilerContext.scopeManager.findLocalInScopes(bindingName);
        if (localIndex != null) {
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, localIndex);
            return true;
        }
        Integer capturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(bindingName);
        if (capturedIndex != null) {
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF, capturedIndex);
            return true;
        }
        return false;
    }

    void emitWithAwareDeleteIdentifier(String name, List<Integer> withObjectLocals, int withDepth) {
        emitWithAwareDeleteIdentifier(name, withObjectLocals, withDepth, false);
    }

    void emitWithAwareDeleteIdentifier(String name, List<Integer> withObjectLocals, int withDepth,
            boolean fallbackToFalse) {
        if (withDepth >= withObjectLocals.size()) {
            if (fallbackToFalse) {
                compilerContext.emitter.emitOpcode(Opcode.PUSH_FALSE);
            } else {
                compilerContext.emitter.emitOpcodeAtom(Opcode.DELETE_VAR, name);
            }
            return;
        }

        int withObjectLocalIndex = withObjectLocals.get(withDepth);
        // Load with-object and check if it has the property
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, withObjectLocalIndex);
        emitWithObjectDelete(name,
                () -> emitWithAwareDeleteIdentifier(name, withObjectLocals, withDepth + 1, fallbackToFalse));
    }

    private void emitWithAwareIdentifierLookup(String name) {
        List<Integer> withObjectLocals = compilerContext.withObjectManager.getActiveLocals();
        emitWithAwareIdentifierLookup(name, withObjectLocals, 0);
    }

    private void emitWithAwareIdentifierLookup(String name, List<Integer> withObjectLocals, int withDepth) {
        if (withDepth >= withObjectLocals.size()) {
            emitIdentifierLookupWithoutWith(name);
            return;
        }

        int withObjectLocalIndex = withObjectLocals.get(withDepth);
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, withObjectLocalIndex);
        emitWithObjectLookup(name, false, () -> emitWithAwareIdentifierLookup(name, withObjectLocals, withDepth + 1));
    }

    void emitWithAwareIdentifierLookupForCall(String name) {
        List<Integer> withObjectLocals = compilerContext.withObjectManager.getActiveLocals();
        if (!withObjectLocals.isEmpty()) {
            emitWithAwareIdentifierLookupForCall(name, withObjectLocals, 0);
            return;
        }
        if (!compilerContext.withObjectManager.getInheritedBindingNames().isEmpty()) {
            emitInheritedWithAwareIdentifierLookupForCall(name,
                    compilerContext.withObjectManager.getInheritedBindingNames(), 0);
            return;
        }
        emitIdentifierLookupWithoutWith(name);
        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
    }

    private void emitWithAwareIdentifierLookupForCall(String name, List<Integer> withObjectLocals, int withDepth) {
        if (withDepth >= withObjectLocals.size()) {
            emitIdentifierLookupWithoutWith(name);
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            return;
        }

        int withObjectLocalIndex = withObjectLocals.get(withDepth);
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, withObjectLocalIndex);
        emitWithObjectLookup(name, true,
                () -> emitWithAwareIdentifierLookupForCall(name, withObjectLocals, withDepth + 1));
    }

    private int emitWithHasPropertyAndJumpIfMissing(String name) {
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.ROT3L);
        compilerContext.emitter.emitOpcode(Opcode.IN);
        return compilerContext.emitter.emitJump(Opcode.IF_FALSE);
    }

    /**
     * Resolve a delete against the with-object already on the stack. Stack: withObject -> boolean. The fallback starts
     * with the with-object removed.
     */
    private void emitWithObjectDelete(String name, Runnable emitFallback) {
        int jumpToFallback = emitWithHasPropertyAndJumpIfMissing(name);

        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSSymbol.UNSCOPABLES);
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int[] jumpToDeleteWithoutUnscopables = emitWithUnscopablesSkipJumps();
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int jumpToFallbackWhenBlocked = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.DELETE);
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        int deleteWithoutUnscopablesOffset = compilerContext.emitter.currentOffset();
        for (int jumpOffset : jumpToDeleteWithoutUnscopables) {
            compilerContext.emitter.patchJump(jumpOffset, deleteWithoutUnscopablesOffset);
        }
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.DELETE);
        int jumpToEndWithoutUnscopables = compilerContext.emitter.emitJump(Opcode.GOTO);

        int fallbackOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToFallback, fallbackOffset);
        compilerContext.emitter.patchJump(jumpToFallbackWhenBlocked, fallbackOffset);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        emitFallback.run();
        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndWithoutUnscopables, compilerContext.emitter.currentOffset());
    }

    /**
     * Resolve a read or call against the with-object already on the stack. Stack: withObject -> value (read), or value,
     * withObject (call). GetBindingValue must repeat HasProperty after HasBinding and the unscopables check: proxy
     * traps or getters may have removed the binding in between.
     */
    private void emitWithObjectLookup(String name, boolean forCall, Runnable emitFallback) {
        int jumpToFallback = emitWithHasPropertyAndJumpIfMissing(name);
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSSymbol.UNSCOPABLES);
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int[] jumpToResolveWithoutUnscopables = emitWithUnscopablesSkipJumps();
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int jumpToFallbackWhenBlocked = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        int jumpToGetBindingValue = compilerContext.emitter.emitJump(Opcode.GOTO);

        int resolveWithoutUnscopablesOffset = compilerContext.emitter.currentOffset();
        for (int jumpOffset : jumpToResolveWithoutUnscopables) {
            compilerContext.emitter.patchJump(jumpOffset, resolveWithoutUnscopablesOffset);
        }
        compilerContext.emitter.emitOpcode(Opcode.DROP);

        compilerContext.emitter.patchJump(jumpToGetBindingValue, compilerContext.emitter.currentOffset());
        int jumpToMissingBinding = emitWithHasPropertyAndJumpIfMissing(name);
        emitWithPropertyLookup(name, forCall);
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        compilerContext.emitter.patchJump(jumpToMissingBinding, compilerContext.emitter.currentOffset());
        if (forCall && !compilerContext.strictMode) {
            // Stack: withObject -> undefined, withObject (preserve the call receiver).
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
        } else {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            if (compilerContext.strictMode) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, name + " is not defined");
                compilerContext.emitter.emitU8(5);
            } else {
                compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            }
        }
        int jumpToEndFromMissing = compilerContext.emitter.emitJump(Opcode.GOTO);

        int fallbackOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToFallback, fallbackOffset);
        compilerContext.emitter.patchJump(jumpToFallbackWhenBlocked, fallbackOffset);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        emitFallback.run();
        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndFromMissing, compilerContext.emitter.currentOffset());
    }

    private void emitWithPropertyLookup(String name, boolean forCall) {
        compilerContext.emitter.emitOpcodeAtom(forCall ? Opcode.GET_FIELD2 : Opcode.GET_FIELD, name);
        if (forCall) {
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
        }
    }

    private int[] emitWithUnscopablesSkipJumps() {
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int jumpToResolveWithoutUnscopablesOnNullish = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.TYPEOF);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("object"));
        compilerContext.emitter.emitOpcode(Opcode.STRICT_EQ);
        int jumpToCheckBlockedWhenObject = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.TYPEOF);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("function"));
        compilerContext.emitter.emitOpcode(Opcode.STRICT_EQ);
        int jumpToResolveWithoutUnscopablesOnPrimitive = compilerContext.emitter.emitJump(Opcode.IF_FALSE);

        compilerContext.emitter.patchJump(jumpToCheckBlockedWhenObject, compilerContext.emitter.currentOffset());
        return new int[]{jumpToResolveWithoutUnscopablesOnNullish, jumpToResolveWithoutUnscopablesOnPrimitive};
    }
}
