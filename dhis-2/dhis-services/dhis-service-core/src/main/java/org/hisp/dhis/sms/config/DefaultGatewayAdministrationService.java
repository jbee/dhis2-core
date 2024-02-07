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
package org.hisp.dhis.sms.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hisp.dhis.common.CodeGenerator;
import org.hisp.dhis.common.IndirectTransactional;
import org.hisp.dhis.common.NonTransactional;
import org.hisp.dhis.feedback.ConflictException;
import org.hisp.dhis.feedback.NotFoundException;
import org.jasypt.encryption.pbe.PBEStringEncryptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * @author Zubair <rajazubair.asghar@gmail.com>
 */
@Slf4j
@RequiredArgsConstructor
@Service("org.hisp.dhis.sms.config.GatewayAdministrationService")
public class DefaultGatewayAdministrationService implements GatewayAdministrationService {

  private final AtomicBoolean hasGateways = new AtomicBoolean();

  private final SmsConfigurationManager smsConfigurationManager;

  @Qualifier("tripleDesStringEncryptor")
  private final PBEStringEncryptor pbeStringEncryptor;

  // -------------------------------------------------------------------------
  // GatewayAdministrationService implementation
  // -------------------------------------------------------------------------

  @EventListener
  public void handleContextRefresh(ContextRefreshedEvent event) {
    updateHasGatewaysState();
  }

  @Override
  @IndirectTransactional
  public void setDefaultGateway(SmsGatewayConfig config) {
    SmsConfiguration configuration = getSmsConfiguration();

    configuration
        .getGateways()
        .forEach(gateway -> gateway.setDefault(Objects.equals(gateway.getUid(), config.getUid())));

    smsConfigurationManager.updateSmsConfiguration(configuration);
    updateHasGatewaysState();
  }

  @Override
  @IndirectTransactional
  public boolean addGateway(SmsGatewayConfig config) {
    if (config == null) {
      return false;
    }

    config.setUid(CodeGenerator.generateCode(10));

    SmsConfiguration smsConfiguration = getSmsConfiguration();

    config.setDefault(smsConfiguration.getGateways().isEmpty());

    if (config instanceof GenericHttpGatewayConfig) {
      ((GenericHttpGatewayConfig) config)
          .getParameters().stream()
              .filter(GenericGatewayParameter::isConfidential)
              .forEach(p -> p.setValue(pbeStringEncryptor.encrypt(p.getValue())));
    }

    config.setPassword(pbeStringEncryptor.encrypt(config.getPassword()));

    smsConfiguration.getGateways().add(config);

    smsConfigurationManager.updateSmsConfiguration(smsConfiguration);
    updateHasGatewaysState();

    return true;
  }

  @Override
  @IndirectTransactional
  public void updateGateway(
      @CheckForNull SmsGatewayConfig persisted, @CheckForNull SmsGatewayConfig updated)
      throws NotFoundException, ConflictException {
    if (persisted == null) throw new NotFoundException(SmsGatewayConfig.class, updated.getUid());
    if (updated == null) throw new ConflictException("Gateway configuration cannot be null");
    if (persisted.getClass() != updated.getClass())
      throw new ConflictException("Type of an existing configuration cannot be changed");

    updated.setUid(persisted.getUid());
    updated.setDefault(persisted.isDefault());

    if (updated.getPassword() == null) {
      // keep old password when undefined
      updated.setPassword(persisted.getPassword());
    } else if (persisted.getPassword() != null
        && !persisted.getPassword().equals(updated.getPassword())) {
      // password change
      updated.setPassword(pbeStringEncryptor.encrypt(updated.getPassword()));
    }

    if (persisted instanceof ClickatellGatewayConfig from) {
      // keep old auth token when undefined
      if (updated instanceof ClickatellGatewayConfig to && to.getAuthToken() == null) {
        to.setAuthToken(from.getAuthToken());
      }
    }

    if (persisted instanceof GenericHttpGatewayConfig from
        && updated instanceof GenericHttpGatewayConfig to) {
      // FIXME this logic below seems broken but it is unclear what it tries to do
      // best guess: encrypt all new confidential parameters and add all existing that are not in
      // the updated
      // issues:
      // - not all confidential new are encrypted
      // - parameters equals compares all fields, hence any difference makes the parameter stay (I
      // guess key should be compared)
      // - it is unclear what stream().distinct() would leave behind, better build a map by key and
      // then a list from values

      List<GenericGatewayParameter> newList = new ArrayList<>();

      List<GenericGatewayParameter> persistedList =
          from.getParameters().stream()
              .filter(GenericGatewayParameter::isConfidential)
              .collect(Collectors.toList());

      List<GenericGatewayParameter> updatedList =
          to.getParameters().stream()
              .filter(GenericGatewayParameter::isConfidential)
              .collect(Collectors.toList());

      for (GenericGatewayParameter p : updatedList) {
        if (!isPresent(persistedList, p)) {
          p.setValue(pbeStringEncryptor.encrypt(p.getValue()));
        }

        newList.add(p);
      }

      to.setParameters(
          Stream.concat(to.getParameters().stream(), newList.stream())
              .distinct()
              .collect(Collectors.toList()));

      updated = to;
    }

    SmsConfiguration configuration = getSmsConfiguration();

    List<SmsGatewayConfig> gateways = configuration.getGateways();
    gateways.remove(persisted);
    gateways.add(updated);

    smsConfigurationManager.updateSmsConfiguration(configuration);
    updateHasGatewaysState();
  }

  @Override
  @IndirectTransactional
  public boolean removeGatewayByUid(String uid) {
    SmsConfiguration smsConfiguration = getSmsConfiguration();

    List<SmsGatewayConfig> gateways = smsConfiguration.getGateways();
    SmsGatewayConfig removed =
        gateways.stream().filter(gateway -> gateway.getUid().equals(uid)).findFirst().orElse(null);
    if (removed == null) {
      return false;
    }
    gateways.remove(removed);
    if (removed.isDefault() && !gateways.isEmpty()) {
      gateways.get(0).setDefault(true);
    }
    smsConfigurationManager.updateSmsConfiguration(smsConfiguration);
    updateHasGatewaysState();
    return true;
  }

  @Override
  @IndirectTransactional
  public SmsGatewayConfig getByUid(String uid) {
    return getSmsConfiguration().getGateways().stream()
        .filter(gw -> gw.getUid().equals(uid))
        .findFirst()
        .orElse(null);
  }

  @Override
  @IndirectTransactional
  public SmsGatewayConfig getDefaultGateway() {
    return getSmsConfiguration().getGateways().stream()
        .filter(SmsGatewayConfig::isDefault)
        .findFirst()
        .orElse(null);
  }

  @Override
  @IndirectTransactional
  public boolean hasDefaultGateway() {
    return getDefaultGateway() != null;
  }

  @Override
  @NonTransactional
  public boolean hasGateways() {
    return hasGateways.get();
  }

  // -------------------------------------------------------------------------
  // Supportive methods
  // -------------------------------------------------------------------------

  private SmsConfiguration getSmsConfiguration() {
    SmsConfiguration smsConfiguration = smsConfigurationManager.getSmsConfiguration();

    if (smsConfiguration != null) {
      return smsConfiguration;
    }

    return new SmsConfiguration();
  }

  private void updateHasGatewaysState() {
    SmsConfiguration smsConfiguration = smsConfigurationManager.getSmsConfiguration();

    if (smsConfiguration == null) {
      log.info("SMS configuration not found");
      hasGateways.set(false);
      return;
    }

    List<SmsGatewayConfig> gatewayList = smsConfiguration.getGateways();

    if (gatewayList == null || gatewayList.isEmpty()) {
      log.info("No Gateway configuration found");

      hasGateways.set(false);
      return;
    }

    log.info("Gateway configuration found: " + gatewayList);

    hasGateways.set(true);
  }

  private boolean isPresent(
      List<GenericGatewayParameter> parameters, GenericGatewayParameter parameter) {
    return parameters.stream().anyMatch(p -> p.equals(parameter));
  }
}
