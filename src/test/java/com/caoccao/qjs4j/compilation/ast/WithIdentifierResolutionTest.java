package com.caoccao.qjs4j.compilation.ast;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.JSString;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class WithIdentifierResolutionTest extends BaseJavetTest {
    private void assertStringWithTest262Semantics(String expected, String code) {
        // Test262 requires HasProperty in both HasBinding and GetBindingValue.
        // The bundled V8 omits the second check, so it is not the oracle for these cases.
        for (String normalizedCode : getNormalizedCodeStrings(code)) {
            assertThat(resetContext().eval(normalizedCode)).isInstanceOfSatisfying(JSString.class,
                    value -> assertThat(value.value()).isEqualTo(expected));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"direct", "closure", "strictClosure"})
    public void testAbruptGetBindingValueHasProperty(String mode) {
        for (String expression : new String[]{"value", "value()"}) {
            assertStringWithTest262Semantics("true|has,get:Symbol(Symbol.unscopables),has", """
                    (() => {
                      const log = [];
                      const marker = {};
                      let count = 0;
                      const scope = new Proxy({}, {
                        has(target, key) {
                          if (key !== 'value') return false;
                          log.push('has');
                          if (++count === 2) throw marker;
                          return true;
                        },
                        get(target, key) { log.push('get:' + String(key)); }
                      });
                      try { %s; }
                      catch (e) { return (e === marker) + '|' + log.join(','); }
                      return 'missing error';
                    })()
                    """.formatted(access(mode, expression, false)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"direct", "closure", "strictClosure"})
    public void testAbruptUnscopablesLookup(String mode) {
        assertStringWithJavet("""
                (() => {
                  const log = [];
                  const marker = {};
                  const scope = new Proxy({ value: 7 }, {
                    has(target, key) { log.push('has:' + String(key)); return Reflect.has(target, key); },
                    get(target, key) {
                      log.push('get:' + String(key));
                      if (key === Symbol.unscopables) throw marker;
                      return target[key];
                    }
                  });
                  try { %s; }
                  catch (e) { return (e === marker) + '|' + log.join(','); }
                  return 'missing error';
                })()
                """.formatted(access(mode, "value", false)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"direct", "closure", "strictClosure"})
    public void testBindingDeletedByUnscopablesGetter(String mode) {
        for (String expression : new String[]{"value", "value()"}) {
            String result = "strictClosure".equals(mode)
                    ? "ReferenceError|unchanged"
                    : "value".equals(expression) ? "none|undefined" : "TypeError|unchanged";
            assertStringWithTest262Semantics(String.join(";", Collections.nCopies(2, result + "|1")), """
                    (() => [null, {}].map(unscopables => {
                      let count = 0;
                      const outer = { value() { return 'outer'; } };
                      const scope = {
                        value: 7,
                        get [Symbol.unscopables]() {
                          count++;
                          delete this.value;
                          return unscopables;
                        }
                      };
                      let result = 'unchanged';
                      let error = 'none';
                      try { result = String(%s); }
                      catch (e) { error = e.name; }
                      return [error, result, count].join('|');
                    }).join(';'))()
                    """.formatted(access(mode, expression, true)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"direct", "closure"})
    public void testBlockedNestedLookupAndDelete(String mode) {
        assertStringWithJavet(
                """
                        (() => {
                          const outer = { value: 7, method() { return this === outer ? 'outer' : 'wrong'; } };
                          const inner = { value: 9, method() { return 'inner'; },
                            [Symbol.unscopables]: { value: true, method: true } };
                          const log = [];
                          const scope = new Proxy(inner, {
                            has(target, key) { log.push('has:' + String(key)); return Reflect.has(target, key); },
                            get(target, key, receiver) {
                              log.push('get:' + String(key)); return Reflect.get(target, key, receiver);
                            },
                            deleteProperty(target, key) { log.push('delete:' + key); return Reflect.deleteProperty(target, key); }
                          });
                          const read = %s;
                          const call = %s;
                          const deleted = %s;
                          return [read, call, deleted, 'value' in outer, inner.value, log.join(',')].join('|');
                        })()
                        """
                        .formatted(access(mode, "value", true), access(mode, "method()", true),
                                access(mode, "delete value", true)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"direct", "closure", "strictClosure"})
    public void testPropertyDisappearsAfterHasBinding(String mode) {
        for (String expression : new String[]{"value", "value()"}) {
            String result = "strictClosure".equals(mode)
                    ? "ReferenceError"
                    : "value".equals(expression) ? "undefined" : "TypeError";
            String expected = result + "|has,get:Symbol(Symbol.unscopables),has";
            assertStringWithTest262Semantics(expected + ";" + expected, """
                    (() => [undefined, {}].map(unscopables => {
                      const log = [];
                      let count = 0;
                      const scope = new Proxy({}, {
                        has(target, key) {
                          if (key !== 'value') return false;
                          log.push('has'); return ++count === 1;
                        },
                        get(target, key) {
                          log.push('get:' + String(key));
                          return key === Symbol.unscopables ? unscopables : 7;
                        }
                      });
                      let result;
                      try { result = String(%s); }
                      catch (e) { result = e.name; }
                      return result + '|' + log.join(',');
                    }).join(';'))()
                    """.formatted(access(mode, expression, false)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"direct", "closure", "strictClosure"})
    public void testReadAndCallTrapOrderAndReceiver(String mode) {
        String expected = "7:receiver|has:value,get:Symbol(Symbol.unscopables),has:value,get:value,"
                + "has:method,get:Symbol(Symbol.unscopables),has:method,get:method";
        assertStringWithTest262Semantics(String.join(";", Collections.nCopies(5, expected)), """
                (() => [undefined, null, 42, {}, function () {}].map(unscopables => {
                  const log = [];
                  const target = { value: 7, method() { return this === scope ? 'receiver' : 'wrong'; } };
                  const scope = new Proxy(target, {
                    has(target, key) { log.push('has:' + String(key)); return Reflect.has(target, key); },
                    get(target, key, receiver) {
                      log.push('get:' + String(key));
                      return key === Symbol.unscopables ? unscopables : Reflect.get(target, key, receiver);
                    }
                  });
                  const read = %s;
                  const call = %s;
                  return read + ':' + call + '|' + log.join(',');
                }).join(';'))()
                """.formatted(access(mode, "value", false), access(mode, "method()", false)));
    }

    private static String access(String mode, String expression, boolean nested) {
        String body = "return " + expression + ";";
        if (!"direct".equals(mode)) {
            body = "return function () { " + ("strictClosure".equals(mode) ? "'use strict'; " : "") + body + " };";
        }
        body = "with (object) { " + body + " }";
        if (nested) {
            body = "with (outer) { " + body + " }";
        }
        // Function keeps the with statement in sloppy code even when the parity harness is strict.
        return "Function('object', 'outer', \"" + body + "\")(scope, " + (nested ? "outer" : "null") + ")"
                + ("direct".equals(mode) ? "" : "()");
    }
}
