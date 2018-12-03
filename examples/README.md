# Installation instructions for jpa-examples

Install at least Karaf 4.1.2 or 4.2.2.

The blueprint example does not work in karaf 4.2.0 and 4.2.1. They deploy blueprint core 1.9.0 which has a severe [bug regarding
interceptors](https://issues.apache.org/jira/browse/ARIES-1793). 

## Copy DataSource config
```
cat https://raw.githubusercontent.com/apache/aries-jpa/master/examples/org.ops4j.datasource-tasklist.cfg | tac etc/org.ops4j.datasource-tasklist.cfg
```

## Install features
```
feature:repo-add pax-jdbc 1.3.1
feature:install scr transaction pax-jdbc-config pax-jdbc-h2 pax-jdbc-pool-dbcp2 http-whiteboard jpa hibernate
```

### Closure based example. (Make sure to start karaf with JDK 8)
```
install -s mvn:org.apache.aries.jpa.example/org.apache.aries.jpa.example.tasklist.model/2.7.0
install -s mvn:org.apache.aries.jpa.example/org.apache.aries.jpa.example.tasklist.ds/2.7.0
```

### Blueprint based example
```
feature:install aries-blueprint
install -s mvn:org.apache.aries.jpa.example/org.apache.aries.jpa.example.tasklist.model/2.7.0
install -s mvn:org.apache.aries.jpa.example/org.apache.aries.jpa.example.tasklist.blueprint/2.7.0
```

## Validate example

After installing the examples you can check for the services.

```
service:list EntityManagerFactory
```

You should see a service for the persistence unit "tasklist".

```
service:list TaskService
```

You should see a service provided by either the tasklist.blueprint or tasklist.ds bundle depending on the example you installed.

http://localhost:8181/tasklist

If you open the above url in a webbrowser you should see a list with one task.
Now add a task:
http://localhost:8181/tasklist?add&taskId=4&title=Buy more coffee

and check it is added to the list
http://localhost:8181/tasklist
