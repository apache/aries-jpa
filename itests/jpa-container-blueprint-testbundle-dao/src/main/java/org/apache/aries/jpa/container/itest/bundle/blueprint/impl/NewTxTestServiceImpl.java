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
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;

import org.apache.aries.jpa.container.itest.bundle.blueprint.CarDao;
import org.apache.aries.jpa.container.itest.bundle.blueprint.NewTxTestService;
import org.apache.aries.jpa.container.itest.entities.Car;

public class NewTxTestServiceImpl implements NewTxTestService {

    private CarDao carDao;

    private TransactionManager transactionManager;

    public void setCarDao(CarDao carDao) {
        this.carDao = carDao;
    }

    public void setTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    @Transactional(TxType.REQUIRES_NEW)
    public void testNewTransaction(Transaction previousTransaction) throws Exception {
        if (previousTransaction == transactionManager.getTransaction()) {
            throw new RuntimeException("No new transaction created for TxType.REQUIRES_NEW");
        }

        List<Car> cars = carDao.getAllCars();
        if (cars.size() != 0) {
            throw new RuntimeException("EntityManager query executed in suspended transaction!");
        }
    }

}
