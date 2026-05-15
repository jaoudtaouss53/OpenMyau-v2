package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.mixin.IAccessorMinecraft;
import myau.module.Module;
import myau.util.BlockUtil;
import myau.util.RotationUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGlass;
import net.minecraft.block.BlockObsidian;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockStainedGlass;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class FastPlace extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat df = new DecimalFormat("0.0#", new DecimalFormatSymbols(Locale.US));
    private long delayMS = 0L;
    public final FloatProperty delay = new FloatProperty("delay", 1.0F, 1.0F, 3.0F);
    public final BooleanProperty blocksOnly = new BooleanProperty("blocks-only", true);
    public final BooleanProperty placeFix = new BooleanProperty("place-fix", true);
    public final BooleanProperty skipObsidian = new BooleanProperty("skip-obsidian", true);
    public final BooleanProperty skipEndStone = new BooleanProperty("skip-endstone", true);
    public final BooleanProperty skipOakPlanks = new BooleanProperty("skip-oak-planks", true);
    public final BooleanProperty skipGlass = new BooleanProperty("skip-glass", true);
    public final BooleanProperty skipInteractable = new BooleanProperty("skip-interactable", true);

    private boolean isOakPlanks(ItemStack stack) {
        Item item = stack.getItem();
        if (!(item instanceof ItemBlock)) return false;
        Block block = ((ItemBlock) item).getBlock();
        if (!(block instanceof BlockPlanks)) return false;
        // Metadata 0 = OAK variant
        return stack.getMetadata() == 0;
    }

    private boolean isGlass(Block block) {
        // BlockGlass covers regular glass, BlockStainedGlass covers colored glass,
        // BlockPane covers both glass panes and stained glass panes (and iron bars)
        if (block instanceof BlockGlass || block instanceof BlockStainedGlass) {
            return true;
        }
        if (block instanceof BlockPane) {
            // Exclude iron bars - only allow glass panes
            return block == Blocks.glass_pane || block == Blocks.stained_glass_pane;
        }
        return false;
    }

    private boolean canPlace() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack != null) {
            Item item = stack.getItem();
            if (item instanceof ItemFishingRod) {
                return false;
            }
            if (item instanceof ItemBlock) {
                Block block = ((ItemBlock) item).getBlock();
                if (skipObsidian.getValue() && block instanceof BlockObsidian) {
                    return false;
                }
                if (skipEndStone.getValue() && block == Blocks.end_stone) {
                    return false;
                }
                if (skipOakPlanks.getValue() && isOakPlanks(stack)) {
                    return false;
                }
                if (skipGlass.getValue() && isGlass(block)) {
                    return false;
                }
                if (skipInteractable.getValue() && BlockUtil.isInteractable(block)) {
                    return false;
                }
                if (!(Boolean) this.placeFix.getValue()) {
                    return true;
                }
                MovingObjectPosition mop = RotationUtil.rayTrace(
                        mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, mc.playerController.getBlockReachDistance(), 1.0F
                );
                return mop != null
                        && mop.typeOfHit == MovingObjectType.BLOCK
                        && ((ItemBlock) item).canPlaceBlockOnSide(mc.theWorld, mop.getBlockPos(), mop.sideHit, mc.thePlayer, stack);
            }
        }
        return !(Boolean) this.blocksOnly.getValue();
    }

    public FastPlace() {
        super("FastPlace", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            int rightClickDelayTimer = ((IAccessorMinecraft) mc).getRightClickDelayTimer();
            if (rightClickDelayTimer == 4) {
                this.delayMS = this.delayMS + (long) (50.0F * this.delay.getValue());
            }
            if (this.delayMS > 0L) {
                this.delayMS = this.delayMS - 50;
            }
            if (this.delayMS <= 0L && rightClickDelayTimer > 1 && this.canPlace()) {
                ((IAccessorMinecraft) mc).setRightClickDelayTimer(0);
            }
        }
    }

    @Override
    public void onDisabled() {
        this.delayMS = 0L;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{df.format(this.delay.getValue())};
    }
}
