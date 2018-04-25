/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIESOR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.aries.jpa.blueprint.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.osgi.service.blueprint.container.BlueprintContainer;
import org.osgi.service.blueprint.reflect.ComponentMetadata;

public class BlueprintContainerStub implements BlueprintContainer {

    private Map<String, Object> instances;

    public BlueprintContainerStub() {
        instances = new HashMap<String, Object>();
        instances.put("coordinator", new CoordinatorStub());
        instances.put("em", new EntityManagerStub());
    }

    @Override
    public Set<String> getComponentIds() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object getComponentInstance(String id) {
        if ("em".equals(id)) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return instances.get(id);
    }

    @Override
    public ComponentMetadata getComponentMetadata(String id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends ComponentMetadata> Collection<T> getMetadata(Class<T> type) {
        // TODO Auto-generated method stub
        return null;
    }

}
