# Aries JPA Specification Jars

These projects contain the JPA API at various versions, providing proper OSGi packaging and contracts so that they can be used easily in OSGi.

##Using the JPA API with your OSGi bundles

If you are using a bnd based plugin (e.g. the bnd-maven-plugin or the maven-bundle-plugin) then you should make sure to set the following:

    -contract: *

or

    -contract: JavaJPA

in your bnd configuration. This will ensure that your bundle is built depending on the JPA contract, and therefore that it will not need to be repackaged to use future, backward compatible versions of the JPA specification.