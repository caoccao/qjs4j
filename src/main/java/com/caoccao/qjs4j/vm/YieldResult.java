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

package com.caoccao.qjs4j.vm;

import com.caoccao.qjs4j.core.JSObject;
import com.caoccao.qjs4j.core.JSValue;

/**
 * Special return value to indicate generator yield.
 * Based on QuickJS FUNC_RET_YIELD constants.
 * <p>
 * When the VM encounters a yield opcode, it returns this special value
 * instead of a regular JSValue. The generator execution logic can then
 * detect this and suspend execution properly.
 *
 * @param value            The yielded value
 * @param delegateIterator The delegated iterator for yield* (optional)
 */
public record YieldResult(Type type, JSValue value, JSObject delegateIterator) {
    public YieldResult(Type type, JSValue value) {
        this(type, value, null);
    }

    public boolean isInitialYield() {
        return type == Type.INITIAL_YIELD;
    }

    public boolean isYield() {
        return type == Type.YIELD || type == Type.INITIAL_YIELD;
    }

    public boolean isYieldStar() {
        return type == Type.YIELD_STAR;
    }

    /**
     * Yield types matching QuickJS FUNC_RET_* constants
     */
    public enum Type {
        INITIAL_YIELD,  // Initial yield at generator start (FUNC_RET_INITIAL_YIELD)
        YIELD,          // Regular yield (FUNC_RET_YIELD)
        YIELD_STAR      // Delegating yield* (FUNC_RET_YIELD_STAR)
    }
}
