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
package org.hisp.dhis.relationship;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.hisp.dhis.common.DeleteNotAllowedException;
import org.hisp.dhis.common.ObjectDeletionRequestedEvent;
import org.hisp.dhis.system.deletion.DefaultDeletionManager;
import org.hisp.dhis.system.deletion.DeletionManager;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class RelationshipDeletionHandlerTest
{
    private RelationshipDeletionHandler relationshipDeletionHandler;

    @Mock
    private RelationshipService relationshipService;

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    private DeletionManager deletionManager = new DefaultDeletionManager();

    @Before
    public void setUp()
    {
        relationshipDeletionHandler = new RelationshipDeletionHandler( relationshipService );
        relationshipDeletionHandler.setManager( deletionManager );
        relationshipDeletionHandler.init();
    }

    @Test
    public void allowDeleteRelationshipTypeWithData()
    {
        RelationshipType relationshipType = new RelationshipType();
        when( relationshipService.getRelationshipsByRelationshipType( any() ) )
            .thenReturn( singletonList( new Relationship() ) );

        assertThrows( DeleteNotAllowedException.class,
            () -> deletionManager.objectDeletionListener( new ObjectDeletionRequestedEvent( relationshipType ) ) );
    }

    @Test
    public void allowDeleteRelationshipTypeWithoutData()
    {
        RelationshipType relationshipType = new RelationshipType();
        when( relationshipService.getRelationshipsByRelationshipType( any() ) )
            .thenReturn( emptyList() );

        deletionManager.objectDeletionListener( new ObjectDeletionRequestedEvent( relationshipType ) );
    }
}
