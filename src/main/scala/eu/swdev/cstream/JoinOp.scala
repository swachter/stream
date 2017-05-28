package eu.swdev.cstream

import akka.stream.FlowShape
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Merge}
import cats.Id

import scala.util.{Failure, Success, Try}

/**
  */
trait JoinSupport {

  implicit class JoinOp[In1, Out1, Mat1, In2](flow1: Flow[In1, Out1, Mat1])(implicit outToIn: OutToIn[Out1, In2]) {

    def bind[Out2, OutC](
        flow2: Flow[In2, Out2, _]
    )(implicit joinEv: JoinEv[Out1, In2, Out2, OutC]): Flow[In1, OutC, Mat1] = flow1.bindMat(flow2)(Keep.left)

    def bindMat[Out2, Mat2, OutC, MatC](flow2: Flow[In2, Out2, Mat2])(comb: (Mat1, Mat2) => MatC)(
        implicit joinEv: JoinEv[Out1, In2, Out2, OutC]
    ): Flow[In1, OutC, MatC] = {
      val g = GraphDSL.create(flow1, flow2)(comb) { implicit b => (flow1, flow2) =>
        val eo = b.add(EitherOr.fromEither[In2, OutC])
        val mg = b.add(Merge[OutC](2))

        flow1 ~> Flow[Out1].map(t =>
          joinEv.eitherIn2OrOutC(t) match {
            case Left(in2)  => Left(in2)
            case Right(out) => Right(out)
        }) ~> eo.in

        eo.left ~> flow2 ~> Flow[Out2].map { out2 =>
          joinEv.promoteOut2ToOutC(out2)
        } ~> mg

        eo.right ~> mg

        FlowShape(flow1.in, mg.out)
      }
      Flow.fromGraph(g)
    }
  }

}

trait OutToIn[Out, In]

trait LowLevelOutToIn {

  def instance[Out, In] = new OutToIn[Out, In] {}

  implicit def base[T] = instance[T, T]

}

object OutToIn extends LowLevelOutToIn {

  implicit def _try[T, X](implicit ev: OutToIn[T, X])      = instance[Try[T], X]
  implicit def option[T, X](implicit ev: OutToIn[T, X])    = instance[Option[T], X]
  implicit def either[L, T, X](implicit ev: OutToIn[T, X]) = instance[Either[L, T], X]

}

trait JoinEv[Out1, In2, Out2, OutC] {

  def eitherIn2OrOutC(out1: Out1): Either[In2, OutC]
  def promoteOut2ToOutC(out2: Out2): OutC
}

trait JoinEvBothOrLeft[Out1, In2, Out2, OutC] extends JoinEv[Out1, In2, Out2, OutC]

trait JoinEvBothOrRight[Out1, In2, Out2, OutC] extends JoinEv[Out1, In2, Out2, OutC]

trait JoinEvBoth[Out1, In2, Out2, OutC] extends JoinEvBothOrLeft[Out1, In2, Out2, OutC] with JoinEvBothOrRight[Out1, In2, Out2, OutC]

trait JoinEvLeft[Out1, In2, Out2, OutC] extends JoinEvBothOrLeft[Out1, In2, Out2, OutC]

trait JoinEvRight[Out1, In2, Out2, OutC] extends JoinEvBothOrRight[Out1, In2, Out2, OutC]

trait JoinEvBase[Out1, In2, Out2, OutC]
    extends JoinEvLeft[Out1, In2, Out2, OutC]
    with JoinEvRight[Out1, In2, Out2, OutC]
    with JoinEvBoth[Out1, In2, Out2, OutC]

trait JoinEvPrio4 {

  implicit def base[X, Y]: JoinEvBase[X, X, Y, Y] = new JoinEvBase[X, X, Y, Y] {
    override def eitherIn2OrOutC(out1: X): Either[X, Y] = Left(out1)
    override def promoteOut2ToOutC(out2: Y): Y          = out2
  }

}

trait JoinEvPrio3 extends JoinEvPrio4 {

  implicit def optionLeft[Out1, In2, Out2, OutC](
    implicit ev: JoinEvBothOrLeft[Out1, In2, Out2, OutC]): JoinEvLeft[Option[Out1], In2, Out2, Option[OutC]] =
    new JoinEvLeft[Option[Out1], In2, Out2, Option[OutC]] {
      override def eitherIn2OrOutC(out1: Option[Out1]): Either[In2, Option[OutC]] = out1 match {
        case Some(s) => ev.eitherIn2OrOutC(s).map(Some(_))
        case None    => Right(None)
      }
      override def promoteOut2ToOutC(out2: Out2): Option[OutC] = Some(ev.promoteOut2ToOutC(out2))
    }

  implicit def tryLeft[Out1, In2, Out2, OutC](
    implicit ev: JoinEvBothOrLeft[Out1, In2, Out2, OutC]): JoinEvLeft[Try[Out1], In2, Out2, Try[OutC]] =
    new JoinEvLeft[Try[Out1], In2, Out2, Try[OutC]] {
      override def eitherIn2OrOutC(out1: Try[Out1]): Either[In2, Try[OutC]] = out1 match {
        case Success(s) => ev.eitherIn2OrOutC(s).map(Success(_))
        case Failure(t) => Right(Failure(t))
      }
      override def promoteOut2ToOutC(out2: Out2): Try[OutC] = Success(ev.promoteOut2ToOutC(out2))
    }

  implicit def optionRight[Out1, In2, Out2, OutC](
    implicit ev: JoinEvBothOrRight[Out1, In2, Out2, OutC]): JoinEvRight[Out1, In2, Option[Out2], Option[OutC]] =
    new JoinEvRight[Out1, In2, Option[Out2], Option[OutC]] {
      override def eitherIn2OrOutC(out1: Out1): Either[In2, Option[OutC]] = ev.eitherIn2OrOutC(out1).map(Some(_))
      override def promoteOut2ToOutC(out2: Option[Out2]): Option[OutC] = out2 match {
        case Some(s) => Some(ev.promoteOut2ToOutC(s))
        case None    => None
      }
    }

  implicit def tryRight[Out1, In2, Out2, OutC](
    implicit ev: JoinEvBothOrRight[Out1, In2, Out2, OutC]): JoinEvRight[Out1, In2, Try[Out2], Try[OutC]] =
    new JoinEvRight[Out1, In2, Try[Out2], Try[OutC]] {
      override def eitherIn2OrOutC(out1: Out1): Either[In2, Try[OutC]] = ev.eitherIn2OrOutC(out1).map(Success(_))
      override def promoteOut2ToOutC(out2: Try[Out2]): Try[OutC] = out2 match {
        case Success(s) => Success(ev.promoteOut2ToOutC(s))
        case Failure(f) => Failure(f)
      }
    }

}

trait JoinEvPrio2 extends JoinEvPrio3 {

  implicit def eitherLeft[L, Out1, In2, Out2, OutC](
    implicit ev: JoinEvBothOrLeft[Out1, In2, Out2, OutC]): JoinEvLeft[Either[L, Out1], In2, Out2, Either[L, OutC]] =
    new JoinEvLeft[Either[L, Out1], In2, Out2, Either[L, OutC]] {
      override def eitherIn2OrOutC(out1: Either[L, Out1]): Either[In2, Either[L, OutC]] = out1 match {
        case Left(l) => Right(Left(l))
        case Right(r) =>
          ev.eitherIn2OrOutC(r) match {
            case Left(l)  => Left(l)
            case Right(r) => Right(Right(r))
          }
      }
      override def promoteOut2ToOutC(out2: Out2): Either[L, OutC] = Right(ev.promoteOut2ToOutC(out2))
    }

  implicit def eitherRight[L, Out1, In2, Out2, OutC](
    implicit ev: JoinEvBothOrRight[Out1, In2, Out2, OutC]): JoinEvRight[Out1, In2, Either[L, Out2], Either[L, OutC]] =
    new JoinEvRight[Out1, In2, Either[L, Out2], Either[L, OutC]] {
      override def eitherIn2OrOutC(out1: Out1): Either[In2, Either[L, OutC]] = ev.eitherIn2OrOutC(out1).map(Right(_))
      override def promoteOut2ToOutC(out2: Either[L, Out2]): Either[L, OutC] = out2 match {
        case Left(l)  => Left(l)
        case Right(r) => Right(ev.promoteOut2ToOutC(r))
      }
    }

}

trait JoinEvPrio1 extends JoinEvPrio2 {

  implicit def optionBoth[Out1, In2, Out2, OutC](
    implicit ev: JoinEvBoth[Out1, In2, Out2, OutC]): JoinEvBoth[Option[Out1], In2, Option[Out2], Option[OutC]] =
    new JoinEvBoth[Option[Out1], In2, Option[Out2], Option[OutC]] {
      override def eitherIn2OrOutC(out1: Option[Out1]): Either[In2, Option[OutC]] = out1 match {
        case Some(s) => ev.eitherIn2OrOutC(s).map(Some(_))
        case None    => Right(None)
      }
      override def promoteOut2ToOutC(out2: Option[Out2]): Option[OutC] = out2 match {
        case Some(s) => Some(ev.promoteOut2ToOutC(s))
        case None    => None
      }
    }

  implicit def tryBoth[Out1, In2, Out2, OutC](
    implicit ev: JoinEvBoth[Out1, In2, Out2, OutC]): JoinEvBoth[Try[Out1], In2, Try[Out2], Try[OutC]] =
    new JoinEvBoth[Try[Out1], In2, Try[Out2], Try[OutC]] {
      override def eitherIn2OrOutC(out1: Try[Out1]): Either[In2, Try[OutC]] = out1 match {
        case Success(s) => ev.eitherIn2OrOutC(s).map(Success(_))
        case Failure(t) => Right(Failure(t))
      }
      override def promoteOut2ToOutC(out2: Try[Out2]): Try[OutC] = out2 match {
        case Success(s) => Success(ev.promoteOut2ToOutC(s))
        case Failure(t) => Failure(t)
      }
    }

  implicit def eitherBoth[L, Out1, In2, Out2, OutC](
    implicit ev: JoinEvBoth[Out1, In2, Out2, OutC]): JoinEvBoth[Either[L, Out1], In2, Either[L, Out2], Either[L, OutC]] =
    new JoinEvBoth[Either[L, Out1], In2, Either[L, Out2], Either[L, OutC]] {
      override def eitherIn2OrOutC(out1: Either[L, Out1]): Either[In2, Either[L, OutC]] = out1 match {
        case Left(l) => Right(Left(l))
        case Right(r) =>
          ev.eitherIn2OrOutC(r) match {
            case Left(l)  => Left(l)
            case Right(r) => Right(Right(r))
          }
      }
      override def promoteOut2ToOutC(out2: Either[L, Out2]): Either[L, OutC] = out2 match {
        case Left(l)  => Left(l)
        case Right(r) => Right(ev.promoteOut2ToOutC(r))
      }
    }

}

object JoinEv extends JoinEvPrio1 {

}
