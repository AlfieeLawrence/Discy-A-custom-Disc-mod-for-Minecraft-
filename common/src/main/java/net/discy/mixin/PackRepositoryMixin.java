package net.discy.mixin;

import com.google.common.collect.ImmutableSet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.discy.core.client.resource.DynamicSoundPack;
import net.discy.core.client.resource.DynamicSoundPackManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/** Injects the runtime song pack so uploaded OGGs become vanilla sound events. */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
    @Mutable
    @Final
    @Shadow
    private Set<RepositorySource> sources;

    @Inject(method = "<init>([Lnet/minecraft/server/packs/repository/RepositorySource;)V", at = @At("RETURN"))
    private void discy$injectDynamicSoundSource(RepositorySource[] originalSources, CallbackInfo ci) {
        Set<RepositorySource> mutable = new LinkedHashSet<>(this.sources);
        mutable.add(consumer -> {
            DynamicSoundPack dynamicPack = DynamicSoundPackManager.getPack();
            if (dynamicPack == null) return;

            Pack.ResourcesSupplier supplier = id -> dynamicPack;
            Pack pack = Pack.readMetaAndCreate(
                    DynamicSoundPack.PACK_ID,
                    Component.literal("Discy Songs"),
                    true,
                    supplier,
                    PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP,
                    PackSource.BUILT_IN);
            if (pack != null) {
                consumer.accept(pack);
            }
        });
        this.sources = ImmutableSet.copyOf(mutable);
    }
}
