package myau.module.modules;

  import myau.event.EventTarget;
  import myau.event.types.EventType;
  import myau.events.TickEvent;
  import myau.module.Module;
  import myau.property.properties.BooleanProperty;
  import net.minecraft.client.Minecraft;
  import net.minecraft.item.ItemSword;

  public class ManualBlock extends Module {
      private static final Minecraft mc = Minecraft.getMinecraft();
      private boolean blocking = false;

      public final BooleanProperty swordOnly = new BooleanProperty("sword-only", true);

      public ManualBlock() {
          super("ManualBlock", false);
      }

      public boolean isBlocking() {
          return this.isEnabled() && this.blocking;
      }

      @EventTarget
      public void onTick(TickEvent event) {
          if (!this.isEnabled() || event.getType() != EventType.PRE) {
              return;
          }

          if (mc.thePlayer == null || mc.thePlayer.getHeldItem() == null) {
              this.blocking = false;
              return;
          }

          boolean holdingSword = mc.thePlayer.getHeldItem().getItem() instanceof ItemSword;

          if (this.swordOnly.getValue() && !holdingSword) {
              this.blocking = false;
              return;
          }

          // Right click is held — player is blocking with sword
          // The auto-clicker handles the right-click spam for block-hitting
          if (mc.gameSettings.keyBindUseItem.isKeyDown() && holdingSword) {
              this.blocking = true;
          } else {
              this.blocking = false;
          }
      }

      @Override
      public void onDisabled() {
          this.blocking = false;
      }
  }
