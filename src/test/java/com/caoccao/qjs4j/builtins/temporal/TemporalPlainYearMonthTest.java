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

public class TemporalPlainYearMonthTest extends BaseJavetTest {

    // ========== Constructor tests ==========

    @Test
    public void testAdd() {
        assertStringWithJavet("new Temporal.PlainYearMonth(2024, 3).add({months: 3}).toString()");
    }

    @Test
    public void testCalendarId() {
        assertStringWithJavet("new Temporal.PlainYearMonth(2024, 3).calendarId");
    }

    @Test
    public void testCompare() {
        assertIntegerWithJavet("Temporal.PlainYearMonth.compare('2024-03', '2024-04')");
    }

    @Test
    public void testConstructorBasic() {
        assertStringWithJavet("new Temporal.PlainYearMonth(2024, 3).toString()");
    }

    @Test
    public void testConstructorInvalidMonth() {
        assertErrorWithJavet("new Temporal.PlainYearMonth(2024, 13)");
    }

    @Test
    public void testConstructorWithCalendar() {
        assertStringWithJavet("new Temporal.PlainYearMonth(2024, 3, 'iso8601').toString()");
    }

    @Test
    public void testConstructorWithSubclass() {
        assertBooleanWithJavet("(() => { class CustomPlainYearMonth extends Temporal.PlainYearMonth {} const value = new CustomPlainYearMonth(2024, 3); return value instanceof CustomPlainYearMonth && value instanceof Temporal.PlainYearMonth; })()");
    }

    @Test
    public void testConstructorWithoutNew() {
        assertErrorWithJavet("Temporal.PlainYearMonth(2024, 3)");
    }

    @Test
    public void testDaysInMonth() {
        assertIntegerWithJavet("new Temporal.PlainYearMonth(2024, 2).daysInMonth");
    }

    @Test
    public void testDaysInYear() {
        assertIntegerWithJavet("new Temporal.PlainYearMonth(2024, 1).daysInYear");
    }

    @Test
    public void testEquals() {
        assertBooleanWithJavet("new Temporal.PlainYearMonth(2024, 3).equals('2024-03')");
    }

    @Test
    public void testEra() {
        assertUndefinedWithJavet("new Temporal.PlainYearMonth(2024, 3).era");
    }

    @Test
    public void testEraYear() {
        assertUndefinedWithJavet("new Temporal.PlainYearMonth(2024, 3).eraYear");
    }

    @Test
    public void testFrom() {
        assertStringWithJavet("Temporal.PlainYearMonth.from('2024-03').toString()");
    }

    @Test
    public void testFromObject() {
        assertStringWithJavet("Temporal.PlainYearMonth.from({year: 2024, month: 3}).toString()");
    }

    @Test
    public void testInLeapYear() {
        assertBooleanWithJavet("new Temporal.PlainYearMonth(2024, 1).inLeapYear");
    }

    @Test
    public void testMonth() {
        assertIntegerWithJavet("new Temporal.PlainYearMonth(2024, 3).month");
    }

    @Test
    public void testMonthCode() {
        assertStringWithJavet("new Temporal.PlainYearMonth(2024, 3).monthCode");
    }

    @Test
    public void testMonthsInYear() {
        assertIntegerWithJavet("new Temporal.PlainYearMonth(2024, 1).monthsInYear");
    }

    @Test
    public void testSince() {
        assertStringWithJavet("new Temporal.PlainYearMonth(2024, 6).since('2024-01').toString()");
    }

    @Test
    public void testSubtract() {
        assertStringWithJavet("new Temporal.PlainYearMonth(2024, 3).subtract({months: 1}).toString()");
    }

    @Test
    public void testToJSON() {
        assertStringWithJavet("new Temporal.PlainYearMonth(2024, 3).toJSON()");
    }

    @Test
    public void testToLocaleString() {
        assertBooleanWithJavet("(() => { const value = new Temporal.PlainYearMonth(2024, 3); const result = value.toLocaleString('en-US', { calendar: 'iso8601', timeZone: 'UTC', year: 'numeric', month: 'long' }); return typeof result === 'string' && result.length > 0; })()");
    }

    @Test
    public void testToPlainDate() {
        assertStringWithJavet("new Temporal.PlainYearMonth(2024, 3).toPlainDate({day: 15}).toString()");
    }

    @Test
    public void testToPlainDateNoDay() {
        assertErrorWithJavet("new Temporal.PlainYearMonth(2024, 3).toPlainDate()");
    }

    @Test
    public void testToString() {
        assertStringWithJavet("new Temporal.PlainYearMonth(2024, 3).toString()");
    }

    @Test
    public void testUntil() {
        assertStringWithJavet("new Temporal.PlainYearMonth(2024, 1).until('2024-06').toString()");
    }

    @Test
    public void testValueOf() {
        assertErrorWithJavet("new Temporal.PlainYearMonth(2024, 3).valueOf()");
    }

    @Test
    public void testWith() {
        assertStringWithJavet("new Temporal.PlainYearMonth(2024, 3).with({month: 6}).toString()");
    }

    @Test
    public void testYear() {
        assertIntegerWithJavet("new Temporal.PlainYearMonth(2024, 3).year");
    }
}
