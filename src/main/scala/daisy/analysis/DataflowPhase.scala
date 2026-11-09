// Copyright 2017 MPI-SWS, Saarbruecken, Germany

package daisy
package analysis

import lang.Trees._
import lang.Identifiers._
import tools._
import FinitePrecision._

/**
  Computes and stores intermediate ranges and absolute roundoff errors.

  Ranges:  interval or affine arithmetic (--rangeMethod)
  Errors:  interval or affine arithmetic (--errorMethod)

  Prerequisites:
    - SpecsProcessingPhase
 */
object DataflowPhase extends DaisyPhase with RoundoffEvaluators {
  override val name = "Dataflow Error"
  override val shortName = "analysis"
  override val description = "Computes ranges and absolute errors via dataflow analysis"

  override val definedOptions: Set[CmdLineOption[Any]] = Set(
    StringChoiceOption(
      "errorMethod",
      Set("affine", "interval"),
      "affine",
      "Method for error analysis")
  )

  implicit val debugSection = DebugSectionAnalysis

  var rangeMethod = ""
  var errorMethod = ""
  var trackRoundoffErrs = true

  override def runPhase(ctx: Context, prg: Program): (Context, Program) = {
    rangeMethod = ctx.option[String]("rangeMethod")
    errorMethod = ctx.option[String]("errorMethod")
    trackRoundoffErrs = !ctx.hasFlag("noRoundoff")

    val mixedPrecision = ctx.option[Option[String]]("mixed-precision").isDefined
    val uniformPrecision = ctx.option[Precision]("precision")

    ctx.reporter.info(s"using $rangeMethod for ranges, $errorMethod for errors")

    var uniformPrecisions = Map[Identifier, Precision]()

    // returns (abs error, result range, interm. errors, interm. ranges)
    val res: Map[Identifier, (Rational, Interval, Map[(Expr, PathCond), Rational], Map[(Expr, PathCond), Interval])] =
      analyzeConsideredFunctions(ctx, prg){ fnc =>

      ctx.reporter.info("analyzing fnc: " + fnc.id)
      if (!mixedPrecision) {
        ctx.reporter.info(s"error analysis for uniform $uniformPrecision precision")
      }

      val inputValMap: Map[Identifier, Interval] = ctx.specInputRanges(fnc.id)
      val inputErrorMap: Map[Identifier, Rational] = ctx.specInputErrors(fnc.id)
      val precisionMap: Map[Identifier, Precision] = ctx.specInputPrecisions(fnc.id)
      uniformPrecisions = uniformPrecisions + (fnc.id -> uniformPrecision) // so that this info is available in codegen

      computeRoundoff(inputValMap, inputErrorMap, precisionMap, fnc.body.get, uniformPrecision)
    }

    (ctx.copy(uniformPrecisions = uniformPrecisions,
      resultAbsoluteErrors = res.mapValues(_._1),
      resultRealRanges = res.mapValues(_._2),
      intermediateAbsErrors = res.mapValues(_._3),
      intermediateRanges = res.mapValues(_._4)), prg)
  }

  def computeRange(inputValMap: Map[Identifier, Interval], expr: Expr):
  (Interval, Map[(Expr, PathCond), Interval]) = {

    (rangeMethod: @unchecked) match {
      case "interval" =>
        evalRange[Interval](expr, inputValMap, Interval.apply)

      case "affine" =>
        val (rng, intrmdRange) = evalRange[AffineForm](expr,
          inputValMap.mapValues(AffineForm(_)), AffineForm.apply)

        // The affine ReLU relaxation keeps correlations, but its own bounds can go below 0.
        // ReLU outputs (and variables that just copy them) are >= 0, so intersect their
        // ranges with [0, +inf). This is sound and only affects the reported ranges,
        // the affine forms used for propagation are unchanged.
        val nonNeg = nonNegativeIds(expr)
        def clamp(i: Interval): Interval =
          Interval(Rational.max(i.xlo, Rational.zero), Rational.max(i.xhi, Rational.zero))
        def isNonNeg(e: Expr): Boolean = e match {
          case Relu(_) => true
          case Variable(id) => nonNeg.contains(id)
          case _ => false
        }

        val ranges = intrmdRange.map({ case (key @ (e, _), af) =>
          key -> (if (isNonNeg(e)) clamp(af.toInterval) else af.toInterval)
        })
        val resRange = if (isNonNeg(lang.TreeOps.getLastExpression(expr))) clamp(rng.toInterval) else rng.toInterval
        (resRange, ranges)
    }
  }

  // ids bound to a ReLU, or to a variable that is (transitively) bound to a ReLU
  private def nonNegativeIds(expr: Expr): Set[Identifier] = {
    def collect(e: Expr, acc: Set[Identifier]): Set[Identifier] = e match {
      case Let(id, Relu(_), body) => collect(body, acc + id)
      case Let(id, Variable(v), body) if acc.contains(v) => collect(body, acc + id)
      case Let(_, _, body) => collect(body, acc)
      case _ => acc
    }
    collect(expr, Set())
  }

  def computeErrors(intermediateRanges: Map[(Expr, PathCond), Interval], inputErrorMap: Map[Identifier, Rational],
    precisionMap: Map[Identifier, Precision], expr: Expr, constPrecision: Precision):
  (Rational, Map[(Expr, PathCond), Rational]) = {

    (errorMethod: @unchecked) match {
      case "interval" =>
        val (resRoundoff, allErrors) = evalRoundoff[Interval](expr, intermediateRanges,
          precisionMap,
          inputErrorMap.mapValues(Interval.+/-),
          zeroError = Interval.zero,
          fromError = Interval.+/-,
          interval2T = Interval.apply,
          constantsPrecision = constPrecision,
          trackRoundoffErrs)

        (Interval.maxAbs(resRoundoff.toInterval), allErrors.mapValues(Interval.maxAbs))

      case "affine" =>
        val (resRoundoff, allErrors) = evalRoundoff[AffineForm](expr, intermediateRanges,
          precisionMap,
          inputErrorMap.mapValues(AffineForm.+/-),
          zeroError = AffineForm.zero,
          fromError = AffineForm.+/-,
          interval2T = AffineForm.apply,
          constantsPrecision = constPrecision,
          trackRoundoffErrs)

        (Interval.maxAbs(resRoundoff.toInterval), allErrors.mapValues(e => Interval.maxAbs(e.toInterval)))
    }
  }

  def computeRoundoff(inputValMap: Map[Identifier, Interval], inputErrorMap: Map[Identifier, Rational],
    precisionMap: Map[Identifier, Precision], expr: Expr, constPrecision: Precision):
    (Rational, Interval, Map[(Expr, PathCond), Rational], Map[(Expr, PathCond), Interval]) = {

    val (resRange, intermediateRanges) = computeRange(inputValMap, expr)

    val (resError, intermediateErrors) = computeErrors(intermediateRanges, inputErrorMap, precisionMap, expr,
      constPrecision)

    (resError, resRange, intermediateErrors, intermediateRanges)
  }
}
