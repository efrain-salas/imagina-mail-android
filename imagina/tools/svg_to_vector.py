#!/usr/bin/env python3
"""Converts Imagina's simple brand SVGs (path, circle, rect, <g transform="translate(...)">) into
Android VectorDrawable XML, so brand images live as SVG sources in imagina/brand/ and the app
resources are generated from them (see IMAGINA.md, «Imágenes de marca»).

Usage: svg_to_vector.py SOURCE.svg OUT.xml --width DP --height DP
         [--viewport W H] [--translate X Y] [--scale S] [--fill COLOR] [--drop-fill COLOR] [--skip-rect]

--viewport/--translate/--scale place the SVG inside a different viewport (e.g. 108x108 for an
adaptive icon foreground). --fill paints every shape one colour (monochrome and notification
icons); --drop-fill skips shapes of that colour; --skip-rect skips <rect> elements (the brand tile, when
the tile is drawn by an adaptive icon's background).
"""
import argparse
import re
import xml.etree.ElementTree as ET

SVG = '{http://www.w3.org/2000/svg}'


def circle_path(cx, cy, r):
    return f'M{cx - r:g},{cy:g}a{r:g},{r:g} 0 1,0 {2 * r:g},0a{r:g},{r:g} 0 1,0 {-2 * r:g},0Z'


def rect_path(x, y, w, h, rx):
    if not rx:
        return f'M{x:g},{y:g}h{w:g}v{h:g}h{-w:g}Z'
    return (f'M{x + rx:g},{y:g}H{x + w - rx:g}A{rx:g},{rx:g} 0 0,1 {x + w:g},{y + rx:g}V{y + h - rx:g}'
            f'A{rx:g},{rx:g} 0 0,1 {x + w - rx:g},{y + h:g}H{x + rx:g}A{rx:g},{rx:g} 0 0,1 {x:g},{y + h - rx:g}'
            f'V{y + rx:g}A{rx:g},{rx:g} 0 0,1 {x + rx:g},{y:g}Z')


def shapes(node, inherited_fill=None):
    """Yields (pathData, attributes, translate) for every drawable element."""
    fill = node.get('fill', inherited_fill)
    for child in node:
        tag = child.tag.replace(SVG, '')
        if tag == 'g':
            tx = ty = 0.0
            match = re.match(r'translate\(\s*([-\d.]+)[ ,]+([-\d.]+)\s*\)', child.get('transform', ''))
            if match:
                tx, ty = float(match.group(1)), float(match.group(2))
            for d, attrs, (gx, gy) in shapes(child, child.get('fill', fill)):
                yield d, attrs, (gx + tx, gy + ty)
            continue
        attrs = {k: child.get(k) for k in ('fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin')}
        attrs['element'] = tag
        attrs['fill'] = attrs['fill'] or fill
        if tag == 'path':
            yield child.get('d'), attrs, (0.0, 0.0)
        elif tag == 'circle':
            yield circle_path(float(child.get('cx')), float(child.get('cy')), float(child.get('r'))), attrs, (0.0, 0.0)
        elif tag == 'rect':
            attrs['element'] = 'rect'
            yield rect_path(float(child.get('x', 0)), float(child.get('y', 0)), float(child.get('width')),
                            float(child.get('height')), float(child.get('rx', 0))), attrs, (0.0, 0.0)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('source')
    parser.add_argument('out')
    parser.add_argument('--width', type=float, required=True)
    parser.add_argument('--height', type=float, required=True)
    parser.add_argument('--viewport', type=float, nargs=2)
    parser.add_argument('--translate', type=float, nargs=2, default=(0.0, 0.0))
    parser.add_argument('--scale', type=float, default=1.0)
    parser.add_argument('--fill')
    parser.add_argument('--drop-fill')
    parser.add_argument('--skip-rect', action='store_true')
    args = parser.parse_args()

    root = ET.parse(args.source).getroot()
    vb = [float(v) for v in root.get('viewBox').split()]
    viewport = args.viewport or (vb[2], vb[3])

    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f'<!-- Generated from imagina/brand/{args.source.split("/")[-1]} by imagina/tools/svg_to_vector.py: edit the SVG, not this file -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{args.width:g}dp" android:height="{args.height:g}dp"',
        f'    android:viewportWidth="{viewport[0]:g}" android:viewportHeight="{viewport[1]:g}">',
        f'    <group android:translateX="{args.translate[0]:g}" android:translateY="{args.translate[1]:g}"'
        f' android:scaleX="{args.scale:g}" android:scaleY="{args.scale:g}">',
    ]
    for d, attrs, (tx, ty) in shapes(root):
        if args.drop_fill and (attrs.get('fill') or '').lower() == args.drop_fill.lower():
            continue
        if args.skip_rect and attrs.get('element') == 'rect':
            continue
        parts = [f'android:pathData="{d}"']
        stroke = attrs.get('stroke')
        if attrs.get('fill') and attrs['fill'] != 'none':
            parts.append(f'android:fillColor="{args.fill or attrs["fill"]}"')
        if stroke and stroke != 'none':
            if args.fill:
                continue  # a cut-out stroke has no meaning in a one-colour icon
            parts.append(f'android:strokeColor="{stroke}"')
            parts.append(f'android:strokeWidth="{attrs.get("stroke-width") or 1}"')
            if attrs.get('stroke-linecap'):
                parts.append(f'android:strokeLineCap="{attrs["stroke-linecap"]}"')
            if attrs.get('stroke-linejoin'):
                parts.append(f'android:strokeLineJoin="{attrs["stroke-linejoin"]}"')
        path = '<path ' + ' '.join(parts) + ' />'
        if tx or ty:
            path = f'<group android:translateX="{tx:g}" android:translateY="{ty:g}">{path}</group>'
        lines.append('        ' + path)
    lines += ['    </group>', '</vector>', '']
    with open(args.out, 'w') as handle:
        handle.write('\n'.join(lines))


if __name__ == '__main__':
    main()
