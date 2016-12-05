package com.spinn3r.metrics.kairosdb;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.codahale.metrics.*;
import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.spinn3r.metrics.kairosdb.TaggedMetrics.parse;

/**
 * A reporter which publishes metric values to a KairosDB server.
 *
 * @see <a href="https://code.google.com/p/kairosdb/">KairosDB - Fast scalable
 *      time series database</a>
 */
public class KairosDbReporter extends ScheduledReporter {
    
    private static final Pattern TAG_PATTERN = Pattern.compile("[\\p{Alnum}\\.\\-_/]+");
    private static final Logger LOGGER = LoggerFactory.getLogger(KairosDbReporter.class);
    
    private final MetricRegistry registry;
    
    private final KairosDb client;
    private final Clock clock;
    private final String prefix;
    
    private boolean garbageCollectAndDeriveTimers = false;
    
    protected GCMetricIndex gcMetricIndex = null;
    
    private ReportWaiter reportWaiter = new ReportWaiter();
    
    private KairosDbReporter(MetricRegistry registry,
                             KairosDb kairosDb,
                             Clock clock,
                             String prefix,
                             TimeUnit rateUnit,
                             TimeUnit durationUnit,
                             MetricFilter filter,
                             boolean garbageCollectAndDeriveTimers) throws IOException {
        
        super(registry, "kairosdb-reporter", filter, rateUnit, durationUnit);
        this.registry = registry;
        this.client = kairosDb;
        this.clock = clock;
        this.prefix = prefix;
        this.garbageCollectAndDeriveTimers = garbageCollectAndDeriveTimers;
        this.gcMetricIndex = new GCMetricIndex( registry, clock, garbageCollectAndDeriveTimers );
        
    }
    
    private KairosDbReporter(MetricRegistry registry,
                             KairosDb kairosDb,
                             Clock clock,
                             String prefix,
                             TimeUnit rateUnit,
                             TimeUnit durationUnit,
                             ScheduledExecutorService executor,
                             MetricFilter filter,
                             boolean garbageCollectAndDeriveTimers) throws IOException {
        
        super(registry, "kairosdb-reporter", filter, rateUnit, durationUnit, executor);
        this.registry = registry;
        this.client = kairosDb;
        this.clock = clock;
        this.prefix = prefix;
        this.garbageCollectAndDeriveTimers = garbageCollectAndDeriveTimers;
        this.gcMetricIndex = new GCMetricIndex( registry, clock, garbageCollectAndDeriveTimers );
        
    }
    
    /**
     * Returns a new {@link Builder} for {@link KairosDbReporter}.
     *
     * @param registry
     *            the registry to report
     * @return a {@link Builder} instance for a {@link KairosDbReporter}
     */
    public static Builder forRegistry(MetricRegistry registry) {
        return new Builder(registry);
    }
    
    public ReportWaiter getReportWaiter() {
        return reportWaiter;
    }
    
    /**
     * A builder for {@link KairosDbReporter} instances. Defaults to not using a
     * prefix, using the default clock, converting rates to events/second,
     * converting durations to milliseconds, and not filtering metrics.
     */
    public static class Builder {
        private final MetricRegistry registry;
        private Clock clock;
        private String prefix;
        private TimeUnit rateUnit;
        private TimeUnit durationUnit;
        private MetricFilter filter;
        private Map<String, String> tags;
        private boolean garbageCollectAndDeriveCounters = false;
        private ScheduledExecutorService executor = null;
        
        private Builder(MetricRegistry registry) {
            this.registry = registry;
            this.clock = Clock.defaultClock();
            this.prefix = null;
            this.rateUnit = TimeUnit.SECONDS;
            this.durationUnit = TimeUnit.MILLISECONDS;
            this.filter = MetricFilter.ALL;
            this.tags = new LinkedHashMap<>();
        }
        
        /**
         * When true, we assume that counters are actually derived every time
         * they are reported.
         *
         * IE, that they track rate, and every time we broadcast them they
         * need to be reset.
         *
         * We also remove them from the index if the value is zero after
         * they've been broadcast.  This prevents memory issues which would
         * arise from sparse metrics.
         *
         * @param garbageCollectAndDeriveCounters
         * @return
         */
        public Builder garbageCollectAndDeriveCounters(boolean garbageCollectAndDeriveCounters) {
            this.garbageCollectAndDeriveCounters = garbageCollectAndDeriveCounters;
            return this;
        }
        
        /**
         * Use the given {@link Clock} instance for the time.
         *
         * @param clock
         *            a {@link Clock} instance
         * @return {@code this}
         */
        public Builder withClock(Clock clock) {
            this.clock = clock;
            return this;
        }
        
        public Builder withScheduledExecutorService(ScheduledExecutorService executor) {
            this.executor = executor;
            return this;
        }
        
        /**
         * Prefix all metric names with the given string.
         *
         * @param prefix
         *            the prefix for all metric names
         * @return {@code this}
         */
        public Builder prefixedWith(String prefix) {
            this.prefix = prefix;
            return this;
        }
        
        /**
         * Convert rates to the given time unit.
         *
         * @param rateUnit
         *            a unit of time
         * @return {@code this}
         */
        public Builder convertRatesTo(TimeUnit rateUnit) {
            this.rateUnit = rateUnit;
            return this;
        }
        
        /**
         * Convert durations to the given time unit.
         *
         * @param durationUnit
         *            a unit of time
         * @return {@code this}
         */
        public Builder convertDurationsTo(TimeUnit durationUnit) {
            this.durationUnit = durationUnit;
            return this;
        }
        
        /**
         * Add a tag to each submitted metric. Both tag name and value must match the following regular expression:
         * <pre>[\p{Alnum}\.\-_/]+</pre>
         *
         * @param tagName
         *            the tag name
         * @param tagValue
         *            the tag value
         * @return {@code this}
         */
        public Builder withTag(String tagName, String tagValue) {
            validateTag(tagName, tagValue);
            this.tags.put(tagName, tagValue);
            return this;
        }
        
        /**
         * Only report metrics which match the given filter.
         *
         * @param filter
         *            a {@link MetricFilter}
         * @return {@code this}
         */
        public Builder filter(MetricFilter filter) {
            this.filter = filter;
            return this;
        }
        
        /**
         * Builds a {@link KairosDbReporter} with the given properties, sending
         * metrics using the given {@link KairosDb} client.
         *
         * @param kairosDb
         *            a {@link KairosDb} client
         * @return a {@link KairosDbReporter}
         */
        public KairosDbReporter build(KairosDb kairosDb) throws IOException {
            kairosDb.setTags(tags);
            if ( executor != null ) {
                return new KairosDbReporter(registry, kairosDb, clock, prefix, rateUnit, durationUnit, executor, filter, garbageCollectAndDeriveCounters);
            } else {
                return new KairosDbReporter(registry, kairosDb, clock, prefix, rateUnit, durationUnit, filter, garbageCollectAndDeriveCounters);
            }
            
        }
        
        private void validateTag(String tagName, String tagValue) {
            validateTag(tagName);
            validateTag(tagValue);
        }
        
        static void validateTag(String tag) {
            if (tag == null || !TAG_PATTERN.matcher(tag).matches()) {
                throw new IllegalArgumentException(
                                                   "\""
                                                   + tag
                                                   + "\" is not a valid tag name or value; it can only contain alphanumeric characters, period, slash, dash and underscore!");
            }
        }
    }
    
    
    @Override
    @SuppressWarnings( "rawtypes" )
    public void report(SortedMap<String, Gauge> gauges,
                       SortedMap<String, Counter> counters,
                       SortedMap<String, Histogram> histograms,
                       SortedMap<String, Meter> meters,
                       SortedMap<String, Timer> timers) {
        
        final long timestamp = clock.getTime();
        
        Stopwatch stopwatch = Stopwatch.createStarted();
        
        try {
            
            LOGGER.info( String.format( "Reporting metrics to %s..." , client ) );
            
            connect();
            
            withTiming( "Reporting gauges", () -> {
                
                for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
                    LOGGER.debug("Reporting: " + entry.getKey());
                    reportGauge(entry.getKey(), entry.getValue(), timestamp);
                }
                
            } );
            
            withTiming( "Reporting counters", () -> {
                for (Map.Entry<String, Counter> entry : counters.entrySet()) {
                    LOGGER.debug("Reporting: " + entry.getKey());
                    reportCounter(entry.getKey(), entry.getValue(), timestamp);
                }
                
            } );
            
            withTiming("Reporting histograms", () -> {
                for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
                    LOGGER.debug("Reporting: " + entry.getKey());
                    reportHistogram(entry.getKey(), entry.getValue(), timestamp);
                }
            } );
            
            withTiming("Reporting meters", () -> {
                System.out.println("In meters");
                for (Map.Entry<String, Meter> entry : meters.entrySet()) {
                    LOGGER.debug("Reporting: " + entry.getKey());
                    System.out.println("Reporting: " + entry.getKey() + " - " + entry.getValue().getCount() + " - " + timestamp );
                    reportMetered(entry.getKey(), entry.getValue(), timestamp);
                }
            } );
            
            withTiming("Reporting timers", () -> {
                for (Map.Entry<String, Timer> entry : timers.entrySet()) {
                    LOGGER.debug("Reporting: " + entry.getKey());
                    reportTimer(entry.getKey(), entry.getValue(), timestamp);
                }
            } );
            
            withTiming("Finalizing", () -> {
                gcMetricIndex.gc();
                
                // count down so that anyone listening to the current latch
                // will be notified that we have reported.
                reportWaiter.countDownLatchReference.get().countDown();
                
                // now give us a new latch for the next report...
                reportWaiter.countDownLatchReference.set(new CountDownLatch(1));
            } );
            
        } catch (Throwable t) {
            LOGGER.warn("Unable to report to server", client, t);
        } finally {
            
            try {
                disconnect();
            } catch (IOException e) {
                LOGGER.debug("Error disconnecting from server", client, e);
            }
            
            LOGGER.info( String.format( "Reporting metrics to %s...done (duration=%s)" , client, stopwatch.stop() ) );
            
        }
        
    }
    
    private void connect() throws IOException {
        
        withTiming("Connecting to: " + client, client::connect);
        
        
    }
    
    private void disconnect() throws IOException {
        withTiming("Disconnecting from: " + client, client::close);
    }
    
    private void withTiming( String message, IORunnable runnable ) throws IOException {
        
        Stopwatch stopwatch = Stopwatch.createStarted();
        
        try {
            LOGGER.info( String.format( "%s ... ", message ) );
            runnable.run();
        } finally {
            LOGGER.info( String.format( "%s ... done (duration=%s)", message, stopwatch.stop() ) );
        }
        
    }
    
    private void reportTimer(String name, Timer timer, long timestamp) throws IOException {
        TaggedMetric taggedMetric = parse( name );
        reportTimer( taggedMetric.getName(), timer, timestamp, taggedMetric.getTags() );
    }
    
    private void reportTimer(String name, Timer timer, long timestamp, Map<String,String> tags) throws IOException {
        
        final Snapshot snapshot = timer.getSnapshot();
        tags.put("MetricSubType", name);
        
        client.send("max", format(convertDuration(snapshot.getMax())), timestamp, tags);
        client.send("mean", format(convertDuration(snapshot.getMean())), timestamp, tags);
        client.send("min", format(convertDuration(snapshot.getMin())), timestamp, tags);
        client.send("stddev", format(convertDuration(snapshot.getStdDev())), timestamp, tags);
        client.send("p50", format(convertDuration(snapshot.getMedian())), timestamp, tags);
        client.send("p75", format(convertDuration(snapshot.get75thPercentile())), timestamp, tags);
        client.send("p95", format(convertDuration(snapshot.get95thPercentile())), timestamp, tags);
        client.send("p98", format(convertDuration(snapshot.get98thPercentile())), timestamp, tags);
        client.send("p99", format(convertDuration(snapshot.get99thPercentile())), timestamp, tags);
        client.send("p999", format(convertDuration(snapshot.get999thPercentile())), timestamp, tags);
        
        
        
        reportMetered(name, timer, timestamp, tags);
    }
    
    private void reportMetered(String name, Metered meter, long timestamp) throws IOException {
        TaggedMetric taggedMetric = parse( name );
        
        reportMetered( taggedMetric.getName(), meter, timestamp, taggedMetric.getTags() );
        System.out.println(taggedMetric.getName() + " - " + meter + "-" + timestamp + "-" + taggedMetric.getTags());
    }
    
    private void reportMetered(String name, Metered meter, long timestamp, Map<String,String> tags) throws IOException {
        
        
        tags.put("MetricSubType", name);
        
        client.send("count", format(meter.getCount()), timestamp, tags);
        client.send("m1_rate", format(convertRate(meter.getOneMinuteRate())), timestamp, tags);
        client.send("m5_rate", format(convertRate(meter.getFiveMinuteRate())), timestamp, tags);
        client.send("m15_rate", format(convertRate(meter.getFifteenMinuteRate())), timestamp, tags);
        client.send("mean_rate", format(convertRate(meter.getMeanRate())), timestamp, tags);
        
    }
    
    private void reportHistogram(String name, Histogram histogram, long timestamp) throws IOException {
        TaggedMetric taggedMetric = parse( name );
        reportHistogram( taggedMetric.getName(), histogram, timestamp, taggedMetric.getTags() );
    }
    
    private void reportHistogram(String name, Histogram histogram, long timestamp, Map<String,String> tags) throws IOException {
        final Snapshot snapshot = histogram.getSnapshot();
        
        tags.put("MetricSubType", name);
        
        client.send("count", format(histogram.getCount()), timestamp, tags);
        client.send("max", format(snapshot.getMax()), timestamp, tags);
        client.send("mean", format(snapshot.getMean()), timestamp, tags);
        client.send("min", format(snapshot.getMin()), timestamp, tags);
        client.send("stddev", format(snapshot.getStdDev()), timestamp, tags);
        client.send("p50", format(snapshot.getMedian()), timestamp, tags);
        client.send("p75", format(snapshot.get75thPercentile()), timestamp, tags);
        client.send("p95", format(snapshot.get95thPercentile()), timestamp, tags);
        client.send("p98", format(snapshot.get98thPercentile()), timestamp, tags);
        client.send("p99", format(snapshot.get99thPercentile()), timestamp, tags);
        client.send("p999", format(snapshot.get999thPercentile()), timestamp, tags);
        
    }
    
    private void reportCounter(String name, Counter counter, long timestamp) throws IOException {
        
        long count = counter.getCount();
        
        TaggedMetric taggedMetric = parse( name );
        reportCounter( taggedMetric.getName(), counter, count, timestamp, taggedMetric.getTags() );
        
        if ( garbageCollectAndDeriveTimers ) {
            
            if ( count > 0 ) {
                gcMetricIndex.touch( name );
            }
            
            counter.dec( count );
        }
        
    }
    
    private void reportCounter(String name, Counter counter, long count, long timestamp, Map<String,String> tags) throws IOException {
        
        tags.put("MetricSubType", name);        
        client.send("count", format(count), timestamp, tags);
        
    }
    
    private void reportGauge(String name, Gauge<?> gauge, long timestamp) throws IOException {
        TaggedMetric taggedMetric = parse( name );
        reportGauge( taggedMetric.getName(), gauge, timestamp, taggedMetric.getTags() );
    }
    
    private void reportGauge(String name, Gauge<?> gauge, long timestamp, Map<String,String> tags) throws IOException {
        tags.put("MetricSubType", name);
        
        final String value = format(gauge.getValue());
        if (value != null) {
            client.send(prefix(name), value, timestamp, tags);
        }
    }
    
    private String format(Object o) {
        if (o instanceof Float) {
            return format(((Float) o).doubleValue());
        } else if (o instanceof Double) {
            return format(((Double) o).doubleValue());
        } else if (o instanceof Byte) {
            return format(((Byte) o).longValue());
        } else if (o instanceof Short) {
            return format(((Short) o).longValue());
        } else if (o instanceof Integer) {
            return format(((Integer) o).longValue());
        } else if (o instanceof Long) {
            return format(((Long) o).longValue());
        }
        return null;
    }
    
    private String prefix(String... components) {
        return MetricRegistry.name(prefix, components);
    }
    
    private String format(long n) {
        return Long.toString(n);
    }
    
    private String format(double v) {
        return Double.toString(v);
    }
}
