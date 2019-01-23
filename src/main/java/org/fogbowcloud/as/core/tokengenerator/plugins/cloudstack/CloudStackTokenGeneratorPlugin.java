package org.fogbowcloud.as.core.tokengenerator.plugins.cloudstack;

import java.util.Map;
import java.util.Properties;

import org.apache.http.client.HttpResponseException;
import org.apache.log4j.Logger;
import org.fogbowcloud.as.core.PropertiesHolder;
import org.fogbowcloud.as.core.constants.ConfigurationConstants;
import org.fogbowcloud.as.core.constants.Messages;
import org.fogbowcloud.as.core.exceptions.FogbowAsException;
import org.fogbowcloud.as.core.exceptions.InvalidParameterException;
import org.fogbowcloud.as.core.exceptions.UnexpectedException;
import org.fogbowcloud.as.common.util.FogbowAuthenticationHolder;
import org.fogbowcloud.as.core.tokengenerator.TokenGeneratorPlugin;
import org.fogbowcloud.as.common.util.PropertiesUtil;
import org.fogbowcloud.as.common.util.connectivity.HttpRequestClientUtil;
import org.fogbowcloud.as.core.util.HttpToFogbowAsExceptionMapper;

public class CloudStackTokenGeneratorPlugin implements TokenGeneratorPlugin {
    private static final Logger LOGGER = Logger.getLogger(CloudStackTokenGeneratorPlugin.class);

    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String DOMAIN = "domain";
    public static final String CLOUDSTACK_URL = "cloudstack_api_url";
    public static final String CLOUDSTACK_TOKEN_VALUE_SEPARATOR = ":";
    public static final String CLOUDSTACK_TOKEN_STRING_SEPARATOR = "!#!";
    public static final String API_KEY = "apikey";

    private String tokenProviderId;
    private String cloudStackUrl;
    private HttpRequestClientUtil client;
	private FogbowAuthenticationHolder fogbowAuthenticationHolder;
    private Properties properties;

    public CloudStackTokenGeneratorPlugin(String confFilePath) {
        this.properties = PropertiesUtil.readProperties(confFilePath);
        this.cloudStackUrl = this.properties.getProperty(CLOUDSTACK_URL);
        this.tokenProviderId = PropertiesHolder.getInstance().getProperty(ConfigurationConstants.LOCAL_MEMBER_ID);
        this.client = new HttpRequestClientUtil();
        
        this.fogbowAuthenticationHolder = FogbowAuthenticationHolder.getInstance();
    }

    @Override
    public String createTokenValue(Map<String, String> credentials) throws FogbowAsException, UnexpectedException {
        if ((credentials == null) || (credentials.get(USERNAME) == null) || (credentials.get(PASSWORD) == null) ||
                credentials.get(DOMAIN) == null) {
            throw new InvalidParameterException(Messages.Exception.NO_USER_CREDENTIALS);
        }

        LoginRequest request = createLoginRequest(credentials);
        HttpRequestClientUtil.Response jsonResponse = null;
        try {
            // NOTE(pauloewerton): since all cloudstack requests params are passed via url args, we do not need to
            // send a valid json body in the post request
            jsonResponse = this.client.doPostRequest(request.getUriBuilder().toString(), "data");
        } catch (HttpResponseException e) {
            HttpToFogbowAsExceptionMapper.map(e);
        }

        LoginResponse response = LoginResponse.fromJson(jsonResponse.getContent());
        String tokenValue = getTokenValue(response.getSessionKey());

        return tokenValue;
    }

    private LoginRequest createLoginRequest(Map<String, String> credentials) throws InvalidParameterException {
        String userId = credentials.get(USERNAME);
        String password = credentials.get(PASSWORD);
        String domain = credentials.get(DOMAIN);

        LoginRequest loginRequest = new LoginRequest.Builder()
                .username(userId)
                .password(password)
                .domain(domain)
                .build(this.cloudStackUrl);

        return loginRequest;
    }

    private String getTokenValue(String sessionKey) throws FogbowAsException, UnexpectedException {
        ListAccountsRequest request = new ListAccountsRequest.Builder()
                .sessionKey(sessionKey)
                .build(this.cloudStackUrl);

        String jsonResponse = null;
        try {
            // NOTE(pauloewerton): passing a placeholder as there is no need to pass a valid token in this request
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), "CloudStackTokenValue");
        } catch (HttpResponseException e) {
            HttpToFogbowAsExceptionMapper.map(e);
        }

        String tokenString = null;
        try {
            ListAccountsResponse response = ListAccountsResponse.fromJson(jsonResponse);
            // NOTE(pauloewerton): considering only one account/user per request
            ListAccountsResponse.User user = response.getAccounts().get(0).getUsers().get(0);

            // NOTE(pauloewerton): keeping a colon as separator as expected by the other cloudstack plugins
            String tokenValue = user.getApiKey() + CLOUDSTACK_TOKEN_VALUE_SEPARATOR + user.getSecretKey();
            String userId = user.getId();
            String firstName = user.getFirstName();
            String lastName = user.getLastName();
            String userName = (firstName != null && lastName != null) ? firstName + " " + lastName : user.getUsername();

            tokenString = this.tokenProviderId + CLOUDSTACK_TOKEN_STRING_SEPARATOR + tokenValue +
                    CLOUDSTACK_TOKEN_STRING_SEPARATOR + userId + CLOUDSTACK_TOKEN_STRING_SEPARATOR + userName;

            String signature = createSignature(tokenString);
            return tokenString + CLOUDSTACK_TOKEN_STRING_SEPARATOR + signature;
        } catch (Exception e) {
            LOGGER.error(Messages.Error.UNABLE_TO_GET_TOKEN_FROM_JSON, e);
            throw new UnexpectedException(Messages.Error.UNABLE_TO_GET_TOKEN_FROM_JSON, e);
        }
    }

    protected String createSignature(String message) throws FogbowAsException {
        return this.fogbowAuthenticationHolder.createSignature(message);
    }

    // Used for testing
    public void setClient(HttpRequestClientUtil client) {
        this.client = client;
    }
}
