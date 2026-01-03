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

package com.caoccao.qjs4j.utils;

import com.caoccao.qjs4j.BaseTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AtomTableTest extends BaseTest {

    @Test
    public void testClear() {
        AtomTable table = new AtomTable();

        int initialSize = table.size();

        // Add some user atoms
        table.intern("user1");
        table.intern("user2");
        assertThat(table.size()).isGreaterThan(initialSize);

        // Clear
        table.clear();

        // Well-known atoms should still be there
        assertThat(table.size()).isEqualTo(initialSize);
        assertThat(table.isValidAtom(AtomTable.ATOM_NULL)).isTrue();
        assertThat(table.getString(AtomTable.ATOM_NULL)).isEqualTo("null");

        // User atoms should be gone
        assertThat(table.getAtom("user1")).isEqualTo(-1);
        assertThat(table.getAtom("user2")).isEqualTo(-1);
    }

    @Test
    public void testGetAtomInterned() {
        AtomTable table = new AtomTable();

        int atom = table.intern("existing");
        assertThat(table.getAtom("existing")).isEqualTo(atom);
    }

    @Test
    public void testGetAtomNotInterned() {
        AtomTable table = new AtomTable();

        assertThat(table.getAtom("nonexistent")).isEqualTo(-1);
    }

    @Test
    public void testGetStringInvalidAtom() {
        AtomTable table = new AtomTable();

        assertThat(table.getString(-1)).isNull();
        assertThat(table.getString(99999)).isNull();
    }

    @Test
    public void testGetStringValidAtom() {
        AtomTable table = new AtomTable();

        int atom = table.intern("test");
        assertThat(table.getString(atom)).isEqualTo("test");
    }

    @Test
    public void testInternEmptyString() {
        AtomTable table = new AtomTable();

        int atom = table.intern("");
        assertThat(table.isValidAtom(atom)).isTrue();
        assertThat(table.getString(atom)).isEqualTo("");
    }

    @Test
    public void testInternExistingString() {
        AtomTable table = new AtomTable();

        int atom1 = table.intern("world");
        int atom2 = table.intern("world");
        assertThat(atom2).isEqualTo(atom1);
        assertThat(table.getString(atom1)).isEqualTo("world");
    }

    @Test
    public void testInternNewString() {
        AtomTable table = new AtomTable();

        int atom1 = table.intern("hello");
        assertThat(atom1).isGreaterThanOrEqualTo(0);
        assertThat(table.getString(atom1)).isEqualTo("hello");
    }

    @Test
    public void testInternNull() {
        AtomTable table = new AtomTable();

        int atom = table.intern(null);
        assertThat(atom).isEqualTo(-1);
    }

    @Test
    public void testInternUnicodeString() {
        AtomTable table = new AtomTable();

        String unicode = "🚀🌟";
        int atom = table.intern(unicode);
        assertThat(table.getString(atom)).isEqualTo(unicode);
    }

    @Test
    public void testIsValidAtom() {
        AtomTable table = new AtomTable();

        // Well-known atoms
        assertThat(table.isValidAtom(AtomTable.ATOM_NULL)).isTrue();
        assertThat(table.isValidAtom(AtomTable.ATOM_FUNCTION)).isTrue();

        // User atoms
        int atom = table.intern("valid");
        assertThat(table.isValidAtom(atom)).isTrue();

        // Invalid atoms
        assertThat(table.isValidAtom(-1)).isFalse();
        assertThat(table.isValidAtom(99999)).isFalse();
    }

    @Test
    public void testMultipleInterns() {
        AtomTable table = new AtomTable();

        String[] strings = {"apple", "banana", "cherry", "apple", "banana"};
        int[] atoms = new int[strings.length];

        for (int i = 0; i < strings.length; i++) {
            atoms[i] = table.intern(strings[i]);
        }

        // Same strings should have same atoms
        assertThat(atoms[3]).isEqualTo(atoms[0]);
        assertThat(atoms[4]).isEqualTo(atoms[1]);

        // Different strings should have different atoms
        assertThat(atoms[0]).isNotEqualTo(atoms[1]);
        assertThat(atoms[1]).isNotEqualTo(atoms[2]);
        assertThat(atoms[0]).isNotEqualTo(atoms[2]);
    }

    @Test
    public void testSize() {
        AtomTable table = new AtomTable();

        int initialSize = table.size();
        assertThat(initialSize).isGreaterThanOrEqualTo(41); // At least the well-known atoms

        table.intern("one");
        assertThat(table.size()).isEqualTo(initialSize + 1);

        table.intern("two");
        assertThat(table.size()).isEqualTo(initialSize + 2);

        table.intern("one"); // Duplicate
        assertThat(table.size()).isEqualTo(initialSize + 2); // Size shouldn't increase
    }

    @Test
    public void testToString() {
        AtomTable table = new AtomTable();

        String str = table.toString();
        assertThat(str).startsWith("AtomTable{size=");
        assertThat(str).endsWith("}");
        assertThat(str).contains(String.valueOf(table.size()));
    }

    @Test
    public void testWellKnownAtoms() {
        AtomTable table = new AtomTable();

        // Test some well-known atoms
        assertThat(table.getString(AtomTable.ATOM_NULL)).isEqualTo("null");
        assertThat(table.getString(AtomTable.ATOM_FALSE)).isEqualTo("false");
        assertThat(table.getString(AtomTable.ATOM_TRUE)).isEqualTo("true");
        assertThat(table.getString(AtomTable.ATOM_IF)).isEqualTo("if");
        assertThat(table.getString(AtomTable.ATOM_ELSE)).isEqualTo("else");
        assertThat(table.getString(AtomTable.ATOM_RETURN)).isEqualTo("return");
        assertThat(table.getString(AtomTable.ATOM_FUNCTION)).isEqualTo("function");
        assertThat(table.getString(AtomTable.ATOM_CLASS)).isEqualTo("class");
        assertThat(table.getString(AtomTable.ATOM_CONST)).isEqualTo("const");
        assertThat(table.getString(AtomTable.ATOM_LET)).isEqualTo("let");
        assertThat(table.getString(AtomTable.ATOM_ASYNC)).isEqualTo("async");
    }
}
