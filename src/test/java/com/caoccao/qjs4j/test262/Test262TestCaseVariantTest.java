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

import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test262 defines how many interpretations a file has. Running one per file — which is what the
 * runner did — meant no ordinary test was ever executed in strict mode.
 */
public class Test262TestCaseVariantTest {
    private Test262TestCase testCaseWithFlags(String... flags) {
        Test262TestCase testCase = new Test262TestCase(Paths.get("fake/test.js"));
        testCase.setFlags(Set.of(flags));
        testCase.setCode("var x = 1;");
        return testCase;
    }

    @Test
    void testDefaultFileRunsNonStrictAndStrict() {
        assertThat(variantsOf())
                .containsExactly(Test262TestCase.Variant.NON_STRICT, Test262TestCase.Variant.STRICT);
    }

    @Test
    void testEqualityAndHashCodeDistinguishVariants() {
        List<Test262TestCase> variants = testCaseWithFlags().expandVariants();
        assertThat(variants.get(0)).isNotEqualTo(variants.get(1));
        assertThat(variants.get(0).hashCode()).isNotEqualTo(variants.get(1).hashCode());
        assertThat(variants.get(0)).isEqualTo(testCaseWithFlags().expandVariants().get(0));
    }

    @Test
    void testModuleFlagBeatsRawFlag() {
        // `raw` says "no harness, no modification"; `module` names the parse goal. A file carrying
        // both is module source, and compiling it as a script fails on its own import declarations.
        assertThat(variantsOf("module", "raw")).containsExactly(Test262TestCase.Variant.MODULE);
    }

    @Test
    void testModuleFlagRunsOnce() {
        assertThat(variantsOf("module")).containsExactly(Test262TestCase.Variant.MODULE);
    }

    @Test
    void testNoStrictFlagRunsNonStrictOnly() {
        assertThat(variantsOf("noStrict")).containsExactly(Test262TestCase.Variant.NON_STRICT);
    }

    @Test
    void testOnlyStrictFlagRunsStrictOnly() {
        assertThat(variantsOf("onlyStrict")).containsExactly(Test262TestCase.Variant.STRICT);
    }

    @Test
    void testRawFlagRunsOnce() {
        assertThat(variantsOf("raw")).containsExactly(Test262TestCase.Variant.RAW);
    }

    @Test
    void testVariantsShareParsedMetadata() {
        Test262TestCase parsed = testCaseWithFlags();
        parsed.setIndex(7);
        parsed.setNegative(new Test262TestCase.NegativeInfo("parse", "SyntaxError"));
        for (Test262TestCase variant : parsed.expandVariants()) {
            assertThat(variant.getCode()).isEqualTo(parsed.getCode());
            assertThat(variant.getIndex()).isEqualTo(7);
            assertThat(variant.getNegative()).isSameAs(parsed.getNegative());
            assertThat(variant.getPath()).isEqualTo(parsed.getPath());
        }
    }

    @Test
    void testVariantsShowInDisplayName() {
        List<Test262TestCase> variants = testCaseWithFlags().expandVariants();
        assertThat(variants.get(0).toString()).doesNotContain("[");
        assertThat(variants.get(1).toString()).endsWith("[strict]");
        assertThat(testCaseWithFlags("module").expandVariants().get(0).toString()).endsWith("[module]");
        assertThat(testCaseWithFlags("raw").expandVariants().get(0).toString()).endsWith("[raw]");
    }

    private List<Test262TestCase.Variant> variantsOf(String... flags) {
        return testCaseWithFlags(flags).expandVariants().stream()
                .map(Test262TestCase::getVariant)
                .toList();
    }
}
