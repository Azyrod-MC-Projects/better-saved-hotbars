package cz.lukynka.bettersavedhotbars.mixin;

import cz.lukynka.bettersavedhotbars.HotbarInfo;
import net.minecraft.client.HotbarManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreen {
    @Shadow private static CreativeModeTab selectedTab;
    @Shadow private float scrollOffs;

    @Unique private static final int LEFT_CLICK = 0;
    @Unique private static final int RIGHT_CLICK = 1;
    @Unique private static final int MIDDLE_CLICK = 2;

    @Shadow protected abstract void selectTab(CreativeModeTab creativeModeTab);

    @Inject(at = @At("HEAD"), method = "slotClicked", cancellable = true)
    private void slotClicked(@Nullable Slot slot, int slotId, int buttonNum, ContainerInput containerInput, CallbackInfo ci) {
        if (selectedTab.getType() != CreativeModeTab.Type.HOTBAR) return;

        Player player = Minecraft.getInstance().player;
        assert player != null;

        if (slot == null) {
            if (buttonNum == MIDDLE_CLICK) {
                player.inventoryMenu.setCarried(ItemStack.EMPTY);
            }
            return;
        }

        ItemStack carriedItem = player.inventoryMenu.getCarried();

        if (slotId >= 45) return;

        if (carriedItem.getItem() == Items.AIR) {
            // If user isn't carrying anything and used the right-click button, then remove the item from the saved hotbar and put it in his hand.
            if (buttonNum == RIGHT_CLICK) {
                this.swapCarriedItemWithSavedHotbarSlot(slot, player, ci);
            }
            // If user used a different button, fallback to the default creative inventory logic handling for that button.
            return;
        }
        // If we reach this point, player is carrying something

        if (buttonNum == MIDDLE_CLICK) {
            // If the user is holding something and uses middle click -> Clear what he is holding
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
            return; // And fall back to the default action, which is to take a full stack of the highlighted item
            // This results in middle click always taking a full stack of what is highlighted, or clearing itself if cell is empty
        }

        if (slot.getItem().isEmpty()) {
            // Put the item carried by the player into the hotbar slot
            this.swapCarriedItemWithSavedHotbarSlot(slot, player, ci);
        } else {
            if (buttonNum == RIGHT_CLICK) {
                // If player right clicks, swap item held with item in slot
                this.swapCarriedItemWithSavedHotbarSlot(slot, player, ci);
            } else if (buttonNum == LEFT_CLICK) {
                // If player left clicks, and there is an item in the slot, clear his hand and default to picking a copy of the stack
                player.inventoryMenu.setCarried(ItemStack.EMPTY);
            }
        }
    }

    @Unique
    private void swapCarriedItemWithSavedHotbarSlot(Slot slot, Player player, CallbackInfo ci) {
        RegistryAccess registryAccess = player.level().registryAccess();
        HotbarManager hotbarManager = Minecraft.getInstance().getHotbarManager();
        HotbarInfo newHotbarInfo = getHotbarWithIndex(slot);
        ItemStack slotItem = slot.getItem();
        ItemStack carriedItem = player.inventoryMenu.getCarried().copy();

        slot.set(carriedItem);
        var hotbar = hotbarManager.get(newHotbarInfo.row());
        hotbar.storeFrom(fakeInventoryWithModifiedHotbar(hotbar.load(registryAccess), newHotbarInfo.slot(), carriedItem), registryAccess);
        hotbarManager.save();

        player.inventoryMenu.setCarried(slotItem);
        ci.cancel(); // Cancel so that default logic isn't run
    }

    @Unique
    private Inventory fakeInventoryWithModifiedHotbar(List<ItemStack> existingItems, Integer slot, ItemStack itemStack) {
        assert Minecraft.getInstance().player != null;
        var player = Minecraft.getInstance().player;
        var fakeInventory = new Inventory(player, ((InventoryAccessor) player.getInventory()).getEquipment());
        var i = 0;
        var fakeInventoryAccessor = ((InventoryAccessor) fakeInventory);
        fakeInventoryAccessor.getItems().clear();
        for(ItemStack item : existingItems) {
            fakeInventory.setItem(i, existingItems.get(i));
            i++;
        }
        fakeInventory.setItem(slot, itemStack);

        return fakeInventory;
    }

    @Unique
    private HotbarInfo getHotbarWithIndex(Slot slot) {
        int scrollPage = Math.round(4 * scrollOffs);

        int slotRow = (slot.getContainerSlot() / 9) + scrollPage;
        int slotNumber = (((slot.x - 9) / 9) / 2);

        assert Minecraft.getInstance().player != null;
        return new HotbarInfo(slotNumber, slotRow);
    }
}
