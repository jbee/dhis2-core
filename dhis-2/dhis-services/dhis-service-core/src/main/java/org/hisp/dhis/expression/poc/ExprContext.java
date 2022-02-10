package org.hisp.dhis.expression.poc;

interface ExprContext
{

    enum ExprType
    {FN}

    void open( ExprType type, String name );

    void close();

    ExprNonTerminal getTopLevelFunction( String name );

    ExprNonTerminal getDotLevelFunction( String name );

}
