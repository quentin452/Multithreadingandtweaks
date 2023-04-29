/*
 * FalseTweaks
 * Copyright (C) 2022 FalsePattern
 * All Rights Reserved
 * The above copyright notice, this permission notice and the word "SNEED"
 * shall be included in all copies or substantial portions of the Software.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package fr.iamacat.multithreading.mixins.common.core;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.*;

import net.minecraft.entity.Entity;
import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import fr.iamacat.multithreading.config.MultithreadingandtweaksConfig;

@Mixin(value = WorldServer.class, priority = 1000)
public abstract class MixinEntityUpdate {

    private int availableProcessors;
    private ThreadPoolExecutor executorService;
    private int maxPoolSize;
    private WorldServer world;

    // Store the entities to be updated in a thread-safe map
    private ConcurrentHashMap<Integer, Entity> entitiesToUpdate = new ConcurrentHashMap<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        availableProcessors = Runtime.getRuntime()
            .availableProcessors();
        maxPoolSize = Math.max(availableProcessors * 2, 1); // Initialize maxPoolSize to a positive value
        ThreadFactoryBuilder builder = new ThreadFactoryBuilder();
        builder.setNameFormat("Mob-Spawner-" + this.hashCode() + "-%d");
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(); // use a LinkedBlockingQueue instead of a
                                                                     // SynchronousQueue
        executorService = new ThreadPoolExecutor(
            0,
            maxPoolSize,
            60L,
            TimeUnit.SECONDS,
            queue,
            builder.build(),
            new ThreadPoolExecutor.AbortPolicy());
        executorService.allowCoreThreadTimeOut(true);
        executorService.setThreadFactory(builder.build());
        executorService.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        world = (WorldServer) (Object) this;
    }

    // Define a getter method for the world object
    public WorldServer getWorldServer() {
        return world;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (!MultithreadingandtweaksConfig.enableMixinEntityUpdate) {
            // Dynamically set the maximum pool size
            int newMaxPoolSize = Math.max(availableProcessors, 1);
            if (newMaxPoolSize != maxPoolSize) {
                maxPoolSize = newMaxPoolSize;
                executorService.setMaximumPoolSize(maxPoolSize);
            }
        }

        // Update all entities in the map
        for (Entity entity : entitiesToUpdate.values()) {
            entity.onEntityUpdate();
        }
        // Clear the map of entities to be updated for the next tick
        entitiesToUpdate.clear();
    }

    public void updateEntities() {
        // Update all entities in the map
        for (Entity entity : entitiesToUpdate.values()) {
            entity.onEntityUpdate();
        }

        // Remove dead entities from the map
        for (Iterator<Map.Entry<Integer, Entity>> it = entitiesToUpdate.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<Integer, Entity> entry = it.next();
            if (entry.getValue().isDead) {
                it.remove();
            }
        }
    }

    public void updateEntity(Entity entity) {
        // Call the original updateEntity method
        ((WorldServer) (Object) this).updateEntity(entity);

        // Add the entity to the map to be updated later
        entitiesToUpdate.putIfAbsent(entity.getEntityId(), entity);

        // Add a check to remove the entity from the queue if it is dead
        if (entity.isDead) {
            entitiesToUpdate.remove(entity);
        }
    }
}