#!/bin/bash
set -e

# ==========================================
# Catalytic Release Build Script
# Version: 2.0 (Production Ready)
# 
# Supports: macOS (arm64/x64), Windows (x64), Linux (x64)
# Usage: ./build_release.sh [--skip-sign] [--output <dir>]
# ==========================================

# ==========================================
# 1. Parse Arguments
# ==========================================
SKIP_SIGN=false
OUTPUT_DIR=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-sign)
            SKIP_SIGN=true
            shift
            ;;
        --output|-o)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --skip-sign       Skip code signing (useful for CI without certs)"
            echo "  --output, -o DIR  Output directory (default: ./release)"
            echo "  --help, -h        Show this help"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# ==========================================
# 2. Environment Detection
# ==========================================
PROJECT_ROOT=$(cd "$(dirname "$0")"; pwd)
OS="$(uname -s)"
ARCH="$(uname -m)"

echo "=============================================="
echo "📂 Project Root: $PROJECT_ROOT"
echo "🖥️  OS: $OS | Arch: $ARCH"
echo "=============================================="

# Detect Platform
case "$OS" in
    Darwin*)    PLATFORM="macos";;
    Linux*)     PLATFORM="linux";;
    CYGWIN*|MINGW*|MSYS*) PLATFORM="windows";;
    *)          echo "❌ Unsupported OS: $OS"; exit 1;;
esac

# Detect Architecture
case "$ARCH" in
    arm64|aarch64)  
        ARCH_NAME="arm64"
        RUST_TARGET_SUFFIX="aarch64"
        ;;
    x86_64|amd64)   
        ARCH_NAME="x64"
        RUST_TARGET_SUFFIX="x86_64"
        ;;
    *)          
        echo "❌ Unsupported Architecture: $ARCH"
        exit 1
        ;;
esac

echo "🎯 Target: $PLATFORM-$ARCH_NAME"

# ==========================================
# 3. Dependency Check
# ==========================================
echo ""
echo "🔍 Checking dependencies..."

check_command() {
    if ! command -v "$1" &> /dev/null; then
        echo "❌ Required: $1 not found"
        exit 1
    fi
    echo "  ✓ $1"
}

check_command cargo
check_command dotnet
check_command java

if [[ "$PLATFORM" == "macos" ]]; then
    check_command codesign
    check_command hdiutil
fi

# ==========================================
# 4. Setup Variables
# ==========================================
if [[ -z "$OUTPUT_DIR" ]]; then
    OUTPUT_DIR="$PROJECT_ROOT/release"
fi

BUNDLE_ROOT="$PROJECT_ROOT/catalyticui/composeApp/service_bundle"
SERVICE_DIR="$BUNDLE_ROOT/service"

# Platform-specific targets
case "$PLATFORM" in
    macos)
        RUST_TARGET="${RUST_TARGET_SUFFIX}-apple-darwin"
        DOTNET_RID="osx-$ARCH_NAME"
        ENGINE_LIB="libcatalytic.dylib"
        ;;
    linux)
        RUST_TARGET="${RUST_TARGET_SUFFIX}-unknown-linux-gnu"
        DOTNET_RID="linux-$ARCH_NAME"
        ENGINE_LIB="libcatalytic.so"
        ;;
    windows)
        RUST_TARGET="${RUST_TARGET_SUFFIX}-pc-windows-msvc"
        DOTNET_RID="win-$ARCH_NAME"
        ENGINE_LIB="catalytic.dll"
        ;;
esac

echo ""
echo "📋 Build Configuration:"
echo "   Rust Target:  $RUST_TARGET"
echo "   .NET RID:     $DOTNET_RID"
echo "   Engine Lib:   $ENGINE_LIB"
echo "   Output Dir:   $OUTPUT_DIR"

# ==========================================
# 5. Cleanup
# ==========================================
echo ""
echo "🧹 Cleaning up..."
rm -rf "$BUNDLE_ROOT"
rm -rf "$OUTPUT_DIR"
mkdir -p "$SERVICE_DIR"
mkdir -p "$OUTPUT_DIR"

# ==========================================
# 6. Build Engine (Rust)
# ==========================================
echo ""
echo "🦀 Building Engine (Rust)..."
cd "$PROJECT_ROOT/catalytic-engine"
cargo build --release --target "$RUST_TARGET"

ENGINE_PATH="target/$RUST_TARGET/release/$ENGINE_LIB"
if [[ ! -f "$ENGINE_PATH" ]]; then
    echo "❌ Engine build failed: $ENGINE_PATH not found"
    exit 1
fi
cp "$ENGINE_PATH" "$SERVICE_DIR/"
echo "   ✓ Engine built: $ENGINE_LIB"

# ==========================================
# 7. Build Host (.NET)
# ==========================================
echo ""
echo "🔮 Building Host (.NET)..."
cd "$PROJECT_ROOT/catalytic/Catalytic"
dotnet publish -c Release -r "$DOTNET_RID" --self-contained true -o "$PROJECT_ROOT/catalytic/Catalytic/publish_output"

# Copy all publish output to service dir
cp -r "$PROJECT_ROOT/catalytic/Catalytic/publish_output/"* "$SERVICE_DIR/"

# Rename Host executable to avoid collision with UI
if [[ "$PLATFORM" == "macos" || "$PLATFORM" == "linux" ]]; then
    HOST_EXEC="$SERVICE_DIR/Catalytic"
    if [[ -f "$HOST_EXEC" ]]; then
        mv "$HOST_EXEC" "$SERVICE_DIR/CatalyticService"
        chmod +x "$SERVICE_DIR/CatalyticService"
    fi
elif [[ "$PLATFORM" == "windows" ]]; then
    HOST_EXEC="$SERVICE_DIR/Catalytic.exe"
    if [[ -f "$HOST_EXEC" ]]; then
        mv "$HOST_EXEC" "$SERVICE_DIR/CatalyticService.exe"
    fi
fi
echo "   ✓ Host built"

# ==========================================
# 8. Build UI (Gradle)
# ==========================================
echo ""
echo "🎨 Building UI (Gradle)..."
cd "$PROJECT_ROOT/catalyticui"

case "$PLATFORM" in
    macos)
        ./gradlew :composeApp:packageReleaseDistributionForCurrentOS --no-configuration-cache
        ;;
    linux)
        ./gradlew :composeApp:packageReleaseAppImage --no-configuration-cache
        ;;
    windows)
        ./gradlew :composeApp:packageReleaseMsi --no-configuration-cache
        ;;
esac

echo "   ✓ UI built"

# ==========================================
# 9. Package & Finalize
# ==========================================
echo ""
echo "📦 Packaging..."

case "$PLATFORM" in
    macos)
        # Find the .app built by Gradle
        GRADLE_APP="$PROJECT_ROOT/catalyticui/composeApp/build/compose/binaries/main-release/app/Catalytic.app"
        
        if [[ ! -d "$GRADLE_APP" ]]; then
            echo "❌ Gradle .app not found at: $GRADLE_APP"
            exit 1
        fi
        
        # Copy to output
        FINAL_APP="$OUTPUT_DIR/Catalytic.app"
        cp -R "$GRADLE_APP" "$FINAL_APP"
        
        # Inject Service into App Bundle
        echo "   💉 Injecting service..."
        mkdir -p "$FINAL_APP/Contents/Resources/service"
        cp -r "$SERVICE_DIR/"* "$FINAL_APP/Contents/Resources/service/"
        
        # Code Signing
        if [[ "$SKIP_SIGN" == "false" ]]; then
            echo "   🔏 Signing..."
            
            ENTITLEMENTS="$PROJECT_ROOT/entitlements.plist"
            
            # Clear existing signatures
            codesign --remove-signature "$FINAL_APP" 2>/dev/null || true
            
            # Sign libraries
            find "$FINAL_APP" -name "*.dylib" -exec codesign --force -s - {} \;
            find "$FINAL_APP" -name "*.jnilib" -exec codesign --force -s - {} \;
            
            # Sign Service executable
            if [[ -f "$ENTITLEMENTS" ]]; then
                codesign --force --entitlements "$ENTITLEMENTS" -s - "$FINAL_APP/Contents/Resources/service/CatalyticService"
            else
                codesign --force -s - "$FINAL_APP/Contents/Resources/service/CatalyticService"
            fi
            
            # Sign Main executable
            MAIN_EXEC="$FINAL_APP/Contents/MacOS/Catalytic"
            if [[ -f "$MAIN_EXEC" ]]; then
                if [[ -f "$ENTITLEMENTS" ]]; then
                    codesign --force --entitlements "$ENTITLEMENTS" -s - "$MAIN_EXEC"
                else
                    codesign --force -s - "$MAIN_EXEC"
                fi
            fi
            
            # Sign App Bundle
            codesign --force -s - "$FINAL_APP"
            echo "   ✓ Signed"
        fi
        
        # Create DMG
        echo "   📀 Creating DMG..."
        DMG_NAME="Catalytic-$ARCH_NAME.dmg"
        DMG_PATH="$OUTPUT_DIR/$DMG_NAME"
        DMG_TEMP="$OUTPUT_DIR/dmg_temp"
        
        # Prepare DMG content
        mkdir -p "$DMG_TEMP"
        cp -R "$FINAL_APP" "$DMG_TEMP/"
        ln -s /Applications "$DMG_TEMP/Applications"
        
        # Create DMG
        hdiutil create -volname "Catalytic" -srcfolder "$DMG_TEMP" -ov -format UDZO "$DMG_PATH"
        
        # Cleanup temp
        rm -rf "$DMG_TEMP"
        
        # Detach any auto-mounted volumes
        hdiutil detach "/Volumes/Catalytic" 2>/dev/null || true
        
        echo "   ✓ DMG created: $DMG_NAME"
        ;;
        
    linux)
        echo "📦 Building Linux AppImage..."
        
        # Find the Gradle-built app directory
        APPDIR="$PROJECT_ROOT/catalyticui/composeApp/build/compose/binaries/main-release/app/Catalytic"
        
        if [[ ! -d "$APPDIR" ]]; then
            echo "❌ Gradle AppDir not found at: $APPDIR"
            exit 1
        fi
        
        # Inject Service into AppDir (critical step)
        echo "   💉 Injecting service into AppDir..."
        SERVICE_TARGET="$APPDIR/lib/app/service"
        mkdir -p "$SERVICE_TARGET"
        cp -r "$SERVICE_DIR/"* "$SERVICE_TARGET/"
        echo "   ✓ Service injected to: $SERVICE_TARGET"
        
        # Download appimagetool if not cached
        APPIMAGETOOL_CACHE="$PROJECT_ROOT/build/cache"
        APPIMAGETOOL="$APPIMAGETOOL_CACHE/appimagetool"
        if [[ ! -x "$APPIMAGETOOL" ]]; then
            echo "   ⬇️  Downloading appimagetool..."
            mkdir -p "$APPIMAGETOOL_CACHE"
            wget -q -O "$APPIMAGETOOL" "https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage"
            chmod +x "$APPIMAGETOOL"
        fi
        
        # Create .desktop file if missing
        DESKTOP_FILE="$APPDIR/catalytic.desktop"
        if [[ ! -f "$DESKTOP_FILE" ]]; then
            echo "   📝 Creating .desktop file..."
            cat > "$DESKTOP_FILE" << 'DESKTOP_EOF'
[Desktop Entry]
Type=Application
Name=Catalytic
Exec=Catalytic
Icon=catalytic
Categories=Development;
Comment=Low-code automation testing platform
DESKTOP_EOF
        fi
        
        # Create AppRun if missing
        APPRUN="$APPDIR/AppRun"
        if [[ ! -f "$APPRUN" ]]; then
            echo "   📝 Creating AppRun..."
            cat > "$APPRUN" << 'APPRUN_EOF'
#!/bin/bash
cd "$(dirname "$0")"
exec ./lib/app/bin/Catalytic "$@"
APPRUN_EOF
            chmod +x "$APPRUN"
        fi
        
        # Create icon symlink if missing
        if [[ ! -f "$APPDIR/catalytic.png" ]]; then
            # Try to find an icon in the lib directory
            ICON_SRC=$(find "$APPDIR/lib" -name "*.png" -type f | head -1)
            if [[ -n "$ICON_SRC" ]]; then
                cp "$ICON_SRC" "$APPDIR/catalytic.png"
            else
                # Create a placeholder icon (1x1 transparent PNG)
                echo "   ⚠️  No icon found, creating placeholder..."
                printf '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\nIDATx\x9cc\x00\x01\x00\x00\x05\x00\x01\r\n-\xb4\x00\x00\x00\x00IEND\xaeB`\x82' > "$APPDIR/catalytic.png"
            fi
        fi
        
        # Generate AppImage
        echo "   📦 Generating AppImage..."
        APPIMAGE_NAME="Catalytic-$ARCH_NAME.AppImage"
        ARCH=x86_64 "$APPIMAGETOOL" "$APPDIR" "$OUTPUT_DIR/$APPIMAGE_NAME"
        chmod +x "$OUTPUT_DIR/$APPIMAGE_NAME"
        
        echo "   ✓ AppImage created: $APPIMAGE_NAME"
        ;;

        
    windows)
        # Find MSI
        MSI=$(find "$PROJECT_ROOT/catalyticui/composeApp/build/compose/binaries" -name "*.msi" | head -1)
        
        if [[ -z "$MSI" ]]; then
            echo "❌ MSI not found"
            exit 1
        fi
        
        # Copy to output with arch suffix
        MSI_NAME="Catalytic-$ARCH_NAME.msi"
        cp "$MSI" "$OUTPUT_DIR/$MSI_NAME"
        
        # TODO: Inject service into MSI
        # MSI 需要用 WiX/etc 重新打包，目前先跳过
        echo "   ⚠️  Note: Service injection for MSI not implemented yet"
        echo "   ✓ MSI copied: $MSI_NAME"
        ;;
esac

# ==========================================
# 10. Summary
# ==========================================
echo ""
echo "=============================================="
echo "✅ BUILD COMPLETE"
echo "=============================================="
echo "Platform:    $PLATFORM-$ARCH_NAME"
echo "Output Dir:  $OUTPUT_DIR"
echo ""
echo "Contents:"
ls -la "$OUTPUT_DIR"
echo ""
