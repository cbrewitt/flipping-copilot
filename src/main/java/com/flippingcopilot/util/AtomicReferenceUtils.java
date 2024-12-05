package com.flippingcopilot.util;

import lombok.AllArgsConstructor;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class AtomicReferenceUtils {
    public static <T> OrElse ifPresent(AtomicReference<T> ref, Consumer<? super T> action) {
        T value = ref.get();
        if (value != null) {
            action.accept(value);
            return new OrElse(false);
        } else {
            return new OrElse(true);
        }
    }

    public static <T, E> OrElse ifBothPresent(AtomicReference<T> ref1, AtomicReference<E> ref2, BiConsumer<? super T,? super E> action) {
        T value1 = ref1.get();
        E value2 = ref2.get();
        if (value1 != null && value2 != null) {
            action.accept(value1, value2);
            return new OrElse(false);
        } else {
            return new OrElse(true);
        }
    }

    @AllArgsConstructor
    public static class OrElse {

        boolean shouldRun;

        public void orElse(Runnable r) {
            if(shouldRun) {
                r.run();
            }
        }
    }
}