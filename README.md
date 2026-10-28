# Shulker Palette

Shulker Palette turns a shulker box into a block palette. Fill a box with the blocks you want to build with, hold it, and each right-click places one of them. The block can be chosen at random, picked by you, or drawn row by row as a gradient. I made it for walls, floors, and paths that mix several kinds of block, which in vanilla means switching hotbar slots for nearly every block you place.

It's needed on both the client and the server. Singleplayer and LAN worlds work as they are.

### Features

#### Four ways to choose the next block

- **Random** draws by count, so the block you have more of comes up more often. With 48 stone and 16 andesite in the box, stone comes up three times out of four.
- **Equal random** gives every kind of block the same chance, however many of each the box holds.
- **Pick** places the one kind of block you choose on every click.
- **Gradient** treats each row of the box as one step. You choose the row, and each click draws from that row.

#### One menu for the box in your hand

- Tap V with a shulker box in your hand to open the palette menu. It turns the palette on or off, changes the mode, and saves or loads palettes, without opening the box.

#### Works in survival

- Each block you place comes out of the box. In creative, nothing is used up.
- Tools, food, and anything else that isn't a block stay in the box and are never placed.
- Blocks go down through the game's normal placement, so they face the right way and make their usual sounds.

#### See what comes next

- A palette box shows the block it will place next, sitting inside the open box, in your inventory, your hotbar, and your hand.
- A slowly cycling rainbow outline marks the modes that choose at random.
- A see-through preview of the next block can show where a right-click will place it. It's off by default.
- Holding V and scrolling shows a view of the box above your hotbar, with what's inside and what's selected.

#### The palette stays with the box

- The palette's settings are stored on the shulker box itself, so the box keeps them when you place it, break it, or hand it to someone else.
- Each box has its own palette, so two boxes in one chest can be set up differently.

#### A library of palettes

- Save a box's palette under a name, for this world or for all your worlds, and load it into any box later.
- In creative, loading fills the box. In survival, it shows what goes in each slot, so you can fill the box with real blocks.
- Palettes are saved as plain text files you can read, edit, and share, and the library copies them to and from your clipboard.

#### Shapes, in creative

- The palette can place its blocks as stairs, slabs, walls, fences, and the other shapes the game makes from them, so a box of stone and andesite can build stone and andesite stairs.

#### Repaint, in creative

- Repaint replaces the block you're looking at with the palette's next block, instead of placing a new block beside it.
- It works on any block that holds nothing, and the new block keeps the old one's facing, so a staircase stays a staircase.
- A see-through preview shows what the block will become before you click.

### Requirements

- [Fabric API](https://modrinth.com/mod/fabric-api).
- [MenuKit](https://modrinth.com/mod/menukit) 7.5.0 or newer, below 8.0.0. It draws the palette menu, the palette library, and the box view above the hotbar.
- [SlotKit](https://modrinth.com/mod/slotkit) 2.2.0 or newer, below 3.0.0. It lays out the shulker box's slots, including the extra rows.
- [Keybindery](https://modrinth.com/mod/keybindery) 1.4.1 or newer. It lets the keys be set as key combinations, and gives the shape key its V + Left Shift default.

YACL comes bundled, so it doesn't need a separate install.

### Pairs well with

- [Mod Menu](https://modrinth.com/mod/modmenu) or [Mod Hub](https://modrinth.com/mod/mod-hub), which open the settings screen from the mod list.
- [Open Anywhere](https://modrinth.com/mod/open-anywhere) 1.0.0 or newer, my mod for opening shulker boxes from your inventory or an open chest. A palette box opened that way shows all its rows, and with this mod's setting turned on, the palette's buttons beside it.

### Compatibility

- Minecraft 26.3 with Fabric.
- Needed on both the client and the server.
- Version 1.0.0 was for Minecraft 1.21.11. Palettes from 1.0.0 don't carry over to 2.0.0. The blocks inside stay where they are, and turning the palette back on makes the box a palette again.

### How to use

#### The palette menu

Hold any shulker box in your main hand and tap the **palette key** (V). A tap means pressing and letting go without scrolling in between. The palette menu opens without opening the box, and it works whether the palette is on or off, so a plain shulker box can be made into a palette here.

Each row names the setting it changes. While the palette is off, every row but Palette: On / Off is grayed out.

- **Palette: On / Off** turns the palette on or off, and the menu stays open. Turning it off makes the box plain storage again, with its blocks left in place.
- **Mode** shows the current mode, and each click moves to the next one (see Choosing a mode). It's grayed out while the palette is off.
- **Repaint: On / Off** shows only in creative, and is grayed out while the palette is off (see Repaint).
- **Save to library** and **Load from library** are described in Saving and loading palettes.
- **Clear cheat sheet** shows when the box has a cheat sheet, and removes it.
- **Manage library** opens the palette library.

V, E, and Escape close the menu. It also closes if the shulker box leaves your main hand.

The **Toggle palette** key (unbound by default) turns the palette on or off for the shulker box in your main hand without opening anything. The action bar shows "Palette on" or "Palette off".

#### Placing blocks

- Hold a palette box in your main hand and right-click a block. The palette's next block is placed against it, the same way a block from your hotbar would be.
- In survival, the placed block is taken out of the box. In creative, nothing is used up.
- Only block items are placed. Tools, food, and other items in the box are ignored and never used.
- If the box has no block to place, the click places nothing and plays a failure click.
- Sneaking doesn't change what the palette does, so you can sneak while placing as you would with any block.
- A palette that's on always places blocks from the box and is never placed itself. To place the shulker box, turn its palette off first, in the menu or with the Toggle palette key.
- A see-through copy of the next block shows where a right-click will place it, facing the way it will be placed. It only shows where the block can actually go. It's off by default, and the **Placement preview** setting turns it on.

#### Choosing a mode

The Mode row in the palette menu cycles the modes in the order Random, Equal random, Pick, Gradient, and then back to Random. Its tooltip describes the current mode.

| Mode | What each right-click places |
| --- | --- |
| Random | A block drawn from everything in the box, weighted by count. Every block in the box is one ticket. |
| Equal random | A kind of block drawn at random, every kind with the same chance, whatever the counts. |
| Pick | The kind of block you picked, every time. When the box runs out of it, the pick moves on to the next kind. |
| Gradient | A block drawn from the current row only, weighted by count. |

Pick and Gradient use the palette key to choose the block or row, described next.

#### The palette key

The palette key is V by default. Hold it with a palette box in your main hand and scroll to change the selection. Scrolling with it held doesn't change your hotbar slot, and the selection wraps around at both ends.

- In Pick, it moves to the next or previous kind of block. The action bar names the block and how many the box holds.
- In Gradient, it moves to the next or previous row, skipping rows with no blocks. The action bar shows the row, such as "Row 2 of 3".
- In Random and Equal random there is nothing to select, so scrolling only opens the box view.

If you scroll while holding V, letting go doesn't open the menu. The palette key does nothing while a screen is open.

#### The box view

Holding the palette key and scrolling opens a small view of the box above the hotbar slot it's in. Letting go of the key closes it.

- The title names the mode as Random, Equal random, Item (for Pick), or Gradient level and the row number.
- The view shows one stack for each kind of block with its total. Empty slots and items that aren't blocks are left out. Equal random shows a single item of each kind, since counts don't matter there.
- Frames mark what a click can place. That's every kind of block in Random and Equal random, the picked kind in Pick, and the current row in Gradient, where each row of the box gets its own line.
- Changing the shape (creative) shows the shapes instead, titled with the current shape, such as "Shape: Stairs". There's one item for each shape the box's blocks come in, and the current shape is framed.
- The **Box view while scrolling** setting turns the view off. Scrolling still changes the pick or row, and the action bar still names it.

#### How a palette box looks

- In your inventory and hotbar, a palette box is drawn open, with its lid off and its next block small inside. A palette with no blocks is drawn as an open, empty box.
- In your hand, in first and third person, it's drawn the same way. With the **Open box in hand** setting off, your hand shows only the next block.
- In Random, Equal random, and Gradient, the next block has a slowly cycling rainbow outline. Pick, which always places the same block, has none. The **Rainbow outline** setting turns it off.
- With the palette off, the box looks like a normal shulker box.

#### Saving and loading palettes

Save and Load work on the shulker box in your hand from the palette menu, while its palette is on.

**Save to library** asks for a name and where to keep the palette.

- The name starts as the box's own name if it has been renamed, or the next free "Palette 1", "Palette 2", and so on.
- The button below the name switches between **Save to: This world** and **Save to: All worlds**.
- If a palette with that name is already saved there, a second question asks before replacing it.
- After saving, the Save row's tooltip briefly says where it went. An empty box saves nothing, and the tooltip says so.

**Load from library** opens a list of your saved palettes, with This world first and All worlds below. Each palette shows a strip of its blocks and its name. A file with a mistake in it is listed in red, with the problem in its tooltip, and can't be picked. **Manage library** opens the library, and **Cancel** or Escape closes the list.

Clicking a palette loads it into the box and sets the box to its mode.

- In creative, it replaces everything in the box with the saved blocks, and the box's row count changes to match the saved palette (up to 16 rows).
- In survival, it leaves the box's contents alone and adds a cheat sheet. The box keeps its own row count, normally three unless it already has more rows added in creative, and anything listed past that is left out.

When the box is opened (placed, or through Open Anywhere), each empty slot on the cheat sheet shows a faded copy of the item that belongs there, with how many go there. Hovering over it says the same. The cheat sheet stays with the box when you place it, break it, or carry it. **Clear cheat sheet** in the palette menu removes it.

#### The palette library

The library opens from **Manage library** in the palette menu or the Load list, and from **Open the palette library** in the settings. It has two tabs.

- **This world** holds palettes for the world you're in. In singleplayer they're stored inside the world's save folder, so they travel with a copy of the world. On a server they're stored per server address. With no world open, the tab says to open one.
- **All worlds** holds palettes for every world, in `config/shulkerpalette/palettes`.

Each palette shows its blocks, its name, its mode, and its number of rows. Clicking one selects it and shows what you can do with it.

- **Rename** edits its name.
- **Move to All worlds** or **Move to This world** moves it to the other tab, unless a palette with the same name is already there.
- **Copy** puts it on your clipboard as text.
- The remove button deletes it, after asking.

Above the list, **Paste from clipboard** adds the palette on your clipboard to the tab. A name that's already taken gets a number added, so nothing is overwritten. If the clipboard doesn't hold a palette, or the text has a mistake, the line next to the buttons says which line is wrong and why. **Open folder** opens the tab's folder, where each palette is a `.txt` file you can edit by hand.

A palette's text looks like this:

```javascript
shulker_palette name="Concrete fade" mode=gradient rows=3
row 1: white_concrete 30, white_wool 4
row 3: -, -, gray_concrete 30
```

- The first line gives the name (optional, in double quotes when it has spaces), the mode (random, equal, pick, or gradient), and the number of rows.
- Each row line lists that row's slots from left to right, separated by commas. A slot is an item ID with an optional count, or `-` for an empty slot.
- Items use the same syntax as the `/give` command, so items with components can be written too.
- Empty slots at the end of a row, and empty rows, can be left out. Blank lines and lines starting with `#` are ignored.

#### Shapes (creative only)

A palette has a shape as well as its blocks. With stone in the box and Stairs chosen, it places stone stairs.

- Hold the **shape key** (V + Left Shift by default) and scroll to change the shape. The action bar names it, such as "Shape: Slab", and the box view shows the shapes.
- The **Previous shape** and **Next shape** keys (unbound by default) step through the shapes without scrolling. The box view stays up for a moment after each step.
- The shapes are Full block, Stairs, Slab, Wall, Fence, Fence gate, Carpet, Cushion, Pressure plate, Button, Trapdoor, and Door. Only the shapes at least one block in the box comes in are offered, in that order, and the list wraps around at both ends.
- Blocks that don't come in the chosen shape are skipped, and the rest keep their usual chances. In Pick, if the picked block doesn't come in the shape, the click places nothing.
- A shaped block in the box counts as its block, so oak stairs in the box can place oak slabs.
- If the box no longer has a block that comes in the chosen shape, the palette places full blocks.
- In survival the shape doesn't apply, and the palette places the items in the box as they are.

#### Repaint (creative only)

The Repaint row in the palette menu turns repaint on or off for the palette in your hand. The **Toggle repaint** key (unbound by default) does the same without opening the menu. The action bar shows "Repaint on" or "Repaint off".

- With repaint on, right-clicking replaces the block you're looking at with the palette's next block, in the palette's shape, instead of placing one beside it.
- The new block keeps the old block's facing, half, stair shape, slab type, and similar properties wherever it has them, so oak stairs repainted as stone stairs face the same way.
- Blocks you'd normally use, like chests, levers, and crafting tables, are repainted instead of opened or used.
- A block that holds something isn't repainted, such as a container with items in it, a sign with writing, or a lectern with a book. Liquids and two-block-tall blocks like doors are skipped too. Clicking one plays a failure click.
- Anything attached to the repainted block, like a torch, stays in place.
- Sneaking while you right-click places a block beside the target as usual instead of repainting.
- A see-through preview sits over the block you're looking at, showing what it will become, facing the way it will face. The **Repaint preview** setting turns it off.

#### Extra rows (creative only)

A shulker box can hold up to 16 rows.

- In creative, a placed shulker box's screen has a **− Row** and a **+ Row** button under the box. **+ Row** adds a row, up to 16. **− Row** takes away the last row, but only when that row is empty, and never below three.
- The box shows every row it has while the window has room for them, and scrolls only when it doesn't.
- The rows work as storage whether the palette is on or off, and they stay in survival, where a box with extra rows shows them all.

#### With Open Anywhere

With Open Anywhere installed, the settings have an Open Anywhere group with the **Palette panel on opened boxes** setting, off by default. With it on, a shulker box opened through Open Anywhere shows a column of small palette buttons on its left for power, mode, repaint, save, load, and clear cheat sheet.

- Mode and repaint show only while the palette is on, and repaint only in creative. The repaint button is tinted green while repaint is on.
- Clear cheat sheet shows only when the box has one.
- A box opened through Open Anywhere shows its cheat sheet whether the setting is on or off.

#### Keys

All six keys are listed under Shulker Palette in Minecraft's key binds, and on the settings screen.

| Key | Default | What it does |
| --- | --- | --- |
| Palette key | V | Tapped with a shulker box in your main hand, opens the palette menu. Held with a palette in hand while scrolling, changes the pick or gradient row. |
| Shape scroll key | V + Left Shift | Held with a palette in hand while scrolling, changes the shape. Creative only. |
| Previous shape | Unbound | Steps the palette in your hand to the previous shape. Creative only. |
| Next shape | Unbound | Steps the palette in your hand to the next shape. Creative only. |
| Toggle palette | Unbound | Turns the palette on or off for the shulker box in your main hand. |
| Toggle repaint | Unbound | Turns repaint on or off for the palette in your main hand. Creative only. |

#### Settings

The settings screen opens from Mod Menu or Mod Hub.

**Keys.** All six keys can be set here, including as key combinations.

**Palette library.** **Open the palette library** opens the library.

**Look.** Each of these settings is on or off. Placement preview is off by default, and the other four are on. They only change what you see.

| Setting | What it does |
| --- | --- |
| Rainbow outline | Shows the cycling outline around the next block in the modes that pick at random. |
| Box view while scrolling | Shows the box view above your hotbar while you hold the palette key and scroll. |
| Open box in hand | Shows the open box with the next block inside it in your hand. Off, your hand shows only the next block. |
| Placement preview | Shows a see-through copy of the next block where a right-click would place it. |
| Repaint preview | Shows what the block you're looking at would become, while repaint is on. |

**Open Anywhere.** This group shows only when Open Anywhere is installed. **Palette panel on opened boxes**, off by default, is described in With Open Anywhere.

### License

MIT.
