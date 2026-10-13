#!/bin/sh
# Generates the brand images of Imagina Mail (app-imagina/src/main/res) from the SVG sources in imagina/brand:
# vector drawables with imagina/tools/svg_to_vector.py (Python) and the PNG launcher icons of Android before 8
# with imagina/tools/svg_to_png.swift (macOS: it renders with AppKit, so no tool has to be installed).
# Run it after changing an SVG and commit the result. See IMAGINA.md.
set -e
cd "$(dirname "$0")/.."
RES=app-imagina/src/main/res
OUT=$RES/drawable
TOOL="python3 -I imagina/tools/svg_to_vector.py"
MARK=imagina/brand/imagina-mail-mark.svg
mkdir -p "$OUT"

# Launcher icon: Thunderbird's adaptive icon puts ic_app_logo with a 22 % inset over
# launcher_icon_background (the brand tile colour, values/imagina_colors.xml). The glyph is
# re-centred in its 64 x 64 box.
$TOOL $MARK "$OUT/ic_app_logo.xml" --width 64 --height 64 --translate -3.25 3.25 --skip-rect
$TOOL $MARK "$OUT/ic_app_logo_monochrome.xml" --width 64 --height 64 --translate -3.25 3.25 --skip-rect --fill '#FFFFFFFF' --drop-fill '#1A1B25'

# The whole mark, tile included, for the «Entrar con Imagina» screen and the About screen. On dark backgrounds the
# brand uses its inverse (ivory tile, ink envelope), which Android picks from drawable-night.
INVERSE=imagina/brand/imagina-mail-mark-inverse.svg
$TOOL $MARK "$OUT/ic_imagina_mark.xml" --width 64 --height 64
mkdir -p "$RES/drawable-night"
$TOOL $INVERSE "$RES/drawable-night/ic_imagina_mark.xml" --width 64 --height 64

# Small icon of the notifications that Thunderbird draws with its logo (the service notification of «Comprobando tu
# correo…», the push one): a one-colour glyph, the envelope with the dot. TbAppIconNotificationProvider uses it.
$TOOL imagina/brand/imagina-mail-notification.svg "$OUT/ic_imagina_notification.xml" --width 24 --height 24

# Launcher icon of Android before 8 (from 8 the adaptive icon of mipmap-anydpi-v26 is used): the whole mark, tile
# included, with transparent corners, in the five densities (48 dp at 1x, 1.5x, 2x, 3x and 4x). The debug, beta
# and daily builds use these too: they have no icon of their own.
for density in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
    mkdir -p "$RES/mipmap-${density%%:*}"
    swift imagina/tools/svg_to_png.swift "$MARK" "$RES/mipmap-${density%%:*}/ic_launcher.png" "${density##*:}"
done

# Preview of the message list widget in the widget picker of the launcher (K-9 Mail's screenshot, with its texts, in
# the library of the widget): an image of the app at the same name and density replaces it. 336 x 252 dp at 3x.
mkdir -p "$RES/drawable-xxhdpi"
swift imagina/tools/svg_to_png.swift imagina/brand/imagina-mail-widget-preview.svg "$RES/drawable-xxhdpi/message_list_widget_preview.png" 1008x756

# Thunderbird's logo in its Compose theme (the header of the permissions step and other onboarding screens)
# is a Compose resource of components/ui/bolt, packaged as an asset: an asset of the app at the same path
# replaces it, so the bird never shows. Bolt has a logo per theme variant and only two logos, Thunderbird's and
# K-9 Mail's: Imagina's light mark takes the place of the first and its inverse, for the dark theme, the one of the
# second (the app uses no K-9 Mail logo). ImaginaBoltTheme picks them.
BOLT=app-imagina/src/main/assets/composeResources/net.thunderbird.components.ui.bolt.resources/drawable
mkdir -p "$BOLT"
$TOOL $MARK "$BOLT/bolt_thunderbird_logo.xml" --width 72 --height 72
$TOOL $INVERSE "$BOLT/bolt_k9mail_logo.xml" --width 72 --height 72

echo "Generated in $RES"
