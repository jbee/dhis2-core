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
package org.hisp.dhis.tracker.export.enrollment;

import static org.hisp.dhis.common.OrganisationUnitSelectionMode.ACCESSIBLE;
import static org.hisp.dhis.common.OrganisationUnitSelectionMode.CAPTURE;
import static org.hisp.dhis.common.OrganisationUnitSelectionMode.DESCENDANTS;
import static org.hisp.dhis.tracker.export.OperationsParamsValidator.validateOrgUnitMode;

import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hisp.dhis.common.IllegalQueryException;
import org.hisp.dhis.feedback.BadRequestException;
import org.hisp.dhis.feedback.ForbiddenException;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.organisationunit.OrganisationUnitService;
import org.hisp.dhis.program.Program;
import org.hisp.dhis.program.ProgramService;
import org.hisp.dhis.security.acl.AclService;
import org.hisp.dhis.trackedentity.TrackedEntity;
import org.hisp.dhis.trackedentity.TrackedEntityService;
import org.hisp.dhis.trackedentity.TrackedEntityType;
import org.hisp.dhis.trackedentity.TrackedEntityTypeService;
import org.hisp.dhis.user.CurrentUserService;
import org.hisp.dhis.user.User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maps {@link EnrollmentOperationParams} to {@link EnrollmentQueryParams} which is used to fetch
 * enrollments from the DB.
 */
@Component
@RequiredArgsConstructor
class EnrollmentOperationParamsMapper {
  private final CurrentUserService currentUserService;

  private final OrganisationUnitService organisationUnitService;

  private final ProgramService programService;

  private final TrackedEntityTypeService trackedEntityTypeService;

  private final TrackedEntityService trackedEntityService;

  private final AclService aclService;

  @Transactional(readOnly = true)
  public EnrollmentQueryParams map(EnrollmentOperationParams operationParams)
      throws BadRequestException, ForbiddenException {
    User user = currentUserService.getCurrentUser();

    Program program = validateProgram(operationParams.getProgramUid(), user);
    TrackedEntityType trackedEntityType =
        validateTrackedEntityType(operationParams.getTrackedEntityTypeUid(), user);
    TrackedEntity trackedEntity = validateTrackedEntity(operationParams.getTrackedEntityUid());

    Set<OrganisationUnit> orgUnits = validateOrgUnits(operationParams.getOrgUnitUids(), user);
    validateOrgUnitMode(operationParams.getOrgUnitMode(), user, program);

    EnrollmentQueryParams params = new EnrollmentQueryParams();
    params.setProgram(program);
    params.setProgramStatus(operationParams.getProgramStatus());
    params.setFollowUp(operationParams.getFollowUp());
    params.setLastUpdated(operationParams.getLastUpdated());
    params.setLastUpdatedDuration(operationParams.getLastUpdatedDuration());
    params.setProgramStartDate(operationParams.getProgramStartDate());
    params.setProgramEndDate(operationParams.getProgramEndDate());
    params.setTrackedEntityType(trackedEntityType);
    params.setTrackedEntity(trackedEntity);
    params.addOrganisationUnits(orgUnits);
    params.setOrganisationUnitMode(operationParams.getOrgUnitMode());
    params.setIncludeDeleted(operationParams.isIncludeDeleted());
    params.setUser(user);
    params.setOrder(operationParams.getOrder());
    params.setEnrollmentUids(operationParams.getEnrollmentUids());

    mergeOrgUnitModes(operationParams, user, params);

    return params;
  }

  /**
   * Prepares the org unit modes to simplify the SQL query creation by merging similar behaviored
   * org unit modes.
   */
  private void mergeOrgUnitModes(
      EnrollmentOperationParams operationParams, User user, EnrollmentQueryParams queryParams) {
    if (user != null && operationParams.getOrgUnitMode() == ACCESSIBLE) {
      queryParams.addOrganisationUnits(
          new HashSet<>(user.getTeiSearchOrganisationUnitsWithFallback()));
      queryParams.setOrganisationUnitMode(DESCENDANTS);
    } else if (user != null && operationParams.getOrgUnitMode() == CAPTURE) {
      queryParams.addOrganisationUnits(new HashSet<>(user.getOrganisationUnits()));
      queryParams.setOrganisationUnitMode(DESCENDANTS);
    }
  }

  private Program validateProgram(String uid, User user) throws BadRequestException {
    if (uid == null) {
      return null;
    }

    Program program = programService.getProgram(uid);
    if (program == null) {
      throw new BadRequestException("Program is specified but does not exist: " + uid);
    }

    if (!aclService.canDataRead(user, program)) {
      throw new IllegalQueryException(
          "Current user is not authorized to read data from selected program:  "
              + program.getUid());
    }

    if (program.getTrackedEntityType() != null
        && !aclService.canDataRead(user, program.getTrackedEntityType())) {
      throw new IllegalQueryException(
          "Current user is not authorized to read data from selected program's tracked entity type:  "
              + program.getTrackedEntityType().getUid());
    }

    return program;
  }

  private TrackedEntityType validateTrackedEntityType(String uid, User user)
      throws BadRequestException {
    if (uid == null) {
      return null;
    }

    TrackedEntityType trackedEntityType = trackedEntityTypeService.getTrackedEntityType(uid);
    if (trackedEntityType == null) {
      throw new BadRequestException("Tracked entity type is specified but does not exist: " + uid);
    }

    if (!aclService.canDataRead(user, trackedEntityType)) {
      throw new IllegalQueryException(
          "Current user is not authorized to read data from selected tracked entity type:  "
              + trackedEntityType.getUid());
    }

    return trackedEntityType;
  }

  private TrackedEntity validateTrackedEntity(String uid) throws BadRequestException {
    if (uid == null) {
      return null;
    }

    TrackedEntity trackedEntity = trackedEntityService.getTrackedEntity(uid);
    if (trackedEntity == null) {
      throw new BadRequestException("Tracked entity is specified but does not exist: " + uid);
    }

    return trackedEntity;
  }

  private Set<OrganisationUnit> validateOrgUnits(Set<String> orgUnitUids, User user)
      throws BadRequestException, ForbiddenException {

    Set<OrganisationUnit> orgUnits = new HashSet<>();
    if (orgUnitUids != null) {
      for (String orgUnitUid : orgUnitUids) {
        OrganisationUnit orgUnit = organisationUnitService.getOrganisationUnit(orgUnitUid);

        if (orgUnit == null) {
          throw new BadRequestException("Organisation unit does not exist: " + orgUnitUid);
        }

        if (user != null
            && !user.isSuper()
            && !organisationUnitService.isInUserHierarchy(
                orgUnitUid, user.getTeiSearchOrganisationUnitsWithFallback())) {
          throw new ForbiddenException(
              "Organisation unit is not part of the search scope: " + orgUnitUid);
        }
        orgUnits.add(orgUnit);
      }
    }

    return orgUnits;
  }
}
