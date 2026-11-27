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

import com.caoccao.qjs4j.core.module.ModuleSourceScan;
import com.caoccao.qjs4j.core.module.ModuleSourceText;
import com.caoccao.qjs4j.exceptions.JSException;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static com.caoccao.qjs4j.core.ModuleSourceTransformer.*;

/** Builds one module's evaluation script and export records from the same declaration scan. */
final class ModuleSourceRewrite {
    private static final Pattern AWAIT = Pattern.compile("\\bawait\\b");
    private final JSContext context;
    private final Set<Integer> declarationLines;
    private final Map<Integer, ModuleSourceScan.DefaultExportExtent> defaultExportExtents;
    private int defaultExportIndex;
    private boolean hasExportSyntax;
    private final List<JSDynamicImportModule.HoistedFunctionExportBinding> hoistedFunctionExportBindings = new ArrayList<>();
    private final Set<String> importedBindingNames = new HashSet<>();
    private final Map<String, ImportBinding> importedBindings = new HashMap<>();
    private final StringBuilder imports = new StringBuilder();
    private int lineIndex;
    private final String[] lines;
    private final List<JSDynamicImportModule.LocalExportBinding> localExportBindings = new ArrayList<>();
    private final JSDynamicImportModule moduleRecord;
    private final StringBuilder nameFixups = new StringBuilder();
    private final StringBuilder output;
    private final String prefix;
    private final List<JSDynamicImportModule.ReExportBinding> reExportBindings = new ArrayList<>();
    private final String[] scanLines;
    private final String scanSourceCode;
    private final String sourceCode;

    private final ModuleSourceTransformer transformer;

    ModuleSourceRewrite(JSContext context, ModuleSourceTransformer transformer, JSDynamicImportModule moduleRecord,
            String sourceCode, ModuleSourceScan analysis) {
        this.context = context;
        this.transformer = transformer;
        this.moduleRecord = moduleRecord;
        this.sourceCode = sourceCode;
        scanSourceCode = ModuleSourceScan.maskModuleComments(sourceCode);
        prefix = analysis.generatedModuleNamePrefix();
        declarationLines = analysis.moduleDeclarationLineIndices();
        defaultExportExtents = analysis.defaultExportExtents();
        lines = moduleLines(sourceCode);
        scanLines = moduleLines(scanSourceCode);
        output = new StringBuilder(sourceCode.length() + 128);
    }

    private void addLocal(String name, String exportedName) {
        localExportBindings.add(new JSDynamicImportModule.LocalExportBinding(name, exportedName));
    }

    private void appendDefaultDeclaration(ExportDeclaration declaration, String name, boolean anonymous,
            boolean isClass) {
        if (!anonymous && !isClass) {
            output.append(declaration.source()).append('\n');
            return;
        }
        String part = declaration.declaration();
        if (isClass) {
            String binding = name;
            if (anonymous) {
                // Set the name before static field initializers run.
                part = nameAnonymousDefaultClass(part, name);
            } else {
                binding = transformer.requireGeneratedIdentifier(name, "default export declaration name '" + name + "'",
                        moduleRecord.resolvedSpecifier());
            }
            output.append("let ").append(binding).append(" = ").append(part).append(";\n");
            if (anonymous) {
                appendNameFixup(output, name);
            }
        } else {
            output.append(transformer.renameAnonymousDefaultExportDeclaration(part, name)).append('\n');
            appendNameFixup(nameFixups, name);
        }
        declaration.appendTrailingCode(output);
    }

    private void appendExportAssignments(StringBuilder preamble, String exportBindingName) {
        String moduleExportsName = prefix + "ModuleExports";
        preamble.append("const ").append(moduleExportsName).append(" = ").append(exportBindingName).append(";\n");
        for (JSDynamicImportModule.LocalExportBinding localExportBinding : localExportBindings) {
            String escapedName = ModuleSourceText.escapeJavaScriptString(localExportBinding.exportedName());
            // The exported name lands in string position, so escaping is enough. The local name
            // lands in identifier position, where only a real identifier is safe.
            String localName = transformer.requireGeneratedIdentifier(localExportBinding.localName(),
                    "local binding of export '" + localExportBinding.exportedName() + "'",
                    moduleRecord.resolvedSpecifier());
            // Exports are always live bindings, including re-exported imports.
            preamble.append("Object.defineProperty(").append(moduleExportsName).append(", \"").append(escapedName)
                    .append("\", { enumerable: true, configurable: false, get() { return ").append(localName)
                    .append("; } });\n");
        }
    }

    private void appendNameFixup(StringBuilder target, String rawLocalName) {
        String localName = transformer.requireGeneratedIdentifier(rawLocalName,
                "default export binding '" + rawLocalName + "'", moduleRecord.resolvedSpecifier());
        target.append("if (typeof ").append(localName)
                .append(" === \"function\" && (!Object.prototype.hasOwnProperty.call(").append(localName)
                .append(", \"name\") || ").append(localName).append(".name === \"\" || ").append(localName)
                .append(".name === \"").append(localName).append("\")) {\n").append("  Object.defineProperty(")
                .append(localName).append(", \"name\", { value: \"default\", configurable: true });\n").append("}\n");
    }

    private void finishSource() {
        boolean hasTLA = MODULE_TOP_LEVEL_AWAIT_PATTERN.matcher(output).find() || AWAIT.matcher(scanSourceCode).find();
        moduleRecord.setHasTLA(hasTLA);
        if (!hasExportSyntax && importedBindingNames.isEmpty() && !hasTLA) {
            moduleRecord.setTransformedSource(sourceCode);
            return;
        }
        // Install live export getters before evaluating the body, including through cycles.
        imports.append(hasTLA ? "(async function() {\n" : "(function() {\n");
        if (hasExportSyntax) {
            String name = transformer.createModuleExportBindingName(prefix, moduleRecord.resolvedSpecifier());
            appendExportAssignments(imports, name);
            imports.append(nameFixups);
            moduleRecord.setExportBindingName(name);
        }
        moduleRecord.setTransformedSource(imports.append(output).append("})();\n").toString());
    }

    private String importSource(String fromClause) {
        if (!fromClause.startsWith("from ")) {
            throw invalidExport();
        }
        String from = ModuleSourceText.stripTrailingSemicolons(fromClause.substring(5).trim());
        String specifier = transformer.stripQuotedSpecifier(from);
        imports.append("import '").append(specifier).append("';\n");
        return specifier;
    }

    private JSException invalidExport() {
        return new JSException(context.throwSyntaxError("Invalid export statement"));
    }

    private String nextDefaultName() {
        return prefix + "DefaultExport$" + defaultExportIndex++;
    }

    private void publishBindings() {
        moduleRecord.setHasExportSyntax(hasExportSyntax);
        moduleRecord.hoistedFunctionExportBindings().clear();
        moduleRecord.hoistedFunctionExportBindings().addAll(hoistedFunctionExportBindings);
        moduleRecord.setHoistedFunctionExportBindingsInitialized(false);
        moduleRecord.localExportBindings().addAll(localExportBindings);
        moduleRecord.reExportBindings().addAll(reExportBindings);
        for (JSDynamicImportModule.LocalExportBinding localExportBinding : localExportBindings) {
            registerExport(localExportBinding.exportedName());
            moduleRecord.exportOrigins().put(localExportBinding.exportedName(), moduleRecord.resolvedSpecifier());
        }
        for (JSDynamicImportModule.ReExportBinding reExportBinding : reExportBindings) {
            if (reExportBinding.starExport()) {
                continue;
            }
            registerExport(reExportBinding.exportedName());
        }
    }

    private void readDefault(String normalizedLine, String defaultClause) {
        if (transformer.isDynamicImportDefaultDeclarationClause(defaultClause)) {
            String declarationName = transformer.extractExportedFunctionOrClassName(defaultClause);
            String declarationLine = normalizedLine.replaceFirst("^(\\s*)export\\s+default\\s+", "$1");
            ExportDeclaration declaration = readExportDeclaration(declarationLine);

            boolean anonymous = declarationName == null || declarationName.isEmpty();
            if (anonymous) {
                declarationName = nextDefaultName();
            }
            appendDefaultDeclaration(declaration, declarationName, anonymous, defaultClause.startsWith("class"));
            addLocal(declarationName, "default");
        } else {
            // An expression may run past the end of its line, and where it ends is a
            // grammar question rather than a bracket count — see defaultExportExtents.
            String defaultExpression;
            String trailingCode = "";
            ModuleSourceScan.DefaultExportExtent defaultExportExtent = defaultExportExtents.get(lineIndex);
            if (defaultExportExtent != null) {
                defaultExpression = defaultExportExtent.expression();
                trailingCode = defaultExportExtent.trailingText();
                lineIndex = Math.max(lineIndex, defaultExportExtent.endLineIndex());
            } else {
                // The parser could not be asked — source that does not tokenise, or a shape
                // it declined. Fall back to taking lines while the delimiters are unbalanced.
                defaultExpression = defaultClause;
                while (!transformer.isBalancedExpressionText(defaultExpression) && lineIndex + 1 < lines.length) {
                    lineIndex++;
                    String continuationLine = lines[lineIndex];
                    defaultExpression = defaultExpression + "\n" + continuationLine;
                }
                defaultExpression = defaultExpression.trim();
                defaultExpression = ModuleSourceText.stripTrailingSemicolons(defaultExpression);
            }
            String defaultLocalName = nextDefaultName();
            output.append("let ").append(defaultLocalName).append(" = (0, ").append(defaultExpression).append(");\n");
            appendNameFixup(output, defaultLocalName);
            if (!trailingCode.isEmpty()) {
                output.append(trailingCode).append('\n');
            }
            addLocal(defaultLocalName, "default");
        }
    }

    private void readExport(String normalizedLine, String exportClause) {
        // If the export clause is empty (bare 'export', 'export' with trailing comments/whitespace),
        // look ahead to subsequent lines for the continuation.
        if (exportClause.isEmpty()) {
            StringBuilder exportContinuation = new StringBuilder();
            while (lineIndex + 1 < lines.length) {
                lineIndex++;
                String normalizedNextScanLine = scanLines[lineIndex];
                exportContinuation.append(normalizedNextScanLine.stripLeading());
                if (exportContinuation.indexOf("}") >= 0 || exportContinuation.indexOf("*") >= 0) {
                    break;
                }
            }
            exportClause = exportContinuation.toString().trim();
        }
        if (exportClause.startsWith("default ")) {
            readDefault(normalizedLine, exportClause.substring("default ".length()).trim());
        } else if (exportClause.startsWith("var ") || exportClause.startsWith("let ")
                || exportClause.startsWith("const ")) {
            output.append(normalizedLine.replaceFirst("export\\s+", "")).append('\n');
            for (String name : transformer.extractSimpleDeclarationNames(exportClause)) {
                addLocal(name, name);
            }
        } else if (isFunctionClause(exportClause) || exportClause.startsWith("class ")) {
            readNamedDeclaration(normalizedLine, exportClause);
        } else if (exportClause.startsWith("{")) {
            readExportList(exportClause);
        } else if (exportClause.startsWith("*")) {
            readStarExport(exportClause.substring(1).trim());
        } else {
            throw new JSException(context.throwSyntaxError("Unexpected export syntax"));
        }
    }

    private ExportDeclaration readExportDeclaration(String firstLine) {
        String source = firstLine;
        int end = ModuleSourceText.findEndOfDeclarationBody(source);
        while (end < 0 && lineIndex + 1 < lines.length) {
            source += "\n" + lines[++lineIndex];
            end = ModuleSourceText.findEndOfDeclarationBody(source);
        }
        return new ExportDeclaration(source, end);
    }

    private void readExportList(String exportClause) {
        String exportSpecifiersText = exportClause;
        while (ModuleSourceText.findMatchingCloseBrace(exportSpecifiersText, 0) < 0 && lineIndex + 1 < lines.length) {
            lineIndex++;
            String normalizedNextScanLine = scanLines[lineIndex];
            exportSpecifiersText = exportSpecifiersText + "\n" + normalizedNextScanLine.stripLeading();
        }

        int closeBraceIndex = ModuleSourceText.findMatchingCloseBrace(exportSpecifiersText, 0);
        if (closeBraceIndex < 0) {
            throw invalidExport();
        }
        String exportListText = exportSpecifiersText.substring(1, closeBraceIndex).trim();
        String afterBraceText = exportSpecifiersText.substring(closeBraceIndex + 1).trim();
        afterBraceText = ModuleSourceText.stripTrailingSemicolons(afterBraceText);
        String sourceSpecifier = null;
        if (!afterBraceText.isEmpty()) {
            sourceSpecifier = importSource(afterBraceText);
        }
        int localBindingStartIndex = localExportBindings.size();
        transformer.parseDynamicImportExportList(exportListText, sourceSpecifier, localExportBindings,
                reExportBindings);
        if (sourceSpecifier == null && localBindingStartIndex < localExportBindings.size()) {
            for (int localBindingIndex = localExportBindings.size()
                    - 1; localBindingIndex >= localBindingStartIndex; localBindingIndex--) {
                JSDynamicImportModule.LocalExportBinding localExportBinding = localExportBindings
                        .get(localBindingIndex);
                ImportBinding importBinding = importedBindings.get(localExportBinding.localName());
                if (importBinding == null || importBinding.deferredImport()) {
                    continue;
                }
                localExportBindings.remove(localBindingIndex);
                reExportBindings.add(new JSDynamicImportModule.ReExportBinding(importBinding.sourceSpecifier(),
                        importBinding.importedName(), localExportBinding.exportedName(), false));
            }
        }
    }

    private void readImport(String normalizedLine, String parseLine) {
        StringBuilder importStatementBuilder = new StringBuilder(normalizedLine);
        StringBuilder importStatementScanBuilder = new StringBuilder(parseLine);
        while (!transformer.isCompleteStaticImportStatement(importStatementScanBuilder.toString())
                && lineIndex + 1 < lines.length) {
            lineIndex++;
            String normalizedNextLine = lines[lineIndex];
            String normalizedNextScanLine = scanLines[lineIndex];
            importStatementBuilder.append('\n').append(normalizedNextLine);
            importStatementScanBuilder.append('\n').append(normalizedNextScanLine);
        }
        String importStatementSource = importStatementBuilder.toString();
        String importStatementForScan = importStatementScanBuilder.toString().strip();
        imports.append(importStatementSource).append('\n');
        transformer.collectImportBindings(importStatementForScan, importedBindingNames, importedBindings);
    }

    private void readNamedDeclaration(String normalizedLine, String exportClause) {
        String declarationLine = normalizedLine.replaceFirst("^(\\s*)export\\s+", "$1");
        ExportDeclaration declaration = readExportDeclaration(declarationLine);

        String declarationPart = declaration.declaration();
        output.append(declarationPart).append('\n');
        declaration.appendTrailingCode(output);
        String declarationName = transformer.extractExportedFunctionOrClassName(exportClause);
        if (declarationName == null || declarationName.isEmpty()) {
            throw invalidExport();
        }
        addLocal(declarationName, declarationName);
        if (isFunctionClause(exportClause)) {
            hoistedFunctionExportBindings.add(new JSDynamicImportModule.HoistedFunctionExportBinding(declarationName,
                    declarationName, declarationPart));
        }
    }

    private void readStarExport(String afterStarText) {
        if (afterStarText.startsWith("as ")) {
            // Handle both identifier and string literal export names:
            // export * as name from '...'
            // export * as "name" from '...'
            String afterAs = afterStarText.substring(3).trim();
            String exportedName;
            String remainingAfterName;
            if (afterAs.startsWith("\"") || afterAs.startsWith("'")) {
                char quote = afterAs.charAt(0);
                int closeQuote = afterAs.indexOf(quote, 1);
                if (closeQuote < 0) {
                    throw invalidExport();
                }
                exportedName = afterAs.substring(1, closeQuote);
                remainingAfterName = afterAs.substring(closeQuote + 1).trim();
            } else {
                Matcher identMatcher = Pattern.compile("^([A-Za-z_$][A-Za-z0-9_$]*)\\s+(.*)$").matcher(afterAs);
                if (!identMatcher.find()) {
                    throw invalidExport();
                }
                exportedName = identMatcher.group(1);
                remainingAfterName = identMatcher.group(2).trim();
            }
            String sourceSpecifier = importSource(remainingAfterName);
            reExportBindings.add(new JSDynamicImportModule.ReExportBinding(sourceSpecifier,
                    MODULE_NAMESPACE_EXPORT_NAME, exportedName, false));
            return;
        }
        if (afterStarText.startsWith("from ")) {
            String sourceSpecifier = importSource(afterStarText);
            reExportBindings.add(new JSDynamicImportModule.ReExportBinding(sourceSpecifier, "*", "*", true));
            return;
        }
        throw invalidExport();
    }

    private void registerExport(String name) {
        moduleRecord.explicitExportNames().add(name);
        moduleRecord.namespace().registerExportName(name);
    }

    void rewrite() {
        for (lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            String masked = scanLines[lineIndex];
            String trimmed = masked.stripLeading();
            boolean declaration = declarationLines == null || declarationLines.contains(lineIndex);
            if (declaration && transformer.isStaticImportLine(trimmed)) {
                readImport(line, masked);
            } else if (declaration && (trimmed.startsWith("export ") || trimmed.startsWith("export{")
                    || trimmed.startsWith("export*") || trimmed.equals("export"))) {
                hasExportSyntax = true;
                readExport(line, trimmed.substring("export".length()).trim());
            } else {
                output.append(line).append('\n');
            }
        }
        publishBindings();
        finishSource();
    }

    private static boolean isFunctionClause(String clause) {
        return clause.startsWith("function ") || clause.startsWith("function*") || clause.startsWith("async function ")
                || clause.startsWith("async function*");
    }

    private static String[] moduleLines(String source) {
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.endsWith("\r")) {
                lines[i] = line.substring(0, line.length() - 1);
            }
        }
        return lines;
    }

    private static String nameAnonymousDefaultClass(String declarationPart, String defaultLocalName) {
        int openBrace = ModuleSourceText.findLikelyClassBodyOpenBrace(declarationPart);
        if (openBrace >= 0) {
            // ES2024 15.2.3.11: Only set name to "default" if the class
            // doesn't already have a "name" own property (e.g. static name method).
            declarationPart = declarationPart.substring(0, openBrace + 1)
                    + " static { if (!Object.prototype.hasOwnProperty.call(this, 'name')" + " || this.name === ''"
                    + " || this.name === '" + defaultLocalName + "') { "
                    + "Object.defineProperty(this, 'name', {value: 'default', configurable: true}); } }"
                    + declarationPart.substring(openBrace + 1);
        }
        return declarationPart;
    }

    private record ExportDeclaration(String source, int end) {
        String declaration() {
            return end < 0 ? source : source.substring(0, end);
        }

        void appendTrailingCode(StringBuilder output) {
            if (end >= 0 && end < source.length()) {
                String trailing = source.substring(end).trim();
                if (!trailing.isEmpty()) {
                    output.append(trailing).append('\n');
                }
            }
        }
    }

}
