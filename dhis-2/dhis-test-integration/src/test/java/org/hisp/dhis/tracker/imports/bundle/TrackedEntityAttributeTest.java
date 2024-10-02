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
package org.hisp.dhis.tracker.imports.bundle;

import static org.hisp.dhis.tracker.Assertions.assertNoErrors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.List;
import org.hisp.dhis.common.IdentifiableObjectManager;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.trackedentity.TrackedEntity;
import org.hisp.dhis.trackedentity.TrackedEntityAttribute;
import org.hisp.dhis.trackedentity.TrackedEntityType;
import org.hisp.dhis.trackedentityattributevalue.TrackedEntityAttributeValue;
import org.hisp.dhis.tracker.TrackerTest;
import org.hisp.dhis.tracker.imports.TrackerIdSchemeParams;
import org.hisp.dhis.tracker.imports.TrackerImportParams;
import org.hisp.dhis.tracker.imports.TrackerImportService;
import org.hisp.dhis.tracker.imports.domain.TrackerObjects;
import org.hisp.dhis.tracker.imports.preheat.TrackerPreheat;
import org.hisp.dhis.tracker.imports.preheat.TrackerPreheatService;
import org.hisp.dhis.tracker.imports.report.ImportReport;
import org.hisp.dhis.tracker.trackedentityattributevalue.TrackedEntityAttributeValueService;
import org.hisp.dhis.user.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Morten Olav Hansen <mortenoh@gmail.com>
 */
class TrackedEntityAttributeTest extends TrackerTest {

  @Autowired private TrackerPreheatService trackerPreheatService;

  @Autowired private TrackerImportService trackerImportService;

  @Autowired private TrackedEntityAttributeValueService trackedEntityAttributeValueService;

  @Autowired private IdentifiableObjectManager manager;

  private User importUser;

  @BeforeAll
  void setUp() throws IOException {
    setUpMetadata("tracker/te_with_tea_metadata.json");

    importUser = userService.getUser("tTgjgobT1oS");
    injectSecurityContextUser(importUser);
  }

  @Test
  void testTrackedAttributePreheater() throws IOException {
    TrackerObjects trackerObjects = fromJson("tracker/te_with_tea_data.json");

    TrackerPreheat preheat =
        trackerPreheatService.preheat(trackerObjects, new TrackerIdSchemeParams());
    assertNotNull(preheat.get(OrganisationUnit.class, "cNEZTkdAvmg"));
    assertNotNull(preheat.get(TrackedEntityType.class, "KrYIdvLxkMb"));
    assertNotNull(preheat.get(TrackedEntityAttribute.class, "sYn3tkL3XKa"));
    assertNotNull(preheat.get(TrackedEntityAttribute.class, "TsfP85GKsU5"));
    assertNotNull(preheat.get(TrackedEntityAttribute.class, "sTGqP5JNy6E"));
  }

  @Test
  void testTrackedAttributeValueBundleImporter() throws IOException {
    TrackerImportParams params = TrackerImportParams.builder().build();
    TrackerObjects trackerObjects = fromJson("tracker/te_with_tea_data.json");

    ImportReport importReport = trackerImportService.importTracker(params, trackerObjects);
    assertNoErrors(importReport);

    List<TrackedEntity> trackedEntities = manager.getAll(TrackedEntity.class);
    assertEquals(1, trackedEntities.size());
    TrackedEntity trackedEntity = trackedEntities.get(0);
    List<TrackedEntityAttributeValue> attributeValues =
        trackedEntityAttributeValueService.getTrackedEntityAttributeValues(trackedEntity);
    assertEquals(3, attributeValues.size());
  }
}
