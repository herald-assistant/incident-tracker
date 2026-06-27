package pl.mkn.tdw.features.incidentanalysis.evidence.provider.operationalcontext;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextBoundedContext;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextGlossaryTerm;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextHandoffRule;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextIntegration;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextProcess;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextTeam;

import java.util.List;

record OperationalContextMatchBundle(
        List<OperationalContextMatchedEntry<OperationalContextSystem>> systemMatches,
        List<OperationalContextMatchedEntry<OperationalContextIntegration>> integrationMatches,
        List<OperationalContextMatchedEntry<OperationalContextProcess>> processMatches,
        List<OperationalContextMatchedEntry<OperationalContextRepository>> repositoryMatches,
        List<OperationalContextMatchedEntry<OperationalContextBoundedContext>> boundedContextMatches,
        List<OperationalContextMatchedEntry<OperationalContextTeam>> teamMatches,
        List<OperationalContextMatchedEntry<OperationalContextGlossaryTerm>> glossaryMatches,
        List<OperationalContextMatchedEntry<OperationalContextHandoffRule>> handoffMatches
) {
}
