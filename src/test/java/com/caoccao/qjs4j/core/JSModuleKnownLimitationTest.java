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

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The authoritative list of ways this engine's ES modules are known to be wrong.
 * <p>
 * <strong>These tests assert the wrong answer on purpose.</strong> Each one states what a conforming
 * engine does, then pins what qjs4j actually does, so the list cannot rot: fix the defect and the
 * test here fails, which is the reminder to delete it. Prose in a README cannot do that — the README
 * previously pointed at {@code testKnownLimitation*} tests that did not exist anywhere in the
 * repository, and its list had drifted out of date while reading as authoritative.
 * <p>
 * Everything below has the same root cause. qjs4j implements ES modules as a <em>textual transform</em>:
 * module source is rewritten into ordinary script source, imports become temporary accessors on the
 * global object, and exports become generated bindings declared beside the author's own code. The
 * parser validates {@code import}/{@code export} syntax and then discards it — {@code ImportDeclaration}
 * and {@code ExportDeclaration} exist in the AST package and are never constructed. Real module
 * environment records, with indirect bindings the compiler can capture, are the fix for all of it, and
 * they are a milestone rather than a patch.
 * <p>
 * Reference behaviour is Node 24.16.0 on the same files, quoted per case.
 */
public class JSModuleKnownLimitationTest extends BaseTest {
    @TempDir
    Path moduleDirectory;

    /**
     * An import attribute naming a module type the engine does not implement is ignored rather than
     * refused, and the target is loaded as JavaScript.
     * <p>
     * Node: {@code TypeError: Import attribute "type" with value "javascript" is not supported}.
     */
    @Test
    public void testKnownLimitationAnUnsupportedImportTypeAttributeIsIgnored() throws IOException {
        writeModule("dep-attr.mjs", "export const x = 42;\n");
        Path entry = writeModule("main-attr.mjs",
                "import { x } from './dep-attr.mjs' with { type: 'javascript' };\n"
                        + "globalThis.result = x;\n");
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            context.eval(Files.readString(entry), entry.toString(), true);
            assertThat(context.eval("String(globalThis.result)", "probe.js", false).toString())
                    .as("Node refuses the attribute; qjs4j loads the module as JavaScript")
                    .isEqualTo("42");
        }
    }

    /**
     * The transformer's own bookkeeping bindings are visible to code inside the module.
     * <p>
     * They are declared in the module's own scope, so a direct {@code eval} can reach them. Choosing
     * a prefix the source does not spell — which the engine does — keeps them out of the way of names
     * the author writes, but it cannot hide a binding from a name the source never spells and builds
     * at run time. Salting the prefix would not help either: obscurity is not scope.
     * <p>
     * Node: {@code "undefined"}.
     */
    @Test
    public void testKnownLimitationGeneratedBindingsAreVisibleToDirectEval() throws IOException {
        Path entry = writeModule("main-generated.mjs",
                "export const x = 1;\n"
                        + "globalThis.probe = eval('typeof __q' + 'js4jModuleExports');\n");
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            context.eval(Files.readString(entry), entry.toString(), true);
            assertThat(context.eval("String(globalThis.probe)", "probe.js", false).toString())
                    .as("Node answers \"undefined\": no such binding is in scope in a module")
                    .isEqualTo("object");
        }
    }

    /**
     * Imported bindings do not survive in closures retained past the module's evaluation.
     * <p>
     * An import is a property installed on the global object for the duration of the module body and
     * removed afterwards, not a binding in a module environment. A closure created in the module body
     * therefore compiles to a global lookup, and the name it looks up is gone by the time anything
     * calls it.
     * <p>
     * Node: {@code readX()} returns 1, and 2 after {@code inc()}. A conforming import is also
     * <em>live</em>, so both halves are wrong here, not just the first.
     */
    @Test
    public void testKnownLimitationImportedBindingsDoNotSurviveRetainedClosures() throws IOException {
        writeModule("dep-closure.mjs", "export let x = 1;\nexport function inc() { x++; }\n");
        Path entry = writeModule("main-closure.mjs",
                "import { x, inc } from './dep-closure.mjs';\n"
                        + "globalThis.readX = () => x;\n"
                        + "globalThis.bumpX = inc;\n");
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            context.eval(Files.readString(entry), entry.toString(), true);
            assertThatThrownBy(() -> context.eval("readX()", "probe.js", false))
                    .as("Node returns 1; qjs4j has already deleted the overlay the closure reads")
                    .isInstanceOf(JSException.class)
                    .hasMessageContaining("x is not defined");
        }
    }

    private Path writeModule(String name, String source) throws IOException {
        Path path = moduleDirectory.resolve(name);
        Files.writeString(path, source);
        return path;
    }
}
