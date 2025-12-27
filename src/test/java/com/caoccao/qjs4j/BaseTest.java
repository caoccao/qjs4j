package com.caoccao.qjs4j;

import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

public abstract class BaseTest {
    protected JSContext ctx;
    protected JSString str;

    protected void assertError(JSValue value) {
        if (value instanceof JSObject jsObject) {
            if (jsObject.get("name") instanceof JSString name && jsObject.get("message") instanceof JSString message) {
                assertEquals("Error", name.value());
                assertNotNull(message.value());
            }
        }
    }

    protected void assertPendingException(JSContext ctx) {
        assertTrue(ctx.hasPendingException());
        ctx.clearPendingException();
    }

    protected void assertRangeError(JSValue value) {
        if (value instanceof JSObject jsObject) {
            if (jsObject.get("name") instanceof JSString name && jsObject.get("message") instanceof JSString message) {
                assertEquals("RangeError", name.value());
                assertNotNull(message.value());
            }
        }
    }

    protected void assertSyntaxError(JSValue value) {
        if (value instanceof JSObject jsObject) {
            if (jsObject.get("name") instanceof JSString name && jsObject.get("message") instanceof JSString message) {
                assertEquals("SyntaxError", name.value());
                assertNotNull(message.value());
            }
        }
    }

    protected void assertTypeError(JSValue value) {
        if (value instanceof JSObject jsObject) {
            if (jsObject.get("name") instanceof JSString name && jsObject.get("message") instanceof JSString message) {
                assertEquals("TypeError", name.value());
                assertNotNull(message.value());
            }
        }
    }

    protected boolean awaitPromise(JSPromise promise) {
        for (int i = 0; i < 1000 && promise.getState() == JSPromise.PromiseState.PENDING; i++) {
            ctx.processMicrotasks();
        }
        return promise.getState() != JSPromise.PromiseState.PENDING;
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
