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

public class TemporalPlainDateTimeTest extends BaseJavetTest {

    // ========== Constructor tests ==========

    @Test
    public void testAdd() {
        assertStringWithJavet("new Temporal.PlainDateTime(2024, 1, 15, 12, 30).add({hours: 5}).toString()");
    }

    @Test
    public void testCalendarId() {
        assertStringWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30).calendarId");
    }

    @Test
    public void testCompare() {
        assertIntegerWithJavet("Temporal.PlainDateTime.compare('2024-01-15T12:00', '2024-01-15T13:00')");
    }

    @Test
    public void testConstructorBasic() {
        assertStringWithJavet("new Temporal.PlainDateTime(2024, 1, 15, 12, 30, 45).toString()");
    }

    @Test
    public void testConstructorDateOnly() {
        assertStringWithJavet("new Temporal.PlainDateTime(2024, 1, 15).toString()");
    }

    @Test
    public void testConstructorFull() {
        assertStringWithJavet("new Temporal.PlainDateTime(2024, 1, 15, 12, 30, 45, 123, 456, 789).toString()");
    }

    @Test
    public void testConstructorInvalidDate() {
        assertErrorWithJavet("new Temporal.PlainDateTime(2024, 13, 1)");
    }

    @Test
    public void testConstructorInvalidTime() {
        assertErrorWithJavet("new Temporal.PlainDateTime(2024, 1, 1, 25, 0)");
    }

    // ========== Static method tests ==========

    @Test
    public void testConstructorLength() {
        assertIntegerWithJavet("Temporal.PlainDateTime.length");
    }

    @Test
    public void testConstructorWithCalendar() {
        assertStringWithJavet("new Temporal.PlainDateTime(2024, 1, 15, 12, 30, 45, 0, 0, 0, 'iso8601').toString()");
    }

    @Test
    public void testConstructorWithSubclass() {
        assertBooleanWithJavet("(() => { class CustomPlainDateTime extends Temporal.PlainDateTime {} const value = new CustomPlainDateTime(2024, 1, 15, 12, 30); return value instanceof CustomPlainDateTime && value instanceof Temporal.PlainDateTime; })()");
    }

    @Test
    public void testConstructorWithoutNew() {
        assertErrorWithJavet("Temporal.PlainDateTime(2024, 1, 15)");
    }

    // ========== Getter tests ==========

    @Test
    public void testDay() {
        assertIntegerWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30).day");
    }

    @Test
    public void testDayOfWeek() {
        assertIntegerWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30).dayOfWeek");
    }

    @Test
    public void testDayOfYear() {
        assertIntegerWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30).dayOfYear");
    }

    @Test
    public void testDaysInMonth() {
        assertIntegerWithJavet("new Temporal.PlainDateTime(2024, 2, 15, 12, 30).daysInMonth");
    }

    @Test
    public void testDaysInWeek() {
        assertIntegerWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30).daysInWeek");
    }

    @Test
    public void testDaysInYear() {
        assertIntegerWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30).daysInYear");
    }

    @Test
    public void testEquals() {
        assertBooleanWithJavet("new Temporal.PlainDateTime(2024, 1, 15, 12, 30).equals('2024-01-15T12:30')");
    }

    @Test
    public void testEra() {
        assertUndefinedWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30).era");
    }

    @Test
    public void testEraYear() {
        assertUndefinedWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30).eraYear");
    }

    @Test
    public void testFrom() {
        assertStringWithJavet("Temporal.PlainDateTime.from('2024-01-15T12:30:45').toString()");
    }

    @Test
    public void testFromObject() {
        assertStringWithJavet("Temporal.PlainDateTime.from({year: 2024, month: 1, day: 15, hour: 12}).toString()");
    }

    @Test
    public void testHour() {
        assertIntegerWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30).hour");
    }

    @Test
    public void testInLeapYear() {
        assertBooleanWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30).inLeapYear");
    }

    @Test
    public void testMicrosecond() {
        assertIntegerWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30, 45, 123, 456).microsecond");
    }

    @Test
    public void testMillisecond() {
        assertIntegerWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30, 45, 123).millisecond");
    }

    @Test
    public void testMinute() {
        assertIntegerWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30).minute");
    }

    @Test
    public void testMonth() {
        assertIntegerWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30).month");
    }

    @Test
    public void testMonthCode() {
        assertStringWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30).monthCode");
    }

    @Test
    public void testMonthsInYear() {
        assertIntegerWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30).monthsInYear");
    }

    @Test
    public void testNanosecond() {
        assertIntegerWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30, 45, 123, 456, 789).nanosecond");
    }

    @Test
    public void testPrototypeToStringTag() {
        assertStringWithJavet("Object.prototype.toString.call(new Temporal.PlainDateTime(2024, 1, 15))");
    }

    @Test
    public void testRound() {
        assertStringWithJavet("new Temporal.PlainDateTime(2024, 1, 15, 12, 34, 56).round('minute').toString()");
    }

    // ========== Method tests ==========

    @Test
    public void testSecond() {
        assertIntegerWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30, 45).second");
    }

    @Test
    public void testSince() {
        assertStringWithJavet("new Temporal.PlainDateTime(2024, 1, 2, 12, 0).since('2024-01-01').toString()");
    }

    @Test
    public void testSubtract() {
        assertStringWithJavet("new Temporal.PlainDateTime(2024, 1, 15, 12, 30).subtract({days: 1}).toString()");
    }

    @Test
    public void testToJSON() {
        assertStringWithJavet("new Temporal.PlainDateTime(2024, 1, 15, 12, 30, 45).toJSON()");
    }

    @Test
    public void testToLocaleString() {
        assertBooleanWithJavet("(() => { const value = new Temporal.PlainDateTime(2024, 1, 15, 12, 30, 45); const locales = 'en-US'; const options = { timeZone: 'UTC', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }; return value.toLocaleString(locales, options) === new Intl.DateTimeFormat(locales, options).format(value); })()");
    }

    @Test
    public void testToPlainDate() {
        assertStringWithJavet("new Temporal.PlainDateTime(2024, 1, 15, 12, 30).toPlainDate().toString()");
    }

    @Test
    public void testToPlainTime() {
        assertStringWithJavet("new Temporal.PlainDateTime(2024, 1, 15, 12, 30).toPlainTime().toString()");
    }

    @Test
    public void testToString() {
        assertStringWithJavet("new Temporal.PlainDateTime(2024, 1, 15, 12, 30, 45).toString()");
    }

    @Test
    public void testToStringFractionalDigits() {
        assertStringWithJavet("new Temporal.PlainDateTime(2024, 1, 15, 12, 30, 45, 123).toString({fractionalSecondDigits: 6})");
    }

    @Test
    public void testUntil() {
        assertStringWithJavet("new Temporal.PlainDateTime(2024, 1, 1, 0, 0).until('2024-01-02T12:00').toString()");
    }

    @Test
    public void testValueOf() {
        assertErrorWithJavet("new Temporal.PlainDateTime(2024, 1, 15, 12, 30).valueOf()");
    }

    @Test
    public void testWeekOfYear() {
        assertIntegerWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30).weekOfYear");
    }

    @Test
    public void testWith() {
        assertStringWithJavet("new Temporal.PlainDateTime(2024, 1, 15, 12, 30).with({hour: 15}).toString()");
    }

    @Test
    public void testWithCalendar() {
        assertStringWithJavet("new Temporal.PlainDateTime(2024, 1, 15, 12, 30).withCalendar('iso8601').toString()");
    }

    @Test
    public void testWithPlainTime() {
        assertStringWithJavet("new Temporal.PlainDateTime(2024, 1, 15, 12, 30).withPlainTime('08:00').toString()");
    }

    @Test
    public void testYear() {
        assertIntegerWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30).year");
    }

    @Test
    public void testYearOfWeek() {
        assertIntegerWithJavet("new Temporal.PlainDateTime(2024, 3, 15, 12, 30).yearOfWeek");
    }
}
