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

import com.caoccao.qjs4j.exceptions.JSException;

import java.util.*;

/**
 * Module namespace exotic-like object for dynamic import().
 * It enforces non-extensible and non-configurable exported bindings semantics.
 */
public final class JSImportNamespaceObject extends JSObject {
    private final JSContext context;
    private final Map<String, JSValue> earlyExportBindings;
    private final Set<String> exportNames;
    private boolean finalized;

    public JSImportNamespaceObject(JSContext context) {
        super(context);
        this.context = context;
        this.earlyExportBindings = new HashMap<>();
        this.exportNames = new HashSet<>();
        this.finalized = false;
        super.defineProperty(
                PropertyKey.SYMBOL_TO_STRING_TAG,
                new JSString("Module"),
                PropertyDescriptor.DataState.None);
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

    public boolean defineExportBinding(JSContext context, PropertyKey key, PropertyDescriptor descriptor) {
        if (finalized || key == null || !key.isString()) {
            return false;
        }
        boolean defined = super.defineProperty(key, descriptor);
        if (defined) {
            registerExportName(key.asString());
        }
        return defined;
    }

    public boolean defineExportBinding(JSContext context, PropertyKey key, JSValue value, PropertyDescriptor.DataState state) {
        return defineExportBinding(context, key, PropertyDescriptor.dataDescriptor(value, state));
    }

    @Override
    public boolean defineProperty(PropertyKey key, PropertyDescriptor descriptor) {
        if (isExportProperty(key)) {
            if (super.hasOwnProperty(key)) {
                return validateExportDefine(key, descriptor);
            }
            if (finalized) {
                return false;
            }
            return super.defineProperty(key, descriptor);
        }
        if (PropertyKey.SYMBOL_TO_STRING_TAG.equals(key)) {
            return super.defineProperty(key, descriptor);
        }
        if (!hasOwnProperty(key)) {
            return false;
        }
        return super.defineProperty(key, descriptor);
    }

    @Override
    public boolean delete(JSContext context, PropertyKey key) {
        JSContext effectiveContext = resolveContext(context);
        if (isExportProperty(key)) {
            if (effectiveContext.isStrictMode()) {
                effectiveContext.throwTypeError("Cannot delete property '" + key.toPropertyString() + "' of [object Module]");
            }
            return false;
        }
        return super.delete(effectiveContext, key);
    }

    @Override
    public boolean delete(PropertyKey key) {
        return delete(resolveContext(null), key);
    }

    @Override
    public boolean deleteNonStrict(PropertyKey key) {
        if (isExportProperty(key)) {
            return false;
        }
        return super.deleteNonStrict(key);
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

    public JSValue getEarlyExportBinding(String exportName) {
        if (exportName == null || exportName.isEmpty()) {
            return null;
        }
        return earlyExportBindings.get(exportName);
    }

    @Override
    public PropertyDescriptor getOwnPropertyDescriptor(PropertyKey key) {
        if (hasDefinedExportProperty(key)) {
            JSContext effectiveContext = context;
            JSValue value = get(effectiveContext, key);
            if (effectiveContext.hasPendingException()) {
                throw new JSException(effectiveContext.getPendingException());
            }
            PropertyDescriptor descriptor = new PropertyDescriptor();
            descriptor.setValue(value);
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
        List<PropertyKey> stringKeys = new ArrayList<>();
        List<PropertyKey> symbolKeys = new ArrayList<>();
        Set<String> seenStringKeys = new HashSet<>();
        for (String exportName : exportNames) {
            if (exportName == null || exportName.isEmpty()) {
                continue;
            }
            PropertyKey exportKey = PropertyKey.fromString(exportName);
            if (super.hasOwnProperty(exportKey) && seenStringKeys.add(exportName)) {
                stringKeys.add(exportKey);
            }
        }
        for (PropertyKey key : keys) {
            if (key.isString()) {
                String keyName = key.asString();
                if (seenStringKeys.add(keyName)) {
                    stringKeys.add(key);
                }
                continue;
            }
            if (key.isSymbol()) {
                symbolKeys.add(key);
            }
        }
        stringKeys.sort(Comparator.comparing(PropertyKey::asString));
        List<PropertyKey> orderedKeys = new ArrayList<>(stringKeys.size() + symbolKeys.size());
        orderedKeys.addAll(stringKeys);
        orderedKeys.addAll(symbolKeys);
        return orderedKeys;
    }

    private boolean hasDefinedExportProperty(PropertyKey key) {
        if (!isExportProperty(key)) {
            return false;
        }
        return super.hasOwnProperty(key);
    }

    public boolean hasDefinedOwnProperty(PropertyKey key) {
        return super.hasOwnProperty(key);
    }

    @Override
    public boolean hasOwnProperty(PropertyKey key) {
        return super.hasOwnProperty(key);
    }

    private boolean isExportProperty(PropertyKey key) {
        return key != null && key.isString() && exportNames.contains(key.asString());
    }

    // ES2024 10.4.6.3 [[IsExtensible]]: Always returns false.
    @Override
    public boolean isExtensible() {
        return false;
    }

    public boolean isFinalized() {
        return finalized;
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

    public void removeExportBinding(String exportName) {
        if (exportName == null || exportName.isEmpty()) {
            return;
        }
        super.delete(PropertyKey.fromString(exportName));
        exportNames.remove(exportName);
    }

    // ES2024 10.4.6.8 [[Set]]: Always returns false for module namespace objects.
    @Override
    public void set(JSContext context, PropertyKey key, JSValue value, JSObject receiver) {
        JSContext effectiveContext = resolveContext(context);
        if (effectiveContext.isStrictMode()) {
            effectiveContext.throwTypeError("Cannot assign to read only property '" + key.toPropertyString() + "' of [object Module]");
        }
    }

    @Override
    public void set(PropertyKey key, JSValue value) {
        set(resolveContext(null), key, value);
    }

    @Override
    public void set(JSContext context, PropertyKey key, JSValue value) {
        set(context, key, value, this);
    }

    public void setEarlyExportBinding(String exportName, JSValue value) {
        if (exportName == null || exportName.isEmpty() || value == null) {
            return;
        }
        earlyExportBindings.put(exportName, value);
    }

    // ES2024 10.4.6.1 [[SetPrototypeOf]]: Uses SetImmutablePrototype semantics.
    @Override
    public SetPrototypeResult setPrototypeChecked(JSObject proto) {
        JSObject currentProto = getPrototype();
        if (proto == currentProto) {
            return SetPrototypeResult.SUCCESS;
        }
        return SetPrototypeResult.NOT_EXTENSIBLE;
    }

    // ES2024 10.4.6.8 [[Set]]: Always returns false for module namespace objects.
    @Override
    public boolean setWithResult(JSContext context, PropertyKey key, JSValue value, JSObject receiver) {
        return false;
    }

    @Override
    public boolean setWithResult(JSContext context, PropertyKey key, JSValue value) {
        return false;
    }

    @Override
    public boolean setWithResult(PropertyKey key, JSValue value) {
        return false;
    }

    public void unregisterExportName(String exportName) {
        if (exportName != null && !exportName.isEmpty()) {
            exportNames.remove(exportName);
        }
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
