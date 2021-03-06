package cloud.lagrange.assassin;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

import cloud.lagrange.assassin.Models.Role;


public class Worker {
    public Worker(Assassin parent) {
        parent.getServer().getScheduler().scheduleSyncRepeatingTask(parent, () -> {
            ArrayList<String> frozeThisTick = new ArrayList<>();
            if (Config.freeze)
                Global.Players.stream().filter(p -> p.role == Role._SPEEDRUNNER_).forEach(player -> {
                    Player thisPlayer = Bukkit.getPlayer(player.UUID);
                    if (thisPlayer == null)
                        return;
                    Entity target = getTarget(thisPlayer);
                    if (target instanceof Player) {
                        Player playerTarget = (Player) target;
                        if (Global.Players.stream().anyMatch(
                                p -> p.role == Role._ASSASSIN_ && p.UUID.equals(playerTarget.getUniqueId()))) {
                            Global.Players.stream().filter(p -> p.UUID.equals(playerTarget.getUniqueId())).findFirst()
                                    .get().isFrozen = true;
                            frozeThisTick.add(playerTarget.getUniqueId().toString());
                            drawLine(thisPlayer.getEyeLocation(), playerTarget.getEyeLocation(), 1);
                        }
                    }
                });
            Global.Players.stream().filter(p -> p.isFrozen).forEach(player -> {
                if (!frozeThisTick.contains(player.UUID.toString()))
                    player.isFrozen = false;
            });
            if (Config.compass)
                Global.Players.stream().filter(p -> p.role == Role._ASSASSIN_).forEach(player -> {
                    Player thisPlayer = Bukkit.getPlayer(player.UUID);
                    if (thisPlayer == null)
                        return;
                    Player nearest = getNearestPlayer(thisPlayer);
                    if (nearest == null) {
                        // Randomize compass direction if (no player found, e.g. players r in different
                        // worlds)
                        if (Config.compassRandomizeInDifferentWorlds) {
                            float angle = (float) (Math.random() * Math.PI * 2);
                            float dx = (float) (Math.cos(angle) * 5);
                            float dz = (float) (Math.sin(angle) * 5);
                            thisPlayer.setCompassTarget(thisPlayer.getLocation().add(new Vector(dx, 0, dz)));
                        }
                        return;
                    }
                    thisPlayer.setCompassTarget(nearest.getLocation());
                // Add direction particle
                    if (thisPlayer.getInventory().getItemInMainHand().getType() == Material.COMPASS || thisPlayer.getInventory().getItemInOffHand().getType() == Material.COMPASS){
                        if (Config.compassParticle &&
                                (thisPlayer.getWorld().getEnvironment() != Environment.NETHER || Config.compassParticleInNether)) {
                            drawDirection(thisPlayer.getLocation(), nearest.getLocation(), 3);
                    }
                }
            });
        }, 1L, 1L);
    }

    public Player getNearestPlayer(Player player) {
        double distNear = 0.0D;
        Player playerNear = null;
        for (Player player2 : Bukkit.getOnlinePlayers()) {
            if (player == player2) { continue; }
            if (Global.Players.stream().noneMatch(p -> p.UUID.equals(player2.getUniqueId()) && p.role == Role._SPEEDRUNNER_)) continue;
            if (player.getWorld() != player2.getWorld()) { continue; }

            Location location = player.getLocation();
            double dist = player.getLocation().distance(location);
            if (playerNear == null || dist < distNear) {
                playerNear = player2;
                distNear = dist;
            }
        }
        return playerNear;
    }

    private LivingEntity getTarget(Player player) {
        int range = 60;
        List<Entity> nearbyE = player.getNearbyEntities(range, range, range);
        ArrayList<LivingEntity> livingE = new ArrayList<LivingEntity>();

        for (Entity e : nearbyE) {
            if (e instanceof LivingEntity) {
                livingE.add((LivingEntity) e);
            }
        }

        LivingEntity target = null;
        BlockIterator bItr = new BlockIterator(player, range);
        Block block;
        Location loc;
        int bx, by, bz;
        double ex, ey, ez;
        // loop through player's line of sight
        while (bItr.hasNext()) {
            block = bItr.next();
            if (block.getType() != Material.AIR && block.getType() != Material.WATER) break;
            bx = block.getX();
            by = block.getY();
            bz = block.getZ();
            // check for entities near this block in the line of sight
            for (LivingEntity e : livingE) {
                loc = e.getLocation();
                ex = loc.getX();
                ey = loc.getY();
                ez = loc.getZ();
                if ((bx - .15 <= ex && ex <= bx + 1.15)
                        && (bz - .15 <= ez && ez <= bz + 1.15)
                        && (by - 1 <= ey && ey <= by + 1)) {
                    // entity is close enough, set target and stop
                    target = e;
                    break;
                }
            }
        }
        return target;
    }
    public void drawLine(Location point1, Location point2, double space) {
        World world = point1.getWorld();
        Validate.isTrue(point2.getWorld().equals(world), "Lines cannot be in different worlds!");
        double distance = point1.distance(point2);
        Vector p1 = point1.toVector();
        Vector p2 = point2.toVector();
        Vector vector = p2.clone().subtract(p1).normalize().multiply(space);
        double length = 0;
        for (; length < distance; p1.add(vector)) {
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1);
            if (p1.distance(point1.toVector()) > 1 && p1.distance(p2) > 1) world.spawnParticle(Particle.REDSTONE, p1.getX(), p1.getY(), p1.getZ(), 0, 0, 0, 0, dust);
            length += space;
        }
    }
    public void drawDirection(Location point1, Location point2, double space) {
    	// Draws a single particle in direction of speedrunner (only X & Z coordinates)
    	World world = point1.getWorld();
        Validate.isTrue(point2.getWorld().equals(world), "You have to be in same worlds!");
        Vector p1 = point1.toVector();
        Vector dir = point2.toVector().clone().subtract(p1).setY(0).normalize().multiply(space);
        Vector p = p1.add(dir);
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 255, 0), 1);
    	world.spawnParticle(Particle.REDSTONE, p.getX(), p1.getY()+1.25, p.getZ(), 0, 0, 0, 0, dust);
    }
}
