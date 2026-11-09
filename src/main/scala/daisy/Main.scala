// Original work Copyright 2009-2016 EPFL, Lausanne
// Modified work Copyright 2017 MPI-SWS, Saarbruecken, Germany
//
// Minimal Daisy: frontend, NN dead-neuron pruning, NN unrolling,
// range/error analysis (interval and affine arithmetic) and code generation.

package daisy

import java.io.File

import daisy.tools.FinitePrecision._

import lang.Trees.Program

object Main {

  val globalOptions: Set[CmdLineOption[Any]] = Set(
    FlagOption(
      "help",
      "Show this message"),
    FlagOption(
      "silent",
      "Don't print anything, except for results."),
    MultiChoiceOption(
      "debug",
      DebugSections.all.map(s => s.name -> s).toMap,
      "For which sections to print debug info"),
    FlagOption(
      "codegen",
      "Generate code (as opposed to just doing analysis)"),
    FlagOption(
      "three-address",
      "Transform code to three-address code prior to analysis."),
    MultiStringOption(
      "functions",
      List("f1", "f2"),
      "Which functions to consider"),
    ChoiceOption(
      "precision",
      Map("Float16" -> Float16, "Float32" -> Float32, "Float64" -> Float64,
        "Quad" -> DoubleDouble, "QuadDouble" -> QuadDouble) ++
        (1 to 64).map(x => ("Fixed" + x -> FixedPrecision(x))),
      "Float64",
      "(Uniform) precision to use"),
    StringChoiceOption(
      "rangeMethod",
      Set("affine", "interval"),
      "interval",
      "Method for range analysis"),
    FlagOption(
      "noRoundoff",
      "Do not track roundoff errors"),
    FlagOption(
      "noInitialErrors",
      "Do not track initial errors specified by user"),
    StringOption(
      "mixed-precision",
      """File with type assignment for all variables.
        The format is the following:
        function_name = {
          variable_name_1: prec_1
          variable_name_2: prec_2
          ... }
        The file can also only give a partial precision map."""),
    FlagOption("mixed-tuning", "Perform mixed-precision tuning"),
    FlagOption("qnn", "input is a neural network"),
    FlagOption("qnn-prune-dead", "prune dead neurons (all-zero weight row and zero bias)"),
    FlagOption("qnn-skip-zeros", "remove multiplications by zero weights and additions of zero biases (before analysis)")
  )

  lazy val allPhases: Set[DaisyPhase] = Set(
    frontend.ExtractionPhase,
    transform.NNPruneDeadPhase,
    transform.NNUnrollPhase,
    transform.NNSkipZerosPhase,
    analysis.SpecsProcessingPhase,
    transform.TACTransformerPhase,
    transform.ConstantTransformerPhase,
    analysis.DataflowPhase,
    opt.MixedPrecisionOptimizationPhase,
    analysis.AbsErrorPhase,
    backend.InfoPhase,
    backend.CodeGenerationPhase
  )

  // all available options from all phases
  private val allOptions: Set[CmdLineOption[Any]] =
    globalOptions ++ allPhases.flatMap(_.definedOptions)

  var ctx: Context = null

  // lowest bit width considered by mixed-precision tuning if --min-bits is not given
  val defaultMinBits = 30

  // checks option combinations before anything runs; returns an error message if invalid
  private def checkOptions(ctx: Context): Option[String] = {
    if (!ctx.hasFlag("mixed-tuning")) return None
    (ctx.option[Precision]("precision"), ctx.option[Option[String]]("min-bits")) match {
      case (FixedPrecision(max), minOpt) =>
        scala.util.Try(minOpt.map(_.toInt).getOrElse(defaultMinBits)).toOption match {
          case None =>
            Some(s"--min-bits=${minOpt.get} is not a valid bit width (use a number, e.g. --min-bits=8)")
          case Some(min) if min < 1 =>
            Some(s"--min-bits=$min is not a valid bit width (must be at least 1)")
          case Some(min) if min > max =>
            val given = if (minOpt.isDefined) s"--min-bits=$min" else s"the default --min-bits=$defaultMinBits"
            ctx.reporter.warning(s"min cannot be bigger than max: $given is bigger than --precision=Fixed$max; " +
              s"using the biggest precision Fixed$max only")
            None
          case _ => None
        }
      case (_, Some(_)) =>
        Some("--min-bits can only be used with a fixed-point --precision (FixedN)")
      case _ => None
    }
  }

  def interfaceMain(args: Array[String]): Option[Context] = {
    processOptions(args.toList) match {
      case Some(new_ctx) =>
        ctx = new_ctx
        checkOptions(ctx).foreach { msg =>
          ctx.reporter.error(msg)
          sys.exit(1)
        }
        ctx.timers.total.start
        val pipeline = computePipeline(ctx)
        ctx.reporter.info("\n************ Starting Daisy ************")
        // all of these stop the pipeline: no result and no code, exit with an error code
        def stop(msg: String): Nothing = {
          ctx.reporter.error(msg + " No code is generated.")
          sys.exit(1)
        }
        try {
          pipeline.run(ctx, Program(null, Nil))
        } catch {
          case _: DaisyFatalError =>  // message already printed
            sys.exit(1)
          case tools.DivisionByZeroException(msg) => stop(msg)
          case tools.DenormalRangeException(msg) => stop(msg)
          case tools.OverflowException(msg) =>
            stop(msg + s" The precision ${ctx.option[Precision]("precision")} has too few bits for the ranges;" +
              " use a larger --precision.")
          case e: java.lang.UnsatisfiedLinkError => stop("A library could not be loaded: " + e)
          case tools.NegativeSqrtException(msg) => stop(msg)
          case tools.ArcOutOfBoundsException(msg) => stop(msg)
        }
        ctx.timers.get("total").stop
        ctx.reporter.info("time: \n" + ctx.timers.toString)
        Option(ctx)
      case None =>
        None
    }
  }

  def main(args: Array[String]): Unit = {
    interfaceMain(args)
  }

  private def computePipeline(ctx: Context): Pipeline[Program, Program] = {

    var pipeline: Pipeline[Program, Program] = frontend.ExtractionPhase

    if (ctx.hasFlag("qnn")) {
      if (ctx.hasFlag("qnn-prune-dead")) {
        pipeline >>= transform.NNPruneDeadPhase
      }
      pipeline >>= transform.NNUnrollPhase
      if (ctx.hasFlag("qnn-skip-zeros")) {
        pipeline >>= transform.NNSkipZerosPhase
      }
    }

    pipeline >>= analysis.SpecsProcessingPhase

    if (ctx.hasFlag("mixed-tuning")) {
      // mixed-precision tuning (always generates code)
      pipeline >>= transform.TACTransformerPhase >>
        transform.ConstantTransformerPhase >>
        analysis.DataflowPhase >>
        opt.MixedPrecisionOptimizationPhase

      ctx.option[Precision]("precision") match {
        case FixedPrecision(_) =>
          pipeline >>=
            analysis.AbsErrorPhase >> // needed to get intermediate ranges for fixed-points
            backend.InfoPhase >>
            backend.CodeGenerationPhase
        case _ =>
          pipeline >>=
            backend.InfoPhase >>
            backend.CodeGenerationPhase
      }

    } else {
      // uniform precision
      if (ctx.fixedPoint && ctx.hasFlag("apfixed")) {
        pipeline >>= transform.ConstantTransformerPhase
      }

      if (ctx.hasFlag("three-address") || (ctx.fixedPoint && ctx.hasFlag("codegen"))) {
        pipeline >>= transform.TACTransformerPhase
      }

      pipeline >>= analysis.DataflowPhase
      pipeline >>= backend.InfoPhase

      if (ctx.hasFlag("codegen")) {
        pipeline >>= backend.CodeGenerationPhase
      }
    }

    pipeline
  }

  private def showHelp(reporter: Reporter): Unit = {
    reporter.info("usage: [--help] [--debug=<N>] [..] <files>")
    reporter.info("")
    for (opt <- Main.globalOptions.toSeq.sortBy(_.name)) {
      reporter.info(opt.helpLine)
    }
    reporter.info("")
    reporter.info("Additional options, by component:")

    for (c <- Main.allPhases.toSeq.sortBy(_.name) if c.definedOptions.nonEmpty) {
      reporter.info("")
      reporter.info(s"${c.name} Phase")
      for(opt <- c.definedOptions.toSeq.sortBy(_.name)) {
        reporter.info(opt.helpLine)
      }
    }
  }


  def processOptions(args: List[String]): Option[Context] = {
    val initReporter = new DefaultReporter(Set(), false)

    if (args.isEmpty || args.contains("--help")) {
      showHelp(initReporter)
      return None
    }

    val argsMap: Map[String, String] =
      args.filter(_.startsWith("--")).map(_.drop(2).split("=", 2).toList match {
      case List(name, value) => name -> value
      case List(name) => name -> "yes"
    }).toMap


    argsMap.keySet.diff(allOptions.map(_.name)).foreach {
      name => initReporter.warning(s"Unknown option: $name")
    }

    // go through all options and check if they are defined, else use default
    val opts: Map[String, Any] = allOptions.map({
      case FlagOption(name, _) => name -> argsMap.get(name).isDefined

      case StringOption(name, _) => name -> argsMap.get(name)

      case MultiStringOption(name, _, _) =>
        name -> argsMap.get(name).map(_.stripPrefix("[").stripPrefix("]").split(":").toList).getOrElse(Nil)

      case NumOption(name, default, _) => argsMap.get(name) match {
          case None => name -> default
          case Some(s) => try {
            name -> s.toLong
          } catch {
            case e: NumberFormatException =>
              initReporter.warning(s"Can't parse argument for option $name, using default")
              name -> default
          }
        }

      case ChoiceOption(name, choices, default, _) => argsMap.get(name) match {
        case Some(s) if choices.keySet.contains(s) => name -> choices(s)
        case Some(s) =>
          initReporter.warning(s"Unknown choice value for $name: $s. Options: " +
            s"${choices.keySet.toSeq.sorted.mkString(", ")}. Using default $default")
          name -> choices(default)
        case None => name -> choices(default)
      }

      case MultiChoiceOption(name, choices, _) => argsMap.get(name) match {
        case Some("all") | Some("[all]") =>
          name -> choices.values.toList
        case Some(ss) => name -> ss.stripPrefix("[").stripSuffix("]").split(":").toList.filter {
          case "all" =>
            initReporter.warning(s"'all' in list for $name, ignoring"); false
          case s if !choices.keySet.contains(s) =>
            initReporter.warning(s"Unknown choice value for $name: $s. Options: ${choices.keySet.toSeq.sorted.mkString(", ")}"); false
          case _ => true
        }.map(choices(_))
        case None => name -> Nil
      }
    }).toMap

    val inputFile: String = args.filterNot(_.startsWith("-")) match {
      case Seq() => initReporter.fatalError("No input file")
      case Seq(f) if new File(f).exists => f
      case Seq(f) => initReporter.fatalError(s"File $f does not exist")
      case fs => initReporter.fatalError("More than one input file: " + fs.mkString(", "))
    }

    Option(Context(
      initReport = initReporter.report,
      file = inputFile,
      options = opts
    ))
  }
}
