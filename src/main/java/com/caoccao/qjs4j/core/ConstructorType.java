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

/**
 * Enum representing internal constructor type markers.
 * These correspond to [[Constructor]] internal slots in the ECMAScript specification.
 */
public enum ConstructorType {
    AGGREGATE_ERROR,
    ARRAY,
    ARRAY_BUFFER,
    BIG_INT,
    BOOLEAN,
    DATA_VIEW,
    DATE,
    ERROR,
    EVAL_ERROR,
    FINALIZATION_REGISTRY,
    MAP,
    NUMBER,
    PROMISE,
    PROXY,
    RANGE_ERROR,
    REFERENCE_ERROR,
    REGEXP,
    SET,
    SHARED_ARRAY_BUFFER,
    STRING,
    SYMBOL,
    SYNTAX_ERROR,
    TYPE_ERROR,
    TYPED_ARRAY_INT8,
    TYPED_ARRAY_UINT8,
    TYPED_ARRAY_UINT8_CLAMPED,
    TYPED_ARRAY_INT16,
    TYPED_ARRAY_UINT16,
    TYPED_ARRAY_INT32,
    TYPED_ARRAY_UINT32,
    TYPED_ARRAY_FLOAT32,
    TYPED_ARRAY_FLOAT64,
    TYPED_ARRAY_BIGINT64,
    TYPED_ARRAY_BIGUINT64,
    URI_ERROR,
    WEAK_MAP,
    WEAK_REF,
    WEAK_SET
}
