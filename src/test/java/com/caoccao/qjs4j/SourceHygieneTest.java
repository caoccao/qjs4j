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

package com.caoccao.qjs4j;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Java source is text, and tooling is entitled to assume so.
 * <p>
 * A NUL byte is legal inside a Java character literal, and {@code javac} accepts it without comment.
 * Everything else does not: {@code grep} and {@code ripgrep} classify a file containing one as
 * binary and stop printing matches, {@code git diff} refuses to show it, and review tools show
 * nothing useful. Four of them reached {@code JSContext.java} — the engine's largest and most
 * frequently read file — written as the delimiter of a two-field key, which is exactly the kind of
 * change a reviewer would want to be able to search for.
 * <p>
 * The delimiter is gone (binding identity is a record now), but nothing stopped the next one, so
 * this does. It is a repository check rather than an engine check, which is why it asserts about
 * files rather than about behaviour.
 */
public class SourceHygieneTest {
    /**
     * Every {@code .java} file in the repository's own source trees.
     *
     * @return the source files, or an empty list when the trees are not where this expects
     */
    private static List<Path> javaSourceFiles() {
        List<Path> sourceFiles = new ArrayList<>();
        for (String sourceRoot : List.of("src/main/java", "src/test/java")) {
            Path root = Paths.get(sourceRoot);
            if (!Files.isDirectory(root)) {
                return List.of();
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .forEach(sourceFiles::add);
            } catch (IOException ioException) {
                throw new UncheckedIOException(ioException);
            }
        }
        return sourceFiles;
    }

    @Test
    public void testNoJavaSourceFileContainsANulByte() throws IOException {
        List<Path> sourceFiles = javaSourceFiles();
        // The working directory is the project directory under Gradle. If some other runner puts it
        // elsewhere, this check has nothing to say rather than something wrong.
        assumeTrue(!sourceFiles.isEmpty(), "source trees are not under the working directory");

        List<String> offenders = new ArrayList<>();
        for (Path sourceFile : sourceFiles) {
            byte[] content = Files.readAllBytes(sourceFile);
            for (int index = 0; index < content.length; index++) {
                if (content[index] == 0) {
                    offenders.add(sourceFile + " at byte " + index);
                    break;
                }
            }
        }
        assertThat(offenders)
                .as("a NUL byte makes a source file read as binary to ordinary search tooling; "
                        + "write '\\0' or \"\\u0000\" instead")
                .isEmpty();
    }
}
