package org.fogbowcloud.as.common.constants;

public class Messages {

    public static class Exception {
        public static final String FATAL_ERROR = "Fatal error.";
        public static final String UNEXPECTED_ERROR = "Unexpected error.";
        public static final String FOGBOW = "Fogbow exception.";
        public static final String GENERIC_EXCEPTION = "Operation returned error: %s";
        public static final String INVALID_CREDENTIALS = "Invalid credentials.";
        public static final String INVALID_PARAMETER = "Invalid parameter.";
        public static final String INVALID_TOKEN = "Invalid token value.";
        public static final String UNAVAILABLE_PROVIDER = "Provider is not available.";
        public static final String AUTHENTICATION_ERROR = "Authentication error.";
    }

    public static class Fatal {
        public static final String PROPERTY_FILE_NOT_FOUND = "Property file %s not found.";
        public static final String UNABLE_TO_FIND_CLASS = "Unable to find class %s.";
    }

    public static final class Error {
        public static final String ERROR_WHILE_CONSUMING_RESPONSE = "Error while consuming response %s.";
        public static final String UNABLE_TO_CLOSE_FILE = "Unable to close file %s.";
    }
}