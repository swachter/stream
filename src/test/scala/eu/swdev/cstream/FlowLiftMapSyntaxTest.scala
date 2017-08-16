package eu.swdev.cstream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.testkit.TestKitBase
import cats.Id
import org.scalatest.FunSuite

import scala.util.{Success, Try}

class FlowLiftMapSyntaxTest extends FunSuite with TestKitBase with FlowLiftMapSyntax {

  implicit lazy val system  = ActorSystem("test")
  implicit val materializer = ActorMaterializer()

  test("liftMap") {

    import cats.implicits._
    import autolift.cats.functor._

    val flow1 = Flow[Option[Int]].liftMap((_: Int) + 2)

    val flow2 = Flow[Option[Try[List[Int]]]].liftMap((_: Int) + 2)

    val flow3 = Flow[Either[String, Int]].liftMap((_: Int) + 2)
  }


}
