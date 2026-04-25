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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TemporalCalendarIdReferenceIsoDateIndexTest {
    private IsoDate findReferenceIsoDateAtOrBelowManual(
            TemporalCalendarId calendarId,
            String monthCode,
            int maximumDayOfMonth) {
        int constrainedMaximumDay = Math.min(maximumDayOfMonth, 31);
        for (int dayOfMonth = constrainedMaximumDay; dayOfMonth >= 1; dayOfMonth--) {
            IsoDate candidateIsoDate = calendarId.findReferenceIsoDateExact(monthCode, dayOfMonth);
            if (candidateIsoDate != null) {
                return candidateIsoDate;
            }
        }
        return null;
    }

    private void verifyAtOrBelowMatchesManual(TemporalCalendarId calendarId, String monthCode) {
        int[] maximumDays = {31, 30, 29, 28, 15, 1};
        for (int maximumDay : maximumDays) {
            IsoDate indexedLookupIsoDate = calendarId.findReferenceIsoDateAtOrBelow(monthCode, maximumDay);
            IsoDate manualLookupIsoDate = findReferenceIsoDateAtOrBelowManual(calendarId, monthCode, maximumDay);
            assertEquals(manualLookupIsoDate, indexedLookupIsoDate);
        }
    }

    @Test
    public void testFindReferenceIsoDateAtOrBelowForChineseAndDangi() {
        verifyAtOrBelowMatchesManual(TemporalCalendarId.CHINESE, "M01");
        verifyAtOrBelowMatchesManual(TemporalCalendarId.CHINESE, "M03");
        verifyAtOrBelowMatchesManual(TemporalCalendarId.CHINESE, "M03L");
        verifyAtOrBelowMatchesManual(TemporalCalendarId.DANGI, "M01");
        verifyAtOrBelowMatchesManual(TemporalCalendarId.DANGI, "M03");
        verifyAtOrBelowMatchesManual(TemporalCalendarId.DANGI, "M03L");
    }

    @Test
    public void testFindReferenceIsoDateAtOrBelowForIsoAndHebrew() {
        verifyAtOrBelowMatchesManual(TemporalCalendarId.ISO8601, "M02");
        verifyAtOrBelowMatchesManual(TemporalCalendarId.HEBREW, "M01");
        verifyAtOrBelowMatchesManual(TemporalCalendarId.HEBREW, "M05");
        verifyAtOrBelowMatchesManual(TemporalCalendarId.HEBREW, "M05L");
    }

    @Test
    public void testFindReferenceIsoDateExactAndAtOrBelowOutOfRangeDay() {
        assertNull(TemporalCalendarId.CHINESE.findReferenceIsoDateExact("M03", 0));
        assertNull(TemporalCalendarId.CHINESE.findReferenceIsoDateExact("M03", 32));
        assertNull(TemporalCalendarId.CHINESE.findReferenceIsoDateAtOrBelow("M03", 0));
    }
}

