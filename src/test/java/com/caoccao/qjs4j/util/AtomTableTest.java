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

package com.caoccao.qjs4j.util;

import com.caoccao.qjs4j.BaseTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AtomTableTest extends BaseTest {

    @Test
    public void testWellKnownAtoms() {
        AtomTable table = new AtomTable();

        // Test some well-known atoms
        assertEquals("null", table.getString(AtomTable.ATOM_NULL));
        assertEquals("false", table.getString(AtomTable.ATOM_FALSE));
        assertEquals("true", table.getString(AtomTable.ATOM_TRUE));
        assertEquals("if", table.getString(AtomTable.ATOM_IF));
        assertEquals("else", table.getString(AtomTable.ATOM_ELSE));
        assertEquals("return", table.getString(AtomTable.ATOM_RETURN));
        assertEquals("function", table.getString(AtomTable.ATOM_FUNCTION));
        assertEquals("class", table.getString(AtomTable.ATOM_CLASS));
        assertEquals("const", table.getString(AtomTable.ATOM_CONST));
        assertEquals("let", table.getString(AtomTable.ATOM_LET));
        assertEquals("async", table.getString(AtomTable.ATOM_ASYNC));
    }

    @Test
    public void testInternNewString() {
        AtomTable table = new AtomTable();

        int atom1 = table.intern("hello");
        assertTrue(atom1 >= 0);
        assertEquals("hello", table.getString(atom1));
    }

    @Test
    public void testInternExistingString() {
        AtomTable table = new AtomTable();

        int atom1 = table.intern("world");
        int atom2 = table.intern("world");
        assertEquals(atom1, atom2);
        assertEquals("world", table.getString(atom1));
    }

    @Test
    public void testInternNull() {
        AtomTable table = new AtomTable();

        int atom = table.intern(null);
        assertEquals(-1, atom);
    }

    @Test
    public void testGetStringValidAtom() {
        AtomTable table = new AtomTable();

        int atom = table.intern("test");
        assertEquals("test", table.getString(atom));
    }

    @Test
    public void testGetStringInvalidAtom() {
        AtomTable table = new AtomTable();

        assertNull(table.getString(-1));
        assertNull(table.getString(99999));
    }

    @Test
    public void testIsValidAtom() {
        AtomTable table = new AtomTable();

        // Well-known atoms
        assertTrue(table.isValidAtom(AtomTable.ATOM_NULL));
        assertTrue(table.isValidAtom(AtomTable.ATOM_FUNCTION));

        // User atoms
        int atom = table.intern("valid");
        assertTrue(table.isValidAtom(atom));

        // Invalid atoms
        assertFalse(table.isValidAtom(-1));
        assertFalse(table.isValidAtom(99999));
    }

    @Test
    public void testGetAtomInterned() {
        AtomTable table = new AtomTable();

        int atom = table.intern("existing");
        assertEquals(atom, table.getAtom("existing"));
    }

    @Test
    public void testGetAtomNotInterned() {
        AtomTable table = new AtomTable();

        assertEquals(-1, table.getAtom("nonexistent"));
    }

    @Test
    public void testSize() {
        AtomTable table = new AtomTable();

        int initialSize = table.size();
        assertTrue(initialSize >= 41); // At least the well-known atoms

        table.intern("one");
        assertEquals(initialSize + 1, table.size());

        table.intern("two");
        assertEquals(initialSize + 2, table.size());

        table.intern("one"); // Duplicate
        assertEquals(initialSize + 2, table.size()); // Size shouldn't increase
    }

    @Test
    public void testClear() {
        AtomTable table = new AtomTable();

        int initialSize = table.size();

        // Add some user atoms
        table.intern("user1");
        table.intern("user2");
        assertTrue(table.size() > initialSize);

        // Clear
        table.clear();

        // Well-known atoms should still be there
        assertEquals(initialSize, table.size());
        assertTrue(table.isValidAtom(AtomTable.ATOM_NULL));
        assertEquals("null", table.getString(AtomTable.ATOM_NULL));

        // User atoms should be gone
        assertEquals(-1, table.getAtom("user1"));
        assertEquals(-1, table.getAtom("user2"));
    }

    @Test
    public void testToString() {
        AtomTable table = new AtomTable();

        String str = table.toString();
        assertTrue(str.startsWith("AtomTable{size="));
        assertTrue(str.endsWith("}"));
        assertTrue(str.contains(String.valueOf(table.size())));
    }

    @Test
    public void testInternEmptyString() {
        AtomTable table = new AtomTable();

        int atom = table.intern("");
        assertTrue(table.isValidAtom(atom));
        assertEquals("", table.getString(atom));
    }

    @Test
    public void testInternUnicodeString() {
        AtomTable table = new AtomTable();

        String unicode = "🚀🌟";
        int atom = table.intern(unicode);
        assertEquals(unicode, table.getString(atom));
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
        assertEquals(atoms[0], atoms[3]);
        assertEquals(atoms[1], atoms[4]);

        // Different strings should have different atoms
        assertNotEquals(atoms[0], atoms[1]);
        assertNotEquals(atoms[1], atoms[2]);
        assertNotEquals(atoms[0], atoms[2]);
    }
}
