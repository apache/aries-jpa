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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.persistence.EntityManagerFactory;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;

import org.apache.aries.jpa.container.parser.impl.PersistenceUnit;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.jdbc.DataSourceFactory;
import org.osgi.service.jpa.EntityManagerFactoryBuilder;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FIXME We are currently not configuring a DataSource for the persistence unit.
 * It still works in the tests as the DataSource is defined in the
 * DataSourceTracker or DSFTracker. This not fully correct though.
 */
public class AriesEntityManagerFactoryBuilder implements EntityManagerFactoryBuilder {

	private static final Logger LOGGER = LoggerFactory.getLogger(AriesEntityManagerFactoryBuilder.class);

	private static final String JPA_CONFIGURATION_PREFIX = "org.apache.aries.jpa.";
	
	private static final String JAVAX_PERSISTENCE_JDBC_DRIVER = "javax.persistence.jdbc.driver";
	private static final String JAVAX_PERSISTENCE_JTA_DATASOURCE = "javax.persistence.jtaDataSource";
	private static final String JAVAX_PERSISTENCE_DATASOURCE = "javax.persistence.dataSource";
	private static final String JAVAX_PERSISTENCE_NON_JTA_DATASOURCE = "javax.persistence.nonJtaDataSource";
	private static final String JAVAX_PERSISTENCE_TX_TYPE = "javax.persistence.transactionType";


	private boolean closed;

	private final PersistenceProvider provider;
	private final Bundle providerBundle;
	private final PersistenceUnit persistenceUnit;
	private final BundleContext containerContext;
	private final PersistenceUnitTransactionType originalTxType;
	private final Bundle bundle;
	private String driver;

	private EntityManagerFactory emf;
	private ServiceRegistration<EntityManagerFactory> reg;
	private ServiceRegistration<?> configReg;
	private Object activeConnectionProvider;
	private Map<String, Object> activeProps;
	private ServiceTracker<?,?> tracker;

	private boolean complete;



	public AriesEntityManagerFactoryBuilder(BundleContext containerContext, PersistenceProvider provider, Bundle providerBundle, PersistenceUnit persistenceUnit) {
		this.provider = provider;
		this.providerBundle = providerBundle;
		this.persistenceUnit = persistenceUnit;
		this.containerContext = containerContext;
		this.originalTxType = persistenceUnit.getTransactionType();
		this.bundle = persistenceUnit.getBundle();
		this.driver = persistenceUnit.getProperties().getProperty(JAVAX_PERSISTENCE_JDBC_DRIVER);
		this.tracker = createDataSourceTracker(provider);
		// This must be done separately to avoid an immediate callback seeing the wrong state
		if(this.tracker != null) {
			 this.tracker.open();
		}
		registerManagedService(containerContext, persistenceUnit);
	}
	
    private ServiceTracker<?, ?> createDataSourceTracker(PersistenceProvider provider) {
        if (usesDataSource()) {
        	synchronized (this) {
				driver = "Pre Configured DataSource";
			}
            if (!usesDataSourceService()) {
                LOGGER.warn("Persistence unit " + persistenceUnit.getPersistenceUnitName() + " refers to a non OSGi service DataSource");
                return null;
            }
            DataSourceTracker dsTracker = new DataSourceTracker(containerContext, this, 
            		DataSourceTracker.getDsName(persistenceUnit));
            return dsTracker;
        } else if (usesDSF()) {
        	String jdbcClass = DSFTracker.getDriverName(persistenceUnit);
        	synchronized (this) {
				driver = jdbcClass;
			}
			DSFTracker dsfTracker = new DSFTracker(containerContext, this, 
            		jdbcClass);
            return dsfTracker;
        } else {
            LOGGER.debug("Persistence unit " + getPUName() + " does not refer a DataSource. "
                         +"It can only be used with EntityManagerFactoryBuilder.");
            return null;
        }
    }

    private boolean usesDataSource() {
        return persistenceUnit.getJtaDataSourceName() != null || persistenceUnit.getNonJtaDataSourceName() != null;
    }

    private boolean usesDSF() {
        return DSFTracker.getDriverName(persistenceUnit) != null;
    }

    private boolean usesDataSourceService() {
        return persistenceUnit.getJtaDataSourceName() != null && persistenceUnit.getJtaDataSourceName().startsWith(DataSourceTracker.DS_PREFIX)
            || persistenceUnit.getNonJtaDataSourceName() != null && persistenceUnit.getNonJtaDataSourceName().startsWith(DataSourceTracker.DS_PREFIX);
    }

	@Override
	public String getPersistenceProviderName() {
		String name = persistenceUnit.getPersistenceProviderClassName();
		return name == null ? provider.getClass().getName() : name;
	}

	@Override
	public Bundle getPersistenceProviderBundle() {
		return providerBundle;
	}

	@Override
	public EntityManagerFactory createEntityManagerFactory(Map<String, Object> props) {
		
		synchronized (this) {
			if (closed) {
				throw new IllegalStateException("The EntityManagerFactoryBuilder for " + 
						getPUName() + " is no longer valid");
			}
		}
		
		if (bundle.getState() == Bundle.UNINSTALLED || bundle.getState() == Bundle.INSTALLED
				|| bundle.getState() == Bundle.STOPPING) {
			// Not sure why but during the TCK tests updated sometimes was
			// called for uninstalled bundles
			throw new IllegalStateException("The EntityManagerFactoryBuilder for " + 
					getPUName() + " is no longer valid");
		}
		
		Map<String, Object> processedProperties = processProperties(props);
		
		synchronized (this) {
			if(processedProperties.equals(activeProps) && emf != null) {
				return emf;
			}
		}
		
		closeEMF();
		
		final EntityManagerFactory toUse = createAndPublishEMF(processedProperties);
		
		return (EntityManagerFactory) Proxy.newProxyInstance(getClass().getClassLoader(), 
				new Class<?>[] {EntityManagerFactory.class}, new InvocationHandler() {
					
					@Override
					public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
						if("close".equals(method.getName())) {
							// Close the registration as per the spec
							closeEMF();
							// Do not delegate as the closeEMF call already closes
							return null;
						}
						return method.invoke(toUse, args);
					}
				});
	}

	public String getPUName() {
		return persistenceUnit.getPersistenceUnitName();
	}

	private Map<String, Object> processProperties(Map<String, Object> props) {
		Map<String, Object> processed = new HashMap<String, Object>();
		
		Properties punitProps = persistenceUnit.getProperties();
		
		for(String key : punitProps.stringPropertyNames()) {
			processed.put(key, punitProps.get(key));
		}
		
		if(props != null) {
			processed.putAll(props);
		}

		String newDriver = (String) processed.get(JAVAX_PERSISTENCE_JDBC_DRIVER);
		
		synchronized (this) {
			if(newDriver != null) {
				if(driver == null) {
					driver = newDriver;
				} else if (!newDriver.equals(driver)) {
					throw new IllegalArgumentException("Cannot rebind to a different database driver, as per the JPA service specification");
				}
			}
		}
		
		boolean dataSourceProvided = false;

		// Handle overridden datasources in a provider agnostic way
		// This isn't necessary for EclipseLink, but Hibernate and
		// OpenJPA both need some extra help.
		Object o = processed.get(JAVAX_PERSISTENCE_JTA_DATASOURCE);

		if (o instanceof DataSource) {
			persistenceUnit.setJtaDataSource((DataSource) o);
			processed.remove(JAVAX_PERSISTENCE_JTA_DATASOURCE);
			dataSourceProvided = true;
		}

		o = processed.get(JAVAX_PERSISTENCE_NON_JTA_DATASOURCE);
		if (o instanceof DataSource) {
			persistenceUnit.setNonJtaDataSource((DataSource) o);
			processed.remove(JAVAX_PERSISTENCE_NON_JTA_DATASOURCE);
			dataSourceProvided = true;
		} else {
			o = processed.get(JAVAX_PERSISTENCE_DATASOURCE);
			if (o != null && o instanceof DataSource) {
				persistenceUnit.setNonJtaDataSource((DataSource) o);
				processed.remove(JAVAX_PERSISTENCE_DATASOURCE);
				dataSourceProvided = true;
			}
		}

		o = processed.get(JAVAX_PERSISTENCE_TX_TYPE);
		if (o instanceof PersistenceUnitTransactionType) {
			persistenceUnit.setTransactionType((PersistenceUnitTransactionType) o);
		} else if (o instanceof String) {
			persistenceUnit.setTransactionType(PersistenceUnitTransactionType.valueOf((String) o));
		} else {
			LOGGER.debug("No transaction type set in config, restoring the original value {}", originalTxType);
			persistenceUnit.setTransactionType(originalTxType);
		}
		
		// This Aries extension is used to communicate the actual transaction type to  clients
		processed.put(PersistenceUnitTransactionType.class.getName(), persistenceUnit.getTransactionType());
		
		synchronized (this) {
			// Either they provide a datasource, or we're already provided and using a tracker
			complete = dataSourceProvided || (complete && tracker != null);
		}
		return processed;
	}

	private void registerManagedService(BundleContext containerContext, PersistenceUnitInfo persistenceUnit) {
		Dictionary<String, Object> configuration = new Hashtable<String, Object>(); // NOSONAR
		configuration.put(Constants.SERVICE_PID, JPA_CONFIGURATION_PREFIX + persistenceUnit.getPersistenceUnitName());
		configReg = containerContext.registerService(ManagedService.class, 
				new ManagedEMF(this, persistenceUnit.getPersistenceUnitName()), configuration);
	}

	public void closeEMF() {
		
		EntityManagerFactory emf;
		ServiceRegistration<EntityManagerFactory> emfReg;
		
		synchronized (this) {
			emf = this.emf;
			this.emf = null;
			
			emfReg = this.reg;
			this.reg = null;
		}
		
		if (emfReg != null) {
			try {
				emfReg.unregister();
			} catch (Exception e) {
				LOGGER.debug("Exception on unregister", e);
			}
		}
		if (emf != null && emf.isOpen()) {
			try {
				emf.close();
			} catch (Exception e) {
				LOGGER.warn("Error closing EntityManagerFactory for " + getPUName(), e);
			}
		}
	}

	public void close() {
		boolean unregister = false;
		ServiceTracker<?, ?> toClose;
		synchronized (this) {
			closed = true;
			unregister = true;
			toClose = tracker;
		}
		
		if(unregister) {
			try {
				configReg.unregister();
			} catch (Exception e) {
				LOGGER.debug("Exception on unregister", e);
			}
		}
		
		if (toClose != null) {
			toClose.close();
		}
		
		closeEMF();
	}

	private EntityManagerFactory createAndPublishEMF(Map<String, Object> overrides) {
		
		boolean makeTracker;
		String dbDriver;
		synchronized (this) {
			makeTracker = driver != null && tracker == null;
			dbDriver = driver; 
		}
		
		if(makeTracker) {
			ServiceTracker<?, ?> dsfTracker = new DSFTracker(containerContext, 
					this, dbDriver);
			synchronized (this) {
				tracker = dsfTracker;
				activeProps = overrides;
			}
			dsfTracker.open();
			
			synchronized (this) {
				if(emf == null) {
					throw new IllegalStateException("No database driver is currently available for class " + dbDriver);
				} else {
					return emf;
				}
			}
		} else {
			synchronized (this) {
				if(!complete) {
					throw new IllegalArgumentException("The persistence unit " + getPUName() + 
							" has incomplete configuration and cannot be created. The configuration is" + overrides);
				}
			}
		}
		
		final EntityManagerFactory tmp = provider.createContainerEntityManagerFactory(persistenceUnit, overrides);
		boolean register = false;
		synchronized (this) {
			if(emf == null) {
				emf = tmp;
				activeProps = overrides;
				register = true;
			}
		}
		if(register) {
			Dictionary<String, Object> props = createBuilderProperties(overrides);
			BundleContext uctx = bundle.getBundleContext();
			ServiceRegistration<EntityManagerFactory> tmpReg = 
					uctx.registerService(EntityManagerFactory.class, 
							(EntityManagerFactory) Proxy.newProxyInstance(getClass().getClassLoader(), 
									new Class<?>[] {EntityManagerFactory.class}, 
									new InvocationHandler() {
										
										@Override
										public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
											if("close".equals(method.getName())) {
												// Ignore close as per the spec
												return null;
											}
											return method.invoke(tmp, args);
										}
									}), props);
			
			synchronized (this) {
				if(emf == tmp) {
					reg = tmpReg;
				} else {
					register = false;
				}
			}
			
			if(!register) {
				tmpReg.unregister();
			}
		} else {
			tmp.close();
			synchronized (this) {
				return emf;
			}
		}
		return tmp;
	}

	private Dictionary<String, Object> createBuilderProperties(Map<String, Object> config) {
		Dictionary<String, Object> props = new Hashtable<String, Object>(); // NOSONAR
		
		for(Entry<String,Object> e : config.entrySet()) {
			String key = e.getKey();
			// Don't display the password
			if(DSFTracker.JDBC_PASSWORD.equals(key)) {
				continue;
			}
			
			Object value = e.getValue();
			
			if(key != null && value != null) {
				props.put(key, e.getValue());
			}
		}
		
		if (persistenceUnit.getPersistenceProviderClassName() != null) {
			props.put(JPA_UNIT_PROVIDER, persistenceUnit.getPersistenceProviderClassName());
		}
		props.put(JPA_UNIT_VERSION, bundle.getVersion().toString());
		props.put(JPA_UNIT_NAME, persistenceUnit.getPersistenceUnitName());
		return props;
	}
	
	public static Dictionary<String, String> createBuilderProperties(PersistenceUnitInfo persistenceUnit, Bundle puBundle) {
		Dictionary<String, String> props = new Hashtable<String, String>(); // NOSONAR
		props.put(JPA_UNIT_NAME, persistenceUnit.getPersistenceUnitName());
		if (persistenceUnit.getPersistenceProviderClassName() != null) {
			props.put(JPA_UNIT_PROVIDER, persistenceUnit.getPersistenceProviderClassName());
		}
		props.put(JPA_UNIT_VERSION, puBundle.getVersion().toString());
		return props;
	}

	public void foundDSF(DataSourceFactory dsf) {
		boolean build = false;
		Map<String,Object> props = null;
		synchronized (this) {
			if(activeConnectionProvider == null) {
				activeConnectionProvider = dsf;
				build = true;
				props = activeProps == null ? new HashMap<String, Object>() :
					new HashMap<String, Object>(activeProps);
			}
		}
		
		if(build) {
			Properties punitProps = persistenceUnit.getProperties();
			for(String key : punitProps.stringPropertyNames()) {
				if(!props.containsKey(key)) {
					props.put(key, punitProps.get(key));
				}
			}
			
			DataSource ds = DSFTracker.createDataSource(dsf, props, persistenceUnit.getName());
			dataSourceReady(ds, props);
		}
	}

	public void lostDSF(DataSourceFactory dsf, DataSourceFactory replacement) {
		boolean destroy = false;
		synchronized (this) {
			if(activeConnectionProvider == dsf) {
				activeConnectionProvider = null;
				destroy = true;
			}
		}
		
		if(destroy) {
			closeEMF();
		}
		
		if(replacement != null) {
			foundDSF(replacement);
		}
	}

	public void foundDS(DataSource ds) {
		boolean build = false;
		Map<String,Object> props = null;
		synchronized (this) {
			if(activeConnectionProvider == null) {
				activeConnectionProvider = ds;
				build = true;
				props = activeProps == null ? new HashMap<String, Object>() :
					new HashMap<String, Object>(activeProps);
			}
		}
		
		if(build) {
			dataSourceReady(ds, props);
		}
	}

	public void lostDS(DataSource ds, DataSource replacement) {
		boolean destroy = false;
		synchronized (this) {
			if(activeConnectionProvider == ds) {
				activeConnectionProvider = null;
				destroy = true;
			}
		}
		
		if(destroy) {
			closeEMF();
		}
		
		if(replacement != null) {
			foundDS(replacement);
		}
	}

	private void dataSourceReady(DataSource ds, Map<String, Object> props) {
		if (persistenceUnit.getTransactionType() == PersistenceUnitTransactionType.JTA) {
			props.put(JAVAX_PERSISTENCE_JTA_DATASOURCE, ds);
		} else {
			props.put(JAVAX_PERSISTENCE_NON_JTA_DATASOURCE, ds);
		}
		createEntityManagerFactory(props);
	}
}
