package net.discy.core.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.discy.core.client.DiscPixelCache;
import net.discy.core.client.texture.DiskTextureManager;
import net.discy.core.item.CustomDiscItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Renders {@link CustomDiscItem} stacks using {@link DiskTextureManager} dynamic textures,
 * falling back to built-in item atlas sprites for preset stems.
 */
public final class CustomDiscItemRenderer {
    private CustomDiscItemRenderer() {}

    /** GUI hook used by Forge {@code IItemDecorator}. Returns true when drawn. */
    public static boolean renderGui(GuiGraphics graphics, ItemStack stack, int x, int y) {
        String label = CustomDiscItem.readTextureLabel(stack);
        if (label == null) return false;

        ResourceLocation tex = resolveTexture(label);
        if (tex == null) return false;

        if (tex.equals(InventoryMenu.BLOCK_ATLAS)) {
            ResourceLocation spriteId = CustomDiscItem.presetSpriteId(label);
            if (spriteId == null) return false;
            TextureAtlasSprite sprite = Minecraft.getInstance()
                    .getModelManager()
                    .getAtlas(InventoryMenu.BLOCK_ATLAS)
                    .getSprite(spriteId);
            graphics.blit(x, y, 0, 16, 16, sprite);
            return true;
        }

        graphics.blit(tex, x, y, 0, 0, 16, 16, 16, 16);
        return true;
    }

    /** 3D / GUI item renderer hook used by {@link net.discy.mixin.ItemRendererMixin}. */
    public static boolean renderItem(ItemStack stack,
                                     ItemDisplayContext displayContext,
                                     boolean leftHand,
                                     PoseStack poseStack,
                                     MultiBufferSource bufferSource,
                                     int packedLight,
                                     BakedModel model) {
        String label = CustomDiscItem.readTextureLabel(stack);
        if (label == null) return false;

        ResourceLocation tex = resolveTexture(label);
        if (tex == null) return false;

        poseStack.pushPose();
        model.getTransforms().getTransform(displayContext).apply(leftHand, poseStack);
        poseStack.translate(-0.5, -0.5, -0.5);

        if (tex.equals(InventoryMenu.BLOCK_ATLAS)) {
            renderAtlasDisc(label, poseStack, bufferSource, packedLight);
        } else {
            renderDynamicDisc(label, tex, poseStack, bufferSource, packedLight);
        }

        poseStack.popPose();
        return true;
    }

    private static ResourceLocation resolveTexture(String label) {
        ResourceLocation dynamic = DiskTextureManager.ensureStem(label);
        if (dynamic != null && DiskTextureManager.isDynamicReady(dynamic)) {
            return dynamic;
        }

        if (CustomDiscItem.presetSpriteId(label) != null) {
            return InventoryMenu.BLOCK_ATLAS;
        }

        return dynamic;
    }

    private static void renderDynamicDisc(String label,
                                          ResourceLocation texLoc,
                                          PoseStack poseStack,
                                          MultiBufferSource bufferSource,
                                          int packedLight) {
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentCull(texLoc));
        Matrix4f mat = poseStack.last().pose();
        Matrix3f nrm = poseStack.last().normal();

        float zFront = 0.5f + 0.03125f;
        float zBack = 0.5f - 0.03125f;

        vc.vertex(mat, 0, 1, zFront).color(255, 255, 255, 255).uv(0, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, 0, 1).endVertex();
        vc.vertex(mat, 0, 0, zFront).color(255, 255, 255, 255).uv(0, 1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, 0, 1).endVertex();
        vc.vertex(mat, 1, 0, zFront).color(255, 255, 255, 255).uv(1, 1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, 0, 1).endVertex();
        vc.vertex(mat, 1, 1, zFront).color(255, 255, 255, 255).uv(1, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, 0, 1).endVertex();

        vc.vertex(mat, 1, 1, zBack).color(255, 255, 255, 255).uv(1, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, 0, -1).endVertex();
        vc.vertex(mat, 1, 0, zBack).color(255, 255, 255, 255).uv(1, 1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, 0, -1).endVertex();
        vc.vertex(mat, 0, 0, zBack).color(255, 255, 255, 255).uv(0, 1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, 0, -1).endVertex();
        vc.vertex(mat, 0, 1, zBack).color(255, 255, 255, 255).uv(0, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, 0, -1).endVertex();

        emitSideQuads(label, texLoc, bufferSource, poseStack, packedLight, zFront, zBack);
    }

    private static void renderAtlasDisc(String label,
                                        PoseStack poseStack,
                                        MultiBufferSource bufferSource,
                                        int packedLight) {
        ResourceLocation spriteId = CustomDiscItem.presetSpriteId(label);
        if (spriteId == null) return;

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getModelManager()
                .getAtlas(InventoryMenu.BLOCK_ATLAS)
                .getSprite(spriteId);
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS));
        Matrix4f mat = poseStack.last().pose();
        Matrix3f nrm = poseStack.last().normal();

        float zFront = 0.5f + 0.03125f;
        float zBack = 0.5f - 0.03125f;
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        vc.vertex(mat, 0, 1, zFront).color(255, 255, 255, 255).uv(u0, v0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, 0, 1).endVertex();
        vc.vertex(mat, 0, 0, zFront).color(255, 255, 255, 255).uv(u0, v1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, 0, 1).endVertex();
        vc.vertex(mat, 1, 0, zFront).color(255, 255, 255, 255).uv(u1, v1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, 0, 1).endVertex();
        vc.vertex(mat, 1, 1, zFront).color(255, 255, 255, 255).uv(u1, v0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, 0, 1).endVertex();

        vc.vertex(mat, 1, 1, zBack).color(255, 255, 255, 255).uv(u1, v0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, 0, -1).endVertex();
        vc.vertex(mat, 1, 0, zBack).color(255, 255, 255, 255).uv(u1, v1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, 0, -1).endVertex();
        vc.vertex(mat, 0, 0, zBack).color(255, 255, 255, 255).uv(u0, v1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, 0, -1).endVertex();
        vc.vertex(mat, 0, 1, zBack).color(255, 255, 255, 255).uv(u0, v0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, 0, -1).endVertex();
    }

    private static void emitSideQuads(String label, ResourceLocation texLoc,
                                      MultiBufferSource bufferSource, PoseStack poseStack,
                                      int packedLight, float zFront, float zBack) {
        int[][] pixels = readPixels(label, texLoc);
        if (pixels == null) return;
        int h = pixels.length;
        int w = pixels[0].length;

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucentCull(texLoc));
        Matrix4f mat = poseStack.last().pose();
        Matrix3f nrm = poseStack.last().normal();

        float invW = 1f / w;
        float invH = 1f / h;

        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int argb = pixels[py][px];
                int alpha = (argb >> 24) & 0xFF;
                if (alpha == 0) continue;

                float a = alpha / 255f;
                float x0 = px * invW;
                float x1 = x0 + invW;
                float y1 = 1f - py * invH;
                float y0 = y1 - invH;
                float uMid = (px + 0.5f) * invW;
                float vMid = (py + 0.5f) * invH;

                if (py == 0 || ((pixels[py - 1][px] >> 24) & 0xFF) == 0) {
                    vc.vertex(mat, x0, y1, zBack).color(1f, 1f, 1f, a).uv(uMid, vMid)
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, 1, 0).endVertex();
                    vc.vertex(mat, x0, y1, zFront).color(1f, 1f, 1f, a).uv(uMid, vMid)
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, 1, 0).endVertex();
                    vc.vertex(mat, x1, y1, zFront).color(1f, 1f, 1f, a).uv(uMid, vMid)
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, 1, 0).endVertex();
                    vc.vertex(mat, x1, y1, zBack).color(1f, 1f, 1f, a).uv(uMid, vMid)
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, 1, 0).endVertex();
                }
                if (py == h - 1 || ((pixels[py + 1][px] >> 24) & 0xFF) == 0) {
                    vc.vertex(mat, x1, y0, zBack).color(1f, 1f, 1f, a).uv(uMid, vMid)
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, -1, 0).endVertex();
                    vc.vertex(mat, x1, y0, zFront).color(1f, 1f, 1f, a).uv(uMid, vMid)
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, -1, 0).endVertex();
                    vc.vertex(mat, x0, y0, zFront).color(1f, 1f, 1f, a).uv(uMid, vMid)
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, -1, 0).endVertex();
                    vc.vertex(mat, x0, y0, zBack).color(1f, 1f, 1f, a).uv(uMid, vMid)
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 0, -1, 0).endVertex();
                }
                if (px == 0 || ((pixels[py][px - 1] >> 24) & 0xFF) == 0) {
                    vc.vertex(mat, x0, y1, zFront).color(1f, 1f, 1f, a).uv(uMid, vMid)
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, -1, 0, 0).endVertex();
                    vc.vertex(mat, x0, y1, zBack).color(1f, 1f, 1f, a).uv(uMid, vMid)
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, -1, 0, 0).endVertex();
                    vc.vertex(mat, x0, y0, zBack).color(1f, 1f, 1f, a).uv(uMid, vMid)
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, -1, 0, 0).endVertex();
                    vc.vertex(mat, x0, y0, zFront).color(1f, 1f, 1f, a).uv(uMid, vMid)
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, -1, 0, 0).endVertex();
                }
                if (px == w - 1 || ((pixels[py][px + 1] >> 24) & 0xFF) == 0) {
                    vc.vertex(mat, x1, y1, zBack).color(1f, 1f, 1f, a).uv(uMid, vMid)
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 1, 0, 0).endVertex();
                    vc.vertex(mat, x1, y1, zFront).color(1f, 1f, 1f, a).uv(uMid, vMid)
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 1, 0, 0).endVertex();
                    vc.vertex(mat, x1, y0, zFront).color(1f, 1f, 1f, a).uv(uMid, vMid)
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 1, 0, 0).endVertex();
                    vc.vertex(mat, x1, y0, zBack).color(1f, 1f, 1f, a).uv(uMid, vMid)
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(nrm, 1, 0, 0).endVertex();
                }
            }
        }
    }

    private static int[][] readPixels(String label, ResourceLocation texLoc) {
        int[][] cached = DiscPixelCache.get(label);
        if (cached != null) return cached;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        var tex = mc.getTextureManager().getTexture(texLoc);
        if (!(tex instanceof DynamicTexture dt)) return null;
        NativeImage img = dt.getPixels();
        if (img == null) return null;

        int w = img.getWidth();
        int h = img.getHeight();
        int[][] pixels = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int abgr = img.getPixelRGBA(x, y);
                int a = (abgr >> 24) & 0xFF;
                int blue = (abgr >> 16) & 0xFF;
                int green = (abgr >> 8) & 0xFF;
                int red = abgr & 0xFF;
                pixels[y][x] = (a << 24) | (red << 16) | (green << 8) | blue;
            }
        }
        DiscPixelCache.put(label, pixels);
        return pixels;
    }
}
