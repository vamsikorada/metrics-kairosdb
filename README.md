metrics-kairosdb
================

KairosDB plugin for the Metrics library

This is forked from the original KairosDB metrics impl written by Tom van den
Berge

https://github.com/tomvandenberge/metrics-kairosdb

The biggest change is the ability for individual metrics to have tags, instead
of tags globally across the entire JVM instance.

This allows us to accomplish things like broadcasting the number of HTTP requests
but also including the domain name of the HTTP request.

In KairosDB this allows additional features such as tracking a specific domain
or grouping by an entire domain which is important.

The way this is accomplished is through a new metric name convention of:

com.example.acme.daemon.http.requests?domain=msnbc.com

Where the metric has a name of:

 com.example.acme.daemon.http.requests

and the domain we're fetching (and want metrics on) is msnbc.com.

The API is simple to use.

    MetricRegistry metricRegistry = new MetricRegistry();

    TaggedMetrics taggedMetrics = new TaggedMetrics( metricRegistry,
                                                     InvalidTagPolicy.FAIL,
                                                     DuplicateTagPolicy.FAIL );

    Meter requests = taggedMetrics.meter( Http.class, "requests", tag( "domain", "msnbc.com" ) );

One significant issue is how to handle duplicate or invalid tags.

KairosDB (for now) doesn't support unicode tags.  You can set the policy how these
are handled including failing and throwing a runtime exception, ignoring the
invalid tag, or mangling it (forcing it valid).

Existing metrics should still work.  If you're using a regular MetricRegistry
you can still use this implementation, you just won't be able to place tags on
metrics.

Next Steps
==========

The big issue I have to work on next is how to handle automatically garbage
collecting metrics that aren't broadcast very often.  Right now, if you're
creating lots of metrics with tags, these are going to stay around in the
MetricRegistry for a long time and never garbage collected.

In a long running JVM this will be a memory leak and the JVM will eventually run
out of memory and crash.  This ONLY happens if you're creating millions of
metrics with lots of unique tags.

If you're only using a few tags you're not going to have this issue.

Some potential solutions include placing a timestamp on a metric every time it's
updated and then evicting metrics that become stale.

Either that or having rolling metric registries that get thrown away
periodically.