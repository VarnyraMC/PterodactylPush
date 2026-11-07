
package com.varnyra.pterodactylpush;

import com.varnyra.pterodactylpush.tasks.DeployTask;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

public class PterodactylPushPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {

        PterodactylPushExtension extension =
                project.getExtensions().create(
                        "pterodactylPush",
                        PterodactylPushExtension.class,
                        project
                );

        TaskProvider<DeployTask> deployTask =
                project.getTasks().register(
                        "pterodactylPushDeploy",
                        DeployTask.class,
                        task -> {
                            task.setGroup("pterodactyl");

                            task.setDescription(
                                    "Deploys project files to a Pterodactyl server."
                            );

                            task.getServerUrl().set(extension.getServerUrl());
                            task.getServerId().set(extension.getServerId());
                            task.getApiToken().set(extension.getApiToken());

                            task.getRemoteDirectory().set(
                                    extension.getRemoteDirectory()
                            );

                            task.getSourceDirectory().set(
                                    extension.getSourceDirectory()
                            );

                            task.getIncludes().set(extension.getIncludes());
                            task.getExcludes().set(extension.getExcludes());
                        }
                );

        project.getPluginManager().withPlugin(
                "java",
                ignored -> deployTask.configure(
                        task -> task.dependsOn("build")
                )
        );

        project.getLogger().lifecycle(
                "PterodactylPush loaded."
        );
    }
}