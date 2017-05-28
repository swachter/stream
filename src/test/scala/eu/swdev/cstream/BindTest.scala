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

class BindTest extends FunSuite with TestKitBase with BindSupport {

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

  test("Simple") {

    // the `bind` combinator can be used like the `via` combinator

    val flow: Flow[Int, Int, NotUsed] =
      Flow[Int]
        .map(_.toString)
        .bind(Flow[String].map(_.length))
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

    // optionality propagates; an additional nested `Try` is introduced

    val ex = new Exception()

    val flow: Flow[Option[String], Option[Try[Int]], NotUsed] =
      Flow[Option[String]]
        .bind(Flow[String].map(s => Try(Integer.parseInt(s))))
        .bind(Flow[Int].map(i => Try(10 / i).recoverWith { case NonFatal(e) => Failure(ex) }))
        .bind(Flow[Int].map(_ * 2))

    probe(flow)(Some("5") -> Some(Success(4)), Some("0") -> Some(Failure(ex)))

  }

  test("Either") {

    // the Left value of an Either is passed through unchanged; the bound flow processes the Right value

    val flow: Flow[Either[String, Int], Either[String, Double], NotUsed] =
      Flow[Either[String, Int]]
        .bind(Flow[Int].map(_.toDouble))

    probe(flow)(Left("abc") -> Left("abc"), Right(5) -> Right(5.0))

  }

  test("Either (Right to Left)") {

    // the flow that is bound to an Either can output Left values

    val flow: Flow[Either[String, Int], Either[String, Double], NotUsed] =
      Flow[Either[String, Int]]
        .bind(Flow[Int].map(_.toDouble).mapConcat(d => List(Left(d.toString), Right(d))))

    probe(flow)(Right(4) -> Left("4.0"))

  }

  test("Try -> Either[Try]]") {

    // the "Try" context of the first flow can be unified with the "Either[Try]" context of the second flow

    val ex = new Exception()

    val flow: Flow[Try[Int], Either[String, Try[Double]], NotUsed] =
      Flow[Try[Int]]
        .bind(
          Flow[Int]
            .map(_.toDouble)
            .map(d => Right(Try(d)).asInstanceOf[Either[String, Try[Double]]]))

    probe(flow)(Success(4) -> Right(Success(4.0)), Failure(ex) -> Right(Failure(ex)))

  }

  test("Try -> Try[Either]") {

    // the "Try" context of the first flow is added on top of the "Either[String, Double]" context of the second flow

    val ex = new Exception()

    val flow: Flow[Try[Int], Try[Either[String, Double]], NotUsed] =
      Flow[Try[Int]]
        .bind(Flow[Int].map(_.toDouble).map(d => Left(d.toString).asInstanceOf[Either[String, Double]]))

    probe(flow)(Success(4) -> Success(Left("4.0")), Failure(ex) -> Failure(ex))
  }

  test("Either[Option[Try]]") {

    // superfluous type constructors are avoided

    val flow: Flow[Either[String, Option[Try[Int]]], Either[String, Option[Try[Int]]], NotUsed] =
      Flow[Either[String, Option[Try[Int]]]]
        .bind(Flow[Int].map(_ * 2))
        // the `Try` type constructor is already part of the input type
        .bind(Flow[Int].map(i => Try(2/i)))
        // the `Option[Try]` type constructor is already part of the input type
        .bind(Flow[Int].map(i => Option(Try(2/i))))

  }

}
