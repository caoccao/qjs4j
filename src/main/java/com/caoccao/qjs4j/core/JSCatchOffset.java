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

package com.caoccao.qjs4j.core;

/**
 * Special marker value pushed on stack by CATCH opcode.
 * Contains the PC offset to the catch handler.
 * When an exception occurs, the VM unwinds the stack looking for this marker,
 * then jumps to the catch handler.
 * <p>
 * This is an internal VM type, not a JavaScript value.
 */
public record JSCatchOffset(int offset) implements JSStackValue {

    @Override
    public String toString() {
        return "[CatchOffset:" + offset + "]";
    }
}
