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

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Proxy.revocable with working revoke function.
 */
public class ProxyConstructorTest extends BaseTest {

    @Test
    public void testProxyApplyBasic() {
        JSValue result = ctx.eval("""
                var target = function(a, b) { return a + b; };
                var handler = {
                  apply: function(target, thisArg, args) {
                    return target.apply(thisArg, args) * 2;
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy(1, 2)""");
        assertEquals(6.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyApplyForward() {
        JSValue result = ctx.eval("""
                var target = function(a, b) { return a + b; };
                var proxy = new Proxy(target, {});
                proxy(1, 2)""");
        assertEquals(3.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyApplyNonFunction() {
        // Test that apply trap on non-function throws error
        try {
            ctx.eval("""
                    var target = {};
                    var handler = {
                      apply: function(target, thisArg, args) {
                        return 42;
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    proxy()""");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("not a function") ||
                    e.getMessage().contains("TypeError"));
        }
    }

    @Test
    public void testProxyApplyWithThisBinding() {
        // Test that apply trap can modify this binding
        JSValue result = ctx.eval("""
                var target = function() { return this.value; };
                var handler = {
                  apply: function(target, thisArg, args) {
                    return thisArg ? thisArg.value * 2 : 0;
                  }
                };
                var proxy = new Proxy(target, handler);
                var obj = {value: 5};
                proxy.call(obj)""");
        assertEquals(10.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyChainWithMultipleLevels() {
        // Test proxy chain with 3 levels
        JSValue result = ctx.eval("""
                var target = {x: 1};
                var proxy1 = new Proxy(target, {
                  get: function(t, p) { return t[p] + 1; }
                });
                var proxy2 = new Proxy(proxy1, {
                  get: function(t, p) { return t[p] + 1; }
                });
                var proxy3 = new Proxy(proxy2, {
                  get: function(t, p) { return t[p] + 1; }
                });
                proxy3.x""");
        assertEquals(4.0, (Double) result.toJavaObject());  // 1 + 1 + 1 + 1
    }

    @Test
    public void testProxyChaining() {
        // Test proxy of proxy
        JSValue result = ctx.eval("""
                var target = {x: 1};
                var handler1 = {
                  get: function(target, prop) {
                    return target[prop] + 1;
                  }
                };
                var proxy1 = new Proxy(target, handler1);
                var handler2 = {
                  get: function(target, prop) {
                    return target[prop] + 1;
                  }
                };
                var proxy2 = new Proxy(proxy1, handler2);
                proxy2.x""");
        assertEquals(3.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyConstructBasic() {
        JSValue result = ctx.eval("""
                var target = function(x) { this.value = x; };
                var handler = {
                  construct: function(target, args, newTarget) {
                    var obj = Object.create(target.prototype);
                    obj.value = args[0] * 2;
                    return obj;
                  }
                };
                var proxy = new Proxy(target, handler);
                var instance = new proxy(5);
                instance.value""");
        assertEquals(10.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyConstructForward() {
        // Test that construct without trap forwards to target
        JSValue result = ctx.eval("""
                var target = function(x) { this.value = x; };
                var proxy = new Proxy(target, {});
                var instance = new proxy(42);
                instance.value""");
        assertEquals(42.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyConstructNonConstructor() {
        // Test that construct on non-constructor throws
        try {
            ctx.eval("""
                    var target = {}; // Not a constructor
                    var handler = {
                      construct: function(target, args) {
                        return {};
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    new proxy()""");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertEquals("TypeError: proxy is not a constructor", e.getMessage());
        }
    }

    @Test
    public void testProxyConstructNonObject() {
        // Test that construct trap must return an object
        try {
            ctx.eval("""
                    var target = function() {};
                    var handler = {
                      construct: function(target, args, newTarget) {
                        return 42; // Return non-object
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    new proxy()""");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("must return an object") ||
                    e.getMessage().contains("TypeError"));
        }
    }

    @Test
    public void testProxyDefinePropertyBasic() {
        JSValue result = ctx.eval("""
                var target = {};
                var handler = {
                  defineProperty: function(target, prop, descriptor) {
                    Object.defineProperty(target, prop, descriptor);
                    return true;
                  }
                };
                var proxy = new Proxy(target, handler);
                Object.defineProperty(proxy, 'x', {value: 42});
                proxy.x""");
        assertEquals(42.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyDefinePropertyForward() {
        // Test that defineProperty without trap forwards to target
        JSValue result = ctx.eval("""
                var target = {};
                var proxy = new Proxy(target, {});
                Object.defineProperty(proxy, 'x', {value: 42, writable: true});
                proxy.x""");
        assertEquals(42.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyDefinePropertyInvariantNonConfigurableChange() {
        // Test invariant: can't change non-configurable property descriptor
        try {
            ctx.eval("""
                    var target = {};
                    Object.defineProperty(target, 'x', {
                      value: 1,
                      writable: false,
                      configurable: false
                    });
                    var handler = {
                      defineProperty: function(target, prop, descriptor) {
                        return true; // Claim success without matching descriptor
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    Object.defineProperty(proxy, 'x', {value: 2})""");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("inconsistent") ||
                    e.getMessage().contains("TypeError") ||
                    e.getMessage().contains("non-configurable"));
        }
    }

    @Test
    public void testProxyDefinePropertyInvariantNonExtensible() {
        // Test invariant: can't add property to non-extensible target
        try {
            ctx.eval("""
                    var target = {};
                    Object.preventExtensions(target);
                    var handler = {
                      defineProperty: function(target, prop, descriptor) {
                        return true; // Claim success
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    Object.defineProperty(proxy, 'x', {value: 42})""");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("inconsistent") ||
                    e.getMessage().contains("TypeError"));
        }
    }

    @Test
    public void testProxyDefinePropertyWithGetterSetter() {
        // Test defineProperty with getter/setter
        JSValue result = ctx.eval("""
                var target = {};
                var value = 0;
                var handler = {
                  defineProperty: function(target, prop, desc) {
                    Object.defineProperty(target, prop, desc);
                    return true;
                  }
                };
                var proxy = new Proxy(target, handler);
                Object.defineProperty(proxy, 'x', {
                  get: function() { return value; },
                  set: function(v) { value = v; }
                });
                proxy.x = 42;
                proxy.x""");
        assertEquals(42.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyDeletePropertyBasic() {
        JSValue result = ctx.eval("""
                var target = {x: 1};
                var handler = {
                  deleteProperty: function(target, prop) {
                    delete target[prop];
                    return true;
                  }
                };
                var proxy = new Proxy(target, handler);
                delete proxy.x;
                'x' in proxy""");
        assertEquals(false, result.toJavaObject());
    }

    @Test
    public void testProxyDeletePropertyForward() {
        JSValue result = ctx.eval("""
                var target = {x: 1};
                var proxy = new Proxy(target, {});
                delete proxy.x;
                'x' in proxy""");
        assertEquals(false, result.toJavaObject());
    }

    @Test
    public void testProxyDeletePropertyNonConfigurable() {
        // Test invariant: can't delete non-configurable property
        try {
            ctx.eval("""
                    var target = {};
                    Object.defineProperty(target, 'x', {
                      value: 1,
                      configurable: false
                    });
                    var handler = {
                      deleteProperty: function(target, prop) {
                        return true; // Claim success
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    delete proxy.x""");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertEquals("TypeError: 'deleteProperty' on proxy: trap returned truish for property 'x' which is non-configurable in the proxy target", e.getMessage());
        }
    }

    @Test
    public void testProxyDeletePropertyReturningFalse() {
        // Test that deleteProperty trap returning false throws in strict mode
        try {
            ctx.eval("""
                    'use strict';
                    var target = {x: 1};
                    var handler = {
                      deleteProperty: function(target, prop) {
                        return false; // Reject the deletion
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    delete proxy.x""");
            fail("Should have thrown TypeError in strict mode");
        } catch (Exception e) {
            // TypeError: 'deleteProperty' on proxy: trap returned falsish for property 'x'
            assertTrue(e.getMessage().contains("TypeError") ||
                    e.getMessage().contains("returned false") ||
                    e.getMessage().contains("delete"));
        }
    }

    @Test
    public void testProxyGetBasic() {
        JSValue result = ctx.eval("""
                var target = {x: 1};
                var handler = {
                  get: function(target, prop, receiver) {
                    return target[prop] * 2;
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.x""");
        assertEquals(2.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyGetForward() {
        JSValue result = ctx.eval("""
                var target = {x: 1};
                var proxy = new Proxy(target, {});
                proxy.x""");
        assertEquals(1.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyGetInvariantNonConfigurableAccessor() {
        // Test invariant: get must return undefined for non-configurable accessor without getter
        try {
            ctx.eval("""
                    var target = {};
                    Object.defineProperty(target, 'x', {
                      set: function(v) {},
                      configurable: false
                    });
                    var handler = {
                      get: function(target, prop) {
                        return 42; // Return value for accessor without getter
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    proxy.x""");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertEquals("TypeError: 'get' on proxy: property 'x' is a non-configurable accessor property on the proxy target and does not have a getter function, but the trap did not return 'undefined' (got '42')", e.getMessage());
        }
    }

    @Test
    public void testProxyGetInvariantNonWritableNonConfigurable() {
        // Test invariant: must return same value for non-writable, non-configurable property
        try {
            ctx.eval("""
                    var target = {};
                    Object.defineProperty(target, 'x', {
                      value: 1,
                      writable: false,
                      configurable: false
                    });
                    var handler = {
                      get: function(target, prop) {
                        return 2; // Return different value
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    proxy.x""");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertEquals("TypeError: 'get' on proxy: property 'x' is a read-only and non-configurable data property on the proxy target but the proxy did not return its actual", e.getMessage());
        }
    }

    @Test
    public void testProxyGetOwnPropertyDescriptorBasic() {
        JSValue result = ctx.eval("""
                var target = {x: 1};
                var handler = {
                  getOwnPropertyDescriptor: function(target, prop) {
                    return Object.getOwnPropertyDescriptor(target, prop);
                  }
                };
                var proxy = new Proxy(target, handler);
                var desc = Object.getOwnPropertyDescriptor(proxy, 'x');
                desc.value""");
        assertEquals(1.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyGetOwnPropertyDescriptorForward() {
        // Test that getOwnPropertyDescriptor without trap forwards to target
        JSValue result = ctx.eval("""
                var target = {x: 42};
                var proxy = new Proxy(target, {});
                var desc = Object.getOwnPropertyDescriptor(proxy, 'x');
                desc.value""");
        assertEquals(42.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyGetOwnPropertyDescriptorInvariantNonConfigurable() {
        // Test invariant: can't return undefined for non-configurable property
        try {
            ctx.eval(
                    "var target = {}; " +
                            "Object.defineProperty(target, 'x', { " +
                            "  value: 1, " +
                            "  configurable: false " +
                            "}); " +
                            "var handler = { " +
                            "  getOwnPropertyDescriptor: function(target, prop) { " +
                            "    return undefined; " +
                            "  } " +
                            "}; " +
                            "var proxy = new Proxy(target, handler); " +
                            "Object.getOwnPropertyDescriptor(proxy, 'x')"
            );
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("inconsistent") ||
                    e.getMessage().contains("TypeError"));
        }
    }

    @Test
    public void testProxyGetOwnPropertyDescriptorUndefined() {
        JSValue result = ctx.eval(
                "var target = {x: 1}; " +
                        "var handler = { " +
                        "  getOwnPropertyDescriptor: function(target, prop) { " +
                        "    return undefined; " +
                        "  } " +
                        "}; " +
                        "var proxy = new Proxy(target, handler); " +
                        "Object.getOwnPropertyDescriptor(proxy, 'x')"
        );
        assertTrue(result.isUndefined());
    }

    @Test
    public void testProxyGetPrototypeOfBasic() {
        JSValue result = ctx.eval(
                "var proto = {x: 1}; " +
                        "var target = Object.create(proto); " +
                        "var handler = { " +
                        "  getPrototypeOf: function(target) { " +
                        "    return proto; " +
                        "  } " +
                        "}; " +
                        "var proxy = new Proxy(target, handler); " +
                        "Object.getPrototypeOf(proxy).x"
        );
        assertEquals(1.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyGetPrototypeOfForward() {
        // Test that missing trap forwards to target
        JSValue result = ctx.eval(
                "var proto = {x: 1}; " +
                        "var target = Object.create(proto); " +
                        "var proxy = new Proxy(target, {}); " +
                        "Object.getPrototypeOf(proxy).x"
        );
        assertEquals(1.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyGetPrototypeOfInvariant() {
        // Test invariant: if target is non-extensible, trap must return target's prototype
        try {
            ctx.eval("""
                    var proto1 = {x: 1};
                    var proto2 = {x: 2};
                    var target = Object.create(proto1);
                    Object.preventExtensions(target);
                    var handler = {
                      getPrototypeOf: function(target) {
                        return proto2; // Return different prototype
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    Object.getPrototypeOf(proxy)""");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("inconsistent") ||
                    e.getMessage().contains("TypeError"));
        }
    }

    @Test
    public void testProxyGetPrototypeOfNull() {
        // Test that getPrototypeOf can return null
        JSValue result = ctx.eval(
                "var target = Object.create(null); " +
                        "var handler = { " +
                        "  getPrototypeOf: function(target) { " +
                        "    return null; " +
                        "  } " +
                        "}; " +
                        "var proxy = new Proxy(target, handler); " +
                        "Object.getPrototypeOf(proxy)"
        );
        assertTrue(result.isNull());
    }

    @Test
    public void testProxyHasBasic() {
        JSValue result = ctx.eval("""
                var target = {x: 1};
                var handler = {
                  has: function(target, prop) {
                    return prop in target;
                  }
                };
                var proxy = new Proxy(target, handler);
                'x' in proxy""");
        assertEquals(true, result.toJavaObject());
    }

    @Test
    public void testProxyHasForward() {
        JSValue result = ctx.eval("""
                var target = {x: 1};
                var proxy = new Proxy(target, {});
                'x' in proxy""");
        assertEquals(true, result.toJavaObject());
    }

    @Test
    public void testProxyHasInvariantNonConfigurable() {
        // Test invariant: must report non-configurable property as present
        try {
            ctx.eval("""
                    var target = {};
                    Object.defineProperty(target, 'x', {
                      value: 1,
                      configurable: false
                    });
                    var handler = {
                      has: function(target, prop) {
                        return false; // Hide non-configurable property
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    'x' in proxy""");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertEquals("TypeError: 'has' on proxy: trap returned falsish for property 'x' which exists in the proxy target as non-configurable", e.getMessage());
        }
    }

    @Test
    public void testProxyHasInvariantNonExtensible() {
        // Test invariant: must report all properties on non-extensible target
        try {
            ctx.eval("""
                    var target = {x: 1};
                    Object.preventExtensions(target);
                    var handler = {
                      has: function(target, prop) {
                        return false; // Hide existing property
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    'x' in proxy""");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertEquals("TypeError: 'has' on proxy: trap returned falsish for property 'x' but the proxy target is not extensible", e.getMessage());
        }
    }

    @Test
    public void testProxyInPrototypeChain() {
        // Test proxy used in prototype chain
        JSValue result = ctx.eval("""
                var proto = {x: 1};
                var handler = {
                  get: function(target, prop) {
                    return target[prop] * 2;
                  }
                };
                var proxy = new Proxy(proto, handler);
                var obj = Object.create(proxy);
                obj.x""");
        assertEquals(2.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyIsExtensibleBasic() {
        JSValue result = ctx.eval("""
                var target = {};
                var handler = {
                  isExtensible: function(target) {
                    return Object.isExtensible(target);
                  }
                };
                var proxy = new Proxy(target, handler);
                Object.isExtensible(proxy)""");
        assertEquals(true, result.toJavaObject());
    }

    @Test
    public void testProxyIsExtensibleForward() {
        // Test that isExtensible without trap forwards to target
        JSValue result = ctx.eval("""
                var target = {};
                var proxy = new Proxy(target, {});
                Object.isExtensible(proxy)""");
        assertEquals(true, result.toJavaObject());
    }

    @Test
    public void testProxyIsExtensibleInvariant() {
        // Test invariant: trap result must match target's extensibility
        try {
            ctx.eval("""
                    var target = {};
                    var handler = {
                      isExtensible: function(target) {
                        return false; // Lie about extensibility
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    Object.isExtensible(proxy)""");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("inconsistent") ||
                    e.getMessage().contains("TypeError"));
        }
    }

    @Test
    public void testProxyMultipleTraps() {
        JSValue result = ctx.eval("""
                var target = {x: 1};
                var getCalled = false;
                var setCalled = false;
                var handler = {
                  get: function(target, prop) {
                    getCalled = true;
                    return target[prop];
                  },
                  set: function(target, prop, value) {
                    setCalled = true;
                    target[prop] = value;
                    return true;
                  }
                };
                var proxy = new Proxy(target, handler);
                var val = proxy.x;
                proxy.y = 2;
                getCalled && setCalled""");
        assertEquals(true, result.toJavaObject());
    }

    @Test
    public void testProxyNestedRevocation() {
        // Test that revoking outer proxy doesn't affect inner proxy
        JSValue result = ctx.eval("""
                var target = {x: 1};
                var {proxy: inner, revoke: revokeInner} = Proxy.revocable(target, {});
                var {proxy: outer, revoke: revokeOuter} = Proxy.revocable(inner, {});
                revokeOuter();
                inner.x""");
        assertEquals(1.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyOwnKeysBasic() {
        JSValue result = ctx.eval("""
                var target = {x: 1, y: 2};
                var handler = {
                  ownKeys: function(target) {
                    return ['x', 'y', 'z'];
                  }
                };
                var proxy = new Proxy(target, handler);
                Object.keys(proxy).length""");
        assertEquals(3.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyOwnKeysForward() {
        JSValue result = ctx.eval("""
                var target = {x: 1, y: 2};
                var proxy = new Proxy(target, {});
                Object.keys(proxy).length""");
        assertEquals(2.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyOwnKeysInvariantDuplicates() {
        // Test invariant: ownKeys result can't have duplicates
        try {
            ctx.eval("""
                    var target = {x: 1};
                    var handler = {
                      ownKeys: function(target) {
                        return ['x', 'x']; // Duplicate property
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    Object.keys(proxy)""");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            // TypeError: 'ownKeys' on proxy: trap returned duplicate entries
            assertTrue(e.getMessage().contains("duplicate") ||
                    e.getMessage().contains("TypeError"));
        }
    }

    @Test
    public void testProxyOwnKeysInvariantNonExtensible() {
        // Test invariant: ownKeys must include all non-configurable properties
        try {
            ctx.eval("""
                    var target = {};
                    Object.defineProperty(target, 'x', {
                      value: 1,
                      configurable: false
                    });
                    var handler = {
                      ownKeys: function(target) {
                        return []; // Omit non-configurable property
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    Object.keys(proxy)""");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertEquals("TypeError: 'ownKeys' on proxy: trap result did not include 'x'", e.getMessage());
        }
    }

    @Test
    public void testProxyOwnKeysWithSymbols() {
        // Test that ownKeys can return symbols
        JSValue result = ctx.eval("""
                var sym1 = Symbol('a');
                var sym2 = Symbol('b');
                var target = {x: 1};
                target[sym1] = 2;
                var handler = {
                  ownKeys: function(target) {
                    return ['x', sym1, sym2];
                  }
                };
                var proxy = new Proxy(target, handler);
                Object.getOwnPropertySymbols(proxy).length""");
        // Should return 2 symbols (sym1 and sym2)
        assertEquals(2.0, (Double) result.toJavaObject());
    }

    // ============================================================
    // Additional invariant and edge case tests
    // ============================================================

    @Test
    public void testProxyPreventExtensionsBasic() {
        JSValue result = ctx.eval("""
                var target = {};
                var handler = {
                  preventExtensions: function(target) {
                    Object.preventExtensions(target);
                    return true;
                  }
                };
                var proxy = new Proxy(target, handler);
                Object.preventExtensions(proxy);
                Object.isExtensible(proxy)""");
        assertEquals(false, result.toJavaObject());
    }

    @Test
    public void testProxyPreventExtensionsForward() {
        // Test that preventExtensions without trap forwards to target
        JSValue result = ctx.eval("""
                var target = {};
                var proxy = new Proxy(target, {});
                Object.preventExtensions(proxy);
                Object.isExtensible(proxy)""");
        assertEquals(false, result.toJavaObject());
    }

    @Test
    public void testProxyPreventExtensionsInvariant() {
        // Test invariant: if trap returns true, target must be non-extensible
        try {
            ctx.eval("""
                    var target = {};
                    var handler = {
                      preventExtensions: function(target) {
                        return true;
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    Object.preventExtensions(proxy)""");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertEquals("TypeError: 'preventExtensions' on proxy: trap returned truish but the proxy target is extensible", e.getMessage());
        }
    }

    @Test
    public void testProxyPreventExtensionsReturningFalse() {
        // Test that preventExtensions trap can return false
        try {
            ctx.eval("""
                    var target = {};
                    var handler = {
                      preventExtensions: function(target) {
                        return false;
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    Object.preventExtensions(proxy)""");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertEquals("TypeError: 'preventExtensions' on proxy: trap returned falsish", e.getMessage());
        }
    }

    @Test
    public void testProxyReceiverInGet() {
        // Test that get trap receives correct receiver
        JSValue result = ctx.eval("""
                var target = {x: 1};
                var handler = {
                  get: function(target, prop, receiver) {
                    return receiver === proxy ? 'correct' : 'wrong';
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.x""");
        assertEquals("correct", result.toJavaObject());
    }

    @Test
    public void testProxyReceiverInSet() {
        // Test that set trap receives correct receiver
        JSValue result = ctx.eval("""
                var target = {};
                var handler = {
                  set: function(target, prop, value, receiver) {
                    if (receiver === proxy) {
                      target.result = 'correct';
                    }
                    return true;
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.x = 1;
                target.result""");
        assertEquals("correct", result.toJavaObject());
    }

    @Test
    public void testProxyRevocableAccessAfterRevoke() {
        // Test that accessing revoked proxy throws TypeError
        ctx.eval("""
                var target = {x: 1};
                var handler = {};
                var {proxy, revoke} = Proxy.revocable(target, handler);
                proxy.x; // Works before revoke
                revoke();""");
        // Try to access revoked proxy - should throw TypeError
        try {
            ctx.eval("proxy.x");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("revoked proxy") ||
                    e.getMessage().contains("TypeError"));
        }
    }

    @Test
    public void testProxyRevocableAccessBeforeRevoke() {
        // Test that proxy works normally before revocation
        JSValue result = ctx.eval("""
                var target = {x: 1};
                var handler = {};
                var {proxy, revoke} = Proxy.revocable(target, handler);
                proxy.x""");
        // Proxy access returns the value
        assertEquals(1.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyRevocableBasic() {
        // Test that Proxy.revocable returns an object with proxy and revoke
        JSValue result = ctx.eval("""
                var target = {x: 1};
                var handler = {};
                var revocable = Proxy.revocable(target, handler);
                typeof revocable""");
        assertEquals("object", result.toJavaObject());

        result = ctx.eval("typeof revocable.proxy");
        assertEquals("object", result.toJavaObject());

        result = ctx.eval("typeof revocable.revoke");
        assertEquals("function", result.toJavaObject());
    }

    @Test
    public void testProxyRevocableRevokeMultipleTimes() {
        // Test that calling revoke multiple times doesn't cause issues
        JSValue result = ctx.eval("""
                var target = {x: 1};
                var handler = {};
                var {proxy, revoke} = Proxy.revocable(target, handler);
                revoke();
                revoke(); // Call revoke again
                'ok'""");
        assertEquals("ok", result.toJavaObject());
    }

    @Test
    public void testProxyRevocableSetAfterRevoke() {
        // Test that setting on revoked proxy throws TypeError
        ctx.eval("""
                var target = {x: 1};
                var handler = {};
                var {proxy, revoke} = Proxy.revocable(target, handler);
                revoke();""");

        // Try to set on revoked proxy - should throw TypeError
        try {
            ctx.eval("proxy.y = 2");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("revoked proxy") ||
                    e.getMessage().contains("TypeError"));
        }
    }

    @Test
    public void testProxySetBasic() {
        JSValue result = ctx.eval("""
                var target = {};
                var handler = {
                  set: function(target, prop, value, receiver) {
                    target[prop] = value * 2;
                    return true;
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.x = 5;
                proxy.x""");
        assertEquals(10.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxySetForward() {
        JSValue result = ctx.eval("""
                var target = {};
                var proxy = new Proxy(target, {});
                proxy.x = 42;
                proxy.x""");
        assertEquals(42.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxySetInvariantNonWritable() {
        // Test invariant: can't change non-writable property
        JSValue result = ctx.eval("""
                var target = {};
                Object.defineProperty(target, 'x', {
                  value: 1,
                  writable: false,
                  configurable: true
                });
                var handler = {
                  set: function(target, prop, value) {
                    return true;
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.x = 2;
                proxy.x""");
        assertEquals(1.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxySetPrototypeOfBasic() {
        JSValue result = ctx.eval("""
                var newProto = {x: 2};
                var target = {y: 1};
                var handler = {
                  setPrototypeOf: function(target, proto) {
                    Object.setPrototypeOf(target, proto);
                    return true;
                  }
                };
                var proxy = new Proxy(target, handler);
                Object.setPrototypeOf(proxy, newProto);
                Object.getPrototypeOf(proxy).x""");
        assertEquals(2.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxySetPrototypeOfForward() {
        // Test that setPrototypeOf without trap forwards to target
        JSValue result = ctx.eval("""
                var newProto = {x: 42};
                var target = {};
                var proxy = new Proxy(target, {});
                Object.setPrototypeOf(proxy, newProto);
                Object.getPrototypeOf(proxy).x""");
        assertEquals(42.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxySetPrototypeOfInvariant() {
        // Test invariant: if target is non-extensible, can't change prototype
        try {
            ctx.eval("""
                    var proto1 = {x: 1};
                    var proto2 = {x: 2};
                    var target = Object.create(proto1);
                    Object.preventExtensions(target);
                    var handler = {
                      setPrototypeOf: function(target, proto) {
                        return true; // Claim success without changing
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    Object.setPrototypeOf(proxy, proto2)""");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("inconsistent") ||
                    e.getMessage().contains("TypeError"));
        }
    }

    @Test
    public void testProxySetPrototypeOfReturningFalse() {
        // Test that setPrototypeOf trap can return false
        try {
            ctx.eval("""
                    'use strict';
                    var target = {};
                    var handler = {
                      setPrototypeOf: function(target, proto) {
                        return false; // Refuse to set prototype
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    Object.setPrototypeOf(proxy, {})""");
            fail("Should have thrown TypeError in strict mode");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("TypeError") ||
                    e.getMessage().contains("returned false"));
        }
    }

    @Test
    public void testProxySetReturningFalse() {
        // Test that set trap returning false throws in strict mode
        try {
            // Reject the assignment
            ctx.eval("""
                    'use strict';
                    var target = {};
                    var handler = {
                      set: function(target, prop, value) {
                        return false;
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    proxy.x = 1""");
            fail("Should have thrown TypeError in strict mode");
        } catch (Exception e) {
            assertEquals("TypeError: 'set' on proxy: trap returned falsish for property 'x'", e.getMessage());
        }
    }

    @Test
    public void testProxyThrowingTrap() {
        // Test that trap can throw custom error
        try {
            ctx.eval("""
                    var target = {x: 1};
                    var handler = {
                      get: function(target, prop) {
                        throw new Error('custom error');
                      }
                    };
                    var proxy = new Proxy(target, handler);
                    proxy.x""");
            fail("Should have thrown custom error");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("custom error"));
        }
    }

    @Test
    public void testProxyTrapWithNonCallableHandler() {
        // Test that non-callable trap throws TypeError
        try {
            // Not a function
            ctx.eval("""
                    var target = {x: 1};
                    var handler = {
                      get: 42
                    };
                    var proxy = new Proxy(target, handler);
                    proxy.x""");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("not a function") ||
                    e.getMessage().contains("TypeError") ||
                    e.getMessage().contains("callable"));
        }
    }

    @Test
    public void testProxyWithArrayLikeObject() {
        // Test proxy with array-like object (has length property)
        JSValue result = ctx.eval("""
                var target = {0: 'a', 1: 'b', 2: 'c', length: 3};
                var handler = {
                  get: function(target, prop) {
                    return target[prop];
                  }
                };
                var proxy = new Proxy(target, handler);
                Array.prototype.join.call(proxy, ',')""");
        assertEquals("a,b,c", result.toJavaObject());
    }

    @Test
    public void testProxyWithBooleanObjectAsTarget() {
        // Test that Boolean object (new Boolean(true)) can be a proxy target
        // Boolean objects are needed as proxy targets since primitive booleans cannot be proxied
        JSValue result = ctx.eval("""
                var target = new Boolean(true);
                var handler = {
                  get: function(target, prop) {
                    if (prop === 'test') {
                      return 'intercepted';
                    }
                    return target[prop];
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.test""");
        assertEquals("intercepted", result.toJavaObject());
    }

    @Test
    public void testProxyWithBooleanObjectToString() {
        // Test that proxied Boolean object still works with toString
        JSValue result = ctx.eval("""
                var target = new Boolean(false);
                var handler = {};
                var proxy = new Proxy(target, handler);
                proxy.toString()""");
        assertEquals("false", result.toJavaObject());
    }

    @Test
    public void testProxyWithBooleanObjectTrapGet() {
        // Test that get trap intercepts valueOf on Boolean object
        JSValue result = ctx.eval("""
                var target = new Boolean(true);
                var handler = {
                  get: function(target, prop) {
                    if (prop === 'valueOf') {
                      return function() { return false; };
                    }
                    return target[prop];
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.valueOf()""");
        assertFalse((Boolean) result.toJavaObject());
    }

    @Test
    public void testProxyWithBooleanObjectValueOf() {
        // Test that proxied Boolean object still works with valueOf
        JSValue result = ctx.eval("""
                var target = new Boolean(true);
                var handler = {};
                var proxy = new Proxy(target, handler);
                proxy.valueOf()""");
        assertTrue((Boolean) result.toJavaObject());
    }

    @Test
    public void testProxyWithNullPrototype() {
        // Test proxy with null prototype target
        JSValue result = ctx.eval("""
                var target = Object.create(null);
                target.x = 42;
                var handler = {
                  get: function(target, prop) {
                    return target[prop];
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.x""");
        assertEquals(42.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyWithNumberObjectAsTarget() {
        // Test that Number object (new Number(42)) can be a proxy target
        // Number objects are needed as proxy targets since primitive numbers cannot be proxied
        JSValue result = ctx.eval("""
                var target = new Number(42);
                var handler = {
                  get: function(target, prop) {
                    if (prop === 'test') {
                      return 'intercepted';
                    }
                    return target[prop];
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.test""");
        assertEquals("intercepted", result.toJavaObject());
    }

    @Test
    public void testProxyWithNumberObjectSpecialValues() {
        // Test that Number object with NaN can be proxied
        JSValue resultNaN = ctx.eval("""
                var target = new Number(NaN);
                var handler = {};
                var proxy = new Proxy(target, handler);
                proxy.valueOf()""");
        assertTrue(Double.isNaN((Double) resultNaN.toJavaObject()));

        // Test that Number object with Infinity can be proxied
        JSValue resultInf = ctx.eval("""
                var target = new Number(Infinity);
                var handler = {};
                var proxy = new Proxy(target, handler);
                proxy.valueOf()""");
        assertEquals(Double.POSITIVE_INFINITY, (Double) resultInf.toJavaObject());
    }

    @Test
    public void testProxyWithNumberObjectToString() {
        // Test that proxied Number object still works with toString
        JSValue result = ctx.eval("""
                var target = new Number(42);
                var handler = {};
                var proxy = new Proxy(target, handler);
                proxy.toString()""");
        assertEquals("42", result.toJavaObject());
    }

    @Test
    public void testProxyWithNumberObjectTrapGet() {
        // Test that get trap intercepts valueOf on Number object
        JSValue result = ctx.eval("""
                var target = new Number(100);
                var handler = {
                  get: function(target, prop) {
                    if (prop === 'valueOf') {
                      return function() { return 999; };
                    }
                    return target[prop];
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.valueOf()""");
        assertEquals(999.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyWithNumberObjectValueOf() {
        // Test that proxied Number object still works with valueOf
        JSValue result = ctx.eval("""
                var target = new Number(3.14);
                var handler = {};
                var proxy = new Proxy(target, handler);
                proxy.valueOf()""");
        assertEquals(3.14, (Double) result.toJavaObject(), 0.001);
    }

    @Test
    public void testProxyWithNumericProperties() {
        // Test that proxy works with object having numeric property names
        JSValue result = ctx.eval("""
                var target = {};
                target['0'] = 1;
                target['1'] = 2;
                var handler = {
                  get: function(target, prop, receiver) {
                    var val = target[prop];
                    return val !== undefined ? val * 2 : undefined;
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy['0']""");
        assertEquals(2.0, (Double) result.toJavaObject());  // 1 * 2
    }

    @Test
    public void testProxyWithSymbolProperty() {
        // Test that proxy works with symbol properties
        JSValue result = ctx.eval("""
                var sym = Symbol('test');
                var target = {};
                target[sym] = 42;
                var handler = {
                  get: function(target, prop) {
                    return target[prop];
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy[sym]""");
        assertEquals(42.0, (Double) result.toJavaObject());
    }
}
