package com.caoccao.qjs4j.compilation.ast;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

import java.util.Collections;

public class WithIdentifierResolutionTest extends BaseJavetTest {
    private void assertStringWithTest262Semantics(String expected, String... scripts) {
        // Test262 requires HasProperty in both HasBinding and GetBindingValue.
        // The bundled V8 omits the second check, so it is not the oracle for these cases.
        for (String code : scripts) {
            assertStringValue(resetContext().eval(code), expected);
            assertStringValue(resetContext().eval("'use strict';\n" + code), expected);
        }
    }

    @Test
    public void testAbruptGetBindingValueHasProperty() {
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
                  try { Function('object', 'outer', "with (object) { return value; }")(scope, null); }
                  catch (e) { return (e === marker) + '|' + log.join(','); }
                  return 'missing error';
                })()
                """, """
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
                  try { Function('object', 'outer', "with (object) { return value(); }")(scope, null); }
                  catch (e) { return (e === marker) + '|' + log.join(','); }
                  return 'missing error';
                })()
                """,
                """
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
                          try { Function('object', 'outer', "with (object) { return function () { return value; }; }")(scope, null)(); }
                          catch (e) { return (e === marker) + '|' + log.join(','); }
                          return 'missing error';
                        })()
                        """,
                """
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
                          try { Function('object', 'outer', "with (object) { return function () { return value(); }; }")(scope, null)(); }
                          catch (e) { return (e === marker) + '|' + log.join(','); }
                          return 'missing error';
                        })()
                        """,
                """
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
                          try { Function('object', 'outer', "with (object) { return function () { 'use strict'; return value; }; }")(scope, null)(); }
                          catch (e) { return (e === marker) + '|' + log.join(','); }
                          return 'missing error';
                        })()
                        """,
                """
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
                          try { Function('object', 'outer', "with (object) { return function () { 'use strict'; return value(); }; }")(scope, null)(); }
                          catch (e) { return (e === marker) + '|' + log.join(','); }
                          return 'missing error';
                        })()
                        """);
    }

    @Test
    public void testAbruptUnscopablesLookup() {
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
                  try { Function('object', 'outer', "with (object) { return value; }")(scope, null); }
                  catch (e) { return (e === marker) + '|' + log.join(','); }
                  return 'missing error';
                })()
                """,
                """
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
                          try { Function('object', 'outer', "with (object) { return function () { return value; }; }")(scope, null)(); }
                          catch (e) { return (e === marker) + '|' + log.join(','); }
                          return 'missing error';
                        })()
                        """,
                """
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
                          try { Function('object', 'outer', "with (object) { return function () { 'use strict'; return value; }; }")(scope, null)(); }
                          catch (e) { return (e === marker) + '|' + log.join(','); }
                          return 'missing error';
                        })()
                        """);
    }

    @Test
    public void testBindingDeletedByUnscopablesGetter() {
        assertStringWithTest262Semantics("none|undefined|1;none|undefined|1",
                """
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
                          try { result = String(Function('object', 'outer', "with (outer) { with (object) { return value; } }")(scope, outer)); }
                          catch (e) { error = e.name; }
                          return [error, result, count].join('|');
                        }).join(';'))()
                        """);
        assertStringWithTest262Semantics("TypeError|unchanged|1;TypeError|unchanged|1",
                """
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
                          try { result = String(Function('object', 'outer', "with (outer) { with (object) { return value(); } }")(scope, outer)); }
                          catch (e) { error = e.name; }
                          return [error, result, count].join('|');
                        }).join(';'))()
                        """);
        assertStringWithTest262Semantics("none|undefined|1;none|undefined|1",
                """
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
                          try { result = String(Function('object', 'outer', "with (outer) { with (object) { return function () { return value; }; } }")(scope, outer)()); }
                          catch (e) { error = e.name; }
                          return [error, result, count].join('|');
                        }).join(';'))()
                        """);
        assertStringWithTest262Semantics("TypeError|unchanged|1;TypeError|unchanged|1",
                """
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
                          try { result = String(Function('object', 'outer', "with (outer) { with (object) { return function () { return value(); }; } }")(scope, outer)()); }
                          catch (e) { error = e.name; }
                          return [error, result, count].join('|');
                        }).join(';'))()
                        """);
        assertStringWithTest262Semantics("ReferenceError|unchanged|1;ReferenceError|unchanged|1",
                """
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
                          try { result = String(Function('object', 'outer', "with (outer) { with (object) { return function () { 'use strict'; return value; }; } }")(scope, outer)()); }
                          catch (e) { error = e.name; }
                          return [error, result, count].join('|');
                        }).join(';'))()
                        """,
                """
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
                          try { result = String(Function('object', 'outer', "with (outer) { with (object) { return function () { 'use strict'; return value(); }; } }")(scope, outer)()); }
                          catch (e) { error = e.name; }
                          return [error, result, count].join('|');
                        }).join(';'))()
                        """);
    }

    @Test
    public void testBlockedNestedLookupAndDelete() {
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
                          const read = Function('object', 'outer', "with (outer) { with (object) { return value; } }")(scope, outer);
                          const call = Function('object', 'outer', "with (outer) { with (object) { return method(); } }")(scope, outer);
                          const deleted = Function('object', 'outer', "with (outer) { with (object) { return delete value; } }")(scope, outer);
                          return [read, call, deleted, 'value' in outer, inner.value, log.join(',')].join('|');
                        })()
                        """,
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
                          const read = Function('object', 'outer', "with (outer) { with (object) { return function () { return value; }; } }")(scope, outer)();
                          const call = Function('object', 'outer', "with (outer) { with (object) { return function () { return method(); }; } }")(scope, outer)();
                          const deleted = Function('object', 'outer', "with (outer) { with (object) { return function () { return delete value; }; } }")(scope, outer)();
                          return [read, call, deleted, 'value' in outer, inner.value, log.join(',')].join('|');
                        })()
                        """);
    }

    @Test
    public void testPropertyDisappearsAfterHasBinding() {
        assertStringWithTest262Semantics(
                "undefined|has,get:Symbol(Symbol.unscopables),has;undefined|has,get:Symbol(Symbol.unscopables),has",
                """
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
                          try { result = String(Function('object', 'outer', "with (object) { return value; }")(scope, null)); }
                          catch (e) { result = e.name; }
                          return result + '|' + log.join(',');
                        }).join(';'))()
                        """);
        assertStringWithTest262Semantics(
                "TypeError|has,get:Symbol(Symbol.unscopables),has;TypeError|has,get:Symbol(Symbol.unscopables),has",
                """
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
                          try { result = String(Function('object', 'outer', "with (object) { return value(); }")(scope, null)); }
                          catch (e) { result = e.name; }
                          return result + '|' + log.join(',');
                        }).join(';'))()
                        """);
        assertStringWithTest262Semantics(
                "undefined|has,get:Symbol(Symbol.unscopables),has;undefined|has,get:Symbol(Symbol.unscopables),has",
                """
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
                          try { result = String(Function('object', 'outer', "with (object) { return function () { return value; }; }")(scope, null)()); }
                          catch (e) { result = e.name; }
                          return result + '|' + log.join(',');
                        }).join(';'))()
                        """);
        assertStringWithTest262Semantics(
                "TypeError|has,get:Symbol(Symbol.unscopables),has;TypeError|has,get:Symbol(Symbol.unscopables),has",
                """
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
                          try { result = String(Function('object', 'outer', "with (object) { return function () { return value(); }; }")(scope, null)()); }
                          catch (e) { result = e.name; }
                          return result + '|' + log.join(',');
                        }).join(';'))()
                        """);
        assertStringWithTest262Semantics(
                "ReferenceError|has,get:Symbol(Symbol.unscopables),has;ReferenceError|has,get:Symbol(Symbol.unscopables),has",
                """
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
                          try { result = String(Function('object', 'outer', "with (object) { return function () { 'use strict'; return value; }; }")(scope, null)()); }
                          catch (e) { result = e.name; }
                          return result + '|' + log.join(',');
                        }).join(';'))()
                        """,
                """
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
                          try { result = String(Function('object', 'outer', "with (object) { return function () { 'use strict'; return value(); }; }")(scope, null)()); }
                          catch (e) { result = e.name; }
                          return result + '|' + log.join(',');
                        }).join(';'))()
                        """);
    }

    @Test
    public void testReadAndCallTrapOrderAndReceiver() {
        String trace = "7:receiver|has:value,get:Symbol(Symbol.unscopables),has:value,get:value,"
                + "has:method,get:Symbol(Symbol.unscopables),has:method,get:method";
        String expected = String.join(";", Collections.nCopies(5, trace));
        assertStringWithTest262Semantics(expected, """
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
                  const read = Function('object', 'outer', "with (object) { return value; }")(scope, null);
                  const call = Function('object', 'outer', "with (object) { return method(); }")(scope, null);
                  return read + ':' + call + '|' + log.join(',');
                }).join(';'))()
                """,
                """
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
                          const read = Function('object', 'outer', "with (object) { return function () { return value; }; }")(scope, null)();
                          const call = Function('object', 'outer', "with (object) { return function () { return method(); }; }")(scope, null)();
                          return read + ':' + call + '|' + log.join(',');
                        }).join(';'))()
                        """,
                """
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
                          const read = Function('object', 'outer', "with (object) { return function () { 'use strict'; return value; }; }")(scope, null)();
                          const call = Function('object', 'outer', "with (object) { return function () { 'use strict'; return method(); }; }")(scope, null)();
                          return read + ':' + call + '|' + log.join(',');
                        }).join(';'))()
                        """);
    }

}
