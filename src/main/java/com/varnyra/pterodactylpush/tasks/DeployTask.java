
package com.varnyra.pterodactylpush.tasks;

import com.varnyra.pterodactylpush.PterodactylClient;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

@DisableCachingByDefault(because = "Deploys files to a remote Pterodactyl server")
public abstract class DeployTask extends DefaultTask {

    @Input
    public abstract Property<String> getServerUrl();

    @Input
    public abstract Property<String> getServerId();

    @Input
    public abstract Property<String> getApiToken();

    @Input
    public abstract Property<String> getRemoteDirectory();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourceDirectory();

    @Input
    public abstract ListProperty<String> getIncludes();

    @Input
    public abstract ListProperty<String> getExcludes();

    @TaskAction
    public void deploy() throws Exception {

        String panel = getServerUrl().get();
        String server = getServerId().get();
        String token = getApiToken().get();

        if (panel.isBlank() || server.isBlank() || token.isBlank()) {
            throw new IllegalStateException(
                    "Pterodactyl configuration is incomplete. "
                            + "Check serverUrl, serverId and apiToken."
            );
        }

        File source = getSourceDirectory().get().getAsFile();

        if (!source.isDirectory()) {
            throw new IOException(
                    "Source directory does not exist: " + source
            );
        }

        PterodactylClient client = new PterodactylClient(
                panel,
                server,
                token
        );

        String remoteRoot = getRemoteDirectory().get()
                .replace("\\", "/");

        if (!remoteRoot.startsWith("/")) {
            remoteRoot = "/" + remoteRoot;
        }

        if (remoteRoot.length() > 1 && remoteRoot.endsWith("/")) {
            remoteRoot = remoteRoot.substring(
                    0, remoteRoot.length() - 1
            );
        }

        File[] files = getProject().fileTree(source, spec -> {
            spec.include(getIncludes().get());
            spec.exclude(getExcludes().get());
        }).getFiles().toArray(new File[0]);

        if (files.length == 0) {
            getLogger().lifecycle(
                    "PterodactylPush: No files matched the deployment patterns."
            );
            return;
        }

        Set<String> directories = new TreeSet<>();

        for (File file : files) {
            Path relative = source.toPath().relativize(file.toPath());

            Path parent = relative.getParent();

            while (parent != null) {
                directories.add(
                        parent.toString().replace("\\", "/")
                );

                parent = parent.getParent();
            }
        }

        getLogger().lifecycle(
                "PterodactylPush: Preparing {} files.",
                files.length
        );

        client.ensureDirectory(remoteRoot);

        for (String directory : directories) {
            client.ensureDirectory(
                    remoteRoot + "/" + directory
            );
        }

        int uploaded = 0;

        for (File file : files) {
            Path relative = source.toPath().relativize(
                    file.toPath()
            );

            String parent = relative.getParent() == null
                    ? ""
                    : relative.getParent().toString()
                    .replace("\\", "/");

            String destination = parent.isEmpty()
                    ? remoteRoot
                    : remoteRoot + "/" + parent;

            getLogger().lifecycle(
                    "[{}/{}] Uploading {}",
                    ++uploaded,
                    files.length,
                    relative.toString().replace("\\", "/")
            );

            client.uploadFile(file.toPath(), destination);
        }

        getLogger().lifecycle(
                "PterodactylPush: Successfully uploaded {} files!",
                uploaded
        );
    }
}