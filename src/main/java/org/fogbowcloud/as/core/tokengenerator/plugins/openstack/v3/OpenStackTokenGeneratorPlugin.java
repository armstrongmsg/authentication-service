package org.fogbowcloud.as.core.tokengenerator.plugins.openstack.v3;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Properties;

import org.apache.http.Header;
import org.apache.http.client.HttpResponseException;
import org.apache.log4j.Logger;
import org.fogbowcloud.as.core.PropertiesHolder;
import org.fogbowcloud.as.core.constants.ConfigurationConstants;
import org.fogbowcloud.as.core.constants.Messages;
import org.fogbowcloud.as.core.exceptions.FatalErrorException;
import org.fogbowcloud.as.core.exceptions.FogbowAsException;
import org.fogbowcloud.as.core.exceptions.UnexpectedException;
import org.fogbowcloud.as.common.util.FogbowAuthenticationHolder;
import org.fogbowcloud.as.core.tokengenerator.TokenGeneratorPlugin;
import org.fogbowcloud.as.common.util.PropertiesUtil;
import org.fogbowcloud.as.common.util.connectivity.HttpRequestClientUtil;
import org.fogbowcloud.as.core.util.HttpToFogbowAsExceptionMapper;

public class OpenStackTokenGeneratorPlugin implements TokenGeneratorPlugin {
    private static final Logger LOGGER = Logger.getLogger(OpenStackTokenGeneratorPlugin.class);

    public static final String V3_TOKENS_ENDPOINT_PATH = "/auth/tokens";
    public static final String X_SUBJECT_TOKEN = "X-Subject-Token";
    public static final String OPENSTACK_TOKEN_STRING_SEPARATOR = "!#!";
    public static final String PROJECT_NAME = "projectname";
    public static final String PASSWORD = "password";
    public static final String USER_NAME = "username";
    public static final Object DOMAIN = "domain";
    public static final String KEYSTONE_V3_URL_KEY = "openstack_keystone_v3_url";

    private String v3TokensEndpoint;
    private HttpRequestClientUtil client;
    private String tokenProviderId;
	private FogbowAuthenticationHolder fogbowAuthenticationHolder;

    public OpenStackTokenGeneratorPlugin(String confFilePath) throws FatalErrorException {
        this.tokenProviderId = PropertiesHolder.getInstance().getProperty(ConfigurationConstants.LOCAL_MEMBER_ID);

        Properties properties = PropertiesUtil.readProperties(confFilePath);

        String identityUrl = properties.getProperty(KEYSTONE_V3_URL_KEY);
        if (isUrlValid(identityUrl)) {
            this.v3TokensEndpoint = identityUrl + V3_TOKENS_ENDPOINT_PATH;
        }
        
        this.fogbowAuthenticationHolder = FogbowAuthenticationHolder.getInstance();
        
        this.client = new HttpRequestClientUtil();
    }

    private boolean isUrlValid(String url) throws FatalErrorException {
        if (url == null || url.trim().isEmpty()) {
            throw new FatalErrorException(String.format(Messages.Fatal.INVALID_SERVICE_URL, (url == null ? "null" : "")));
        }
        return true;
    }

    @Override
    public String createTokenValue(Map<String, String> credentials) throws FogbowAsException,
            UnexpectedException {

        String jsonBody = mountJsonBody(credentials);

        HttpRequestClientUtil.Response response = null;
        try {
            response = this.client.doPostRequest(this.v3TokensEndpoint, jsonBody);
        } catch (HttpResponseException e) {
            HttpToFogbowAsExceptionMapper.map(e);
        }
        String tokenString = getTokenFromJson(response);
        return tokenString;
    }

    private String getTokenFromJson(HttpRequestClientUtil.Response response) throws UnexpectedException {

        String tokenValue = null;
        Header[] headers = response.getHeaders();
        for (Header header : headers) {
            if (header.getName().equals(X_SUBJECT_TOKEN)) {
                tokenValue = header.getValue();
            }
        }

        try {
            CreateTokenResponse createTokenResponse = CreateTokenResponse.fromJson(response.getContent());
            CreateTokenResponse.User userTokenResponse = createTokenResponse.getUser();
            String userId = userTokenResponse.getId();
            String userName = userTokenResponse.getName();

            CreateTokenResponse.Project projectTokenResponse = createTokenResponse.getProject();
            String projectId = projectTokenResponse.getId();

            String tokenString = this.tokenProviderId + OPENSTACK_TOKEN_STRING_SEPARATOR + tokenValue +
                    OPENSTACK_TOKEN_STRING_SEPARATOR +
                    userId + OPENSTACK_TOKEN_STRING_SEPARATOR + userName + OPENSTACK_TOKEN_STRING_SEPARATOR + projectId;
            
            String signature = createSignature(tokenString);
            return tokenString + OPENSTACK_TOKEN_STRING_SEPARATOR + signature;
        } catch (Exception e) {
            LOGGER.error(Messages.Error.UNABLE_TO_GET_TOKEN_FROM_JSON, e);
            throw new UnexpectedException(Messages.Error.UNABLE_TO_GET_TOKEN_FROM_JSON, e);
        }
    }

    protected String createSignature(String message) throws IOException, GeneralSecurityException, FogbowAsException {
        return this.fogbowAuthenticationHolder.createSignature(message);
    }
    
    private String mountJsonBody(Map<String, String> credentials) {
        String projectName = credentials.get(PROJECT_NAME);
        String password = credentials.get(PASSWORD);
        String domain = credentials.get(DOMAIN);
        String userName = credentials.get(USER_NAME);

        CreateTokenRequest createTokenRequest = new CreateTokenRequest.Builder()
                .projectName(projectName)
                .domain(domain)
                .userName(userName)
                .password(password)
                .build();

        return createTokenRequest.toJson();
    }

    // Used in testing
    public void setClient(HttpRequestClientUtil client) {
        this.client = client;
    }
}
