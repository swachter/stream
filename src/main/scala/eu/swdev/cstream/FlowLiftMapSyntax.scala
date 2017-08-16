package eu.swdev.cstream

import akka.stream.scaladsl.Flow
import autolift.LiftMap

trait FlowLiftMapSyntax {

  implicit class LiftMapOp[In, Out, Mat, F[_]](self: Flow[In, F[Out], Mat]) {

    def liftMap[B, C](func: B => C)(implicit lift: LiftMap[F[Out], B => C]): Flow[In, lift.Out, Mat] =
      self.map(fo => lift(fo, func))

  }

}
