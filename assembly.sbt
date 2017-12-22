import sbtassembly.PathList

test in assembly := {}

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case PathList("codegen.json") => MergeStrategy.discard
  case _ => MergeStrategy.first
}
