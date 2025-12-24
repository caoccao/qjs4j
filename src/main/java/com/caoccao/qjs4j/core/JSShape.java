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

import java.util.Map;

/**
 * Represents the shape (hidden class) of a JavaScript object.
 * Shapes enable efficient property access through inline caching.
 */
public final class JSShape {
    private final int[] propertyKeys;
    private final PropertyDescriptor[] descriptors;
    private final JSShape parent;
    private final Map<PropertyKey, JSShape> transitions;

    public JSShape(int[] propertyKeys, PropertyDescriptor[] descriptors, JSShape parent) {
        this.propertyKeys = propertyKeys;
        this.descriptors = descriptors;
        this.parent = parent;
        this.transitions = null;
    }

    public int getPropertyOffset(int propertyKey) {
        return -1;
    }

    public JSShape addProperty(PropertyKey key, PropertyDescriptor descriptor) {
        return null;
    }

    public int[] getPropertyKeys() {
        return propertyKeys;
    }

    public PropertyDescriptor[] getDescriptors() {
        return descriptors;
    }

    public JSShape getParent() {
        return parent;
    }
}
