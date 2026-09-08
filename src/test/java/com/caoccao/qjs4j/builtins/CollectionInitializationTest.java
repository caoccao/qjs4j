package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class CollectionInitializationTest extends BaseJavetTest {
    private static Stream<String> collectionNames() {
        return Stream.of("Map", "Set", "WeakMap", "WeakSet");
    }

    @ParameterizedTest
    @MethodSource("collectionNames")
    public void testAdderAndIteratorMethodsAreCapturedOnce(String name) {
        assertStringWithJavet("""
                (() => {
                  const C = %s;
                  const adder = C.name.endsWith('Map') ? 'set' : 'add';
                  const key = {};
                  const log = [];
                  class Derived extends C {}
                  Object.defineProperty(Derived.prototype, adder, { get() {
                    log.push('adder');
                    return function (...args) {
                      log.push(this instanceof Derived ? 'receiver' : 'wrong');
                      return C.prototype[adder].apply(this, args);
                    };
                  } });
                  let count = 0;
                  const iterable = { [Symbol.iterator]() {
                    log.push('iterator');
                    return { get next() {
                      log.push('next getter');
                      return function () {
                        log.push('next');
                        return ++count > 1 ? { done: true }
                          : { done: false, value: adder === 'set' ? [key, 42] : key };
                      };
                    } };
                  } };
                  const collection = new Derived(iterable);
                  return [Object.getPrototypeOf(collection) === Derived.prototype,
                    collection.has(key), adder === 'set' ? collection.get(key) : 'set', log.join(',')].join('|');
                })()
                """.formatted(name));
    }

    @ParameterizedTest
    @MethodSource("collectionNames")
    public void testEmptyInputSkipsAdderAndInvalidAdderSkipsIterator(String name) {
        assertStringWithJavet("""
                (() => {
                  const C = %s;
                  const adder = C.name.endsWith('Map') ? 'set' : 'add';
                  const log = [];
                  class Derived extends C {}
                  Object.defineProperty(Derived.prototype, adder, { get() { log.push('adder'); return 0; } });
                  new Derived(); new Derived(undefined); new Derived(null);
                  const iterable = { get [Symbol.iterator]() { log.push('iterator'); return null; } };
                  try { new Derived(iterable); }
                  catch (e) { return e.name + '|' + log.join(','); }
                  return 'missing error';
                })()
                """.formatted(name));
    }

    @ParameterizedTest
    @MethodSource("collectionNames")
    public void testIteratorClosePreservesAdderError(String name) {
        assertStringWithJavet("""
                (() => {
                  const C = %s;
                  const adder = C.name.endsWith('Map') ? 'set' : 'add';
                  return [false, true].map(throwFromGetter => {
                    const log = [];
                    const marker = {};
                    class Derived extends C {}
                    Derived.prototype[adder] = function () { log.push('adder'); throw marker; };
                    const iterable = { [Symbol.iterator]() { return {
                      next() { return { value: adder === 'set' ? [{}, 1] : {}, done: false }; },
                      get return() {
                        log.push('return getter');
                        if (throwFromGetter) throw new Error('close getter');
                        return function () { log.push('return call'); throw new Error('close call'); };
                      }
                    }; } };
                    try { new Derived(iterable); }
                    catch (e) { return (e === marker) + '|' + log.join(','); }
                    return 'missing error';
                  }).join(';');
                })()
                """.formatted(name));
    }

    @ParameterizedTest
    @MethodSource("collectionNames")
    public void testIteratorStepErrorsDoNotCloseIterator(String name) {
        assertStringWithJavet("""
                (() => {
                  const C = %s;
                  return ['next', 'done', 'value'].map(stage => {
                    const log = [];
                    const marker = {};
                    const iterable = { [Symbol.iterator]() { return {
                      next() {
                        log.push('next');
                        if (stage === 'next') throw marker;
                        return {
                          get done() { log.push('done'); if (stage === 'done') throw marker; return false; },
                          get value() { log.push('value'); throw marker; }
                        };
                      },
                      return() { log.push('return'); return {}; }
                    }; } };
                    try { new C(iterable); }
                    catch (e) { return (e === marker) + '|' + log.join(','); }
                    return 'missing error';
                  }).join(';');
                })()
                """.formatted(name));
    }
}
