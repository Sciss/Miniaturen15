lazy val baseName         = "Miniaturen15"
lazy val baseNameL        = baseName.toLowerCase
lazy val projectVersion   = "0.1.0-SNAPSHOT"

lazy val commonSettings = Seq(
  version             := projectVersion,
  organization        := "de.sciss",
  description         := "Series of music pieces",
  homepage            := Some(url(s"https://github.com/Sciss/$baseName")),
  scalaVersion        := "2.11.7",
  licenses            := Seq(cc_by_nc_nd),
  scalacOptions      ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture")
)

lazy val cc_by_nc_nd = "CC BY-NC-ND 4.0" -> url("http://creativecommons.org/licenses/by-nc-nd/4.0/legalcode")

lazy val root = Project(id = baseNameL, base = file("."))
  .aggregate(common, lyapunov)
  .dependsOn(common, lyapunov)
  .settings(commonSettings)
  .settings(
    publishArtifact in (Compile, packageBin) := false, // there are no binaries
    publishArtifact in (Compile, packageDoc) := false, // there are no javadocs
    publishArtifact in (Compile, packageSrc) := false  // there are no sources
  )

lazy val common = Project(id = s"$baseNameL-common", base = file("common"))
  .settings(commonSettings)

lazy val lyapunov = Project(id = s"$baseNameL-lyapunov", base = file("lyapunov"))
  .dependsOn(common)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "de.sciss" %% "numbers"            % "0.1.1",
      "de.sciss" %  "intensitypalette"   % "1.0.0",
      "de.sciss" %% "processor"          % "0.4.0",
      "de.sciss" %% "fileutil"           % "1.1.1",
      "de.sciss" %% "scissdsp"           % "1.2.2",
      "de.sciss" %% "audiowidgets-swing" % "1.9.1",
      "de.sciss" %% "guiflitz"           % "0.5.0",
      "de.sciss" %% "desktop"            % "0.7.1"
    )
  )
