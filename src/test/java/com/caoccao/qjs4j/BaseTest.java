package com.caoccao.qjs4j;

import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

public abstract class BaseTest {
    protected JSContext ctx;
    protected JSString str;

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

    protected void assertRangeError(JSValue value) {
        if (value instanceof JSObject jsObject) {
            if (jsObject.get("name") instanceof JSString name && jsObject.get("message") instanceof JSString message) {
                assertEquals("RangeError", name.getValue());
                assertNotNull(message.getValue());
            }
        }
    }

    protected void assertSyntaxError(JSValue value) {
        if (value instanceof JSObject jsObject) {
            if (jsObject.get("name") instanceof JSString name && jsObject.get("message") instanceof JSString message) {
                assertEquals("SyntaxError", name.getValue());
                assertNotNull(message.getValue());
            }
        }
    }

    protected void assertError(JSValue value) {
        if (value instanceof JSObject jsObject) {
            if (jsObject.get("name") instanceof JSString name && jsObject.get("message") instanceof JSString message) {
                assertEquals("Error", name.getValue());
                assertNotNull(message.getValue());
            }
        }
    }

    @BeforeEach
    public void setUp() {
        ctx = new JSContext(new JSRuntime());
        str = new JSString("hello world");
    }

    @AfterEach
    public void tearDown() {
        ctx.getRuntime().gc();
        ctx.close();
    }
}
