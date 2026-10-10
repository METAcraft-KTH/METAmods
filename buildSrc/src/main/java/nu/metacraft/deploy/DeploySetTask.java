package nu.metacraft.deploy;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

import java.io.File;
import java.nio.file.Path;
import java.util.SortedMap;
import java.util.TreeMap;

/** {@code ./gradlew deploySet -Pserver=<name>}: build/deploy/<name>/ with the jars and manifest.json. */
@UntrackedTask(because = "fetches external jars and rewrites build/deploy/<server>/ each time; it is cheap")
public abstract class DeploySetTask extends DefaultTask {
    // Optional: when deploySet is invoked without -Pserver, build.gradle leaves this and every
    // property below unset and wires only a failure action (see build.gradle). Without @Optional,
    // Gradle's own required-property validation would run first and hide the "needs -Pserver"
    // message that failure action gives.
    @Optional
    @Input
    public abstract Property<String> getServer();

    @Optional
    @InputFile
    public abstract RegularFileProperty getListFile();

    /** Project name to its built mod jar (the Loom {@code jar} task's output). */
    @Internal
    public abstract MapProperty<String, File> getProjectJars();

    /** Mod ids of every project under mods/, for the depends check. */
    @Optional
    @Input
    public abstract SetProperty<String> getRepoModIds();

    @Optional
    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    public void buildSet() {
        String server = getServer().get();
        DeployList list = DeployList.read(server, getListFile().get().getAsFile().toPath());
        SortedMap<String, Path> jars = new TreeMap<>();
        getProjectJars().get().forEach((name, file) -> jars.put(name, file.toPath()));
        Path out = getOutputDir().get().getAsFile().toPath();
        Manifest manifest = SetBuilder.build(list, jars, getRepoModIds().get(), out, SetBuilder.URL_FETCHER);
        getLogger().lifecycle("{}: {} mods in {}", server, manifest.entries().size(), out);
    }
}
