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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceProviderResolver;
import javax.persistence.spi.PersistenceProviderResolverHolder;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

public class AriesJPASpecActivator implements BundleActivator, PersistenceProviderResolver {

	private ServiceTracker<PersistenceProvider, PersistenceProvider> tracker;
	
	private EMFBuilderServiceResolver emfResolver;
	
	@Override
	public void start(BundleContext context) throws Exception {
		emfResolver = new EMFBuilderServiceResolver(context);
		
		tracker = new ServiceTracker<PersistenceProvider, PersistenceProvider>(context, 
				PersistenceProvider.class, null);
		tracker.open();
		
		PersistenceProviderResolverHolder.setPersistenceProviderResolver(this);
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		PersistenceProviderResolverHolder.setPersistenceProviderResolver(null);
		tracker.close();
		emfResolver.close();
	}

	@Override
	public List<PersistenceProvider> getPersistenceProviders() {
		Collection<PersistenceProvider> services = tracker.getTracked().values();
		
		List<PersistenceProvider> providers = new ArrayList<PersistenceProvider>(services.size() + 1);
		
		providers.add(emfResolver);
		providers.addAll(services);
		
		return providers;
	}

	@Override
	public void clearCachedProviders() {
		// This is a no-op
	}
}
