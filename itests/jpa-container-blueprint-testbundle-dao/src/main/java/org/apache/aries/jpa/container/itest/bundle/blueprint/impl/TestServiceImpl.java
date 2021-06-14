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
package org.apache.aries.jpa.container.itest.bundle.blueprint.impl;

import java.util.List;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;

import org.apache.aries.jpa.container.itest.bundle.blueprint.CarDao;
import org.apache.aries.jpa.container.itest.bundle.blueprint.NewTxTestService;
import org.apache.aries.jpa.container.itest.bundle.blueprint.TestService;
import org.apache.aries.jpa.container.itest.entities.Car;

public class TestServiceImpl implements TestService {

    private CarDao carDao;
    private NewTxTestService newTxTestService;

    private TransactionManager transactionManager;

    public void setCarDao(CarDao carDao) {
        this.carDao = carDao;
    }

    public void setNewTxTestService(NewTxTestService newTxTestService) {
        this.newTxTestService = newTxTestService;
    }

    public void setTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    @Transactional(TxType.REQUIRED)
    public void performTest() throws Exception {
        try {
            List<Car> cars = carDao.getAllCars();
            if (cars.size() != 0) {
                throw new RuntimeException("Database must be cleaned before executing the test!");
            }

            Car car = new Car();
            car.setNumberOfSeats(4);
            car.setEngineSize(42);
            car.setNumberPlate("WWW-xxxx");
            car.setColour("blue");
            carDao.createNew(car);

            newTxTestService.testNewTransaction(transactionManager.getTransaction());
        } finally {
            transactionManager.setRollbackOnly();
        }
    }

}
