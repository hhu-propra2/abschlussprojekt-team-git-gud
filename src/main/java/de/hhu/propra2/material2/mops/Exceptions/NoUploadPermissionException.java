package de.hhu.propra2.material2.mops.Exceptions;

public class NoUploadPermissionException extends Exception {
    public NoUploadPermissionException() {
        super();
    }

    public NoUploadPermissionException(final String message) {
        super(message);
    }
}
