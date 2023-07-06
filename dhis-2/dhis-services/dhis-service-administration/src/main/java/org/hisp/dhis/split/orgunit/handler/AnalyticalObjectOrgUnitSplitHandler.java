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
package org.hisp.dhis.split.orgunit.handler;

import java.util.List;
import lombok.AllArgsConstructor;
import org.hisp.dhis.common.AnalyticalObject;
import org.hisp.dhis.common.AnalyticalObjectService;
import org.hisp.dhis.eventchart.EventChartService;
import org.hisp.dhis.eventreport.EventReportService;
import org.hisp.dhis.mapping.MappingService;
import org.hisp.dhis.split.orgunit.OrgUnitSplitRequest;
import org.hisp.dhis.visualization.VisualizationService;
import org.springframework.stereotype.Service;

/**
 * @author Lars Helge Overland
 */
@Service
@AllArgsConstructor
public class AnalyticalObjectOrgUnitSplitHandler {
  private final VisualizationService visualizationService;

  private final MappingService mapViewService;

  private final EventReportService eventReportService;

  private final EventChartService eventChartService;

  public void splitAnalyticalObjects(OrgUnitSplitRequest request) {
    splitAnalyticalObject(visualizationService, request);
    splitAnalyticalObject(mapViewService, request);
    splitAnalyticalObject(eventReportService, request);
    splitAnalyticalObject(eventChartService, request);
  }

  private <T extends AnalyticalObject> void splitAnalyticalObject(
      AnalyticalObjectService<T> service, OrgUnitSplitRequest request) {
    List<T> objects = service.getAnalyticalObjects(request.getSource());

    objects.forEach(
        ao -> {
          ao.getOrganisationUnits().addAll(request.getTargets());
          ao.getOrganisationUnits().remove(request.getSource());
        });
  }
}
