package com.caoccao.qjs4j;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interfaces.IJavetSupplier;
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.qjs4j.core.JSBigInt;
import com.caoccao.qjs4j.core.JSBigIntObject;
import com.caoccao.qjs4j.core.JSBoolean;
import com.caoccao.qjs4j.core.JSBooleanObject;
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

public class BaseJavetTest extends BaseTest {
    protected V8Runtime v8Runtime;

    protected void assertBigIntegerObjectWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> BigInteger.valueOf(v8Runtime.getExecutor(code).executeLong()),
                    () -> context.eval(code).asBigIntObject().map(JSBigIntObject::getValue).map(JSBigInt::value).orElseThrow());
        }
    }

    protected void assertBigIntegerWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> BigInteger.valueOf(v8Runtime.getExecutor(code).executeLong()),
                    () -> context.eval(code).toJavaObject());
        }
    }

    protected void assertBooleanObjectWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> v8Runtime.getExecutor(code).executeBoolean(),
                    () -> context.eval(code).asBooleanObject().map(JSBooleanObject::getValue).map(JSBoolean::value).orElseThrow());
        }
    }

    protected void assertBooleanWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> v8Runtime.getExecutor(code).executeBoolean(),
                    () -> context.eval(code).toJavaObject());
        }
    }

    protected void assertDoubleWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> v8Runtime.getExecutor(code).executeDouble(),
                    () -> context.eval(code).toJavaObject());
        }
    }

    protected void assertErrorWithJavet(String... codeArray) {
        for (String code : codeArray) {
            String expectedMessage = assertThatThrownBy(() -> v8Runtime.getExecutor(code).executeVoid())
                    .isInstanceOf(JavetException.class).actual().getMessage();
            assertThatThrownBy(() -> context.eval(code), expectedMessage)
                    .isInstanceOf(JSException.class)
                    .hasMessageContaining(expectedMessage);
        }
    }

    protected void assertIntegerWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> v8Runtime.getExecutor(code).executeInteger().doubleValue(),
                    () -> context.eval(code).toJavaObject());
        }
    }

    protected void assertLongWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> v8Runtime.getExecutor(code).executeLong().doubleValue(),
                    () -> context.eval(code).toJavaObject());
        }
    }

    protected void assertObjectWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> v8Runtime.getExecutor(code).executeObject(),
                    () -> context.eval(code).toJavaObject());
        }
    }

    protected void assertStringWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> v8Runtime.getExecutor(code).executeString(),
                    () -> context.eval(code).toJavaObject());
        }
    }

    protected <E extends Exception, T> void assertWithJavet(
            IJavetSupplier<T, E> javetSupplier,
            Supplier<T> qjs4jSupplier) {
        try {
            T expectedResult = javetSupplier.get();
            T result = qjs4jSupplier.get();
            assertThat(result).isEqualTo(expectedResult);
        } catch (Throwable t) {
            fail(t);
        }
    }

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        v8Runtime = V8Host.getV8Instance().createV8Runtime();
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        assertThat(v8Runtime.getReferenceCount()).as("V8 runtime reference count").isEqualTo(0);
        v8Runtime.close();
        super.tearDown();
    }
}
