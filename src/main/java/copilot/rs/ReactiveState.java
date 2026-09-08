package copilot.rs;

import java.util.function.*;

public interface ReactiveState<T> {
    Runnable registerListener(Consumer<T> onUpdate);
    void update(Function<T, T> update);
    T get();
    void set(T newValue);
}
