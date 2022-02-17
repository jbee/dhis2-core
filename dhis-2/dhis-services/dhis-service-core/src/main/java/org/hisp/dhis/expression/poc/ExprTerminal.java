package org.hisp.dhis.expression.poc;

public interface ExprTerminal extends ExprNonTerminal
{
    @Override
    default void parse( Expr expr, ExprContext ctx )
    {
        ExprType type = literalOf();
        ctx.literal( type, expr.LITERAL( type ) );
    }

    ExprType literalOf();

    String EXAMPLE = "#{deabcdefghA.W3Ba8wgjgFK}+#{deabcdefghB.W3Ba8wgjgFK}+avg(#{deabcdefghA.W3Ba8wgjgFK}+#{deabcdefghB.W3Ba8wgjgFK})+1.5*stddev(#{deabcdefghA.W3Ba8wgjgFK}+#{deabcdefghB.W3Ba8wgjgFK})+#{deabcdefghC.W3Ba8wgjgFK}-#{deabcdefghD.W3Ba8wgjgFK}";
}
