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

public record TemporalDateDurationFields(long years, long months, long weeks, long days) {
    public static final TemporalDateDurationFields ZERO = new TemporalDateDurationFields(0, 0, 0, 0);

    public int sign() {
        if (years > 0 || months > 0 || weeks > 0 || days > 0) {
            return 1;
        } else if (years < 0 || months < 0 || weeks < 0 || days < 0) {
            return -1;
        } else {
            return 0;
        }
    }
}
