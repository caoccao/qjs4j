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

import com.caoccao.qjs4j.core.*;

/** Restrictions shared by the defineProperty and getOwnPropertyDescriptor proxy traps. */
final class ProxyDescriptorInvariants {
    private ProxyDescriptorInvariants() {
    }

    private static boolean addsRestriction(PropertyDescriptor descriptor, PropertyDescriptor current) {
        if (current.isConfigurable()) {
            return setsNonConfigurable(descriptor);
        }
        return current.isDataDescriptor() && current.isWritable() && descriptor.hasWritable()
                && !descriptor.isWritable();
    }

    /** A successful delete and a failed has trap both report that an own property is absent. */
    static boolean canHideProperty(JSContext context, JSObject target, PropertyKey key, boolean deleting) {
        PropertyDescriptor descriptor = target.getOwnPropertyDescriptor(key);
        if (descriptor == null) {
            return true;
        }
        String detail;
        if (!descriptor.isConfigurable()) {
            detail = deleting
                    ? "which is non-configurable in the proxy target"
                    : "which exists in the proxy target as non-configurable";
        } else if (!target.isExtensible()) {
            detail = deleting ? "but the proxy target is non-extensible" : "but the proxy target is not extensible";
        } else {
            return true;
        }
        String failure = deleting
                ? "'deleteProperty' on proxy: trap returned truish"
                : "'has' on proxy: trap returned falsish";
        context.throwTypeError(failure + " for property '" + key.toPropertyString() + "' " + detail);
        return false;
    }

    static boolean canReport(PropertyDescriptor descriptor, PropertyDescriptor current, boolean extensible) {
        if (current == null) {
            return extensible && !setsNonConfigurable(descriptor);
        }
        return compatible(descriptor, current) && !addsRestriction(descriptor, current);
    }

    private static boolean compatible(PropertyDescriptor descriptor, PropertyDescriptor current) {
        if (current.isConfigurable()) {
            return true;
        }
        if (descriptor.hasConfigurable() && descriptor.isConfigurable()
                || descriptor.hasEnumerable() && descriptor.isEnumerable() != current.isEnumerable()) {
            return false;
        }
        boolean accessor = descriptor.hasGetter() || descriptor.hasSetter();
        if (!accessor && !descriptor.isDataDescriptor()) {
            return true;
        }
        if (accessor != (current.hasGetter() || current.hasSetter())) {
            return false;
        }
        if (accessor) {
            return (!descriptor.hasGetter() || JSObject.sameValue(descriptor.getGetter(), current.getGetter()))
                    && (!descriptor.hasSetter() || JSObject.sameValue(descriptor.getSetter(), current.getSetter()));
        }
        return current.isWritable() || ((!descriptor.hasWritable() || !descriptor.isWritable())
                && (!descriptor.hasValue() || JSObject.sameValue(descriptor.getValue(), current.getValue())));
    }

    private static boolean setsNonConfigurable(PropertyDescriptor descriptor) {
        return descriptor.hasConfigurable() && !descriptor.isConfigurable();
    }

    static boolean validateDefinition(JSContext context, PropertyKey key, PropertyDescriptor descriptor,
            PropertyDescriptor current, boolean extensible) {
        if (current == null) {
            if (extensible && !setsNonConfigurable(descriptor)) {
                return true;
            }
            context.throwTypeError("'defineProperty' on proxy: trap returned truish for adding property '"
                    + key.toPropertyString() + "'  to the non-extensible proxy target");
            return false;
        }
        if (!compatible(descriptor, current)) {
            context.throwTypeError(
                    "'defineProperty' on proxy: trap returned truish for adding property '" + key.toPropertyString()
                            + "'  that is incompatible with the existing property in the proxy target");
            return false;
        }
        if (addsRestriction(descriptor, current)) {
            context.throwTypeError("proxy: inconsistent defineProperty");
            return false;
        }
        return true;
    }
}
