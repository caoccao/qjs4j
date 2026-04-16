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

public class TemporalOptionResolverParityTest extends BaseJavetTest {
    @Test
    public void testDurationRoundWithOptions() {
        assertStringWithJavet(
                "Temporal.Duration.from('PT1H').round({ smallestUnit: 'minute', roundingMode: 'halfExpand', roundingIncrement: 1 }).toString()");
    }

    @Test
    public void testPlainTimeRoundWithOptions() {
        assertStringWithJavet(
                "new Temporal.PlainTime(12, 34, 56).round({ smallestUnit: 'minute', roundingMode: 'halfExpand' }).toString()");
    }

    @Test
    public void testPlainTimeToStringWithSmallestUnit() {
        assertStringWithJavet("new Temporal.PlainTime(12, 34, 56, 987, 654, 321).toString({ smallestUnit: 'microsecond', roundingMode: 'trunc' })");
    }

    @Test
    public void testZonedDateTimeWithOptions() {
        assertStringWithJavet(
                "Temporal.ZonedDateTime.from('2020-01-01T00:00:00+00:00[UTC]').with({ hour: 1 }, { disambiguation: 'compatible', offset: 'prefer', overflow: 'constrain' }).toString()");
    }
}
