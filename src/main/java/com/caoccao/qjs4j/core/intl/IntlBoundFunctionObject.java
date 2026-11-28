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

package com.caoccao.qjs4j.core.intl;

import com.caoccao.qjs4j.core.*;

import java.util.Locale;

/** An Intl object whose prototype getter creates one bound function per receiver. */
abstract class IntlBoundFunctionObject extends IntlLocaleObject {
    JSFunction boundFunction;

    IntlBoundFunctionObject(JSContext context, Locale locale) {
        super(context, locale);
    }

    final JSFunction getOrCreateBoundFunction(JSContext context, int length, JSNativeCallback operation) {
        if (boundFunction == null) {
            JSNativeFunction function = new JSNativeFunction(context, "", length,
                    (activeContext, ignored, args) -> operation.call(activeContext, this, args));
            context.transferPrototype(function, JSFunction.NAME);
            boundFunction = function;
        }
        return boundFunction;
    }
}
