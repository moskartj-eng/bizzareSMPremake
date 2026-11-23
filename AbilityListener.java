package de.trimabilities;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.block.Block;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.*;

public final class AbilityListener implements Listener {

    private final TrimAbilitiesPlugin plugin;
    private final Map<UUID, Map<Ability, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Long> noFallUntil = new HashMap<>();

    @EventHandler
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL
                && e.getEntity() instanceof Player pl
                && noFallUntil.getOrDefault(pl.getUniqueId(), 0L) > System.currentTimeMillis()) {
            e.setCancelled(true);
        }
    }

    private void noFall(Player p, int seconds) {
        noFallUntil.put(p.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    public AbilityListener(TrimAbilitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cooldowns.remove(e.getPlayer().getUniqueId());
    }

    /** F-Taste (Offhand-Swap) löst die Fähigkeit aus. */
    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPermission("trimabilities.use")) return;
        if (plugin.getConfig().getBoolean("require-sneak", false) && !p.isSneaking()) return;

        Map.Entry<Ability, Integer> found = detect(p);
        if (found == null) return; // kein Trim -> normales Item-Tauschen

        e.setCancelled(true);
        Ability ability = found.getKey();
        int pieces = found.getValue();

        long now = System.currentTimeMillis();
        long until = cooldowns.computeIfAbsent(p.getUniqueId(), k -> new EnumMap<>(Ability.class))
                .getOrDefault(ability, 0L);
        if (until > now) {
            long left = (until - now + 999) / 1000;
            p.sendActionBar(Component.text("Cooldown: " + left + "s", NamedTextColor.RED));
            return;
        }

        activate(p, ability, pieces);
        int cd = plugin.getConfig().getInt("cooldowns." + ability.key(), 30);
        cooldowns.get(p.getUniqueId()).put(ability, now + cd * 1000L);
        p.sendActionBar(Component.text(ability.name() + " aktiviert!", NamedTextColor.GOLD));
    }

    /** Findet das Trim-Muster, das am häufigsten getragen wird. */
    private Map.Entry<Ability, Integer> detect(Player p) {
        Map<Ability, Integer> counts = new EnumMap<>(Ability.class);
        List<String> disabled = plugin.getConfig().getStringList("disabled");

        for (ItemStack piece : p.getInventory().getArmorContents()) {
            if (piece == null || !(piece.getItemMeta() instanceof ArmorMeta meta)) continue;
            ArmorTrim trim = meta.getTrim();
            if (trim == null) continue;
            NamespacedKey key = Registry.TRIM_PATTERN.getKey(trim.getPattern());
            if (key == null) continue;
            Ability a = Ability.fromKey(key.getKey());
            if (a == null || disabled.contains(a.key())) continue;
            counts.merge(a, 1, Integer::sum);
        }

        int min = plugin.getConfig().getInt("min-pieces", 2);
        return counts.entrySet().stream()
                .filter(en -> en.getValue() >= min)
                .max(Map.Entry.comparingByValue())
                .orElse(null);
    }

    // ---------------------------------------------------------------- abilities

    private void activate(Player p, Ability ability, int pieces) {
        // Stärke skaliert mit Anzahl der Teile (1-4)
        int amp = Math.max(0, pieces - 2);
        int sec = 4 + pieces * 2;
        World w = p.getWorld();
        Location loc = p.getLocation();
        Vector dir = p.getEyeLocation().getDirection();

        switch (ability) {
            case SENTRY -> {
                for (int i = -1; i <= 1; i++) {
                    Vector d = dir.clone().rotateAroundY(Math.toRadians(i * 8)).multiply(3);
                    Arrow a = p.launchProjectile(Arrow.class, d);
                    a.setDamage(4 + pieces);
                    a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                }
                w.playSound(loc, Sound.ENTITY_ARROW_SHOOT, 1f, 1f);
            }
            case DUNE -> {
                p.setVelocity(dir.clone().setY(Math.max(0.2, dir.getY() * 0.5)).normalize().multiply(1.6 + 0.2 * pieces));
                w.spawnParticle(Particle.CLOUD, loc, 30, 0.4, 0.2, 0.4, 0.05);
                w.playSound(loc, Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1f);
            }
            case COAST -> {
                for (LivingEntity t : nearby(p, 6)) {
                    Vector push = t.getLocation().toVector().subtract(loc.toVector()).setY(0.4).normalize().multiply(1.5);
                    t.setVelocity(push);
                }
                effect(p, PotionEffectType.DOLPHINS_GRACE, sec, 0);
                w.spawnParticle(Particle.SPLASH, loc, 80, 2, 0.5, 2, 0.1);
                w.playSound(loc, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1f, 1f);
            }
            case WILD -> grapple(p, pieces);
            case WARD -> {
                effect(p, PotionEffectType.ABSORPTION, sec, 1 + amp);
                effect(p, PotionEffectType.RESISTANCE, sec, amp);
                w.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4);
                w.playSound(loc, Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
            }
            case EYE -> laser(p, pieces);
            case VEX -> {
                noFall(p, 4);
                p.setVelocity(dir.clone().multiply(2.4 + 0.2 * pieces));
                w.spawnParticle(Particle.CLOUD, loc.clone().add(0, 1, 0), 40, 0.4, 0.4, 0.4, 0.1);
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.05);
                w.playSound(loc, Sound.ENTITY_VEX_CHARGE, 1f, 1f);
            }
            case TIDE -> {
                for (LivingEntity t : nearby(p, 8)) {
                    t.setVelocity(loc.toVector().subtract(t.getLocation().toVector()).normalize().multiply(1.2));
                }
                w.spawnParticle(Particle.BUBBLE_POP, loc.clone().add(0, 1, 0), 60, 3, 1, 3, 0.05);
                w.playSound(loc, Sound.ENTITY_GUARDIAN_ATTACK, 1f, 1f);
            }
            case SNOUT -> {
                effect(p, PotionEffectType.STRENGTH, sec, amp);
                effect(p, PotionEffectType.FIRE_RESISTANCE, sec, 0);
                w.spawnParticle(Particle.ANGRY_VILLAGER, loc.clone().add(0, 2, 0), 10, 0.3, 0.2, 0.3);
                w.playSound(loc, Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 1f, 1f);
            }
            case RIB -> {
                for (LivingEntity t : nearby(p, 7)) t.setFireTicks(20 * sec);
                w.spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 120, 3, 0.6, 3, 0.05);
                w.playSound(loc, Sound.ITEM_FIRECHARGE_USE, 1f, 1f);
            }
            case SPIRE -> {
                RayTraceResult r = w.rayTrace(p.getEyeLocation(), dir, 40, FluidCollisionMode.NEVER, true, 0.6, en -> en != p);
                Location target = r != null ? r.getHitPosition().toLocation(w) : loc.clone().add(dir.clone().multiply(15));
                w.strikeLightning(target);
            }
            case SILENCE -> {
                for (LivingEntity t : nearby(p, 8)) {
                    effect(t, PotionEffectType.DARKNESS, sec, 0);
                    effect(t, PotionEffectType.SLOWNESS, sec, 1);
                }
                w.spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0, 1, 0), 40, 2, 0.5, 2, 0.02);
                w.playSound(loc, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1f, 1f);
            }
            case RAISER -> {
                noFall(p, 1);
                for (LivingEntity t : nearby(p, 7)) {
                    t.setVelocity(new Vector(0, 1.6 + 0.2 * pieces, 0));
                    w.spawnParticle(Particle.CLOUD, t.getLocation(), 20, 0.3, 0.1, 0.3, 0.05);
                }
                w.spawnParticle(Particle.EXPLOSION, loc, 3, 1, 0.2, 1);
                w.playSound(loc, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 0.8f);
            }
            case HOST -> {
                for (LivingEntity t : nearby(p, 8)) {
                    Location base = t.getLocation();
                    for (int dy = 0; dy <= 1; dy++) {
                        Block b = base.clone().add(0, dy, 0).getBlock();
                        if (b.getType() == Material.AIR) {
                            b.setType(Material.COBWEB);
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                if (b.getType() == Material.COBWEB) b.setType(Material.AIR);
                            }, 20L * sec);
                        }
                    }
                }
                w.spawnParticle(Particle.ITEM_COBWEB, loc.clone().add(0, 1, 0), 60, 3, 0.6, 3, 0.02);
                w.playSound(loc, Sound.ENTITY_SPIDER_AMBIENT, 1f, 0.7f);
            }
            case SHAPER -> {
                effect(p, PotionEffectType.INVISIBILITY, sec * 2, 0);
                effect(p, PotionEffectType.RESISTANCE, sec * 2, 1 + amp);
                w.spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4);
                w.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            }
            case WAYFINDER -> {
                effect(p, PotionEffectType.SPEED, sec, 1 + amp);
                effect(p, PotionEffectType.JUMP_BOOST, sec, 1 + amp);
                w.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.02);
            }
            case FLOW -> {
                p.setVelocity(new Vector(dir.getX() * 0.6, 1.5 + 0.1 * pieces, dir.getZ() * 0.6));
                effect(p, PotionEffectType.SLOW_FALLING, 5, 0);
                w.spawnParticle(Particle.GUST, loc, 3, 0.2, 0.1, 0.2);
                w.playSound(loc, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1f);
            }
            case BOLT -> {
                for (LivingEntity t : nearby(p, 6)) {
                    w.strikeLightningEffect(t.getLocation());
                    t.damage(4 + pieces * 1.5, p);
                }
                w.strikeLightningEffect(loc);
                w.playSound(loc, Sound.ITEM_TRIDENT_THUNDER, 1f, 1f);
            }
        }
    }

    /** Eye: 3 Sekunden Laserstrahl, folgt dem Blick, macht Schaden. */
    private void laser(Player p, int pieces) {
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.5f);
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || ticks >= 60) { cancel(); return; }
                Location eye = p.getEyeLocation();
                Vector d = eye.getDirection();
                double range = 40;
                RayTraceResult hit = w.rayTrace(eye, d, range, FluidCollisionMode.NEVER, true, 0.3, en -> en != p && en instanceof LivingEntity);
                double len = range;
                if (hit != null) len = hit.getHitPosition().distance(eye.toVector());
                Particle.DustOptions red = new Particle.DustOptions(Color.RED, 1.4f);
                for (double t = 1; t < len; t += 0.5) {
                    w.spawnParticle(Particle.DUST, eye.clone().add(d.clone().multiply(t)).subtract(0, 0.15, 0), 1, 0, 0, 0, 0, red);
                }
                if (hit != null && hit.getHitEntity() instanceof LivingEntity target && ticks % 5 == 0) {
                    target.damage(1.5 + pieces * 0.5, p);
                    target.setNoDamageTicks(0);
                }
                if (ticks % 10 == 0) w.playSound(eye, Sound.ENTITY_GUARDIAN_ATTACK, 0.6f, 2f);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Wild: Enterhaken an Block oder Entity, zieht dich hin. */
    private void grapple(Player p, int pieces) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector d = eye.getDirection();
        double range = 40;
        RayTraceResult hit = w.rayTrace(eye, d, range, FluidCollisionMode.NEVER, true, 0.5, en -> en != p);
        if (hit == null) {
            p.sendActionBar(Component.text("Kein Ziel in Reichweite", NamedTextColor.RED));
            return;
        }
        final Entity targetEntity = hit.getHitEntity();
        final Vector fixedPoint = hit.getHitPosition();
        w.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 1f, 0.8f);
        noFall(p, 8);
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || ticks >= 60) { cancel(); return; }
                Vector target = (targetEntity != null && targetEntity.isValid())
                        ? targetEntity.getLocation().add(0, targetEntity.getHeight() / 2, 0).toVector()
                        : fixedPoint;
                Vector from = p.getLocation().add(0, 1, 0).toVector();
                Vector diff = target.clone().subtract(from);
                double dist = diff.length();
                Vector start = p.getEyeLocation().subtract(0, 0.3, 0).toVector();
                for (double t = 0; t < dist; t += 0.6) {
                    Vector pt = start.clone().add(diff.clone().normalize().multiply(t));
                    w.spawnParticle(Particle.CRIT, pt.getX(), pt.getY(), pt.getZ(), 1, 0, 0, 0, 0);
                }
                if (dist < 2.0) { cancel(); return; }
                p.setVelocity(diff.normalize().multiply(0.9 + 0.1 * pieces));
                p.setFallDistance(0);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private List<LivingEntity> nearby(Player p, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : p.getNearbyEntities(r, r, r)) {
            if (e instanceof LivingEntity le && !(e instanceof ArmorStand)) out.add(le);
        }
        return out;
    }

    private void effect(LivingEntity e, PotionEffectType type, int seconds, int amplifier) {
        e.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier, false, true, true));
    }
}
