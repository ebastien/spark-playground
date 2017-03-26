package name.ebastien.spark

import java.util.concurrent.Future

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringSerializer
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

/**
 * Converter parses a Kafka ConsumerRecord with a lazy
 * Kafka Connect JSON converter.
 */
class Converter(topic: String) extends Serializable {

  lazy val converter = {
    val c = new JsonConverter()
    c.configure(Map().asJava, false)
    c
  }

  def fromRecord[K](record: ConsumerRecord[K, Array[Byte]]) : Option[String] = {
    for {
      data <- Option(converter.toConnectData(topic, record.value))
      if data.schema == Schema.STRING_SCHEMA
      value <- Option(data.value.asInstanceOf[String])
    } yield value
  }
}

/**
 * KafkaSink sends values back to Kafka.
 */
class KafkaSink[K, V](topic: String, config: Map[String, Object]) extends Serializable {

  lazy val producer = {
    val p = new KafkaProducer[K, V](config.asJava)
    sys.addShutdownHook(p.close())
    p
  }

  def send(value: V) : Future[RecordMetadata] = {
    producer.send(new ProducerRecord[K, V](topic, value))
  }
}

/**
 * TestStreamingRegression runs a linear regression on a stream of points.
 */
object TestStreamingRegression {
  def main(args: Array[String]) = {

    if (args.size < 3)
      throw new RuntimeException("Invalid arguments")

    val brokers :: inputTopic :: outputTopic :: _ = args.toList

    val conf = new SparkConf().setAppName("Spark Streaming basic example")
    val ssc = new StreamingContext(conf, Seconds(10))

    // Kafka consumer parameters
    val consParams = Map[String, Object](
      "bootstrap.servers" -> brokers,
      "key.deserializer" -> classOf[ByteArrayDeserializer],
      "value.deserializer" -> classOf[ByteArrayDeserializer],
      "group.id" -> "TestStreamingRegression",
      "auto.offset.reset" -> "earliest",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )

    // Kafka producer parameters
    val prodParams = Map[String, Object](
      "bootstrap.servers" -> brokers,
      "key.serializer" -> classOf[StringSerializer],
      "value.serializer" -> classOf[StringSerializer]
    )

    // As JsonConverter is not serializable and maintains some caches
    // it is wrapped in a lazy instance and broadcasted to each node.
    val converter = ssc.sparkContext.broadcast(new Converter(inputTopic))

    // The Kafka Producer is broadcasted to all nodes in a similar manner.
    val sink = ssc.sparkContext.broadcast(
      new KafkaSink[String, String](outputTopic, prodParams)
    )

    // We use a direct Kafka stream (low-level Kafka API)
    val stream = KafkaUtils.createDirectStream[Array[Byte], Array[Byte]](
      ssc,
      PreferConsistent,
      Subscribe[Array[Byte], Array[Byte]](Array(inputTopic), consParams)
    )

    // Generate our own output on the stream of input records
    stream.foreachRDD { rdd =>
      println("Input records: " + rdd.count)
    }

    // Transform the RDD of Kafka records to a RDD of points (label and features).
    // As the algorithm does not learn the intercept we must add it as a feature.
    val points = stream.flatMap { record =>
      for {
        value <- converter.value.fromRecord(record)
        row = value.split(",").flatMap(f => Option(f.toDouble))
        if row.size >= 2
        label = row.head
        features = Array(1.0) ++ row.tail
      } yield LabeledPoint(label, Vectors.dense(features))
    }.cache()

    // Initialize the linear regression model and algorithm
    val model = new StreamingLinearRegressionWithSGD().
                    setInitialWeights(Vectors.zeros(2)).
                    setNumIterations(50).
                    setStepSize(1.0)

    // What this function does is generate an output to our RDD of points
    // to update the linear regression model
    model.trainOn(points)

    // Generate our own output on the stream of points after the model update
    points.foreachRDD { rdd =>
      val n = rdd.count
      val theta = model.latestModel.weights

      println("Examples: " + n + " / Theta: " + theta)

      sink.value.send(theta.toArray.mkString(",")); ()
    }

    println("=== START ===")

    ssc.start()
    ssc.awaitTerminationOrTimeout(600000)
    ssc.stop(true, true)

    println("=== STOP ===")
  }
}
