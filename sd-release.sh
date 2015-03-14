#!/bin/bash

env

MVN=echo

# Set the version to the next release
${MVN} versions:set \
  -DnewVersion=${KITE_RELEASE_VERSION} \
  -DgenerateBackupPoms=false

# Commit and push the version number update
${MVN} \
  scm:add \
    -Dincludes=**/pom.xml \
    -Dexcludes=**/target/**/pom.xml \
  scm:checkin \
    -Dmessage="SD-BUILD: Preparing for release ${KITE_RELEASE_VERSION}"

# Deploy the release to the maven repo and tag the release in git
${MVN} \
  deploy \
  scm:tag \
    -Dtag=release-$KITE_RELEASE_VERSION

# Set the version to the next development version
${MVN} \
  versions:set \
    -DnewVersion=${KITE_DEVELOPMENT_VERSION}
    -DgenerateBackupPoms=false

# Commit and push the version number update
${MVN} \
  scm:add \
    -Dincludes=**/pom.xml
    -Dexcludes=**/target/**/pom.xml \
  scm:checkin \
    -Dmessage="SD-BUILD: Preparing for $KITE_DEVELOPMENT_VERSION development"
