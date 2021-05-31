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
package org.hisp.dhis.tracker;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hisp.dhis.program.ProgramInstance;
import org.hisp.dhis.program.ProgramInstanceService;
import org.hisp.dhis.program.ProgramStageInstance;
import org.hisp.dhis.program.ProgramStageInstanceService;
import org.hisp.dhis.relationship.RelationshipService;
import org.hisp.dhis.trackedentity.TrackedEntityInstance;
import org.hisp.dhis.trackedentity.TrackedEntityInstanceService;
import org.hisp.dhis.tracker.bundle.TrackerBundle;
import org.hisp.dhis.tracker.converter.EnrollmentTrackerConverterService;
import org.hisp.dhis.tracker.converter.EventTrackerConverterService;
import org.hisp.dhis.tracker.domain.Enrollment;
import org.hisp.dhis.tracker.domain.Event;
import org.hisp.dhis.tracker.domain.Relationship;
import org.hisp.dhis.tracker.domain.TrackedEntity;
import org.hisp.dhis.tracker.report.TrackerTypeReport;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

/**
 * @author Zubair Asghar
 */

@Service
public class DefaultTrackerObjectsDeletionService
    implements TrackerObjectDeletionService
{
    private final ProgramInstanceService programInstanceService;

    private final TrackedEntityInstanceService teiService;

    private final ProgramStageInstanceService programStageInstanceService;

    private final RelationshipService relationshipService;

    private final EnrollmentTrackerConverterService enrollmentTrackerConverterService;

    private final EventTrackerConverterService eventTrackerConverterService;

    public DefaultTrackerObjectsDeletionService( ProgramInstanceService programInstanceService,
        TrackedEntityInstanceService entityInstanceService,
        ProgramStageInstanceService stageInstanceService,
        RelationshipService relationshipService,
        EnrollmentTrackerConverterService enrollmentTrackerConverterService,
        EventTrackerConverterService eventTrackerConverterService )
    {
        this.programInstanceService = programInstanceService;
        this.teiService = entityInstanceService;
        this.programStageInstanceService = stageInstanceService;
        this.relationshipService = relationshipService;
        this.enrollmentTrackerConverterService = enrollmentTrackerConverterService;
        this.eventTrackerConverterService = eventTrackerConverterService;
    }

    @Override
    public TrackerTypeReport deleteEnrollments( TrackerBundle bundle, TrackerType trackerType )
    {
        TrackerTypeReport typeReport = new TrackerTypeReport( trackerType );

        List<Enrollment> enrollments = bundle.getEnrollments();

        for ( int idx = 0; idx < enrollments.size(); idx++ )
        {
            String uid = enrollments.get( idx ).getEnrollment();

            ProgramInstance programInstance = programInstanceService.getProgramInstance( uid );

            List<Event> events = eventTrackerConverterService
                .to( Lists.newArrayList( programInstance.getProgramStageInstances()
                    .stream().filter( psi -> !psi.isDeleted() )
                    .collect( Collectors.toList() ) ) );

            TrackerBundle trackerBundle = TrackerBundle.builder().events( events ).user( bundle.getUser() )
                .build();

            deleteEvents( trackerBundle, TrackerType.EVENT );

            TrackedEntityInstance tei = programInstance.getEntityInstance();
            tei.getProgramInstances().remove( programInstance );

            programInstanceService.deleteProgramInstance( programInstance );
            teiService.updateTrackedEntityInstance( tei );

            typeReport.getStats().incDeleted();
        }

        return typeReport;
    }

    @Override
    public TrackerTypeReport deleteEvents( TrackerBundle bundle, TrackerType trackerType )
    {
        TrackerTypeReport typeReport = new TrackerTypeReport( trackerType );

        List<Event> events = bundle.getEvents();

        for ( int idx = 0; idx < events.size(); idx++ )
        {
            String uid = events.get( idx ).getEvent();

            ProgramStageInstance programStageInstance = programStageInstanceService.getProgramStageInstance( uid );

            ProgramInstance programInstance = programStageInstance.getProgramInstance();

            programStageInstanceService.deleteProgramStageInstance( programStageInstance );

            if ( programStageInstance.getProgramStage().getProgram().isRegistration() )
            {
                teiService.updateTrackedEntityInstance( programStageInstance.getProgramInstance().getEntityInstance() );

                programInstance.getProgramStageInstances().remove( programStageInstance );
                programInstanceService.updateProgramInstance( programInstance );
            }

            typeReport.getStats().incDeleted();
        }

        return typeReport;
    }

    @Override
    public TrackerTypeReport deleteTrackedEntityInstances( TrackerBundle bundle, TrackerType trackerType )
    {
        TrackerTypeReport typeReport = new TrackerTypeReport( trackerType );

        List<TrackedEntity> trackedEntities = bundle.getTrackedEntities();

        for ( int idx = 0; idx < trackedEntities.size(); idx++ )
        {
            String uid = trackedEntities.get( idx ).getTrackedEntity();

            org.hisp.dhis.trackedentity.TrackedEntityInstance daoEntityInstance = teiService
                .getTrackedEntityInstance( uid );

            Set<ProgramInstance> programInstances = daoEntityInstance.getProgramInstances();

            List<Enrollment> enrollments = enrollmentTrackerConverterService
                .to( Lists.newArrayList( programInstances.stream()
                    .filter( pi -> !pi.isDeleted() )
                    .collect( Collectors.toList() ) ) );

            TrackerBundle trackerBundle = TrackerBundle.builder().enrollments( enrollments )
                .user( bundle.getUser() )
                .build();

            deleteEnrollments( trackerBundle, TrackerType.ENROLLMENT );

            teiService.deleteTrackedEntityInstance( daoEntityInstance );

            typeReport.getStats().incDeleted();
        }

        return typeReport;
    }

    @Override
    public TrackerTypeReport deleteRelationShips( TrackerBundle bundle, TrackerType trackerType )
    {
        TrackerTypeReport typeReport = new TrackerTypeReport( trackerType );

        List<Relationship> relationships = bundle.getRelationships();

        for ( int idx = 0; idx < relationships.size(); idx++ )
        {
            String uid = relationships.get( idx ).getRelationship();

            org.hisp.dhis.relationship.Relationship relationship = relationshipService.getRelationship( uid );

            relationshipService.deleteRelationship( relationship );

            typeReport.getStats().incDeleted();
        }

        return typeReport;
    }
}
