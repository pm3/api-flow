package eu.aston.blob;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AzureBlobAuthBuilder {
    private final String blobUrl;
    private final String blobAccount;
    private final byte[] blobAuth;
    private final DateTimeFormatter utcFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH).withZone(ZoneId.of("GMT"));

    public AzureBlobAuthBuilder(String blobUrl, String blobAuth) {
        this.blobUrl = blobUrl;
        this.blobAuth = Base64.getDecoder().decode(blobAuth);
        this.blobAccount = blobUrl.substring(8, blobUrl.indexOf('.'));
    }

    public AzureBlobAuthBuilderBuilder path(String path) {
        return new AzureBlobAuthBuilderBuilder(path);
    }

    public class AzureBlobAuthBuilderBuilder {
        private final String path;
        private final Map<String, String> headers = new HashMap<>();

        AzureBlobAuthBuilderBuilder(String path) {
            this.path = path;
            headers.put("x-ms-version", "2019-12-12");
        }

        public AzureBlobAuthBuilderBuilder header(String name, String value) {
            if (value != null) headers.put(name, value);
            else headers.remove(name);
            return this;
        }

        public HttpRequest.Builder buildGET() throws URISyntaxException, NoSuchAlgorithmException, InvalidKeyException {
            return build("GET", null);
        }

        public HttpRequest.Builder build(String method, byte[] body) throws URISyntaxException, NoSuchAlgorithmException, InvalidKeyException {
            HttpRequest.Builder b = HttpRequest.newBuilder();
            headers.put("x-ms-date", utcFormatter.format(Instant.now()));
            StringBuilder token = new StringBuilder();
            if ("GET".equals(method)) {
                b.GET();
                b.uri(new URI(blobUrl + path));
                token.append("GET\n\n\n\n\n\n\n\n\n\n\n\n");
            } else {
                b.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
                b.uri(new URI(blobUrl + path));
                token.append(method).append("\n")
                        .append("\n")    /*Content-Encoding*/
                        .append("\n")    /*Content-Language*/
                        .append(body.length > 0 ? String.valueOf(body.length) : "").append("\n")    /*Content-Length (empty string when zero)*/
                        .append("\n")    /*Content-MD5*/
                        .append(headers.getOrDefault("Content-Type", "")).append("\n")    /*Content-Type*/
                        .append("\n")    /*Date*/
                        .append("\n")    /*If-Modified-Since */
                        .append("\n")    /*If-Match*/
                        .append("\n")    /*If-None-Match*/
                        .append("\n")    /*If-Unmodified-Since*/
                        .append("\n");   /*Range*/
            }
            List<String> keys = new ArrayList<>(headers.keySet());
            keys.sort(String::compareTo);
            for (String key : keys) {
                String val = headers.get(key);
                b.header(key, val);
                if (key.equals("Content-Type")) continue;
                token.append(key).append(":").append(val).append("\n");
            }
            int posQ = path.indexOf("?");
            token.append("/").append(blobAccount).append(posQ > 0 ? path.substring(0, posQ) : path);
            if (posQ > 0) {
                String params = path.substring(posQ + 1);
                token.append("\n").append(params.replace('&', '\n').replace('=', ':'));
            }
            //System.out.println("'" + token + "'");
            String tokenSign = Base64.getEncoder().encodeToString(hmacSHA256(token.toString(), blobAuth));
            b.header("Authorization", "SharedKey " + blobAccount + ":" + tokenSign);
            return b;
        }
    }

    public static byte[] hmacSHA256(String data, byte[] key) throws NoSuchAlgorithmException, InvalidKeyException {
        String algorithm = "HmacSHA256";
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(key, algorithm));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private final DateTimeFormatter sasDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).withZone(ZoneId.of("UTC"));

    public String generateSAS(String path, long expire) throws NoSuchAlgorithmException, InvalidKeyException {

        String res = "/blob/" + blobAccount + path;
        String st = sasDateFormatter.format(Instant.now()) + "Z";
        String se = sasDateFormatter.format(Instant.now().plus(expire, ChronoUnit.MILLIS)) + "Z";
        String sp = "r";
        String spr = "https";
        String sv = "2021-06-08";
        String sr = "b";

        String token = String.join("\n",
                sp,
                st,
                se,
                res,
                "",//signedIdentifier
                "", //signedIP
                spr,
                sv,
                sr,
                "",//signedSnapshotTime
                "", //signedEncryptionScope
                "",
                "",
                "",
                "",
                ""//empy line last enter
        );
        String tokenSign = Base64.getEncoder().encodeToString(hmacSHA256(token, blobAuth));

        return String.join("",
                blobUrl + path, "?sp=", sp, "&st=", st, "&se=", se, "&spr=", spr, "&sv=", sv, "&sr=", sr,
                "&sig=", URLEncoder.encode(tokenSign, StandardCharsets.UTF_8));
    }
    
}
