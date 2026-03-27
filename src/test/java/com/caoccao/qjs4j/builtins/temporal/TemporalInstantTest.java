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

public class TemporalInstantTest extends BaseJavetTest {

    // ========== Constructor tests ==========

    @Test
    public void testAdd() {
        assertStringWithJavet("new Temporal.Instant(0n).add({hours: 1}).toString()");
    }

    @Test
    public void testCompare() {
        assertIntegerWithJavet("Temporal.Instant.compare('1970-01-01T00:00:00Z', '1970-01-01T00:00:01Z')");
    }

    @Test
    public void testConstructorBasic() {
        assertStringWithJavet("new Temporal.Instant(0n).toString()");
    }

    @Test
    public void testConstructorNegative() {
        assertStringWithJavet("new Temporal.Instant(-1000000000000000000n).toString()");
    }

    @Test
    public void testConstructorNonBigInt() {
        assertErrorWithJavet("new Temporal.Instant(0)");
    }

    @Test
    public void testConstructorOutOfRange() {
        assertErrorWithJavet("new Temporal.Instant(8640000000000000000001n)");
    }

    @Test
    public void testConstructorPositive() {
        assertStringWithJavet("new Temporal.Instant(1000000000000000000n).toString()");
    }

    @Test
    public void testConstructorWithSubclass() {
        assertBooleanWithJavet("(() => { class CustomInstant extends Temporal.Instant {} const value = new CustomInstant(0n); return value instanceof CustomInstant && value instanceof Temporal.Instant; })()");
    }

    @Test
    public void testConstructorWithoutNew() {
        assertErrorWithJavet("Temporal.Instant(0n)");
    }

    @Test
    public void testEpochMilliseconds() {
        assertIntegerWithJavet("new Temporal.Instant(1000000000n).epochMilliseconds");
    }

    @Test
    public void testEpochNanoseconds() {
        assertLongWithJavet("new Temporal.Instant(1000000000n).epochNanoseconds");
    }

    @Test
    public void testEquals() {
        assertBooleanWithJavet("new Temporal.Instant(0n).equals('1970-01-01T00:00:00Z')");
    }

    @Test
    public void testFrom() {
        assertStringWithJavet("Temporal.Instant.from('1970-01-01T00:00:00Z').toString()");
    }

    @Test
    public void testFromEpochMilliseconds() {
        assertStringWithJavet("Temporal.Instant.fromEpochMilliseconds(0).toString()");
    }

    @Test
    public void testFromEpochNanoseconds() {
        assertStringWithJavet("Temporal.Instant.fromEpochNanoseconds(0n).toString()");
    }

    @Test
    public void testRound() {
        assertStringWithJavet("new Temporal.Instant(1234567890123456789n).round('second').toString()");
    }

    @Test
    public void testSince() {
        assertStringWithJavet("new Temporal.Instant(3600000000000n).since('1970-01-01T00:00:00Z').toString()");
    }

    @Test
    public void testSubtract() {
        assertStringWithJavet("new Temporal.Instant(3600000000000n).subtract({hours: 1}).toString()");
    }

    @Test
    public void testToJSON() {
        assertStringWithJavet("new Temporal.Instant(0n).toJSON()");
    }

    @Test
    public void testToLocaleString() {
        assertBooleanWithJavet("(() => { const value = new Temporal.Instant(0n); const locales = 'en-US'; const options = { timeZone: 'UTC', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }; return value.toLocaleString(locales, options) === new Intl.DateTimeFormat(locales, options).format(value); })()");
    }

    @Test
    public void testToString() {
        assertStringWithJavet("new Temporal.Instant(0n).toString()");
    }

    @Test
    public void testToStringWithTimeZone() {
        assertStringWithJavet("new Temporal.Instant(0n).toString({timeZone: 'UTC'})");
    }

    @Test
    public void testToZonedDateTimeISO() {
        assertStringWithJavet("new Temporal.Instant(0n).toZonedDateTimeISO('UTC').toString()");
    }

    @Test
    public void testUntil() {
        assertStringWithJavet("new Temporal.Instant(0n).until('1970-01-01T01:00:00Z').toString()");
    }

    @Test
    public void testValueOf() {
        assertErrorWithJavet("new Temporal.Instant(0n).valueOf()");
    }
}
