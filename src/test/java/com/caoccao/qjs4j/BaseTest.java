package com.caoccao.qjs4j;

import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

public abstract class BaseTest {
    protected JSContext context;

    protected void assertError(JSValue value) {
        assertError(value, "Error", null);
    }

    protected void assertError(JSValue value, String expectedType, String expectedMessage) {
        if (value instanceof JSObject jsObject) {
            if (jsObject.get("name") instanceof JSString name && jsObject.get("message") instanceof JSString message) {
                assertEquals(expectedType, name.value());
                if (expectedMessage == null) {
                    assertNotNull(message.value());
                } else {
                    assertEquals(expectedMessage, message.value());
                }
            } else {
                fail("Error object does not have name or message property");
            }
        } else {
            fail("Value is not an error object");
        }
    }

    protected void assertPendingException(JSContext ctx) {
        assertTrue(ctx.hasPendingException());
        ctx.clearPendingException();
    }

    protected void assertRangeError(JSValue value) {
        assertError(value, "RangeError", null);
    }

    protected void assertSyntaxError(JSValue value) {
        assertError(value, "SyntaxError", null);
    }

    protected void assertTypeError(JSValue value) {
        assertError(value, "TypeError", null);
    }

    protected void assertTypeError(JSValue value, String expectedMessage) {
        assertError(value, "TypeError", expectedMessage);
    }

    protected boolean awaitPromise(JSPromise promise) {
        for (int i = 0; i < 1000 && promise.getState() == JSPromise.PromiseState.PENDING; i++) {
            context.processMicrotasks();
        }
        return promise.getState() != JSPromise.PromiseState.PENDING;
    }

    @BeforeEach
    public void setUp() {
        context = new JSContext(new JSRuntime());
    }

    @AfterEach
    public void tearDown() {
        context.getRuntime().gc();
        context.close();
    }
}
