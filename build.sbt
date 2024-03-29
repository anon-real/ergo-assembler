import sbt.Attributed

name := "ergo-assembler"

version := "1.1"

lazy val `distributedsigsclient` = (project in file(".")).enablePlugins(PlayScala)

//resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
//
//resolvers += "Akka Snapshot Repository" at "https://repo.akka.io/snapshots/"

resolvers ++= Seq("Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "SonaType" at "https://oss.sonatype.org/content/groups/public",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots")

scalaVersion := "2.12.2"

libraryDependencies ++= Seq(ehcache, ws, specs2 % Test, guice)
libraryDependencies ++= Seq(
  "org.scalaj" %% "scalaj-http" % "2.4.2",
  "com.h2database" % "h2" % "1.4.200",
  "com.typesafe.play" %% "play-slick" % "4.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "4.0.0",
  "org.scorexfoundation" %% "scrypto" % "2.1.10",
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test,
  "com.github.tomakehurst" % "wiremock-standalone" % "2.27.1" % Test
)
libraryDependencies += "org.ergoplatform" %% "ergo-appkit" % "5bc5571b-SNAPSHOT"

val circeVersion = "0.12.3"
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)
libraryDependencies += "com.dripower" %% "play-circe" % "2712.0"

assemblyJarName in assembly := s"${name.value}-${version.value}.jar"

unmanagedResourceDirectories in Test <+= baseDirectory(_ / "target/web/public/test")

assemblyMergeStrategy in assembly := {
  case PathList("reference.conf") => MergeStrategy.concat
  case manifest if manifest.contains("MANIFEST.MF") => MergeStrategy.discard
  case manifest if manifest.contains("module-info.class") => MergeStrategy.discard
  case referenceOverrides if referenceOverrides.contains("reference-overrides.conf") => MergeStrategy.concat
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

fullClasspath in assembly += Attributed.blank(PlayKeys.playPackageAssets.value)

javaOptions in Test += "-Dconfig.file=conf/test.conf"

javaOptions in Universal ++= Seq(
  "-Dpidfile.path=/dev/null"
)

