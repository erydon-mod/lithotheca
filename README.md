# ERYDON Lithotheca

Lithotheca is a Creative block picker and extended hotbar for Minecraft 1.20.1 on Fabric. It works as a companion mod. ERYDON, Themelios, Daedalon, and other installed block mods appear in the catalogue.

- Open Lithotheca using the plaque above the Creative inventory tabs or press the remappable **L** key. Press it again to close. Click Search or use **Ctrl+F** to type. The original default **B** moves to **L** once when upgrading.
- Browse with **Match**, **Nearby**, **Browse**, or **Hotbar**. Results group related types together, with the closest matches and direct search words first. Hotbar shows the blocks in your current palette. **Shapes** is selected on opening, with **Textures** beside it on the right.
- Use the small cross beside a filter to clear it. Search narrows the current view and the other active filters. Reopening the picker restores its default filters.
- Base stone labels show the active Honed, Polished, or Ganosis finish from ERYDON. A shared filter lists each finish present in its current results. Base finish is used when the owning finish information is unavailable.
- Left-click a result to hold one block on the cursor. Clicking a hotbar slot swaps its contents with the held stack. Adding or replacing a stack makes that slot active. Click anywhere outside the hotbar to discard the held stack. You can also drag a block onto a slot.
- Scroll through one continuous hotbar that scales to show every slot. Mod Menu lets you choose between zero and eighteen extra slots. Extra slots are real per-player storage. Install Lithotheca on the server as well for multiplayer storage.
- In the world, middle-click selects a matching stack or fills the first empty slot across the extended bar. When the bar is full, Pick Block follows vanilla's replacement rule and puts the displaced stack in the main inventory if there is space.
- In Creative inventory, the extra slots beside the plaque use the normal cursor to pick up, place, split, or distribute stacks. Pick up a stack and use the Creative trash slot to delete it.

The local catalogue caches stable block information for each source. An absent mod stays in the cache and disappears from picker results until it returns. Hide duplicates keeps the ERYDON result when Themelios supplies the same catalogue entry. Inferred third-party matches remain individually selectable.

Lithotheca uses the approved animated plaque and ERYDON's installed marble and font assets when available.

## Development

Use Java 17 and the included Gradle wrapper. Compile and run the inventory, cache, and mixin tests with:

```powershell
.\gradlew.bat compileJava processResources test
```

If the working folder is synced by OneDrive, use `-PlithothecaBuildDir=<local output path>` to keep generated files in a separate local directory. The project is ready to import into IntelliJ as a Gradle project.

## In-game checks for the draft

- Use a disposable Creative world. Try zero and eighteen extra slots, drag one block into an empty slot, scroll across both ends, place it, and reload the world.
- Select an extra slot, open chat and the slash-command field in turn, and check that the full hotbar and held stack stay visible while typing.
- Select the first or last extra slot, then open and close Creative inventory with E. Check that you are still holding the same block and that the vanilla and extra slots keep their contents.
- Try REI, JEI, and EMI separately. Check their search fields, recipe navigation, and wheel handling.
- With Creative inventory open, type a word containing L in its search field and REI or EMI's search field. Check that the shortcut works again after leaving the field.
- With Axiom, scroll across the join between the first and last hotbar slots in both directions and check that the builder slot is included. Try its editor, context menu, flight-speed control, saved hotbars, and pick block.

## Licence

ERYDON Lithotheca is by Oliver and uses [Oliver's Minecraft Mods Restricted License 1.0](LICENSE). The [third-party notices](THIRD_PARTY_NOTICES.md) cover the plaque lettering and Gradle wrapper.
