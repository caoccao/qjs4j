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

package com.caoccao.qjs4j.builtins.object;

import com.caoccao.qjs4j.core.*;
import java.util.List;

/** Receiver and descriptor checks for Object's seal, freeze and extensibility operations. */
enum ObjectIntegrity {
    FREEZE,
    IS_FROZEN,
    IS_SEALED,
    PREVENT_EXTENSIONS,
    SEAL;

    JSValue apply(JSContext context, JSValue[] arguments) {
        if (arguments.length == 0 && context.getVirtualMachine().getCurrentFrame() == null) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }
        JSValue value = JSValue.argumentOrUndefined(arguments, 0);
        if (!(value instanceof JSObject object)) {
            return this == IS_FROZEN || this == IS_SEALED ? JSBoolean.TRUE : value;
        }
        return switch (this) {
            case IS_FROZEN, IS_SEALED -> JSBoolean.valueOf(testIntegrityLevel(context, object, this == IS_FROZEN));
            case FREEZE, SEAL -> object.setIntegrityLevel(context, this == FREEZE) ? object : JSUndefined.INSTANCE;
            case PREVENT_EXTENSIONS -> preventExtensions(context, object);
        };
    }

    private static JSValue preventExtensions(JSContext context, JSObject obj) {
        if (!obj.preventExtensionsWithResult()) {
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (obj instanceof JSProxy) {
                return context.throwTypeError("'preventExtensions' on proxy: trap returned falsish");
            }
            return context.throwTypeError("Cannot prevent extensions");
        }
        return obj;
    }

    private static boolean testIntegrityLevel(JSContext context, JSObject obj, boolean frozen) {
        // Step 1: If IsExtensible(O), return false
        if (obj.isExtensible()) {
            return false;
        }

        // Step 3: Let keys be ? O.[[OwnPropertyKeys]]()
        List<PropertyKey> keys = obj.getOwnPropertyKeys();

        // Step 4: For each element k of keys
        for (PropertyKey key : keys) {
            PropertyDescriptor currentDesc = obj.getOwnPropertyDescriptor(key);
            if (context.hasPendingException()) {
                return false;
            }
            if (currentDesc != null) {
                // Step 4.b.i: If currentDesc.[[Configurable]] is true, return false
                if (currentDesc.isConfigurable()) {
                    return false;
                }
                // Step 4.b.ii: If level is frozen and IsDataDescriptor(currentDesc)
                // and currentDesc.[[Writable]] is true, return false
                if (frozen && currentDesc.isDataDescriptor() && currentDesc.isWritable()) {
                    return false;
                }
            }
        }
        return true;
    }
}
