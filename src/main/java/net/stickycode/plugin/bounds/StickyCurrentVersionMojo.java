package net.stickycode.plugin.bounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;

@Mojo(name = "current-version", threadSafe = true, defaultPhase = LifecyclePhase.VALIDATE)
public class StickyCurrentVersionMojo
    extends AbstractMojo {

  /**
   * The current repository/network configuration of Maven.
   */
  @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
  private RepositorySystemSession session;

  @Parameter(required = false)
  private Map<String, String> coordinates;

  /**
   * The artifacts to get the current version for group:artifact:version
   */
  @Parameter(required = true)
  private List<String> artifacts;

  @Parameter(defaultValue = "false")
  private Boolean includeSnapshots = false;

  @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
  private List<RemoteRepository> repositories;

  @Parameter(readonly = true, defaultValue = "${project}")
  private MavenProject project;

  @Component
  private RepositorySystem repository;

  @Override
  public void execute()
      throws MojoExecutionException, MojoFailureException {
    if (coordinates != null)
      throw new MojoFailureException("Coordinates are deprecated in favour of artifacts. \\n"
        + " <artifacts>\\n"
        + "   <artifact>group:artifact:[lower,upper)</artifact>\\n"
        + " </artifacts>\\n");

    lookupVersions(artifacts);
  }

  void lookupVersions(List<String> lookup) {
    lookup
      .parallelStream()
      .map(this::parseCoordinates)
      .forEach(this::lookupArtifactVersion);
  }

  DefaultArtifact parseCoordinates(String gav) {
    String[] c = gav.split(":");
    return new DefaultArtifact(c[0],
      c[1],
      c.length >= 4 ? c[3] : null,
      c.length == 5 ? c[4] : "jar",
      c[2]);
  }

  void lookupArtifactVersion(DefaultArtifact artifact) {
    Version version = highestVersion(artifact);
    String propertyName = artifact.getArtifactId() + ".version";
    project.getProperties().setProperty(propertyName, version.toString());
    log("resolved %s to %s", artifact, version.toString());
  }

  private Version highestVersion(Artifact artifact) {
    VersionRangeRequest request = new VersionRangeRequest(artifact, repositories, null);
    VersionRangeResult v = resolve(request);

    if (!includeSnapshots) {
      List<Version> filtered = new ArrayList<Version>();
      for (Version aVersion : v.getVersions()) {
        if (!aVersion.toString().endsWith("SNAPSHOT")) {
          filtered.add(aVersion);
        }
      }
      v.setVersions(filtered);
    }

    if (v.getHighestVersion() == null) {
      throw (v.getExceptions().isEmpty())
        ? new RuntimeException("Failed to resolve " + artifact.toString())
        : new RuntimeException("Failed to resolve " + artifact.toString(), v.getExceptions().get(0));
    }

    return v.getHighestVersion();
  }

  private VersionRangeResult resolve(VersionRangeRequest request) {
    try {
      return repository.resolveVersionRange(session, request);
    }
    catch (VersionRangeResolutionException e) {
      throw new RuntimeException(e);
    }
  }

  private void log(String message, Object... parameters) {
    getLog().info(String.format(message, parameters));
  }

}
