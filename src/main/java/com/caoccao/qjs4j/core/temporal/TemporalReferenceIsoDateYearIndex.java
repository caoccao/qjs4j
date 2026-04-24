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

import java.util.HashMap;
import java.util.Map;

/**
 * Per-(calendar, ISO year) lookup index for monthCode/day -> reference ISO date.
 * <p>
 * It preserves previous semantics where the last matching ISO date in the year
 * is selected when multiple dates map to the same monthCode/day.
 */
final class TemporalReferenceIsoDateYearIndex {
    private static final int MAX_DAY_OF_MONTH = 31;
    private static final int MIN_DAY_OF_MONTH = 1;

    private final Map<String, IsoDate[]> isoDatesByMonthCode;

    private TemporalReferenceIsoDateYearIndex(Map<String, IsoDate[]> isoDatesByMonthCode) {
        this.isoDatesByMonthCode = isoDatesByMonthCode;
    }

    static TemporalReferenceIsoDateYearIndex create(TemporalCalendarId calendarId, int isoYear) {
        Map<String, IsoDate[]> indexedIsoDates = new HashMap<>();
        for (int isoMonth = 1; isoMonth <= 12; isoMonth++) {
            int daysInMonth = IsoDate.daysInMonth(isoYear, isoMonth);
            for (int isoDay = 1; isoDay <= daysInMonth; isoDay++) {
                IsoDate candidateIsoDate = new IsoDate(isoYear, isoMonth, isoDay);
                IsoCalendarDate candidateCalendarDate = candidateIsoDate.toIsoCalendarDate(calendarId);
                int dayOfMonth = candidateCalendarDate.day();
                if (dayOfMonth < MIN_DAY_OF_MONTH || dayOfMonth > MAX_DAY_OF_MONTH) {
                    continue;
                }
                IsoDate[] dayLookup = indexedIsoDates.computeIfAbsent(
                        candidateCalendarDate.monthCode(),
                        key -> new IsoDate[MAX_DAY_OF_MONTH + 1]);
                dayLookup[dayOfMonth] = candidateIsoDate;
            }
        }
        return new TemporalReferenceIsoDateYearIndex(indexedIsoDates);
    }

    IsoDate findExact(String monthCode, int dayOfMonth) {
        if (dayOfMonth < MIN_DAY_OF_MONTH || dayOfMonth > MAX_DAY_OF_MONTH) {
            return null;
        }
        IsoDate[] dayLookup = isoDatesByMonthCode.get(monthCode);
        if (dayLookup == null) {
            return null;
        }
        return dayLookup[dayOfMonth];
    }
}
