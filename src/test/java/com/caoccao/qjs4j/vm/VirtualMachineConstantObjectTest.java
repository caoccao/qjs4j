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

package com.caoccao.qjs4j.vm;

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.JSObject;
import com.caoccao.qjs4j.core.JSRuntime;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bytecode constant objects — array literals, regexp literals, tagged-template objects — must not outlive the bytecode
 * that owns them.
 * <p>
 * The VM tracked "prototype already transferred" in a per-VM {@code Set<JSObject>} built on an {@code IdentityHashMap}.
 * Entries were added but never removed, so every constant object ever evaluated stayed strongly reachable for the
 * lifetime of the VM, along with everything it transitively referenced. The bit now lives on the object itself, so it
 * dies with the object.
 */
public class VirtualMachineConstantObjectTest extends BaseTest {
    @TempDir
    Path temporaryDirectory;

    private void assertConstantObjectsAreCollected(int count) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java");
        // Only the probe and engine classes: the child does not load Javet or the coverage agent.
        String classpath = Path
                .of(ConstantObjectGcProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                + File.pathSeparator
                + Path.of(JSRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path outputFile = temporaryDirectory.resolve("constant-object-gc.log");
        Process process = new ProcessBuilder(java.toString(), "-Xms16m", "-Xmx128m", "-XX:+UseSerialGC",
                "-XX:+ExitOnOutOfMemoryError", "-cp", classpath, ConstantObjectGcProbe.class.getName(),
                Integer.toString(count)).redirectErrorStream(true).redirectOutput(outputFile.toFile()).start();
        boolean finished;
        try {
            finished = process.waitFor(30, TimeUnit.SECONDS);
        } finally {
            // A JUnit timeout interrupts a thread, which cannot stop a blocked System.gc().
            // Bound collection with a process lifetime and reap the child even on interruption.
            if (process.isAlive()) {
                process.destroyForcibly();
                assertThat(process.waitFor(5, TimeUnit.SECONDS)).as("GC probe must terminate after being killed")
                        .isTrue();
            }
        }
        String output = Files.readString(outputFile);
        assertThat(finished).as("GC probe must finish within 30 seconds:%n%s", output).isTrue();
        assertThat(process.exitValue()).as("GC probe must collect all %d template objects:%n%s", count, output)
                .isZero();
    }

    @Test
    @Timeout(60)
    public void testConstantObjectIsCollectableAfterItsBytecodeIsDropped() throws Exception {
        assertConstantObjectsAreCollected(1);
    }

    @Test
    public void testConstantObjectPrototypeIsStillTransferred() {
        // The side table existed to make the transfer happen exactly once. That must still hold.
        assertThat(context.eval("""
                function tag(strings) { return Object.getPrototypeOf(strings) === Array.prototype }
                tag`a ${1} b`""").toString()).isEqualTo("true");
        assertThat(context.eval("Object.getPrototypeOf(/x/g) === RegExp.prototype").toString()).isEqualTo("true");
        assertThat(context.eval("""
                function tag(strings) { return Object.getPrototypeOf(strings.raw) === Array.prototype }
                tag`a ${1} b`""").toString()).isEqualTo("true");
    }

    @Test
    public void testConstantObjectRecordsItsPrototypeInitialization() {
        JSValue templateObject = context.eval("""
                function tag(strings) { return strings }
                tag`a ${1} b`""");
        assertThat(templateObject).isInstanceOfSatisfying(JSObject.class,
                object -> assertThat(object.isConstantPrototypeInitialized()).isTrue());
        // A plain runtime object is not a bytecode constant and carries no such marking.
        assertThat(context.eval("({})")).isInstanceOfSatisfying(JSObject.class,
                object -> assertThat(object.isConstantPrototypeInitialized()).isFalse());
    }

    @Test
    @Timeout(60)
    public void testRepeatedConstantObjectEvaluationDoesNotRetainMemory() throws Exception {
        // Observe all 200 objects directly instead of sampling 200 out of 20,000 evaluations.
        // The original strong-reference side table would retain every one of them.
        assertConstantObjectsAreCollected(200);
    }
}
