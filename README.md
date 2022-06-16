# Dependency Angel

## Disclaimer
**This application comes without guarantees of any kind.**

1. Anyone using this tool is responsible to ensure that
Dependency Angel tool's changes are appropriate.  
2. Dependency Angel may make decisions that could result
in incorrect behavior, including Runtime failures.
3. Dependency Angel is a destructive process, so users should 
ensure they have backups.
4. This is not a complete list. 

## Description
Dependency Angel is a tool that developers can use with their maven projects to help manage "Dependency Hell" with conflicting and transitive dependencies.

## Assumptions (Incomplete List)
1. Projects are either a single pom.xml file or a hierarchy of 2 levels.
2. If it's a hierarchy of 2 levels, versions are managed in the parent pom.
3. Semantic versioning is preferred.  Semantic versions are preferred over non-semantic versions.
4. If versions are not semantic, an algorithm is in place to resolve latest.  At some level, this is simply a string comparison, which may choose the wrong version.
5. This is not a complete list.

## Building
`mvn clean install`

The binary created will be a shaded jar: `target/DependencyAngel-x.y.z-SNAPSHOT.jar`


## Usage 
### Command Line:
`java -jar /path/to/DependencyAngel-*-SNAPSHOT.jar`

Setting up an alias to use Dependency Angel may be a good way to have it accessible, with standard options defaulted.

Ex: `alias angel='java -jar /path/to/DependencyAngel-*-SNAPSHOT.jar -b org.slf4j:slf4j-log4j12,org.slf4j:slf4j-jdk14,log4j:log4j,commons-logging:commons-logging,javax.activation:javax.activation-api,javax.servlet:javax.servlet-api,javax.validation:validation-api,javax.xml.bind:jaxb-api,javax.ws.rs:javax.ws.rs-api'`
which will default to handling some common banned dependencies.

### Parameters
* `-h`, `--help` Shows usage information
* `-b`, `--banned` <groupId:artifactId,...> Accounts for Banned Dependencies (preserves existing exclusions)
* `-d`, `--displayExecutionOutput` Displays execution output of processing.
* `-e`, `--env` <key:value,...> Specify environment variables.
* `-m`, `--mode` `All` (default), `SetupOnly`, `Continue`, `ProcessOnly`, `ProcessSingleStep`, or `ExclusionReduction`
* `-p`, `--preserveExclusions` <groupId:artifactId,...> Preserve exclusions
* `-s`, `--skipPrompt` (default false)
* `directory` location of project

## Modes
* `All` (default): Performs SetupOnly, ProcessOnly, and ExclusionReduction.
* `SetupOnly`: Cleans out exclusions for processing.
* `Continue`: Performs ProcessOnly and then ExclusionReduction
* `ProcessOnly`: Iterates dependency management processing until done.
* `ProcessSingleStep`: Single iteration of dependency management processing.
* `ExclusionReduction`: Remove unnecessary exclusions in dependencyManagement. 

## Workflow
Dependency Angel performs the following process:
1. Performs a setup step which cleans out any existing exclusions.  This is to ensure that all dependencies are re-evaluated and latest versions are chosen.
2. Performs a process step that runs `mvn dependency:analyze` and evaluates dependencies and determines if there are conflicts.  These conflicts are processed, assuming the latest version is always desired.
   1. Latest version is preferred (see Assumptions)
   2. Explicit dependencies are added when transitive conflicts cannot be resolved from a single source
   3. Versions are added to properties
3. Repeats the process step until no dependency issues are found.
4. Removes unnecessary exclusions from `<dependencyManagement>`
5. Dependency Angel will keep a config file `.angel.conf` storing some configuration to help make maintenance / reprocessing consistent.

## Runbook
* If you have challenges, it may be useful to run Dependency Angel in order, manually, to identify where changes could occur.  This is done by running `-m SetupOnly`, then `-m ProcessOnly` or `-m ProcessSingleStep`.
* If Dependency Angel adds an explicit dependency that has banned transitive dependency, you will have to manually add that exclusion.
* If your application fails at runtime, it could likely be because of a lost transitive dependency (or version issues).  Compare the `mvn dependency:tree` between prior work and Dependency Angel to help identify gaps.
* If you get a problem finding a version of a dependency from the repository, try doing a build of your project before running Dependency Angel.

## Money
If you find this tool saves you time, please consider sending me some money: unhumansoftware@gmail.com.  Alternatively, please consider donating the same to a food bank, and let me know: unhumansoftware@gmail.com.  Thank you!
