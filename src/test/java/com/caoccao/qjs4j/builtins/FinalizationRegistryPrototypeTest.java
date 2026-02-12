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

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for FinalizationRegistry.prototype methods.
 */
public class FinalizationRegistryPrototypeTest extends BaseJavetTest {

    @Test
    public void testObjectToString() {
        assertStringWithJavet(
                "Object.prototype.toString.call(new FinalizationRegistry(function(){}))");
    }

    @Test
    public void testRegisterDescriptor() {
        assertBooleanWithJavet(
                "Object.getOwnPropertyDescriptor(FinalizationRegistry.prototype, 'register').writable === true",
                "Object.getOwnPropertyDescriptor(FinalizationRegistry.prototype, 'register').enumerable === false",
                "Object.getOwnPropertyDescriptor(FinalizationRegistry.prototype, 'register').configurable === true");
    }

    @Test
    public void testRegisterHeldValueCanBeAnything() {
        assertUndefinedWithJavet(
                "var fr = new FinalizationRegistry(function(){}); fr.register({}, 42)",
                "var fr = new FinalizationRegistry(function(){}); fr.register({}, 'held')",
                "var fr = new FinalizationRegistry(function(){}); fr.register({}, null)",
                "var fr = new FinalizationRegistry(function(){}); fr.register({}, undefined)",
                "var fr = new FinalizationRegistry(function(){}); fr.register({}, true)",
                "var fr = new FinalizationRegistry(function(){}); fr.register({}, Symbol('s'))");
    }

    @Test
    public void testRegisterHeldValueCannotBeTarget() {
        assertThatThrownBy(() -> context.eval(
                "var fr = new FinalizationRegistry(function(){}); var obj = {}; fr.register(obj, obj)"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
    }

    @Test
    public void testRegisterInvalidTarget() {
        for (String code : new String[]{
                "var fr = new FinalizationRegistry(function(){}); fr.register(42, 'held')",
                "var fr = new FinalizationRegistry(function(){}); fr.register('str', 'held')",
                "var fr = new FinalizationRegistry(function(){}); fr.register(true, 'held')",
                "var fr = new FinalizationRegistry(function(){}); fr.register(null, 'held')",
                "var fr = new FinalizationRegistry(function(){}); fr.register(undefined, 'held')"}) {
            assertThatThrownBy(() -> context.eval(code))
                    .isInstanceOf(JSException.class)
                    .hasMessageContaining("TypeError");
        }
    }

    @Test
    public void testRegisterInvalidToken() {
        for (String code : new String[]{
                "var fr = new FinalizationRegistry(function(){}); fr.register({}, 'held', 42)",
                "var fr = new FinalizationRegistry(function(){}); fr.register({}, 'held', 'str')",
                "var fr = new FinalizationRegistry(function(){}); fr.register({}, 'held', true)",
                "var fr = new FinalizationRegistry(function(){}); fr.register({}, 'held', null)"}) {
            assertThatThrownBy(() -> context.eval(code))
                    .isInstanceOf(JSException.class)
                    .hasMessageContaining("TypeError");
        }
    }

    @Test
    public void testRegisterLength() {
        assertIntegerWithJavet("FinalizationRegistry.prototype.register.length");
    }

    @Test
    public void testRegisterName() {
        assertStringWithJavet("FinalizationRegistry.prototype.register.name");
    }

    @Test
    public void testRegisterReturnsUndefined() {
        assertUndefinedWithJavet(
                "new FinalizationRegistry(function(){}).register({}, 'held')");
    }

    @Test
    public void testRegisterWithObjectToken() {
        assertUndefinedWithJavet(
                "var fr = new FinalizationRegistry(function(){}); fr.register({}, 'held', {})");
    }

    @Test
    public void testRegisterWithSymbolToken() {
        assertUndefinedWithJavet(
                "var fr = new FinalizationRegistry(function(){}); fr.register({}, 'held', Symbol('tok'))");
    }

    @Test
    public void testRegisterWithUndefinedToken() {
        assertUndefinedWithJavet(
                "var fr = new FinalizationRegistry(function(){}); fr.register({}, 'held', undefined)");
    }

    @Test
    public void testToStringTagDescriptor() {
        assertBooleanWithJavet(
                "Object.getOwnPropertyDescriptor(FinalizationRegistry.prototype, Symbol.toStringTag).configurable === true",
                "Object.getOwnPropertyDescriptor(FinalizationRegistry.prototype, Symbol.toStringTag).enumerable === false",
                "Object.getOwnPropertyDescriptor(FinalizationRegistry.prototype, Symbol.toStringTag).writable === false");
    }

    @Test
    public void testToStringTagValue() {
        assertStringWithJavet(
                "FinalizationRegistry.prototype[Symbol.toStringTag]");
    }

    @Test
    public void testUnregisterDescriptor() {
        assertBooleanWithJavet(
                "Object.getOwnPropertyDescriptor(FinalizationRegistry.prototype, 'unregister').writable === true",
                "Object.getOwnPropertyDescriptor(FinalizationRegistry.prototype, 'unregister').enumerable === false",
                "Object.getOwnPropertyDescriptor(FinalizationRegistry.prototype, 'unregister').configurable === true");
    }

    @Test
    public void testUnregisterInvalidToken() {
        for (String code : new String[]{
                "var fr = new FinalizationRegistry(function(){}); fr.unregister(42)",
                "var fr = new FinalizationRegistry(function(){}); fr.unregister('str')",
                "var fr = new FinalizationRegistry(function(){}); fr.unregister(true)",
                "var fr = new FinalizationRegistry(function(){}); fr.unregister(null)",
                "var fr = new FinalizationRegistry(function(){}); fr.unregister(undefined)"}) {
            assertThatThrownBy(() -> context.eval(code))
                    .isInstanceOf(JSException.class)
                    .hasMessageContaining("TypeError");
        }
    }

    @Test
    public void testUnregisterLength() {
        assertIntegerWithJavet("FinalizationRegistry.prototype.unregister.length");
    }

    @Test
    public void testUnregisterName() {
        assertStringWithJavet("FinalizationRegistry.prototype.unregister.name");
    }

    @Test
    public void testUnregisterNotFound() {
        assertBooleanWithJavet(
                "var fr = new FinalizationRegistry(function(){}); fr.unregister({}) === false");
    }

    @Test
    public void testUnregisterRegistered() {
        assertBooleanWithJavet(
                "var fr = new FinalizationRegistry(function(){}); var tok = {}; fr.register({}, 'held', tok); fr.unregister(tok) === true");
    }

    @Test
    public void testUnregisterReturnType() {
        assertStringWithJavet(
                "var fr = new FinalizationRegistry(function(){}); typeof fr.unregister({})");
    }

    @Test
    public void testUnregisterSecondCallReturnsFalse() {
        assertBooleanWithJavet(
                "var fr = new FinalizationRegistry(function(){}); var tok = {}; fr.register({}, 'held', tok); fr.unregister(tok); fr.unregister(tok) === false");
    }
}
