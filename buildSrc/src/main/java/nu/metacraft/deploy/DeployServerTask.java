package nu.metacraft.deploy;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** {@code ./gradlew deployServer -Pserver=<name>}: uploads build/deploy/<name>/ to the server. */
@UntrackedTask(because = "uploads to a server over SFTP")
public abstract class DeployServerTask extends DefaultTask {
    @Internal
    public abstract Property<String> getServer();

    @Internal
    public abstract DirectoryProperty getDeployDir();

    @Internal
    public abstract RegularFileProperty getServersFile();

    @Internal
    public abstract Property<Boolean> getDryRun();

    @Internal
    public abstract RegularFileProperty getSummaryFile();

    @TaskAction
    public void deploy() throws IOException {
        if (!getServer().isPresent()) {
            throw new DeployException("deployServer needs -Pserver=<name>, for example -Pserver=test");
        }
        String name = getServer().get();
        ServersConfig.Server target = ServersConfig.read(getServersFile().get().getAsFile().toPath()).server(name);
        String password = System.getenv("AUTODEPLOY_PASSWORD");
        if (password == null || password.isEmpty()) {
            throw new DeployException("AUTODEPLOY_PASSWORD is not set; nothing was uploaded");
        }
        Path dir = getDeployDir().get().getAsFile().toPath();
        Deployer.Report report;
        try (SftpRemoteFiles remote = SftpRemoteFiles.connect(target.host(), target.port(), target.user(), password)) {
            report = Deployer.deploy(name, dir, remote, getDryRun().get());
        }
        String markdown = Summary.markdown(report);
        Path summary = getSummaryFile().get().getAsFile().toPath();
        Files.createDirectories(summary.getParent());
        Files.writeString(summary, markdown, StandardCharsets.UTF_8);
        String stepSummary = System.getenv("GITHUB_STEP_SUMMARY");
        if (stepSummary != null && !stepSummary.isEmpty()) {
            Files.writeString(Path.of(stepSummary), markdown, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        getLogger().lifecycle(markdown);
    }
}
