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

import com.caoccao.qjs4j.core.JSKeyword;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility methods for AST analysis.
 */
public final class AstUtils {

    private AstUtils() {
    }

    public static Set<String> buildParameterNames(List<Pattern> params, List<Statement> body) {
        Set<String> paramNames = new HashSet<>();
        for (Pattern param : params) {
            paramNames.addAll(param.getBoundNames());
        }
        if (!paramNames.contains(JSKeyword.ARGUMENTS)) {
            boolean hasVarArguments = body != null
                    && body.stream().anyMatch(Statement::containsVarArguments);
            if (!hasVarArguments) {
                paramNames.add(JSKeyword.ARGUMENTS);
            }
        }
        return paramNames;
    }

}
