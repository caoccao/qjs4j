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

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.promise.JSPromise;
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Every module is located and read through the host's {@link IJSModuleLoader}, and a runtime without one loads nothing.
 * <p>
 * The engine used to resolve every specifier to a path and read it with {@code java.nio.file.Files}. Any script could
 * therefore read any file the process could — {@code import('/etc/hosts', { with: { type: 'text' } })} returned its
 * contents, {@code 'bytes'} returned its raw bytes — and an embedder had no hook to intercept, confine or refuse it.
 * These cases are embedder-API tests: V8 has no file system of its own to compare against, which is the point.
 */
public class JSModuleLoaderHookTest extends ModuleFileTest {
    @TempDir
    Path outsideDirectory;

    @Test
    public void testACustomLoaderServesEveryKindOfImport() {
        // An in-memory loader: nothing here exists on disk, so everything that loads came through
        // the hook.
        Map<String, byte[]> modules = new HashMap<>();
        modules.put("/virtual/dep.mjs", "export const value = 41;\n".getBytes(StandardCharsets.UTF_8));
        modules.put("/virtual/data.json", "{\"n\": 2}".getBytes(StandardCharsets.UTF_8));
        modules.put("/virtual/note.txt", "hello".getBytes(StandardCharsets.UTF_8));
        modules.put("/virtual/raw.bin", new byte[]{1, 2, 3});
        modules.put("/virtual/lazy.mjs", """
                import { base } from './child.mjs';
                export const value = base + 1;
                """.getBytes(StandardCharsets.UTF_8));
        modules.put("/virtual/child.mjs", "export const base = 10;\n".getBytes(StandardCharsets.UTF_8));
        modules.put("/virtual/lazy.bin", new byte[]{4, 5});
        List<String> requests = new ArrayList<>();
        IJSModuleLoader memoryLoader = new IJSModuleLoader() {
            @Override
            public byte[] load(String key) throws IOException {
                requests.add("load " + key);
                byte[] payload = modules.get(key);
                if (payload == null) {
                    throw new IOException("no such module " + key);
                }
                return payload;
            }

            @Override
            public String resolve(String specifier, String referrer) {
                requests.add("resolve " + specifier);
                String key = "/virtual/" + specifier.substring(specifier.lastIndexOf('/') + 1);
                return modules.containsKey(key) ? key : null;
            }
        };
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setModuleLoader(memoryLoader));
                JSContext moduleContext = runtime.createContext()) {
            String result = evalAndSettle(moduleContext, """
                    import { value } from './dep.mjs';
                    import data from './data.json' with { type: 'json' };
                    import note from './note.txt' with { type: 'text' };
                    import raw from './raw.bin' with { type: 'bytes' };
                    globalThis.result = [value + 1, data.n, note, raw.length].join(',');
                    """, "/virtual/main.mjs");
            assertThat(result).isEqualTo("42,2,hello,3");
            assertThat(requests).contains("resolve ./dep.mjs", "load /virtual/dep.mjs", "resolve ./data.json",
                    "load /virtual/data.json", "load /virtual/note.txt", "load /virtual/raw.bin");

            assertThat(evalAndSettle(moduleContext, outcomeOf("import('./note.txt', { with: { type: 'text' } })"),
                    "/virtual/dynamic.mjs")).isEqualTo("resolved:hello");
            // Deferred imports read their module, its dependencies and their payloads through the hook too.
            assertThat(evalAndSettle(moduleContext, """
                    import defer * as lazy from './lazy.mjs';
                    import defer * as lazyBytes from './lazy.bin' with { type: 'bytes' };
                    globalThis.result = [lazy.value, lazyBytes.default.length].join(',');
                    """, "/virtual/deferred.mjs")).isEqualTo("11,2");
            assertThat(requests).contains("load /virtual/lazy.mjs", "load /virtual/child.mjs",
                    "load /virtual/lazy.bin");
            assertThat(evalAndSettle(moduleContext, outcomeOf("import('./absent.mjs')"), "/virtual/absent-main.mjs"))
                    .isEqualTo("rejected:TypeError: Cannot find module './absent.mjs'");
        }
    }

    @Test
    public void testADefaultRuntimeCannotReadAnyFile() throws IOException {
        // The review's reproduction: a default runtime handed out any readable file.
        Path secret = outsideDirectory.resolve("secret.txt");
        Files.writeString(secret, "TOP SECRET");
        String secretPath = secret.toString().replace("\\", "\\\\");
        try (JSRuntime runtime = new JSRuntime(); JSContext moduleContext = runtime.createContext()) {
            assertThat(
                    evalAndSettle(moduleContext, outcomeOf("import('" + secretPath + "', { with: { type: 'text' } })"),
                            moduleDirectory.resolve("text.mjs").toString()))
                    .isEqualTo("rejected:TypeError: Cannot find module '" + secret + "'");
            assertThat(
                    evalAndSettle(moduleContext, outcomeOf("import('" + secretPath + "', { with: { type: 'bytes' } })"),
                            moduleDirectory.resolve("bytes.mjs").toString()))
                    .isEqualTo("rejected:TypeError: Cannot find module '" + secret + "'");
            assertThatThrownBy(() -> moduleContext.eval("import s from '" + secretPath + "' with { type: 'text' };",
                    moduleDirectory.resolve("static.mjs").toString(), true))
                    .isInstanceOfSatisfying(JSException.class, e -> assertThat(e.getMessage())
                            .isEqualTo("TypeError: Cannot find module '" + secret + "'"));
            // A module whose source is handed to eval still runs: only loading is refused.
            assertThat(moduleContext.eval("const v = await 1; String(v + 1);", "entry.mjs", true).toString())
                    .isEqualTo("2");
        }
    }

    @Test
    public void testAFailureInsideTheLoaderIsAnImportFailure() {
        IJSModuleLoader brokenLoader = new IJSModuleLoader() {
            @Override
            public byte[] load(String key) {
                throw new IllegalStateException("storage offline");
            }

            @Override
            public String resolve(String specifier, String referrer) {
                return "/broken/" + specifier;
            }
        };
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setModuleLoader(brokenLoader));
                JSContext moduleContext = runtime.createContext()) {
            assertThatThrownBy(() -> moduleContext.eval("import './dep.mjs';", "/broken/main.mjs", true))
                    .isInstanceOfSatisfying(JSException.class,
                            e -> assertThat(e.getMessage()).contains("Cannot find module"));
            assertThatThrownBy(() -> moduleContext.eval("import t from './t.txt' with { type: 'text' };",
                    "/broken/text.mjs", true)).isInstanceOfSatisfying(JSException.class,
                            e -> assertThat(e.getMessage()).contains("Cannot find module"));
        }
    }

    @Test
    public void testAnUnrestrictedFileSystemLoaderResolvesLikeAPath() throws IOException {
        Path dependency = moduleDirectory.resolve("dep.mjs");
        Files.writeString(dependency, "export default 1;\n");
        String referrer = moduleDirectory.resolve("main.mjs").toString();
        JSFileSystemModuleLoader loader = new JSFileSystemModuleLoader();

        assertThat(loader.resolve("./dep.mjs", referrer)).isEqualTo(dependency.toString());
        assertThat(loader.resolve("./sub/../dep.mjs", referrer)).isEqualTo(dependency.toString());
        assertThat(loader.resolve(dependency.toString(), null)).as("an absolute specifier needs no referrer")
                .isEqualTo(dependency.toString());
        assertThat(loader.resolve(dependency.toString(), "")).isEqualTo(dependency.toString());
        assertThat(loader.resolve("./no-such-module-" + System.nanoTime() + ".mjs", ""))
                .as("an empty referrer contributes nothing").isNull();
        assertThat(loader.resolve("./dep.mjs", "<eval>")).as("a synthetic referrer contributes nothing").isNull();
        assertThat(loader.resolve("dep.mjs", "main.mjs")).as("a referrer without a directory contributes nothing")
                .isNull();
        assertThat(loader.resolve("./missing.mjs", referrer)).isNull();
        assertThat(loader.resolve("bad\0name.mjs", referrer)).as("a specifier that is not a path").isNull();
        assertThat(loader.resolve("./dep.mjs", "bad\0referrer.mjs")).as("a referrer that is not a path").isNull();

        assertThat(loader.load(dependency.toString()))
                .isEqualTo("export default 1;\n".getBytes(StandardCharsets.UTF_8));
        assertThat(loader.loadText(dependency.toString())).isEqualTo("export default 1;\n");
        assertThatThrownBy(() -> loader.load("bad\0key")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> loader.load(moduleDirectory.toString())).as("a directory is not a payload")
                .isInstanceOf(IOException.class);

        Path malformed = moduleDirectory.resolve("malformed.txt");
        Files.write(malformed, new byte[]{(byte) 0xC3, (byte) 0x28});
        assertThatThrownBy(() -> loader.loadText(malformed.toString())).isInstanceOf(MalformedInputException.class);
    }

    @Test
    public void testARootedFileSystemLoaderRefusesEverythingOutsideItsRoot() throws IOException {
        Path inside = moduleDirectory.resolve("inside.mjs");
        Files.writeString(inside, "export default 'inside';\n");
        Path outside = outsideDirectory.resolve("outside.txt");
        Files.writeString(outside, "outside");
        String referrer = moduleDirectory.resolve("main.mjs").toString();
        JSFileSystemModuleLoader loader = new JSFileSystemModuleLoader(moduleDirectory);

        assertThat(loader.resolve("./inside.mjs", referrer)).isEqualTo(inside.toString());
        assertThat(loader.resolve(outside.toString(), referrer)).as("an absolute path outside the root").isNull();
        String escape = moduleDirectory.relativize(outside).toString();
        assertThat(loader.resolve("./" + escape, referrer)).as("a relative path that climbs out of the root").isNull();
        assertThat(loader.load(inside.toString()))
                .isEqualTo("export default 'inside';\n".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> loader.load(outside.toString())).isInstanceOf(IOException.class)
                .hasMessageContaining("outside the module root");
        assertThat(
                new JSFileSystemModuleLoader(moduleDirectory.resolve("no-such-root")).resolve(inside.toString(), null))
                .as("a root that does not exist admits nothing").isNull();

        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setModuleLoader(loader));
                JSContext moduleContext = runtime.createContext()) {
            assertThat(evalAndSettle(moduleContext, outcomeOf("import('./inside.mjs')"), referrer))
                    .isEqualTo("resolved:inside");
            String outsidePath = outside.toString().replace("\\", "\\\\");
            assertThat(evalAndSettle(moduleContext,
                    outcomeOf("import('" + outsidePath + "', { with: { type: 'text' } })"), referrer))
                    .isEqualTo("rejected:TypeError: Cannot find module '" + outside + "'");
        }
    }

    @Test
    public void testAStackOverflowInsideTheLoaderIsARangeError() {
        // The eval pipeline's backstop: recursion that overflows outside both the compiler and a
        // bytecode frame — here, in host code the engine calls while linking — used to escape
        // JSContext.eval() as a raw java.lang.StackOverflowError.
        IJSModuleLoader overflowingLoader = new IJSModuleLoader() {
            @Override
            public byte[] load(String key) {
                return new byte[0];
            }

            @Override
            public String resolve(String specifier, String referrer) {
                throw new StackOverflowError();
            }
        };
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setModuleLoader(overflowingLoader));
                JSContext moduleContext = runtime.createContext()) {
            assertThatThrownBy(() -> moduleContext.eval("import './dep.mjs';", "/main.mjs", true))
                    .isInstanceOfSatisfying(JSException.class,
                            e -> assertThat(e.getMessage()).isEqualTo("RangeError: Maximum call stack size exceeded"));
            // And the context is still usable.
            assertThat(moduleContext.eval("1 + 1").toString()).isEqualTo("2");
        }
    }

    @Test
    public void testASymbolicLinkOutOfTheRootIsRefused() throws IOException {
        Path outside = outsideDirectory.resolve("target.txt");
        Files.writeString(outside, "outside");
        Path link = moduleDirectory.resolve("link.txt");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException e) {
            assumeThat(false).as("symbolic links are not available: " + e).isTrue();
        }
        JSFileSystemModuleLoader loader = new JSFileSystemModuleLoader(moduleDirectory);
        assertThat(loader.resolve(link.toString(), null)).isNull();
        assertThatThrownBy(() -> loader.load(link.toString())).isInstanceOf(IOException.class);
        assertThat(new JSFileSystemModuleLoader().resolve(link.toString(), null))
                .as("an unrestricted loader follows the link").isEqualTo(link.toString());
    }

    @Test
    public void testLoaderKeysRemainOpaqueDuringModuleEvaluation() {
        String moduleKey = "memory://packages/../dep.mjs";
        List<String> loads = new ArrayList<>();
        IJSModuleLoader loader = new IJSModuleLoader() {
            @Override
            public byte[] load(String key) throws IOException {
                loads.add(key);
                if (!key.equals(moduleKey)) {
                    throw new IOException("unexpected key " + key);
                }
                return "export let value = 41; export function increment() { value++; }"
                        .getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public String resolve(String specifier, String referrer) {
                return specifier.equals("./dep.mjs") || specifier.equals(moduleKey) ? moduleKey : null;
            }
        };
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setModuleLoader(loader));
                JSContext moduleContext = runtime.createContext()) {
            assertThat(evalAndSettle(moduleContext, """
                    import { value, increment } from './dep.mjs';
                    increment();
                    globalThis.readValue = () => value;
                    globalThis.result = String(value);
                    """, "entry.mjs")).isEqualTo("42");
            assertThat(evalAndSettle(moduleContext, """
                    import('./dep.mjs').then(ns => {
                      ns.increment();
                      globalThis.result = [ns.value, readValue()].join(',');
                    });
                    """, "dynamic.mjs")).isEqualTo("43,43");
            assertThat(loads).isNotEmpty().containsOnly(moduleKey);
        }
    }

    @Test
    public void testShadowRealmImportValueGoesThroughTheLoader() throws IOException {
        Path module = moduleDirectory.resolve("realm-module.js");
        Files.writeString(module, "export var answer = 42;\n");
        String modulePath = module.toString().replace("\\", "\\\\");
        String script = """
                globalThis.result = 'pending';
                new ShadowRealm().importValue('%s', 'answer').then(
                  v => { globalThis.result = 'resolved:' + v; },
                  e => { globalThis.result = 'rejected:' + e.name + ': ' + e.message; });
                """.formatted(modulePath);
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setShadowRealmEnabled(true)
                .setModuleLoader(new JSFileSystemModuleLoader(moduleDirectory)));
                JSContext realmContext = runtime.createContext()) {
            realmContext.eval(script, "realm.js", false);
            realmContext.processMicrotasks();
            assertThat(realmContext.eval("globalThis.result").toString()).isEqualTo("resolved:42");
        }
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setShadowRealmEnabled(true)
                .setModuleLoader(new JSFileSystemModuleLoader(moduleDirectory)));
                JSContext realmContext = runtime.createContext()) {
            // Called from Java, with no script frame to supply a referrer.
            JSFunction importValue = (JSFunction) realmContext.eval("ShadowRealm.prototype.importValue");
            JSValue realm = realmContext.eval("new ShadowRealm()");
            JSPromise promise = (JSPromise) importValue.call(realmContext, realm,
                    new JSValue[]{new JSString(module.toString()), new JSString("answer")});
            realmContext.processMicrotasks();
            assertThat(promise.getResult().toString()).isEqualTo("42");
        }
        try (JSRuntime runtime = new JSRuntime(new JSRuntimeOptions().setShadowRealmEnabled(true));
                JSContext realmContext = runtime.createContext()) {
            realmContext.eval(script, "realm.js", false);
            realmContext.processMicrotasks();
            assertThat(realmContext.eval("globalThis.result").toString())
                    .isEqualTo("rejected:TypeError: ShadowRealm import failed");
        }
    }

    private static String evalAndSettle(JSContext moduleContext, String source, String filename) {
        moduleContext.eval(source, filename, true);
        moduleContext.processMicrotasks();
        return moduleContext.eval("globalThis.result").toString();
    }

    private static String outcomeOf(String importExpression) {
        return """
                globalThis.result = 'pending';
                %s.then(
                  m => { globalThis.result = 'resolved:' + String(m.default); },
                  e => { globalThis.result = 'rejected:' + e.name + ': ' + e.message; });
                """.formatted(importExpression);
    }
}
