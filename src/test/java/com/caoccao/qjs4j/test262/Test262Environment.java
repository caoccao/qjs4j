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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
 * A wrong revision is a refusal, because a gate that follows a moving suite is not a gate — it
 * turned red once already when upstream added tests for features the engine has not implemented,
 * without a line of this repository changing. Everything else is a warning: a suite exported
 * without its history can still be run, it just cannot be said to be the pinned one.
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
        return check(test262Root, pinnedRevision(Paths.get("")), ZoneId.systemDefault().getId());
    }

    /**
     * Check every premise against values supplied by the caller.
     *
     * @param test262Root       the suite root
     * @param pinnedRevision    the revision this repository pins, or null when none is configured
     * @param defaultTimeZoneId the identifier of the zone the run will read dates in
     * @return what is wrong, in the order it should be reported; empty when nothing is
     */
    static List<Diagnostic> check(Path test262Root, String pinnedRevision, String defaultTimeZoneId) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        Diagnostic revision = revisionDiagnostic(test262Root, pinnedRevision);
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
        String looseReference = readTrimmed(gitDirectory.resolve(reference));
        if (looseReference != null && !looseReference.isEmpty()) {
            return looseReference;
        }
        return packedReference(gitDirectory, reference);
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
     * Whether the caller has asked for whatever revision is on disk.
     *
     * @return true when the revision check is disabled
     */
    private static boolean isAnyRevisionAllowed() {
        return Boolean.parseBoolean(System.getProperty(ALLOW_ANY_REVISION_PROPERTY, "false"));
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
     * Whether the suite on disk is the one this repository measures itself against.
     *
     * @param test262Root    the suite root
     * @param pinnedRevision the revision this repository pins, or null when none is configured
     * @return the diagnostic, or null when there is nothing to report
     */
    private static Diagnostic revisionDiagnostic(Path test262Root, String pinnedRevision) {
        if (pinnedRevision == null || pinnedRevision.isEmpty() || isAnyRevisionAllowed()) {
            return null;
        }
        String checkoutRevision = checkoutRevision(test262Root);
        if (checkoutRevision == null) {
            return new Diagnostic(
                    "Cannot tell which revision of test262 is at " + test262Root
                            + ", so this run cannot be compared with the recorded baseline. This"
                            + " repository pins " + pinnedRevision + " in " + REVISION_FILE_NAME + ".",
                    false);
        }
        if (checkoutRevision.equalsIgnoreCase(pinnedRevision)) {
            return null;
        }
        return new Diagnostic(
                "test262 at " + test262Root + " is at revision " + checkoutRevision + ", but this"
                        + " repository pins " + pinnedRevision + " in " + REVISION_FILE_NAME + "."
                        + " Conformance counts from another revision are not comparable: upstream"
                        + " adds tests for features this engine has not implemented, which is a"
                        + " failure of the suite's clock rather than of this code. Check the pinned"
                        + " revision out, or pass -Ptest262AllowAnyRevision=true to run anyway.",
                true);
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
     * Something wrong with the premises of a run.
     *
     * @param message what is wrong and what to do about it
     * @param fatal   whether the run should be refused rather than merely qualified
     */
    record Diagnostic(String message, boolean fatal) {
    }
}
