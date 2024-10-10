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
package org.hisp.dhis.webapi.controller;

import static org.hisp.dhis.test.webapi.Assertions.assertWebMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hisp.dhis.external.conf.ConfigurationKey;
import org.hisp.dhis.external.conf.DhisConfigurationProvider;
import org.hisp.dhis.http.HttpStatus;
import org.hisp.dhis.jsontree.JsonArray;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.test.webapi.H2ControllerIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests the {@link AppHubController} using (mocked) REST requests.
 *
 * @author Jan Bernitt
 */
@Transactional
class AppHubControllerTest extends H2ControllerIntegrationTestBase {
  @Autowired private DhisConfigurationProvider configuration;

  @AfterEach
  void cleanUp() {
    // back to default
    configuration.getProperties().remove(ConfigurationKey.APPHUB_API_URL.getKey());
  }

  @Test
  void testListAppHub() {
    JsonArray apps = GET("/appHub").content();
    assertTrue(apps.isArray());
    assertTrue(apps.size() > 0, "There should be apps registered");
  }

  @Test
  void testGetAppHubApiResponse() {
    assertWebMessage(
        "Not Found",
        404,
        "ERROR",
        "404 Not Found: \"{\"statusCode\":404,\"error\":\"Not Found\",\"message\":\"Not Found\"}\"",
        GET("/appHub/v37/test").content(HttpStatus.NOT_FOUND));
  }

  @Test
  void testAppHubInstallResponseContainsAppInfo() {
    JsonArray apps = GET("/appHub").content();
    JsonObject firstApp = apps.getObject(0).getArray("versions").getObject(0);

    String versionId = firstApp.getString("id").string();
    String version = firstApp.getString("version").string();

    HttpResponse response = POST("/appHub/" + versionId);

    assertEquals(201, response.status().code());

    JsonObject result = response.content();
    assertEquals(
        version,
        result.getString("version").string(),
        "an object should be returned containing the version of the app");
  }
}
