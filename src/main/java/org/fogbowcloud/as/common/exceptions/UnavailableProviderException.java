package org.fogbowcloud.as.common.exceptions;

import org.fogbowcloud.as.common.constants.Messages;

public class UnavailableProviderException extends FogbowException {
    private static final long serialVersionUID = 1L;

    public UnavailableProviderException() {
        super(Messages.Exception.UNAVAILABLE_PROVIDER);
    }

    public UnavailableProviderException(String message) {
        super(message);
    }

    public UnavailableProviderException(String message, Throwable cause) {
        super(message, cause);
    }

}