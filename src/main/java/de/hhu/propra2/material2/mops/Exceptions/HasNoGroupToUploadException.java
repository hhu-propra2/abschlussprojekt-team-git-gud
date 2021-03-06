package de.hhu.propra2.material2.mops.Exceptions;

public class HasNoGroupToUploadException extends Exception {

    /**
     * Constructor
     */
    public HasNoGroupToUploadException() {
        super("Something went wrong with the Download.");
    }

    /**
     * Constructor with message append
     *
     * @param message e.g. message from another error
     */
    public HasNoGroupToUploadException(final String message) {
        super(message);
    }
}
