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
 * ShadowRealm constructor.
 * ShadowRealm is not implemented by QuickJS, so this follows the ECMAScript proposal behavior
 * needed by test262.
 */
public final class ShadowRealmConstructor {
    private ShadowRealmConstructor() {
    }

    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        JSRuntime runtime = context.getRuntime();
        JSContext shadowContext = runtime.createContext();
        JSShadowRealm shadowRealm = new JSShadowRealm(shadowContext);
        context.transferPrototype(shadowRealm, JSShadowRealm.NAME);
        return shadowRealm;
    }
}
