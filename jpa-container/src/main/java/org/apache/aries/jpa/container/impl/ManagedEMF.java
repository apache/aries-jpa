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

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates an EntityManagerFactory(EMF) for a persistence unit and publishes it
 * as a service. Custom properties can be configured by supplying a config admin
 * configuriation named like the JPA_CONFIGURATION_PREFIX.persistence unit name.
 */
public class ManagedEMF implements ManagedService {

	private static final Logger LOGGER = LoggerFactory.getLogger(ManagedEMF.class);

	private final AriesEntityManagerFactoryBuilder builder;
	
	private final String pUnitName;
	
	private final AtomicBoolean configured = new AtomicBoolean(false);

	public ManagedEMF(AriesEntityManagerFactoryBuilder builder, String name) {
		this.builder = builder;
		this.pUnitName = name;
	}

	@Override
	public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
		
		if(properties == null) {
			if(configured.getAndSet(false)) {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("The configuration has been deleted for persistence unit {}. Destroying the EMF", pUnitName);
				}
				builder.closeEMF();
			} else {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("Ignoring the unset configuration for persistence unit {}", pUnitName);
				}
				return;
			}
		}
		
		Map<String, Object> overrides = (properties != null) ? asMap(properties) : null;
		LOGGER.info("Configuration received for persistence unit {}", pUnitName);
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Using properties override {}", overrides);
		}
		
		builder.createEntityManagerFactory(overrides);
		configured.set(true);
	}

	private Map<String, Object> asMap(Dictionary<String, ?> dict) {
		Map<String, Object> map = new HashMap<String, Object>(); // NOSONAR
		for (Enumeration<String> e = dict.keys(); e.hasMoreElements();) {
			String key = e.nextElement();
			map.put(key, dict.get(key));
		}
		return map;
	}
}
