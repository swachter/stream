package eu.swdev.cstream

import akka.NotUsed
import akka.stream.scaladsl.Flow
import org.scalatest.FunSuite

import scala.util.Try

class JoinTest extends FunSuite with JoinSupport {

  test("bind") {

    val f1: Flow[Option[String], Option[Boolean], NotUsed] =
      Flow[Option[String]]
        .bind(Flow[String].map(_.length))
        .bind(Flow[Int].map(_ % 3 == 0))
        .bind(Flow[Boolean])

    val f2: Flow[Option[String], Option[Boolean], NotUsed] =
      Flow[Option[String]]
        .bind(Flow[String].map(s => Option(s.length)))
        .bind(Flow[Int].map(_ % 3 == 0))
        .bind(Flow[Boolean].map(Option(_)))

    val f3: Flow[Option[String], Option[Try[Int]], NotUsed] =
      Flow[Option[String]]
        .bind(Flow[String].map(s => Try { Integer.parseInt(s) }))
        .bind(Flow[Int].map(i => Try { 10 / i }))
        .bind(Flow[Int])

    val f4 = Flow[Either[String, Int]]
      .bind(Flow[Int].map(_.toDouble))

    val f5 = Flow[Either[String, Int]]
      .bind(Flow[Int].map(_.toDouble).mapConcat(d => List(Left(d.toString), Right(d))))

    val f6: Flow[Try[Int], Either[String, Try[Double]], NotUsed] =
      Flow[Try[Int]]
        .bind(Flow[Int].map(_.toDouble).mapConcat(d => List(Left(d.toString), Right(Try { d }))))

    val f7 = Flow[Try[Int]]
      .bind(Flow[Int].map(_.toDouble).mapConcat(d => List(Left(d.toString), Right(d))))

  }

}
