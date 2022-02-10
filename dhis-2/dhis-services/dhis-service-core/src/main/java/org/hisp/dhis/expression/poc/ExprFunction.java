package org.hisp.dhis.expression.poc;

interface ExprFunction
{

    Object eval( Expr expr, ExprContext ctx );
}
