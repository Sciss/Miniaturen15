lazy val baseName         = "Miniaturen15"
lazy val baseNameL        = baseName.toLowerCase
lazy val projectVersion   = "0.1.0"

lazy val commonSettings = Seq(
  version             := projectVersion,
  organization        := "de.sciss",
  description         := "Series of music pieces",
  homepage            := Some(url(s"https://github.com/Sciss/$baseName")),
  scalaVersion        := "2.11.7",
  licenses            := Seq(cc_by_nc_nd),
  scalacOptions      ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture"),
  resolvers += Resolver.typesafeRepo("releases"),
  libraryDependencies ++= Seq(
    "de.sciss"        %% "fileutil"           % "1.1.1",
    "de.sciss"        %% "numbers"            % "0.1.1",
    "de.sciss"        %% "processor"          % "0.4.0",
    "com.mortennobel" % "java-image-scaling"  % "0.8.6",  // includes jh filters
    "de.sciss"        %% "audiowidgets-swing" % "1.9.1",
    "de.sciss"        %% "desktop"            % "0.7.1",
    "de.sciss"        %% "guiflitz"           % "0.5.0",
    "de.sciss"        %% "play-json-sealed"   % "0.2.0",
    "de.sciss"        %% "scissdsp"           % "1.2.2",
    "de.sciss"        %% "kollflitz"          % "0.2.0"
  )
)

lazy val cc_by_nc_nd = "CC BY-NC-ND 4.0" -> url("http://creativecommons.org/licenses/by-nc-nd/4.0/legalcode")

lazy val root = Project(id = baseNameL, base = file("."))
  .aggregate(common, lyapunov, collateral, trunks)
  .dependsOn(common, lyapunov, collateral, trunks)
  .settings(commonSettings)
  .settings(
    publishArtifact in (Compile, packageBin) := false, // there are no binaries
    publishArtifact in (Compile, packageDoc) := false, // there are no javadocs
    publishArtifact in (Compile, packageSrc) := false  // there are no sources
  )

lazy val common = Project(id = s"$baseNameL-common", base = file("common"))
  .settings(commonSettings)

// ---- 1 ----

lazy val lyapunov = Project(id = s"$baseNameL-lyapunov", base = file("lyapunov"))
  .dependsOn(common)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "de.sciss" %  "intensitypalette"   % "1.0.0"
    )
  )

// ---- 2 ----

lazy val collateral = Project(id = s"$baseNameL-collateral", base = file("collateral"))
  .dependsOn(common)
  .settings(commonSettings)

// ---- 3 ----

lazy val trunks = Project(id = s"$baseNameL-trunks", base = file("trunks"))
  .dependsOn(common)
  .settings(commonSettings)

// ---- 4 ----

lazy val text = Project(id = s"$baseNameL-text", base = file("text"))
  .dependsOn(common)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "de.sciss" % "prefuse-core" % "1.0.0"
    )
  )

// ---- 5 ----

// ?
