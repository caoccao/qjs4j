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

package com.caoccao.qjs4j.core.console;

import com.caoccao.qjs4j.BaseTest;
import org.junit.jupiter.api.BeforeEach;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

abstract class ConsoleOutputTestBase extends BaseTest {
    protected ByteArrayOutputStream errStream;
    protected JSConsole jsConsole;
    protected ByteArrayOutputStream outStream;

    protected String err() {
        return errStream.toString().replace(System.lineSeparator(), "\n");
    }

    protected String out() {
        return outStream.toString().replace(System.lineSeparator(), "\n");
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        errStream = new ByteArrayOutputStream();
        outStream = new ByteArrayOutputStream();
        jsConsole = context.getJSGlobalObject().getConsole();
        jsConsole.setErr(new PrintStream(errStream));
        jsConsole.setOut(new PrintStream(outStream));
    }

}
