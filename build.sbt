name := "ferload-client"
organization := "ca.ferlab"
version := "0.1"

scalaVersion := "2.13.6"

// some TUs write in a common config file
Test / parallelExecution := false

libraryDependencies += "info.picocli" % "picocli" % "4.6.1"
libraryDependencies += "com.typesafe" % "config" % "1.4.1"
libraryDependencies += "org.keycloak" % "keycloak-authz-client" % "12.0.3"
libraryDependencies += "org.apache.httpcomponents" % "httpclient" % "4.5.13"
libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.12.0"
libraryDependencies += "org.json" % "json" % "20210307"
libraryDependencies += "com.amazonaws" % "aws-java-sdk-s3" % "1.12.14"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.7" % "test"

// import sbtassembly.AssemblyPlugin.defaultUniversalScript
// import sbtassembly.AssemblyPlugin.defaultShellScript

assembly / mainClass := Some("ca.ferlab.ferload.client.Main")
assembly / test := {}
assembly / assemblyJarName := s"${name.value}-${version.value}.jar"
// assembly / assemblyOption := (assembly / assemblyOption).value.copy(prependShellScript = Some(defaultUniversalScript(shebang = false)))
// assembly / assemblyOption := (assembly / assemblyOption).value.copy(prependShellScript = Some(defaultShellScript))

assembly / assemblyMergeStrategy  := {
  case PathList("module-info.class") => MergeStrategy.discard
  case x if x.endsWith("/module-info.class") => MergeStrategy.discard
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}