package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LeftClickMouseEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.*;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings.GameType;

import java.util.Objects;

public class AutoClicker extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private boolean clickPending = false;
    private long clickDelay = 0L;
    private boolean blockHitPending = false;
    private long blockHitDelay = 0L;
    public final IntProperty minCPS = new IntProperty("min-cps", 8, 1, 20);
    public final IntProperty maxCPS = new IntProperty("max-cps", 12, 1, 20);
    public final BooleanProperty blockHit = new BooleanProperty("block-hit", false);
    public final BooleanProperty blockHitPredict = new BooleanProperty("block-hit-predict", false, this.blockHit::getValue);
    public final FloatProperty blockHitTicks = new FloatProperty("block-hit-ticks", 1.5F, 1.0F, 20.0F, this.blockHit::getValue);
    public final IntProperty blockHitMinChance = new IntProperty("block-hit-min-chance", 60, 0, 100, this.blockHit::getValue);
    public final IntProperty blockHitMaxChance = new IntProperty("block-hit-max-chance", 80, 0, 100, this.blockHit::getValue);
    public final FloatProperty blockHitRange = new FloatProperty("block-hit-range", 4.0F, 1.0F, 8.0F, this.blockHit::getValue);
    public final FloatProperty blockHitPredictRange = new FloatProperty("block-hit-predict-range", 4.0F, 1.0F, 6.0F, () -> this.blockHit.getValue() && this.blockHitPredict.getValue());
    public final IntProperty blockHitPredictSwingTick = new IntProperty("block-hit-predict-swing-tick", 2, 0, 5, () -> this.blockHit.getValue() && this.blockHitPredict.getValue());
    public final BooleanProperty weaponsOnly = new BooleanProperty("weapons-only", true);
    public final BooleanProperty allowTools = new BooleanProperty("allow-tools", false, this.weaponsOnly::getValue);
    public final BooleanProperty breakBlocks = new BooleanProperty("break-blocks", true);
    public final FloatProperty range = new FloatProperty("range", 3.0F, 3.0F, 8.0F, this.breakBlocks::getValue);
    public final FloatProperty hitBoxVertical = new FloatProperty("hit-box-vertical", 0.1F, 0.0F, 1.0F, this.breakBlocks::getValue);
    public final FloatProperty hitBoxHorizontal = new FloatProperty("hit-box-horizontal", 0.2F, 0.0F, 1.0F, this.breakBlocks::getValue);

    private long getNextClickDelay() {
        return 1000L / RandomUtil.nextLong(this.minCPS.getValue(), this.maxCPS.getValue());
    }

    private long getBlockHitDelay() {
        return (long) (50.0F * this.blockHitTicks.getValue());
    }

    /**
     * Picks a random chance value between min and max, then rolls against it.
     * Example: min=40, max=80 → rolls a random target between 40-80, then rolls if hit succeeds.
     */
    private boolean rollBlockHitChance() {
        int min = this.blockHitMinChance.getValue();
        int max = this.blockHitMaxChance.getValue();
        if (min > max) {
            int tmp = min;
            min = max;
            max = tmp;
        }
        // Pick a target chance between min and max
        int targetChance = min + (int) (Math.random() * (max - min + 1));
        // Roll against the picked target
        return Math.random() * 100.0 < targetChance;
    }

    private boolean isBreakingBlock() {
        return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
    }

    private boolean canClick() {
        if (!this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            if (this.breakBlocks.getValue() && this.isBreakingBlock() && !this.hasValidTarget()) {
                GameType gameType12 = mc.playerController.getCurrentGameType();
                return gameType12 != GameType.SURVIVAL && gameType12 != GameType.CREATIVE;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    private boolean isValidTarget(EntityPlayer entityPlayer) {
        if (entityPlayer != mc.thePlayer && entityPlayer != mc.thePlayer.ridingEntity) {
            if (entityPlayer == mc.getRenderViewEntity() || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityPlayer.deathTime > 0) {
                return false;
            } else {
                float borderSize = entityPlayer.getCollisionBorderSize();
                return RotationUtil.rayTrace(entityPlayer.getEntityBoundingBox().expand(
                        borderSize + this.hitBoxHorizontal.getValue(),
                        borderSize + this.hitBoxVertical.getValue(),
                        borderSize + this.hitBoxHorizontal.getValue()
                ), mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, this.range.getValue()) != null;
            }
        } else {
            return false;
        }
    }

    private boolean hasValidTarget() {
        return mc.theWorld
                .loadedEntityList
                .stream()
                .filter(e -> e instanceof EntityPlayer)
                .map(e -> (EntityPlayer) e)
                .anyMatch(this::isValidTarget);
    }

    private boolean isPlayerNearby() {
        double rangeSq = this.blockHitRange.getValue() * this.blockHitRange.getValue();
        return mc.theWorld
                .loadedEntityList
                .stream()
                .filter(e -> e instanceof EntityPlayer)
                .map(e -> (EntityPlayer) e)
                .anyMatch(p -> p != mc.thePlayer
                        && p != mc.thePlayer.ridingEntity
                        && p.deathTime <= 0
                        && p.getDistanceSqToEntity(mc.thePlayer) <= rangeSq);
    }

    /**
     * Checks if opponent is facing towards the local player.
     */
    private boolean isFacingPlayer(EntityPlayer opponent) {
        Vec3 toPlayer = new Vec3(
                mc.thePlayer.posX - opponent.posX,
                mc.thePlayer.posY - opponent.posY,
                mc.thePlayer.posZ - opponent.posZ
        ).normalize();

        float yawRad = (float) Math.toRadians(opponent.rotationYaw);
        float pitchRad = (float) Math.toRadians(opponent.rotationPitch);
        Vec3 lookVec = new Vec3(
                -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad),
                -MathHelper.sin(pitchRad),
                MathHelper.cos(yawRad) * MathHelper.cos(pitchRad)
        );

        double dot = toPlayer.xCoord * lookVec.xCoord + toPlayer.yCoord * lookVec.yCoord + toPlayer.zCoord * lookVec.zCoord;
        return dot > 0.5;
    }

    /**
     * Predict mode: returns true if any nearby opponent is about to hit us.
     * Detects opponent swing in early phase while they are facing us and in range.
     */
    private boolean shouldPredictBlock() {
        double rangeSq = this.blockHitPredictRange.getValue() * this.blockHitPredictRange.getValue();
        int maxSwingTick = this.blockHitPredictSwingTick.getValue();

        return mc.theWorld
                .loadedEntityList
                .stream()
                .filter(e -> e instanceof EntityPlayer)
                .map(e -> (EntityPlayer) e)
                .filter(p -> p != mc.thePlayer
                        && p != mc.thePlayer.ridingEntity
                        && p.deathTime <= 0
                        && p.getDistanceSqToEntity(mc.thePlayer) <= rangeSq)
                .anyMatch(p -> p.isSwingInProgress
                        && p.swingProgressInt <= maxSwingTick
                        && this.isFacingPlayer(p));
    }

    public AutoClicker() {
        super("AutoClicker", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE) {
            if (this.clickDelay > 0L) {
                this.clickDelay -= 50L;
            }
            if (this.blockHitDelay > 0L) {
                this.blockHitDelay -= 50L;
            }
            if (mc.currentScreen != null) {
                this.clickPending = false;
                this.blockHitPending = false;
            } else {
                if (this.clickPending) {
                    this.clickPending = false;
                    KeyBindUtil.updateKeyState(mc.gameSettings.keyBindAttack.getKeyCode());
                }
                if (this.blockHitPending) {
                    this.blockHitPending = false;
                    KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                }
                if (this.isEnabled() && this.canClick() && mc.gameSettings.keyBindAttack.isKeyDown()) {
                    if (!mc.thePlayer.isUsingItem()) {
                        boolean didClickThisTick = false;
                        while (this.clickDelay <= 0L) {
                            this.clickPending = true;
                            this.clickDelay = this.clickDelay + this.getNextClickDelay();
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                            KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindAttack.getKeyCode());
                            didClickThisTick = true;
                        }

                        if (didClickThisTick
                                && this.blockHit.getValue()
                                && this.blockHitDelay <= 0L
                                && ItemUtil.isHoldingSword()
                                && !mc.thePlayer.isUsingItem()) {

                            boolean shouldBlock;

                            if (this.blockHitPredict.getValue()) {
                                // Predict mode: only block if opponent is about to hit us
                                shouldBlock = this.shouldPredictBlock();
                            } else {
                                // Auto mode: block based on chance + nearby player check
                                shouldBlock = this.isPlayerNearby() && this.rollBlockHitChance();
                            }

                            if (shouldBlock) {
                                this.blockHitPending = true;
                                this.blockHitDelay = this.blockHitDelay + this.getBlockHitDelay();
                                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                                KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindUseItem.getKeyCode());
                            }
                        }
                    }
                }
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onCLick(LeftClickMouseEvent event) {
        if (this.isEnabled() && !event.isCancelled()) {
            if (!this.clickPending) {
                this.clickDelay = this.clickDelay + this.getNextClickDelay();
            }
        }
    }

    @Override
    public void onEnabled() {
        this.clickDelay = 0L;
        this.blockHitDelay = 0L;
    }

    @Override
    public void verifyValue(String mode) {
        if (this.minCPS.getName().equals(mode)) {
            if (this.minCPS.getValue() > this.maxCPS.getValue()) {
                this.maxCPS.setValue(this.minCPS.getValue());
            }
        } else if (this.maxCPS.getName().equals(mode) && this.minCPS.getValue() > this.maxCPS.getValue()) {
            this.minCPS.setValue(this.maxCPS.getValue());
        } else if (this.blockHitMinChance.getName().equals(mode)) {
            if (this.blockHitMinChance.getValue() > this.blockHitMaxChance.getValue()) {
                this.blockHitMaxChance.setValue(this.blockHitMinChance.getValue());
            }
        } else if (this.blockHitMaxChance.getName().equals(mode)
                && this.blockHitMinChance.getValue() > this.blockHitMaxChance.getValue()) {
            this.blockHitMinChance.setValue(this.blockHitMaxChance.getValue());
        }
    }

    @Override
    public String[] getSuffix() {
        return Objects.equals(this.minCPS.getValue(), this.maxCPS.getValue())
                ? new String[]{this.minCPS.getValue().toString()}
                : new String[]{String.format("%d-%d", this.minCPS.getValue(), this.maxCPS.getValue())};
    }

}
