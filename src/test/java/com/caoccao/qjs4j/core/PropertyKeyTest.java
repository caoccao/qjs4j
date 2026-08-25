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

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PropertyKey} identity.
 * <p>
 * A canonical array index has two internal encodings — {@code fromIndex(0)} boxes an
 * {@code Integer} while {@code fromString("0")} keeps a {@code String} — but both name the same
 * JavaScript property. They used to be neither {@code equals} nor hash-equal, so any
 * {@code HashSet<PropertyKey>} or {@code HashMap<PropertyKey, ?>} in the engine misbehaved.
 */
public class PropertyKeyTest {

    @Test
    public void testEqualsAndHashCodeAgreeForIndexAndStringEncodings() {
        int[] indices = {0, 1, 7, 9, 10, 99, 100, 1023, 1024, 65535, 1000000, Integer.MAX_VALUE};
        for (int index : indices) {
            PropertyKey indexKey = PropertyKey.fromIndex(index);
            PropertyKey stringKey = PropertyKey.fromString(Integer.toString(index));
            assertThat(indexKey).as("fromIndex(%d) equals fromString", index).isEqualTo(stringKey);
            assertThat(stringKey).as("fromString equals fromIndex(%d)", index).isEqualTo(indexKey);
            assertThat(indexKey.hashCode())
                    .as("hash codes agree for %d", index)
                    .isEqualTo(stringKey.hashCode());
        }
    }

    @Test
    public void testHashMapTreatsBothEncodingsAsOneKey() {
        Map<PropertyKey, String> map = new HashMap<>();
        map.put(PropertyKey.fromIndex(42), "first");
        map.put(PropertyKey.fromString("42"), "second");
        assertThat(map).hasSize(1);
        assertThat(map.get(PropertyKey.fromIndex(42))).isEqualTo("second");
        assertThat(map.get(PropertyKey.fromString("42"))).isEqualTo("second");
    }

    @Test
    public void testHashSetRemovesAcrossEncodings() {
        Set<PropertyKey> keys = new HashSet<>();
        keys.add(PropertyKey.fromString("0"));
        keys.add(PropertyKey.fromString("1"));
        assertThat(keys.remove(PropertyKey.fromIndex(0))).isTrue();
        assertThat(keys.remove(PropertyKey.fromIndex(1))).isTrue();
        assertThat(keys).isEmpty();
    }

    @Test
    public void testInternedAndNonInternedKeysAgree() {
        // fromAtom is the only producer of a non-negative atom index. Deriving hashCode from it
        // would break the equals/hashCode contract, because an interned key can equal a plain one.
        PropertyKey interned = PropertyKey.fromAtom("length", 17);
        PropertyKey plain = PropertyKey.fromString("length");
        assertThat(interned).isEqualTo(plain);
        assertThat(plain).isEqualTo(interned);
        assertThat(interned.hashCode()).isEqualTo(plain.hashCode());

        Set<PropertyKey> keys = new HashSet<>();
        keys.add(interned);
        keys.add(plain);
        assertThat(keys).hasSize(1);
    }

    @Test
    public void testNonCanonicalNumericStringsAreNotIndexKeys() {
        // "00", "01" and "+1" are not canonical array indices, so they stay distinct properties.
        assertThat(PropertyKey.fromString("00")).isNotEqualTo(PropertyKey.fromIndex(0));
        assertThat(PropertyKey.fromString("01")).isNotEqualTo(PropertyKey.fromIndex(1));
        assertThat(PropertyKey.fromString("+1")).isNotEqualTo(PropertyKey.fromIndex(1));
        assertThat(PropertyKey.fromString("1.0")).isNotEqualTo(PropertyKey.fromIndex(1));
        assertThat(PropertyKey.fromString(" 1")).isNotEqualTo(PropertyKey.fromIndex(1));
    }

    @Test
    public void testStringKeysStayDistinct() {
        assertThat(PropertyKey.fromString("length")).isEqualTo(PropertyKey.fromString("length"));
        assertThat(PropertyKey.fromString("length")).isNotEqualTo(PropertyKey.fromString("name"));
        assertThat(PropertyKey.fromString("0")).isNotEqualTo(PropertyKey.fromString("1"));
    }

    @Test
    public void testSymbolKeysCompareByIdentity() {
        JSSymbol first = new JSSymbol("shared");
        JSSymbol second = new JSSymbol("shared");
        assertThat(PropertyKey.fromSymbol(first)).isEqualTo(PropertyKey.fromSymbol(first));
        assertThat(PropertyKey.fromSymbol(first)).isNotEqualTo(PropertyKey.fromSymbol(second));

        Set<PropertyKey> keys = new HashSet<>();
        keys.add(PropertyKey.fromSymbol(first));
        keys.add(PropertyKey.fromSymbol(second));
        assertThat(keys).hasSize(2);
    }

    @Test
    public void testSymbolKeyNeverEqualsItsPropertyString() {
        JSSymbol symbol = new JSSymbol("description");
        PropertyKey symbolKey = PropertyKey.fromSymbol(symbol);
        PropertyKey stringKey = PropertyKey.fromString(symbolKey.toPropertyString());
        assertThat(symbolKey).isNotEqualTo(stringKey);
        assertThat(stringKey).isNotEqualTo(symbolKey);
    }
}
