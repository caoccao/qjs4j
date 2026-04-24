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

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.temporal.TemporalDuration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TemporalDurationConstructorTest extends BaseTest {

    @Test
    public void testNormalizeFloat64RepresentableFieldsWithAlreadyRepresentableLargeValue() {
        TemporalDuration originalDuration = new TemporalDuration(
                18_014_398_509_481_984L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L);
        TemporalDuration normalizedDuration = originalDuration.normalizeFloat64RepresentableFields();
        assertThat(normalizedDuration).isSameAs(originalDuration);
    }

    @Test
    public void testNormalizeFloat64RepresentableFieldsWithNonRepresentableValue() {
        TemporalDuration originalDuration = new TemporalDuration(
                9_007_199_254_740_993L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L);
        TemporalDuration normalizedDuration = originalDuration.normalizeFloat64RepresentableFields();
        assertThat(normalizedDuration).isNotSameAs(originalDuration);
        assertThat(normalizedDuration.years()).isEqualTo(9_007_199_254_740_992L);
    }

    @Test
    public void testNormalizeFloat64RepresentableFieldsWithSmallValues() {
        TemporalDuration originalDuration = new TemporalDuration(
                12L,
                -10L,
                0L,
                365L,
                23L,
                59L,
                58L,
                999L,
                998L,
                997L);
        TemporalDuration normalizedDuration = originalDuration.normalizeFloat64RepresentableFields();
        assertThat(normalizedDuration).isSameAs(originalDuration);
    }
}
