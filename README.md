# METAmods

Repository containing our dependent mods in one place, to make development easier.


## Developing
- After cloning the repository:
  - Run `./gradlew runDatagen` to generate data.
  - Then run `./gradlew build` to build the project.
- The default run configurations will launch all mods at once.
  - To set up the run configuration, you will need to run `./gradlew dist:configureLaunch`
  - Then, you can either:
    - Create a native run configuration for your IDE:
      - IntelliJ: Gradle should generate a RunServer configuration for you and update it whenever you sync.
        - Make sure to set the correct Java Version under Project Structure
    - Run `./gradlew dist:runServer`
- In some cases you may wish to debug an individual module separately.
  - To do this, copy the run configuration and change the "METAmods.main" module to the "main" module of the submodule you want to work with.
  - Alternatively, you can just run the `runServer` command on a module. For example `./gradlew mods:metacraft-lib:runServer`.
- To add additional modules:
  - Create a new directory under "mods" containing the "build.gradle" and "src" folder of your module.
  - Mention your module with an include statement in the root-level "settings.gradle".
  - An easy way to create a new module is to copy an existing module.
    - Just be sure to update all values in fabric.mod.json.
  - If you wish to put your module in a sub-directory, make sure to add the sub-directory to the "nonModSubProjects" variable in the root-level "build.gradle".
  - By default, METAmods expects all mods to have the same mod id as their module name.
    - If you wish to diverge from this, you'll need to add an entry to the PROJECT_TO_MOD_ID map in the dist module.
    - This is used to prevent startup if some module was not loaded in order to prevent data in your test world from getting lost.
- Don't forget that you can always run a gradle task for a specific subproject if necessary.
  - For example: `./gradlew mods:metacraft-lib:build` to rebuild metacraft-lib.
  - If a module A says that a class from another module B doesn't exist, rebuilding module B usually solves the problem.
