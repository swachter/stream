package eu.swdev.cstream

import cats.Functor

/**
  * Allow to map over nested functors.
  *
  * Based on work by Owein Reese (cf. http://www.oweinreese.com/blog/2016/1/10/type-recursion-and-functors)
  */
trait CtxFlowLiftMapSyntax {

  implicit class LiftMapOp[In, Out, Mat, F[_]](self: CtxFlow[In, F[Out], Mat]) {

    def liftMap[B, C](func: B => C)(implicit lift: LiftMap[F[Out], B, C]): CtxFlow[In, lift.Out, Mat] =
      self.map(fo => lift(fo, func))

  }

  trait LiftMap[FA, A, B] {
    type Out
    def apply(fa: FA, func: A => B): Out
  }

  trait LowPrioLiftMap {
    implicit def recur[F[_], FA, A, B](implicit fn: Functor[F], lift: LiftMap[FA, A, B]) =
      new LiftMap[F[FA], A, B] {
        override type Out = F[lift.Out]
        override def apply(ffa: F[FA], f: A => B) = fn.map(ffa)(fa => lift(fa, f))
      }
  }

  object LiftMap extends LowPrioLiftMap {

    implicit def base[F[_], A, B](implicit fn: Functor[F]) =
      new LiftMap[F[A], A, B] {
        override type Out = F[B]
        override def apply(fa: F[A], f: A => B) = fn.map(fa)(f)
      }

  }

}
