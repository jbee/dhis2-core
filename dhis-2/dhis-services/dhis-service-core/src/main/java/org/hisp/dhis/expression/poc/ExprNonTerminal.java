package org.hisp.dhis.expression.poc;

import java.util.Map;

@FunctionalInterface
interface ExprNonTerminal
{
    void parse( Expr expr, ExprContext ctx );

    default boolean isMaybe()
    {
        return false;
    }

    default String name()
    {
        return null;
    }

    default ExprNonTerminal maybe()
    {
        ExprNonTerminal self = this;
        return new ExprNonTerminal()
        {
            @Override
            public void parse( Expr expr, ExprContext ctx )
            {
                self.parse( expr, ctx );
            }

            @Override
            public boolean isMaybe()
            {
                return true;
            }
        };
    }

    default ExprNonTerminal named( String name )
    {
        class Named implements ExprNonTerminal
        {
            final String name;

            private ExprNonTerminal body;

            Named( String name, ExprNonTerminal body )
            {
                this.name = name;
                this.body = body;
            }

            @Override
            public void parse( Expr expr, ExprContext ctx )
            {
                body.parse( expr, ctx );
            }

            @Override
            public String name()
            {
                return name;
            }
        }
        return new Named( name, this instanceof Named ? ((Named) this).body : this );
    }

    default ExprNonTerminal inWS()
    {
        return ( expr, ctx ) -> {
            expr.skipWS();
            parse( expr, ctx );
            expr.skipWS();
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

    static ExprNonTerminal fn( String name, ExprNonTerminal... args )
    {
        return let( ExprType.FN, name, args ).inRound();
    }

    static ExprNonTerminal aggFn( String name, ExprNonTerminal... args )
    {
        return let( ExprType.AGG_FN, name, args ).inRound();
    }

    static ExprNonTerminal let( ExprType type, String name, ExprNonTerminal... args )
    {
        ExprNonTerminal res = ( expr, ctx ) -> {
            ctx.begin( type, name );
            for ( int i = 0; i < args.length; i++ )
            {
                expr.skipWS();
                ExprNonTerminal arg = args[i];
                if ( i > 0 )
                {
                    char c = expr.peek();
                    if ( c != ',' )
                    {
                        if ( arg.isMaybe() )
                        {
                            return;
                        }
                        expr.error( "Expected more arguments" );
                    }
                    expr.skipWS();
                }
                String argName = arg.name();
                ctx.begin( ExprType.ARG, argName != null ? argName : "" + i );
                arg.parse( expr, ctx );
                ctx.end();
            }
            ctx.end();
        };
        return res.named( name );
    }

    static ExprNonTerminal in( char open, ExprNonTerminal body, char close )
    {
        return ( expr, ctx ) -> {
            expr.expect( open );
            body.parse( expr, ctx );
            expr.expect( close );
        };
    }

    static ExprNonTerminal varargs( ExprNonTerminal of )
    {
        ExprNonTerminal varargs = ( expr, ctx ) -> {
            of.parse( expr, ctx );
            // now there might be more
            expr.skipWS();
            char c = expr.peek();
            while ( c == ',' )
            {
                expr.gobble(); // the ","
                expr.skipWS();
                of.parse( expr, ctx );
                expr.skipWS();
                c = expr.peek();
            }
        };
        return varargs.maybe();
    }

    static ExprNonTerminal is( String tag, ExprNonTerminal on )
    {
        return ( expr, ctx ) -> {
            if ( expr.peek( tag ) )
            {
                ctx.literal( ExprType.TAG, tag );
                expr.skipWS();
            }
            on.parse( expr, ctx );
        };
    }

    static ExprNonTerminal tag( String tag, ExprNonTerminal on )
    {
        return ( expr, ctx ) -> {
            tag.chars().forEachOrdered( c -> expr.expect( (char) c ) );
            ctx.literal( ExprType.TAG, tag );
            on.parse( expr, ctx );
        };
    }

    static ExprNonTerminal ref( char indicator, ExprNonTerminal to )
    {
        return ( expr, ctx ) -> {
            expr.skipWS();
            expr.expect( indicator );
            expr.expect( '{' );
            to.parse( expr, ctx );
            expr.expect( '}' );
            expr.skipWS();
        };
    }

    static ExprNonTerminal or( Map<Character, ExprNonTerminal> options )
    {
        return ( expr, ctx ) -> {
            char c = expr.peek();
            ExprNonTerminal option = options.get( c );
            if ( option == null )
            {
                expr.error( "Expected one of " + options.keySet() );
            }
            option.parse( expr, ctx );
        };
    }
}
