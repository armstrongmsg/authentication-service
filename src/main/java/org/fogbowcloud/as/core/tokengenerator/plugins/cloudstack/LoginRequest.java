package org.fogbowcloud.as.core.tokengenerator.plugins.cloudstack;

import org.fogbowcloud.as.core.exceptions.InvalidParameterException;
import org.fogbowcloud.as.core.util.cloud.CloudStackRequest;

public class LoginRequest extends CloudStackRequest {
    public static final String LOGIN_COMMAND = "login";
    public static final String USERNAME_KEY = "username";
    public static final String PASSWORD_KEY = "password";
    public static final String DOMAIN_KEY = "domain";

    private LoginRequest(Builder builder) throws InvalidParameterException {
        super(builder.cloudStackUrl);
        addParameter(USERNAME_KEY, builder.username);
        addParameter(PASSWORD_KEY, builder.password);
        addParameter(DOMAIN_KEY, builder.domain);
    }

    @Override
    public String toString() {
        return super.toString();
    }

    @Override
    public String getCommand() {
        return LOGIN_COMMAND;
    }

    public static class Builder {
        private String cloudStackUrl;
        private String username;
        private String password;
        private String domain;

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public LoginRequest build(String cloudStackUrl) throws InvalidParameterException {
            this.cloudStackUrl = cloudStackUrl;
            return new LoginRequest(this);
        }

    }
}
