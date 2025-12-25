package com.caoccao.qjs4j;

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSObject;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSValue;

import static org.junit.jupiter.api.Assertions.*;

public abstract class BaseTest {
    protected void assertPendingException(JSContext ctx) {
        assertTrue(ctx.hasPendingException());
        ctx.clearPendingException();
    }

    protected void assertTypeError(JSValue value) {
        if (value instanceof JSObject jsObject) {
            if (jsObject.get("name") instanceof JSString name && jsObject.get("message") instanceof JSString message) {
                assertEquals("TypeError", name.getValue());
                assertNotNull(message.getValue());
            }
        }
    }
}
