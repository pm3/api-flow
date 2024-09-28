package eu.aston.flow.ognl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreType;

@JsonIgnoreType
@JsonIgnoreProperties({"stackTrace", "cause", "localizedMessage", "suppressed"})
public class WaitingException extends RuntimeException {

    public WaitingException(String message) {
        super("waiting "+message);
    }

    public String getErrorType() {
        return WaitingException.class.getSimpleName();
    }
}
