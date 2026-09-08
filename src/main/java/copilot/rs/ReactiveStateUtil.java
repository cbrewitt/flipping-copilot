package copilot.rs;

import java.util.function.*;

public final class ReactiveStateUtil {

    private ReactiveStateUtil() {
    }

    public static <A, B> ReactiveState<B> derive(ReactiveState<A> source, Function<A, B> transform) {
        ReactiveStateImpl<B> derived = new ReactiveStateImpl<>(transform.apply(source.get()));
        source.registerListener(value -> derived.set(transform.apply(value)));
        return derived;
    }

    public static <A, B, C, D, E> ReactiveState<E> derive(
            ReactiveState<A> first,
            ReactiveState<B> second,
            ReactiveState<C> third,
            ReactiveState<D> fourth,
            QuadFunction<A, B, C, D, E> transform) {
        ReactiveStateImpl<E> derived = new ReactiveStateImpl<>(transform.apply(first.get(), second.get(), third.get(), fourth.get()));
        registerJointListener(first, second, third, fourth, (value1, value2, value3, value4) ->
                derived.set(transform.apply(value1, value2, value3, value4)));
        return derived;
    }

    public static <A, B, C, D> void registerJointListener(
            ReactiveState<A> first,
            ReactiveState<B> second,
            ReactiveState<C> third,
            ReactiveState<D> fourth,
            QuadConsumer<A, B, C, D> listener) {
        first.registerListener(value -> listener.accept(value, second.get(), third.get(), fourth.get()));
        second.registerListener(value -> listener.accept(first.get(), value, third.get(), fourth.get()));
        third.registerListener(value -> listener.accept(first.get(), second.get(), value, fourth.get()));
        fourth.registerListener(value -> listener.accept(first.get(), second.get(), third.get(), value));
    }

    @FunctionalInterface
    public interface QuadConsumer<A, B, C, D> {
        void accept(A first, B second, C third, D fourth);
    }

    @FunctionalInterface
    public interface QuadFunction<A, B, C, D, E> {
        E apply(A first, B second, C third, D fourth);
    }
}
