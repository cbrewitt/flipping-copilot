package com.flippingcopilot.rs;

import java.util.function.Consumer;
import java.util.function.Function;

public interface ReactiveState<T> {
    Runnable registerListener(Consumer<T> onUpdate);
    void update(Function<T, T> update);
    T get();
    void set(T newValue);
}
