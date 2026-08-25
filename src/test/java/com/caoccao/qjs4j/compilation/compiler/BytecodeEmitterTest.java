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

package com.caoccao.qjs4j.compilation.compiler;

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.vm.Bytecode;
import com.caoccao.qjs4j.vm.Opcode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BytecodeEmitter} writes and patches its code buffer in place.
 * <p>
 * It used to be backed by a {@code ByteArrayOutputStream}, which cannot overwrite a byte already
 * written: every jump patch copied the whole buffer out, edited it, reset the stream and copied it
 * back, so compiling a function with <em>J</em> jumps over <em>N</em> bytes cost O(J&middot;N).
 * Atom emission separately did a linear pool scan per atom, costing O(A&sup2;).
 */
public class BytecodeEmitterTest extends BaseTest {

    private static String buildLargeFunctionSource(int branchCount) {
        StringBuilder source = new StringBuilder("function big(x) {\n  let r = 0;\n");
        for (int index = 0; index < branchCount; index++) {
            source.append("  if (x === ").append(index).append(") { r += ").append(index)
                    .append("; } else { r -= 1; }\n");
            source.append("  for (let i").append(index).append(" = 0; i").append(index)
                    .append(" < 1; i").append(index).append("++) { r += 0; }\n");
            source.append("  r = r && 1 || 0;\n");
            source.append("  var v").append(index).append(" = { p").append(index).append(": ")
                    .append(index).append(" };\n");
        }
        // Reference the first and last object literal so the returned value proves every branch
        // body compiled and every per-branch atom resolved.
        source.append("  return r + v0.p0 + v").append(branchCount - 1)
                .append(".p").append(branchCount - 1).append(";\n}\n");
        return source.toString();
    }

    @Test
    public void testAtomPoolDeduplicatesRepeatedAtoms() {
        BytecodeEmitter emitter = new BytecodeEmitter();
        assertThat(emitter.emitAtom("alpha")).isZero();
        assertThat(emitter.emitAtom("beta")).isEqualTo(1);
        assertThat(emitter.emitAtom("alpha")).isZero();
        assertThat(emitter.emitAtom("gamma")).isEqualTo(2);
        assertThat(emitter.emitAtom("beta")).isEqualTo(1);
        assertThat(emitter.getAtomPool()).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    public void testBuildProducesExactlyTheEmittedBytes() {
        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitU8(0x11);
        emitter.emitU16(0x2233);
        emitter.emitU32(0x44556677);
        assertThat(emitter.getCodeSize()).isEqualTo(7);

        Bytecode bytecode = emitter.build(0);
        assertThat(bytecode.getInstructions()).containsExactly(
                (byte) 0x11,
                (byte) 0x22, (byte) 0x33,
                (byte) 0x44, (byte) 0x55, (byte) 0x66, (byte) 0x77);
    }

    @Test
    public void testConstantPoolDeduplicatesRepeatedConstants() {
        BytecodeEmitter emitter = new BytecodeEmitter();
        assertThat(emitter.emitConstant(new JSString("a"))).isZero();
        assertThat(emitter.emitConstant(JSNumber.of(1))).isEqualTo(1);
        assertThat(emitter.emitConstant(new JSString("a"))).isZero();
        assertThat(emitter.getConstantPool()).hasSize(2);
    }

    @Test
    public void testEmissionAcrossTheInitialBufferBoundaryIsCorrect() {
        // The buffer starts at 256 bytes and doubles. Emitting well past that must not lose or
        // duplicate a byte.
        BytecodeEmitter emitter = new BytecodeEmitter();
        int count = 5000;
        for (int index = 0; index < count; index++) {
            emitter.emitU8(index & 0xFF);
        }
        byte[] code = emitter.getCode();
        assertThat(code).hasSize(count);
        for (int index = 0; index < count; index++) {
            assertThat(code[index]).as("byte %d", index).isEqualTo((byte) (index & 0xFF));
        }
    }

    @Test
    public void testGetCodeReturnsACopyThatDoesNotAliasTheBuffer() {
        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitU32(0);
        byte[] first = emitter.getCode();
        first[0] = (byte) 0xEE;
        assertThat(emitter.getCode()[0]).isZero();
    }

    @Test
    public void testLargeFunctionWithManyJumpsCompilesAndRunsCorrectly() {
        // Exercises many jump patches and many distinct atoms in a single function.
        // The last branch never matches, so `r` ends at -1, normalised to 1 by `r && 1 || 0`;
        // the result is then 1 + v0.p0 (0) + v399.p399 (399).
        context.eval(buildLargeFunctionSource(400));
        assertThat(context.eval("big(0)")).isEqualTo(JSNumber.of(400));
        assertThat(context.eval("big(7)")).isEqualTo(JSNumber.of(400));
    }

    @Test
    @Timeout(60)
    public void testVeryLargeFunctionCompilesInBoundedTime() {
        // 8000 branches is roughly 1.2 MB of source with ~32000 jump patches. With the quadratic
        // emitter this moved gigabytes through Arrays.copyOf; the bound here is generous enough
        // not to be timing-sensitive while still failing if quadratic behaviour returns.
        context.eval(buildLargeFunctionSource(8000));
        assertThat(context.eval("big(1)")).isEqualTo(JSNumber.of(8000));
    }

    @Test
    public void testMarkCatchAsFinallySetsOnlyTheTopBit() {
        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitU32(0x01020304);
        emitter.emitU32(0x0A0B0C0D);
        emitter.markCatchAsFinally(4);
        assertThat(emitter.getCode()).containsExactly(
                (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
                (byte) 0x8A, (byte) 0x0B, (byte) 0x0C, (byte) 0x0D);
    }

    @Test
    public void testPatchJumpWritesTheRelativeDistanceInPlace() {
        BytecodeEmitter emitter = new BytecodeEmitter();
        emitter.emitU8(0x01);
        int jumpPosition = emitter.emitJump(Opcode.GOTO);
        emitter.emitU8(0x02);
        int sizeBeforePatch = emitter.getCodeSize();

        emitter.patchJump(jumpPosition, emitter.currentOffset());

        assertThat(emitter.getCodeSize()).as("patching must not change the code size")
                .isEqualTo(sizeBeforePatch);
        byte[] code = emitter.getCode();
        int patched = ((code[jumpPosition] & 0xFF) << 24)
                | ((code[jumpPosition + 1] & 0xFF) << 16)
                | ((code[jumpPosition + 2] & 0xFF) << 8)
                | (code[jumpPosition + 3] & 0xFF);
        assertThat(patched).isEqualTo(sizeBeforePatch - (jumpPosition + 4));
        assertThat(code[0]).as("bytes before the patch are untouched").isEqualTo((byte) 0x01);
        assertThat(code[sizeBeforePatch - 1]).as("bytes after the patch are untouched")
                .isEqualTo((byte) 0x02);
    }

    @Test
    public void testPatchJumpWithNegativeDistance() {
        BytecodeEmitter emitter = new BytecodeEmitter();
        int loopStart = emitter.currentOffset();
        emitter.emitU8(0x01);
        int jumpPosition = emitter.emitJump(Opcode.GOTO);
        emitter.patchJump(jumpPosition, loopStart);

        byte[] code = emitter.getCode();
        int patched = ((code[jumpPosition] & 0xFF) << 24)
                | ((code[jumpPosition + 1] & 0xFF) << 16)
                | ((code[jumpPosition + 2] & 0xFF) << 8)
                | (code[jumpPosition + 3] & 0xFF);
        assertThat(patched).isEqualTo(loopStart - (jumpPosition + 4)).isNegative();
    }
}
