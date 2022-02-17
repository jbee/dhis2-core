package org.hisp.dhis.expression.poc;

public enum ExprType
{
    // complex nodes
    ARG,
    FN,
    AGG_FN,
    PROG_FN,
    DATA_ITEM,
    VAR,

    // operators

    // simple nodes (literals)
    TAG,
    NUMERIC_LITERAL,
    STRING_LITERAL,
    BOOLEAN_LITERAL,
    DATE_LITERAL,
    UID,
    IDENTIFIER,
    REPORTING_RATE_TYPE
}
