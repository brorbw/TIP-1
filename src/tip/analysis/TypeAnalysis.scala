package tip.analysis

import tip.ast._
import tip.solvers._
import tip.types._
import tip.ast.AstNodeData._
import tip.util.Log

/**
  * Unification-based type analysis.
  * The analysis associates a [[tip.types.TipType]] with each variable declaration and expression node in the AST.
  * It is implemented using [[tip.solvers.UnionFindSolver]].
  *
  * To novice Scala programmers:
  * The parameter `declData` is declared as "implicit", which means that invocations of `TypeAnalysis` obtain its value implicitly:
  * The call to `new TypeAnalysis` in Tip.scala does not explicitly provide this parameter, but it is in scope of
  * `implicit val declData: TypeData = new DeclarationAnalysis(programNode).analyze()`.
  * The TIP implementation uses implicit parameters many places to provide easy access to the declaration information produced
  * by `DeclarationAnalysis` and the type information produced by `TypeAnalysis`.
  * For more information about implicit parameters in Scala, see [[https://docs.scala-lang.org/tour/implicit-parameters.html]].
  */
class TypeAnalysis(program: AProgram)(implicit declData: DeclarationData) extends DepthFirstAstVisitor[Null] with Analysis[TypeData] {

  val log = Log.logger[this.type]()

  val solver = new UnionFindSolver[TipType]

  /**
    * @inheritdoc
    */
  def analyze(): TypeData = {

    // generate the constraints by traversing the AST and solve them on-the-fly
    visit(program, null)

    var ret: TypeData = Map()

    // close the terms and create the TypeData
    new DepthFirstAstVisitor[Null] {
      val sol = solver.solution()
      visit(program, null)

      // extract the type for each identifier declaration and each non-identifier expression
      override def visit(node: AstNode, arg: Null): Unit = {
        node match {
          case _: AIdentifier =>
          case _: ADeclaration | _: AExpr =>
            ret += node -> Some(TipTypeOps.close(TipVar(node), sol).asInstanceOf[TipType])
          case _ =>
        }
        visitChildren(node, null)
      }
    }

    log.info(s"Inferred types are:\n${ret.map { case (k, v) => s"  [[$k]] = ${v.get}" }.mkString("\n")}")
    ret
  }

  /**
    * Generates the constraints for the given sub-AST.
    * @param node the node for which it generates the constraints
    * @param arg unused for this visitor
    */
  override def visit(node: AstNode, arg: Null): Unit = {
    log.verb(s"Visiting ${node.getClass.getSimpleName} at ${node.loc}")
    node match {
      case _: ANumber =>
        unify(node, TipInt())
      case _: AInput =>
        unify(node, TipInt())
      case iff: AIfStmt =>
        unify(iff.guard, TipInt())
      case out: AOutputStmt =>
        unify(node, TipInt())
      case whl: AWhileStmt =>
        unify(whl.guard, TipInt())
      case ass: AAssignStmt =>
        ass.left match {
          case aIdentifier: AIdentifier =>
            unify(aIdentifier, ass.right)
          case aDerefOp: AUnaryOp[DerefOp.type] =>
            unify(aDerefOp.target, TipRef(ass.right))
        }
      case bin: ABinaryOp =>
        bin.operator match {
          case Eqq =>
            unify(bin.left, bin.right)
            unify(bin, TipInt())
          case _ =>
            unify(bin.left, TipInt())
            unify(bin.right, TipInt())
            unify(node, TipInt())
        }
      case un: AUnaryOp[_] =>
        un.operator match {
          case RefOp =>
            unify(node, TipRef(un.target))
          case DerefOp =>
            unify(un.target, TipRef(un))
        }
      case _: AAlloc =>
        unify(node, TipRef(TipTypeOps.makeAlpha(node)))
      case _: ANull =>
        unify(node, TipRef(TipTypeOps.makeAlpha(node)))
      case fun: AFunDeclaration =>
        unify(fun, TipFunction(fun.args.map(TipVar), fun.stmts.ret.value))
      case call: ACallFuncExpr =>
        unify(call.targetFun, TipFunction(call.args.map(TipType.ast2typevar), node))
      case _ =>
    }
    visitChildren(node, null)
  }
  private def unify(t1: Term[TipType], t2: Term[TipType]): Unit = {
    log.verb(s"Generating constraint $t1 = $t2")
    solver.unify(t1, t2)
  }
}
