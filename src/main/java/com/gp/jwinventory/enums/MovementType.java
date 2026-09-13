package com.gp.jwinventory.enums;

public enum MovementType {
    IN("Entrata"),
    OUT("Uscita"),
    SET("Rettifica"),
    INVENTORY("Inventario");

    private final String label;

    MovementType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
