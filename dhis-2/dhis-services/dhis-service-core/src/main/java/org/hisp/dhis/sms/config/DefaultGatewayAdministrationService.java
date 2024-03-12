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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.CheckForNull;
import lombok.extern.slf4j.Slf4j;
import org.hisp.dhis.common.CodeGenerator;
import org.hisp.dhis.common.IllegalQueryException;
import org.jasypt.encryption.pbe.PBEStringEncryptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * @author Zubair <rajazubair.asghar@gmail.com>
 */
@Slf4j
@Service("org.hisp.dhis.sms.config.GatewayAdministrationService")
public class DefaultGatewayAdministrationService implements GatewayAdministrationService {

  private final AtomicBoolean hasGateways = new AtomicBoolean();

  private final SmsConfigurationManager smsConfigurationManager;

  private final PBEStringEncryptor pbeStringEncryptor;

  public DefaultGatewayAdministrationService(
      SmsConfigurationManager smsConfigurationManager,
      @Qualifier("tripleDesStringEncryptor") PBEStringEncryptor pbeStringEncryptor) {
    checkNotNull(smsConfigurationManager);
    checkNotNull(pbeStringEncryptor);

    this.smsConfigurationManager = smsConfigurationManager;
    this.pbeStringEncryptor = pbeStringEncryptor;
  }

  // -------------------------------------------------------------------------
  // GatewayAdministrationService implementation
  // -------------------------------------------------------------------------

  @EventListener
  public void handleContextRefresh(ContextRefreshedEvent event) {
    updateHasGatewaysState();
  }

  @Override
  public void setDefaultGateway(SmsGatewayConfig config) {
    SmsConfiguration configuration = getSmsConfiguration();

    configuration
        .getGateways()
        .forEach(gateway -> gateway.setDefault(Objects.equals(gateway.getUid(), config.getUid())));

    smsConfigurationManager.updateSmsConfiguration(configuration);
    updateHasGatewaysState();
  }

  @Override
  public boolean addGateway(SmsGatewayConfig config) {
    if (config == null) {
      return false;
    }

    config.setUid(CodeGenerator.generateCode(10));

    SmsConfiguration smsConfiguration = getSmsConfiguration();
    if (smsConfiguration.getGateways().stream().anyMatch(c -> c.getClass() == config.getClass()))
      return false;

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
  public void updateGateway(
      @CheckForNull SmsGatewayConfig persisted, @CheckForNull SmsGatewayConfig updated)
      throws IllegalQueryException {
    if (updated == null) throw new IllegalQueryException("Gateway configuration cannot be null");
    if (persisted == null)
      throw new IllegalQueryException(
          String.format("msGatewayConfig with id %s could not be found.", updated.getUid()));
    if (persisted.getClass() != updated.getClass())
      throw new IllegalQueryException("Type of an existing configuration cannot be changed");

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

    if (persisted instanceof ClickatellGatewayConfig
        && updated instanceof ClickatellGatewayConfig) {
      ClickatellGatewayConfig from = (ClickatellGatewayConfig) persisted;
      ClickatellGatewayConfig to = (ClickatellGatewayConfig) updated;
      if (to.getAuthToken() == null) to.setAuthToken(from.getAuthToken());
    }

    if (persisted instanceof GenericHttpGatewayConfig
        && updated instanceof GenericHttpGatewayConfig) {
      GenericHttpGatewayConfig from = (GenericHttpGatewayConfig) persisted;
      GenericHttpGatewayConfig to = (GenericHttpGatewayConfig) updated;
      Map<String, GenericGatewayParameter> oldParamsByKey =
          from.getParameters().stream().collect(toMap(GenericGatewayParameter::getKey, identity()));

      for (GenericGatewayParameter newParam : to.getParameters()) {
        if (newParam.isConfidential()) {
          GenericGatewayParameter oldParam = oldParamsByKey.get(newParam.getKey());
          String newValue = newParam.getValue();
          String oldValue = oldParam == null ? null : oldParam.getValue();

          if (isBlank(newValue)) {
            // keep the old already encoded value
            newParam.setValue(oldValue);
          } else if (!Objects.equals(oldValue, newValue)) {
            newParam.setValue(pbeStringEncryptor.encrypt(newValue));
          }
          // else: new=old => keep already encoded value
        }
      }
    }

    SmsConfiguration configuration = getSmsConfiguration();

    List<SmsGatewayConfig> gateways = configuration.getGateways();
    gateways.remove(persisted);
    gateways.add(updated);

    smsConfigurationManager.updateSmsConfiguration(configuration);
    updateHasGatewaysState();
  }

  @Override
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
  public SmsGatewayConfig getByUid(String uid) {
    return getSmsConfiguration().getGateways().stream()
        .filter(gw -> gw.getUid().equals(uid))
        .findFirst()
        .orElse(null);
  }

  @Override
  public SmsGatewayConfig getDefaultGateway() {
    return getSmsConfiguration().getGateways().stream()
        .filter(SmsGatewayConfig::isDefault)
        .findFirst()
        .orElse(null);
  }

  @Override
  public boolean hasDefaultGateway() {
    return getDefaultGateway() != null;
  }

  @Override
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
}
