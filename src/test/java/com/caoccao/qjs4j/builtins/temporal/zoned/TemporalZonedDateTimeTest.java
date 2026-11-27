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

package com.caoccao.qjs4j.builtins.temporal.zoned;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

public class TemporalZonedDateTimeTest extends BaseJavetTest {

    @Test
    public void testAdd() {
        assertStringWithJavet("new Temporal.ZonedDateTime(0n, 'UTC').add({hours: 1}).toString()");
    }

    @Test
    public void testCompare() {
        assertIntegerWithJavet(
                "Temporal.ZonedDateTime.compare('2024-01-15T12:00:00+00:00[UTC]', '2024-01-15T13:00:00+00:00[UTC]')");
    }

    @Test
    public void testConstructorMissingTimeZone() {
        assertErrorWithJavet("new Temporal.ZonedDateTime(0n)");
    }

    @Test
    public void testConstructorNonBigInt() {
        assertErrorWithJavet("new Temporal.ZonedDateTime(0, 'UTC')");
    }

    @Test
    public void testConstructorWithCalendar() {
        assertStringWithJavet("new Temporal.ZonedDateTime(0n, 'UTC', 'iso8601').toString()");
    }

    @Test
    public void testConstructorWithoutNew() {
        assertErrorWithJavet("Temporal.ZonedDateTime(0n, 'UTC')");
    }

    @Test
    public void testConstructorWithSubclass() {
        assertBooleanWithJavet(
                "(() => { class CustomZonedDateTime extends Temporal.ZonedDateTime {} const value = new CustomZonedDateTime(0n, 'UTC'); return value instanceof CustomZonedDateTime && value instanceof Temporal.ZonedDateTime; })()");
    }

    @Test
    public void testEpochMilliseconds() {
        assertIntegerWithJavet("new Temporal.ZonedDateTime(1000000000n, 'UTC').epochMilliseconds");
    }

    @Test
    public void testEpochNanoseconds() {
        assertLongWithJavet("new Temporal.ZonedDateTime(1000000000n, 'UTC').epochNanoseconds");
    }

    @Test
    public void testEquals() {
        assertBooleanWithJavet("new Temporal.ZonedDateTime(0n, 'UTC').equals(new Temporal.ZonedDateTime(0n, 'UTC'))");
    }

    @Test
    public void testEra() {
        assertUndefinedWithJavet("new Temporal.ZonedDateTime(0n, 'UTC').era");
    }

    @Test
    public void testEraYear() {
        assertUndefinedWithJavet("new Temporal.ZonedDateTime(0n, 'UTC').eraYear");
    }

    @Test
    public void testFrom() {
        assertStringWithJavet("Temporal.ZonedDateTime.from('2024-01-15T12:00:00+00:00[UTC]').toString()");
    }

    @Test
    public void testGetTimeZoneTransition() {
        assertStringWithJavet(
                "let z = new Temporal.ZonedDateTime(0n, 'America/New_York'); let t = z.getTimeZoneTransition('next'); t !== null ? t.toString() : 'null'");
    }

    @Test
    public void testInLeapYear() {
        assertBooleanWithJavet("new Temporal.ZonedDateTime(0n, 'UTC').inLeapYear");
    }

    @Test
    void testIntegerProperties() {
        assertIntegerWithJavet("new Temporal.ZonedDateTime(0n, 'UTC').day",
                "new Temporal.ZonedDateTime(0n, 'UTC').dayOfWeek", "new Temporal.ZonedDateTime(0n, 'UTC').dayOfYear",
                "new Temporal.ZonedDateTime(0n, 'UTC').daysInMonth", "new Temporal.ZonedDateTime(0n, 'UTC').daysInWeek",
                "new Temporal.ZonedDateTime(0n, 'UTC').daysInYear", "new Temporal.ZonedDateTime(0n, 'UTC').hour",
                "new Temporal.ZonedDateTime(0n, 'UTC').hoursInDay", "new Temporal.ZonedDateTime(0n, 'UTC').microsecond",
                "new Temporal.ZonedDateTime(0n, 'UTC').millisecond", "new Temporal.ZonedDateTime(0n, 'UTC').minute",
                "new Temporal.ZonedDateTime(0n, 'UTC').month", "new Temporal.ZonedDateTime(0n, 'UTC').monthsInYear",
                "new Temporal.ZonedDateTime(0n, 'UTC').nanosecond",
                "new Temporal.ZonedDateTime(0n, 'UTC').offsetNanoseconds",
                "new Temporal.ZonedDateTime(0n, 'UTC').second", "new Temporal.ZonedDateTime(0n, 'UTC').weekOfYear",
                "new Temporal.ZonedDateTime(0n, 'UTC').year", "new Temporal.ZonedDateTime(0n, 'UTC').yearOfWeek");
    }

    @Test
    public void testRound() {
        assertStringWithJavet("new Temporal.ZonedDateTime(1234567890123456789n, 'UTC').round('second').toString()");
    }

    @Test
    public void testSince() {
        assertStringWithJavet(
                "new Temporal.ZonedDateTime(3600000000000n, 'UTC').since('1970-01-01T00:00:00+00:00[UTC]').toString()");
    }

    @Test
    public void testStartOfDay() {
        assertStringWithJavet("new Temporal.ZonedDateTime(43200000000000n, 'UTC').startOfDay().toString()");
    }

    @Test
    void testStringConversions() {
        assertStringWithJavet("new Temporal.ZonedDateTime(0n, 'UTC').toString()",
                "new Temporal.ZonedDateTime(0n, 'UTC').toInstant().toString()",
                "new Temporal.ZonedDateTime(0n, 'UTC').toJSON()",
                "new Temporal.ZonedDateTime(0n, 'UTC').toPlainDate().toString()",
                "new Temporal.ZonedDateTime(0n, 'UTC').toPlainDateTime().toString()",
                "new Temporal.ZonedDateTime(0n, 'UTC').toPlainTime().toString()",
                "new Temporal.ZonedDateTime(0n, 'UTC').toString()");
    }

    @Test
    void testStringProperties() {
        assertStringWithJavet("new Temporal.ZonedDateTime(0n, 'UTC').calendarId",
                "new Temporal.ZonedDateTime(0n, 'UTC').monthCode", "new Temporal.ZonedDateTime(0n, 'UTC').offset",
                "new Temporal.ZonedDateTime(0n, 'UTC').timeZoneId");
    }

    @Test
    public void testSubtract() {
        assertStringWithJavet("new Temporal.ZonedDateTime(3600000000000n, 'UTC').subtract({hours: 1}).toString()");
    }

    @Test
    public void testToLocaleString() {
        assertBooleanWithJavet(
                "(() => { const value = new Temporal.ZonedDateTime(0n, 'UTC'); const result = value.toLocaleString('en-US', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }); return typeof result === 'string' && result.length > 0; })()");
    }

    @Test
    public void testUntil() {
        assertStringWithJavet(
                "new Temporal.ZonedDateTime(0n, 'UTC').until('1970-01-01T01:00:00+00:00[UTC]').toString()");
    }

    @Test
    public void testValueOf() {
        assertErrorWithJavet("new Temporal.ZonedDateTime(0n, 'UTC').valueOf()");
    }

    @Test
    public void testWith() {
        assertStringWithJavet("new Temporal.ZonedDateTime(0n, 'UTC').with({hour: 12}).toString()");
    }

    @Test
    public void testWithCalendar() {
        assertStringWithJavet("new Temporal.ZonedDateTime(0n, 'UTC').withCalendar('iso8601').toString()");
    }

    @Test
    public void testWithPlainTime() {
        assertStringWithJavet("new Temporal.ZonedDateTime(0n, 'UTC').withPlainTime('12:00').toString()");
    }

    @Test
    public void testWithTimeZone() {
        assertStringWithJavet("new Temporal.ZonedDateTime(0n, 'UTC').withTimeZone('America/New_York').toString()");
    }
}
