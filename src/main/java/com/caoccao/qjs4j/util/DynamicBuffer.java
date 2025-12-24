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

package com.caoccao.qjs4j.util;

/**
 * Auto-growing byte buffer similar to C's DynBuf.
 */
public final class DynamicBuffer {
    private byte[] buffer;
    private int size;

    public DynamicBuffer() {
        this.buffer = new byte[64];
        this.size = 0;
    }

    public void append(byte b) {
    }

    public void append(byte[] bytes) {
    }

    public void append(byte[] bytes, int offset, int length) {
    }

    public byte[] toByteArray() {
        byte[] result = new byte[size];
        System.arraycopy(buffer, 0, result, 0, size);
        return result;
    }

    public int size() {
        return size;
    }

    private void ensureCapacity(int required) {
    }
}
