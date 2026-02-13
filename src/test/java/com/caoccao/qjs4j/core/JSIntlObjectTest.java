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
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Intl object and Intl.* constructors.
 */
public class JSIntlObjectTest extends BaseJavetTest {

    @Test
    public void testCollator() {
        assertBooleanWithJavet(
                """
                        var collator = new Intl.Collator('en-US');
                        collator.compare('a', 'b') < 0 && collator.compare('b', 'a') > 0 && collator.compare('a', 'a') === 0""",
                """
                        var collator = Intl.Collator('en-US', { sensitivity: 'base' });
                        collator instanceof Intl.Collator && typeof collator.resolvedOptions().locale === 'string'""",
                """
                        Intl.Collator.supportedLocalesOf(['en-US', 'fr-FR']).length >= 2""");

        assertThatThrownBy(() -> context.eval("Intl.Collator.prototype.compare.call({}, 'a', 'b')"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
        assertThatThrownBy(() -> context.eval("new Intl.Collator('')"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("RangeError");
    }

    @Test
    public void testDateTimeFormat() {
        assertBooleanWithJavet(
                """
                        var dateTimeFormat = new Intl.DateTimeFormat('en-US', { dateStyle: 'short', timeStyle: 'short' });
                        typeof dateTimeFormat.format(1704067200000) === 'string' && dateTimeFormat.format(1704067200000).length > 0""",
                """
                        var dateTimeFormat = Intl.DateTimeFormat('en-US');
                        dateTimeFormat instanceof Intl.DateTimeFormat""",
                """
                        var resolvedOptions = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long' }).resolvedOptions();
                        typeof resolvedOptions.locale === 'string' && typeof resolvedOptions.timeZone === 'string'""");

        assertThatThrownBy(() -> context.eval("Intl.DateTimeFormat.prototype.format.call({}, 1704067200000)"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
        assertThatThrownBy(() -> context.eval("new Intl.DateTimeFormat('', { dateStyle: 'short' })"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("RangeError");
        assertThatThrownBy(() -> context.eval("new Intl.DateTimeFormat('en-US', { dateStyle: 'invalid-style' })"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("RangeError");
    }

    @Test
    public void testGetCanonicalLocales() {
        assertStringWithJavet(
                "JSON.stringify(Intl.getCanonicalLocales(['EN-us', 'fr-fr', 'en-US']))",
                "JSON.stringify(Intl.NumberFormat.supportedLocalesOf(['en-US', 'fr-FR', 'en-US']))",
                "JSON.stringify(Intl.Collator.supportedLocalesOf(['de-DE']))");

        assertThatThrownBy(() -> context.eval("Intl.getCanonicalLocales([''])"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("RangeError");
        assertThatThrownBy(() -> context.eval("Intl.NumberFormat.supportedLocalesOf([''])"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("RangeError");
    }

    @Test
    public void testIntlNamespace() {
        assertBooleanWithJavet(
                "typeof Intl === 'object' && Intl !== null",
                "typeof Intl.getCanonicalLocales === 'function'",
                "typeof Intl.DateTimeFormat === 'function'",
                "typeof Intl.NumberFormat === 'function'",
                "typeof Intl.Collator === 'function'",
                "typeof Intl.PluralRules === 'function'",
                "typeof Intl.RelativeTimeFormat === 'function'",
                "typeof Intl.ListFormat === 'function'",
                "typeof Intl.Locale === 'function'");
    }

    @Test
    public void testListFormat() {
        assertBooleanWithJavet(
                """
                        var listFormat = new Intl.ListFormat('en-US', { style: 'short', type: 'conjunction' });
                        typeof listFormat.format(['A', 'B', 'C']) === 'string' && listFormat.format(['A', 'B']).length > 0""",
                """
                        var listFormat = new Intl.ListFormat('en-US', { type: 'disjunction' });
                        listFormat instanceof Intl.ListFormat""",
                """
                        var resolvedOptions = new Intl.ListFormat('en-US', { style: 'narrow', type: 'unit' }).resolvedOptions();
                        resolvedOptions.style === 'narrow' && resolvedOptions.type === 'unit'""");

        assertThatThrownBy(() -> context.eval("Intl.ListFormat.prototype.format.call({}, ['a', 'b'])"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
        assertThatThrownBy(() -> context.eval("new Intl.ListFormat('en-US').format('not-an-array')"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
        assertThatThrownBy(() -> context.eval("new Intl.ListFormat('', { style: 'short' })"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("RangeError");
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

        assertThatThrownBy(() -> context.eval("Intl.Locale('en-US')"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
        assertThatThrownBy(() -> context.eval("new Intl.Locale()"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
        assertThatThrownBy(() -> context.eval("new Intl.Locale('')"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("RangeError");
    }

    @Test
    public void testNumberFormat() {
        assertBooleanWithJavet(
                """
                        var numberFormat = new Intl.NumberFormat('en-US');
                        typeof numberFormat.format(123456.789) === 'string' && numberFormat.format(123456.789).length > 0""",
                """
                        var numberFormat = Intl.NumberFormat('en-US', { style: 'percent' });
                        numberFormat instanceof Intl.NumberFormat && typeof numberFormat.resolvedOptions().style === 'string'""",
                """
                        var numberFormat = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
                        numberFormat.resolvedOptions().currency === 'USD'""");

        assertThatThrownBy(() -> context.eval("Intl.NumberFormat.prototype.format.call({}, 1)"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
        assertThatThrownBy(() -> context.eval("new Intl.NumberFormat('', { style: 'decimal' })"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("RangeError");
        assertThatThrownBy(() -> context.eval("new Intl.NumberFormat('en-US', { style: 'currency', currency: 'US' })"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("RangeError");
        assertThatThrownBy(() -> context.eval("new Intl.NumberFormat('en-US', { style: 'bad-style' })"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("RangeError");
    }

    @Test
    public void testPluralRules() {
        assertBooleanWithJavet(
                """
                        var pluralRules = new Intl.PluralRules('en-US');
                        pluralRules.select(1) === 'one' && pluralRules.select(2) === 'other'""",
                """
                        var pluralRules = new Intl.PluralRules('en-US', { type: 'ordinal' });
                        ['one', 'two', 'few', 'other'].includes(pluralRules.select(3))""",
                """
                        var resolvedOptions = new Intl.PluralRules('en-US').resolvedOptions();
                        resolvedOptions.type === 'cardinal' && Array.isArray(resolvedOptions.pluralCategories)""");

        assertThatThrownBy(() -> context.eval("Intl.PluralRules.prototype.select.call({}, 1)"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
        assertThatThrownBy(() -> context.eval("new Intl.PluralRules('', { type: 'cardinal' })"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("RangeError");
        assertThatThrownBy(() -> context.eval("new Intl.PluralRules('en-US', { type: 'bad-type' })"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("RangeError");
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

        assertThatThrownBy(() -> context.eval("Intl.RelativeTimeFormat.prototype.format.call({}, 1, 'day')"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
        assertThatThrownBy(() -> context.eval("new Intl.RelativeTimeFormat('', { numeric: 'always' })"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("RangeError");
        assertThatThrownBy(() -> context.eval("new Intl.RelativeTimeFormat('en-US').format(1, 'bad-unit')"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("RangeError");
    }
}
