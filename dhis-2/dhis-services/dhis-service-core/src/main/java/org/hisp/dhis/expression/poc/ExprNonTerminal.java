package org.hisp.dhis.expression.poc;

import java.util.ArrayList;
import java.util.List;

public class ExprNonTerminal
{

    static Object expr( Expr expr, ExprContext ctx )
    {
        expr.skipWS();
        char c = expr.peek();
        if ( c == '(' )
        {
            expr.gobble();
            Object res = expr( expr, ctx );
            expr.skipWS();
            expr.expect( ')' );
            expr.skipWS();
            return res;
        }
        // unary operators
        if ( Expr.isOperator( c ) )
        {
            expr.gobble();
            //TODO return ctx.unary(c, expr(expr, ctx));
        }
        // should be a top level function then...
        String name = expr.NAME_LITERAL();
        ExprFunction fn = ctx.getTopLevelFunction( name );
        if ( fn == null )
        {
            expr.error( "unknown function: " + name );
        }
        Object left = fn.eval( expr, ctx );
        c = expr.peek();
        if ( Expr.isOperator( c ) )
        {
            String op = expr.OPERATOR_LITERAL();
            Object right = expr( expr, ctx );
            return null; // TODO ctx.power( left, right );
        }
        expr.skipWS();
        return left;
    }

    static List<Object> expr_list( Expr expr, ExprContext ctx )
    {
        Object first = expr( expr, ctx );
        char c = expr.peek();
        if ( c != ',' )
        {
            return List.of( first );
        }
        List<Object> list = new ArrayList<>();
        while ( (c == ',') )
        {
            list.add( expr( expr, ctx ) );
            c = expr.peek();
        }
        return list;
    }

    static List<Object> expr_expr( Expr expr, ExprContext ctx )
    {
        Object p0 = expr( expr, ctx );
        expr.expect( ',' );
        Object p1 = expr( expr, ctx );
        return List.of( p0, p1 );
    }

    static List<Object> expr_expr_expr( Expr expr, ExprContext ctx )
    {
        Object p0 = expr( expr, ctx );
        expr.expect( ',' );
        Object p1 = expr( expr, ctx );
        expr.expect( ',' );
        Object p2 = expr( expr, ctx );
        return List.of( p0, p1, p2 );
    }

    static List<Object> expr_maybe_expr( Expr expr, ExprContext ctx )
    {
        Object p0 = expr( expr, ctx );
        if ( expr.peek() != ',' )
        {
            return List.of( p0 );
        }
        expr.expect( ',' );
        Object p1 = expr( expr, ctx );
        return List.of( p0, p1 );
    }

}
