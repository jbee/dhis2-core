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
package org.hisp.dhis.user;

import static java.util.Collections.unmodifiableMap;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.hisp.dhis.common.IdentifiableObject;
import org.springframework.security.core.GrantedAuthority;

public interface UserDetails extends org.springframework.security.core.userdetails.UserDetails {

  // TODO MAS: This is a workaround and usually indicated a design flaw, and that we should refactor
  // to use UserDetails higher up in the layers.
  @CheckForNull
  static UserDetails fromUser(@CheckForNull User user) {
    if (user == null) return null;
    // TODO check in session if a UserDetails for the user already exists (if the user is the
    // current user)
    return createUserDetails(
        user, user.isAccountNonLocked(), user.isCredentialsNonExpired(), new HashMap<>());
  }

  @CheckForNull
  static UserDetails createUserDetails(
      @CheckForNull User user,
      boolean accountNonLocked,
      boolean credentialsNonExpired,
      @CheckForNull Map<String, Serializable> settings) {
    if (user == null) {
      return null;
    }

    return UserDetailsImpl.builder()
        .id(user.getId())
        .uid(user.getUid())
        .username(user.getUsername())
        .password(user.getPassword())
        .externalAuth(user.isExternalAuth())
        .isTwoFactorEnabled(user.isTwoFactorEnabled())
        .code(user.getCode())
        .firstName(user.getFirstName())
        .surname(user.getSurname())
        .enabled(user.isEnabled())
        .accountNonExpired(user.isAccountNonExpired())
        .accountNonLocked(accountNonLocked)
        .credentialsNonExpired(credentialsNonExpired)
        .authorities(user.getAuthorities())
        .allAuthorities(
            Set.copyOf(user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()))
        .isSuper(user.isSuper())
        .allRestrictions(user.getAllRestrictions())
        .userRoleIds(setOfIds(user.getUserRoles()))
        .userGroupIds(user.getUid() == null ? Set.of() : setOfIds(user.getGroups()))
        .userOrgUnitIds(setOfIds(user.getOrganisationUnits()))
        .userSearchOrgUnitIds(setOfIds(user.getTeiSearchOrganisationUnitsWithFallback()))
        .userDataOrgUnitIds(setOfIds(user.getDataViewOrganisationUnitsWithFallback()))
        .userSettings(settings == null ? Map.of() : unmodifiableMap(settings))
        .build();
  }

  @Nonnull
  @Override
  Collection<? extends GrantedAuthority> getAuthorities();

  @Override
  String getPassword();

  @Override
  String getUsername();

  @Override
  boolean isAccountNonExpired();

  @Override
  boolean isAccountNonLocked();

  @Override
  boolean isCredentialsNonExpired();

  @Override
  boolean isEnabled();

  boolean isSuper();

  String getUid();

  Long getId();

  String getCode();

  String getFirstName();

  String getSurname();

  @Nonnull
  Set<String> getUserGroupIds();

  @Nonnull
  Set<String> getAllAuthorities();

  @Nonnull
  Set<String> getUserOrgUnitIds();

  @Nonnull
  Set<String> getUserSearchOrgUnitIds();

  @Nonnull
  Set<String> getUserDataOrgUnitIds();

  boolean hasAnyAuthority(Collection<String> auths);

  boolean isAuthorized(String auth);

  @Nonnull
  Map<String, Serializable> getUserSettings();

  @Nonnull
  Set<String> getUserRoleIds();

  boolean canModifyUser(User userToModify);

  boolean isExternalAuth();

  boolean isTwoFactorEnabled();

  boolean hasAnyRestrictions(Collection<String> restrictions);

  void setId(Long id);

  default boolean isInUserHierarchy(String orgUnitPath) {
    return isInUserHierarchy(orgUnitPath, getUserOrgUnitIds());
  }

  default boolean isInUserSearchHierarchy(String orgUnitPath) {
    return isInUserHierarchy(orgUnitPath, getUserSearchOrgUnitIds());
  }

  default boolean isInUserDataHierarchy(String orgUnitPath) {
    return isInUserHierarchy(orgUnitPath, getUserDataOrgUnitIds());
  }

  private static boolean isInUserHierarchy(
      @CheckForNull String orgUnitPath, @Nonnull Set<String> orgUnitIds) {
    if (orgUnitPath == null) return false;
    for (String uid : orgUnitPath.split("/")) if (orgUnitIds.contains(uid)) return true;
    return false;
  }

  private static Set<String> setOfIds(
      @CheckForNull Collection<? extends IdentifiableObject> objects) {
    return objects == null || objects.isEmpty()
        ? Set.of()
        : Set.copyOf(objects.stream().map(IdentifiableObject::getUid).toList());
  }
}
