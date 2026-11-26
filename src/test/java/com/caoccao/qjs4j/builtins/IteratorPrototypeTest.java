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

    private void assertEntry(JSIterator iterator, JSValue key, JSValue value) {
        JSObject result = iterator.next();
        assertThat(result.get("done")).isEqualTo(JSBoolean.FALSE);
        JSArray entry = result.get("value").asArray().orElseThrow();
        assertThat(entry.get(0)).isEqualTo(key);
        assertThat(entry.get(1)).isEqualTo(value);
    }

    private void assertInvalidReceiver(JSNativeCallback method, JSValue receiver) {
        assertPendingTypeError(context, method.call(context, receiver, JSValue.NO_ARGS));
    }

    private void assertValues(JSIterator iterator, JSValue... values) {
        for (JSValue value : values) {
            assertIteratorResult(iterator.next(), value, false);
        }
        assertIteratorResult(iterator.next(), JSUndefined.INSTANCE, true);
    }

    private JSValue evalError(String code) {
        try {
            context.eval(code);
            fail("Expected JSException for code: " + code);
            return null;
        } catch (JSException e) {
            return e.getErrorValue();
        }
    }

    private JSIterator iterator(JSNativeCallback method, JSValue receiver) {
        return method.call(context, receiver, JSValue.NO_ARGS).asIterator().orElseThrow();
    }

    @Test
    public void testArrayEntries() {
        JSArray array = new JSArray(context, new JSString("a"), new JSString("b"));
        JSIterator iterator = iterator(IteratorPrototype::arrayEntries, array);
        assertEntry(iterator, JSNumber.of(0), new JSString("a"));
        assertEntry(iterator, JSNumber.of(1), new JSString("b"));
        assertValues(iterator);
        assertInvalidReceiver(IteratorPrototype::arrayEntries, JSNull.INSTANCE);
    }

    @Test
    void testArrayEntriesGeneric() {
        // Generic: called on plain object with length
        assertStringWithJavet(
                "(() => { var it = Array.prototype.entries.call({0: 'a', 1: 'b', length: 2}); var r = it.next(); return r.value[0] + ':' + r.value[1]; })()",
                "(() => { var it = Array.prototype.entries.call({0: 'a', 1: 'b', length: 2}); it.next(); var r = it.next(); return r.value[0] + ':' + r.value[1]; })()",
                "(() => { var it = Array.prototype.entries.call({0: 'a', 1: 'b', length: 2}); it.next(); it.next(); return String(it.next().done); })()");
        // Generic: called on string (ToObject wraps to String object)
        assertStringWithJavet(
                "(() => { var it = Array.prototype.entries.call('ab'); var r = it.next(); return r.value[0] + ':' + r.value[1]; })()",
                "(() => { var it = Array.prototype.entries.call('ab'); it.next(); var r = it.next(); return r.value[0] + ':' + r.value[1]; })()");
        // Generic: empty object (no length → 0)
        assertBooleanWithJavet("Array.prototype.entries.call({}).next().done");
        // Generic: boolean primitive (no length → 0)
        assertBooleanWithJavet("Array.prototype.entries.call(true).next().done");
        // Error: null/undefined throws TypeError
        assertErrorWithJavet("Array.prototype.entries.call(null)", "Array.prototype.entries.call(undefined)");
    }

    @Test
    public void testArrayKeys() {
        JSArray array = new JSArray(context, numberArgs(10, 20));
        JSIterator iterator = iterator(IteratorPrototype::arrayKeys, array);
        assertValues(iterator, JSNumber.of(0), JSNumber.of(1));
        assertValues(iterator(IteratorPrototype::arrayKeys, new JSObject(context)));
    }

    @Test
    void testArrayKeysGeneric() {
        // Generic: called on plain object with length
        assertStringWithJavet(
                "(() => { var it = Array.prototype.keys.call({length: 3}); return [it.next().value, it.next().value, it.next().value].join(','); })()",
                "(() => { var it = Array.prototype.keys.call({length: 3}); it.next(); it.next(); it.next(); return String(it.next().done); })()");
        // Generic: called on string
        assertStringWithJavet(
                "(() => { var it = Array.prototype.keys.call('hi'); return [it.next().value, it.next().value].join(','); })()");
        // Generic: empty object
        assertBooleanWithJavet("Array.prototype.keys.call({}).next().done");
        // Error: null throws TypeError
        assertErrorWithJavet("Array.prototype.keys.call(null)");
    }

    @Test
    public void testArrayValues() {
        JSArray array = new JSArray(context, new JSNumber(1), new JSString("hello"), JSBoolean.TRUE);
        JSIterator iterator = iterator(IteratorPrototype::arrayValues, array);
        assertValues(iterator, JSNumber.of(1), new JSString("hello"), JSBoolean.TRUE);
        assertValues(iterator(IteratorPrototype::arrayValues, new JSString("hi")), new JSString("h"),
                new JSString("i"));
    }

    @Test
    void testArrayValuesGeneric() {
        // Generic: called on plain object with length
        assertStringWithJavet(
                "(() => { var it = Array.prototype.values.call({0: 'x', 1: 'y', length: 2}); return [it.next().value, it.next().value].join(','); })()",
                "(() => { var it = Array.prototype.values.call({0: 'x', 1: 'y', length: 2}); it.next(); it.next(); return String(it.next().done); })()");
        // Generic: called on string (iterates characters)
        assertStringWithJavet(
                "(() => { var it = Array.prototype.values.call('ab'); return [it.next().value, it.next().value].join(','); })()");
        // Generic: missing indices return undefined
        assertStringWithJavet(
                "(() => { var it = Array.prototype.values.call({length: 2}); return String(it.next().value); })()");
        // Generic: number primitive (no length → 0)
        assertBooleanWithJavet("Array.prototype.values.call(42).next().done");
        // Error: undefined throws TypeError
        assertErrorWithJavet("Array.prototype.values.call(undefined)");
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
        assertStringWithJavet("Iterator.from([1, 2, 3]).map(v => v * 2).toArray().join(',');",
                "Iterator.from([1, 2, 3, 4]).filter(v => v % 2 === 0).toArray().join(',');",
                "Iterator.from([1, 2, 3, 4]).drop(2).toArray().join(',');",
                "Iterator.from([1, 2, 3, 4]).take(2).toArray().join(',');",
                "Iterator.from([1, 2]).flatMap(v => [v, v + 10]).toArray().join(',');",
                "Iterator.from('ab').toArray().join(',');");
    }

    @Test
    void testIteratorHelpersEdgeErrors() {
        assertStringWithJavet("(() => { try { Iterator.from(1); return 'OK'; } catch (e) { return e.name; } })();",
                "(() => { try { Iterator.from([1]).drop(-1); return 'OK'; } catch (e) { return e.name; } })();",
                "(() => { try { Iterator.from([1]).drop(NaN); return 'OK'; } catch (e) { return e.name; } })();",
                "(() => { try { Iterator.from([1]).take(-1); return 'OK'; } catch (e) { return e.name; } })();",
                "(() => { try { Iterator.from([]).reduce((a, b) => a + b); return 'OK'; } catch (e) { return e.name; } })();",
                "(() => { try { Iterator.prototype.map.call(1, x => x); return 'OK'; } catch (e) { return e.name; } })();");
    }

    @Test
    void testIteratorHelpersTerminalOperations() {
        assertBooleanWithJavet("Iterator.from([2, 4, 6]).every(v => v % 2 === 0);",
                "Iterator.from([1, 3, 4]).some(v => v % 2 === 0);");
        assertIntegerWithJavet("Iterator.from([1, 2, 3]).reduce((a, b) => a + b, 0);",
                "Iterator.from([1, 2, 3]).reduce((a, b) => a + b);");
        assertStringWithJavet("String(Iterator.from([1, 3, 4]).find(v => v % 2 === 0));");
    }

    @Test
    public void testMapEntriesIterator() {
        JSMap map = new JSMap(context);
        map.mapSet(new JSString("key1"), new JSString("value1"));
        map.mapSet(new JSString("key2"), new JSNumber(42));
        JSIterator iterator = iterator(IteratorPrototype::mapEntriesIterator, map);
        assertEntry(iterator, new JSString("key1"), new JSString("value1"));
        assertEntry(iterator, new JSString("key2"), JSNumber.of(42));
        assertValues(iterator);
        assertInvalidReceiver(IteratorPrototype::mapEntriesIterator, new JSArray(context));
    }

    @Test
    public void testMapKeysIterator() {
        JSMap map = new JSMap(context);
        map.mapSet(new JSString("a"), new JSNumber(1));
        map.mapSet(new JSString("b"), new JSNumber(2));
        JSIterator iterator = iterator(IteratorPrototype::mapKeysIterator, map);
        assertValues(iterator, new JSString("a"), new JSString("b"));
        assertInvalidReceiver(IteratorPrototype::mapKeysIterator, JSUndefined.INSTANCE);
    }

    @Test
    public void testMapValuesIterator() {
        JSMap map = new JSMap(context);
        map.mapSet(new JSNumber(1), new JSString("one"));
        map.mapSet(new JSNumber(2), new JSString("two"));
        JSIterator iterator = iterator(IteratorPrototype::mapValuesIterator, map);
        assertValues(iterator, new JSString("one"), new JSString("two"));
        assertInvalidReceiver(IteratorPrototype::mapValuesIterator, new JSString("not a map"));
    }

    @Test
    public void testNext() {
        // Create an array iterator
        JSArray array = new JSArray(context, numberArgs(1, 2));
        JSIterator iterator = JSIterator.arrayIterator(context, array);

        // Normal case: next() on iterator
        JSValue result = IteratorPrototype.next(context, iterator, JSValue.NO_ARGS);
        assertIteratorResult(result, new JSNumber(1.0), false);

        // Continue iteration
        result = IteratorPrototype.next(context, iterator, JSValue.NO_ARGS);
        assertIteratorResult(result, new JSNumber(2.0), false);

        // End of iteration
        result = IteratorPrototype.next(context, iterator, JSValue.NO_ARGS);
        assertIteratorResult(result, JSUndefined.INSTANCE, true);

        // Edge case: called on non-iterator
        result = IteratorPrototype.next(context, new JSString("not an iterator"), JSValue.NO_ARGS);
        assertTypeError(result);
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testSetEntriesIterator() {
        JSSet set = new JSSet(context);
        set.setAdd(new JSString("hello"));
        set.setAdd(new JSNumber(42));
        JSIterator iterator = iterator(IteratorPrototype::setEntriesIterator, set);
        assertEntry(iterator, new JSString("hello"), new JSString("hello"));
        assertEntry(iterator, JSNumber.of(42), JSNumber.of(42));
        assertValues(iterator);
        assertInvalidReceiver(IteratorPrototype::setEntriesIterator, JSBoolean.FALSE);
    }

    @Test
    public void testSetKeysIterator() {
        // Create test set
        JSSet set = new JSSet(context);
        set.setAdd(new JSString("x"));
        set.setAdd(new JSNumber(5));

        // Normal case: keys() should be same as values() for Set
        JSValue result = IteratorPrototype.setKeysIterator(context, set, JSValue.NO_ARGS);
        result.asIterator().orElseThrow();

        // Should behave identically to values iterator
        JSValue valuesResult = IteratorPrototype.setValuesIterator(context, set, JSValue.NO_ARGS);
        // Note: In a full test, we'd verify they produce the same results

        // Edge case: called on non-set (should delegate to setValuesIterator)
        result = IteratorPrototype.setKeysIterator(context, new JSArray(context), JSValue.NO_ARGS);
        assertTypeError(result);
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testSetValuesIterator() {
        JSSet set = new JSSet(context);
        set.setAdd(new JSString("a"));
        set.setAdd(new JSString("b"));
        set.setAdd(new JSNumber(3));
        JSIterator iterator = iterator(IteratorPrototype::setValuesIterator, set);
        assertValues(iterator, new JSString("a"), new JSString("b"), JSNumber.of(3));
        assertInvalidReceiver(IteratorPrototype::setValuesIterator, new JSObject(context));
    }

    @Test
    public void testStringIterator() {
        JSString str = new JSString("abc");
        assertValues(iterator(IteratorPrototype::stringIterator, str), new JSString("a"), new JSString("b"),
                new JSString("c"));
        assertValues(iterator(IteratorPrototype::stringIterator, new JSString("")));
        JSObject boxedString = new JSObject(context);
        boxedString.setPrimitiveValue(str);
        iterator(IteratorPrototype::stringIterator, boxedString);
        assertInvalidReceiver(IteratorPrototype::stringIterator, new JSNumber(123));
        JSObject badBox = new JSObject(context);
        badBox.set("[[PrimitiveValue]]", new JSNumber(456));
        assertInvalidReceiver(IteratorPrototype::stringIterator, badBox);
    }
}
