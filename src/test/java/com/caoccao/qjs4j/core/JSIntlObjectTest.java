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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Intl object and Intl.* constructors.
 */
public class JSIntlObjectTest extends BaseJavetTest {

    @Test
    public void testCollator() {
        assertBooleanWithJavet("""
                var collator = new Intl.Collator('en-US');
                collator.compare('a', 'b') < 0 && collator.compare('b', 'a') > 0 && collator.compare('a', 'a') === 0""",
                """
                        var collator = Intl.Collator('en-US', { sensitivity: 'base' });
                        collator instanceof Intl.Collator && typeof collator.resolvedOptions().locale === 'string'""",
                """
                        Intl.Collator.supportedLocalesOf(['en-US', 'fr-FR']).length >= 2""");

        assertEvalThrows("Intl.Collator.prototype.compare.call({}, 'a', 'b')", "TypeError");
        assertEvalThrows("new Intl.Collator('')", "RangeError");
    }

    @Test
    public void testCompactFormatPartsAtScaleBoundaries() {
        assertStringWithJavet("""
                (() => {
                    const results = [];
                    for (const locale of ['en', 'en-IN', 'ja', 'ko', 'zh-TW']) {
                        const format = new Intl.NumberFormat(locale, {notation: 'compact'});
                        for (const value of [999, 1000, 10000, 100000, 1000000, 10000000, 100000000]) {
                            results.push(format.formatToParts(value).map(p => p.type + '=' + p.value).join(','));
                        }
                    }
                    return results.join('|');
                })()
                """);
    }

    @Test
    public void testDateTimeFormat() {
        assertBooleanWithJavet(
                """
                        var dateTimeFormat = new Intl.DateTimeFormat('en-US', { dateStyle: 'short', timeStyle: 'short' });
                        typeof dateTimeFormat.format(1704067200000) === 'string' && dateTimeFormat.format(1704067200000).length > 0""",
                """
                        var dateTimeFormat = Intl.DateTimeFormat('en-US');
                        dateTimeFormat instanceof Intl.DateTimeFormat""", """
                        var resolvedOptions = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long' }).resolvedOptions();
                        typeof resolvedOptions.locale === 'string' && typeof resolvedOptions.timeZone === 'string'""");

        assertEvalThrows("Intl.DateTimeFormat.prototype.format.call({}, 1704067200000)", "TypeError");
        assertEvalThrows("new Intl.DateTimeFormat('', { dateStyle: 'short' })", "RangeError");
        assertEvalThrows("new Intl.DateTimeFormat('en-US', { dateStyle: 'invalid-style' })", "RangeError");
    }

    @Test
    public void testGetCanonicalLocales() {
        assertStringWithJavet("JSON.stringify(Intl.getCanonicalLocales(['EN-us', 'fr-fr', 'en-US']))",
                "JSON.stringify(Intl.NumberFormat.supportedLocalesOf(['en-US', 'fr-FR', 'en-US']))",
                "JSON.stringify(Intl.Collator.supportedLocalesOf(['de-DE']))");

        assertEvalThrows("Intl.getCanonicalLocales([''])", "RangeError");
        assertEvalThrows("Intl.NumberFormat.supportedLocalesOf([''])", "RangeError");
    }

    @Test
    public void testIntlNamespace() {
        assertBooleanWithJavet("typeof Intl === 'object' && Intl !== null",
                "typeof Intl.getCanonicalLocales === 'function'", "typeof Intl.DateTimeFormat === 'function'",
                "typeof Intl.NumberFormat === 'function'", "typeof Intl.Collator === 'function'",
                "typeof Intl.PluralRules === 'function'", "typeof Intl.RelativeTimeFormat === 'function'",
                "typeof Intl.ListFormat === 'function'", "typeof Intl.Locale === 'function'");
    }

    @Test
    public void testInvalidTagsRejected() {
        // Empty string, lone singletons, and private-use only tags are invalid
        assertErrorWithJavet("Intl.getCanonicalLocales('')", "Intl.getCanonicalLocales('i')",
                "Intl.getCanonicalLocales('x')", "Intl.getCanonicalLocales('x-foo')");

    }

    @Test
    public void testListFormat() {
        assertBooleanWithJavet("""
                var listFormat = new Intl.ListFormat('en-US', { style: 'short', type: 'conjunction' });
                typeof listFormat.format(['A', 'B', 'C']) === 'string' && listFormat.format(['A', 'B']).length > 0""",
                """
                        var listFormat = new Intl.ListFormat('en-US', { type: 'disjunction' });
                        listFormat instanceof Intl.ListFormat""",
                """
                        var resolvedOptions = new Intl.ListFormat('en-US', { style: 'narrow', type: 'unit' }).resolvedOptions();
                        resolvedOptions.style === 'narrow' && resolvedOptions.type === 'unit'""");

        assertEvalThrows("Intl.ListFormat.prototype.format.call({}, ['a', 'b'])", "TypeError");
        // Strings are iterable and yield single-character strings, so format() accepts them per spec
        assertBooleanWithJavet("typeof new Intl.ListFormat('en-US').format('abc') === 'string'");
        assertEvalThrows("new Intl.ListFormat('', { style: 'short' })", "RangeError");
    }

    @Test
    public void testLocale() {
        assertBooleanWithJavet(
                """
                        var locale = new Intl.Locale('en-Latn-US');
                        locale.language === 'en' && locale.script === 'Latn' && locale.region === 'US' && locale.toString() === 'en-Latn-US'""",
                """
                        var locale = new Intl.Locale('en');
                        locale.baseName === 'en' && locale.region === undefined && locale.script === undefined""");

        assertEvalThrows("Intl.Locale('en-US')", "TypeError");
        assertEvalThrows("new Intl.Locale()", "TypeError");
        assertEvalThrows("new Intl.Locale('')", "RangeError");
    }

    @Test
    public void testNumberFormat() {
        assertBooleanWithJavet("""
                var numberFormat = new Intl.NumberFormat('en-US');
                typeof numberFormat.format(123456.789) === 'string' && numberFormat.format(123456.789).length > 0""",
                """
                        var numberFormat = Intl.NumberFormat('en-US', { style: 'percent' });
                        numberFormat instanceof Intl.NumberFormat && typeof numberFormat.resolvedOptions().style === 'string'""",
                """
                        var numberFormat = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
                        numberFormat.resolvedOptions().currency === 'USD'""");

        assertEvalThrows("Intl.NumberFormat.prototype.format.call({}, 1)", "TypeError");
        assertEvalThrows("new Intl.NumberFormat('', { style: 'decimal' })", "RangeError");
        assertEvalThrows("new Intl.NumberFormat('en-US', { style: 'currency', currency: 'US' })", "RangeError");
        assertEvalThrows("new Intl.NumberFormat('en-US', { style: 'bad-style' })", "RangeError");
    }

    @Test
    public void testPluralRules() {
        assertBooleanWithJavet("""
                var pluralRules = new Intl.PluralRules('en-US');
                pluralRules.select(1) === 'one' && pluralRules.select(2) === 'other'""", """
                var pluralRules = new Intl.PluralRules('en-US', { type: 'ordinal' });
                ['one', 'two', 'few', 'other'].includes(pluralRules.select(3))""", """
                var resolvedOptions = new Intl.PluralRules('en-US').resolvedOptions();
                resolvedOptions.type === 'cardinal' && Array.isArray(resolvedOptions.pluralCategories)""");

        assertEvalThrows("Intl.PluralRules.prototype.select.call({}, 1)", "TypeError");
        assertEvalThrows("new Intl.PluralRules('', { type: 'cardinal' })", "RangeError");
        assertEvalThrows("new Intl.PluralRules('en-US', { type: 'bad-type' })", "RangeError");
    }

    @Test
    public void testRelativeTimeFormat() {
        assertBooleanWithJavet(
                """
                        var relativeTimeFormat = new Intl.RelativeTimeFormat('en-US', { numeric: 'always' });
                        typeof relativeTimeFormat.format(-1, 'day') === 'string' && relativeTimeFormat.format(-1, 'day').length > 0""",
                """
                        var relativeTimeFormat = new Intl.RelativeTimeFormat('en-US', { numeric: 'auto', style: 'short' });
                        relativeTimeFormat instanceof Intl.RelativeTimeFormat && relativeTimeFormat.resolvedOptions().numeric === 'auto'""",
                """
                        var relativeTimeFormat = new Intl.RelativeTimeFormat('en-US');
                        relativeTimeFormat.format(2, 'week').includes('2')""");

        assertEvalThrows("Intl.RelativeTimeFormat.prototype.format.call({}, 1, 'day')", "TypeError");
        assertEvalThrows("new Intl.RelativeTimeFormat('', { numeric: 'always' })", "RangeError");
        assertEvalThrows("new Intl.RelativeTimeFormat('en-US').format(1, 'bad-unit')", "RangeError");
    }

    @Test
    public void testRelativeTimePartsAfterCoercion() {
        assertStringWithJavet("""
                (() => {
                    const formatter = new Intl.RelativeTimeFormat('en', {numeric: 'auto'});
                    const calls = [];
                    const value = { valueOf() { calls.push('value'); return -2.5; } };
                    const unit = { toString() { calls.push('unit'); return 'days'; } };
                    const parts = formatter.formatToParts(value, unit);
                    return JSON.stringify([calls, parts.map(p => [p.type, p.value, p.unit]),
                        formatter.formatToParts(-1, 'days')]);
                })()
                """);
    }

    @Test
    public void testSupportedValuesOfCalendars() {
        // Required calendars should be present
        assertBooleanWithJavet("Intl.supportedValuesOf('calendar').includes('islamic-civil')",
                "Intl.supportedValuesOf('calendar').includes('islamic-tbla')",
                "Intl.supportedValuesOf('calendar').includes('islamic-umalqura')",
                "Intl.supportedValuesOf('calendar').includes('gregory')");

        // The list should be sorted
        assertBooleanWithJavet("""
                var calendars = Intl.supportedValuesOf('calendar');
                JSON.stringify(calendars) === JSON.stringify(calendars.slice().sort())""");
    }

    @Test
    public void testTransformExtensionCanonicalization() {
        // Script in t-extension should be lowercased (not title-cased like main locale)
        assertStringWithJavet("Intl.getCanonicalLocales('und-Latn-t-und-Hani-m0-prprname')[0]",
                "Intl.getCanonicalLocales('en-t-en-Latn')[0]");

        // Language in t-extension should be lowercased and aliased
        assertStringWithJavet("Intl.getCanonicalLocales('en-t-iw')[0]");

        // Region in t-extension should be lowercased
        assertStringWithJavet("Intl.getCanonicalLocales('en-t-en-US')[0]");

        // Tfield keys sorted, values lowercased
        assertStringWithJavet("Intl.getCanonicalLocales('DE-T-M0-DIN-K0-QWERTZ')[0]");

        // Variant subtags in tlang sorted alphabetically
        assertStringWithJavet("Intl.getCanonicalLocales('sl-t-sl-rozaj-biske-1994')[0]");

        // Valid t-extension tags should be accepted unchanged when already canonical
        assertStringWithJavet("Intl.getCanonicalLocales('en-t-en')[0]", "Intl.getCanonicalLocales('en-t-en-latn')[0]",
                "Intl.getCanonicalLocales('en-t-d0-ascii')[0]", "Intl.getCanonicalLocales('en-t-m0-true')[0]");
    }

    @Test
    public void testWhitespaceTagRejection() {
        // Tags with leading/trailing whitespace should be rejected
        assertErrorWithJavet("Intl.getCanonicalLocales(' en')", "Intl.getCanonicalLocales('en ')",
                "Intl.getCanonicalLocales(' en ')");
    }
}
