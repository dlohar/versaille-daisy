name := "Daisy"

version := "0.1-minimal"

organization := "org.mpi-sws.ava"

scalaVersion := "2.11.11"

scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-feature",
    "-Xlint:_,-adapted-args")

libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-compiler" % "2.11.11"
)

envVars := Map("LC_NUMERIC" -> "en_US.UTF-8")

Keys.fork in run := true

javaOptions in run ++= Seq("-Xms1G", "-Xmx2G", "-Xss1G")

logBuffered := false

lazy val scriptFile = file(".") / "daisy"

clean := {
  clean.value
  if(scriptFile.exists && scriptFile.isFile) {
    scriptFile.delete
  }
}

lazy val script = taskKey[Unit]("Generate the daisy Bash script")

script := {
  val s = streams.value
  try {
    val cps = (dependencyClasspath in Compile).value
    val out = (classDirectory      in Compile).value
    val res = (resourceDirectory   in Compile).value
    val f = scriptFile
    if(f.exists) {
      s.log.info("Regenerating '"+f.getName+"' script ...")
      f.delete
    } else {
      s.log.info("Generating '"+f.getName+"' script ...")
    }
    val paths = (res.getAbsolutePath +: out.getAbsolutePath +: cps.map(_.data.absolutePath)).mkString(System.getProperty("path.separator"))
    IO.write(f, s"""|#!/bin/bash --posix
                    |
                    |SCALACLASSPATH="$paths"
                    |
                    |TMP=$$LC_NUMERIC
                    |LC_NUMERIC=en_US.UTF-8
                    |
                    |java -Xmx2G -Xms1G -Xss1G -classpath "$${SCALACLASSPATH}" -Dscala.usejavacp=false scala.tools.nsc.MainGenericRunner -classpath "$${SCALACLASSPATH}" daisy.Main "$$@" 2>&1 | tee -i last.log
                    |STATUS=$${PIPESTATUS[0]}
                    |
                    |LC_NUMERIC=$$TMP
                    |exit $$STATUS
                    |""".stripMargin)
    f.setExecutable(true)
  } catch {
    case e: Throwable =>
      s.log.error("There was an error while generating the script file: " + e.getLocalizedMessage)
  }
}
