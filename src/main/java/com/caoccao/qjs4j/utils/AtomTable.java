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

import com.caoccao.qjs4j.core.JSKeyword;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Atom table for interned strings.
 * Provides fast string comparison using integer indices (atoms).
 * Based on QuickJS atom system in quickjs.c.
 * <p>
 * Atoms are used extensively in JavaScript engines for:
 * - Property names
 * - Variable names
 * - Built-in symbols
 * - Fast string comparison (compare integers instead of strings)
 */
public final class AtomTable {
    public static final int ATOM_ASYNC = 40;
    public static final int ATOM_AWAIT = 39;
    public static final int ATOM_BREAK = 17;
    public static final int ATOM_CASE = 20;
    public static final int ATOM_CATCH = 24;
    public static final int ATOM_CLASS = 29;
    public static final int ATOM_CONST = 30;
    public static final int ATOM_CONTINUE = 18;
    public static final int ATOM_DEBUGGER = 27;
    public static final int ATOM_DEFAULT = 21;
    public static final int ATOM_DELETE = 8;
    public static final int ATOM_DO = 14;
    public static final int ATOM_ELSE = 4;
    public static final int ATOM_ENUM = 31;
    public static final int ATOM_EXPORT = 32;
    public static final int ATOM_EXTENDS = 33;
    public static final int ATOM_FALSE = 1;
    public static final int ATOM_FINALLY = 25;
    public static final int ATOM_FOR = 16;
    public static final int ATOM_FUNCTION = 26;
    public static final int ATOM_IF = 3;
    public static final int ATOM_IMPORT = 34;
    public static final int ATOM_IN = 12;
    public static final int ATOM_INSTANCEOF = 13;
    public static final int ATOM_LET = 36;
    public static final int ATOM_NEW = 11;
    // Well-known atom indices for common JavaScript identifiers
    public static final int ATOM_NULL = 0;
    public static final int ATOM_RETURN = 5;
    public static final int ATOM_STATIC = 37;
    public static final int ATOM_SUPER = 35;
    public static final int ATOM_SWITCH = 19;
    public static final int ATOM_THIS = 7;
    public static final int ATOM_THROW = 22;
    public static final int ATOM_TRUE = 2;
    public static final int ATOM_TRY = 23;
    public static final int ATOM_TYPEOF = 10;
    public static final int ATOM_VAR = 6;
    public static final int ATOM_VOID = 9;
    public static final int ATOM_WHILE = 15;
    public static final int ATOM_WITH = 28;
    public static final int ATOM_YIELD = 38;
    // Reserved for future well-known atoms
    private static final int ATOM_RESERVED_COUNT = 128;
    private final List<String> atomToString;
    private final Map<String, Integer> stringToAtom;

    public AtomTable() {
        this.stringToAtom = new HashMap<>();
        this.atomToString = new ArrayList<>();

        // Pre-allocate well-known atoms
        initializeWellKnownAtoms();
    }

    /**
     * Clear all atoms (except well-known atoms).
     */
    public void clear() {
        stringToAtom.clear();
        atomToString.clear();
        initializeWellKnownAtoms();
    }

    /**
     * Get the atom index for a string without interning it.
     * Returns -1 if the string is not interned.
     */
    public int getAtom(String str) {
        Integer atom = stringToAtom.get(str);
        return atom != null ? atom : -1;
    }

    /**
     * Get the string for a given atom index.
     */
    public String getString(int atom) {
        if (atom < 0 || atom >= atomToString.size()) {
            return null;
        }
        return atomToString.get(atom);
    }

    /**
     * Initialize well-known atoms (JavaScript keywords and common identifiers).
     */
    private void initializeWellKnownAtoms() {
        // Reserve space for well-known atoms
        for (int i = 0; i < ATOM_RESERVED_COUNT; i++) {
            atomToString.add(null);
        }

        // Initialize the well-known atoms
        setWellKnownAtom(ATOM_NULL, JSKeyword.NULL);
        setWellKnownAtom(ATOM_FALSE, JSKeyword.FALSE);
        setWellKnownAtom(ATOM_TRUE, JSKeyword.TRUE);
        setWellKnownAtom(ATOM_IF, JSKeyword.IF);
        setWellKnownAtom(ATOM_ELSE, JSKeyword.ELSE);
        setWellKnownAtom(ATOM_RETURN, JSKeyword.RETURN);
        setWellKnownAtom(ATOM_VAR, JSKeyword.VAR);
        setWellKnownAtom(ATOM_THIS, JSKeyword.THIS);
        setWellKnownAtom(ATOM_DELETE, JSKeyword.DELETE);
        setWellKnownAtom(ATOM_VOID, JSKeyword.VOID);
        setWellKnownAtom(ATOM_TYPEOF, JSKeyword.TYPEOF);
        setWellKnownAtom(ATOM_NEW, JSKeyword.NEW);
        setWellKnownAtom(ATOM_IN, JSKeyword.IN);
        setWellKnownAtom(ATOM_INSTANCEOF, JSKeyword.INSTANCEOF);
        setWellKnownAtom(ATOM_DO, JSKeyword.DO);
        setWellKnownAtom(ATOM_WHILE, JSKeyword.WHILE);
        setWellKnownAtom(ATOM_FOR, JSKeyword.FOR);
        setWellKnownAtom(ATOM_BREAK, JSKeyword.BREAK);
        setWellKnownAtom(ATOM_CONTINUE, JSKeyword.CONTINUE);
        setWellKnownAtom(ATOM_SWITCH, JSKeyword.SWITCH);
        setWellKnownAtom(ATOM_CASE, JSKeyword.CASE);
        setWellKnownAtom(ATOM_DEFAULT, JSKeyword.DEFAULT);
        setWellKnownAtom(ATOM_THROW, JSKeyword.THROW);
        setWellKnownAtom(ATOM_TRY, JSKeyword.TRY);
        setWellKnownAtom(ATOM_CATCH, JSKeyword.CATCH);
        setWellKnownAtom(ATOM_FINALLY, JSKeyword.FINALLY);
        setWellKnownAtom(ATOM_FUNCTION, JSKeyword.FUNCTION);
        setWellKnownAtom(ATOM_DEBUGGER, JSKeyword.DEBUGGER);
        setWellKnownAtom(ATOM_WITH, JSKeyword.WITH);
        setWellKnownAtom(ATOM_CLASS, JSKeyword.CLASS);
        setWellKnownAtom(ATOM_CONST, JSKeyword.CONST);
        setWellKnownAtom(ATOM_ENUM, JSKeyword.ENUM);
        setWellKnownAtom(ATOM_EXPORT, JSKeyword.EXPORT);
        setWellKnownAtom(ATOM_EXTENDS, JSKeyword.EXTENDS);
        setWellKnownAtom(ATOM_IMPORT, JSKeyword.IMPORT);
        setWellKnownAtom(ATOM_SUPER, JSKeyword.SUPER);
        setWellKnownAtom(ATOM_LET, JSKeyword.LET);
        setWellKnownAtom(ATOM_STATIC, JSKeyword.STATIC);
        setWellKnownAtom(ATOM_YIELD, JSKeyword.YIELD);
        setWellKnownAtom(ATOM_AWAIT, JSKeyword.AWAIT);
        setWellKnownAtom(ATOM_ASYNC, JSKeyword.ASYNC);
    }

    /**
     * Intern a string and return its atom index.
     * If the string is already interned, returns the existing atom.
     */
    public int intern(String str) {
        if (str == null) {
            return -1;
        }

        Integer atom = stringToAtom.get(str);
        if (atom != null) {
            return atom;
        }

        int newAtom = atomToString.size();
        atomToString.add(str);
        stringToAtom.put(str, newAtom);
        return newAtom;
    }

    /**
     * Check if an atom index is valid.
     */
    public boolean isValidAtom(int atom) {
        return atom >= 0 && atom < atomToString.size() && atomToString.get(atom) != null;
    }

    private void setWellKnownAtom(int index, String str) {
        atomToString.set(index, str);
        stringToAtom.put(str, index);
    }

    /**
     * Get the number of interned strings.
     */
    public int size() {
        return atomToString.size();
    }

    @Override
    public String toString() {
        return "AtomTable{size=" + size() + "}";
    }
}
