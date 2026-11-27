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
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;

/** An import matched in transformed source, retained through loading, linking and error propagation. */
final class ModuleImportDeclaration {

    private final Map<String, String> attributes;
    private final String binding;
    private final Kind kind;
    private final String specifier;
    private final int start;
    private final String type;

    private ModuleImportDeclaration(ModuleSourceTransformer transformer, Matcher match, Kind kind, int specifierGroup,
            String binding) {
        start = match.start();
        this.kind = kind;
        this.binding = binding;
        specifier = ModuleSourceText.decodeModuleStringLiteralValue(match.group(specifierGroup));
        attributes = transformer.extractImportAttributes(match.group());
        type = attributes == null ? null : attributes.get("type");
    }

    void load(JSContext context, ModuleSourceTransformer transformer, JSObject imports, String filename) {
        if (kind == Kind.BINDINGS) {
            String moduleKind = null;
            if ("text".equals(type)) {
                moduleKind = "Text";
            } else if ("bytes".equals(type)) {
                moduleKind = "Bytes";
            } else if ("json".equals(type) && specifier.endsWith(".json")) {
                moduleKind = "JSON";
            }
            if (moduleKind != null && transformer.hasNonDefaultNamedBindings(binding)) {
                throw new JSSyntaxErrorException(moduleKind + " modules do not support named exports");
            }
        }
        JSObject namespace = kind == Kind.DEFERRED_NAMESPACE
                ? context.moduleLoader().loadDynamicImportModuleDeferred(specifier, filename, attributes)
                : context.moduleLoader().loadDynamicImportModule(specifier, filename, attributes);
        switch (kind) {
            case SIDE_EFFECT -> context.moduleLinker().resolveIncrementalReExport(specifier, filename);
            case NAMESPACE, DEFERRED_NAMESPACE ->
                context.importBindingInstaller().bindImportValue(imports, binding, namespace);
            case BINDINGS -> context.importBindingInstaller().applyImportClauseBindings(imports, namespace, binding);
        }
    }

    void propagateEvaluationError(JSContext context, String filename) {
        if (kind == Kind.DEFERRED_NAMESPACE) {
            return;
        }
        try {
            String resolved = context.moduleLoader().resolveDynamicImportSpecifier(specifier, filename, specifier);
            JSDynamicImportModule record = context.moduleLoader().cachedModule(resolved);
            if (record != null && record.status() == JSDynamicImportModule.Status.EVALUATED_ERROR) {
                throw new JSException(record.evaluationError());
            }
        } catch (JSException e) {
            throw e;
        } catch (Exception ignored) {
            // Resolution failures are handled by the loader.
        }
    }

    void validateBindings(JSContext context, String filename) {
        if (kind != Kind.BINDINGS || "text".equals(type) || "bytes".equals(type)) {
            return;
        }
        String resolved;
        try {
            resolved = context.moduleLoader().resolveDynamicImportSpecifier(specifier, filename, specifier);
        } catch (Exception ignored) {
            return;
        }
        JSDynamicImportModule record = context.moduleLoader().cachedModule(resolved);
        if (record != null && record.status() == JSDynamicImportModule.Status.EVALUATED
                && record.namespace().isFinalized()) {
            context.moduleLinker().validateNamedImportBindings(record.namespace(), binding);
        } else if (record != null && (record.status() == JSDynamicImportModule.Status.LOADING
                || record.status() == JSDynamicImportModule.Status.EVALUATING)) {
            context.moduleLinker().validateNamedImportBindingsAgainstExplicitExports(record, binding);
        }
    }

    static List<ModuleImportDeclaration> scan(ModuleSourceTransformer transformer, String source) {
        String text = ModuleSourceScan.maskModuleComments(source);
        List<ModuleImportDeclaration> declarations = new ArrayList<>();
        Matcher effect = ModuleSourceTransformer.MODULE_SIDE_EFFECT_IMPORT_PATTERN.matcher(text);
        while (effect.find()) {
            declarations.add(new ModuleImportDeclaration(transformer, effect, Kind.SIDE_EFFECT, 2, null));
        }
        Matcher namespace = ModuleSourceTransformer.MODULE_NAMESPACE_IMPORT_PATTERN.matcher(text);
        while (namespace.find()) {
            Kind kind = "defer".equals(namespace.group(1)) ? Kind.DEFERRED_NAMESPACE : Kind.NAMESPACE;
            declarations.add(new ModuleImportDeclaration(transformer, namespace, kind, 4, namespace.group(2)));
        }
        Matcher binding = ModuleSourceTransformer.MODULE_BINDING_IMPORT_PATTERN.matcher(text);
        while (binding.find()) {
            String clause = binding.group(1).trim();
            if (!clause.startsWith("*") && !clause.startsWith("defer *")) {
                declarations.add(new ModuleImportDeclaration(transformer, binding, Kind.BINDINGS, 3, clause));
            }
        }
        declarations.sort(Comparator.comparingInt(declaration -> declaration.start));
        return declarations;
    }

    static void visitBindings(String importClause, Consumer<String> defaultBinding, Consumer<String> namedClause,
            Consumer<String> namespaceBinding) {
        String clause = importClause.trim();
        if (clause.isEmpty() || clause.startsWith("*") || clause.startsWith("defer *")) {
            // Standalone namespace imports are handled by the namespace declaration kind.
            return;
        }
        if (!clause.startsWith("{")) {
            int comma = clause.indexOf(',');
            String name = comma < 0 ? clause : clause.substring(0, comma).trim();
            if (!name.isEmpty()) {
                defaultBinding.accept(name);
            }
            if (comma < 0) {
                return;
            }
            clause = clause.substring(comma + 1).trim();
        }
        if (clause.startsWith("{")) {
            namedClause.accept(clause);
        } else if (clause.startsWith("*")) {
            String name = clause.replaceFirst("^\\*\\s*as\\s+", "").trim();
            if (!name.isEmpty()) {
                namespaceBinding.accept(name);
            }
        }
    }

    private enum Kind {
        BINDINGS,
        DEFERRED_NAMESPACE,
        NAMESPACE,
        SIDE_EFFECT
    }
}
