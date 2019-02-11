# Aries JPA

[![Build Status](https://builds.apache.org/buildStatus/icon?job=Aries-JPA-Trunk-JDK8-Deploy)](https://builds.apache.org/job/Aries-JPA-Trunk-JDK8-Deploy/)

Implements the [JPA Service Specification from the OSGi compendium spec](https://osgi.org/specification/osgi.cmpn/7.0.0/service.jpa.html). Additionally some convenience
services are provided to make it easier to use JPA in blueprint and DS.

http://aries.apache.org/modules/jpaproject.html

# Building

    mvn clean install

# Examples

See [examples](examples).

# Running tck tests

See itests/jpa-tck-itest/README.txt

# Releasing

Run the tck tests to make sure we are still conforming to the spec.

    mvn clean deploy
    mvn release:prepare -Darguments="-DskipTests"
    mvn release:perform

After the release make sure to adapt the versions in the tck test modules.
 
