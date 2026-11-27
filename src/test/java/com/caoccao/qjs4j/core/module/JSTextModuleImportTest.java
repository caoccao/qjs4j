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

package com.caoccao.qjs4j.core.module;

import org.junit.jupiter.api.Test;

import java.io.IOException;
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
public class JSTextModuleImportTest extends ModuleFileTest {

    private void assertAsyncImport(String name, String source, String payload) throws IOException {
        Path entry = writeModule(name, source);
        context.eval(source, entry.toString(), true);
        context.processMicrotasks();
        assertThat(context.eval("globalThis.result", "probe.js", false).toString()).isEqualTo(payload);
    }

    @Test
    public void testAnEmptyPayloadIsTheEmptyString() throws IOException {
        writeModule("empty.txt", "");
        assertThat(evaluateModuleResult("empty.txt.importer.mjs", """
                import value from './empty.txt' with { type: 'text' };
                globalThis.result = value;
                """, "globalThis.result")).isEmpty();
    }

    @Test
    public void testAPayloadImportedThroughADeferredImportIsStillText() throws IOException {
        String payload = "export {}; this is arbitrary text\n";
        writeModule("deferred.txt", payload);
        String entrySource = "import defer * as ns from './deferred.txt' with { type: 'text' };\n"
                + "globalThis.result = ns.default;\n";
        assertAsyncImport("deferred-importer.mjs", entrySource, payload);
    }

    @Test
    public void testAPayloadImportedThroughADynamicImportIsStillText() throws IOException {
        String payload = "export {}; this is arbitrary text\n";
        writeModule("dynamic.txt", payload);
        String entrySource = "const m = await import('./dynamic.txt', "
                + "{ with: { type: 'text' } });\nglobalThis.result = m.default;\n";
        assertAsyncImport("dynamic-importer.mjs", entrySource, payload);
    }

    @Test
    public void testAPayloadOfOnlyACommentIsStillText() throws IOException {
        String payload = "// not a comment, just text\n";
        writeModule("comment.txt", payload);
        assertThat(evaluateModuleResult("comment.txt.importer.mjs", """
                import value from './comment.txt' with { type: 'text' };
                globalThis.result = value;
                """, "globalThis.result")).isEqualTo(payload);
    }

    @Test
    public void testAPayloadThatIsInvalidJavaScriptIsStillText() throws IOException {
        String payload = "function ( { ] not javascript at all\n";
        writeModule("invalid.txt", payload);
        assertThat(evaluateModuleResult("invalid.txt.importer.mjs", """
                import value from './invalid.txt' with { type: 'text' };
                globalThis.result = value;
                """, "globalThis.result")).isEqualTo(payload);
    }

    @Test
    public void testAPayloadThatIsValidJavaScriptIsNotExecuted() throws IOException {
        String payload = "globalThis.executed = true;\nexport const v = 1;\n";
        writeModule("valid-js.txt", payload);
        assertThat(evaluateModuleResult("valid-js.txt.importer.mjs", """
                import value from './valid-js.txt' with { type: 'text' };
                globalThis.result = value;
                """, "globalThis.result")).isEqualTo(payload);
    }

    @Test
    public void testAPayloadThatLooksLikeAModuleDeclarationIsStillText() throws IOException {
        // The review's reproduction.
        String payload = "export {}; this is arbitrary text\n";
        writeModule("declaration-like.txt", payload);
        assertThat(evaluateModuleResult("declaration-like.txt.importer.mjs", """
                import value from './declaration-like.txt' with { type: 'text' };
                globalThis.result = value;
                """, "globalThis.result")).isEqualTo(payload);
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
        writeModule("crlf-unicode.txt", payload);
        assertThat(evaluateModuleResult("crlf-unicode.txt.importer.mjs", """
                import value from './crlf-unicode.txt' with { type: 'text' };
                globalThis.result = value;
                """, "globalThis.result")).isEqualTo(payload);
    }
}
