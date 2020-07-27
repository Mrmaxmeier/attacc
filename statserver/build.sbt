val Http4sVersion   = "1.0.0-M3"
val CirceVersion    = "0.13.0"
val ZIOVersion      = "1.0.0-RC21-2"
val SilencerVersion = "1.4.4"

lazy val root = (project in file("."))
  .enablePlugins(GraalVMNativeImagePlugin)
  .settings(
    organization := "ALLES",
    name := "attacc",
    scalaVersion := "2.13.1",
    scalacOptions := Seq(
      "-feature",
      "-deprecation",
      "-explaintypes",
      "-unchecked",
      "-encoding",
      "UTF-8",
      "-language:higherKinds",
      "-language:existentials",
      "-Xfatal-warnings",
      "-Xlint:-infer-any,_",
      "-Ywarn-value-discard",
      "-Ywarn-numeric-widen",
      "-Ywarn-extra-implicit",
      "-Ymacro-annotations"
    ) ++ (if (isSnapshot.value) Seq.empty
          else
            Seq("-opt:l:inline")),
    libraryDependencies ++= Seq(
      "org.http4s"            %% "http4s-blaze-server"  % Http4sVersion,
      "org.http4s"            %% "http4s-circe"         % Http4sVersion,
      "org.http4s"            %% "http4s-dsl"           % Http4sVersion,
      "io.circe"              %% "circe-core"           % CirceVersion,
      "io.circe"              %% "circe-generic"        % CirceVersion,
      "io.circe"              %% "circe-generic-extras" % CirceVersion,
      "io.circe"              %% "circe-parser"         % CirceVersion,
      "dev.zio"               %% "zio"                  % ZIOVersion,
      "dev.profunktor"        %% "redis4cats-effects"   % "0.9.6",
      "dev.zio"               %% "zio-logging-slf4j"    % "0.3.2",
      "dev.zio"               %% "zio-interop-cats"     % "2.1.4.0-RC17",
      "org.slf4j"             % "slf4j-log4j12"         % "1.7.26",
      "com.github.pureconfig" %% "pureconfig"           % "0.12.1",
      "com.lihaoyi"           %% "sourcecode"           % "0.1.7",
      "io.lettuce"            % "lettuce-core"          % "6.0.0.M1",
      ("com.github.ghik" % "silencer-lib" % SilencerVersion % "provided")
        .cross(CrossVersion.full),
      // plugins
      compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
      compilerPlugin(
        ("org.typelevel" % "kind-projector" % "0.11.0").cross(CrossVersion.full)
      ),
      compilerPlugin(
        ("com.github.ghik" % "silencer-plugin" % SilencerVersion)
          .cross(CrossVersion.full)
      )
    )
  )
