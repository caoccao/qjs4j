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
import org.junit.jupiter.api.Test;

/**
 * Javet-based tests for JSON.stringify() and JSON.parse() methods.
 * Tests cover replacer/reviver functionality, edge cases, and error conditions.
 */
public class JSONObjectJavetTest extends BaseJavetTest {

    @Test
    public void testParseArrayElement() {
        String code = "JSON.parse('[1,2,3]')[1]";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeInteger().doubleValue(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testParseArrayLength() {
        String code = "JSON.parse('[1,\"two\",true,null]').length";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeInteger().doubleValue(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testParseBoolean() {
        String code = "JSON.parse('true')";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeBoolean(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testParseComplexNested() {
        String code = """
                var json = '{"users":[{"name":"Alice","age":30},{"name":"Bob","age":25}],"metadata":{"version":"1.0"}}';
                var obj = JSON.parse(json);
                obj.users[0].name""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testParseEmptyString() {
        assertErrorWithJavet("JSON.parse('')");
    }

    @Test
    public void testParseEscapedNewline() {
        String code = "JSON.parse('\"hello\\\\nworld\"')";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testParseEscapedTab() {
        String code = "JSON.parse('\"tab\\\\there\"')";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
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
        String code = "JSON.parse('{\"items\":[1,2,3]}').items[1]";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeInteger().doubleValue(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testParseNestedObject() {
        String code = "JSON.parse('{\"user\":{\"name\":\"Alice\",\"age\":30}}').user.name";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testParseNoArguments() {
        assertErrorWithJavet("JSON.parse()");
    }

    @Test
    public void testParseNull() {
        String code = "JSON.parse('null')";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeObject(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testParseNumber() {
        String code = "JSON.parse('42')";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeInteger().doubleValue(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testParseObjectProperty() {
        String code = "JSON.parse('{\"a\":1,\"b\":2}').a";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeInteger().doubleValue(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testParseObjectStringProperty() {
        String code = "JSON.parse('{\"name\":\"test\",\"value\":42}').name";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testParseString() {
        String code = "JSON.parse('\"hello\"')";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testParseTrailingComma() {
        assertErrorWithJavet("JSON.parse('{\"a\":1,}')");
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
    public void testParseWithReviver() {
        String code = """
                JSON.parse('{"a":1,"b":2,"c":3}', function(key, value) {
                  if (typeof value === 'number') {
                    return value * 2;
                  }
                  return value;
                }).b""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeInteger().doubleValue(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testParseWithReviverAndComplexStructure() {
        String code = """
                var json = '{"numbers":[1,2,3],"data":{"x":10,"y":20}}';
                var result = JSON.parse(json, function(key, value) {
                  if (typeof value === 'number') {
                    return value + 1;
                  }
                  return value;
                });
                result.data.x""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeInteger().doubleValue(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testParseWithReviverArray() {
        String code = """
                JSON.parse('[1,2,3,4,5]', function(key, value) {
                  if (typeof value === 'number') {
                    return value * 2;
                  }
                  return value;
                })[2]""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeInteger().doubleValue(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testParseWithReviverFilter() {
        String code = """
                var result = JSON.parse('{"a":1,"b":2,"c":3}', function(key, value) {
                  if (key === 'b') {
                    return undefined;
                  }
                  return value;
                });
                result.b""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeObject(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testParseWithReviverNested() {
        String code = """
                JSON.parse('{"user":{"name":"Bob","age":25}}', function(key, value) {
                  if (key === 'age' && typeof value === 'number') {
                    return value + 10;
                  }
                  return value;
                }).user.age""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeInteger().doubleValue(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testParseWithReviverTransform() {
        String code = """
                JSON.parse('{"date":"2023-01-01"}', function(key, value) {
                  if (key === 'date') {
                    return 'Transformed: ' + value;
                  }
                  return value;
                }).date""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testParseWithWhitespace() {
        String code = "JSON.parse('  {  \"a\"  :  1  }  ').a";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeInteger().doubleValue(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testRoundTrip() {
        String code = """
                var original = {name: 'test', value: 42, items: [1, 2, 3]};
                var json = JSON.stringify(original);
                var parsed = JSON.parse(json);
                parsed.value""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeInteger().doubleValue(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testRoundTripWithReplacerReviver() {
        String code = """
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
                parsed.b""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeInteger().doubleValue(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testRoundTripWithSpace() {
        String code = """
                var original = {a: 1, b: 2};
                var json = JSON.stringify(original, null, 2);
                var parsed = JSON.parse(json);
                parsed.b""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeInteger().doubleValue(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyArrayMixed() {
        String code = "JSON.stringify([1, 'two', true, null])";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyArrayNested() {
        String code = "JSON.stringify([[1, 2], [3, 4]])";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyArraySimple() {
        String code = "JSON.stringify([1, 2, 3])";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyArrayWithMixedTypes() {
        String code = "JSON.stringify([1, 'two', true, null, {a: 5}, [6, 7]])";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyArrayWithNumberSpace() {
        String code = "JSON.stringify([1, 2, 3], null, 4)";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyArrayWithUndefined() {
        String code = "JSON.stringify([1, undefined, 3])";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyBoolean() {
        String code = "JSON.stringify(true)";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
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
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyEmptyArray() {
        String code = "JSON.stringify([])";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyEmptyObject() {
        String code = "JSON.stringify({})";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyEscapeNewline() {
        String code = "JSON.stringify('hello\\nworld')";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyEscapeQuote() {
        String code = "JSON.stringify('quote\\\"test')";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyEscapeTab() {
        String code = "JSON.stringify('tab\\there')";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyFloatingPoint() {
        String code = "JSON.stringify(0.1 + 0.2)";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyFunction() {
        String code = "JSON.stringify(function() {})";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeObject(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyInfinity() {
        String code = "JSON.stringify(Infinity)";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyNaN() {
        String code = "JSON.stringify(NaN)";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyNegativeInfinity() {
        String code = "JSON.stringify(-Infinity)";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyNestedStructure() {
        String code = """
                JSON.stringify({
                  user: {
                    name: 'Alice',
                    age: 30
                  },
                  items: [1, 2, 3]
                })""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyNull() {
        String code = "JSON.stringify(null)";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyNumber() {
        String code = "JSON.stringify(42)";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyObjectComplex() {
        String code = "JSON.stringify({name: 'test', value: 42, active: true})";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyObjectSimple() {
        String code = "JSON.stringify({a: 1, b: 2})";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyObjectWithFunction() {
        String code = "JSON.stringify({a: 1, b: function() {}, c: 2})";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyObjectWithUndefined() {
        String code = "JSON.stringify({a: 1, b: undefined, c: 3})";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyScientificNotationLarge() {
        String code = "JSON.stringify(1e10)";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyScientificNotationSmall() {
        String code = "JSON.stringify(1e-10)";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyString() {
        String code = "JSON.stringify('hello')";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyUndefined() {
        String code = "JSON.stringify(undefined)";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeObject(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyWithNumberSpace() {
        String code = "JSON.stringify({a: 1, b: 2}, null, 2)";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyWithReplacerAndSpace() {
        String code = """
                JSON.stringify(
                  {a: 1, b: 2, c: 3, d: 4},
                  ['a', 'c'],
                  2
                )""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyWithReplacerArray() {
        String code = "JSON.stringify({a: 1, b: 2, c: 3, d: 4}, ['a', 'c'])";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyWithReplacerArrayNested() {
        String code = """
                JSON.stringify({
                  name: 'test',
                  data: {x: 1, y: 2, z: 3},
                  extra: 'value'
                }, ['name', 'data', 'x', 'y'])""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyWithReplacerArrayNumbers() {
        String code = "JSON.stringify({0: 'a', 1: 'b', 2: 'c'}, [0, 2])";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyWithReplacerFilteringOut() {
        String code = """
                JSON.stringify({a: 1, b: 2, c: 3}, function(key, value) {
                  if (key === 'b') {
                    return undefined;
                  }
                  return value;
                })""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyWithReplacerFunction() {
        String code = """
                JSON.stringify({a: 1, b: 2, c: 3}, function(key, value) {
                  if (typeof value === 'number') {
                    return value * 2;
                  }
                  return value;
                })""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyWithSpaceLimitNumber() {
        // Space is limited to 10 characters
        String code = "JSON.stringify({a: 1}, null, 20)";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyWithSpaceLimitString() {
        String code = "JSON.stringify({a: 1}, null, 'abcdefghijklmnop')";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyWithStringSpace() {
        String code = "JSON.stringify({a: 1, b: 2}, null, '  ')";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyWithTabSpace() {
        String code = "JSON.stringify({a: 1}, null, '\\t')";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyWithToJSON() {
        String code = """
                var obj = {
                  value: 42,
                  toJSON: function() {
                    return this.value * 2;
                  }
                };
                JSON.stringify(obj)""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }

    @Test
    public void testStringifyWithToJSONAndReplacer() {
        String code = """
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
                })""";
        assertWithJavet(
                () -> v8Runtime.getExecutor(code).executeString(),
                () -> context.eval(code).toJavaObject());
    }
}
