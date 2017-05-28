package eu.swdev.cstream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.{TestKit, TestKitBase}
import org.scalatest.FunSuite

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class JoinTest extends FunSuite with TestKitBase with JoinSupport {

  implicit lazy val system  = ActorSystem("test")
  implicit val materializer = ActorMaterializer()

  def probe[In, Out](flow: Flow[In, Out, _])(inOuts: (In, Out)*): Unit = {
    val (pub, sub) = TestSource.probe[In].via(flow).toMat(TestSink.probe[Out])(Keep.both).run
    for {
      (in, out) <- inOuts
    } {
      pub.sendNext(in)
      sub.requestNext(out)
    }
  }

  test("Option") {

    // optionality propagates

    val flow: Flow[Option[String], Option[Boolean], NotUsed] =
      Flow[Option[String]]
        .bind(Flow[String].map(_.length))
        .bind(Flow[Int].map(_ % 3 == 0))

    probe(flow)(None -> None, Some("abc") -> Some(true), Some("abcd") -> Some(false))

  }

  test("Option[Option[]]") {

    // nested options are flattened

    val flow: Flow[Option[String], Option[Boolean], NotUsed] =
      Flow[Option[String]]
        .bind(Flow[String].map(s => Option(s.length)))
        .bind(Flow[Int].map(_ % 3 == 0))
        .bind(Flow[Boolean].map(Option(_)))

    probe(flow)(None -> None, Some("abc") -> Some(true), Some("abcd") -> Some(false))

  }

  test("Option[Try]]") {

    val ex = new Exception()

    val flow: Flow[Option[String], Option[Try[Int]], NotUsed] =
      Flow[Option[String]]
        .bind(Flow[String].map(s =>
          Try {
            Integer.parseInt(s)
        }))
        .bind(Flow[Int].map(i =>
          Try {
            10 / i
          }.recoverWith { case NonFatal(e) => Failure(ex) }))
        .bind(Flow[Int].map(_ * 2))

    probe(flow)(Some("5") -> Some(Success(4)), Some("0") -> Some(Failure(ex)))

  }

  test("Either") {

    val flow: Flow[Either[String, Int], Either[String, Double], NotUsed] =
      Flow[Either[String, Int]]
        .bind(Flow[Int].map(_.toDouble))

    probe(flow)(Left("abc") -> Left("abc"), Right(5) -> Right(5.0))

  }

  test("Either (Right to Left)") {

    val flow: Flow[Either[String, Int], Either[String, Double], NotUsed] =
      Flow[Either[String, Int]]
        .bind(Flow[Int].map(_.toDouble).mapConcat(d => List(Left(d.toString), Right(d))))

    probe(flow)(Right(4) -> Left("4.0"))

  }

  test("Try -> Either[Try]]") {

    val flow: Flow[Try[Int], Either[String, Try[Double]], NotUsed] =
      Flow[Try[Int]]
        .bind(
          Flow[Int]
            .map(_.toDouble)
            .mapConcat(d =>
              List(Right(Try {
                d
              }), Left(d.toString))))

    probe(flow)(Success(4) -> Right(Success(4.0)))

  }

  test("Try -> Try[Either]") {

    val flow: Flow[Try[Int], Try[Either[String, Double]], NotUsed] =
      Flow[Try[Int]]
        .bind(Flow[Int].map(_.toDouble).mapConcat(d => List(Left(d.toString), Right(d))))

    probe(flow)(Success(4) -> Success(Left("4.0")))
  }

}
