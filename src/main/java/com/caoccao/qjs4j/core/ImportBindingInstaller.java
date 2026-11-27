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

import com.caoccao.qjs4j.core.module.ModuleSourceText;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

import java.util.List;

/** Installs live, immutable imports in a persistent module environment and exports in its namespace. */
final class ImportBindingInstaller {
    private final JSContext context;
    private final ModuleLinker linker;
    private final ModuleSourceTransformer transformer;

    ImportBindingInstaller(JSContext context, ModuleSourceTransformer transformer, ModuleLinker linker) {
        this.context = context;
        this.transformer = transformer;
        this.linker = linker;
    }

    void applyImportClauseBindings(JSObject globalObject, JSObject namespaceObject, String importClause) {
        ModuleImportDeclaration.visitBindings(importClause,
                name -> bindImportLiveBinding(globalObject, name, namespaceObject, "default"),
                named -> bindNamedImports(globalObject, namespaceObject, named),
                name -> bindImportValue(globalObject, name, namespaceObject));
    }

    /**
     * Bind an import as a live binding using a getter that reads from the namespace. This ensures that changes to the
     * exported value are reflected in the import.
     */
    void bindImportLiveBinding(JSObject globalObject, String localName, JSObject namespaceObject, String importedName) {
        // The environment key is the decoded name, because that is what module code writes to reach
        // this binding: `import { x as \\u0079 }` binds `y`, and a key spelled `\\u0079` is a
        // property nothing in the module can name.
        String bindingName = ModuleSourceText.decodeIdentifierEscapes(localName.trim());
        if (bindingName.isEmpty()) {
            return;
        }
        PropertyKey key = PropertyKey.fromString(bindingName);
        PropertyKey importKey = PropertyKey.fromString(importedName);
        JSNativeFunction getter = new JSNativeFunction(context, "get " + bindingName, 0, (context, thisArg, args) -> {
            JSValue val = namespaceObject.get(importKey);
            if (context != null && context.hasPendingException()) {
                JSValue exception = context.takePendingException();
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
        JSNativeFunction setter = new JSNativeFunction(context, "set " + bindingName, 1,
                (ctx, thisArg, args) -> ctx.throwTypeError("Assignment to constant variable."));
        setter.initializePrototypeChain(context);
        PropertyDescriptor descriptor = new PropertyDescriptor();
        descriptor.setGetter(getter);
        descriptor.setSetter(setter);
        descriptor.setConfigurable(true);
        descriptor.setEnumerable(true);
        globalObject.defineProperty(key, descriptor);
    }

    void bindImportValue(JSObject globalObject, String localName, JSValue value) {
        // The environment key is the decoded name, because that is what module code writes to reach
        // this binding: `import { x as \\u0079 }` binds `y`, and a key spelled `\\u0079` is a
        // property nothing in the module can name.
        String bindingName = ModuleSourceText.decodeIdentifierEscapes(localName.trim());
        if (bindingName.isEmpty()) {
            return;
        }
        PropertyKey key = PropertyKey.fromString(bindingName);
        PropertyDescriptor descriptor = new PropertyDescriptor();
        descriptor.setValue(value != null ? value : JSUndefined.INSTANCE);
        descriptor.setWritable(false);
        descriptor.setEnumerable(true);
        descriptor.setConfigurable(true);
        globalObject.defineProperty(key, descriptor);
    }

    void bindNamedImports(JSObject globalObject, JSObject namespaceObject, String namedClause) {
        transformer.forEachNamedImport(namedClause, (importedName, localName) -> {
            // ES2024 16.2.1.6.3: If the imported binding doesn't exist in a
            // finalized module namespace, it is a SyntaxError (linking error).
            if (namespaceObject instanceof JSImportNamespaceObject nsObj && nsObj.isFinalized()) {
                PropertyKey importKey = PropertyKey.fromString(importedName);
                if (!nsObj.has(importKey)) {
                    throw new JSSyntaxErrorException(
                            "The requested module does not provide an export named '" + importedName + "'");
                }
            }
            bindImportLiveBinding(globalObject, localName, namespaceObject, importedName);
        });
    }

    void defineDynamicImportNamespaceForwardingBinding(JSDynamicImportModule moduleRecord, String exportName,
            JSDynamicImportModule targetModuleRecord, String targetSpecifier, String importedName) {
        JSImportNamespaceObject namespace = moduleRecord.namespace();
        PropertyKey exportKey = PropertyKey.fromString(exportName);
        if (namespace.hasDefinedOwnProperty(exportKey)) {
            return;
        }
        JSNativeFunction getter = new JSNativeFunction(context, "get " + exportName, 0, (ctx, thisArg, args) -> {
            if (ModuleSourceTransformer.MODULE_NAMESPACE_EXPORT_NAME.equals(importedName)) {
                return targetModuleRecord.namespace();
            }
            String resolvedImportedName = linker.getDynamicImportModuleExport(targetModuleRecord, importedName,
                    targetSpecifier);
            return targetModuleRecord.namespace().get(PropertyKey.fromString(resolvedImportedName));
        });
        getter.initializePrototypeChain(context);
        PropertyDescriptor descriptor = new PropertyDescriptor();
        descriptor.setGetter(getter);
        descriptor.setEnumerable(true);
        descriptor.setConfigurable(true);
        namespace.defineExportBinding(context, exportKey, descriptor);
        namespace.registerExportName(exportName);
    }

    void defineDynamicImportNamespaceValue(JSDynamicImportModule moduleRecord, String exportName, JSValue exportValue) {
        // Use All (writable, enumerable, configurable) during construction so that
        // mergeStarReExport can delete ambiguous bindings. finalizeNamespace() will
        // report them as non-configurable via getOwnPropertyDescriptor override.
        moduleRecord.namespace().defineExportBinding(context, PropertyKey.fromString(exportName), exportValue,
                PropertyDescriptor.DataState.All);
        moduleRecord.namespace().registerExportName(exportName);
    }

    void initializeHoistedFunctionExportBindings(JSDynamicImportModule moduleRecord) {
        if (moduleRecord == null || moduleRecord.hoistedFunctionExportBindingsInitialized()) {
            return;
        }
        List<JSDynamicImportModule.HoistedFunctionExportBinding> hoistedBindings = moduleRecord
                .hoistedFunctionExportBindings();
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
            sourceBuilder.append("\"").append(ModuleSourceText.escapeJavaScriptString(hoistedBinding.localName()))
                    .append("\": ")
                    .append(transformer.requireGeneratedIdentifier(hoistedBinding.localName(),
                            "hoisted function export '" + hoistedBinding.localName() + "'",
                            moduleRecord.resolvedSpecifier()));
        }
        sourceBuilder.append("};\n})();");

        JSValue bindingsValue = context.eval(sourceBuilder.toString(), "<hoisted-export-init>", true, false);
        if (!(bindingsValue instanceof JSObject functionBindingsObject)) {
            moduleRecord.setHoistedFunctionExportBindingsInitialized(true);
            return;
        }

        JSImportNamespaceObject namespaceObject = moduleRecord.namespace();
        for (JSDynamicImportModule.HoistedFunctionExportBinding hoistedBinding : hoistedBindings) {
            PropertyKey localKey = PropertyKey.fromString(hoistedBinding.localName());
            JSValue functionValue = functionBindingsObject.get(localKey);
            if (context.hasPendingException()) {
                JSValue pendingError = context.takePendingException();
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
