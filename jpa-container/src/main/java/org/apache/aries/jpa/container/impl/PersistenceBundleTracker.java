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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.aries.jpa.container.parser.impl.PersistenceUnit;
import org.apache.aries.jpa.container.parser.impl.PersistenceUnitParser;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.jpa.EntityManagerFactoryBuilder;
import org.osgi.util.tracker.BundleTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Looks for bundles containing a persistence.xml. For each persistence unit
 * found a PersistenceProviderTracker is installed that tracks matching providers.
 */
public class PersistenceBundleTracker implements BundleTrackerCustomizer<Bundle> {
	
	private static final String OSGI_EXTENDER_NS = "osgi.extender";
	
	private static final String OSGI_CONTRACT_NS = "osgi.contract";
	private static final String JAVA_JPA_CONTRACT = "JavaJPA";

	private static final String OSGI_PACKAGE_NS = "osgi.wiring.package";
	private static final String JAVAX_PERSISTENCE_PKG = "javax.persistence";
	private static final String JPA_SERVICE_PKG = "org.osgi.service.jpa";
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PersistenceBundleTracker.class);
    private final Map<Bundle, Collection<PersistenceProviderTracker>> trackers;
    private final Map<Integer, String> typeMap;
    private final BundleWiring wiring;

    public PersistenceBundleTracker(BundleWiring bundleWiring) {
        wiring = bundleWiring;
		trackers = new HashMap<Bundle, Collection<PersistenceProviderTracker>>();
        this.typeMap = new HashMap<Integer, String>();
        this.typeMap.put(BundleEvent.INSTALLED, "INSTALLED");
        this.typeMap.put(BundleEvent.LAZY_ACTIVATION, "LAZY_ACTIVATION");
        this.typeMap.put(BundleEvent.RESOLVED, "RESOLVED");
        this.typeMap.put(BundleEvent.STARTED, "STARTED");
        this.typeMap.put(BundleEvent.STARTING, "Starting");
        this.typeMap.put(BundleEvent.STOPPED, "STOPPED");
        this.typeMap.put(BundleEvent.UNINSTALLED, "UNINSTALLED");
        this.typeMap.put(256, "UNRESOLVED");
        this.typeMap.put(BundleEvent.UPDATED, "UPDATED");
    }

    @Override
    public synchronized Bundle addingBundle(Bundle bundle, BundleEvent event) {
    	
    	if(incompatibleExtender(bundle)) {
    		// We must not process bundles that we aren't compatible with
    		LOGGER.info("The bundle {} is wired to a different JPA Extender and must be ignored.", 
    				bundle.getSymbolicName());
    		return null;
    	}
    	
    	if(incompatibleClassSpace(bundle)) {
    		// We must not process bundles that we aren't compatible with
    		LOGGER.warn("The bundle {} does not share a class space with the JPA Extender and must be ignored.", 
    				bundle.getSymbolicName());
    		return null;
    	}
    	
        if (event != null && event.getType() == BundleEvent.STOPPED) {
            // Avoid starting persistence units in state STOPPED.
            // TODO No idea why we are called at all in this state
            return bundle;
        }
        if (getTrackers(bundle).isEmpty()) {
            findPersistenceUnits(bundle, event);
        }
        return bundle;
    }

    private boolean incompatibleExtender(Bundle bundle) {
    	
		List<BundleWire> requiredWires = bundle.adapt(BundleWiring.class)
				.getRequiredWires(OSGI_EXTENDER_NS);
		
		for(BundleWire bw : requiredWires) {
			BundleCapability capability = bw.getCapability();
			if(EntityManagerFactoryBuilder.JPA_CAPABILITY_NAME.equals(
					capability.getAttributes().get(OSGI_EXTENDER_NS))) {
				
				// If the persistence bundle requires a different revision for the 
				// JPA extender then we are incompatible, otherwise we are
				return !capability.getRevision().equals(wiring.getRevision());
			}
		}
		
		// If there is no requirement then we must assume that it's safe
		return false;
	}

    /**
     * Sufficient Criteria for having/failing class space compatibility - 
     * <ol>
     *   <li>Sharing a contract for <code>JavaJPA</code></li>
     *   <li>Sharing a provider of <code>javax.persistence</code></li>
     *   <li>Sharing a provider of <code>org.osgi.service.jpa</code></li>
     * </ol>
     * 
     * @param bundle
     * @return
     */
	private boolean incompatibleClassSpace(Bundle bundle) {
		BundleWiring pbWiring = bundle.adapt(BundleWiring.class);
		
		BundleCapability pbContract = getUsedCapability(pbWiring, OSGI_CONTRACT_NS, JAVA_JPA_CONTRACT);
		
		if(pbContract != null) {
			LOGGER.debug("Matching JPA contract for possible persistence bundle {}", bundle.getSymbolicName());
			
			BundleCapability implContract = getUsedCapability(pbWiring, OSGI_CONTRACT_NS, JAVA_JPA_CONTRACT);
			return !pbContract.equals(implContract);
		}
		
		// No contract required by the persistence bundle, try javax.persistence
		BundleCapability pbJavaxPersistence = getUsedCapability(pbWiring, 
					OSGI_PACKAGE_NS, JAVAX_PERSISTENCE_PKG);
			
		if(pbJavaxPersistence != null) {
			LOGGER.debug("Matching JPA API package for possible persistence bundle {}", bundle.getSymbolicName());
			
			BundleCapability implJavaxPersistence = getUsedCapability(pbWiring, 
					OSGI_PACKAGE_NS, JAVAX_PERSISTENCE_PKG);
			return !pbJavaxPersistence.equals(implJavaxPersistence);
		}

		// No jpa package required by the persistence bundle, try org.osgi.service.jpa
		BundleCapability pbJpaService = getUsedCapability(pbWiring, 
				OSGI_PACKAGE_NS, JPA_SERVICE_PKG);
		
		if(pbJpaService != null) {
			LOGGER.debug("Matching JPA service package for possible persistence bundle {}", bundle.getSymbolicName());
			
			BundleCapability implJpaService = getUsedCapability(pbWiring, 
					OSGI_PACKAGE_NS, JPA_SERVICE_PKG);
			return !pbJpaService.equals(implJpaService);
		}
		
		// If there is nothing to clash on then we must assume that it's safe
		return false;
	}

	private BundleCapability getUsedCapability(BundleWiring toCheck, String ns, String attr) {
		BundleCapability cap = null;
		
		for(BundleWire bw : toCheck.getRequiredWires(ns)) {
			BundleCapability capability = bw.getCapability();
			if(attr.equals(capability.getAttributes().get(ns))) {
				cap = capability;
				break;
			}
		}
		
		if(cap == null) {
			for(BundleCapability capability : toCheck.getCapabilities(ns)) {
				if(attr.equals(capability.getAttributes().get(ns))) {
					cap = capability;
					break;
				}
			}
		}
		
		return cap;
	}

	@Override
    public synchronized void removedBundle(Bundle bundle, BundleEvent event, Bundle object) {
        Collection<PersistenceProviderTracker> providerTrackers = trackers.remove(bundle);
        if (providerTrackers == null || providerTrackers.isEmpty()) {
            return;
        }
        LOGGER.info("removing persistence units for " + bundle.getSymbolicName() + " " + getType(event));
        for (PersistenceProviderTracker providerTracker : providerTrackers) {
            providerTracker.close();
        }
        providerTrackers.clear();
    }

    private void findPersistenceUnits(Bundle bundle, BundleEvent event) {
        for (PersistenceUnit punit : PersistenceUnitParser.getPersistenceUnits(bundle)) {
            punit.addAnnotated();
            trackProvider(bundle, punit);
        }
        if (!getTrackers(bundle).isEmpty()) {
            LOGGER.info("Persistence units added for bundle " + bundle.getSymbolicName() + " event " + getEventType(event));
        }
    }

    private static Integer getEventType(BundleEvent event) {
        return (event != null) ? event.getType() : null;
    }

    private void trackProvider(Bundle bundle, PersistenceUnit punit) {
        LOGGER.info(String.format("Found persistence unit %s in bundle %s with provider %s.",
                                  punit.getPersistenceUnitName(), bundle.getSymbolicName(),
                                  punit.getPersistenceProviderClassName()));
        PersistenceProviderTracker tracker = new PersistenceProviderTracker(bundle.getBundleContext(), punit);
        tracker.open();
        getTrackers(bundle).add(tracker);
    }

    @Override
    public void modifiedBundle(Bundle bundle, BundleEvent event, Bundle object) {
        // Only interested in added or removed
    }

    private String getType(BundleEvent event) {
        if (event == null) {
            return "null";
        }
        int type = event.getType();
        String typeSt = typeMap.get(type);
        return (typeSt != null) ? typeSt : "unknown event type: " + type;
    }
    
    private Collection<PersistenceProviderTracker> getTrackers(Bundle bundle) {
        Collection<PersistenceProviderTracker> providerTrackers = trackers.get(bundle);
        if (providerTrackers == null) {
            providerTrackers = new ArrayList<PersistenceProviderTracker>();
            trackers.put(bundle, providerTrackers);
        }
        return providerTrackers;
    }

}
