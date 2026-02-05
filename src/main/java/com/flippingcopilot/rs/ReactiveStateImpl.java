package com.flippingcopilot.rs;

import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
        if (!Objects.equals(newState, state)) {
            forceSet(newState);
        }
    }

    public void forceSet(T newState) {
        state = newState;
        listeners.forEach(i -> i.accept(state));
    }

    @Override
    public T get() {
        return state;
    }
}
