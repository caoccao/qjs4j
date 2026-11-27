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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Loads modules from the file system, optionally confined to one directory.
 * <p>
 * A specifier is a path. A relative one is resolved against the directory of the importing module, an absolute one is
 * taken as it is, and the normalised path is the module's key.
 * <p>
 * Constructed with a root, the loader refuses every module whose real path — after {@code ..} segments and symbolic
 * links are resolved — is not inside that root, so a script can read nothing else the process can. Constructed without
 * one, it can read any file the process can, which is what the command-line interpreter wants and what an embedder
 * running untrusted code does not.
 */
public final class JSFileSystemModuleLoader implements IJSModuleLoader {
    private final Path root;

    /**
     * Create a loader that may read any file the process can read.
     */
    public JSFileSystemModuleLoader() {
        root = null;
    }

    /**
     * Create a loader that reads only files inside a root directory.
     *
     * @param root
     *            the directory every module must be inside
     */
    public JSFileSystemModuleLoader(Path root) {
        this.root = Objects.requireNonNull(root).toAbsolutePath().normalize();
    }

    private boolean isPermitted(Path path) {
        if (root == null) {
            return true;
        }
        try {
            return path.toRealPath().startsWith(root.toRealPath());
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public byte[] load(String key) throws IOException {
        Path path = toPath(key);
        if (!isPermitted(path)) {
            throw new IOException("Module is outside the module root: " + key);
        }
        return Files.readAllBytes(path);
    }

    @Override
    public String resolve(String specifier, String referrer) {
        Path resolvedPath;
        try {
            resolvedPath = Paths.get(specifier);
            if (!resolvedPath.isAbsolute() && referrer != null && !referrer.isEmpty() && !referrer.startsWith("<")) {
                Path parentPath = Paths.get(referrer).getParent();
                if (parentPath != null) {
                    resolvedPath = parentPath.resolve(resolvedPath);
                }
            }
        } catch (InvalidPathException e) {
            return null;
        }
        resolvedPath = resolvedPath.normalize();
        if (!Files.exists(resolvedPath) || !isPermitted(resolvedPath)) {
            return null;
        }
        return resolvedPath.toString();
    }

    private static Path toPath(String key) throws IOException {
        try {
            return Paths.get(key);
        } catch (InvalidPathException e) {
            throw new IOException("Invalid module path: " + key, e);
        }
    }
}
