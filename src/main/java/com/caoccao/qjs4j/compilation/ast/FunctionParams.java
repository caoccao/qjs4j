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
 * Holds function parameters: regular params, default values, and optional rest parameter.
 */
public record FunctionParams(List<Pattern> params, List<Expression> defaults, RestParameter restParameter) {
    public int computeDefinedArgCount() {
        if (defaults == null) {
            return params.size();
        }
        int count = 0;
        for (int i = 0; i < params.size(); i++) {
            if (i < defaults.size() && defaults.get(i) != null) {
                break;
            }
            count++;
        }
        return count;
    }

    public boolean hasNonSimpleParameters() {
        if (restParameter != null) {
            return true;
        }
        if (defaults != null) {
            for (Expression d : defaults) {
                if (d != null) {
                    return true;
                }
            }
        }
        if (params != null) {
            for (Pattern param : params) {
                if (!(param instanceof Identifier)) {
                    return true;
                }
            }
        }
        return false;
    }
}
