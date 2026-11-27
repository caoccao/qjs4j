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

import com.caoccao.qjs4j.core.buffer.JSTypedArray;
import com.caoccao.qjs4j.exceptions.JSRangeErrorException;

import java.util.*;

/**
 * Represents a JavaScript Array object. Based on QuickJS array implementation.
 * <p>
 * Uses dual storage strategy: - Dense array for consecutive indices [0, 1, 2, ...] - Sparse map (inherited from
 * JSObject) for gaps or large indices
 * <p>
 * Automatically switches between dense and sparse based on usage patterns.
 */
public final class JSArray extends JSObject {
    private static final double HOLE;
    /**
     * Bits of the NaN that marks a hole in {@link #doubleArray}. A stored NaN is always canonicalized, so no element
     * value has these bits.
     */
    private static final long HOLE_BITS = 0x7FFC_0DEA_DBEE_F001L;
    public static final int INITIAL_CAPACITY = 8;
    private static final long MAX_ARRAY_INDEX = 0xFFFF_FFFEL; // 2^32 - 2
    private static final long MAX_ARRAY_LENGTH = 0xFFFF_FFFFL; // 2^32 - 1
    /**
     * Largest dense storage a JVM array can hold.
     */
    private static final int MAX_DENSE_CAPACITY = Integer.MAX_VALUE - 8;
    /**
     * Dense storage of up to this many elements is used for any index, however sparse the array; the bound on a
     * capacity hint, and on how far an isolated write may grow dense storage.
     */
    public static final int MAX_DENSE_SIZE = 10000;
    /**
     * Bound on the prototype chain walk performed before an indexed write to a hole. A real Array prototype chain is
     * two links; the bound only stops a cyclic chain.
     */
    private static final int MAX_PROTOTYPE_SET_DEPTH = 1000;
    public static final String NAME = "Array";
    private static final double UINT32_MAX_DOUBLE = 4_294_967_295d;
    private static final double UINT32_MODULO = 4_294_967_296d;

    static {
        HOLE = Double.longBitsToDouble(HOLE_BITS);
    }

    private JSValue[] denseArray;
    /**
     * Dense elements of an array that has held only numbers, unboxed, or null once any other value is stored. Exactly
     * one of this and {@link #denseArray} is non-null.
     * <p>
     * Every element used to be a {@link JSNumber} reference — a 24-byte object plus the reference for each number — so
     * an array of 3e7 numbers needed more than 1 GB where V8 needs about 240 MB. Elements are stored as doubles until
     * the first non-number arrives, when the storage turns into references for good, as V8's element kinds do.
     */
    private double[] doubleArray;
    private long length;
    /**
     * Index of the non-configurable element that stopped the most recent length truncation, or -1.
     */
    private long lengthTruncationBlockedIndex = -1;

    /**
     * Create an empty array.
     */
    public JSArray(JSContext context) {
        this(context, 0);
    }

    /**
     * Create an array from values.
     */
    public JSArray(JSContext context, JSValue... values) {
        super(context);
        this.length = values.length;
        initializeDenseStorage(values, Math.max(values.length, INITIAL_CAPACITY), false);
        // Mark as array class (equivalent to QuickJS class_id == JS_CLASS_ARRAY)
        this.arrayObject = true;
        initializeLengthProperty();
    }

    /**
     * Create an array from values taking ownership of the provided dense storage. Internal fast path: caller must not
     * reuse or mutate {@code ownedValues}.
     */
    JSArray(JSContext context, JSValue[] ownedValues, boolean takeOwnership) {
        super(context);
        this.length = ownedValues.length;
        initializeDenseStorage(ownedValues, Math.max(ownedValues.length, INITIAL_CAPACITY), takeOwnership);
        // Mark as array class (equivalent to QuickJS class_id == JS_CLASS_ARRAY)
        this.arrayObject = true;
        initializeLengthProperty();
    }

    /**
     * Create an array with a specific initial length.
     */
    public JSArray(JSContext context, long length) {
        this(context, length, INITIAL_CAPACITY);
    }

    /**
     * Create an array with a specific initial length and a dense-storage capacity hint.
     * <p>
     * The hint is clamped into {@code [INITIAL_CAPACITY, MAX_DENSE_SIZE]}. Clamping it down to {@code INITIAL_CAPACITY}
     * with {@code Math.min} made every capacity hint in the engine a no-op, and let a negative hint — produced by
     * narrowing a length above {@code 2^31} to {@code int} — reach {@code new JSValue[capacity]} as a
     * {@link NegativeArraySizeException}.
     *
     * @param context
     *            the owning context
     * @param length
     *            the array length; must be a valid ECMAScript array length
     * @param capacity
     *            the dense-storage capacity hint
     * @throws JSRangeErrorException
     *             when {@code length} is not in {@code [0, 2^32 - 1]}
     */
    public JSArray(JSContext context, long length, int capacity) {
        super(context);
        // Validate the semantic length, not only the capacity hint. Clamping the hint stopped the
        // NegativeArraySizeException a negative length used to cause, but it hid the invalid state
        // rather than rejecting it: createJSArray(-1) produced an array reporting length -1, and
        // createJSArray(2^32) one reporting 4294967296. Neither is an ECMAScript array length, and
        // every later operation on such an array is undefined behaviour.
        requireValidLength(length);
        this.length = length;
        capacity = Math.max(INITIAL_CAPACITY, Math.min(capacity, MAX_DENSE_SIZE));
        this.doubleArray = new double[capacity];
        Arrays.fill(this.doubleArray, HOLE);
        // Mark as array class (equivalent to QuickJS class_id == JS_CLASS_ARRAY)
        this.arrayObject = true;
        initializeLengthProperty();
    }

    /**
     * Turn dense storage into references for good, the first time a value other than a number is stored.
     */
    private void boxDenseStorage() {
        double[] doubles = doubleArray;
        JSValue[] values = new JSValue[doubles.length];
        for (int i = 0; i < doubles.length; i++) {
            double value = doubles[i];
            if (Double.doubleToRawLongBits(value) != HOLE_BITS) {
                values[i] = JSNumber.of(value);
            }
        }
        denseArray = values;
        doubleArray = null;
    }

    /**
     * Make the dense slots in {@code [from, to)} holes.
     */
    private void clearDense(int from, int to) {
        if (from >= to) {
            return;
        }
        if (doubleArray != null) {
            Arrays.fill(doubleArray, from, to, HOLE);
        } else {
            Arrays.fill(denseArray, from, to, null);
        }
    }

    /**
     * Take the index that last blocked a length truncation, clearing it.
     * <p>
     * Set by {@link #defineProperty(PropertyKey, PropertyDescriptor)} and read by whichever caller is going to throw,
     * so that a non-throwing caller such as {@code Reflect.defineProperty} is unaffected.
     *
     * @return the blocking index, or -1 when the last length truncation was not blocked
     */
    private long consumeLengthTruncationBlockedIndex() {
        long blockedIndex = lengthTruncationBlockedIndex;
        lengthTruncationBlockedIndex = -1;
        return blockedIndex;
    }

    @Override
    public boolean defineProperty(PropertyKey key, PropertyDescriptor descriptor) {
        // Per ES spec ArraySetLength (10.4.2.4) / QuickJS set_array_length:
        // When defining "length" with a value, coerce BEFORE descriptor validation
        if (key.isString() && "length".equals(key.asString()) && descriptor.hasValue()) {
            Long newLength = toArrayLengthForLengthProperty(this.context, descriptor.getValue());
            if (newLength == null) {
                return false; // coercion error (RangeError pending)
            }
            descriptor.setValue(JSNumber.of(newLength));

            // Step 7-9: Get old length descriptor
            PropertyDescriptor oldLenDesc = getOwnPropertyDescriptor(key);

            // Validate non-value attributes against non-configurable length property.
            // Array length is always non-configurable (ES2024 10.4.2.4 step 9).
            if (oldLenDesc != null && !oldLenDesc.isConfigurable()) {
                if (descriptor.hasConfigurable() && descriptor.isConfigurable()) {
                    return false;
                }
                if (descriptor.hasEnumerable() && descriptor.isEnumerable() != oldLenDesc.isEnumerable()) {
                    return false;
                }
            }

            // Step 12: If oldLenDesc.[[Writable]] is false, return false
            if (oldLenDesc != null && !oldLenDesc.isWritable()) {
                if (descriptor.hasWritable() && descriptor.isWritable()) {
                    return false;
                }
                long oldLen = this.length;
                if (newLength != oldLen) {
                    return false;
                }
                super.defineProperty(key, descriptor);
                return true;
            }

            long oldLength = this.length;

            // Step 11: If newLen >= oldLen, just apply
            if (newLength >= oldLength) {
                setLength(newLength);
                super.defineProperty(key, descriptor);
                return true;
            }

            // Step 13-14: Defer writable:false
            boolean newWritable = !descriptor.hasWritable() || descriptor.isWritable();

            // Step 17: Delete elements from oldLen-1 down to newLen.
            // Non-configurable elements stop deletion (QuickJS set_array_length).
            // Scan shape properties to find the highest non-configurable index >= newLen.
            long actualNewLength = newLength;
            long blockingIndex = -1;
            for (PropertyKey shapeKey : shape.getPropertyKeys()) {
                long idx = shapeKey.toArrayIndex();
                if (idx >= newLength && idx < oldLength) {
                    PropertyDescriptor elemDesc = super.getOwnPropertyDescriptorRaw(shapeKey);
                    if (elemDesc != null && !elemDesc.isConfigurable()) {
                        if (idx + 1 > actualNewLength) {
                            blockingIndex = idx;
                        }
                        actualNewLength = Math.max(actualNewLength, idx + 1);
                    }
                }
            }
            // Remember which element stopped the truncation. A throwing caller reports that, the
            // way V8 does — the delete is the operation that actually failed, so "Cannot delete
            // property 'N' of [object Array]" says what went wrong where a message about the
            // length assignment does not. Recorded rather than thrown here because
            // Reflect.defineProperty must return false without an exception.
            lengthTruncationBlockedIndex = blockingIndex;

            // Truncate to the actual achievable length
            setLength(actualNewLength);

            // Apply the length descriptor with the actual length
            PropertyDescriptor lengthDesc = new PropertyDescriptor();
            lengthDesc.setValue(JSNumber.of(actualNewLength));
            if (!newWritable) {
                lengthDesc.setWritable(false);
            }
            super.defineProperty(key, lengthDesc);

            // Step 17.b: If non-configurable element prevented full truncation, return false
            return actualNewLength <= newLength;

            // Step 18: If newWritable is false, apply writable:false
            // (already applied above in lengthDesc)
        }
        // Per ES spec 10.4.2.1 [[DefineOwnProperty]] / QuickJS JS_CreateProperty:
        // When defining a property at an array index >= current length,
        // update the length to index + 1 (if length is writable).
        long index = key.toArrayIndex();
        if (index >= 0 && index >= this.length) {
            if (!isLengthWritable()) {
                this.context.throwTypeError("Cannot add property " + index + ", array length is not writable");
                return false;
            }
            boolean result = super.defineProperty(key, descriptor);
            if (result) {
                setLength(index + 1);
            }
            return result;
        }
        return super.defineProperty(key, descriptor);
    }

    @Override
    protected void definePropertyInternal(PropertyKey key, PropertyDescriptor descriptor) {
        long index = key.toArrayIndex();
        if (index >= 0 && index <= Integer.MAX_VALUE) {
            int intIndex = (int) index;
            // For default data descriptors (writable, enumerable, configurable),
            // store in array storage (denseArray/sparseProperties) rather than shape.
            if (descriptor.isDataDescriptor() && !descriptor.isAccessorDescriptor() && descriptor.isWritable()
                    && descriptor.isEnumerable() && descriptor.isConfigurable()) {
                if (index >= length) {
                    if (isLengthWritable()) {
                        setLength(index + 1);
                    } else {
                        // Can't extend length, fall through to shape-based storage.
                        super.definePropertyInternal(key, descriptor);
                        return;
                    }
                }
                PropertyKey shapeKey = getOwnShapeKey(key);
                if (shapeKey != null) {
                    int shapeOffset = shape.getPropertyOffset(shapeKey);
                    shape.removeProperty(shapeKey);
                    if (shapeOffset >= 0) {
                        propertyValues[shapeOffset] = JSUndefined.INSTANCE;
                    }
                    if (shape.shouldCompact()) {
                        compactProperties();
                    }
                }
                putElement(intIndex, descriptor.getValue());
                return;
            }
            // Non-default descriptor: clear dense entry so shape storage takes precedence.
            if (intIndex < denseCapacity()) {
                setDenseValue(intIndex, null);
            }
        }
        super.definePropertyInternal(key, descriptor);
    }

    @Override
    public boolean delete(PropertyKey key, boolean throwOnFailure) {
        long index = key.toArrayIndex();
        if (index < 0) {
            return super.delete(key, throwOnFailure);
        }

        if (sealed || frozen) {
            PropertyKey stringKey = key.isString() ? key : PropertyKey.fromIndexString(index);
            return super.delete(stringKey, throwOnFailure);
        }

        if (index <= Integer.MAX_VALUE) {
            int intIndex = (int) index;
            if (hasDenseValue(intIndex)) {
                setDenseValue(intIndex, null);
                return true;
            }
            if (sparseProperties != null && sparseProperties.remove(intIndex) != null) {
                return true;
            }
        }

        return super.delete(key, throwOnFailure);
    }

    private int denseCapacity() {
        return doubleArray != null ? doubleArray.length : denseArray.length;
    }

    /**
     * The dense element at an index, or null for a hole or an index beyond the dense capacity.
     */
    private JSValue denseValue(int index) {
        if (doubleArray != null) {
            if (index < doubleArray.length) {
                double value = doubleArray[index];
                if (Double.doubleToRawLongBits(value) != HOLE_BITS) {
                    return JSNumber.of(value);
                }
            }
            return null;
        }
        return index < denseArray.length ? denseArray[index] : null;
    }

    /**
     * Ensure dense array has sufficient capacity, moving any element stored sparsely below the new capacity into it so
     * that an index lives in one storage.
     */
    private void ensureDenseCapacity(int requiredCapacity) {
        int capacity = denseCapacity();
        if (requiredCapacity <= capacity) {
            return;
        }

        int newCapacity = (int) Math.min(Math.max(capacity * 2L, requiredCapacity), MAX_DENSE_CAPACITY);
        if (doubleArray != null) {
            doubleArray = Arrays.copyOf(doubleArray, newCapacity);
            Arrays.fill(doubleArray, capacity, newCapacity, HOLE);
        } else {
            denseArray = Arrays.copyOf(denseArray, newCapacity);
        }
        if (sparseProperties != null) {
            List<Map.Entry<Integer, JSValue>> migrated = new ArrayList<>();
            Iterator<Map.Entry<Integer, JSValue>> iterator = sparseProperties.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, JSValue> entry = iterator.next();
                int index = entry.getKey();
                if (index < newCapacity) {
                    migrated.add(entry);
                    iterator.remove();
                }
            }
            for (Map.Entry<Integer, JSValue> entry : migrated) {
                if (!hasDenseValue(entry.getKey())) {
                    setDenseValue(entry.getKey(), entry.getValue());
                }
            }
            if (sparseProperties.isEmpty()) {
                sparseProperties = null;
            }
        }
    }

    @Override
    public PropertyKey[] enumerableKeys() {
        return getOwnPropertyKeysInternal(true).toArray(new PropertyKey[0]);
    }

    @Override
    public JSValue get(int index) {
        return get((long) index);
    }

    /**
     * Get element at index.
     */
    public JSValue get(long index) {
        if (index < 0) {
            return JSUndefined.INSTANCE;
        }

        JSValue value = storedElement(index);
        return value != null ? value : super.get(PropertyKey.fromIndexString(index));
    }

    /**
     * Override get by PropertyKey to handle array indices.
     */
    @Override
    public JSValue get(PropertyKey key) {
        long index = key.toArrayIndex();
        if (index >= 0) {
            // Check shape for accessor properties first (e.g., Object.defineProperty with getter)
            PropertyDescriptor desc = super.getOwnPropertyDescriptorRaw(key);
            if (desc != null && desc.hasGetter()) {
                return super.get(key);
            }
            JSValue value = storedElement(index);
            if (value != null) {
                return value;
            }
        }
        return super.get(key);
    }

    /**
     * Get the array length.
     */
    public long getLength() {
        return length;
    }

    @Override
    protected PropertyDescriptor getOwnPropertyDescriptorRaw(PropertyKey key) {
        PropertyDescriptor descriptor = super.getOwnPropertyDescriptorRaw(key);
        if (descriptor != null) {
            return descriptor;
        }

        long index = key.toArrayIndex();
        if (index < 0) {
            return null;
        }

        if (index <= Integer.MAX_VALUE) {
            int intIndex = (int) index;
            JSValue denseValue = denseValue(intIndex);
            if (denseValue != null) {
                return PropertyDescriptor.dataDescriptor(denseValue, PropertyDescriptor.DataState.All);
            }
            if (sparseProperties != null) {
                JSValue sparseValue = sparseProperties.get(intIndex);
                if (sparseValue != null) {
                    return PropertyDescriptor.dataDescriptor(sparseValue, PropertyDescriptor.DataState.All);
                }
            }
        } else {
            // getOwnPropertyDescriptorRaw, not getOwnPropertyDescriptor: the public method
            // dispatches back to this override, so `super.` on it recurses forever.
            return super.getOwnPropertyDescriptorRaw(PropertyKey.fromIndexString(index));
        }

        return null;
    }

    @Override
    public List<PropertyKey> getOwnPropertyKeys() {
        return getOwnPropertyKeysInternal(false);
    }

    private List<PropertyKey> getOwnPropertyKeysInternal(boolean enumerableOnly) {
        if (sparseProperties == null && shape.getDeletedPropCount() == 0 && shape.getPropertyCount() == 1
                && PropertyKey.LENGTH.equals(shape.getPropertyKeyAt(0))) {
            long denseLimit = Math.min(length, denseCapacity());
            boolean packedDenseRange = denseLimit == length;
            if (packedDenseRange) {
                for (int index = 0; index < denseLimit; index++) {
                    if (!hasDenseValue(index)) {
                        packedDenseRange = false;
                        break;
                    }
                }
            }
            if (packedDenseRange && denseLimit <= Integer.MAX_VALUE) {
                int ownKeyCount = (int) denseLimit + (enumerableOnly ? 0 : 1);
                List<PropertyKey> ownKeys = new ArrayList<>(ownKeyCount);
                for (int index = 0; index < denseLimit; index++) {
                    ownKeys.add(PropertyKey.fromIndex(index));
                }
                if (!enumerableOnly) {
                    ownKeys.add(PropertyKey.LENGTH);
                }
                return ownKeys;
            }
        }

        List<Map.Entry<Long, PropertyKey>> numericKeys = new ArrayList<>();
        Set<Long> seenNumericIndices = new HashSet<>();
        List<PropertyKey> stringKeys = new ArrayList<>();
        List<PropertyKey> symbolKeys = new ArrayList<>();
        Set<PropertyKey> seenPropertyKeys = new HashSet<>();

        long denseLimit = Math.min(length, denseCapacity());
        for (int i = 0; i < denseLimit; i++) {
            if (hasDenseValue(i)) {
                long numericIndex = Integer.toUnsignedLong(i);
                if (seenNumericIndices.add(numericIndex)) {
                    numericKeys.add(Map.entry(numericIndex, PropertyKey.fromIndex(i)));
                }
            }
        }
        if (sparseProperties != null) {
            for (Map.Entry<Integer, JSValue> entry : sparseProperties.entrySet()) {
                int sparseIndex = entry.getKey();
                long index = Integer.toUnsignedLong(sparseIndex);
                if (index >= 0 && index < length) {
                    if (seenNumericIndices.add(index)) {
                        numericKeys.add(Map.entry(index, PropertyKey.fromIndex(sparseIndex)));
                    }
                }
            }
        }

        for (PropertyKey key : shape.getPropertyKeys()) {
            long index = key.toArrayIndex();
            if (index >= 0) {
                if (!seenNumericIndices.contains(index)) {
                    PropertyDescriptor descriptor = super.getOwnPropertyDescriptorRaw(key);
                    if (descriptor != null && (!enumerableOnly || descriptor.isEnumerable())) {
                        if (seenNumericIndices.add(index)) {
                            if (index <= Integer.MAX_VALUE) {
                                numericKeys.add(Map.entry(index, PropertyKey.fromIndex((int) index)));
                            } else {
                                numericKeys.add(Map.entry(index, PropertyKey.fromIndexString(index)));
                            }
                        }
                    }
                }
                continue;
            }
            if (key.isSymbol()) {
                if (!enumerableOnly) {
                    if (seenPropertyKeys.add(key)) {
                        symbolKeys.add(key);
                    }
                } else {
                    PropertyDescriptor descriptor = super.getOwnPropertyDescriptorRaw(key);
                    if (descriptor != null && descriptor.isEnumerable() && seenPropertyKeys.add(key)) {
                        symbolKeys.add(key);
                    }
                }
                continue;
            }
            if (!enumerableOnly) {
                if (seenPropertyKeys.add(key)) {
                    stringKeys.add(key);
                }
            } else {
                PropertyDescriptor descriptor = super.getOwnPropertyDescriptorRaw(key);
                if (descriptor != null && descriptor.isEnumerable() && seenPropertyKeys.add(key)) {
                    stringKeys.add(key);
                }
            }
        }

        numericKeys.sort(Comparator.comparingLong(Map.Entry::getKey));

        List<PropertyKey> keys = new ArrayList<>(numericKeys.size() + stringKeys.size() + symbolKeys.size());
        for (Map.Entry<Long, PropertyKey> entry : numericKeys) {
            keys.add(entry.getValue());
        }
        keys.addAll(stringKeys);
        keys.addAll(symbolKeys);
        return keys;
    }

    private boolean hasDenseValue(int index) {
        if (doubleArray != null) {
            return index < doubleArray.length && Double.doubleToRawLongBits(doubleArray[index]) != HOLE_BITS;
        }
        return index < denseArray.length && denseArray[index] != null;
    }

    /**
     * Check whether an index has an own element (distinguishes holes from undefined values).
     */
    public boolean hasElement(long index) {
        if (index < 0 || index >= length) {
            return false;
        }

        if (index <= Integer.MAX_VALUE) {
            int intIndex = (int) index;
            if (hasDenseValue(intIndex)) {
                return true;
            }
            return sparseProperties != null && sparseProperties.containsKey(intIndex);
        }
        return super.hasOwnProperty(PropertyKey.fromIndexString(index));
    }

    @Override
    public boolean hasOwnProperty(PropertyKey key) {
        if (super.hasOwnProperty(key)) {
            return true;
        }

        long index = key.toArrayIndex();
        if (index < 0) {
            return false;
        }

        if (index <= Integer.MAX_VALUE) {
            return hasDenseValue((int) index);
        }
        return super.hasOwnProperty(PropertyKey.fromIndexString(index));
    }

    private boolean hasPrototypeSetInterference(PropertyKey key) {
        // Runs on every element write to a hole, which includes every write that grows an array.
        // It used to allocate a HashSet<JSObject> per call purely as a cycle guard, and to take a
        // defensive copy of each descriptor it only null-checked. A depth bound and the raw
        // descriptor accessor give the same protection with no allocation at all.
        //
        // The two answers are not symmetric. False means "nothing on the chain can observe this
        // write", which licenses the dense fast path and skips ordinary [[Set]]; true only costs a
        // slower, fully specification-compliant write. So running out of depth must answer true.
        // Answering false there meant a setter 1,001 links up was never called and an own element
        // was created instead — `array[0] = 1` silently diverging from [[Set]] by prototype depth.
        JSObject prototypeObject = getPrototype();
        for (int depth = 0; prototypeObject != null; depth++) {
            if (depth >= MAX_PROTOTYPE_SET_DEPTH) {
                return true;
            }
            if (prototypeObject instanceof JSTypedArray || prototypeObject instanceof JSProxy) {
                return true;
            }
            if (prototypeObject.getOwnPropertyDescriptorRaw(key) != null) {
                return true;
            }
            prototypeObject = prototypeObject.getPrototype();
        }
        return false;
    }

    /**
     * Take initial dense storage: unboxed when every value is a number, references otherwise.
     */
    private void initializeDenseStorage(JSValue[] values, int capacity, boolean takeOwnership) {
        boolean allNumbers = true;
        for (JSValue value : values) {
            if (!(value instanceof JSNumber)) {
                allNumbers = false;
                break;
            }
        }
        if (allNumbers) {
            double[] doubles = new double[capacity];
            for (int i = 0; i < values.length; i++) {
                doubles[i] = toStoredDouble(((JSNumber) values[i]).value());
            }
            Arrays.fill(doubles, values.length, capacity, HOLE);
            doubleArray = doubles;
        } else {
            denseArray = takeOwnership ? values : Arrays.copyOf(values, capacity);
        }
    }

    /**
     * Initialize the "length" property as a special data property.
     */
    private void initializeLengthProperty() {
        // The length property is special - it's writable but not enumerable or configurable
        PropertyDescriptor lengthDesc = PropertyDescriptor.dataDescriptor(JSNumber.of(length),
                PropertyDescriptor.DataState.Writable);
        super.defineProperty(PropertyKey.LENGTH, lengthDesc);
    }

    /**
     * Whether an element at this index belongs in dense storage: below the capacity already allocated or
     * {@link #MAX_DENSE_SIZE}, or just past the allocated capacity, which is where appending and sequential filling
     * write. Dense storage used to stop at {@link #MAX_DENSE_SIZE} for every array, so element 10,000 and every one
     * after it went into a {@code HashMap<Integer, JSValue>} — a boxed key and an entry per element, about 90 bytes
     * each, and a hash lookup per access.
     */
    private boolean isDenseIndex(int index) {
        int capacity = denseCapacity();
        return index < capacity || index < MAX_DENSE_SIZE || index - capacity < Math.max(MAX_DENSE_SIZE, capacity >> 1);
    }

    private boolean isLengthWritable() {
        PropertyDescriptor descriptor = shape.getDescriptor(PropertyKey.LENGTH);
        return descriptor == null || descriptor.isWritable();
    }

    /**
     * Own-property lookup, so a prototype-chain walk finds dense array elements.
     * <p>
     * This used to override {@code getWithReceiver}, which meant an array link in a prototype chain could only be
     * reached by recursing into it — one Java frame and one unit of the prototype-depth budget per link. Adding the
     * elements to the own lookup instead lets the iterative walk in {@link JSObject} handle arrays like any other
     * ordinary object.
     *
     * @param key
     *            the property key
     * @param receiver
     *            the receiver a getter is called with
     * @return the value, or {@code null} when there is no own property
     */
    @Override
    protected JSValue lookupOwnForGet(PropertyKey key, JSValue receiver) {
        JSValue value = storedElement(key.toArrayIndex());
        return value != null ? value : super.lookupOwnForGet(key, receiver);
    }

    @Override
    public PropertyKey[] ownPropertyKeys() {
        return getOwnPropertyKeys().toArray(new PropertyKey[0]);
    }

    /**
     * Remove and return the last element.
     */
    public JSValue pop() {
        if (length == 0) {
            return JSUndefined.INSTANCE;
        }

        long lastIndex = length - 1;
        JSValue value = get(lastIndex);

        // Remove the element
        if (lastIndex < denseCapacity()) {
            setDenseValue((int) lastIndex, null);
        } else if (lastIndex <= Integer.MAX_VALUE && sparseProperties != null) {
            sparseProperties.remove((int) lastIndex);
        } else {
            // Two-argument form so the call stays non-virtual: the one-argument delete now
            // routes back through the overridable [[Delete]], which would re-enter this class.
            super.delete(PropertyKey.fromIndexString(lastIndex), context.isStrictMode());
        }

        setLength(lastIndex);
        return value;
    }

    /**
     * Add element to the end of the array.
     */
    public void push(JSValue value) {
        long previousLength = length;
        set(length, value);

        // Array.prototype.push must throw when append fails because the array is not extensible
        // or length is read-only.
        if (!this.context.hasPendingException() && length == previousLength) {
            if (!extensible) {
                this.context.throwTypeError("Cannot add property " + previousLength + ", object is not extensible");
            } else if (!isLengthWritable()) {
                this.context.throwTypeError("Cannot assign to read only property 'length' of object '[object Array]'");
            }
        }
    }

    /**
     * Store an element in dense or sparse storage, never in both.
     */
    private void putElement(int index, JSValue value) {
        if (isDenseIndex(index)) {
            ensureDenseCapacity(index + 1);
            setDenseValue(index, value);
            if (sparseProperties != null && sparseProperties.remove(index) != null && sparseProperties.isEmpty()) {
                sparseProperties = null;
            }
        } else {
            if (sparseProperties == null) {
                sparseProperties = new HashMap<>();
            }
            sparseProperties.put(index, value);
        }
    }

    @Override
    public void set(int index, JSValue value) {
        set((long) index, value);
    }

    /**
     * Set element at index.
     */
    public void set(long index, JSValue value) {
        if (index < 0 || index > MAX_ARRAY_INDEX) {
            // Negative indices are treated as string properties
            super.set(PropertyKey.fromIndexString(index), value);
            return;
        }

        // Check if we're adding a new element beyond current length
        boolean isAddingNewElement = index >= length;

        // Check if array is not extensible/frozen before adding new elements
        if (isAddingNewElement && !extensible) {
            // In strict mode, throw TypeError when trying to add to non-extensible array
            if (this.context.isStrictMode()) {
                this.context.throwTypeError("Cannot add property " + index + ", object is not extensible");
            }
            return;
        }

        // Growing the array requires writable length.
        if (isAddingNewElement && !isLengthWritable()) {
            if (this.context.isStrictMode()) {
                this.context.throwTypeError("Cannot assign to read only property 'length' of object '[object Array]'");
            }
            return;
        }

        // Check if array is frozen when modifying existing elements
        if (!isAddingNewElement && frozen) {
            // In strict mode, throw TypeError when trying to modify frozen array
            if (this.context.isStrictMode()) {
                this.context.throwTypeError(
                        "Cannot assign to read only property '" + index + "' of object '[object Array]'");
            }
            return;
        }

        // If this index has a shape property (e.g., from Object.defineProperty),
        // delegate to JSObject.set which handles descriptor constraints (writable, accessor, etc.).
        // Following QuickJS which checks find_own_property before using the fast array path.
        if (!isAddingNewElement && index <= Integer.MAX_VALUE) {
            PropertyKey key = PropertyKey.fromIndexString(index);
            if (super.hasOwnShapeProperty(key)) {
                super.set(key, value);
                return;
            }
            // If this index is a hole (not present in own dense/sparse storage),
            // delegate to JSObject.set so inherited setters on the prototype chain
            // are correctly invoked per ES2024 OrdinarySet.
            boolean hasOwnElement = hasElement(index);
            if (!hasOwnElement) {
                super.set(key, value);
                return;
            }
        }

        // Extend length if necessary
        if (index >= length) {
            setLength(index + 1);
        }

        if (index <= Integer.MAX_VALUE) {
            putElement((int) index, value);
        } else {
            // Preserve semantics for very large array indices without integer overflow.
            super.set(PropertyKey.fromIndexString(index), value);
        }
    }

    /**
     * Override set by PropertyKey to handle array indices.
     */
    @Override
    public void set(PropertyKey key, JSValue value) {
        long index = key.toArrayIndex();
        if (index >= 0) {
            boolean hasOwnIndexedElement = hasElement(index);
            if (!hasOwnIndexedElement && hasPrototypeSetInterference(key)) {
                super.set(key, value);
            } else {
                set(index, value);
            }
        } else if (key.isString() && "length".equals(key.asString())) {
            // Route through ArraySetLength so internal `length` and element truncation
            // stay in sync with the observable length data property.
            boolean result = setWithResult(key, value, this);
            if (!result && !this.context.hasPendingException() && this.context.isStrictMode()
                    && throwBlockedLengthTruncation() == null) {
                this.context.throwTypeError("Cannot assign to read only property 'length' of object '[object Array]'");
            }
        } else {
            // Otherwise, use the shape-based storage from JSObject
            super.set(key, value);
        }
    }

    /**
     * Store a dense element; null makes a hole. A value other than a number turns unboxed storage into references.
     */
    private void setDenseValue(int index, JSValue value) {
        if (doubleArray != null) {
            if (value == null) {
                doubleArray[index] = HOLE;
                return;
            }
            if (value instanceof JSNumber number) {
                doubleArray[index] = toStoredDouble(number.value());
                return;
            }
            boxDenseStorage();
        }
        denseArray[index] = value;
    }

    /**
     * Set the array length. When length is reduced, elements beyond the new length are deleted.
     */
    public void setLength(long newLength) {
        requireValidLength(newLength);

        if (newLength < length) {
            // Truncate array - delete elements beyond new length
            int denseLimit = (int) Math.min(newLength, denseCapacity());
            clearDense(denseLimit, (int) Math.min(length, denseCapacity()));

            // Remove sparse elements beyond new length
            if (sparseProperties != null) {
                sparseProperties.entrySet().removeIf(entry -> entry.getKey() >= newLength);
            }

            // Remove indexed string properties outside the new length range.
            for (PropertyKey key : shape.getPropertyKeys()) {
                long index = key.toArrayIndex();
                if (index >= newLength && index >= 0) {
                    super.delete(key, context.isStrictMode());
                }
            }
        }

        this.length = newLength;
        updateLengthProperty();
    }

    @Override
    public boolean setWithResult(PropertyKey key, JSValue value) {
        return setWithResult(key, value, this);
    }

    @Override
    public boolean setWithResult(PropertyKey key, JSValue value, JSObject receiver) {
        long index = key.toArrayIndex();
        if (index >= 0 || !(key.isString() && "length".equals(key.asString()))) {
            // Non-length keys: delegate to JSObject which handles prototype chain
            return super.setWithResult(key, value, receiver);
        }
        // When receiver differs from this (e.g. proxy forwarding to target),
        // delegate to JSObject so that setOnReceiver invokes the receiver's
        // [[GetOwnPropertyDescriptor]] and [[DefineOwnProperty]] traps.
        if (receiver != this) {
            return super.setWithResult(key, value, receiver);
        }
        // [[Set]] semantics differ from [[DefineOwnProperty]] for non-writable data properties:
        // assignment to array.length must fail even when assigning the current value.
        PropertyDescriptor oldLengthDescriptor = getOwnPropertyDescriptor(PropertyKey.LENGTH);
        if (oldLengthDescriptor != null && !oldLengthDescriptor.isWritable()) {
            return false;
        }
        // Per ES spec ArraySetLength / QuickJS set_array_length:
        // Coerce value BEFORE descriptor updates.
        Long newLength = toArrayLengthForLengthProperty(this.context, value);
        if (newLength == null) {
            return false; // coercion error (pending exception)
        }
        PropertyDescriptor lengthDescriptor = new PropertyDescriptor();
        lengthDescriptor.setValue(JSNumber.of(newLength));
        return defineProperty(PropertyKey.LENGTH, lengthDescriptor);
    }

    /**
     * Remove and return the first element.
     */
    public JSValue shift() {
        if (length == 0) {
            return JSUndefined.INSTANCE;
        }

        JSValue first = get(0);

        // Shift all elements down
        shiftElementsLeft(0, 1);

        setLength(length - 1);
        return first;
    }

    /**
     * Shift elements left (for shift/splice operations).
     */
    private void shiftElementsLeft(int start, int count) {
        if (count <= 0) {
            return;
        }

        // Shift dense elements
        int denseEnd = (int) Math.min(length, denseCapacity());
        if (start + count < denseEnd) {
            if (doubleArray != null) {
                System.arraycopy(doubleArray, start + count, doubleArray, start, denseEnd - start - count);
            } else {
                System.arraycopy(denseArray, start + count, denseArray, start, denseEnd - start - count);
            }
        }

        // Clear the tail
        clearDense(Math.max(0, denseEnd - count), denseEnd);

        // Handle sparse properties
        if (sparseProperties != null) {
            Map<Integer, JSValue> newSparse = new HashMap<>();
            for (Map.Entry<Integer, JSValue> entry : sparseProperties.entrySet()) {
                int index = entry.getKey();
                if (index >= start + count) {
                    newSparse.put(index - count, entry.getValue());
                } else if (index < start) {
                    newSparse.put(index, entry.getValue());
                }
            }
            sparseProperties = newSparse.isEmpty() ? null : newSparse;
        }
    }

    private JSValue storedElement(long index) {
        if (index < 0 || index >= length || index > Integer.MAX_VALUE) {
            return null;
        }
        int intIndex = (int) index;
        JSValue value = denseValue(intIndex);
        return value != null || sparseProperties == null ? value : sparseProperties.get(intIndex);
    }

    /**
     * Raise the {@code TypeError} for a length truncation a non-configurable element blocked.
     * <p>
     * V8 names the element rather than the length assignment, because the delete is the operation that actually failed.
     * Both throwing callers — a strict-mode {@code length} assignment and {@code Object.defineProperty} — report it
     * through here so the message stays in one place; a non-throwing caller such as {@code Reflect.defineProperty}
     * never calls it and still just sees {@code false}.
     *
     * @return the error value, or {@code null} when the last truncation was not blocked
     */
    public JSValue throwBlockedLengthTruncation() {
        long blockedIndex = consumeLengthTruncationBlockedIndex();
        if (blockedIndex < 0) {
            return null;
        }
        return context.throwTypeError(
                "Cannot delete property '" + blockedIndex + "' of " + getObjectDescriptionForError(true));
    }

    /**
     * Convert array to a Java array.
     * <p>
     * A JavaScript array length is a {@code uint32} and can exceed {@code Integer.MAX_VALUE}, which no Java array can
     * hold. Narrowing the length to {@code int} produced a negative size and a {@link NegativeArraySizeException}; the
     * range is checked explicitly instead.
     *
     * @return the array elements
     * @throws JSRangeErrorException
     *             when the length exceeds {@code Integer.MAX_VALUE}
     */
    public JSValue[] toArray() {
        if (length > Integer.MAX_VALUE) {
            throw new JSRangeErrorException("Array length " + length + " exceeds the maximum Java array size");
        }
        JSValue[] result = new JSValue[(int) length];
        for (int i = 0; i < result.length; i++) {
            result[i] = get(i);
        }
        return result;
    }

    private Long toArrayLengthForLengthProperty(JSContext context, JSValue value) {
        JSNumber uint32Source = JSTypeConversions.toNumberOrNull(context, value);
        if (uint32Source == null) {
            return null;
        }
        long uint32Length = toUint32(uint32Source.value());

        JSNumber numericValue = JSTypeConversions.toNumber(context, value);
        if (context.hasPendingException()) {
            return null;
        }
        Long exactLength = toArrayLengthFromNumber(numericValue.value());
        if (exactLength == null || exactLength != uint32Length) {
            context.throwRangeError("Invalid array length");
            return null;
        }
        return exactLength;
    }

    /**
     * Convert to a {@link List}; integral numbers become {@link Integer}/{@link Long}, nested objects convert too.
     *
     * @see JavaObjectConverter
     */
    @Override
    public Object toJavaObject() {
        return JavaObjectConverter.convert(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        long maxDisplay = Math.min(length, 100);
        for (long i = 0; i < maxDisplay; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            JSValue val = get(i);
            if (val instanceof JSString s) {
                sb.append('"').append(s.value()).append('"');
            } else if (val instanceof JSUndefined) {
                sb.append("undefined");
            } else {
                sb.append(val);
            }
        }
        if (length > maxDisplay) {
            sb.append(", ... (").append(length - maxDisplay).append(" more)");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Update the length property value.
     */
    private void updateLengthProperty() {
        int offset = shape.getPropertyOffset(PropertyKey.LENGTH);
        if (offset >= 0) {
            propertyValues[offset] = JSNumber.of(length);
        }
    }

    /**
     * Array constructor implementation. new Array() - creates an empty array new Array(len) - creates an array with
     * specified length (if len is a number) new Array(element0, element1, ..., elementN) - creates an array with the
     * given elements
     * <p>
     * Based on ES2020 22.1.1.1
     */
    public static JSArray create(JSContext context, JSValue... args) {
        JSArray array = context.createJSArray();

        // Special case: single numeric argument sets array length
        if (args.length == 1 && args[0] instanceof JSNumber num) {
            Long length = toArrayLengthFromNumber(num.value());
            if (length == null) {
                context.throwRangeError("Invalid array length");
                return context.createJSArray();
            }
            array.setLength(length);
            return array;
        }

        // Multiple arguments or non-numeric single argument: create array with elements
        for (JSValue arg : args) {
            array.push(arg);
        }

        return array;
    }

    /**
     * Reject a length that no ECMAScript array can have.
     * <p>
     * A public engine API must raise an error the engine's own machinery understands, not a raw {@code java.lang}
     * exception that is neither catchable from JavaScript nor typed for embedders.
     *
     * @param length
     *            the candidate length
     * @throws JSRangeErrorException
     *             when {@code length} is not in {@code [0, 2^32 - 1]}
     */
    private static void requireValidLength(long length) {
        if (length < 0 || length > MAX_ARRAY_LENGTH) {
            throw new JSRangeErrorException("Invalid array length: " + length);
        }
    }

    private static Long toArrayLengthFromNumber(double value) {
        if (!(value >= 0 && value <= UINT32_MAX_DOUBLE)) {
            return null;
        }
        long length = (long) value;
        return ((double) length == value) ? length : null;
    }

    /**
     * A number as stored unboxed: every NaN canonical, so none can be mistaken for {@link #HOLE}.
     */
    private static double toStoredDouble(double value) {
        return Double.isNaN(value) ? Double.NaN : value;
    }

    private static long toUint32(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value == 0.0) {
            return 0;
        }
        double integer = value > 0 ? Math.floor(value) : Math.ceil(value);
        double modulo = integer % UINT32_MODULO;
        if (modulo < 0) {
            modulo += UINT32_MODULO;
        }
        return (long) modulo;
    }
}
