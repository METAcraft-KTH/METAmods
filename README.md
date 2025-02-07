# METAmods

Repository containing our dependent mods in one place, to make development easier.


## Developing
- After cloning the repository, make sure to compile the project at least once (`./gradlew assemble`).
  - Then, if you have already imported the project into your IDE, perform a gradle refresh. 
  - If you don't do this, the project won't run properly in your IDE.
  - It is also highly recommended to run `./gradlew runDatagen` and then `./gradlew assemble` again (note that you must run the above command first, and make sure to run each command separately!).
- The default run configurations will launch all mods at once.
  - To use the run configuration, you will need to run `./gradlew dist:configureLaunch`
- Whenever you change something, you will need to rebuild the project for the changes to appear in the default launch configuration.
  - I recommend running `./gradlew dist:build` for this.
- In some cases you may wish to debug an individual module separately.
  - This removes the need to rebuild the project after every change and allows some extra features such as reloading classes without restarting.
  - To do this, copy the run configuration and change the "METAmods.main" module to the "main" module of the submodule you want to work with.
- To add additional modules:
  - Create a new directory under "mods" containing the "build.gradle" and "src" folder of your module.
  - Mention your module with an include statement in the root-level "settings.gradle".
  - Please see the other modules for examples.
  - If you wish to put your module in a sub-directory, make sure to add the sub-directory to the "ignoredProjects" variable in the root-level "build.gradle".
- Important, run `./gradlew clean` before bumping versions, otherwise gradle will fail to evaluate the project!
- If you find yourself in a situation that gradle can't resolve dependencies inside of one of the build/libs folders of this project:
  - Try running `./gradlew clean -Pskip-dist-resolve`
- Don't forget that you can always run a gradle task for a specific subproject if necessary.
  - For example: `./gradlew mods:metacraft-lib:build` to rebuild metacraft-lib.
  - If a module A says that a class from another module B doesn't exist, rebuilding module B usually solves the problem.
