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
public final class JSBoundFunction implements JSFunction {
    private final JSFunction target;
    private final JSValue boundThis;
    private final JSValue[] boundArgs;

    public JSBoundFunction(JSFunction target, JSValue boundThis, JSValue[] boundArgs) {
        this.target = target;
        this.boundThis = boundThis;
        this.boundArgs = boundArgs;
    }

    @Override
    public JSValue call(JSContext ctx, JSValue thisArg, JSValue[] args) {
        // Merge bound args with call args
        JSValue[] mergedArgs = new JSValue[boundArgs.length + args.length];
        System.arraycopy(boundArgs, 0, mergedArgs, 0, boundArgs.length);
        System.arraycopy(args, 0, mergedArgs, boundArgs.length, args.length);
        return target.call(ctx, boundThis, mergedArgs);
    }

    @Override
    public String getName() {
        return "bound " + target.getName();
    }

    @Override
    public int getLength() {
        return Math.max(0, target.getLength() - boundArgs.length);
    }

    @Override
    public JSValueType type() {
        return JSValueType.FUNCTION;
    }

    @Override
    public Object toJavaObject() {
        return this;
    }
}
