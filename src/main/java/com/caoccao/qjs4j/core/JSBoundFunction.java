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
    public static final String NAME = JSFunction.NAME;
    private final JSValue[] boundArgs;
    private final JSValue boundThis;
    private final double computedLength;
    private final String computedName;
    private final JSFunction target;

    public JSBoundFunction(JSFunction target, JSValue boundThis, JSValue[] boundArgs) {
        this(target, boundThis, boundArgs,
                Math.max(0, target.getLength() - (boundArgs != null ? boundArgs.length : 0)),
                "bound " + (target.getName() != null ? target.getName() : ""));
    }

    public JSBoundFunction(JSFunction target, JSValue boundThis, JSValue[] boundArgs,
                           double computedLength, String computedName) {
        super(); // Initialize as JSObject
        this.target = target;
        this.boundThis = boundThis;
        this.boundArgs = boundArgs != null ? boundArgs.clone() : new JSValue[0];
        this.computedLength = computedLength;
        this.computedName = computedName != null ? computedName : "bound ";

        // Set up function properties on the object
        // Per ECMAScript spec, bound functions have "name" and "length" properties
        // with attributes: { [[Writable]]: false, [[Enumerable]]: false, [[Configurable]]: true }
        // Following QuickJS implementation: use defineProperty with only configurable=true

        // Length can be Infinity or a large double, use JSNumber for full range
        JSValue lengthValue;
        if (Double.isInfinite(this.computedLength) || this.computedLength > Integer.MAX_VALUE) {
            lengthValue = JSNumber.of(this.computedLength);
        } else {
            lengthValue = JSNumber.of((int) this.computedLength);
        }
        this.defineProperty(
                PropertyKey.LENGTH,
                PropertyDescriptor.dataDescriptor(
                        lengthValue,
                        PropertyDescriptor.DataState.Configurable
                )
        );

        this.defineProperty(
                PropertyKey.NAME,
                PropertyDescriptor.dataDescriptor(
                        new JSString(this.computedName),
                        PropertyDescriptor.DataState.Configurable
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
        if (Double.isInfinite(computedLength) || computedLength > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) computedLength;
    }

    @Override
    public String getName() {
        return computedName;
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
