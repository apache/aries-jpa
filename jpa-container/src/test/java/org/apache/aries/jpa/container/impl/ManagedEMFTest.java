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
package org.apache.aries.jpa.container.impl;

import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.ConfigurationException;

@RunWith(MockitoJUnitRunner.class)
public class ManagedEMFTest {

	@Mock
	AriesEntityManagerFactoryBuilder builder;
	
    @Test
    public void testEmfWithoutProps() throws InvalidSyntaxException, ConfigurationException {
    	ManagedEMF emf = new ManagedEMF(builder, "test");
        emf.updated(null);
        verify(builder).createEntityManagerFactory(null);
    }

    @Test
    public void testEmfWithProps() throws InvalidSyntaxException, ConfigurationException {
    	ManagedEMF emf = new ManagedEMF(builder, "test");

        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put("hibernate.hbm2ddl.auto", "create-drop");
        emf.updated(props);

        verify(builder).createEntityManagerFactory(Collections.<String, Object>singletonMap(
        		"hibernate.hbm2ddl.auto", "create-drop"));
    }
}
