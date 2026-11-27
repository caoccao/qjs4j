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

package com.caoccao.qjs4j.core.json;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JSONObject methods.
 */
public class JSONObjectTest extends BaseJavetTest {

    private void assertIndentation(JSObject obj, JSValue space, String indent) {
        String expected = indent.isEmpty()
                ? "{\"name\":\"test\",\"value\":123}"
                : "{\n" + indent + "\"name\": \"test\",\n" + indent + "\"value\": 123\n}";
        assertThat(stringifyIndented(obj, space)).as("indent %s", space).isEqualTo(expected);
    }

    private JSFunction doubleNumbers(String name) {
        return new JSNativeFunction(context, name, 2, (ctx, receiver,
                args) -> args[1] instanceof JSNumber number ? new JSNumber(number.value() * 2) : args[1]);
    }

    @Test
    void jsonSourceMetadataIsAbsentWithoutReviver() throws Exception {
        var sources = JSONParser.class.getDeclaredField("elementSources");
        sources.setAccessible(true);
        String text = "{\"a\":[1,2,3],\"b\":42}";
        JSONParser plain = new JSONParser(context, text, null);
        plain.parse();
        assertThat(sources.get(plain)).isNull();

        JSFunction identity = new JSNativeFunction(context, "reviver", 2, (ctx, receiver, args) -> args[1]);
        JSONParser tracked = new JSONParser(context, text, identity);
        tracked.parse();
        assertThat((java.util.Map<?, ?>) sources.get(tracked)).isNotEmpty();
    }

    private JSValue parse(JSValue... args) {
        return context.getJSGlobalObject().getJSONObject().parse(JSUndefined.INSTANCE, args);
    }

    private JSValue parse(String source) {
        return parse(new JSString(source));
    }

    private JSValue stringify(JSValue... args) {
        return context.getJSGlobalObject().getJSONObject().stringify(JSUndefined.INSTANCE, args);
    }

    private String stringifyIndented(JSValue value, JSValue space) {
        return stringify(value, JSUndefined.INSTANCE, space).asString().orElseThrow().value();
    }

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

        JSValue result = parse(new JSString(complexJson));
        JSObject root = result.asObject().orElseThrow();

        // Check users array
        JSArray users = root.get("users").asArray().orElseThrow();
        assertThat(users.getLength()).isEqualTo(2);

        // Check first user
        JSObject user1 = users.get(0).asObject().orElseThrow();
        assertStringValue(user1.get("name"), "Alice");
        assertNumberValue(user1.get("age"), 30.0);
        assertThat(user1.get("active")).isEqualTo(JSBoolean.TRUE);

        JSArray tags1 = user1.get("tags").asArray().orElseThrow();
        assertThat(tags1.getLength()).isEqualTo(2);
        assertStringValue(tags1.get(0), "developer");
        assertStringValue(tags1.get(1), "admin");

        // Check metadata
        JSObject metadata = root.get("metadata").asObject().orElseThrow();
        assertStringValue(metadata.get("version"), "1.0");
        assertNumberValue(metadata.get("count"), 2.0);

        // Test stringify back
        JSValue stringified = stringify(result);
        assertThat(stringified.isString()).isTrue();

        // Parse again to ensure round-trip works
        JSValue reparsed = parse(stringified);
        JSObject reparsedObj = reparsed.asObject().orElseThrow();
    }

    @Test
    public void testParse() {
        assertThat(parse("null")).isEqualTo(JSNull.INSTANCE);
        assertThat(parse("true").isBooleanTrue()).isTrue();
        assertThat(parse("false").isBooleanFalse()).isTrue();
        assertNumberValue(parse("42"), 42.0);
        assertNumberValue(parse("-123.45"), -123.45);
        assertStringValue(parse("\"hello world\""), "hello world");
        assertStringValue(parse("\"hello\\nworld\""), "hello\nworld");

        assertThat(parse("[]").asArray().orElseThrow().getLength()).isEqualTo(0);

        JSArray arr = parse("[1, \"two\", true]").asArray().orElseThrow();
        assertThat(arr.getLength()).isEqualTo(3);
        assertNumberValue(arr.get(0), 1.0);
        assertStringValue(arr.get(1), "two");
        assertThat(arr.get(2)).isEqualTo(JSBoolean.TRUE);

        parse("{}").asObject().orElseThrow();

        JSObject obj = parse("{\"name\": \"test\", \"value\": 123}").asObject().orElseThrow();
        assertStringValue(obj.get("name"), "test");
        assertNumberValue(obj.get("value"), 123.0);

        obj = parse("{\"data\": {\"nested\": true}}").asObject().orElseThrow();
        assertThat(obj.get("data").asObject().orElseThrow().get("nested")).isEqualTo(JSBoolean.TRUE);

        obj = parse("  {  \"key\"  :  \"value\"  }  ").asObject().orElseThrow();
        assertStringValue(obj.get("key"), "value");
        assertPendingSyntaxError(context, parse(""));
        assertPendingSyntaxError(context, parse("{invalid}"));
        assertPendingSyntaxError(context, parse("\"unterminated"));
        assertPendingSyntaxError(context, parse());
        assertNumberValue(parse(numberArgs(123)), 123.0);
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
    void testParseInvalidInputs() {
        assertErrorWithJavet("JSON.parse('')", "JSON.parse('{invalid}')", "JSON.parse('01')", "JSON.parse()",
                "JSON.parse('{\"a\":1,}')", "JSON.parse('[1,2,3')", "JSON.parse('{\"a\":1')",
                "JSON.parse('\"unterminated')", "JSON.parse('{\"' + String.fromCharCode(1) + '\":1}')",
                "JSON.parse('\"' + String.fromCharCode(1) + '\"')");
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
    void testParseStrings() {
        assertStringWithJavet("JSON.parse('\"hello\\\\nworld\"')", "JSON.parse('\"tab\\\\there\"')",
                "JSON.parse('\"hello\"')");
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
    public void testParseWithProtoPropertyUsesDataProperty() {
        assertBooleanWithJavet("""
                var o = JSON.parse('{"__proto__":{"x":1}}');
                Object.getPrototypeOf(o) === Object.prototype
                    && Object.getOwnPropertyDescriptor(o, '__proto__').value.x === 1""");
    }

    @Test
    public void testParseWithReviver() {
        // Test basic reviver functionality
        String json = "{\"a\":1,\"b\":2,\"c\":3}";

        JSFunction reviver = doubleNumbers("reviver");

        JSValue result = parse(new JSString(json), reviver);
        JSObject obj = result.asObject().orElseThrow();

        assertNumberValue(obj.get("a"), 2.0);
        assertNumberValue(obj.get("b"), 4.0);
        assertNumberValue(obj.get("c"), 6.0);
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
    public void testParseWithReviverArray() {
        // Test reviver with arrays - simplified test
        String json = "[1,2]";

        JSFunction reviver = doubleNumbers("reviver");

        JSValue result = parse(new JSString(json), reviver);
        JSArray arr = result.asArray().orElseThrow();

        assertNumberValue(arr.get(0), 2.0);
        assertNumberValue(arr.get(1), 4.0);
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
    public void testParseWithReviverFilter() {
        // Test reviver that filters out properties
        String json = "{\"name\":\"Alice\",\"age\":30,\"internal\":\"secret\"}";

        // Create a reviver that removes properties starting with "internal"
        JSFunction reviver = new JSNativeFunction(context, "reviver", 2, (context, thisArg, args) -> {
            String key = args[0].asString().map(JSString::value).orElseThrow();
            JSValue value = args[1];

            if (key.equals("internal")) {
                return JSUndefined.INSTANCE; // Remove this property
            }
            return value;
        });

        JSValue result = parse(new JSString(json), reviver);
        JSObject obj = result.asObject().orElseThrow();

        assertStringValue(obj.get("name"), "Alice");
        assertNumberValue(obj.get("age"), 30.0);
        assertThat(obj.get("internal")).isInstanceOf(JSUndefined.class);
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
    public void testParseWithReviverNested() {
        // Test reviver with nested objects
        String json = "{\"user\":{\"name\":\"Bob\",\"age\":25},\"count\":5}";

        // Create a reviver that converts age to string
        JSFunction reviver = new JSNativeFunction(context, "reviver", 2, (context, thisArg, args) -> {
            String key = args[0].asString().map(JSString::value).orElseThrow();
            JSValue value = args[1];

            if (key.equals("age") && value instanceof JSNumber num) {
                return new JSString((int) num.value() + " years");
            }
            return value;
        });

        JSValue result = parse(new JSString(json), reviver);
        JSObject obj = result.asObject().orElseThrow();
        assertStringValue(obj.get("user").asObject().orElseThrow().get("age"), "25 years");
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
    public void testParseWithReviverSourceForPrimitiveProperty() {
        assertStringWithJavet("""
                var source;
                JSON.parse('{"a":123}', function (key, value, context) {
                    if (key === 'a') {
                        source = context.source;
                    }
                    return value;
                });
                source""");
    }

    @Test
    public void testParseWithReviverSourceForRootPrimitive() {
        assertStringWithJavet("""
                var source;
                JSON.parse('123', function (key, value, context) {
                    if (key === '') {
                        source = context.source;
                    }
                    return value;
                });
                source""");
    }

    @Test
    public void testParseWithReviverSourceUndefinedForStructuredValue() {
        assertBooleanWithJavet("""
                var source;
                JSON.parse('{"o":{}}', function (key, value, context) {
                    if (key === 'o') {
                        source = context.source;
                    }
                    return value;
                });
                source === undefined""");
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
    public void testRawJSONAndIsRawJSON() {
        assertBooleanWithJavet("""
                var raw = JSON.rawJSON('1');
                JSON.isRawJSON(raw) && !JSON.isRawJSON(1) && !JSON.isRawJSON({rawJSON: '1'})""");
    }

    @Test
    void testRawJSONInvalidInputs() {
        assertErrorWithJavet("JSON.rawJSON('')", "JSON.rawJSON('1 2')", "JSON.rawJSON()", "JSON.rawJSON(' 1')");
    }

    @Test
    public void testRawJSONObjectAndArrayErrorMessage() {
        assertErrorWithJavet("JSON.rawJSON('{}')", "JSON.rawJSON('[]')");
    }

    @Test
    public void testReviverSourceForSubstitutedObjectWithEmptyKey() {
        assertBooleanWithJavet("""
                let observed;
                JSON.parse('{"first":1,"second":2}', function (key, value, context) {
                    if (key === 'first') this.second = { '': 42 };
                    if (key === '' && value === 42) observed = !('source' in context);
                    return value;
                });
                observed""");
    }

    @Test
    public void testRoundTrip() {
        // Test round-trip: stringify then parse
        JSObject original = new JSObject(context);
        original.set("string", new JSString("hello"));
        original.set("number", new JSNumber(42));
        original.set("boolean", JSBoolean.TRUE);
        original.set("null", JSNull.INSTANCE);

        JSArray arr = new JSArray(context, new JSString("item1"), new JSNumber(2));
        original.set("array", arr);

        // Stringify
        JSValue jsonString = stringify(original);
        // Should be a string containing the JSON
        assertThat(jsonString.asString().isPresent()).isTrue();

        // Parse back
        JSValue parsed = parse(jsonString);
        JSObject parsedObj = parsed.asObject().orElseThrow();
        assertStringValue(parsedObj.get("string"), "hello");
        assertNumberValue(parsedObj.get("number"), 42.0);
        assertThat(parsedObj.get("boolean")).isEqualTo(JSBoolean.TRUE);
        assertThat(parsedObj.get("null")).isEqualTo(JSNull.INSTANCE);

        JSArray parsedArr = parsedObj.get("array").asArray().orElseThrow();
        assertThat(parsedArr.getLength()).isEqualTo(2);
        assertStringValue(parsedArr.get(0), "item1");
        assertNumberValue(parsedArr.get(1), 2.0);
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
    void testStringifyArrays() {
        assertStringWithJavet("JSON.stringify([1, 'two', true, null])", "JSON.stringify([[1, 2], [3, 4]])",
                "JSON.stringify([1, 2, 3])", "JSON.stringify([1, 'two', true, null, {a: 5}, [6, 7]])",
                "JSON.stringify([1, undefined, 3])", "JSON.stringify([])",
                "JSON.stringify([JSON.rawJSON('1'), JSON.rawJSON('2'), 3])");
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
        assertStringWithJavet(
                """
                        var parseDesc = Object.getOwnPropertyDescriptor(JSON, 'parse');
                        var stringifyDesc = Object.getOwnPropertyDescriptor(JSON, 'stringify');
                        var rawJSONDesc = Object.getOwnPropertyDescriptor(JSON, 'rawJSON');
                        var isRawJSONDesc = Object.getOwnPropertyDescriptor(JSON, 'isRawJSON');
                        JSON.stringify([
                          [typeof parseDesc.value, parseDesc.writable, parseDesc.enumerable, parseDesc.configurable, parseDesc.value.length],
                          [typeof stringifyDesc.value, stringifyDesc.writable, stringifyDesc.enumerable, stringifyDesc.configurable, stringifyDesc.value.length],
                          [typeof rawJSONDesc.value, rawJSONDesc.writable, rawJSONDesc.enumerable, rawJSONDesc.configurable, rawJSONDesc.value.length],
                          [typeof isRawJSONDesc.value, isRawJSONDesc.writable, isRawJSONDesc.enumerable, isRawJSONDesc.configurable, isRawJSONDesc.value.length],
                          JSON[Symbol.toStringTag],
                          Object.keys(JSON).length
                        ])""");
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
    public void testStringifyForIndent() {
        JSObject obj = new JSObject(context);
        obj.set("name", new JSString("test"));
        obj.set("value", new JSNumber(123));

        for (int width : new int[]{0, 1, 2, 4, 10, 20}) {
            assertIndentation(obj, JSNumber.of(width), " ".repeat(Math.min(width, 10)));
        }
        for (String space : new String[]{"", " ", "  ", "\t", "abcdefghijk"}) {
            assertIndentation(obj, new JSString(space), space.substring(0, Math.min(space.length(), 10)));
        }

        JSArray testArr = new JSArray(context, new JSNumber(1), new JSString("test"), JSBoolean.TRUE);

        assertThat(stringifyIndented(testArr, JSNumber.of(2))).isEqualTo("[\n  1,\n  \"test\",\n  true\n]");

        JSObject nested = new JSObject(context);
        nested.set("inner", new JSString("value"));
        JSObject outer = new JSObject(context);
        outer.set("nested", nested);
        outer.set("array", testArr);

        assertThat(stringifyIndented(outer, JSNumber.of(4)))
                .contains("{\n    \"nested\": {\n        \"inner\": \"value\"\n    }");
    }

    @Test
    public void testStringifyForObjects() {
        JSArray emptyArr = new JSArray(context);
        assertStringValue(stringify(emptyArr), "[]");

        JSArray arr = new JSArray(context, new JSNumber(1), new JSString("two"), JSBoolean.TRUE);
        assertStringValue(stringify(arr), "[1,\"two\",true]");

        JSObject emptyObj = new JSObject(context);
        assertStringValue(stringify(emptyObj), "{}");

        JSObject obj = new JSObject(context);
        obj.set("name", new JSString("test"));
        obj.set("value", new JSNumber(123));
        JSValue result = stringify(obj);
        // Note: property order may vary
        String jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr.contains("\"name\":\"test\"")).isTrue();
        assertThat(jsonStr.contains("\"value\":123")).isTrue();

        JSObject nestedObj = new JSObject(context);
        nestedObj.set("nested", JSBoolean.TRUE);
        JSObject parentObj = new JSObject(context);
        parentObj.set("data", nestedObj);
        result = stringify(parentObj);
        jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr.contains("\"data\":{\"nested\":true}")).isTrue();

        assertThat(stringify(JSUndefined.INSTANCE).isUndefined()).isTrue();

        JSFunction func = new JSNativeFunction(context, "test", 0, (context, thisArg, args) -> JSUndefined.INSTANCE);
        assertThat(stringify(func).isUndefined()).isTrue();

        assertThat(stringify().isUndefined()).isTrue();

        JSObject objWithUndefined = new JSObject(context);
        objWithUndefined.set("defined", new JSString("value"));
        objWithUndefined.set("undefined", JSUndefined.INSTANCE);
        result = stringify(objWithUndefined);
        jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr.contains("\"defined\":\"value\"")).isTrue();
        // Note: simplified implementation may or may not include undefined values
    }

    @Test
    public void testStringifyForPrimitives() {
        assertStringValue(stringify(JSNull.INSTANCE), "null");
        assertStringValue(stringify(JSBoolean.TRUE), "true");
        assertStringValue(stringify(JSBoolean.FALSE), "false");
        assertStringValue(stringify(numberArgs(42)), "42");
        assertStringValue(stringify(numberArgs(-123.45)), "-123.45");
        assertStringValue(stringify(stringArgs("hello world")), "\"hello world\"");
        assertStringValue(stringify(stringArgs("hello\nworld")), "\"hello\\nworld\"");
    }

    @Test
    public void testStringifyForReplacer() {
        // Test replacer function that converts BigInts to strings
        JSObject objWithBigInt = new JSObject(context);
        objWithBigInt.set("num", new JSNumber(123)); // Using number instead of BigInt for testing
        objWithBigInt.set("str", new JSString("test"));

        // Create a replacer function that doubles numbers
        JSFunction replacer = new JSNativeFunction(context, "replacer", 2, (context, thisArg, args) -> {
            JSValue value = args[1];
            if (value instanceof JSNumber num) {
                return new JSNumber(num.value() * 2);
            }
            return value;
        });

        JSValue result = stringify(objWithBigInt, replacer);
        String jsonStr = result.asString().map(JSString::value).orElseThrow();
        assertThat(jsonStr).contains("\"num\":246");
        assertThat(jsonStr).contains("\"str\":\"test\"");
    }

    @Test
    public void testStringifyFunction() {
        assertStringWithJavet("String(JSON.stringify(function() {}))");
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
    void testStringifyObjects() {
        assertStringWithJavet("JSON.stringify({})", "JSON.stringify({name: 'test', value: 42, active: true})",
                "JSON.stringify({a: 1, b: 2})", "JSON.stringify({a: 1, b: function() {}, c: 2})",
                "JSON.stringify({a: 1, b: undefined, c: 3})",
                "JSON.stringify([new Number(1.5), new String('x'), new Boolean(false)])");
    }

    @Test
    void testStringifyPrimitives() {
        assertStringWithJavet("JSON.stringify(true)", "JSON.stringify(0.1 + 0.2)", "JSON.stringify(Infinity)",
                "JSON.stringify(NaN)", "JSON.stringify(-Infinity)", "JSON.stringify(null)", "JSON.stringify(42)",
                "JSON.stringify(1e10)", "JSON.stringify(1e-10)", "JSON.stringify('hello')");
    }

    @Test
    void testStringifyReplacers() {
        assertStringWithJavet("JSON.stringify({'1.5': 'x', '1': 'y'}, [1.5])",
                "JSON.stringify({0: 'a', 1: 'b', 2: 'c'}, [0, 2])",
                "JSON.stringify({a: 1, b: 2}, [{toString: function() { return 'a'; }}, 'b'])",
                "JSON.stringify({a: 1, b: 2, c: 3, d: 4}, ['a', 'c'])");
    }

    @Test
    void testStringifySpacing() {
        assertStringWithJavet("JSON.stringify([1, 2, 3], null, 4)",
                "JSON.stringify({a: 1}, null, {toString: function() { return '  '; }})",
                "JSON.stringify({a: 1, b: 2}, null, 2)", "JSON.stringify({a: 1}, null, 'abcdefghijklmnop')",
                "JSON.stringify({a: 1}, null, new Number(2))", "JSON.stringify({a: 1, b: 2}, null, '  ')",
                "JSON.stringify({a: 1}, null, '\\t')");
    }

    @Test
    public void testStringifyUndefined() {
        assertBooleanWithJavet("JSON.stringify(undefined) === undefined");
    }

    @Test
    public void testStringifyWithBigInt() {
        assertErrorWithJavet("JSON.stringify(1n)", "JSON.stringify({a: 1n})", "JSON.stringify([1n])");
    }

    @Test
    public void testStringifyWithRawJSON() {
        assertStringWithJavet("JSON.stringify({a: JSON.rawJSON('1'), b: JSON.rawJSON('true')})");
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
        JSObject obj = new JSObject(context);
        obj.set("value", new JSNumber(10));

        // Add a toJSON method that returns a different value
        JSFunction toJSON = new JSNativeFunction(context, "toJSON", 0, (context, thisArg, args) -> {
            return new JSNumber(20);
        });
        obj.set("toJSON", toJSON);

        JSFunction replacer = doubleNumbers("replacer");

        JSValue result = stringify(obj, replacer);
        String jsonStr = result.asString().map(JSString::value).orElseThrow();

        // toJSON returns 20, then replacer doubles it to 40
        assertThat(jsonStr).isEqualTo("40");
    }

    @Test
    public void testStringifyWithReplacerArray() {
        // Test replacer array (property whitelist)
        JSObject obj = new JSObject(context);
        obj.set("name", new JSString("Alice"));
        obj.set("age", new JSNumber(30));
        obj.set("internal", new JSString("secret"));
        obj.set("password", new JSString("12345"));

        // Create a replacer array with only allowed properties
        JSArray replacer = new JSArray(context, new JSString("name"), new JSString("age"));

        JSValue result = stringify(obj, replacer);
        String jsonStr = result.asString().map(JSString::value).orElseThrow();

        assertThat(jsonStr).contains("\"name\":\"Alice\"");
        assertThat(jsonStr).contains("\"age\":30");
        assertThat(jsonStr).doesNotContain("internal");
        assertThat(jsonStr).doesNotContain("password");
    }

    @Test
    public void testStringifyWithReplacerArrayNested() {
        // Test replacer array with nested objects
        JSObject inner = new JSObject(context);
        inner.set("x", new JSNumber(1));
        inner.set("y", new JSNumber(2));
        inner.set("z", new JSNumber(3));

        JSObject outer = new JSObject(context);
        outer.set("data", inner);
        outer.set("extra", new JSString("value"));

        // Only allow "data" and "x", "y" properties
        JSArray replacer = new JSArray(context, new JSString("data"), new JSString("x"), new JSString("y"));

        JSValue result = stringify(outer, replacer);
        String jsonStr = result.asString().map(JSString::value).orElseThrow();

        assertThat(jsonStr).contains("\"data\":");
        assertThat(jsonStr).contains("\"x\":1");
        assertThat(jsonStr).contains("\"y\":2");
        assertThat(jsonStr).doesNotContain("\"z\"");
        assertThat(jsonStr).doesNotContain("extra");
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
    public void testStringifyWithReplacerArrayNumbers() {
        // Test that replacer array can contain numbers (for array indices)
        JSArray arr = new JSArray(context, new JSString("a"), new JSString("b"), new JSString("c"));

        JSObject obj = new JSObject(context);
        obj.set("0", new JSString("zero"));
        obj.set("1", new JSString("one"));
        obj.set("2", new JSString("two"));

        // Replacer array with numbers
        JSArray replacer = new JSArray(context, numberArgs(0, 2));

        JSValue result = stringify(obj, replacer);
        String jsonStr = result.asString().map(JSString::value).orElseThrow();

        assertThat(jsonStr).contains("\"0\":\"zero\"");
        assertThat(jsonStr).contains("\"2\":\"two\"");
        assertThat(jsonStr).doesNotContain("\"1\"");
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
    public void testStringifyWithReplacerFunction() {
        // Test replacer function
        JSObject obj = new JSObject(context);
        obj.set("name", new JSString("test"));
        obj.set("value", new JSNumber(100));
        obj.set("active", JSBoolean.TRUE);

        // Create a replacer that filters out boolean values
        JSFunction replacer = new JSNativeFunction(context, "replacer", 2, (context, thisArg, args) -> {
            String key = args[0].asString().map(JSString::value).orElseThrow();
            JSValue value = args[1];

            if (value instanceof JSBoolean) {
                return JSUndefined.INSTANCE; // Filter out booleans
            }
            return value;
        });

        JSValue result = stringify(obj, replacer);
        String jsonStr = result.asString().map(JSString::value).orElseThrow();

        assertThat(jsonStr).contains("\"name\":\"test\"");
        assertThat(jsonStr).contains("\"value\":100");
        assertThat(jsonStr).doesNotContain("active");
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
    public void testStringifyWithSpaceLimitNumber() {
        // Space is limited to 10 characters
        assertStringWithJavet("JSON.stringify({a: 1}, null, 20)");
    }

    @Test
    public void testStringifyWithToJSON() {
        // Test toJSON method support
        JSObject obj = new JSObject(context);
        obj.set("value", new JSNumber(42));

        // Add a toJSON method
        JSFunction toJSON = new JSNativeFunction(context, "toJSON", 0, (context, thisArg, args) -> {
            return new JSString("custom");
        });
        obj.set("toJSON", toJSON);

        JSValue result = stringify(obj);
        assertThat(result.asString().map(JSString::value).orElseThrow()).isEqualTo("\"custom\"");
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

}
