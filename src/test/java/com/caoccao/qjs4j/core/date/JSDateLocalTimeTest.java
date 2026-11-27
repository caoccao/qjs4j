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

package com.caoccao.qjs4j.core.date;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneRules;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Local wall-clock times convert to UTC as V8 converts them.
 * <p>
 * The offset of a local time was looked up as though the wall-clock value were a UTC instant, so a time in the
 * spring-forward gap took the offset after the transition and a time in the autumn overlap resolved to its second
 * occurrence; and offsets were truncated to whole minutes, so dates in a zone's local-mean-time era were off by
 * seconds. The script cases run in both engines in the machine's time zone; the unit cases pin the rule in a zone with
 * transitions whatever the machine's zone is.
 */
public class JSDateLocalTimeTest extends BaseJavetTest {
    @Test
    public void testConstructorArgumentsThatThrowOrOverflow() {
        // A thrown primitive used to be cast to an object inside the engine; the first throwing argument wins.
        assertStringWithJavet("""
                const attempt = action => { try { return String(action()); } catch (e) { return 'caught ' + e; } };
                [
                  attempt(() => new Date({ [Symbol.toPrimitive]() { throw 42; } })),
                  attempt(() => new Date({ valueOf() { throw new Error('one'); } }).getTime()),
                  attempt(() => new Date(2020, { valueOf() { throw 'month'; } }, { valueOf() { throw 'day'; } })),
                  attempt(() => new Date(300000, 0).getTime()),
                  attempt(() => new Date(2020, 0).setFullYear(300000)),
                  attempt(() => Date.UTC(300000, 0)),
                ].join('\\n')""");
    }

    @Test
    public void testLocalTimesMatchV8() {
        assertStringWithJavet("""
                var out = [];
                for (var y of [1800, 1893, 1916, 1945, 1970, 2024, 2100, 100]) {
                  for (var m of [0, 2, 3, 9, 10]) {
                    for (var d of [1, 26, 27, 28, 31]) {
                      for (var h of [0, 1, 2, 3, 12]) {
                        var dt = new Date(y, m, d, h, 30, 15, 500);
                        out.push(dt.getTime(), dt.getHours(), dt.getMinutes(), dt.getSeconds(), dt.getTimezoneOffset());
                      }
                    }
                  }
                }
                out.join()""", """
                var s = new Date(2024, 2, 31, 1, 30);
                s.setHours(2);
                var t = new Date(2024, 9, 27, 1, 30);
                t.setHours(2);
                var u = new Date(2024, 9, 27, 3, 0);
                u.setHours(2, 30);
                [s.getTime(), t.getTime(), u.getTime(), new Date(1800, 0, 1).getTime(), Date.parse('Jan 1 1800 10:00'),
                  new Date(-5364665608000).getSeconds()].join()""");
    }

    @Test
    public void testTheOffsetBeforeTheTransitionApplies() {
        ZoneRules berlin = ZoneId.of("Europe/Berlin").getRules();
        // 02:30 on 31 March 2024 does not exist; the winter offset applies, as in V8.
        assertThat(JSDate.getLocalOffsetSeconds(berlin, localMillis(2024, 3, 31, 2, 30))).isEqualTo(3600);
        // 02:30 on 27 October 2024 happens twice; the summer offset picks the first occurrence.
        assertThat(JSDate.getLocalOffsetSeconds(berlin, localMillis(2024, 10, 27, 2, 30))).isEqualTo(7200);
        assertThat(JSDate.getLocalOffsetSeconds(berlin, localMillis(2024, 7, 1, 12, 0))).isEqualTo(7200);
        // Local mean time, seconds included.
        assertThat(JSDate.getLocalOffsetSeconds(berlin, localMillis(1800, 1, 1, 0, 0))).isEqualTo(3208);
        ZoneRules newYork = ZoneId.of("America/New_York").getRules();
        assertThat(JSDate.getLocalOffsetSeconds(newYork, localMillis(2024, 3, 10, 2, 30))).isEqualTo(-5 * 3600);
        assertThat(JSDate.getLocalOffsetSeconds(newYork, localMillis(2024, 11, 3, 1, 30))).isEqualTo(-4 * 3600);
        // Far outside the time value range the conversion still answers rather than failing.
        assertThat(JSDate.getLocalOffsetSeconds(berlin, Long.MAX_VALUE)).isIn(3600, 7200);
        assertThat(JSDate.getLocalOffsetSeconds(berlin, Long.MIN_VALUE)).isEqualTo(3208);
    }

    private static long localMillis(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).toEpochSecond(ZoneOffset.UTC) * 1000L;
    }
}
