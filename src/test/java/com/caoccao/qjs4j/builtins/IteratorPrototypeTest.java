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
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Unit tests for IteratorPrototype methods.
 */
public class IteratorPrototypeTest extends BaseJavetTest {

    private JSValue evalError(String code) {
        try {
            context.eval(code);
            fail("Expected JSException for code: " + code);
            return null;
        } catch (JSException e) {
            return e.getErrorValue();
        }
    }

    @Test
    public void testArrayEntries() {
        // Create test array
        JSArray array = new JSArray();
        array.push(new JSString("a"));
        array.push(new JSString("b"));

        // Normal case: get entries iterator
        JSValue result = IteratorPrototype.arrayEntries(context, array, new JSValue[]{});
        JSIterator iterator = result.asIterator().orElseThrow();

        // Test iteration - first entry [0, "a"]
        JSObject iteratorResult = iterator.next();
        JSArray pair = iteratorResult.get("value").asArray().orElseThrow();
        assertThat(pair.get(0)).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(0.0));
        assertThat(pair.get(1)).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("a"));
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);

        // Second entry [1, "b"]
        iteratorResult = iterator.next();
        pair = iteratorResult.get("value").asArray().orElseThrow();
        assertThat(pair.get(0)).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(1.0));
        assertThat(pair.get(1)).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("b"));
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);

        // End
        iteratorResult = iterator.next();
        assertThat(iteratorResult.get("value")).isEqualTo(JSUndefined.INSTANCE);
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.TRUE);

        // Edge case: called on non-array
        result = IteratorPrototype.arrayEntries(context, JSNull.INSTANCE, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testArrayKeys() {
        // Create test array
        JSArray array = new JSArray();
        array.push(new JSNumber(10));
        array.push(new JSNumber(20));

        // Normal case: get keys iterator
        JSValue result = IteratorPrototype.arrayKeys(context, array, new JSValue[]{});
        JSIterator iterator = result.asIterator().orElseThrow();

        // Test iteration
        JSObject iteratorResult = iterator.next();
        assertThat(iteratorResult.get("value")).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(0.0));
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);

        iteratorResult = iterator.next();
        assertThat(iteratorResult.get("value")).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(1.0));
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);

        iteratorResult = iterator.next();
        assertThat(iteratorResult.get("value")).isEqualTo(JSUndefined.INSTANCE);
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.TRUE);

        // Edge case: called on non-array
        result = IteratorPrototype.arrayKeys(context, new JSObject(), new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testArrayValues() {
        // Create test array
        JSArray array = new JSArray();
        array.push(new JSNumber(1));
        array.push(new JSString("hello"));
        array.push(JSBoolean.TRUE);

        // Normal case: get values iterator
        JSValue result = IteratorPrototype.arrayValues(context, array, new JSValue[]{});
        JSIterator iterator = result.asIterator().orElseThrow();

        // Test iteration
        JSObject iteratorResult = iterator.next();
        assertThat(iteratorResult.get("value")).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(1.0));
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);

        iteratorResult = iterator.next();
        assertThat(iteratorResult.get("value")).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("hello"));
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);

        iteratorResult = iterator.next();
        assertThat(iteratorResult.get("value")).isEqualTo(JSBoolean.TRUE);
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);

        iteratorResult = iterator.next();
        assertThat(iteratorResult.get("value")).isEqualTo(JSUndefined.INSTANCE);
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.TRUE);

        // Edge case: called on non-array
        result = IteratorPrototype.arrayValues(context, new JSString("not an array"), new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    void testIteratorHelperErrorPaths() {
        assertTypeError(evalError("Iterator.from([]).map(1)"));
        assertTypeError(evalError("Iterator.from([]).filter(1)"));
        assertTypeError(evalError("Iterator.from([]).flatMap(1)"));
        assertRangeError(evalError("Iterator.from([1]).drop(-1)"));
        assertRangeError(evalError("Iterator.from([1]).take(-1)"));
        assertTypeError(evalError("Iterator.from([]).reduce((a, b) => a + b)"));
    }

    @Test
    void testIteratorHelpersBasicTransformations() {
        assertStringWithJavet(
                "Iterator.from([1, 2, 3]).map(v => v * 2).toArray().join(',');",
                "Iterator.from([1, 2, 3, 4]).filter(v => v % 2 === 0).toArray().join(',');",
                "Iterator.from([1, 2, 3, 4]).drop(2).toArray().join(',');",
                "Iterator.from([1, 2, 3, 4]).take(2).toArray().join(',');",
                "Iterator.from([1, 2]).flatMap(v => [v, v + 10]).toArray().join(',');",
                "Iterator.from('ab').toArray().join(',');");
    }

    @Test
    void testIteratorHelpersEdgeErrors() {
        assertStringWithJavet(
                "(() => { try { Iterator.from(1); return 'OK'; } catch (e) { return e.name; } })();",
                "(() => { try { Iterator.from([1]).drop(-1); return 'OK'; } catch (e) { return e.name; } })();",
                "(() => { try { Iterator.from([1]).drop(NaN); return 'OK'; } catch (e) { return e.name; } })();",
                "(() => { try { Iterator.from([1]).take(-1); return 'OK'; } catch (e) { return e.name; } })();",
                "(() => { try { Iterator.from([]).reduce((a, b) => a + b); return 'OK'; } catch (e) { return e.name; } })();",
                "(() => { try { Iterator.prototype.map.call(1, x => x); return 'OK'; } catch (e) { return e.name; } })();");
    }

    @Test
    void testIteratorHelpersTerminalOperations() {
        assertBooleanWithJavet(
                "Iterator.from([2, 4, 6]).every(v => v % 2 === 0);",
                "Iterator.from([1, 3, 4]).some(v => v % 2 === 0);");
        assertIntegerWithJavet(
                "Iterator.from([1, 2, 3]).reduce((a, b) => a + b, 0);",
                "Iterator.from([1, 2, 3]).reduce((a, b) => a + b);");
        assertStringWithJavet(
                "String(Iterator.from([1, 3, 4]).find(v => v % 2 === 0));");
    }

    @Test
    public void testMapEntriesIterator() {
        // Create test map
        JSMap map = new JSMap();
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSNumber(42));

        // Normal case: get entries iterator
        JSValue result = IteratorPrototype.mapEntriesIterator(context, map, new JSValue[]{});
        JSIterator iterator = result.asIterator().orElseThrow();

        // Test iteration (order may vary)
        boolean foundKey1 = false, foundKey2 = false;
        for (int i = 0; i < 2; i++) {
            JSObject iteratorResult = iterator.next();
            assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);
            JSArray pair = iteratorResult.get("value").asArray().orElseThrow();

            String key = pair.get(0).asString().map(JSString::value).orElseThrow();
            if ("key1".equals(key)) {
                assertThat(pair.get(1)).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("value1"));
                foundKey1 = true;
            } else if ("key2".equals(key)) {
                assertThat(pair.get(1)).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(42.0));
                foundKey2 = true;
            }
        }
        assertThat(foundKey1 && foundKey2).isTrue();

        // End of iteration
        JSObject iteratorResult = iterator.next();
        assertThat(iteratorResult.get("value")).isEqualTo(JSUndefined.INSTANCE);
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.TRUE);

        // Edge case: called on non-map
        result = IteratorPrototype.mapEntriesIterator(context, new JSArray(), new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testMapKeysIterator() {
        // Create test map
        JSMap map = new JSMap();
        map.mapSet(new JSString("a"), new JSNumber(1));
        map.mapSet(new JSString("b"), new JSNumber(2));

        // Normal case: get keys iterator
        JSValue result = IteratorPrototype.mapKeysIterator(context, map, new JSValue[]{});
        JSIterator iterator = result.asIterator().orElseThrow();

        // Test iteration
        boolean foundA = false, foundB = false;
        for (int i = 0; i < 2; i++) {
            JSObject iteratorResult = iterator.next();
            assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);
            String key = iteratorResult.get("value").asString().map(JSString::value).orElseThrow();
            if ("a".equals(key)) foundA = true;
            else if ("b".equals(key)) foundB = true;
        }
        assertThat(foundA && foundB).isTrue();

        // Edge case: called on non-map
        result = IteratorPrototype.mapKeysIterator(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testMapValuesIterator() {
        // Create test map
        JSMap map = new JSMap();
        map.mapSet(new JSNumber(1), new JSString("one"));
        map.mapSet(new JSNumber(2), new JSString("two"));

        // Normal case: get values iterator
        JSValue result = IteratorPrototype.mapValuesIterator(context, map, new JSValue[]{});
        JSIterator iterator = result.asIterator().orElseThrow();

        // Test iteration
        boolean foundOne = false, foundTwo = false;
        for (int i = 0; i < 2; i++) {
            JSObject iteratorResult = iterator.next();
            assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);
            String value = iteratorResult.get("value").asString().map(JSString::value).orElseThrow();
            if ("one".equals(value)) foundOne = true;
            else if ("two".equals(value)) foundTwo = true;
        }
        assertThat(foundOne && foundTwo).isTrue();

        // Edge case: called on non-map
        result = IteratorPrototype.mapValuesIterator(context, new JSString("not a map"), new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testNext() {
        // Create an array iterator
        JSArray array = new JSArray();
        array.push(new JSNumber(1));
        array.push(new JSNumber(2));
        JSIterator iterator = JSIterator.arrayIterator(context, array);

        // Normal case: next() on iterator
        JSValue result = IteratorPrototype.next(context, iterator, new JSValue[]{});
        JSObject iteratorResult = result.asObject().orElseThrow();
        assertThat(iteratorResult.get("value")).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(1.0));
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);

        // Continue iteration
        result = IteratorPrototype.next(context, iterator, new JSValue[]{});
        iteratorResult = result.asObject().orElseThrow();
        assertThat(iteratorResult.get("value")).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(2.0));
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);

        // End of iteration
        result = IteratorPrototype.next(context, iterator, new JSValue[]{});
        iteratorResult = result.asObject().orElseThrow();
        assertThat(iteratorResult.get("value")).isEqualTo(JSUndefined.INSTANCE);
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.TRUE);

        // Edge case: called on non-iterator
        result = IteratorPrototype.next(context, new JSString("not an iterator"), new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testSetEntriesIterator() {
        // Create test set
        JSSet set = new JSSet();
        set.setAdd(new JSString("hello"));
        set.setAdd(new JSNumber(42));

        // Normal case: get entries iterator
        JSValue result = IteratorPrototype.setEntriesIterator(context, set, new JSValue[]{});
        JSIterator iterator = result.asIterator().orElseThrow();

        // Test iteration - each entry should be [value, value]
        boolean foundHello = false, found42 = false;
        for (int i = 0; i < 2; i++) {
            JSObject iteratorResult = iterator.next();
            assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);
            JSArray pair = iteratorResult.get("value").asArray().orElseThrow();

            // In Set entries, both elements should be the same
            assertThat(pair.get(0)).isEqualTo(pair.get(1));

            JSValue value = pair.get(0);
            if (value instanceof JSString str && "hello".equals(str.value())) {
                foundHello = true;
            } else if (value instanceof JSNumber num && num.value() == 42.0) {
                found42 = true;
            }
        }
        assertThat(foundHello && found42).isTrue();

        // Edge case: called on non-set
        result = IteratorPrototype.setEntriesIterator(context, JSBoolean.FALSE, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testSetKeysIterator() {
        // Create test set
        JSSet set = new JSSet();
        set.setAdd(new JSString("x"));
        set.setAdd(new JSNumber(5));

        // Normal case: keys() should be same as values() for Set
        JSValue result = IteratorPrototype.setKeysIterator(context, set, new JSValue[]{});
        result.asIterator().orElseThrow();

        // Should behave identically to values iterator
        JSValue valuesResult = IteratorPrototype.setValuesIterator(context, set, new JSValue[]{});
        // Note: In a full test, we'd verify they produce the same results

        // Edge case: called on non-set (should delegate to setValuesIterator)
        result = IteratorPrototype.setKeysIterator(context, new JSArray(), new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testSetValuesIterator() {
        // Create test set
        JSSet set = new JSSet();
        set.setAdd(new JSString("a"));
        set.setAdd(new JSString("b"));
        set.setAdd(new JSNumber(3));

        // Normal case: get values iterator
        JSValue result = IteratorPrototype.setValuesIterator(context, set, new JSValue[]{});
        JSIterator iterator = result.asIterator().orElseThrow();

        // Test iteration (order may vary, but all values should be present)
        boolean foundA = false, foundB = false, found3 = false;
        for (int i = 0; i < 3; i++) {
            JSObject iteratorResult = iterator.next();
            assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);
            JSValue value = iteratorResult.get("value");
            if (value instanceof JSString str) {
                if ("a".equals(str.value())) foundA = true;
                else if ("b".equals(str.value())) foundB = true;
            } else if (value instanceof JSNumber num && num.value() == 3.0) {
                found3 = true;
            }
        }
        assertThat(foundA && foundB && found3).isTrue();

        // End of iteration
        JSObject iteratorResult = iterator.next();
        assertThat(iteratorResult.get("value")).isEqualTo(JSUndefined.INSTANCE);
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.TRUE);

        // Edge case: called on non-set
        result = IteratorPrototype.setValuesIterator(context, new JSObject(), new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testStringIterator() {
        // Normal case: string iteration
        JSString str = new JSString("abc");
        JSValue result = IteratorPrototype.stringIterator(context, str, new JSValue[]{});
        JSIterator iterator = result.asIterator().orElseThrow();

        // Test iteration
        JSObject iteratorResult = iterator.next();
        assertThat(iteratorResult.get("value")).isInstanceOfSatisfying(JSString.class, s -> assertThat(s.value()).isEqualTo("a"));
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);

        iteratorResult = iterator.next();
        assertThat(iteratorResult.get("value")).isInstanceOfSatisfying(JSString.class, s -> assertThat(s.value()).isEqualTo("b"));
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);

        iteratorResult = iterator.next();
        assertThat(iteratorResult.get("value")).isInstanceOfSatisfying(JSString.class, s -> assertThat(s.value()).isEqualTo("c"));
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);

        iteratorResult = iterator.next();
        assertThat(iteratorResult.get("value")).isEqualTo(JSUndefined.INSTANCE);
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.TRUE);

        // Normal case: empty string
        JSString emptyStr = new JSString("");
        result = IteratorPrototype.stringIterator(context, emptyStr, new JSValue[]{});
        iterator = result.asIterator().orElseThrow();
        iteratorResult = iterator.next();
        assertThat(iteratorResult.get("value")).isEqualTo(JSUndefined.INSTANCE);
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.TRUE);

        // Normal case: boxed string
        JSObject boxedString = new JSObject();
        boxedString.setPrimitiveValue(str);
        result = IteratorPrototype.stringIterator(context, boxedString, new JSValue[]{});
        result.asIterator().orElseThrow();

        // Edge case: called on non-string
        result = IteratorPrototype.stringIterator(context, new JSNumber(123), new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();

        // Edge case: boxed non-string
        JSObject badBox = new JSObject();
        badBox.set("[[PrimitiveValue]]", new JSNumber(456));
        result = IteratorPrototype.stringIterator(context, badBox, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }
}