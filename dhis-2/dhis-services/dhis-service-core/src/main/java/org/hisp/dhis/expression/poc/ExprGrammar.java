package org.hisp.dhis.expression.poc;

import static org.hisp.dhis.expression.poc.ExprNonTerminal.fn;
import static org.hisp.dhis.expression.poc.ExprNonTerminal.list;

public interface ExprGrammar
{
    /*
    it='firstNonNull(' expr (',' expr )* ')'
    |   it='greatest(' expr (',' expr )* ')'
    |   it='if(' expr ',' expr ',' expr ')'
    |   it='isNotNull(' expr ')'
    |   it='isNull(' expr ')'
    |   it='least(' expr (',' expr )* ')'
    |   it='log(' expr (',' expr )? ')'
    |   it='log10(' expr ')'
    |   it='orgUnit.ancestor(' WS* UID WS* (',' WS* UID WS* )* ')'
    |   it='orgUnit.group(' WS* UID WS* (',' WS* UID WS* )* ')'
     */

    ExprNonTerminal expr = Expr::expr;

    ExprNonTerminal
        firstNonNull = ( input, ctx ) -> fn( "firstNonNull", list( expr ) ),
        greatest = ( input, ctx ) -> fn( "greatest", list( expr ) );
}
