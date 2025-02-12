package eu.aston.utils;

import eu.aston.flow.model.FlowTask;
import eu.aston.user.UserException;

public class BaseValid {

    public static void require(Object val, String name) {
        if (val == null)
            throw new UserException("require " + name);
    }

    public static void str(String val, int min, int max, String name) {
        if (val == null) {
            if (min >= 0)
                throw new UserException("require " + name);
            return;
        }
        int l = val.length();
        if (l < min)
            throw new UserException("str_min " + min + " " + name);
        if (l > max)
            throw new UserException("str_max " + max + " " + name);
    }

    public static void code(String code, String name, String spec) {
        if(code==null) throw new UserException("require " + name);
        if(code.equals(FlowTask.STEP_ITERATOR) || code.equals(FlowTask.FLOW_RESPONSE)) return;
        if(!code.matches("^[a-zA-Z]{1}[a-zA-Z0-9"+spec+"]+$")) throw new UserException("code name error " + name + " = " + code);
    }
}
