package com.spinn3r.metrics.kairosdb;

/**
 * How should we handle two tags with the same name.
 */
public enum DuplicateTagPolicy {

    /**
     * Fail when there's a duplicate tag.
     */
    FAIL,

    /**
     * Accept only the first tag, ignoring any additional tags.
     */
    IGNORE,

    /**
     * Ignore the tags and log that we have a duplicate.
     */
    IGNORE_AND_LOG,

}
