package eu.swdev.cstream

import akka.stream.scaladsl.GraphDSL.{Builder, Implicits}
import akka.stream.scaladsl.GraphDSL.Implicits.{CombinerBase, PortOps}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, Inlet, Outlet, Shape}

import scala.collection.immutable.Seq
import scala.collection.immutable

abstract class BaseShape(
  val inlets: immutable.Seq[Inlet[_]],
  val outlets: immutable.Seq[Outlet[_]]
) extends Shape {

  final override def deepCopy() = copyFromPorts(inlets.map(_.carbonCopy()), outlets.map(_.carbonCopy()))

  def copyFromPorts(inlets: immutable.Seq[Inlet[_]], outlets: immutable.Seq[Outlet[_]]): Shape

}

class EitherOrShape[In, Left, Right](
  val in: Inlet[In],
  val left: Outlet[Left],
  val right: Outlet[Right]
) extends BaseShape(in :: Nil, left :: right :: Nil) {

  override def copyFromPorts(inlets: Seq[Inlet[_]], outlets: Seq[Outlet[_]]): Shape =
    new EitherOrShape[In, Left, Right](inlets(0).as, outlets(0).as, outlets(1).as)

}

object EitherOrShape {

  /**
    * Allows to use an EitherOr shape as link target in the GraphDSL.
    *
    * (e.g. ```out ~> eitherOr```)
    */
  implicit def eitherOrShapeToInlet[In, Left, Right](shape: EitherOrShape[In, Left, Right]): Inlet[In] = shape.in
}

/**
  * GraphStage that supports the distinction of two different cases.
  *
  * Each input is published either on the "left" or on the "right" output depending on the result of the given function.
  *
  * @example {{{
  *           // distinguished between values and errors
  *           val eo = b.add(EitherOr.fromPf[Data[Int]] {
  *             case DValue(v) => Left(v)
  *             case d: DError => Right(d)
  *           })
  *           eo.left ~> <use v>
  *           eo.right ~> <forward the error>
  * }}}
  *
  * @example
  *          {{{
  *          // splits numbers into even and odd numbers
  *          val eo = b.add(EitherOr((i: Int) => if (i % 2 == 0) Left(i) else Right(i)))
  *          in ~> eo.in
  *          eo.left ~> <process even numbers>
  *          eo.right ~> <process odd numbers>
  * }}}
  */
case class EitherOr[In, Left, Right](func: In => Either[Left, Right]) extends GraphStage[EitherOrShape[In, Left, Right]] {

  val shape = new EitherOrShape(
    Inlet[In]("EitherOr.in"),
    Outlet[Left]("EitherOr.left"),
    Outlet[Right]("EitherOr.right")
  )

  override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {

    setHandler(
      shape.in,
      new InHandler {
        override def onPush(): Unit = {
          val grabbed = grab(shape.in)
          func(grabbed) match {
            case Left(v)  => push(shape.left, v)
            case Right(v) => push(shape.right, v)
          }
        }
      }
    )

    setHandler(shape.left, new OutHandler {
      override def onPull(): Unit = {
        if (isAvailable(shape.right)) {
          pull(shape.in)
        }
      }
    })

    setHandler(shape.right, new OutHandler {
      override def onPull(): Unit = {
        if (isAvailable(shape.left)) {
          pull(shape.in)
        }
      }
    })
  }

}

object EitherOr {

  /**
    * Used to capture the input type parameter.
    */
  trait EitherOrCreator[In] {
    def apply[Left, Right](func: PartialFunction[In, Either[Left, Right]]): EitherOr[In, Left, Right]
  }

  /**
    * Allows to construct an EitherOr stage from a partial function.
    *
    * Scalas type inference requires that the input type of a function literal is known. The parameterless apply
    * method allows to specify and capture the input type parameter while still deriving the output parameter type.
    */
  def apply[In]: EitherOrCreator[In] = new EitherOrCreator[In] {
    override def apply[Left, Right](func: PartialFunction[In, Either[Left, Right]]) = new EitherOr(func)
  }

  /**
    * Offers the same functionality as the parameterless apply method.
    *
    * This method is added because IntelliJ has difficulties in distinguishing between the parameterless apply method
    * and a constructor of the EitherOr class.
    */
  def fromPf[In]: EitherOrCreator[In] = new EitherOrCreator[In] {
    override def apply[Left, Right](func: PartialFunction[In, Either[Left, Right]]) = new EitherOr(func)
  }

  /**
    * Allows to construct an EitherOr stage that splits Either instances.
    */
  def fromEither[Left, Right]: EitherOr[Either[Left, Right], Left, Right] = new EitherOr(identity)

}
