package org.hisp.dhis.expression.poc;

import java.util.Map;

import static org.hisp.dhis.expression.poc.ExprNonTerminal.aggFn;
import static org.hisp.dhis.expression.poc.ExprNonTerminal.fn;
import static org.hisp.dhis.expression.poc.ExprNonTerminal.is;
import static org.hisp.dhis.expression.poc.ExprNonTerminal.or;
import static org.hisp.dhis.expression.poc.ExprNonTerminal.tag;
import static org.hisp.dhis.expression.poc.ExprNonTerminal.varargs;

public interface ExprGrammar
{
    /*
    Terminals (basic building blocks)
     */

    ExprTerminal
        numericLiteral = () -> ExprType.NUMERIC_LITERAL,
        stringLiteral = () -> ExprType.STRING_LITERAL,
        UID = () -> ExprType.UID;

    /*
    Terminal-ish Non-Terminals (almost just a terminal but not quite)
     */

    ExprNonTerminal
        QUOTED_UID = UID; //TODO not really

    /*
    Enumeration-like Non-Terminals
     */

    ExprNonTerminal
        programRuleVariableName = UID, //TODO not really
        programRuleStringVariableName = stringLiteral,
        programVariable = UID; //TODO not really

    /*
     Program functions argument alternatives
     */

    ExprNonTerminal
        programRuleFnArg = or( Map.of(
        '#', programRuleVariableName.inCurly(),
        'A', programRuleVariableName.inCurly(),
        '\'', programRuleStringVariableName,
        '"', programRuleStringVariableName ) );
    ExprNonTerminal
        programRuleWithVarFnArg = or( Map.of(
        '#', programRuleVariableName.inCurly(),
        'A', programRuleVariableName.inCurly(),
        'V', programVariable.inCurly(),
        '\'', programRuleStringVariableName,
        '"', programRuleStringVariableName ) );
    ExprNonTerminal
        programRuleWithTagFnArg = or( Map.of(
        '#', programRuleVariableName.inCurly(),
        'A', programRuleVariableName.inCurly(),
        'P', tag( "PS_EVENTDATE:", UID.inWS() ).inCurly(),
        '\'', programRuleStringVariableName,
        '"', programRuleStringVariableName ) );

    /*
    Essential Non-Terminals
     */

    ExprNonTerminal
        expr = Expr::expr;

    /*
    Production Rules
     */

    //  Functions (alphabetical)
    ExprNonTerminal
        firstNonNull____ = ( input, ctx ) -> fn( "firstNonNull", expr, varargs( expr ) ),
        greatest________ = ( input, ctx ) -> fn( "greatest", expr, varargs( expr ) ),
        if______________ = ( input, ctx ) -> fn( "if", expr, expr, expr ),
        isNotNull_______ = ( input, ctx ) -> fn( "isNotNull", expr ),
        isNull__________ = ( input, ctx ) -> fn( "isNull", expr ),
        least___________ = ( input, ctx ) -> fn( "least", expr, varargs( expr ) ),
        log_____________ = ( input, ctx ) -> fn( "log", expr, expr.maybe() ),
        log10___________ = ( input, ctx ) -> fn( "log10", expr ),
        orgUnit_ancestor = ( input, ctx ) -> fn( "orgUnit.ancestor", UID, varargs( UID ) ),
        orgUnit_dataSet_ = ( input, ctx ) -> fn( "orgUnit.dataSet", UID, varargs( UID ) ),
        orgUnit_group___ = ( input, ctx ) -> fn( "orgUnit.group", UID, varargs( UID ) ),
        orgUnit_program_ = ( input, ctx ) -> fn( "orgUnit.program", UID, varargs( UID ) ),
        subExpression___ = ( input, ctx ) -> fn( "subExpression", expr );

    //  Aggregation functions (alphabetical)
    ExprNonTerminal
        avg_____________ = ( input, ctx ) -> aggFn( "avg", expr ),
        count___________ = ( input, ctx ) -> aggFn( "count", is( "distinct", expr ) ),
        max_____________ = ( input, ctx ) -> aggFn( "max", expr ),
        median__________ = ( input, ctx ) -> aggFn( "median", expr ),
        min_____________ = ( input, ctx ) -> aggFn( "min", expr ),
        percentileCont__ = ( input, ctx ) -> aggFn( "percentileCont", expr, expr ),
        stddev__________ = ( input, ctx ) -> aggFn( "stddev", expr ),
        stddevPop_______ = ( input, ctx ) -> aggFn( "stddevPop", expr ),
        stddevSamp______ = ( input, ctx ) -> aggFn( "stddevSamp", expr ),
        sum_____________ = ( input, ctx ) -> aggFn( "sum", expr ),
        variance________ = ( input, ctx ) -> aggFn( "variance", expr );

    //  Program functions (alphabetical)
    ExprNonTerminal
        d2_addDays______ = ( input, ctx ) -> fn( "d2:addDays", expr, expr ),
        d2_ceil_________ = ( input, ctx ) -> fn( "d2:ceil", expr ),
        d2_concatenate__ = ( input, ctx ) -> fn( "d2:concatenate", expr, varargs( expr ) ),
        d2_condition____ = ( input, ctx ) -> fn( "d2:condition", stringLiteral, expr, expr ),
        d2_count________ = ( input, ctx ) -> fn( "d2:count", programRuleFnArg ),
        d2_countIfCondition = ( input, ctx ) -> fn( "d2:countIfCondition", programRuleFnArg, stringLiteral ),
        d2_countIfValue_ = ( input, ctx ) -> fn( "d2:countIfValue", programRuleFnArg, expr ),
        d2_countIfZeroPos = ( input, ctx ) -> fn( "d2:countIfZeroPos", programRuleFnArg ),
        d2_daysBetween__ = ( input, ctx ) -> fn( "d2:daysBetween", expr, expr ),
        d2_extractDataMatrixValue = ( input, ctx ) -> fn( "d2:extractDataMatrixValue", expr, expr ),
        d2_floor________ = ( input, ctx ) -> fn( "d2:floor", expr ),
        d2_hasUserRole__ = ( input, ctx ) -> fn( "d2:hasUserRole", expr ),
        d2_hasValue_____ = ( input, ctx ) -> fn( "d2:hasValue", programRuleWithVarFnArg ),
        d2_inOrgUnitGroup = ( input, ctx ) -> fn( "d2:inOrgUnitGroup", expr ),
        d2_lastEventDate = ( input, ctx ) -> fn( "d2:lastEventDate", expr ),
        d2_left_________ = ( input, ctx ) -> fn( "d2:left", expr, expr ),
        d2_length_______ = ( input, ctx ) -> fn( "d2:length", expr ),
        d2_maxValue_____ = ( input, ctx ) -> fn( "d2:maxValue", programRuleWithTagFnArg ),
        d2_minutesBetween = ( input, ctx ) -> fn( "d2:minutesBetween", expr, expr ),
        d2_minValue_____ = ( input, ctx ) -> fn( "d2:minValue", programRuleWithTagFnArg ),
        d2_modulus______ = ( input, ctx ) -> fn( "d2:modulus", expr, expr ),
        d2_monthsBetween = ( input, ctx ) -> fn( "d2:monthsBetween", expr, expr ),
        d2_oizp_________ = ( input, ctx ) -> fn( "d2:oizp", expr ),
        d2_relationshipCount = ( input, ctx ) -> fn( "d2:relationshipCount", QUOTED_UID ),
        d2_right________ = ( input, ctx ) -> fn( "d2:right", expr, expr ),
        d2_round________ = ( input, ctx ) -> fn( "d2:round", expr ),
        d2_split________ = ( input, ctx ) -> fn( "d2:split", expr, expr, expr ),
        d2_substring____ = ( input, ctx ) -> fn( "d2:substring", expr, expr, expr ),
        d2_validatePattern = ( input, ctx ) -> fn( "d2:validatePattern", expr, expr ),
        d2_weeksBetween_ = ( input, ctx ) -> fn( "d2:weeksBetween", expr, expr ),
        d2_yearsBetween_ = ( input, ctx ) -> fn( "d2:yearsBetween", expr, expr ),
        d2_zing_________ = ( input, ctx ) -> fn( "d2:zing", expr ),
        d2_zpvc_________ = ( input, ctx ) -> fn( "d2:zpvc", expr, varargs( expr ) ),
        d2_zScoreHFA____ = ( input, ctx ) -> fn( "d2:zScoreHFA", expr, expr, expr ),
        d2_zScoreWFA____ = ( input, ctx ) -> fn( "d2:zScoreWFA", expr, expr, expr ),
        d2_zScoreWFH____ = ( input, ctx ) -> fn( "d2:zScoreWFH", expr, expr, expr );

}
