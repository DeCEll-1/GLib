package org.dark.graphics.util;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import db.twiglib.TwigUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.dark.shaders.ShaderModPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class Twig {

    public static ShipAPI empTargetTwig(ShipAPI ship) {
        if (ShaderModPlugin.hasTwigLib) {
            if (!TwigUtils.isMultiShip(ship)) {
                return ship;
            }

            ShipAPI root = TwigUtils.getRoot(ship);
            List<ShipAPI> children = TwigUtils.getChildNodesAsShips(root);
            children.add(root);

            WeightedRandomPicker<ShipAPI> targetPicker = new WeightedRandomPicker<>();
            for (ShipAPI child : children) {
                if (!child.isAlive()) {
                    continue;
                }
                int weight = 0;
                for (WeaponAPI weapon : child.getAllWeapons()) {
                    if (!weapon.getSlot().isHidden() && !weapon.getSlot().isDecorative()) {
                        weight++;
                    }
                }
                for (ShipEngineAPI engine : child.getEngineController().getShipEngines()) {
                    if (!engine.isSystemActivated()) {
                        weight++;
                    }
                }
                targetPicker.add(child, weight);
            }

            if (targetPicker.getItems().size() > 0) {
                return targetPicker.pick();
            } else {
                return ship;
            }
        } else {
            return ship;
        }
    }

    public static void filterConnections(ShipAPI ship, Collection<ShipAPI> col) {
        if (ShaderModPlugin.hasTwigLib) {
            TwigUtils.filterConnections(ship, col);
        }
    }

    public static List<ShipAPI> getChildren(ShipAPI ship) {
        if (ShaderModPlugin.hasTwigLib) {
            List<ShipAPI> children = TwigUtils.getChildNodesAsShips(ship);
            if (children == null) {
                return new ArrayList<>(0);
            } else {
                return children;
            }
        } else {
            return new ArrayList<>(0);
        }
    }

    public static ShipAPI getRoot(ShipAPI ship) {
        if (ShaderModPlugin.hasTwigLib) {
            return TwigUtils.getRoot(ship);
        } else {
            return ship;
        }
    }

    public static List<ShipAPI> getSortedAreaList(Vector2f loc, List<ShipAPI> list) {
        List<ShipAPI> out;
        if (ShaderModPlugin.hasTwigLib) {
            List<ShipAPI> temp = new ArrayList<>(list);
            Collections.sort(temp, new SortShipsByDistance(loc));

            out = new ArrayList<>(list.size());
            while (temp.size() > 0) {
                ShipAPI ship = temp.get(0);
                if (TwigUtils.isMultiShip(ship)) {
                    out.add(ship);
                    TwigUtils.filterConnections(ship, temp);
                    if (temp.size() > 0 && temp.get(0) == ship) {
                        temp.remove(0);
                    }
                } else {
                    out.add(ship);
                    temp.remove(0);
                }
            }
        } else {
            out = new ArrayList<>(list);
            Collections.sort(out, new SortShipsByDistance(loc));
        }
        return out;
    }

    public static boolean hasChildren(ShipAPI ship) {
        if (ShaderModPlugin.hasTwigLib) {
            return TwigUtils.getNumberOfChildNodes(ship) > 0;
        } else {
            return true;
        }
    }

    public static boolean isRoot(ShipAPI ship) {
        if (ShaderModPlugin.hasTwigLib) {
            return TwigUtils.getRoot(ship) == ship;
        } else {
            return true;
        }
    }

    public static boolean isWithinEmpRange(Vector2f loc, float dist, ShipAPI ship) {
        float distSq = dist * dist;
        if (ship.getShield() != null && ship.getShield().isOn() && ship.getShield().isWithinArc(loc)) {
            if (MathUtils.getDistance(ship.getLocation(), loc) - ship.getShield().getRadius() <= dist) {
                return true;
            }
        }

        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (!weapon.getSlot().isHidden() && weapon.getSlot().getWeaponType() != WeaponType.DECORATIVE && weapon.getSlot().getWeaponType() !=
                    WeaponType.LAUNCH_BAY &&
                    weapon.getSlot().getWeaponType() != WeaponType.SYSTEM) {
                if (MathUtils.getDistanceSquared(weapon.getLocation(), loc) <= distSq) {
                    return true;
                }
            }
        }

        for (ShipEngineAPI engine : ship.getEngineController().getShipEngines()) {
            if (!engine.isSystemActivated()) {
                if (MathUtils.getDistanceSquared(engine.getLocation(), loc) <= distSq) {
                    return true;
                }
            }
        }

        return false;
    }

    private static class SortShipsByDistance implements Comparator<ShipAPI> {

        private final Vector2f loc;

        SortShipsByDistance(Vector2f loc) {
            this.loc = loc;
        }

        @Override
        public int compare(ShipAPI s1, ShipAPI s2) {
            float dist1;
            if (s1.getShield() != null && s1.getShield().isOn() && s1.getShield().isWithinArc(loc)) {
                dist1 = MathUtils.getDistance(s1.getLocation(), loc) - s1.getShield().getRadius();
                dist1 *= dist1;
            } else {
                dist1 = MathUtils.getDistanceSquared(s1.getLocation(), loc);
            }
            float dist2;
            if (s2.getShield() != null && s2.getShield().isOn() && s2.getShield().isWithinArc(loc)) {
                dist2 = MathUtils.getDistance(s2.getLocation(), loc) - s2.getShield().getRadius();
                dist2 *= dist1;
            } else {
                dist2 = MathUtils.getDistanceSquared(s2.getLocation(), loc);
            }
            return Float.compare(dist1, dist2);
        }
    }
}
