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

public class TemporalPlainTimeTest extends BaseJavetTest {

    // ========== Constructor tests ==========

    @Test
    public void testAdd() {
        assertStringWithJavet("new Temporal.PlainTime(12, 30).add({hours: 3}).toString()");
    }

    @Test
    public void testAddWraps() {
        assertStringWithJavet("new Temporal.PlainTime(23, 30).add({hours: 2}).toString()");
    }

    @Test
    public void testCompare() {
        assertIntegerWithJavet("Temporal.PlainTime.compare('12:30', '13:00')");
    }

    @Test
    public void testConstructorBasic() {
        assertStringWithJavet("new Temporal.PlainTime(12, 30, 45).toString()");
    }

    @Test
    public void testConstructorDefaults() {
        assertStringWithJavet("new Temporal.PlainTime().toString()");
    }

    @Test
    public void testConstructorFull() {
        assertStringWithJavet("new Temporal.PlainTime(12, 30, 45, 123, 456, 789).toString()");
    }

    // ========== Static method tests ==========

    @Test
    public void testConstructorInfinity() {
        assertErrorWithJavet("new Temporal.PlainTime(Infinity)");
    }

    @Test
    public void testConstructorInvalidHour() {
        assertErrorWithJavet("new Temporal.PlainTime(24, 0, 0)");
    }

    @Test
    public void testConstructorWithoutNew() {
        assertErrorWithJavet("Temporal.PlainTime(12, 30)");
    }

    // ========== Getter tests ==========

    @Test
    public void testEquals() {
        assertBooleanWithJavet("new Temporal.PlainTime(12, 30).equals('12:30')");
    }

    @Test
    public void testFrom() {
        assertStringWithJavet("Temporal.PlainTime.from('12:30:45').toString()");
    }

    @Test
    public void testFromObject() {
        assertStringWithJavet("Temporal.PlainTime.from({hour: 12, minute: 30}).toString()");
    }

    @Test
    public void testHour() {
        assertIntegerWithJavet("new Temporal.PlainTime(12, 30, 45).hour");
    }

    @Test
    public void testMicrosecond() {
        assertIntegerWithJavet("new Temporal.PlainTime(12, 30, 45, 123, 456).microsecond");
    }

    @Test
    public void testMillisecond() {
        assertIntegerWithJavet("new Temporal.PlainTime(12, 30, 45, 123).millisecond");
    }

    // ========== Prototype method tests ==========

    @Test
    public void testMinute() {
        assertIntegerWithJavet("new Temporal.PlainTime(12, 30, 45).minute");
    }

    @Test
    public void testNanosecond() {
        assertIntegerWithJavet("new Temporal.PlainTime(12, 30, 45, 123, 456, 789).nanosecond");
    }

    @Test
    public void testPrototypeToStringTag() {
        assertStringWithJavet("Temporal.PlainTime.prototype[Symbol.toStringTag]");
    }

    @Test
    public void testRound() {
        assertStringWithJavet("new Temporal.PlainTime(12, 34, 56).round('minute').toString()");
    }

    @Test
    public void testSecond() {
        assertIntegerWithJavet("new Temporal.PlainTime(12, 30, 45).second");
    }

    @Test
    public void testSince() {
        assertStringWithJavet("new Temporal.PlainTime(15, 30).since('12:00').toString()");
    }

    @Test
    public void testSubtract() {
        assertStringWithJavet("new Temporal.PlainTime(12, 30).subtract({hours: 3}).toString()");
    }

    @Test
    public void testToJSON() {
        assertStringWithJavet("new Temporal.PlainTime(12, 30, 45).toJSON()");
    }

    @Test
    public void testToString() {
        assertStringWithJavet("new Temporal.PlainTime(12, 30, 45).toString()");
    }

    @Test
    public void testUntil() {
        assertStringWithJavet("new Temporal.PlainTime(12, 0).until('15:30').toString()");
    }

    @Test
    public void testValueOf() {
        assertErrorWithJavet("new Temporal.PlainTime(12, 30).valueOf()");
    }

    // ========== Namespace tests ==========

    @Test
    public void testWith() {
        assertStringWithJavet("new Temporal.PlainTime(12, 30).with({hour: 15}).toString()");
    }
}
