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
package org.hisp.dhis.metadata;

import java.util.Date;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.Immutable;
import org.hisp.dhis.common.CodeGenerator;
import org.hisp.dhis.common.DxfNamespaces;
import org.hisp.dhis.common.UniqueObject;
import org.hisp.dhis.common.annotation.Description;
import org.hisp.dhis.schema.PropertyType;
import org.hisp.dhis.schema.annotation.Property;
import org.hisp.dhis.schema.annotation.Property.Value;
import org.hisp.dhis.schema.annotation.PropertyRange;
import org.hisp.dhis.schema.annotation.PropertyTransformer;
import org.hisp.dhis.schema.transformer.UserPropertyTransformer;
import org.hisp.dhis.user.User;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * A proposal is a record about a change proposed by a user to add, update or
 * remove a certain metadata object.
 *
 * @author Jan Bernitt
 */
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor( access = AccessLevel.PRIVATE )
@JacksonXmlRootElement( localName = "metadataProposal", namespace = DxfNamespaces.DXF_2_0 )
public class MetadataProposal implements UniqueObject
{
    private long id;

    @Immutable
    private String uid;

    @Immutable
    private MetadataProposalType type;

    @Immutable
    private MetadataProposalTarget target;

    @Immutable
    private String targetUid;

    @Immutable
    private User createdBy;

    @Immutable
    private Date created;

    @Immutable
    private ObjectNode change;

    private String comment;
    // status? mark deleted?

    @Override
    @JsonIgnore
    public long getId()
    {
        return id;
    }

    @Override
    @JsonProperty( value = "id" )
    @JacksonXmlProperty( localName = "id", isAttribute = true )
    @Description( "The Unique Identifier for this Object." )
    @Property( value = PropertyType.IDENTIFIER, required = Value.FALSE )
    @PropertyRange( min = 11, max = 11 )
    public String getUid()
    {
        return uid;
    }

    @JsonProperty
    @JacksonXmlProperty( namespace = DxfNamespaces.DXF_2_0 )
    public MetadataProposalType getType()
    {
        return type;
    }

    @JsonProperty
    @JacksonXmlProperty( namespace = DxfNamespaces.DXF_2_0 )
    public MetadataProposalTarget getTarget()
    {
        return target;
    }

    @JsonProperty
    @JacksonXmlProperty( namespace = DxfNamespaces.DXF_2_0 )
    public String getTargetUid()
    {
        return targetUid;
    }

    @JsonProperty
    @JsonSerialize( using = UserPropertyTransformer.JacksonSerialize.class )
    @JsonDeserialize( using = UserPropertyTransformer.JacksonDeserialize.class )
    @PropertyTransformer( UserPropertyTransformer.class )
    @JacksonXmlProperty( namespace = DxfNamespaces.DXF_2_0 )
    public User getCreatedBy()
    {
        return createdBy;
    }

    @JsonProperty
    @JacksonXmlProperty( isAttribute = true )
    @Description( "The date this object was created." )
    @Property( value = PropertyType.DATE, required = Value.TRUE )
    public Date getCreated()
    {
        return created;
    }

    @JsonProperty
    @JacksonXmlProperty( namespace = DxfNamespaces.DXF_2_0 )
    public ObjectNode getChange()
    {
        return change;
    }

    @JsonProperty
    @JacksonXmlProperty( namespace = DxfNamespaces.DXF_2_0 )
    public String getComment()
    {
        return comment;
    }

    public void setAutoFields()
    {
        if ( uid == null || uid.length() == 0 )
        {
            uid = CodeGenerator.generateUid();
        }
        if ( created == null )
        {
            created = new Date();
        }
    }
}
