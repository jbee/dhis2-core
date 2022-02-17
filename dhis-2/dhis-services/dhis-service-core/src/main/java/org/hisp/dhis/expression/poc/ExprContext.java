package org.hisp.dhis.expression.poc;

interface ExprContext
{

    void begin( ExprType type, String value );

    void end();

    default void literal( ExprType type, String value )
    {
        begin( type, value );
        end();
    }

    ExprNonTerminal getTopLevelFunction( String name );

    ExprNonTerminal getDotLevelFunction( String name );

}
