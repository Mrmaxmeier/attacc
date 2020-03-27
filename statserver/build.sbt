val Http4sVersion   = "0.21.2"
val CirceVersion    = "0.12.3"
val ZIOVersion      = "1.0.0-RC16"
val SilencerVersion = "1.4.4"
val RedisVersion    = "3.20"

lazy val root = (project in file("."))
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
      "-Ywarn-unused:_"
    ) ++ (if (isSnapshot.value) Seq.empty
          else
            Seq(
              "-opt:l:inline"
            )),
    libraryDependencies ++= Seq(
      "org.http4s"            %% "http4s-blaze-server" % Http4sVersion,
      "org.http4s"            %% "http4s-circe"        % Http4sVersion,
      "org.http4s"            %% "http4s-dsl"          % Http4sVersion,
      "io.circe"              %% "circe-core"          % CirceVersion,
      "io.circe"              %% "circe-generic"       % CirceVersion,
      "dev.zio"               %% "zio"                 % ZIOVersion,
      "net.debasishg"         %% "redisclient"         % RedisVersion,
      "dev.zio"               %% "zio-interop-cats"    % "2.0.0.0-RC7",
      "dev.zio"               %% "zio-macros-core"     % "0.5.0",
      "org.flywaydb"          % "flyway-core"          % "5.2.4",
      "org.slf4j"             % "slf4j-log4j12"        % "1.7.26",
      "com.github.pureconfig" %% "pureconfig"          % "0.12.1",
      "com.lihaoyi"           %% "sourcecode"          % "0.1.7",
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
