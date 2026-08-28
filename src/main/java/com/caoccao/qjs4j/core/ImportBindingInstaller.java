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

import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Puts a module's imports and exports where the running code can see them.
 * <p>
 * Two different places, for two different reasons. An <em>import</em> becomes an accessor on the
 * global object for the duration of the module body — a getter that reads through to the exporting
 * module's namespace, so the binding stays live, and a setter that throws, so assigning to it is
 * the {@code TypeError} an immutable binding owes. What each one displaced is recorded in an
 * {@link EvalOverlayManager} frame and put back afterwards. An <em>export</em> becomes a property
 * of this module's own namespace object, either a value or a getter forwarding to whichever module
 * a re-export chain ends at.
 * <p>
 * The overlay is the part that is not really right, and it is documented as such on
 * {@link ModuleSourceTransformer#parseDynamicImportModuleSource}: a binding on the global object is
 * not a module environment record, so it is gone once the body finishes and a closure that outlives
 * the module cannot read it. Real module records are the fix, and this class is where that change
 * would land.
 */
final class ImportBindingInstaller {
    private final JSContext context;
    private final ModuleLinker linker;
    private final ModuleSourceTransformer transformer;

    ImportBindingInstaller(JSContext context, ModuleSourceTransformer transformer, ModuleLinker linker) {
        this.context = context;
        this.transformer = transformer;
        this.linker = linker;
    }

    void applyImportClauseBindings(
            JSObject globalObject,
            Map<String, JSValue> savedGlobals,
            Set<String> absentKeys,
            JSObject namespaceObject,
            String importClause) {
        String clause = importClause.trim();
        if (clause.isEmpty()) {
            return;
        }
        if (clause.startsWith("{")) {
            bindNamedImports(globalObject, savedGlobals, absentKeys, namespaceObject, clause);
            return;
        }

        int commaIndex = clause.indexOf(',');
        if (commaIndex < 0) {
            bindImportOverlayLiveBinding(globalObject, savedGlobals, absentKeys, clause, namespaceObject, "default");
            return;
        }

        String defaultBinding = clause.substring(0, commaIndex).trim();
        if (!defaultBinding.isEmpty()) {
            bindImportOverlayLiveBinding(globalObject, savedGlobals, absentKeys, defaultBinding, namespaceObject, "default");
        }

        String remainder = clause.substring(commaIndex + 1).trim();
        if (remainder.startsWith("*")) {
            String namespaceBinding = remainder.replaceFirst("^\\*\\s*as\\s+", "").trim();
            if (!namespaceBinding.isEmpty()) {
                bindImportOverlayValue(globalObject, savedGlobals, absentKeys, namespaceBinding, namespaceObject);
            }
        } else if (remainder.startsWith("{")) {
            bindNamedImports(globalObject, savedGlobals, absentKeys, namespaceObject, remainder);
        }
    }

    /**
     * Bind an import as a live binding using a getter that reads from the namespace.
     * This ensures that changes to the exported value are reflected in the import.
     */
    void bindImportOverlayLiveBinding(
            JSObject globalObject,
            Map<String, JSValue> savedGlobals,
            Set<String> absentKeys,
            String localName,
            JSObject namespaceObject,
            String importedName) {
        // The overlay key is the decoded name, because that is what module code writes to reach
        // this binding: `import { x as \\u0079 }` binds `y`, and a key spelled `\\u0079` is a
        // property nothing in the module can name.
        String bindingName = transformer.decodeIdentifierEscapes(localName.trim());
        if (bindingName.isEmpty()) {
            return;
        }
        PropertyKey key = PropertyKey.fromString(bindingName);
        if (globalObject.has(key)) {
            if (!savedGlobals.containsKey(bindingName)) {
                JSValue currentValue = globalObject.get(key);
                savedGlobals.put(bindingName, currentValue);
            }
        } else {
            absentKeys.add(bindingName);
        }
        PropertyKey importKey = PropertyKey.fromString(importedName);
        JSNativeFunction getter = new JSNativeFunction(context, "get " + bindingName, 0,
                (context, thisArg, args) -> {
                    JSValue val = namespaceObject.get(importKey);
                    if (context != null && context.hasPendingException()) {
                        JSValue exception = context.getPendingException();
                        context.clearPendingException();
                        throw new JSException(exception);
                    }
                    if (val == JSUndefined.INSTANCE && namespaceObject instanceof JSImportNamespaceObject namespace) {
                        JSValue earlyValue = namespace.getEarlyExportBinding(importedName);
                        if (earlyValue != null) {
                            return earlyValue;
                        }
                    }
                    return val != null ? val : JSUndefined.INSTANCE;
                });
        getter.initializePrototypeChain(context);
        // Setter distinguishes bare variable assignment (PUT_VAR, e.g., check = true)
        // from property-based writes (PUT_FIELD, e.g., globalThis.check = true).
        // Bare variable assignment to an import binding throws TypeError (ES2024 immutable binding).
        // Property-based writes update savedGlobals for correct value restoration.
        JSNativeFunction setter = new JSNativeFunction(context, "set " + bindingName, 1,
                (ctx, thisArg, args) -> {
                    if (ctx != null && ctx.isInBareVariableAssignment()) {
                        ctx.throwTypeError("Assignment to constant variable.");
                        return JSUndefined.INSTANCE;
                    }
                    JSValue val = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                    savedGlobals.put(bindingName, val);
                    return JSUndefined.INSTANCE;
                });
        setter.initializePrototypeChain(context);
        PropertyDescriptor descriptor = new PropertyDescriptor();
        descriptor.setGetter(getter);
        descriptor.setSetter(setter);
        descriptor.setConfigurable(true);
        descriptor.setEnumerable(true);
        globalObject.defineProperty(key, descriptor);
    }

    void bindImportOverlayValue(
            JSObject globalObject,
            Map<String, JSValue> savedGlobals,
            Set<String> absentKeys,
            String localName,
            JSValue value) {
        // The overlay key is the decoded name, because that is what module code writes to reach
        // this binding: `import { x as \\u0079 }` binds `y`, and a key spelled `\\u0079` is a
        // property nothing in the module can name.
        String bindingName = transformer.decodeIdentifierEscapes(localName.trim());
        if (bindingName.isEmpty()) {
            return;
        }
        PropertyKey key = PropertyKey.fromString(bindingName);
        if (globalObject.has(key)) {
            if (!savedGlobals.containsKey(bindingName)) {
                savedGlobals.put(bindingName, globalObject.get(key));
            }
        } else {
            absentKeys.add(bindingName);
        }
        PropertyDescriptor descriptor = new PropertyDescriptor();
        descriptor.setValue(value != null ? value : JSUndefined.INSTANCE);
        descriptor.setWritable(false);
        descriptor.setEnumerable(true);
        descriptor.setConfigurable(true);
        globalObject.defineProperty(key, descriptor);
    }

    void bindNamedImports(
            JSObject globalObject,
            Map<String, JSValue> savedGlobals,
            Set<String> absentKeys,
            JSObject namespaceObject,
            String namedClause) {
        String clause = namedClause.trim();
        if (!clause.startsWith("{") || !clause.endsWith("}")) {
            return;
        }
        String specifiersText = clause.substring(1, clause.length() - 1).trim();
        if (specifiersText.isEmpty()) {
            return;
        }
        for (String rawSpecifier : transformer.splitOnTopLevelCommas(specifiersText)) {
            String specifier = rawSpecifier.trim();
            if (specifier.isEmpty()) {
                continue;
            }
            String importedName;
            String localName;
            int asIndex = transformer.findTopLevelAs(specifier);
            if (asIndex >= 0) {
                importedName = transformer.parseModuleExportNameValue(specifier.substring(0, asIndex).trim());
                localName = specifier.substring(asIndex + 2).trim();
            } else {
                importedName = transformer.parseModuleExportNameValue(specifier);
                localName = importedName;
            }
            // ES2024 16.2.1.6.3: If the imported binding doesn't exist in a
            // finalized module namespace, it is a SyntaxError (linking error).
            if (namespaceObject instanceof JSImportNamespaceObject nsObj && nsObj.isFinalized()) {
                PropertyKey importKey = PropertyKey.fromString(importedName);
                if (!nsObj.has(importKey)) {
                    throw new JSSyntaxErrorException(
                            "The requested module does not provide an export named '" + importedName + "'");
                }
            }
            bindImportOverlayLiveBinding(globalObject, savedGlobals, absentKeys, localName, namespaceObject, importedName);
        }
    }

    void defineDynamicImportNamespaceForwardingBinding(
            JSDynamicImportModule moduleRecord,
            String exportName,
            JSDynamicImportModule targetModuleRecord,
            String targetSpecifier,
            String importedName) {
        JSImportNamespaceObject namespace = moduleRecord.namespace();
        PropertyKey exportKey = PropertyKey.fromString(exportName);
        if (namespace.hasDefinedOwnProperty(exportKey)) {
            return;
        }
        JSNativeFunction getter = new JSNativeFunction(context, "get " + exportName, 0,
                (ctx, thisArg, args) -> {
                    if (ModuleSourceTransformer.MODULE_NAMESPACE_EXPORT_NAME.equals(importedName)) {
                        return targetModuleRecord.namespace();
                    }
                    String resolvedImportedName = linker.getDynamicImportModuleExport(
                            targetModuleRecord, importedName, targetSpecifier);
                    return targetModuleRecord.namespace().get(PropertyKey.fromString(resolvedImportedName));
                });
        getter.initializePrototypeChain(context);
        PropertyDescriptor descriptor = new PropertyDescriptor();
        descriptor.setGetter(getter);
        descriptor.setEnumerable(true);
        descriptor.setConfigurable(true);
        namespace.defineExportBinding(
                context, exportKey, descriptor);
        namespace.registerExportName(exportName);
    }

    void defineDynamicImportNamespaceValue(
            JSDynamicImportModule moduleRecord,
            String exportName,
            JSValue exportValue) {
        // Use All (writable, enumerable, configurable) during construction so that
        // mergeStarReExport can delete ambiguous bindings. finalizeNamespace() will
        // report them as non-configurable via getOwnPropertyDescriptor override.
        moduleRecord.namespace().defineExportBinding(
                context,
                PropertyKey.fromString(exportName),
                exportValue,
                PropertyDescriptor.DataState.All);
        moduleRecord.namespace().registerExportName(exportName);
    }

    void initializeHoistedFunctionExportBindings(JSDynamicImportModule moduleRecord) {
        if (moduleRecord == null
                || moduleRecord.hoistedFunctionExportBindingsInitialized()) {
            return;
        }
        List<JSDynamicImportModule.HoistedFunctionExportBinding> hoistedBindings =
                moduleRecord.hoistedFunctionExportBindings();
        if (hoistedBindings.isEmpty()) {
            moduleRecord.setHoistedFunctionExportBindingsInitialized(true);
            return;
        }

        StringBuilder sourceBuilder = new StringBuilder();
        sourceBuilder.append("(function () {\n");
        for (JSDynamicImportModule.HoistedFunctionExportBinding hoistedBinding : hoistedBindings) {
            sourceBuilder.append(hoistedBinding.functionDeclarationSource()).append('\n');
        }
        sourceBuilder.append("return {");
        for (int bindingIndex = 0; bindingIndex < hoistedBindings.size(); bindingIndex++) {
            JSDynamicImportModule.HoistedFunctionExportBinding hoistedBinding = hoistedBindings.get(bindingIndex);
            if (bindingIndex > 0) {
                sourceBuilder.append(", ");
            }
            sourceBuilder.append("\"")
                    .append(transformer.escapeJavaScriptString(hoistedBinding.localName()))
                    .append("\": ")
                    .append(transformer.requireGeneratedIdentifier(
                            hoistedBinding.localName(),
                            "hoisted function export '" + hoistedBinding.localName() + "'",
                            moduleRecord.resolvedSpecifier()));
        }
        sourceBuilder.append("};\n})();");

        JSValue bindingsValue = context.eval(
                sourceBuilder.toString(),
                "<hoisted-export-init>",
                true,
                false);
        if (!(bindingsValue instanceof JSObject functionBindingsObject)) {
            moduleRecord.setHoistedFunctionExportBindingsInitialized(true);
            return;
        }

        JSImportNamespaceObject namespaceObject = moduleRecord.namespace();
        for (JSDynamicImportModule.HoistedFunctionExportBinding hoistedBinding : hoistedBindings) {
            PropertyKey localKey = PropertyKey.fromString(hoistedBinding.localName());
            JSValue functionValue = functionBindingsObject.get(localKey);
            if (context.hasPendingException()) {
                JSValue pendingError = context.getPendingException();
                context.clearPendingException();
                throw new JSException(pendingError);
            }
            if (!(functionValue instanceof JSFunction)) {
                continue;
            }
            namespaceObject.setEarlyExportBinding(hoistedBinding.exportedName(), functionValue);
        }
        moduleRecord.setHoistedFunctionExportBindingsInitialized(true);
    }
}
