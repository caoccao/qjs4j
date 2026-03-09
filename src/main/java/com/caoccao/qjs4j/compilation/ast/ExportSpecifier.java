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
                ? local.location()
                : (exported != null ? exported.location() : new SourceLocation(0, 0, 0, 0)));
        this.local = local;
        this.exported = exported;
    }

    @Override
    public boolean containsAwait() {
        if (awaitInside == null) {
            awaitInside = false;
            if (local != null && local.containsAwait()) {
                awaitInside = true;
            }
            if (!awaitInside && exported != null && exported.containsAwait()) {
                awaitInside = true;
            }
        }
        return awaitInside;
    }

    @Override
    public boolean containsYield() {
        if (yieldInside == null) {
            yieldInside = false;
            if (local != null && local.containsYield()) {
                yieldInside = true;
            }
            if (!yieldInside && exported != null && exported.containsYield()) {
                yieldInside = true;
            }
        }
        return yieldInside;
    }

    public Identifier exported() {
        return exported;
    }

    public Identifier local() {
        return local;
    }
}
