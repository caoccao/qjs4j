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

package com.caoccao.qjs4j.builtins.temporal;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

public class TemporalFieldResolverParityTest extends BaseJavetTest {
    @Test
    public void testCalendarStringAnnotationParsingWithCalendarDefaulting() {
        assertStringWithJavet(
                "Temporal.PlainYearMonth.from({ year: 2024, month: 7, calendar: '2020-01-01[u-ca=iso8601]' }).calendarId");
    }

    @Test
    public void testEraAliasFromPlainDate() {
        assertIntegerWithJavet(
                "Temporal.PlainDate.from({ calendar: 'gregory', era: 'ad', eraYear: 2024, month: 1, day: 1 }).year");
    }

    @Test
    public void testInvalidMonthCodeSyntaxFromZonedDateTime() {
        assertErrorWithJavet(
                "Temporal.ZonedDateTime.from({ year: 2024, monthCode: 'X01', day: 1, hour: 1, timeZone: 'UTC' })");
    }

    @Test
    public void testMonthCodeTypeErrorFromPlainYearMonth() {
        assertErrorWithJavet(
                "Temporal.PlainYearMonth.from({ year: 2024, monthCode: { valueOf() { return 1; } }, day: 1, calendar: 'iso8601' })");
    }
}
