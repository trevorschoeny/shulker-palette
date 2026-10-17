# Shulker Palette

Shulker Palette turns a shulker box into a block palette. Fill a box with the blocks you want to build with, hold it, and each right-click places one of them. The block can be chosen at random, picked by you, or drawn row by row as a gradient. I made it for walls, floors, and paths that mix several kinds of block, which in vanilla means switching hotbar slots for nearly every block you place.

It's needed on both the client and the server. Singleplayer and LAN worlds work as they are.

## Features

### Four ways to choose the next block

- **Random** draws by count, so the block you have more of comes up more often. With 48 stone and 16 andesite in the box, stone comes up three times out of four.
- **Equal random** gives every kind of block the same chance, however many of each the box holds.
- **Pick** places the one kind of block you choose on every click.
- **Gradient** treats each row of the box as one step. You choose the row, and each click draws from that row.

### Works in survival

- Each block you place comes out of the box. In creative, nothing is used up.
- Tools, food, and anything else that isn't a block stay in the box and are never placed.
- Blocks go down through the game's normal placement, so they face the right way and make their usual sounds.

### See what comes next

- A palette box shows the block it will place next, sitting inside the open box, in your inventory, your hotbar, and your hand.
- A slowly cycling rainbow outline marks the modes that choose at random.
- Holding the palette modifier and scrolling shows a view of the box above your hotbar, with what's inside and what's selected.

### The palette stays with the box

- The palette is stored on the shulker box itself, so the box keeps it when you place it, break it, or hand it to someone else.
- Each box has its own palette, so two boxes in one chest can be set up differently.

### Save and share palettes as text

- Save copies a palette to your clipboard as plain text you can read, edit, and share.
- Load reads it back. In creative it fills the box. In survival it shows what goes in each slot, so you can fill the box with real blocks.

### Repaint, in creative

- Repaint replaces the block you're looking at with the palette's next block, instead of placing a new block beside it.
- A see-through preview shows what the block will become before you click.

## Requirements

- [Fabric API](https://modrinth.com/mod/fabric-api).
- [MenuKit](https://modrinth.com/mod/menukit) 7.2.0 or newer, below 8.0.0. It draws the buttons on the shulker box screen and the box view above the hotbar.
- [SlotKit](https://modrinth.com/mod/slotkit) 1.1.0 or newer, below 2.0.0. It keeps a shulker box from being moved out of your hotbar while you have it open from your hand.

YACL comes bundled, so it doesn't need a separate install.

## Pairs well with

- [Mod Menu](https://modrinth.com/mod/modmenu), which opens the settings screen from the mod list.
- [Keybindery](https://modrinth.com/mod/keybindery) 1.3.0 or newer, which lets you set the keys as key combinations right on the settings screen.

## Compatibility

- Minecraft 26.3 with Fabric.
- Needed on both the client and the server.
- Version 1.0.0 was for Minecraft 1.21.11. Palettes from 1.0.0 don't carry over to 2.0.0. The blocks inside stay where they are, and turning the palette back on makes the box a palette again.

## How to use

### Turning a box into a palette

Open any shulker box, either placed or from your hand. A column of buttons appears on the left of the shulker box screen. While the palette is off, only the power button shows, and the box works as ordinary storage.

- **Power button.** Click it to turn the palette on or off. Turning it on shows the rest of the buttons. Turning it off hides them and makes the box plain storage again, with its blocks left in place.
- **Toggle palette key** (unbound by default). Turns the palette on or off for the shulker box in your main hand without opening it. The action bar shows "Palette on" or "Palette off".

### Placing blocks

- Hold a palette box in your main hand and right-click a block. The palette's next block is placed against it, the same way a block from your hotbar would be.
- In survival, the placed block is taken out of the box. In creative, nothing is used up.
- Only block items are placed. Tools, food, and other items in the box are ignored and never used.
- If the box has no block to place, the click places nothing and plays a failure click.
- Sneaking doesn't change what the palette does, so you can sneak while placing as you would with any block.

### Choosing a mode

The mode button is second in the column and shows while the palette is on. Each click moves to the next mode, in the order Random, Equal random, Pick, Gradient, and then back to Random. Its tooltip names the current mode.

| Mode | What each right-click places |
| --- | --- |
| Random | A block drawn from everything in the box, weighted by count. Every block in the box is one ticket. |
| Equal random | A kind of block drawn at random, every kind with the same chance, whatever the counts. |
| Pick | The kind of block you picked, every time. When the box runs out of it, the pick moves on to the next kind. |
| Gradient | A block drawn from the current row only, weighted by count. |

Pick and Gradient use the palette modifier to choose the block or row, described next.

### The palette modifier

The palette modifier is Left Alt by default (Option on a Mac). Hold it with a palette box in your main hand to do the following.

- **Scroll** to change the selection. Scrolling with the modifier held doesn't change your hotbar slot, and the selection wraps around at both ends.
  - In Pick, it moves to the next or previous kind of block. The action bar names the block and how many the box holds.
  - In Gradient, it moves to the next or previous row, skipping rows with no blocks. The action bar shows the row, such as "Row 2 of 3".
  - In Random and Equal random there is nothing to select, so scrolling only opens the box view.
- **Right-click a block** to place the shulker box itself instead of a block from it.
- **Right-click the air** to open the shulker box in your hand without placing it. This works for any shulker box, palette or not. While it's open, the box's hotbar slot is grayed out and can't be moved, and the screen closes if the box leaves your main hand.

The modifier does nothing while a screen is open.

### The box view

Holding the palette modifier and scrolling opens a small view of the box above the hotbar slot it's in. Letting go of the modifier closes it.

- The title names the mode as Random, Equal random, Item (for Pick), or Gradient level and the row number.
- The view shows one stack for each kind of block with its total. Empty slots and items that aren't blocks are left out. Equal random shows a single item of each kind, since counts don't matter there.
- Frames mark what a click can place. That's every kind of block in Random and Equal random, the picked kind in Pick, and the current row in Gradient, where each row of the box gets its own line.
- The **Box view while scrolling** setting turns the view off. Scrolling still changes the pick or row, and the action bar still names it.

### How a palette box looks

- In your inventory and hotbar, a palette box is drawn open, with its lid off and its next block small inside. A palette with no blocks is drawn as an open, empty box.
- In your hand, in first and third person, it's drawn the same way. With the **Open box in hand** setting off, your hand shows only the next block.
- In Random, Equal random, and Gradient, the next block has a slowly cycling rainbow outline. Pick, which always places the same block, has none. The **Rainbow outline** setting turns it off.
- With the palette off, the box looks like a normal shulker box.

### Save, Load, and Clear

These buttons show while the palette is on.

**Save** copies the palette to your clipboard as text, and its tooltip briefly says "Palette copied to the clipboard". The text looks like this:

```javascript
shulker_palette mode=gradient rows=3
row 1: white_concrete 30, white_wool 4
row 3: -, -, gray_concrete 30
```

- The first line gives the mode (random, equal, pick, or gradient) and the number of rows.
- Each row line lists that row's slots from left to right, separated by commas. A slot is an item ID with an optional count, or `-` for an empty slot.
- Items use the same syntax as the `/give` command, so items with components can be written too.
- Empty slots at the end of a row, and empty rows, can be left out. Blank lines and lines starting with `#` are ignored.

**Load** reads a palette from your clipboard and sets the box to its mode.

- In creative, it replaces everything in the box with the saved blocks.
- In survival, it leaves the box's contents alone and adds a cheat sheet. Each empty slot shows a faded copy of the item that belongs there, with how many go there. Hovering over it says the same. The cheat sheet stays with the box when you place it, break it, or carry it.
- A shulker box has three rows, so anything listed past row 3 is left out.
- If the clipboard doesn't hold a palette, or the text has a mistake, the Load button's tooltip says which line is wrong and why.

**Clear** appears when the box has a cheat sheet, and removes it.

### Repaint (creative only)

The repaint button shows in creative while the palette is on, and it's tinted green while repaint is on. The **Toggle repaint** key (unbound by default) turns repaint on or off for the palette in your main hand without opening it. The action bar shows "Repaint on" or "Repaint off".

- With repaint on, right-clicking replaces the block you're looking at with the palette's next block, instead of placing one beside it.
- A see-through preview of the next block sits over the block you're looking at. It shows the block's default orientation, so a log, for example, may end up facing another way once placed.
- Only full, solid blocks can be repainted. Stairs, slabs, and blocks like chests are skipped, and clicking one plays a failure click.
- Anything attached to the repainted block, like a torch, stays in place.
- Sneaking while you right-click places a block beside the target as usual instead of repainting.

### Keys

All three keys are listed under Shulker Palette in Minecraft's key binds.

| Key | Default | What it does |
| --- | --- | --- |
| Palette modifier | Left Alt (Option on a Mac) | Held with a palette in hand for scrolling, placing the box itself, and opening the box in your hand. |
| Toggle palette | Unbound | Turns the palette on or off for the shulker box in your main hand. |
| Toggle repaint | Unbound | Turns repaint on or off for the palette in your main hand. Creative only. |

### Settings

The settings screen opens from Mod Menu. It has two groups.

**Keys.** With Keybindery installed, the three keys can be set here, including as key combinations. Without it, a **Key binds** button opens Minecraft's key binds.

**Look.** Each of these settings is on or off, and all three are on by default. They only change what you see.

| Setting | What it does |
| --- | --- |
| Rainbow outline | Shows the cycling outline around the next block in the modes that pick at random. |
| Box view while scrolling | Shows the box view above your hotbar while you hold the palette modifier and scroll. |
| Open box in hand | Shows the open box with the next block inside it in your hand. Off, your hand shows only the next block. |

## License

MIT.
