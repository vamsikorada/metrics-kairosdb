package net.vandenberge.metrics.kairosdb;

import com.codahale.metrics.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contains code for running garbage collection and purging of old metrics that
 * are no longer being used.
 */
public class GCMetricIndex {

    private MetricRegistry registry;

    private Clock clock;

    private boolean enabled;

    private long garbageCollectionInterval = 5 * 60 * 1000;

    public GCMetricIndex(MetricRegistry registry, Clock clock, boolean enabled) {
        this.registry = registry;
        this.clock = clock;
        this.enabled = enabled;
    }

    // when garbageCollectAndDeriveTimers is enabled, we keep a side index of
    // metrics that are being used, if they're not used for a while, we GC them.

    protected Map<String,Long> lastUpdatedIndex = new ConcurrentHashMap<>();

    public void gc() {

        if ( ! enabled )
            return;

        this.registry.removeMatching( new MetricFilter() {

            @Override
            public boolean matches(String name, Metric metric) {

                if ( metric instanceof Counter) {

                    Counter counter = (Counter)metric;

                    if ( counter.getCount() > 0 ) {
                        // it's obviously being used so we can't remove it.
                        return false;
                    }

                    if ( ! lastUpdatedIndex.containsKey( name ) ) {
                        // it isn't in the GC index so it we can't remove it yet
                        return false;
                    }


                    long lastUpdated = lastUpdatedIndex.get( name );
                    long now = clock.getTime();

                    boolean remove = lastUpdated < ( now - garbageCollectionInterval );

                    if ( remove ) {
                        lastUpdatedIndex.remove( name );
                    }

                    return remove;

                }

                return false;

            }

        } );


    }

    public void touch( String metric ) {

        if ( ! enabled )
            return;

        lastUpdatedIndex.put( metric, clock.getTime() );
    }

    public int size() {
        return lastUpdatedIndex.size();
    }

}
