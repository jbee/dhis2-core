package org.hisp.dhis.expression.poc;

final class Expr
{
    public static final char EOF = 0;

    public void error( String desc )
    {
        throw new BadExpressionException( this, desc );
    }

    public static class BadExpressionException extends IllegalArgumentException
    {
        final Expr expr;

        BadExpressionException( Expr expr, String msg )
        {
            super( msg );
            this.expr = expr;
        }
    }

    private interface CharPredicate
    {
        boolean matches( char c );
    }

    private final char[] expr;

    private char next;

    private int pos;

    private boolean peeked = true;

    Expr( String expr )
    {
        this.expr = expr.toCharArray();
        this.pos = 0;
        this.next = this.expr[pos];
    }

    char peek()
    {
        if ( !peeked )
        {
            next = pos >= expr.length ? EOF : expr[pos++];
            peeked = true;
        }
        return next;
    }

    boolean peek( String ahead )
    {
        if ( peek() != ahead.charAt( 0 ) )
        {
            return false;
        }
        for ( int i = 1; i < ahead.length(); i++ )
        {
            if ( pos + i >= expr.length || expr[pos + i] != ahead.charAt( i ) )
            {
                return false;
            }
        }
        return true;
    }

    void expect( char c )
    {
        if ( c != peek() )
        {
            error( "expected " + c );
        }
        gobble();
    }

    private void expect( String desc, CharPredicate test )
    {
        if ( !test.matches( peek() ) )
        {
            error( "expected " + desc );
        }
        gobble();
    }

    void skipWS()
    {
        skipWhile( Expr::isWS );
    }

    private void skipWhile( CharPredicate test )
    {
        while ( test.matches( peek() ) )
        {
            gobble();
        }
    }

    void gobble()
    {
        peeked = false;
    }

    /*
    Literals
    (A text pattern that is an atomic semantic unit)
     */

    private String literal( int start )
    {
        return new String( expr, start, pos - start );
    }

    private String nextLiteral( String desc, CharPredicate test )
    {
        int s = pos;
        skipWhile( test );
        if ( pos == s )
        {
            error( "expected " + desc );
        }
        return literal( s );
    }

    private String nextLiteral( String desc, CharPredicate... seq )
    {
        int s = pos;
        for ( CharPredicate test : seq )
        {
            if ( !test.matches( peek() ) )
            {
                error( "expected " + desc );
            }
            gobble();
        }
        return literal( s );
    }

    private String nextLiteral( String desc, String expected )
    {
        for ( int i = 0; i < expected.length(); i++ )
        {
            if ( peek() != expected.charAt( i ) )
            {
                error( "expected " + desc );
            }
            gobble();
        }
        return expected;
    }

    String OPERATOR_LITERAL()
    {
        char c = peek();
        if ( isOperator1( c ) )
        {
            // + - * / % . ^
            gobble();
            return literal( pos - 1 );
        }
        if ( isOperator2( c ) )
        {
            gobble();
            // && || == !=
            expect( c == '!' ? '=' : c );
            return literal( pos - 2 );
        }
        else if ( isOperator12( c ) )
        {
            gobble();
            if ( peek() == '=' )
            {
                gobble();
                return literal( pos - 2 );
            }
            return literal( pos - 1 );
        }
        error( "expected operator" );
        return null;
    }

    String NAME_LITERAL()
    {
        return nextLiteral( "name", Expr::isName );
    }

    String INTEGER_LITERAL()
    {
        return nextLiteral( "integer", Expr::isNum );
    }

    String NUMERIC_LITERAL()
    {
        int s = pos;
        boolean hasInt = isNum( peek() );
        if ( hasInt )
        {
            skipWhile( Expr::isNum );
        }
        if ( !hasInt || peek() == '.' )
        {
            expect( '.' );
            skipWhile( Expr::isNum );
        }
        char c = peek();
        if ( c == 'e' || c == 'E' )
        {
            gobble(); // e/E
            c = peek();
            if ( c == '+' || c == '-' )
            {
                gobble(); // +/-
            }
            skipWhile( Expr::isNum );
        }
        return literal( s );
    }

    String BOOLEAN_LITERAL()
    {
        return nextLiteral( "boolean", peek() == 't' ? "true" : "false" );
    }

    String DATE_LITERAL()
    {
        // [1-9] [0-9] [0-9] [0-9] '-' [0-1]? [0-9] '-' [0-3]? [0-9]
        int s = pos;
        expect( "digit", Expr::isNum );
        expect( "digit", Expr::isNum );
        expect( "digit", Expr::isNum );
        expect( "digit", Expr::isNum );
        expect( '-' );
        expect( "digit", Expr::isNum );
        if ( peek() != '-' )
        {
            expect( "digit", Expr::isNum );
        }
        else
        {
            expect( '-' );
        }
        expect( "digit", Expr::isNum );
        if ( isNum( peek() ) )
        {
            gobble();
        }
        return literal( s );
    }

    String UID_LITERAL()
    {
        return nextLiteral( "uid",
            Expr::isAlpha,
            Expr::isAlphaNum, Expr::isAlphaNum, Expr::isAlphaNum, Expr::isAlphaNum,
            Expr::isAlphaNum, Expr::isAlphaNum, Expr::isAlphaNum, Expr::isAlphaNum );
    }

    /*
    Symbols
    (The category of a single character)
     */

    static boolean isName( char c )
    {
        return c == ':' || isAlphaNum( c );
    }

    static boolean isIdentifier( char c )
    {
        return c == '_' || isAlpha( c );
    }

    static boolean isOperator( char c )
    {
        return isOperator1( c ) || isOperator2( c ) || isOperator12( c );
    }

    static boolean isOperator1( char c )
    {
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '%' || c == '.' || c == '^';
    }

    static boolean isOperator2( char c )
    {
        return c == '&' || c == '|' || c == '=' || c == '!';
    }

    static boolean isOperator12( char c )
    {
        return c == '<' || c == '>';
    }

    static boolean isWS( char c )
    {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    static boolean isAlpha( char c )
    {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
    }

    static boolean isAlphaNum( char c )
    {
        return isAlpha( c ) || isNum( c );
    }

    static boolean isNum( char c )
    {
        return c >= '0' && c <= '9';
    }

    static boolean isHexDigit( char c )
    {
        return c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F' || isNum( c );
    }

}
