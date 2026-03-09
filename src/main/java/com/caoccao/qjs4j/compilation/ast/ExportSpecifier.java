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

public final class ExportSpecifier extends ASTNode {
    private final Identifier exported;
    private final Identifier local;

    public ExportSpecifier(Identifier local, Identifier exported) {
        super(local != null
                ? local.getLocation()
                : (exported != null ? exported.getLocation() : new SourceLocation(0, 0, 0, 0)));
        this.local = local;
        this.exported = exported;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = local != null && local.containsAwait();
            if (!awaitInside && exported != null && exported.containsAwait()) {
                awaitInside = true;
            }
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = local != null && local.containsYield();
            if (!yieldInside && exported != null && exported.containsYield()) {
                yieldInside = true;
            }
        }
        return yieldInside;
    }

    public Identifier getExported() {
        return exported;
    }

    public Identifier getLocal() {
        return local;
    }
}
