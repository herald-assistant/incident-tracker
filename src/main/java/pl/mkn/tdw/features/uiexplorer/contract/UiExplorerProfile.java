package pl.mkn.tdw.features.uiexplorer.contract;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import static pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId.ACTIONS_AND_OUTCOMES;
import static pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId.DATA_AND_SERVICES;
import static pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId.FORMS_AND_RULES;
import static pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId.NAVIGATION_AND_ACCESS;
import static pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId.OVERVIEW;
import static pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId.SCREEN_STRUCTURE;
import static pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId.STATE_AND_SYNCHRONIZATION;
import static pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId.VARIANTS_AND_FAILURES;
import static pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode.COMPACT;
import static pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode.DEEP;

public enum UiExplorerProfile {
    FUNCTIONAL_DOCUMENTATION(
            "Dokumentacja funkcjonalna",
            "Business-readable documentation of what the user sees and can do."
    ),
    CHANGE_PREPARATION(
            "Przygotowanie zmiany",
            "Input for defining a change, its likely impact and unresolved decisions."
    ),
    TECHNICAL_DOCUMENTATION(
            "Dokumentacja techniczna",
            "Technical handoff grounded in navigation, data, state and source relationships."
    );

    private final String label;
    private final String description;

    UiExplorerProfile(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public Map<UiExplorerSectionId, UiExplorerSectionMode> defaultSectionModes() {
        var modes = new EnumMap<UiExplorerSectionId, UiExplorerSectionMode>(UiExplorerSectionId.class);
        switch (this) {
            case FUNCTIONAL_DOCUMENTATION -> {
                modes.put(OVERVIEW, DEEP);
                modes.put(NAVIGATION_AND_ACCESS, COMPACT);
                modes.put(SCREEN_STRUCTURE, DEEP);
                modes.put(ACTIONS_AND_OUTCOMES, DEEP);
                modes.put(FORMS_AND_RULES, DEEP);
                modes.put(DATA_AND_SERVICES, COMPACT);
                modes.put(STATE_AND_SYNCHRONIZATION, COMPACT);
                modes.put(VARIANTS_AND_FAILURES, DEEP);
            }
            case CHANGE_PREPARATION -> {
                modes.put(OVERVIEW, COMPACT);
                modes.put(NAVIGATION_AND_ACCESS, COMPACT);
                modes.put(SCREEN_STRUCTURE, COMPACT);
                modes.put(ACTIONS_AND_OUTCOMES, DEEP);
                modes.put(FORMS_AND_RULES, DEEP);
                modes.put(DATA_AND_SERVICES, DEEP);
                modes.put(STATE_AND_SYNCHRONIZATION, DEEP);
                modes.put(VARIANTS_AND_FAILURES, DEEP);
            }
            case TECHNICAL_DOCUMENTATION -> {
                modes.put(OVERVIEW, COMPACT);
                modes.put(NAVIGATION_AND_ACCESS, DEEP);
                modes.put(SCREEN_STRUCTURE, DEEP);
                modes.put(ACTIONS_AND_OUTCOMES, COMPACT);
                modes.put(FORMS_AND_RULES, COMPACT);
                modes.put(DATA_AND_SERVICES, DEEP);
                modes.put(STATE_AND_SYNCHRONIZATION, DEEP);
                modes.put(VARIANTS_AND_FAILURES, COMPACT);
            }
        }
        return Collections.unmodifiableMap(modes);
    }
}

