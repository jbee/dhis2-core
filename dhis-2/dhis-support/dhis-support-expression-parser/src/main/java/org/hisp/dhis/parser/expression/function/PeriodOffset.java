/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.parser.expression.function;

import static com.google.common.base.MoreObjects.firstNonNull;
import static org.hisp.dhis.parser.expression.antlr.ExpressionParser.ExprContext;
import static org.hisp.dhis.system.util.MathUtils.parseInt;

import org.hisp.dhis.common.QueryModifiers;
import org.hisp.dhis.parser.expression.CommonExpressionVisitor;
import org.hisp.dhis.parser.expression.ExpressionItem;

/**
 * Function periodOffset
 *
 * @author Enrico Colasante
 */
public class PeriodOffset
    implements ExpressionItem
{
    @Override
    public Object evaluate( ExprContext ctx, CommonExpressionVisitor visitor )
    {
        int existingPeriodOffset = (visitor.getQueryMods() == null) ? 0 : visitor.getQueryMods().getPeriodOffset();

        int parsedPeriodOffset = (ctx.period == null) ? 0 : firstNonNull( parseInt( ctx.period.getText() ), 0 );

        int periodOffset = existingPeriodOffset + parsedPeriodOffset;

        QueryModifiers queryMods = visitor.getQueryModsBuilder().periodOffset( periodOffset ).build();

        return visitor.visitWithQueryMods( ctx.expr( 0 ), queryMods );
    }
}
