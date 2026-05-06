package pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.operationalcontext;

import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextBoundedContext;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextGlossaryTerm;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextHandoffRule;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextIntegration;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextProcess;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextTeam;

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
