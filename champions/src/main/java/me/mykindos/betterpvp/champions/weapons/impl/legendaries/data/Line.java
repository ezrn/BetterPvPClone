package me.mykindos.betterpvp.champions.weapons.impl.legendaries.data;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class Line {
    private final List<Location> points;

    public Line(List<Location> points) {
        this.points = new ArrayList<>(points);
    }

    public List<Location> getPoints() {
        return points;
    }
}
