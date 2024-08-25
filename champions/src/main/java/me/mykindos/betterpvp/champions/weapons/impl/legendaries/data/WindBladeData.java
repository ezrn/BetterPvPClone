package me.mykindos.betterpvp.champions.weapons.impl.legendaries.data;

import lombok.Data;

@Data
public class WindBladeData {
    private int charges = 0;

    public void useCharge() {
        charges = Math.max(0, charges - 1);
    }

    public void addCharge() {
        charges++;
    }
}
