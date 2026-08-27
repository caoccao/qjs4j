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

    /**
     * Run something with the revision check waived, exactly as {@code -Ptest262AllowAnyRevision}
     * does, and put the property back afterwards.
     *
     * @param body what to run
     */
    private static void withRevisionCheckWaived(Runnable body) {
        String previous = System.getProperty(Test262Environment.ALLOW_ANY_REVISION_PROPERTY);
        System.setProperty(Test262Environment.ALLOW_ANY_REVISION_PROPERTY, "true");
        try {
            body.run();
        } finally {
            if (previous == null) {
                System.clearProperty(Test262Environment.ALLOW_ANY_REVISION_PROPERTY);
            } else {
                System.setProperty(Test262Environment.ALLOW_ANY_REVISION_PROPERTY, previous);
            }
        }
    }

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
    public void testASuiteWhoseRevisionCannotBeReadIsRefused() {
        // A suite unpacked from an archive has no revision to read. It may well be the pinned one,
        // but nothing here can say so — and the whole point of the check is that a green run means
        // "the pinned suite passed". This was a warning, printed on standard error into a log
        // nobody reads when the build is green, while the count it qualified went on being quoted
        // as the pinned one. The override below is how a suite without history is run deliberately.
        List<Test262Environment.Diagnostic> diagnostics =
                Test262Environment.check(workingDirectory, PINNED, "UTC");

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.get(0).fatal()).isTrue();
        assertThat(diagnostics.get(0).message())
                .contains("Cannot tell which revision")
                .contains(PINNED)
                .contains("-Ptest262AllowAnyRevision=true");
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
    public void testCheckoutRevisionFollowsALinkedWorktreeOnABranch() throws IOException {
        // The shape the previous case did not have: a linked worktree whose HEAD is symbolic. Its
        // HEAD is per-worktree and lives in .git/worktrees/<name>/, but its branches are not —
        // those are in the repository's common directory, named by the commondir file beside that
        // HEAD. Looking only beside HEAD resolved a detached worktree and gave up on this one,
        // which is the ordinary shape of a worktree, so the run could not say what it was testing.
        Path worktreeDirectory = workingDirectory.resolve("main/.git/worktrees/conformance");
        Path commonDirectory = workingDirectory.resolve("main/.git");
        write(workingDirectory.resolve(".git"), "gitdir: main/.git/worktrees/conformance\n");
        write(worktreeDirectory.resolve("HEAD"), "ref: refs/heads/pinned\n");
        write(worktreeDirectory.resolve("commondir"), "../..\n");
        write(commonDirectory.resolve("refs/heads/pinned"), PINNED + "\n");
        assertThat(Test262Environment.checkoutRevision(workingDirectory))
                .as("a loose reference in the common directory")
                .isEqualTo(PINNED);

        // And when that repository has packed its references instead.
        Files.delete(commonDirectory.resolve("refs/heads/pinned"));
        write(commonDirectory.resolve("packed-refs"),
                "# pack-refs with: peeled fully-peeled sorted\n" + PINNED + " refs/heads/pinned\n");
        assertThat(Test262Environment.checkoutRevision(workingDirectory))
                .as("a packed reference in the common directory")
                .isEqualTo(PINNED);

        // Git writes commondir relative; an absolute one names the same directory.
        write(worktreeDirectory.resolve("commondir"), commonDirectory + "\n");
        assertThat(Test262Environment.checkoutRevision(workingDirectory)).isEqualTo(PINNED);

        // A per-worktree reference beside HEAD still wins over the common directory, which is what
        // Git does: HEAD, and the bisect and rebase references, belong to this worktree alone.
        write(worktreeDirectory.resolve("refs/heads/pinned"), "0123456789abcdef0123456789abcdef01234567\n");
        assertThat(Test262Environment.checkoutRevision(workingDirectory))
                .isEqualTo("0123456789abcdef0123456789abcdef01234567");
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
    public void testDescribeStatesTheRevisionAndZoneOfEveryRun() throws IOException {
        // The successful log used to print the suite root and nothing else, so a count copied out
        // of it — or a CI artifact downloaded once the workflow run had scrolled away — could not
        // say which suite produced it. A count without a revision is not comparable with anything.
        assertThat(Test262Environment.describe(workingDirectory, PINNED, "UTC").lines())
                .as("a suite with no readable history says so rather than guessing")
                .containsExactly(
                        "Test262 revision: unknown (not the pinned " + PINNED + ")",
                        "Test262 time zone: UTC");

        write(gitDirectory().resolve("HEAD"), PINNED + "\n");
        assertThat(Test262Environment.describe(workingDirectory, PINNED, "UTC").lines())
                .containsExactly(
                        "Test262 revision: " + PINNED + " (pinned)",
                        "Test262 time zone: UTC");

        write(gitDirectory().resolve("HEAD"), "0123456789abcdef0123456789abcdef01234567\n");
        assertThat(Test262Environment.describe(workingDirectory, PINNED, "Asia/Shanghai").lines())
                .containsExactly(
                        "Test262 revision: 0123456789abcdef0123456789abcdef01234567 (not the pinned "
                                + PINNED + ")",
                        "Test262 time zone: Asia/Shanghai");

        assertThat(Test262Environment.describe(workingDirectory, null, "UTC").lines())
                .containsExactly(
                        "Test262 revision: 0123456789abcdef0123456789abcdef01234567 (nothing pinned)",
                        "Test262 time zone: UTC");
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
    public void testWaivingTheRevisionCheckStillReportsTheRevision() throws IOException {
        // The override exists for running against upstream tip on purpose, which is the case most
        // in need of a revision in the log — and the one that used to report none, because the
        // diagnostic path returns before reading one and the log printed only what it produced.
        write(gitDirectory().resolve("HEAD"), "0123456789abcdef0123456789abcdef01234567\n");
        withRevisionCheckWaived(() -> {
            assertThat(Test262Environment.check(workingDirectory, PINNED, "UTC"))
                    .as("the wrong revision is what was asked for, so there is nothing to report")
                    .isEmpty();
            assertThat(Test262Environment.describe(workingDirectory, PINNED, "UTC").lines())
                    .containsExactly(
                            "Test262 revision: 0123456789abcdef0123456789abcdef01234567"
                                    + " (revision check waived, pinned " + PINNED + ")",
                            "Test262 time zone: UTC");
        });
        assertThat(Test262Environment.check(workingDirectory, PINNED, "UTC"))
                .as("and the waiver lasts no longer than it was asked for")
                .hasSize(1);
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
