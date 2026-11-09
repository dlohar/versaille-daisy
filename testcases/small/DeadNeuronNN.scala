import daisy.lang._
import Vector._

object DeadNeuronNN {
  def nn1(x: Vector): Vector = {
    require(lowerBounds(x, List(0.0, 0.0)) &&
      upperBounds(x, List(1.0, 1.0)))

    
    val weights1 = Matrix(List(
      List(1.0, 0.0),  // alive
      List(0.0, 0.0),  // dead -> should be pruned
      List(0.0, 1.0)   // alive
    ))
    val bias1 = Vector(List(0.0, 0.0, 0.0))

    val weights2 = Matrix(List(
      List(10.0, 100.0, 1000.0)
    ))
    val bias2 = Vector(List(0.0))

    val layer1 = relu(weights1 * x + bias1)
    val out = linear(weights2 * layer1 + bias2)
    out
  } ensuring(res => res +/- 1e-2)
}