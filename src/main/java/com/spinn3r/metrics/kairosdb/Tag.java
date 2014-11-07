package com.spinn3r.metrics.kairosdb;

/**
 * A tag with a name and value
 */
public class Tag {

    private final String name;

    private final String value;

    public Tag(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public String toString() {
        return name + "=" + value;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!( o instanceof Tag )) return false;

        Tag tag = (Tag) o;

        if (!name.equals( tag.name )) return false;
        if (!value.equals( tag.value )) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + value.hashCode();
        return result;
    }

    /**
     * Return true if this tag is valid.
     *
     * @return
     */
    public boolean isValid() {

        if ( name == null || value == null ) {
            return false;
        }

        return isValid( name ) && isValid( value );

    }

    /**
     * Mangle the given data to replace invalid characters so that the resulting
     * data is valid but corrupted.  Used to help log values which may have
     * been incorrectly generated but should be fixed.
     *
     * @param data
     * @return
     */
    public static String mangle( String data ) {
        return mangle( data, '_' );
    }

    public static String mangle( String data, char replacement ) {

        if ( data == null ) {
            return "_";
        }

        StringBuilder buff = new StringBuilder();

        for (int i = 0; i < data.length(); i++) {
            char c  = data.charAt( i );

            if ( isValid( c ) ) {
                buff.append( c );
            } else {
                buff.append( replacement );
            }

        }

        return buff.toString();

    }

    protected static boolean isValid( String data ) {

        for (int i = 0; i < data.length(); i++) {

            char c = data.charAt( i );

            if ( ! isValid( c ) )
                return false;

        }

        return true;

    }

    protected static boolean isValid( char c ) {

        // Metric names, tag names and values are case sensitive and can only
        // contain the following characters: alphanumeric characters, period ".",
        // slash "/", dash "-", and underscore "_".

        if ( c >= 'a' && c <= 'z' )
            return true;

        if ( c >= 'A' && c <= 'Z' )
            return true;

        if ( Character.isDigit( c ) )
            return true;

        switch( c ) {
            case '.':
                return true;
            case '/':
                return true;
            case '-':
                return true;
            case '_':
                return true;
        }

        return false;

    }

}
