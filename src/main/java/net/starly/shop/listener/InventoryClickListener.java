package net.starly.shop.listener;

import lombok.AllArgsConstructor;
import net.milkbowl.vault.economy.Economy;
import net.starly.core.data.Config;
import net.starly.core.jb.util.Pair;
import net.starly.core.jb.version.nms.tank.NmsItemStackUtil;
import net.starly.core.jb.version.nms.wrapper.ItemStackWrapper;
import net.starly.core.jb.version.nms.wrapper.NBTTagCompoundWrapper;
import net.starly.core.util.InventoryUtil;
import net.starly.shop.context.ConfigContent;
import net.starly.shop.data.ChatInputMap;
import net.starly.shop.data.InventoryOpenMap;
import net.starly.shop.enums.ButtonType;
import net.starly.shop.enums.ChatInputType;
import net.starly.shop.shop.ShopData;
import net.starly.shop.enums.InventoryOpenType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static net.starly.shop.ShopMain.*;

@AllArgsConstructor
public class InventoryClickListener implements Listener {
    private final InventoryOpenMap inventoryOpenMap;
    private final Economy economy;
    private final ChatInputMap chatInputMap;

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (player == null) return;
        if (!inventoryOpenMap.has(player)) return;

        Config msgConfig = ConfigContent.getInstance().getMsgConfig();
        ItemStack currentStack = event.getCurrentItem();
        int slot = event.getSlot();
        Config config = ConfigContent.getInstance().getConfig();
        ClickType clickType = event.getClick();
        InventoryOpenType openType = inventoryOpenMap.get(player).getFirst();
        ShopData shopData = inventoryOpenMap.get(player).getSecond();

        if (currentStack == null || currentStack.getType() == Material.AIR) return;
        if (openType == InventoryOpenType.ITEM_SETTING) return;

        event.setCancelled(true);

        switch (openType) {
            case SHOP: {
                if (event.getClickedInventory() == player.getInventory()) return;

                if (clickType.name().equals(config.getString("click.buy"))) {
                    if (shopData.getBuyPrice(slot) == -1) {
                        player.sendMessage(msgConfig.getMessage("errorMessages.cannotBuyThisItem"));
                        return;
                    }

                    ItemStack originStack = shopData.getItem(slot);

                    if (InventoryUtil.getSpace(player.getInventory()) - 5 < 1) {
                        player.sendMessage(msgConfig.getMessage("errorMessages.inventorySpaceIsNotEnough"));
                        return;
                    }

                    if (economy.getBalance(player) < shopData.getBuyPrice(slot)) {
                        player.sendMessage(msgConfig.getMessage("errorMessages.moneyIsNotEnough"));
                        return;
                    }

                    economy.withdrawPlayer(player, shopData.getBuyPrice(slot));
                    player.getInventory().addItem(originStack);
                    player.sendMessage(msgConfig.getMessage("messages.itemBuyed").replace("{price}", shopData.getBuyPrice(slot) + "").replace("{amount}", 1 + ""));
                } else if (clickType.name().equals(config.getString("click.buy-64"))) {
                    if (shopData.getBuyPrice(slot) == -1) {
                        player.sendMessage(msgConfig.getMessage("errorMessages.cannotBuyThisItem"));
                        return;
                    }

                    ItemStack originStack = shopData.getItem(slot);

                    if (economy.getBalance(player) < shopData.getBuyPrice(slot)) {
                        player.sendMessage(msgConfig.getMessage("errorMessages.moneyIsNotEnough"));
                        return;
                    } else if (InventoryUtil.getSpace(player.getInventory()) - 5 < 1) {
                        player.sendMessage(msgConfig.getMessage("errorMessages.inventorySpaceIsNotEnough"));
                        return;
                    }

                    int totalPurchased = 0;
                    for (int i = 0; i < 64; i++) {
                        if (InventoryUtil.getSpace(player.getInventory()) - 5 < 1) break;
                        if (economy.getBalance(player) < shopData.getBuyPrice(slot) * (totalPurchased + 1)) break;

                        player.getInventory().addItem(originStack);
                        totalPurchased++;
                    }

                    economy.withdrawPlayer(player, totalPurchased * shopData.getBuyPrice(slot));
                    player.sendMessage(msgConfig.getMessage("messages.itemBuyed").replace("{price}", (shopData.getBuyPrice(slot) * totalPurchased) + "").replace("{amount}", totalPurchased + ""));
                } else if (clickType.name().equals(config.getString("click.sell"))) {
                    if (shopData.getSellPrice(slot) == -1) {
                        player.sendMessage(msgConfig.getMessage("errorMessages.cannotSellThisItem"));
                        return;
                    }

                    ItemStack originStack = shopData.getItem(slot);
                    if (!InventoryUtil.removeItem(player, originStack, 1)) {
                        player.sendMessage(msgConfig.getMessage("errorMessages.noItemInInventory"));
                        return;
                    }

                    getEconomy().depositPlayer(player, shopData.getSellPrice(slot));
                    player.sendMessage(msgConfig.getMessage("messages.itemSelled").replace("{price}", shopData.getSellPrice(slot) + "").replace("{amount}", 1 + ""));
                } else if (clickType.name().equals(config.getString("click.sell-64"))) {
                    if (shopData.getSellPrice(slot) == -1) {
                        player.sendMessage(msgConfig.getMessage("errorMessages.cannotSellThisItem"));
                        return;
                    }

                    ItemStack originStack = shopData.getItem(slot);
                    int sellable = Arrays.stream(player.getInventory().getContents()).filter(Objects::nonNull).filter(originStack::isSimilar).mapToInt(ItemStack::getAmount).sum();
                    int totalSold = Math.min(64, sellable);
                    if (totalSold == 0 || !InventoryUtil.removeItem(player, originStack, totalSold)) {
                        player.sendMessage(msgConfig.getMessage("errorMessages.noItemInInventory"));
                        return;
                    }

                    getEconomy().depositPlayer(player, totalSold * shopData.getSellPrice(slot));
                    player.sendMessage(msgConfig.getMessage("messages.itemSelled").replace("{price}", String.valueOf(totalSold * shopData.getSellPrice(slot))).replace("{amount}", String.valueOf(totalSold)));
                } else return;

                break;
            }

            case SHOP_SETTING: {
                ItemStack itemStack = event.getCurrentItem();
                if (itemStack == null || itemStack.getType() == Material.AIR || !itemStack.hasItemMeta()) return;
                ItemStackWrapper nmsStack = NmsItemStackUtil.getInstance().asNMSCopy(itemStack);
                NBTTagCompoundWrapper nbtTagCompound = nmsStack.getTag();
                if (nbtTagCompound == null) return;
                ButtonType buttonType = ButtonType.valueOf(nbtTagCompound.getString("buttonId"));

                switch (buttonType) {
                    case SHOP_ENABLED: {
                        shopData.setEnabled(false);
                        player.sendMessage(msgConfig.getMessage("messages.shopDisabled"));

                        Bukkit.getServer().getScheduler().runTaskLater(getPlugin(), () -> {
                            player.openInventory(shopData.getShopSettingInv());
                            inventoryOpenMap.set(player, new Pair<>(InventoryOpenType.SHOP_SETTING, shopData));
                        }, 1);
                        break;
                    }

                    case SHOP_DISABLED: {
                        shopData.setEnabled(true);
                        player.sendMessage(msgConfig.getMessage("messages.shopEnabled"));

                        Bukkit.getServer().getScheduler().runTaskLater(getPlugin(), () -> {
                            player.openInventory(shopData.getShopSettingInv());
                            inventoryOpenMap.set(player, new Pair<>(InventoryOpenType.SHOP_SETTING, shopData));
                        }, 1);
                        break;
                    }

                    case ITEM_SETTING: {
                        Bukkit.getServer().getScheduler().runTaskLater(getPlugin(), () -> {
                            player.openInventory(shopData.getItemSettingInv());
                            inventoryOpenMap.set(player, new Pair<>(InventoryOpenType.ITEM_SETTING, shopData));
                        }, 1);
                        break;
                    }

                    case ITEM_DETAIL_SETTING: {
                        Bukkit.getServer().getScheduler().runTaskLater(getPlugin(), () -> {
                            player.openInventory(shopData.getItemDetailSettingInv());
                            inventoryOpenMap.set(player, new Pair<>(InventoryOpenType.ITEM_DETAIL_SETTING, shopData));
                        }, 1);
                        break;
                    }
                }
                break;
            }

            case ITEM_SETTING: {
                event.setCancelled(false);
                break;
            }

            case ITEM_DETAIL_SETTING: {
                if (event.getClickedInventory() == player.getInventory()) return;

                if (clickType == ClickType.LEFT) {
                    //판매가격
                    Bukkit.getServer().getScheduler().runTaskLater(getPlugin(), () -> {
                        inventoryOpenMap.remove(player);
                        player.closeInventory();

                        player.sendMessage(msgConfig.getMessage("messages.enterSellPrice"));
                        chatInputMap.set(player, new Pair<>(ChatInputType.SELL_PRICE, new Pair<>(shopData, slot)));
                    }, 1);
                } else if (clickType == ClickType.RIGHT) {
                    //구매가격
                    Bukkit.getServer().getScheduler().runTaskLater(getPlugin(), () -> {
                        inventoryOpenMap.remove(player);
                        player.closeInventory();

                        player.sendMessage(msgConfig.getMessage("messages.enterBuyPrice"));
                        chatInputMap.set(player, new Pair<>(ChatInputType.BUY_PRICE, new Pair<>(shopData, slot)));
                    }, 1);
                } else if (clickType.isShiftClick()) {
                    //삭제
                    shopData.setItem(slot, null);
                    event.getClickedInventory().setItem(slot, null);
                }

                break;
            }
        }
    }
}
