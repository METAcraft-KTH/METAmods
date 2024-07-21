# METAmods

Repository containing our dependent mods in one place, to make development easier.


## Developing
- After cloning the repository, make sure to compile the project with `./gradlew build` at least once. 
  - Then, if you have already imported the project into your IDE, perform a gradle refresh. 
  - If you don't do this, the project won't run properly in your IDE.
- The default run configurations will launch all mods at once.
- If you wish to debug a specific mod without the others, copy the run configuration and change the "METAmods.main" module to the "main" module of the submodule you want to work with.
- To make changes made in a module you're not running take effect, run `./gradlew build` and refresh gradle.
- To add additional modules:
  - Create a new directory under "mods" containing the "build.gradle" and "src" folder of your module.
  - Mention your module with an include statement in the root-level "settings.gradle".
  - Please see the other modules for examples.
  - If you wish to put your module in a sub-directory, make sure to add the sub-directory to the "metaProjects" variable in the root-level "build.gradle".