package io.opensaber.pojos;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component("apiMessage")
@Scope(value = WebApplicationContext.SCOPE_REQUEST,
					proxyMode = ScopedProxyMode.TARGET_CLASS)
public class APIMessage {
	private static Logger logger = LoggerFactory.getLogger(APIMessage.class);

	/* HTTP wrapper */
	private RequestWrapper requestWrapper;

	/* Custom pojo specific to org */
	private Request request;

	/* A temporary map to pass data cooked up in the interceptors, modules */
	private Map<String, Object> localMap = new HashMap<>();

	private static ObjectMapper objectMapper = new ObjectMapper();

	public APIMessage() {}

	protected Request getRequest(Map<String, Object> requestMap) {
		if (null != requestMap && !requestMap.isEmpty()) {
            String id = (String) requestMap.get("id");
            request.setId(id);

            try {
    			String ver = (String) requestMap.get("ver");
    			Long ts = (Long) requestMap.get("ets");
                request.setEts(ts);
                request.setVer(ver);
            } catch (Exception e) {
			    // its ok if people are not sending.
            }

			Object reqParams = requestMap.get("params");
			if (null != reqParams) {
				try {
					RequestParams params = (RequestParams) objectMapper.convertValue(reqParams, RequestParams.class);
					request.setParams(params);
				} catch (Exception e) {
				}
			}
			Object requestObj = requestMap.get("request");
			if (null != requestObj) {
				try {
					String strRequest = objectMapper.writeValueAsString(requestObj);
					Map<String, Object> map = objectMapper.readValue(strRequest, Map.class);
					if (null != map && !map.isEmpty())
						request.setRequestMap(map);
				} catch (Exception e) {
				}
			}
		}
		return request;
	}

	@Autowired
	public APIMessage(HttpServletRequest servletRequest) {
		request = new Request();
		requestWrapper = new RequestWrapper(servletRequest);
		String body = requestWrapper.getBody();
		try {
			Map<String, Object> auditMap = new ObjectMapper().readValue(body, new TypeReference<Map<String, Object>>() {
			});
			getRequest(auditMap);
		} catch (IOException jpe) {
			logger.error("Can't read request body" + jpe);
			request = null;
		}
	}

	/**
	 * Get the message body
	 * @return
	 */
	public String getBody() {
		return requestWrapper.getBody();
	}

	/**
	 * Provides access to HTTPServletRequest operations
	 * @return
	 */
	public RequestWrapper getRequestWrapper() {
		return requestWrapper;
	}

	public Request getRequest() {
		return request;
	}

	/**
	 * Add some temporary request-specific data, say massaged data
	 * @param key
	 * @param data
	 */
	public void addLocalMap(String key, Object data) {
	    localMap.put(key, data);
    }

	/**
	 * Read back from local
	 * @param key
	 * @return
	 */
	public Object getLocalMap(String key) {
	    return localMap.get(key);
    }

	/**
	 * Get a map of all temporary data
	 * @return
	 */
	public Map<String, Object> getLocalMap() {
		return localMap;
	}
}
