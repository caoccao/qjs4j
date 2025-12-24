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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Atom table for interned strings.
 * Provides fast string comparison using integer indices.
 */
public final class AtomTable {
    private final Map<String, Integer> stringToAtom;
    private final List<String> atomToString;

    public AtomTable() {
        this.stringToAtom = new HashMap<>();
        this.atomToString = new ArrayList<>();
    }

    public int intern(String str) {
        Integer atom = stringToAtom.get(str);
        if (atom != null) {
            return atom;
        }

        int newAtom = atomToString.size();
        atomToString.add(str);
        stringToAtom.put(str, newAtom);
        return newAtom;
    }

    public String getString(int atom) {
        return atomToString.get(atom);
    }

    public int size() {
        return atomToString.size();
    }
}
