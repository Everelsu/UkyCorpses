/*
 * Adapted from JvmDowngrader 2.0.1, William Gray, LGPL-2.1.
 * Changes: retain pending references and run cleanup at most once, including concurrent explicit/GC cleanup.
 * Upstream sources and license are bundled in META-INF/licenses/jvmdowngrader.
 */
package com.uky.corpses.joltcompat.xyz.wagyourtail.jvmdg.j9.stub.java_base;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class J_L_R_Cleaner {

    private static final AtomicInteger COUNT = new AtomicInteger();
    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();
    // ReferenceQueue alone does not keep PhantomReferences alive until their referents are collected.
    private final Set<PhantomCleanable> pending = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private J_L_R_Cleaner(ThreadFactory factory) {
        Thread thread = factory.newThread(() -> {
            while (true) {
                try {
                    ((PhantomCleanable) queue.remove()).clean();
                } catch (Throwable failure) {
                    failure.printStackTrace(System.err);
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public static J_L_R_Cleaner create() {
        return create(runnable -> {
            Thread thread = new Thread(runnable, "Jolt-Cleaner-" + COUNT.incrementAndGet());
            thread.setContextClassLoader(ClassLoader.getSystemClassLoader());
            return thread;
        });
    }

    public static J_L_R_Cleaner create(ThreadFactory factory) {
        return new J_L_R_Cleaner(Objects.requireNonNull(factory));
    }

    public Cleanable register(Object object, Runnable action) {
        PhantomCleanable reference = new PhantomCleanable(
            Objects.requireNonNull(object),
            Objects.requireNonNull(action));
        pending.add(reference);
        if (reference.cleaned.get()) pending.remove(reference);
        return reference;
    }

    public interface Cleanable {

        void clean();
    }

    private final class PhantomCleanable extends PhantomReference<Object> implements Cleanable {

        private final Runnable action;
        private final AtomicBoolean cleaned = new AtomicBoolean();

        PhantomCleanable(Object object, Runnable action) {
            super(object, queue);
            this.action = action;
        }

        @Override
        public void clean() {
            if (!cleaned.compareAndSet(false, true)) return;
            pending.remove(this);
            clear();
            action.run();
        }
    }
}
