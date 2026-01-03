package com.caoccao.qjs4j;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interfaces.IJavetSupplier;
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.options.V8RuntimeOptions;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.primitive.*;
import com.caoccao.javet.values.reference.V8ValuePromise;
import com.caoccao.qjs4j.core.*;
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

    protected void assertBigIntegerWithJavet(boolean async, String... codeArray) {
        if (async) {
            for (String code : codeArray) {
                assertWithJavet(
                        () -> {
                            try (V8Value v8Value = v8Runtime.getExecutor(code).execute()) {
                                assertThat(v8Value).isInstanceOf(V8ValuePromise.class);
                                V8ValuePromise v8ValuePromise = (V8ValuePromise) v8Value;
                                v8Runtime.await();
                                assertThat(v8ValuePromise.getState()).isNotEqualTo(V8ValuePromise.STATE_PENDING);
                                try (V8Value v8Result = v8ValuePromise.getResult()) {
                                    assertThat(v8Result).isInstanceOf(V8ValueLong.class);
                                    return ((V8ValueLong) v8Result).getValue();
                                }
                            }
                        },
                        () -> {
                            JSPromise jsPromise = context.eval(code).asPromise().orElseThrow();
                            awaitPromise(jsPromise);
                            JSValue jsResult = jsPromise.getResult();
                            assertThat(jsResult).isInstanceOf(JSBigInt.class);
                            return ((JSBigInt) jsResult).value();
                        });
            }
        } else {
            assertBigIntegerWithJavet(codeArray);
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

    protected void assertBooleanWithJavet(boolean async, String... codeArray) {
        if (async) {
            for (String code : codeArray) {
                assertWithJavet(
                        () -> {
                            try (V8Value v8Value = v8Runtime.getExecutor(code).execute()) {
                                assertThat(v8Value).isInstanceOf(V8ValuePromise.class);
                                V8ValuePromise v8ValuePromise = (V8ValuePromise) v8Value;
                                v8Runtime.await();
                                assertThat(v8ValuePromise.getState()).isNotEqualTo(V8ValuePromise.STATE_PENDING);
                                try (V8Value v8Result = v8ValuePromise.getResult()) {
                                    assertThat(v8Result).isInstanceOf(V8ValueBoolean.class);
                                    return ((V8ValueBoolean) v8Result).getValue();
                                }
                            }
                        },
                        () -> {
                            JSPromise jsPromise = context.eval(code).asPromise().orElseThrow();
                            awaitPromise(jsPromise);
                            JSValue jsResult = jsPromise.getResult();
                            assertThat(jsResult).isInstanceOf(JSBoolean.class);
                            return ((JSBoolean) jsResult).value();
                        });
            }
        } else {
            assertBooleanWithJavet(codeArray);
        }
    }

    protected void assertDoubleWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> v8Runtime.getExecutor(code).executeDouble(),
                    () -> context.eval(code).toJavaObject());
        }
    }

    protected void assertDoubleWithJavet(boolean async, String... codeArray) {
        if (async) {
            for (String code : codeArray) {
                assertWithJavet(
                        () -> {
                            try (V8Value v8Value = v8Runtime.getExecutor(code).execute()) {
                                assertThat(v8Value).isInstanceOf(V8ValuePromise.class);
                                V8ValuePromise v8ValuePromise = (V8ValuePromise) v8Value;
                                v8Runtime.await();
                                assertThat(v8ValuePromise.getState()).isNotEqualTo(V8ValuePromise.STATE_PENDING);
                                try (V8Value v8Result = v8ValuePromise.getResult()) {
                                    assertThat(v8Result).isInstanceOf(V8ValueDouble.class);
                                    return ((V8ValueDouble) v8Result).getValue();
                                }
                            }
                        },
                        () -> {
                            JSPromise jsPromise = context.eval(code).asPromise().orElseThrow();
                            awaitPromise(jsPromise);
                            JSValue jsResult = jsPromise.getResult();
                            assertThat(jsResult).isInstanceOf(JSNumber.class);
                            return ((JSNumber) jsResult).value();
                        });
            }
        } else {
            assertDoubleWithJavet(codeArray);
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

    protected void assertIntegerWithJavet(boolean async, String... codeArray) {
        if (async) {
            for (String code : codeArray) {
                assertWithJavet(
                        () -> {
                            try (V8Value v8Value = v8Runtime.getExecutor(code).execute()) {
                                assertThat(v8Value).isInstanceOf(V8ValuePromise.class);
                                V8ValuePromise v8ValuePromise = (V8ValuePromise) v8Value;
                                v8Runtime.await();
                                assertThat(v8ValuePromise.getState()).isNotEqualTo(V8ValuePromise.STATE_PENDING);
                                try (V8Value v8Result = v8ValuePromise.getResult()) {
                                    assertThat(v8Result).isInstanceOf(V8ValueInteger.class);
                                    return ((V8ValueInteger) v8Result).getValue().doubleValue();
                                }
                            }
                        },
                        () -> {
                            JSPromise jsPromise = context.eval(code).asPromise().orElseThrow();
                            awaitPromise(jsPromise);
                            JSValue jsResult = jsPromise.getResult();
                            assertThat(jsResult).isInstanceOf(JSNumber.class);
                            return ((JSNumber) jsResult).value();
                        });
            }
        } else {
            assertIntegerWithJavet(codeArray);
        }
    }

    protected void assertLongWithJavet(String... codeArray) {
        for (String code : codeArray) {
            assertWithJavet(
                    () -> v8Runtime.getExecutor(code).executeLong().doubleValue(),
                    () -> context.eval(code).toJavaObject());
        }
    }

    protected void assertLongWithJavet(boolean async, String... codeArray) {
        if (async) {
            for (String code : codeArray) {
                assertWithJavet(
                        () -> {
                            try (V8Value v8Value = v8Runtime.getExecutor(code).execute()) {
                                assertThat(v8Value).isInstanceOf(V8ValuePromise.class);
                                V8ValuePromise v8ValuePromise = (V8ValuePromise) v8Value;
                                v8Runtime.await();
                                assertThat(v8ValuePromise.getState()).isNotEqualTo(V8ValuePromise.STATE_PENDING);
                                try (V8Value v8Result = v8ValuePromise.getResult()) {
                                    assertThat(v8Result).isInstanceOf(V8ValueLong.class);
                                    return ((V8ValueLong) v8Result).getValue().doubleValue();
                                }
                            }
                        },
                        () -> {
                            JSPromise jsPromise = context.eval(code).asPromise().orElseThrow();
                            awaitPromise(jsPromise);
                            JSValue jsResult = jsPromise.getResult();
                            assertThat(jsResult).isInstanceOf(JSNumber.class);
                            return ((JSNumber) jsResult).value();
                        });
            }
        } else {
            assertLongWithJavet(codeArray);
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

    protected void assertStringWithJavet(boolean async, String... codeArray) {
        if (async) {
            for (String code : codeArray) {
                assertWithJavet(
                        () -> {
                            try (V8Value v8Value = v8Runtime.getExecutor(code).execute()) {
                                assertThat(v8Value).isInstanceOf(V8ValuePromise.class);
                                V8ValuePromise v8ValuePromise = (V8ValuePromise) v8Value;
                                v8Runtime.await();
                                assertThat(v8ValuePromise.getState()).isNotEqualTo(V8ValuePromise.STATE_PENDING);
                                try (V8Value v8Result = v8ValuePromise.getResult()) {
                                    assertThat(v8Result).isInstanceOf(V8ValueString.class);
                                    return ((V8ValueString) v8Result).getValue();
                                }
                            }
                        },
                        () -> {
                            JSPromise jsPromise = context.eval(code).asPromise().orElseThrow();
                            awaitPromise(jsPromise);
                            JSValue jsResult = jsPromise.getResult();
                            assertThat(jsResult).isInstanceOf(JSString.class);
                            return ((JSString) jsResult).value();
                        });
            }
        } else {
            assertStringWithJavet(codeArray);
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
        V8RuntimeOptions.V8_FLAGS.setJsFloat16Array(true);
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
