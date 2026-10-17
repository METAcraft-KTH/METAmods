package nu.metacraft.deploy;

import org.gradle.api.GradleException;

/** A deploy problem, worded for staff (deploy/README.md quotes these messages). */
public class DeployException extends GradleException {
    public DeployException(String message) {
        super(message);
    }

    public DeployException(String message, Throwable cause) {
        super(message, cause);
    }
}
