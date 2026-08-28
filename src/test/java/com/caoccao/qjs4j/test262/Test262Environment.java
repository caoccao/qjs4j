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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The premises a conformance run rests on, checked before it starts.
 * <p>
 * A pass count only means something alongside the suite revision that produced it and the time zone
 * the host was in. Both used to be arranged outside the runner — in one GitHub workflow — so the
 * documented {@code ./gradlew test262} was reproducible only by reverse-engineering that file:
 * every local invocation accepted whatever {@code ../test262} happened to be checked out at, and a
 * host whose own zone is spelled {@code Etc/UTC} failed two interpretations that the same code
 * passes elsewhere.
 * <p>
 * The Gradle tasks now pin the time zone, so the documented command is reproducible wherever it is
 * run. What is checked here is what the tasks cannot enforce: that the suite on disk is the one this
 * repository measures itself against, and that the host has not silently made the run something
 * other than what it claims to be.
 * <p>
 * A revision that is not the pinned one is a refusal, because a gate that follows a moving suite is
 * not a gate — it turned red once already when upstream added tests for features the engine has not
 * implemented, without a line of this repository changing. A revision that cannot be read at all is
 * the same refusal: a suite exported without its history may well be the pinned one, but nothing
 * here can say so, and a gate that passes on a suite it cannot identify is not a gate either. A
 * checkout with edits on top of the pinned revision is the same refusal again, because the runner
 * discovers the files on disk and not the commit object: one untracked {@code .js} file changes the
 * total, and one edited harness file changes thousands of outcomes while {@code HEAD} goes on
 * naming the pin. And no pin at all is the refusal too — the one file that defines what
 * reproducible means here cannot be allowed to disable the check by going missing.
 * <p>
 * All of them are waived by {@code -Ptest262AllowAnyRevision=true}, which is the one explicit,
 * reviewable way to run against something other than the pin.
 * <p>
 * The host's time zone is only a warning, because it costs a handful of intl402 interpretations
 * rather than the meaning of the run.
 */
final class Test262Environment {
    /**
     * Run whatever revision is on disk. For deliberately testing against upstream tip.
     */
    static final String ALLOW_ANY_REVISION_PROPERTY = "qjs4j.test262.allowAnyRevision";
    /**
     * The file holding the revision, read relative to the working directory.
     */
    static final String REVISION_FILE_NAME = "test262-revision.txt";
    /**
     * The revision, when the build passes it explicitly rather than leaving it to be read.
     */
    static final String REVISION_PROPERTY = "qjs4j.test262.revision";
    /**
     * How long {@code git status} is given before the run gives up on establishing cleanliness.
     * <p>
     * Generous, because the suite is about fifty thousand files and a cold cache is slow; bounded,
     * because a conformance run must not hang on a Git invocation that is waiting for a lock or a
     * credential prompt. Running out of it is not a pass — it is the same refusal as any other way
     * of failing to establish what is on disk.
     */
    private static final int GIT_STATUS_TIMEOUT_SECONDS = 120;
    /**
     * The reference namespaces Git keeps per worktree rather than in the common directory.
     * <p>
     * Everything else under {@code refs/} — {@code refs/heads/}, {@code refs/tags/},
     * {@code refs/remotes/} — is common and shared by every worktree, so a file of that name beside
     * a linked worktree's {@code HEAD} is one Git ignores. See {@code gitrepository-layout}.
     */
    private static final List<String> PER_WORKTREE_REFERENCE_PREFIXES =
            List.of("refs/bisect/", "refs/rewritten/", "refs/worktree/");
    /**
     * How much of {@code git status} output a diagnostic quotes.
     */
    private static final int QUOTED_STATUS_LINES = 5;
    /**
     * The zone identifiers the pinned harness will not accept.
     * <p>
     * {@code isCanonicalizedStructurallyValidTimeZoneName} in {@code harness/testIntl.js} rejects
     * exactly these two, applying the pre-{@code canonical-tz} rule that folded them to
     * {@code UTC} — while {@code canonicalize-utc-timezone.js}, in the same suite, asserts that an
     * engine must preserve them. The engine follows the newer rule and is right to; only the
     * harness is behind, and only on a host whose own zone is spelled one of these ways.
     */
    private static final Set<String> UNUSABLE_TIME_ZONE_IDS = Set.of("Etc/GMT", "Etc/UTC");

    private Test262Environment() {
    }

    /**
     * Check every premise, in the environment the current process is running in.
     *
     * @param test262Root the suite root
     * @return what is wrong, in the order it should be reported; empty when nothing is
     */
    static List<Diagnostic> check(Path test262Root) {
        return check(
                test262Root,
                pinnedRevision(Paths.get("")),
                ZoneId.systemDefault().getId(),
                Test262Environment::gitWorktreeStatus);
    }

    /**
     * Check every premise against values supplied by the caller.
     * <p>
     * The worktree reader is a parameter rather than a fixed call so that the cases below can state
     * which checkout they are describing — clean, edited, or one whose state cannot be established —
     * without fabricating a repository Git would agree with. It is the only premise here that needs
     * a tool rather than a file.
     *
     * @param test262Root          the suite root
     * @param pinnedRevision       the revision this repository pins, or null when none is configured
     * @param defaultTimeZoneId    the identifier of the zone the run will read dates in
     * @param worktreeStatusReader how to find out whether the checkout has been edited
     * @return what is wrong, in the order it should be reported; empty when nothing is
     */
    static List<Diagnostic> check(
            Path test262Root,
            String pinnedRevision,
            String defaultTimeZoneId,
            WorktreeStatusReader worktreeStatusReader) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        Diagnostic revision = revisionDiagnostic(test262Root, pinnedRevision, worktreeStatusReader);
        if (revision != null) {
            diagnostics.add(revision);
        }
        Diagnostic timeZone = timeZoneDiagnostic(defaultTimeZoneId);
        if (timeZone != null) {
            diagnostics.add(timeZone);
        }
        return diagnostics;
    }

    /**
     * The revision a checkout is at, read from its Git metadata rather than by running Git.
     * <p>
     * Running {@code git} would need it on the path and would answer differently inside a container
     * that has the files but not the tool. The three shapes below are the ones a checkout of a
     * dependency actually takes: detached at a revision, which is what a pinned CI checkout is; on a
     * branch, which is what a clone is; and a linked worktree or submodule, whose {@code .git} is a
     * file pointing elsewhere.
     *
     * @param test262Root the suite root
     * @return the revision, or null when it cannot be determined
     */
    static String checkoutRevision(Path test262Root) {
        Path gitDirectory = gitDirectory(test262Root);
        if (gitDirectory == null) {
            return null;
        }
        String head = readTrimmed(gitDirectory.resolve("HEAD"));
        if (head == null || head.isEmpty()) {
            return null;
        }
        if (!head.startsWith("ref:")) {
            return head;
        }
        String reference = head.substring("ref:".length()).trim();
        if (reference.isEmpty()) {
            return null;
        }
        // Where Git would look, which is not always beside HEAD: a linked worktree keeps its own
        // HEAD and the three per-worktree namespaces, and everything else — refs/heads/ above all —
        // in the common directory. Reading beside HEAD first gave precedence to a file Git ignores,
        // so a stale or hand-made .git/worktrees/<name>/refs/heads/<branch> made the run report a
        // revision that is not the one the worktree has checked out. That is the exact disagreement
        // between this parser and Git that the parser exists to avoid. In an ordinary checkout there
        // is no common directory and both are the same place.
        Path referenceDirectory = referenceDirectory(gitDirectory);
        Path resolvedFrom = isPerWorktreeReference(reference) ? gitDirectory : referenceDirectory;
        String looseReference = readTrimmed(resolvedFrom.resolve(reference));
        if (looseReference != null && !looseReference.isEmpty()) {
            return looseReference;
        }
        return packedReference(referenceDirectory, reference);
    }

    /**
     * What this run is, in the two lines a pass count has to be quoted alongside.
     * <p>
     * Printed whatever happens, including when the revision check has been waived — that is the case
     * most in need of a revision, and the diagnostic path deliberately does not read one there. A
     * count without a suite revision is not comparable with anything, and the log is where a count
     * outlives the machine that produced it.
     *
     * @param test262Root the suite root
     * @return two lines: the suite revision and how it relates to the pin, then the time zone
     */
    static String describe(Path test262Root) {
        return describe(test262Root, pinnedRevision(Paths.get("")), ZoneId.systemDefault().getId());
    }

    /**
     * What this run is, against values supplied by the caller.
     *
     * @param test262Root       the suite root
     * @param pinnedRevision    the revision this repository pins, or null when none is configured
     * @param defaultTimeZoneId the identifier of the zone the run will read dates in
     * @return two lines: the suite revision and how it relates to the pin, then the time zone
     */
    static String describe(Path test262Root, String pinnedRevision, String defaultTimeZoneId) {
        String checkoutRevision = checkoutRevision(test262Root);
        String qualification;
        if (pinnedRevision == null || pinnedRevision.isEmpty()) {
            qualification = "nothing pinned";
        } else if (isAnyRevisionAllowed()) {
            qualification = "revision check waived, pinned " + pinnedRevision;
        } else if (checkoutRevision != null && checkoutRevision.equalsIgnoreCase(pinnedRevision)) {
            qualification = "pinned";
        } else {
            qualification = "not the pinned " + pinnedRevision;
        }
        return "Test262 revision: " + (checkoutRevision == null ? "unknown" : checkoutRevision)
                + " (" + qualification + ")"
                + System.lineSeparator()
                + "Test262 time zone: " + defaultTimeZoneId;
    }

    /**
     * The directory holding a checkout's Git metadata.
     *
     * @param test262Root the suite root
     * @return the directory, or null when the root is not a checkout
     */
    private static Path gitDirectory(Path test262Root) {
        Path gitPath = test262Root.resolve(".git");
        if (Files.isDirectory(gitPath)) {
            return gitPath;
        }
        String pointer = readTrimmed(gitPath);
        if (pointer == null || !pointer.startsWith("gitdir:")) {
            return null;
        }
        Path linkedDirectory = Paths.get(pointer.substring("gitdir:".length()).trim());
        return linkedDirectory.isAbsolute()
                ? linkedDirectory
                : test262Root.resolve(linkedDirectory).normalize();
    }

    /**
     * Whether a checkout holds the files its revision names, asked of Git itself.
     * <p>
     * The revision is read out of the metadata rather than by running Git, because a revision is one
     * file and a container may have the files without the tool. Cleanliness is not one file: it is
     * the index, the worktree, sparse-checkout state and file modes compared against a commit, and a
     * partial reimplementation of {@code git status} would be wrong in exactly the quiet ways that
     * make a conformance count wrong. So this asks Git, and not being able to ask is reported as not
     * knowing — which the caller treats as a refusal, the same as any other premise it cannot
     * establish.
     *
     * @param test262Root the suite root
     * @return what Git says about the checkout, or that it could not be asked
     */
    static WorktreeStatus gitWorktreeStatus(Path test262Root) {
        ProcessBuilder builder = new ProcessBuilder(
                "git",
                "-C",
                test262Root.toString(),
                "status",
                "--porcelain=v1",
                "--untracked-files=all");
        builder.redirectErrorStream(true);
        Process process = null;
        try {
            process = builder.start();
            String output;
            try (InputStream stream = process.getInputStream()) {
                output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!process.waitFor(GIT_STATUS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return WorktreeStatus.unknown(
                        "git status did not finish within " + GIT_STATUS_TIMEOUT_SECONDS + "s");
            }
            if (process.exitValue() != 0) {
                return WorktreeStatus.unknown(
                        "git status exited with " + process.exitValue() + ": " + quoted(output));
            }
            String changes = output.strip();
            return changes.isEmpty() ? WorktreeStatus.clean() : WorktreeStatus.modified(quoted(changes));
        } catch (IOException unrunnable) {
            return WorktreeStatus.unknown("git could not be run: " + unrunnable.getMessage());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return WorktreeStatus.unknown("interrupted while waiting for git status");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * Whether the caller has asked for whatever revision is on disk.
     *
     * @return true when the revision check is disabled
     */
    private static boolean isAnyRevisionAllowed() {
        return Boolean.parseBoolean(System.getProperty(ALLOW_ANY_REVISION_PROPERTY, "false"));
    }

    /**
     * Whether a reference belongs to one worktree rather than to the repository.
     *
     * @param reference the full reference name
     * @return true when it is resolved beside the worktree's own {@code HEAD}
     */
    private static boolean isPerWorktreeReference(String reference) {
        return PER_WORKTREE_REFERENCE_PREFIXES.stream().anyMatch(reference::startsWith);
    }

    /**
     * Resolve a reference out of a checkout's {@code packed-refs}.
     * <p>
     * A freshly cloned repository packs its references, so the loose file a branch name would name
     * does not exist. A {@code ^} line is the object a tag dereferences to and is not a reference.
     *
     * @param gitDirectory the checkout's Git metadata directory
     * @param reference    the full reference name
     * @return the revision, or null when the reference is not packed
     */
    private static String packedReference(Path gitDirectory, String reference) {
        Path packedReferences = gitDirectory.resolve("packed-refs");
        if (!Files.isRegularFile(packedReferences)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(packedReferences, StandardCharsets.UTF_8)) {
                if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '^') {
                    continue;
                }
                int separatorIndex = line.indexOf(' ');
                if (separatorIndex > 0 && line.substring(separatorIndex + 1).trim().equals(reference)) {
                    return line.substring(0, separatorIndex).trim();
                }
            }
        } catch (IOException unreadable) {
            return null;
        }
        return null;
    }

    /**
     * The revision this repository pins.
     * <p>
     * The build passes it as a system property, having read the one file that holds it; reading that
     * file directly is the fallback for a runner started some other way, from an IDE for instance.
     *
     * @param projectDirectory the directory holding {@value #REVISION_FILE_NAME}
     * @return the revision, or null when none is configured
     */
    static String pinnedRevision(Path projectDirectory) {
        String configured = System.getProperty(REVISION_PROPERTY, "").trim();
        if (!configured.isEmpty()) {
            return configured;
        }
        Path revisionFile = projectDirectory.resolve(REVISION_FILE_NAME);
        String contents = readTrimmed(revisionFile);
        if (contents == null) {
            return null;
        }
        for (String line : contents.split("\\R")) {
            String candidate = line.trim();
            if (!candidate.isEmpty() && !candidate.startsWith("#")) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * As much of a Git message as a diagnostic should carry.
     * <p>
     * A dirty checkout can have thousands of entries, and a diagnostic nobody can read is a
     * diagnostic nobody reads. The first few name the kind of edit, which is what the reader has to
     * act on; the count says how much more there is.
     *
     * @param output what Git printed
     * @return the first few lines, and how many were left out
     */
    private static String quoted(String output) {
        List<String> lines = output.strip().lines().limit(QUOTED_STATUS_LINES + 1L).toList();
        String quoted = String.join("; ", lines.subList(0, Math.min(lines.size(), QUOTED_STATUS_LINES)));
        return lines.size() > QUOTED_STATUS_LINES ? quoted + "; ..." : quoted;
    }

    /**
     * Read a file and trim it, answering null rather than throwing when it is not there.
     *
     * @param path the file
     * @return the trimmed contents, or null
     */
    private static String readTrimmed(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).trim();
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }

    /**
     * The directory holding the references a checkout's {@code HEAD} can name.
     * <p>
     * A linked worktree keeps its own {@code HEAD} in {@code .git/worktrees/<name>/}, but not its
     * branches: those are in the repository's common Git directory, named by the {@code commondir}
     * file beside that {@code HEAD}. Looking only beside {@code HEAD} therefore resolved a detached
     * worktree and gave up on a branch-based one — which is the ordinary shape of a worktree —
     * leaving the run unable to say which revision it was testing.
     *
     * @param gitDirectory the directory holding {@code HEAD}
     * @return the directory holding the references, which is {@code gitDirectory} itself unless this
     * is a linked worktree
     */
    private static Path referenceDirectory(Path gitDirectory) {
        String commonDirectory = readTrimmed(gitDirectory.resolve("commondir"));
        if (commonDirectory == null || commonDirectory.isEmpty()) {
            return gitDirectory;
        }
        Path resolved = Paths.get(commonDirectory);
        return resolved.isAbsolute()
                ? resolved.normalize()
                : gitDirectory.resolve(resolved).normalize();
    }

    /**
     * Whether the suite on disk is the one this repository measures itself against.
     *
     * @param test262Root          the suite root
     * @param pinnedRevision       the revision this repository pins, or null when none is configured
     * @param worktreeStatusReader how to find out whether the checkout has been edited
     * @return the diagnostic, or null when there is nothing to report
     */
    private static Diagnostic revisionDiagnostic(
            Path test262Root, String pinnedRevision, WorktreeStatusReader worktreeStatusReader) {
        if (isAnyRevisionAllowed()) {
            return null;
        }
        if (pinnedRevision == null || pinnedRevision.isEmpty()) {
            // Fail closed, for the same reason an unreadable revision does. This used to be read as
            // "the caller has not asked for a revision, so there is nothing to enforce" — but the
            // caller is the build, and the build reads the one checked-in file that says what
            // reproducible means here. Deleting that file, emptying it, or leaving nothing but
            // comments in it therefore turned the guard off silently, which is the one thing a guard
            // must not be able to do.
            return new Diagnostic(
                    "No pinned test262 revision is configured, so this run cannot be compared with"
                            + " the recorded baseline. Restore " + REVISION_FILE_NAME + " with the"
                            + " revision this repository measures itself against, or pass"
                            + " -Ptest262AllowAnyRevision=true to run against whatever is on disk.",
                    true);
        }
        String checkoutRevision = checkoutRevision(test262Root);
        if (checkoutRevision == null) {
            // A refusal, not a warning. A suite whose revision cannot be read may well be the
            // pinned one, but nothing here can say so — and a run reported as green against a suite
            // it cannot identify is exactly the claim this check exists to stop being made. The
            // warning it used to be was printed on standard error, into a log nobody reads when the
            // build is green, while the count it qualified went on being quoted as the pinned one.
            return new Diagnostic(
                    "Cannot tell which revision of test262 is at " + test262Root
                            + ", so this run cannot be compared with the recorded baseline. This"
                            + " repository pins " + pinnedRevision + " in " + REVISION_FILE_NAME + "."
                            + " Use a checkout with its Git metadata intact, or pass"
                            + " -Ptest262AllowAnyRevision=true to run against whatever is on disk.",
                    true);
        }
        if (!checkoutRevision.equalsIgnoreCase(pinnedRevision)) {
            return new Diagnostic(
                    "test262 at " + test262Root + " is at revision " + checkoutRevision + ", but this"
                            + " repository pins " + pinnedRevision + " in " + REVISION_FILE_NAME + "."
                            + " Conformance counts from another revision are not comparable: upstream"
                            + " adds tests for features this engine has not implemented, which is a"
                            + " failure of the suite's clock rather than of this code. Check the pinned"
                            + " revision out, or pass -Ptest262AllowAnyRevision=true to run anyway.",
                    true);
        }
        return worktreeDiagnostic(test262Root, worktreeStatusReader.read(test262Root));
    }

    /**
     * Whether the host's zone is one the pinned harness can be run in.
     *
     * @param defaultTimeZoneId the identifier of the zone the run will read dates in
     * @return the diagnostic, or null when there is nothing to report
     */
    private static Diagnostic timeZoneDiagnostic(String defaultTimeZoneId) {
        if (!UNUSABLE_TIME_ZONE_IDS.contains(defaultTimeZoneId)) {
            return null;
        }
        return new Diagnostic(
                "The default time zone is " + defaultTimeZoneId + ", which the pinned harness's"
                        + " isCanonicalizedStructurallyValidTimeZoneName rejects, so a few intl402"
                        + " interpretations will fail for that reason and not for anything in this"
                        + " engine. The Gradle test262 tasks pin the zone; a runner started some"
                        + " other way should be given -Duser.timezone=UTC.",
                false);
    }

    /**
     * Whether the files the run will execute are the ones the pinned revision names.
     * <p>
     * Naming the right commit is not the same as executing it. Discovery walks the working tree, so
     * an untracked {@code .js} file adds an interpretation the pinned suite does not contain, a
     * deleted one removes several, and an edited harness file changes the outcome of thousands while
     * {@code HEAD} goes on naming the pin. A count reported against that is a count for a suite
     * nothing else can reproduce.
     *
     * @param test262Root the suite root
     * @param status      what Git said about the checkout
     * @return the diagnostic, or null when there is nothing to report
     */
    private static Diagnostic worktreeDiagnostic(Path test262Root, WorktreeStatus status) {
        return switch (status.state()) {
            case CLEAN -> null;
            case MODIFIED -> new Diagnostic(
                    "test262 at " + test262Root + " is at the pinned revision but has been edited"
                            + " since, so this run would not execute the pinned suite: "
                            + status.detail() + ". The runner discovers the files on disk rather"
                            + " than the commit, so counts from an edited checkout are not"
                            + " comparable. Clean the checkout, or pass"
                            + " -Ptest262AllowAnyRevision=true to run against it anyway.",
                    true);
            case UNKNOWN -> new Diagnostic(
                    "Cannot tell whether test262 at " + test262Root + " still holds the files the"
                            + " pinned revision names (" + status.detail() + "), so this run cannot"
                            + " be compared with the recorded baseline. Make git available where the"
                            + " suite is checked out, or pass -Ptest262AllowAnyRevision=true to run"
                            + " against whatever is on disk.",
                    true);
        };
    }

    /**
     * How the run finds out whether the checkout has been edited.
     */
    @FunctionalInterface
    interface WorktreeStatusReader {
        /**
         * Ask about one checkout.
         *
         * @param test262Root the suite root
         * @return what it holds relative to the revision it names
         */
        WorktreeStatus read(Path test262Root);
    }

    /**
     * Something wrong with the premises of a run.
     *
     * @param message what is wrong and what to do about it
     * @param fatal   whether the run should be refused rather than merely qualified
     */
    record Diagnostic(String message, boolean fatal) {
    }

    /**
     * What a checkout holds, relative to the revision it names.
     *
     * @param state  clean, edited, or not established
     * @param detail what Git said, for a reader who has to act on it; empty when there is nothing
     *               to say
     */
    record WorktreeStatus(State state, String detail) {
        /**
         * A checkout holding exactly the files its revision names.
         *
         * @return the status
         */
        static WorktreeStatus clean() {
            return new WorktreeStatus(State.CLEAN, "");
        }

        /**
         * A checkout with edits on top of the revision it names.
         *
         * @param detail what Git said was different
         * @return the status
         */
        static WorktreeStatus modified(String detail) {
            return new WorktreeStatus(State.MODIFIED, detail);
        }

        /**
         * A checkout whose state could not be established.
         *
         * @param reason why not
         * @return the status
         */
        static WorktreeStatus unknown(String reason) {
            return new WorktreeStatus(State.UNKNOWN, reason);
        }

        /**
         * The three answers there are. Not knowing is one of them, and is not the same as clean.
         */
        enum State {
            /**
             * The files on disk are the ones the revision names.
             */
            CLEAN,
            /**
             * Something has been added, changed or removed since.
             */
            MODIFIED,
            /**
             * Git could not be asked, or did not answer.
             */
            UNKNOWN
        }
    }
}
