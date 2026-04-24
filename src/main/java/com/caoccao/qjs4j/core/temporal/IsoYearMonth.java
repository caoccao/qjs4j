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

public record IsoYearMonth(int year, int monthNumber) {

    static IsoYearMonth getNextMonth(TemporalCalendarId calendarId, IsoYearMonth currentIndex) {
        TemporalMonths monthSlots = TemporalMonths.get(calendarId, currentIndex.year());
        if (currentIndex.monthNumber() < monthSlots.size()) {
            return new IsoYearMonth(currentIndex.year(), currentIndex.monthNumber() + 1);
        }
        return new IsoYearMonth(currentIndex.year() + 1, 1);
    }

    static IsoYearMonth getPreviousMonth(TemporalCalendarId calendarId, IsoYearMonth currentIndex) {
        if (currentIndex.monthNumber() > 1) {
            return new IsoYearMonth(currentIndex.year(), currentIndex.monthNumber() - 1);
        }
        int previousYear = currentIndex.year() - 1;
        TemporalMonths previousYearSlots = TemporalMonths.get(calendarId, previousYear);
        return new IsoYearMonth(previousYear, previousYearSlots.size());
    }
}
