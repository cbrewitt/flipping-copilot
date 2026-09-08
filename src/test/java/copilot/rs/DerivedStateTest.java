package copilot.rs;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;

public class DerivedStateTest {
    @Test public void oneSourceHandlesNullsAndSuppressesEqualResults() {
        var source = new ReactiveStateImpl<String>(null);
        var derived = ReactiveStateUtil.derive(source, value -> value == null ? 0 : value.length());
        List<Integer> updates = new ArrayList<>();
        derived.registerListener(updates::add);
        assertEquals(Integer.valueOf(0), derived.get());
        source.set("ab");
        source.set("cd");
        source.set(null);
        assertEquals(Arrays.asList(2, 0), updates);
    }

    @Test public void eachOfFourSourcesUpdatesTheCombinedState() {
        var first = new ReactiveStateImpl<>(1);
        var second = new ReactiveStateImpl<>(2);
        var third = new ReactiveStateImpl<>(3);
        var fourth = new ReactiveStateImpl<>(4);
        var derived = ReactiveStateUtil.derive(first, second, third, fourth, (a, b, c, d) -> a + b + c + d);
        List<Integer> updates = new ArrayList<>();
        derived.registerListener(updates::add);
        assertEquals(Integer.valueOf(10), derived.get());
        first.set(20);
        second.set(30);
        third.set(40);
        fourth.set(50);
        assertEquals(Arrays.asList(29, 57, 94, 140), updates);
    }

    @Test public void reentrantUpdatesKeepTheValueOfEachPublishedEvent() {
        var first = new ReactiveStateImpl<>(1);
        first.registerListener(value -> { if (value == 2) first.set(3); });
        var derived = ReactiveStateUtil.derive(first, new ReactiveStateImpl<>(2),
                new ReactiveStateImpl<>(3), new ReactiveStateImpl<>(4), (a, b, c, d) -> a + b + c + d);
        List<Integer> updates = new ArrayList<>();
        derived.registerListener(updates::add);
        first.set(2);
        assertEquals(Arrays.asList(12, 11), updates);
        assertEquals(Integer.valueOf(3), first.get());
    }
}
