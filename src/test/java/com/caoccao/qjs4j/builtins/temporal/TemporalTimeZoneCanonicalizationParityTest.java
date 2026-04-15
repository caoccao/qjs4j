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

public class TemporalTimeZoneCanonicalizationParityTest extends BaseJavetTest {
    @Test
    public void testCanonicalAliasEquality() {
        assertBooleanWithJavet(
                "new Temporal.ZonedDateTime(0n, 'Asia/Calcutta').equals(new Temporal.ZonedDateTime(0n, 'Asia/Kolkata'))");
    }

    @Test
    public void testCanonicalDistinctNotEqual() {
        assertBooleanWithJavet(
                "new Temporal.ZonedDateTime(0n, 'Europe/Berlin').equals(new Temporal.ZonedDateTime(0n, 'Europe/Paris'))");
    }

    @Test
    public void testInvalidTimeZoneIdentifier() {
        assertBooleanWithJavet("(() => { try { new Temporal.ZonedDateTime(0n, 'Invalid/Zone'); return false; } catch (e) { return e instanceof RangeError; } })()");
    }

    @Test
    public void testOffsetIdentifierForms() {
        assertBooleanWithJavet(
                "new Temporal.ZonedDateTime(0n, '+00:00').equals(new Temporal.ZonedDateTime(0n, '-00:00'))");
    }
}
