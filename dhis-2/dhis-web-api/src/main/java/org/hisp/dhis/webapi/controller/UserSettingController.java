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

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.hisp.dhis.dxf2.webmessage.WebMessageUtils.ok;
import static org.hisp.dhis.util.ObjectUtils.firstNonNull;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.hisp.dhis.common.DhisApiVersion;
import org.hisp.dhis.common.OpenApi;
import org.hisp.dhis.common.UID;
import org.hisp.dhis.dxf2.webmessage.WebMessage;
import org.hisp.dhis.feedback.ConflictException;
import org.hisp.dhis.feedback.ForbiddenException;
import org.hisp.dhis.jsontree.JsonMap;
import org.hisp.dhis.jsontree.JsonPrimitive;
import org.hisp.dhis.setting.UserSettings;
import org.hisp.dhis.user.CurrentUserUtil;
import org.hisp.dhis.user.User;
import org.hisp.dhis.user.UserDetails;
import org.hisp.dhis.user.UserGroup;
import org.hisp.dhis.user.UserService;
import org.hisp.dhis.user.UserSettingService;
import org.hisp.dhis.webapi.mvc.annotation.ApiVersion;
import org.hisp.dhis.webapi.utils.ContextUtils;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Lars Helge Overland
 */
@OpenApi.Document(domain = User.class, group = OpenApi.Document.GROUP_CONFIG)
@RestController
@RequestMapping("/api/userSettings")
@ApiVersion({DhisApiVersion.DEFAULT, DhisApiVersion.ALL})
@RequiredArgsConstructor
public class UserSettingController {

  private final UserSettingService userSettingService;
  private final UserService userService;

  @GetMapping
  public JsonMap<? extends JsonPrimitive> getAllUserSettings(
      @RequestParam(required = false, defaultValue = "true") boolean useFallback,
      @RequestParam(value = "user", required = false) String username,
      @OpenApi.Param({UID.class, User.class}) @RequestParam(value = "userId", required = false)
          String userId,
      HttpServletResponse response)
      throws ForbiddenException, ConflictException {

    response.setHeader(
        ContextUtils.HEADER_CACHE_CONTROL, CacheControl.noCache().cachePrivate().getHeaderValue());

    UserSettings settings = getUserSettings(userId, username, useFallback);
    return settings.toJson();
  }

  @OpenApi.Response(String.class)
  @GetMapping(value = "/{key}")
  public void getUserSettingByKey(
      @PathVariable(value = "key") String key,
      @RequestParam(required = false, defaultValue = "true") boolean useFallback,
      @RequestParam(value = "user", required = false) String username,
      @OpenApi.Param({UID.class, User.class}) @RequestParam(value = "userId", required = false)
          String userId,
      HttpServletResponse response)
      throws IOException, ForbiddenException, ConflictException {

    response.setHeader(
        ContextUtils.HEADER_CACHE_CONTROL, CacheControl.noCache().cachePrivate().getHeaderValue());
    response.setHeader(HttpHeaders.CONTENT_TYPE, ContextUtils.CONTENT_TYPE_TEXT);

    UserSettings settings = getUserSettings(userId, username, useFallback);
    response.getWriter().print(settings.asString(key, ""));
  }

  @PostMapping(value = "/{key}")
  public WebMessage setUserSettingByKey(
      @PathVariable(value = "key") String key,
      @RequestParam(value = "user", required = false) String username,
      @OpenApi.Param({UID.class, User.class}) @RequestParam(value = "userId", required = false)
          String userId,
      @RequestParam(required = false) String value,
      @RequestBody(required = false) String valuePayload)
      throws ForbiddenException, ConflictException {

    String newValue = firstNonNull(value, valuePayload);

    if (isEmpty(newValue))
      throw new ConflictException("You need to specify a new value");

    userSettingService.saveUserSetting(key, newValue, getUser(userId, username));

    return ok("User setting saved");
  }

  @DeleteMapping(value = "/{key}")
  public void deleteUserSettingByKey(
      @PathVariable(value = "key") String key,
      @RequestParam(value = "user", required = false) String username,
      @OpenApi.Param({UID.class, User.class}) @RequestParam(value = "userId", required = false)
          String userId) throws ForbiddenException, ConflictException {
    userSettingService.deleteUserSetting(key, getUser(userId, username));
  }

  /**
   * Tries to find a user based on the uid or username. If none is supplied, currentUser will be
   * returned. If uid or username is found, it will also make sure the current user has access to
   * the user.
   *
   * @param uid the user uid
   * @param username the user username
   * @return the user found with uid or username, or current user if no uid or username was
   *     specified
   */
  private UserDetails getUser(String uid, String username) throws ConflictException, ForbiddenException {
    UserDetails currentUser = CurrentUserUtil.getCurrentUserDetails();
    if (uid == null && username == null)
      return currentUser;

    User user = uid != null
        ? userService.getUser(uid)
        : userService.getUserByUsername(username);

    if (user == null)
      throw new ConflictException("Could not find user '" + firstNonNull(uid, username) + "'");

    Set<String> userGroups =
        user.getGroups().stream().map(UserGroup::getUid).collect(Collectors.toSet());

    if (!userService.canAddOrUpdateUser(userGroups) && !currentUser.canModifyUser(user))
      throw new ForbiddenException("You are not authorized to access user: " + user.getUsername());

    return UserDetails.fromUser(user);
  }

  private UserSettings getUserSettings(String userId, String username, boolean useFallback) throws ConflictException, ForbiddenException {
    UserDetails user = getUser(userId, username);
    return useFallback
        ? user.getUserSettings()
        : userSettingService.getSettings(user);
  }
}
