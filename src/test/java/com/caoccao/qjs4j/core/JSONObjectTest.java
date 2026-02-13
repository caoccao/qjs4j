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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JSONObject methods.
 */
public class JSONObjectTest extends BaseJavetTest {

    @Test
    public void testComplexJSON() {
        // Test complex nested structure
        String complexJson = """
                {
                  "users": [
                    {
                      "name": "Alice",
                      "age": 30,
                      "active": true,
                      "tags": ["developer", "admin"]
                    },
                    {
                      "name": "Bob",
                      "age": 25,
                      "active": false,
                      "tags": ["user"]
                    }
                  ],
                  "metadata": {
                    "version": "1.0",
                    "count": 2
                  }
                }
                """;

        JSValue result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString(complexJson)});
        JSObject root = result.asObject().orElseThrow();

        // Check users array
        JSArray users = root.get("users").asArray().orElseThrow();
        assertThat(users.getLength()).isEqualTo(2);

        // Check first user
        JSObject user1 = users.get(0).asObject().orElseThrow();
        assertThat(user1.get("name").asString().map(JSString::value).orElseThrow()).isEqualTo("Alice");
        assertThat(user1.get("age").asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(30.0);
        assertThat(user1.get("active")).isEqualTo(JSBoolean.TRUE);

        JSArray tags1 = user1.get("tags").asArray().orElseThrow();
        assertThat(tags1.getLength()).isEqualTo(2);
        assertThat(tags1.get(0).asString().map(JSString::value).orElseThrow()).isEqualTo("developer");
        assertThat(tags1.get(1).asString().map(JSString::value).orElseThrow()).isEqualTo("admin");

        // Check metadata
        JSObject metadata = root.get("metadata").asObject().orElseThrow();
        assertThat(metadata.get("version").asString().map(JSString::value).orElseThrow()).isEqualTo("1.0");
        assertThat(metadata.get("count").asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);

        // Test stringify back
        JSValue stringified = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{result});
        assertThat(stringified.isString()).isTrue();

        // Parse again to ensure round-trip works
        JSValue reparsed = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{stringified});
        JSObject reparsedObj = reparsed.asObject().orElseThrow();
    }

    @Test
    public void testParse() {
        // Normal case: parse null
        JSValue result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("null")});
        assertThat(result).isEqualTo(JSNull.INSTANCE);

        // Normal case: parse boolean true
        result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("true")});
        assertThat(result.isBooleanTrue()).isTrue();

        // Normal case: parse boolean false
        result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("false")});
        assertThat(result.isBooleanFalse()).isTrue();

        // Normal case: parse number
        result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("42")});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.0);

        // Normal case: parse negative number
        result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("-123.45")});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(-123.45);

        // Normal case: parse string
        result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("\"hello world\"")});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("hello world");

        // Normal case: parse string with escapes
        result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("\"hello\\nworld\"")});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("hello\nworld");

        // Normal case: parse empty array
        result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("[]")});
        JSArray emptyArr = result.asArray().orElseThrow();
        assertThat(emptyArr.getLength()).isEqualTo(0);

        // Normal case: parse array with values
        result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("[1, \"two\", true]")});
        JSArray arr = result.asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(3);
        assertThat(arr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(1.0);
        assertThat(arr.get(1).asString().map(JSString::value).orElseThrow()).isEqualTo("two");
        assertThat(arr.get(2)).isEqualTo(JSBoolean.TRUE);

        // Normal case: parse empty object
        result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("{}")});
        result.asObject().orElseThrow();

        // Normal case: parse object with properties
        result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("{\"name\": \"test\", \"value\": 123}")});
        JSObject obj = result.asObject().orElseThrow();
        assertThat(obj.get("name").asString().map(JSString::value).orElseThrow()).isEqualTo("test");
        assertThat(obj.get("value").asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(123.0);

        // Normal case: parse nested object
        result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("{\"data\": {\"nested\": true}}")});
        obj = result.asObject().orElseThrow();
        JSObject data = obj.get("data").asObject().orElseThrow();
        assertThat(data.get("nested")).isEqualTo(JSBoolean.TRUE);

        // Normal case: parse with whitespace
        result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("  {  \"key\"  :  \"value\"  }  ")});
        obj = result.asObject().orElseThrow();
        assertThat(obj.get("key").asString().map(JSString::value).orElseThrow()).isEqualTo("value");

        // Edge case: empty string
        result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("")});
        assertSyntaxError(result);
        assertPendingException(context);

        // Edge case: invalid JSON
        result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("{invalid}")});
        assertSyntaxError(result);
        assertPendingException(context);

        // Edge case: unterminated string
        result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("\"unterminated")});
        assertSyntaxError(result);
        assertPendingException(context);

        // Edge case: no arguments
        result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertSyntaxError(result);
        assertPendingException(context);

        // Edge case: non-string argument (should be converted to string)
        result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(123)});
        assertThat(result.asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(123.0);
    }

    @Test
    public void testParseArrayElement() {
        assertIntegerWithJavet("JSON.parse('[1,2,3]')[1]");
    }

    @Test
    public void testParseArrayLength() {
        assertIntegerWithJavet("JSON.parse('[1,\"two\",true,null]').length");
    }

    @Test
    public void testParseBoolean() {
        assertBooleanWithJavet("JSON.parse('true')");
    }

    @Test
    public void testParseComplexNested() {
        assertStringWithJavet("""
                var json = '{"users":[{"name":"Alice","age":30},{"name":"Bob","age":25}],"metadata":{"version":"1.0"}}';
                var obj = JSON.parse(json);
                obj.users[0].name""");
    }

    @Test
    public void testParseEmptyString() {
        assertErrorWithJavet("JSON.parse('')");
    }

    @Test
    public void testParseEscapedNewline() {
        assertStringWithJavet("JSON.parse('\"hello\\\\nworld\"')");
    }

    @Test
    public void testParseEscapedTab() {
        assertStringWithJavet("JSON.parse('\"tab\\\\there\"')");
    }

    @Test
    public void testParseInvalidJSON() {
        assertErrorWithJavet("JSON.parse('{invalid}')");
    }

    @Test
    public void testParseInvalidNumber() {
        assertErrorWithJavet("JSON.parse('01')"); // Leading zeros not allowed
    }

    @Test
    public void testParseNestedArray() {
        assertIntegerWithJavet("JSON.parse('{\"items\":[1,2,3]}').items[1]");
    }

    @Test
    public void testParseNestedObject() {
        assertStringWithJavet("JSON.parse('{\"user\":{\"name\":\"Alice\",\"age\":30}}').user.name");
    }

    @Test
    public void testParseNoArguments() {
        assertErrorWithJavet("JSON.parse()");
    }

    @Test
    public void testParseNull() {
        assertStringWithJavet("String(JSON.parse('null'))");
    }

    @Test
    public void testParseNumber() {
        assertIntegerWithJavet("JSON.parse('42')");
    }

    @Test
    public void testParseObjectProperty() {
        assertIntegerWithJavet("JSON.parse('{\"a\":1,\"b\":2}').a");
    }

    @Test
    public void testParseObjectStringProperty() {
        assertStringWithJavet("JSON.parse('{\"name\":\"test\",\"value\":42}').name");
    }

    @Test
    public void testParseString() {
        assertStringWithJavet("JSON.parse('\"hello\"')");
    }

    @Test
    public void testParseTrailingComma() {
        assertErrorWithJavet("JSON.parse('{\"a\":1,}')");
    }

    @Test
    public void testParseTrailingData() {
        assertBooleanWithJavet("""
                (() => {
                  try {
                    JSON.parse('1 2');
                    return false;
                  } catch (e) {
                    return e instanceof SyntaxError;
                  }
                })()""");
    }

    @Test
    public void testParseUnterminatedArray() {
        assertErrorWithJavet("JSON.parse('[1,2,3')");
    }

    @Test
    public void testParseUnterminatedObject() {
        assertErrorWithJavet("JSON.parse('{\"a\":1')");
    }

    @Test
    public void testParseUnterminatedString() {
        assertErrorWithJavet("JSON.parse('\"unterminated')");
    }

    @Test
    public void testParseWithControlCharacterInPropertyName() {
        assertErrorWithJavet("JSON.parse('{\"' + String.fromCharCode(1) + '\":1}')");
    }

    @Test
    public void testParseWithControlCharacterInString() {
        assertErrorWithJavet("JSON.parse('\"' + String.fromCharCode(1) + '\"')");
    }

    @Test
    public void testParseWithReviver() {
        // Test basic reviver functionality
        String json = "{\"a\":1,\"b\":2,\"c\":3}";

        // Create a reviver that doubles all numbers
        JSFunction reviver = new JSNativeFunction("reviver", 2, (context, thisArg, args) -> {
            JSValue value = args[1];
            if (value instanceof JSNumber num) {
                return new JSNumber(num.value() * 2);
            }
            return value;
        });

        JSValue result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString(json), reviver});
        JSObject obj = result.asObject().orElseThrow();

        assertThat(obj.get("a").asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(obj.get("b").asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);
        assertThat(obj.get("c").asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(6.0);
    }

    @Test
    public void testParseWithReviverAndComplexStructure() {
        assertIntegerWithJavet("""
                var json = '{"numbers":[1,2,3],"data":{"x":10,"y":20}}';
                var result = JSON.parse(json, function(key, value) {
                  if (typeof value === 'number') {
                    return value + 1;
                  }
                  return value;
                });
                result.data.x""");
    }

    @Test
    public void testParseWithReviverArrayWithJavet() {
        assertIntegerWithJavet("""
                JSON.parse('[1,2,3,4,5]', function(key, value) {
                  if (typeof value === 'number') {
                    return value * 2;
                  }
                  return value;
                })[2]""");
    }

    @Test
    public void testParseWithReviverArray() {
        // Test reviver with arrays - simplified test
        String json = "[1,2]";

        // Create a reviver that doubles array elements (simpler logic)
        JSFunction reviver = new JSNativeFunction("reviver", 2, (context, thisArg, args) -> {
            JSValue value = args[1];

            // For all numbers, double them
            if (value instanceof JSNumber num) {
                return new JSNumber(num.value() * 2);
            }
            return value;
        });

        JSValue result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString(json), reviver});
        JSArray arr = result.asArray().orElseThrow();

        assertThat(arr.get(0).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
        assertThat(arr.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(4.0);
    }

    @Test
    public void testParseWithReviverFilterWithJavet() {
        assertBooleanWithJavet("""
                var result = JSON.parse('{"a":1,"b":2,"c":3}', function(key, value) {
                  if (key === 'b') {
                    return undefined;
                  }
                  return value;
                });
                result.b === undefined""");
    }

    @Test
    public void testParseWithReviverFilter() {
        // Test reviver that filters out properties
        String json = "{\"name\":\"Alice\",\"age\":30,\"internal\":\"secret\"}";

        // Create a reviver that removes properties starting with "internal"
        JSFunction reviver = new JSNativeFunction("reviver", 2, (context, thisArg, args) -> {
            String key = args[0].asString().map(JSString::value).orElseThrow();
            JSValue value = args[1];

            if (key.equals("internal")) {
                return JSUndefined.INSTANCE;  // Remove this property
            }
            return value;
        });

        JSValue result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString(json), reviver});
        JSObject obj = result.asObject().orElseThrow();

        assertThat(obj.get("name").asString().map(JSString::value).orElseThrow()).isEqualTo("Alice");
        assertThat(obj.get("age").asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(30.0);
        assertThat(obj.get("internal")).isInstanceOf(JSUndefined.class);
    }

    @Test
    public void testParseWithReviverNestedWithJavet() {
        assertIntegerWithJavet("""
                JSON.parse('{"user":{"name":"Bob","age":25}}', function(key, value) {
                  if (key === 'age' && typeof value === 'number') {
                    return value + 10;
                  }
                  return value;
                }).user.age""");
    }

    @Test
    public void testParseWithReviverNested() {
        // Test reviver with nested objects
        String json = "{\"user\":{\"name\":\"Bob\",\"age\":25},\"count\":5}";

        // Create a reviver that converts age to string
        JSFunction reviver = new JSNativeFunction("reviver", 2, (context, thisArg, args) -> {
            String key = args[0].asString().map(JSString::value).orElseThrow();
            JSValue value = args[1];

            if (key.equals("age") && value instanceof JSNumber num) {
                return new JSString((int) num.value() + " years");
            }
            return value;
        });

        JSValue result = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{new JSString(json), reviver});
        JSObject obj = result.asObject().orElseThrow();
        JSObject user = obj.get("user").asObject().orElseThrow();

        assertThat(user.get("age").asString().map(JSString::value).orElseThrow()).isEqualTo("25 years");
    }

    @Test
    public void testParseWithReviverTransform() {
        assertStringWithJavet("""
                JSON.parse('{"date":"2023-01-01"}', function(key, value) {
                  if (key === 'date') {
                    return 'Transformed: ' + value;
                  }
                  return value;
                }).date""");
    }

    @Test
    public void testParseWithReviverWithJavet() {
        assertIntegerWithJavet("""
                JSON.parse('{"a":1,"b":2,"c":3}', function(key, value) {
                  if (typeof value === 'number') {
                    return value * 2;
                  }
                  return value;
                }).b""");
    }

    @Test
    public void testParseWithWhitespace() {
        assertIntegerWithJavet("JSON.parse('  {  \"a\"  :  1  }  ').a");
    }

    @Test
    public void testParseWithWhitespaceFormFeed() {
        assertBooleanWithJavet("""
                (() => {
                  try {
                    JSON.parse('\\f1\\f');
                    return false;
                  } catch (e) {
                    return e instanceof SyntaxError;
                  }
                })()""");
    }

    @Test
    public void testRoundTripWithJavet() {
        assertIntegerWithJavet("""
                var original = {name: 'test', value: 42, items: [1, 2, 3]};
                var json = JSON.stringify(original);
                var parsed = JSON.parse(json);
                parsed.value""");
    }

    @Test
    public void testRoundTrip() {
        // Test round-trip: stringify then parse
        JSObject original = new JSObject();
        original.set("string", new JSString("hello"));
        original.set("number", new JSNumber(42));
        original.set("boolean", JSBoolean.TRUE);
        original.set("null", JSNull.INSTANCE);

        JSArray arr = new JSArray();
        arr.push(new JSString("item1"));
        arr.push(new JSNumber(2));
        original.set("array", arr);

        // Stringify
        JSValue jsonString = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{original});
        // Should be a string containing the JSON
        assertThat(jsonString.asString().isPresent()).isTrue();

        // Parse back
        JSValue parsed = JSONObject.parse(context, JSUndefined.INSTANCE, new JSValue[]{jsonString});
        JSObject parsedObj = parsed.asObject().orElseThrow();
        assertThat(parsedObj.get("string").asString().map(JSString::value).orElseThrow()).isEqualTo("hello");
        assertThat(parsedObj.get("number").asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(42.0);
        assertThat(parsedObj.get("boolean")).isEqualTo(JSBoolean.TRUE);
        assertThat(parsedObj.get("null")).isEqualTo(JSNull.INSTANCE);

        JSArray parsedArr = parsedObj.get("array").asArray().orElseThrow();
        assertThat(parsedArr.getLength()).isEqualTo(2);
        assertThat(parsedArr.get(0).asString().map(JSString::value).orElseThrow()).isEqualTo("item1");
        assertThat(parsedArr.get(1).asNumber().map(JSNumber::value).orElseThrow()).isEqualTo(2.0);
    }

    @Test
    public void testRoundTripWithReplacerReviverForBigInt() {
        assertLongWithJavet("""
                var original = {a: 1n, b: 2n, c: 3};
                var json = JSON.stringify(original, function(key, value) {
                  if (typeof value === "bigint") {
                    return value.toString() + "n";
                  }
                  return value;
                });
                var parsed = JSON.parse(json, function(key, value) {
                  if (typeof value === 'string' && /^\\d+n$/.test(value)) {
                    return BigInt(value.slice(0, -1));
                  }
                  return value;
                });
                parsed.b""");
    }

    @Test
    public void testRoundTripWithReplacerReviverForNumber() {
        assertIntegerWithJavet("""
                var original = {a: 1, b: 2, c: 3};
                var json = JSON.stringify(original, function(key, value) {
                  if (typeof value === 'number') {
                    return value * 2;
                  }
                  return value;
                });
                var parsed = JSON.parse(json, function(key, value) {
                  if (typeof value === 'number') {
                    return value / 2;
                  }
                  return value;
                });
                parsed.b""");
    }

    @Test
    public void testRoundTripWithSpace() {
        assertIntegerWithJavet("""
                var original = {a: 1, b: 2};
                var json = JSON.stringify(original, null, 2);
                var parsed = JSON.parse(json);
                parsed.b""");
    }

    @Test
    public void testStringifyArrayMixed() {
        assertStringWithJavet("JSON.stringify([1, 'two', true, null])");
    }

    @Test
    public void testStringifyArrayNested() {
        assertStringWithJavet("JSON.stringify([[1, 2], [3, 4]])");
    }

    @Test
    public void testStringifyArraySimple() {
        assertStringWithJavet("JSON.stringify([1, 2, 3])");
    }

    @Test
    public void testStringifyArrayWithMixedTypes() {
        assertStringWithJavet("JSON.stringify([1, 'two', true, null, {a: 5}, [6, 7]])");
    }

    @Test
    public void testStringifyArrayWithNumberSpace() {
        assertStringWithJavet("JSON.stringify([1, 2, 3], null, 4)");
    }

    @Test
    public void testStringifyArrayWithUndefined() {
        assertStringWithJavet("JSON.stringify([1, undefined, 3])");
    }

    @Test
    public void testStringifyBoolean() {
        assertStringWithJavet("JSON.stringify(true)");
    }

    @Test
    public void testStringifyCircularReference() {
        assertErrorWithJavet("""
                var obj = {a: 1};
                obj.self = obj;
                JSON.stringify(obj)""");
    }

    @Test
    public void testStringifyCircularReferenceInArray() {
        assertErrorWithJavet("""
                var arr = [1, 2];
                arr.push(arr);
                JSON.stringify(arr)""");
    }

    @Test
    public void testStringifyCircularReferenceNested() {
        assertErrorWithJavet("""
                var a = {name: 'a'};
                var b = {name: 'b', ref: a};
                a.ref = b;
                JSON.stringify(a)""");
    }

    @Test
    public void testStringifyComplexNested() {
        String code = """
                JSON.stringify({
                  users: [
                    {name: 'Alice', age: 30, tags: ['dev', 'admin']},
                    {name: 'Bob', age: 25, tags: ['user']}
                  ],
                  metadata: {version: '1.0', count: 2}
                })""";
        assertStringWithJavet(code);
    }

    @Test
    public void testStringifyDescriptorAndToStringTag() {
        assertStringWithJavet("""
                var parseDesc = Object.getOwnPropertyDescriptor(JSON, 'parse');
                var stringifyDesc = Object.getOwnPropertyDescriptor(JSON, 'stringify');
                JSON.stringify([
                  [typeof parseDesc.value, parseDesc.writable, parseDesc.enumerable, parseDesc.configurable, parseDesc.value.length],
                  [typeof stringifyDesc.value, stringifyDesc.writable, stringifyDesc.enumerable, stringifyDesc.configurable, stringifyDesc.value.length],
                  JSON[Symbol.toStringTag],
                  Object.keys(JSON).length
                ])""");
    }

    @Test
    public void testStringifyEmptyArray() {
        assertStringWithJavet("JSON.stringify([])");
    }

    @Test
    public void testStringifyEmptyObject() {
        assertStringWithJavet("JSON.stringify({})");
    }

    @Test
    public void testStringifyEnumerableOwnStringKeysOnly() {
        assertStringWithJavet("""
                var o = {a: 1};
                o[1] = 3;
                Object.defineProperty(o, 'b', {value: 2, enumerable: false});
                JSON.stringify(o)""");
    }

    @Test
    public void testStringifyEscapeNewline() {
        assertStringWithJavet("JSON.stringify('hello\\nworld')");
    }

    @Test
    public void testStringifyEscapeQuote() {
        assertStringWithJavet("JSON.stringify('quote\\\"test')");
    }

    @Test
    public void testStringifyEscapeTab() {
        assertStringWithJavet("JSON.stringify('tab\\there')");
    }

    @Test
    public void testStringifyFloatingPoint() {
        assertStringWithJavet("JSON.stringify(0.1 + 0.2)");
    }

    @Test
    public void testStringifyForIndent() {
        JSObject obj = new JSObject();
        obj.set("name", new JSString("test"));
        obj.set("value", new JSNumber(123));

        // Normal case: stringify with space parameter (number)
        JSValue result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{obj, JSUndefined.INSTANCE, new JSNumber(2)});
        String jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr.contains("\n  ")).isTrue(); // Should have indentation

        // Normal case: stringify with space parameter (string)
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{obj, JSUndefined.INSTANCE, new JSString("  ")});
        jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr.contains("\n  ")).isTrue(); // Should have indentation

        // Normal case: stringify with large space (should be limited)
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{obj, JSUndefined.INSTANCE, new JSNumber(20)});
        jsonStr = result.asString().map(JSString::value).orElseThrow();
        // Should not have more than 10 spaces of indentation
        assertThat(jsonStr).isEqualTo("{\n" +
                "          \"name\": \"test\",\n" +
                "          \"value\": 123\n" +
                "}");

        // Test indentation with different number values
        // Indent 0 (no indentation)
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{obj, JSUndefined.INSTANCE, new JSNumber(0)});
        jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr.contains("\n")).isFalse(); // Should be compact

        // Indent 1
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{obj, JSUndefined.INSTANCE, new JSNumber(1)});
        jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr.contains("\n ")).isTrue(); // Should have 1 space indent

        // Indent 4
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{obj, JSUndefined.INSTANCE, new JSNumber(4)});
        jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr.contains("\n    ")).isTrue(); // Should have 4 spaces indent

        // Indent 10 (maximum)
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{obj, JSUndefined.INSTANCE, new JSNumber(10)});
        jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr.contains("\n          ")).isTrue(); // Should have 10 spaces indent

        // Test indentation with different string values
        // Empty string indent
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{obj, JSUndefined.INSTANCE, new JSString("")});
        jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr.contains("\n")).isFalse(); // Should be compact

        // Single space string
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{obj, JSUndefined.INSTANCE, new JSString(" ")});
        jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr.contains("\n ")).isTrue(); // Should have space indent

        // Tab character
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{obj, JSUndefined.INSTANCE, new JSString("\t")});
        jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr.contains("\n\t")).isTrue(); // Should have tab indent

        // Multiple character string
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{obj, JSUndefined.INSTANCE, new JSString("  ")});
        jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr.contains("\n  ")).isTrue(); // Should have two spaces

        // String longer than 10 characters (should be truncated)
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{obj, JSUndefined.INSTANCE, new JSString("abcdefghijk")});
        jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr.contains("\nabcdefghij")).isTrue(); // Should have first 10 chars

        // Test indentation with arrays
        JSArray testArr = new JSArray();
        testArr.push(new JSNumber(1));
        testArr.push(new JSString("test"));
        testArr.push(JSBoolean.TRUE);

        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{testArr, JSUndefined.INSTANCE, new JSNumber(2)});
        jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr.contains("[\n  1,\n  \"test\",\n  true\n]")).isTrue(); // Should have proper array indentation

        // Test indentation with nested structures
        JSObject nested = new JSObject();
        nested.set("inner", new JSString("value"));
        JSObject outer = new JSObject();
        outer.set("nested", nested);
        outer.set("array", testArr);

        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{outer, JSUndefined.INSTANCE, new JSNumber(4)});
        jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr.contains("{\n    \"nested\": {\n        \"inner\": \"value\"\n    }")).isTrue(); // Should have nested indentation
    }

    @Test
    public void testStringifyForObjects() {
        // Normal case: stringify empty array
        JSArray emptyArr = new JSArray();
        JSValue result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{emptyArr});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("[]");

        // Normal case: stringify array with values
        JSArray arr = new JSArray();
        arr.push(new JSNumber(1));
        arr.push(new JSString("two"));
        arr.push(JSBoolean.TRUE);
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{arr});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("[1,\"two\",true]");

        // Normal case: stringify empty object
        JSObject emptyObj = new JSObject();
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{emptyObj});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("{}");

        // Normal case: stringify object with properties
        JSObject obj = new JSObject();
        obj.set("name", new JSString("test"));
        obj.set("value", new JSNumber(123));
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        // Note: property order may vary
        String jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr.contains("\"name\":\"test\"")).isTrue();
        assertThat(jsonStr.contains("\"value\":123")).isTrue();

        // Normal case: stringify nested object
        JSObject nestedObj = new JSObject();
        nestedObj.set("nested", JSBoolean.TRUE);
        JSObject parentObj = new JSObject();
        parentObj.set("data", nestedObj);
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{parentObj});
        jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr.contains("\"data\":{\"nested\":true}")).isTrue();

        // Edge case: stringify undefined (should return undefined)
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{JSUndefined.INSTANCE});
        assertThat(result.isUndefined()).isTrue();

        // Edge case: stringify function (should be undefined in simplified implementation)
        JSFunction func = new JSNativeFunction("test", 0, (context, thisArg, args) -> JSUndefined.INSTANCE);
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{func});
        assertThat(result.isUndefined()).isTrue();

        // Edge case: no arguments
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(result.isUndefined()).isTrue();

        // Edge case: stringify object with undefined values (should be omitted in simplified implementation)
        JSObject objWithUndefined = new JSObject();
        objWithUndefined.set("defined", new JSString("value"));
        objWithUndefined.set("undefined", JSUndefined.INSTANCE);
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{objWithUndefined});
        jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr.contains("\"defined\":\"value\"")).isTrue();
        // Note: simplified implementation may or may not include undefined values
    }

    @Test
    public void testStringifyForPrimitives() {
        // Normal case: stringify null
        JSValue result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("null");

        // Normal case: stringify boolean true
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{JSBoolean.TRUE});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("true");

        // Normal case: stringify boolean false
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{JSBoolean.FALSE});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("false");

        // Normal case: stringify number
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("42");

        // Normal case: stringify negative number
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(-123.45)});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("-123.45");

        // Normal case: stringify string
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("hello world")});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("\"hello world\"");

        // Normal case: stringify string with special characters
        result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("hello\nworld")});
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("\"hello\\nworld\"");
    }

    @Test
    public void testStringifyForReplacer() {
        // Test replacer function that converts BigInts to strings
        JSObject objWithBigInt = new JSObject();
        objWithBigInt.set("num", new JSNumber(123));  // Using number instead of BigInt for testing
        objWithBigInt.set("str", new JSString("test"));

        // Create a replacer function that doubles numbers
        JSFunction replacer = new JSNativeFunction("replacer", 2, (context, thisArg, args) -> {
            JSValue value = args[1];
            if (value instanceof JSNumber num) {
                return new JSNumber(num.value() * 2);
            }
            return value;
        });

        JSValue result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{objWithBigInt, replacer});
        String jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr).contains("\"num\":246");
        assertThat(jsonStr).contains("\"str\":\"test\"");
    }

    @Test
    public void testStringifyFunction() {
        assertStringWithJavet("String(JSON.stringify(function() {}))");
    }

    @Test
    public void testStringifyGenericObjectSpaceIgnored() {
        assertStringWithJavet("JSON.stringify({a: 1}, null, {toString: function() { return '  '; }})");
    }

    @Test
    public void testStringifyInfinity() {
        assertStringWithJavet("JSON.stringify(Infinity)");
    }

    @Test
    public void testStringifyNaN() {
        assertStringWithJavet("JSON.stringify(NaN)");
    }

    @Test
    public void testStringifyNegativeInfinity() {
        assertStringWithJavet("JSON.stringify(-Infinity)");
    }

    @Test
    public void testStringifyNestedStructure() {
        assertStringWithJavet("""
                JSON.stringify({
                  user: {
                    name: 'Alice',
                    age: 30
                  },
                  items: [1, 2, 3]
                })""");
    }

    @Test
    public void testStringifyNull() {
        assertStringWithJavet("JSON.stringify(null)");
    }

    @Test
    public void testStringifyNumber() {
        assertStringWithJavet("JSON.stringify(42)");
    }

    @Test
    public void testStringifyObjectComplex() {
        assertStringWithJavet("JSON.stringify({name: 'test', value: 42, active: true})");
    }

    @Test
    public void testStringifyObjectSimple() {
        assertStringWithJavet("JSON.stringify({a: 1, b: 2})");
    }

    @Test
    public void testStringifyObjectWithFunction() {
        assertStringWithJavet("JSON.stringify({a: 1, b: function() {}, c: 2})");
    }

    @Test
    public void testStringifyObjectWithUndefined() {
        assertStringWithJavet("JSON.stringify({a: 1, b: undefined, c: 3})");
    }

    @Test
    public void testStringifyScientificNotationLarge() {
        assertStringWithJavet("JSON.stringify(1e10)");
    }

    @Test
    public void testStringifyScientificNotationSmall() {
        assertStringWithJavet("JSON.stringify(1e-10)");
    }

    @Test
    public void testStringifyString() {
        assertStringWithJavet("JSON.stringify('hello')");
    }

    @Test
    public void testStringifyUndefined() {
        assertBooleanWithJavet("JSON.stringify(undefined) === undefined");
    }

    @Test
    public void testStringifyWithBigInt() {
        assertErrorWithJavet("JSON.stringify(1n)");
        assertErrorWithJavet("JSON.stringify({a: 1n})");
        assertErrorWithJavet("JSON.stringify([1n])");
    }

    @Test
    public void testStringifyWithNumberSpace() {
        assertStringWithJavet("JSON.stringify({a: 1, b: 2}, null, 2)");
    }

    @Test
    public void testStringifyWithReplacerAndSpace() {
        assertStringWithJavet("""
                JSON.stringify(
                  {a: 1, b: 2, c: 3, d: 4},
                  ['a', 'c'],
                  2
                )""");
    }

    @Test
    public void testStringifyWithReplacerAndToJSON() {
        // Test that toJSON is called before replacer
        JSObject obj = new JSObject();
        obj.set("value", new JSNumber(10));

        // Add a toJSON method that returns a different value
        JSFunction toJSON = new JSNativeFunction("toJSON", 0, (context, thisArg, args) -> {
            return new JSNumber(20);
        });
        obj.set("toJSON", toJSON);

        // Create a replacer that doubles numbers
        JSFunction replacer = new JSNativeFunction("replacer", 2, (context, thisArg, args) -> {
            JSValue value = args[1];
            if (value instanceof JSNumber num) {
                return new JSNumber(num.value() * 2);
            }
            return value;
        });

        JSValue result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{obj, replacer});
        String jsonStr = result.asString().map(JSString::value).orElseThrow();

        // toJSON returns 20, then replacer doubles it to 40
        assertThat(jsonStr).isEqualTo("40");
    }

    @Test
    public void testStringifyWithReplacerArrayWithJavet() {
        assertStringWithJavet("JSON.stringify({a: 1, b: 2, c: 3, d: 4}, ['a', 'c'])");
    }

    @Test
    public void testStringifyWithReplacerArray() {
        // Test replacer array (property whitelist)
        JSObject obj = new JSObject();
        obj.set("name", new JSString("Alice"));
        obj.set("age", new JSNumber(30));
        obj.set("internal", new JSString("secret"));
        obj.set("password", new JSString("12345"));

        // Create a replacer array with only allowed properties
        JSArray replacer = new JSArray();
        replacer.push(new JSString("name"));
        replacer.push(new JSString("age"));

        JSValue result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{obj, replacer});
        String jsonStr = result.asString().map(JSString::value).orElseThrow();

        assertThat(jsonStr).contains("\"name\":\"Alice\"");
        assertThat(jsonStr).contains("\"age\":30");
        assertThat(jsonStr).doesNotContain("internal");
        assertThat(jsonStr).doesNotContain("password");
    }

    @Test
    public void testStringifyWithReplacerArrayNestedWithJavet() {
        assertStringWithJavet("""
                JSON.stringify({
                  name: 'test',
                  data: {x: 1, y: 2, z: 3},
                  extra: 'value'
                }, ['name', 'data', 'x', 'y'])""");
    }

    @Test
    public void testStringifyWithReplacerArrayNested() {
        // Test replacer array with nested objects
        JSObject inner = new JSObject();
        inner.set("x", new JSNumber(1));
        inner.set("y", new JSNumber(2));
        inner.set("z", new JSNumber(3));

        JSObject outer = new JSObject();
        outer.set("data", inner);
        outer.set("extra", new JSString("value"));

        // Only allow "data" and "x", "y" properties
        JSArray replacer = new JSArray();
        replacer.push(new JSString("data"));
        replacer.push(new JSString("x"));
        replacer.push(new JSString("y"));

        JSValue result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{outer, replacer});
        String jsonStr = result.asString().map(JSString::value).orElseThrow();

        assertThat(jsonStr).contains("\"data\":");
        assertThat(jsonStr).contains("\"x\":1");
        assertThat(jsonStr).contains("\"y\":2");
        assertThat(jsonStr).doesNotContain("\"z\"");
        assertThat(jsonStr).doesNotContain("extra");
    }

    @Test
    public void testStringifyWithReplacerArrayNonIntegerNumber() {
        assertStringWithJavet("JSON.stringify({'1.5': 'x', '1': 'y'}, [1.5])");
    }

    @Test
    public void testStringifyWithReplacerArrayNumbersWithJavet() {
        assertStringWithJavet("JSON.stringify({0: 'a', 1: 'b', 2: 'c'}, [0, 2])");
    }

    @Test
    public void testStringifyWithReplacerArrayNumbers() {
        // Test that replacer array can contain numbers (for array indices)
        JSArray arr = new JSArray();
        arr.push(new JSString("a"));
        arr.push(new JSString("b"));
        arr.push(new JSString("c"));

        JSObject obj = new JSObject();
        obj.set("0", new JSString("zero"));
        obj.set("1", new JSString("one"));
        obj.set("2", new JSString("two"));

        // Replacer array with numbers
        JSArray replacer = new JSArray();
        replacer.push(new JSNumber(0));
        replacer.push(new JSNumber(2));

        JSValue result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{obj, replacer});
        String jsonStr = result.asString().map(JSString::value).orElseThrow();

        assertThat(jsonStr).contains("\"0\":\"zero\"");
        assertThat(jsonStr).contains("\"2\":\"two\"");
        assertThat(jsonStr).doesNotContain("\"1\"");
    }

    @Test
    public void testStringifyWithReplacerArraySkipsGenericObjects() {
        assertStringWithJavet("JSON.stringify({a: 1, b: 2}, [{toString: function() { return 'a'; }}, 'b'])");
    }

    @Test
    public void testStringifyWithReplacerFilteringOut() {
        assertStringWithJavet("""
                JSON.stringify({a: 1, b: 2, c: 3}, function(key, value) {
                  if (key === 'b') {
                    return undefined;
                  }
                  return value;
                })""");
    }

    @Test
    public void testStringifyWithReplacerFunctionWithJavet() {
        assertStringWithJavet("""
                JSON.stringify({a: 1, b: 2, c: 3}, function(key, value) {
                  if (typeof value === 'number') {
                    return value * 2;
                  }
                  return value;
                })""");
    }

    @Test
    public void testStringifyWithReplacerFunction() {
        // Test replacer function
        JSObject obj = new JSObject();
        obj.set("name", new JSString("test"));
        obj.set("value", new JSNumber(100));
        obj.set("active", JSBoolean.TRUE);

        // Create a replacer that filters out boolean values
        JSFunction replacer = new JSNativeFunction("replacer", 2, (context, thisArg, args) -> {
            String key = args[0].asString().map(JSString::value).orElseThrow();
            JSValue value = args[1];

            if (value instanceof JSBoolean) {
                return JSUndefined.INSTANCE;  // Filter out booleans
            }
            return value;
        });

        JSValue result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{obj, replacer});
        String jsonStr = result.asString().map(JSString::value).orElseThrow();

        assertThat(jsonStr).contains("\"name\":\"test\"");
        assertThat(jsonStr).contains("\"value\":100");
        assertThat(jsonStr).doesNotContain("active");
    }

    @Test
    public void testStringifyWithSpaceLimitNumber() {
        // Space is limited to 10 characters
        assertStringWithJavet("JSON.stringify({a: 1}, null, 20)");
    }

    @Test
    public void testStringifyWithSpaceLimitString() {
        assertStringWithJavet("JSON.stringify({a: 1}, null, 'abcdefghijklmnop')");
    }

    @Test
    public void testStringifyWithSpaceNumberObject() {
        assertStringWithJavet("JSON.stringify({a: 1}, null, new Number(2))");
    }

    @Test
    public void testStringifyWithStringSpace() {
        assertStringWithJavet("JSON.stringify({a: 1, b: 2}, null, '  ')");
    }

    @Test
    public void testStringifyWithTabSpace() {
        assertStringWithJavet("JSON.stringify({a: 1}, null, '\\t')");
    }

    @Test
    public void testStringifyWithToJSONWithJavet() {
        assertStringWithJavet("""
                var obj = {
                  value: 42,
                  toJSON: function() {
                    return this.value * 2;
                  }
                };
                JSON.stringify(obj)""");
    }

    @Test
    public void testStringifyWithToJSON() {
        // Test toJSON method support
        JSObject obj = new JSObject();
        obj.set("value", new JSNumber(42));

        // Add a toJSON method
        JSFunction toJSON = new JSNativeFunction("toJSON", 0, (context, thisArg, args) -> {
            return new JSString("custom");
        });
        obj.set("toJSON", toJSON);

        JSValue result = JSONObject.stringify(context, JSUndefined.INSTANCE, new JSValue[]{obj});
        String jsonStr = result.asString().map(JSString::value).orElseThrow();

        assertThat(jsonStr).isEqualTo("\"custom\"");
    }

    @Test
    public void testStringifyWithToJSONAndReplacer() {
        assertStringWithJavet("""
                var obj = {
                  value: 10,
                  toJSON: function() {
                    return this.value * 2;
                  }
                };
                JSON.stringify(obj, function(key, value) {
                  if (typeof value === 'number') {
                    return value + 10;
                  }
                  return value;
                })""");
    }

    @Test
    public void testStringifyWithWrapperObjects() {
        assertStringWithJavet("JSON.stringify([new Number(1.5), new String('x'), new Boolean(false)])");
    }
}
