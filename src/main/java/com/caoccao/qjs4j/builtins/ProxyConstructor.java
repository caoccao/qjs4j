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

import com.caoccao.qjs4j.core.*;

/**
 * Implementation of the Proxy constructor.
 * Based on ES2020 Proxy specification (simplified).
 * The Proxy constructor creates a Proxy object that wraps a target object
 * and allows intercepting operations on it via handler traps.
 */
public final class ProxyConstructor {

    /**
     * Proxy.revocable(target, handler)
     * ES2020 26.2.2.1
     * Creates a revocable proxy object.
     * Returns an object with a proxy and a revoke function.
     */
    public static JSValue revocable(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length < 2) {
            return ctx.throwError("TypeError", "Proxy.revocable requires target and handler arguments");
        }

        // Target must be an object (since JSFunction extends JSObject, this covers both)
        JSValue target = args[0];
        if (!(target instanceof JSObject)) {
            return ctx.throwError("TypeError", "Proxy.revocable target must be an object");
        }

        if (!(args[1] instanceof JSObject handler)) {
            return ctx.throwError("TypeError", "Proxy.revocable handler must be an object");
        }

        // Create the proxy
        JSProxy proxy = new JSProxy(target, handler, ctx);

        // Create result object with proxy and revoke function
        JSObject result = new JSObject();
        result.set("proxy", proxy);

        // Create revoke function that invalidates the proxy
        JSNativeFunction revokeFunc = new JSNativeFunction("revoke", 0, (context, thisValue, funcArgs) -> {
            // Revoke the proxy - all subsequent operations will throw TypeError
            proxy.revoke();
            return JSUndefined.INSTANCE;
        });
        result.set("revoke", revokeFunc);

        return result;
    }
}
