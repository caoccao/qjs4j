package com.caoccao.qjs4j.core.collection;

import com.caoccao.qjs4j.core.JSNumber;
import org.junit.jupiter.api.Test;
import java.util.NoSuchElementException;
import static org.assertj.core.api.Assertions.*;
class OrderedHashTableTest {
    @Test
    void replacingAnEntryKeepsItsPositionAndExhaustedJavaIteratorsThrow() {
        OrderedHashTable<String> table = new OrderedHashTable<>();
        JSMap.KeyWrapper first = new JSMap.KeyWrapper(JSNumber.of(1));
        JSMap.KeyWrapper second = new JSMap.KeyWrapper(JSNumber.of(2));
        table.put(first, "old");
        table.put(second, "last");
        table.put(first, "new");
        assertThat(table.size()).isEqualTo(2);
        assertThat(table.get(first)).isEqualTo("new");
        var iterator = table.values().iterator();
        assertThat(iterator.next()).isEqualTo("new");
        assertThat(iterator.next()).isEqualTo("last");
        assertThat(iterator.hasNext()).isFalse();
        assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
    }
}
