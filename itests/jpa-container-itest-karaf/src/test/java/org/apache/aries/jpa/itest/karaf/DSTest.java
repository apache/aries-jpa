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
package org.apache.aries.jpa.itest.karaf;

import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.debugConfiguration;

import java.util.Collection;
import java.util.Iterator;

import javax.inject.Inject;

import org.apache.aries.jpa.example.tasklist.model.Task;
import org.apache.aries.jpa.example.tasklist.model.TaskService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleException;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class DSTest extends AbstractJPAItest {
    
    @Inject
    TaskService taskService;

    @Before
    public void initService() {
        taskService = getService(TaskService.class, null);
    }
    
    @Test
    public void test() throws BundleException {
        resolveBundles();
        Collection<Task> tasks1 = taskService.getTasks();
        Assert.assertEquals(1, tasks1.size());
        Task task = new Task();
        task.setId(2);
        task.setDescription("My task 2");
        taskService.addTask(task);
        Collection<Task> tasks = taskService.getTasks();
        Assert.assertEquals(2, tasks.size());
        Iterator<Task> iterator = tasks.iterator();
        Task task2  = iterator.next();
        Assert.assertEquals(2, task2.getId().intValue());
        Assert.assertEquals("My task 2", task2.getDescription());
        Task task1  = iterator.next();
        Assert.assertEquals(1, task1.getId().intValue());
        Assert.assertEquals("Task1", task1.getTitle());
    }

    @Configuration
    public Option[] configuration() {
        return new Option[] {
            baseOptions(), //
            mavenBundle("org.apache.aries.jpa.example", "org.apache.aries.jpa.example.tasklist.ds").versionAsInProject()
        };
    }
}
