
package com.varnyra.pterodactylpush;

import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class PterodactylPushExtension {

    @Inject
    public PterodactylPushExtension(Project project) {
        getServerUrl().convention("");
        getServerId().convention("");
        getApiToken().convention("");
        getRemoteDirectory().convention("/");
        getSourceDirectory().convention(
                project.getLayout().getBuildDirectory().dir("libs")
        );

        getIncludes().convention(java.util.List.of("**/*"));
        getExcludes().convention(java.util.List.of());
    }

    public abstract Property<String> getServerUrl();

    public abstract Property<String> getServerId();

    public abstract Property<String> getApiToken();

    public abstract Property<String> getRemoteDirectory();

    public abstract DirectoryProperty getSourceDirectory();

    public abstract ListProperty<String> getIncludes();

    public abstract ListProperty<String> getExcludes();
}
