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

package com.caoccao.qjs4j.vm;

import com.caoccao.qjs4j.core.JSUndefined;
import com.caoccao.qjs4j.core.JSValue;

/**
 * A mutable reference cell for closure variables.
 * Following QuickJS JSVarRef pattern: enables shared mutable access
 * to variables captured by closures.
 * <p>
 * A VarRef points to a slot in a JSValue[] array (typically a parent
 * function's locals array). Multiple closures capturing the same
 * variable share the same VarRef, so mutations through one closure
 * are visible to all others and to the parent function.
 * <p>
 * When {@link #close()} is called (via CLOSE_LOC opcode), the VarRef
 * detaches from the shared storage by copying the current value to
 * its own standalone storage. This freezes the value for any closures
 * that captured this VarRef, enabling per-iteration binding in loops.
 */
public final class VarRef {
    private JSValue[] storage;
    private int index;

    /**
     * Create a VarRef pointing to a slot in a storage array.
     * Used for capturing locals from a parent frame.
     *
     * @param storage The backing array (e.g., parent's locals)
     * @param index   The slot index in the array
     */
    public VarRef(JSValue[] storage, int index) {
        this.storage = storage;
        this.index = index;
    }

    /**
     * Create a standalone VarRef wrapping a single value.
     * Used for class method private symbols and other cases where
     * no shared storage is needed.
     *
     * @param value The initial value
     */
    public VarRef(JSValue value) {
        this.storage = new JSValue[]{value};
        this.index = 0;
    }

    /**
     * Detach this VarRef from its shared storage array.
     * Copies the current value to standalone storage so that
     * the original storage slot can be reused (e.g., for the next
     * loop iteration) without affecting closures that hold this VarRef.
     * Following QuickJS close_var_refs pattern.
     */
    public void close() {
        JSValue currentValue = storage[index];
        this.storage = new JSValue[]{currentValue};
        this.index = 0;
    }

    /**
     * Get the current value.
     */
    public JSValue get() {
        JSValue v = storage[index];
        return v != null ? v : JSUndefined.INSTANCE;
    }

    /**
     * Set the value. This writes through to the backing storage,
     * so all VarRef instances pointing to the same slot see the update.
     */
    public void set(JSValue value) {
        storage[index] = value;
    }
}
