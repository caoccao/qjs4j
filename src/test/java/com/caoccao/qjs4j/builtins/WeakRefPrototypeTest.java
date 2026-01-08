package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

/**
 * Temp tests for WeakRef to verify it's a function.
 */
public class WeakRefPrototypeTest extends BaseJavetTest {

    @Test
    void testWeakRefBasicOperations() {
        assertBooleanWithJavet("""
                const obj = { value: 42 };
                const ref = new WeakRef(obj);
                ref.deref() === obj;
                """);
    }

    @Test
    void testWeakRefDeref() {
        assertIntegerWithJavet("""
                const obj = { x: 100 };
                const ref = new WeakRef(obj);
                ref.deref().x;
                """);
    }

    @Test
    void testWeakRefWithNonObject() {
        assertErrorWithJavet(
                "new WeakRef(null)",
                "new WeakRef(undefined)",
                "new WeakRef(42)",
                "new WeakRef('string')",
                "new WeakRef(true)");
    }
}
