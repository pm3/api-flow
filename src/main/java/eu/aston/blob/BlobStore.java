package eu.aston.blob;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.aston.flow.model.FlowAsset;
import eu.aston.flow.model.FlowCase;
import io.micronaut.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlobStore {

    private final static Logger LOGGER = LoggerFactory.getLogger(BlobStore.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AzureBlobAuthBuilder azureBlobAuthBuilder;

    public BlobStore(HttpClient httpClient, ObjectMapper objectMapper, AzureBlobAuthBuilder azureBlobAuthBuilder) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.azureBlobAuthBuilder = azureBlobAuthBuilder;
    }

    public void saveAsset(String flowType, String assetId, byte[] data, String fileName) throws IOException{
        String path = "/"+flowType+"/assets/"+assetId;
        MediaType mediaType = MediaType.forExtension(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM_TYPE);
        saveContent(path, mediaType.toString(), data, fileName);
    }

    public FlowAsset loadAssetInfo(String flowType, String assetId) throws Exception {
        String path = "/"+flowType+"/assets/"+assetId;
        HttpRequest r = azureBlobAuthBuilder.path(path).build("HEAD", new byte[0]).build();
        HttpResponse<String> resp = httpClient.send(r, HttpResponse.BodyHandlers.ofString());
        if(resp.statusCode()==200){
            FlowAsset asset =  new FlowAsset();
            asset.setId(assetId);
            asset.setExtName(resp.headers().firstValue("x-ms-meta-filename").orElse(null));
            return asset;
        }
        return null;
    }

    public String createAssetUrl(String flowType, String assetId) throws Exception {
        String path = "/"+flowType+"/assets/"+assetId;
        return azureBlobAuthBuilder.generateSAS(path, Duration.ofMinutes(10).toMillis());
    }

    public FlowCase loadFinalCase(String flowType, String flowCaseId) throws Exception {
        String path = "/"+flowType+"/flows/"+flowCaseId+".json";
        HttpResponse<InputStream> resp = loadStream(path);
        if(resp!=null){
            return objectMapper.readValue(resp.body(), FlowCase.class);
        }
        return null;
    }

    public void saveFinalCase(String flowType, String flowCaseId, FlowCase flowCase) throws IOException{
        String path = "/"+flowType+"/flows/"+flowCaseId+".json";
        String body = objectMapper.writeValueAsString(flowCase);
        saveContent(path,
                    MediaType.APPLICATION_JSON,
                    body.getBytes(StandardCharsets.UTF_8),
                    "case.json");
    }

    private HttpResponse<InputStream> loadStream(String path) throws Exception {
        HttpRequest.Builder r = azureBlobAuthBuilder.path(path).buildGET();
        HttpResponse<InputStream> resp = httpClient.send(r.build(), HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() == 200) return resp;
        if (resp.statusCode() == 404) return null;
        String body = stream2String(resp.body());
        throw new Exception("response error " + resp.statusCode() + " " + body);
    }

    private void saveContent(String path, String contentType, byte[] data, String fileName) throws IOException {
        try {
            HttpRequest.Builder r = azureBlobAuthBuilder.path(path)
                    .header("Content-Type", contentType)
                    .header("x-ms-blob-type", "BlockBlob")
                    .header("x-ms-meta-filename", fileName)
                    .build("PUT", data);
            HttpResponse<String> resp = httpClient.send(r.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() > 299) {
                throw new Exception("response error " + resp.statusCode() + " " + resp.body());
            }
        } catch (Exception e) {
            throw new IOException("save blob " + path, e);
        }
    }

    private String stream2String(InputStream body) throws IOException {
        return new String(body.readAllBytes(), StandardCharsets.UTF_8);
    }
}
