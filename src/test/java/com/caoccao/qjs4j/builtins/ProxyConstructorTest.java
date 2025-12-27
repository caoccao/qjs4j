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
}
