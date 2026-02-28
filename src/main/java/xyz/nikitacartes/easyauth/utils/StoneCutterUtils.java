package xyz.nikitacartes.easyauth.utils;

import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.FabricLoader;
//? if >= 1.21.11 {
import net.minecraft.command.permission.LeveledPermissionPredicate;
//?}
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.server.OperatorEntry;
//? if >= 1.21.9 {
import net.minecraft.server.PlayerConfigEntry;
//?}
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
//? if >= 1.21.6 {
import net.minecraft.storage.ReadView;
//?}
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;

public class StoneCutterUtils {

    public static String getUsername(ServerPlayerEntity player) {
        //? if >= 1.20.3 {
        return player.getNameForScoreboard();
        //?} else {
         /*return player.getName().getString();
        *///?}
    }

    public static Vec3d getPosition(ServerPlayerEntity player) {
        //? if >= 1.21.9 {
        return player.getEntityPos();
        //?} else {
        /*return player.getPos();
         *///?}
    }

    public static void teleport(ServerPlayerEntity player, LastLocation lastLocation, ServerWorld fallbackWorld) {
        //? if >= 1.21.2 {
        player.teleport(
                lastLocation.dimension == null ? fallbackWorld : player.server.getWorld(lastLocation.dimension),
                lastLocation.position.getX(),
                lastLocation.position.getY(),
                lastLocation.position.getZ(),
                EnumSet.noneOf(PositionFlag.class),
                lastLocation.yaw,
                lastLocation.pitch,
                true);
        //?} else {
        /*player.teleport(
                lastLocation.dimension == null ? fallbackWorld : player.server.getWorld(lastLocation.dimension),
                lastLocation.position.getX(),
                lastLocation.position.getY(),
                lastLocation.position.getZ(),
                lastLocation.yaw,
                lastLocation.pitch);
         *///?}
    }

    public static World getWorld(Entity entity) {
        //? if >= 1.21.9 {
        return entity.getEntityWorld();
        //?} else {
         /*return entity.getWorld();
        *///?}

    }

    public static ServerWorld getServerWorld(ServerPlayerEntity player) {
        //? if >= 1.21.9 {
        return player.getEntityWorld();
        //?} else if >= 1.21.6 {
        // return player.getWorld();
        //?} else if >= 1.20 {
        // return player.getServerWorld();
        //?} else {
         /*return player.getWorld();
        *///?}
    }

    public static void killPlayer(ServerPlayerEntity player) {
        //? if >= 1.21.2 {
        player.kill(StoneCutterUtils.getServerWorld(player));
        //?} else {
        /*player.kill();
         *///?}

        //? if >= 1.20.3 {
        StoneCutterUtils.getServerWorld(player).getScoreboard().forEachScore(ScoreboardCriterion.DEATH_COUNT, player, (score) -> score.setScore(score.getScore() - 1));
        //?} else {
        /*player.getScoreboard().forEachScore(ScoreboardCriterion.DEATH_COUNT, getUsername(player), (score) -> score.setScore(score.getScore() - 1));
         *///?}
    }

    //? if >= 1.21.9 {
    public static String getName(PlayerConfigEntry profile) {
        return profile.name();
    }
    //?}

    public static String getName(GameProfile profile) {
        //? if >= 1.21.9 {
        return profile.name();
        //?} else {
        /*return profile.getName();
        *///?}
    }

    public static String getName(PlayerEntity player) {
        return getName(player.getGameProfile());
    }

    //? if >= 1.21.9 {
    public static UUID getId(PlayerConfigEntry profile) {
        return profile.id();
    }
    //?}

    public static UUID getId(GameProfile profile) {
        //? if >= 1.21.9 {
        return profile.id();
        //?} else {
        /*return profile.getId();
         *///?}
    }

    //? if >= 1.21.6 {
    public static void readRootVehicle(ServerPlayerEntity player, ReadView rootVehicle) {
        player.readRootVehicle(rootVehicle);
    }
    //?} else {
    /*public static void readRootVehicle(ServerPlayerEntity player, NbtCompound rootVehicle) {
        //? if >= 1.21.5 {
        player.readRootVehicle(rootVehicle);
        //?} else >= 1.21.2 {
        /^player.readRootVehicle(Optional.of(rootVehicle));
         ^///?} else {
            /^NbtCompound nbtCompound = rootVehicle.getCompound("RootVehicle");
            //? if > 1.19.4 {
             Entity entity = EntityType.loadEntityWithPassengers(nbtCompound.getCompound("Entity"), player.getServerWorld(), (vehicle) -> !player.getServerWorld().tryLoadEntity(vehicle) ? null : vehicle);
            //?} else {
            /^¹Entity entity = EntityType.loadEntityWithPassengers(nbtCompound.getCompound("Entity"), player.getWorld(), (vehicle) -> !player.getWorld().tryLoadEntity(vehicle) ? null : vehicle);
            ¹^///?}
            if (entity != null) {
                UUID uUID;
                if (nbtCompound.containsUuid("Attach")) {
                    uUID = nbtCompound.getUuid("Attach");
                } else {
                    uUID = null;
                }

                Iterator var23;
                Entity entity2;
                if (entity.getUuid().equals(uUID)) {
                    player.startRiding(entity, true);
                } else {
                    var23 = entity.getPassengersDeep().iterator();

                    while(var23.hasNext()) {
                        entity2 = (Entity)var23.next();
                        if (entity2.getUuid().equals(uUID)) {
                            player.startRiding(entity2, true);
                            break;
                        }
                    }
                }
            }
            ^///?}
    }
    *///?}

    public static void startRiding(ServerPlayerEntity player, Entity entity) {
        //? if >= 1.21.9 {
        player.startRiding(entity, true, false);
        //?} else {
        /*player.startRiding(entity, true);
         *///?}
    }

    public static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    public static boolean isOperator(PlayerManager playerManager, ServerPlayerEntity player) {
        //? if >= 1.21.9 {
        OperatorEntry operatorEntry = playerManager.getOpList().get(new PlayerConfigEntry(player.getGameProfile()));
        //?} else {
        /*OperatorEntry operatorEntry = playerManager.getOpList().get(player.getGameProfile());
         *///?}
        if (operatorEntry != null) {
            //? if >= 1.21.11 {
            return operatorEntry.getLevel() == LeveledPermissionPredicate.ADMINS || operatorEntry.getLevel() == LeveledPermissionPredicate.OWNERS;
            //?} else {
            /*return operatorEntry.getPermissionLevel() >= 3;
             *///?}
        }
        return false;
    }

}
