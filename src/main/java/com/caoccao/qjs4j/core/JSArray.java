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
 * Represents a JavaScript Array object.
 * Optimizes dense arrays with a dedicated storage array.
 */
public final class JSArray extends JSObject {
    private JSValue[] denseArray;
    private long length;

    public JSArray() {
        this.denseArray = new JSValue[0];
        this.length = 0;
    }

    public JSArray(long length) {
        this.length = length;
        this.denseArray = new JSValue[(int) Math.min(length, 1024)];
    }

    public long getLength() {
        return length;
    }

    public void setLength(long newLength) {
        this.length = newLength;
    }

    public JSValue get(long index) {
        return null;
    }

    public void set(long index, JSValue value) {
    }

    public void push(JSValue value) {
    }

    public JSValue pop() {
        return null;
    }

    public JSValue shift() {
        return null;
    }

    public void unshift(JSValue value) {
    }
}
