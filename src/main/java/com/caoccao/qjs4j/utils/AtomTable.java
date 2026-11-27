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
 * Atom table for interned strings. Provides fast string comparison using integer indices (atoms). Based on QuickJS atom
 * system in quickjs.c.
 * <p>
 * Atoms are used extensively in JavaScript engines for: - Property names - Variable names - Built-in symbols - Fast
 * string comparison (compare integers instead of strings)
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
    // Reserved for future well-known atoms
    private static final int ATOM_RESERVED_COUNT = 128;
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
    private static final Map<String, Integer> KEYWORD_ATOMS = Map.ofEntries(Map.entry(JSKeyword.NULL, ATOM_NULL),
            Map.entry(JSKeyword.FALSE, ATOM_FALSE), Map.entry(JSKeyword.TRUE, ATOM_TRUE),
            Map.entry(JSKeyword.IF, ATOM_IF), Map.entry(JSKeyword.ELSE, ATOM_ELSE),
            Map.entry(JSKeyword.RETURN, ATOM_RETURN), Map.entry(JSKeyword.VAR, ATOM_VAR),
            Map.entry(JSKeyword.THIS, ATOM_THIS), Map.entry(JSKeyword.DELETE, ATOM_DELETE),
            Map.entry(JSKeyword.VOID, ATOM_VOID), Map.entry(JSKeyword.TYPEOF, ATOM_TYPEOF),
            Map.entry(JSKeyword.NEW, ATOM_NEW), Map.entry(JSKeyword.IN, ATOM_IN),
            Map.entry(JSKeyword.INSTANCEOF, ATOM_INSTANCEOF), Map.entry(JSKeyword.DO, ATOM_DO),
            Map.entry(JSKeyword.WHILE, ATOM_WHILE), Map.entry(JSKeyword.FOR, ATOM_FOR),
            Map.entry(JSKeyword.BREAK, ATOM_BREAK), Map.entry(JSKeyword.CONTINUE, ATOM_CONTINUE),
            Map.entry(JSKeyword.SWITCH, ATOM_SWITCH), Map.entry(JSKeyword.CASE, ATOM_CASE),
            Map.entry(JSKeyword.DEFAULT, ATOM_DEFAULT), Map.entry(JSKeyword.THROW, ATOM_THROW),
            Map.entry(JSKeyword.TRY, ATOM_TRY), Map.entry(JSKeyword.CATCH, ATOM_CATCH),
            Map.entry(JSKeyword.FINALLY, ATOM_FINALLY), Map.entry(JSKeyword.FUNCTION, ATOM_FUNCTION),
            Map.entry(JSKeyword.DEBUGGER, ATOM_DEBUGGER), Map.entry(JSKeyword.WITH, ATOM_WITH),
            Map.entry(JSKeyword.CLASS, ATOM_CLASS), Map.entry(JSKeyword.CONST, ATOM_CONST),
            Map.entry(JSKeyword.ENUM, ATOM_ENUM), Map.entry(JSKeyword.EXPORT, ATOM_EXPORT),
            Map.entry(JSKeyword.EXTENDS, ATOM_EXTENDS), Map.entry(JSKeyword.IMPORT, ATOM_IMPORT),
            Map.entry(JSKeyword.SUPER, ATOM_SUPER), Map.entry(JSKeyword.LET, ATOM_LET),
            Map.entry(JSKeyword.STATIC, ATOM_STATIC), Map.entry(JSKeyword.YIELD, ATOM_YIELD),
            Map.entry(JSKeyword.AWAIT, ATOM_AWAIT), Map.entry(JSKeyword.ASYNC, ATOM_ASYNC));
    private static final String[] KEYWORD_NAMES = new String[ATOM_RESERVED_COUNT];

    static {
        KEYWORD_ATOMS.forEach((name, atom) -> KEYWORD_NAMES[atom] = name);
    }

    private Map<String, Integer> atoms = KEYWORD_ATOMS;
    private final List<String> userNames = new ArrayList<>();

    /**
     * Clear all atoms (except well-known atoms).
     */
    public void clear() {
        atoms = KEYWORD_ATOMS;
        userNames.clear();
    }

    /**
     * Get the atom index for a string without interning it. Returns -1 if the string is not interned.
     */
    public int getAtom(String str) {
        return str == null ? -1 : atoms.getOrDefault(str, -1);
    }

    /**
     * Get the string for a given atom index.
     */
    public String getString(int atom) {
        if (atom < 0 || atom >= size()) {
            return null;
        }
        return atom < ATOM_RESERVED_COUNT ? KEYWORD_NAMES[atom] : userNames.get(atom - ATOM_RESERVED_COUNT);
    }

    /**
     * Intern a string and return its atom index. If the string is already interned, returns the existing atom.
     */
    public int intern(String str) {
        if (str == null) {
            return -1;
        }

        Integer atom = atoms.get(str);
        if (atom != null) {
            return atom;
        }

        if (atoms == KEYWORD_ATOMS) {
            atoms = new HashMap<>(KEYWORD_ATOMS);
        }
        int newAtom = size();
        userNames.add(str);
        atoms.put(str, newAtom);
        return newAtom;
    }

    /**
     * Check if an atom index is valid.
     */
    public boolean isValidAtom(int atom) {
        return getString(atom) != null;
    }

    /**
     * Get the number of interned strings.
     */
    public int size() {
        return ATOM_RESERVED_COUNT + userNames.size();
    }

    @Override
    public String toString() {
        return "AtomTable{size=" + size() + "}";
    }
}
