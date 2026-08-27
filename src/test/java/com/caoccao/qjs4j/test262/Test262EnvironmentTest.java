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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The premises a conformance run states before it starts: which revision of the suite is on disk,
 * and which zone the host will read dates in.
 * <p>
 * Both used to live in one CI workflow, which meant the documented Gradle command was reproducible
 * only by reverse-engineering that file. These cases fabricate checkouts on disk rather than
 * running Git, because that is what the runner does — a container can have the files without the
 * tool, and shelling out would answer differently there.
 */
public class Test262EnvironmentTest {
    private static final String PINNED = "5c8206929d81b2d3d727ca6aac56c18358c8d790";

    @TempDir
    Path workingDirectory;

    private Path gitDirectory() throws IOException {
        Path gitDirectory = workingDirectory.resolve(".git");
        Files.createDirectories(gitDirectory);
        return gitDirectory;
    }

    @Test
    public void testACheckoutAtAnotherRevisionIsRefusedAndSaysWhichOne() throws IOException {
        write(gitDirectory().resolve("HEAD"), "0123456789abcdef0123456789abcdef01234567\n");

        List<Test262Environment.Diagnostic> diagnostics =
                Test262Environment.check(workingDirectory, PINNED, "UTC");

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.get(0).fatal())
                .as("counts from another revision are not comparable, so the run is refused")
                .isTrue();
        assertThat(diagnostics.get(0).message())
                .contains("0123456789abcdef0123456789abcdef01234567")
                .contains(PINNED)
                .contains("-Ptest262AllowAnyRevision=true");
    }

    @Test
    public void testACheckoutAtThePinnedRevisionHasNothingToReport() throws IOException {
        write(gitDirectory().resolve("HEAD"), PINNED + "\n");
        assertThat(Test262Environment.check(workingDirectory, PINNED, "UTC")).isEmpty();
        // Git writes lower case; a revision typed in upper case names the same commit.
        assertThat(Test262Environment.check(workingDirectory, PINNED.toUpperCase(), "UTC")).isEmpty();
    }

    @Test
    public void testASuiteWithNoHistoryIsQualifiedRatherThanRefused() {
        // A suite unpacked from an archive has no revision to read. That run cannot be reported as
        // the pinned one, but it is still worth running, and saying so is not the same as the
        // silent acceptance this replaced.
        List<Test262Environment.Diagnostic> diagnostics =
                Test262Environment.check(workingDirectory, PINNED, "UTC");

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.get(0).fatal()).isFalse();
        assertThat(diagnostics.get(0).message())
                .contains("Cannot tell which revision")
                .contains(PINNED);
    }

    @Test
    public void testAnUnusableTimeZoneIsReportedWithoutRefusingTheRun() {
        // The pinned harness rejects exactly these two identifiers. A few intl402 interpretations
        // fail because of it, which is worth saying — and is not a reason to refuse to run the
        // other hundred thousand.
        for (String timeZoneId : List.of("Etc/UTC", "Etc/GMT")) {
            List<Test262Environment.Diagnostic> diagnostics =
                    Test262Environment.check(workingDirectory, null, timeZoneId);
            assertThat(diagnostics).as(timeZoneId).hasSize(1);
            assertThat(diagnostics.get(0).fatal()).isFalse();
            assertThat(diagnostics.get(0).message())
                    .contains(timeZoneId)
                    .contains("-Duser.timezone=UTC");
        }
        // GMT is not Etc/GMT, and the harness accepts it.
        assertThat(Test262Environment.check(workingDirectory, null, "GMT")).isEmpty();
        assertThat(Test262Environment.check(workingDirectory, null, "UTC")).isEmpty();
        assertThat(Test262Environment.check(workingDirectory, null, "Asia/Shanghai")).isEmpty();
    }

    @Test
    public void testCheckoutRevisionFollowsABranchThroughItsLooseReference() throws IOException {
        Path gitDirectory = gitDirectory();
        write(gitDirectory.resolve("HEAD"), "ref: refs/heads/main\n");
        write(gitDirectory.resolve("refs/heads/main"), PINNED + "\n");
        assertThat(Test262Environment.checkoutRevision(workingDirectory)).isEqualTo(PINNED);
    }

    @Test
    public void testCheckoutRevisionFollowsABranchThroughPackedReferences() throws IOException {
        // A freshly cloned repository packs its references, so the file the branch name points at
        // is not there — which is the ordinary shape of a checkout, not an edge case.
        Path gitDirectory = gitDirectory();
        write(gitDirectory.resolve("HEAD"), "ref: refs/heads/main\n");
        write(gitDirectory.resolve("packed-refs"), """
                # pack-refs with: peeled fully-peeled sorted
                0123456789abcdef0123456789abcdef01234567 refs/heads/other
                """ + PINNED + " refs/heads/main\n"
                + "aaaabbbbccccddddeeeeffff00001111222233334 refs/tags/v1\n"
                + "^bbbbccccddddeeeeffff000011112222333344445\n");
        assertThat(Test262Environment.checkoutRevision(workingDirectory)).isEqualTo(PINNED);
    }

    @Test
    public void testCheckoutRevisionFollowsALinkedWorktree() throws IOException {
        // A submodule or a linked worktree has a .git file rather than a directory.
        Path linkedDirectory = workingDirectory.resolve("elsewhere/modules/test262");
        write(linkedDirectory.resolve("HEAD"), PINNED + "\n");
        write(workingDirectory.resolve(".git"), "gitdir: elsewhere/modules/test262\n");
        assertThat(Test262Environment.checkoutRevision(workingDirectory)).isEqualTo(PINNED);

        write(workingDirectory.resolve(".git"), "gitdir: " + linkedDirectory + "\n");
        assertThat(Test262Environment.checkoutRevision(workingDirectory)).isEqualTo(PINNED);
    }

    @Test
    public void testCheckoutRevisionReadsADetachedHead() throws IOException {
        // What a pinned CI checkout looks like: HEAD holds the revision itself.
        write(gitDirectory().resolve("HEAD"), PINNED + "\n");
        assertThat(Test262Environment.checkoutRevision(workingDirectory)).isEqualTo(PINNED);
    }

    @Test
    public void testCheckoutRevisionSaysNothingRatherThanGuessing() throws IOException {
        // Nothing here is an error worth throwing over; each one means "this is not a checkout I
        // can read", and the caller decides what that is worth.
        assertThat(Test262Environment.checkoutRevision(workingDirectory))
                .as("no .git at all").isNull();

        write(workingDirectory.resolve(".git"), "not a gitdir pointer\n");
        assertThat(Test262Environment.checkoutRevision(workingDirectory))
                .as("a .git file that points nowhere").isNull();

        Files.delete(workingDirectory.resolve(".git"));
        Path gitDirectory = gitDirectory();
        assertThat(Test262Environment.checkoutRevision(workingDirectory))
                .as("a .git directory with no HEAD").isNull();

        write(gitDirectory.resolve("HEAD"), "\n");
        assertThat(Test262Environment.checkoutRevision(workingDirectory))
                .as("an empty HEAD").isNull();

        write(gitDirectory.resolve("HEAD"), "ref:\n");
        assertThat(Test262Environment.checkoutRevision(workingDirectory))
                .as("a symbolic HEAD naming nothing").isNull();

        write(gitDirectory.resolve("HEAD"), "ref: refs/heads/main\n");
        assertThat(Test262Environment.checkoutRevision(workingDirectory))
                .as("a branch with neither a loose nor a packed reference").isNull();

        write(gitDirectory.resolve("packed-refs"), "# pack-refs with: peeled\n^0123\n\n");
        assertThat(Test262Environment.checkoutRevision(workingDirectory))
                .as("packed references that do not include this branch").isNull();
    }

    @Test
    public void testPinnedRevisionIgnoresCommentsAndBlankLines() throws IOException {
        // The file is the one place the revision is written down, so it has to be able to say why.
        write(workingDirectory.resolve(Test262Environment.REVISION_FILE_NAME), """
                # The revision this repository measures itself against.
                
                """ + PINNED + "\n");
        assertThat(Test262Environment.pinnedRevision(workingDirectory)).isEqualTo(PINNED);
    }

    @Test
    public void testPinnedRevisionIsAbsentWhenNothingConfiguresIt() throws IOException {
        assertThat(Test262Environment.pinnedRevision(workingDirectory)).isNull();
        write(workingDirectory.resolve(Test262Environment.REVISION_FILE_NAME), "# only a comment\n");
        assertThat(Test262Environment.pinnedRevision(workingDirectory)).isNull();
    }

    @Test
    public void testWithNothingPinnedThereIsNothingToEnforce() throws IOException {
        // A caller that has not said which revision it expects cannot be told it has the wrong one.
        write(gitDirectory().resolve("HEAD"), "0123456789abcdef0123456789abcdef01234567\n");
        assertThat(Test262Environment.check(workingDirectory, null, "UTC")).isEmpty();
        assertThat(Test262Environment.check(workingDirectory, "", "UTC")).isEmpty();
    }

    private void write(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents, StandardCharsets.UTF_8);
    }
}
