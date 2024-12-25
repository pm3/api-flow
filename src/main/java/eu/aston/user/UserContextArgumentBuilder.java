package eu.aston.user;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import eu.aston.AppConfig;
import eu.aston.flow.FlowDefStore;
import eu.aston.utils.Hash;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.cookie.Cookie;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class UserContextArgumentBuilder implements TypedRequestArgumentBinder<UserContext> {

    public static final String BEARER_ = "Bearer ";
    public static final String X_API_KEY = "X-Api-Key";

    @Override
    public Argument<UserContext> argumentType() {
        return Argument.of(UserContext.class);
    }

    private final static Logger LOGGER = LoggerFactory.getLogger(UserContextArgumentBuilder.class);

    private final FlowDefStore flowDefStore;
    private final byte[] workerApiKey;

    public UserContextArgumentBuilder(FlowDefStore flowDefStore, AppConfig appConfig) {
        this.flowDefStore = flowDefStore;
        this.workerApiKey = appConfig.getWorkerApiKey()!=null && appConfig.getWorkerApiKey().length()>1 ? appConfig.getWorkerApiKey().getBytes(StandardCharsets.UTF_8) : null;
    }

    @Override
    public BindingResult<UserContext> bind(ArgumentConversionContext<UserContext> context, HttpRequest<?> source) {

        String cookieJwt = source.getCookies().findCookie("jwt_session").map(Cookie::getValue).orElse(null);
        if (cookieJwt != null) {
            try {
                DecodedJWT jwt = JWT.decode(cookieJwt);
                UserContext  userContext = flowDefStore.verifyJwt(jwt);
                return () -> Optional.of(userContext);
            } catch (Exception e) {
                LOGGER.warn("cookie jwt error {}", e.getMessage());
                throw new AuthException("invalid session jwt token " + e.getMessage(), true);
            }
        }

        String auth = source.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith(BEARER_)) {
            try {
                DecodedJWT jwt = JWT.decode(auth.substring(BEARER_.length()));
                UserContext  userContext = flowDefStore.verifyJwt(jwt);
                return () -> Optional.of(userContext);
            } catch (Exception e) {
                LOGGER.warn("bearer jwt error {}", e.getMessage());
                throw new AuthException("invalid bearer jwt token " + e.getMessage(), true);
            }
        }
        String xApiKey = source.getHeaders().get(X_API_KEY);
        if (xApiKey!=null) {
            if(workerApiKey!=null){
                String pathKey = Hash.hmacSha1(source.getPath().getBytes(StandardCharsets.UTF_8), workerApiKey);
                if(!Objects.equals(pathKey, xApiKey)){
                    throw new AuthException("invalid api key", true);
                }
            }
            UserContext userContext = new UserContext(xApiKey, xApiKey);
            return () -> Optional.of(userContext);
        }

        return ()->Optional.of(new UserContext(null, null));
    }
}
