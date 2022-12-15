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
package org.hisp.dhis.tracker.validation.hooks;

import static org.hisp.dhis.tracker.TrackerType.EVENT;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1030;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1032;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1082;
import static org.hisp.dhis.tracker.validation.hooks.AssertValidationErrorReporter.hasTrackerError;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.hisp.dhis.program.ProgramStageInstance;
import org.hisp.dhis.tracker.TrackerIdSchemeParams;
import org.hisp.dhis.tracker.TrackerImportStrategy;
import org.hisp.dhis.tracker.bundle.TrackerBundle;
import org.hisp.dhis.tracker.domain.Event;
import org.hisp.dhis.tracker.preheat.TrackerPreheat;
import org.hisp.dhis.tracker.validation.ValidationErrorReporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Enrico Colasante
 */
@ExtendWith( MockitoExtension.class )
class EventPreCheckExistenceValidatorTest
{
    private final static String SOFT_DELETED_EVENT_UID = "SoftDeletedEventId";

    private final static String EVENT_UID = "EventId";

    private final static String NOT_PRESENT_EVENT_UID = "NotPresentEventId";

    @Mock
    private TrackerBundle bundle;

    @Mock
    private TrackerPreheat preheat;

    private EventPreCheckExistenceValidator validator;

    private ValidationErrorReporter reporter;

    @BeforeEach
    void setUp()
    {
        when( bundle.getPreheat() ).thenReturn( preheat );

        TrackerIdSchemeParams idSchemes = TrackerIdSchemeParams.builder().build();
        reporter = new ValidationErrorReporter( idSchemes );

        validator = new EventPreCheckExistenceValidator();
    }

    @Test
    void verifyEventValidationSuccessWhenIsCreateAndEventIsNotPresent()
    {
        Event event = Event.builder()
            .event( NOT_PRESENT_EVENT_UID )
            .build();
        when( bundle.getStrategy( event ) ).thenReturn( TrackerImportStrategy.CREATE );

        validator.validate( reporter, bundle, event );

        assertFalse( reporter.hasErrors() );
    }

    @Test
    void verifyEventValidationSuccessWhenEventIsNotPresent()
    {
        Event event = Event.builder()
            .event( NOT_PRESENT_EVENT_UID )
            .build();
        when( bundle.getStrategy( any( Event.class ) ) ).thenReturn( TrackerImportStrategy.CREATE_AND_UPDATE );

        validator.validate( reporter, bundle, event );

        assertFalse( reporter.hasErrors() );
    }

    @Test
    void verifyEventValidationSuccessWhenIsUpdate()
    {
        Event event = Event.builder()
            .event( EVENT_UID )
            .build();
        when( preheat.getEvent( EVENT_UID ) ).thenReturn( getEvent() );
        when( bundle.getStrategy( any( Event.class ) ) ).thenReturn( TrackerImportStrategy.CREATE_AND_UPDATE );

        validator.validate( reporter, bundle, event );

        assertFalse( reporter.hasErrors() );
    }

    @Test
    void verifyEventValidationFailsWhenIsSoftDeleted()
    {
        Event event = Event.builder()
            .event( SOFT_DELETED_EVENT_UID )
            .build();
        when( preheat.getEvent( SOFT_DELETED_EVENT_UID ) ).thenReturn( getSoftDeletedEvent() );
        when( bundle.getStrategy( any( Event.class ) ) ).thenReturn( TrackerImportStrategy.CREATE_AND_UPDATE );

        validator.validate( reporter, bundle, event );

        hasTrackerError( reporter, E1082, EVENT, event.getUid() );
    }

    @Test
    void verifyEventValidationFailsWhenIsCreateAndEventIsAlreadyPresent()
    {
        Event event = Event.builder()
            .event( EVENT_UID )
            .build();
        when( preheat.getEvent( EVENT_UID ) ).thenReturn( getEvent() );
        when( bundle.getStrategy( event ) ).thenReturn( TrackerImportStrategy.CREATE );

        validator.validate( reporter, bundle, event );

        hasTrackerError( reporter, E1030, EVENT, event.getUid() );
    }

    @Test
    void verifyEventValidationFailsWhenIsUpdateAndEventIsNotPresent()
    {
        Event event = Event.builder()
            .event( NOT_PRESENT_EVENT_UID )
            .build();
        when( bundle.getStrategy( event ) ).thenReturn( TrackerImportStrategy.UPDATE );

        validator.validate( reporter, bundle, event );

        hasTrackerError( reporter, E1032, EVENT, event.getUid() );
    }

    private ProgramStageInstance getSoftDeletedEvent()
    {
        ProgramStageInstance programStageInstance = new ProgramStageInstance();
        programStageInstance.setUid( SOFT_DELETED_EVENT_UID );
        programStageInstance.setDeleted( true );
        return programStageInstance;
    }

    private ProgramStageInstance getEvent()
    {
        ProgramStageInstance programStageInstance = new ProgramStageInstance();
        programStageInstance.setUid( EVENT_UID );
        programStageInstance.setDeleted( false );
        return programStageInstance;
    }
}