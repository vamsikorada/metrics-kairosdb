package com.spinn3r.metrics.kairosdb;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Wait for the most recent report to complete.
 */
public class ReportWaiter {

    protected AtomicReference<CountDownLatch> countDownLatchReference
      = new AtomicReference<>(new CountDownLatch(1));

    /**
     * Get the most recent count down latch.  The reporter replaces it with
     * a new one once it's done a report() call.
     */
    public CountDownLatch getCountDownLatch() {
        return countDownLatchReference.get();
    }

}
