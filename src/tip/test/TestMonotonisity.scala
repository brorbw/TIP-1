package tip.test

import tip.lattices.SignLattice

object TestMonotonisity {
  def main(args: Array[String]): Unit = {
    println(SignLattice.isMonotone(SignLattice.plus))
    println(SignLattice.isMonotone(SignLattice.minus))
    println(SignLattice.isMonotone(SignLattice.times))
    println(SignLattice.isMonotone(SignLattice.div))
    println(SignLattice.isMonotone(SignLattice.eqq))
    println(SignLattice.isMonotone(SignLattice.gt))
  }
}
