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

import com.caoccao.qjs4j.BaseTemporalJavetTest;
import org.junit.jupiter.api.Test;

public class TemporalPlainDateTest extends BaseTemporalJavetTest {

    // ========== Constructor tests ==========

    @Test
    public void testAdd() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 1, 15).add({days: 10}).toString()");
    }

    @Test
    public void testAddMonths() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 1, 31).add({months: 1}).toString()");
    }

    @Test
    public void testCalendarId() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 3, 15).calendarId");
    }

    @Test
    public void testCompare() {
        assertIntegerWithJavet("Temporal.PlainDate.compare('2024-01-15', '2024-01-16')");
    }

    @Test
    public void testCompareEqual() {
        assertIntegerWithJavet("Temporal.PlainDate.compare('2024-01-15', '2024-01-15')");
    }

    @Test
    public void testCompareGreater() {
        assertIntegerWithJavet("Temporal.PlainDate.compare('2024-02-01', '2024-01-15')");
    }

    @Test
    public void testCompareNoArgs() {
        assertErrorWithJavet("Temporal.PlainDate.compare()");
    }

    // ========== Static method tests ==========

    @Test
    public void testConstructorBasic() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 1, 15).toString()");
    }

    @Test
    public void testConstructorCalendarMustBeString() {
        assertErrorWithJavet("new Temporal.PlainDate(2024, 1, 1, 42)");
    }

    @Test
    public void testConstructorInvalidDate() {
        assertErrorWithJavet("new Temporal.PlainDate(2024, 13, 1)");
    }

    @Test
    public void testConstructorInvalidDay() {
        assertErrorWithJavet("new Temporal.PlainDate(2024, 2, 30)");
    }

    @Test
    public void testConstructorLength() {
        assertIntegerWithJavet("Temporal.PlainDate.length");
    }

    @Test
    public void testConstructorNonIntegerArgs() {
        assertErrorWithJavet("new Temporal.PlainDate(Infinity, 1, 1)");
    }

    @Test
    public void testConstructorWithCalendar() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 1, 15, 'iso8601').toString()");
    }

    @Test
    public void testConstructorWithoutNew() {
        assertErrorWithJavet("Temporal.PlainDate(2024, 1, 15)");
    }

    @Test
    public void testDay() {
        assertIntegerWithJavet("new Temporal.PlainDate(2024, 3, 15).day");
    }

    // ========== Getter tests ==========

    @Test
    public void testDayOfWeek() {
        assertIntegerWithJavet("new Temporal.PlainDate(2024, 3, 15).dayOfWeek");
    }

    @Test
    public void testDayOfYear() {
        assertIntegerWithJavet("new Temporal.PlainDate(2024, 3, 15).dayOfYear");
    }

    @Test
    public void testDaysInMonth() {
        assertIntegerWithJavet("new Temporal.PlainDate(2024, 2, 15).daysInMonth");
    }

    @Test
    public void testDaysInWeek() {
        assertIntegerWithJavet("new Temporal.PlainDate(2024, 3, 15).daysInWeek");
    }

    @Test
    public void testDaysInYear() {
        assertIntegerWithJavet("new Temporal.PlainDate(2024, 3, 15).daysInYear");
    }

    @Test
    public void testEquals() {
        assertBooleanWithJavet("new Temporal.PlainDate(2024, 1, 15).equals('2024-01-15')");
    }

    @Test
    public void testEqualsNotEqual() {
        assertBooleanWithJavet("new Temporal.PlainDate(2024, 1, 15).equals('2024-01-16')");
    }

    @Test
    public void testEra() {
        assertUndefinedWithJavet("new Temporal.PlainDate(2024, 3, 15).era");
    }

    @Test
    public void testEraYear() {
        assertUndefinedWithJavet("new Temporal.PlainDate(2024, 3, 15).eraYear");
    }

    @Test
    public void testFrom() {
        assertStringWithJavet("Temporal.PlainDate.from('2024-01-15').toString()");
    }

    @Test
    public void testFromInvalidString() {
        assertErrorWithJavet("Temporal.PlainDate.from('invalid')");
    }

    @Test
    public void testFromNonStringNonObject() {
        assertErrorWithJavet("Temporal.PlainDate.from(42)");
    }

    @Test
    public void testFromObject() {
        assertStringWithJavet("Temporal.PlainDate.from({year: 2024, month: 1, day: 15}).toString()");
    }

    @Test
    public void testFromPlainDate() {
        assertStringWithJavet("Temporal.PlainDate.from(new Temporal.PlainDate(2024, 1, 15)).toString()");
    }

    @Test
    public void testInLeapYear() {
        assertBooleanWithJavet("new Temporal.PlainDate(2024, 3, 15).inLeapYear");
    }

    @Test
    public void testMonth() {
        assertIntegerWithJavet("new Temporal.PlainDate(2024, 3, 15).month");
    }

    // ========== Prototype method tests ==========

    @Test
    public void testMonthCode() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 3, 15).monthCode");
    }

    @Test
    public void testMonthsInYear() {
        assertIntegerWithJavet("new Temporal.PlainDate(2024, 3, 15).monthsInYear");
    }

    @Test
    public void testPrototypeToStringTag() {
        assertStringWithJavet("Temporal.PlainDate.prototype[Symbol.toStringTag]");
    }

    @Test
    public void testSince() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 3, 1).since('2024-01-01').toString()");
    }

    @Test
    public void testSubtract() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 1, 15).subtract({days: 10}).toString()");
    }

    @Test
    public void testToJSON() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 1, 15).toJSON()");
    }

    @Test
    public void testToPlainDateTime() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 1, 15).toPlainDateTime().toString()");
    }

    @Test
    public void testToPlainDateTimeWithTime() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 1, 15).toPlainDateTime('12:30').toString()");
    }

    @Test
    public void testToPlainMonthDay() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 1, 15).toPlainMonthDay().toString()");
    }

    @Test
    public void testToPlainYearMonth() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 1, 15).toPlainYearMonth().toString()");
    }

    @Test
    public void testToString() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 1, 15).toString()");
    }

    @Test
    public void testToStringCalendarOption() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 1, 15).toString({calendarName: 'always'})");
    }

    @Test
    public void testUntil() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 1, 1).until('2024-03-01').toString()");
    }

    @Test
    public void testValueOf() {
        assertErrorWithJavet("new Temporal.PlainDate(2024, 1, 15).valueOf()");
    }

    @Test
    public void testWeekOfYear() {
        assertIntegerWithJavet("new Temporal.PlainDate(2024, 3, 15).weekOfYear");
    }

    @Test
    public void testWith() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 1, 15).with({day: 20}).toString()");
    }

    @Test
    public void testWithCalendar() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 1, 15).withCalendar('iso8601').toString()");
    }

    // ========== Error path tests ==========

    @Test
    public void testWithNoPartial() {
        assertErrorWithJavet("new Temporal.PlainDate(2024, 1, 15).with({})");
    }

    @Test
    public void testYear() {
        assertIntegerWithJavet("new Temporal.PlainDate(2024, 3, 15).year");
    }

    @Test
    public void testYearOfWeek() {
        assertIntegerWithJavet("new Temporal.PlainDate(2024, 3, 15).yearOfWeek");
    }
}
