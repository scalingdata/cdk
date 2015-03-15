#!/bin/bash
set -x

MVN=$(type -p mvn)
MVN_FLAGS="-B"

if [ -z "${KITE_RELEASE_VERSION}" ] || [ -z "${KITE_DEVELOPMENT_VERSION}" ]; then
  echo "You must set KITE_RELEASE_VERSION and KITE_DEVELOPMENT_VERSION before running this script."
  exit 1
fi

if [ -n "${WORKSPACE}" ]; then
  MVN_FLAGS="${MVN_FLAGS} -f ${WORKSPACE}/pom.xml"
  MVN_FLAGS="${MVN_FLAGS} -Dmaven.repo.local=${WORKSPACE}/.repository"
fi

# Set the version to the next release
${MVN} ${MVN_FLAGS} \
  versions:set \
    -DnewVersion=${KITE_RELEASE_VERSION} \
    -DgenerateBackupPoms=false

# Commit and push the version number update
${MVN} ${MVN_FLAGS} \
  scm:add \
    -Dincludes=**/pom.xml \
    -Dexcludes=**/target/**/pom.xml \
  scm:checkin \
    -Dmessage="SD-BUILD: Preparing for release ${KITE_RELEASE_VERSION}"

# Deploy the release to the maven repo and tag the release in git
${MVN} ${MVN_FLAGS} \
  deploy \
  scm:tag \
    -Dtag=release-${KITE_RELEASE_VERSION}

# Set the version to the next development version
${MVN} ${MVN_FLAGS} \
  versions:set \
    -DnewVersion=${KITE_DEVELOPMENT_VERSION} \
    -DgenerateBackupPoms=false

# Commit and push the version number update
${MVN} ${MVN_FLAGS} \
  scm:add \
    -Dincludes=**/pom.xml \
    -Dexcludes=**/target/**/pom.xml \
  scm:checkin \
    -Dmessage="SD-BUILD: Preparing for ${KITE_DEVELOPMENT_VERSION} development"
