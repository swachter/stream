package eu.swdev.cstream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.testkit.TestKitBase
import org.scalatest.FunSuite

import scala.util.Try

class CtxFlowLiftMapTest extends FunSuite with TestKitBase {

  implicit lazy val system  = ActorSystem("test")
  implicit val materializer = ActorMaterializer()

  test("liftMap") {

    import cats.implicits._
//    implicit val optFunctor = cats.implicits.catsStdInstancesForOption

    val flow1: CtxFlow[Option[Int], Option[Int], NotUsed] = CtxFlow[Option[Int]].liftMap((_: Int) + 2)

    val flow2: CtxFlow[Option[Try[List[Int]]], Option[Try[List[Int]]], NotUsed] = CtxFlow[Option[Try[List[Int]]]].liftMap((_: Int) + 2)

    val flow3: CtxFlow[Either[String, Int], Either[String, Int], NotUsed] = CtxFlow[Either[String, Int]].liftMap((_: Int) + 2)
  }

}
