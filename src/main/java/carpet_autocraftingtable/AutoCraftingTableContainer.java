package carpet_autocraftingtable;

import net.minecraft.inventory.CraftingInventory;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeMatcher;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeUnlocker;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

public class AutoCraftingTableContainer extends AbstractRecipeScreenHandler<CraftingInventory> {
    private final CraftingTableBlockEntity blockEntity;
    private final PlayerEntity player;

    AutoCraftingTableContainer(int id, PlayerInventory playerInventory, CraftingTableBlockEntity blockEntity) {
        super(ScreenHandlerType.CRAFTING, id);
        this.blockEntity = blockEntity;
        this.player = playerInventory.player;
        this.addSlot(new OutputSlot(this.blockEntity, this.player));

        for(int y = 0; y < 3; ++y) {
            for(int x = 0; x < 3; ++x) {
                this.addSlot(new Slot(this.blockEntity, x + y * 3 + 1, 30 + x * 18, 17 + y * 18));
            }
        }

        for(int y = 0; y < 3; ++y) {
            for(int x = 0; x < 9; ++x) {
                this.addSlot(new Slot(playerInventory, x + y * 9 + 9, 8 + x * 18, 84 + y * 18));
            }
        }

        for(int x = 0; x < 9; ++x) {
            this.addSlot(new Slot(playerInventory, x, 8 + x * 18, 142));
        }
    }

    @Override
    public void populateRecipeFinder(RecipeMatcher finder) {
        this.blockEntity.provideRecipeInputs(finder);
    }

    @Override
    public void clearCraftingSlots() {
        this.blockEntity.clear();
    }

    @Override
    public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
        return canInsertIntoSlot(slot.id) && super.canInsertIntoSlot(stack, slot);
    }

    @Override
    public boolean matches(Recipe<? super CraftingInventory> recipe) {
        return this.blockEntity.matches(recipe);
    }

    @Override
    public int getCraftingResultSlotIndex() {
        return 0;
    }

    @Override
    public int getCraftingWidth() {
        return 3;
    }

    @Override
    public int getCraftingHeight() {
        return 3;
    }

    @Override
    public int getCraftingSlotCount() {
        return 10;
    }

    @Override
    public RecipeBookCategory getCategory() {
        return RecipeBookCategory.CRAFTING;
    }

    @Override
    public boolean canInsertIntoSlot(int index) {
        return index != this.getCraftingResultSlotIndex();
    }

    @Override
    public void onContentChanged(Inventory inv) {
        if (this.player instanceof ServerPlayerEntity) {
            ServerPlayNetworkHandler netHandler = ((ServerPlayerEntity) this.player).networkHandler;
            netHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(this.syncId, 0, 0, this.blockEntity.getStack(0)));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.blockEntity.canPlayerUse(player);
    }

    @Override
    public ItemStack transferSlot(PlayerEntity player, int slot) {
        if (slot == 0) {
            ItemStack before = this.blockEntity.getStack(0).copy();
            ItemStack current = before.copy();
            if (!this.insertItem(current, 10, 46, true)) {
                return ItemStack.EMPTY;
            }
            this.blockEntity.removeStack(0, before.getCount() - current.getCount());

            if(player instanceof ServerPlayerEntity && blockEntity.getLastRecipe() != null)
            {
                // this sets recipe in container
                if (!blockEntity.shouldCraftRecipe(player.world, (ServerPlayerEntity) player, blockEntity.getLastRecipe()))
                {
                    return ItemStack.EMPTY;
                }
            }
            slots.get(0).onQuickTransfer(current, before); // calls onCrafted if different
            return this.blockEntity.getStack(0);
        }
        return super.transferSlot(player, slot);
    }

    public void close(PlayerEntity player) {
        ItemStack cursorStack = this.player.currentScreenHandler.getCursorStack();
        if (!cursorStack.isEmpty()) {
            player.dropItem(cursorStack, false);
            this.player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
        }
        this.blockEntity.onContainerClose(this);
    }

    private class OutputSlot extends Slot {
        private PlayerEntity player;
        OutputSlot(Inventory inv, PlayerEntity player) {
            super(inv, 0, 124, 35);
            this.player = player;
        }

        @Override
        public boolean canInsert(ItemStack itemStack_1) {
            return false;
        }

        @Override
        protected void onTake(int amount) {
            AutoCraftingTableContainer.this.blockEntity.removeStack(0, amount);
        }

        @Override
        protected void onCrafted(ItemStack stack, int amount)
        {
            super.onCrafted(stack);
            // from CraftingResultsSlot onCrafted
            if (amount > 0) {
                stack.onCraft(this.player.world, this.player, amount);
            }

            if (this.inventory instanceof RecipeUnlocker) {
                ((RecipeUnlocker)this.inventory).unlockLastRecipe(this.player);
            }
        }

        @Override
        public void onTakeItem(PlayerEntity player, ItemStack stack)
        {
            onCrafted(stack, stack.getCount());
            super.onTakeItem(player, stack);
        }
    }
}
