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
    public void testProxyRevocableAccessAfterRevoke() {
        // Test that accessing revoked proxy throws TypeError
        ctx.eval(
                "var target = {x: 1}; " +
                        "var handler = {}; " +
                        "var {proxy, revoke} = Proxy.revocable(target, handler); " +
                        "proxy.x; " +  // Works before revoke
                        "revoke();"    // Revoke the proxy
        );

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
        JSValue result = ctx.eval(
                "var target = {x: 1}; " +
                        "var handler = {}; " +
                        "var {proxy, revoke} = Proxy.revocable(target, handler); " +
                        "proxy.x"
        );
        // Proxy access returns the value
        assertEquals(1.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyRevocableBasic() {
        // Test that Proxy.revocable returns an object with proxy and revoke
        JSValue result = ctx.eval(
                "var target = {x: 1}; " +
                        "var handler = {}; " +
                        "var revocable = Proxy.revocable(target, handler); " +
                        "typeof revocable"
        );
        assertEquals("object", result.toJavaObject());

        result = ctx.eval("typeof revocable.proxy");
        assertEquals("object", result.toJavaObject());

        result = ctx.eval("typeof revocable.revoke");
        assertEquals("function", result.toJavaObject());
    }

    @Test
    public void testProxyRevocableRevokeMultipleTimes() {
        // Test that calling revoke multiple times doesn't cause issues
        JSValue result = ctx.eval(
                "var target = {x: 1}; " +
                        "var handler = {}; " +
                        "var {proxy, revoke} = Proxy.revocable(target, handler); " +
                        "revoke(); " +
                        "revoke(); " +  // Call revoke again
                        "'ok'"
        );
        assertEquals("ok", result.toJavaObject());
    }

    @Test
    public void testProxyRevocableSetAfterRevoke() {
        // Test that setting on revoked proxy throws TypeError
        ctx.eval(
                "var target = {x: 1}; " +
                        "var handler = {}; " +
                        "var {proxy, revoke} = Proxy.revocable(target, handler); " +
                        "revoke();"
        );

        // Try to set on revoked proxy - should throw TypeError
        try {
            ctx.eval("proxy.y = 2");
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("revoked proxy") ||
                    e.getMessage().contains("TypeError"));
        }
    }

    // ============================================================
    // getPrototypeOf trap tests
    // ============================================================

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
            ctx.eval(
                    "var proto1 = {x: 1}; " +
                    "var proto2 = {x: 2}; " +
                    "var target = Object.create(proto1); " +
                    "Object.preventExtensions(target); " +
                    "var handler = { " +
                    "  getPrototypeOf: function(target) { " +
                    "    return proto2; " +  // Return different prototype
                    "  } " +
                    "}; " +
                    "var proxy = new Proxy(target, handler); " +
                    "Object.getPrototypeOf(proxy)"
            );
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("inconsistent") ||
                    e.getMessage().contains("TypeError"));
        }
    }

    // ============================================================
    // setPrototypeOf trap tests
    // ============================================================

    @Test
    public void testProxySetPrototypeOfBasic() {
        JSValue result = ctx.eval(
                "var newProto = {x: 2}; " +
                "var target = {y: 1}; " +
                "var handler = { " +
                "  setPrototypeOf: function(target, proto) { " +
                "    Object.setPrototypeOf(target, proto); " +
                "    return true; " +
                "  } " +
                "}; " +
                "var proxy = new Proxy(target, handler); " +
                "Object.setPrototypeOf(proxy, newProto); " +
                "Object.getPrototypeOf(proxy).x"
        );
        assertEquals(2.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxySetPrototypeOfInvariant() {
        // Test invariant: if target is non-extensible, can't change prototype
        try {
            ctx.eval(
                    "var proto1 = {x: 1}; " +
                    "var proto2 = {x: 2}; " +
                    "var target = Object.create(proto1); " +
                    "Object.preventExtensions(target); " +
                    "var handler = { " +
                    "  setPrototypeOf: function(target, proto) { " +
                    "    return true; " +  // Claim success without changing
                    "  } " +
                    "}; " +
                    "var proxy = new Proxy(target, handler); " +
                    "Object.setPrototypeOf(proxy, proto2)"
            );
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("inconsistent") ||
                    e.getMessage().contains("TypeError"));
        }
    }

    // ============================================================
    // isExtensible trap tests
    // ============================================================

    @Test
    public void testProxyIsExtensibleBasic() {
        JSValue result = ctx.eval(
                "var target = {}; " +
                "var handler = { " +
                "  isExtensible: function(target) { " +
                "    return Object.isExtensible(target); " +
                "  } " +
                "}; " +
                "var proxy = new Proxy(target, handler); " +
                "Object.isExtensible(proxy)"
        );
        assertEquals(true, result.toJavaObject());
    }

    @Test
    public void testProxyIsExtensibleInvariant() {
        // Test invariant: trap result must match target's extensibility
        try {
            ctx.eval(
                    "var target = {}; " +
                    "var handler = { " +
                    "  isExtensible: function(target) { " +
                    "    return false; " +  // Lie about extensibility
                    "  } " +
                    "}; " +
                    "var proxy = new Proxy(target, handler); " +
                    "Object.isExtensible(proxy)"
            );
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("inconsistent") ||
                    e.getMessage().contains("TypeError"));
        }
    }

    // ============================================================
    // preventExtensions trap tests
    // ============================================================

    @Test
    public void testProxyPreventExtensionsBasic() {
        JSValue result = ctx.eval(
                "var target = {}; " +
                "var handler = { " +
                "  preventExtensions: function(target) { " +
                "    Object.preventExtensions(target); " +
                "    return true; " +
                "  } " +
                "}; " +
                "var proxy = new Proxy(target, handler); " +
                "Object.preventExtensions(proxy); " +
                "Object.isExtensible(proxy)"
        );
        assertEquals(false, result.toJavaObject());
    }

    @Test
    public void testProxyPreventExtensionsInvariant() {
        // Test invariant: if trap returns true, target must be non-extensible
        try {
            ctx.eval(
                    "var target = {}; " +
                    "var handler = { " +
                    "  preventExtensions: function(target) { " +
                    "    return true; " +  // Claim success without doing it
                    "  } " +
                    "}; " +
                    "var proxy = new Proxy(target, handler); " +
                    "Object.preventExtensions(proxy)"
            );
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("inconsistent") ||
                    e.getMessage().contains("TypeError"));
        }
    }

    // ============================================================
    // getOwnPropertyDescriptor trap tests
    // ============================================================

    @Test
    public void testProxyGetOwnPropertyDescriptorBasic() {
        JSValue result = ctx.eval(
                "var target = {x: 1}; " +
                "var handler = { " +
                "  getOwnPropertyDescriptor: function(target, prop) { " +
                "    return Object.getOwnPropertyDescriptor(target, prop); " +
                "  } " +
                "}; " +
                "var proxy = new Proxy(target, handler); " +
                "var desc = Object.getOwnPropertyDescriptor(proxy, 'x'); " +
                "desc.value"
        );
        assertEquals(1.0, (Double) result.toJavaObject());
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

    // ============================================================
    // defineProperty trap tests
    // ============================================================

    @Test
    public void testProxyDefinePropertyBasic() {
        JSValue result = ctx.eval(
                "var target = {}; " +
                "var handler = { " +
                "  defineProperty: function(target, prop, descriptor) { " +
                "    Object.defineProperty(target, prop, descriptor); " +
                "    return true; " +
                "  } " +
                "}; " +
                "var proxy = new Proxy(target, handler); " +
                "Object.defineProperty(proxy, 'x', {value: 42}); " +
                "proxy.x"
        );
        assertEquals(42.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyDefinePropertyInvariantNonExtensible() {
        // Test invariant: can't add property to non-extensible target
        try {
            ctx.eval(
                    "var target = {}; " +
                    "Object.preventExtensions(target); " +
                    "var handler = { " +
                    "  defineProperty: function(target, prop, descriptor) { " +
                    "    return true; " +  // Claim success
                    "  } " +
                    "}; " +
                    "var proxy = new Proxy(target, handler); " +
                    "Object.defineProperty(proxy, 'x', {value: 42})"
            );
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("inconsistent") ||
                    e.getMessage().contains("TypeError"));
        }
    }

    // ============================================================
    // has trap tests
    // ============================================================

    @Test
    public void testProxyHasBasic() {
        JSValue result = ctx.eval(
                "var target = {x: 1}; " +
                "var handler = { " +
                "  has: function(target, prop) { " +
                "    return prop in target; " +
                "  } " +
                "}; " +
                "var proxy = new Proxy(target, handler); " +
                "'x' in proxy"
        );
        assertEquals(true, result.toJavaObject());
    }

    @Test
    public void testProxyHasForward() {
        JSValue result = ctx.eval(
                "var target = {x: 1}; " +
                "var proxy = new Proxy(target, {}); " +
                "'x' in proxy"
        );
        assertEquals(true, result.toJavaObject());
    }

    // ============================================================
    // get trap tests
    // ============================================================

    @Test
    public void testProxyGetBasic() {
        JSValue result = ctx.eval(
                "var target = {x: 1}; " +
                "var handler = { " +
                "  get: function(target, prop, receiver) { " +
                "    return target[prop] * 2; " +
                "  } " +
                "}; " +
                "var proxy = new Proxy(target, handler); " +
                "proxy.x"
        );
        assertEquals(2.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyGetForward() {
        JSValue result = ctx.eval(
                "var target = {x: 1}; " +
                "var proxy = new Proxy(target, {}); " +
                "proxy.x"
        );
        assertEquals(1.0, (Double) result.toJavaObject());
    }

    // ============================================================
    // set trap tests
    // ============================================================

    @Test
    public void testProxySetBasic() {
        JSValue result = ctx.eval(
                "var target = {}; " +
                "var handler = { " +
                "  set: function(target, prop, value, receiver) { " +
                "    target[prop] = value * 2; " +
                "    return true; " +
                "  } " +
                "}; " +
                "var proxy = new Proxy(target, handler); " +
                "proxy.x = 5; " +
                "proxy.x"
        );
        assertEquals(10.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxySetForward() {
        JSValue result = ctx.eval(
                "var target = {}; " +
                "var proxy = new Proxy(target, {}); " +
                "proxy.x = 42; " +
                "proxy.x"
        );
        assertEquals(42.0, (Double) result.toJavaObject());
    }

    // ============================================================
    // deleteProperty trap tests
    // ============================================================

    @Test
    public void testProxyDeletePropertyBasic() {
        JSValue result = ctx.eval(
                "var target = {x: 1}; " +
                "var handler = { " +
                "  deleteProperty: function(target, prop) { " +
                "    delete target[prop]; " +
                "    return true; " +
                "  } " +
                "}; " +
                "var proxy = new Proxy(target, handler); " +
                "delete proxy.x; " +
                "'x' in proxy"
        );
        assertEquals(false, result.toJavaObject());
    }

    @Test
    public void testProxyDeletePropertyForward() {
        JSValue result = ctx.eval(
                "var target = {x: 1}; " +
                "var proxy = new Proxy(target, {}); " +
                "delete proxy.x; " +
                "'x' in proxy"
        );
        assertEquals(false, result.toJavaObject());
    }

    // ============================================================
    // ownKeys trap tests
    // ============================================================

    @Test
    public void testProxyOwnKeysBasic() {
        JSValue result = ctx.eval(
                "var target = {x: 1, y: 2}; " +
                "var handler = { " +
                "  ownKeys: function(target) { " +
                "    return ['x', 'y', 'z']; " +
                "  } " +
                "}; " +
                "var proxy = new Proxy(target, handler); " +
                "Object.keys(proxy).length"
        );
        assertEquals(3.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyOwnKeysForward() {
        JSValue result = ctx.eval(
                "var target = {x: 1, y: 2}; " +
                "var proxy = new Proxy(target, {}); " +
                "Object.keys(proxy).length"
        );
        assertEquals(2.0, (Double) result.toJavaObject());
    }

    // ============================================================
    // apply trap tests
    // ============================================================

    @Test
    public void testProxyApplyBasic() {
        JSValue result = ctx.eval(
                "var target = function(a, b) { return a + b; }; " +
                "var handler = { " +
                "  apply: function(target, thisArg, args) { " +
                "    return target.apply(thisArg, args) * 2; " +
                "  } " +
                "}; " +
                "var proxy = new Proxy(target, handler); " +
                "proxy(1, 2)"
        );
        assertEquals(6.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyApplyForward() {
        JSValue result = ctx.eval(
                "var target = function(a, b) { return a + b; }; " +
                "var proxy = new Proxy(target, {}); " +
                "proxy(1, 2)"
        );
        assertEquals(3.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyApplyNonFunction() {
        // Test that apply trap on non-function throws error
        try {
            ctx.eval(
                    "var target = {}; " +
                    "var handler = { " +
                    "  apply: function(target, thisArg, args) { " +
                    "    return 42; " +
                    "  } " +
                    "}; " +
                    "var proxy = new Proxy(target, handler); " +
                    "proxy()"
            );
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("not a function") ||
                    e.getMessage().contains("TypeError"));
        }
    }

    // ============================================================
    // construct trap tests
    // ============================================================

    @Test
    public void testProxyConstructBasic() {
        JSValue result = ctx.eval(
                "var target = function(x) { this.value = x; }; " +
                "var handler = { " +
                "  construct: function(target, args, newTarget) { " +
                "    var obj = Object.create(target.prototype); " +
                "    obj.value = args[0] * 2; " +
                "    return obj; " +
                "  } " +
                "}; " +
                "var proxy = new Proxy(target, handler); " +
                "var instance = new proxy(5); " +
                "instance.value"
        );
        assertEquals(10.0, (Double) result.toJavaObject());
    }

    @Test
    public void testProxyConstructNonObject() {
        // Test that construct trap must return an object
        try {
            ctx.eval(
                    "var target = function() {}; " +
                    "var handler = { " +
                    "  construct: function(target, args, newTarget) { " +
                    "    return 42; " +  // Return non-object
                    "  } " +
                    "}; " +
                    "var proxy = new Proxy(target, handler); " +
                    "new proxy()"
            );
            fail("Should have thrown TypeError");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("must return an object") ||
                    e.getMessage().contains("TypeError"));
        }
    }

    // ============================================================
    // Combined/Complex tests
    // ============================================================

    @Test
    public void testProxyMultipleTraps() {
        JSValue result = ctx.eval(
                "var target = {x: 1}; " +
                "var getCalled = false; " +
                "var setCalled = false; " +
                "var handler = { " +
                "  get: function(target, prop) { " +
                "    getCalled = true; " +
                "    return target[prop]; " +
                "  }, " +
                "  set: function(target, prop, value) { " +
                "    setCalled = true; " +
                "    target[prop] = value; " +
                "    return true; " +
                "  } " +
                "}; " +
                "var proxy = new Proxy(target, handler); " +
                "var val = proxy.x; " +
                "proxy.y = 2; " +
                "getCalled && setCalled"
        );
        assertEquals(true, result.toJavaObject());
    }

    @Test
    public void testProxyChaining() {
        // Test proxy of proxy
        JSValue result = ctx.eval(
                "var target = {x: 1}; " +
                "var handler1 = { " +
                "  get: function(target, prop) { " +
                "    return target[prop] + 1; " +
                "  } " +
                "}; " +
                "var proxy1 = new Proxy(target, handler1); " +
                "var handler2 = { " +
                "  get: function(target, prop) { " +
                "    return target[prop] + 1; " +
                "  } " +
                "}; " +
                "var proxy2 = new Proxy(proxy1, handler2); " +
                "proxy2.x"
        );
        assertEquals(3.0, (Double) result.toJavaObject());
    }
}
