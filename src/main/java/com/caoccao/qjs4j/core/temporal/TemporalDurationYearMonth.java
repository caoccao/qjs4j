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

public record TemporalDurationYearMonth(long years, long months) {
    public static final TemporalDurationYearMonth ZERO = new TemporalDurationYearMonth(0L, 0L);

    public static TemporalDurationYearMonth fromTotalMonths(long totalMonthsDifference, TemporalUnit largestUnit) {
        if (largestUnit == TemporalUnit.YEAR) {
            long years = totalMonthsDifference / 12L;
            long months = totalMonthsDifference % 12L;
            return new TemporalDurationYearMonth(years, months);
        }
        return new TemporalDurationYearMonth(0L, totalMonthsDifference);
    }
}
