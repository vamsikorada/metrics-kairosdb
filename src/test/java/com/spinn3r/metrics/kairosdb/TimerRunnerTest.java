package com.spinn3r.metrics.kairosdb;

import com.codahale.metrics.MetricRegistry;
import junit.framework.TestCase;

import java.util.concurrent.atomic.AtomicInteger;

import static com.spinn3r.metrics.kairosdb.TaggedMetrics.tag;

public class TimerRunnerTest extends TestCase {

    public void testRun() throws Exception {

        final AtomicInteger value = new AtomicInteger( 0 );

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                value.getAndIncrement();
            }
        };

        MetricRegistry metricRegistry = new MetricRegistry();

        TaggedMetrics taggedMetrics = new TaggedMetrics( metricRegistry,
                                                         InvalidTagPolicy.FAIL,
                                                         DuplicateTagPolicy.FAIL );

        taggedMetrics.timer( runnable )
            .withTag( tag( "foo", "bar" ) )
            .run();

        assertEquals( 1, value.get() );

        System.out.printf( "%s\n", metricRegistry.getMetrics().keySet() );

        //assertTrue( metricRegistry.getMeters().containsKey( "net.vandenberge.metrics.kairosdb.TimerRunnerTest$1.run?foo=bar" ) );

    }
}