package eu.aston.flow.def;

import java.util.List;

public class AuthDef {
    String code;
    List<JwtIssuerDef> jwtIssuers;
    List<BaseAuthDef> adminUsers;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public List<JwtIssuerDef> getJwtIssuers() {
        return jwtIssuers;
    }

    public void setJwtIssuers(List<JwtIssuerDef> jwtIssuers) {
        this.jwtIssuers = jwtIssuers;
    }

    public List<BaseAuthDef> getAdminUsers() {
        return adminUsers;
    }

    public void setAdminUsers(List<BaseAuthDef> adminUsers) {
        this.adminUsers = adminUsers;
    }
}
