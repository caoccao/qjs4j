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

package com.caoccao.qjs4j.performance;

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("performance")
public class TemporalHotPathPerformanceTest extends BaseTest {
    @Test
    public void testDurationRoundHotPath() {
        JSValue value = context.eval("""
                (() => {
                  const duration = Temporal.Duration.from({ seconds: 12345, milliseconds: 678 });
                  let total = 0;
                  for (let i = 0; i < 5000; i++) {
                    const rounded = duration.round({
                      smallestUnit: 'second',
                      roundingIncrement: 1,
                      roundingMode: 'trunc'
                    });
                    total += rounded.seconds;
                  }
                  return total;
                })()
                """);
        assertThat(value).isInstanceOfSatisfying(JSNumber.class, jsNumber ->
                assertThat(jsNumber.value()).isEqualTo(61725000D));
    }

    @Test
    public void testPlainMonthDayFromHotPath() {
        JSValue value = context.eval("""
                (() => {
                  let count = 0;
                  for (let i = 0; i < 1000; i++) {
                    const value = Temporal.PlainMonthDay.from({ calendar: 'iso8601', monthCode: 'M02', day: 28, year: 2023 });
                    count += value.day;
                  }
                  return count;
                })()
                """);
        assertThat(value).isInstanceOfSatisfying(JSNumber.class, jsNumber ->
                assertThat(jsNumber.value()).isEqualTo(28000D));
    }

    @Test
    public void testZonedDateTimeEqualsHotPath() {
        JSValue value = context.eval("""
                (() => {
                  const first = Temporal.ZonedDateTime.from('2020-01-01T00:00:00+00:00[UTC]');
                  const second = Temporal.ZonedDateTime.from('2020-01-01T00:00:00+00:00[Etc/UTC]');
                  let count = 0;
                  for (let i = 0; i < 10000; i++) {
                    if (first.equals(second)) {
                      count++;
                    }
                  }
                  return count;
                })()
                """);
        assertThat(value).isInstanceOfSatisfying(JSNumber.class, jsNumber ->
                assertThat(jsNumber.value()).isEqualTo(10000D));
    }
}
