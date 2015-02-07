package com.spinn3r.metrics.kairosdb;

/**
 *
 */
@SuppressWarnings("serial")
public class DuplicateTagException extends RuntimeException {

    public DuplicateTagException(String message) {
        super( message );
    }

    public DuplicateTagException(String message, Throwable cause) {
        super( message, cause );
    }

}
