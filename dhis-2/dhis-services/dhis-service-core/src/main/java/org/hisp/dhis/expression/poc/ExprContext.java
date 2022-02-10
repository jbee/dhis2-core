package org.hisp.dhis.expression.poc;

interface ExprContext
{

    ExprFunction getTopLevelFunction( String name );

    ExprFunction getDotLevelFunction( String name );
}
