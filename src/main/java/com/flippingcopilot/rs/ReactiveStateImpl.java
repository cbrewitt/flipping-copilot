package com.flippingcopilot.rs;

import lombok.AllArgsConstructor;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Note on synchronization:
 *
 */
@AllArgsConstructor
public class ReactiveStateImpl<T> implements ReactiveState<T> {

    private final CopyOnWriteArrayList<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    private T state;

    @Override
    public Runnable registerListener(Consumer<T> onUpdate) {
        listeners.add(onUpdate);
        return () -> listeners.remove(onUpdate);
    }

    @Override
    public void update(Function<T, T> updateFunc) {
        final T updated;
        synchronized (this) {
            updated = updateFunc.apply(get());
        }
        setAndPublish(updated);
    }

    @Override
    public void set(T newState) {
        if (!Objects.equals(newState, get())) {
            setAndPublish(newState);
        }
    }

    public void forceSet(T newState) {
        setAndPublish(newState);
    }

    @Override
    public synchronized T get() {
        return state;
    }

    private void setAndPublish(T value) {
        synchronized (this) {
            state = value;
        }
        listeners.forEach(i -> i.accept(value));
    }
}