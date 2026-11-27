package com.caoccao.qjs4j.builtins.array;
import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class SortOperationsTest extends BaseJavetTest {
    @Test
    void nativeCallbackPendingExceptionStopsSortWithoutWriteback() {
        JSNativeFunction comparator = new JSNativeFunction(context, "compare", 2,
                (ctx, receiver, args) -> ctx.throwTypeError("native failure"));
        JSArray array = (JSArray) context.eval("[3,2,1]");
        callBuiltin(ArrayPrototype::sort, array, comparator);
        assertThat(context.hasPendingException()).isTrue();
        context.clearPendingException();
        assertThat(callBuiltin(ArrayPrototype::join, array).toString()).isEqualTo("3,2,1");
    }
}
