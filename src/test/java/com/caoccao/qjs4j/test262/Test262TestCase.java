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

import java.nio.file.Path;
import java.util.*;

/**
 * Represents a single test262 test case with its metadata and code.
 * <p>
 * A parsed file is not by itself an executable unit. Test262's {@code INTERPRETING.md} defines
 * how many <em>interpretations</em> a file has and what each one is: an ordinary file is executed
 * twice, once as sloppy-mode script source and once with a {@code "use strict"} prologue prepended,
 * while {@code onlyStrict}, {@code noStrict}, {@code module} and {@code raw} each define exactly
 * one. {@link #expandVariants()} turns a parsed file into those units, and {@link #getVariant()}
 * says which one an instance is, so a result is attributable to an interpretation rather than to a
 * path.
 */
public class Test262TestCase {
    private String code;
    private String description;
    private String esid;
    private Set<String> features = new HashSet<>();
    private Set<String> flags = new HashSet<>();
    private Set<String> includes = new HashSet<>();
    private int index;
    private NegativeInfo negative;
    private Path path;
    private long timeElapsed;
    private Variant variant = Variant.NON_STRICT;

    public Test262TestCase(Path path) {
        this.path = path;
    }

    private Test262TestCase copyWithVariant(Variant newVariant) {
        Test262TestCase copy = new Test262TestCase(path);
        copy.code = code;
        copy.description = description;
        copy.esid = esid;
        copy.features = features;
        copy.flags = flags;
        copy.includes = includes;
        copy.index = index;
        copy.negative = negative;
        copy.variant = newVariant;
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Test262TestCase that = (Test262TestCase) o;
        return Objects.equals(path, that.path) && variant == that.variant;
    }

    /**
     * Expand this parsed file into the execution variants Test262 requires for it.
     * <p>
     * The returned cases share this instance's parsed metadata and source; they differ only in
     * {@link #getVariant()}, which the executor turns into a strict prologue, a module evaluation
     * or a harness-free raw evaluation.
     *
     * @return one case per required interpretation, never empty
     */
    public List<Test262TestCase> expandVariants() {
        List<Test262TestCase> variants = new ArrayList<>(2);
        // `module` is checked before `raw` because the two are not alternatives: `module` names the
        // parse goal and `raw` only says the source is evaluated with no harness and no
        // modification. A file flagged `[module, raw]` is module source, and compiling it as a
        // script fails on its own import declarations.
        if (hasFlag("module")) {
            variants.add(copyWithVariant(Variant.MODULE));
        } else if (hasFlag("raw")) {
            variants.add(copyWithVariant(Variant.RAW));
        } else if (hasFlag("onlyStrict")) {
            variants.add(copyWithVariant(Variant.STRICT));
        } else if (hasFlag("noStrict")) {
            variants.add(copyWithVariant(Variant.NON_STRICT));
        } else {
            variants.add(copyWithVariant(Variant.NON_STRICT));
            variants.add(copyWithVariant(Variant.STRICT));
        }
        return variants;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getEsid() {
        return esid;
    }

    public Set<String> getFeatures() {
        return features;
    }

    public Set<String> getFlags() {
        return flags;
    }

    public Set<String> getIncludes() {
        return includes;
    }

    public int getIndex() {
        return index;
    }

    public NegativeInfo getNegative() {
        return negative;
    }

    public Path getPath() {
        return path;
    }

    public long getTimeElapsed() {
        return timeElapsed;
    }

    /**
     * The interpretation this case represents.
     *
     * @return the variant, never {@code null}
     */
    public Variant getVariant() {
        return variant;
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, variant);
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setEsid(String esid) {
        this.esid = esid;
    }

    public void setFeatures(Set<String> features) {
        this.features = features;
    }

    public void setFlags(Set<String> flags) {
        this.flags = flags;
    }

    public void setIncludes(Set<String> includes) {
        this.includes = includes;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void setNegative(NegativeInfo negative) {
        this.negative = negative;
    }

    public void setPath(Path path) {
        this.path = path;
    }

    public void setTimeElapsed(long timeElapsed) {
        this.timeElapsed = timeElapsed;
    }

    public void setVariant(Variant variant) {
        this.variant = Objects.requireNonNull(variant);
    }

    @Override
    public String toString() {
        String pathText = path != null ? path.toString() : "unknown";
        return variant == Variant.NON_STRICT ? pathText : pathText + " [" + variant.label() + "]";
    }

    /**
     * The interpretations a Test262 file can have.
     */
    public enum Variant {
        /**
         * The file's source, evaluated as sloppy-mode script source.
         */
        NON_STRICT("non-strict"),
        /**
         * The file's source with a {@code "use strict";} prologue prepended, evaluated as script
         * source. Required for every file that is not {@code noStrict}, {@code module} or
         * {@code raw}.
         */
        STRICT("strict"),
        /**
         * The file's source evaluated as module source. Module code is always strict, so this is
         * the only interpretation a {@code module} file has.
         */
        MODULE("module"),
        /**
         * The file's source evaluated with no harness and no modification.
         */
        RAW("raw");

        private final String label;

        Variant(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * Represents negative test metadata.
     */
    public static class NegativeInfo {
        private String phase;
        private String type;

        public NegativeInfo() {
        }

        public NegativeInfo(String phase, String type) {
            this.phase = phase;
            this.type = type;
        }

        public String getPhase() {
            return phase;
        }

        public String getType() {
            return type;
        }

        public void setPhase(String phase) {
            this.phase = phase;
        }

        public void setType(String type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return String.format("NegativeInfo{phase='%s', type='%s'}", phase, type);
        }
    }
}
