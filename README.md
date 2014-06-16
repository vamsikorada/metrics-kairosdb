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