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

package com.caoccao.qjs4j.cli;

import com.caoccao.qjs4j.compilation.Lexer;
import com.caoccao.qjs4j.compilation.ast.Program;
import com.caoccao.qjs4j.compilation.compiler.BytecodeCompiler;
import com.caoccao.qjs4j.compilation.parser.Parser;
import com.caoccao.qjs4j.vm.Bytecode;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Command-line tool for compiling JavaScript to bytecode.
 */
public final class BytecodeCompilerTool {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: qjsc <input.js> <output.qjsb>");
            System.exit(1);
        }

        String inputFile = args[0];
        String outputFile = args[1];

        String code = Files.readString(Path.of(inputFile));

        Lexer lexer = new Lexer(code);
        Parser parser = new Parser(lexer);
        Program ast = parser.parse();

        BytecodeCompiler compiler = new BytecodeCompiler();
        Bytecode bytecode = compiler.compile(ast);

        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(outputFile))) {
            out.writeObject(bytecode);
        }

        System.out.println("Compiled " + inputFile + " to " + outputFile);
    }
}
