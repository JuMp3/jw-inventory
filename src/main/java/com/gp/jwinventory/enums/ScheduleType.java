package com.gp.jwinventory.enums;

public enum ScheduleType {
    ONCE("Una volta"),
    DAILY("Ogni giorno"),
    WEEKLY("Ogni settimana"),
    MONTHLY("Ogni mese");

    private final String label;

    ScheduleType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
