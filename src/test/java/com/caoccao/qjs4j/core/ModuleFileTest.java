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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** File-backed modules resolved relative to a fresh directory for each test. */
abstract class ModuleFileTest extends BaseTest {
    @TempDir
    Path moduleDirectory;

    JSException assertModuleFailsBeforeEvaluation(String entryName, String entrySource, String expectedMessagePart)
            throws IOException {
        Path entry = writeModule(entryName, entrySource);
        try (JSRuntime runtime = createRuntime(); JSContext context = runtime.createContext()) {
            JSException failure = catchThrowableOfType(JSException.class,
                    () -> context.eval(entrySource, entry.toString(), true));
            assertThat(failure).as("the graph must fail").isNotNull();
            assertThat(failure).hasMessageContaining(expectedMessagePart);
            assertThat(context.eval("String(globalThis.dependencyBodyRan)", "probe.js", false).toString())
                    .as("the dependency named before the bad one must not have run").isEqualTo("undefined");
            assertThat(context.eval("String(globalThis.rootBodyRan)", "probe.js", false).toString())
                    .as("nor the entry module's own body").isEqualTo("undefined");
            assertThat(context.getModuleBodyEvaluationCount()).as("the engine agrees that nothing was evaluated")
                    .isZero();
            return failure;
        }
    }

    String evaluateModuleResult(String name, String source) throws IOException {
        return evaluateModuleResult(name, source, "String(globalThis.result)");
    }

    String evaluateModuleResult(String name, String source, String probe) throws IOException {
        Path path = writeModule(name, source);
        try (JSRuntime runtime = createRuntime(); JSContext context = runtime.createContext()) {
            context.eval(source, path.toString(), true);
            return context.eval(probe, "probe.js", false).toString();
        }
    }

    Path writeModule(String name, String source) throws IOException {
        return Files.writeString(moduleDirectory.resolve(name), source);
    }
}
