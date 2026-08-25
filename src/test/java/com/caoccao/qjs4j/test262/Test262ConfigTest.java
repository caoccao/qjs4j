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

package com.caoccao.qjs4j.test262;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Test262 configurations must carry the thread ceiling each suite is meant to run under.
 * <p>
 * The full suite holds a runtime and a context per worker and includes tests that build strings and
 * backtrack stacks in the tens of megabytes, so it is capped; the short subsets are not, and take
 * the machine's core count.
 */
public class Test262ConfigTest {

    @Test
    public void testDefaultConfigurationCapsThreads() {
        assertThat(Test262Config.loadDefault().getMaxThreadCount())
                .isEqualTo(Test262Config.DEFAULT_MAX_THREAD_COUNT)
                .isEqualTo(4);
    }

    @Test
    public void testLanguageConfigurationIsUnlimited() {
        assertThat(Test262Config.forLanguageTests().getMaxThreadCount())
                .isEqualTo(Test262Config.UNLIMITED_THREAD_COUNT)
                .isZero();
    }

    @Test
    public void testLongRunningConfigurationKeepsTheDefaultCap() {
        assertThat(Test262Config.forLongRunningTest().getMaxThreadCount())
                .isEqualTo(Test262Config.DEFAULT_MAX_THREAD_COUNT);
    }

    @Test
    public void testMaxThreadCountIsSettable() {
        Test262Config config = Test262Config.loadDefault();
        config.setMaxThreadCount(8);
        assertThat(config.getMaxThreadCount()).isEqualTo(8);
        config.setMaxThreadCount(Test262Config.UNLIMITED_THREAD_COUNT);
        assertThat(config.getMaxThreadCount()).isEqualTo(Test262Config.UNLIMITED_THREAD_COUNT);
    }

    @Test
    public void testNegativeMaxThreadCountIsTreatedAsUnlimited() {
        Test262Config config = Test262Config.loadDefault();
        config.setMaxThreadCount(-1);
        assertThat(config.getMaxThreadCount()).isEqualTo(Test262Config.UNLIMITED_THREAD_COUNT);
    }

    @Test
    public void testQuickConfigurationIsUnlimited() {
        assertThat(Test262Config.forQuickTest().getMaxThreadCount())
                .isEqualTo(Test262Config.UNLIMITED_THREAD_COUNT)
                .isZero();
    }
}
