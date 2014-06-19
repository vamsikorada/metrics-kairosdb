package net.vandenberge.metrics.kairosdb;

import com.codahale.metrics.Timer;

import java.util.ArrayList;
import java.util.List;

/**
 * Run a timer with a simpler syntax.
 */
public class TimerRunner implements Runnable {

    private final TaggedMetrics taggedMetrics;

    private final Runnable delegate;

    private List<Tag> tags = new ArrayList<>();

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

        try(Timer.Context context = timer.time() ) {
            delegate.run();
        }

    }

}
