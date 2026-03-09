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
 * Represents an export declaration.
 */
public final class ExportDeclaration extends ModuleItem {
    private final Declaration declaration;
    private final Literal source;
    private final List<ExportSpecifier> specifiers;

    public ExportDeclaration(Declaration declaration, List<ExportSpecifier> specifiers, Literal source, SourceLocation location) {
        super(location);
        this.declaration = declaration;
        this.specifiers = specifiers;
        this.source = source;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = declaration != null && declaration.containsAwait();
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = declaration != null && declaration.containsYield();
        }
        return yieldInside;
    }

    public Declaration getDeclaration() {
        return declaration;
    }

    public Literal getSource() {
        return source;
    }

    public List<ExportSpecifier> getSpecifiers() {
        return specifiers;
    }

}
