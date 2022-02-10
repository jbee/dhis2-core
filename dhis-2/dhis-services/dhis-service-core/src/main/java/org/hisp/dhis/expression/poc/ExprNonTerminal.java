package org.hisp.dhis.expression.poc;

import org.hisp.dhis.expression.poc.ExprContext.ExprType;

import java.util.ArrayList;
import java.util.List;

interface ExprNonTerminal
{

    Object eval( Expr expr, ExprContext ctx );

    default ExprNonTerminal list()
    {
        return list( this );
    }

    default ExprNonTerminal inWS()
    {
        return ( expr, ctx ) -> {
            expr.skipWS();
            Object res = eval( expr, ctx );
            expr.skipWS();
            return res;
        };
    }

    default ExprNonTerminal in( char open, char close )
    {
        return in( open, this, close );
    }

    default ExprNonTerminal inRound()
    {
        return in( '(', ')' );
    }

    default ExprNonTerminal inCurly()
    {
        return in( '{', '}' );
    }

    static ExprNonTerminal fn( String name, ExprNonTerminal is )
    {
        return let( ExprType.FN, name, is.inRound() );
    }

    static ExprNonTerminal let( ExprType type, String name, ExprNonTerminal be )
    {
        return ( expr, ctx ) -> {
            ctx.open( type, name );
            Object res = be.eval( expr, ctx );
            ctx.close();
            return res;
        };
    }

    static ExprNonTerminal in( char open, ExprNonTerminal body, char close )
    {
        return ( expr, ctx ) -> {
            expr.expect( open );
            Object res = body.eval( expr, ctx );
            expr.expect( close );
            return res;
        };
    }

    static ExprNonTerminal list( ExprNonTerminal of )
    {
        return ( expr, ctx ) -> {
            Object first = of.eval( expr, ctx );
            expr.skipWS();
            char c = expr.peek();
            if ( c != ',' )
            {
                return List.of( first );
            }
            List<Object> list = new ArrayList<>();
            while ( (c == ',') )
            {
                list.add( of.eval( expr, ctx ) );
                expr.skipWS();
                c = expr.peek();
            }
            return list;
        };
    }

    static ExprNonTerminal seq( ExprNonTerminal... elements )
    {
        if ( elements.length == 1 )
        {
            return elements[0];
        }
        return ( expr, ctx ) -> {
            Object[] values = new Object[elements.length];
            for ( int i = 0; i < values.length; i++ )
            {
                values[i] = elements[i].eval( expr, ctx );
            }
            return List.of( values );
        };
    }

    static ExprNonTerminal maybe( char when, ExprNonTerminal then )
    {
        return ( expr, ctx ) -> {
            if ( expr.peek() != when )
            {
                return null;
            }
            expr.expect( when );
            return then.eval( expr, ctx );
        };
    }

}
