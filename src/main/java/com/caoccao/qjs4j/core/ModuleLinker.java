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

import com.caoccao.qjs4j.compilation.ast.SourceLocation;
import com.caoccao.qjs4j.compilation.lexer.Token;
import com.caoccao.qjs4j.compilation.lexer.TokenType;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Links a module graph: reads what each module asks of the modules it names, resolves each name to
 * the binding that provides it, and raises the failure before anything is evaluated.
 * <p>
 * ECMAScript links a whole graph and only then evaluates it. This engine still loads a dependency
 * and evaluates it in the same step, so {@link #requireModuleGraphLinks} is a separate pass over
 * records of its own — see {@code ModuleLinkPass} — that restores the ordering for the failures
 * that are observable: a specifier that does not resolve, a module that is not valid source, a name
 * nothing exports, two {@code export *} routes to different bindings for one name. It never touches
 * the module cache, so the evaluation that follows is unaffected.
 * <p>
 * The rest of the class is ResolveExport as evaluation needs it — {@code resolveDynamicImportExport}
 * and the re-export merging built on it — plus the token readers both halves share.
 */
final class ModuleLinker {
    /**
     * Sentinel for an export name two different {@code export *} targets provide.
     * <p>
     * Recognised by identity, never by value, so no module can produce a binding equal to it.
     */
    static final LinkedBinding AMBIGUOUS_LINKED_EXPORT = new LinkedBinding("", "ambiguous");

    /**
     * Sentinel for an export the link pass cannot decide about, because a module it would have to
     * read is not one this pass can read. Evaluation still decides those.
     * <p>
     * Recognised by identity, never by value, so no module can produce a binding equal to it.
     */
    static final LinkedBinding UNKNOWN_LINKED_EXPORT = new LinkedBinding("", "unknown");
    private final JSContext context;
    private final ModuleSourceTransformer transformer;

    ModuleLinker(JSContext context, ModuleSourceTransformer transformer) {
        this.context = context;
        this.transformer = transformer;
    }

    String getDynamicImportModuleExport(
            JSDynamicImportModule moduleRecord,
            String exportName,
            String targetSpecifier) {
        DynamicImportExportResolution resolution = resolveDynamicImportExport(
                moduleRecord,
                exportName,
                new HashSet<>(),
                new HashSet<>());
        if (resolution.ambiguous()) {
            throw new JSException(context.throwSyntaxError("ambiguous indirect export: " + exportName));
        }
        if (!resolution.found()) {
            throw new JSException(context.throwSyntaxError(
                    "module '" + targetSpecifier + "' does not provide export '" + exportName + "'"));
        }
        return resolution.bindingName();
    }

    /**
     * Every module a source names, and every export name it asks that module for, read from the
     * engine's own tokens.
     * <p>
     * This used to be pulled out of the source with string operations — {@code indexOf(" as ")} for
     * a renaming specifier, {@code isValidIdentifierName} over everything before the first brace
     * for a default binding — which recognised one particular spelling of each form and quietly
     * ignored the rest. A tab instead of a space around {@code as}, a line break inside the clause,
     * a comment between the tokens, {@code import d, * as ns from '…'}, or a string import name
     * ({@code import \{ "a-b" as c \}}) all produced no request at all, so the link check passed and
     * the dependency ran before the same failure was found. Whether the engine preserved module
     * stage ordering came down to how the source happened to be formatted.
     * <p>
     * Tokens answer all of those the same way the compiler does, and carry positions, which is what
     * lets a failure name where the request was written. {@code export \{ a \} from '…'} asks for a
     * name exactly as an import does and is read here for the same reason.
     *
     * @param sourceCode the module source, as written
     * @return one entry per declaration that names a module, or null when the source does not
     * tokenise
     */
    List<LinkedModuleRequest> linkedModuleRequests(String sourceCode) {
        if (sourceCode == null || (!sourceCode.contains("import") && !sourceCode.contains("export"))) {
            return List.of();
        }
        List<Token> tokens;
        try {
            tokens = ModuleSourceTransformer.tokenizeModuleSource(sourceCode);
        } catch (RuntimeException notTokenisable) {
            // Source that does not tokenise is not this pass's problem to report: the compiler
            // rejects it with a proper SyntaxError.
            context.clearPendingException();
            return null;
        }
        List<LinkedModuleRequest> requests = new ArrayList<>();
        int depth = 0;
        for (int index = 0; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            switch (token.type()) {
                case LBRACE, LPAREN, LBRACKET -> depth++;
                case RBRACE, RPAREN, RBRACKET -> depth = Math.max(0, depth - 1);
                default -> {
                }
            }
            if (depth != 0 || index + 1 >= tokens.size()) {
                continue;
            }
            if (token.type() == TokenType.EXPORT) {
                index = readExportDeclaration(tokens, index, sourceCode, requests);
                continue;
            }
            if (token.type() != TokenType.IMPORT
                    || tokens.get(index + 1).type() == TokenType.LPAREN
                    || tokens.get(index + 1).type() == TokenType.DOT) {
                // `import(...)` and `import.meta` are expressions, not declarations.
                continue;
            }
            index = readImportDeclaration(tokens, index, sourceCode, requests);
        }
        return requests;
    }

    void mergeStarReExport(
            JSDynamicImportModule moduleRecord,
            JSDynamicImportModule targetModuleRecord,
            Map<String, String> exportOrigins,
            String targetSpecifier) {
        Set<String> candidateExportNames = new TreeSet<>();
        for (PropertyKey key : targetModuleRecord.namespace().getOwnPropertyKeys()) {
            if (key.isString()) {
                candidateExportNames.add(key.asString());
            }
        }
        candidateExportNames.addAll(targetModuleRecord.explicitExportNames());
        for (JSDynamicImportModule.ReExportBinding reExportBinding : targetModuleRecord.reExportBindings()) {
            if (!reExportBinding.starExport()) {
                candidateExportNames.add(reExportBinding.exportedName());
            }
        }

        for (String exportName : candidateExportNames) {
            if ("default".equals(exportName)) {
                continue;
            }
            // Skip names already known to be ambiguous in this module
            // (from a previous incremental or full re-export resolution pass).
            if (moduleRecord.ambiguousExportNames().contains(exportName)) {
                continue;
            }
            if (targetModuleRecord.ambiguousExportNames().contains(exportName)) {
                moduleRecord.ambiguousExportNames().add(exportName);
                moduleRecord.namespace().removeExportBinding(exportName);
                exportOrigins.remove(exportName);
                continue;
            }
            if (moduleRecord.explicitExportNames().contains(exportName)) {
                continue;
            }
            DynamicImportExportResolution resolution = resolveDynamicImportExport(
                    targetModuleRecord,
                    exportName,
                    new HashSet<>(),
                    new HashSet<>());
            if (resolution.ambiguous()) {
                moduleRecord.ambiguousExportNames().add(exportName);
                moduleRecord.namespace().removeExportBinding(exportName);
                exportOrigins.remove(exportName);
                continue;
            }
            if (!resolution.found()) {
                continue;
            }
            String existingOrigin = exportOrigins.get(exportName);
            String candidateOrigin = resolution.moduleRecord().resolvedSpecifier();
            if (existingOrigin == null) {
                context.importBindingInstaller().defineDynamicImportNamespaceForwardingBinding(
                        moduleRecord,
                        exportName,
                        resolution.moduleRecord(),
                        candidateOrigin,
                        resolution.bindingName());
                exportOrigins.put(exportName, candidateOrigin);
                continue;
            }
            if (!existingOrigin.equals(candidateOrigin)) {
                moduleRecord.ambiguousExportNames().add(exportName);
                moduleRecord.namespace().removeExportBinding(exportName);
                exportOrigins.remove(exportName);
            }
        }
    }

    /**
     * Read one {@code export} declaration, and record what it asks another module for.
     * <p>
     * Only the forms with a {@code from} clause name a module. {@code export \{ a \} from '…'} asks
     * for {@code a} exactly as an import does; {@code export * from '…'} and
     * {@code export * as ns from '…'} ask for no particular name but still name a module, which has
     * to link.
     *
     * @param tokens     the token list
     * @param start      the index of the {@code export} keyword
     * @param sourceCode the source the tokens came from, for positions
     * @param requests   collects the declaration, when it names a module
     * @return the index of the last token consumed
     */
    int readExportDeclaration(
            List<Token> tokens,
            int start,
            String sourceCode,
            List<LinkedModuleRequest> requests) {
        int index = start + 1;
        List<LinkedExportNameRequest> requestedNames = new ArrayList<>();
        if (tokens.get(index).type() == TokenType.MUL) {
            index++;
            if (index < tokens.size() && tokens.get(index).type() == TokenType.AS) {
                index += 2;
            }
        } else if (tokens.get(index).type() == TokenType.LBRACE) {
            index = readNamedImports(tokens, index, sourceCode, requestedNames) + 1;
        } else {
            // `export default …` and `export <declaration>` name no module.
            return start;
        }
        if (index >= tokens.size() || tokens.get(index).type() != TokenType.FROM) {
            // `export { a, b };` re-exports local bindings, which are this module's own.
            return Math.min(Math.max(index - 1, start), tokens.size() - 1);
        }
        index++;
        if (index >= tokens.size() || tokens.get(index).type() != TokenType.STRING) {
            return Math.min(index, tokens.size() - 1);
        }
        requests.add(new LinkedModuleRequest(
                tokens.get(index).value(),
                transformer.moduleTokenLocation(tokens, index, sourceCode),
                readImportAttributes(tokens, index),
                List.copyOf(requestedNames)));
        return ModuleSourceTransformer.skipModuleDeclarationTail(tokens, index);
    }

    /**
     * Read a {@code with}/{@code assert} attributes clause that follows a module specifier.
     *
     * @param tokens         the token list
     * @param specifierIndex the index of the specifier string token
     * @return the attributes, empty when the declaration carries none
     */
    Map<String, String> readImportAttributes(List<Token> tokens, int specifierIndex) {
        int index = specifierIndex + 1;
        if (index + 1 >= tokens.size()
                || !("with".equals(tokens.get(index).value()) || "assert".equals(tokens.get(index).value()))
                || tokens.get(index + 1).type() != TokenType.LBRACE) {
            return Map.of();
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        index += 2;
        while (index < tokens.size() && tokens.get(index).type() != TokenType.RBRACE) {
            if (tokens.get(index).type() == TokenType.COMMA) {
                index++;
                continue;
            }
            String key = tokens.get(index).value();
            index++;
            if (index < tokens.size() && tokens.get(index).type() == TokenType.COLON) {
                index++;
                if (index < tokens.size()) {
                    attributes.put(key, tokens.get(index).value());
                    index++;
                }
            }
        }
        return attributes;
    }

    /**
     * Read one static {@code import} declaration, from its keyword to its last token.
     *
     * @param tokens     the token list
     * @param start      the index of the {@code import} keyword
     * @param sourceCode the source the tokens came from, for positions
     * @param requests   collects the declaration, when it names a module
     * @return the index of the declaration's last token
     */
    int readImportDeclaration(
            List<Token> tokens,
            int start,
            String sourceCode,
            List<LinkedModuleRequest> requests) {
        int index = start + 1;
        List<LinkedExportNameRequest> importedNames = new ArrayList<>();
        if (tokens.get(index).type() != TokenType.STRING) {
            // `import defer * as ns from '…'` is this engine's deferred-namespace form.
            if ("defer".equals(tokens.get(index).value())
                    && index + 1 < tokens.size()
                    && tokens.get(index + 1).type() == TokenType.MUL) {
                index++;
            }
            if (tokens.get(index).type() == TokenType.LBRACE) {
                index = readNamedImports(tokens, index, sourceCode, importedNames);
            } else if (tokens.get(index).type() != TokenType.MUL) {
                // An ImportedDefaultBinding asks for the name `default`, and fails to link exactly
                // as any other name does.
                importedNames.add(new LinkedExportNameRequest(
                        "default", transformer.moduleTokenLocation(tokens, index, sourceCode)));
                index++;
                if (index < tokens.size() && tokens.get(index).type() == TokenType.COMMA) {
                    index++;
                    if (index < tokens.size() && tokens.get(index).type() == TokenType.LBRACE) {
                        index = readNamedImports(tokens, index, sourceCode, importedNames);
                    }
                }
            }
            // Whatever the clause was, the specifier is the string after `from`.
            while (index < tokens.size()
                    && tokens.get(index).type() != TokenType.FROM
                    && tokens.get(index).type() != TokenType.SEMICOLON) {
                index++;
            }
            if (index >= tokens.size() || tokens.get(index).type() != TokenType.FROM) {
                return Math.min(index, tokens.size() - 1);
            }
            index++;
        }
        if (index >= tokens.size() || tokens.get(index).type() != TokenType.STRING) {
            return Math.min(Math.max(index, start + 1), tokens.size() - 1);
        }
        requests.add(new LinkedModuleRequest(
                tokens.get(index).value(),
                transformer.moduleTokenLocation(tokens, index, sourceCode),
                readImportAttributes(tokens, index),
                List.copyOf(importedNames)));
        return ModuleSourceTransformer.skipModuleDeclarationTail(tokens, index);
    }

    /**
     * Read a {@code \{ a, b as c, "d-e" as f \}} import clause.
     *
     * @param tokens        the token list
     * @param lbraceIndex   the index of the opening brace
     * @param sourceCode    the source the tokens came from, for positions
     * @param importedNames collects the names the clause asks the exporting module for
     * @return the index of the closing brace
     */
    int readNamedImports(
            List<Token> tokens,
            int lbraceIndex,
            String sourceCode,
            List<LinkedExportNameRequest> importedNames) {
        int index = lbraceIndex + 1;
        while (index < tokens.size() && tokens.get(index).type() != TokenType.RBRACE) {
            if (tokens.get(index).type() == TokenType.COMMA) {
                index++;
                continue;
            }
            // A ModuleExportName is an IdentifierName — reserved words included — or a string, and
            // the token's value is the decoded one either way.
            importedNames.add(new LinkedExportNameRequest(
                    tokens.get(index).value(), transformer.moduleTokenLocation(tokens, index, sourceCode)));
            index++;
            if (index < tokens.size() && tokens.get(index).type() == TokenType.AS) {
                // `as` and the local binding it introduces, which the exporting module never sees.
                index += 2;
            }
        }
        return Math.min(index, tokens.size() - 1);
    }

    /**
     * Load and parse every module the graph reaches, and check every name it imports, before any
     * module body is evaluated.
     * <p>
     * ECMAScript links a whole module graph and only then evaluates it. This engine loads a
     * dependency and evaluates it in the same step, so an import naming an export nothing provides
     * was discovered <em>after</em> the module it imports from had already run — externally visible
     * side effects and all — for a graph that must never have begun evaluating.
     * <p>
     * This pass restores the ordering for the failure that is observable: it reads and parses the
     * graph, resolves each named import against the binding the exporting module actually provides,
     * and raises the {@code SyntaxError} before anything runs. It uses records of its own rather
     * than the module cache, so the evaluation that follows is completely unaffected — the cost is
     * parsing each module's source twice, and the only behavioural change is that a link failure
     * now happens at link time.
     * <p>
     * It is containment, not the fix. Loading, linking and evaluating are still one operation for
     * everything else: a dependency that <em>throws</em> still does so before the rest of the graph
     * is linked, and only real module records with separate stages resolve that.
     *
     * @param sourceCode the entry module's source, as written
     * @param filename   the entry module's name, used to resolve its specifiers
     */
    void requireModuleGraphLinks(String sourceCode, String filename) {
        String rootSpecifier;
        try {
            rootSpecifier = context.moduleLoader().resolveDynamicImportSpecifier(filename, null, filename);
        } catch (JSException unresolvable) {
            context.clearPendingException();
            rootSpecifier = context.moduleLoader().normalizeModuleSpecifier(filename);
        }
        new ModuleLinkPass(rootSpecifier).linkGraph(sourceCode);
    }

    DynamicImportExportResolution resolveDynamicImportExport(
            JSDynamicImportModule moduleRecord,
            String exportName,
            Set<String> resolveSet,
            Set<String> exportStarSet) {
        if (moduleRecord.ambiguousExportNames().contains(exportName)) {
            return DynamicImportExportResolution.ambiguousResolution();
        }

        String resolveSetKey = moduleRecord.resolvedSpecifier() + "::" + exportName;
        if (!resolveSet.add(resolveSetKey)) {
            // Circular resolve request. Per ES2024 ResolveExport, return null.
            return DynamicImportExportResolution.notFoundResolution();
        }

        for (JSDynamicImportModule.LocalExportBinding localExportBinding : moduleRecord.localExportBindings()) {
            if (exportName.equals(localExportBinding.exportedName())) {
                return DynamicImportExportResolution.resolvedResolution(moduleRecord, exportName);
            }
        }

        for (JSDynamicImportModule.ReExportBinding reExportBinding : moduleRecord.reExportBindings()) {
            if (reExportBinding.starExport()) {
                continue;
            }
            if (!exportName.equals(reExportBinding.exportedName())) {
                continue;
            }
            String targetSpecifier = context.moduleLoader().resolveDynamicImportSpecifier(
                    reExportBinding.sourceSpecifier(),
                    moduleRecord.resolvedSpecifier(),
                    reExportBinding.sourceSpecifier());
            JSDynamicImportModule targetModuleRecord =
                    context.moduleLoader().loadJSDynamicImportModule(targetSpecifier, new HashSet<>(), null);
            if (ModuleSourceTransformer.MODULE_NAMESPACE_EXPORT_NAME.equals(reExportBinding.importedName())) {
                return DynamicImportExportResolution.resolvedResolution(targetModuleRecord, ModuleSourceTransformer.MODULE_NAMESPACE_EXPORT_NAME);
            }
            return resolveDynamicImportExport(
                    targetModuleRecord,
                    reExportBinding.importedName(),
                    resolveSet,
                    exportStarSet);
        }

        if ("default".equals(exportName)) {
            return DynamicImportExportResolution.notFoundResolution();
        }

        String exportStarSetKey = moduleRecord.resolvedSpecifier() + "::" + exportName;
        if (!exportStarSet.add(exportStarSetKey)) {
            return DynamicImportExportResolution.notFoundResolution();
        }

        DynamicImportExportResolution starResolution = DynamicImportExportResolution.notFoundResolution();
        for (JSDynamicImportModule.ReExportBinding reExportBinding : moduleRecord.reExportBindings()) {
            if (!reExportBinding.starExport()) {
                continue;
            }
            String targetSpecifier = context.moduleLoader().resolveDynamicImportSpecifier(
                    reExportBinding.sourceSpecifier(),
                    moduleRecord.resolvedSpecifier(),
                    reExportBinding.sourceSpecifier());
            JSDynamicImportModule targetModuleRecord =
                    context.moduleLoader().loadJSDynamicImportModule(targetSpecifier, new HashSet<>(), null);
            DynamicImportExportResolution resolution = resolveDynamicImportExport(
                    targetModuleRecord,
                    exportName,
                    resolveSet,
                    exportStarSet);
            if (resolution.ambiguous()) {
                return resolution;
            }
            if (!resolution.found()) {
                continue;
            }
            if (!starResolution.found()) {
                starResolution = resolution;
                continue;
            }
            boolean sameTargetModule = starResolution.moduleRecord() == resolution.moduleRecord();
            boolean sameBindingName = Objects.equals(starResolution.bindingName(), resolution.bindingName());
            if (!sameTargetModule || !sameBindingName) {
                return DynamicImportExportResolution.ambiguousResolution();
            }
        }
        return starResolution;
    }

    void resolveDynamicImportReExports(
            JSDynamicImportModule moduleRecord,
            Set<String> importResolutionStack) {
        if (moduleRecord.reExportBindings().isEmpty()) {
            return;
        }

        if (!importResolutionStack.add(moduleRecord.resolvedSpecifier())) {
            throw new JSException(context.throwSyntaxError("Circular module dependency"));
        }
        try {
            Map<String, String> exportOrigins = moduleRecord.exportOrigins();
            for (JSDynamicImportModule.ReExportBinding reExportBinding : moduleRecord.reExportBindings()) {
                String targetSpecifier = context.moduleLoader().resolveDynamicImportSpecifier(
                        reExportBinding.sourceSpecifier(),
                        moduleRecord.resolvedSpecifier(),
                        reExportBinding.sourceSpecifier());
                JSDynamicImportModule targetModuleRecord =
                        context.moduleLoader().loadJSDynamicImportModule(targetSpecifier, importResolutionStack, null);
                if (reExportBinding.starExport()) {
                    mergeStarReExport(moduleRecord, targetModuleRecord, exportOrigins, targetSpecifier);
                    continue;
                }
                String importedName = reExportBinding.importedName();
                DynamicImportExportResolution resolution;
                if (ModuleSourceTransformer.MODULE_NAMESPACE_EXPORT_NAME.equals(importedName)) {
                    resolution = DynamicImportExportResolution.resolvedResolution(targetModuleRecord, ModuleSourceTransformer.MODULE_NAMESPACE_EXPORT_NAME);
                } else {
                    resolution = resolveDynamicImportExport(
                            targetModuleRecord,
                            importedName,
                            new HashSet<>(),
                            new HashSet<>());
                }
                if (resolution.ambiguous()) {
                    throw new JSException(context.throwSyntaxError(
                            "ambiguous indirect export: " + reExportBinding.exportedName()));
                }
                if (!resolution.found()) {
                    throw new JSException(context.throwSyntaxError(
                            "module '" + targetSpecifier + "' does not provide export '" + importedName + "'"));
                }
                String existingOrigin = exportOrigins.get(reExportBinding.exportedName());
                String resolvedOrigin = resolution.moduleRecord().resolvedSpecifier();
                if (existingOrigin != null && existingOrigin.equals(resolvedOrigin)) {
                    continue;
                }
                context.importBindingInstaller().defineDynamicImportNamespaceForwardingBinding(
                        moduleRecord,
                        reExportBinding.exportedName(),
                        resolution.moduleRecord(),
                        resolvedOrigin,
                        resolution.bindingName());
                moduleRecord.explicitExportNames().add(reExportBinding.exportedName());
                exportOrigins.put(reExportBinding.exportedName(), resolvedOrigin);
            }
        } finally {
            importResolutionStack.remove(moduleRecord.resolvedSpecifier());
        }
    }

    /**
     * After loading a side-effect import (from an export-from line), resolve matching
     * re-export bindings for the current module immediately. This populates the namespace
     * before the IIFE body runs, so self-imports can see re-exported names.
     */
    void resolveIncrementalReExport(String specifier, String filename) {
        String normalizedFilename = Paths.get(filename).normalize().toString();
        JSDynamicImportModule currentModule = context.moduleLoader().cachedModule(normalizedFilename);
        if (currentModule == null || currentModule.reExportBindings().isEmpty()) {
            return;
        }
        String resolvedTargetSpec;
        try {
            resolvedTargetSpec = context.moduleLoader().resolveDynamicImportSpecifier(
                    specifier, filename, specifier);
        } catch (Exception e) {
            return;
        }
        JSDynamicImportModule targetModule = context.moduleLoader().cachedModule(resolvedTargetSpec);
        if (targetModule == null || targetModule.status() == JSDynamicImportModule.Status.EVALUATED_ERROR) {
            return;
        }
        Map<String, String> exportOrigins = currentModule.exportOrigins();
        for (JSDynamicImportModule.ReExportBinding reExport : currentModule.reExportBindings()) {
            String reExportTargetSpec;
            try {
                reExportTargetSpec = context.moduleLoader().resolveDynamicImportSpecifier(
                        reExport.sourceSpecifier(),
                        currentModule.resolvedSpecifier(),
                        reExport.sourceSpecifier());
            } catch (Exception e) {
                continue;
            }
            if (!reExportTargetSpec.equals(resolvedTargetSpec)) {
                continue;
            }
            if (reExport.starExport()) {
                mergeStarReExport(currentModule, targetModule, exportOrigins, reExportTargetSpec);
            } else {
                String importedName = reExport.importedName();
                DynamicImportExportResolution resolution;
                if (ModuleSourceTransformer.MODULE_NAMESPACE_EXPORT_NAME.equals(importedName)) {
                    resolution = DynamicImportExportResolution.resolvedResolution(targetModule, ModuleSourceTransformer.MODULE_NAMESPACE_EXPORT_NAME);
                } else {
                    resolution = resolveDynamicImportExport(
                            targetModule,
                            importedName,
                            new HashSet<>(),
                            new HashSet<>());
                }
                if (resolution.ambiguous()) {
                    throw new JSException(context.throwSyntaxError(
                            "ambiguous indirect export: " + reExport.exportedName()));
                }
                if (!resolution.found()) {
                    throw new JSException(context.throwSyntaxError(
                            "module '" + reExportTargetSpec + "' does not provide export '" + importedName + "'"));
                }
                String existingOrigin = exportOrigins.get(reExport.exportedName());
                String resolvedOrigin = resolution.moduleRecord().resolvedSpecifier();
                if (existingOrigin != null && existingOrigin.equals(resolvedOrigin)) {
                    continue;
                }
                context.importBindingInstaller().defineDynamicImportNamespaceForwardingBinding(
                        currentModule,
                        reExport.exportedName(),
                        resolution.moduleRecord(),
                        resolvedOrigin,
                        resolution.bindingName());
                currentModule.explicitExportNames().add(reExport.exportedName());
                exportOrigins.put(reExport.exportedName(), resolvedOrigin);
            }
        }
    }

    void validateImportNameAgainstModuleRecord(
            JSDynamicImportModule moduleRecord,
            String importedName) {
        DynamicImportExportResolution resolution = resolveDynamicImportExport(
                moduleRecord,
                importedName,
                new HashSet<>(),
                new HashSet<>());
        if (resolution.ambiguous()) {
            throw new JSSyntaxErrorException(
                    "ambiguous indirect export: " + importedName);
        }
        if (!resolution.found()) {
            throw new JSSyntaxErrorException(
                    "The requested module does not provide an export named '" + importedName + "'");
        }
    }

    void validateModuleScriptEarlyErrors(String sourceCode) {
        String sourceWithoutComments = sourceCode
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
        Matcher varMatcher = Pattern.compile("\\bvar\\s+([A-Za-z_$][A-Za-z0-9_$]*)").matcher(sourceWithoutComments);
        Set<String> varNames = new HashSet<>();
        while (varMatcher.find()) {
            varNames.add(varMatcher.group(1));
        }
        Matcher functionMatcher = Pattern.compile("\\bfunction\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(")
                .matcher(sourceWithoutComments);
        while (functionMatcher.find()) {
            String functionName = functionMatcher.group(1);
            if (varNames.contains(functionName)) {
                throw new JSException(context.throwSyntaxError("Identifier '" + functionName + "' has already been declared"));
            }
        }
    }

    /**
     * Validate that all named imports in an import clause exist in the finalized namespace.
     * Throws SyntaxError if any binding is missing (ES2024 linking error).
     */
    void validateNamedImportBindings(JSImportNamespaceObject namespace, String importClause) {
        String clause = importClause.trim();
        if (clause.isEmpty() || clause.startsWith("*") || clause.startsWith("defer *")) {
            return;
        }
        if (clause.startsWith("{")) {
            validateNamedImportSpecifiers(namespace, clause);
            return;
        }
        int commaIdx = clause.indexOf(',');
        if (commaIdx < 0) {
            PropertyKey defaultKey = PropertyKey.fromString("default");
            if (!namespace.has(defaultKey)) {
                throw new JSSyntaxErrorException(
                        "The requested module does not provide an export named 'default'");
            }
            return;
        }

        String defaultBinding = clause.substring(0, commaIdx).trim();
        if (!defaultBinding.isEmpty()) {
            PropertyKey defaultKey = PropertyKey.fromString("default");
            if (!namespace.has(defaultKey)) {
                throw new JSSyntaxErrorException(
                        "The requested module does not provide an export named 'default'");
            }
        }

        String remainder = clause.substring(commaIdx + 1).trim();
        if (remainder.startsWith("{")) {
            validateNamedImportSpecifiers(namespace, remainder);
        }
    }

    void validateNamedImportBindingsAgainstExplicitExports(
            JSDynamicImportModule moduleRecord, String importClause) {
        String clause = importClause.trim();
        if (clause.isEmpty() || clause.startsWith("*") || clause.startsWith("defer *")) {
            return;
        }
        if (clause.startsWith("{")) {
            validateNamedImportSpecifiersAgainstModuleRecord(moduleRecord, clause);
            return;
        }
        int commaIdx = clause.indexOf(',');
        if (commaIdx < 0) {
            validateImportNameAgainstModuleRecord(moduleRecord, "default");
            return;
        }

        String defaultBinding = clause.substring(0, commaIdx).trim();
        if (!defaultBinding.isEmpty()) {
            validateImportNameAgainstModuleRecord(moduleRecord, "default");
        }

        String remainder = clause.substring(commaIdx + 1).trim();
        if (remainder.startsWith("{")) {
            validateNamedImportSpecifiersAgainstModuleRecord(moduleRecord, remainder);
        }
    }

    void validateNamedImportSpecifiers(JSImportNamespaceObject namespace, String namedClause) {
        if (!namedClause.startsWith("{") || !namedClause.endsWith("}")) {
            return;
        }
        String specifiersText = namedClause.substring(1, namedClause.length() - 1).trim();
        if (specifiersText.isEmpty()) {
            return;
        }
        for (String rawSpecifier : transformer.splitOnTopLevelCommas(specifiersText)) {
            String specifier = rawSpecifier.trim();
            if (specifier.isEmpty()) {
                continue;
            }
            String importedName;
            int asIndex = transformer.findTopLevelAs(specifier);
            if (asIndex >= 0) {
                importedName = transformer.parseModuleExportNameValue(specifier.substring(0, asIndex).trim());
            } else {
                importedName = transformer.parseModuleExportNameValue(specifier);
            }
            PropertyKey importKey = PropertyKey.fromString(importedName);
            if (!namespace.has(importKey)) {
                throw new JSSyntaxErrorException(
                        "The requested module does not provide an export named '" + importedName + "'");
            }
        }
    }

    void validateNamedImportSpecifiersAgainstModuleRecord(
            JSDynamicImportModule moduleRecord,
            String namedClause) {
        if (!namedClause.startsWith("{") || !namedClause.endsWith("}")) {
            return;
        }
        String specifiersText = namedClause.substring(1, namedClause.length() - 1).trim();
        if (specifiersText.isEmpty()) {
            return;
        }
        for (String rawSpecifier : transformer.splitOnTopLevelCommas(specifiersText)) {
            String specifier = rawSpecifier.trim();
            if (specifier.isEmpty()) {
                continue;
            }
            String importedName;
            int asIndex = transformer.findTopLevelAs(specifier);
            if (asIndex >= 0) {
                importedName = transformer.parseModuleExportNameValue(specifier.substring(0, asIndex).trim());
            } else {
                importedName = transformer.parseModuleExportNameValue(specifier);
            }
            validateImportNameAgainstModuleRecord(moduleRecord, importedName);
        }
    }

    record DynamicImportExportResolution(
            JSDynamicImportModule moduleRecord,
            String bindingName,
            boolean ambiguous) {
        private static DynamicImportExportResolution ambiguousResolution() {
            return new DynamicImportExportResolution(null, null, true);
        }

        private static DynamicImportExportResolution notFoundResolution() {
            return new DynamicImportExportResolution(null, null, false);
        }

        private static DynamicImportExportResolution resolvedResolution(
                JSDynamicImportModule moduleRecord,
                String bindingName) {
            return new DynamicImportExportResolution(moduleRecord, bindingName, false);
        }

        private boolean found() {
            return moduleRecord != null;
        }
    }

    /**
     * What ECMAScript's ResolveExport answers with: a module, and a name within it.
     * <p>
     * Two fields rather than one delimited string. The delimited form needed a separator that could
     * not occur in either half, which meant a NUL — a byte that made the engine's largest source
     * file read as binary to ordinary search tooling. A pair needs no separator, and its
     * {@code equals} is exactly the binding identity that decides whether two {@code export *}
     * routes to a name are the same binding or an ambiguity.
     *
     * @param specifier the resolved specifier of the module the binding lives in
     * @param name      the name of the binding within that module
     */
    record LinkedBinding(String specifier, String name) {
    }

    /**
     * One name an import clause asks the exporting module for, and where it asked.
     *
     * @param name     the requested export name, decoded
     * @param location where the name was written, in the importing module's own source
     */
    record LinkedExportNameRequest(String name, SourceLocation location) {
    }

    /**
     * One static {@code import} declaration, as the link pass needs it.
     *
     * @param specifier     the module specifier, decoded
     * @param location      where the specifier was written, so a module that cannot be loaded can
     *                      be reported at the declaration that asked for it
     * @param attributes    the {@code with}/{@code assert} attributes, empty when there are none
     * @param importedNames the names asked for; empty for a namespace or side-effect import, which
     *                      still names a module that has to link
     */
    record LinkedModuleRequest(
            String specifier,
            SourceLocation location,
            Map<String, String> attributes,
            List<LinkedExportNameRequest> importedNames) {
    }

    /**
     * One run of the link check over one module graph.
     * <p>
     * A class rather than a set of methods because the pass is a small algorithm with state: the
     * records it has already parsed, and which module the caller actually handed to {@code eval},
     * both of which every step needs. It parses into records of its own and never touches the
     * module cache, so nothing it does is visible to the evaluation that follows.
     */
    final class ModuleLinkPass {
        private final Map<String, JSDynamicImportModule> linkRecords = new HashMap<>();
        private final String rootSpecifier;

        private ModuleLinkPass(String rootSpecifier) {
            this.rootSpecifier = rootSpecifier;
        }

        /**
         * Check every module one module names, and every name it asks them for, then do the same
         * for everything it reaches.
         * <p>
         * Every request is decided here, and none is deferred. The pass used to skip a request it
         * could not resolve, could not read, or that carried a {@code type} attribute, on the
         * grounds that evaluation would report it. Evaluation does — but evaluation reaches the
         * requests in source order, so by the time it reached the skipped one it had already run
         * the bodies of the modules named before it. A graph containing an unloadable module must
         * never begin evaluating, so a request this pass cannot satisfy fails here, at the
         * declaration that made it.
         *
         * @param moduleRecord the module being linked
         */
        private void checkModuleLinks(JSDynamicImportModule moduleRecord) {
            List<LinkedModuleRequest> moduleRequests = linkedModuleRequests(moduleRecord.rawSource());
            if (moduleRequests == null) {
                // Source that does not tokenise is the compiler's to reject, not this pass's.
                return;
            }
            for (LinkedModuleRequest moduleRequest : moduleRequests) {
                String resolvedSpecifier = requireModuleResolves(moduleRequest, moduleRecord);
                String moduleType = moduleRequest.attributes().get("type");
                if (isSyntheticModuleRequest(resolvedSpecifier, moduleType)) {
                    checkSyntheticModuleLinks(moduleRequest, resolvedSpecifier, moduleType, moduleRecord);
                    continue;
                }
                JSDynamicImportModule targetRecord =
                        requireJavaScriptModuleLinks(moduleRequest, resolvedSpecifier, moduleRecord);
                // A namespace import and `export *` ask for no particular name, but linking the
                // module they name is still what surfaces that module's own unresolvable imports.
                for (LinkedExportNameRequest requestedName : moduleRequest.importedNames()) {
                    requireExportResolves(
                            targetRecord,
                            requestedName.name(),
                            moduleRequest.specifier(),
                            moduleRecord,
                            requestedName.location());
                }
            }
        }

        /**
         * Check a request for a module whose exports the host manufactures rather than reading out
         * of JavaScript source: a {@code type: 'text'} or {@code type: 'bytes'} payload, or JSON.
         * <p>
         * Such a module provides exactly one export, {@code default}. The rules and the wording are
         * the ones evaluation already applies; the only thing that changes is that they are applied
         * before anything runs.
         *
         * @param moduleRequest     the declaration's request
         * @param resolvedSpecifier the resolved path of the payload
         * @param moduleType        the {@code type} attribute, or null when the declaration has none
         * @param importerRecord    the module whose declaration made the request
         */
        private void checkSyntheticModuleLinks(
                LinkedModuleRequest moduleRequest,
                String resolvedSpecifier,
                String moduleType,
                JSDynamicImportModule importerRecord) {
            String moduleKind;
            if ("text".equals(moduleType)) {
                moduleKind = "Text";
            } else if ("bytes".equals(moduleType)) {
                moduleKind = "Bytes";
            } else if ("json".equals(moduleType)) {
                moduleKind = "JSON";
            } else {
                // A .json payload is JSON whatever the declaration says, and evaluation refuses to
                // read one it was not told to expect.
                throw linkFailure(
                        "Import attribute type must be 'json'",
                        importerRecord,
                        moduleRequest.location(),
                        true);
            }
            requireModuleIsReadable(moduleRequest, resolvedSpecifier, importerRecord);
            for (LinkedExportNameRequest requestedName : moduleRequest.importedNames()) {
                if (!"default".equals(requestedName.name())) {
                    throw linkFailure(
                            moduleKind + " modules do not support named exports",
                            importerRecord,
                            requestedName.location(),
                            false);
                }
            }
        }

        /**
         * Whether a request names a module whose exports the host manufactures.
         * <p>
         * The order matters and mirrors the loader's: {@code type} chooses first, so a {@code .json}
         * payload imported as text is text.
         *
         * @param resolvedSpecifier the resolved path
         * @param moduleType        the {@code type} attribute, or null
         * @return true when the target is not JavaScript source
         */
        private boolean isSyntheticModuleRequest(String resolvedSpecifier, String moduleType) {
            return "text".equals(moduleType)
                    || "bytes".equals(moduleType)
                    || resolvedSpecifier.endsWith(".json");
        }

        /**
         * Build the error for a link failure, positioned at the declaration that caused it.
         * <p>
         * A {@code SourceLocation} is an offset into <em>some</em> source, and on its own it does
         * not say which. That is why a failure in a dependency used to carry no location at all: a
         * dependency's offsets attached to a bare exception would have read as offsets into the
         * text the caller passed to {@code eval}. The exception now carries the source's name
         * alongside its offsets, so a dependency's position can be reported as what it is — and the
         * embedder gets one structured diagnostic for a root failure and a transitive one alike,
         * instead of having to parse coordinates back out of a message.
         *
         * @param message         what went wrong
         * @param importerRecord  the module whose declaration made the request
         * @param requestLocation where the request was written, or null when it is not a source name
         * @param typeError       true for a module that cannot be loaded, false for one that links
         *                        but does not provide what was asked of it
         * @return the exception to throw
         */
        private JSException linkFailure(
                String message,
                JSDynamicImportModule importerRecord,
                SourceLocation requestLocation,
                boolean typeError) {
            String importerSpecifier = importerRecord.resolvedSpecifier();
            boolean importerIsEntryModule = rootSpecifier.equals(importerSpecifier);
            String reportedMessage = importerIsEntryModule || requestLocation == null
                    ? message
                    : message + " (imported by " + importerSpecifier
                      + ":" + requestLocation.line() + ":" + requestLocation.column() + ")";
            JSError error = typeError
                    ? context.throwTypeError(reportedMessage, requestLocation)
                    : context.throwSyntaxError(reportedMessage, requestLocation);
            // Which source the offsets belong to. Recorded on the error value rather than only on
            // the exception, because eval() re-wraps its pending exception on the way out.
            if (requestLocation != null) {
                error.setSourceName(importerSpecifier);
            }
            return new JSException(error);
        }

        /**
         * Parse and check the whole graph rooted at the entry module.
         *
         * @param sourceCode the entry module's source, as written
         */
        private void linkGraph(String sourceCode) {
            JSDynamicImportModule rootRecord =
                    new JSDynamicImportModule(rootSpecifier, context.moduleLoader().createModuleNamespaceObject());
            rootRecord.setStatus(JSDynamicImportModule.Status.LOADING);
            rootRecord.setRawSource(sourceCode);
            transformer.parseDynamicImportModuleSource(rootRecord);
            linkRecords.put(rootSpecifier, rootRecord);
            checkModuleLinks(rootRecord);
        }

        /**
         * Parse one module for linking, without evaluating it.
         * <p>
         * A module already in {@link #linkRecords} is returned as it stands, including one whose own
         * links are still being checked further up the stack. That is what makes a cycle
         * resolvable: its exports are known as soon as its source has been parsed, which happens
         * before anything it imports is looked at, so the module on the other side of a cycle can
         * be asked for a name even though it is only half linked. Declining to answer for a cycle
         * meant a graph with a genuinely unresolvable name across one ran its dependencies before
         * saying so.
         *
         * @param specifier the specifier as written
         * @param filename  the importing module's name
         * @return the parsed record, or null when the specifier is not a JavaScript module this
         * pass can read
         */
        private JSDynamicImportModule linkModule(String specifier, String filename) {
            String resolvedSpecifier;
            try {
                resolvedSpecifier = context.moduleLoader().resolveDynamicImportSpecifier(specifier, filename, specifier);
            } catch (JSException unresolvable) {
                context.clearPendingException();
                return null;
            }
            JSDynamicImportModule existingRecord = linkRecords.get(resolvedSpecifier);
            if (existingRecord != null) {
                return existingRecord;
            }
            if (resolvedSpecifier.endsWith(".json")) {
                return null;
            }
            String dependencySource = readModuleSource(resolvedSpecifier);
            if (dependencySource == null) {
                return null;
            }
            return loadLinkRecord(resolvedSpecifier, dependencySource);
        }

        /**
         * Parse one module into a link record and check what it, in turn, names.
         * <p>
         * ECMAScript parses every module in the graph while loading it, so a module anywhere in the
         * graph that is not valid source is a failure of the whole graph before any of it runs.
         * Without this the engine compiled each dependency as it reached it, so the dependencies
         * ahead of a broken one had already been evaluated.
         * <p>
         * The record goes into {@link #linkRecords} before its own links are checked, which is what
         * makes a cycle resolvable: a module's exports are known as soon as its source has been
         * parsed, so the module on the other side of a cycle can be asked for a name even though it
         * is only half linked.
         *
         * @param resolvedSpecifier the module's resolved path
         * @param dependencySource  its source, as written
         * @return the link record
         */
        private JSDynamicImportModule loadLinkRecord(String resolvedSpecifier, String dependencySource) {
            transformer.requireDependencyModuleSourceCompiles(dependencySource, resolvedSpecifier);
            JSDynamicImportModule linkRecord =
                    new JSDynamicImportModule(resolvedSpecifier, context.moduleLoader().createModuleNamespaceObject());
            linkRecord.setStatus(JSDynamicImportModule.Status.LOADING);
            linkRecord.setRawSource(dependencySource);
            transformer.parseDynamicImportModuleSource(linkRecord);
            linkRecords.put(resolvedSpecifier, linkRecord);
            checkModuleLinks(linkRecord);
            return linkRecord;
        }

        /**
         * A module's source, or null when it cannot be read.
         *
         * @param resolvedSpecifier the resolved path
         * @return the source, or null
         */
        private String readModuleSource(String resolvedSpecifier) {
            try {
                return Files.readString(Path.of(resolvedSpecifier));
            } catch (IOException | RuntimeException unreadable) {
                return null;
            }
        }

        /**
         * Require that a module being linked resolves an export name to exactly one binding.
         *
         * @param moduleRecord    the exporting module
         * @param exportName      the name asked for
         * @param specifier       the specifier the importer wrote
         * @param importerRecord  the module that asked
         * @param requestLocation where the name was written, or null when it is not a source name
         */
        private void requireExportResolves(
                JSDynamicImportModule moduleRecord,
                String exportName,
                String specifier,
                JSDynamicImportModule importerRecord,
                SourceLocation requestLocation) {
            LinkedBinding binding = resolveExport(moduleRecord, exportName, new HashSet<>());
            if (binding == UNKNOWN_LINKED_EXPORT) {
                // A star target this pass could not read may still provide the name. Evaluation
                // reports it if it does not.
                return;
            }
            if (binding == AMBIGUOUS_LINKED_EXPORT) {
                throw linkFailure(
                        "The requested module '" + specifier
                                + "' contains conflicting star exports for the name '" + exportName + "'",
                        importerRecord,
                        requestLocation,
                        false);
            }
            if (binding == null) {
                throw linkFailure(
                        "The requested module '" + specifier
                                + "' does not provide an export named '" + exportName + "'",
                        importerRecord,
                        requestLocation,
                        false);
            }
        }

        /**
         * Load, parse and link the JavaScript module a request names, or fail at the request.
         *
         * @param moduleRequest     the declaration's request
         * @param resolvedSpecifier the resolved path
         * @param importerRecord    the module whose declaration made the request
         * @return the linked record, never null
         */
        private JSDynamicImportModule requireJavaScriptModuleLinks(
                LinkedModuleRequest moduleRequest,
                String resolvedSpecifier,
                JSDynamicImportModule importerRecord) {
            JSDynamicImportModule existingRecord = linkRecords.get(resolvedSpecifier);
            if (existingRecord != null) {
                return existingRecord;
            }
            String dependencySource = readModuleSource(resolvedSpecifier);
            if (dependencySource == null) {
                throw linkFailure(
                        "Cannot find module '" + resolvedSpecifier + "'",
                        importerRecord,
                        moduleRequest.location(),
                        true);
            }
            return loadLinkRecord(resolvedSpecifier, dependencySource);
        }

        /**
         * Require that a payload the host will read is actually readable.
         *
         * @param moduleRequest     the declaration's request
         * @param resolvedSpecifier the resolved path
         * @param importerRecord    the module whose declaration made the request
         */
        private void requireModuleIsReadable(
                LinkedModuleRequest moduleRequest,
                String resolvedSpecifier,
                JSDynamicImportModule importerRecord) {
            // isReadable alone is true of a directory, and a directory is not a payload: the read
            // would fail at evaluation, which is the ordering this pass exists to prevent.
            Path payloadPath = Path.of(resolvedSpecifier);
            if (!Files.isRegularFile(payloadPath) || !Files.isReadable(payloadPath)) {
                throw linkFailure(
                        "Cannot find module '" + resolvedSpecifier + "'",
                        importerRecord,
                        moduleRequest.location(),
                        true);
            }
        }

        /**
         * Resolve a request's specifier to a path, or fail at the request.
         *
         * @param moduleRequest  the declaration's request
         * @param importerRecord the module whose declaration made the request
         * @return the resolved specifier
         */
        private String requireModuleResolves(
                LinkedModuleRequest moduleRequest,
                JSDynamicImportModule importerRecord) {
            try {
                return context.moduleLoader().resolveDynamicImportSpecifier(
                        moduleRequest.specifier(),
                        importerRecord.resolvedSpecifier(),
                        moduleRequest.specifier());
            } catch (JSException unresolvable) {
                // Rebuilt rather than rethrown, so it carries the position of the declaration that
                // named the module instead of no position at all.
                context.clearPendingException();
                throw linkFailure(
                        "Cannot find module '" + moduleRequest.specifier() + "'",
                        importerRecord,
                        moduleRequest.location(),
                        true);
            }
        }

        /**
         * The binding a module's export name resolves to, following {@code export * from} and
         * indirect re-exports.
         * <p>
         * ECMAScript's ResolveExport answers with a <em>binding</em> — a module and a name within
         * it — and that is what makes ambiguity decidable. Two {@code export *} targets providing a
         * name are ambiguous only when they provide different bindings; two routes to the same one
         * are not, which is why {@code export * as foo from './m.js'} in one and
         * {@code import * as foo from './m.js'; export \{ foo \}} in the other is legal. An earlier
         * version of this answered with the module a name came from, could not tell those apart,
         * and so declined to call anything ambiguous — and the graph ran.
         *
         * @param moduleRecord the exporting module
         * @param exportName   the name the importer asked for
         * @param resolveSet   the (module, name) requests already in progress, so a cycle terminates
         * @return the binding the name resolves to, {@link #AMBIGUOUS_LINKED_EXPORT},
         * {@link #UNKNOWN_LINKED_EXPORT}, or null when nothing provides the name
         */
        private LinkedBinding resolveExport(
                JSDynamicImportModule moduleRecord,
                String exportName,
                Set<LinkedBinding> resolveSet) {
            if (!resolveSet.add(new LinkedBinding(moduleRecord.resolvedSpecifier(), exportName))) {
                // This module has already been asked for this name further up the recursion: a
                // circular request, which resolves to nothing rather than to a second binding.
                return null;
            }
            for (JSDynamicImportModule.LocalExportBinding localExportBinding
                    : moduleRecord.localExportBindings()) {
                if (exportName.equals(localExportBinding.exportedName())) {
                    return new LinkedBinding(
                            moduleRecord.resolvedSpecifier(), localExportBinding.localName());
                }
            }
            for (JSDynamicImportModule.ReExportBinding reExportBinding : moduleRecord.reExportBindings()) {
                if (reExportBinding.starExport() || !exportName.equals(reExportBinding.exportedName())) {
                    continue;
                }
                JSDynamicImportModule target =
                        linkModule(reExportBinding.sourceSpecifier(), moduleRecord.resolvedSpecifier());
                if (target == null) {
                    return UNKNOWN_LINKED_EXPORT;
                }
                if (ModuleSourceTransformer.MODULE_NAMESPACE_EXPORT_NAME.equals(reExportBinding.importedName())) {
                    // The binding is the target's namespace object, which every route to that
                    // module shares.
                    return new LinkedBinding(
                            target.resolvedSpecifier(), ModuleSourceTransformer.MODULE_NAMESPACE_EXPORT_NAME);
                }
                return resolveExport(target, reExportBinding.importedName(), resolveSet);
            }
            if (moduleRecord.explicitExportNames().contains(exportName)
                    || moduleRecord.namespace().hasExportName(exportName)) {
                // A name the parse recorded without a binding to go with it. Treating it as the
                // module's own is what keeps this pass from rejecting something it cannot model.
                return new LinkedBinding(moduleRecord.resolvedSpecifier(), exportName);
            }
            // `export *` never re-exports `default`.
            if ("default".equals(exportName)) {
                return null;
            }
            LinkedBinding starBinding = null;
            for (JSDynamicImportModule.ReExportBinding reExportBinding : moduleRecord.reExportBindings()) {
                if (!reExportBinding.starExport()) {
                    continue;
                }
                JSDynamicImportModule starTarget =
                        linkModule(reExportBinding.sourceSpecifier(), moduleRecord.resolvedSpecifier());
                if (starTarget == null) {
                    // A star target this pass cannot read may still provide the name at evaluation
                    // time, so an unreadable one is not evidence either way.
                    return UNKNOWN_LINKED_EXPORT;
                }
                LinkedBinding binding = resolveExport(starTarget, exportName, resolveSet);
                if (binding == null) {
                    continue;
                }
                if (binding == UNKNOWN_LINKED_EXPORT || binding == AMBIGUOUS_LINKED_EXPORT) {
                    return binding;
                }
                if (starBinding == null) {
                    starBinding = binding;
                } else if (!starBinding.equals(binding)) {
                    return AMBIGUOUS_LINKED_EXPORT;
                }
            }
            return starBinding;
        }
    }
}
