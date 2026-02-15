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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Generator.prototype methods.
 */
public class GeneratorPrototypeTest extends BaseJavetTest {
    @Test
    public void testBasicYield() {
        assertIntegerWithJavet(
                "function* gen() { yield 1; yield 2; yield 3; } var g = gen(); g.next().value");
    }

    @Test
    public void testCustomGenerator() {
        // Create a custom generator that yields specific values
        final int[] counter = {0};
        JSGenerator generator = JSGenerator.fromIteratorFunction(context, () -> {
            counter[0]++;
            if (counter[0] == 1) {
                return JSIterator.IteratorResult.of(context, new JSString("first"));
            } else if (counter[0] == 2) {
                return JSIterator.IteratorResult.of(context, new JSString("second"));
            } else {
                return JSIterator.IteratorResult.done(context);
            }
        });

        // Test the custom generator
        JSValue result = GeneratorPrototype.next(context, generator, new JSValue[]{});
        JSObject iteratorResult = result.asObject().orElseThrow();
        assertThat(iteratorResult.get("value")).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("first"));
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);

        result = GeneratorPrototype.next(context, generator, new JSValue[]{});
        iteratorResult = result.asObject().orElseThrow();
        assertThat(iteratorResult.get("value")).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("second"));
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);

        result = GeneratorPrototype.next(context, generator, new JSValue[]{});
        iteratorResult = result.asObject().orElseThrow();
        assertThat(iteratorResult.get("value")).isEqualTo(JSUndefined.INSTANCE);
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.TRUE);
    }

    @Test
    public void testDoneAfterCompletion() {
        assertBooleanWithJavet(
                "function* gen() { yield 1; } var g = gen(); g.next(); g.next().done === true");
    }

    @Test
    public void testEmptyGenerator() {
        // Create an empty generator
        JSArray emptyArray = new JSArray();
        JSGenerator generator = JSGenerator.fromArray(context, emptyArray);

        // Normal case: next() on empty generator
        JSValue result = GeneratorPrototype.next(context, generator, new JSValue[]{});
        JSObject iteratorResult = result.asObject().orElseThrow();
        assertThat(iteratorResult.get("value")).isEqualTo(JSUndefined.INSTANCE);
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.TRUE);

        // Normal case: return on empty generator
        JSGenerator generator2 = JSGenerator.fromArray(context, emptyArray);
        result = GeneratorPrototype.returnMethod(context, generator2, new JSValue[]{new JSString("done")});
        iteratorResult = result.asObject().orElseThrow();
        assertThat(iteratorResult.get("value")).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("done"));
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.TRUE);
    }

    @Test
    public void testEmptyGeneratorEval() {
        assertBooleanWithJavet(
                "function* gen() {} var g = gen(); g.next().done === true");
    }

    @Test
    public void testEmptyGeneratorValueIsUndefined() {
        assertBooleanWithJavet(
                "function* gen() {} var g = gen(); g.next().value === undefined");
    }

    @Test
    public void testForOfLoop() {
        assertIntegerWithJavet(
                "function* gen() { yield 1; yield 2; yield 3; } var sum = 0; for (var v of gen()) sum += v; sum");
    }

    @Test
    public void testGeneratorIsIterable() {
        assertBooleanWithJavet(
                "function* gen() { yield 1; } var g = gen(); g[Symbol.iterator]() === g");
    }

    @Test
    public void testGeneratorReturnIsDone() {
        assertBooleanWithJavet(
                "function* gen() { yield 1; return 99; } var g = gen(); g.next(); g.next().done === true");
    }

    @Test
    public void testGeneratorWithReturn() {
        assertIntegerWithJavet(
                "function* gen() { yield 1; return 99; } var g = gen(); g.next(); g.next().value");
    }

    @Test
    public void testMultipleGeneratorInstances() {
        assertIntegerWithJavet(
                "function* gen() { yield 1; yield 2; } var g1 = gen(); var g2 = gen(); g1.next(); g1.next().value + g2.next().value");
    }

    @Test
    public void testNext() {
        // Create a simple generator from array
        JSArray array = new JSArray();
        array.push(new JSNumber(1));
        array.push(new JSNumber(2));
        array.push(new JSNumber(3));
        JSGenerator generator = JSGenerator.fromArray(context, array);

        // Normal case: first next() call
        JSValue result = GeneratorPrototype.next(context, generator, new JSValue[]{});
        JSObject iteratorResult = result.asObject().orElseThrow();
        assertThat(iteratorResult.get("value")).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(1.0));
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);

        // Normal case: second next() call
        result = GeneratorPrototype.next(context, generator, new JSValue[]{});
        iteratorResult = result.asObject().orElseThrow();
        assertThat(iteratorResult.get("value")).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(2.0));
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);

        // Normal case: third next() call
        result = GeneratorPrototype.next(context, generator, new JSValue[]{});
        iteratorResult = result.asObject().orElseThrow();
        assertThat(iteratorResult.get("value")).isInstanceOfSatisfying(JSNumber.class, num -> assertThat(num.value()).isEqualTo(3.0));
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.FALSE);

        // Normal case: fourth next() call (done)
        result = GeneratorPrototype.next(context, generator, new JSValue[]{});
        iteratorResult = result.asObject().orElseThrow();
        assertThat(iteratorResult.get("value")).isEqualTo(JSUndefined.INSTANCE);
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.TRUE);

        // Normal case: subsequent calls after done
        result = GeneratorPrototype.next(context, generator, new JSValue[]{});
        iteratorResult = result.asObject().orElseThrow();
        assertThat(iteratorResult.get("value")).isEqualTo(JSUndefined.INSTANCE);
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.TRUE);

        // Normal case: next() with value argument (ignored in this simple implementation)
        result = GeneratorPrototype.next(context, generator, new JSValue[]{new JSString("ignored")});
        iteratorResult = result.asObject().orElseThrow();
        assertThat(iteratorResult.get("value")).isEqualTo(JSUndefined.INSTANCE);
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.TRUE);

        // Edge case: called on non-generator
        result = GeneratorPrototype.next(context, new JSString("not a generator"), new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();

        // Edge case: called on null
        result = GeneratorPrototype.next(context, JSNull.INSTANCE, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testNextAfterDone() {
        assertBooleanWithJavet(
                "function* gen() { yield 1; } var g = gen(); g.next(); g.next(); g.next().value === undefined");
    }

    @Test
    public void testNextValueUndefinedWhenDone() {
        assertBooleanWithJavet(
                "function* gen() { yield 1; } var g = gen(); g.next(); g.next().value === undefined");
    }

    @Test
    public void testReturn() {
        // Create a generator
        JSArray array = new JSArray();
        array.push(new JSNumber(1));
        array.push(new JSNumber(2));
        array.push(new JSNumber(3));
        JSGenerator generator = JSGenerator.fromArray(context, array);

        // Normal case: return with value
        JSValue result = GeneratorPrototype.returnMethod(context, generator, new JSValue[]{new JSString("returned")});
        JSObject iteratorResult = result.asObject().orElseThrow();
        assertThat(iteratorResult.get("value")).isInstanceOfSatisfying(JSString.class, str -> assertThat(str.value()).isEqualTo("returned"));
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.TRUE);

        // Normal case: subsequent next() calls after return
        // QuickJS: completed generator's next() returns {value: undefined, done: true}
        result = GeneratorPrototype.next(context, generator, new JSValue[]{});
        iteratorResult = result.asObject().orElseThrow();
        assertThat(iteratorResult.get("value")).isEqualTo(JSUndefined.INSTANCE);
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.TRUE);

        // Normal case: return without value (undefined)
        JSGenerator generator2 = JSGenerator.fromArray(context, array);
        result = GeneratorPrototype.returnMethod(context, generator2, new JSValue[]{});
        iteratorResult = result.asObject().orElseThrow();
        assertThat(iteratorResult.get("value")).isEqualTo(JSUndefined.INSTANCE);
        assertThat(iteratorResult.get("done")).isEqualTo(JSBoolean.TRUE);

        // Edge case: called on non-generator
        result = GeneratorPrototype.returnMethod(context, new JSObject(), new JSValue[]{new JSNumber(42)});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();

        // Edge case: called on undefined
        result = GeneratorPrototype.returnMethod(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testReturnCompletesGenerator() {
        assertBooleanWithJavet(
                "function* gen() { yield 1; yield 2; } var g = gen(); g.next(); g.return(42).done === true");
    }

    @Test
    public void testReturnValue() {
        assertIntegerWithJavet(
                "function* gen() { yield 1; yield 2; } var g = gen(); g.next(); g.return(42).value");
    }

    @Test
    public void testReturnWithoutValue() {
        assertBooleanWithJavet(
                "function* gen() { yield 1; } var g = gen(); g.return().value === undefined");
    }

    @Test
    public void testSequentialYields() {
        assertIntegerWithJavet(
                "function* gen() { yield 10; yield 20; yield 30; } var g = gen(); g.next(); g.next(); g.next().value");
    }

    @Test
    public void testSpreadOperator() {
        assertIntegerWithJavet(
                "function* gen() { yield 1; yield 2; yield 3; } var arr = [...gen()]; arr.length");
    }

    @Test
    public void testThrow() {
        // Create a generator
        JSArray array = new JSArray();
        array.push(new JSNumber(1));
        array.push(new JSNumber(2));
        JSGenerator generator = JSGenerator.fromArray(context, array);

        // Normal case: throw with exception
        JSValue result = GeneratorPrototype.throwMethod(context, generator, new JSValue[]{new JSString("test exception")});
        // In this simplified implementation, throw completes the generator and returns an error
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isNotNull();
            assertThat(error.get("message")).isNotNull();
        });
        assertThat(context.getPendingException()).isNotNull();

        // Normal case: throw without exception (undefined)
        JSGenerator generator2 = JSGenerator.fromArray(context, array);
        result = GeneratorPrototype.throwMethod(context, generator2, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isNotNull();
            assertThat(error.get("message")).isNotNull();
        });
        assertThat(context.getPendingException()).isNotNull();

        // Edge case: called on non-generator
        result = GeneratorPrototype.throwMethod(context, new JSNumber(123), new JSValue[]{new JSString("error")});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();

        // Edge case: called on null
        result = GeneratorPrototype.throwMethod(context, JSNull.INSTANCE, new JSValue[]{new JSString("error")});
        assertThat(result).isInstanceOfSatisfying(JSObject.class, error -> {
            assertThat(error.get("name")).isInstanceOfSatisfying(JSString.class, name ->
                    assertThat(name.value()).isEqualTo("TypeError"));
        });
        assertThat(context.getPendingException()).isNotNull();
    }

    @Test
    public void testThrowIntoGeneratorCanBeCaught() {
        assertStringWithJavet(
                "function* gen() { try { yield 1; } catch (e) { return 'caught:' + e; } } var g = gen(); g.next(); g.throw('x').value");
    }

    @Test
    public void testThrowOnCompletedGeneratorThrowsRawValue() {
        assertBooleanWithJavet(
                "function* gen() { yield 1; } var g = gen(); g.next(); try { g.throw('z'); false; } catch (e) { e === 'z'; }");
    }

    @Test
    public void testThrowOnSuspendedStartThrowsRawValueAndClosesGenerator() {
        assertBooleanWithJavet(
                "function* gen() { yield 1; } var g = gen(); " +
                        "var ok; try { g.throw('s'); ok = false; } catch (e) { ok = (e === 's'); } " +
                        "ok && g.next().done === true");
    }

    @Test
    public void testYieldExpression() {
        assertIntegerWithJavet(
                "function* gen() { var x = yield 1; yield x + 10; } var g = gen(); g.next().value");
    }

    @Test
    public void testYieldExpressionReceivesNextValue() {
        assertIntegerWithJavet(
                "function* gen() { var x = yield 1; return x + 1; } var g = gen(); g.next(); g.next(41).value");
    }

    @Test
    public void testYieldStarDelegatedIteratorMissingReturnMethodDoesNotAffectNext() {
        assertStringWithJavet(
                """
                        var IsHTMLDDA = {}.IsHTMLDDA;
                        var iter = {
                          [Symbol.iterator]() { return this; },
                          next() { return { a: 1 }; },
                          return: IsHTMLDDA,
                        };
                        var outer = (function* () { yield* iter; })();
                        JSON.stringify(outer.next());""");
    }
}
