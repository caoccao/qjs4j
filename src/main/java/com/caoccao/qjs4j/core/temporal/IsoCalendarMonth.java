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

import com.caoccao.qjs4j.core.JSContext;

public record IsoCalendarMonth(
        int monthNumber,
        boolean leapMonth,
        String monthCode,
        int daysInMonth) {

    static IsoCalendarMonth resolveMonthSlotForInput(
            JSContext context,
            TemporalCalendarId calendarId,
            int calendarYear,
            Integer monthFromProperty,
            String monthCodeFromProperty,
            String overflow) {
        TemporalMonths monthSlots = TemporalMonths.get(calendarId, calendarYear);
        IsoCalendarMonth monthSlotFromNumber = null;
        if (monthFromProperty != null) {
            int monthNumber = monthFromProperty;
            if (monthNumber < 1) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            if (monthNumber > monthSlots.size()) {
                if (!TemporalOverflow.REJECT.matches(overflow) && monthCodeFromProperty == null) {
                    monthNumber = monthSlots.size();
                } else {
                    context.throwRangeError("Temporal error: Invalid ISO date.");
                    return null;
                }
            }
            monthSlotFromNumber = monthSlots.getByMonthNumber(monthNumber);
        }

        IsoCalendarMonth monthSlotFromCode = null;
        if (monthCodeFromProperty != null) {
            IsoMonth monthCodeData = IsoMonth.parseByMonthCode(monthCodeFromProperty);
            if (monthCodeData == null) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            monthSlotFromCode = monthSlots.getByMonthCode(monthCodeFromProperty);
            if (monthSlotFromCode == null) {
                if (!TemporalOverflow.REJECT.matches(overflow) && monthCodeData.leapMonth()) {
                    String fallbackMonthCode = calendarId.resolveFallbackMonthCodeForMissingLeapMonth(monthCodeFromProperty);
                    monthSlotFromCode = monthSlots.getByMonthCode(fallbackMonthCode);
                }
            }
            if (monthSlotFromCode == null) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
        }

        if (monthSlotFromNumber != null && monthSlotFromCode != null) {
            if (!monthSlotFromNumber.monthCode().equals(monthSlotFromCode.monthCode())) {
                context.throwRangeError("Temporal error: Invalid ISO date.");
                return null;
            }
            return monthSlotFromCode;
        }
        if (monthSlotFromNumber != null) {
            return monthSlotFromNumber;
        }
        if (monthSlotFromCode != null) {
            return monthSlotFromCode;
        }
        context.throwRangeError("Temporal error: Invalid ISO date.");
        return null;
    }
}
