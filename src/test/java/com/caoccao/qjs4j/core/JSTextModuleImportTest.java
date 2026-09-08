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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code import text from './x' with \{ type: 'text' \}} makes a synthetic module whose default export is the file's
 * contents, exactly as they are. The payload is data.
 * <p>
 * Both the eager and the deferred loader put it through the JavaScript module normaliser first, and that normaliser
 * compiles what it is given. A text file was therefore accepted or rejected according to whether its bytes happened to
 * resemble an ECMAScript declaration: {@code export \{\}; this is arbitrary text} was a {@code SyntaxError} instead of
 * a string, and an innocuous edit to a data file could change whether the file loaded at all.
 */
public class JSTextModuleImportTest extends BaseTest {
    @TempDir
    Path moduleDirectory;

    /**
     * Import a payload as text and return what the default export was.
     *
     * @param payloadName
     *            the payload's file name
     * @param payload
     *            the exact bytes of the payload
     * @return the imported string
     */
    private String importAsText(String payloadName, String payload) throws IOException {
        Files.writeString(moduleDirectory.resolve(payloadName), payload);
        Path entry = moduleDirectory.resolve(payloadName + ".importer.mjs");
        String entrySource = "import value from './" + payloadName + "' with { type: 'text' };\n"
                + "globalThis.result = value;\n";
        Files.writeString(entry, entrySource);
        try (JSRuntime runtime = new JSRuntime(); JSContext context = runtime.createContext()) {
            context.eval(entrySource, entry.toString(), true);
            return context.eval("globalThis.result", "probe.js", false).toString();
        }
    }

    @Test
    public void testAnEmptyPayloadIsTheEmptyString() throws IOException {
        assertThat(importAsText("empty.txt", "")).isEmpty();
    }

    @Test
    public void testAPayloadImportedThroughADeferredImportIsStillText() throws IOException {
        String payload = "export {}; this is arbitrary text\n";
        Files.writeString(moduleDirectory.resolve("deferred.txt"), payload);
        Path entry = moduleDirectory.resolve("deferred-importer.mjs");
        String entrySource = "import defer * as ns from './deferred.txt' with { type: 'text' };\n"
                + "globalThis.result = ns.default;\n";
        Files.writeString(entry, entrySource);
        try (JSRuntime runtime = new JSRuntime(); JSContext context = runtime.createContext()) {
            context.eval(entrySource, entry.toString(), true);
            context.processMicrotasks();
            assertThat(context.eval("globalThis.result", "probe.js", false).toString()).isEqualTo(payload);
        }
    }

    @Test
    public void testAPayloadImportedThroughADynamicImportIsStillText() throws IOException {
        String payload = "export {}; this is arbitrary text\n";
        Files.writeString(moduleDirectory.resolve("dynamic.txt"), payload);
        Path entry = moduleDirectory.resolve("dynamic-importer.mjs");
        String entrySource = "const m = await import('./dynamic.txt', "
                + "{ with: { type: 'text' } });\nglobalThis.result = m.default;\n";
        Files.writeString(entry, entrySource);
        try (JSRuntime runtime = new JSRuntime(); JSContext context = runtime.createContext()) {
            context.eval(entrySource, entry.toString(), true);
            context.processMicrotasks();
            assertThat(context.eval("globalThis.result", "probe.js", false).toString()).isEqualTo(payload);
        }
    }

    @Test
    public void testAPayloadOfOnlyACommentIsStillText() throws IOException {
        String payload = "// not a comment, just text\n";
        assertThat(importAsText("comment.txt", payload)).isEqualTo(payload);
    }

    @Test
    public void testAPayloadThatIsInvalidJavaScriptIsStillText() throws IOException {
        String payload = "function ( { ] not javascript at all\n";
        assertThat(importAsText("invalid.txt", payload)).isEqualTo(payload);
    }

    @Test
    public void testAPayloadThatIsValidJavaScriptIsNotExecuted() throws IOException {
        String payload = "globalThis.executed = true;\nexport const v = 1;\n";
        assertThat(importAsText("valid-js.txt", payload)).isEqualTo(payload);
    }

    @Test
    public void testAPayloadThatLooksLikeAModuleDeclarationIsStillText() throws IOException {
        // The review's reproduction.
        String payload = "export {}; this is arbitrary text\n";
        assertThat(importAsText("declaration-like.txt", payload)).isEqualTo(payload);
    }

    @Test
    public void testCarriageReturnsAndUnicodeSurviveExactly() throws IOException {
        // The NUL is written as an escape rather than as itself. A literal U+0000 byte in a .java
        // file makes the whole file binary to ordinary tooling: `file` reports `data`, git prints
        // no textual diff for it, and ripgrep skips it — so a test added to make a payload's exact
        // bytes reviewable stopped being reviewable.
        String payload = "first\r\nsecond\r\né中文\0tail";
        assertThat(payload).as("the payload really does carry a NUL, whatever the source spells it as")
                .contains("文\0tail");
        assertThat(importAsText("crlf-unicode.txt", payload)).isEqualTo(payload);
    }
}
