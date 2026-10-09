#!/bin/sh
# Generates the brand images of Imagina Mail (app-imagina/src/main/res/drawable) from the SVG sources
# in imagina/brand. Run it after changing an SVG and commit the result. See IMAGINA.md.
set -e
cd "$(dirname "$0")/.."
OUT=app-imagina/src/main/res/drawable
TOOL="python3 -I imagina/tools/svg_to_vector.py"
MARK=imagina/brand/imagina-mail-mark.svg
mkdir -p "$OUT"

# Launcher icon: Thunderbird's adaptive icon puts ic_app_logo with a 22 % inset over
# launcher_icon_background (the brand tile colour, values/imagina_colors.xml). The glyph is
# re-centred in its 64 x 64 box.
$TOOL $MARK "$OUT/ic_app_logo.xml" --width 64 --height 64 --translate -3.25 3.25 --skip-rect
$TOOL $MARK "$OUT/ic_app_logo_monochrome.xml" --width 64 --height 64 --translate -3.25 3.25 --skip-rect --fill '#FFFFFFFF' --drop-fill '#1A1B25'

# The whole mark, tile included, for the «Entrar con Imagina» screen.
$TOOL $MARK "$OUT/ic_imagina_mark.xml" --width 64 --height 64

# Thunderbird's logo in its Compose theme (the header of the permissions step and other onboarding screens)
# is a Compose resource of components/ui/bolt, packaged as an asset: an asset of the app at the same path
# replaces it, so the bird never shows.
BOLT=app-imagina/src/main/assets/composeResources/net.thunderbird.components.ui.bolt.resources/drawable
mkdir -p "$BOLT"
$TOOL $MARK "$BOLT/bolt_thunderbird_logo.xml" --width 72 --height 72

echo "Generated in $OUT"
