package name.ebastien.spark

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer

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
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "my_group",
      "auto.offset.reset" -> "earliest",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )

    // Kafka topic where the test Kafka connector is pushing logs
    val topic = "test-connect-test"

    // We use a direct Kafka stream (low-level Kafka API)
    val stream = KafkaUtils.createDirectStream[String, String](
      ssc,
      PreferConsistent,
      Subscribe[String, String](Array(topic), consParams)
    )

    // Transform the RDD of Kafka records to a RDD of points (label and features).
    val points = stream.flatMap {
      s => try {
        val sample = s.value.split(",")
        val label = sample.head.toDouble
        // As the algorithm does not learn the intercept we must add it as a feature.
        val features = Array(1.0) ++ sample.tail.map(_.toDouble)
        if (features.size == 2)
            Option(LabeledPoint(label, Vectors.dense(features)))
        else
            None
      } catch {
        case _ : java.lang.NumberFormatException => None
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
    points.foreachRDD { rdd =>
      val n = rdd.count
      println("Samples: " + n + " / Weights: " + model.latestModel.weights)
    }

    println("=== START ===")

    ssc.start()
    ssc.awaitTermination()
    ssc.stop(true, true)

    println("=== STOP ===")
  }
}
