package com.nateyolles.aem.core.replication;

import java.io.IOException;
import java.util.Arrays;

import com.day.cq.replication.AgentConfig;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.ReplicationLog;
import com.day.cq.replication.ReplicationResult;
import com.day.cq.replication.ReplicationTransaction;
import com.day.cq.replication.TransportContext;
import com.day.cq.replication.TransportHandler;

import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.jackrabbit.util.Base64;

/**
 * Transport handler to send test and purge requests to Akamai and handle
 * responses. The handler sets up basic authentication with the user/pass from
 * the replication agent's transport config and sends a GET request as a test
 * and POST as purge request. A valid test response is 200 while a valid purge
 * response is 201.
 * 
 * The transport handler is triggered by setting your replication agent's
 * transport URL's protocol to "akamai://".
 *
 * The transport handler builds the POST request body in accordance with
 * Akamai's CCU REST APIs {@link https://api.ccu.akamai.com/ccu/v2/docs/}
 * using the replication agent properties. 
 */
@Service(TransportHandler.class)
@Component(label = "Akamai Purge Agent", immediate = true)
public class AkamaiTransportHandler implements TransportHandler {

    /** Protocol for replication agent transport URI that triggers this transport handler. */
    private final static String AKAMAI_PROTOCOL = "akamai://";

    /** Akamai CCU REST API URL */
    private final static String AKAMAI_CCU_REST_API_URL = "https://api.ccu.akamai.com/ccu/v2/queues/default";

    /** Replication agent type property name. Valid values are "arl" and "cpcode". */
    private final static String PROPERTY_TYPE = "akamaiType";

    /** Replication agent multifield CP Code property name.*/
    private final static String PROPERTY_CP_CODES = "akamaiCPCodes";

    /** Replication agent domain property name. Valid values are "staging" and "production". */
    private final static String PROPERTY_DOMAIN = "akamaiDomain";

    /** Replication agent action property name. Valid values are "remove" and "invalidate". */
    private final static String PROPERTY_ACTION = "akamaiAction";

    /** Replication agent default type value */
    private final static String PROPERTY_TYPE_DEFAULT = "arl";

    /** Replication agent default domain value */
    private final static String PROPERTY_DOMAIN_DEFAULT = "production";

    /** Replication agent default action value */
    private final static String PROPERTY_ACTION_DEFAULT = "remove";

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canHandle(AgentConfig config) {
        final String transportURI = config.getTransportURI();

        return (transportURI != null) ? transportURI.toLowerCase().startsWith(AKAMAI_PROTOCOL) : false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReplicationResult deliver(TransportContext ctx, ReplicationTransaction tx)
            throws ReplicationException {

        final ReplicationActionType replicationType = tx.getAction().getType();

        if (replicationType == ReplicationActionType.TEST) {
            return doTest(ctx, tx);
        } else if (replicationType == ReplicationActionType.ACTIVATE) {
            return doActivate(ctx, tx);
        } else {
            throw new ReplicationException("Replication action type " + replicationType + " not supported.");
        }
    }

    /**
     * Send test request to Akamai via a GET request.
     *
     * Akamai will respond with a 200 HTTP status code if the request was
     * successfully submitted. The response will have information about the
     * queue length, but we're simply interested in the fact that the request
     * was authenticated.
     *
     * @param ctx Transport Context
     * @param tx Replication Transaction
     * @return ReplicationResult OK if 200 response from Akamai
     * @throws ReplicationException
     */
    private ReplicationResult doTest(TransportContext ctx, ReplicationTransaction tx)
            throws ReplicationException {

        final ReplicationLog log = tx.getLog();
        final HttpGet request = new HttpGet(AKAMAI_CCU_REST_API_URL);
        final HttpResponse response = sendRequest(request, ctx, tx);

        if (response != null) {
            final int statusCode = response.getStatusLine().getStatusCode();

            log.info(response.toString());
            log.info("---------------------------------------");

            if (statusCode == HttpStatus.SC_OK) {
                return ReplicationResult.OK;
            }
        }

        return new ReplicationResult(false, 0, "Replication test failed");
    }

    /**
     * Send purge request to Akamai via a POST request
     *
     * Akamai will respond with a 201 HTTP status code if the purge request was
     * successfully submitted.
     *
     * @param ctx Transport Context
     * @param tx Replication Transaction
     * @return ReplicationResult OK if 201 response from Akamai
     * @throws ReplicationException
     */
    private ReplicationResult doActivate(TransportContext ctx, ReplicationTransaction tx)
            throws ReplicationException {

    	final ReplicationLog log = tx.getLog();
        final HttpPost request = new HttpPost(AKAMAI_CCU_REST_API_URL);

        createPostBody(request, ctx, tx);

        final HttpResponse response = sendRequest(request, ctx, tx);

        if (response != null) {        	
            final int statusCode = response.getStatusLine().getStatusCode();

            log.info(response.toString());
            log.info("---------------------------------------");
            
            if (statusCode == HttpStatus.SC_CREATED) {
                return ReplicationResult.OK;
            }
        }

        return new ReplicationResult(false, 0, "Replication failed");
    }

    /**
     * Build preemptive basic authentication headers and send request.
     *
     * @param request The request to send to Akamai
     * @param ctx The TransportContext containing the username and password
     * @return HttpResponse The HTTP response from Akamai
     * @throws ReplicationException if a request could not be sent
     */
    private <T extends HttpRequestBase> HttpResponse sendRequest(final T request,
            final TransportContext ctx, final ReplicationTransaction tx)
            throws ReplicationException {

        final ReplicationLog log = tx.getLog();
        final String auth = ctx.getConfig().getTransportUser() + ":" + ctx.getConfig().getTransportPassword();
        final String encodedAuth = Base64.encode(auth);
        
        request.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth);
        request.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());

        HttpClient client = HttpClientBuilder.create().build();
        HttpResponse response;

        try {
            response = client.execute(request);
        } catch (IOException e) {
            throw new ReplicationException("Could not send replication request.", e);
        }

        return response;
    }

    /**
     * Build the Akamai purge request body based on the replication agent
     * settings and append it to the POST request.
     *
     * @param request The HTTP POST request to append the request body
     * @param ctx TransportContext
     * @param tx ReplicationTransaction
     * @throws ReplicationException if errors building the request body 
     */
    private void createPostBody(final HttpPost request, final TransportContext ctx,
            final ReplicationTransaction tx) throws ReplicationException {

        final ValueMap properties = ctx.getConfig().getProperties();
        final String type = PropertiesUtil.toString(properties.get(PROPERTY_TYPE), PROPERTY_TYPE_DEFAULT);
        final String domain = PropertiesUtil.toString(properties.get(PROPERTY_DOMAIN), PROPERTY_DOMAIN_DEFAULT);
        final String action = PropertiesUtil.toString(properties.get(PROPERTY_ACTION), PROPERTY_ACTION_DEFAULT);

        JSONObject json = new JSONObject();
        JSONArray purgeObjects = null;

        /*
         * Get list of CP codes or ARLs/URLs depending on agent setting
         */
        if (type.equals(PROPERTY_TYPE_DEFAULT)) {

            /*
             * Get the content created with the custom content builder class
             * 
             * The list of activated resources (e.g.: ["/content/geometrixx/en/blog"])
             * is available in tx.getAction().getPaths(). For this example, we want the
             * content created in our custom content builder which is available in
             * tx.getContent().getInputStream().
             */
            try {
                final String content = IOUtils.toString(tx.getContent().getInputStream());

                if (StringUtils.isNotBlank(content)) {
                    purgeObjects = new JSONArray(content);
                }
            } catch (IOException | JSONException e) {
                throw new ReplicationException("Could not retrieve content from content builder", e);
            }
        } else {
            final String[] cpCodes = PropertiesUtil.toStringArray(properties.get(PROPERTY_CP_CODES));
            purgeObjects = new JSONArray(Arrays.asList(cpCodes));
        }

        if (purgeObjects != null && purgeObjects.length() > 0) {
            try {
                json.put("type", type)
                    .put("action", action)
                    .put("domain", domain)
                    .put("objects", purgeObjects);
            } catch (JSONException e) {
                throw new ReplicationException("Could not build purge request content", e);
            }

            final StringEntity entity = new StringEntity(json.toString(), CharEncoding.ISO_8859_1);
            request.setEntity(entity);

        } else {
            throw new ReplicationException("No CP codes or pages to purge");
        }
    }
}
