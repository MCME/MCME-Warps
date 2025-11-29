package com.mcmiddleearth.warps.velocity.warps;

import com.mcmiddleearth.warps.velocity.WarpVelocity;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import org.enginehub.squirrelid.Profile;
import org.enginehub.squirrelid.cache.HashMapCache;
import org.enginehub.squirrelid.cache.ProfileCache;
import org.enginehub.squirrelid.cache.SQLiteCache;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

public class PlayerNameResolver {

    private ProfileCache cache;

    /**
     * Constructs a profile cache, using an in memory HashMap as a backup
     * If the file at {@code cachePath} doesn't exist SquirellId will create it
     *
     * @param cachePath the cache file
     */
    public PlayerNameResolver(Path cachePath) {
        try {
            cache = new SQLiteCache(cachePath.toFile());
        } catch (IOException e) {
            WarpVelocity.getInstance().getLogger().error("Failed to access SQLite profile cache. Player names will be resolved from memory.", e);
            cache = new HashMapCache();
        }
    }

    @Nullable
    public Profile getByUniqueId(UUID uniqueId) {
            return cache.getIfPresent(uniqueId);
    }

    /**
     * Keeping the UUID <-> name cache up to date
     */
    @Subscribe
    public EventTask onPlayerLogin(LoginEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String username = event.getPlayer().getUsername();

        return EventTask.async(() -> {
            Profile profile = getByUniqueId(uuid);

            if (profile == null || !profile.getName().equals(username)) {
                cache.put(new Profile(uuid, username));
                // Update any warps owned by this player
                WarpManager
                    .getWarps(w -> w.getCreatorName().equals(username))
                    .forEach((s, warp) -> warp.setCreatorName(username));
            }
        });
    }
}