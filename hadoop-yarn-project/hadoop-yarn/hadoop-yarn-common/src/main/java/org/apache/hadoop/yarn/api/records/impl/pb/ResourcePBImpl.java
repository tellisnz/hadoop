/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.api.records.impl.pb;

import org.apache.hadoop.classification.InterfaceAudience.Private;
import org.apache.hadoop.classification.InterfaceStability.Unstable;
import org.apache.hadoop.yarn.api.protocolrecords.ResourceTypes;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.ResourceInformation;
import org.apache.hadoop.yarn.exceptions.ResourceNotFoundException;
import org.apache.hadoop.yarn.exceptions.YarnRuntimeException;
import org.apache.hadoop.yarn.proto.YarnProtos.ResourceProto;
import org.apache.hadoop.yarn.proto.YarnProtos.ResourceProtoOrBuilder;
import org.apache.hadoop.yarn.proto.YarnProtos.ResourceInformationProto;
import org.apache.hadoop.yarn.util.resource.ResourceUtils;
import org.apache.hadoop.yarn.util.UnitsConversionUtil;

import java.util.*;

@Private
@Unstable
public class ResourcePBImpl extends Resource {

  ResourceProto proto = ResourceProto.getDefaultInstance();
  ResourceProto.Builder builder = null;
  boolean viaProto = false;

  private Map<String, ResourceInformation> resources;

  public ResourcePBImpl() {
    builder = ResourceProto.newBuilder();
  }

  public ResourcePBImpl(ResourceProto proto) {
    this.proto = proto;
    viaProto = true;
    this.resources = null;
    initResources();
  }

  public ResourceProto getProto() {
    mergeLocalToProto();
    proto = viaProto ? proto : builder.build();
    viaProto = true;
    return proto;
  }

  private void maybeInitBuilder() {
    if (viaProto || builder == null) {
      builder = ResourceProto.newBuilder(proto);
    }
    viaProto = false;
  }

  @Override
  public int getMemory() {
    // memory should always be present
    initResourcesMap();
    ResourceInformation ri =
        this.getResourceInformation(ResourceInformation.MEMORY_MB.getName());
    if (ri.getValue() > Integer.MAX_VALUE) {
      throw new YarnRuntimeException(
          "Illegal value for memory - " + ri.getValue()
              + ". Value must be less than " + Integer.MAX_VALUE);
    }
    return (int) UnitsConversionUtil.convert(ri.getUnits(), "Mi", ri.getValue())
        .longValue();
  }

  @Override
  public void setMemory(int memory) {
    setResourceInformation(ResourceInformation.MEMORY_MB.getName(),
        ResourceInformation.newInstance(ResourceInformation.MEMORY_MB.getName(),
            ResourceInformation.MEMORY_MB.getUnits(), (long) memory));

  }

  @Override
  public int getVirtualCores() {
    // vcores should always be present
    initResourcesMap();
    Long vcores = this.getResourceValue(ResourceInformation.VCORES.getName());
    if (vcores > Integer.MAX_VALUE) {
      throw new YarnRuntimeException(
          "Illegal value for vcores - " + vcores + ". Value must be less than "
              + Integer.MAX_VALUE);
    }
    return (int) vcores.longValue();
  }

  @Override
  public void setVirtualCores(int vCores) {
    try {
      setResourceValue(ResourceInformation.VCORES.getName(),
          Long.valueOf(vCores));
    } catch (ResourceNotFoundException re) {
      this.setResourceInformation(ResourceInformation.VCORES.getName(),
          ResourceInformation.newInstance(ResourceInformation.VCORES.getName(),
              (long) vCores));
    }
  }

  private void initResources() {
    if (this.resources != null) {
      return;
    }
    ResourceProtoOrBuilder p = viaProto ? proto : builder;
    initResourcesMap();
    for (ResourceInformationProto entry : p.getResourceValueMapList()) {
      ResourceTypes type =
          entry.hasType() ? ProtoUtils.convertFromProtoFormat(entry.getType()) :
              ResourceTypes.COUNTABLE;
      String units = entry.hasUnits() ? entry.getUnits() : "";
      Long value = entry.hasValue() ? entry.getValue() : 0L;
      ResourceInformation ri =
          ResourceInformation.newInstance(entry.getKey(), units, value, type);
      resources.put(ri.getName(), ri);
    }
    if(this.getMemory() != p.getMemory()) {
      setMemory(p.getMemory());
    }
    if(this.getVirtualCores() != p.getVirtualCores()) {
      setVirtualCores(p.getVirtualCores());
    }
  }

  @Override
  public void setResourceInformation(String resource,
      ResourceInformation resourceInformation) {
    maybeInitBuilder();
    if (resource == null || resourceInformation == null) {
      throw new IllegalArgumentException(
          "resource and/or resourceInformation cannot be null");
    }
    if (!resource.equals(resourceInformation.getName())) {
      resourceInformation.setName(resource);
    }
    initResourcesMap();
    resources.put(resource, resourceInformation);
  }

  @Override
  public void setResourceValue(String resource, Long value)
      throws ResourceNotFoundException {
    maybeInitBuilder();
    if (resource == null) {
      throw new IllegalArgumentException("resource type object cannot be null");
    }
    if (resources == null || (!resources.containsKey(resource))) {
      throw new ResourceNotFoundException(
          "Resource " + resource + " not found");
    }
    ResourceInformation ri = resources.get(resource);
    ri.setValue(value);
    resources.put(resource, ri);
  }

  @Override
  public Map<String, ResourceInformation> getResources() {
    initResources();
    return Collections.unmodifiableMap(this.resources);
  }

  @Override
  public ResourceInformation getResourceInformation(String resource) {
    initResources();
    if (this.resources.containsKey(resource)) {
      return this.resources.get(resource);
    }
    throw new ResourceNotFoundException("Could not find entry for " + resource);
  }

  @Override
  public Long getResourceValue(String resource) {
    initResources();
    if (this.resources.containsKey(resource)) {
      return this.resources.get(resource).getValue();
    }
    throw new ResourceNotFoundException("Could not find entry for " + resource);
  }

  private void initResourcesMap() {
    if (resources == null) {
      resources = new HashMap<>();
      Map<String, ResourceInformation> types = ResourceUtils.getResourceTypes();
      if (types == null) {
        throw new YarnRuntimeException(
            "Got null return value from ResourceUtils.getResourceTypes()");
      }
      for (Map.Entry<String, ResourceInformation> entry : types.entrySet()) {
        resources.put(entry.getKey(),
            ResourceInformation.newInstance(entry.getValue()));
      }
    }
  }

  synchronized private void mergeLocalToBuilder() {
    builder.clearResourceValueMap();
    if (resources != null && !resources.isEmpty()) {
      for (Map.Entry<String, ResourceInformation> entry : resources.entrySet()) {
        ResourceInformationProto.Builder e = ResourceInformationProto.newBuilder();
        e.setKey(entry.getKey());
        e.setUnits(entry.getValue().getUnits());
        e.setType(
            ProtoUtils.converToProtoFormat(entry.getValue().getResourceType()));
        e.setValue(entry.getValue().getValue());
        builder.addResourceValueMap(e);
      }
    }
    builder.setMemory(this.getMemory());
    builder.setVirtualCores(this.getVirtualCores());
  }

  private void mergeLocalToProto() {
    if (viaProto) {
      maybeInitBuilder();
    }
    mergeLocalToBuilder();
    proto = builder.build();
    viaProto = true;
  }
}  
