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

public class TemporalDurationTest extends BaseTemporalJavetTest {

    // ========== Constructor tests ==========

    @Test
    public void testAbs() {
        assertStringWithJavet("new Temporal.Duration(-1, -2, -3).abs().toString()");
    }

    @Test
    public void testAdd() {
        assertStringWithJavet("new Temporal.Duration(0, 0, 0, 0, 1).add({hours: 2}).toString()");
    }

    @Test
    public void testBlank() {
        assertBooleanWithJavet("new Temporal.Duration().blank");
    }

    @Test
    public void testBlankNotBlank() {
        assertBooleanWithJavet("new Temporal.Duration(1).blank");
    }

    @Test
    public void testCompare() {
        assertIntegerWithJavet("Temporal.Duration.compare('PT1H', 'PT30M')");
    }

    @Test
    public void testCompareEqual() {
        assertIntegerWithJavet("Temporal.Duration.compare('PT1H', 'PT60M')");
    }

    @Test
    public void testConstructorAllDefaults() {
        assertStringWithJavet("new Temporal.Duration().toString()");
    }

    @Test
    public void testConstructorBasic() {
        assertStringWithJavet("new Temporal.Duration(1, 2, 3, 4, 5, 6, 7).toString()");
    }

    // ========== Static method tests ==========

    @Test
    public void testConstructorFractional() {
        assertErrorWithJavet("new Temporal.Duration(1.5)");
    }

    @Test
    public void testConstructorFull() {
        assertStringWithJavet("new Temporal.Duration(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).toString()");
    }

    @Test
    public void testConstructorInfinity() {
        assertErrorWithJavet("new Temporal.Duration(Infinity)");
    }

    @Test
    public void testConstructorMixedSigns() {
        assertErrorWithJavet("new Temporal.Duration(1, -2)");
    }

    @Test
    public void testConstructorNegative() {
        assertStringWithJavet("new Temporal.Duration(-1, -2).toString()");
    }

    // ========== Getter tests ==========

    @Test
    public void testConstructorWithoutNew() {
        assertErrorWithJavet("Temporal.Duration(1)");
    }

    @Test
    public void testDays() {
        assertIntegerWithJavet("new Temporal.Duration(0, 0, 0, 4).days");
    }

    @Test
    public void testFrom() {
        assertStringWithJavet("Temporal.Duration.from('P1Y2M3DT4H5M6S').toString()");
    }

    @Test
    public void testFromDuration() {
        assertStringWithJavet("Temporal.Duration.from(new Temporal.Duration(1, 2, 3)).toString()");
    }

    @Test
    public void testFromObject() {
        assertStringWithJavet("Temporal.Duration.from({years: 1, months: 2, days: 3}).toString()");
    }

    @Test
    public void testHours() {
        assertIntegerWithJavet("new Temporal.Duration(0, 0, 0, 0, 5).hours");
    }

    @Test
    public void testMicroseconds() {
        assertIntegerWithJavet("new Temporal.Duration(0, 0, 0, 0, 0, 0, 0, 0, 9).microseconds");
    }

    @Test
    public void testMilliseconds() {
        assertIntegerWithJavet("new Temporal.Duration(0, 0, 0, 0, 0, 0, 0, 8).milliseconds");
    }

    @Test
    public void testMinutes() {
        assertIntegerWithJavet("new Temporal.Duration(0, 0, 0, 0, 0, 6).minutes");
    }

    @Test
    public void testMonths() {
        assertIntegerWithJavet("new Temporal.Duration(1, 2, 3).months");
    }

    @Test
    public void testNanoseconds() {
        assertIntegerWithJavet("new Temporal.Duration(0, 0, 0, 0, 0, 0, 0, 0, 0, 10).nanoseconds");
    }

    @Test
    public void testNegated() {
        assertStringWithJavet("new Temporal.Duration(1, 2, 3).negated().toString()");
    }

    @Test
    public void testPrototypeToStringTag() {
        assertStringWithJavet("Object.prototype.toString.call(new Temporal.Duration(1))");
    }

    @Test
    public void testRound() {
        assertStringWithJavet("new Temporal.Duration(0, 0, 0, 0, 1, 30).round({largestUnit: 'hour'}).toString()");
    }

    @Test
    public void testRoundToMissing() {
        assertErrorWithJavet("new Temporal.Duration(0, 0, 0, 0, 1).round()");
    }

    // ========== Method tests ==========

    @Test
    public void testSeconds() {
        assertIntegerWithJavet("new Temporal.Duration(0, 0, 0, 0, 0, 0, 7).seconds");
    }

    @Test
    public void testSign() {
        assertIntegerWithJavet("new Temporal.Duration(1).sign");
    }

    @Test
    public void testSignNegative() {
        assertIntegerWithJavet("new Temporal.Duration(-1).sign");
    }

    @Test
    public void testSignZero() {
        assertIntegerWithJavet("new Temporal.Duration().sign");
    }

    @Test
    public void testSubtract() {
        assertStringWithJavet("new Temporal.Duration(0, 0, 0, 0, 3).subtract({hours: 1}).toString()");
    }

    @Test
    public void testToJSON() {
        assertStringWithJavet("new Temporal.Duration(1, 2, 3).toJSON()");
    }

    @Test
    public void testToString() {
        assertStringWithJavet("new Temporal.Duration(1, 2, 3, 4, 5, 6, 7).toString()");
    }

    @Test
    public void testTotal() {
        assertIntegerWithJavet("new Temporal.Duration(0, 0, 0, 0, 1, 30).total('minutes')");
    }

    @Test
    public void testTotalMissing() {
        assertErrorWithJavet("new Temporal.Duration(0, 0, 0, 0, 1).total()");
    }

    @Test
    public void testValueOf() {
        assertErrorWithJavet("new Temporal.Duration(1).valueOf()");
    }

    @Test
    public void testWeeks() {
        assertIntegerWithJavet("new Temporal.Duration(1, 2, 3).weeks");
    }

    @Test
    public void testWith() {
        assertStringWithJavet("new Temporal.Duration(1, 2, 3).with({years: 5}).toString()");
    }

    @Test
    public void testYears() {
        assertIntegerWithJavet("new Temporal.Duration(1, 2, 3).years");
    }
}
