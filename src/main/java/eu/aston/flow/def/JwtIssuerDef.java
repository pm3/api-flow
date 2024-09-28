package eu.aston.flow.def;

public class JwtIssuerDef {
    private String issuer;
    private String aud;
    private String url;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAud() {
        return aud;
    }

    public void setAud(String aud) {
        this.aud = aud;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "JwtIssuerDef{" + "issuer='" + issuer + '\'' + ", aud='" + aud + '\'' + ", url='" + url + '\'' + '}';
    }
}
