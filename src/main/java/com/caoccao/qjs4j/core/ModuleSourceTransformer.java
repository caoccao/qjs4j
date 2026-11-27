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
import com.caoccao.qjs4j.compilation.compiler.Compiler;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.exceptions.JSErrorException;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

import java.nio.file.Path;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites ES module source into plain script source, and answers every question about that source the rest of the
 * module machinery asks: which lines begin a declaration, where an {@code export default} expression ends, what a
 * specifier or an export name really spells.
 * <p>
 * This is the textual half of module support, kept together so it can be read — and tested — without the loader, the
 * linker or the realm around it. Almost everything here is a function from strings and tokens to strings and tokens;
 * the only realm state it touches is the context it reports syntax errors through, the compiler it validates source
 * with, and two callbacks it needs to name a generated binding and to recognise a module importing itself.
 * <p>
 * The transform itself is documented on {@link #parseDynamicImportModuleSource}, including what it still gets wrong and
 * why the fix is real module records rather than better scanning. QuickJS does this differently —
 * {@code js_create_module_def} reads import and export entries out of the parsed function rather than rewriting text —
 * and moving to that is the intended destination; this class exists so that it can be replaced behind the loader and
 * linker seams instead of unpicked from {@link JSContext}.
 */
final class ModuleSourceTransformer {
    static final Pattern DYNAMIC_IMPORT_EXPORT_CLASS_NAME_PATTERN = Pattern
            .compile("^class\\s+(" + ModuleSourceTransformer.IDENTIFIER_NAME_REGEX + ")");

    static final Pattern DYNAMIC_IMPORT_EXPORT_FUNCTION_NAME_PATTERN = Pattern
            .compile("^(?:async\\s+)?function(?:\\s*\\*)?\\s+(" + ModuleSourceTransformer.IDENTIFIER_NAME_REGEX + ")");

    /**
     * An ECMAScript {@code IdentifierName}, as a regular-expression fragment.
     * <p>
     * The module transformer used to re-extract binding names with {@code [A-Za-z_$][A-Za-z0-9_$]*}, which is not the
     * identifier grammar the lexer three files away accepts. {@code café} and {@code módulo} are ordinary identifiers;
     * so is {@code \\u0078}, which <em>is</em> {@code x}. The parser took all of them and the transformer did not, so
     * valid source was handed to one component that accepted it and another that assigned it different semantics — a
     * module exporting {@code café} reported no such export, and a namespace bound to {@code módulo} was not defined
     * when the module body ran.
     * <p>
     * Deliberately a little wider than the real grammar: whatever this matches is decoded by
     * {@link ModuleSourceText#decodeIdentifierEscapes(String)} and then checked against the Unicode tables by
     * {@link ModuleSourceText#isValidIdentifierName(String)}, which is the authority. Matching slightly too much costs
     * a rejection with a message; matching too little silently loses a binding.
     * <p>
     * The repetition is possessive for the reason given on {@link #MODULE_SPECIFIER_BODY_REGEX}.
     */
    static final String IDENTIFIER_NAME_REGEX = "(?:[\\p{L}\\p{Nl}$_]|\\\\u[0-9A-Fa-f]{4}|\\\\u\\{[0-9A-Fa-f]{1,6}\\})"
            + "(?:[\\p{L}\\p{Nl}\\p{Mn}\\p{Mc}\\p{Nd}\\p{Pc}$_\\u200C\\u200D]"
            + "|\\\\u[0-9A-Fa-f]{4}|\\\\u\\{[0-9A-Fa-f]{1,6}\\})*+";

    static final Pattern MODULE_BINDING_IMPORT_PATTERN = Pattern.compile(
            "(?m)^\\s*import\\s*([^;]*?)\\s+from\\s+(['\"])(" + ModuleSourceTransformer.MODULE_SPECIFIER_BODY_REGEX
                    + ")\\2(?:\\s+with\\s*\\{[^}]*\\})?\\s*;?\\s*$");

    static final Pattern MODULE_EXPORT_SYNTAX_PATTERN = Pattern.compile("(?m)^\\s*export\\s");

    /**
     * The pseudo export name a namespace binding is recorded under — {@code import * as ns} and
     * {@code export * as ns from}, whose binding is a module's namespace object rather than a name that module exports.
     */
    static final String MODULE_NAMESPACE_EXPORT_NAME = "*namespace*";

    static final Pattern MODULE_NAMESPACE_IMPORT_PATTERN = Pattern
            .compile("(?m)^\\s*import\\s*(?:(defer)\\s+)?\\*\\s*as\\s+(" + ModuleSourceTransformer.IDENTIFIER_NAME_REGEX
                    + ")\\s+from\\s+(['\"])(" + ModuleSourceTransformer.MODULE_SPECIFIER_BODY_REGEX
                    + ")\\3(?:\\s+with\\s*\\{[^}]*\\})?\\s*;?\\s*$");

    static final Pattern MODULE_SIDE_EFFECT_IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s*(['\"])("
            + ModuleSourceTransformer.MODULE_SPECIFIER_BODY_REGEX + ")\\1(?:\\s+with\\s*\\{[^}]*\\})?\\s*;?\\s*$");

    /**
     * The body of a module specifier's string literal, as a regular-expression fragment.
     * <p>
     * A backslash escapes whatever follows it, so a quote character does not necessarily end the literal and a line
     * terminator does not necessarily end the declaration. The previous {@code [^'"\r\n]+} could express neither:
     * {@code './a\'b.mjs'} matched nothing at all, and so did a specifier written across a LineContinuation.
     * <p>
     * The repetition is possessive. A greedy repetition of an alternation is matched by recursion, one Java frame per
     * character, so a long specifier overflowed the stack; giving characters back could never help anyway, because the
     * body cannot match the quote that has to follow it.
     */
    static final String MODULE_SPECIFIER_BODY_REGEX = "(?:\\\\[\\s\\S]|[^'\"\\\\\\r\\n])++";

    static final Pattern MODULE_STATIC_IMPORT_PATTERN = Pattern
            .compile("(?m)^\\s*import\\s*(?:defer\\s+)?(?:[^;]*?\\s+from\\s+)?['\"]("
                    + ModuleSourceTransformer.MODULE_SPECIFIER_BODY_REGEX
                    + ")['\"](?:\\s+with\\s*\\{[^}]*\\})?\\s*;?\\s*$");

    static final Pattern MODULE_STATIC_IMPORT_SYNTAX_PATTERN = Pattern.compile("(?m)^\\s*import(?!\\s*\\(|\\s*\\.)");

    static final Pattern MODULE_TOP_LEVEL_AWAIT_PATTERN = Pattern.compile("(?m)^\\s*await\\b");

    static final Pattern MODULE_WITH_CLAUSE_PATTERN = Pattern.compile("with\\s*\\{([^}]*)\\}");

    private final JSContext context;

    ModuleSourceTransformer(JSContext context) {
        this.context = context;
    }

    void collectImportBindings(String importLine, Set<String> bindingNames,
            Map<String, ImportBinding> importedBindings) {
        Matcher namespaceMatcher = MODULE_NAMESPACE_IMPORT_PATTERN.matcher(importLine);
        if (namespaceMatcher.find()) {
            boolean deferredImport = "defer".equals(namespaceMatcher.group(1));
            String localName = namespaceMatcher.group(2);
            String sourceSpecifier = ModuleSourceText.decodeModuleStringLiteralValue(namespaceMatcher.group(4));
            registerImportedBinding(localName, sourceSpecifier, MODULE_NAMESPACE_EXPORT_NAME, deferredImport,
                    bindingNames, importedBindings);
            return;
        }
        Matcher bindingMatcher = MODULE_BINDING_IMPORT_PATTERN.matcher(importLine);
        if (!bindingMatcher.find()) {
            return;
        }
        String clause = bindingMatcher.group(1).trim();
        String sourceSpecifier = ModuleSourceText.decodeModuleStringLiteralValue(bindingMatcher.group(3));
        ModuleImportDeclaration.visitBindings(clause,
                name -> registerImportedBinding(name, sourceSpecifier, "default", false, bindingNames,
                        importedBindings),
                named -> collectNamedImportBindings(named, sourceSpecifier, bindingNames, importedBindings),
                name -> registerImportedBinding(name, sourceSpecifier, MODULE_NAMESPACE_EXPORT_NAME, false,
                        bindingNames, importedBindings));
    }

    void collectNamedImportBindings(String namedClause, String sourceSpecifier, Set<String> bindingNames,
            Map<String, ImportBinding> importedBindings) {
        forEachNamedImport(namedClause, (importedName, localName) -> registerImportedBinding(localName, sourceSpecifier,
                importedName, false, bindingNames, importedBindings));
    }

    String createModuleExportBindingName(String generatedNamePrefix, String resolvedSpecifier) {
        // Unsigned, because Math.abs(Integer.MIN_VALUE) is still Integer.MIN_VALUE: a specifier
        // whose hash landed there produced an identifier containing a minus sign, and a valid
        // module then failed to evaluate because of what its file was called.
        return generatedNamePrefix + "DynamicImportExports$" + Integer.toUnsignedString(resolvedSpecifier.hashCode())
                + "$" + context.moduleLoader().moduleCacheSize();
    }

    private void extractBindingName(String element, List<String> names) {
        if ((element.startsWith("{") && element.endsWith("}")) || (element.startsWith("[") && element.endsWith("]"))) {
            extractDestructuringNames(element.substring(1, element.length() - 1), names);
        } else if (!element.isEmpty()) {
            names.add(ModuleSourceText.decodeIdentifierEscapes(element));
        }
    }

    void extractDestructuringNames(String innerText, List<String> names) {
        for (String element : ModuleSourceText.splitOnTopLevelCommas(innerText)) {
            extractSingleDestructuringName(element.trim(), names);
        }
    }

    /**
     * The name a {@code function} or {@code class} declaration binds, decoded.
     * <p>
     * Decoded because the declared binding is the decoded name: {@code export function café()} declares {@code café},
     * and the generated export bookkeeping has to name it the way the rest of the module names it.
     *
     * @param exportClause
     *            the declaration text, with {@code export} already removed
     * @return the bound name, or null when the declaration is anonymous
     */
    String extractExportedFunctionOrClassName(String exportClause) {
        Matcher functionMatcher = DYNAMIC_IMPORT_EXPORT_FUNCTION_NAME_PATTERN.matcher(exportClause);
        if (functionMatcher.find()) {
            return ModuleSourceText.decodeIdentifierEscapes(functionMatcher.group(1));
        }
        Matcher classMatcher = DYNAMIC_IMPORT_EXPORT_CLASS_NAME_PATTERN.matcher(exportClause);
        if (classMatcher.find()) {
            String candidateName = ModuleSourceText.decodeIdentifierEscapes(classMatcher.group(1));
            if (JSKeyword.EXTENDS.equals(candidateName)) {
                return null;
            }
            return candidateName;
        }
        return null;
    }

    Map<String, String> extractImportAttributes(String importStatement) {
        Matcher withMatcher = MODULE_WITH_CLAUSE_PATTERN.matcher(importStatement);
        if (!withMatcher.find()) {
            return null;
        }
        String withBody = withMatcher.group(1).trim();
        if (withBody.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, String> attributes = new HashMap<>();
        String[] pairs = withBody.split(",");
        for (String pair : pairs) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colonIndex = trimmed.indexOf(':');
            if (colonIndex < 0) {
                continue;
            }
            String key = trimmed.substring(0, colonIndex).trim();
            String value = trimmed.substring(colonIndex + 1).trim();
            // Remove surrounding quotes from value
            if (value.length() >= 2 && ((value.startsWith("'") && value.endsWith("'"))
                    || (value.startsWith("\"") && value.endsWith("\"")))) {
                value = value.substring(1, value.length() - 1);
            }
            attributes.put(key, value);
        }
        return attributes;
    }

    List<String> extractSimpleDeclarationNames(String declarationSource) {
        String declarationText = declarationSource.trim();
        int firstSpaceIndex = declarationText.indexOf(' ');
        if (firstSpaceIndex < 0 || firstSpaceIndex >= declarationText.length() - 1) {
            return List.of();
        }
        String declaratorsText = declarationText.substring(firstSpaceIndex + 1).trim();
        if (declaratorsText.endsWith(";")) {
            declaratorsText = declaratorsText.substring(0, declaratorsText.length() - 1).trim();
        }
        if (declaratorsText.isEmpty()) {
            return List.of();
        }
        List<String> declarators = ModuleSourceText.splitOnTopLevelCommas(declaratorsText);
        List<String> declarationNames = new ArrayList<>(declarators.size());
        for (String declarator : declarators) {
            String declaratorText = declarator.trim();
            int assignmentIndex = ModuleSourceText.findTopLevelAssignment(declaratorText);
            if (assignmentIndex >= 0) {
                declaratorText = declaratorText.substring(0, assignmentIndex).trim();
            }
            extractBindingName(declaratorText, declarationNames);
        }
        return declarationNames;
    }

    void extractSingleDestructuringName(String element, List<String> names) {
        if (element.isEmpty() || element.equals("...")) {
            return;
        }
        // Handle rest element: ...name
        if (element.startsWith("...")) {
            element = element.substring(3).trim();
        }
        // Handle rename: key: value
        int colonIndex = ModuleSourceText.findTopLevelDelimiter(element, 0, ':');
        if (colonIndex >= 0) {
            element = element.substring(colonIndex + 1).trim();
        }
        // Handle default value: name = defaultVal
        int eqIndex = ModuleSourceText.findTopLevelAssignment(element);
        if (eqIndex >= 0) {
            element = element.substring(0, eqIndex).trim();
        }
        extractBindingName(element, names);
    }

    private void forEachModuleSpecifier(String text, boolean export, BiConsumer<String, String> binding) {
        for (String entry : ModuleSourceText.splitOnTopLevelCommas(text)) {
            String specifier = entry.trim();
            if (specifier.isEmpty()) {
                continue;
            }
            int as = ModuleSourceText.findTopLevelAs(specifier);
            if (as < 0) {
                String name = ModuleSourceText.parseModuleExportNameValue(specifier);
                binding.accept(name, name);
            } else {
                String alias = specifier.substring(as + 2).trim();
                binding.accept(ModuleSourceText.parseModuleExportNameValue(specifier.substring(0, as)),
                        export ? ModuleSourceText.parseModuleExportNameValue(alias) : alias);
            }
        }
    }

    void forEachNamedImport(String namedClause, BiConsumer<String, String> binding) {
        String clause = namedClause.trim();
        if (clause.startsWith("{") && clause.endsWith("}")) {
            forEachModuleSpecifier(clause.substring(1, clause.length() - 1), false, binding);
        }
    }

    boolean hasModuleExportSyntax(String code) {
        var scan = scan(code).scanTopLevelModuleDeclarations();
        return scan == null
                ? MODULE_EXPORT_SYNTAX_PATTERN.matcher(ModuleSourceScan.maskModuleComments(code)).find()
                : scan.hasExportDeclaration();
    }

    boolean hasModuleStaticImportSyntax(String code) {
        var scan = scan(code).scanTopLevelModuleDeclarations();
        return scan == null
                ? MODULE_STATIC_IMPORT_SYNTAX_PATTERN.matcher(ModuleSourceScan.maskModuleComments(code)).find()
                : scan.hasImportDeclaration();
    }

    boolean hasModuleTopLevelAwaitSyntax(String code) {
        return MODULE_TOP_LEVEL_AWAIT_PATTERN.matcher(ModuleSourceScan.maskModuleComments(code)).find();
    }

    /**
     * Check if an import clause contains named bindings other than 'default'. E.g., {@code {name}} returns true,
     * {@code {default as x}} returns false.
     */
    boolean hasNonDefaultNamedBindings(String importClause) {
        int braceStart = importClause.indexOf('{');
        if (braceStart < 0) {
            return false;
        }
        int braceEnd = importClause.indexOf('}', braceStart);
        if (braceEnd < 0) {
            return false;
        }
        String body = importClause.substring(braceStart + 1, braceEnd);
        for (String entry : body.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // Get the imported name (before 'as')
            String[] parts = trimmed.split("\\s+as\\s+");
            String importedName = parts[0].trim();
            if (!"default".equals(importedName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an expression's brackets, braces and parentheses all close.
     * <p>
     * Comments, strings and templates are masked first, so a delimiter inside one of them does not count. This decides
     * only whether a default-export expression has run past the end of its line; it is not a parser, and cannot be one
     * at this layer.
     *
     * @param expressionText
     *            the text scanned so far
     * @return true when nothing is left open
     */
    boolean isBalancedExpressionText(String expressionText) {
        String maskedExpressionText = ModuleSourceScan.maskModuleComments(expressionText);
        int depth = 0;
        for (int index = 0; index < maskedExpressionText.length(); index++) {
            switch (maskedExpressionText.charAt(index)) {
                case '(', '[', '{' -> depth++;
                case ')', ']', '}' -> depth--;
                default -> {
                }
            }
        }
        return depth <= 0;
    }

    boolean isCompleteStaticImportStatement(String importStatement) {
        if (importStatement == null || importStatement.isBlank()) {
            return false;
        }
        String normalizedImportStatement = importStatement.strip();
        if (MODULE_NAMESPACE_IMPORT_PATTERN.matcher(normalizedImportStatement).matches()) {
            return true;
        }
        if (MODULE_BINDING_IMPORT_PATTERN.matcher(normalizedImportStatement).matches()) {
            return true;
        }
        return MODULE_SIDE_EFFECT_IMPORT_PATTERN.matcher(normalizedImportStatement).matches();
    }

    boolean isDynamicImportDefaultDeclarationClause(String defaultClause) {
        return defaultClause.startsWith("function") || defaultClause.startsWith("async function")
                || defaultClause.startsWith("class");
    }

    boolean isSelfImportBinding(ImportBinding importBinding, String moduleSpecifier) {
        if (importBinding == null || importBinding.sourceSpecifier() == null
                || importBinding.sourceSpecifier().isEmpty()) {
            return false;
        }
        try {
            String resolvedImportSpecifier = context.moduleLoader().resolveDynamicImportSpecifier(
                    importBinding.sourceSpecifier(), moduleSpecifier, importBinding.sourceSpecifier());
            Path resolvedImportPath = Path.of(resolvedImportSpecifier).normalize().toAbsolutePath();
            Path modulePath = Path.of(moduleSpecifier).normalize().toAbsolutePath();
            String resolvedImportPathString = resolvedImportPath.toString();
            String modulePathString = modulePath.toString();
            if (resolvedImportPathString.equals(modulePathString)) {
                return true;
            }
            return resolvedImportPathString.equalsIgnoreCase(modulePathString);
        } catch (JSException ignored) {
            context.clearPendingException();
            return false;
        } catch (Exception ignored) {
            context.clearPendingException();
            return false;
        }
    }

    boolean isStaticImportLine(String trimmedLine) {
        if (trimmedLine == null || !trimmedLine.startsWith("import")) {
            return false;
        }
        if (trimmedLine.startsWith("import(") || trimmedLine.startsWith("import.")) {
            return false;
        }
        if (trimmedLine.length() == "import".length()) {
            return false;
        }
        char nextChar = trimmedLine.charAt("import".length());
        return !Character.isLetterOrDigit(nextChar) && nextChar != '_' && nextChar != '$';
    }

    /**
     * Rewrite module source so that every top-level {@code import} and {@code export} declaration occupies whole lines.
     * <p>
     * The transformer that follows is line-oriented, and this is what makes that sound for the shapes it could not see
     * before: {@code const t = 1; export const v = 15;} and {@code import { v } from './dep.mjs'; use(v);} are both
     * valid modules whose declarations were invisible or unhoisted purely because of where the line breaks fell. Only
     * line breaks are inserted — no token is moved, rewritten or dropped — so the only observable difference is that
     * source positions after a break shift by a line.
     * <p>
     * A line terminator is not a neutral thing to insert: line terminators are what lets automatic semicolon insertion
     * terminate a statement, so splitting unconditionally handed the parser a semicolon the author never wrote, and the
     * engine accepted modules — {@code export {} let x =
     * 1;} — that a conforming parser must reject. The source is therefore parsed <em>as written</em> before any break
     * is inserted. Breaks only ever go immediately before a top-level declaration or immediately after one whose extent
     * is identifiable, and in source the parser has already accepted both of those are statement boundaries, so the
     * split cannot change what it means.
     *
     * @param sourceCode
     *            the module source
     * @return the source with declarations on their own lines
     * @throws JSSyntaxErrorException
     *             when the source as written is not a valid module
     */
    String normalizeModuleDeclarationLines(String sourceCode) {
        return normalizeModuleDeclarationLines(sourceCode, true);
    }

    /**
     * Rewrite module source so that every top-level declaration occupies whole lines.
     *
     * @param sourceCode
     *            the module source
     * @param validate
     *            whether to compile the source as written first; false only when the caller has already done so and
     *            would otherwise pay for a second compilation
     * @return the source with declarations on their own lines
     * @throws JSSyntaxErrorException
     *             when the source as written is not a valid module
     */
    String normalizeModuleDeclarationLines(String sourceCode, boolean validate) {
        return normalizeModuleDeclarationLines(sourceCode, validate, scan(sourceCode));
    }

    private String normalizeModuleDeclarationLines(String sourceCode, boolean validate, ModuleSourceScan analysis) {
        var scan = analysis.scanTopLevelModuleDeclarations();
        if (scan == null || scan.lineBreakOffsets().isEmpty()) {
            return sourceCode;
        }
        if (validate) {
            requireModuleSourceCompiles(sourceCode);
        }
        StringBuilder normalized = new StringBuilder(sourceCode.length() + scan.lineBreakOffsets().size());
        int copiedUpTo = 0;
        for (int breakOffset : scan.lineBreakOffsets()) {
            if (breakOffset <= copiedUpTo || breakOffset > sourceCode.length()) {
                continue;
            }
            normalized.append(sourceCode, copiedUpTo, breakOffset).append('\n');
            copiedUpTo = breakOffset;
        }
        normalized.append(sourceCode, copiedUpTo, sourceCode.length());
        return normalized.toString();
    }

    void parseDynamicImportExportList(String exportListText, String sourceSpecifier,
            List<JSDynamicImportModule.LocalExportBinding> localExportBindings,
            List<JSDynamicImportModule.ReExportBinding> reExportBindings) {
        forEachModuleSpecifier(exportListText, true, (localName, exportedName) -> {
            if (sourceSpecifier == null) {
                localExportBindings.add(new JSDynamicImportModule.LocalExportBinding(localName, exportedName));
            } else {
                reExportBindings.add(
                        new JSDynamicImportModule.ReExportBinding(sourceSpecifier, localName, exportedName, false));
            }
        });
    }

    /**
     * Rewrite ES module source into plain script source that the compiler can evaluate.
     * <p>
     * <strong>This is a line-based textual transformer, not a parser.</strong> It splits the source on {@code \n},
     * classifies each line, and splices generated JavaScript into the result.
     * <p>
     * <em>Which</em> text it classifies is no longer decided by how the source is formatted.
     * {@link #normalizeModuleDeclarationLines(String)} tokenises the source first and puts every top-level declaration
     * on its own lines, {@link ModuleSourceScan#defaultExportExtents()} asks the parser where an {@code export default}
     * expression ends, and both binding names and string literals are read as the values the lexer says they are rather
     * than as the characters that spell them. So a declaration sharing a line with other code, a regular expression
     * literal containing a quote, a Unicode or escaped identifier, and a string export name are all handled — those
     * were once documented here as unsupported, and are covered by {@code JSModuleSourceTransformTest},
     * {@code JSModuleDefaultExportExtentTest} and {@code JSModuleIdentifierSpellingTest}.
     * <p>
     * Live imports are installed in persistent module environments inherited by closures. Generated local metadata and
     * private namespace bindings are hidden from direct eval, and the loader validates import attributes. Retaining
     * import/export AST nodes would further simplify the textual transform, but runtime bindings no longer depend on
     * temporary global properties. Correct-behavior regressions live in JSModuleEnvironmentTest.
     * <p>
     * Loading and linking are separate from evaluation: see {@link #requireModuleGraphLinks(String, String)}. Every
     * value that reaches identifier position in the generated source is validated by
     * {@link #requireGeneratedIdentifier(String, String, String)}, and every value that reaches string position is
     * escaped by {@link ModuleSourceText#escapeJavaScriptString(String)}, so a name the scanner mis-extracts produces a
     * diagnosable SyntaxError rather than spliced source text.
     *
     * @param moduleRecord
     *            the module whose raw source is to be transformed
     */
    void parseDynamicImportModuleSource(JSDynamicImportModule moduleRecord) {
        // Put every top-level declaration on its own lines before the line-oriented scan below
        // runs, so which text it classifies is decided by the lexer rather than by where the
        // author happened to break lines.
        String raw = moduleRecord.rawSource();
        ModuleSourceScan analysis = scan(raw);
        String sourceCode = normalizeModuleDeclarationLines(raw, true, analysis);
        if (sourceCode != raw) {
            // Inserted newlines change token offsets and line numbers.
            analysis = scan(sourceCode);
        }
        new ModuleSourceRewrite(context, this, moduleRecord, sourceCode, analysis).rewrite();
    }

    void registerImportedBinding(String localName, String sourceSpecifier, String importedName, boolean deferredImport,
            Set<String> bindingNames, Map<String, ImportBinding> importedBindings) {
        String normalizedLocalName = localName == null
                ? ""
                : ModuleSourceText.decodeIdentifierEscapes(localName.trim());
        if (normalizedLocalName.isEmpty()) {
            return;
        }
        bindingNames.add(normalizedLocalName);
        if (sourceSpecifier != null && !sourceSpecifier.isEmpty()) {
            importedBindings.put(normalizedLocalName, new ImportBinding(sourceSpecifier, importedName, deferredImport));
        }
    }

    String renameAnonymousDefaultExportDeclaration(String declarationLine, String defaultLocalName) {
        String replacementName = Matcher.quoteReplacement(defaultLocalName);
        String renamedFunctionDeclaration = declarationLine
                .replaceFirst("^(\\s*(?:async\\s+)?function(?:\\s*\\*)?)\\s*\\(", "$1 " + replacementName + "(");
        if (!renamedFunctionDeclaration.equals(declarationLine)) {
            return renamedFunctionDeclaration;
        }

        String renamedClassDeclaration = declarationLine.replaceFirst("^(\\s*class)\\b", "$1 " + replacementName);
        if (!renamedClassDeclaration.equals(declarationLine)) {
            return renamedClassDeclaration;
        }

        throw new JSException(context.throwSyntaxError("Invalid default export declaration"));
    }

    /**
     * Require that a module loaded from disk is valid, and report its failure as a JavaScript {@code SyntaxError}
     * carrying that file's own coordinates.
     * <p>
     * A dependency is compiled before it is turned into generated module code for the same reason an entry module is —
     * see {@link #moduleSourceTransformer.requireModuleSourceCompiles(String)} — and additionally because a Java
     * compiler exception escaping here would leave the module cache holding a record stuck in {@code LOADING}.
     * Converting it to a {@link JSException} lets the caller's existing handler evict that record.
     *
     * @param sourceCode
     *            the dependency's source, unmodified
     * @param resolvedSpecifier
     *            the resolved path, used as the compilation's file name
     * @throws JSException
     *             when the dependency is not a valid module
     */
    void requireDependencyModuleSourceCompiles(String sourceCode, String resolvedSpecifier) {
        try {
            new Compiler(sourceCode, resolvedSpecifier).setContext(context).compile(true);
        } catch (JSSyntaxErrorException syntaxError) {
            throw new JSException(context.throwSyntaxError(syntaxError.getMessage(), syntaxError.getSourceLocation()));
        } catch (JSCompilerException compilerError) {
            throw new JSException(
                    context.throwSyntaxError(compilerError.getMessage(), compilerError.getSourceLocation()));
        } catch (JSErrorException error) {
            // A dependency nested too deeply to compile is a RangeError, as it is for the entry module.
            throw new JSException(context.throwError(error));
        }
    }

    /**
     * Validate a name that is about to be interpolated into generated module source in identifier position.
     * <p>
     * The module transformer builds JavaScript source text and evaluates it. Names extracted from the module source by
     * the ad-hoc scanner reach identifier position unescaped, so a value that is not an identifier would splice
     * arbitrary source into the generated program. Anything that is not a well-formed ECMAScript identifier is rejected
     * with a SyntaxError instead.
     *
     * @param name
     *            the candidate identifier
     * @param description
     *            what the name denotes, used in the error message
     * @param moduleSpecifier
     *            which module is being transformed, or null when it is not known
     * @return the name unchanged when it is a valid identifier
     * @throws JSException
     *             wrapping a SyntaxError when the name is not a valid identifier
     */
    String requireGeneratedIdentifier(String name, String description, String moduleSpecifier) {
        if (ModuleSourceText.isValidIdentifierName(name)) {
            return name;
        }
        // No SourceLocation: the transformer works on rewritten text, and offsets into that name
        // no character of the source anybody wrote. Naming the module is the part that can be made
        // true, and it is the part an embedder with a graph of them actually needs — the message
        // used to identify neither the module nor the position.
        JSError error = context
                .throwSyntaxError("Unsupported module syntax: " + description + " is not a valid identifier"
                        + (moduleSpecifier == null ? "" : " in module '" + moduleSpecifier + "'"));
        error.setSourceName(moduleSpecifier);
        throw new JSException(error);
    }

    /**
     * Require that module source is valid <em>as written</em>, before it is rewritten.
     * <p>
     * The grammar is the only thing that knows where a statement really ends: whether a brace closes a block or an
     * object literal, whether a line terminator would trigger automatic semicolon insertion, whether a restricted
     * production is in play. Re-deriving any of that from a token scan would be a second, worse implementation of the
     * parser — and its diagnostics would be a second, worse vocabulary. Handing the source to the parser gives both for
     * free.
     * <p>
     * <strong>Why this compiles rather than parses.</strong> Parsing alone answers the automatic semicolon insertion
     * question but leaves every early error the bytecode compiler raises — duplicate {@code __proto__}, a duplicate
     * export name, an unresolvable {@code break} target, an invalid regular expression literal — to be discovered
     * later, by which point the source has been split onto more lines and wrapped in generated module code. The
     * location on such an error then belongs to text the caller never wrote: a duplicate {@code __proto__} at offset 44
     * of a 60-character module was reported at offset 119. Compiling the untouched source moves every early error in
     * front of the rewrite, so all of them are reported in the caller's coordinates. The bytecode produced here is
     * discarded; only the diagnostics matter.
     *
     * @param sourceCode
     *            the module source, unmodified
     * @throws JSSyntaxErrorException
     *             when the source is not a valid module
     */
    void requireModuleSourceCompiles(String sourceCode) {
        new Compiler(sourceCode, "<module>").setContext(context).compile(true);
    }

    ModuleSourceScan scan(String source) {
        return new ModuleSourceScan(context, source);
    }

    /**
     * The module a quoted specifier names, as a value rather than as source text.
     *
     * @param text
     *            the specifier literal, possibly followed by an attributes clause
     * @return the decoded specifier
     */
    String stripQuotedSpecifier(String text) {
        String specifierText = text.trim();
        if (specifierText.length() < 2) {
            throw new JSException(context.throwSyntaxError("Invalid module specifier"));
        }
        char quote = specifierText.charAt(0);
        if (quote != '\'' && quote != '"') {
            throw new JSException(context.throwSyntaxError("Invalid module specifier"));
        }
        // A backslash escapes whatever follows it, so the first matching quote is not necessarily
        // the closing one: `'./a\\'b.mjs'` ends at the third quote, not the second.
        int closeQuote = ModuleSourceText.skipQuotedText(specifierText, 0);
        if (closeQuote >= specifierText.length()) {
            throw new JSException(context.throwSyntaxError("Invalid module specifier"));
        }
        // Everything after the closing quote (e.g. `with { ... }`) is not part of the specifier.
        return ModuleSourceText.decodeModuleStringLiteralValue(specifierText.substring(1, closeQuote));
    }

    record ImportBinding(String sourceSpecifier, String importedName, boolean deferredImport) {
    }
}
