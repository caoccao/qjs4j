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
 * ResolveExport returns "ambiguous" when two {@code export *} declarations supply the same name
 * from different modules. An ambiguous name is absent from the namespace object and is a
 * resolution-time {@code SyntaxError} when imported by name.
 * <p>
 * The withdrawal of an ambiguous binding goes through a {@code super.delete} call that must bypass
 * the namespace object's own {@code [[Delete]]}, which refuses export properties — so these cases
 * also pin that the internal delete stays non-virtual.
 */
public class JSModuleAmbiguousExportTest extends BaseTest {
    @TempDir
    Path moduleDirectory;

    private String evalMain(Path mainPath, String source) throws IOException {
        Files.writeString(mainPath, source);
        context.eval(source, mainPath.toString(), true);
        context.processMicrotasks();
        return JSTypeConversions.toString(context, context.eval("String(globalThis.__out)")).value();
    }

    @Test
    public void testAmbiguousNameIsAbsentFromNamespace() throws IOException {
        Path mainPath = writeAmbiguousStarModule();
        assertThat(evalMain(mainPath, """
                import * as ns from './star.mjs';
                globalThis.__out = JSON.stringify(Object.keys(ns));"""))
                .isEqualTo("[\"first\",\"second\"]");
    }

    @Test
    public void testAmbiguousNameIsNotAnOwnProperty() throws IOException {
        Path mainPath = writeAmbiguousStarModule();
        assertThat(evalMain(mainPath, """
                import * as ns from './star.mjs';
                globalThis.__out = ('both' in ns) + ',' + String(ns.both);"""))
                .isEqualTo("false,undefined");
    }

    @Test
    public void testNamedImportOfAmbiguousNameIsASyntaxError() throws IOException {
        Path mainPath = writeAmbiguousStarModule();
        String source = """
                import { both } from './star.mjs';
                globalThis.__out = both;""";
        Files.writeString(mainPath, source);
        assertThatThrownBy(() -> context.eval(source, mainPath.toString(), true))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("both");
    }

    @Test
    public void testUnambiguousNamesStillResolve() throws IOException {
        Path mainPath = writeAmbiguousStarModule();
        assertThat(evalMain(mainPath, """
                import { first, second } from './star.mjs';
                globalThis.__out = first + ',' + second;"""))
                .isEqualTo("a,b");
    }

    private Path writeAmbiguousStarModule() throws IOException {
        Files.writeString(moduleDirectory.resolve("first.mjs"), """
                export var first = 'a';
                export var both = 'from-first';""");
        Files.writeString(moduleDirectory.resolve("second.mjs"), """
                export var second = 'b';
                export var both = 'from-second';""");
        Files.writeString(moduleDirectory.resolve("star.mjs"), """
                export * from './first.mjs';
                export * from './second.mjs';""");
        return moduleDirectory.resolve("main.mjs");
    }
}
