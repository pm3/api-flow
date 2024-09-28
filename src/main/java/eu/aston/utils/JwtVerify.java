package eu.aston.utils;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import eu.aston.user.UserContext;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtVerify {

    private final static Logger LOGGER = LoggerFactory.getLogger(JwtVerify.class);

    private final Map<String, KeyConfig> keyConfigs = new ConcurrentHashMap<>();
    private final Map<String, IssuerCfg> issuerConfigMap = new HashMap<>();
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public JwtVerify(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public void addIssuer(String issuer, String aud, String url, String tenant){
        issuerConfigMap.put(issuer + "#" + aud, new IssuerCfg(issuer, aud, url, tenant));
    }

    public UserContext verify(DecodedJWT jwt) throws Exception {
        KeyConfig keyConfig = keyConfigs.get(jwt.getKeyId());
        if (keyConfig == null) createVerifiers(jwt.getIssuer(), jwt.getAudience());
        keyConfig = keyConfigs.get(jwt.getKeyId());
        if (keyConfig == null) throw new Exception("invalid keyId");
        JWT.require(keyConfig.algorithm).withAudience(keyConfig.issuerConfig.aud()).build().verify(jwt);
        String user = jwt.getClaim("email").asString();
        if(user==null) user = jwt.getSubject();
        return new UserContext(keyConfig.issuerConfig.tenant(), user);
    }

    private void createVerifiers(String issuer, List<String> audiences) throws Exception {
        if (issuer == null || audiences == null) throw new Exception("invalid jwt, issuer and audience is required");
        IssuerCfg issuerConfig = null;
        for (Iterator<String> i = audiences.iterator(); issuerConfig == null && i.hasNext(); ) {
            issuerConfig = issuerConfigMap.get(issuer + "#" + i.next());
        }
        if (issuerConfig == null) throw new Exception("unsupported issuer " + issuer);
        List<OpenIdKey> keys = loadKeys(issuerConfig.url());
        for (OpenIdKey key : keys) {
            if (key == null || key.e() == null || key.n() == null) continue;
            BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(key.e()));
            BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(key.n()));
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(new RSAPublicKeySpec(n, e));
            Algorithm algorithm = Algorithm.RSA256(new PublicKeyProvider(publicKey, key.kid()));
            keyConfigs.put(key.kid(), new KeyConfig(key.kid(), algorithm, issuerConfig));
        }
    }

    private List<OpenIdKey> loadKeys(String keysPath) throws Exception {
        HttpResponse<String> resp = httpClient.send(HttpRequest.newBuilder()
                .uri(new URI(keysPath))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) {
            OpenIdKeys keys = objectMapper.readValue(resp.body(), OpenIdKeys.class);
            if (keys != null && keys.keys() != null) return keys.keys();
        } else {
            LOGGER.warn("load keys error {} - {} {}",keysPath, resp.statusCode(), resp.body());
        }
        throw new Exception("load keys " + resp.statusCode());
    }

    public void clear() {
        keyConfigs.clear();
        issuerConfigMap.clear();
    }

    public record IssuerCfg(String issuer, String aud, String url, String tenant){}

    public record OpenIdKeys(List<OpenIdKey> keys){}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OpenIdKey(String kid, String e, String n){}

    public record KeyConfig(String keyId, Algorithm algorithm, IssuerCfg issuerConfig){}

    public static class PublicKeyProvider implements RSAKeyProvider {
        private final RSAPublicKey publicKey;
        private final String keyId;

        public PublicKeyProvider(RSAPublicKey publicKey, String keyId) {
            this.publicKey = publicKey;
            this.keyId = keyId;
        }

        @Override
        public RSAPublicKey getPublicKeyById(String s) {
            return publicKey;
        }

        @Override
        public RSAPrivateKey getPrivateKey() {
            return null;
        }

        @Override
        public String getPrivateKeyId() {
            return keyId;
        }
    }

}
