package pl.mkn.tdw.features.uiexplorer.report;

import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultResponse;

public interface UiExplorerResultReportAssembler {

    UiExplorerReportAssembly assemble(String reportId, UiExplorerResultResponse result);
}

