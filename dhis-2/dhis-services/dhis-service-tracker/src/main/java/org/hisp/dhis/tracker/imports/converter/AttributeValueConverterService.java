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
package org.hisp.dhis.tracker.imports.converter;

import static org.hisp.dhis.util.DateUtils.fromInstant;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.hisp.dhis.trackedentity.TrackedEntityAttribute;
import org.hisp.dhis.trackedentityattributevalue.TrackedEntityAttributeValue;
import org.hisp.dhis.tracker.imports.domain.Attribute;
import org.hisp.dhis.tracker.imports.preheat.TrackerPreheat;
import org.hisp.dhis.user.UserDetails;
import org.springframework.stereotype.Service;

/**
 * @author Luciano Fiandesio
 */
@Service
public class AttributeValueConverterService
    implements TrackerConverterService<Attribute, TrackedEntityAttributeValue> {

  @Override
  public TrackedEntityAttributeValue from(TrackerPreheat preheat, Attribute at, UserDetails user) {
    TrackedEntityAttribute attribute = preheat.getTrackedEntityAttribute(at.getAttribute());

    if (attribute == null) {
      return null;
    }

    TrackedEntityAttributeValue teav = new TrackedEntityAttributeValue();

    teav.setCreated(fromInstant(at.getCreatedAt()));
    teav.setLastUpdated(fromInstant(at.getUpdatedAt()));
    teav.setStoredBy(at.getStoredBy());
    teav.setValue(at.getValue());
    teav.setAttribute(attribute);

    return teav;
  }

  @Override
  public List<TrackedEntityAttributeValue> from(
      TrackerPreheat preheat, List<Attribute> attributes, UserDetails user) {
    return attributes.stream()
        .filter(Objects::nonNull)
        .map(n -> from(preheat, n, user))
        .collect(Collectors.toList());
  }
}
