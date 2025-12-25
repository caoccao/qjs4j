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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSRuntime;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Object constructor and methods.
 */
public class ObjectTest extends BaseTest {
    @Test
    public void testObjectKeys() {
        JSValue result = ctx.eval("var obj = {a: 1, b: 2, c: 3}; Object.keys(obj)");
        assertNotNull(result);
        assertEquals("[\"a\", \"b\", \"c\"]", result.toString());
    }

    @Test
    public void testObjectValues() {
        ctx.eval("var obj = {a: 1, b: 2, c: 3}");
        JSValue result = ctx.eval("JSON.stringify(Object.values(obj))");
        assertNotNull(result);
        assertEquals("[1,2,3]", result.toJavaObject());
    }

    @Test
    public void testObjectEntries() {
        ctx.eval("var obj = {a: 1, b: 2, c: 3}");
        JSValue result = ctx.eval("JSON.stringify(Object.entries(obj))");
        assertNotNull(result);
        assertEquals("[[\"a\",1],[\"b\",2],[\"c\",3]]", result.toJavaObject());
    }

    @Test
    public void testObjectAssign() {
        JSValue result = ctx.eval("var target = {a: 1}; Object.assign(target, {b: 2}, {c: 3}); JSON.stringify(target)");
        assertNotNull(result);
        assertEquals("{\"a\":1,\"b\":2,\"c\":3}", result.toJavaObject());
    }

    @Test
    public void testObjectCreate() {
        ctx.eval("var proto = {x: 10}");
        JSValue result = ctx.eval("var newObj = Object.create(proto); newObj.x");
        assertNotNull(result);
        assertEquals(10.0, (Double) result.toJavaObject());
    }

    @Test
    public void testObjectGetPrototypeOf() {
        ctx.eval("var proto = {x: 10}; var newObj = Object.create(proto)");
        JSValue result = ctx.eval("Object.getPrototypeOf(newObj) === proto");
        assertNotNull(result);
        assertTrue((boolean) result.toJavaObject());
    }
}
