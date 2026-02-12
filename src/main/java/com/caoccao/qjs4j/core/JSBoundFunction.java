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

package com.caoccao.qjs4j.core;

/**
 * Represents a bound JavaScript function (created via Function.prototype.bind).
 */
public final class JSBoundFunction extends JSFunction {
    private final JSValue[] boundArgs;
    private final JSValue boundThis;
    private final JSFunction target;

    public JSBoundFunction(JSFunction target, JSValue boundThis, JSValue[] boundArgs) {
        super(); // Initialize as JSObject
        this.target = target;
        this.boundThis = boundThis;
        this.boundArgs = boundArgs != null ? boundArgs.clone() : new JSValue[0];

        // Set up function properties on the object
        // Per ECMAScript spec, bound functions have "name" and "length" properties
        // with attributes: { [[Writable]]: false, [[Enumerable]]: false, [[Configurable]]: true }
        // Following QuickJS implementation: use defineProperty with only configurable=true
        this.defineProperty(
                PropertyKey.fromString("name"),
                PropertyDescriptor.dataDescriptor(
                        new JSString(getName()),
                        false, // writable
                        false, // enumerable
                        true   // configurable
                )
        );

        this.defineProperty(
                PropertyKey.fromString("length"),
                PropertyDescriptor.dataDescriptor(
                        new JSNumber(getLength()),
                        false, // writable
                        false, // enumerable
                        true   // configurable
                )
        );
    }

    @Override
    public JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        // Merge bound args with call args
        return target.call(context, boundThis, prependBoundArgs(args));
    }

    @Override
    public int getLength() {
        return Math.max(0, target.getLength() - boundArgs.length);
    }

    @Override
    public String getName() {
        String targetName = target.getName();
        if (targetName == null) {
            targetName = "";
        }
        return "bound " + targetName;
    }

    public JSFunction getTarget() {
        return target;
    }

    public JSValue[] prependBoundArgs(JSValue[] args) {
        JSValue[] mergedArgs = new JSValue[boundArgs.length + args.length];
        System.arraycopy(boundArgs, 0, mergedArgs, 0, boundArgs.length);
        System.arraycopy(args, 0, mergedArgs, boundArgs.length, args.length);
        return mergedArgs;
    }

    @Override
    public JSValueType type() {
        return JSValueType.FUNCTION;
    }
}
