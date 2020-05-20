package me.ethanbell.liberchat

import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.{FlowShape, Supervision}
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Merge, Partition}
object AkkaUtil {

  def chattySupervisor(implicit system: ActorSystem[_]): Supervision.Decider = { err =>
    system.log.error("Unhandled exception in stream", err)
    Supervision.Stop
  }

  trait Replyable {
    type T
    val replyTo: ActorRef[T]
  }
  implicit class FlowOps[-In, L, R, +Mat](flow: Flow[In, Either[L, R], Mat]) {
    def viaEitherMat[Out, M1, M2, M3](
      leftFlow: Flow[L, Out, M1],
      rightFlow: Flow[R, Out, M2]
    )(combineMat: (Mat, M1, M2) => M3): Flow[In, Out, M3] =
      flow.viaMat(eitherFlow(leftFlow, rightFlow)(Keep.both)) {
        case (mat, (m1, m2)) => combineMat(mat, m1, m2)
      }
  }
  implicit class FlowOpsNoMat[-In, L, R, +Mat](flow: Flow[In, Either[L, R], Mat]) {
    def viaEither[Out](
      leftFlow: Flow[L, Out, NotUsed],
      rightFlow: Flow[R, Out, NotUsed]
    ): Flow[In, Out, Mat] =
      FlowOps(flow).viaEitherMat(leftFlow, rightFlow) {
        case (mat, _, _) => mat
      }
  }

  def eitherFlow[L, R, Out, M1, M2, Mat](
    leftFlow: Flow[L, Out, M1],
    rightFlow: Flow[R, Out, M2]
  )(combineMat: (M1, M2) => Mat): Flow[Either[L, R], Out, Mat] =
    Flow.fromGraph(GraphDSL.create(leftFlow, rightFlow)(combineMat) {
      implicit builder: GraphDSL.Builder[Mat] => (left, right) =>
        import GraphDSL.Implicits._
        val partition = builder.add(Partition[Either[L, R]](2, {
          case Left(_)  => 0
          case Right(_) => 1
        }))

        val merge = builder.add(Merge[Out](2))
        partition.out(0).map(_.swap.getOrElse(???)) ~> left ~> merge.in(0)
        partition.out(1).map(_.getOrElse(???)) ~> right ~> merge.in(1)
        FlowShape.of(partition.in, merge.out)
    })
}
