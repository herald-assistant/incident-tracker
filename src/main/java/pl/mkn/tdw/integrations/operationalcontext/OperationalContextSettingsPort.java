package pl.mkn.tdw.integrations.operationalcontext;

public interface OperationalContextSettingsPort {

    boolean isEnabled();

    int getMaxItemsPerType();

    int getMaxGlossaryTerms();

    int getMaxHandoffRules();
}
