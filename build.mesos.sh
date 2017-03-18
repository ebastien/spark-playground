cp -f ${MESOS_SANDBOX}/hdfs-site.xml ${MESOS_SANDBOX}/core-site.xml ${HADOOP_CONF_DIR}/

NEXUS_SERVICE_PORT=10111
NEXUS_BASEURL="http://marathon-lb.marathon.mesos:${NEXUS_SERVICE_PORT}/repository"

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

tar xzf ./target/universal/playground-0.1.0.tgz

/opt/spark/dist/bin/spark-submit \
  --master mesos://master.mesos:5050 \
  --class name.ebastien.spark.Hi \
  --executor-memory 1G \
  --total-executor-cores 6 \
  --conf spark.mesos.coarse=true \
  --conf spark.mesos.executor.docker.image=mesosphere/spark:1.0.7-2.1.0-hadoop-2.6 \
  --conf spark.mesos.executor.home=/opt/spark/dist \
  ${WORKSPACE}/playground-0.1.0/lib/name.ebastien.spark.playground-0.1.0.jar
