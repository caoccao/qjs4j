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

import com.caoccao.qjs4j.core.JSArguments;
import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.List;

/**
 * Handles compilation of identifier expressions and with-scope-aware
 * identifier resolution for reads, calls, and deletes.
 */
final class IdentifierCompiler {
    private final CompilerContext compilerContext;

    IdentifierCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compileIdentifier(String name) {
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
            compilerContext.emitter.emitU8(0);  // Type 0 = arguments object
            return;
        }

        if (emitInheritedWithAwareIdentifierLookup(name)) {
            return;
        }

        emitCapturedOrGlobalIdentifierLookup(name);
    }

    // --- With-aware identifier lookup (read) ---

    void emitInheritedWithAwareDeleteIdentifier(String name, List<String> withBindingNames, int withDepth) {
        if (withDepth >= withBindingNames.size()) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.DELETE_VAR, name);
            return;
        }

        String withBindingName = withBindingNames.get(withDepth);
        Integer withLocalIndex = compilerContext.scopeManager.findLocalInScopes(withBindingName);
        if (withLocalIndex != null) {
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, withLocalIndex);
        } else {
            Integer withCapturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(withBindingName);
            if (withCapturedIndex != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF, withCapturedIndex);
            } else {
                emitInheritedWithAwareDeleteIdentifier(name, withBindingNames, withDepth + 1);
                return;
            }
        }

        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.ROT3L);
        compilerContext.emitter.emitOpcode(Opcode.IN);
        int jumpToFallback = compilerContext.emitter.emitJump(Opcode.IF_FALSE);

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
        emitInheritedWithAwareDeleteIdentifier(name, withBindingNames, withDepth + 1);
        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndWithoutUnscopables, compilerContext.emitter.currentOffset());
    }

    private boolean emitInheritedWithAwareIdentifierLookup(String name) {
        if (compilerContext.withObjectManager.getInheritedBindingNames().isEmpty()) {
            return false;
        }
        emitInheritedWithAwareIdentifierLookup(name, compilerContext.withObjectManager.getInheritedBindingNames(), 0);
        return true;
    }

    // --- Inherited with-aware identifier lookup (read) ---

    private void emitInheritedWithAwareIdentifierLookup(String name, List<String> withBindingNames, int withDepth) {
        if (withDepth >= withBindingNames.size()) {
            emitCapturedOrGlobalIdentifierLookup(name);
            return;
        }

        String withBindingName = withBindingNames.get(withDepth);
        Integer withLocalIndex = compilerContext.scopeManager.findLocalInScopes(withBindingName);
        if (withLocalIndex != null) {
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, withLocalIndex);
        } else {
            Integer withCapturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(withBindingName);
            if (withCapturedIndex != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF, withCapturedIndex);
            } else {
                emitInheritedWithAwareIdentifierLookup(name, withBindingNames, withDepth + 1);
                return;
            }
        }

        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.ROT3L);
        compilerContext.emitter.emitOpcode(Opcode.IN);

        int jumpToFallback = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSSymbol.UNSCOPABLES);
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int[] jumpToResolveWithoutUnscopables = emitWithUnscopablesSkipJumps();
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int jumpToFallbackWhenBlocked = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        int jumpToMissingAfterSecondHas = emitWithHasPropertyAndJumpIfMissing(name);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, name);
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        int resolveWithoutUnscopablesOffset = compilerContext.emitter.currentOffset();
        for (int jumpOffset : jumpToResolveWithoutUnscopables) {
            compilerContext.emitter.patchJump(jumpOffset, resolveWithoutUnscopablesOffset);
        }
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        int jumpToMissingWithoutUnscopablesAfterSecondHas = emitWithHasPropertyAndJumpIfMissing(name);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, name);
        int jumpToEndWithoutUnscopables = compilerContext.emitter.emitJump(Opcode.GOTO);

        int missingAfterSecondHasOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToMissingAfterSecondHas, missingAfterSecondHasOffset);
        compilerContext.emitter.patchJump(
                jumpToMissingWithoutUnscopablesAfterSecondHas,
                missingAfterSecondHasOffset);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        if (compilerContext.strictMode) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, name + " is not defined");
            compilerContext.emitter.emitU8(5);
        } else {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        }
        int jumpToEndFromMissing = compilerContext.emitter.emitJump(Opcode.GOTO);

        int fallbackOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToFallback, fallbackOffset);
        compilerContext.emitter.patchJump(jumpToFallbackWhenBlocked, fallbackOffset);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        emitInheritedWithAwareIdentifierLookup(name, withBindingNames, withDepth + 1);
        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndWithoutUnscopables, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndFromMissing, compilerContext.emitter.currentOffset());
    }

    private void emitInheritedWithAwareIdentifierLookupForCall(String name, List<String> withBindingNames, int withDepth) {
        if (withDepth >= withBindingNames.size()) {
            emitCapturedOrGlobalIdentifierLookup(name);
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            return;
        }

        String withBindingName = withBindingNames.get(withDepth);
        Integer withLocalIndex = compilerContext.scopeManager.findLocalInScopes(withBindingName);
        if (withLocalIndex != null) {
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, withLocalIndex);
        } else {
            Integer withCapturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(withBindingName);
            if (withCapturedIndex != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF, withCapturedIndex);
            } else {
                emitInheritedWithAwareIdentifierLookupForCall(name, withBindingNames, withDepth + 1);
                return;
            }
        }

        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.ROT3L);
        compilerContext.emitter.emitOpcode(Opcode.IN);

        int jumpToFallback = compilerContext.emitter.emitJump(Opcode.IF_FALSE);

        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSSymbol.UNSCOPABLES);
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int[] jumpToResolveWithoutUnscopables = emitWithUnscopablesSkipJumps();
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int jumpToFallbackWhenBlocked = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        int jumpToMissingAfterSecondHas = emitWithHasPropertyAndJumpIfMissing(name);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, name);
        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        int resolveWithoutUnscopablesOffset = compilerContext.emitter.currentOffset();
        for (int jumpOffset : jumpToResolveWithoutUnscopables) {
            compilerContext.emitter.patchJump(jumpOffset, resolveWithoutUnscopablesOffset);
        }
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        int jumpToMissingWithoutUnscopablesAfterSecondHas = emitWithHasPropertyAndJumpIfMissing(name);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, name);
        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        int jumpToEndWithoutUnscopables = compilerContext.emitter.emitJump(Opcode.GOTO);

        int missingAfterSecondHasOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToMissingAfterSecondHas, missingAfterSecondHasOffset);
        compilerContext.emitter.patchJump(
                jumpToMissingWithoutUnscopablesAfterSecondHas,
                missingAfterSecondHasOffset);
        if (compilerContext.strictMode) {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, name + " is not defined");
            compilerContext.emitter.emitU8(5);
        } else {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
        }
        int jumpToEndFromMissing = compilerContext.emitter.emitJump(Opcode.GOTO);

        int fallbackOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToFallback, fallbackOffset);
        compilerContext.emitter.patchJump(jumpToFallbackWhenBlocked, fallbackOffset);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        emitInheritedWithAwareIdentifierLookupForCall(name, withBindingNames, withDepth + 1);
        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndWithoutUnscopables, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndFromMissing, compilerContext.emitter.currentOffset());
    }

    // --- With-aware identifier lookup for call expressions ---

    void emitWithAwareDeleteIdentifier(String name, List<Integer> withObjectLocals, int withDepth) {
        if (withDepth >= withObjectLocals.size()) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.DELETE_VAR, name);
            return;
        }

        int withObjectLocalIndex = withObjectLocals.get(withDepth);
        // Load with-object and check if it has the property
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, withObjectLocalIndex);
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.ROT3L);
        compilerContext.emitter.emitOpcode(Opcode.IN);

        int jumpToFallback = compilerContext.emitter.emitJump(Opcode.IF_FALSE);

        // Check @@unscopables
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSSymbol.UNSCOPABLES);
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int[] jumpToDeleteWithoutUnscopables = emitWithUnscopablesSkipJumps();
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int jumpToFallbackWhenBlocked = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
        // Property found and not blocked by unscopables: delete from with-object
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.DELETE);
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        // No unscopables object: delete from with-object
        int deleteWithoutUnscopablesOffset = compilerContext.emitter.currentOffset();
        for (int jumpOffset : jumpToDeleteWithoutUnscopables) {
            compilerContext.emitter.patchJump(jumpOffset, deleteWithoutUnscopablesOffset);
        }
        compilerContext.emitter.emitOpcode(Opcode.DROP); // drop undefined unscopables result
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.DELETE);
        int jumpToEndWithoutUnscopables = compilerContext.emitter.emitJump(Opcode.GOTO);

        // Property not in with-object or blocked by unscopables: fall through
        int fallbackOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToFallback, fallbackOffset);
        compilerContext.emitter.patchJump(jumpToFallbackWhenBlocked, fallbackOffset);
        compilerContext.emitter.emitOpcode(Opcode.DROP); // drop with-object
        emitWithAwareDeleteIdentifier(name, withObjectLocals, withDepth + 1);
        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndWithoutUnscopables, compilerContext.emitter.currentOffset());
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
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.ROT3L);
        compilerContext.emitter.emitOpcode(Opcode.IN);

        int jumpToFallback = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSSymbol.UNSCOPABLES);
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int[] jumpToResolveWithoutUnscopables = emitWithUnscopablesSkipJumps();
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int jumpToFallbackWhenBlocked = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        int jumpToMissingAfterSecondHas = emitWithHasPropertyAndJumpIfMissing(name);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, name);
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        int resolveWithoutUnscopablesOffset = compilerContext.emitter.currentOffset();
        for (int jumpOffset : jumpToResolveWithoutUnscopables) {
            compilerContext.emitter.patchJump(jumpOffset, resolveWithoutUnscopablesOffset);
        }
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        int jumpToMissingWithoutUnscopablesAfterSecondHas = emitWithHasPropertyAndJumpIfMissing(name);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, name);
        int jumpToEndWithoutUnscopables = compilerContext.emitter.emitJump(Opcode.GOTO);

        int missingAfterSecondHasOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToMissingAfterSecondHas, missingAfterSecondHasOffset);
        compilerContext.emitter.patchJump(
                jumpToMissingWithoutUnscopablesAfterSecondHas,
                missingAfterSecondHasOffset);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        if (compilerContext.strictMode) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, name + " is not defined");
            compilerContext.emitter.emitU8(5);
        } else {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        }
        int jumpToEndFromMissing = compilerContext.emitter.emitJump(Opcode.GOTO);

        int fallbackOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToFallback, fallbackOffset);
        compilerContext.emitter.patchJump(jumpToFallbackWhenBlocked, fallbackOffset);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        emitWithAwareIdentifierLookup(name, withObjectLocals, withDepth + 1);
        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndWithoutUnscopables, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndFromMissing, compilerContext.emitter.currentOffset());
    }

    // --- With-aware delete ---

    void emitWithAwareIdentifierLookupForCall(String name) {
        List<Integer> withObjectLocals = compilerContext.withObjectManager.getActiveLocals();
        if (!withObjectLocals.isEmpty()) {
            emitWithAwareIdentifierLookupForCall(name, withObjectLocals, 0);
            return;
        }
        if (!compilerContext.withObjectManager.getInheritedBindingNames().isEmpty()) {
            emitInheritedWithAwareIdentifierLookupForCall(name, compilerContext.withObjectManager.getInheritedBindingNames(), 0);
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
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.ROT3L);
        compilerContext.emitter.emitOpcode(Opcode.IN);

        int jumpToFallback = compilerContext.emitter.emitJump(Opcode.IF_FALSE);

        // Check @@unscopables
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSSymbol.UNSCOPABLES);
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int[] jumpToResolveWithoutUnscopables = emitWithUnscopablesSkipJumps();
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        int jumpToFallbackWhenBlocked = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        // Found and not blocked: GET_FIELD2 keeps withObj, SWAP → [value, withObj]
        int jumpToMissingAfterSecondHas = emitWithHasPropertyAndJumpIfMissing(name);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, name);
        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        // Found without unscopables check:
        int resolveWithoutUnscopablesOffset = compilerContext.emitter.currentOffset();
        for (int jumpOffset : jumpToResolveWithoutUnscopables) {
            compilerContext.emitter.patchJump(jumpOffset, resolveWithoutUnscopablesOffset);
        }
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        int jumpToMissingWithoutUnscopablesAfterSecondHas = emitWithHasPropertyAndJumpIfMissing(name);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, name);
        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        int jumpToEndWithoutUnscopables = compilerContext.emitter.emitJump(Opcode.GOTO);

        int missingAfterSecondHasOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToMissingAfterSecondHas, missingAfterSecondHasOffset);
        compilerContext.emitter.patchJump(
                jumpToMissingWithoutUnscopablesAfterSecondHas,
                missingAfterSecondHasOffset);
        if (compilerContext.strictMode) {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, name + " is not defined");
            compilerContext.emitter.emitU8(5);
        } else {
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
        }
        int jumpToEndFromMissing = compilerContext.emitter.emitJump(Opcode.GOTO);

        // Not found / blocked: fallback to next with scope or global
        int fallbackOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToFallback, fallbackOffset);
        compilerContext.emitter.patchJump(jumpToFallbackWhenBlocked, fallbackOffset);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        emitWithAwareIdentifierLookupForCall(name, withObjectLocals, withDepth + 1);
        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndWithoutUnscopables, compilerContext.emitter.currentOffset());
        compilerContext.emitter.patchJump(jumpToEndFromMissing, compilerContext.emitter.currentOffset());
    }

    // --- Shared helpers ---

    private int emitWithHasPropertyAndJumpIfMissing(String name) {
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.ROT3L);
        compilerContext.emitter.emitOpcode(Opcode.IN);
        return compilerContext.emitter.emitJump(Opcode.IF_FALSE);
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
