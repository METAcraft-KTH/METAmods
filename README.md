# METAmods

Repository containing our dependent mods in one place, to make development easier.


## Developing
- After cloning the repository:
  - Run `./gradlew runDatagen` to generate data.
  - Then run `./gradlew build` to build the project.
- The default run configurations will launch all mods at once.
  - To set up the run configuration, you will need to run `./gradlew dist:configureLaunch`
  - Then, you can either import the RunServer xml file or launch the server manually via `./gradlew runServer`.
- In some cases you may wish to debug an individual module separately.
  - To do this, copy the run configuration and change the "METAmods.main" module to the "main" module of the submodule you want to work with.
  - Alternatively, you can just run the `runServer` command on a module. For example `./gradlew mods:metacraft-lib:runServer`.
- To add additional modules:
  - Create a new directory under "mods" containing the "build.gradle" and "src" folder of your module.
  - Mention your module with an include statement in the root-level "settings.gradle".
  - Please see the other modules for examples.
  - If you wish to put your module in a sub-directory, make sure to add the sub-directory to the "nonModSubProjects" variable in the root-level "build.gradle".
- Don't forget that you can always run a gradle task for a specific subproject if necessary.
  - For example: `./gradlew mods:metacraft-lib:build` to rebuild metacraft-lib.
  - If a module A says that a class from another module B doesn't exist, rebuilding module B usually solves the problem.
