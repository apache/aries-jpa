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

import static javax.persistence.spi.PersistenceUnitTransactionType.JTA;

import javax.sql.DataSource;

import org.apache.aries.jpa.container.parser.impl.PersistenceUnit;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataSourceTracker extends ServiceTracker<DataSource, DataSource>{
    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceTracker.class);

    static final String DS_PREFIX = "osgi:service/javax.sql.DataSource";

	private final AriesEntityManagerFactoryBuilder builder;

	private final String dsName;

    public DataSourceTracker(BundleContext context, AriesEntityManagerFactoryBuilder builder, 
    		String dsName) {
        super(context, createFilter(context, dsName, builder.getPUName()), null);
		this.builder = builder;
		this.dsName = dsName;
    }

    static Filter createFilter(BundleContext context, String dsName, String puName) {
        if (dsName == null) {
            throw new IllegalArgumentException("No DataSource supplied in persistence.xml");
        }
        String subFilter = getSubFilter(dsName);
        String filter = String.format("(&(objectClass=%s)%s)",
                                      DataSource.class.getName(),
                                      subFilter);
        LOGGER.info("Tracking DataSource for punit " + puName + " with filter " + filter);
        try {
            return context.createFilter(filter);
        } catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static String getSubFilter(String dsName) {
        if (dsName.startsWith(DS_PREFIX)) {
            return (dsName.length() > DS_PREFIX.length() +1) 
                ? dsName.substring(DS_PREFIX.length()+1) 
                : "(osgi.jndi.service.name=*)"; 
        } else {
            return "(osgi.jndi.service.name=" + dsName + ")";
        }
    }

    static String getDsName(PersistenceUnit punit) {
        return punit.getTransactionType() == JTA ? punit.getJtaDataSourceName() : punit.getNonJtaDataSourceName();
    }

    @Override
    public DataSource addingService(ServiceReference<DataSource> reference) {
        LOGGER.info("Found DataSource for " + builder.getPUName() + " " + dsName);
        DataSource ds = super.addingService(reference);
        
        if(ds != null) {
        	builder.foundDS(ds);
        }
        return ds;
    }


    @Override
    public void removedService(ServiceReference<DataSource> reference, DataSource ds) {
        LOGGER.info("Lost DataSource for " + builder.getPUName() + " " + dsName);
        builder.lostDS(ds, getService());
        super.removedService(reference, ds);
    }

}
