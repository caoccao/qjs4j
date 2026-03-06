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

import java.util.*;

/**
 * Module namespace exotic-like object for dynamic import().
 * It enforces non-extensible and non-configurable exported bindings semantics.
 */
public final class JSImportNamespaceObject extends JSObject {
    private final JSContext context;
    private final Set<String> exportNames;
    private boolean finalized;

    public JSImportNamespaceObject(JSContext context) {
        super();
        this.context = context;
        this.exportNames = new HashSet<>();
        this.finalized = false;
        setPrototype(null);
    }

    private static boolean sameValue(JSValue leftValue, JSValue rightValue) {
        if (leftValue == rightValue) {
            return true;
        }
        if (leftValue == null || rightValue == null) {
            return false;
        }

        JSValueType leftType = leftValue.type();
        JSValueType rightType = rightValue.type();
        if (leftType != rightType) {
            return false;
        }

        return switch (leftType) {
            case UNDEFINED, NULL -> true;
            case NUMBER -> {
                if (!(leftValue instanceof JSNumber leftNumber) || !(rightValue instanceof JSNumber rightNumber)) {
                    yield false;
                }
                double left = leftNumber.value();
                double right = rightNumber.value();
                if (Double.isNaN(left) && Double.isNaN(right)) {
                    yield true;
                }
                yield Double.doubleToRawLongBits(left) == Double.doubleToRawLongBits(right);
            }
            case STRING -> (leftValue instanceof JSString leftString && rightValue instanceof JSString rightString)
                    && leftString.value().equals(rightString.value());
            case BOOLEAN -> (leftValue instanceof JSBoolean leftBoolean && rightValue instanceof JSBoolean rightBoolean)
                    && leftBoolean.value() == rightBoolean.value();
            case BIGINT -> (leftValue instanceof JSBigInt leftBigInt && rightValue instanceof JSBigInt rightBigInt)
                    && leftBigInt.value().equals(rightBigInt.value());
            case SYMBOL, OBJECT, FUNCTION -> false;
        };
    }

    @Override
    public boolean defineProperty(JSContext context, PropertyKey key, PropertyDescriptor descriptor) {
        if (finalized && isExportProperty(key)) {
            return validateExportDefine(key, descriptor);
        }
        return super.defineProperty(context, key, descriptor);
    }

    @Override
    public boolean delete(JSContext context, PropertyKey key) {
        if (finalized && isExportProperty(key)) {
            if (context != null && context.isStrictMode()) {
                context.throwTypeError("Cannot delete property '" + key.toPropertyString() + "' of [object Module]");
            }
            return false;
        }
        return super.delete(context, key);
    }

    public void finalizeNamespace() {
        if (!hasOwnProperty(PropertyKey.SYMBOL_TO_STRING_TAG)) {
            defineProperty(
                    PropertyKey.SYMBOL_TO_STRING_TAG,
                    new JSString("Module"),
                    PropertyDescriptor.DataState.None);
        }
        preventExtensions();
        finalized = true;
    }

    @Override
    public PropertyDescriptor getOwnPropertyDescriptor(PropertyKey key) {
        if (finalized && isExportProperty(key)) {
            PropertyDescriptor descriptor = new PropertyDescriptor();
            descriptor.setValue(get(context, key));
            descriptor.setWritable(true);
            descriptor.setEnumerable(true);
            descriptor.setConfigurable(false);
            return descriptor;
        }
        return super.getOwnPropertyDescriptor(key);
    }

    @Override
    public List<PropertyKey> getOwnPropertyKeys() {
        List<PropertyKey> keys = super.getOwnPropertyKeys();
        if (!finalized) {
            return keys;
        }
        List<PropertyKey> stringKeys = new ArrayList<>();
        List<PropertyKey> symbolKeys = new ArrayList<>();
        for (PropertyKey key : keys) {
            if (key.isSymbol()) {
                symbolKeys.add(key);
            } else {
                stringKeys.add(key);
            }
        }
        stringKeys.sort(Comparator.comparing(PropertyKey::asString));
        List<PropertyKey> orderedKeys = new ArrayList<>(keys.size());
        orderedKeys.addAll(stringKeys);
        orderedKeys.addAll(symbolKeys);
        return orderedKeys;
    }

    private boolean isExportProperty(PropertyKey key) {
        return key != null && key.isString() && exportNames.contains(key.asString());
    }

    @Override
    public PropertyKey[] ownPropertyKeys() {
        return getOwnPropertyKeys().toArray(new PropertyKey[0]);
    }

    public void registerExportName(String exportName) {
        if (exportName != null && !exportName.isEmpty()) {
            exportNames.add(exportName);
        }
    }

    @Override
    public void set(JSContext context, PropertyKey key, JSValue value, JSObject receiver) {
        if (finalized) {
            if (context != null && context.isStrictMode()) {
                context.throwTypeError("Cannot assign to read only property '" + key.toPropertyString() + "' of [object Module]");
            }
            return;
        }
        super.set(context, key, value, receiver);
    }

    @Override
    public boolean setWithResult(JSContext context, PropertyKey key, JSValue value, JSObject receiver) {
        if (finalized) {
            return false;
        }
        return super.setWithResult(context, key, value, receiver);
    }

    private boolean validateExportDefine(PropertyKey key, PropertyDescriptor descriptor) {
        if (descriptor.isAccessorDescriptor()) {
            return false;
        }
        if (descriptor.hasConfigurable() && descriptor.isConfigurable()) {
            return false;
        }
        if (descriptor.hasEnumerable() && !descriptor.isEnumerable()) {
            return false;
        }
        if (descriptor.hasWritable() && !descriptor.isWritable()) {
            return false;
        }
        if (descriptor.hasValue()) {
            JSValue currentValue = get(context, key);
            return sameValue(currentValue, descriptor.getValue());
        }
        return true;
    }
}
