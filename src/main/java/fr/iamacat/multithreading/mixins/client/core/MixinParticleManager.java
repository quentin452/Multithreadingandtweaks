package fr.iamacat.multithreading.mixins.client.core;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.*;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.RenderGlobal;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import fr.iamacat.multithreading.batching.BatchedParticles;
import fr.iamacat.multithreading.config.MultithreadingandtweaksMultithreadingConfig;

@Mixin(RenderGlobal.class)
public abstract class MixinParticleManager {

    private final ThreadPoolExecutor executorService = new ThreadPoolExecutor(
        MultithreadingandtweaksMultithreadingConfig.numberofcpus,
        MultithreadingandtweaksMultithreadingConfig.numberofcpus,
        60L,
        TimeUnit.SECONDS,
        new SynchronousQueue<>(),
        new ThreadFactoryBuilder().setNameFormat("Particle-Manager-%d")
            .build());

    private static final int BATCH_SIZE = MultithreadingandtweaksMultithreadingConfig.batchsize;

    private final Queue<EntityFX> particleQueue = new ArrayDeque<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        executorService.execute(this::drawLoop);
    }

    private void drawLoop() {
        if (MultithreadingandtweaksMultithreadingConfig.enableMixinParticle) {
            while (!Thread.currentThread()
                .isInterrupted()) {
                EntityFX[] particles = new EntityFX[BATCH_SIZE];
                int count = 0;

                while (count < BATCH_SIZE) {
                    EntityFX particle = particleQueue.poll();
                    if (particle == null) {
                        break;
                    }
                    particles[count++] = particle;
                }

                if (count > 0) {
                    try {
                        BatchedParticles.drawBatch(particles, count);
                    } catch (Exception e) {
                        // Log the exception to the console
                        e.printStackTrace();
                    }
                } else {
                    // Wait for a short amount of time to reduce TPS usage
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread()
                            .interrupt();
                    }
                }
            }
        }
    }

    @Inject(method = "addEffect", at = @At("HEAD"), cancellable = true)
    private void onAddEffect(EntityFX particle, CallbackInfo ci) {
        particle.setParticleTextureIndex(particle.getFXLayer());
        particleQueue.offer(particle);
        ci.cancel();
    }
}
