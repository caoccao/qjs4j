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

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * How a name is spelled is not what the name is.
 * <p>
 * The lexer has always known this: {@code café} is an identifier, {@code \u0078} <em>is</em>
 * {@code x}, and the {@code StringValue} of {@code "a\u002db"} is {@code a-b}. The module
 * transformer did not. It re-extracted binding names with {@code [A-Za-z_$][A-Za-z0-9_$]*} and read
 * string literals with {@code substring}, so the parser accepted source that the transformer then
 * gave different semantics — a module exporting {@code café} reported no such export, and
 * {@code export const \u0078 = 1} was rejected as "not a valid identifier" for a binding the
 * compiler had just accepted.
 * <p>
 * These are the cases expressible in a single module, so V8 can be the arbiter directly. The
 * cross-module halves — escaped specifiers, escaped arbitrary export names between two files — need
 * a module graph on disk and live in {@link JSModuleNameResolutionTest}.
 */
public class JSModuleIdentifierSpellingTest extends BaseJavetTest {
    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        moduleMode = true;
    }

    @Test
    public void testADefaultExportedUnicodeFunctionName() {
        assertStringWithJavet("export default function café() { return 42; }\n"
                + "String(café());");
    }

    @Test
    public void testAStringExportNameWithAnEscape() {
        // ES2022 arbitrary module export names. The StringValue is `a-b`, not the eight characters
        // that spell it.
        assertStringWithJavet("const x = 42;\nexport { x as \"a\\u002db\" };\nString(x);");
    }

    @Test
    public void testAUnicodeExportedClassName() {
        assertStringWithJavet("export class Café {}\nString(typeof Café);");
    }

    @Test
    public void testAUnicodeExportedFunctionName() {
        // The review's reproduction, in one module.
        assertStringWithJavet("export function café() { return 42; }\nString(café());");
    }

    @Test
    public void testAUnicodeExportedGeneratorName() {
        assertStringWithJavet("export function* genérateur() { yield 42; }\n"
                + "String(genérateur().next().value);");
    }

    @Test
    public void testAUnicodeExportedLetAndDestructuredNames() {
        assertStringWithJavet("export let { á, b: é } = { á: 1, b: 2 };\n"
                + "String(á + é);");
    }

    @Test
    public void testAUnicodeExportedNameIsAlsoTheExportedName() {
        // The transformer builds an exports object keyed by the exported name, so a mis-extracted
        // name is visible from inside the module too, through its own namespace.
        assertStringWithJavet("export const módulo = 42;\n"
                + "export function read() { return módulo; }\n"
                + "String(read());");
    }

    @Test
    public void testAUnicodeExportedVarName() {
        assertStringWithJavet("export var été = 42;\nString(été);");
    }

    @Test
    public void testAnAstralIdentifierIsExported() {
        // Outside the BMP, so the name is two code units and a scan that walks chars must not split
        // it.
        assertStringWithJavet("export const 𝑥 = 42;\nString(𝑥);");
    }

    @Test
    public void testAnAsyncUnicodeExportedFunctionName() {
        assertStringWithJavet("export async function café() { return 42; }\n"
                + "String(typeof café);");
    }

    @Test
    public void testAnEscapedExportedClassName() {
        assertStringWithJavet("export class \\u0043af\\u00e9 {}\nString(typeof Café);");
    }

    @Test
    public void testAnEscapedExportedConstName() {
        // The review's reproduction: rejected outright as unsupported generated syntax.
        assertStringWithJavet("export const \\u0078 = 42;\nString(x);");
    }

    @Test
    public void testAnEscapedExportedFunctionName() {
        assertStringWithJavet("export function \\u0066oo() { return 42; }\nString(foo());");
    }

    @Test
    public void testAnEscapedIdentifierInALaterPosition() {
        assertStringWithJavet("export const a\\u0062c = 42;\nString(abc);");
    }

    @Test
    public void testAnEscapedRenamedExportName() {
        assertStringWithJavet("const x = 42;\nexport { x as \\u0079 };\nString(x);");
    }
}
