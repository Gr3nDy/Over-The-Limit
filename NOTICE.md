# Notices

## Source code

The mod's source code and binaries are licensed under the GNU General Public License v3.0 (see LICENSE).

## Map data (previews and generated terrain meshes)

The bundled map previews (`src/main/resources/assets/otl/maps/`) and the generated terrain meshes (`Maps/` in this repository; downloaded per map into `config/Over The Limit/Maps/` at runtime) are derived from publicly available PGM map repositories:

- https://github.com/OvercastCommunity/PublicMaps (branch `main`)
- https://github.com/OvercastCommunity/CommunityMaps (branch `master`)

The maintainers confirm those maps are shared under the Creative Commons Attribution-ShareAlike 4.0 International license (CC BY-SA 4.0; see LICENSE-CC-BY-SA-4.0.txt). Accordingly, the derived meshes are also licensed under CC BY-SA 4.0 by their respective authors, with this project noted as the adapter (modifications are described in `tools/generate_minimap_models.py`: surface meshes baked from world region files, cube approximations, simplified block colors).

Attribution:
- Authors of the bundled maps: see `Maps/ATTRIBUTION.txt` (combined author list extracted from the upstream `map.xml` files, which are also bundled here).
- Upstream per-map author and UUID data: each map's `map.xml` (bundled at `src/main/resources/assets/otl/maps/<mode>/<map>/map.xml`).
- ResourcePile (mcresourcepile.github.io) is the curated index of these maps; it is the source of truth for per-map licensing and is also linked from the two license files present upstream.

No endorsement is implied by any author of this work.

## Third-party material

Minecraft Forge 1.8.9 is the only runtime dependency. Tests use JUnit (test-only). No other third-party libraries are bundled.

## Contact

License questions about map data: open an issue or contact the repository administrators; a takedown path is always via removing the derived meshes (and the bundled previews, if required) and regenerating without them.
