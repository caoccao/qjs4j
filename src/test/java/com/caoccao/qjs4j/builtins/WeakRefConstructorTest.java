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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WeakRef constructor.
 */
public class WeakRefConstructorTest extends BaseTest {

    @Test
    public void testConstruct() {
        // WeakRef constructor must be called with 'new', so direct call should throw error
        assertTypeError(WeakRefConstructor.construct(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSObject()}));
        assertPendingException(ctx);
    }

    @Test
    public void testCreateWeakRef() {
        JSObject target = new JSObject();

        // Normal case: create WeakRef with object
        JSValue result = WeakRefConstructor.createWeakRef(ctx, target);
        assertTrue(result instanceof JSWeakRef);
        JSWeakRef weakRef = (JSWeakRef) result;
        assertSame(target, weakRef.deref());

        // Edge case: target is null
        assertTypeError(WeakRefConstructor.createWeakRef(ctx, JSNull.INSTANCE));
        assertPendingException(ctx);

        // Edge case: target is not an object
        assertTypeError(WeakRefConstructor.createWeakRef(ctx, new JSString("string")));
        assertPendingException(ctx);

        assertTypeError(WeakRefConstructor.createWeakRef(ctx, new JSNumber(42)));
        assertPendingException(ctx);

        assertTypeError(WeakRefConstructor.createWeakRef(ctx, JSBoolean.TRUE));
        assertPendingException(ctx);

        assertTypeError(WeakRefConstructor.createWeakRef(ctx, JSUndefined.INSTANCE));
        assertPendingException(ctx);
    }
}
