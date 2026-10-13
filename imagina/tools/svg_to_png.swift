// Rasterises an SVG of imagina/brand into a PNG with a transparent background, with what macOS already has
// (AppKit renders SVG), so no tool has to be installed. Used by imagina/generate-resources.sh for the launcher
// icons of Android before 8 (see IMAGINA.md, «Imágenes de marca»).
//
// Usage: swift svg_to_png.swift SOURCE.svg OUT.png SIZE
//        swift svg_to_png.swift SOURCE.svg OUT.png WIDTHxHEIGHT
// The SVG is stretched to the size, so give the one of its viewBox's proportions.
import AppKit

let arguments = CommandLine.arguments
let sizeParts = arguments.count == 4 ? arguments[3].split(separator: "x").compactMap { Int($0) } : []
guard sizeParts.count == 1 || sizeParts.count == 2, sizeParts.allSatisfy({ $0 > 0 }) else {
    FileHandle.standardError.write(Data("Usage: svg_to_png.swift SOURCE.svg OUT.png SIZE|WIDTHxHEIGHT\n".utf8))
    exit(2)
}
let width = sizeParts[0]
let height = sizeParts.count == 2 ? sizeParts[1] : sizeParts[0]

guard let image = NSImage(contentsOfFile: arguments[1]) else {
    FileHandle.standardError.write(Data("Cannot read \(arguments[1]) as an image\n".utf8))
    exit(1)
}

guard
    let bitmap = NSBitmapImageRep(
        bitmapDataPlanes: nil,
        pixelsWide: width,
        pixelsHigh: height,
        bitsPerSample: 8,
        samplesPerPixel: 4,
        hasAlpha: true,
        isPlanar: false,
        colorSpaceName: .deviceRGB,
        bytesPerRow: 0,
        bitsPerPixel: 0,
    ),
    let context = NSGraphicsContext(bitmapImageRep: bitmap)
else {
    FileHandle.standardError.write(Data("Cannot create a bitmap of \(width) x \(height) px\n".utf8))
    exit(1)
}

NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = context
context.imageInterpolation = .high
NSColor.clear.set()
NSRect(x: 0, y: 0, width: width, height: height).fill(using: .copy)
image.draw(
    in: NSRect(x: 0, y: 0, width: width, height: height),
    from: .zero,
    operation: .sourceOver,
    fraction: 1.0,
)
NSGraphicsContext.restoreGraphicsState()

guard let png = bitmap.representation(using: .png, properties: [:]) else {
    FileHandle.standardError.write(Data("Cannot encode the PNG\n".utf8))
    exit(1)
}
try png.write(to: URL(fileURLWithPath: arguments[2]))
