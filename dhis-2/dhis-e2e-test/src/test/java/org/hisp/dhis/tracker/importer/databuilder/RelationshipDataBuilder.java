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

package org.hisp.dhis.tracker.importer.databuilder;

import com.google.gson.JsonObject;
import org.hisp.dhis.helpers.JsonObjectBuilder;

/**
 * @author Gintare Vilkelyte <vilkelyte.gintare@gmail.com>
 */
public class RelationshipDataBuilder
{
    private JsonObjectBuilder jsonBuilder;

    public RelationshipDataBuilder()
    {
        this.jsonBuilder = new JsonObjectBuilder();
    }

    public RelationshipDataBuilder setRelationshipType( String relationshipType )
    {
        this.jsonBuilder.addProperty( "relationshipType", relationshipType );
        return this;
    }

    public RelationshipDataBuilder setFromTrackedEntity( String trackedEntityId )
    {
        this.jsonBuilder.addObject( "from", new JsonObjectBuilder()
            .addProperty( "trackedEntity", trackedEntityId ) );

        return this;
    }

    public RelationshipDataBuilder setToTrackedEntity( String trackedEntityId )
    {
        this.jsonBuilder.addObject( "to", new JsonObjectBuilder()
            .addProperty( "trackedEntity", trackedEntityId ) );

        return this;
    }

    public RelationshipDataBuilder buildUniDirectionalRelationship( String teiA, String teiB )
    {
        this.setRelationshipType( "TV9oB9LT3sh" ).setFromTrackedEntity( teiA ).setToTrackedEntity( teiB );

        return this;
    }

    public RelationshipDataBuilder buildBidirectionalRelationship( String teiA, String teiB )
    {
        this.setRelationshipType( "xLmPUYJX8Ks" ).setFromTrackedEntity( teiA ).setToTrackedEntity( teiB );

        return this;
    }

    public JsonObject buildTrackedEntityRelationship( String trackedEntity_1, String trackedEntity_2,
        String relationshipType )
    {
        return new JsonObjectBuilder()
            .addProperty( "relationshipType", relationshipType )
            .addObject( "from", new JsonObjectBuilder()
                .addProperty( "trackedEntity", trackedEntity_1 ) )
            .addObject( "to", new JsonObjectBuilder()
                .addProperty( "trackedEntity", trackedEntity_2 ) )
            .build();
    }

    public JsonObject build()
    {
        return jsonBuilder.build();
    }

    public JsonObject wrapToArray()
    {
        return jsonBuilder.wrapIntoArray( "relationships" );
    }
}
