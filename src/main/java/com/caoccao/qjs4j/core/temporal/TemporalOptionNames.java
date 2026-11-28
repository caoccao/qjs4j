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

package com.caoccao.qjs4j.core.temporal;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Builds a read-only lookup from the JavaScript spelling of each Temporal option. */
public final class TemporalOptionNames {
    private TemporalOptionNames() {
    }

    public static <E extends Enum<E>> Map<String, E> index(E[] options, Function<E, String> name) {
        Map<String, E> byName = new HashMap<>();
        for (E option : options) {
            byName.put(name.apply(option), option);
        }
        return Collections.unmodifiableMap(byName);
    }
}
