package com.uky.corpses.corpse;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.stephengold.joltjni.JoltPhysicsObject;

/** Exercises the rewritten Cleaner and string concatenation through the original JNI wrapper. */
final class JoltJava8Check {

    static void run() {
        JoltPhysicsObject.startCleaner();
        AtomicInteger releases = new AtomicInteger();
        Resource resource = new Resource(releases);
        assert resource.toString() != null;
        resource.close();
        resource.close();
        assert releases.get() == 1 : "explicit cleanup ran more than once";
        WeakReference<Resource> abandoned = abandon(releases);
        long deadline = System.nanoTime() + 10000000000L;
        while ((abandoned.get() != null || releases.get() != 2) && System.nanoTime() < deadline) {
            System.gc();
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread()
                    .interrupt();
                throw new AssertionError(interrupted);
            }
        }
        assert abandoned.get() == null && releases.get() == 2 : "automatic cleanup did not release the wrapper";
        System.out.println("Jolt Java " + System.getProperty("java.version") + ": explicit and GC cleanup passed");
    }

    private static WeakReference<Resource> abandon(AtomicInteger releases) {
        return new WeakReference<>(new Resource(releases));
    }

    private static final class Resource extends JoltPhysicsObject {

        Resource(AtomicInteger releases) {
            setVirtualAddress(1, releases::incrementAndGet);
        }
    }
}
