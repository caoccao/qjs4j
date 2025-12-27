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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the shape (hidden class) of a JavaScript object.
 * Based on QuickJS shape system and V8's hidden classes.
 * <p>
 * Shapes enable:
 * - Efficient property access through stable property offsets
 * - Inline caching for property access
 * - Memory-efficient property storage (shared shape metadata)
 * - Shape transitions when properties are added
 * <p>
 * Shapes form a tree structure where:
 * - Root shape has no properties
 * - Each property addition creates a child shape
 * - Objects with same property sequence share shapes
 */
public final class JSShape {
    private static final JSShape ROOT = new JSShape();

    private final PropertyKey[] propertyKeys;
    private final PropertyDescriptor[] descriptors;
    private final JSShape parent;
    private final int propertyCount;

    // Transition map: property key -> child shape
    // Used to find or create child shapes when properties are added
    private final Map<TransitionKey, JSShape> transitions;

    /**
     * Create root shape (no properties).
     */
    private JSShape() {
        this.propertyKeys = new PropertyKey[0];
        this.descriptors = new PropertyDescriptor[0];
        this.parent = null;
        this.propertyCount = 0;
        this.transitions = new ConcurrentHashMap<>();
    }

    /**
     * Create a shape by adding a property to a parent shape.
     */
    private JSShape(JSShape parent, PropertyKey key, PropertyDescriptor descriptor) {
        this.parent = parent;
        this.propertyCount = parent.propertyCount + 1;

        // Copy parent's properties and add new one
        this.propertyKeys = Arrays.copyOf(parent.propertyKeys, propertyCount);
        this.descriptors = Arrays.copyOf(parent.descriptors, propertyCount);

        this.propertyKeys[propertyCount - 1] = key;
        this.descriptors[propertyCount - 1] = descriptor;

        this.transitions = new HashMap<>();
    }

    /**
     * Get the root shape (empty object shape).
     */
    public static JSShape getRoot() {
        return ROOT;
    }

    /**
     * Add a property to this shape, returning a new shape.
     * Uses transitions to share shapes when possible.
     */
    public JSShape addProperty(PropertyKey key, PropertyDescriptor descriptor) {
        TransitionKey transKey = new TransitionKey(key, descriptor);

        // Check if we already have a transition for this property
        JSShape existing = transitions.get(transKey);
        if (existing != null) {
            return existing;
        }

        // Create new child shape
        JSShape newShape = new JSShape(this, key, descriptor);
        transitions.put(transKey, newShape);
        return newShape;
    }

    /**
     * Get the offset of a property in the property array.
     * Returns -1 if property not found.
     */
    public int getPropertyOffset(PropertyKey key) {
        for (int i = 0; i < propertyCount; i++) {
            if (propertyKeys[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Get the descriptor for a property.
     * Returns null if property not found.
     */
    public PropertyDescriptor getDescriptor(PropertyKey key) {
        int offset = getPropertyOffset(key);
        return offset >= 0 ? descriptors[offset] : null;
    }

    /**
     * Get the descriptor at a specific offset.
     */
    public PropertyDescriptor getDescriptorAt(int offset) {
        return offset >= 0 && offset < propertyCount ? descriptors[offset] : null;
    }

    /**
     * Check if this shape has a property.
     */
    public boolean hasProperty(PropertyKey key) {
        return getPropertyOffset(key) >= 0;
    }

    /**
     * Get all property keys in this shape.
     */
    public PropertyKey[] getPropertyKeys() {
        return Arrays.copyOf(propertyKeys, propertyCount);
    }

    /**
     * Get all property descriptors in this shape.
     */
    public PropertyDescriptor[] getDescriptors() {
        return Arrays.copyOf(descriptors, propertyCount);
    }

    /**
     * Get the number of properties in this shape.
     */
    public int getPropertyCount() {
        return propertyCount;
    }

    /**
     * Get the parent shape.
     */
    public JSShape getParent() {
        return parent;
    }

    /**
     * Check if this is the root shape.
     */
    public boolean isRoot() {
        return parent == null;
    }

    /**
     * Get the depth of this shape in the transition tree.
     */
    public int getDepth() {
        int depth = 0;
        JSShape current = this;
        while (current.parent != null) {
            depth++;
            current = current.parent;
        }
        return depth;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("JSShape{");
        sb.append("properties=[");
        for (int i = 0; i < propertyCount; i++) {
            if (i > 0) sb.append(", ");
            sb.append(propertyKeys[i].toPropertyString());
        }
        sb.append("], depth=").append(getDepth());
        sb.append("}");
        return sb.toString();
    }

    /**
         * Key for shape transitions.
         * Combines property key and descriptor attributes to determine
         * if two property additions should share a shape.
         */
        private record TransitionKey(PropertyKey propertyKey, int descriptorFlags) {
            private TransitionKey(PropertyKey propertyKey, PropertyDescriptor descriptorFlags) {
                this.propertyKey = propertyKey;
                // Only care about enumerable/configurable for transitions
                // (writable and value don't affect shape)
                this.descriptorFlags =
                        (descriptorFlags.isEnumerable() ? 1 : 0) |
                                (descriptorFlags.isConfigurable() ? 2 : 0) |
                                (descriptorFlags.isDataDescriptor() ? 4 : 0) |
                                (descriptorFlags.isAccessorDescriptor() ? 8 : 0);
            }

            @Override
            public boolean equals(Object obj) {
                if (!(obj instanceof TransitionKey other)) return false;
                return propertyKey.equals(other.propertyKey) &&
                        descriptorFlags == other.descriptorFlags;
            }

    }
}
