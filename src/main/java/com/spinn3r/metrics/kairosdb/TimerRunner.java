package com.spinn3r.metrics.kairosdb;

import com.codahale.metrics.Timer;

import java.util.ArrayList;
import java.util.List;

/**
 * Run a timer with a simpler syntax.
 */
public class TimerRunner implements Runnable {

    private final TaggedMetrics taggedMetrics;

    private final Runnable delegate;

    private Timer timer = null;

    public TimerRunner(TaggedMetrics taggedMetrics, Runnable delegate) {
        this.taggedMetrics = taggedMetrics;
        this.delegate = delegate;
    }

    public TimerRunner withTag( Tag tag0 ) {
        timer = taggedMetrics.timer( delegate.getClass(), "run", tag0 );
        return this;
    }

    @Override
    public void run() {

        // TODO: this will log failure too... that's not what we want!
        try(Timer.Context context = timer.time() ) {
            delegate.run();
        }

    }

}
