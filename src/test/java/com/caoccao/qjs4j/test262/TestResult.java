/*
 * Copyright (c) 2024. caoccao.com Sam Cao
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

/**
 * Represents the result of executing a test262 test.
 */
public class TestResult {
    private final Test262TestCase testCase;
    private final TestStatus status;
    private final String message;

    private TestResult(Test262TestCase testCase, TestStatus status, String message) {
        this.testCase = testCase;
        this.status = status;
        this.message = message;
    }

    public static TestResult pass(Test262TestCase testCase) {
        return new TestResult(testCase, TestStatus.PASS, null);
    }

    public static TestResult fail(Test262TestCase testCase, String message) {
        return new TestResult(testCase, TestStatus.FAIL, message);
    }

    public static TestResult skip(Test262TestCase testCase, String reason) {
        return new TestResult(testCase, TestStatus.SKIP, reason);
    }

    public static TestResult timeout(Test262TestCase testCase) {
        return new TestResult(testCase, TestStatus.TIMEOUT, "Test exceeded timeout");
    }

    public String getMessage() {
        return message;
    }

    public TestStatus getStatus() {
        return status;
    }

    public Test262TestCase getTestCase() {
        return testCase;
    }

    public boolean isFailed() {
        return status == TestStatus.FAIL;
    }

    public boolean isPassed() {
        return status == TestStatus.PASS;
    }

    public boolean isSkipped() {
        return status == TestStatus.SKIP;
    }

    public boolean isTimeout() {
        return status == TestStatus.TIMEOUT;
    }

    @Override
    public String toString() {
        return String.format("TestResult{%s: %s%s}", 
            status, 
            testCase.getPath(),
            message != null ? " - " + message : "");
    }

    public enum TestStatus {
        PASS,
        FAIL,
        SKIP,
        TIMEOUT
    }
}
