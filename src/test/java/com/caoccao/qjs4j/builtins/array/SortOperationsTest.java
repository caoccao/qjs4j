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
        ArrayPrototype.sort(context, array, new JSValue[]{comparator});
        assertThat(context.hasPendingException()).isTrue();
        context.clearPendingException();
        assertThat(ArrayPrototype.join(context, array, JSValue.NO_ARGS).toString()).isEqualTo("3,2,1");
    }
}
