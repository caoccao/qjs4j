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
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for WeakRef constructor.
 */
public class WeakRefConstructorTest extends BaseTest {

    @Test
    public void testConstruct() {
        // WeakRef constructor must be called with 'new', so direct call should throw error
        assertTypeError(WeakRefConstructor.construct(context, JSUndefined.INSTANCE, new JSValue[]{new JSObject()}));
        assertPendingException(context);
    }

    @Test
    public void testCreateWeakRef() {
        JSObject target = new JSObject();

        // Normal case: create WeakRef with object
        JSValue result = WeakRefConstructor.createWeakRef(context, target);
        assertInstanceOf(JSWeakRef.class, result);
        JSWeakRef weakRef = (JSWeakRef) result;
        assertSame(target, weakRef.deref());

        // Edge case: target is null
        assertTypeError(WeakRefConstructor.createWeakRef(context, JSNull.INSTANCE));
        assertPendingException(context);

        // Edge case: target is not an object
        assertTypeError(WeakRefConstructor.createWeakRef(context, new JSString("string")));
        assertPendingException(context);

        assertTypeError(WeakRefConstructor.createWeakRef(context, new JSNumber(42)));
        assertPendingException(context);

        assertTypeError(WeakRefConstructor.createWeakRef(context, JSBoolean.TRUE));
        assertPendingException(context);

        assertTypeError(WeakRefConstructor.createWeakRef(context, JSUndefined.INSTANCE));
        assertPendingException(context);
    }
}
