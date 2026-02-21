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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a single test262 test case with its metadata and code.
 */
public class Test262TestCase {
    private String code;
    private String description;
    private String esid;
    private Set<String> features = new HashSet<>();
    private Set<String> flags = new HashSet<>();
    private Set<String> includes = new HashSet<>();
    private NegativeInfo negative;
    private Path path;

    public Test262TestCase() {
    }

    public Test262TestCase(Path path) {
        this.path = path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        Test262TestCase that = (Test262TestCase) o;
        return Objects.equals(path, that.path);
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

    public NegativeInfo getNegative() {
        return negative;
    }

    public Path getPath() {
        return path;
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
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

    public void setNegative(NegativeInfo negative) {
        this.negative = negative;
    }

    public void setPath(Path path) {
        this.path = path;
    }

    @Override
    public String toString() {
        return path != null ? path.toString() : "unknown";
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
