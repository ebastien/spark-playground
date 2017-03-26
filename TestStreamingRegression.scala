package name.ebastien.spark

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.connect.data.Schema
import org.apache.kafka.connect.json.JsonConverter

import org.apache.spark._
import org.apache.spark.streaming._
import org.apache.spark.streaming.kafka010._
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe

import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.regression.StreamingLinearRegressionWithSGD

import collection.JavaConverters._

object TestStreamingRegression {
  def main(args: Array[String]) = {

    val conf = new SparkConf().setAppName("Spark Streaming basic example")
    val ssc = new StreamingContext(conf, Seconds(10))

    // Kafka consumer parameters
    val consParams = Map[String, Object](
      "bootstrap.servers" -> "broker.confluent-kafka.l4lb.thisdcos.directory:9092",
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[ByteArrayDeserializer],
      "group.id" -> "TestStreamingRegression",
      "auto.offset.reset" -> "earliest",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )

    // Kafka topic where the test Kafka connector is pushing logs
    val topic = if (args.size >= 1) args(0)
                else "test-connect-test"

    // We use a direct Kafka stream (low-level Kafka API)
    val stream = KafkaUtils.createDirectStream[String, Array[Byte]](
      ssc,
      PreferConsistent,
      Subscribe[String, Array[Byte]](Array(topic), consParams)
    )

    // Transform the RDD of Kafka records to a RDD of points (label and features).
    // As the algorithm does not learn the intercept we must add it as a feature.
    // As JsonConverter is not serializable it is initialized on each partition.
    val points = stream.mapPartitions { iter =>

      // Make use of Kafka Connect schema and envelop
      val converter = new JsonConverter()
      converter.configure(Map().asJava, false)

      iter.flatMap {
        record => for {
          message <- Option(converter.toConnectData(topic, record.value))
          if message.schema == Schema.STRING_SCHEMA
          value <- Option(message.value.asInstanceOf[String])
          val row = value.split(",").flatMap(f => Option(f.toDouble))
          if row.size >= 2
          val label = row.head
          val features = Array(1.0) ++ row.tail
        } yield LabeledPoint(label, Vectors.dense(features))
      }
    }

    // Initialize the linear regression model and algorithm
    val model = new StreamingLinearRegressionWithSGD().
                    setInitialWeights(Vectors.zeros(2)).
                    setNumIterations(50).
                    setStepSize(1.0)

    // What this function does is generate an output to our RDD of points
    // to update the model
    model.trainOn(points)

    // Generate our own output
    stream.foreachRDD { rdd =>
      println("Records: " + rdd.count)
    }

    // Generate our own output
    points.foreachRDD { rdd =>
      val n = rdd.count
      println("Samples: " + n + " / Weights: " + model.latestModel.weights)
    }

    println("=== START ===")

    ssc.start()
    ssc.awaitTerminationOrTimeout(600000)
    ssc.stop(true, true)

    println("=== STOP ===")
  }
}
