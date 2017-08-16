package eu.swdev.cstream

import akka.stream.FlowShape
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Merge}

import scala.util.{Failure, Success, Try}

/** Provides the `bind` combinator that allows to feed the output of a flow into the input of a next flow.
  * The `bind` combinator is a generalization of the Akka Stream `via` combinator.
  *
  * The functionality of the `bind` combinator is similar to a "monadic" bind operation in that it allows to
  * sequence values through a compound flow while taking care of the context of these values (i.e. involved type
  * constructors).
  *
  * The output type of the first flow might be a possibly nested higher-kinded type whereas the input type of the second
  * flow must be a "simple" type. The `bind` combinator extracts the "simple" value of the output of the first flow
  * if possible and feeds that value into the second flow. If no "simple" value can be extracted then the
  * second flow can not be executed and that output is "promoted" into a corresponding output of the compound flow.
  *
  * The `bind` combinator calculates an output type for the compound flow that depends on the output type of the first
  * flow and the output type of the second flow. The calculation tries to avoid superfluous type constructors by unifying
  * the stacks of nested type constructors and deriving minimal adjustments.
  */
trait BindSupport {

  implicit class BindOp[In1, Out1, Mat1, In2](flow1: Flow[In1, Out1, Mat1])(implicit outToIn: OutToIn[Out1, In2]) {

    def bind[Out2, OutC](
        flow2: Flow[In2, Out2, _]
    )(implicit bindEv: BindEv[Out1, In2, Out2, OutC]): Flow[In1, OutC, Mat1] = flow1.bindMat(flow2)(Keep.left)

    def bindMat[Out2, Mat2, OutC, MatC](flow2: Flow[In2, Out2, Mat2])(comb: (Mat1, Mat2) => MatC)(
        implicit bindEv: BindEv[Out1, In2, Out2, OutC]
    ): Flow[In1, OutC, MatC] = {
      val g = GraphDSL.create(flow1, flow2)(comb) { implicit b => (flow1, flow2) =>
        val eo = b.add(EitherOr.fromEither[In2, OutC])
        val mg = b.add(Merge[OutC](2))

        flow1 ~> Flow[Out1].map(t =>
          bindEv.eitherIn2OrOutC(t) match {
            case Left(in2)  => Left(in2)
            case Right(out) => Right(out)
        }) ~> eo.in

        eo.left ~> flow2 ~> Flow[Out2].map { out2 =>
          bindEv.promoteOut2ToOutC(out2)
        } ~> mg

        eo.right ~> mg

        FlowShape(flow1.in, mg.out)
      }
      Flow.fromGraph(g)
    }
  }

}

/**
  * Extracts the innermost type from a nested higher-kinded type.
  *
  * @tparam Out a possibly higher-kinded type
  * @tparam In the innermost type
  */
class OutToIn[Out, In]

trait LowLevelOutToIn {

  implicit def base[T] = new OutToIn[T, T]

}

object OutToIn extends LowLevelOutToIn {

  implicit def recurse[F[_], Out, In](implicit ev: OutToIn[Out, In]): OutToIn[F[Out], In] = new OutToIn[F[Out], In]

}

trait BindEv[-Out1, In2, -Out2, OutC] {

  def eitherIn2OrOutC(out1: Out1): Either[In2, OutC]
  def promoteOut2ToOutC(out2: Out2): OutC
}

trait BindEvPrio3 {

  implicit def base[X, Y]: BindEv[X, X, Y, Y] = new BindEv[X, X, Y, Y] {
    override def eitherIn2OrOutC(out1: X) = Left(out1)

    override def promoteOut2ToOutC(out2: Y) = out2
  }
}

trait BindEvPrio2 extends BindEvPrio3 {

  implicit def optionRight[Out1, In2, Out2, OutC](
      implicit ev: BindEv[Out1, In2, Out2, OutC]): BindEv[Out1, In2, Option[Out2], Option[OutC]] =
    new BindEv[Out1, In2, Option[Out2], Option[OutC]] {
      override def eitherIn2OrOutC(out1: Out1): Either[In2, Option[OutC]] = ev.eitherIn2OrOutC(out1).map(Some(_))
      override def promoteOut2ToOutC(out2: Option[Out2]): Option[OutC] = out2 match {
        case Some(s) => Some(ev.promoteOut2ToOutC(s))
        case None    => None
      }
    }

  implicit def tryRight[Out1, In2, Out2, OutC](
      implicit ev: BindEv[Out1, In2, Out2, OutC]): BindEv[Out1, In2, Try[Out2], Try[OutC]] =
    new BindEv[Out1, In2, Try[Out2], Try[OutC]] {
      override def eitherIn2OrOutC(out1: Out1): Either[In2, Try[OutC]] = ev.eitherIn2OrOutC(out1).map(Success(_))
      override def promoteOut2ToOutC(out2: Try[Out2]): Try[OutC] = out2 match {
        case Success(s) => Success(ev.promoteOut2ToOutC(s))
        case Failure(f) => Failure(f)
      }
    }

}

trait BindEvPrio1 extends BindEvPrio2 {

  implicit def optionLeft[Out1, In2, Out2, OutC](
      implicit ev: BindEv[Out1, In2, Out2, OutC]): BindEv[Option[Out1], In2, Out2, Option[OutC]] =
    new BindEv[Option[Out1], In2, Out2, Option[OutC]] {
      override def eitherIn2OrOutC(out1: Option[Out1]): Either[In2, Option[OutC]] = out1 match {
        case Some(s) => ev.eitherIn2OrOutC(s).map(Some(_))
        case None    => Right(None)
      }
      override def promoteOut2ToOutC(out2: Out2): Option[OutC] = Some(ev.promoteOut2ToOutC(out2))
    }

  implicit def tryLeft[Out1, In2, Out2, OutC](
      implicit ev: BindEv[Out1, In2, Out2, OutC]): BindEv[Try[Out1], In2, Out2, Try[OutC]] =
    new BindEv[Try[Out1], In2, Out2, Try[OutC]] {
      override def eitherIn2OrOutC(out1: Try[Out1]): Either[In2, Try[OutC]] = out1 match {
        case Success(s) => ev.eitherIn2OrOutC(s).map(Success(_))
        case Failure(t) => Right(Failure(t))
      }
      override def promoteOut2ToOutC(out2: Out2): Try[OutC] = Success(ev.promoteOut2ToOutC(out2))
    }

}

trait BindEvPrio0 extends BindEvPrio1 {

  implicit def eitherLeft[L, Out1, In2, Out2, OutC](
      implicit ev: BindEv[Out1, In2, Out2, OutC]): BindEv[Either[L, Out1], In2, Out2, Either[L, OutC]] =
    new BindEv[Either[L, Out1], In2, Out2, Either[L, OutC]] {
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
      implicit ev: BindEv[Out1, In2, Out2, OutC]): BindEv[Out1, In2, Either[L, Out2], Either[L, OutC]] =
    new BindEv[Out1, In2, Either[L, Out2], Either[L, OutC]] {
      override def eitherIn2OrOutC(out1: Out1): Either[In2, Either[L, OutC]] = ev.eitherIn2OrOutC(out1).map(Right(_))
      override def promoteOut2ToOutC(out2: Either[L, Out2]): Either[L, OutC] = out2 match {
        case Left(l)  => Left(l)
        case Right(r) => Right(ev.promoteOut2ToOutC(r))
      }
    }

}

object BindEv extends BindEvPrio0 {


  implicit def optionBoth[Out1, In2, Out2, OutC](implicit ev: BindEv[Out1, In2, Out2, OutC]): BindEv[Option[Out1], In2, Option[Out2], Option[OutC]] = new BindEv[Option[Out1], In2, Option[Out2], Option[OutC]] {
    override def eitherIn2OrOutC(out1: Option[Out1]) = out1 match {
      case Some(s) => ev.eitherIn2OrOutC(s).map(Some(_))
      case None => Right(None)
    }

    override def promoteOut2ToOutC(out2: Option[Out2]) = out2 match {
      case Some(s) => Some(ev.promoteOut2ToOutC(s))
      case None => None
    }
  }

  implicit def tryBoth[Out1, In2, Out2, OutC](
    implicit ev: BindEv[Out1, In2, Out2, OutC]): BindEv[Try[Out1], In2, Try[Out2], Try[OutC]] =
    new BindEv[Try[Out1], In2, Try[Out2], Try[OutC]] {
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
    implicit ev: BindEv[Out1, In2, Out2, OutC]): BindEv[Either[L, Out1], In2, Either[L, Out2], Either[L, OutC]] =
    new BindEv[Either[L, Out1], In2, Either[L, Out2], Either[L, OutC]] {
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

