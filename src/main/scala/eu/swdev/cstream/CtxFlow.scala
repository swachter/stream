package eu.swdev.cstream

import akka.NotUsed
import akka.stream.scaladsl.Flow

trait CtxFlow[-In, +Out, Mat] { self =>

 def create[Ctx <: StreamCtx]: Flow[(In, Ctx), (Out, Ctx), Mat]

  def map[O2](f: Out => O2): CtxFlow[In, O2, Mat] = new CtxFlow[In, O2, Mat] {
    override def create[Ctx <: StreamCtx]: Flow[(In, Ctx), (O2, Ctx), Mat] = self.create.map {
      case (out, ctx) => (f(out), ctx)
    }
  }
}

object CtxFlow extends BindSupport {

  def apply[In]: CtxFlow[In, In, NotUsed] = new CtxFlow[In, In, NotUsed] {
    override def create[Ctx <: StreamCtx]: Flow[(In, Ctx), (In, Ctx), NotUsed] = Flow[(In, Ctx)]
  }

}