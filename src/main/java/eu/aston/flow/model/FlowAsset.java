package eu.aston.flow.model;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class FlowAsset {
    private String id;
    private String extName;
    private String url;

    public FlowAsset() {
    }

    public FlowAsset(String id, String extName, String url) {
        this.id = id;
        this.extName = extName;
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getExtName() {
        return extName;
    }

    public void setExtName(String extName) {
        this.extName = extName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "FlowAsset{" + "id='" + id + '\'' + '}';
    }
}
