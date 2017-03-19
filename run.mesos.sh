#!/bin/bash

set -e -x 

NEXUS_SERVICE_PORT=10111
NEXUS_BASEURL="http://marathon-lb.marathon.mesos:${NEXUS_SERVICE_PORT}/repository"

PROJECT_NAME="playground"
PROJECT_VERSION="0.1.0"
PROJECT_PACKAGE="name.ebastien.spark"
PROJECT_CLASS="Hi"
PROJECT_ARCHIVE="${PROJECT_NAME}-${PROJECT_VERSION}.tgz"

curl -v -O "${NEXUS_BASEURL}/site-archive/jars/${PROJECT_ARCHIVE}"

tar xvzf "./${PROJECT_ARCHIVE}"

cp -f ${MESOS_SANDBOX}/hdfs-site.xml ${MESOS_SANDBOX}/core-site.xml ${HADOOP_CONF_DIR}/

/opt/spark/dist/bin/spark-submit \
  --master mesos://master.mesos:5050 \
  --class "${PROJECT_PACKAGE}.${PROJECT_CLASS}" \
  --executor-memory 1G \
  --total-executor-cores 6 \
  --conf spark.mesos.coarse=true \
  --conf spark.mesos.executor.docker.image=mesosphere/spark:1.0.7-2.1.0-hadoop-2.6 \
  --conf spark.mesos.executor.home=/opt/spark/dist \
  "${WORKSPACE}/${PROJECT_NAME}-${PROJECT_VERSION}/lib/${PROJECT_PACKAGE}.${PROJECT_NAME}-${PROJECT_VERSION}.jar"
