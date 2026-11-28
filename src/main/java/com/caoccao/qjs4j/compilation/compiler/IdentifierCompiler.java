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
final class IdentifierCompiler {
    private IdentifierCompiler() {
    }

    static void compile(CompilerContext compilerContext, Identifier identifier) {
        BytecodeEmitter emitter = compilerContext.emitter;
        String name = identifier.getName();
        // Handle 'this' keyword
        if (JSKeyword.THIS.equals(name)) {
            emitter.emitOpcode(Opcode.PUSH_THIS);
            return;
        }

        // Handle 'new.target' meta-property
        if ("new.target".equals(name)) {
            if (compilerContext.classFieldEvalContext || compilerContext.inClassFieldInitializer) {
                // ES2024: eval in class field initializer treats new.target as undefined
                emitter.emitOpcode(Opcode.UNDEFINED);
            } else {
                emitter.emitIntOperand(Opcode.SPECIAL_OBJECT, 3);
            }
            return;
        }

        // Handle 'import.meta' meta-property
        if ("import.meta".equals(name)) {
            emitter.emitIntOperand(Opcode.SPECIAL_OBJECT, 6);
            return;
        }

        // A read that can throw a ReferenceError — of a global, a captured binding or a binding still in its
        // temporal dead zone — reports the identifier's position in a stack trace, as V8 does. A plain local
        // read cannot throw, so it records nothing.
        if (compilerContext.scopeManager.findLocalInScopes(name) == null || compilerContext.tdzLocals.contains(name)) {
            emitter.emitSourcePosition(identifier.getLocation());
        }

        if (compilerContext.withObjectManager.hasActiveWithObject()) {
            emitWithAwareIdentifierLookup(compilerContext, name);
            return;
        }

        emitIdentifierLookupWithoutWith(compilerContext, name);
    }

    static void emitCapturedOrGlobalIdentifierLookup(CompilerContext compilerContext, String name,
            Opcode globalOpcode) {
        BytecodeEmitter emitter = compilerContext.emitter;
        Integer capturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(name);
        if (capturedIndex != null) {
            emitter.emitIntOperand(Opcode.GET_VAR_REF_CHECK, capturedIndex);
        } else {
            // Not found in local scopes, use global variable
            emitter.emitOpcodeAtom(globalOpcode, name);
        }
    }

    private static void emitIdentifierLookupWithoutWith(CompilerContext compilerContext, String name) {

        // Always check local scopes first, even in global scope (for nested blocks/loops)
        // This must happen BEFORE the 'arguments' special handling so that
        // explicit `var arguments` or `let arguments` declarations take precedence.
        // Following QuickJS: arguments is resolved through normal variable lookup first.
        Integer localIndex = compilerContext.scopeManager.findLocalInScopes(name);

        if (localIndex != null) {
            emitLocalRead(compilerContext, name, localIndex);
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
            compilerContext.emitter.emitIntOperand(Opcode.SPECIAL_OBJECT, 0); // Type 0 = arguments object
            return;
        }

        if (emitInheritedWithAwareIdentifierLookup(compilerContext, name)) {
            return;
        }

        emitCapturedOrGlobalIdentifierLookup(compilerContext, name, Opcode.GET_VAR);
    }

    static void emitInheritedWithAwareDeleteIdentifier(CompilerContext compilerContext, String name,
            List<String> withBindingNames, int withDepth) {
        emitInheritedWithAwareDeleteIdentifier(compilerContext, name, withBindingNames, withDepth, false);
    }

    static void emitInheritedWithAwareDeleteIdentifier(CompilerContext compilerContext, String name,
            List<String> withBindingNames, int withDepth, boolean fallbackToFalse) {
        BytecodeEmitter emitter = compilerContext.emitter;
        if (withDepth >= withBindingNames.size()) {
            if (fallbackToFalse) {
                emitter.emitOpcode(Opcode.PUSH_FALSE);
            } else {
                emitter.emitOpcodeAtom(Opcode.DELETE_VAR, name);
            }
            return;
        }

        if (!emitInheritedWithObject(compilerContext, withBindingNames.get(withDepth))) {
            emitInheritedWithAwareDeleteIdentifier(compilerContext, name, withBindingNames, withDepth + 1,
                    fallbackToFalse);
            return;
        }

        emitWithObjectDelete(compilerContext, name, () -> emitInheritedWithAwareDeleteIdentifier(compilerContext, name,
                withBindingNames, withDepth + 1, fallbackToFalse));
    }

    private static boolean emitInheritedWithAwareIdentifierLookup(CompilerContext compilerContext, String name) {
        if (compilerContext.withObjectManager.getInheritedBindingNames().isEmpty()) {
            return false;
        }
        emitInheritedWithAwareIdentifierLookup(compilerContext, name,
                compilerContext.withObjectManager.getInheritedBindingNames(), 0, false);
        return true;
    }

    private static void emitInheritedWithAwareIdentifierLookup(CompilerContext compilerContext, String name,
            List<String> withBindingNames, int withDepth, boolean forCall) {
        if (withDepth >= withBindingNames.size()) {
            emitCapturedOrGlobalIdentifierLookup(compilerContext, name, Opcode.GET_VAR);
            if (forCall) {
                compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            }
            return;
        }

        if (!emitInheritedWithObject(compilerContext, withBindingNames.get(withDepth))) {
            emitInheritedWithAwareIdentifierLookup(compilerContext, name, withBindingNames, withDepth + 1, forCall);
            return;
        }

        emitWithObjectLookup(compilerContext, name, forCall,
                () -> emitInheritedWithAwareIdentifierLookup(compilerContext, name, withBindingNames, withDepth + 1,
                        forCall));
    }

    /**
     * Load a captured with-object, or report that its binding is unavailable.
     */
    static boolean emitInheritedWithObject(CompilerContext compilerContext, String bindingName) {
        BytecodeEmitter emitter = compilerContext.emitter;
        Integer localIndex = compilerContext.scopeManager.findLocalInScopes(bindingName);
        if (localIndex != null) {
            emitter.emitGetLocal(localIndex);
            return true;
        }
        Integer capturedIndex = compilerContext.captureResolver.resolveCapturedBindingIndex(bindingName);
        if (capturedIndex != null) {
            emitter.emitIntOperand(Opcode.GET_VAR_REF, capturedIndex);
            return true;
        }
        return false;
    }

    /** A local in its temporal dead zone must throw even when read by typeof. */
    static void emitLocalRead(CompilerContext compilerContext, String name, int index) {
        Opcode opcode = compilerContext.tdzLocals.contains(name) ? Opcode.GET_LOC_CHECK : Opcode.GET_LOC;
        compilerContext.emitter.emitOpcodeU16(opcode, index);
    }

    static void emitWithAwareDeleteIdentifier(CompilerContext compilerContext, String name,
            List<Integer> withObjectLocals, int withDepth) {
        emitWithAwareDeleteIdentifier(compilerContext, name, withObjectLocals, withDepth, false);
    }

    static void emitWithAwareDeleteIdentifier(CompilerContext compilerContext, String name,
            List<Integer> withObjectLocals, int withDepth, boolean fallbackToFalse) {
        BytecodeEmitter emitter = compilerContext.emitter;
        if (withDepth >= withObjectLocals.size()) {
            if (fallbackToFalse) {
                emitter.emitOpcode(Opcode.PUSH_FALSE);
            } else {
                emitter.emitOpcodeAtom(Opcode.DELETE_VAR, name);
            }
            return;
        }

        int withObjectLocalIndex = withObjectLocals.get(withDepth);
        // Load with-object and check if it has the property
        emitter.emitGetLocal(withObjectLocalIndex);
        emitWithObjectDelete(compilerContext, name, () -> emitWithAwareDeleteIdentifier(compilerContext, name,
                withObjectLocals, withDepth + 1, fallbackToFalse));
    }

    private static void emitWithAwareIdentifierLookup(CompilerContext compilerContext, String name) {
        List<Integer> withObjectLocals = compilerContext.withObjectManager.getActiveLocals();
        emitWithAwareIdentifierLookup(compilerContext, name, withObjectLocals, 0, false);
    }

    private static void emitWithAwareIdentifierLookup(CompilerContext compilerContext, String name,
            List<Integer> withObjectLocals, int withDepth, boolean forCall) {
        BytecodeEmitter emitter = compilerContext.emitter;
        if (withDepth >= withObjectLocals.size()) {
            emitIdentifierLookupWithoutWith(compilerContext, name);
            if (forCall) {
                emitter.emitOpcode(Opcode.UNDEFINED);
            }
            return;
        }

        int withObjectLocalIndex = withObjectLocals.get(withDepth);
        emitter.emitGetLocal(withObjectLocalIndex);
        emitWithObjectLookup(compilerContext, name, forCall,
                () -> emitWithAwareIdentifierLookup(compilerContext, name, withObjectLocals, withDepth + 1, forCall));
    }

    static void emitWithAwareIdentifierLookupForCall(CompilerContext compilerContext, String name) {
        List<Integer> withObjectLocals = compilerContext.withObjectManager.getActiveLocals();
        if (!withObjectLocals.isEmpty()) {
            emitWithAwareIdentifierLookup(compilerContext, name, withObjectLocals, 0, true);
            return;
        }
        if (!compilerContext.withObjectManager.getInheritedBindingNames().isEmpty()) {
            emitInheritedWithAwareIdentifierLookup(compilerContext, name,
                    compilerContext.withObjectManager.getInheritedBindingNames(), 0, true);
            return;
        }
        emitIdentifierLookupWithoutWith(compilerContext, name);
        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
    }

    /**
     * Resolve a delete against the with-object already on the stack. Stack: withObject -> boolean. The fallback starts
     * with the with-object removed.
     */
    private static void emitWithObjectDelete(CompilerContext compilerContext, String name, Runnable emitFallback) {
        BytecodeEmitter emitter = compilerContext.emitter;
        var fallback = WithBindingCompiler.emitHasBinding(compilerContext, name);
        emitter.emitPushConstant(new JSString(name));
        emitter.emitOpcode(Opcode.DELETE);
        int end = emitter.emitJump(Opcode.GOTO);
        fallback.begin(emitter);
        emitFallback.run();
        emitter.patchJumpToHere(end);
    }

    /**
     * Resolve a read or call against the with-object already on the stack. Stack: withObject -> value (read), or value,
     * withObject (call). GetBindingValue must repeat HasProperty after HasBinding and the unscopables check: proxy
     * traps or getters may have removed the binding in between.
     */
    private static void emitWithObjectLookup(CompilerContext compilerContext, String name, boolean forCall,
            Runnable emitFallback) {
        BytecodeEmitter emitter = compilerContext.emitter;
        var fallback = WithBindingCompiler.emitHasBinding(compilerContext, name);
        int jumpToMissingBinding = WithBindingCompiler.emitHasPropertyTest(emitter, name);
        emitWithPropertyLookup(compilerContext, name, forCall);
        int jumpToEnd = emitter.emitJump(Opcode.GOTO);

        emitter.patchJumpToHere(jumpToMissingBinding);
        if (forCall && !compilerContext.strictMode) {
            // Stack: withObject -> undefined, withObject (preserve the call receiver).
            emitter.emitOpcode(Opcode.UNDEFINED);
            emitter.emitOpcode(Opcode.SWAP);
        } else {
            emitter.emitOpcode(Opcode.DROP);
            if (compilerContext.strictMode) {
                emitter.emitOpcodeAtom(Opcode.THROW_ERROR, name + " is not defined");
                emitter.emitU8(5);
            } else {
                emitter.emitOpcode(Opcode.UNDEFINED);
            }
        }
        int jumpToEndFromMissing = emitter.emitJump(Opcode.GOTO);

        fallback.begin(emitter);
        emitFallback.run();
        emitter.patchJumpToHere(jumpToEnd);
        emitter.patchJumpToHere(jumpToEndFromMissing);
    }

    private static void emitWithPropertyLookup(CompilerContext compilerContext, String name, boolean forCall) {
        BytecodeEmitter emitter = compilerContext.emitter;
        emitter.emitOpcodeAtom(forCall ? Opcode.GET_FIELD2 : Opcode.GET_FIELD, name);
        if (forCall) {
            emitter.emitOpcode(Opcode.SWAP);
        }
    }
}
