package eu.aston.utils;

import java.util.UUID;

public class ID {
    public static String newId(){
        return UUID.randomUUID().toString().replaceAll("-", "");
    }
}
