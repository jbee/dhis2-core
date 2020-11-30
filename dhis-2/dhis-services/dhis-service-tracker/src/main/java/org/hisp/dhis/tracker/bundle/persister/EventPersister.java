package org.hisp.dhis.tracker.bundle.persister;

/*
 * Copyright (c) 2004-2020, University of Oslo
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

import static com.google.api.client.util.Preconditions.checkNotNull;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.Session;
import org.hisp.dhis.dataelement.DataElement;
import org.hisp.dhis.eventdatavalue.EventDataValue;
import org.hisp.dhis.program.ProgramStageInstance;
import org.hisp.dhis.reservedvalue.ReservedValueService;
import org.hisp.dhis.trackedentitycomment.TrackedEntityComment;
import org.hisp.dhis.trackedentitycomment.TrackedEntityCommentService;
import org.hisp.dhis.tracker.TrackerIdScheme;
import org.hisp.dhis.tracker.TrackerType;
import org.hisp.dhis.tracker.bundle.TrackerBundle;
import org.hisp.dhis.tracker.bundle.TrackerBundleHook;
import org.hisp.dhis.tracker.converter.TrackerConverterService;
import org.hisp.dhis.tracker.converter.TrackerSideEffectConverterService;
import org.hisp.dhis.tracker.domain.DataValue;
import org.hisp.dhis.tracker.domain.Event;
import org.hisp.dhis.tracker.job.TrackerSideEffectDataBundle;
import org.hisp.dhis.tracker.preheat.TrackerPreheat;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @author Luciano Fiandesio
 */
@Component
public class EventPersister extends AbstractTrackerPersister<Event, ProgramStageInstance>
{
    private final TrackerConverterService<Event, ProgramStageInstance> eventConverter;

    private final TrackedEntityCommentService trackedEntityCommentService;

    private final TrackerSideEffectConverterService sideEffectConverterService;
    
    public EventPersister( List<TrackerBundleHook> bundleHooks, ReservedValueService reservedValueService,
        TrackerConverterService<Event, ProgramStageInstance> eventConverter,
        TrackedEntityCommentService trackedEntityCommentService, TrackerSideEffectConverterService sideEffectConverterService )
    {
        super( bundleHooks, reservedValueService );
        this.eventConverter = eventConverter;
        this.trackedEntityCommentService = trackedEntityCommentService;
        this.sideEffectConverterService = sideEffectConverterService;
    }

    @Override
    protected void persistComments( ProgramStageInstance programStageInstance )
    {
        if ( !programStageInstance.getComments().isEmpty() )
        {
            for ( TrackedEntityComment comment : programStageInstance.getComments() )
            {
                this.trackedEntityCommentService.addTrackedEntityComment( comment );
            }
        }
    }

    @Override
    protected void updatePreheat( TrackerPreheat preheat, ProgramStageInstance programStageInstance )
    {
        preheat.putEvents( TrackerIdScheme.UID, Collections.singletonList( programStageInstance ) );
    }

    @Override
    protected boolean isNew( TrackerPreheat preheat, String uid )
    {
        return preheat.getEvent( TrackerIdScheme.UID, uid ) == null;
    }

    @Override
    protected TrackerSideEffectDataBundle handleSideEffects( TrackerBundle bundle,
        ProgramStageInstance programStageInstance )
    {
        return TrackerSideEffectDataBundle.builder()
            .klass( ProgramStageInstance.class )
            .enrollmentRuleEffects( new HashMap<>() )
            .eventRuleEffects( sideEffectConverterService.toTrackerSideEffects( bundle.getEventRuleEffects() ) )
            .object( programStageInstance.getUid() )
            .importStrategy( bundle.getImportStrategy() )
            .accessedBy( bundle.getUsername() )
            .build();
    }

    @Override
    protected ProgramStageInstance convert( TrackerBundle bundle, Event event )
    {
        return eventConverter.from( bundle.getPreheat(), event );
    }

    @Override
    protected TrackerType getType()
    {
        return TrackerType.EVENT;
    }

    @Override
    protected void runPostCreateHooks( TrackerBundle bundle )
    {
        bundle.getEvents()
            .forEach( o -> bundleHooks.forEach( hook -> hook.postCreate( Event.class, o, bundle ) ) );
    }

    @Override
    protected void runPreCreateHooks( TrackerBundle bundle )
    {
        bundle.getEvents()
            .forEach( o -> bundleHooks.forEach( hook -> hook.preCreate( Event.class, o, bundle ) ) );
    }

    @Override
    protected void updateEntityValues( Session session, TrackerPreheat preheat,
        Event event, ProgramStageInstance programStageInstance )
    {
        handleDataValues( session, preheat, event.getDataValues(), programStageInstance );
    }

    private void handleDataValues( Session session, TrackerPreheat preheat, Set<DataValue> payloadDataValues,
        ProgramStageInstance psi )
    {
        Map<String, EventDataValue> dataValueDBMap = psi
            .getEventDataValues()
            .stream()
            .collect( Collectors.toMap( EventDataValue::getDataElement, Function.identity() ) );

        for ( DataValue dv : payloadDataValues )
        {
            DataElement dateElement = preheat.get( TrackerIdScheme.UID, DataElement.class, dv.getDataElement() );

            checkNotNull( dateElement,
                "Data element should never be NULL here if validation is enforced before commit." );

            EventDataValue eventDataValue = dataValueDBMap.getOrDefault( dv.getDataElement(), new EventDataValue() );

            eventDataValue.setDataElement( dateElement.getUid() );
            eventDataValue.setValue( dv.getValue() );
            eventDataValue.setStoredBy( dv.getStoredBy() );

            handleDataValueCreatedUpdatedDates( dv, eventDataValue );

            if ( StringUtils.isEmpty( eventDataValue.getValue() ) )
            {
                if ( dateElement.isFileType() )
                {
                    unassignFileResource( session, preheat, dataValueDBMap.get( dv.getDataElement() ).getValue() );
                }
                psi.getEventDataValues().remove( eventDataValue );
            }
            else
            {
                if ( dateElement.isFileType() )
                {
                    assignFileResource( session, preheat, eventDataValue.getValue() );
                }
                psi.getEventDataValues().add( eventDataValue );
            }
        }
    }

    private void handleDataValueCreatedUpdatedDates( DataValue dv, EventDataValue eventDataValue )
    {
        try
        {
            eventDataValue.setCreated( dv.getCreatedAt() == null ? new Date()
                : new SimpleDateFormat( "yyyy-MM-dd" ).parse( dv.getCreatedAt() ) );
            eventDataValue.setLastUpdated( dv.getUpdatedAt() == null ? new Date()
                : new SimpleDateFormat( "yyyy-MM-dd" ).parse( dv.getUpdatedAt() ) );
        }
        catch ( ParseException e )
        {
            // Created and updated dates are already validated.
            // This catch should never be reached
            e.printStackTrace();
        }
    }
}
