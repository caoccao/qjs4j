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

package com.caoccao.qjs4j.compilation.ast;

import java.util.List;

/**
 * Represents an import declaration.
 */
public final class ImportDeclaration extends ModuleItem {
    private final Literal source;
    private final List<ImportSpecifier> specifiers;

    public ImportDeclaration(List<ImportSpecifier> specifiers, Literal source, SourceLocation location) {
        super(location);
        this.specifiers = specifiers;
        this.source = source;
    }

    public Literal getSource() {
        return source;
    }

    public List<ImportSpecifier> getSpecifiers() {
        return specifiers;
    }

}
