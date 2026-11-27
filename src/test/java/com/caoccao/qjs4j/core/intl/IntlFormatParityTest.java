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

package com.caoccao.qjs4j.core.intl;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

/**
 * Intl output matches V8 where the review found it did not.
 * <p>
 * Duration and unit formatting printed the singular unit identifier for every count and width ("30 minute");
 * DurationFormat resolved its unit options by its own rules rather than GetDurationUnitOptions, so the digital style
 * defaulted to long date units and hid zero hours; a full or long dateStyle with a timeStyle used the plain date-time
 * glue instead of CLDR's "at" glue; component patterns shrank the locale's numeric widths ("1/1/1970" for en-GB); and
 * English AM/PM was preceded by U+202F, which V8 replaces with an ordinary space.
 */
public class IntlFormatParityTest extends BaseJavetTest {

    @Test
    public void testDateTimeFormatAtTimeGlue() {
        assertStringWithJavet("""
                const date = new Date(Date.UTC(2024, 2, 5, 13, 4, 5));
                const format = (locale, options) =>
                  new Intl.DateTimeFormat(locale, { timeZone: 'UTC', ...options }).format(date);
                [
                  format('en', { dateStyle: 'full', timeStyle: 'long' }),
                  format('en', { dateStyle: 'long', timeStyle: 'short' }),
                  format('en', { dateStyle: 'medium', timeStyle: 'short' }),
                  format('en', { dateStyle: 'long', timeStyle: 'short', hourCycle: 'h12' }),
                  format('en-GB', { dateStyle: 'full', timeStyle: 'short' }),
                  format('de', { dateStyle: 'full', timeStyle: 'short' }),
                  format('fr', { dateStyle: 'long', timeStyle: 'medium' }),
                  format('it', { dateStyle: 'long', timeStyle: 'short' }),
                  format('nl', { dateStyle: 'long', timeStyle: 'short' }),
                  format('ru', { dateStyle: 'long', timeStyle: 'short' }),
                  format('es', { dateStyle: 'long', timeStyle: 'short' }),
                  format('pl', { dateStyle: 'long', timeStyle: 'short' }),
                  format('vi', { dateStyle: 'long', timeStyle: 'short' }),
                  format('ja', { dateStyle: 'long', timeStyle: 'short' }),
                  JSON.stringify(new Intl.DateTimeFormat('en-GB', { timeZone: 'UTC', dateStyle: 'long',
                    timeStyle: 'short' }).formatToParts(date)),
                ].join('\\n')""");
    }

    @EnabledForJreRange(min = JRE.JAVA_19)
    @Test
    public void testDateTimeFormatComponentPatternsFollowCldrSkeletons() {
        // The CLDR skeleton lookup is JDK 19+ API; JDK 17 and 18 keep deriving component patterns from the date styles.
        assertStringWithJavet("""
                const date = new Date(Date.UTC(2024, 2, 5, 13, 4, 5));
                const format = (locale, options) =>
                  new Intl.DateTimeFormat(locale, { timeZone: 'UTC', ...options }).format(date);
                [
                  format('en-GB', {}), format('fr', {}), format('ru', {}), format('en-CA', {}),
                  format('hu', {}), format('de', {}), format('ja', {}),
                  format('en-GB', { month: 'numeric', day: 'numeric' }),
                  format('en-GB', { year: 'numeric', month: '2-digit', day: '2-digit' }),
                  format('en', { weekday: 'narrow', year: '2-digit', month: 'narrow', day: '2-digit' }),
                  format('en', { weekday: 'short', year: 'numeric', month: 'short', day: 'numeric' }),
                  format('en', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' }),
                  format('de', { year: 'numeric', month: 'long', day: 'numeric' }),
                  format('es', { year: 'numeric', month: 'long', day: 'numeric' }),
                  format('en', { year: 'numeric', month: 'long' }),
                  format('en', { era: 'long', year: 'numeric', month: 'long', day: 'numeric' }),
                  format('en-GB', { year: 'numeric', month: 'numeric', day: 'numeric', hour: 'numeric',
                    minute: 'numeric' }),
                  new Date(0).toLocaleDateString('en-GB', { timeZone: 'UTC' }),
                ].join('\\n')""");
        // Other calendars, and skeletons the JDK cannot resolve ("yd"), keep the style-derived patterns. Neither
        // matches V8 exactly, so only the fields are checked.
        assertBooleanWithJavet("""
                const date = new Date(Date.UTC(2024, 2, 5, 13, 4, 5));
                const format = (locale, options) =>
                  new Intl.DateTimeFormat(locale, { timeZone: 'UTC', ...options }).format(date);
                format('en', { calendar: 'buddhist', year: 'numeric', month: 'numeric', day: 'numeric' })
                  .startsWith('3/5/2567')""", """
                const date = new Date(Date.UTC(2024, 2, 5, 13, 4, 5));
                const format = (locale, options) =>
                  new Intl.DateTimeFormat(locale, { timeZone: 'UTC', ...options }).format(date);
                /5.*2024|2024.*5/.test(format('en', { year: 'numeric', day: 'numeric' }))""");
    }

    @Test
    public void testDateTimeFormatEraWidths() {
        assertStringWithJavet("""
                const date = new Date(Date.UTC(2024, 2, 5, 13, 4, 5));
                const format = (locale, options) =>
                  new Intl.DateTimeFormat(locale, { timeZone: 'UTC', ...options }).format(date);
                const bc = (era) =>
                  new Intl.DateTimeFormat('en', { timeZone: 'UTC', era, year: 'numeric' }).format(Date.UTC(-100, 0, 1));
                [
                  format('en', { era: 'short', year: 'numeric', month: 'short', day: 'numeric' }),
                  format('en', { era: 'long', year: 'numeric', month: 'long', day: 'numeric' }),
                  format('en', { era: 'narrow', weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' }),
                  bc('short'), bc('long'), bc('narrow'),
                ].join('\\n')""");
    }

    @Test
    public void testDateTimeFormatFormatsWeekdayAlone() {
        assertStringWithJavet("""
                const date = new Date(Date.UTC(2024, 2, 5, 13, 4, 5));
                const format = (locale, options) =>
                  new Intl.DateTimeFormat(locale, { timeZone: 'UTC', ...options }).format(date);
                [
                  format('en', { weekday: 'long' }),
                  format('en', { weekday: 'short' }),
                  format('de', { weekday: 'long' }),
                ].join('\\n')""");
    }

    @Test
    public void testDateTimeFormatUsesOrdinarySpaceBeforeDayPeriod() {
        assertStringWithJavet("""
                const date = new Date(Date.UTC(2024, 2, 5, 13, 4, 5));
                const format = (locale, options) =>
                  new Intl.DateTimeFormat(locale, { timeZone: 'UTC', ...options }).format(date);
                [
                  format('en', { hour: 'numeric', minute: 'numeric' }),
                  format('en', { timeStyle: 'short' }),
                  date.toLocaleTimeString('en-US', { timeZone: 'UTC' }),
                  new Date(0).toLocaleString('en-US', { timeZone: 'UTC' }),
                  new Intl.DateTimeFormat('en', { timeZone: 'UTC', dateStyle: 'medium', timeStyle: 'medium' })
                    .formatRange(date, date),
                ].join('\\n')""");
        // V8 keeps U+202F in formatToParts(); qjs4j replaces it there too, so that format(), formatToParts() and
        // formatRange() agree, as test262 requires.
        assertEvalJavaValue("""
                new Intl.DateTimeFormat('en', { timeZone: 'UTC', timeStyle: 'short' }).formatToParts(0)
                  .map(part => part.value).join('|')""", "12|:|00| |AM");
    }

    @Test
    public void testDurationFormatDigitalStyleDefaults() {
        assertStringWithJavet("""
                const options = [
                  { style: 'digital' }, { hours: 'numeric' }, { hours: 'numeric', minutes: '2-digit' },
                  { style: 'digital', hours: 'long' }, { style: 'digital', hoursDisplay: 'auto' },
                  { minutes: '2-digit' },
                ];
                const durations = [
                  { hours: 1, minutes: 30 }, { years: 1, months: 2, weeks: 1, days: 1 },
                  { minutes: -1, seconds: -2 }, { days: 1, hours: 1 }, { seconds: 5 },
                ];
                const digital = new Intl.DurationFormat('en', { style: 'digital' });
                options.map(o => JSON.stringify(new Intl.DurationFormat('en', o).resolvedOptions()))
                  .concat(durations.map(d => digital.format(d)))
                  .concat([new Intl.DurationFormat('en', { hours: 'numeric' }).format({ hours: 1, minutes: 30 })])
                  .join('\\n')""");
    }

    @Test
    public void testDurationFormatNonEnglishPartsMatchFormat() {
        // Only English unit names are translated; other locales keep the unit identifier, but their parts still have
        // V8's shape and join to what format() prints.
        assertBooleanWithJavet("""
                const duration = { hours: 2, minutes: 1, seconds: 3, milliseconds: 500 };
                ['de', 'fr', 'ja'].every(locale => ['long', 'short', 'narrow'].every(style =>
                  [{ style }, { style, milliseconds: 'numeric' }].every(options => {
                    const df = new Intl.DurationFormat(locale, options);
                    return df.format(duration) === df.formatToParts(duration).map(part => part.value).join('');
                  })))""");
        assertStringWithJavet("""
                JSON.stringify(['de', 'fr'].map(locale => new Intl.DurationFormat(locale, { style: 'long' })
                  .formatToParts({ hours: 2, minutes: 1 }).map(part => part.type)))""");
    }

    @Test
    public void testDurationFormatPluralizesEnglishUnits() {
        assertStringWithJavet("""
                const duration = {
                  years: 2, months: 1, weeks: 3, days: 1, hours: 1, minutes: 30,
                  seconds: 1, milliseconds: 5, microseconds: 1, nanoseconds: 2,
                };
                const format = (options, value) => new Intl.DurationFormat('en', options).format(value);
                ['long', 'short', 'narrow'].map(style => format({ style }, duration)).concat([
                  format({ style: 'long' }, { hours: 1, minutes: 30 }),
                  format({ style: 'short' }, { years: 1, months: 1, weeks: 1, days: 1 }),
                  format({ style: 'long' }, { hours: -1, minutes: -30 }),
                  format({ style: 'long', fractionalDigits: 2 }, { seconds: 1, milliseconds: 500 }),
                  format({ style: 'long', milliseconds: 'numeric' }, { seconds: 1, milliseconds: 500 }),
                  format({ style: 'long' }, { days: 2 }),
                ]).join('\\n')""");
    }

    @Test
    public void testDurationFormatReadsEachDisplayAfterValidatingItsStyle() {
        assertStringWithJavet("""
                const log = [];
                const record = (name, value) => ({ get() { log.push(name); return value; } });
                for (const options of [
                  Object.defineProperties({}, {
                    hours: record('hours', 'bogus'),
                    hoursDisplay: record('hoursDisplay', 'auto'),
                  }),
                  Object.defineProperties({}, {
                    minutes: record('minutes', 'long'),
                    minutesDisplay: { get() { throw new TypeError('display'); } },
                  }),
                ]) {
                  try {
                    new Intl.DurationFormat('en', options);
                  } catch (e) {
                    log.push(e.name + ': ' + e.message);
                  }
                }
                log.join('\\n')""");
    }

    @Test
    public void testDurationFormatRejectsInvalidUnitStyleCombinations() {
        // V8 reports these as "Invalid object : #<Object>"; qjs4j names the conflict, so only the type is compared.
        assertBooleanWithJavet("""
                [
                  { hours: 'numeric', minutes: 'long' }, { milliseconds: 'numeric', millisecondsDisplay: 'always' },
                  { seconds: 'numeric', milliseconds: 'long' }, { milliseconds: 'numeric', microseconds: 'long' },
                  { style: 'digital', milliseconds: 'long' }, { style: 'digital', millisecondsDisplay: 'always' },
                  { years: 'numeric' }, { hoursDisplay: 'sometimes' },
                ].every(options => {
                  try {
                    new Intl.DurationFormat('en', options);
                    return false;
                  } catch (e) {
                    return e instanceof RangeError;
                  }
                })""");
    }

    @Test
    public void testDurationFormatToPartsUsesPluralUnits() {
        assertStringWithJavet("""
                const duration = { hours: 1, minutes: 30, seconds: 1, milliseconds: 500 };
                [
                  new Intl.DurationFormat('en', { style: 'long' }).formatToParts(duration),
                  new Intl.DurationFormat('en', { style: 'narrow' }).formatToParts(duration),
                  new Intl.DurationFormat('en', { style: 'long', milliseconds: 'numeric' })
                    .formatToParts({ seconds: 1, milliseconds: 500 }),
                ].map(parts => JSON.stringify(parts)).join('\\n')""");
    }

    @Test
    public void testNumberFormatPluralizesEnglishTimeUnits() {
        assertStringWithJavet("""
                const units = [
                  'year', 'month', 'week', 'day', 'hour', 'minute', 'second',
                  'millisecond', 'microsecond', 'nanosecond',
                ];
                const format = (options, value) =>
                  new Intl.NumberFormat('en', { style: 'unit', ...options }).format(value);
                const out = [];
                for (const unit of units) {
                  for (const unitDisplay of ['long', 'short', 'narrow']) {
                    out.push([1, 2, 1.5, -1, 0].map(value => format({ unit, unitDisplay }, value)).join(' | '));
                  }
                }
                out.push(format({ unit: 'hour' }, 3));
                out.push(format({ unit: 'hour', unitDisplay: 'long', signDisplay: 'always' }, 1));
                out.push(format({ unit: 'kilometer-per-hour', unitDisplay: 'long' }, 50));
                out.join('\\n')""");
    }
}
