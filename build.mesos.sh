#!/bin/bash

set -e -x 

PROJECT_NAME="playground"
PROJECT_VERSION="0.1.0"
PROJECT_ARCHIVE="${PROJECT_NAME}-${PROJECT_VERSION}.tgz"

cat > repositories <<EOF
[repositories]
  proxied-maven-central: ${NEXUS_BASEURL}/maven-central/
  proxied-typesafe-releases: ${NEXUS_BASEURL}/typesafe-releases/
  proxied-sonatype-public: ${NEXUS_BASEURL}/sonatype-public/
  proxied-sbt-plugin-releases: ${NEXUS_BASEURL}/sbt-plugin-releases/, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
  proxied-typesafe-ivy-releases: ${NEXUS_BASEURL}/typesafe-ivy-releases/, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
EOF

/opt/sbt/bin/sbt \
  -Dsbt.override.build.repos=true \
  -Dsbt.repository.config=${WORKSPACE}/repositories \
  universal:packageZipTarball

curl -v -T \
  "${WORKSPACE}/target/universal/${PROJECT_ARCHIVE}" \
  "${NEXUS_BASEURL}/site-archive/jars/"
