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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the shape (structure) of a JavaScript object.
 * Based on QuickJS shape system with mutable property lists.
 * <p>
 * Following QuickJS implementation:
 * - Shapes are mutable and can have properties removed
 * - Deleted properties are tracked and shape is compacted when threshold is reached
 * - Each object has its own shape instance (no sharing)
 * - Supports property addition, removal, and compaction
 * <p>
 * For shapes with more than INDEX_THRESHOLD properties, a HashMap index is maintained
 * for O(1) property offset lookups instead of O(N) linear scans.
 */
public final class JSShape {
    private static final PropertyDescriptor[] EMPTY_DESCRIPTORS = new PropertyDescriptor[0];
    private static final PropertyKey[] EMPTY_KEYS = new PropertyKey[0];
    private static final int INDEX_THRESHOLD = 6;
    private static final int INITIAL_CAPACITY = 4;
    private int deletedPropCount;
    private PropertyDescriptor[] descriptors;
    private Object lastLookupIndexKey;
    private int lastLookupOffset;
    private int lastLookupShapeVersion;
    private int propertyCount;
    private Map<Object, Integer> propertyIndex;
    private PropertyKey[] propertyKeys;
    private int shapeVersion;

    /**
     * Create an empty shape (no properties).
     */
    public JSShape() {
        this.propertyKeys = EMPTY_KEYS;
        this.descriptors = EMPTY_DESCRIPTORS;
        this.propertyCount = 0;
        this.deletedPropCount = 0;
        this.shapeVersion = 0;
        this.lastLookupShapeVersion = -1;
        this.lastLookupOffset = -1;
        this.lastLookupIndexKey = null;
    }

    /**
     * Create a shape by copying from another shape.
     */
    private JSShape(JSShape other) {
        this.propertyKeys = other.propertyKeys.clone();
        this.descriptors = other.descriptors.clone();
        this.propertyCount = other.propertyCount;
        this.deletedPropCount = other.deletedPropCount;
        this.shapeVersion = other.shapeVersion;
        this.lastLookupShapeVersion = -1;
        this.lastLookupOffset = -1;
        this.lastLookupIndexKey = null;
        if (other.propertyIndex != null) {
            this.propertyIndex = new HashMap<>(other.propertyIndex);
        }
    }

    /**
     * Create a shape with pre-defined properties in bulk.
     * Avoids the O(N²) cost of calling addProperty repeatedly on a fresh shape.
     */
    JSShape(PropertyKey[] keys, PropertyDescriptor[] descriptors) {
        this.propertyKeys = keys;
        this.descriptors = descriptors;
        this.propertyCount = keys.length;
        this.deletedPropCount = 0;
        this.shapeVersion = 0;
        this.lastLookupShapeVersion = -1;
        this.lastLookupOffset = -1;
        this.lastLookupIndexKey = null;
        if (propertyCount > INDEX_THRESHOLD) {
            buildIndex();
        }
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

        int newPropertyCount = propertyCount + 1;
        ensureCapacity(newPropertyCount);
        propertyKeys[propertyCount] = key;
        descriptors[propertyCount] = descriptor;
        if (propertyIndex != null) {
            propertyIndex.put(key.getValue(), propertyCount);
        }
        propertyCount = newPropertyCount;
        if (propertyIndex == null && propertyCount > INDEX_THRESHOLD) {
            buildIndex();
        }
        onShapeMutated();
    }

    private void buildIndex() {
        propertyIndex = new HashMap<>(propertyCount * 4 / 3 + 1);
        for (int i = 0; i < propertyCount; i++) {
            if (propertyKeys[i] != null) {
                propertyIndex.put(propertyKeys[i].getValue(), i);
            }
        }
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

        int keptPropertyCount = propertyCount - deletedPropCount;
        PropertyKey[] compactedPropertyKeys = new PropertyKey[keptPropertyCount];
        PropertyDescriptor[] compactedPropertyDescriptors = new PropertyDescriptor[keptPropertyCount];
        int newOffset = 0;
        for (int i = 0; i < propertyCount; i++) {
            if (propertyKeys[i] != null) {
                compactedPropertyKeys[newOffset] = propertyKeys[i];
                compactedPropertyDescriptors[newOffset] = descriptors[i];
                newOffset++;
            }
        }

        propertyKeys = compactedPropertyKeys;
        descriptors = compactedPropertyDescriptors;
        propertyCount = keptPropertyCount;
        this.deletedPropCount = 0;
        onShapeMutated();

        // Rebuild index if needed
        if (propertyCount > INDEX_THRESHOLD) {
            buildIndex();
        } else {
            propertyIndex = null;
        }
    }

    /**
     * Create a copy of this shape.
     */
    public JSShape copy() {
        return new JSShape(this);
    }

    private void ensureCapacity(int requiredCapacity) {
        int currentCapacity = propertyKeys.length;
        if (currentCapacity >= requiredCapacity) {
            return;
        }
        int newCapacity;
        if (currentCapacity > 0) {
            newCapacity = currentCapacity;
            while (newCapacity < requiredCapacity) {
                newCapacity <<= 1;
            }
        } else {
            newCapacity = INITIAL_CAPACITY;
            while (newCapacity < requiredCapacity) {
                newCapacity <<= 1;
            }
        }
        PropertyKey[] newPropertyKeys = new PropertyKey[newCapacity];
        PropertyDescriptor[] newPropertyDescriptors = new PropertyDescriptor[newCapacity];
        if (propertyCount > 0) {
            System.arraycopy(propertyKeys, 0, newPropertyKeys, 0, propertyCount);
            System.arraycopy(descriptors, 0, newPropertyDescriptors, 0, propertyCount);
        }
        propertyKeys = newPropertyKeys;
        descriptors = newPropertyDescriptors;
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
     * Get the property key at a specific offset.
     * Returns null if offset is invalid or property is deleted.
     */
    public PropertyKey getPropertyKeyAt(int offset) {
        if (offset < 0 || offset >= propertyCount) {
            return null;
        }
        return propertyKeys[offset];
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
     * Uses HashMap index for shapes with more than INDEX_THRESHOLD properties.
     */
    public int getPropertyOffset(PropertyKey key) {
        if (key == null) {
            return -1;
        }
        return getPropertyOffsetByIndexKey(key.getValue());
    }

    /**
     * Get the offset of a property by raw index key (String / Integer / JSSymbol).
     * Returns -1 if property not found or deleted.
     */
    public int getPropertyOffsetByIndexKey(Object indexKey) {
        if (indexKey == null) {
            return -1;
        }
        if (lastLookupShapeVersion == shapeVersion && lastLookupIndexKey == indexKey) {
            return lastLookupOffset;
        }
        int offset;
        if (propertyIndex != null) {
            Integer cachedOffset = propertyIndex.get(indexKey);
            offset = cachedOffset != null ? cachedOffset : -1;
        } else {
            offset = -1;
            for (int i = 0; i < propertyCount; i++) {
                PropertyKey propertyKey = propertyKeys[i];
                if (propertyKey == null) {
                    continue;
                }
                Object propertyValue = propertyKey.getValue();
                if (propertyValue == indexKey || propertyValue.equals(indexKey)) {
                    offset = i;
                    break;
                }
            }
        }
        lastLookupShapeVersion = shapeVersion;
        lastLookupIndexKey = indexKey;
        lastLookupOffset = offset;
        return offset;
    }

    /**
     * Check if this shape has a property (not deleted).
     */
    public boolean hasProperty(PropertyKey key) {
        return getPropertyOffset(key) >= 0;
    }

    private void onShapeMutated() {
        shapeVersion++;
        lastLookupShapeVersion = -1;
        lastLookupIndexKey = null;
        lastLookupOffset = -1;
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
        onShapeMutated();

        // Remove from index
        if (propertyIndex != null) {
            propertyIndex.remove(key.getValue());
        }

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
                if (!first) {
                    sb.append(", ");
                }
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
