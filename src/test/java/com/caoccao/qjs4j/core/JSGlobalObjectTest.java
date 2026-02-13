package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for global object functions.
 */
public class JSGlobalObjectTest extends BaseJavetTest {
    @Test
    public void testEscape() {
        assertStringWithJavet(
                // Test basic ASCII characters that should not be escaped
                "escape('abc')",
                "escape('ABC')",
                "escape('123')",
                "escape('@*_+-./')",
                // Test characters that should be escaped with %XX format
                "escape('Hello World')",
                "escape('a b c')",
                "escape('!#$%&()=')",
                "escape('a=b&c=d')",
                "escape('test:value')",
                "escape('<script>')",
                // Test Unicode characters (should use %uXXXX format)
                "escape('你好')",
                "escape('世界')",
                "escape('Hello 世界')",
                "escape('abc中def')",
                // Test empty string
                "escape('')",
                // Test special cases
                "escape('100% correct')",
                "escape('email@domain.com')",
                "escape('path/to/file.txt')",
                "JSON.stringify(Object.getOwnPropertyDescriptor(escape, \"name\"))",
                "JSON.stringify(Object.getOwnPropertyDescriptor(escape, \"length\"))");
    }

    @Test
    public void testEscapeEdgeCases() {
        assertStringWithJavet(
                // Test with numbers and other types (should convert to string first)
                "escape('42')",
                "escape('3.14')",
                // Test consecutive spaces and special characters
                "escape('  ')",
                "escape('!!!')",
                "escape('###')");
    }

    @Test
    public void testEscapeUnescapeRoundTrip() {
        // Test that escape and unescape are inverses
        assertStringWithJavet(
                "unescape(escape('Hello World'))",
                "unescape(escape('test@example.com'))",
                "unescape(escape('a=b&c=d'))",
                "unescape(escape('你好世界'))",
                "unescape(escape('Hello 世界!'))",
                "unescape(escape('!@#$%^&*()'))",
                "unescape(escape(''))");
    }

    @Test
    public void testGlobalDescriptorsAndTagWithQuickJSSemantics() {
        assertBooleanWithJavet(
                "Object.getOwnPropertyDescriptor(globalThis, 'parseInt').writable === true",
                "Object.getOwnPropertyDescriptor(globalThis, 'parseInt').enumerable === false",
                "Object.getOwnPropertyDescriptor(globalThis, 'parseInt').configurable === true",
                "Object.getOwnPropertyDescriptor(globalThis, 'Object').enumerable === false",
                "Object.getOwnPropertyDescriptor(globalThis, 'Infinity').writable === false",
                "Object.getOwnPropertyDescriptor(globalThis, 'Infinity').enumerable === false",
                "Object.getOwnPropertyDescriptor(globalThis, 'Infinity').configurable === false",
                "Object.getOwnPropertyDescriptor(globalThis, 'NaN').writable === false",
                "Object.getOwnPropertyDescriptor(globalThis, 'undefined').writable === false",
                "Object.getOwnPropertyDescriptor(globalThis, 'globalThis').writable === true",
                "Object.getOwnPropertyDescriptor(globalThis, 'globalThis').enumerable === false",
                "Object.getOwnPropertyDescriptor(globalThis, 'globalThis').configurable === true");
        assertThat(context.eval("globalThis[Symbol.toStringTag]").toJavaObject()).isEqualTo("global");
        assertThat(context.eval("Object.prototype.toString.call(globalThis)").toJavaObject()).isEqualTo("[object global]");
    }

    @Test
    public void testGlobalFunctionErrorsWithQuickJSSemantics() {
        String[] codeArray = {
                "new parseInt()",
                "new isNaN()",
                "new decodeURI()",
                "isNaN(Symbol())",
                "isFinite(Symbol())",
                "isNaN(1n)",
                "isFinite(1n)",
                "isNaN(Object(Symbol()))",
                "isFinite(Object(Symbol()))",
                "isNaN(Object(1n))",
                "isFinite(Object(1n))"
        };
        for (String code : codeArray) {
            assertThatThrownBy(() -> context.eval(code))
                    .as(code)
                    .isInstanceOf(JSException.class)
                    .hasMessageContaining("TypeError");
        }
    }

    @Test
    public void testGlobalFunctionsAreNotConstructors() {
        assertBooleanWithJavet("""
                (() => {
                  const isConstructor = (fn) => {
                    if (typeof fn !== 'function') {
                      return false;
                    }
                    try {
                      Reflect.construct(function () {}, [], fn);
                      return true;
                    } catch {
                      return false;
                    }
                  };
                
                  const functionPrototypeArguments = Object.getOwnPropertyDescriptor(Function.prototype, 'arguments');
                  const functionPrototypeCaller = Object.getOwnPropertyDescriptor(Function.prototype, 'caller');
                
                  const nonConstructors = [
                    parseInt,
                    parseFloat,
                    isNaN,
                    isFinite,
                    eval,
                    encodeURI,
                    decodeURI,
                    encodeURIComponent,
                    decodeURIComponent,
                    escape,
                    unescape,
                    Function.prototype,
                    Function.prototype.call,
                    Function.prototype.apply,
                    Function.prototype.bind,
                    Function.prototype.toString,
                    functionPrototypeArguments.get,
                    functionPrototypeArguments.set,
                    functionPrototypeCaller.get,
                    functionPrototypeCaller.set
                  ];
                
                  return nonConstructors.every((fn) => !isConstructor(fn));
                })();
                """);
    }

    @Test
    public void testUnescape() {
        assertStringWithJavet(
                // Test basic %XX sequences
                "unescape('%20')",
                "unescape('%21')",
                "unescape('%40')",
                // Test %uXXXX sequences for Unicode
                "unescape('%u4F60')",
                "unescape('%u597D')",
                "unescape('%u4F60%u597D')",
                // Test mixed sequences
                "unescape('Hello%20World')",
                "unescape('%21%23%24')",
                "unescape('test%3Avalue')",
                // Test strings without escape sequences
                "unescape('abc')",
                "unescape('123')",
                "unescape('')",
                // Test invalid sequences (should be treated as literals)
                "unescape('%')",
                "unescape('%Z')",
                "unescape('%uGGGG')",
                "JSON.stringify(Object.getOwnPropertyDescriptor(unescape, \"name\"))",
                "JSON.stringify(Object.getOwnPropertyDescriptor(unescape, \"length\"))");
    }
}
