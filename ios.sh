# 构建
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
-destination 'platform=iOS Simulator,name=iPhone 16 Pro' build

# 解析 .app 路径
APP="$(find ~/Library/Developer/Xcode/DerivedData -type d -path '*/Build/Products/Debug-iphonesimulator/Zurron.app' | sort | tail -n 1)"
plutil -p "$APP/Info.plist" | grep CFBundleIdentifier

# 卸载旧包并安装、启动
xcrun simctl boot "iPhone 16 Pro"
open -a Simulator
xcrun simctl uninstall booted compose.project.zurron.Zurron 2 >/dev/null || true
xcrun simctl install booted "$APP"
xcrun simctl launch booted compose.project.zurron.Zurron