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
package org.apache.aries.jpa.container.itest.bundle.blueprint.dao;

import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

import org.apache.aries.jpa.container.itest.bundle.blueprint.CarDao;
import org.apache.aries.jpa.container.itest.entities.Car;

public class CarDaoImpl implements CarDao {

    @PersistenceContext(unitName = "xa-test-unit")
    protected EntityManager em;

    @Override
    public void createNew(Car car) {
        em.persist(car);
    }

    @Override
    public List<Car> getAllCars() {
        TypedQuery<Car> query = em.createQuery("select c from Car c", Car.class);
        return query.getResultList();
    }

}
