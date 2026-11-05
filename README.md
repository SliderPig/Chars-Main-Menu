# {C} Char's Main Menu

A hand-built cherry wood house as the Minecraft main menu. Forge 1.20.1, PC side only: servers and other players don't need it.

## What you see
- The game opens on the front of a cherry wood house in a blossom-filled yard, with see-through glass buttons on the left.
- Press a button: the buttons fade, the double doors swing open with a soft creak, the camera glides up the path, through the doors, down the lamp-lit hall,
  the back doors open, and it flies out into the garden and arcs round to a giant cherry tree with **your own skin** asleep on its long branch. About 4.7 seconds. Any click skips to the end.
- The screen you chose (worlds, servers, options, mods...) opens over that view instead of dirt. Going back to the title flies the camera back round through the house.
- Coming back from a world: a soft fade in at the house front.

## Settings
The small cat button under the main buttons, or the Config button for this mod in the Mods list. Also `config/charmenu.properties`:
`enabled` (false = plain vanilla menu), `animation` (false = soft fades instead of flying), `quality` (high/low), `ambience`, `music` (off by default).
All the menu's sounds follow the Music volume slider.

## How it stays out of the way
- The title screen is a subclass of vanilla's, so Forge's Mods button and update notice, the language and accessibility buttons, and every button other mods add still exist. Realms is hidden. Other mods' buttons move to the bottom right corner.
- If a mod that replaces the menu is installed (FancyMenu and friends), or another mod swapped the title screen, this mod steps aside.
- Any problem while drawing falls back to the vanilla menu with one line in the log.
- Into a world: everything on the graphics card is freed, and only one quick check per tick is left running.

## How it's made
- `tools/make_scene.py` (pure Python) builds the whole scene: writes `assets/charmenu/scene/world.bin.gz` (every block), `far.bin.gz` (low detail land to the horizon) and `meta.txt` (doors, sleeper, camera paths).
- `tools/make_art.py`, `tools/make_sounds.py`: clouds, petal, glow, cat icon, and the original sounds (all synthesized).
- At run time a background thread turns the blocks into vertex arrays using the game's real block models, with a small pretend world (`SceneLevel`) so block colours and smooth lighting work. Light is baked once (`model/LightBake`): soft sky light plus traced sun shadows, stored in the normal light numbers.
- `SceneEngine` draws sky, far land, blocks, doors, the sleeper, lantern glow and petals with its own small shaders. `Director` works out the camera from the clock alone, so speed never depends on frame rate.

## Testing
- `gradlew test`: the pure maths (easing, camera spline, door timing, light).
- `gradlew build -Psmoke -x test` then `python tools/run_real_client.py full`: the real game in a throwaway `run-real/` folder walks through everything, saves screenshots and quits. `look` only takes the scene views. `lab` stays open and retakes the views each time `run-real/lab/go` appears (with `scene=<folder>` to load a freshly made scene without a restart).

MIT licence, see LICENSE. Every sound and texture here is original; the blocks are the player's own game assets, read at run time.
