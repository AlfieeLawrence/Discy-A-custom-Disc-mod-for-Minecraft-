package net.discy.integration.sophisticatedcore;

import com.mojang.logging.LogUtils;
import dev.architectury.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.discy.api.DiscyApi;
import net.discy.core.item.CustomDiscItem;
import org.slf4j.Logger;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Optional hook for Sophisticated Backpacks / Storage jukebox upgrades.
 *
 * <p>Fabric port: {@code SoundHandler} + full {@link ItemStack} (NBT preserved).
 * Forge 1.3+: {@code IDiscHandler} registry.
 */
public final class SophisticatedCoreIntegration {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "sophisticatedcore";
    private static boolean registered;

    private SophisticatedCoreIntegration() {}

    public static void init() {
        if (registered || !Platform.isModLoaded(MOD_ID)) {
            return;
        }
        if (registerSoundHandlerApi() || registerDiscHandlerApi()) {
            registered = true;
        }
    }

    /** Fabric / unofficial port — {@code ServerStorageSoundHandler.registerSoundHandler}. */
    private static boolean registerSoundHandlerApi() {
        try {
            Class<?> handlerIface = Class.forName(
                    "net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.SoundHandler");
            Class<?> serverHandler = Class.forName(
                    "net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.ServerStorageSoundHandler");
            Object handler = Proxy.newProxyInstance(
                    handlerIface.getClassLoader(),
                    new Class<?>[]{handlerIface},
                    new SoundHandlerInvocationHandler());
            serverHandler.getMethod("registerSoundHandler", handlerIface).invoke(null, handler);
            LOGGER.info("Discy: registered Sophisticated Core SoundHandler for custom discs");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Throwable t) {
            LOGGER.warn("Discy: SoundHandler registration failed: {}", t.toString());
            return false;
        }
    }

    /** Forge 1.3+ — {@code DiscHandlerRegistry.registerHandler}. */
    private static boolean registerDiscHandlerApi() {
        try {
            Class<?> handlerIface = Class.forName("net.p3pp3rf1y.sophisticatedcore.api.IDiscHandler");
            Class<?> registry = Class.forName(
                    "net.p3pp3rf1y.sophisticatedcore.upgrades.jukebox.DiscHandlerRegistry");
            Object handler = Proxy.newProxyInstance(
                    handlerIface.getClassLoader(),
                    new Class<?>[]{handlerIface},
                    new DiscHandlerInvocationHandler());
            @SuppressWarnings("unchecked")
            List<Object> handlers = (List<Object>) registry.getMethod("getHandlers").invoke(null);
            handlers.add(0, handler);
            LOGGER.info("Discy: registered Sophisticated Core IDiscHandler for custom discs");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Throwable t) {
            LOGGER.warn("Discy: IDiscHandler registration failed: {}", t.toString());
            return false;
        }
    }

    private static final class SoundHandlerInvocationHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "play" -> playSoundHandler(method, args);
                case "stop" -> {
                    ServerLevel level = (ServerLevel) args[0];
                    Vec3 pos = (Vec3) args[1];
                    UUID storageUuid = (UUID) args[2];
                    PortableDiscPlayback.stop(level, pos, storageUuid);
                    yield null;
                }
                case "update" -> {
                    UUID storageUuid = (UUID) args[0];
                    Vec3 pos = (Vec3) args[1];
                    PortableDiscPlayback.updatePosition(storageUuid, pos);
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private static boolean playSoundHandler(Method method, Object[] args) {
            ServerLevel level = (ServerLevel) args[0];
            UUID storageUuid = (UUID) args[2];
            ItemStack disc = (ItemStack) args[args.length - 1];
            Vec3 pos = method.getParameterCount() == 4
                    ? Vec3.atCenterOf((BlockPos) args[1])
                    : (Vec3) args[1];
            return PortableDiscPlayback.play(level, pos, storageUuid, disc);
        }
    }

    private static final class DiscHandlerInvocationHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return switch (method.getName()) {
                case "supports" -> DiscyApi.isBoundCustomDisc((ItemStack) args[0]);
                case "getSongInfo" -> Optional.empty();
                case "getRandomDisc" -> Optional.empty();
                case "getMusicDiscSize" -> 0;
                case "getMusicLengthInTicks" -> Optional.of(PortableDiscPlayback.lengthTicks((ItemStack) args[0]));
                case "playDisc" -> {
                    playDiscHandler(method, args);
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private static void playDiscHandler(Method method, Object[] args) {
            ServerLevel level = (ServerLevel) args[0];
            UUID storageUuid = (UUID) args[2];
            ItemStack disc = (ItemStack) args[3];
            Runnable onFinished = (Runnable) args[args.length - 1];
            Vec3 pos = method.getParameterCount() == 5
                    ? Vec3.atCenterOf((BlockPos) args[1])
                    : (Vec3) args[1];
            if (PortableDiscPlayback.play(level, pos, storageUuid, disc)) {
                PortableDiscPlayback.trackFinish(level, storageUuid, pos, disc, onFinished);
            }
        }
    }
}
