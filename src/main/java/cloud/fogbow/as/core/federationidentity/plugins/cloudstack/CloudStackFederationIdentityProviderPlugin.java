package cloud.fogbow.as.core.federationidentity.plugins.cloudstack;

import java.util.HashMap;
import java.util.Map;

import cloud.fogbow.as.core.federationidentity.FederationIdentityProviderPlugin;
import cloud.fogbow.common.constants.CloudStackConstants;
import cloud.fogbow.common.constants.HttpMethod;
import cloud.fogbow.common.exceptions.InvalidParameterException;
import cloud.fogbow.common.models.FederationUser;
import cloud.fogbow.common.util.connectivity.HttpResponse;
import cloud.fogbow.common.util.connectivity.HttpRequestClientUtil;
import cloud.fogbow.as.core.PropertiesHolder;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.apache.log4j.Logger;
import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.UnexpectedException;
import cloud.fogbow.as.constants.ConfigurationPropertyKeys;
import cloud.fogbow.as.constants.Messages;
import cloud.fogbow.as.core.util.HttpToFogbowAsExceptionMapper;

public class CloudStackFederationIdentityProviderPlugin implements FederationIdentityProviderPlugin {
    private static final Logger LOGGER = Logger.getLogger(CloudStackFederationIdentityProviderPlugin.class);

    private String cloudStackUrl;
    private String tokenProviderId;

    public CloudStackFederationIdentityProviderPlugin() {
        this.tokenProviderId = PropertiesHolder.getInstance().getProperty(ConfigurationPropertyKeys.LOCAL_MEMBER_ID_KEY);
        this.cloudStackUrl = PropertiesHolder.getInstance().getProperty(ConfigurationPropertyKeys.CLOUDSTACK_URL_KEY);
    }

    public CloudStackFederationIdentityProviderPlugin(String cloudStackUrl, String tokenProviderId) {
        this.cloudStackUrl = cloudStackUrl;
        this.tokenProviderId = tokenProviderId;
    }

    @Override
    public FederationUser getFederationUser(Map<String, String> credentials) throws FogbowException, UnexpectedException {
        if ((credentials == null) || (credentials.get(CloudStackConstants.Identity.USERNAME_KEY_JSON) == null) ||
                (credentials.get(CloudStackConstants.Identity.PASSWORD_KEY_JSON) == null) ||
                credentials.get(CloudStackConstants.Identity.DOMAIN_KEY_JSON) == null) {
            throw new InvalidParameterException(Messages.Exception.NO_USER_CREDENTIALS);
        }

        LoginRequest request = createLoginRequest(credentials);

        // NOTE(pauloewerton): since all cloudstack requests params are passed via url args, we do not need to
        // send a valid json body in the post request
        HttpResponse response = HttpRequestClientUtil.doGenericRequest(HttpMethod.POST,
                request.getUriBuilder().toString(), new HashMap<>(), new HashMap<>());

        if (response.getHttpCode() > HttpStatus.SC_OK) {
            HttpResponseException exception = new HttpResponseException(response.getHttpCode(), response.getContent());
            HttpToFogbowAsExceptionMapper.map(exception);
            return null;
        } else {
            LoginResponse loginResponse = LoginResponse.fromJson(response.getContent());
            return getTokenValue(loginResponse.getSessionKey());
        }
    }

    private LoginRequest createLoginRequest(Map<String, String> credentials) throws InvalidParameterException {
        String userId = credentials.get(CloudStackConstants.Identity.USERNAME_KEY_JSON);
        String password = credentials.get(CloudStackConstants.Identity.PASSWORD_KEY_JSON);
        String domain = credentials.get(CloudStackConstants.Identity.DOMAIN_KEY_JSON);

        LoginRequest loginRequest = new LoginRequest.Builder()
                .username(userId)
                .password(password)
                .domain(domain)
                .build(this.cloudStackUrl);

        return loginRequest;
    }

    private FederationUser getTokenValue(String sessionKey) throws FogbowException, UnexpectedException {
        ListAccountsRequest request = new ListAccountsRequest.Builder()
                .sessionKey(sessionKey)
                .build(this.cloudStackUrl);

        HttpResponse response = HttpRequestClientUtil.doGenericRequest(HttpMethod.GET,
                request.getUriBuilder().toString(), new HashMap<>(), new HashMap<>());

        if (response.getHttpCode() > HttpStatus.SC_OK) {
            HttpResponseException exception = new HttpResponseException(response.getHttpCode(), response.getContent());
            HttpToFogbowAsExceptionMapper.map(exception);
            return null;
        } else {
            try {
                ListAccountsResponse listAccountsResponse = ListAccountsResponse.fromJson(response.getContent());
                // NOTE(pauloewerton): considering only one account/user per request
                ListAccountsResponse.User user = listAccountsResponse.getAccounts().get(0).getUsers().get(0);

                // NOTE(pauloewerton): keeping the token-value separator as expected by the other cloudstack plugins
                String tokenValue = user.getApiKey() + CloudStackConstants.KEY_VALUE_SEPARATOR + user.getSecretKey();
                String userId = user.getId();
                String userName = getUserName(user);

                return new FederationUser(this.tokenProviderId, userId, userName, tokenValue, new HashMap<>());
            } catch (Exception e) {
                LOGGER.error(Messages.Error.UNABLE_TO_GET_TOKEN_FROM_JSON, e);
                throw new UnexpectedException(Messages.Error.UNABLE_TO_GET_TOKEN_FROM_JSON, e);
            }
        }
    }

    private String getUserName(ListAccountsResponse.User user) {
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        return (firstName != null && lastName != null) ? firstName + " " + lastName : user.getUsername();
    }
}
