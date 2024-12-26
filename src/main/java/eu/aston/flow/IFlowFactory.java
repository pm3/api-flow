package eu.aston.flow;

import java.io.File;

public interface IFlowFactory {

    IFlowDef createFlow(File f) throws Exception;
}
