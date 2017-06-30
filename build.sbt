lazy val baseName                = "Miniaturen15"
lazy val baseNameL               = baseName.toLowerCase
lazy val projectVersion          = "0.2.0-SNAPSHOT"

lazy val fileUtilVersion         = "1.1.2"
lazy val numbersVersion          = "0.1.3"
lazy val processorVersion        = "0.4.1"
lazy val javaImageScalingVersion = "0.8.6"
lazy val audioWidgetsVersion     = "1.10.3"
lazy val desktopVersion          = "0.7.5"
lazy val guiFlitzVersion         = "0.5.1"
lazy val playJSONVersion         = "0.4.1"
lazy val scissDSPVersion         = "1.2.3"
lazy val kollFlitzVersion        = "0.2.1"
lazy val intensityPaletteVersion = "1.0.0"
lazy val prefuseVersion          = "1.0.0"

lazy val commonSettings = Seq(
  version             := projectVersion,
  organization        := "de.sciss",
  description         := "Series of music pieces",
  homepage            := Some(url(s"https://github.com/Sciss/$baseName")),
  scalaVersion        := "2.12.2",
  licenses            := Seq(cc_by_nc_nd),
  scalacOptions      ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture"),
  resolvers += Resolver.typesafeRepo("releases"),
  libraryDependencies ++= Seq(
    "de.sciss"        %% "fileutil"           % fileUtilVersion,
    "de.sciss"        %% "numbers"            % numbersVersion,
    "de.sciss"        %% "processor"          % processorVersion,
    "com.mortennobel" % "java-image-scaling"  % javaImageScalingVersion,  // includes jh filters
    "de.sciss"        %% "audiowidgets-swing" % audioWidgetsVersion,
    "de.sciss"        %% "desktop"            % desktopVersion,
    "de.sciss"        %% "guiflitz"           % guiFlitzVersion,
    "de.sciss"        %% "play-json-sealed"   % playJSONVersion,
    "de.sciss"        %% "scissdsp"           % scissDSPVersion,
    "de.sciss"        %% "kollflitz"          % kollFlitzVersion
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
      "de.sciss" % "intensitypalette" % intensityPaletteVersion
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
      "de.sciss" % "prefuse-core" % prefuseVersion
    )
  )

// ---- 5 ----

// ?
