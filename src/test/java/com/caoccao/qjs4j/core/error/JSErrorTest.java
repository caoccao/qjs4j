package com.caoccao.qjs4j.core.error;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.exceptions.JSErrorType;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

public class JSErrorTest extends BaseJavetTest {
    @Test
    public void testFunctionLength() {
        assertIntegerWithJavet(
                Stream.of(JSErrorType.values()).map(error -> error.name() + ".length").toArray(String[]::new));
    }

    @Test
    public void testInstanceof() {
        assertBooleanWithJavet("new AggregateError('AggregateError') instanceof AggregateError",
                "new Error('Error') instanceof Error", "new EvalError('EvalError') instanceof EvalError",
                "new RangeError('RangeError') instanceof RangeError",
                "new ReferenceError('ReferenceError') instanceof ReferenceError",
                "new SuppressedError(new Error('a'), new Error('b'), 'msg') instanceof SuppressedError",
                "new SyntaxError('SyntaxError') instanceof SyntaxError",
                "new TypeError('TypeError') instanceof TypeError", "new URIError('URIError') instanceof URIError");
    }

    @Test
    public void testNativeErrorPrototypeChains() {
        assertBooleanWithJavet("Object.getPrototypeOf(AggregateError) === Error",
                "Object.getPrototypeOf(AggregateError.prototype) === Error.prototype",
                "Object.getPrototypeOf(EvalError) === Error",
                "Object.getPrototypeOf(EvalError.prototype) === Error.prototype",
                "Object.getPrototypeOf(RangeError) === Error",
                "Object.getPrototypeOf(RangeError.prototype) === Error.prototype",
                "Object.getPrototypeOf(ReferenceError) === Error",
                "Object.getPrototypeOf(ReferenceError.prototype) === Error.prototype",
                "Object.getPrototypeOf(SuppressedError) === Error",
                "Object.getPrototypeOf(SuppressedError.prototype) === Error.prototype",
                "Object.getPrototypeOf(SyntaxError) === Error",
                "Object.getPrototypeOf(SyntaxError.prototype) === Error.prototype",
                "Object.getPrototypeOf(TypeError) === Error",
                "Object.getPrototypeOf(TypeError.prototype) === Error.prototype",
                "Object.getPrototypeOf(URIError) === Error",
                "Object.getPrototypeOf(URIError.prototype) === Error.prototype");
        assertBooleanWithJavet("AggregateError.prototype.hasOwnProperty('errors')");
        assertStringWithJavet("new AggregateError([], 'message').toString()");
    }

    @Test
    public void testSuppressedError() {
        assertStringWithJavet(
                "const e1 = new SuppressedError(new Error('main'), new Error('suppressed'), 'custom message'); e1.name + ': ' + e1.message",
                "const e2 = new SuppressedError(new Error('A'), new Error('B')); e2.error.message + '|' + e2.suppressed.message",
                "try { throw new SuppressedError(new Error('query failed'), new Error('cleanup failed'), 'transaction aborted'); } catch (e) { e.name + ': ' + e.message; }",
                "try { throw new SuppressedError(new Error('E1'), new Error('E2'), 'msg'); } catch (e) { e.error.message + '|' + e.suppressed.message; }",
                "try { const err = new SuppressedError(new Error('primary'), new Error('secondary')); throw err; } catch (e) { e.name + ': ' + e.error.message + ' / ' + e.suppressed.message; }");
    }

    @Test
    public void testTryCatchTypeError() {
        assertStringWithJavet(
                "try { throw new TypeError('I am a TypeError'); } catch (e) { e.name + ': ' + e.message; }");
    }

    @Test
    public void testTypeof() {
        assertStringWithJavet(
                Stream.of(JSErrorType.values()).map(error -> "typeof " + error.name()).toArray(String[]::new));
    }
}
