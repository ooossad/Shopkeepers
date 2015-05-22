package com.nisovin.shopkeepers.shoptypes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.nisovin.shopkeepers.Filter;
import com.nisovin.shopkeepers.ItemCount;
import com.nisovin.shopkeepers.Settings;
import com.nisovin.shopkeepers.ShopCreationData;
import com.nisovin.shopkeepers.ShopType;
import com.nisovin.shopkeepers.Utils;
import com.nisovin.shopkeepers.shoptypes.offers.PriceOffer;
import com.nisovin.shopkeepers.ui.UIType;
import com.nisovin.shopkeepers.ui.defaults.DefaultUIs;

public class BuyingPlayerShopkeeper extends PlayerShopkeeper {

	protected static class BuyingPlayerShopEditorHandler extends PlayerShopEditorHandler {

		protected BuyingPlayerShopEditorHandler(UIType uiType, BuyingPlayerShopkeeper shopkeeper) {
			super(uiType, shopkeeper);
		}

		@Override
		protected boolean openWindow(Player player) {
			Inventory inventory = Bukkit.createInventory(player, 27, Settings.editorTitle);

			// add the shopkeeper's offers:
			List<ItemCount> chestItems = ((BuyingPlayerShopkeeper) shopkeeper).getItemsFromChest();
			for (int column = 0; column < chestItems.size() && column < 8; column++) {
				ItemCount itemCount = chestItems.get(column);
				ItemStack type = itemCount.getItem(); // this item is already a copy with amount 1
				ItemStack currencyItem = null;
				PriceOffer offer = ((BuyingPlayerShopkeeper) shopkeeper).getOffer(type);

				if (offer != null) {
					if (offer.getPrice() == 0) {
						currencyItem = createZeroCurrencyItem();
					} else {
						currencyItem = createCurrencyItem(offer.getPrice());
					}
					int tradedItemAmount = offer.getItem().getAmount();
					type.setAmount(tradedItemAmount);
				} else {
					currencyItem = createZeroCurrencyItem();
				}
				assert currencyItem != null;

				// add offer to inventory:
				inventory.setItem(column, currencyItem);
				inventory.setItem(column + 18, type);
			}

			// add the special buttons:
			this.setActionButtons(inventory);
			// show editing inventory:
			player.openInventory(inventory);
			return true;
		}

		@Override
		protected void onInventoryClick(InventoryClickEvent event, Player player) {
			event.setCancelled(true);
			if (event.getRawSlot() >= 0 && event.getRawSlot() <= 7) {
				// modifying cost:
				ItemStack item = event.getCurrentItem();
				if (item != null) {
					if (item.getType() == Settings.currencyItem) {
						int amount = item.getAmount();
						amount = this.getNewAmountAfterEditorClick(event, amount);
						if (amount > 64) amount = 64;
						if (amount <= 0) {
							setZeroCurrencyItem(item);
							item.setAmount(1);
						} else {
							item.setAmount(amount);
						}
					} else if (item.getType() == Settings.zeroCurrencyItem) {
						setCurrencyItem(item);
						item.setAmount(1);
					}
				}

			} else if (event.getRawSlot() >= 18 && event.getRawSlot() <= 25) {
				// modifying quantity:
				ItemStack item = event.getCurrentItem();
				if (item != null && item.getType() != Material.AIR) {
					int amount = item.getAmount();
					amount = this.getNewAmountAfterEditorClick(event, amount);
					if (amount <= 0) amount = 1;
					if (amount > item.getMaxStackSize()) amount = item.getMaxStackSize();
					item.setAmount(amount);
				}
			} else if (event.getRawSlot() >= 9 && event.getRawSlot() <= 16) {
			} else {
				super.onInventoryClick(event, player);
			}
		}

		@Override
		protected void saveEditor(Inventory inventory, Player player) {
			for (int column = 0; column < 8; column++) {
				ItemStack tradedItem = inventory.getItem(column + 18);
				if (tradedItem != null) {
					ItemStack priceItem = inventory.getItem(column);
					if (priceItem != null && priceItem.getType() == Settings.currencyItem && priceItem.getAmount() > 0) {
						((BuyingPlayerShopkeeper) shopkeeper).addOffer(tradedItem, priceItem.getAmount());
					} else {
						((BuyingPlayerShopkeeper) shopkeeper).removeOffer(tradedItem);
					}
				}
			}
		}
	}

	protected class BuyingPlayerShopTradingHandler extends PlayerShopTradingHandler {

		protected BuyingPlayerShopTradingHandler(UIType uiType, BuyingPlayerShopkeeper shopkeeper) {
			super(uiType, shopkeeper);
		}

		@Override
		protected void onPurchaseClick(InventoryClickEvent event, Player player) {
			super.onPurchaseClick(event, player);
			if (event.isCancelled()) return;

			// get offer for this type of item:
			ItemStack item = event.getInventory().getItem(0);
			PriceOffer offer = ((BuyingPlayerShopkeeper) shopkeeper).getOffer(item);
			if (offer == null) {
				event.setCancelled(true);
				return;
			}

			int tradedItemAmount = offer.getItem().getAmount();
			if (tradedItemAmount > item.getAmount()) {
				event.setCancelled(true);
				return;
			}

			// get chest:
			Block chest = ((BuyingPlayerShopkeeper) shopkeeper).getChest();
			if (!Utils.isChest(chest.getType())) {
				event.setCancelled(true);
				return;
			}

			// remove currency from chest:
			Inventory inventory = ((Chest) chest.getState()).getInventory();
			ItemStack[] contents = inventory.getContents();
			boolean removed = this.removeCurrencyFromChest(offer.getPrice(), contents);
			if (!removed) {
				event.setCancelled(true);
				return;
			}

			// add items to chest:
			int amount = this.getAmountAfterTaxes(tradedItemAmount);
			if (amount > 0) {
				ItemStack boughtItems = item.clone();
				boughtItems.setAmount(amount);
				boolean added = this.addToInventory(boughtItems, contents);
				if (!added) {
					event.setCancelled(true);
					return;
				}
			}

			// save chest contents:
			inventory.setContents(contents);
		}

		protected boolean removeCurrencyFromChest(int amount, ItemStack[] contents) {
			int remaining = amount;

			// first pass - remove currency:
			int emptySlot = -1;
			for (int i = 0; i < contents.length; i++) {
				ItemStack item = contents[i];
				if (item != null) {
					if (isHighCurrencyItem(item) && remaining >= Settings.highCurrencyValue) {
						int needed = remaining / Settings.highCurrencyValue;
						int amt = item.getAmount();
						if (amt > needed) {
							item.setAmount(amt - needed);
							remaining = remaining - (needed * Settings.highCurrencyValue);
						} else {
							contents[i] = null;
							remaining = remaining - (amt * Settings.highCurrencyValue);
						}
					} else if (isCurrencyItem(item)) {
						int amt = item.getAmount();
						if (amt > remaining) {
							item.setAmount(amt - remaining);
							return true;
						} else if (amt == remaining) {
							contents[i] = null;
							return true;
						} else {
							contents[i] = null;
							remaining -= amt;
						}
					}
				} else if (emptySlot < 0) {
					emptySlot = i;
				}
				if (remaining <= 0) {
					return true;
				}
			}

			// second pass - try to make change:
			if (remaining > 0 && remaining <= Settings.highCurrencyValue && Settings.highCurrencyItem != Material.AIR && emptySlot >= 0) {
				for (int i = 0; i < contents.length; i++) {
					ItemStack item = contents[i];
					if (isHighCurrencyItem(item)) {
						if (item.getAmount() == 1) {
							contents[i] = null;
						} else {
							item.setAmount(item.getAmount() - 1);
						}
						int stackSize = Settings.highCurrencyValue - remaining;
						if (stackSize > 0) {
							contents[emptySlot] = createCurrencyItem(stackSize);
						}
						return true;
					}
				}
			}

			return false;
		}
	}

	private static final Filter<ItemStack> ITEM_FILTER = new Filter<ItemStack>() {

		@Override
		public boolean accept(ItemStack item) {
			if (isCurrencyItem(item) || isHighCurrencyItem(item)) return false;
			if (item.getType() == Material.WRITTEN_BOOK) return false;
			if (item.getEnchantments().isEmpty()) return false; // TODO why don't allow buying of enchanted items?
			return true;
		}
	};

	// TODO how to handle equal items with different costs? on purchase: take the currentSelectedPage/recipe into account?
	private final List<PriceOffer> offers = new ArrayList<PriceOffer>();

	public BuyingPlayerShopkeeper(ConfigurationSection config) {
		super(config);
		this.onConstruction();
	}

	public BuyingPlayerShopkeeper(ShopCreationData creationData) {
		super(creationData);
		this.onConstruction();
	}

	private final void onConstruction() {
		this.registerUIHandler(new BuyingPlayerShopEditorHandler(DefaultUIs.EDITOR_WINDOW, this));
		this.registerUIHandler(new BuyingPlayerShopTradingHandler(DefaultUIs.TRADING_WINDOW, this));
	}

	@Override
	protected void load(ConfigurationSection config) {
		super.load(config);
		// load offers:
		offers.clear();
		// legacy: load offers from old costs section
		offers.addAll(PriceOffer.loadFromConfigOld(config, "costs"));
		offers.addAll(PriceOffer.loadFromConfig(config, "offers"));
	}

	@Override
	protected void save(ConfigurationSection config) {
		super.save(config);
		// save offers:
		PriceOffer.saveToConfig(config, "offers", offers);
	}

	@Override
	public ShopType<BuyingPlayerShopkeeper> getType() {
		return DefaultShopTypes.PLAYER_BUY;
	}

	@Override
	public List<ItemStack[]> getRecipes() {
		List<ItemStack[]> recipes = new ArrayList<ItemStack[]>();
		List<ItemCount> chestItems = this.getItemsFromChest();
		int chestTotal = this.getCurrencyInChest();
		for (PriceOffer offer : offers) {
			ItemStack tradedItem = offer.getItem();
			ItemCount itemCount = ItemCount.findSimilar(chestItems, tradedItem);
			if (itemCount != null) {
				if (chestTotal >= offer.getPrice()) {
					ItemStack[] recipe = new ItemStack[3];
					recipe[0] = tradedItem.clone();
					recipe[2] = createCurrencyItem(offer.getPrice());
					recipes.add(recipe);
				}
			}
		}
		return recipes;
	}

	public PriceOffer getOffer(ItemStack item) {
		for (PriceOffer offer : offers) {
			if (Utils.areSimilar(offer.getItem(), item, Settings.ignoreNameAndLoreOfTradedItems)) {
				return offer;
			}
		}
		return null;
	}

	public PriceOffer addOffer(ItemStack tradedItem, int price) {
		assert tradedItem != null;
		// remove multiple offers for the same item:
		this.removeOffer(tradedItem);

		// making a copy of the item stack, just in case it is used elsewhere as well:
		PriceOffer newOffer = new PriceOffer(tradedItem.clone(), price);
		offers.add(newOffer);
		return newOffer;
	}

	public void clearOffers() {
		offers.clear();
	}

	public void removeOffer(ItemStack item) {
		Iterator<PriceOffer> iter = offers.iterator();
		while (iter.hasNext()) {
			if (Utils.areSimilar(iter.next().getItem(), item, Settings.ignoreNameAndLoreOfTradedItems)) {
				iter.remove();
				return;
			}
		}
	}

	private List<ItemCount> getItemsFromChest() {
		return this.getItemsFromChest(ITEM_FILTER);
	}
}