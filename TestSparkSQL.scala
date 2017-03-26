package name.ebastien.spark

import org.apache.spark.sql.{SparkSession, SaveMode}

case class Movie(
    color: String,
    director_name: String,
    num_critic_for_reviews: Int,
    duration: Int,
    director_facebook_likes: Int,
    actor_3_facebook_likes: Int,
    actor_2_name: String,
    actor_1_facebook_likes: Int,
    gross: Long,
    genres: String,
    actor_1_name: String,
    movie_title: String,
    num_voted_users: Int,
    cast_total_facebook_likes: Int,
    actor_3_name: String,
    facenumber_in_poster: Int,
    plot_keywords: String,
    movie_imdb_link: String,
    num_user_for_reviews: Int,
    language: String,
    country: String,
    content_rating: String,
    budget: Long,
    title_year: Int,
    actor_2_facebook_likes: Int,
    imdb_score: Double,
    aspect_ratio: Double,
    movie_facebook_likes: Int
  )


object TestSparkSQL {
  def main(args: Array[String]) = {
    val spark = SparkSession
      .builder()
      .appName("Spark SQL basic example")
      .getOrCreate()

    import spark.implicits._

    println("=== START ===")

    val movies = spark.read
      .option("header", true)
      .option("inferSchema", true)
      .csv("hdfs://hdfs/imdb-5000-movie-dataset/movie_metadata.csv")
      .na.fill(0).as[Movie]

    val actors = movies.groupBy("actor_1_name")
                       .sum("gross")
                       .sort($"sum(gross)".desc)

    actors.coalesce(1).write.mode(SaveMode.Overwrite).csv("hdfs://hdfs/tmp/actors.csv")

    println("=== STOP ===")

    spark.stop
  }
}

// vim: set ts=4 sw=4 et:
