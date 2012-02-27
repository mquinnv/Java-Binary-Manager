project {
    modelVersion '4.0.0'
    parent ('org.truegods:parent:1.0-SNAPSHOT', relativePath:'../')
    groupId 'org.truegods'
    artifactId 'binary'
    version '1.0-SNAPSHOT'
    name 'Binary Manager'
    description 'TrueGods Binary Manager'
    dependencies {
	    dependency('joda-time:joda-time:1.6.2')
      dependency('org.hyperic:sigar:1.6.4.129')
      dependency('com.intellij:forms_rt:7.0.3')
      dependency('org.hyperic:sigar-dist:1.6.4.129:runtime', type:'zip')
    }
    build {
        plugins {
            plugin { 
              groupId 'org.codehaus.mojo'
              artifactId 'ideauidesigner-maven-plugin'
              version '1.0-beta-1'
              executions {
                execution {
                  goals {
                    goal 'javac2'
                  }
                }
              }
              configuration {
                debug true
                failOnError true
                fork true
              }
            }
            plugin {
                artifactId 'maven-surefire-plugin'
                version '2.8.1'
                configuration {
                    forkMode 'pertest'
                    argLine '-javaagent:../../agent/target/agent-6.0-SNAPSHOT.jar'
                    workingDirectory '${basedir}/target'
                }
            }
            plugin {
                artifactId 'maven-assembly-plugin'
                configuration {
                    archive {
                        manifest {
                            mainClass 'org.truegods.binary.BinaryManager'
                        }
                    }
                    descriptorRefs {
                        descriptorRef 'jar-with-dependencies'
                    }
                }
            }
        }
    }
}
