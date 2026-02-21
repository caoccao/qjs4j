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

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the shape (structure) of a JavaScript object.
 * Based on QuickJS shape system with mutable property lists.
 * <p>
 * Following QuickJS implementation:
 * - Shapes are mutable and can have properties removed
 * - Deleted properties are tracked and shape is compacted when threshold is reached
 * - Each object has its own shape instance (no sharing)
 * - Supports property addition, removal, and compaction
 */
public final class JSShape {
    private int deletedPropCount;
    private PropertyDescriptor[] descriptors;
    private int propertyCount;
    private PropertyKey[] propertyKeys;

    /**
     * Create an empty shape (no properties).
     */
    public JSShape() {
        this.propertyKeys = new PropertyKey[0];
        this.descriptors = new PropertyDescriptor[0];
        this.propertyCount = 0;
        this.deletedPropCount = 0;
    }

    /**
     * Create a shape by copying from another shape.
     */
    private JSShape(JSShape other) {
        this.propertyKeys = other.propertyKeys.clone();
        this.descriptors = other.descriptors.clone();
        this.propertyCount = other.propertyCount;
        this.deletedPropCount = other.deletedPropCount;
    }

    /**
     * Add a property to this shape.
     * Modifies the shape in-place.
     */
    public void addProperty(PropertyKey key, PropertyDescriptor descriptor) {
        // Check if property already exists (might be deleted)
        int existingOffset = getPropertyOffset(key);
        if (existingOffset >= 0) {
            // Property exists, merge only explicitly specified attributes
            descriptors[existingOffset].mergeFrom(descriptor);
            return;
        }

        // Grow arrays
        PropertyKey[] newKeys = new PropertyKey[propertyCount + 1];
        PropertyDescriptor[] newDescriptors = new PropertyDescriptor[propertyCount + 1];

        // Copy existing properties
        System.arraycopy(propertyKeys, 0, newKeys, 0, propertyCount);
        System.arraycopy(descriptors, 0, newDescriptors, 0, propertyCount);

        // Add new property
        newKeys[propertyCount] = key;
        newDescriptors[propertyCount] = descriptor;

        this.propertyKeys = newKeys;
        this.descriptors = newDescriptors;
        this.propertyCount++;
    }

    /**
     * Compact the shape by removing deleted properties.
     * Creates new arrays without deleted properties.
     * Following QuickJS compact_properties() logic.
     */
    public void compact() {
        if (deletedPropCount == 0) {
            return; // Nothing to compact
        }

        List<PropertyKey> newKeys = new ArrayList<>(propertyCount - deletedPropCount);
        List<PropertyDescriptor> newDescriptors = new ArrayList<>(propertyCount - deletedPropCount);

        // Copy non-null properties
        for (int i = 0; i < propertyCount; i++) {
            if (propertyKeys[i] != null) {
                newKeys.add(propertyKeys[i]);
                newDescriptors.add(descriptors[i]);
            }
        }

        // Update arrays
        this.propertyKeys = newKeys.toArray(new PropertyKey[0]);
        this.descriptors = newDescriptors.toArray(new PropertyDescriptor[0]);
        this.propertyCount = newKeys.size();
        this.deletedPropCount = 0;
    }

    /**
     * Create a copy of this shape.
     */
    public JSShape copy() {
        return new JSShape(this);
    }

    /**
     * Get the count of deleted properties.
     */
    public int getDeletedPropCount() {
        return deletedPropCount;
    }

    /**
     * Get the descriptor for a property.
     * Returns null if property not found or deleted.
     */
    public PropertyDescriptor getDescriptor(PropertyKey key) {
        int offset = getPropertyOffset(key);
        return offset >= 0 ? descriptors[offset] : null;
    }

    /**
     * Get the descriptor at a specific offset.
     * Returns null if offset is invalid or property is deleted.
     */
    public PropertyDescriptor getDescriptorAt(int offset) {
        if (offset < 0 || offset >= propertyCount) {
            return null;
        }
        // Check if deleted
        if (propertyKeys[offset] == null) {
            return null;
        }
        return descriptors[offset];
    }

    /**
     * Get all property descriptors in this shape (excluding deleted).
     */
    public PropertyDescriptor[] getDescriptors() {
        List<PropertyDescriptor> result = new ArrayList<>(propertyCount - deletedPropCount);
        for (int i = 0; i < propertyCount; i++) {
            if (propertyKeys[i] != null) {
                result.add(descriptors[i]);
            }
        }
        return result.toArray(new PropertyDescriptor[0]);
    }

    /**
     * Get the number of properties in this shape (including deleted).
     */
    public int getPropertyCount() {
        return propertyCount;
    }

    /**
     * Get all property keys in this shape (excluding deleted).
     */
    public PropertyKey[] getPropertyKeys() {
        List<PropertyKey> result = new ArrayList<>(propertyCount - deletedPropCount);
        for (int i = 0; i < propertyCount; i++) {
            if (propertyKeys[i] != null) {
                result.add(propertyKeys[i]);
            }
        }
        return result.toArray(new PropertyKey[0]);
    }

    /**
     * Get the offset of a property in the property array.
     * Returns -1 if property not found or deleted.
     */
    public int getPropertyOffset(PropertyKey key) {
        for (int i = 0; i < propertyCount; i++) {
            if (propertyKeys[i] != null && propertyKeys[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Check if this shape has a property (not deleted).
     */
    public boolean hasProperty(PropertyKey key) {
        return getPropertyOffset(key) >= 0;
    }

    /**
     * Remove a property from this shape.
     * Marks the property as deleted (sets key to null).
     * Following QuickJS delete_property() logic.
     *
     * @return true if property was removed, false if not found or not configurable
     */
    public boolean removeProperty(PropertyKey key) {
        int offset = getPropertyOffset(key);
        if (offset < 0) {
            return true; // Property doesn't exist, deletion successful
        }

        // Check if configurable
        PropertyDescriptor desc = descriptors[offset];
        if (!desc.isConfigurable()) {
            return false; // Cannot delete non-configurable property
        }

        // Mark as deleted (QuickJS sets atom to JS_ATOM_NULL)
        propertyKeys[offset] = null;
        descriptors[offset] = null;
        deletedPropCount++;

        return true;
    }

    /**
     * Check if compaction should be performed.
     * Following QuickJS logic: compact if deleted >= 8 AND deleted >= prop_count/2
     */
    public boolean shouldCompact() {
        return deletedPropCount >= 8 &&
                deletedPropCount >= propertyCount / 2;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("JSShape{");
        sb.append("properties=[");
        boolean first = true;
        for (int i = 0; i < propertyCount; i++) {
            if (propertyKeys[i] != null) {
                if (!first) { sb.append(", "); }
                sb.append(propertyKeys[i].toPropertyString());
                first = false;
            }
        }
        sb.append("], total=").append(propertyCount);
        sb.append(", deleted=").append(deletedPropCount);
        sb.append("}");
        return sb.toString();
    }
}
