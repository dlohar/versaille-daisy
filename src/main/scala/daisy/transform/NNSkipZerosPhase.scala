package daisy
package transform

import lang.Trees._
import lang.Identifiers._
import tools.Rational

/**
  Removes terms that are always zero from an unrolled neural network:
    - products `w * x` where the weight w is the constant 0
    - additions `v + b` where the bias b is the constant 0 (or where v became 0)
  and removes the zero constants that are no longer used.

  The network computes exactly the same values. Since the removed operations are
  no longer analysed, the error bound can only get tighter (in fixed-point arithmetic
  `0 * x` and `v + 0` are exact, so the bound is also valid for the loop version of
  the code, which still multiplies by the zeros stored in its weight arrays).

  Prerequisites:
    - NNUnrollPhase (runs on the unrolled program, before any analysis)
 */
object NNSkipZerosPhase extends DaisyPhase {
  override val name = "NNSkipZeros"
  override val shortName = "nn-skip-zeros"
  override val description = "Removes multiplications by zero weights and additions of zero biases"

  override def runPhase(ctx: Context, prg: Program): (Context, Program) = {

    val newDefs = transformConsideredFunctions(ctx, prg){ fnc =>
      fnc.body match {
        case None => fnc
        case Some(body0) =>
          droppedTerms = 0
          val simplified = walk(body0, Set(), Map())
          val used = lang.TreeOps.allVariablesOf(simplified)
          removedConsts = 0
          val newBody = removeUnusedZeros(simplified, used)
          ctx.reporter.info(s"${fnc.id}: removed $droppedTerms zero terms and $removedConsts zero constants")
          fnc.copy(body = Some(newBody))
      }
    }
    (ctx, Program(prg.id, newDefs))
  }

  // a zero literal with a string value (codegen prints literals by their string value)
  private def zeroLit: RealLiteral = RealLiteral(Rational.zero, "0.0")

  private var droppedTerms = 0
  private var removedConsts = 0

  private def isZero(e: Expr): Boolean = e match {
    case RealLiteral(r) => r == Rational.zero
    case _ => false
  }

  // replace variables that were removed as plain copies by the variable they copied
  private def rename(e: Expr, subst: Map[Identifier, Identifier]): Expr =
    if (subst.isEmpty) e
    else lang.TreeOps.postMap({
      case Variable(a) if subst.contains(a) => Some(Variable(subst(a)))
      case _ => None
    })(e)

  // go through the let-chain in order, tracking which variables are exactly 0.
  // If a binding becomes a plain copy (e.g. `layer1_bias_0 = layer1_dot_0` after dropping a
  // zero bias), it is removed and later uses refer to the copied variable directly
  // (copies are not supported by the mixed-precision tuning).
  private def walk(e: Expr, zeros: Set[Identifier], subst: Map[Identifier, Identifier]): Expr = e match {
    case Let(id, lit @ RealLiteral(r), body) if r == Rational.zero =>
      Let(id, lit, walk(body, zeros + id, subst))

    case Let(id, value @ (Plus(_, _) | Times(_, _)), body) =>
      simplify(rename(value, subst), zeros) match {
        case Variable(v) =>
          walk(body, zeros, subst + (id -> v))
        case newValue =>
          Let(id, newValue, walk(body, if (isZero(newValue)) zeros + id else zeros, subst))
      }

    case Let(id, value, body) =>
      Let(id, rename(value, subst), walk(body, zeros, subst))

    case _ => rename(e, subst)
  }

  // only applied to dot products (sums of products) and bias additions
  private def simplify(e: Expr, zeros: Set[Identifier]): Expr = e match {
    case Times(Variable(a), _) if zeros.contains(a) => droppedTerms += 1; zeroLit
    case Times(_, Variable(b)) if zeros.contains(b) => droppedTerms += 1; zeroLit
    case Variable(a) if zeros.contains(a) => zeroLit
    case Plus(lhs, rhs) =>
      (simplify(lhs, zeros), simplify(rhs, zeros)) match {
        case (l, r) if isZero(l) && isZero(r) => zeroLit
        case (l, r) if isZero(r) =>
          if (!rhs.isInstanceOf[Times]) droppedTerms += 1 // a zero bias / zero sum, not a counted product
          l
        case (l, r) if isZero(l) =>
          if (!lhs.isInstanceOf[Times] && !lhs.isInstanceOf[Plus]) droppedTerms += 1
          r
        case (l, r) => Plus(l, r)
      }
    case _ => e
  }

  // drop `let id = 0` bindings that are no longer referenced
  private def removeUnusedZeros(e: Expr, used: Set[Identifier]): Expr = e match {
    case Let(id, RealLiteral(r), body) if r == Rational.zero && !used.contains(id) =>
      removedConsts += 1
      removeUnusedZeros(body, used)
    case Let(id, value, body) =>
      Let(id, value, removeUnusedZeros(body, used))
    case _ => e
  }
}
