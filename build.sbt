
name := "ferload-client"
organization := "ca.ferlab"

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

scalaVersion := "2.13.9"

// some TUs write in a common config file
Test / parallelExecution := false

libraryDependencies += "info.picocli" % "picocli" % "4.6.1"
libraryDependencies += "com.typesafe" % "config" % "1.4.1"
libraryDependencies += "org.apache.httpcomponents" % "httpclient" % "4.5.13"
libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.12.0"
libraryDependencies += "org.apache.commons" % "commons-csv" % "1.8"
libraryDependencies += "commons-io" % "commons-io" % "2.11.0"
libraryDependencies += "org.json" % "json" % "20210307"
libraryDependencies += "com.auth0" % "java-jwt" % "3.18.2"
libraryDependencies += "com.amazonaws" % "aws-java-sdk-s3" % "1.12.261"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.7" % "test"

import sbtassembly.AssemblyPlugin.defaultUniversalScript
//import sbtassembly.AssemblyPlugin.defaultShellScript

assembly / mainClass := Some("ca.ferlab.ferload.client.Main")
assembly / test := {}
assembly / assemblyJarName := s"${name.value}"
assembly / assemblyOption := (assembly / assemblyOption).value.copy(prependShellScript = Some(defaultUniversalScript(shebang = false)))
// assembly / assemblyOption := (assembly / assemblyOption).value.copy(prependShellScript = Some(defaultShellScript))

assembly / assemblyMergeStrategy  := {
  case PathList("module-info.class") => MergeStrategy.discard
  case x if x.endsWith("/module-info.class") => MergeStrategy.discard
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}