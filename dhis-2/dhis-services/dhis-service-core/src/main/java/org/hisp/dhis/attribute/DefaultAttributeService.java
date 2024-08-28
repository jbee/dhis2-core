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
package org.hisp.dhis.attribute;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.hisp.dhis.attribute.exception.NonUniqueAttributeValueException;
import org.hisp.dhis.cache.Cache;
import org.hisp.dhis.cache.CacheProvider;
import org.hisp.dhis.common.IdentifiableObject;
import org.hisp.dhis.common.IdentifiableObjectManager;
import org.hisp.dhis.hibernate.HibernateProxyUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Morten Olav Hansen <mortenoh@gmail.com>
 */
@Service("org.hisp.dhis.attribute.AttributeService")
public class DefaultAttributeService implements AttributeService {
  private final Cache<Attribute> attributeCache;

  // -------------------------------------------------------------------------
  // Dependencies
  // -------------------------------------------------------------------------

  private final AttributeStore attributeStore;
  private final IdentifiableObjectManager manager;

  public DefaultAttributeService(
      AttributeStore attributeStore,
      IdentifiableObjectManager manager,
      CacheProvider cacheProvider) {
    checkNotNull(attributeStore);
    checkNotNull(manager);

    this.attributeStore = attributeStore;
    this.manager = manager;
    this.attributeCache = cacheProvider.createMetadataAttributesCache();
  }

  // -------------------------------------------------------------------------
  // Attribute implementation
  // -------------------------------------------------------------------------

  @Override
  @Transactional
  public void addAttribute(Attribute attribute) {
    attributeStore.save(attribute);
  }

  @Override
  @Transactional
  public void deleteAttribute(Attribute attribute) {
    attributeStore.delete(attribute);
    attributeCache.invalidate(attribute.getUid());
  }

  @Override
  public void invalidateCachedAttribute(String attributeUid) {
    attributeCache.invalidate(attributeUid);
  }

  @Override
  @Transactional(readOnly = true)
  public Attribute getAttribute(long id) {
    return attributeStore.get(id);
  }

  @Override
  @Transactional(readOnly = true)
  public Attribute getAttribute(String uid) {
    return attributeCache.get(
        uid,
        attr -> {
          Attribute attribute = attributeStore.getByUid(uid);
          HibernateProxyUtils.unproxy(attribute);
          return attribute;
        });
  }

  @Override
  @Transactional(readOnly = true)
  public Attribute getAttributeByName(String name) {
    return attributeStore.getByName(name);
  }

  @Override
  @Transactional(readOnly = true)
  public Attribute getAttributeByCode(String code) {
    return attributeStore.getByCode(code);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Attribute> getAllAttributes() {
    return attributeStore.getAll();
  }

  @Override
  @Transactional(readOnly = true)
  public List<Attribute> getAttributesByIds(Set<String> ids) {
    return attributeStore.getByUid(ids);
  }

  // -------------------------------------------------------------------------
  // AttributeValue implementation
  // -------------------------------------------------------------------------

  @Override
  @Transactional
  @SuppressWarnings({"unchecked", "rawtypes"})
  public <T extends IdentifiableObject> void addAttributeValue(
      T object, AttributeValue attributeValue) throws NonUniqueAttributeValueException {
    if (object == null || attributeValue == null || attributeValue.getAttribute() == null) {
      return;
    }

    Attribute attribute = getAttribute(attributeValue.getAttribute().getUid());

    Class realClass = HibernateProxyUtils.getRealClass(object);

    if (Objects.isNull(attribute) || !attribute.getSupportedClasses().contains(realClass)) {
      return;
    }

    if (attribute.isUnique()
        && !manager.isAttributeValueUnique(realClass, object, attributeValue)) {
      throw new NonUniqueAttributeValueException(attributeValue);
    }

    object.addAttributeValue(attributeValue.getAttribute().getUid(), attributeValue.getValue());
    manager.update(object);
  }

  @Override
  @Transactional
  public <T extends IdentifiableObject> void deleteAttributeValue(
      T object, AttributeValue attributeValue) {
    object.removeAttributeValue(attributeValue.getAttribute().getUid());
    manager.update(object);
  }

  @Override
  @Transactional
  public <T extends IdentifiableObject> void deleteAttributeValues(
      T object, Set<AttributeValue> attributeValues) {
    attributeValues.forEach(a -> object.removeAttributeValue(a.getAttribute().getUid()));
    manager.update(object);
  }

  @Override
  @Transactional(readOnly = true)
  public <T extends IdentifiableObject> void generateAttributes(List<T> entityList) {
    // FIXME(JB) originally this was where the full Attribute was put into each AttributeValue
  }
}
