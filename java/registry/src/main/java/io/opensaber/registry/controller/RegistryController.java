package io.opensaber.registry.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.opensaber.pojos.*;
import io.opensaber.registry.middleware.util.Constants;
import io.opensaber.registry.middleware.util.Constants.Direction;
import io.opensaber.registry.middleware.util.JSONUtil;
import io.opensaber.registry.model.DBConnectionInfoMgr;
import io.opensaber.registry.service.RegistryAuditService;
import io.opensaber.registry.service.RegistryService;
import io.opensaber.registry.service.SearchService;
import io.opensaber.registry.sink.shard.Shard;
import io.opensaber.registry.sink.shard.ShardManager;
import io.opensaber.registry.transform.*;
import io.opensaber.registry.util.ReadConfigurator;
import io.opensaber.registry.util.RecordIdentifier;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.websocket.server.PathParam;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

@RestController
public class RegistryController {
    private static Logger logger = LoggerFactory.getLogger(RegistryController.class);
    @Autowired
    Transformer transformer;
    @Autowired
    private ConfigurationHelper configurationHelper;
    @Autowired
    private RegistryService registryService;
    @Autowired
    private RegistryAuditService registryAuditService;
    @Autowired
    private SearchService searchService;
    @Autowired
    private APIMessage apiMessage;
    @Autowired
    private DBConnectionInfoMgr dbConnectionInfoMgr;

    private Gson gson = new Gson();
    private Type mapType = new TypeToken<Map<String, Object>>() {
    }.getType();
    @Value("${audit.enabled}")
    private boolean auditEnabled;
    @Value("${database.uuidPropertyName}")
    public String uuidPropertyName;
    @Autowired
    private OpenSaberInstrumentation watch;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ShardManager shardManager;

    @Value("${visitor.idStart}")
    private int visitorIdNext;

    public int getVisitorIdNext() {
        return visitorIdNext++;
    }

    private static final String VISITOR_STR = "Visitor";
    private static final String VISITOR_CODE_STR = "VIS";
    private static final String CODE_STR = "code";
    private static final String CODE_UUID_FILENAME_STR = "entity.json";
    private static final String ROLE_CODE_STR = "roleCode";
    private static final String STALL_CODE_STR = "stallCode";

    private ObjectNode codeUUIDNode;

    @PostConstruct
    public void loadCodeUUIDMap() {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(CODE_UUID_FILENAME_STR);
        try {
            codeUUIDNode = (ObjectNode) objectMapper.readTree(is);
        } catch (Exception e) {
            logger.info("Can't read existing code uuid maps");
        } finally {
            if (codeUUIDNode == null) {
                logger.info("Empty entity.json");
                codeUUIDNode = JsonNodeFactory.instance.objectNode();
            }
        }
    }

    private void populateCodeUUIDNode(boolean shouldAppend, ObjectNode values) {
        try {
            InputStream is = this.getClass().getClassLoader().getResourceAsStream(CODE_UUID_FILENAME_STR);
            String filePath = this.getClass().getClassLoader().getResource(CODE_UUID_FILENAME_STR).getPath();
            String fileDir = filePath.substring(0, filePath.lastIndexOf("/"));

            String bkpName = CODE_UUID_FILENAME_STR + DateTime.now().toString();
            String bkpFileName = fileDir + "/" + bkpName;

            ObjectNode oldNode = (ObjectNode) objectMapper.readTree(is);
            ObjectNode newNode = objectMapper.convertValue(values, ObjectNode.class);

            logger.info("Code_UUID backup - Taking backup (shouldAppend: " + shouldAppend + ":)" + bkpFileName);
            objectMapper.writeValue(new File(bkpFileName), oldNode);

            if (shouldAppend) {
                ObjectNode merged = oldNode;
                merged.setAll(newNode);
                codeUUIDNode = merged;
            } else {
                codeUUIDNode = newNode;
            }

            // Writing the entity.json here
            objectMapper.writeValue(new File(filePath), codeUUIDNode);
        } catch (IOException ioe) {
            logger.info("Can't load entity code uuid mapping");
        }
    }

    /**
     * Note: Only one mime type is supported at a time. Pick up the first mime
     * type from the header.
     *
     * @return
     */
    @RequestMapping(value = "/search", method = RequestMethod.POST)
    public ResponseEntity<Response> searchEntity(@RequestHeader HttpHeaders header) {

        ResponseParams responseParams = new ResponseParams();
        Response response = new Response(Response.API_ID.SEARCH, "OK", responseParams);
        JsonNode payload = apiMessage.getRequest().getRequestMapNode();

        response.setResult("API to be supported soon");
        responseParams.setStatus(Response.Status.SUCCESSFUL);

        try {
            shardManager.activateShard(null);

            watch.start("RegistryController.searchEntity");
            JsonNode result = searchService.search(payload);

            // Search is tricky to support LD. Needs a revisit here.

            response.setResult(result);
            responseParams.setStatus(Response.Status.SUCCESSFUL);
            watch.stop("RegistryController.searchEntity");
        } catch (Exception e) {
            logger.error("Exception in controller while searching entities !",
                    e);
            response.setResult("");
            responseParams.setStatus(Response.Status.UNSUCCESSFUL);
            responseParams.setErrmsg(e.getMessage());
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value = "/health", method = RequestMethod.GET)
    public ResponseEntity<Response> health() {

        ResponseParams responseParams = new ResponseParams();
        Response response = new Response(Response.API_ID.HEALTH, "OK", responseParams);

        try {
            HealthCheckResponse healthCheckResult = registryService.health();
            response.setResult(JSONUtil.convertObjectJsonMap(healthCheckResult));
            responseParams.setErrmsg("");
            responseParams.setStatus(Response.Status.SUCCESSFUL);
            logger.debug("Application heath checked : ", healthCheckResult.toString());
        } catch (Exception e) {
            logger.error("Error in health checking!", e);
            HealthCheckResponse healthCheckResult = new HealthCheckResponse(Constants.OPENSABER_REGISTRY_API_NAME,
                    false, null);
            response.setResult(JSONUtil.convertObjectJsonMap(healthCheckResult));
            responseParams.setStatus(Response.Status.UNSUCCESSFUL);
            responseParams.setErrmsg("Error during health check");
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @ResponseBody
    @RequestMapping(value = "/fetchAudit/{id}", method = RequestMethod.GET)
    public ResponseEntity<Response> fetchAudit(@PathVariable("id") String id) {
        ResponseParams responseParams = new ResponseParams();
        Response response = new Response(Response.API_ID.AUDIT, "OK", responseParams);
        // if (auditEnabled) {

        response.setResult("To be implemented soon...");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public ResponseEntity<Response> deleteEntity(@RequestHeader HttpHeaders header) {
        ResponseParams responseParams = new ResponseParams();
        Response response = new Response(Response.API_ID.DELETE, "OK", responseParams);
        response.setResult("DevCon - why delete?");

        /*try {
            String entityId = apiMessage.getRequest().getRequestMap().get(dbConnectionInfoMgr.getUuidPropertyName()).toString();
            RecordIdentifier recordId = RecordIdentifier.parse(entityId);
            String shardId = dbConnectionInfoMgr.getShardId(recordId.getShardLabel());
            shardManager.activateShard(shardId);
            registryService.deleteEntityById(recordId.getUuid());
            responseParams.setErrmsg("");
            responseParams.setStatus(Response.Status.SUCCESSFUL);
        } catch (UnsupportedOperationException e) {
            logger.error("Controller: UnsupportedOperationException while deleting entity !", e);
            response.setResult(null);
            responseParams.setStatus(Response.Status.UNSUCCESSFUL);
            responseParams.setErrmsg(e.getMessage());
        } catch (Exception e) {
            logger.error("Controller: Exception while deleting entity !", e);
            response.setResult(null);
            responseParams.setStatus(Response.Status.UNSUCCESSFUL);
            responseParams.setErrmsg("Meh ! You encountered an error!");
        }*/

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value = "/add", method = RequestMethod.POST)
    public ResponseEntity<Response> addTP2Graph(@RequestParam(value = "id", required = false) String id,
                                                @RequestParam(value = "prop", required = false) String property) {

        ResponseParams responseParams = new ResponseParams();
        Response response = new Response(Response.API_ID.CREATE, "OK", responseParams);
        Map<String, Object> result = new HashMap<>();
        String entityType = apiMessage.getRequest().getEntityType();
        String code;
        String jsonString;

        if (entityType.equals(VISITOR_STR)) {
            code = VISITOR_CODE_STR + getVisitorIdNext();
            apiMessage.getRequest().addField(CODE_STR, code);
        } else {
            code = apiMessage.getRequest().getRequestMapNode().get(entityType).get(CODE_STR).asText();
        }
        jsonString = apiMessage.getRequest().getRequestMapAsString(entityType.equals(VISITOR_STR));

        try {
            JsonNode entityData = (JsonNode) apiMessage.getRequest().getRequestMapNode().get(entityType);
            //logger.info("Add api: entity type " +  + " and shard propery: " + shardManager.getShardProperty());
            logger.info("request: " + entityData.get(shardManager.getShardProperty()));
            Object attribute = entityData.get(shardManager.getShardProperty());
            logger.info("attribute " + attribute);
            Shard shard = shardManager.getShard(attribute);

            watch.start("RegistryController.addToExistingEntity");
            String resultId = registryService.addEntity(jsonString);
            RecordIdentifier recordId = new RecordIdentifier(shard.getShardLabel(), resultId);
            Map resultMap = new HashMap();
            String label = recordId.toString();

            resultMap.put(dbConnectionInfoMgr.getUuidPropertyName(), label);
            resultMap.put(CODE_STR, code);

            logger.info("Added " + code + " -> " + resultId + " into entity.json map");
            codeUUIDNode.put(code, resultId);

            result.put(apiMessage.getRequest().getEntityType(), resultMap);
            response.setResult(result);
            responseParams.setStatus(Response.Status.SUCCESSFUL);
            watch.stop("RegistryController.addToExistingEntity");
            logger.debug("RegistryController : Entity {} added !", resultId);
        } catch (Exception e) {
            logger.error("Exception in controller while adding entity !", e);
            response.setResult(result);
            responseParams.setStatus(Response.Status.UNSUCCESSFUL);
            responseParams.setErrmsg(e.getMessage());
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value = "/read", method = RequestMethod.POST)
    public ResponseEntity<Response> greadGraph2Json(@RequestHeader HttpHeaders header) {
        ResponseParams responseParams = new ResponseParams();
        Response response = new Response(Response.API_ID.READ, "OK", responseParams);

        String label = apiMessage.getRequest().getRequestMap().get(dbConnectionInfoMgr.getUuidPropertyName()).toString();
        RecordIdentifier recordId = RecordIdentifier.parse(label);
        String shardId = dbConnectionInfoMgr.getShardId(recordId.getShardLabel());
        shardManager.activateShard(shardId);
        logger.info("Read Api: shard id: " + recordId.getShardLabel() + " for label: " + label);

        String acceptType = header.getAccept().iterator().next().toString();

        ReadConfigurator configurator = new ReadConfigurator();
        boolean includeSignatures = (boolean) apiMessage.getRequest().getRequestMap().getOrDefault("includeSignatures",
                false);
        configurator.setIncludeSignatures(includeSignatures);
        configurator.setIncludeTypeAttributes(acceptType.equals(Constants.LD_JSON_MEDIA_TYPE));

        try {
            JsonNode resultNode = registryService.getEntity(recordId.getUuid(), configurator);
            // Transformation based on the mediaType
            Data<Object> data = new Data<>(resultNode);
            Configuration config = configurationHelper.getConfiguration(acceptType, Direction.OUT);
            logger.info("config : " + config);
            ITransformer<Object> responseTransformer = transformer.getInstance(config);
            Data<Object> resultContent = responseTransformer.transform(data);
            logger.info("JSON LD: " + resultContent.getData());
            response.setResult(resultContent.getData());

        } catch (Exception e) {
            logger.error("Read Api Exception occurred ", e);
            responseParams.setErr(e.getMessage());
            responseParams.setStatus(Response.Status.UNSUCCESSFUL);
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value = "/read-dev", method = RequestMethod.POST)
    public ResponseEntity<Response> devconRead(@RequestHeader HttpHeaders header) throws IOException {
        ResponseParams responseParams = new ResponseParams();
        Response response = new Response(Response.API_ID.READ, "OK", responseParams);
        String code = apiMessage.getRequest().getRequestMapNode().get("code").asText();

        // At the time of login, there will be extra fields sent.
        JsonNode roleCode = apiMessage.getRequest().getRequestMapNode().get(ROLE_CODE_STR);
        JsonNode stallCode = apiMessage.getRequest().getRequestMapNode().get(STALL_CODE_STR);

        JsonNode osid = codeUUIDNode.get(code);

        RecordIdentifier recordId = RecordIdentifier.parse(osid.textValue());
        String shardId = dbConnectionInfoMgr.getShardId(recordId.getShardLabel());
        shardManager.activateShard(shardId);
        logger.info("Read Api: shard id: " + recordId.getShardLabel() + " for label: " + osid.asText());

        String acceptType = header.getAccept().iterator().next().toString();

        ReadConfigurator configurator = new ReadConfigurator();
        boolean includeSignatures = (boolean) apiMessage.getRequest().getRequestMap().getOrDefault("includeSignatures",
                false);
        configurator.setIncludeSignatures(includeSignatures);
        configurator.setIncludeTypeAttributes(acceptType.equals(Constants.LD_JSON_MEDIA_TYPE));

        try {
            JsonNode resultNode = registryService.getEntity(recordId.getUuid(), configurator);
            // Transformation based on the mediaType
            Data<Object> data = new Data<>(resultNode);
            Configuration config = configurationHelper.getConfiguration(acceptType, Direction.OUT);
            logger.info("config : " + config);
            ITransformer<Object> responseTransformer = transformer.getInstance(config);
            Data<Object> resultContent = responseTransformer.transform(data);
            logger.info("JSON LD: " + resultContent.getData());
            response.setResult(resultContent.getData());

        } catch (Exception e) {
            logger.error("Read-devcon Api Exception occurred ", e);
            responseParams.setErr(e.getMessage());
            responseParams.setStatus(Response.Status.UNSUCCESSFUL);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value = "/read-dev/{code}", method = RequestMethod.GET)
    public ResponseEntity<Response> devconRead2(@RequestHeader HttpHeaders header,
                                                @PathParam("code") String code) throws IOException {
        ResponseParams responseParams = new ResponseParams();
        Response response = new Response(Response.API_ID.READ, "OK", responseParams);
        JsonNode osid = codeUUIDNode.get(code);

        RecordIdentifier recordId = RecordIdentifier.parse(osid.textValue());
        String shardId = dbConnectionInfoMgr.getShardId(recordId.getShardLabel());
        shardManager.activateShard(shardId);
        logger.info("Read Api: shard id: " + recordId.getShardLabel() + " for label: " + osid.asText());

        String acceptType = header.getAccept().iterator().next().toString();

        ReadConfigurator configurator = new ReadConfigurator();
        boolean includeSignatures = false;
        configurator.setIncludeSignatures(includeSignatures);
        configurator.setIncludeTypeAttributes(acceptType.equals(Constants.LD_JSON_MEDIA_TYPE));

        try {
            JsonNode resultNode = registryService.getEntity(recordId.getUuid(), configurator);
            // Transformation based on the mediaType
            Data<Object> data = new Data<>(resultNode);
            Configuration config = configurationHelper.getConfiguration(acceptType, Direction.OUT);
            logger.info("config : " + config);
            ITransformer<Object> responseTransformer = transformer.getInstance(config);
            Data<Object> resultContent = responseTransformer.transform(data);
            logger.info("JSON LD: " + resultContent.getData());
            response.setResult(resultContent.getData());

        } catch (Exception e) {
            logger.error("Read-devcon Api Exception occurred ", e);
            responseParams.setErr(e.getMessage());
            responseParams.setStatus(Response.Status.UNSUCCESSFUL);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }


    @RequestMapping(value = "/load", method = RequestMethod.POST)
    public ResponseEntity<Response> loadConfig(@RequestHeader HttpHeaders header) {
        ResponseParams responseParams = new ResponseParams();
        Response response = new Response(Response.API_ID.LOAD, "OK", responseParams);

        JsonNode reqMap =  apiMessage.getRequest().getRequestMapNode();
        logger.info("Loading key values " + "");

        try {
            populateCodeUUIDNode(reqMap.has("append"), (ObjectNode) reqMap.get("append"));
        } catch (Exception e) {
            logger.error("load Api Exception occurred ", e);
            responseParams.setErr(e.getMessage());
            responseParams.setStatus(Response.Status.UNSUCCESSFUL);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @ResponseBody
    @RequestMapping(value = "/update", method = RequestMethod.POST)
    public ResponseEntity<Response> updateTP2Graph() {
        ResponseParams responseParams = new ResponseParams();
        Response response = new Response(Response.API_ID.UPDATE, "OK", responseParams);

        String jsonString = apiMessage.getRequest().getRequestMapAsString();
        String entityType = apiMessage.getRequest().getEntityType();

        String label = apiMessage.getRequest().getRequestMapNode().get(entityType).get(uuidPropertyName).asText();
        RecordIdentifier recordId = RecordIdentifier.parse(label);
        String shardId = dbConnectionInfoMgr.getShardId(recordId.getShardLabel());
        shardManager.activateShard(shardId);
        logger.info("Read Api: shard id: " + recordId.getShardLabel() + " for label: " + label);

        logger.info("Update Api: shard id: " + recordId.getShardLabel() + " for uuid: " + recordId.getUuid());

        try {
            watch.start("RegistryController.update");
            registryService.updateEntity(recordId.getUuid(), jsonString);
            responseParams.setErrmsg("");
            responseParams.setStatus(Response.Status.SUCCESSFUL);
            watch.stop("RegistryController.update");
            logger.debug("RegistryController: entity updated !");
        } catch (Exception e) {
            logger.error("RegistryController: Exception while updating entity (without id)!", e);
            responseParams.setStatus(Response.Status.UNSUCCESSFUL);
            responseParams.setErrmsg(e.getMessage());
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PreDestroy
    public void beforeShutdown() {
        logger.info("Before shutting down controller, overwriting code uuid map");
        populateCodeUUIDNode(false, codeUUIDNode);
    }

}
