package io.github.moulberry.hudcaching;

import io.github.moulberry.hudcaching.entityculling.EntityCulling;
import io.github.moulberry.hudcaching.mixins.GuiIngameForgeInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.util.*;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

@Mod(modid = HUDCaching.MODID, version = HUDCaching.VERSION, clientSideOnly = true)
public class HUDCaching {
    public static final String MODID = "hudcaching";
    public static final String VERSION = "1.7-REL";

    private static Framebuffer framebufferMain = null;
    private static Framebuffer framebufferSecond = null;
    private static boolean usingMain;
    public static boolean renderingCacheOverride = false;

    @EventHandler
    public void preinit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(EntityCulling.getInstance());
    }

    //todo: check framebuffer is main framebuffer when modifying glstatemanager

    private static boolean dirty = true;

    public static boolean doOptimization = true;

    public static Framebuffer getCurrentBuffer() {
        return usingMain ? framebufferMain : framebufferSecond;
    }

    public static void setCurrentBuffer(Framebuffer framebuffer) {
        if(usingMain) {
            framebufferMain = framebuffer;
        } else {
            framebufferSecond = framebuffer;
        }
    }

    SimpleCommand hudcachingCommand = new SimpleCommand("hudcaching", new SimpleCommand.ProcessCommandRunnable() {
        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if(args.length != 1) {
                sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED+
                        "Invalid argument count. Usage: /hudcaching (on/off)"));
            } else {
                if(args[0].equalsIgnoreCase("on")) {
                    doOptimization = true;
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN+
                            "HudCaching enabled. HUD framerate is now limited to: 20"));
                } else if(args[0].equalsIgnoreCase("off")) {
                    doOptimization = false;
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW+
                            "HudCaching disabled. HUD framerate is now limited to: NO LIMIT"));
                } else {
                    sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED+
                            "Unknown argument: "+args[0]+". Usage: /hudcaching (on/off)"));

                }
            }
        }
    });

    @EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(hudcachingCommand);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if(event.phase == TickEvent.Phase.END && doOptimization) {
            if(!OpenGlHelper.isFramebufferEnabled() && Minecraft.getMinecraft().thePlayer != null) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.RED+
                        "Framebuffers disabled, HudCaching will not work. Disabling...\n"+EnumChatFormatting.RED+
                        "Please disable ESC > Options > Video Settings > Performance > Fast Render\n"+EnumChatFormatting.RED+
                        "If issue still persists, contact support at https://discord.gg/moulberry"));
                doOptimization = false;
            } else {
                dirty = true;
            }
        }
    }

    public static void renderCachedHud(EntityRenderer entityRenderer, GuiIngame guiIngame, float partialTicks) {
        if(!OpenGlHelper.isFramebufferEnabled() || !doOptimization) {
            guiIngame.renderGameOverlay(partialTicks);
        } else {
            ScaledResolution scaledResolution = new ScaledResolution(Minecraft.getMinecraft());
            int width = scaledResolution.getScaledWidth();
            int height = scaledResolution.getScaledHeight();
            double widthD = scaledResolution.getScaledWidth_double();
            double heightD = scaledResolution.getScaledHeight_double();

            entityRenderer.setupOverlayRendering();
            GlStateManager.enableBlend();

            //guiIngame.renderVignette(Minecraft.getMinecraft().thePlayer.getBrightness(partialTicks),
            //        scaledResolution);

            Framebuffer current = getCurrentBuffer();
            if(current != null) {
                if(guiIngame instanceof GuiIngameForge) {
                    ((GuiIngameForgeInvoker)guiIngame).invokeRenderCrosshairs(width, height);
                } else if(guiIngame.showCrosshair()) {
                    Minecraft.getMinecraft().getTextureManager().bindTexture(Gui.icons);
                    GlStateManager.enableBlend();
                    GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR, 1, 0);
                    GlStateManager.enableAlpha();
                    drawTexturedModalRect(width/2 - 7, height/2 - 7, 0, 0, 16, 16);
                    GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
                }

                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GlStateManager.color(1, 1, 1, 1);
                current.bindFramebufferTexture();
                drawTexturedRect(0, 0, (float)widthD, (float)heightD, 0, 1, 1, 0, GL11.GL_NEAREST);
                GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            }

            usingMain = !usingMain;

            current = getCurrentBuffer();
            if(current == null || dirty) {
                dirty = false;

                current = checkFramebufferSizes(current, Minecraft.getMinecraft().displayWidth,
                        Minecraft.getMinecraft().displayHeight);
                setCurrentBuffer(current);
                current.framebufferClear();
                current.bindFramebuffer(false);

                GlStateManager.disableBlend();
                GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                        1, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GlStateManager.color(1, 1, 1, 1);
                GlStateManager.disableLighting();
                GlStateManager.disableFog();

                renderingCacheOverride = true;
                guiIngame.renderGameOverlay(partialTicks);
                renderingCacheOverride = false;

                Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(false);
                GlStateManager.enableBlend();
            }
        }
    }

    private static void drawTexturedModalRect(int x, int y, int textureX, int textureY, int width, int height) {
        float f = 0.00390625F;
        float f1 = 0.00390625F;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX);
        worldrenderer.pos((x + 0), (y + height), 100).tex(((float)(textureX + 0) * f), ((float)(textureY + height) * f1)).endVertex();
        worldrenderer.pos((x + width), (y + height), 100).tex(((float)(textureX + width) * f), ((float)(textureY + height) * f1)).endVertex();
        worldrenderer.pos((x + width), (y + 0), 100).tex(((float)(textureX + width) * f), ((float)(textureY + 0) * f1)).endVertex();
        worldrenderer.pos((x + 0), (y + 0), 100).tex(((float)(textureX + 0) * f), ((float)(textureY + 0) * f1)).endVertex();
        tessellator.draw();
    }

    private static Framebuffer checkFramebufferSizes(Framebuffer framebuffer, int width, int height) {
        if(framebuffer == null || framebuffer.framebufferWidth != width || framebuffer.framebufferHeight != height) {
            if(framebuffer == null) {
                framebuffer = new Framebuffer(width, height, true);
                framebuffer.framebufferColor[0] = 0;
                framebuffer.framebufferColor[1] = 0;
                framebuffer.framebufferColor[2] = 0;
            } else {
                framebuffer.createBindFramebuffer(width, height);
            }
            framebuffer.setFramebufferFilter(GL11.GL_NEAREST);
        }
        return framebuffer;
    }

    public static void drawTexturedRect(float x, float y, float width, float height, float uMin, float uMax, float vMin, float vMax, int filter) {
        GlStateManager.enableTexture2D();

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX);
        worldrenderer
                .pos(x, y+height, 0.0D)
                .tex(uMin, vMax).endVertex();
        worldrenderer
                .pos(x+width, y+height, 0.0D)
                .tex(uMax, vMax).endVertex();
        worldrenderer
                .pos(x+width, y, 0.0D)
                .tex(uMax, vMin).endVertex();
        worldrenderer
                .pos(x, y, 0.0D)
                .tex(uMin, vMin).endVertex();
        tessellator.draw();

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }


}
