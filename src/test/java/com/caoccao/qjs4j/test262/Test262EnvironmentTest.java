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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The premises a conformance run states before it starts: which revision of the suite is on disk,
 * whether it still holds that revision's files, and which zone the host will read dates in.
 * <p>
 * The first and the third used to live in one CI workflow, which meant the documented Gradle command
 * was reproducible only by reverse-engineering that file. Most of these cases fabricate checkouts on
 * disk rather than running Git, because that is what the revision reader does — a container can have
 * the files without the tool, and shelling out would answer differently there. Cleanliness is the
 * one premise that does ask Git, so the cases for it either supply the answer through the reader
 * seam or build a real repository and check what Git actually says.
 */
public class Test262EnvironmentTest {
    private static final String OTHER = "0123456789abcdef0123456789abcdef01234567";
    private static final String PINNED = "5c8206929d81b2d3d727ca6aac56c18358c8d790";

    @TempDir
    Path workingDirectory;

    /**
     * A reader standing for a checkout holding exactly the files its revision names.
     *
     * @return the reader
     */
    private static Test262Environment.WorktreeStatusReader clean() {
        return root -> Test262Environment.WorktreeStatus.clean();
    }

    /**
     * A reader that fails the test if the run asks it anything.
     * <p>
     * Used where the answer cannot matter: a suite whose revision cannot be read is already refused,
     * and a run with the check waived has said it does not care. Asking Git there is a subprocess
     * spent on a question nobody will read the answer to.
     *
     * @return the reader
     */
    private static Test262Environment.WorktreeStatusReader neverAsked() {
        return root -> {
            throw new AssertionError("the worktree should not have been inspected for " + root);
        };
    }

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

    /**
     * A repository with one committed harness file and one committed test, or no repository at all
     * when this host has no Git to build one with.
     *
     * @return the checkout root
     * @throws IOException if the files cannot be written
     */
    private Path committedCheckout() throws IOException {
        Path checkout = workingDirectory.resolve("test262");
        Files.createDirectories(checkout);
        assumeTrue(git(checkout, "init", "--quiet") == 0, "git is not available on this host");
        write(checkout.resolve("harness/assert.js"), "function assert() {}\n");
        write(checkout.resolve("test/language/example.js"), "assert(true);\n");
        assertThat(git(checkout, "add", ".")).isZero();
        assertThat(git(
                checkout,
                "-c", "user.name=qjs4j",
                "-c", "user.email=qjs4j@example.com",
                "-c", "commit.gpgsign=false",
                "commit", "--quiet", "-m", "pinned"))
                .isZero();
        return checkout;
    }

    /**
     * Run Git in a checkout, discarding what it says.
     *
     * @param directory where to run it
     * @param arguments what to run
     * @return the exit status, or -1 when Git could not be run at all
     * @throws IOException if waiting for it fails
     */
    private int git(Path directory, String... arguments) throws IOException {
        List<String> command = new ArrayList<>(List.of("git", "-C", directory.toString()));
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            try (InputStream stream = process.getInputStream()) {
                // Drained so the process cannot block on a full pipe; nothing here reads it.
                stream.transferTo(OutputStream.nullOutputStream());
            }
            return process.waitFor();
        } catch (IOException unrunnable) {
            return -1;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException(interrupted);
        }
    }

    private Path gitDirectory() throws IOException {
        Path gitDirectory = workingDirectory.resolve(".git");
        Files.createDirectories(gitDirectory);
        return gitDirectory;
    }

    @Test
    public void testACheckoutAtAnotherRevisionIsRefusedAndSaysWhichOne() throws IOException {
        write(gitDirectory().resolve("HEAD"), OTHER + "\n");

        List<Test262Environment.Diagnostic> diagnostics =
                Test262Environment.check(workingDirectory, PINNED, "UTC", neverAsked());

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.get(0).fatal())
                .as("counts from another revision are not comparable, so the run is refused")
                .isTrue();
        assertThat(diagnostics.get(0).message())
                .contains(OTHER)
                .contains(PINNED)
                .contains("-Ptest262AllowAnyRevision=true");
    }

    @Test
    public void testACheckoutAtThePinnedRevisionHasNothingToReport() throws IOException {
        write(gitDirectory().resolve("HEAD"), PINNED + "\n");
        assertThat(Test262Environment.check(workingDirectory, PINNED, "UTC", clean())).isEmpty();
        // Git writes lower case; a revision typed in upper case names the same commit.
        assertThat(Test262Environment.check(workingDirectory, PINNED.toUpperCase(), "UTC", clean()))
                .isEmpty();
    }

    @Test
    public void testACheckoutEditedSinceThePinnedRevisionIsRefused() throws IOException {
        // Naming the right commit is not executing it: discovery walks the working tree, so an
        // untracked .js file adds an interpretation the pinned suite does not contain and an edited
        // harness file changes the outcome of thousands — while HEAD goes on naming the pin. This
        // used to pass, and the count it produced was quoted as the pinned baseline.
        write(gitDirectory().resolve("HEAD"), PINNED + "\n");

        List<Test262Environment.Diagnostic> diagnostics = Test262Environment.check(
                workingDirectory,
                PINNED,
                "UTC",
                root -> Test262Environment.WorktreeStatus.modified("M harness/assert.js"));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.get(0).fatal()).isTrue();
        assertThat(diagnostics.get(0).message())
                .contains("has been edited")
                .contains("M harness/assert.js")
                .contains("-Ptest262AllowAnyRevision=true");
    }

    @Test
    public void testACheckoutWhoseStateCannotBeEstablishedIsRefused() throws IOException {
        // The same fail-closed rule an unreadable revision follows. Not knowing whether the suite
        // has been edited is not evidence that it has not been.
        write(gitDirectory().resolve("HEAD"), PINNED + "\n");

        List<Test262Environment.Diagnostic> diagnostics = Test262Environment.check(
                workingDirectory,
                PINNED,
                "UTC",
                root -> Test262Environment.WorktreeStatus.unknown("git could not be run: no such file"));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.get(0).fatal()).isTrue();
        assertThat(diagnostics.get(0).message())
                .contains("Cannot tell whether")
                .contains("git could not be run: no such file")
                .contains("-Ptest262AllowAnyRevision=true");
    }

    @Test
    public void testASuiteWhoseRevisionCannotBeReadIsRefused() {
        // A suite unpacked from an archive has no revision to read. It may well be the pinned one,
        // but nothing here can say so — and the whole point of the check is that a green run means
        // "the pinned suite passed". This was a warning, printed on standard error into a log
        // nobody reads when the build is green, while the count it qualified went on being quoted
        // as the pinned one. The override below is how a suite without history is run deliberately.
        List<Test262Environment.Diagnostic> diagnostics =
                Test262Environment.check(workingDirectory, PINNED, "UTC", neverAsked());

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.get(0).fatal()).isTrue();
        assertThat(diagnostics.get(0).message())
                .contains("Cannot tell which revision")
                .contains(PINNED)
                .contains("-Ptest262AllowAnyRevision=true");
    }

    @Test
    public void testAnUnusableTimeZoneIsReportedWithoutRefusingTheRun() throws IOException {
        // The pinned harness rejects exactly these two identifiers. A few intl402 interpretations
        // fail because of it, which is worth saying — and is not a reason to refuse to run the
        // other hundred thousand.
        write(gitDirectory().resolve("HEAD"), PINNED + "\n");
        for (String timeZoneId : List.of("Etc/UTC", "Etc/GMT")) {
            List<Test262Environment.Diagnostic> diagnostics =
                    Test262Environment.check(workingDirectory, PINNED, timeZoneId, clean());
            assertThat(diagnostics).as(timeZoneId).hasSize(1);
            assertThat(diagnostics.get(0).fatal()).isFalse();
            assertThat(diagnostics.get(0).message())
                    .contains(timeZoneId)
                    .contains("-Duser.timezone=UTC");
        }
        // GMT is not Etc/GMT, and the harness accepts it.
        assertThat(Test262Environment.check(workingDirectory, PINNED, "GMT", clean())).isEmpty();
        assertThat(Test262Environment.check(workingDirectory, PINNED, "UTC", clean())).isEmpty();
        assertThat(Test262Environment.check(workingDirectory, PINNED, "Asia/Shanghai", clean()))
                .isEmpty();
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
    }

    @Test
    public void testCheckoutRevisionReadsADetachedHead() throws IOException {
        // What a pinned CI checkout looks like: HEAD holds the revision itself.
        write(gitDirectory().resolve("HEAD"), PINNED + "\n");
        assertThat(Test262Environment.checkoutRevision(workingDirectory)).isEqualTo(PINNED);
    }

    @Test
    public void testCheckoutRevisionResolvesWorktreeReferencesTheWayGitDoes() throws IOException {
        // refs/heads/ is common, not per-worktree: when commondir is present Git ignores a file of
        // that name beside the worktree's HEAD. Reading it first — which this used to do, and had a
        // case asserting it — let a stale or hand-made file make the run report a revision the
        // worktree does not have checked out, which is the disagreement with Git this parser exists
        // to avoid.
        Path worktreeDirectory = workingDirectory.resolve("main/.git/worktrees/conformance");
        Path commonDirectory = workingDirectory.resolve("main/.git");
        write(workingDirectory.resolve(".git"), "gitdir: main/.git/worktrees/conformance\n");
        write(worktreeDirectory.resolve("HEAD"), "ref: refs/heads/pinned\n");
        write(worktreeDirectory.resolve("commondir"), "../..\n");
        write(commonDirectory.resolve("refs/heads/pinned"), PINNED + "\n");
        write(worktreeDirectory.resolve("refs/heads/pinned"), OTHER + "\n");
        assertThat(Test262Environment.checkoutRevision(workingDirectory))
                .as("a branch beside the worktree HEAD is a file Git ignores")
                .isEqualTo(PINNED);

        // The namespaces that really are per-worktree are read beside HEAD, which is where Git
        // keeps them: refs/bisect/, refs/rewritten/ and refs/worktree/.
        write(worktreeDirectory.resolve("HEAD"), "ref: refs/worktree/pinned\n");
        write(worktreeDirectory.resolve("refs/worktree/pinned"), PINNED + "\n");
        write(commonDirectory.resolve("refs/worktree/pinned"), OTHER + "\n");
        assertThat(Test262Environment.checkoutRevision(workingDirectory))
                .as("refs/worktree/ belongs to this worktree alone")
                .isEqualTo(PINNED);

        write(worktreeDirectory.resolve("HEAD"), "ref: refs/bisect/bad\n");
        write(worktreeDirectory.resolve("refs/bisect/bad"), PINNED + "\n");
        assertThat(Test262Environment.checkoutRevision(workingDirectory)).isEqualTo(PINNED);

        write(worktreeDirectory.resolve("HEAD"), "ref: refs/rewritten/onto\n");
        write(worktreeDirectory.resolve("refs/rewritten/onto"), PINNED + "\n");
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

        write(gitDirectory().resolve("HEAD"), OTHER + "\n");
        assertThat(Test262Environment.describe(workingDirectory, PINNED, "Asia/Shanghai").lines())
                .containsExactly(
                        "Test262 revision: " + OTHER + " (not the pinned " + PINNED + ")",
                        "Test262 time zone: Asia/Shanghai");

        assertThat(Test262Environment.describe(workingDirectory, null, "UTC").lines())
                .containsExactly(
                        "Test262 revision: " + OTHER + " (nothing pinned)",
                        "Test262 time zone: UTC");
    }

    @Test
    public void testGitWorktreeStatusSaysItDoesNotKnowRatherThanGuessing() throws IOException {
        // A directory with no repository in it, and a path with nothing at it at all. Neither is
        // evidence that the suite is unedited, so neither may be reported as clean.
        Path notARepository = workingDirectory.resolve("archive");
        Files.createDirectories(notARepository);
        assumeTrue(git(workingDirectory, "--version") == 0, "git is not available on this host");

        Test262Environment.WorktreeStatus archive = Test262Environment.gitWorktreeStatus(notARepository);
        assertThat(archive.state()).isEqualTo(Test262Environment.WorktreeStatus.State.UNKNOWN);
        assertThat(archive.detail()).isNotEmpty();

        Test262Environment.WorktreeStatus missing =
                Test262Environment.gitWorktreeStatus(workingDirectory.resolve("nowhere"));
        assertThat(missing.state()).isEqualTo(Test262Environment.WorktreeStatus.State.UNKNOWN);
        assertThat(missing.detail()).isNotEmpty();
    }

    @Test
    public void testGitWorktreeStatusSeesEveryKindOfEdit() throws IOException {
        // Against a real repository, because a partial reimplementation of `git status` would be
        // wrong in exactly the quiet ways that make a conformance count wrong. Each of these is a
        // way the executed suite stops being the committed one while HEAD goes on naming it.
        Path checkout = committedCheckout();
        assertThat(Test262Environment.gitWorktreeStatus(checkout).state())
                .as("what CI checks out")
                .isEqualTo(Test262Environment.WorktreeStatus.State.CLEAN);

        write(checkout.resolve("test/language/example.js"), "assert(false);\n");
        Test262Environment.WorktreeStatus modified = Test262Environment.gitWorktreeStatus(checkout);
        assertThat(modified.state()).isEqualTo(Test262Environment.WorktreeStatus.State.MODIFIED);
        assertThat(modified.detail()).contains("test/language/example.js");
        assertThat(git(checkout, "checkout", "--", ".")).isZero();

        write(checkout.resolve("harness/assert.js"), "function assert() { throw 1; }\n");
        assertThat(Test262Environment.gitWorktreeStatus(checkout).detail())
                .as("one harness file changes the outcome of thousands of interpretations")
                .contains("harness/assert.js");
        assertThat(git(checkout, "checkout", "--", ".")).isZero();

        Files.delete(checkout.resolve("test/language/example.js"));
        assertThat(Test262Environment.gitWorktreeStatus(checkout).state())
                .as("a deleted test is one the pinned suite contains and this run would not")
                .isEqualTo(Test262Environment.WorktreeStatus.State.MODIFIED);
        assertThat(git(checkout, "checkout", "--", ".")).isZero();

        write(checkout.resolve("test/language/untracked.js"), "assert(true);\n");
        Test262Environment.WorktreeStatus untracked = Test262Environment.gitWorktreeStatus(checkout);
        assertThat(untracked.state())
                .as("discovery walks the working tree, so an untracked test is executed")
                .isEqualTo(Test262Environment.WorktreeStatus.State.MODIFIED);
        assertThat(untracked.detail()).contains("test/language/untracked.js");

        // A checkout can differ in thousands of places; the diagnostic quotes the first few and
        // says there are more, because one nobody can read is one nobody reads.
        for (int index = 0; index < 6; index++) {
            write(checkout.resolve("test/language/untracked" + index + ".js"), "assert(true);\n");
        }
        assertThat(Test262Environment.gitWorktreeStatus(checkout).detail()).endsWith("; ...");
    }

    @Test
    public void testPinnedRevisionIgnoresCommentsAndBlankLines() throws IOException {
        // The file is the one place the revision is written down, so it has to be able to say why.
        // Written with explicit newlines rather than as a text block: a blank line inside one is
        // indented to the block's margin, which is trailing whitespace in the source and fails
        // `git diff --check`.
        write(workingDirectory.resolve(Test262Environment.REVISION_FILE_NAME),
                "# The revision this repository measures itself against.\n"
                        + "\n"
                        + PINNED + "\n");
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
        write(gitDirectory().resolve("HEAD"), OTHER + "\n");
        withRevisionCheckWaived(() -> {
            assertThat(Test262Environment.check(workingDirectory, PINNED, "UTC", neverAsked()))
                    .as("the wrong revision is what was asked for, so there is nothing to report")
                    .isEmpty();
            assertThat(Test262Environment.check(workingDirectory, null, "UTC", neverAsked()))
                    .as("and the waiver is the one way to run with no pin configured")
                    .isEmpty();
            assertThat(Test262Environment.describe(workingDirectory, PINNED, "UTC").lines())
                    .containsExactly(
                            "Test262 revision: " + OTHER
                                    + " (revision check waived, pinned " + PINNED + ")",
                            "Test262 time zone: UTC");
        });
        assertThat(Test262Environment.check(workingDirectory, PINNED, "UTC", clean()))
                .as("and the waiver lasts no longer than it was asked for")
                .hasSize(1);
    }

    @Test
    public void testWithNothingPinnedTheRunIsRefused() throws IOException {
        // This used to be "a caller that has not said which revision it expects cannot be told it
        // has the wrong one", and the caller is the build, which reads the one checked-in file that
        // says what reproducible means here. So deleting that file, emptying it, or leaving nothing
        // but comments in it turned the guard off without saying so — the same fail-open pattern
        // the unreadable-revision case had, and the one thing a guard must not be able to do.
        write(gitDirectory().resolve("HEAD"), PINNED + "\n");
        AtomicInteger inspections = new AtomicInteger();
        Test262Environment.WorktreeStatusReader counting = root -> {
            inspections.incrementAndGet();
            return Test262Environment.WorktreeStatus.clean();
        };

        for (String unconfigured : new String[]{null, ""}) {
            List<Test262Environment.Diagnostic> diagnostics =
                    Test262Environment.check(workingDirectory, unconfigured, "UTC", counting);
            assertThat(diagnostics).hasSize(1);
            assertThat(diagnostics.get(0).fatal()).isTrue();
            assertThat(diagnostics.get(0).message())
                    .contains("No pinned test262 revision is configured")
                    .contains(Test262Environment.REVISION_FILE_NAME)
                    .contains("-Ptest262AllowAnyRevision=true");
        }
        assertThat(inspections)
                .as("there is nothing to compare the checkout against, so nothing asks Git")
                .hasValue(0);
    }

    private void write(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents, StandardCharsets.UTF_8);
    }
}
