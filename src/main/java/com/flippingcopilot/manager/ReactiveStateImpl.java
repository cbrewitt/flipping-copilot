package com.flippingcopilot.manager;

import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

@AllArgsConstructor
public class ReactiveStateImpl<T> implements ReactiveState<T> {

    private final List<Consumer<T>> listeners =  new ArrayList<>();
    private T state;

    @Override
    public Runnable registerListener(Consumer<T> onUpdate) {
        listeners.add(onUpdate);
        return () -> listeners.removeIf(i -> i.equals(onUpdate));
    }

    @Override
    public void applyUpdate(Function<T, Boolean> update) {
        if(update.apply(state)) {
            listeners.forEach(i -> i.accept(state));
        }
    }

    @Override
    public void set(T newState) {
        state = newState;
        listeners.forEach(i -> i.accept(state));
    }

    @Override
    public T get() {
        return state;
    }
}
