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

        // A read that can throw a ReferenceError — of a global, a captured binding or a binding still in its
        // temporal dead zone — reports the identifier's position in a stack trace, as V8 does. A plain local
        // read cannot throw, so it records nothing.
        if (compilerContext.scopeManager.findLocalInScopes(name) == null || compilerContext.tdzLocals.contains(name)) {
            compilerContext.emitter.emitSourcePosition(identifier.getLocation());
        }

        if (compilerContext.withObjectManager.hasActiveWithObject()) {
            emitWithAwareIdentifierLookup(name);
            return;
        }

        emitIdentifierLookupWithoutWith(name);
    }

    void emitCapturedOrGlobalIdentifierLookup(String name, Opcode globalOpcode) {
        Integer capturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(name);
        if (capturedIndex != null) {
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF_CHECK, capturedIndex);
        } else {
            // Not found in local scopes, use global variable
            compilerContext.emitter.emitOpcodeAtom(globalOpcode, name);
        }
    }

    private void emitIdentifierLookupWithoutWith(String name) {

        // Always check local scopes first, even in global scope (for nested blocks/loops)
        // This must happen BEFORE the 'arguments' special handling so that
        // explicit `var arguments` or `let arguments` declarations take precedence.
        // Following QuickJS: arguments is resolved through normal variable lookup first.
        Integer localIndex = compilerContext.scopeManager.findLocalInScopes(name);

        if (localIndex != null) {
            emitLocalRead(name, localIndex);
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

        emitCapturedOrGlobalIdentifierLookup(name, Opcode.GET_VAR);
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
        emitInheritedWithAwareIdentifierLookup(name, compilerContext.withObjectManager.getInheritedBindingNames(), 0,
                false);
        return true;
    }

    private void emitInheritedWithAwareIdentifierLookup(String name, List<String> withBindingNames, int withDepth,
            boolean forCall) {
        if (withDepth >= withBindingNames.size()) {
            emitCapturedOrGlobalIdentifierLookup(name, Opcode.GET_VAR);
            if (forCall) {
                compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            }
            return;
        }

        if (!emitInheritedWithObject(withBindingNames.get(withDepth))) {
            emitInheritedWithAwareIdentifierLookup(name, withBindingNames, withDepth + 1, forCall);
            return;
        }

        emitWithObjectLookup(name, forCall,
                () -> emitInheritedWithAwareIdentifierLookup(name, withBindingNames, withDepth + 1, forCall));
    }

    /**
     * Load a captured with-object, or report that its binding is unavailable.
     */
    boolean emitInheritedWithObject(String bindingName) {
        Integer localIndex = compilerContext.scopeManager.findLocalInScopes(bindingName);
        if (localIndex != null) {
            compilerContext.emitter.emitGetLocal(localIndex);
            return true;
        }
        Integer capturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(bindingName);
        if (capturedIndex != null) {
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF, capturedIndex);
            return true;
        }
        return false;
    }

    /** A local in its temporal dead zone must throw even when read by typeof. */
    void emitLocalRead(String name, int index) {
        Opcode opcode = compilerContext.tdzLocals.contains(name) ? Opcode.GET_LOC_CHECK : Opcode.GET_LOC;
        compilerContext.emitter.emitOpcodeU16(opcode, index);
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
        compilerContext.emitter.emitGetLocal(withObjectLocalIndex);
        emitWithObjectDelete(name,
                () -> emitWithAwareDeleteIdentifier(name, withObjectLocals, withDepth + 1, fallbackToFalse));
    }

    private void emitWithAwareIdentifierLookup(String name) {
        List<Integer> withObjectLocals = compilerContext.withObjectManager.getActiveLocals();
        emitWithAwareIdentifierLookup(name, withObjectLocals, 0, false);
    }

    private void emitWithAwareIdentifierLookup(String name, List<Integer> withObjectLocals, int withDepth,
            boolean forCall) {
        if (withDepth >= withObjectLocals.size()) {
            emitIdentifierLookupWithoutWith(name);
            if (forCall) {
                compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            }
            return;
        }

        int withObjectLocalIndex = withObjectLocals.get(withDepth);
        compilerContext.emitter.emitGetLocal(withObjectLocalIndex);
        emitWithObjectLookup(name, forCall,
                () -> emitWithAwareIdentifierLookup(name, withObjectLocals, withDepth + 1, forCall));
    }

    void emitWithAwareIdentifierLookupForCall(String name) {
        List<Integer> withObjectLocals = compilerContext.withObjectManager.getActiveLocals();
        if (!withObjectLocals.isEmpty()) {
            emitWithAwareIdentifierLookup(name, withObjectLocals, 0, true);
            return;
        }
        if (!compilerContext.withObjectManager.getInheritedBindingNames().isEmpty()) {
            emitInheritedWithAwareIdentifierLookup(name, compilerContext.withObjectManager.getInheritedBindingNames(),
                    0, true);
            return;
        }
        emitIdentifierLookupWithoutWith(name);
        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
    }

    /**
     * Resolve a delete against the with-object already on the stack. Stack: withObject -> boolean. The fallback starts
     * with the with-object removed.
     */
    private void emitWithObjectDelete(String name, Runnable emitFallback) {
        var fallback = WithBindingCompiler.emitHasBinding(compilerContext, name);
        compilerContext.emitter.emitPushConstant(new JSString(name));
        compilerContext.emitter.emitOpcode(Opcode.DELETE);
        int end = compilerContext.emitter.emitJump(Opcode.GOTO);
        fallback.begin(compilerContext.emitter);
        emitFallback.run();
        compilerContext.emitter.patchJumpToHere(end);
    }

    /**
     * Resolve a read or call against the with-object already on the stack. Stack: withObject -> value (read), or value,
     * withObject (call). GetBindingValue must repeat HasProperty after HasBinding and the unscopables check: proxy
     * traps or getters may have removed the binding in between.
     */
    private void emitWithObjectLookup(String name, boolean forCall, Runnable emitFallback) {
        var fallback = WithBindingCompiler.emitHasBinding(compilerContext, name);
        int jumpToMissingBinding = WithBindingCompiler.emitHasPropertyTest(compilerContext.emitter, name);
        emitWithPropertyLookup(name, forCall);
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        compilerContext.emitter.patchJumpToHere(jumpToMissingBinding);
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

        fallback.begin(compilerContext.emitter);
        emitFallback.run();
        compilerContext.emitter.patchJumpToHere(jumpToEnd);
        compilerContext.emitter.patchJumpToHere(jumpToEndFromMissing);
    }

    private void emitWithPropertyLookup(String name, boolean forCall) {
        compilerContext.emitter.emitOpcodeAtom(forCall ? Opcode.GET_FIELD2 : Opcode.GET_FIELD, name);
        if (forCall) {
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
        }
    }
}
