// Settings for sbt assembly

test in assembly := {}

assemblyMergeStrategy in assembly := {
 case PathList("META-INF", _ @ _*) => MergeStrategy.discard
 case _ => MergeStrategy.first
}
