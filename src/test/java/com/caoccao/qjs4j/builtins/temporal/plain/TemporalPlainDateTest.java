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

package com.caoccao.qjs4j.builtins.temporal.plain;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

public class TemporalPlainDateTest extends BaseJavetTest {
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
    public void testConstructorWithSubclass() {
        assertBooleanWithJavet(
                "(() => { class CustomPlainDate extends Temporal.PlainDate {} const value = new CustomPlainDate(2024, 1, 15); return value instanceof CustomPlainDate && value instanceof Temporal.PlainDate; })()");
    }

    @Test
    public void testDaysInMonth() {
        assertIntegerWithJavet("new Temporal.PlainDate(2024, 2, 15).daysInMonth");
    }

    @Test
    public void testDifferenceRoundingAcrossUnitBoundaries() {
        assertStringWithJavet(
                "Temporal.PlainDate.from('2022-01-01').since(Temporal.PlainDate.from('2023-12-25'), {largestUnit: 'year', smallestUnit: 'month', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2022-01-01').since(Temporal.PlainDate.from('2023-12-25'), {largestUnit: 'month', smallestUnit: 'day', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2022-01-01').since(Temporal.PlainDate.from('2023-12-25'), {largestUnit: 'week', smallestUnit: 'day', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2023-12-25').since(Temporal.PlainDate.from('2022-01-01'), {largestUnit: 'year', smallestUnit: 'month', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2023-12-25').since(Temporal.PlainDate.from('2022-01-01'), {largestUnit: 'month', smallestUnit: 'day', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2023-12-25').since(Temporal.PlainDate.from('2022-01-01'), {largestUnit: 'week', smallestUnit: 'day', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2023-01-01').since(Temporal.PlainDate.from('2023-01-31'), {largestUnit: 'year', smallestUnit: 'month', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2023-01-01').since(Temporal.PlainDate.from('2023-01-31'), {largestUnit: 'month', smallestUnit: 'day', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2023-01-01').since(Temporal.PlainDate.from('2023-01-31'), {largestUnit: 'week', smallestUnit: 'day', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2023-01-31').since(Temporal.PlainDate.from('2023-01-01'), {largestUnit: 'year', smallestUnit: 'month', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2023-01-31').since(Temporal.PlainDate.from('2023-01-01'), {largestUnit: 'month', smallestUnit: 'day', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2023-01-31').since(Temporal.PlainDate.from('2023-01-01'), {largestUnit: 'week', smallestUnit: 'day', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2022-01-01').until(Temporal.PlainDate.from('2023-12-25'), {largestUnit: 'year', smallestUnit: 'month', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2022-01-01').until(Temporal.PlainDate.from('2023-12-25'), {largestUnit: 'month', smallestUnit: 'day', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2022-01-01').until(Temporal.PlainDate.from('2023-12-25'), {largestUnit: 'week', smallestUnit: 'day', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2023-12-25').until(Temporal.PlainDate.from('2022-01-01'), {largestUnit: 'year', smallestUnit: 'month', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2023-12-25').until(Temporal.PlainDate.from('2022-01-01'), {largestUnit: 'month', smallestUnit: 'day', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2023-12-25').until(Temporal.PlainDate.from('2022-01-01'), {largestUnit: 'week', smallestUnit: 'day', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2023-01-01').until(Temporal.PlainDate.from('2023-01-31'), {largestUnit: 'year', smallestUnit: 'month', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2023-01-01').until(Temporal.PlainDate.from('2023-01-31'), {largestUnit: 'month', smallestUnit: 'day', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2023-01-01').until(Temporal.PlainDate.from('2023-01-31'), {largestUnit: 'week', smallestUnit: 'day', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2023-01-31').until(Temporal.PlainDate.from('2023-01-01'), {largestUnit: 'year', smallestUnit: 'month', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2023-01-31').until(Temporal.PlainDate.from('2023-01-01'), {largestUnit: 'month', smallestUnit: 'day', roundingMode: 'expand', roundingIncrement: 2}).toString()",
                "Temporal.PlainDate.from('2023-01-31').until(Temporal.PlainDate.from('2023-01-01'), {largestUnit: 'week', smallestUnit: 'day', roundingMode: 'expand', roundingIncrement: 2}).toString()");
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
    void testIntegerProperties() {
        assertIntegerWithJavet("new Temporal.PlainDate(2024, 3, 15).day",
                "new Temporal.PlainDate(2024, 3, 15).dayOfWeek", "new Temporal.PlainDate(2024, 3, 15).dayOfYear",
                "new Temporal.PlainDate(2024, 3, 15).daysInWeek", "new Temporal.PlainDate(2024, 3, 15).daysInYear",
                "new Temporal.PlainDate(2024, 3, 15).month", "new Temporal.PlainDate(2024, 3, 15).monthsInYear",
                "new Temporal.PlainDate(2024, 3, 15).weekOfYear", "new Temporal.PlainDate(2024, 3, 15).year",
                "new Temporal.PlainDate(2024, 3, 15).yearOfWeek");
    }

    @Test
    public void testMonthCode() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 3, 15).monthCode");
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
    void testStringConversions() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 1, 15).toString()",
                "new Temporal.PlainDate(2024, 1, 15).toJSON()",
                "new Temporal.PlainDate(2024, 1, 15).toPlainDateTime().toString()",
                "new Temporal.PlainDate(2024, 1, 15).toPlainDateTime('12:30').toString()",
                "new Temporal.PlainDate(2024, 1, 15).toPlainMonthDay().toString()",
                "new Temporal.PlainDate(2024, 1, 15).toPlainYearMonth().toString()",
                "new Temporal.PlainDate(2024, 1, 15).toString()",
                "new Temporal.PlainDate(2024, 1, 15).toString({calendarName: 'always'})");
    }

    @Test
    public void testSubtract() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 1, 15).subtract({days: 10}).toString()");
    }

    @Test
    public void testToLocaleString() {
        assertBooleanWithJavet(
                "(() => { const value = new Temporal.PlainDate(2024, 1, 15); const locales = 'en-US'; const options = { timeZone: 'UTC', year: 'numeric', month: '2-digit', day: '2-digit' }; return value.toLocaleString(locales, options) === new Intl.DateTimeFormat(locales, options).format(value); })()");
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
    public void testWith() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 1, 15).with({day: 20}).toString()");
    }

    @Test
    public void testWithCalendar() {
        assertStringWithJavet("new Temporal.PlainDate(2024, 1, 15).withCalendar('iso8601').toString()");
    }

    @Test
    public void testWithNoPartial() {
        assertErrorWithJavet("new Temporal.PlainDate(2024, 1, 15).with({})");
    }
}
