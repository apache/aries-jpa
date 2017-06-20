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

import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.aries.jpa.container.parser.impl.PersistenceUnit;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.jdbc.DataSourceFactory;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DSFTracker extends ServiceTracker<DataSourceFactory, DataSourceFactory>{
    static final String JDBC_DRIVER = "javax.persistence.jdbc.driver";
    static final String JDBC_URL = "javax.persistence.jdbc.url";
    static final String JDBC_USER = "javax.persistence.jdbc.user";
    static final String JDBC_PASSWORD = "javax.persistence.jdbc.password"; // NOSONAR

    private static final Logger LOGGER = LoggerFactory.getLogger(DSFTracker.class);


    private final AriesEntityManagerFactoryBuilder builder;
	private final String driverClass;
    
    public DSFTracker(BundleContext context, AriesEntityManagerFactoryBuilder builder, 
    		String driverClass) {
        super(context, createFilter(context, driverClass, builder.getPUName()), null);
        this.builder = builder;
		this.driverClass = driverClass;
    }

    static Filter createFilter(BundleContext context, String driverClass, String puName) {
        if (driverClass == null) {
            throw new IllegalArgumentException("No javax.persistence.jdbc.driver supplied in persistence.xml");
        }
        String filter = String.format("(&(objectClass=%s)(%s=%s))",
                                      DataSourceFactory.class.getName(),
                                      DataSourceFactory.OSGI_JDBC_DRIVER_CLASS,
                                      driverClass);
        LOGGER.info("Tracking DataSourceFactory for punit " + puName + " with filter " + filter);
        try {
            return context.createFilter(filter);
        } catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static String getDriverName(PersistenceUnit punit) {
        return (String)punit.getProperties().getProperty(JDBC_DRIVER);
    }

    @Override
    public DataSourceFactory addingService(ServiceReference<DataSourceFactory> reference) {
        LOGGER.info("Found DataSourceFactory for " + builder.getPUName() + " of type "
                    + driverClass);
        try {
            DataSourceFactory dsf = super.addingService(reference);
            
            if(dsf != null)
            	builder.foundDSF(dsf);
            return dsf;
        } catch (Exception e) {
            LOGGER.error("Error creating DataSource for punit " + builder.getPUName(), e);
            return null;
        }
    }

    static DataSource createDataSource(DataSourceFactory dsf, Map<String, Object> punitProps, String punitName) {
        try {
            Properties props = new Properties();
            put(props, DataSourceFactory.JDBC_URL, punitProps, JDBC_URL);
            put(props, DataSourceFactory.JDBC_USER, punitProps, JDBC_USER);
            put(props, DataSourceFactory.JDBC_PASSWORD, punitProps, JDBC_PASSWORD);
            return dsf.createDataSource(props);
        } catch (SQLException e) {
            String msg = "Error creating DataSource for persistence unit " + punitName + ". " + e.getMessage();
            throw new RuntimeException(msg, e); // NOSONAR
        }
    }

    private static void put(Properties props, String destKey, Map<String, Object> punitProps, String sourceKey) {
        Object value = punitProps.get(sourceKey);
        if (value != null) {
            props.setProperty(destKey, String.valueOf(value));
        }
    }

    @Override
    public void removedService(ServiceReference<DataSourceFactory> reference, DataSourceFactory dsf) {
        LOGGER.info("Lost DataSourceFactory for " + builder.getPUName() + " of type " + driverClass);
        builder.lostDSF(dsf, getService());
        super.removedService(reference, dsf);
    }
}
