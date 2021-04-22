/*
 * Copyright (c) 2004-2021, University of Oslo
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
package org.hisp.dhis.webapi.controller;

import static org.hisp.dhis.webapi.WebClient.Body;
import static org.hisp.dhis.webapi.utils.WebClientUtils.assertStatus;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.LocalDate;

import org.hisp.dhis.dataanalysis.FollowupAnalysisParams;
import org.hisp.dhis.webapi.DhisControllerConvenienceTest;
import org.hisp.dhis.webapi.json.JsonList;
import org.hisp.dhis.webapi.json.JsonObject;
import org.hisp.dhis.webapi.json.domain.JsonFollowupValue;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;

/**
 * Tests the
 * {@link DataAnalysisController#performFollowupAnalysis(FollowupAnalysisParams)}
 * method.
 *
 * @author Jan Bernitt
 */
public class FollowupAnalysisControllerTest extends DhisControllerConvenienceTest
{

    private String dataElementId;

    private String orgUnitId;

    private String cocId;

    @Before
    public void setUp()
    {
        orgUnitId = assertStatus( HttpStatus.CREATED,
            POST( "/organisationUnits/", "{'name':'My Unit', 'shortName':'OU1', 'openingDate': '2020-01-01'}" ) );

        // add OU to users hierarchy
        assertStatus( HttpStatus.NO_CONTENT,
            POST( "/users/{id}/organisationUnits", getCurrentUser().getUid(),
                Body( "{'additions':[{'id':'" + orgUnitId + "'}]}" ) ) );

        JsonObject ccDefault = GET(
            "/categoryCombos/gist?fields=id,categoryOptionCombos::ids&pageSize=1&headless=true&filter=name:eq:default" )
                .content().getObject( 0 );
        String ccId = ccDefault.getString( "id" ).string();
        cocId = ccDefault.getArray( "categoryOptionCombos" ).getString( 0 ).string();

        dataElementId = assertStatus( HttpStatus.CREATED,
            POST( "/dataElements/",
                "{'name':'My data element', 'shortName':'DE1', 'code':'DE1', 'valueType':'INTEGER', " +
                    "'aggregationType':'SUM', 'zeroIsSignificant':false, 'domainType':'AGGREGATE', " +
                    "'categoryCombo': {'id': '" + ccId + "'}}" ) );

        assertStatus( HttpStatus.CREATED,
            POST( "/dataValues?de={de}&pe=2021-03&ou={ou}&co={coc}&value=5&comment=Needs_check&followUp=true",
                dataElementId, orgUnitId, cocId ) );
    }

    /**
     * This test makes sure the fields returned by a
     * {@link org.hisp.dhis.dataanalysis.FollowupValue} are mapped correctly.
     */
    @Test
    public void testPerformFollowupAnalysis_FieldMapping()
    {
        JsonList<JsonFollowupValue> values = GET( "/dataAnalysis/followup?ouParent={ou}&de={de}&pe={pe}",
            orgUnitId, dataElementId, "2021" ).content().asList( JsonFollowupValue.class );

        assertEquals( 1, values.size() );

        JsonFollowupValue value = values.get( 0 );
        assertEquals( dataElementId, value.getDe() );
        assertEquals( "My data element", value.getDeName() );
        assertEquals( orgUnitId, value.getOu() );
        assertEquals( "My Unit", value.getOuName() );
        assertEquals( "/" + orgUnitId, value.getOuPath() );
        assertEquals( "Monthly", value.getPe() );
        assertEquals( LocalDate.of( 2021, 03, 01 ).atStartOfDay(), value.getPeStartDate() );
        assertEquals( LocalDate.of( 2021, 03, 31 ).atStartOfDay(), value.getPeEndDate() );
        assertEquals( cocId, value.getCoc() );
        assertEquals( "default", value.getCocName() );
        assertEquals( cocId, value.getAoc() );
        assertEquals( "default", value.getAocName() );
        assertEquals( "5", value.getValue() );
        assertEquals( "admin", value.getStoredBy() );
        assertEquals( "Needs_check", value.getComment() );
        assertNotNull( value.getLastUpdated() );
        assertNotNull( value.getCreated() );
    }
}
