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
 * Unit tests for Proxy.revocable with working revoke function.
 */
public class ProxyConstructorTest extends BaseJavetTest {

    @Test
    public void testCallApplyOnFunctionArg() {
        // Test calling .apply() on a function argument
        assertIntegerWithJavet("""
                function outer(fn, args) {
                    return fn.apply(null, args);
                }
                var target = function(a, b) { return a + b; };
                outer(target, [1, 2])""");
    }

    @Test
    public void testCallWithUndefined() {
        // When callee is undefined, what error do we get?
        assertErrorWithJavet("""
                var x = undefined;
                x(1, 2)""");
    }

    @Test
    public void testFunctionApply() {
        // Test that Function.prototype.apply works directly
        assertIntegerWithJavet("""
                var fn = function(a, b) { return a + b; };
                fn.apply(null, [1, 2])""");
    }

    @Test
    public void testFunctionApplyDirectly() {
        // Test apply works on its own
        assertIntegerWithJavet("""
                var fn = function(a, b) { return a + b; };
                fn.apply(null, [1, 2])""");
    }

    @Test
    public void testFunctionHasApply() {
        // Test that a simple function has apply
        assertBooleanWithJavet("""
                var fn = function(a, b) { return a + b; };
                typeof fn.apply === 'function'""");
    }

    @Test
    public void testGetFieldOnFunctionArg() {
        // Test accessing a property on a function argument
        assertBooleanWithJavet("""
                function outer(fn) {
                    return typeof fn.apply === 'function';
                }
                var target = function(a, b) { return a + b; };
                outer(target)""");
    }

    @Test
    public void testNestedFunctionApply() {
        // Simulate what the apply trap does manually
        assertIntegerWithJavet("""
                var target = function(a, b) { return a + b; };
                var handler = {
                  apply: function(t, thisArg, args) {
                    // Just call t directly
                    return t(args[0], args[1]) * 2;
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy(1, 2)""");
    }

    @Test
    public void testProxyApplyBasic() {
        assertIntegerWithJavet("""
                var target = function(a, b) { return a + b; };
                var handler = {
                  apply: function(target, thisArg, args) {
                    return target.apply(thisArg, args) * 2;
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy(1, 2)""");
    }

    @Test
    public void testProxyApplyForward() {
        assertIntegerWithJavet("""
                var target = function(a, b) { return a + b; };
                var proxy = new Proxy(target, {});
                proxy(1, 2)""");
    }

    @Test
    public void testProxyApplyNonFunction() {
        // Test that apply trap on non-function throws error
        assertErrorWithJavet("""
                var target = {};
                var handler = {
                  apply: function(target, thisArg, args) {
                    return 42;
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy()""");
    }

    @Test
    public void testProxyApplyWithDirectCall() {
        // The actual failing test
        assertIntegerWithJavet("""
                var target = function(a, b) { return a + b; };
                var handler = {
                  apply: function(target, thisArg, args) {
                    return target.apply(thisArg, args) * 2;
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy(1, 2)""");
    }

    @Test
    public void testProxyApplyWithExplicitLog() {
        // Test with logging to understand what's happening
        assertIntegerWithJavet("""
                var target = function(a, b) { return a + b; };
                var handler = {
                  apply: function(t, thisArg, args) {
                    // Log to understand what t is
                    if (typeof t !== 'function') {
                      throw new Error('target is not a function, it is: ' + typeof t);
                    }
                    // Try to access apply on t
                    if (typeof t.apply !== 'function') {
                      throw new Error('t.apply is not a function, it is: ' + typeof t.apply);
                    }
                    return t.apply(thisArg, args) * 2;
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy(1, 2)""");
    }

    @Test
    public void testProxyApplyWithThisBinding() {
        // Test that apply trap can modify this binding
        assertIntegerWithJavet("""
                var target = function() { return this.value; };
                var handler = {
                  apply: function(target, thisArg, args) {
                    return thisArg ? thisArg.value * 2 : 0;
                  }
                };
                var proxy = new Proxy(target, handler);
                var obj = {value: 5};
                proxy.call(obj)""");
    }

    @Test
    public void testProxyChainWithMultipleLevels() {
        // Test proxy chain with 3 levels
        assertIntegerWithJavet("""
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
    }

    @Test
    public void testProxyChaining() {
        // Test proxy of proxy
        assertIntegerWithJavet("""
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
    }

    @Test
    public void testProxyConstructBasic() {
        assertIntegerWithJavet("""
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
    }

    @Test
    public void testProxyConstructForward() {
        // Test that construct without trap forwards to target
        assertIntegerWithJavet("""
                var target = function(x) { this.value = x; };
                var proxy = new Proxy(target, {});
                var instance = new proxy(42);
                instance.value""");
    }

    @Test
    public void testProxyConstructNonConstructor() {
        // Test that construct on non-constructor throws
        assertErrorWithJavet("""
                var target = {}; // Not a constructor
                var handler = {
                  construct: function(target, args) {
                    return {};
                  }
                };
                var proxy = new Proxy(target, handler);
                new proxy()""");
    }

    @Test
    public void testProxyConstructNonObject() {
        // Test that construct trap must return an object
        assertErrorWithJavet("""
                var target = function() {};
                var handler = {
                  construct: function(target, args, newTarget) {
                    return 42; // Return non-object
                  }
                };
                var proxy = new Proxy(target, handler);
                new proxy()""");
    }

    @Test
    public void testProxyDefinePropertyBasic() {
        assertIntegerWithJavet("""
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
    }

    @Test
    public void testProxyDefinePropertyForward() {
        // Test that defineProperty without trap forwards to target
        assertIntegerWithJavet("""
                var target = {};
                var proxy = new Proxy(target, {});
                Object.defineProperty(proxy, 'x', {value: 42, writable: true});
                proxy.x""");
    }

    @Test
    public void testProxyDefinePropertyInvariantNonConfigurableChange() {
        // Test invariant: can't change non-configurable property descriptor
        assertErrorWithJavet("""
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
    }

    @Test
    public void testProxyDefinePropertyInvariantNonExtensible() {
        // Test invariant: can't add property to non-extensible target
        assertErrorWithJavet("""
                var target = {};
                Object.preventExtensions(target);
                var handler = {
                  defineProperty: function(target, prop, descriptor) {
                    return true; // Claim success
                  }
                };
                var proxy = new Proxy(target, handler);
                Object.defineProperty(proxy, 'x', {value: 42})""");
    }

    @Test
    public void testProxyDefinePropertyWithGetterSetter() {
        // Test defineProperty with getter/setter
        assertIntegerWithJavet("""
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
    }

    @Test
    public void testProxyDeletePropertyBasic() {
        assertBooleanWithJavet("""
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
    }

    @Test
    public void testProxyDeletePropertyForward() {
        assertBooleanWithJavet("""
                var target = {x: 1};
                var proxy = new Proxy(target, {});
                delete proxy.x;
                'x' in proxy""");
    }

    @Test
    public void testProxyDeletePropertyNonConfigurable() {
        // Test invariant: can't delete non-configurable property
        assertErrorWithJavet("""
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
    }

    @Test
    public void testProxyDeletePropertyReturningFalse() {
        assertBooleanWithJavet("""
                var target = {x: 1};
                var handler = {
                  deleteProperty: function(target, prop) {
                    return false; // Reject the deletion
                  }
                };
                var proxy = new Proxy(target, handler);
                delete proxy.x""");
    }

    @Test
    public void testProxyGetBasic() {
        assertIntegerWithJavet("""
                var target = {x: 1};
                var handler = {
                  get: function(target, prop, receiver) {
                    return target[prop] * 2;
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.x""");
    }

    @Test
    public void testProxyGetForward() {
        assertIntegerWithJavet("""
                var target = {x: 1};
                var proxy = new Proxy(target, {});
                proxy.x""");
    }

    @Test
    public void testProxyGetInvariantNonConfigurableAccessor() {
        // Test invariant: get must return undefined for non-configurable accessor without getter
        assertErrorWithJavet("""
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
    }

    @Test
    public void testProxyGetInvariantNonWritableNonConfigurable() {
        // Test invariant: must return same value for non-writable, non-configurable property
        assertErrorWithJavet("""
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
    }

    @Test
    public void testProxyGetOwnPropertyDescriptorBasic() {
        assertIntegerWithJavet("""
                var target = {x: 1};
                var handler = {
                  getOwnPropertyDescriptor: function(target, prop) {
                    return Object.getOwnPropertyDescriptor(target, prop);
                  }
                };
                var proxy = new Proxy(target, handler);
                var desc = Object.getOwnPropertyDescriptor(proxy, 'x');
                desc.value""");
    }

    @Test
    public void testProxyGetOwnPropertyDescriptorForward() {
        // Test that getOwnPropertyDescriptor without trap forwards to target
        assertIntegerWithJavet("""
                var target = {x: 42};
                var proxy = new Proxy(target, {});
                var desc = Object.getOwnPropertyDescriptor(proxy, 'x');
                desc.value""");
    }

    @Test
    public void testProxyGetOwnPropertyDescriptorInvariantNonConfigurable() {
        // Test invariant: can't return undefined for non-configurable property
        assertErrorWithJavet(
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
                        "Object.getOwnPropertyDescriptor(proxy, 'x')");
    }

    @Test
    public void testProxyGetOwnPropertyDescriptorUndefined() {
        assertBooleanWithJavet("""
                var target = {x: 1};
                var handler = {
                  getOwnPropertyDescriptor: function(target, prop) {
                    return undefined;
                  }
                };
                var proxy = new Proxy(target, handler);
                Object.getOwnPropertyDescriptor(proxy, 'x') === undefined""");
    }

    @Test
    public void testProxyGetPrototypeOfBasic() {
        assertIntegerWithJavet("var proto = {x: 1}; " +
                "var target = Object.create(proto); " +
                "var handler = { " +
                "  getPrototypeOf: function(target) { " +
                "    return proto; " +
                "  } " +
                "}; " +
                "var proxy = new Proxy(target, handler); " +
                "Object.getPrototypeOf(proxy).x");
    }

    @Test
    public void testProxyGetPrototypeOfForward() {
        // Test that missing trap forwards to target
        assertIntegerWithJavet("var proto = {x: 1}; " +
                "var target = Object.create(proto); " +
                "var proxy = new Proxy(target, {}); " +
                "Object.getPrototypeOf(proxy).x");
    }

    @Test
    public void testProxyGetPrototypeOfInvariant() {
        // Test invariant: if target is non-extensible, trap must return target's prototype
        assertErrorWithJavet("""
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
    }

    @Test
    public void testProxyGetPrototypeOfNull() {
        // Test that getPrototypeOf can return null - uses executeObject, leave as is
        assertBooleanWithJavet("""
                var target = Object.create(null);
                var handler = {
                  getPrototypeOf: function(target) {
                    return null;
                  }
                };
                var proxy = new Proxy(target, handler);
                Object.getPrototypeOf(proxy) === null""");
    }

    @Test
    public void testProxyHasBasic() {
        assertBooleanWithJavet("""
                var target = {x: 1};
                var handler = {
                  has: function(target, prop) {
                    return prop in target;
                  }
                };
                var proxy = new Proxy(target, handler);
                'x' in proxy""");
    }

    @Test
    public void testProxyHasForward() {
        assertBooleanWithJavet("""
                var target = {x: 1};
                var proxy = new Proxy(target, {});
                'x' in proxy""");
    }

    @Test
    public void testProxyHasInvariantNonConfigurable() {
        // Test invariant: must report non-configurable property as present
        assertErrorWithJavet("""
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
    }

    @Test
    public void testProxyHasInvariantNonExtensible() {
        // Test invariant: must report all properties on non-extensible target
        assertErrorWithJavet("""
                var target = {x: 1};
                Object.preventExtensions(target);
                var handler = {
                  has: function(target, prop) {
                    return false; // Hide existing property
                  }
                };
                var proxy = new Proxy(target, handler);
                'x' in proxy""");
    }

    @Test
    public void testProxyInPrototypeChain() {
        // Test proxy used in prototype chain
        assertIntegerWithJavet("""
                var proto = {x: 1};
                var handler = {
                  get: function(target, prop) {
                    return target[prop] * 2;
                  }
                };
                var proxy = new Proxy(proto, handler);
                var obj = Object.create(proxy);
                obj.x""");
    }

    @Test
    public void testProxyIsExtensibleBasic() {
        assertBooleanWithJavet("""
                var target = {};
                var handler = {
                  isExtensible: function(target) {
                    return Object.isExtensible(target);
                  }
                };
                var proxy = new Proxy(target, handler);
                Object.isExtensible(proxy)""");
    }

    @Test
    public void testProxyIsExtensibleForward() {
        // Test that isExtensible without trap forwards to target
        assertBooleanWithJavet("""
                var target = {};
                var proxy = new Proxy(target, {});
                Object.isExtensible(proxy)""");
    }

    @Test
    public void testProxyIsExtensibleInvariant() {
        // Test invariant: trap result must match target's extensibility
        assertErrorWithJavet("""
                var target = {};
                var handler = {
                  isExtensible: function(target) {
                    return false; // Lie about extensibility
                  }
                };
                var proxy = new Proxy(target, handler);
                Object.isExtensible(proxy)""");
    }

    @Test
    public void testProxyMultipleTraps() {
        assertBooleanWithJavet("""
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
    }

    @Test
    public void testProxyNestedRevocation() {
        // Test that revoking outer proxy doesn't affect inner proxy
        assertIntegerWithJavet("""
                var target = {x: 1};
                var {proxy: inner, revoke: revokeInner} = Proxy.revocable(target, {});
                var {proxy: outer, revoke: revokeOuter} = Proxy.revocable(inner, {});
                revokeOuter();
                inner.x""");
    }

    @Test
    public void testProxyOwnKeysBasic() {
        assertStringWithJavet("""
                var target = {x: 1, y: 2};
                var handler = {
                  ownKeys: function(target) {
                    return ['x', 'y', 'z'];
                  }
                };
                var proxy = new Proxy(target, handler);
                JSON.stringify(Object.keys(proxy))""");
    }

    @Test
    public void testProxyOwnKeysForward() {
        assertIntegerWithJavet("""
                var target = {x: 1, y: 2};
                var proxy = new Proxy(target, {});
                Object.keys(proxy).length""");
    }

    @Test
    public void testProxyOwnKeysInvariantDuplicates() {
        // Test invariant: ownKeys result can't have duplicates
        assertErrorWithJavet("""
                var target = {x: 1};
                var handler = {
                  ownKeys: function(target) {
                    return ['x', 'x']; // Duplicate property
                  }
                };
                var proxy = new Proxy(target, handler);
                Object.keys(proxy)""");
    }

    @Test
    public void testProxyOwnKeysInvariantNonExtensible() {
        // Test invariant: ownKeys must include all non-configurable properties
        assertErrorWithJavet("""
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
    }

    @Test
    public void testProxyOwnKeysWithSymbols() {
        // Test that ownKeys can return symbols
        assertIntegerWithJavet("""
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
    }

    @Test
    public void testProxyPreventExtensionsBasic() {
        assertBooleanWithJavet("""
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
    }

    @Test
    public void testProxyPreventExtensionsForward() {
        // Test that preventExtensions without trap forwards to target
        assertBooleanWithJavet("""
                var target = {};
                var proxy = new Proxy(target, {});
                Object.preventExtensions(proxy);
                Object.isExtensible(proxy)""");
    }

    @Test
    public void testProxyPreventExtensionsInvariant() {
        // Test invariant: if trap returns true, target must be non-extensible
        String code = """
                var target = {};
                var handler = {
                  preventExtensions: function(target) {
                    return true;
                  }
                };
                var proxy = new Proxy(target, handler);
                Object.preventExtensions(proxy)""";
        assertErrorWithJavet(code);
    }

    @Test
    public void testProxyPreventExtensionsReturningFalse() {
        // Test that preventExtensions trap can return false
        String code = """
                var target = {};
                var handler = {
                  preventExtensions: function(target) {
                    return false;
                  }
                };
                var proxy = new Proxy(target, handler);
                Object.preventExtensions(proxy)""";
        assertErrorWithJavet(code);
    }

    @Test
    public void testProxyReceiverInGet() {
        // Test that get trap receives correct receiver
        assertStringWithJavet("""
                var target = {x: 1};
                var handler = {
                  get: function(target, prop, receiver) {
                    return receiver === proxy ? 'correct' : 'wrong';
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.x""");
    }

    @Test
    public void testProxyReceiverInSet() {
        // Test that set trap receives correct receiver
        assertStringWithJavet("""
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
    }

    @Test
    public void testProxyRevocableAccessAfterRevoke() {
        // Test that accessing revoked proxy throws TypeError
        assertErrorWithJavet("""
                var target = {x: 1};
                var handler = {};
                var {proxy, revoke} = Proxy.revocable(target, handler);
                proxy.x; // Works before revoke
                revoke();
                proxy.x""");
    }

    @Test
    public void testProxyRevocableAccessBeforeRevoke() {
        // Test that proxy works normally before revocation
        assertIntegerWithJavet("""
                var target = {x: 1};
                var handler = {};
                var {proxy, revoke} = Proxy.revocable(target, handler);
                proxy.x""");
    }

    @Test
    public void testProxyRevocableBasic() {
        // Test that Proxy.revocable returns an object with proxy and revoke
        assertStringWithJavet("""
                var target = {x: 1};
                var handler = {};
                var revocable = Proxy.revocable(target, handler);
                typeof revocable""");

        assertStringWithJavet("""
                var target = {x: 1};
                var handler = {};
                var revocable = Proxy.revocable(target, handler);
                typeof revocable.proxy""");

        assertStringWithJavet("""
                var target = {x: 1};
                var handler = {};
                var revocable = Proxy.revocable(target, handler);
                typeof revocable.revoke""");
    }

    @Test
    public void testProxyRevocableRevokeMultipleTimes() {
        // Test that calling revoke multiple times doesn't cause issues
        assertStringWithJavet("""
                var target = {x: 1};
                var handler = {};
                var {proxy, revoke} = Proxy.revocable(target, handler);
                revoke();
                revoke(); // Call revoke again
                'ok'""");
    }

    @Test
    public void testProxyRevocableSetAfterRevoke() {
        // Test that setting on revoked proxy throws TypeError
        assertErrorWithJavet("""
                var target = {x: 1};
                var handler = {};
                var {proxy, revoke} = Proxy.revocable(target, handler);
                revoke();
                proxy.y = 2""");
    }

    @Test
    public void testProxySetBasic() {
        assertIntegerWithJavet("""
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
    }

    @Test
    public void testProxySetForward() {
        assertIntegerWithJavet("""
                var target = {};
                var proxy = new Proxy(target, {});
                proxy.x = 42;
                proxy.x""");
    }

    @Test
    public void testProxySetInvariantNonWritable() {
        // Test invariant: can't change non-writable property
        assertIntegerWithJavet("""
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
    }

    @Test
    public void testProxySetPrototypeOfBasic() {
        assertIntegerWithJavet("""
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
    }

    @Test
    public void testProxySetPrototypeOfForward() {
        // Test that setPrototypeOf without trap forwards to target
        assertIntegerWithJavet("""
                var newProto = {x: 42};
                var target = {};
                var proxy = new Proxy(target, {});
                Object.setPrototypeOf(proxy, newProto);
                Object.getPrototypeOf(proxy).x""");
    }

    @Test
    public void testProxySetPrototypeOfInvariant() {
        // Test invariant: if target is non-extensible, can't change prototype
        assertErrorWithJavet("""
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
    }

    @Test
    public void testProxySetPrototypeOfReturningFalse() {
        // Test that setPrototypeOf trap can return false
        assertBooleanWithJavet("""
                var target = {};
                var handler = {
                  setPrototypeOf: function(target, proto) {
                    return false; // Refuse to set prototype
                  }
                };
                var proxy = new Proxy(target, handler);
                Object.setPrototypeOf(proxy, {})""");
    }

    @Test
    public void testProxySetReturningFalse() {
        // Test that set trap returning false throws in strict mode
        assertIntegerWithJavet("""
                var target = {};
                var handler = {
                  set: function(target, prop, value) {
                    return false;
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.x = 1""");
    }

    @Test
    public void testProxyThrowingTrap() {
        // Test that trap can throw custom error
        assertErrorWithJavet("""
                var target = {x: 1};
                var handler = {
                  get: function(target, prop) {
                    throw new Error('custom error');
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.x""");
    }

    @Test
    public void testProxyTrapAccessesTargetApply() {
        // Test proxy trap accessing target.apply
        assertBooleanWithJavet("""
                var target = function(a, b) { return a + b; };
                var result;
                var handler = {
                  apply: function(t, thisArg, args) {
                    result = typeof t.apply;
                    return 42;
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy(1, 2);
                result === 'function'""");
    }

    @Test
    public void testProxyTrapChecksTargetType() {
        // Check if t is a function in the trap
        assertBooleanWithJavet("""
                var target = function(a, b) { return a + b; };
                var result;
                var handler = {
                  apply: function(t, thisArg, args) {
                    result = typeof t;
                    return 42;
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy(1, 2);
                result === 'function'""");
    }

    @Test
    public void testProxyTrapWithNonCallableHandler() {
        // Test that non-callable trap throws TypeError
        assertErrorWithJavet("""
                var target = {x: 1};
                var handler = {
                  get: 42
                };
                var proxy = new Proxy(target, handler);
                proxy.x""");
    }

    @Test
    public void testProxyWithArrayLikeObject() {
        // Test proxy with array-like object (has length property)
        assertStringWithJavet("""
                var target = {0: 'a', 1: 'b', 2: 'c', length: 3};
                var handler = {
                  get: function(target, prop) {
                    return target[prop];
                  }
                };
                var proxy = new Proxy(target, handler);
                Array.prototype.join.call(proxy, ',')""");
    }

    @Test
    public void testProxyWithBigIntObjectArithmetic() {
        // Test that proxied BigInt object valueOf works
        assertErrorWithJavet("""
                var target = Object(BigInt(10));
                var handler = {};
                var proxy = new Proxy(target, handler);
                proxy.valueOf()""");
    }

    @Test
    public void testProxyWithBigIntObjectAsTarget() {
        // Test that BigInt object (Object(BigInt(42))) can be a proxy target
        // BigInt objects are needed as proxy targets since primitive BigInts cannot be proxied
        assertStringWithJavet("""
                var target = Object(BigInt(42));
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
    }

    @Test
    public void testProxyWithBigIntObjectHasTrap() {
        // Test has trap on BigInt object proxy
        assertBooleanWithJavet("""
                var target = Object(BigInt(42));
                target.customProp = 'exists';
                var handler = {
                  has: function(target, prop) {
                    if (prop === 'fakeProperty') {
                      return true;
                    }
                    return prop in target;
                  }
                };
                var proxy = new Proxy(target, handler);
                'fakeProperty' in proxy""");
    }

    @Test
    public void testProxyWithBigIntObjectSetTrap() {
        // Test set trap on BigInt object proxy
        assertIntegerWithJavet("""
                var target = Object(BigInt(42));
                var handler = {
                  set: function(target, prop, value) {
                    target[prop] = value * 2;
                    return true;
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.test = 21;
                proxy.test""");
    }

    @Test
    public void testProxyWithBigIntObjectToString() {
        // Test that proxied BigInt object toString works correctly
        assertErrorWithJavet("""
                var target = Object(BigInt(255));
                var handler = {};
                var proxy = new Proxy(target, handler);
                proxy.toString()""");
    }

    @Test
    public void testProxyWithBigIntObjectValueOf() {
        // Test that proxied BigInt object valueOf works correctly
        assertErrorWithJavet("""
                var target = Object(BigInt(100));
                var handler = {
                  get: function(target, prop) {
                    return target[prop];
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.valueOf()""");
    }

    @Test
    public void testProxyWithBooleanObjectAsTarget() {
        // Test that Boolean object (new Boolean(true)) can be a proxy target
        // Boolean objects are needed as proxy targets since primitive booleans cannot be proxied
        assertStringWithJavet("""
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
    }

    @Test
    public void testProxyWithBooleanObjectToString() {
        // Test that proxied Boolean object still works with toString
        assertErrorWithJavet("""
                var target = new Boolean(true);
                var handler = {};
                var proxy = new Proxy(target, handler);
                proxy.toString()""");
    }

    @Test
    public void testProxyWithBooleanObjectTrapGet() {
        // Test that get trap intercepts valueOf on Boolean object
        assertBooleanWithJavet("""
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
    }

    @Test
    public void testProxyWithBooleanObjectValueOf() {
        // Test that proxied Boolean object still works with valueOf
        assertErrorWithJavet("""
                var target = new Boolean(true);
                var handler = {};
                var proxy = new Proxy(target, handler);
                proxy.valueOf()""");
    }

    @Test
    public void testProxyWithNullPrototype() {
        // Test proxy with null prototype target
        assertIntegerWithJavet("""
                var target = Object.create(null);
                target.x = 42;
                var handler = {
                  get: function(target, prop) {
                    return target[prop];
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.x""");
    }

    @Test
    public void testProxyWithNumberObjectAsTarget() {
        // Test that Number object (new Number(42)) can be a proxy target
        // Number objects are needed as proxy targets since primitive numbers cannot be proxied
        assertStringWithJavet("""
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
    }

    @Test
    public void testProxyWithNumberObjectToString() {
        // Test that proxied Number object still works with toString
        assertErrorWithJavet("""
                var target = new Number(42);
                var handler = {};
                var proxy = new Proxy(target, handler);
                proxy.toString()""");
    }

    @Test
    public void testProxyWithNumberObjectTrapGet() {
        // Test that get trap intercepts valueOf on Number object
        assertIntegerWithJavet("""
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
    }

    @Test
    public void testProxyWithNumberObjectValueOf() {
        // Test that proxied Number object still works with valueOf
        assertErrorWithJavet("""
                var target = new Number(3.14);
                var handler = {};
                var proxy = new Proxy(target, handler);
                proxy.valueOf()""");
    }

    @Test
    public void testProxyWithNumericProperties() {
        // Test that proxy works with object having numeric property names
        assertIntegerWithJavet("""
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
    }

    @Test
    public void testProxyWithSimpleTrap() {
        // Test proxy with simple trap that doesn't use apply
        assertIntegerWithJavet("""
                var target = function(a, b) { return a + b; };
                var handler = {
                  apply: function(t, thisArg, args) {
                    // Just return a constant
                    return 42;
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy(1, 2)""");
    }

    @Test
    public void testProxyWithStringObjectAsTarget() {
        // Test that String object (new String("hello")) can be a proxy target
        // String objects are needed as proxy targets since primitive strings cannot be proxied
        assertStringWithJavet("""
                var target = new String('hello');
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
    }

    @Test
    public void testProxyWithStringObjectCharAccess() {
        // Test that proxied String object supports character access
        assertErrorWithJavet("""
                var target = new String('hello');
                var handler = {
                  get: function(target, prop) {
                    if (prop === '1') {
                      return 'X';
                    }
                    return target[prop];
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy[1]""");
    }

    @Test
    public void testProxyWithStringObjectLength() {
        // Test that proxied String object has length property
        assertIntegerWithJavet("""
                var target = new String('hello');
                var handler = {};
                var proxy = new Proxy(target, handler);
                proxy.length""");
    }

    @Test
    public void testProxyWithStringObjectToString() {
        // Test that proxied String object still works with toString
        assertErrorWithJavet("""
                var target = new String('hello');
                var handler = {};
                var proxy = new Proxy(target, handler);
                proxy.toString()""");
    }

    @Test
    public void testProxyWithStringObjectTrapGet() {
        // Test that get trap intercepts methods on String object
        assertStringWithJavet("""
                var target = new String('hello');
                var handler = {
                  get: function(target, prop) {
                    if (prop === 'toUpperCase') {
                      return function() { return 'INTERCEPTED'; };
                    }
                    return target[prop];
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.toUpperCase()""");
    }

    @Test
    public void testProxyWithStringObjectValueOf() {
        // Test that proxied String object still works with valueOf
        assertErrorWithJavet("""
                var target = new String('world');
                var handler = {};
                var proxy = new Proxy(target, handler);
                proxy.valueOf()""");
    }

    @Test
    public void testProxyWithSymbolObjectAsPropertyKey() {
        // Test that symbol object is created and can be used with proxy
        assertStringWithJavet("""
                var symObj = Object(Symbol('key'));
                var sym = symObj.valueOf();
                var target = {};
                var handler = {
                  set: function(t, p, v) {
                    t[p] = v;
                    return true;
                  },
                  get: function(t, p) {
                    return t[p];
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy[sym] = 'value';
                proxy[sym]""");
    }

    @Test
    public void testProxyWithSymbolObjectAsTarget() {
        // Test that Symbol object (Object(Symbol('foo'))) can be a proxy target
        // Symbol objects are needed as proxy targets since primitive symbols cannot be proxied
        assertStringWithJavet("""
                var target = Object(Symbol('foo'));
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
    }

    @Test
    public void testProxyWithSymbolObjectDescription() {
        // Test accessing description through proxy
        assertStringWithJavet("""
                var target = Object(Symbol('myDescription'));
                var handler = {
                  get: function(target, prop) {
                    if (prop === 'description') {
                      return 'modified';
                    }
                    return target[prop];
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.description""");
    }

    @Test
    public void testProxyWithSymbolObjectGetPrimitiveValue() {
        // Test accessing [[PrimitiveValue]] through proxy
        assertStringWithJavet("""
                var target = Object(Symbol('test'));
                var handler = {};
                var proxy = new Proxy(target, handler);
                var primitiveValue = proxy['[[PrimitiveValue]]'];
                typeof primitiveValue""");
    }

    @Test
    public void testProxyWithSymbolObjectToString() {
        // Test that proxied Symbol object still works with toString
        assertErrorWithJavet("""
                var target = Object(Symbol('test'));
                var handler = {};
                var proxy = new Proxy(target, handler);
                proxy.toString()""");
    }

    @Test
    public void testProxyWithSymbolObjectTrapGet() {
        // Test that get trap intercepts methods on Symbol object
        assertStringWithJavet("""
                var target = Object(Symbol('test'));
                var handler = {
                  get: function(target, prop) {
                    if (prop === 'toString') {
                      return function() { return 'INTERCEPTED'; };
                    }
                    return target[prop];
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.toString()""");
    }

    @Test
    public void testProxyWithSymbolObjectValueOf() {
        // Test that proxied Symbol object still works with valueOf
        assertErrorWithJavet("""
                var target = Object(Symbol('mySymbol'));
                var handler = {};
                var proxy = new Proxy(target, handler);
                var primitiveValue = proxy.valueOf();
                typeof primitiveValue""");
    }

    @Test
    public void testProxyWithSymbolObjectWellKnown() {
        // Test proxying an object wrapping a well-known symbol
        assertStringWithJavet("""
                var target = Object(Symbol.iterator);
                var handler = {
                  get: function(target, prop) {
                    if (prop === 'test') {
                      return 'well-known';
                    }
                    return target[prop];
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy.test""");
    }

    @Test
    public void testProxyWithSymbolProperty() {
        // Test that proxy works with symbol properties
        assertIntegerWithJavet("""
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
    }

    @Test
    public void testSimpleProxyApply() {
        // Simplest case - just call target directly without using apply
        assertIntegerWithJavet("""
                var target = function(a, b) { return a + b; };
                var handler = {
                  apply: function(target, thisArg, args) {
                    // Call target directly instead of using target.apply
                    return target(args[0], args[1]) * 2;
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy(1, 2)""");
    }

    @Test
    public void testTargetApplyAccessedInTrap() {
        // Access target's apply in the trap and check type
        assertIntegerWithJavet("""
                var target = function(a, b) { return a + b; };
                
                // Check prototype before proxy
                var protoBefore = Object.getPrototypeOf(target);
                if (!protoBefore) {
                    throw new Error('target has null prototype before proxy');
                }
                if (protoBefore !== Function.prototype) {
                    throw new Error('target prototype BEFORE PROXY is not Function.prototype! It is: ' + Object.prototype.toString.call(protoBefore) + ', has apply: ' + (typeof protoBefore.apply));
                }
                // Also check apply directly
                if (typeof target.apply !== 'function') {
                    throw new Error('target.apply is not function before proxy, it is: ' + typeof target.apply);
                }
                
                var handler = {
                  apply: function(t, thisArg, args) {
                    // Check prototype of t inside trap  
                    var protoInTrap = Object.getPrototypeOf(t);
                    if (!protoInTrap) {
                      throw new Error('t has null prototype inside trap, t is ' + typeof t);
                    }
                    if (protoInTrap !== Function.prototype) {
                      throw new Error('t prototype inside trap is not Function.prototype! It is: ' + Object.prototype.toString.call(protoInTrap) + ', t === target: ' + (t === target));
                    }
                
                    // Access t.apply
                    var applyType = typeof t.apply;
                    if (applyType !== 'function') {
                      throw new Error('t.apply is ' + applyType);
                    }
                    return 42;
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy(1, 2)""");
    }

    @Test
    public void testTargetHasApplyBeforeProxy() {
        // Verify target has apply before creating proxy
        assertBooleanWithJavet("""
                var target = function(a, b) { return a + b; };
                typeof target.apply === 'function'""");
    }

    @Test
    public void testTrapWithClosureAccess() {
        // Access target through closure instead of parameter
        assertIntegerWithJavet("""
                var target = function(a, b) { return a + b; };
                
                var handler = {
                  apply: function(t, thisArg, args) {
                    // Access target (closure) instead of t (parameter)
                    var applyType = typeof target.apply;
                    if (applyType !== 'function') {
                      throw new Error('target.apply via closure is ' + applyType);
                    }
                    return target.apply(thisArg, args) * 2;
                  }
                };
                var proxy = new Proxy(target, handler);
                proxy(1, 2)""");
    }

    @Test
    void testTypeof() {
        // Proxy should be a function
        assertStringWithJavet("typeof Proxy");

        // Proxy.length should be 0
        assertIntegerWithJavet("Proxy.length");

        // Proxy.name should be "Proxy"
        assertStringWithJavet("Proxy.name");

        assertErrorWithJavet(
                "new Proxy()",
                "Proxy()");
    }
}

