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

import static java.util.Collections.singletonMap;
import static javax.persistence.spi.PersistenceUnitTransactionType.JTA;
import static javax.persistence.spi.PersistenceUnitTransactionType.RESOURCE_LOCAL;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.osgi.framework.Constants.SERVICE_PID;
import static org.osgi.service.jpa.EntityManagerFactoryBuilder.JPA_UNIT_NAME;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

import javax.persistence.EntityManagerFactory;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;

import org.apache.aries.jpa.container.parser.impl.PersistenceUnit;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.jdbc.DataSourceFactory;

@RunWith(MockitoJUnitRunner.class)
public class PropsConfigurationTest {

	private static final String JDBC_PASSWORD = "123456";

	private static final String JDBC_USER = "bob";

	private static final String JDBC_URL = "jdbc:h2:foo";

	@Mock
	PersistenceUnit punit;
	
	@Mock
	ServiceRegistration<ManagedService> msReg;
	
	@Mock
	PersistenceProvider provider;
	
	@Mock
	BundleContext containerContext, punitContext;
	
	@Mock
	Bundle punitBundle;
	
	@Mock
	EntityManagerFactory emf;

	@Mock
	ServiceRegistration<EntityManagerFactory> emfReg;
	
	Properties punitProperties = new Properties();
	
    @Mock
    ServiceReference<DataSourceFactory> dsfRef;
    
    @Mock
    ServiceReference<DataSource> dsRef;
    
    @Mock
    DataSourceFactory dsf;
    
    @Mock
    DataSource ds;
	
	@SuppressWarnings("unchecked")
	@Before
	public void setup() throws Exception {
		
		when(punit.getPersistenceUnitName()).thenReturn("test-props");
		when(punit.getPersistenceProviderClassName())
			.thenReturn("org.eclipse.persistence.jpa.PersistenceProvider");
		when(punit.getTransactionType()).thenReturn(PersistenceUnitTransactionType.JTA);
		when(punit.getBundle()).thenReturn(punitBundle);
		when(punit.getProperties()).thenReturn(punitProperties);
		
		when(punitBundle.getBundleContext()).thenReturn(punitContext);
		when(punitBundle.getVersion()).thenReturn(Version.parseVersion("1.2.3"));
		
		when(containerContext.registerService(eq(ManagedService.class), 
				any(ManagedService.class), any(Dictionary.class))).thenReturn(msReg);
		when(containerContext.getService(dsfRef)).thenReturn(dsf);
		when(containerContext.getService(dsRef)).thenReturn(ds);
		when(containerContext.createFilter(Mockito.anyString()))
			.thenAnswer(new Answer<Filter>() {
				@Override
				public Filter answer(InvocationOnMock i) throws Throwable {
					return FrameworkUtil.createFilter(i.getArguments()[0].toString());
				}
			});
		
		when(punitContext.registerService(eq(EntityManagerFactory.class), any(EntityManagerFactory.class), 
				any(Dictionary.class))).thenReturn(emfReg);
		
		when(emf.isOpen()).thenReturn(true);
		
		
		Properties jdbcProps = new Properties();
		jdbcProps.setProperty("url", JDBC_URL);
		jdbcProps.setProperty("user", JDBC_USER);
		jdbcProps.setProperty("password", JDBC_PASSWORD);
		
		when(dsf.createDataSource(jdbcProps)).thenReturn(ds);
		
	}
	
	@Test
	public void testRegistersManagedEMF() throws InvalidSyntaxException, ConfigurationException {
		
		AriesEntityManagerFactoryBuilder emfb = new AriesEntityManagerFactoryBuilder(
				containerContext, provider, punit);
		
		verify(containerContext).registerService(eq(ManagedService.class),
				any(ManagedService.class), argThat(servicePropsMatcher(
						SERVICE_PID, "org.apache.aries.jpa.test-props")));
		
		// No EMF created as incomplete
		verifyZeroInteractions(msReg, provider);
		
		emfb.close();
		verify(msReg).unregister();
	}

	private BaseMatcher<Dictionary<String, Object>> servicePropsMatcher(final String key, final Object value) {
		return new BaseMatcher<Dictionary<String, Object>>() {

			Object props;
			@SuppressWarnings("unchecked")
			@Override
			public boolean matches(Object arg0) {
				props = arg0;
				return value.equals(((Dictionary<String, Object>) props).get(key));
			}

			@Override
			public void describeTo(Description arg0) {
				arg0.appendText("Service Properties did not contain " + key +
						"=" + value + ". Props were " + props);
			}
		};
	}
	
    @Test
    public void testIncompleteEmfWithoutProps() throws InvalidSyntaxException, ConfigurationException {
        
    	when(provider.createContainerEntityManagerFactory(eq(punit), 
    			eq(singletonMap(PersistenceUnitTransactionType.class.getName(), JTA))))
    		.thenReturn(emf);
    	
    	AriesEntityManagerFactoryBuilder emfb = new AriesEntityManagerFactoryBuilder(
        		containerContext, provider, punit);
    	
    	try {
    		emfb.createEntityManagerFactory(null);
    		fail("Should throw an exception as incomplete");
    	} catch (IllegalArgumentException iae) {
    		// Expected
    	}
        
        
        // No EMF created as incomplete
     	verifyZeroInteractions(emf, emfReg, provider);
        
        emfb.close();
        
        verifyZeroInteractions(emf, emfReg, provider);
    }

    @Test
	public void testIncompleteEmfWithDSGetsPassed() throws InvalidSyntaxException, ConfigurationException {
		
		when(provider.createContainerEntityManagerFactory(eq(punit), 
				eq(singletonMap(PersistenceUnitTransactionType.class.getName(), JTA))))
			.thenReturn(emf);
		
		Map<String, Object> props = new Hashtable<String, Object>();
		props.put("javax.persistence.dataSource", ds);
		
		AriesEntityManagerFactoryBuilder emfb = new AriesEntityManagerFactoryBuilder(
				containerContext, provider, punit);
		emfb.createEntityManagerFactory(props);
		
		
		verify(punit).setNonJtaDataSource(ds);
		verify(punitContext).registerService(eq(EntityManagerFactory.class),
				any(EntityManagerFactory.class), argThat(servicePropsMatcher(JPA_UNIT_NAME, "test-props")));
		
		emfb.close();
		
		verify(emfReg).unregister();
		verify(emf).close();
	}

	@Test
    public void testIncompleteEmfWithPropsGetsPassed() throws InvalidSyntaxException, ConfigurationException {
        
    	Map<String, Object> providerProps = new HashMap<String, Object>();
    	providerProps.put(PersistenceUnitTransactionType.class.getName(), JTA);
    	providerProps.put("hibernate.hbm2ddl.auto", "create-drop");
    	
    	when(provider.createContainerEntityManagerFactory(eq(punit), 
    			eq(providerProps))).thenReturn(emf);

    	Map<String, Object> props = new Hashtable<String, Object>();
        props.put("hibernate.hbm2ddl.auto", "create-drop");
        props.put("javax.persistence.dataSource", ds);
       
        AriesEntityManagerFactoryBuilder emfb = new AriesEntityManagerFactoryBuilder(
        		containerContext, provider, punit);
        emfb.createEntityManagerFactory(props);
        
        
        verify(punitContext).registerService(eq(EntityManagerFactory.class),
        		any(EntityManagerFactory.class), and(argThat(servicePropsMatcher(JPA_UNIT_NAME, "test-props")),
        				argThat(servicePropsMatcher("hibernate.hbm2ddl.auto", "create-drop"))));
        
        emfb.close();
		verify(emfReg).unregister();
		verify(emf).close();
    }

    @Test
    public void testPUWithDriverGetsCreatedAutomatically() throws InvalidSyntaxException, ConfigurationException {
    	
    	punitProperties.setProperty("javax.persistence.jdbc.driver", "org.h2.Driver");
    	punitProperties.setProperty("javax.persistence.jdbc.url", JDBC_URL);
    	punitProperties.setProperty("javax.persistence.jdbc.user", JDBC_USER);
    	punitProperties.setProperty("javax.persistence.jdbc.password", JDBC_PASSWORD);
    	
    	when(containerContext.getServiceReferences((String) null, 
    			"(&(objectClass=org.osgi.service.jdbc.DataSourceFactory)(osgi.jdbc.driver.class=org.h2.Driver))"))
    		.thenReturn(new ServiceReference<?>[] {dsfRef});
    	
    	
    	when(provider.createContainerEntityManagerFactory(eq(punit), 
    			any(Map.class))).thenReturn(emf);
    	
    	AriesEntityManagerFactoryBuilder emfb = new AriesEntityManagerFactoryBuilder(
    			containerContext, provider, punit);
    	
    	verify(punit).setJtaDataSource(ds);
    	verify(punitContext).registerService(eq(EntityManagerFactory.class),
    			any(EntityManagerFactory.class), argThat(servicePropsMatcher(JPA_UNIT_NAME, "test-props")));
    	
    	emfb.close();
		verify(emfReg).unregister();
		verify(emf).close();
    }

    @Test
    public void testPUWithDriverEMFBReturnsExisting() throws InvalidSyntaxException, ConfigurationException {
    	
    	punitProperties.setProperty("javax.persistence.jdbc.driver", "org.h2.Driver");
    	punitProperties.setProperty("javax.persistence.jdbc.url", JDBC_URL);
    	punitProperties.setProperty("javax.persistence.jdbc.user", JDBC_USER);
    	punitProperties.setProperty("javax.persistence.jdbc.password", JDBC_PASSWORD);
    	
    	when(containerContext.getServiceReferences((String) null, 
    			"(&(objectClass=org.osgi.service.jdbc.DataSourceFactory)(osgi.jdbc.driver.class=org.h2.Driver))"))
    	.thenReturn(new ServiceReference<?>[] {dsfRef});
    	
    	
    	when(provider.createContainerEntityManagerFactory(eq(punit), 
    			any(Map.class))).thenReturn(emf);
    	
    	AriesEntityManagerFactoryBuilder emfb = new AriesEntityManagerFactoryBuilder(
    			containerContext, provider, punit);
    	
    	verify(punit).setJtaDataSource(ds);
    	verify(punitContext).registerService(eq(EntityManagerFactory.class),
    			any(EntityManagerFactory.class), 
    			and(and(argThat(servicePropsMatcher(JPA_UNIT_NAME, "test-props")),
    					argThat(servicePropsMatcher("javax.persistence.jdbc.user", JDBC_USER))),
    					not(argThat(servicePropsMatcher("javax.persistence.jdbc.password", JDBC_PASSWORD)))));
    	
    	assertSame(emf, emfb.createEntityManagerFactory(null));
    	
    	assertSame(emf, emfb.createEntityManagerFactory(new HashMap<String, Object>()));
    	
    	verify(provider, Mockito.times(1)).createContainerEntityManagerFactory(
    			any(PersistenceUnitInfo.class), anyMap());
    	
    	emfb.close();
    	verify(emfReg).unregister();
    	verify(emf).close();
    }

    @Test
    public void testLateBindingDriver() throws InvalidSyntaxException, ConfigurationException {
    	
    	
    	when(containerContext.getServiceReferences((String) null, 
    			"(&(objectClass=org.osgi.service.jdbc.DataSourceFactory)(osgi.jdbc.driver.class=org.h2.Driver))"))
    	.thenReturn(new ServiceReference<?>[] {dsfRef});
    	
    	
    	when(provider.createContainerEntityManagerFactory(eq(punit), 
    			any(Map.class))).thenReturn(emf);
    	
    	AriesEntityManagerFactoryBuilder emfb = new AriesEntityManagerFactoryBuilder(
    			containerContext, provider, punit);

    	verifyZeroInteractions(provider);
    	
    	Map<String, Object> config = new HashMap<String, Object>();
    	config.put("javax.persistence.jdbc.driver", "org.h2.Driver");
    	config.put("javax.persistence.jdbc.url", JDBC_URL);
    	config.put("javax.persistence.jdbc.user", JDBC_USER);
    	config.put("javax.persistence.jdbc.password", JDBC_PASSWORD);
    	
    	emfb.createEntityManagerFactory(config);
    	
    	verify(punit).setJtaDataSource(ds);
    	verify(punitContext).registerService(eq(EntityManagerFactory.class),
    			any(EntityManagerFactory.class), argThat(servicePropsMatcher(JPA_UNIT_NAME, "test-props")));
    	
    	
    	try {
    		config.put("javax.persistence.jdbc.driver", "org.apache.derby.client.ClientDriver");
    		emfb.createEntityManagerFactory(config);
    		fail("Must throw an IllegalArgumentException on rebind");
    	} catch (IllegalArgumentException ise) {
    		// Expected
    	}
    	
    	emfb.close();
    }
    
    @Test
    public void testPUWithJtaDSGetsCreatedAutomatically() throws InvalidSyntaxException, ConfigurationException {
    	
    	when(containerContext.getServiceReferences((String) null, 
    			"(&(objectClass=javax.sql.DataSource)(osgi.jndi.service.name=testds))"))
    		.thenReturn(new ServiceReference<?>[] {dsRef});
    	
    	when(punit.getJtaDataSourceName()).thenReturn(
    			"osgi:service/javax.sql.DataSource/(osgi.jndi.service.name=testds)");
    	
    	when(provider.createContainerEntityManagerFactory(eq(punit), 
    			any(Map.class))).thenReturn(emf);
    	
    	AriesEntityManagerFactoryBuilder emfb = new AriesEntityManagerFactoryBuilder(
    			containerContext, provider, punit);
    	
    	verify(punit).setJtaDataSource(ds);
    	verify(punitContext).registerService(eq(EntityManagerFactory.class),
    			any(EntityManagerFactory.class), argThat(servicePropsMatcher(JPA_UNIT_NAME, "test-props")));
    	
    	emfb.close();
    }

    @Test
    public void testPUWithNonJtaDSGetsCreatedAutomatically() throws InvalidSyntaxException, ConfigurationException {
    	
    	when(containerContext.getServiceReferences((String) null, 
    			"(&(objectClass=javax.sql.DataSource)(osgi.jndi.service.name=testds))"))
    	.thenReturn(new ServiceReference<?>[] {dsRef});
    	
    	when(punit.getTransactionType()).thenReturn(RESOURCE_LOCAL);

    	when(punit.getNonJtaDataSourceName()).thenReturn(
    			"osgi:service/javax.sql.DataSource/(osgi.jndi.service.name=testds)");
    	
    	when(provider.createContainerEntityManagerFactory(eq(punit), 
    			any(Map.class))).thenReturn(emf);
    	
    	AriesEntityManagerFactoryBuilder emfb = new AriesEntityManagerFactoryBuilder(
    			containerContext, provider, punit);
    	
    	verify(punit).setNonJtaDataSource(ds);
    	verify(punitContext).registerService(eq(EntityManagerFactory.class),
    			any(EntityManagerFactory.class), argThat(servicePropsMatcher(JPA_UNIT_NAME, "test-props")));
    	
    	emfb.close();
    }
    
    @Test
	public void testReturnedEmfClose() throws InvalidSyntaxException, ConfigurationException {
		
		when(provider.createContainerEntityManagerFactory(eq(punit), 
				eq(singletonMap(PersistenceUnitTransactionType.class.getName(), JTA))))
			.thenReturn(emf);
		
		Map<String, Object> props = new Hashtable<String, Object>();
		props.put("javax.persistence.dataSource", ds);
		
		AriesEntityManagerFactoryBuilder emfb = new AriesEntityManagerFactoryBuilder(
				containerContext, provider, punit);
		
		EntityManagerFactory returnedEMF = emfb.createEntityManagerFactory(props);
		
		
		verify(punit).setNonJtaDataSource(ds);
		verify(punitContext).registerService(eq(EntityManagerFactory.class),
				any(EntityManagerFactory.class), argThat(servicePropsMatcher(JPA_UNIT_NAME, "test-props")));
		
		returnedEMF.close();
		
		verify(emfReg).unregister();
		verify(emf).close();
		
		emfb.close();
	}

    @Test
    public void testServiceEmfClose() throws InvalidSyntaxException, ConfigurationException {
    	
    	when(provider.createContainerEntityManagerFactory(eq(punit), 
    			eq(singletonMap(PersistenceUnitTransactionType.class.getName(), JTA))))
    	.thenReturn(emf);
    	
    	Map<String, Object> props = new Hashtable<String, Object>();
    	props.put("javax.persistence.dataSource", ds);
    	
    	AriesEntityManagerFactoryBuilder emfb = new AriesEntityManagerFactoryBuilder(
    			containerContext, provider, punit);
    	
    	emfb.createEntityManagerFactory(props);
    	
    	ArgumentCaptor<EntityManagerFactory> emfCaptor = ArgumentCaptor.forClass(EntityManagerFactory.class);
    	
    	verify(punit).setNonJtaDataSource(ds);
    	verify(punitContext).registerService(eq(EntityManagerFactory.class),
    			emfCaptor.capture(), argThat(servicePropsMatcher(JPA_UNIT_NAME, "test-props")));
    	
    	emfCaptor.getValue().close();
    	
    	verifyZeroInteractions(emfReg, emf);
    	
    	emfb.close();
    }
}
