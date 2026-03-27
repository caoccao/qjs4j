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

public class TemporalPlainMonthDayTest extends BaseJavetTest {

    // ========== Constructor tests ==========

    @Test
    public void testCalendarId() {
        assertStringWithJavet("new Temporal.PlainMonthDay(3, 15).calendarId");
    }

    @Test
    public void testConstructorBasic() {
        assertStringWithJavet("new Temporal.PlainMonthDay(3, 15).toString()");
    }

    @Test
    public void testConstructorInvalidDay() {
        assertErrorWithJavet("new Temporal.PlainMonthDay(2, 30)");
    }

    @Test
    public void testConstructorInvalidMonth() {
        assertErrorWithJavet("new Temporal.PlainMonthDay(13, 1)");
    }

    @Test
    public void testConstructorWithCalendar() {
        assertStringWithJavet("new Temporal.PlainMonthDay(3, 15, 'iso8601').toString()");
    }

    @Test
    public void testConstructorWithoutNew() {
        assertErrorWithJavet("Temporal.PlainMonthDay(3, 15)");
    }

    @Test
    public void testDay() {
        assertIntegerWithJavet("new Temporal.PlainMonthDay(3, 15).day");
    }

    @Test
    public void testEquals() {
        assertBooleanWithJavet("new Temporal.PlainMonthDay(3, 15).equals('--03-15')");
    }

    @Test
    public void testFrom() {
        assertStringWithJavet("Temporal.PlainMonthDay.from('--03-15').toString()");
    }

    @Test
    public void testFromObject() {
        assertStringWithJavet("Temporal.PlainMonthDay.from({monthCode: 'M03', day: 15}).toString()");
    }

    @Test
    public void testMonthCode() {
        assertStringWithJavet("new Temporal.PlainMonthDay(3, 15).monthCode");
    }

    @Test
    public void testToJSON() {
        assertStringWithJavet("new Temporal.PlainMonthDay(3, 15).toJSON()");
    }

    @Test
    public void testToPlainDate() {
        assertStringWithJavet("new Temporal.PlainMonthDay(3, 15).toPlainDate({year: 2024}).toString()");
    }

    @Test
    public void testToPlainDateNoYear() {
        assertErrorWithJavet("new Temporal.PlainMonthDay(3, 15).toPlainDate()");
    }

    @Test
    public void testToString() {
        assertStringWithJavet("new Temporal.PlainMonthDay(3, 15).toString()");
    }

    @Test
    public void testValueOf() {
        assertErrorWithJavet("new Temporal.PlainMonthDay(3, 15).valueOf()");
    }

    @Test
    public void testWith() {
        assertStringWithJavet("new Temporal.PlainMonthDay(3, 15).with({day: 20}).toString()");
    }
}
