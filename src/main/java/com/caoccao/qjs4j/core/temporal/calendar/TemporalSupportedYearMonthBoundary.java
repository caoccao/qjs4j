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

package com.caoccao.qjs4j.core.temporal.calendar;

public record TemporalSupportedYearMonthBoundary(int minimumYear, int minimumMonth, int maximumYear, int maximumMonth) {

    public static TemporalSupportedYearMonthBoundary forCalendar(TemporalCalendarId calendarId) {
        return switch (calendarId) {
            case BUDDHIST -> new TemporalSupportedYearMonthBoundary(-271278, 5, 276303, 9);
            case COPTIC -> new TemporalSupportedYearMonthBoundary(-272099, 4, 275471, 6);
            case ETHIOAA -> new TemporalSupportedYearMonthBoundary(-266323, 4, 281247, 6);
            case ETHIOPIC -> new TemporalSupportedYearMonthBoundary(-271823, 4, 275747, 6);
            case GREGORY, JAPANESE -> new TemporalSupportedYearMonthBoundary(-271821, 4, 275760, 9);
            case HEBREW -> new TemporalSupportedYearMonthBoundary(-268058, 12, 279517, 10);
            case INDIAN -> new TemporalSupportedYearMonthBoundary(-271899, 2, 275682, 7);
            case ISLAMIC_CIVIL, ISLAMIC_TBLA, ISLAMIC_UMALQURA ->
                new TemporalSupportedYearMonthBoundary(-280804, 4, 283583, 6);
            case PERSIAN -> new TemporalSupportedYearMonthBoundary(-272442, 2, 275139, 7);
            case ROC -> new TemporalSupportedYearMonthBoundary(-273732, 5, 273849, 9);
            default -> null;
        };
    }

    public boolean excludes(int year, int month) {
        return year < minimumYear || (year == minimumYear && month < minimumMonth) || year > maximumYear
                || (year == maximumYear && month > maximumMonth);
    }
}
