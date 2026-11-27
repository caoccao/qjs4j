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

import com.caoccao.qjs4j.core.JSRuntimeOptions;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * The host hook through which every module is located and read: QuickJS's {@code JS_SetModuleLoaderFunc}, V8's
 * {@code HostLoadImportedModule}.
 * <p>
 * The engine never touches a file system on its own. Static {@code import}, dynamic {@code import()}, JSON, text and
 * bytes payloads and {@code ShadowRealm.prototype.importValue} all resolve their specifier and read their payload
 * through the loader installed with {@link JSRuntimeOptions#setModuleLoader(IJSModuleLoader)}. A runtime without one
 * loads no module at all: every import fails with {@code TypeError: Cannot find module}.
 * <p>
 * {@link JSFileSystemModuleLoader} is the file-system implementation, optionally confined to one directory. An embedder
 * serving modules from memory, a database or a bundle implements the two methods below.
 */
public interface IJSModuleLoader {
    /**
     * Read the payload of a module that {@link #resolve(String, String)} returned the key of.
     *
     * @param key
     *            the module's key
     * @return the payload's bytes; JavaScript, JSON and text payloads are decoded as UTF-8
     * @throws IOException
     *             when the module cannot be read
     */
    byte[] load(String key) throws IOException;

    /**
     * Read a module's payload as text.
     *
     * @param key
     *            the module's key
     * @return the payload decoded as UTF-8
     * @throws IOException
     *             when the module cannot be read, or its payload is not well-formed UTF-8
     */
    default String loadText(String key) throws IOException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(load(key))).toString();
    }

    /**
     * Resolve a module specifier to the key that identifies the module.
     * <p>
     * The key is the module's identity: two specifiers resolving to the same key import the same module instance, and
     * the key is the referrer passed back here when the module itself imports something.
     *
     * @param specifier
     *            the specifier as written
     * @param referrer
     *            the key or file name of the importing module, or {@code null} when there is none; synthetic names
     *            start with {@code <}
     * @return the module's key, or {@code null} when no such module exists or it may not be loaded
     */
    String resolve(String specifier, String referrer);
}
