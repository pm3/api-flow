package eu.aston.flow.def;

public class BaseAuthDef {
    private String login;
    private String password;
    private String tenant;

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    @Override
    public String toString() {
        return "BaseAuthDef{" + "login='" + login + '\'' + '}';
    }
}
