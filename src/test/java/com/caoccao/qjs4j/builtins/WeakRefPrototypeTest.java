package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for WeakRef.prototype methods.
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
    void testWeakRefDescriptors() {
        assertBooleanWithJavet("""
                (() => {
                  const derefDesc = Object.getOwnPropertyDescriptor(WeakRef.prototype, 'deref');
                  const tagDesc = Object.getOwnPropertyDescriptor(WeakRef.prototype, Symbol.toStringTag);
                  const ctorDesc = Object.getOwnPropertyDescriptor(WeakRef, 'prototype');
                  return !!derefDesc
                    && typeof derefDesc.value === 'function'
                    && derefDesc.writable
                    && !derefDesc.enumerable
                    && derefDesc.configurable
                    && !!tagDesc
                    && tagDesc.value === 'WeakRef'
                    && !tagDesc.writable
                    && !tagDesc.enumerable
                    && tagDesc.configurable
                    && typeof tagDesc.get === 'undefined'
                    && !!ctorDesc
                    && !ctorDesc.writable
                    && !ctorDesc.enumerable
                    && !ctorDesc.configurable;
                })()""");
    }

    @Test
    void testWeakRefPrototypeDerefReceiverValidation() {
        assertThatThrownBy(() -> context.eval("WeakRef.prototype.deref.call({})"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
    }
}
