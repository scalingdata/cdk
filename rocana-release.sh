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

error() {
  local msg="$1"
  local retcode="$2"

  echo "Error: $msg"

  [ -n "$retcode" ] && exit "$retcode"
}

# Set the version to the next release
${MVN} ${MVN_FLAGS} \
  versions:set \
    -DnewVersion=${KITE_RELEASE_VERSION} \
    -DgenerateBackupPoms=false ||
error "Unable to update the release version to ${KITE_RELEASE_VERSION}" 1

# Commit and push the version number update
${MVN} ${MVN_FLAGS} \
  scm:add \
    -Dincludes=**/pom.xml \
    -Dexcludes=**/target/**/pom.xml \
  scm:checkin \
    -Dmessage="ROCANA-BUILD: Preparing for release ${KITE_RELEASE_VERSION}" ||
error "Unable to checkin the update of the release version to ${KITE_RELEASE_VERSION}" 1

# Deploy the release to the maven repo
${MVN} ${MVN_FLAGS} \
  clean \
  deploy ||
error "Unable to deploy CDH artifacts to nexus" 1

# Deploy the hdp release to the maven repo
${MVN} ${MVN_FLAGS} \
  -Phdp2,hdp2-release \
  clean \
  deploy ||
error "Unable to deploy HDP artifacts to nexus" 1

# Tag the release in git
${MVN} ${MVN_FLAGS} \
  scm:tag \
    -Dtag=release-${KITE_RELEASE_VERSION} ||
error "Unable to create release tag" 1

# Set the version to the next development version
${MVN} ${MVN_FLAGS} \
  versions:set \
    -DnewVersion=${KITE_DEVELOPMENT_VERSION} \
    -DgenerateBackupPoms=false ||
error "Unable to update the development version to ${KITE_DEVELOPMENT_VERSION}" 1

# Commit and push the version number update
${MVN} ${MVN_FLAGS} \
  scm:add \
    -Dincludes=**/pom.xml \
    -Dexcludes=**/target/**/pom.xml \
  scm:checkin \
    -Dmessage="ROCANA-BUILD: Preparing for ${KITE_DEVELOPMENT_VERSION} development" ||
error "Unable to checkin the update of the development version to ${KITE_DEVELOPMENT_VERSION}" 1
