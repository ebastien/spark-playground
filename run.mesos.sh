#!/bin/bash

set -e -x 

PROJECT_NAME="playground"
PROJECT_VERSION="0.1.0"
PROJECT_PACKAGE="name.ebastien.spark"
PROJECT_CLASS=$1
PROJECT_ARCHIVE="${PROJECT_NAME}-${PROJECT_VERSION}.tgz"

shift

curl -v -O "${NEXUS_BASEURL}/site-archive/jars/${PROJECT_ARCHIVE}"

tar xvzf "./${PROJECT_ARCHIVE}"

PROJECT_LIBPATH="${WORKSPACE}/${PROJECT_NAME}-${PROJECT_VERSION}/lib"

cp -f ${MESOS_SANDBOX}/hdfs-site.xml ${MESOS_SANDBOX}/core-site.xml ${HADOOP_CONF_DIR}/

PROJECT_JARS=(${PROJECT_LIBPATH}/*.jar)
IFS=','
SPARK_JARS="${PROJECT_JARS[*]}"

/opt/spark/dist/bin/spark-submit \
  --master mesos://master.mesos:5050 \
  --class "${PROJECT_PACKAGE}.${PROJECT_CLASS}" \
  --executor-memory 1G \
  --total-executor-cores 6 \
  --conf spark.mesos.coarse=true \
  --conf spark.mesos.executor.docker.image=mesosphere/spark:1.0.7-2.1.0-hadoop-2.6 \
  --conf spark.mesos.executor.home=/opt/spark/dist \
  --jars "${SPARK_JARS}" \
  "${PROJECT_LIBPATH}/${PROJECT_PACKAGE}.${PROJECT_NAME}-${PROJECT_VERSION}.jar" $*
