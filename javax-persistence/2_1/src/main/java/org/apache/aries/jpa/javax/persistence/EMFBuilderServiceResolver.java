/*  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.aries.jpa.javax.persistence;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Map.Entry;

import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceException;
import javax.persistence.spi.LoadState;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.ProviderUtil;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

public class EMFBuilderServiceResolver implements PersistenceProvider, ProviderUtil {

	private final ServiceTracker<Object, Object> tracker;
	
	public EMFBuilderServiceResolver(BundleContext context) {
		tracker = new ServiceTracker<Object, Object>(context, 
				"org.osgi.service.jpa.EntityManagerFactoryBuilder", null);
		
		tracker.open();
	}
	
	public void close() {
		tracker.close();
	}
	
	/**
	 * This method looks for a matching EntityManagerFactoryBuilder service to create the
	 * EMF with.
	 */
	@Override
	public EntityManagerFactory createEntityManagerFactory(String emName, @SuppressWarnings("rawtypes") Map map) {
		for (Entry<ServiceReference<Object>, Object> e : tracker.getTracked().entrySet()) {
			String serviceUnitName = String.valueOf(e.getKey().getProperty("osgi.unit.name"));
			
			if(serviceUnitName.equals(emName)) {
				try {
					Object emfBuilder = e.getValue();
					Method m = emfBuilder.getClass().getMethod("createEntityManagerFactory", Map.class);
					return (EntityManagerFactory) m.invoke(emfBuilder, map);
				} catch (Exception ex) {
					throw new PersistenceException("Failed to create an EntityManagerFactory for unit " +
							emName, ex);
				}
			}
		}
		return null;
	}

	/**
	 * This method is not intended to be used, as this PersistenceProvider is internal to
	 * the Spec API for supporting static factory usage (see OSGi JPA spec 127.7.1)
	 */
	@Override
	public EntityManagerFactory createContainerEntityManagerFactory(PersistenceUnitInfo info, @SuppressWarnings("rawtypes") Map map) {
		return null;
	}

	@Override
	public ProviderUtil getProviderUtil() {
		return this;
	}

	@Override
	public LoadState isLoadedWithoutReference(Object entity, String attributeName) {
		return LoadState.UNKNOWN;
	}

	@Override
	public LoadState isLoadedWithReference(Object entity, String attributeName) {
		return LoadState.UNKNOWN;
	}

	@Override
	public LoadState isLoaded(Object entity) {
		return LoadState.UNKNOWN;
	}

	/**
	 * This operation is not provided for OSGi JPA Static Factory access
	 */
	@Override
	public void generateSchema(PersistenceUnitInfo arg0, @SuppressWarnings("rawtypes") Map arg1) {
		return;
	}

	/**
	 * This operation is not provided for OSGi JPA Static Factory access
	 */
	@Override
	public boolean generateSchema(String arg0, @SuppressWarnings("rawtypes") Map arg1) {
		return false;
	}

}
