package com.uky.corpses.corpse;

import java.lang.management.ManagementFactory;

/** Guards client snapshot construction against allocating an unused collision world per body. */
final class RagdollAllocationCheck {

    static void run() {
        java.lang.management.ThreadMXBean platform = ManagementFactory.getThreadMXBean();
        if (!(platform instanceof com.sun.management.ThreadMXBean)) return;
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) platform;
        if (!threads.isThreadAllocatedMemorySupported()) return;
        threads.setThreadAllocatedMemoryEnabled(true);
        float[] state = new float[Ragdoll.STATE_SIZE];
        for (int part = 0; part < Skeleton.PARTS; part++) state[part * Ragdoll.STRIDE + 6] = 1;
        for (int i = 0; i < 32; i++) new Ragdoll(state, 0);
        long thread = Thread.currentThread()
            .getId(), before = threads.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 100; i++) new Ragdoll(state, 0);
        long bytesPerBody = (threads.getThreadAllocatedBytes(thread) - before) / 100;
        assert bytesPerBody < 131072 : "Client snapshot allocated a collision world: " + bytesPerBody;
        System.out.println("Ragdoll snapshot allocation: " + bytesPerBody + " bytes per body");
    }
}
