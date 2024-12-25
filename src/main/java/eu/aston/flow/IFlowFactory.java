package eu.aston.flow;

import java.io.File;

import eu.aston.flow.def.IFlowDef;

public interface IFlowFactory {

    IFlowDef createFlow(File f) throws Exception;
}
